package ru.finnypet.app.ui.screens.budget

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.finnypet.app.domain.economy.BudgetEngine
import ru.finnypet.app.domain.economy.GameBalance
import ru.finnypet.app.domain.economy.PeriodEngine
import ru.finnypet.app.domain.economy.PetStateEngine
import ru.finnypet.app.domain.economy.PlanCheck
import ru.finnypet.app.domain.economy.SavingsEngine
import ru.finnypet.app.domain.model.BudgetPlan
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.Goal
import ru.finnypet.app.domain.model.GoalId
import ru.finnypet.app.domain.model.GoalProgress
import ru.finnypet.app.domain.model.PeriodStatus
import ru.finnypet.app.domain.model.Pet
import ru.finnypet.app.domain.model.Profile
import ru.finnypet.app.domain.model.SpendCategory
import ru.finnypet.app.domain.model.Transaction
import ru.finnypet.app.domain.repository.ContentRepository
import ru.finnypet.app.domain.repository.PeriodRepository
import ru.finnypet.app.domain.repository.ProfileRepository
import ru.finnypet.app.domain.repository.SavingsRepository
import ru.finnypet.app.domain.usecase.ConfirmPlan
import ru.finnypet.app.ui.components.BudgetLine
import ru.finnypet.app.ui.components.OwlLook
import ru.finnypet.app.ui.components.owlDescription
import ru.finnypet.app.ui.components.owlLook
import ru.finnypet.app.ui.screens.ProfileViewModel
import ru.finnypet.app.domain.usecase.OpenPeriodIfNeeded
import ru.finnypet.app.ui.text.textOf
import javax.inject.Inject

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

    /**
     * [available] — весь кошелёк, а не остаток со вчера и доход: бонус
     * взрослого и награда за задание тоже раскладываются по плану (R5).
     * [needsGoal] — в копилку запланировано, а цели нет: отложить некуда.
     * [hasGoal] — без цели копилку не разложить: вместо ползунка «Выбрать цель».
     * [owl] и [phrase] — сова отвечает на каждое движение ползунка;
     * [hints] — строка пояснения под банкой, `null` — пояснять нечего.
     * [mandatoryCover] — сколько стоит закрыть все непокрытые потребности,
     * `null` — в магазине их не закрыть целиком, считать нечего.
     */
    data class Planning(
        val available: Coins,
        val plan: BudgetPlan,
        val remainder: Coins,
        val overBy: Coins,
        val needsGoal: Boolean,
        val hasGoal: Boolean,
        val owl: OwlLook,
        val phrase: String,
        val hints: Map<SpendCategory, String?> = emptyMap(),
        val mandatoryCover: Coins? = null,
    ) : BudgetState {

        /** Пустой план подтверждать нечего, а перебор сначала надо исправить. */
        val canConfirm: Boolean get() = plan.total > Coins.ZERO && overBy == Coins.ZERO && !needsGoal

        val isDistributed: Boolean get() = remainder == Coins.ZERO && overBy == Coins.ZERO
    }

    /** Банки после подтверждения: сколько задумано и сколько уже потрачено или отложено. */
    data class Started(val lines: List<BudgetLine>) : BudgetState
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
    profiles: ProfileRepository,
    private val periods: PeriodRepository,
    private val savings: SavingsRepository,
    private val openPeriod: OpenPeriodIfNeeded,
    private val confirmPlan: ConfirmPlan,
    private val budget: BudgetEngine,
    private val periodEngine: PeriodEngine,
    private val petState: PetStateEngine,
    private val savingsEngine: SavingsEngine,
    private val balance: GameBalance,
    content: ContentRepository,
) : ProfileViewModel(profiles) {

    private val pack = content.pack()
    private val goals: Map<GoalId, Goal> = pack.goals.associateBy { it.id }
    private val wants = pack.shop.filter { it.category == SpendCategory.OPTIONAL }

    /**
     * Правки плана идут по одной. Без этого два быстрых движения ползунка
     * прочитали бы одно и то же значение и одно из них потерялось бы:
     * запись в базу занимает больше времени, чем промежуток между ними.
     */
    private val editing = Mutex()

    /** Ползунок только что упёрся в конец кошелька — до следующего движения. */
    private val hitLimit = MutableStateFlow(false)

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<BudgetState> =
        combine(profiles.observeActive(), failed) { profile, isFailed -> profile to isFailed }
            .flatMapLatest { (profile, isFailed) ->
                when {
                    isFailed -> flowOf(BudgetState.Failed)
                    profile == null -> flowOf(BudgetState.Loading)
                    else -> forProfile(profile)
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

    fun retry() = retryWith { openPeriod(it) }


    /**
     * Подтверждение переводит день из планирования в работу и сразу
     * откладывает долю копилки. С этого момента план — то, с чем
     * сравнивается факт, и менять его уже нельзя (ТЗ 2.5.5).
     */
    fun confirm() {
        act { profileId ->
            editing.withLock { confirmPlan(profileId) }
        }
    }

    /**
     * Превысить доступную сумму нельзя: ТЗ 2.5.5 требует, чтобы приложение это
     * контролировало. Ползунок дальше свободных монет не идёт, а сова
     * объясняет почему — сообщения об ошибке нет.
     */
    fun set(category: SpendCategory, amount: Coins) {
        act { profileId ->
            editing.withLock {
                val period = periods.current(profileId) ?: return@withLock
                if (period.status != PeriodStatus.PLANNING) return@withLock

                // Считаем от того, что лежит в базе, а не от показанного на
                // экране: экран отстаёт от базы на время записи, и при быстром
                // движении он вернул бы устаревшую сумму.
                val stored = periods.plan(period.id) ?: BudgetPlan.EMPTY
                val clamped = budget.clamped(stored, category, amount, periods.balance(period))
                hitLimit.value = clamped < amount
                periods.savePlan(period.id, stored.with(category, clamped))
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun forProfile(profile: Profile): Flow<BudgetState> = combine(
        periods.observeCurrent(profile.id),
        profiles.observePet(profile.id),
    ) { period, pet -> period to pet }
        .flatMapLatest { (period, pet) ->
            if (period == null || pet == null) {
                flowOf(BudgetState.Loading)
            } else {
                combine(
                    periods.observePlan(period.id),
                    periods.observeTransactions(period.id),
                    periods.observeBalance(period),
                    savings.observeActive(profile.id),
                    hitLimit,
                ) { plan, transactions, wallet, goal, limit ->
                    val stored = plan ?: BudgetPlan.EMPTY
                    when (period.status) {
                        PeriodStatus.PLANNING -> planning(profile, pet, stored, wallet, goal, limit)
                        else -> started(stored, transactions)
                    }
                }
            }
        }

    private fun planning(
        profile: Profile,
        pet: Pet,
        plan: BudgetPlan,
        wallet: Coins,
        progress: GoalProgress?,
        limit: Boolean,
    ): BudgetState.Planning {
        val check = budget.check(plan, wallet)
        val remainder = (check as? PlanCheck.Fits)?.remainder ?: Coins.ZERO
        val overBy = (check as? PlanCheck.Exceeds)?.overBy ?: Coins.ZERO
        val goal = progress?.let { goals[it.goalId] }
        val goalTitle = goal?.let { pack.texts.textOf(it.titleKey) }
        val coverByNeed = petState.coverByNeed(pet.state, pack.shop)
        val cover = coverByNeed?.values?.fold(Coins.ZERO) { sum, price -> sum + price }
        val owl = planOwl(plan, wallet, cover, balance.needSlack, goalTitle, limit)
        val days = if (progress != null && goal != null) savingsEngine.periodsToGoal(progress, goal, plan.savings) else null
        return BudgetState.Planning(
            available = wallet,
            plan = plan,
            remainder = remainder,
            overBy = overBy,
            needsGoal = plan.savings > Coins.ZERO && progress == null,
            hasGoal = progress != null,
            owl = owlLook(
                pets = pack.pets,
                appearance = profile.appearance,
                stage = pet.growth.stage,
                mood = owl.mood,
                // Грусть на плане — от нехватки на потребности, первой из них.
                description = owlDescription(pack.texts, profile.petName, owl.mood, petState.needsOf(pet.state).firstOrNull()),
            ),
            phrase = pack.texts.textOf(owl.phrase),
            hints = mapOf(
                SpendCategory.MANDATORY to mandatoryHint(pack.texts, coverByNeed),
                SpendCategory.OPTIONAL to optionalHint(pack.texts, plan.optional, wants),
                SpendCategory.SAVINGS to savingsHint(pack.texts, plan.savings, goalTitle, days),
            ),
            mandatoryCover = cover,
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
        )
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
