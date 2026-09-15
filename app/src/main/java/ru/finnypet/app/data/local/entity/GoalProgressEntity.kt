package ru.finnypet.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Прогресс по финансовой цели (ТЗ 2.5.7).
 *
 * goalId ссылается на каталог целей из контент-пака, который живёт в assets,
 * а не в базе, поэтому внешнего ключа здесь нет — только строка.
 */
@Entity(
    tableName = "goal_progress",
    primaryKeys = ["profileId", "goalId"],
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("profileId")],
)
data class GoalProgressEntity(
    val profileId: String,
    val goalId: String,
    val saved: Int,
    val isActive: Boolean,
)
