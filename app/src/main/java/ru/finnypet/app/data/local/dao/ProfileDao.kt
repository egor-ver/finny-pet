package ru.finnypet.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import ru.finnypet.app.data.local.entity.ProfileEntity

/**
 * Понятия «текущий профиль» здесь нет намеренно.
 *
 * Выбор активного профиля — состояние сессии, оно живёт в настройках и
 * переключается явно. Эвристика «самый свежесозданный» заперла бы ребёнка
 * в тестовом профиле после первого же входа в демонстрационный режим.
 */
@Dao
interface ProfileDao {

    @Upsert
    suspend fun upsert(profile: ProfileEntity)

    @Query("SELECT * FROM profiles WHERE id = :id")
    suspend fun byId(id: String): ProfileEntity?

    @Query("SELECT * FROM profiles WHERE id = :id")
    fun observeById(id: String): Flow<ProfileEntity?>

    @Query("SELECT * FROM profiles ORDER BY createdAt")
    suspend fun all(): List<ProfileEntity>

    /** Профиль демонстрационного режима. Он один: StartDemo сносит прежний. */
    @Query("SELECT * FROM profiles WHERE isTest = 1 LIMIT 1")
    suspend fun testProfile(): ProfileEntity?

    /** Удаление профиля взрослым (ТЗ 3.5). Каскад уносит всё связанное состояние. */
    @Query("DELETE FROM profiles WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM profiles")
    suspend fun deleteAll()
}
