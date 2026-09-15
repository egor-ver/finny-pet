package ru.finnypet.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Завершённое задание (ТЗ 2.5.11 — «пользователь видит завершенные задания»).
 *
 * Строка на каждое прохождение, а не на задание: повторный проход не затирает
 * прежний результат, а список пройденных тем получается через DISTINCT.
 */
@Entity(
    tableName = "task_progress",
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
data class TaskProgressEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: String,
    val taskId: String,
    val outcomeId: String,
    val reward: Int,
    val completedAt: Long,
)
