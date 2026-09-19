package ru.finnypet.app.ui.screens.tasks

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.finnypet.app.domain.economy.BudgetEngine
import ru.finnypet.app.domain.economy.GameBalance
import ru.finnypet.app.domain.economy.TaskEngine
import ru.finnypet.app.domain.model.BudgetPlan
import ru.finnypet.app.domain.model.Change
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.GamePeriod
import ru.finnypet.app.domain.model.ItemId
import ru.finnypet.app.domain.model.LearningTask
import ru.finnypet.app.domain.model.PeriodStatus
import ru.finnypet.app.domain.model.PetAppearance
import ru.finnypet.app.domain.model.Profile
import ru.finnypet.app.domain.model.ProfileId
import ru.finnypet.app.domain.model.ShopItem
import ru.finnypet.app.domain.model.SpendCategory
import ru.finnypet.app.domain.model.StepAnswer
import ru.finnypet.app.domain.model.TaskAttempt
import ru.finnypet.app.domain.model.TaskCompletion
import ru.finnypet.app.domain.model.TaskId
import ru.finnypet.app.domain.model.TaskStep
import ru.finnypet.app.domain.model.TaskTopic
import ru.finnypet.app.domain.model.Transaction
import ru.finnypet.app.domain.repository.ActionOutcome
import ru.finnypet.app.domain.repository.ContentRepository
import ru.finnypet.app.domain.repository.OutcomeRecorder
import ru.finnypet.app.domain.repository.PeriodRepository
import ru.finnypet.app.domain.repository.ProfileRepository
import ru.finnypet.app.ui.screens.ProfileViewModel
import ru.finnypet.app.domain.usecase.OpenPeriodIfNeeded
import ru.finnypet.app.domain.usecase.TaskSchedule
import ru.finnypet.app.ui.navigation.Task
import ru.finnypet.app.ui.text.textOf
import javax.inject.Inject

data class OptionView(
    val id: String,
    val label: String,
)

data class PickItemView(
    val id: ItemId,
    val title: String,
    val price: Coins,
    val category: SpendCategory,
)

/** Текущий шаг вместе с тем, что ребёнок уже набрал на нём. */
sealed interface StepView {

    val prompt: String

    /** Можно ли идти дальше: ответ должен быть, пустой шаг ничему не учит. */
    val canProceed: Boolean

    /** «История»: один вариант из нескольких. */
    data class Choice(
        override val prompt: String,
        val options: List<OptionView>,
        val chosen: String?,
    ) : StepView {

        override val canProceed: Boolean get() = chosen != null
    }

    /** «Три банки»: бюджет шага раскладывается по трём направлениям. */
    data class Distribute(
        override val prompt: String,
        val budget: Coins,
        val plan: BudgetPlan,
    ) : StepView {

        val remainder: Coins get() = budget - plan.total

        val canAdd: Boolean get() = remainder > Coins.ZERO

        fun canRemove(category: SpendCategory): Boolean = plan.amountFor(category) > Coins.ZERO

        override val canProceed: Boolean get() = plan.total > Coins.ZERO
    }

    /** «Полка»: корзина из товаров в рамках бюджета шага. */
    data class Pick(
        override val prompt: String,
        val budget: Coins,
        val items: List<PickItemView>,
        val picked: Set<ItemId>,
    ) : StepView {

        val spent: Coins
            get() = items.filter { it.id in picked }.fold(Coins.ZERO) { total, item -> total + item.price }

        /** Взять можно то, что влезает; убрать — что уже в корзине. */
        fun canToggle(id: ItemId): Boolean {
            val item = items.firstOrNull { it.id == id } ?: return false
            return id in picked || budget.covers(spent + item.price)
        }

        /** Пустая корзина — тоже решение: «ничего не купил». */
        override val canProceed: Boolean get() = true
    }
}

/** Итог: объяснение из контент-пака, что заплатили и что изменилось у питомца. */
data class TaskOutcomeView(
    val text: String,
    val reward: Coins,
    /** Было ли право на монеты сегодня; при нуле награды в исходе это всё равно «с монетами». */
    val rewardable: Boolean,
    val changes: List<Change.PetStat>,
)

sealed interface TaskStage {

    data object Intro : TaskStage

    data class Step(
        val index: Int,
        val total: Int,
        val step: StepView,
    ) : TaskStage

    data class Done(val outcome: TaskOutcomeView) : TaskStage
}

sealed interface TaskState {

    data object Loading : TaskState

    data object Failed : TaskState

    /** Задания с таким идентификатором в контент-паке нет — ссылка устарела. */
    data object Missing : TaskState

    data class Ready(
        val id: TaskId,
        val topic: TaskTopic,
        val intro: String,
        /** Кто просит совета: сова ребёнка, как на главном. */
        val appearance: PetAppearance,
        val maxReward: Coins,
        /** Остался ли на сегодня лимит наград: сообщается до старта, не после. */
        val rewardAvailable: Boolean,
        /** Задания проходятся только когда день идёт — как покупки и копилка. */
        val canStart: Boolean,
        val stage: TaskStage,
        /** Ответ отправлен и разбирается. */
        val submitting: Boolean = false,
    ) : TaskState
}

