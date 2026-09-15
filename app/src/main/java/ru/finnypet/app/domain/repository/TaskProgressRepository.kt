package ru.finnypet.app.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.CompletedTask
import ru.finnypet.app.domain.model.ProfileId
import ru.finnypet.app.domain.model.TaskId

/**
 * Учебный прогресс: какие задания ребёнок уже прошёл (ТЗ 2.5.11).
 */
interface TaskProgressRepository {

    suspend fun complete(
        profileId: ProfileId,
        taskId: TaskId,
        outcomeId: String,
        reward: Coins,
    )

    /** Идентификаторы пройденных заданий без повторов. */
    suspend fun completedIds(profileId: ProfileId): Set<TaskId>

    fun observeCompleted(profileId: ProfileId): Flow<List<CompletedTask>>
}
