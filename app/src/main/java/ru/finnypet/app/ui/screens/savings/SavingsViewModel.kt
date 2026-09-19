package ru.finnypet.app.ui.screens.savings

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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.finnypet.app.domain.economy.SavingsEngine
import ru.finnypet.app.domain.economy.WithdrawPreview
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.GamePeriod
import ru.finnypet.app.domain.model.Goal
import ru.finnypet.app.domain.model.GoalId
import ru.finnypet.app.domain.model.GoalProgress
import ru.finnypet.app.domain.model.PeriodStatus
import ru.finnypet.app.domain.model.ProfileId
import ru.finnypet.app.domain.repository.ContentRepository
import ru.finnypet.app.domain.repository.PeriodRepository
import ru.finnypet.app.domain.repository.ProfileRepository
import ru.finnypet.app.domain.repository.SavingsRepository
import ru.finnypet.app.domain.usecase.OpenPeriodIfNeeded
import ru.finnypet.app.ui.text.textOf
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

/** Цель как её видит экран: название готовым текстом, цена и сколько уже отложено. */
data class GoalView(
    val id: GoalId,
    val title: String,
    val price: Coins,
    val saved: Coins,
    val isActive: Boolean,
) {

    val remaining: Coins get() = saved.shortfallTo(price)

    val isReached: Boolean get() = saved.covers(price)

    val fraction: Float get() = (saved.amount.toFloat() / price.amount).coerceAtMost(1f)
}

/**
 * Сумма, которую ребёнок набирает кнопками, и что домен про неё говорит.
 *
 * Живёт во вьюмодели, а не в экране: превью снятия считает [SavingsEngine]
 * по среднему пополнению из базы, и экрану неоткуда взять эти числа самому.
 */
sealed interface SavingsDraft {

    val amount: Coins
    val max: Coins

    val canAdd: Boolean get() = AmountLadder.canGoUp(amount, max)

    val canRemove: Boolean get() = AmountLadder.canGoDown(amount, max)

    data class Deposit(
        override val amount: Coins,
        override val max: Coins,
    ) : SavingsDraft

    /**
     * ТЗ 2.5.7: до подтверждения снятия ребёнок видит, сколько останется и,
     * если срок считается, как он изменится. [avgDeposit] хранится здесь,
     * чтобы не ходить в базу на каждое нажатие «больше».
     */
    data class Withdraw(
        override val amount: Coins,
        override val max: Coins,
        val preview: WithdrawPreview,
        val avgDeposit: Coins,
    ) : SavingsDraft
}

/** Итог пополнения или снятия: объяснение из контент-пака. */
data class SavingsOutcomeView(
    val text: String,
    val goalReached: Boolean,
)

sealed interface SavingsState {

    data object Loading : SavingsState

    data object Failed : SavingsState

    data class Ready(
        val goals: List<GoalView>,
        val active: GoalView?,
        /**
         * Через сколько игровых дней цель соберётся при таких же пополнениях
         * (ТЗ 2.5.7: расчёт по средней сумме). `null` — пополнений ещё не было
         * или цели нет, считать не из чего.
         */
        val periodsToGoal: Int?,
        val balance: Coins,
        /**
         * Откладывать и брать можно только когда день идёт: во время
         * планирования операция испортила бы сравнение плана с фактом
         * (ТЗ 2.5.5), как и покупка.
         */
        val canOperate: Boolean,
        val draft: SavingsDraft? = null,
        val outcome: SavingsOutcomeView? = null,
    ) : SavingsState {

        val canDeposit: Boolean get() = canOperate && active != null && balance > Coins.ZERO

        val canWithdraw: Boolean get() = canOperate && active != null && active.saved > Coins.ZERO
    }
}

/**
 * Копилка и цель (ТЗ 2.5.7): выбор цели, пополнение, снятие с превью,
 * срок достижения по средней сумме пополнения.
 *
 * Считает [SavingsEngine]; здесь чтение базы, запись результата и тексты.
 */
