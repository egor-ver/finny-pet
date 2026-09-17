package ru.finnypet.app.ui.screens.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.finnypet.app.domain.economy.BudgetEngine
import ru.finnypet.app.domain.economy.PeriodEngine
import ru.finnypet.app.domain.economy.PlanCheck
import ru.finnypet.app.domain.model.BudgetPlan
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.GamePeriod
import ru.finnypet.app.domain.model.PeriodStatus
import ru.finnypet.app.domain.model.ProfileId
import ru.finnypet.app.domain.model.SpendCategory
import ru.finnypet.app.domain.model.Transaction
import ru.finnypet.app.domain.repository.PeriodRepository
import ru.finnypet.app.domain.repository.ProfileRepository
import ru.finnypet.app.domain.usecase.OpenPeriodIfNeeded
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

/** Строка сравнения: сколько задумали и сколько вышло на самом деле. */
data class BudgetLine(
    val category: SpendCategory,
    val planned: Coins,
    val actual: Coins,
    val followed: Boolean,
)

/**
 * Что показывает экран плана.
 *
 * Два рабочих состояния, потому что и ТЗ 2.5.5 описывает два разных момента:
 * до подтверждения ребёнок распределяет монеты, после — видит, как план сошёлся
 * с тратами. Менять план задним числом нельзя, иначе сравнение теряет смысл.
 */
sealed interface BudgetState {

    data object Loading : BudgetState

    data object Failed : BudgetState

    data class Planning(
        val available: Coins,
        val plan: BudgetPlan,
        val remainder: Coins,
        val overBy: Coins,
        val step: Int,
    ) : BudgetState {

        /** Пустой план подтверждать нечего, а перебор сначала надо исправить. */
        val canConfirm: Boolean get() = plan.total > Coins.ZERO && overBy == Coins.ZERO

        val isDistributed: Boolean get() = remainder == Coins.ZERO && overBy == Coins.ZERO

        /**
         * Хватает и остатка меньше шага: последние монеты добираются неполным
         * шагом. Иначе при доступной сумме, не кратной шагу, остаток нельзя
         * было бы обнулить, а числа экономики правит контент-пак.
         */
        fun canAdd(): Boolean = remainder > Coins.ZERO

        fun canRemove(category: SpendCategory): Boolean =
            plan.amountFor(category) > Coins.ZERO
    }

    data class Started(
        val lines: List<BudgetLine>,
        val planTotal: Coins,
        val factTotal: Coins,
    ) : BudgetState
}

/**
 * Планирование личного бюджета (ТЗ 2.5.5).
 *
 * Черновик плана пишется в базу на каждое изменение, а не при выходе: ТЗ
 * разрешает менять план до подтверждения, и ребёнок вправе выйти, подумать и
 * вернуться к тем же цифрам.
 */
