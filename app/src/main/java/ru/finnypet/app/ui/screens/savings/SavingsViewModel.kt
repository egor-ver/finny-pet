package ru.finnypet.app.ui.screens.savings

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
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
import ru.finnypet.app.domain.repository.ActionOutcome
import ru.finnypet.app.domain.repository.ContentRepository
import ru.finnypet.app.domain.repository.OutcomeRecorder
import ru.finnypet.app.domain.repository.PeriodRepository
import ru.finnypet.app.domain.repository.ProfileRepository
import ru.finnypet.app.ui.components.goalFraction
import ru.finnypet.app.ui.screens.ProfileViewModel
import ru.finnypet.app.domain.repository.SavingsRepository
import ru.finnypet.app.domain.usecase.OpenPeriodIfNeeded
import ru.finnypet.app.ui.text.textOf
import javax.inject.Inject

/** Цель как её видит экран: название готовым текстом, цена и сколько уже отложено. */
data class GoalView(
    val id: GoalId,
    val title: String,
    val price: Coins,
    val saved: Coins,
    val isActive: Boolean,
    val icon: String,
    /** Уже куплена (R13): второй раз не выбирается, иначе рядом с совой встанут две одинаковые вещи. */
    val isBought: Boolean = false,
) {

    val remaining: Coins get() = saved.shortfallTo(price)

    val isReached: Boolean get() = saved.covers(price)

    val fraction: Float get() = goalFraction(saved, price)
}

/**
 * Сумма, которую ребёнок набирает кнопками, и что домен про неё говорит.
 *
 * Собирается из состояния, а не хранится: граница — это баланс или
 * накопленное прямо сейчас, а превью снятия считает [SavingsEngine] по
 * среднему пополнению из базы. Экрану неоткуда взять эти числа самому.
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
     * если срок считается, как он изменится.
     */
    data class Withdraw(
        override val amount: Coins,
        override val max: Coins,
        val preview: WithdrawPreview,
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
        /**
         * Через сколько игровых дней цель соберётся при таких же пополнениях
         * (ТЗ 2.5.7: расчёт по средней сумме). `null` — пополнений ещё не было
         * или цели нет, считать не из чего.
         */
        val periodsToGoal: Int?,
        /** Среднее пополнение — «если откладывать как обычно, по 8». */
        val usualDeposit: Coins,
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

        val active: GoalView? get() = goals.firstOrNull { it.isActive }

        val canDeposit: Boolean get() = canOperate && active != null && balance > Coins.ZERO

        val canWithdraw: Boolean get() = canOperate && (active?.saved ?: Coins.ZERO) > Coins.ZERO

        val canBuy: Boolean get() = canOperate && active?.isReached == true
    }
}

/** Что ребёнок набирает: вид операции и сумма. Границы и превью досчитывает состояние. */
private data class DraftRequest(
    val kind: OperationKind,
    val amount: Coins,
)

private enum class OperationKind { DEPOSIT, WITHDRAW }

/**
 * Копилка и цель (ТЗ 2.5.7): выбор цели, пополнение, снятие с превью,
 * срок достижения по средней сумме пополнения.
 *
 * Считает [SavingsEngine]; здесь чтение базы, запись результата и тексты.
 */
