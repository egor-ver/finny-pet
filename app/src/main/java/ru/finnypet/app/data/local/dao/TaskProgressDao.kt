package ru.finnypet.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import ru.finnypet.app.data.local.entity.TaskProgressEntity

@Dao
interface TaskProgressDao {

    @Insert
    suspend fun insert(progress: TaskProgressEntity): Long

    @Query("SELECT * FROM task_progress WHERE profileId = :profileId ORDER BY completedAt DESC")
    suspend fun all(profileId: String): List<TaskProgressEntity>

    @Query("SELECT * FROM task_progress WHERE profileId = :profileId ORDER BY completedAt DESC")
    fun observeAll(profileId: String): Flow<List<TaskProgressEntity>>

    /** Пройденные темы для раздела прогресса: повторные проходы не дублируются. */
    @Query("SELECT DISTINCT taskId FROM task_progress WHERE profileId = :profileId")
    suspend fun completedTaskIds(profileId: String): List<String>
}
