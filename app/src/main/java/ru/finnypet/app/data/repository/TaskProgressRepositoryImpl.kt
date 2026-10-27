package ru.finnypet.app.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.finnypet.app.data.local.dao.TaskProgressDao
import ru.finnypet.app.data.local.entity.TaskProgressEntity
import ru.finnypet.app.domain.economy.GameClock
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.CompletedTask
import ru.finnypet.app.domain.model.ProfileId
import ru.finnypet.app.domain.model.TaskId
import ru.finnypet.app.domain.repository.TaskProgressRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskProgressRepositoryImpl @Inject constructor(
    private val tasks: TaskProgressDao,
    private val clock: GameClock,
) : TaskProgressRepository {

    override suspend fun complete(
        profileId: ProfileId,
        taskId: TaskId,
        outcomeId: String,
        reward: Coins,
    ) {
        tasks.insert(
            TaskProgressEntity(
                profileId = profileId.value,
                taskId = taskId.value,
                outcomeId = outcomeId,
                reward = reward.amount,
                completedAt = clock.now(),
            )
        )
    }

    override suspend fun completedIds(profileId: ProfileId): Set<TaskId> =
        tasks.completedTaskIds(profileId.value).map(::TaskId).toSet()

    override fun observeCompleted(profileId: ProfileId): Flow<List<CompletedTask>> =
        tasks.observeAll(profileId.value).map { rows -> rows.map(TaskProgressEntity::toCompletedTask) }
}

private fun TaskProgressEntity.toCompletedTask() = CompletedTask(
    taskId = TaskId(taskId),
    outcomeId = outcomeId,
    reward = Coins(reward),
    completedAt = completedAt,
)
