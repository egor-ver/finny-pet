package ru.finnypet.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * План на период: распределение по трём направлениям (ТЗ 2.5.5).
 * Один план на период, поэтому periodId и есть первичный ключ.
 */
@Entity(
    tableName = "budget_plans",
    foreignKeys = [
        ForeignKey(
            entity = PeriodEntity::class,
            parentColumns = ["id"],
            childColumns = ["periodId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
)
data class BudgetPlanEntity(
    @PrimaryKey val periodId: Long,
    val mandatory: Int,
    val optional: Int,
    val savings: Int,
)
