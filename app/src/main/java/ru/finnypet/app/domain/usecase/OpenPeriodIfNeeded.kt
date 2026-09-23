package ru.finnypet.app.domain.usecase

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.finnypet.app.domain.economy.GameBalance
import ru.finnypet.app.domain.economy.WalletEngine
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.GamePeriod
import ru.finnypet.app.domain.model.PeriodStatus
import ru.finnypet.app.domain.model.ProfileId
import ru.finnypet.app.domain.model.TransactionType
import ru.finnypet.app.domain.repository.PeriodRepository
import kotlin.coroutines.cancellation.CancellationException

/**
 * Отдаёт период, в котором сейчас идёт игра: открывает самый первый, если игра
 * только началась, и следит, чтобы доход периода был начислен.
 *
 * Баланс считается от периода: стартовые деньги лежат в нём, а доход приходит
 * отдельной операцией. Поэтому без периода главному экрану нечего показать,
 * а сразу после создания питомца периодов ещё нет — PeriodEngine.openNext()
 * умеет открывать только следующий, ему нужен предыдущий.
 *
 * Числа берутся из контент-пака: правка экономики не идёт через код (ТЗ 3.2).
 */
class OpenPeriodIfNeeded(
    private val periods: PeriodRepository,
    private val wallet: WalletEngine,
    private val balance: GameBalance,
) {

    /**
     * Экраны зовут это каждый из своего init, и вызовы могут прийти разом.
     * «Проверил, что дохода нет, — начислил» без замка начислило бы дважды.
     * Замок работает, пока экземпляр один на приложение, — см. DomainModule.
     */
    private val opening = Mutex()

    suspend operator fun invoke(profileId: ProfileId): GamePeriod = opening.withLock {
        val period = periods.current(profileId) ?: openFirst(profileId)
        creditIncome(period)
        period
    }

    private suspend fun openFirst(profileId: ProfileId): GamePeriod {
        // Закрытие периода обязано открывать следующий тем же действием: иначе
        // остаток неистраченных монет не переносится и просто пропадает.
        // Молчаливый первый период вместо потерянного скрыл бы эту ошибку.
        check(periods.lastClosed(profileId) == null) {
            "У профиля ${profileId.value} нет открытого периода, но закрытые есть: " +
                "период закрыли, не открыв следующий, и остаток потерян"
        }

        return try {
            periods.open(firstPeriod(profileId))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            // Между проверкой и вставкой период мог открыть кто-то ещё —
            // например, сброс демо-режима при уже открытом главном экране.
            // Уникальность номера внутри профиля не даст завести дубль, и это
            // не повод падать: нужный период к этому моменту уже есть.
            periods.current(profileId) ?: throw error
        }
    }

    /**
     * ТЗ 2.5.5 требует, чтобы ребёнок распределял доступную сумму — а это
     * стартовый остаток вместе с доходом периода. Значит доход должен лежать
     * на балансе до планирования, а не появляться потом.
     *
     * Проверка «не начислен ли уже» нужна не для красоты: между открытием
     * периода и записью операции приложение может закрыться, и тогда период
     * остался бы без дохода навсегда. Здесь это чинится при следующем входе.
     */
    private suspend fun creditIncome(period: GamePeriod) {
        if (period.income == Coins.ZERO) return
        if (periods.transactions(period.id).any { it.type == TransactionType.INCOME_PERIOD }) return

        val credited = wallet.credit(
            type = TransactionType.INCOME_PERIOD,
            amount = period.income,
            currentBalance = periods.balance(period),
            periodId = period.id,
        )
        periods.addTransaction(credited.value.transaction)
    }

    private fun firstPeriod(profileId: ProfileId) = GamePeriod(
        id = UNSAVED,
        profileId = profileId,
        number = FIRST_NUMBER,
        income = balance.periodIncome,
        startBalance = balance.startingBalance,
        status = PeriodStatus.PLANNING,
    )

    private companion object {
        const val UNSAVED = 0L
        const val FIRST_NUMBER = 1
    }
}