/** Что ребёнок уже сделал в этом задании. Живёт во вьюмодели — поворот переживает. */
private data class Progress(
    val started: Boolean = false,
    val answers: List<StepAnswer> = emptyList(),
    val draft: Draft = Draft.None,
    /** Ответ ушёл и ещё разбирается: кнопка гаснет, второе нажатие не нужно. */
    val submitting: Boolean = false,
    val outcome: TaskOutcomeView? = null,
)

private sealed interface Draft {
    data object None : Draft
    data class Chosen(val optionId: String?) : Draft
    data class Allocated(val plan: BudgetPlan) : Draft
    data class Picked(val ids: Set<ItemId>) : Draft
}

/**
 * Прохождение задания (ТЗ 2.5.8): вступление, шаги, ответ, объяснение.
 *
 * Считает [TaskEngine]; операцию, питомца и запись о прохождении одной
 * транзакцией пишет [OutcomeRecorder]. Лимит наград в день — по операциям
 * периода ([TaskSchedule]).
 */
@HiltViewModel
class TaskViewModel @Inject constructor(
    savedState: SavedStateHandle,
    profiles: ProfileRepository,
    private val periods: PeriodRepository,
    private val openPeriod: OpenPeriodIfNeeded,
    private val engine: TaskEngine,
    private val budget: BudgetEngine,
    private val recorder: OutcomeRecorder,
    private val balance: GameBalance,
    content: ContentRepository,
) : ProfileViewModel(profiles) {

    private val taskId = TaskId(savedState.toRoute<Task>().taskId)
    private val task: LearningTask? = content.pack().tasks.firstOrNull { it.id == taskId }
    private val items: Map<ItemId, ShopItem> = content.pack().shop.associateBy { it.id }
    private val texts: Map<String, String> = content.pack().texts

    private val progress = MutableStateFlow(Progress())

    /** Ответ уходит один раз: два быстрых «Ответить» иначе записали бы две награды. */
    private val answering = Mutex()

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<TaskState> =
        combine(profiles.observeActive(), failed) { profile, isFailed -> profile to isFailed }
            .flatMapLatest { (profile, isFailed) ->
                when {
                    isFailed -> flowOf(TaskState.Failed)
                    task == null -> flowOf(TaskState.Missing)
                    profile == null -> flowOf(TaskState.Loading)
                    else -> forProfile(profile, task)
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = TaskState.Loading,
            )

    init {
        act { openPeriod(it) }
    }

    fun retry() = retryWith { openPeriod(it) }

    fun start() {
        val task = task ?: return
        progress.update { current ->
            if (current.started) current else current.copy(started = true, draft = initialDraft(task.steps.first()))
        }
    }

    fun choose(optionId: String) {
        progress.update { current ->
            if (current.draft is Draft.Chosen) current.copy(draft = Draft.Chosen(optionId)) else current
        }
    }

    fun add(category: SpendCategory) = move(category, +STEP)

    fun remove(category: SpendCategory) = move(category, -STEP)

    /** Можно ли взять — решает та же [StepView.Pick], что показана на экране: правило одно. */
    fun toggle(itemId: ItemId) {
        val task = task ?: return
        progress.update { current ->
            val draft = current.draft as? Draft.Picked ?: return@update current
            val step = task.steps[current.answers.size] as? TaskStep.PickItems ?: return@update current
            val view = stepView(step, draft) as StepView.Pick
            if (!view.canToggle(itemId)) return@update current
            val ids = if (itemId in draft.ids) draft.ids - itemId else draft.ids + itemId
            current.copy(draft = Draft.Picked(ids))
        }
    }

    /**
     * Ответ на текущем шаге принят: дальше следующий шаг или разбор всего
     * задания. Разбор идёт под замком и от базы.
     */
    fun next() {
        val task = task ?: return
        val current = progress.value
        val step = task.steps.getOrNull(current.answers.size) ?: return
        val answer = answerOf(step, current.draft) ?: return
        if (current.submitting) return
        val answers = current.answers + answer
        if (answers.size < task.steps.size) {
            progress.update { it.copy(answers = answers, draft = initialDraft(task.steps[answers.size])) }
            return
        }
        // Последний шаг остаётся на экране, пока идёт разбор: сбрасывать его
        // в пустоту — значит мигать, а ответ уже отправлен.
        progress.update { it.copy(submitting = true) }
        act { profileId ->
            answering.withLock {
                try {
                    if (progress.value.outcome == null) answer(profileId, task, TaskAttempt(answers))
                } finally {
                    progress.update { it.copy(submitting = false) }
                }
            }
        }
    }

    /** Тот же шаг, что в плане дня: правило одно, живёт в [BudgetEngine]. */
    private fun move(category: SpendCategory, delta: Int) {
        val task = task ?: return
        progress.update { current ->
            val draft = current.draft as? Draft.Allocated ?: return@update current
            val step = task.steps[current.answers.size] as? TaskStep.Distribute ?: return@update current
            val amount = budget.stepped(draft.plan, category, delta, step.budget) ?: return@update current
            current.copy(draft = Draft.Allocated(draft.plan.with(category, amount)))
        }
    }

    private suspend fun answer(profileId: ProfileId, task: LearningTask, attempt: TaskAttempt) {
        // День перестал идти, пока задание было открыто: молча вернуть на шаг
        // нельзя, ребёнок не поймёт. Возвращаемся ко вступлению — там видна
        // подсказка про план и дорога к нему.
        val period = periods.current(profileId)?.takeIf { it.status == PeriodStatus.RUNNING }
        if (period == null) {
            progress.update { Progress() }
            return
        }
        val rewardable = TaskSchedule.rewardAvailable(periods.transactions(period.id), balance)
        val result = engine.evaluate(task, attempt, periods.balance(period), period.id, rewardable)
        val paid = result.value.transaction?.amount ?: Coins.ZERO
        val changes = recorder.record(
            profileId,
            ActionOutcome(
                transaction = result.value.transaction,
                effects = result.value.effects,
                taskCompletion = TaskCompletion(
                    taskId = task.id,
                    outcomeId = result.value.outcome.id,
                    reward = paid,
                ),
            ),
        )
        progress.update {
            it.copy(
                outcome = TaskOutcomeView(
                    text = texts.textOf(result.explanation),
                    reward = paid,
                    rewardable = rewardable,
                    changes = changes,
                ),
            )
        }
    }

    private fun initialDraft(step: TaskStep): Draft = when (step) {
        is TaskStep.Choice -> Draft.Chosen(optionId = null)
        is TaskStep.Distribute -> Draft.Allocated(BudgetPlan.EMPTY)
        is TaskStep.PickItems -> Draft.Picked(emptySet())
    }

    private fun answerOf(step: TaskStep, draft: Draft): StepAnswer? = when (step) {
        is TaskStep.Choice -> (draft as? Draft.Chosen)?.optionId?.let { StepAnswer.Chosen(it) }

        is TaskStep.Distribute -> (draft as? Draft.Allocated)
            ?.takeIf { it.plan.total > Coins.ZERO }
            ?.let { StepAnswer.Allocated(it.plan) }

        is TaskStep.PickItems -> (draft as? Draft.Picked)?.let { picked ->
            val ids = step.itemIds.filter { it in picked.ids }
            StepAnswer.Picked(ids, Coins(ids.sumOf { items.getValue(it).price.amount }))
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun forProfile(profile: Profile, task: LearningTask): Flow<TaskState> =
        periods.observeCurrent(profile.id).flatMapLatest { period ->
            if (period == null) {
                flowOf(TaskState.Loading)
            } else {
                combine(periods.observeTransactions(period.id), progress) { transactions, current ->
                    ready(profile, task, period, transactions, current)
                }
            }
        }

    private fun ready(
        profile: Profile,
        task: LearningTask,
        period: GamePeriod,
        transactions: List<Transaction>,
        current: Progress,
    ) = TaskState.Ready(
        id = task.id,
        topic = task.topic,
        intro = texts.textOf(task.introKey),
        appearance = profile.appearance,
        maxReward = task.outcomes.maxOf { it.reward },
        rewardAvailable = TaskSchedule.rewardAvailable(transactions, balance),
        canStart = period.status == PeriodStatus.RUNNING,
        stage = stageOf(task, current),
        submitting = current.submitting,
    )

    private fun stageOf(task: LearningTask, current: Progress): TaskStage {
        current.outcome?.let { return TaskStage.Done(it) }
        if (!current.started) return TaskStage.Intro
        val index = current.answers.size.coerceAtMost(task.steps.lastIndex)
        return TaskStage.Step(
            index = index,
            total = task.steps.size,
            step = stepView(task.steps[index], current.draft),
        )
    }

    private fun stepView(step: TaskStep, draft: Draft): StepView = when (step) {
        is TaskStep.Choice -> StepView.Choice(
            prompt = texts.textOf(step.promptKey),
            options = step.options.map { OptionView(id = it.id, label = texts.textOf(it.labelKey)) },
            chosen = (draft as? Draft.Chosen)?.optionId,
        )

        is TaskStep.Distribute -> StepView.Distribute(
            prompt = texts.textOf(step.promptKey),
            budget = step.budget,
            plan = (draft as? Draft.Allocated)?.plan ?: BudgetPlan.EMPTY,
        )

        is TaskStep.PickItems -> StepView.Pick(
            prompt = texts.textOf(step.promptKey),
            budget = step.budget,
            items = step.itemIds.mapNotNull { id ->
                items[id]?.let { PickItemView(id = it.id, title = texts.textOf(it.titleKey), price = it.price, category = it.category) }
            },
            picked = (draft as? Draft.Picked)?.ids ?: emptySet(),
        )
    }

    private companion object {
        const val STEP = 5
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
