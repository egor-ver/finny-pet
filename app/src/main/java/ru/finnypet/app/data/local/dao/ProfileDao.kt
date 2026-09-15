package ru.finnypet.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import ru.finnypet.app.data.local.entity.ProfileEntity

@Dao
interface ProfileDao {

    @Upsert
    suspend fun upsert(profile: ProfileEntity)

    @Query("SELECT * FROM profiles WHERE id = :id")
    suspend fun byId(id: String): ProfileEntity?

    @Query("SELECT * FROM profiles ORDER BY createdAt DESC LIMIT 1")
    suspend fun current(): ProfileEntity?

    @Query("SELECT * FROM profiles ORDER BY createdAt DESC LIMIT 1")
    fun observeCurrent(): Flow<ProfileEntity?>

    /** Удаление профиля взрослым (ТЗ 3.5). Каскад уносит всё связанное состояние. */
    @Query("DELETE FROM profiles WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM profiles")
    suspend fun deleteAll()
}
