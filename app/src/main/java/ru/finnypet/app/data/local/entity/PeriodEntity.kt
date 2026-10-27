package ru.finnypet.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import ru.finnypet.app.domain.model.PeriodStatus

/**
 * Игровой период.
 *
 * [id] выдаёт база. Период обязан быть вставлен до создания первой операции:
 * PeriodEngine.close() требует, чтобы все транзакции несли его настоящий id,
 * а openNext() отдаёт заготовку с нулём.
 */
@Entity(
    tableName = "periods",
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    // Составной индекс покрывает и выборки по одному profileId: SQLite умеет
    // пользоваться левым префиксом, поэтому отдельный индекс не нужен.
    indices = [Index(value = ["profileId", "number"], unique = true)],
)
data class PeriodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: String,
    val number: Int,
    val income: Int,
    val startBalance: Int,
    val status: PeriodStatus,
    val closedAt: Long?,
)
