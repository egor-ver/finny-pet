package ru.finnypet.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import ru.finnypet.app.domain.model.GrowthStage

/**
 * Состояние и рост питомца в одной строке на профиль.
 *
 * ТЗ разводит два понятия: состояние обратимо и меняется после отдельного
 * решения, стадия развития — по совокупности решений за несколько периодов.
 * Хранятся вместе, потому что у обоих ровно один владелец — профиль.
 */
@Entity(
    tableName = "pet_states",
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
)
data class PetStateEntity(
    @PrimaryKey val profileId: String,
    val mood: Int,
    val satiety: Int,
    val care: Int,
    val growthPoints: Int,
    val stage: GrowthStage,
)
