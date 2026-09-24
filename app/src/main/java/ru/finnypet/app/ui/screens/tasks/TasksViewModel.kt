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
import ru.finnypet.app.domain.model.LearningTask
import ru.finnypet.app.domain.model.ProfileId
import ru.finnypet.app.domain.model.TaskId
import ru.finnypet.app.domain.model.TaskTopic
import ru.finnypet.app.domain.repository.ContentRepository
import ru.finnypet.app.domain.repository.PeriodRepository
import ru.finnypet.app.domain.repository.ProfileRepository
import ru.finnypet.app.ui.screens.ProfileViewModel
import ru.finnypet.app.domain.repository.TaskProgressRepository
import ru.finnypet.app.domain.usecase.OpenPeriodIfNeeded
import ru.finnypet.app.domain.usecase.TaskSchedule
import ru.finnypet.app.ui.text.textOf
import javax.inject.Inject

/** Задание в списке: тема, начало вступления и было ли пройдено. */
data class TaskRow(
    val id: TaskId,
    val topic: TaskTopic,
    val intro: String,
    val completed: Boolean,
)

data class TaskGroup(
    val topic: TaskTopic,
    val tasks: List<TaskRow>,
)

sealed interface TasksState {

    data object Loading : TasksState

    data object Failed : TasksState

    data class Ready(
        /** Группы в порядке тем ТЗ 2.5.8; пустые темы пропущены. */
        val groups: List<TaskGroup>,
        /** Остался ли на сегодня лимит наград. */
        val rewardAvailable: Boolean,
        /** Сколько заданий в день приносят монеты — из чисел экономики, не из строки. */
        val rewardLimit: Int,
    ) : TasksState
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

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<TasksState> =
        combine(profiles.observeActive(), failed) { profile, isFailed -> profile to isFailed }
            .flatMapLatest { (profile, isFailed) ->
                when {
                    isFailed -> flowOf(TasksState.Failed)
                    profile == null -> flowOf(TasksState.Loading)
                    else -> forProfile(profile.id)
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
    private fun forProfile(profileId: ProfileId): Flow<TasksState> =
        periods.observeCurrent(profileId).flatMapLatest { period ->
            if (period == null) {
                flowOf(TasksState.Loading)
            } else {
                combine(
                    periods.observeTransactions(period.id),
                    progress.observeCompleted(profileId),
                ) { transactions, completed ->
                    val done = TaskSchedule.passed(allTasks, completed)
                    TasksState.Ready(
                        groups = TaskTopic.entries.mapNotNull { topic ->
                            val rows = tasks.filter { it.topic == topic }.map { task ->
                                TaskRow(
                                    id = task.id,
                                    topic = topic,
                                    intro = texts.textOf(task.introKey),
                                    completed = task.id in done,
                                )
                            }
                            if (rows.isEmpty()) null else TaskGroup(topic = topic, tasks = rows)
                        },
                        rewardAvailable = TaskSchedule.rewardAvailable(transactions, balance),
                        rewardLimit = balance.rewardedTasksPerPeriod,
                    )
                }
            }
        }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
