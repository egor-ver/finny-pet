package ru.finnypet.app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ru.finnypet.app.domain.economy.BudgetEngine
import ru.finnypet.app.domain.economy.GameBalance
import ru.finnypet.app.domain.economy.GameClock
import ru.finnypet.app.domain.economy.GrowthEngine
import ru.finnypet.app.domain.economy.PeriodEngine
import ru.finnypet.app.domain.economy.PetStateEngine
import ru.finnypet.app.domain.economy.SavingsEngine
import ru.finnypet.app.domain.economy.TaskEngine
import ru.finnypet.app.domain.economy.WalletEngine
import ru.finnypet.app.domain.repository.ContentRepository
import javax.inject.Singleton

/**
 * Движки экономики и числа, на которых они считают.
 *
 * Движки без состояния, поэтому областью видимости не ограничиваются: создать
 * их заново дешевле, чем держать. Синглтон только у GameBalance — числа обязаны
 * быть одни и те же во всём приложении.
 */
@Module
@InstallIn(SingletonComponent::class)
object DomainModule {

    /**
     * Числа приходят из balance.json — правка экономики не требует пересборки
     * логики и не идёт через код (ТЗ 3.2).
     */
    @Provides
    @Singleton
    fun gameBalance(content: ContentRepository): GameBalance = content.pack().balance

    @Provides
    fun budgetEngine(): BudgetEngine = BudgetEngine()

    @Provides
    fun petStateEngine(balance: GameBalance): PetStateEngine = PetStateEngine(balance)

    @Provides
    fun growthEngine(balance: GameBalance): GrowthEngine = GrowthEngine(balance)

    @Provides
    fun walletEngine(clock: GameClock): WalletEngine = WalletEngine(clock)

    @Provides
    fun savingsEngine(clock: GameClock): SavingsEngine = SavingsEngine(clock)

    @Provides
    fun taskEngine(clock: GameClock): TaskEngine = TaskEngine(clock)

    @Provides
    fun periodEngine(
        budget: BudgetEngine,
        pet: PetStateEngine,
        growth: GrowthEngine,
        balance: GameBalance,
        clock: GameClock,
    ): PeriodEngine = PeriodEngine(
        budget = budget,
        pet = pet,
        growth = growth,
        balance = balance,
        clock = clock,
    )
}
