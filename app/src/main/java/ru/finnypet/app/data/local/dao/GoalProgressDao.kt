package ru.finnypet.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import ru.finnypet.app.data.local.entity.GoalProgressEntity

@Dao
interface GoalProgressDao {

    @Upsert
    suspend fun upsert(progress: GoalProgressEntity)

    @Query("SELECT * FROM goal_progress WHERE profileId = :profileId AND goalId = :goalId")
    suspend fun byGoal(profileId: String, goalId: String): GoalProgressEntity?

    @Query("SELECT * FROM goal_progress WHERE profileId = :profileId AND isActive = 1 LIMIT 1")
    suspend fun active(profileId: String): GoalProgressEntity?

    @Query("SELECT * FROM goal_progress WHERE profileId = :profileId AND isActive = 1 LIMIT 1")
    fun observeActive(profileId: String): Flow<GoalProgressEntity?>

    @Query("SELECT * FROM goal_progress WHERE profileId = :profileId")
    suspend fun all(profileId: String): List<GoalProgressEntity>

    /**
     * Смена активной цели целиком: снять флаг со всех и поставить одной.
     *
     * Обе операции идут в одной транзакции — иначе прерывание между ними
     * оставило бы профиль либо с двумя активными целями, либо без единой,
     * а на главном экране цель обязана быть ровно одна (ТЗ 2.5.3).
     */
    @Transaction
    suspend fun setActive(progress: GoalProgressEntity) {
        clearActive(progress.profileId)
        upsert(progress.copy(isActive = true))
    }

    @Query("UPDATE goal_progress SET isActive = 0 WHERE profileId = :profileId")
    suspend fun clearActive(profileId: String)
}
