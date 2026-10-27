package ru.finnypet.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import ru.finnypet.app.data.local.entity.PetStateEntity

@Dao
interface PetStateDao {

    @Upsert
    suspend fun upsert(state: PetStateEntity)

    @Query("SELECT * FROM pet_states WHERE profileId = :profileId")
    suspend fun byProfile(profileId: String): PetStateEntity?

    @Query("SELECT * FROM pet_states WHERE profileId = :profileId")
    fun observe(profileId: String): Flow<PetStateEntity?>
}
