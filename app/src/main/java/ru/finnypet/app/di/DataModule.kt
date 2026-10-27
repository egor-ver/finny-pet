package ru.finnypet.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import ru.finnypet.app.data.local.FinnyDatabase
import ru.finnypet.app.data.local.dao.BudgetPlanDao
import ru.finnypet.app.data.local.dao.GoalProgressDao
import ru.finnypet.app.data.local.dao.PeriodDao
import ru.finnypet.app.data.local.dao.PetStateDao
import ru.finnypet.app.data.local.dao.ProfileDao
import ru.finnypet.app.data.local.dao.TaskProgressDao
import ru.finnypet.app.data.local.dao.TransactionDao
import javax.inject.Singleton

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(
    name = "finny_settings",
)

/**
 * База и хранилище настроек. Оба — синглтоны: два экземпляра Room поверх
 * одного файла дают несогласованные кэши, а два DataStore — исключение.
 */
@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): FinnyDatabase =
        Room.databaseBuilder(context, FinnyDatabase::class.java, FinnyDatabase.NAME)
            // Прототип живёт до защиты, миграций между версиями схемы не пишем:
            // при смене версии база пересоздаётся. Записано в ограничения —
            // установка новой сборки поверх старой стирает прогресс.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    @Singleton
    fun settingsStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.settingsStore

    @Provides
    fun profileDao(database: FinnyDatabase): ProfileDao = database.profiles()

    @Provides
    fun petStateDao(database: FinnyDatabase): PetStateDao = database.petStates()

    @Provides
    fun periodDao(database: FinnyDatabase): PeriodDao = database.periods()

    @Provides
    fun budgetPlanDao(database: FinnyDatabase): BudgetPlanDao = database.budgetPlans()

    @Provides
    fun transactionDao(database: FinnyDatabase): TransactionDao = database.transactions()

    @Provides
    fun goalProgressDao(database: FinnyDatabase): GoalProgressDao = database.goalProgress()

    @Provides
    fun taskProgressDao(database: FinnyDatabase): TaskProgressDao = database.taskProgress()
}
