package ru.finnypet.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import ru.finnypet.app.data.local.dao.BudgetPlanDao
import ru.finnypet.app.data.local.dao.GoalProgressDao
import ru.finnypet.app.data.local.dao.PeriodDao
import ru.finnypet.app.data.local.dao.PetStateDao
import ru.finnypet.app.data.local.dao.ProfileDao
import ru.finnypet.app.data.local.dao.TaskProgressDao
import ru.finnypet.app.data.local.dao.TransactionDao
import ru.finnypet.app.data.local.entity.BudgetPlanEntity
import ru.finnypet.app.data.local.entity.GoalProgressEntity
import ru.finnypet.app.data.local.entity.PeriodEntity
import ru.finnypet.app.data.local.entity.PetStateEntity
import ru.finnypet.app.data.local.entity.ProfileEntity
import ru.finnypet.app.data.local.entity.TaskProgressEntity
import ru.finnypet.app.data.local.entity.TransactionEntity

/**
 * Локальное хранилище состояния игрока (ТЗ 2.5.13).
 *
 * Здесь лежит только то, что меняется по ходу игры. Учебный контент — товары,
 * цели, задания, внешность питомца — приходит из JSON в ассетах и в базу не
 * попадает: ТЗ 3.2 требует отделить контент от кода, а ТЗ 2.5.14 — добавлять
 * задание без переработки логики.
 *
 * Схема выгружается в app/schemas и коммитится: без неё нельзя написать тест
 * миграции.
 */
@Database(
    entities = [
        ProfileEntity::class,
        PetStateEntity::class,
        PeriodEntity::class,
        BudgetPlanEntity::class,
        TransactionEntity::class,
        GoalProgressEntity::class,
        TaskProgressEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class FinnyDatabase : RoomDatabase() {

    abstract fun profiles(): ProfileDao

    abstract fun petStates(): PetStateDao

    abstract fun periods(): PeriodDao

    abstract fun budgetPlans(): BudgetPlanDao

    abstract fun transactions(): TransactionDao

    abstract fun goalProgress(): GoalProgressDao

    abstract fun taskProgress(): TaskProgressDao

    companion object {
        const val NAME = "finny.db"
    }
}
