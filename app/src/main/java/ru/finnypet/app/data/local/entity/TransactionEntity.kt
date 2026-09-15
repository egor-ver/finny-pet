package ru.finnypet.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import ru.finnypet.app.domain.model.TransactionType

/**
 * Денежная операция — единственный источник правды о балансе.
 *
 * Колонки «сколько денег» нет нигде: баланс выводится сложением операций
 * через Transaction.balanceDelta. Счётчик рядом с историей неизбежно
 * разошёлся бы с ней, а ТЗ 2.5.4 требует объяснимости каждого изменения.
 */
@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = PeriodEntity::class,
            parentColumns = ["id"],
            childColumns = ["periodId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("periodId"), Index("goalId")],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val periodId: Long,
    val type: TransactionType,
    val amount: Int,
    val reasonKey: String,
    val createdAt: Long,
    val itemId: String?,
    val goalId: String?,
)