@HiltViewModel
class SavingsViewModel @Inject constructor(
    profiles: ProfileRepository,
    private val periods: PeriodRepository,
    private val savings: SavingsRepository,
    private val openPeriod: OpenPeriodIfNeeded,
    private val engine: SavingsEngine,
    private val recorder: OutcomeRecorder,
    content: ContentRepository,
) : ProfileViewModel(profiles) {

    private val goals: List<Goal> = content.pack().goals
    private val texts: Map<String, String> = content.pack().texts

    private val draft = MutableStateFlow<DraftRequest?>(null)
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

    fun retry() = retryWith { openPeriod(it) }

    /**
     * Выбор цели денег не двигает, поэтому разрешён и во время планирования.
     * Открытый черновик закрывается: его границы и превью считались по
     * прежней цели.
     */
    fun choose(goalId: GoalId) {
        act { profileId ->
            editing.withLock {
                if (goals.none { it.id == goalId }) return@withLock
                if (goalId in savings.observeBought(profileId).first()) return@withLock
                draft.value = null
                val progress = savings.progress(profileId, goalId)
                savings.setActive(profileId, progress.copy(isActive = true))
            }
        }
    }

    fun startDeposit() = start(OperationKind.DEPOSIT)

    fun startWithdraw() = start(OperationKind.WITHDRAW)

    fun add() = step(up = true)

    fun remove() = step(up = false)

    fun cancel() {
        draft.value = null
    }

    /**
     * Окно закрывается сразу, до записи: повторное нажатие или «не сейчас»
     * во время записи уже ни на что не влияют, а ребёнок не видит окно,
     * которое «не реагирует».
     */
    fun confirm() {
        act { profileId ->
            editing.withLock {
                val current = draft.value ?: return@withLock
                draft.value = null
                operate(profileId, current.kind, current.amount)
            }
        }
    }

    /** Покупка собранной цели (R13): всё берётся из базы в момент записи, не из экрана. */
    fun buy() {
        act { profileId ->
            editing.withLock {
                draft.value = null
                val period = runningPeriod(profileId) ?: return@withLock
                val (progress, goal) = activeGoal(profileId) ?: return@withLock
                if (!progress.isReached(goal)) return@withLock
                val result = engine.buy(periods.balance(period), progress, goal, period.id)
                recorder.record(
                    profileId,
                    ActionOutcome(transaction = result.value.transaction, savings = result.value.progress),
                )
                outcome.value = SavingsOutcomeView(text = texts.textOf(result.explanation), goalReached = false)
            }
        }
    }

    fun dismissOutcome() {
        outcome.value = null
    }

    private fun start(kind: OperationKind) {
        act { profileId ->
            editing.withLock {
                val limit = limitOf(profileId, kind) ?: return@withLock
                if (limit == Coins.ZERO) return@withLock
                draft.value = DraftRequest(kind = kind, amount = AmountLadder.first(limit))
            }
        }
    }

    /** Шаг по лесенке в границах, прочитанных из базы прямо сейчас. */
    private fun step(up: Boolean) {
        act { profileId ->
            editing.withLock {
                val current = draft.value ?: return@withLock
                val limit = limitOf(profileId, current.kind) ?: return@withLock
                val amount = if (up) AmountLadder.up(current.amount, limit) else AmountLadder.down(current.amount, limit)
                draft.value = current.copy(amount = amount)
            }
        }
    }

    /**
     * Сколько всего можно отложить или взять: баланс либо накопленное.
     * `null` — операция сейчас невозможна: день не идёт или цели нет.
     */
    private suspend fun limitOf(profileId: ProfileId, kind: OperationKind): Coins? {
        val period = runningPeriod(profileId) ?: return null
        val (progress, _) = activeGoal(profileId) ?: return null
        return when (kind) {
            OperationKind.DEPOSIT -> periods.balance(period)
            OperationKind.WITHDRAW -> progress.saved
        }
    }

    /** Баланс, прогресс и период берутся из базы в момент записи, не из экрана. */
    private suspend fun operate(profileId: ProfileId, kind: OperationKind, requested: Coins) {
        val period = runningPeriod(profileId) ?: return
        val (progress, goal) = activeGoal(profileId) ?: return
        val balance = periods.balance(period)
        val limit = when (kind) {
            OperationKind.DEPOSIT -> balance
            OperationKind.WITHDRAW -> progress.saved
        }
        val amount = minOf(requested, limit)
        if (amount == Coins.ZERO) return

        val result = when (kind) {
            OperationKind.DEPOSIT -> engine.deposit(amount, balance, progress, goal, period.id)
            OperationKind.WITHDRAW -> engine.withdraw(amount, balance, progress, goal, period.id)
        }
        recorder.record(
            profileId,
            ActionOutcome(transaction = result.value.transaction, savings = result.value.progress),
        )
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

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun forProfile(profileId: ProfileId): Flow<SavingsState> =
        periods.observeCurrent(profileId).flatMapLatest { period ->
            if (period == null) {
                flowOf(SavingsState.Loading)
            } else {
                // Средний взнос меняется только вместе с операциями и выбором
                // цели — считается на их изменение, а не на каждое нажатие
                // «больше» в окне. Идёт одним значением с прогрессом, чтобы
                // экран не показал новое накопленное со старым сроком.
                val progressWithAverage = combine(
                    savings.observeAll(profileId),
                    periods.observeTransactions(period.id),
                ) { progresses, _ ->
                    val active = progresses.firstOrNull { it.isActive }
                    progresses to (active?.let { savings.averageDeposit(profileId, it.goalId) } ?: Coins.ZERO)
                }
                combine(
                    periods.observeBalance(period),
                    progressWithAverage,
                    savings.observeBought(profileId),
                    draft,
                    outcome,
                ) { balance, (progresses, avgDeposit), bought, draft, outcome ->
                    ready(period, balance, progresses, avgDeposit, bought.toSet(), draft, outcome)
                }
            }
        }

    private fun ready(
        period: GamePeriod,
        balance: Coins,
        progresses: List<GoalProgress>,
        avgDeposit: Coins,
        bought: Set<GoalId>,
        request: DraftRequest?,
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
                icon = goal.icon,
                isBought = goal.id in bought,
            )
        }
        val progress = progresses.firstOrNull { it.isActive }
        val goal = progress?.let { active -> goals.firstOrNull { it.id == active.goalId } }
        val periodsToGoal = if (progress != null && goal != null) engine.periodsToGoal(progress, goal, avgDeposit) else null
        return SavingsState.Ready(
            goals = views,
            periodsToGoal = periodsToGoal,
            usualDeposit = avgDeposit,
            balance = balance,
            canOperate = period.status == PeriodStatus.RUNNING,
            draft = request?.let { draftOf(it, balance, progress, goal, avgDeposit) },
            outcome = outcome,
        )
    }

    /**
     * Черновик в сегодняшних границах: баланс и накопленное могли измениться
     * после того, как окно открылось, и сумма в нём не должна обещать больше,
     * чем есть. Когда брать или откладывать стало нечего — окно закрывается.
     */
    private fun draftOf(
        request: DraftRequest,
        balance: Coins,
        progress: GoalProgress?,
        goal: Goal?,
        avgDeposit: Coins,
    ): SavingsDraft? = when (request.kind) {
        OperationKind.DEPOSIT -> {
            if (balance == Coins.ZERO || progress == null) {
                null
            } else {
                SavingsDraft.Deposit(amount = minOf(request.amount, balance), max = balance)
            }
        }

        OperationKind.WITHDRAW -> {
            if (progress == null || goal == null || progress.saved == Coins.ZERO) {
                null
            } else {
                val amount = minOf(request.amount, progress.saved)
                SavingsDraft.Withdraw(
                    amount = amount,
                    max = progress.saved,
                    preview = engine.previewWithdraw(amount, progress, goal, avgDeposit),
                )
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
