package ru.finnypet.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import ru.finnypet.app.data.local.entity.BudgetPlanEntity

@Dao
interface BudgetPlanDao {

    /** План можно менять до подтверждения (ТЗ 2.5.5), поэтому upsert. */
    @Upsert
    suspend fun upsert(plan: BudgetPlanEntity)

    @Query("SELECT * FROM budget_plans WHERE periodId = :periodId")
    suspend fun byPeriod(periodId: Long): BudgetPlanEntity?

    @Query("SELECT * FROM budget_plans WHERE periodId = :periodId")
    fun observe(periodId: Long): Flow<BudgetPlanEntity?>
}