@HiltViewModel
class BudgetViewModel @Inject constructor(
    private val profiles: ProfileRepository,
    private val periods: PeriodRepository,
    private val openPeriod: OpenPeriodIfNeeded,
    private val budget: BudgetEngine,
    private val periodEngine: PeriodEngine,
) : ViewModel() {

    private val failed = MutableStateFlow(false)

    /**
     * Правки плана идут по одной. Без этого два быстрых нажатия «плюс»
     * прочитали бы одно и то же значение и вторая монета потерялась бы:
     * запись в базу занимает больше времени, чем промежуток между нажатиями.
     */
    private val editing = Mutex()

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<BudgetState> =
        combine(profiles.observeActive(), failed) { profile, isFailed -> profile to isFailed }
            .flatMapLatest { (profile, isFailed) ->
                when {
                    isFailed -> flowOf(BudgetState.Failed)
                    profile == null -> flowOf(BudgetState.Loading)
                    else -> forProfile(profile.id)
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = BudgetState.Loading,
            )

    init {
        // На экран плана можно попасть и после перезапуска приложения, когда
        // главный экран не успел открыть игровой день.
        act { openPeriod(it) }
    }

    fun retry() {
        failed.value = false
        act { openPeriod(it) }
    }

    fun add(category: SpendCategory) = change(category, STEP)

    fun remove(category: SpendCategory) = change(category, -STEP)

    /**
     * Подтверждение переводит день из планирования в работу. С этого момента
     * план — то, с чем сравнивается факт, и менять его уже нельзя (ТЗ 2.5.5).
     */
    fun confirm() {
        act { profileId ->
            editing.withLock {
                val period = periods.current(profileId) ?: return@withLock
                val plan = periods.plan(period.id) ?: return@withLock
                if (period.status != PeriodStatus.PLANNING) return@withLock
                if (plan.total == Coins.ZERO) return@withLock
                periods.save(periodEngine.confirmPlan(period))
            }
        }
    }

    /**
     * Превысить доступную сумму нельзя: ТЗ 2.5.5 требует, чтобы приложение это
     * контролировало. Кнопка «плюс» в таком случае просто недоступна, и ребёнок
     * видит нулевой остаток, а не сообщение об ошибке.
     */
    private fun change(category: SpendCategory, delta: Int) {
        act { profileId ->
            editing.withLock {
                val period = periods.current(profileId) ?: return@withLock
                if (period.status != PeriodStatus.PLANNING) return@withLock

                // Считаем от того, что лежит в базе, а не от показанного на
                // экране: экран отстаёт от базы на время записи, и при быстрых
                // нажатиях он вернул бы устаревшую сумму.
                val stored = periods.plan(period.id) ?: BudgetPlan.EMPTY
                val amount = moved(stored, category, delta, period.available) ?: return@withLock
                periods.savePlan(period.id, stored.with(category, Coins(amount)))
            }
        }
    }

    /**
     * Новая сумма направления или `null`, если двигать некуда.
     *
     * Шаг урезается по месту: добавить можно не больше, чем осталось
     * нераспределённого, а убрать — не больше, чем лежит. Так последние монеты
     * не застревают, когда доступная сумма не делится на шаг нацело.
     */
    private fun moved(
        plan: BudgetPlan,
        category: SpendCategory,
        delta: Int,
        available: Coins,
    ): Int? {
        val current = plan.amountFor(category).amount
        return if (delta > 0) {
            val free = available.amount - plan.total.amount
            if (free <= 0) null else current + minOf(delta, free)
        } else {
            if (current == 0) null else current - minOf(-delta, current)
        }
    }

    /**
     * Общая обёртка: дождаться профиля, выполнить и не уронить экран.
     * ТЗ 3.4 запрещает тупики, поэтому любой сбой превращается в состояние
     * с кнопкой повтора, а не в исключение.
     */
    private fun act(block: suspend (ProfileId) -> Unit) {
        viewModelScope.launch {
            val profile = profiles.observeActive().filterNotNull().first()
            try {
                block(profile.id)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                failed.value = true
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun forProfile(profileId: ProfileId): Flow<BudgetState> =
        periods.observeCurrent(profileId).flatMapLatest { period ->
            if (period == null) {
                flowOf(BudgetState.Loading)
            } else {
                combine(
                    periods.observePlan(period.id),
                    periods.observeTransactions(period.id),
                ) { plan, transactions ->
                    stateOf(period, plan ?: BudgetPlan.EMPTY, transactions)
                }
            }
        }

    private fun stateOf(
        period: GamePeriod,
        plan: BudgetPlan,
        transactions: List<Transaction>,
    ): BudgetState = when (period.status) {
        PeriodStatus.PLANNING -> planning(period, plan)
        else -> started(plan, transactions)
    }

    private fun planning(period: GamePeriod, plan: BudgetPlan): BudgetState.Planning {
        val check = budget.check(plan, period.available)
        return BudgetState.Planning(
            available = period.available,
            plan = plan,
            remainder = (check as? PlanCheck.Fits)?.remainder ?: Coins.ZERO,
            overBy = (check as? PlanCheck.Exceeds)?.overBy ?: Coins.ZERO,
            step = STEP,
        )
    }

    private fun started(plan: BudgetPlan, transactions: List<Transaction>): BudgetState.Started {
        val report = budget.compare(plan, periodEngine.factOf(transactions))
        return BudgetState.Started(
            lines = report.lines.map { line ->
                BudgetLine(
                    category = line.category,
                    planned = line.planned,
                    actual = line.actual,
                    followed = line.followed,
                )
            },
            planTotal = report.planTotal,
            factTotal = report.factTotal,
        )
    }

    private companion object {
        /**
         * Шаг в пять монет: суммы в игре двузначные, и набирать их по одной
         * ребёнку долго, а клавиатура на этом экране лишняя — промах по цифре
         * ломал бы весь план.
         */
        const val STEP = 5
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
