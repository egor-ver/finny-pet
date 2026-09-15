package ru.finnypet.app.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ru.finnypet.app.data.SystemGameClock
import ru.finnypet.app.data.repository.PeriodRepositoryImpl
import ru.finnypet.app.data.repository.ProfileRepositoryImpl
import ru.finnypet.app.data.repository.SavingsRepositoryImpl
import ru.finnypet.app.data.repository.SettingsRepositoryImpl
import ru.finnypet.app.data.repository.TaskProgressRepositoryImpl
import ru.finnypet.app.domain.economy.GameClock
import ru.finnypet.app.domain.repository.PeriodRepository
import ru.finnypet.app.domain.repository.ProfileRepository
import ru.finnypet.app.domain.repository.SavingsRepository
import ru.finnypet.app.domain.repository.SettingsRepository
import ru.finnypet.app.domain.repository.TaskProgressRepository
import javax.inject.Singleton

/**
 * Связывает контракты из domain с реализациями из data.
 *
 * Направление зависимостей держится именно здесь: domain знает только свои
 * интерфейсы и ничего не знает про Room, DataStore и Android.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun profileRepository(impl: ProfileRepositoryImpl): ProfileRepository

    @Binds
    @Singleton
    abstract fun periodRepository(impl: PeriodRepositoryImpl): PeriodRepository

    @Binds
    @Singleton
    abstract fun savingsRepository(impl: SavingsRepositoryImpl): SavingsRepository

    @Binds
    @Singleton
    abstract fun taskProgressRepository(impl: TaskProgressRepositoryImpl): TaskProgressRepository

    @Binds
    @Singleton
    abstract fun settingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    abstract fun gameClock(impl: SystemGameClock): GameClock
}
