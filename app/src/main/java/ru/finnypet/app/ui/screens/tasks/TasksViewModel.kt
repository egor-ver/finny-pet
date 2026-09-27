package ru.finnypet.app.ui.screens.tasks

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import ru.finnypet.app.domain.economy.GameBalance
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.CompletedTask
import ru.finnypet.app.domain.model.LearningTask
import ru.finnypet.app.domain.model.Pet
import ru.finnypet.app.domain.model.PetMood
import ru.finnypet.app.domain.model.Profile
import ru.finnypet.app.domain.model.TaskId
import ru.finnypet.app.domain.model.TaskTopic
import ru.finnypet.app.domain.model.Transaction
import ru.finnypet.app.domain.repository.ContentRepository
import ru.finnypet.app.domain.repository.PeriodRepository
import ru.finnypet.app.domain.repository.ProfileRepository
import ru.finnypet.app.domain.repository.TaskProgressRepository
import ru.finnypet.app.domain.usecase.OpenPeriodIfNeeded
import ru.finnypet.app.domain.usecase.TaskSchedule
import ru.finnypet.app.ui.components.OwlLook
import ru.finnypet.app.ui.components.owlDescription
import ru.finnypet.app.ui.components.owlLook
import ru.finnypet.app.ui.screens.ProfileViewModel
import ru.finnypet.app.ui.text.textOf
import javax.inject.Inject

/**
 * Задание в списке: тема, начало вступления, пройдено ли и сколько за него
 * заплатят сегодня. [reward] `null` — сегодня без монет (лимит дня выбран
 * или задание уже пробовали сегодня, R8).
 */
data class TaskRow(
    val id: TaskId,
    val topic: TaskTopic,
    val intro: String,
    val completed: Boolean,
    val reward: Coins? = null,
) {

    /**
     * Что стоит справа на карточке (DESIGN_PLAN 3.9). Пройденное — отметка,
     * а если сегодня за него снова заплатят, ещё и награда рядом: иначе
     * шапка обещает «Сегодня: +10», а ни одна карточка не говорит, где их взять.
     */
    val badge: TaskBadge
        get() = when {
            completed -> TaskBadge.Passed(reward)
            reward != null -> TaskBadge.Reward(reward)
            else -> TaskBadge.Training
        }
}

sealed interface TaskBadge {
    /** [reward] `null` — сегодня за повтор не платят. */
    data class Passed(val reward: Coins?) : TaskBadge
    data class Reward(val coins: Coins) : TaskBadge
    data object Training : TaskBadge
}

data class TaskGroup(
    val topic: TaskTopic,
    val tasks: List<TaskRow>,
) {
    val passed: Int get() = tasks.count { it.completed }
}

sealed interface TasksState {

    data object Loading : TasksState

    data object Failed : TasksState

    data class Ready(
        /** Группы в порядке тем ТЗ 2.5.8; пустые темы пропущены. */
        val groups: List<TaskGroup>,
        /** Сова в карточке прогресса — тот же питомец, что на главном. */
        val owl: OwlLook,
        /** Остался ли на сегодня лимит наград. */
        val rewardAvailable: Boolean,
        /** Награда за задание сегодня — чип «Сегодня: +10», как на главном. */
        val reward: Coins,
    ) : TasksState {

        /** «Пройдено 2 из 6» — по всем темам сразу. */
        val passed: Int get() = groups.sumOf { it.passed }
        val total: Int get() = groups.sumOf { it.tasks.size }
    }
}

/**
 * Список заданий (ТЗ 2.5.8): все доступны сразу, пройденные помечены и
 * открываются снова. Здесь только чтение: прохождение — в [TaskViewModel].
 */
@HiltViewModel
class TasksViewModel @Inject constructor(
    profiles: ProfileRepository,
    private val periods: PeriodRepository,
    private val progress: TaskProgressRepository,
    private val openPeriod: OpenPeriodIfNeeded,
    private val balance: GameBalance,
    content: ContentRepository,
) : ProfileViewModel(profiles) {

    private val allTasks: List<LearningTask> = content.pack().tasks
    private val tasks: List<LearningTask> = TaskSchedule.listed(allTasks)
    private val texts: Map<String, String> = content.pack().texts
    private val pets = content.pack().pets

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<TasksState> =
        combine(profiles.observeActive(), failed) { profile, isFailed -> profile to isFailed }
            .flatMapLatest { (profile, isFailed) ->
                when {
                    isFailed -> flowOf(TasksState.Failed)
                    profile == null -> flowOf(TasksState.Loading)
                    else -> forProfile(profile)
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = TasksState.Loading,
            )

    init {
        act { openPeriod(it) }
    }

    fun retry() = retryWith { openPeriod(it) }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun forProfile(profile: Profile): Flow<TasksState> =
        combine(profiles.observePet(profile.id), periods.observeCurrent(profile.id)) { pet, period -> pet to period }
            .flatMapLatest { (pet, period) ->
                if (pet == null || period == null) {
                    flowOf(TasksState.Loading)
                } else {
                    combine(
                        periods.observeTransactions(period.id),
                        progress.observeCompleted(profile.id),
                    ) { transactions, completed -> ready(profile, pet, transactions, completed) }
                }
            }

    private fun ready(
        profile: Profile,
        pet: Pet,
        transactions: List<Transaction>,
        completed: List<CompletedTask>,
    ): TasksState.Ready {
        val done = TaskSchedule.passed(allTasks, completed)
        return TasksState.Ready(
            groups = TaskTopic.entries.mapNotNull { topic ->
                val rows = tasks.filter { it.topic == topic }.map { task ->
                    TaskRow(
                        id = task.id,
                        topic = topic,
                        intro = texts.textOf(task.introKey),
                        completed = task.id in done,
                        reward = task.maxReward.takeIf {
                            TaskSchedule.rewardable(task.id, completed, transactions, balance)
                        },
                    )
                }
                if (rows.isEmpty()) null else TaskGroup(topic = topic, tasks = rows)
            },
            // Спокойная, как на вступлении задания: список — не повод для
            // эмоций, а грусть по еде ребёнок и так видит на главном.
            owl = owlLook(
                pets = pets,
                appearance = profile.appearance,
                stage = pet.growth.stage,
                mood = PetMood.CALM,
                description = owlDescription(texts, profile.petName, PetMood.CALM, sadAbout = null),
            ),
            rewardAvailable = TaskSchedule.rewardAvailable(transactions, balance),
            reward = balance.taskReward,
        )
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