@HiltViewModel
class SavingsViewModel @Inject constructor(
    private val profiles: ProfileRepository,
    private val periods: PeriodRepository,
    private val savings: SavingsRepository,
    private val openPeriod: OpenPeriodIfNeeded,
    private val engine: SavingsEngine,
    content: ContentRepository,
) : ViewModel() {

    private val goals: List<Goal> = content.pack().goals
    private val texts: Map<String, String> = content.pack().texts

    private val failed = MutableStateFlow(false)
    private val draft = MutableStateFlow<SavingsDraft?>(null)
    private val outcome = MutableStateFlow<SavingsOutcomeView?>(null)

    /** Операции по одной: два быстрых «отложить» иначе списали бы баланс дважды. */
    private val editing = Mutex()

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<SavingsState> =
        combine(profiles.observeActive(), failed) { profile, isFailed -> profile to isFailed }
            .flatMapLatest { (profile, isFailed) ->
                when {
                    isFailed -> flowOf(SavingsState.Failed)
                    profile == null -> flowOf(SavingsState.Loading)
                    else -> forProfile(profile.id)
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = SavingsState.Loading,
            )

    init {
        act { openPeriod(it) }
    }

    fun retry() {
        failed.value = false
        act { openPeriod(it) }
    }

    /** Выбор цели денег не двигает, поэтому разрешён и во время планирования. */
    fun choose(goalId: GoalId) {
        act { profileId ->
            editing.withLock {
                if (goals.none { it.id == goalId }) return@withLock
                val progress = savings.progress(profileId, goalId)
                savings.setActive(profileId, progress.copy(isActive = true))
            }
        }
    }

    fun startDeposit() {
        act { profileId ->
            editing.withLock {
                val period = runningPeriod(profileId) ?: return@withLock
                activeGoal(profileId) ?: return@withLock
                val balance = periods.balance(period)
                if (balance == Coins.ZERO) return@withLock
                draft.value = SavingsDraft.Deposit(amount = AmountLadder.first(balance), max = balance)
            }
        }
    }

    fun startWithdraw() {
        act { profileId ->
            editing.withLock {
                runningPeriod(profileId) ?: return@withLock
                val (progress, goal) = activeGoal(profileId) ?: return@withLock
                if (progress.saved == Coins.ZERO) return@withLock
                val avgDeposit = savings.averageDeposit(profileId, goal.id)
                val amount = AmountLadder.first(progress.saved)
                draft.value = SavingsDraft.Withdraw(
                    amount = amount,
                    max = progress.saved,
                    preview = engine.previewWithdraw(amount, progress, goal, avgDeposit),
                    avgDeposit = avgDeposit,
                )
            }
        }
    }

    fun add() = step(up = true)

    fun remove() = step(up = false)

    fun cancel() {
        draft.value = null
    }

    fun confirm() {
        act { profileId ->
            editing.withLock {
                when (val current = draft.value) {
                    null -> Unit
                    is SavingsDraft.Deposit -> deposit(profileId, current.amount)
                    is SavingsDraft.Withdraw -> withdraw(profileId, current.amount)
                }
                draft.value = null
            }
        }
    }

    fun dismissOutcome() {
        outcome.value = null
    }

    /**
     * Шаг по лесенке. Превью снятия пересчитывается по прогрессу из базы:
     * сумма в черновике могла бы разойтись с накоплениями, если бы их
     * поменял кто-то ещё, и домен на это отвечает исключением.
     */
    private fun step(up: Boolean) {
        act { profileId ->
            editing.withLock {
                when (val current = draft.value) {
                    null -> Unit

                    is SavingsDraft.Deposit -> draft.value = current.copy(
                        amount = if (up) AmountLadder.up(current.amount, current.max) else AmountLadder.down(current.amount, current.max),
                    )

                    is SavingsDraft.Withdraw -> {
                        val (progress, goal) = activeGoal(profileId) ?: return@withLock
                        val amount = if (up) AmountLadder.up(current.amount, current.max) else AmountLadder.down(current.amount, current.max)
                        if (!progress.saved.covers(amount)) return@withLock
                        draft.value = current.copy(
                            amount = amount,
                            preview = engine.previewWithdraw(amount, progress, goal, current.avgDeposit),
                        )
                    }
                }
            }
        }
    }

    /** Баланс, прогресс и период берутся из базы в момент записи, не из экрана. */
    private suspend fun deposit(profileId: ProfileId, requested: Coins) {
        val period = runningPeriod(profileId) ?: return
        val (progress, goal) = activeGoal(profileId) ?: return
        val balance = periods.balance(period)
        val amount = minOf(requested, balance)
        if (amount == Coins.ZERO) return

        val result = engine.deposit(amount, balance, progress, goal, period.id)
        // Сначала операция, потом прогресс: баланс считается по операциям,
        // и если приложение закроется между записями, монеты будут видны в
        // истории, а не пропадут.
        periods.addTransaction(result.value.transaction)
        savings.save(profileId, result.value.progress)
        outcome.value = SavingsOutcomeView(
            text = texts.textOf(result.explanation),
            goalReached = result.value.goalReached,
        )
    }

    private suspend fun withdraw(profileId: ProfileId, requested: Coins) {
        val period = runningPeriod(profileId) ?: return
        val (progress, goal) = activeGoal(profileId) ?: return
        val amount = minOf(requested, progress.saved)
        if (amount == Coins.ZERO) return

        val result = engine.withdraw(amount, periods.balance(period), progress, goal, period.id)
        periods.addTransaction(result.value.transaction)
        savings.save(profileId, result.value.progress)
        outcome.value = SavingsOutcomeView(
            text = texts.textOf(result.explanation),
            goalReached = result.value.goalReached,
        )
    }

    private suspend fun runningPeriod(profileId: ProfileId): GamePeriod? =
        periods.current(profileId)?.takeIf { it.status == PeriodStatus.RUNNING }

    /**
     * Активная цель вместе с прогрессом. Цель могла исчезнуть из контент-пака
     * после правки файла — тогда копить не на что, и операции не идут.
     */
    private suspend fun activeGoal(profileId: ProfileId): Pair<GoalProgress, Goal>? {
        val progress = savings.activeProgress(profileId) ?: return null
        val goal = goals.firstOrNull { it.id == progress.goalId } ?: return null
        return progress to goal
    }

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
    private fun forProfile(profileId: ProfileId): Flow<SavingsState> =
        periods.observeCurrent(profileId).flatMapLatest { period ->
            if (period == null) {
                flowOf(SavingsState.Loading)
            } else {
                combine(
                    periods.observeBalance(period),
                    savings.observeAll(profileId),
                    periods.observeTransactions(period.id),
                    draft,
                    outcome,
                ) { balance, progresses, _, draft, outcome ->
                    ready(profileId, period, balance, progresses, draft, outcome)
                }
            }
        }

    /**
     * Срок достижения считается по среднему пополнению из базы, поэтому
     * состояние собирается в приостанавливаемой функции. Подписка на
     * операции периода нужна только чтобы пересчитать срок после пополнения.
     */
    private suspend fun ready(
        profileId: ProfileId,
        period: GamePeriod,
        balance: Coins,
        progresses: List<GoalProgress>,
        draft: SavingsDraft?,
        outcome: SavingsOutcomeView?,
    ): SavingsState.Ready {
        val byGoal = progresses.associateBy { it.goalId }
        val views = goals.map { goal ->
            val progress = byGoal[goal.id]
            GoalView(
                id = goal.id,
                title = texts.textOf(goal.titleKey),
                price = goal.price,
                saved = progress?.saved ?: Coins.ZERO,
                isActive = progress?.isActive == true,
            )
        }
        val active = views.firstOrNull { it.isActive }
        val periodsToGoal = active?.let { view ->
            val goal = goals.first { it.id == view.id }
            engine.periodsToGoal(byGoal.getValue(goal.id), goal, savings.averageDeposit(profileId, goal.id))
        }
        return SavingsState.Ready(
            goals = views,
            active = active,
            periodsToGoal = periodsToGoal,
            balance = balance,
            canOperate = period.status == PeriodStatus.RUNNING,
            draft = draft,
            outcome = outcome,
        )
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
