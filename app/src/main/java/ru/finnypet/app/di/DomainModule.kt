package ru.finnypet.app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ru.finnypet.app.domain.economy.GameBalance
import ru.finnypet.app.domain.economy.GameClock
import ru.finnypet.app.domain.economy.WalletEngine
import ru.finnypet.app.domain.repository.ContentRepository
import ru.finnypet.app.domain.repository.OutcomeRecorder
import ru.finnypet.app.domain.repository.PeriodRepository
import ru.finnypet.app.domain.usecase.OpenPeriodIfNeeded
import javax.inject.Singleton

/**
 * Движки экономики и сценарии Hilt собирает сам по `@Inject constructor`.
 * Здесь только то, что по конструктору не собрать.
 */
@Module
@InstallIn(SingletonComponent::class)
object DomainModule {

    /**
     * Числа приходят из balance.json: правка экономики не требует пересборки
     * логики и не идёт через код. Одни и те же числа во всём приложении.
     */
    @Provides
    @Singleton
    fun gameBalance(content: ContentRepository): GameBalance = content.pack().balance

    /**
     * Один на приложение: его замок не даёт начислить доход дня дважды.
     * События дня берутся из контент-пака, а не из самого репозитория, поэтому
     * по конструктору он не собирается.
     */
    @Provides
    @Singleton
    fun openPeriodIfNeeded(
        periods: PeriodRepository,
        wallet: WalletEngine,
        balance: GameBalance,
        content: ContentRepository,
        recorder: OutcomeRecorder,
        clock: GameClock,
    ): OpenPeriodIfNeeded = OpenPeriodIfNeeded(
        periods = periods,
        wallet = wallet,
        balance = balance,
        events = content.pack().events,
        recorder = recorder,
        clock = clock,
    )
}
