package ru.finnypet.app.domain.usecase

import ru.finnypet.app.domain.economy.CreditOutcome
import ru.finnypet.app.domain.economy.GameBalance
import ru.finnypet.app.domain.economy.WalletEngine
import ru.finnypet.app.domain.model.GameResult
import ru.finnypet.app.domain.model.ProfileId
import ru.finnypet.app.domain.model.TransactionType
import ru.finnypet.app.domain.repository.PeriodRepository

/**
 * Взрослый начисляет ребёнку монеты за дело вне игры (ТЗ 2.5.12).
 *
 * Правило начисления ТЗ оставляет команде: у нас это один бонус в игровой
 * день фиксированного размера из `balance.json`. Признак «сегодня уже
 * начислено» не хранится отдельно — он виден в операциях дня, как и всё
 * остальное про деньги.
 *
 * Возвращает `null`, когда начислять нечего: дня нет или бонус этого дня уже
 * выдан. Это не ошибка — раздел просто не покажет кнопку.
 */
class AwardParentBonus(
    private val periods: PeriodRepository,
    private val wallet: WalletEngine,
    private val balance: GameBalance,
) {

    suspend operator fun invoke(profileId: ProfileId): GameResult<CreditOutcome>? {
        val period = periods.current(profileId) ?: return null
        val transactions = periods.transactions(period.id)
        if (transactions.any { it.type == TransactionType.INCOME_PARENT }) return null

        val credited = wallet.credit(
            type = TransactionType.INCOME_PARENT,
            amount = balance.parentBonus,
            currentBalance = periods.balance(period),
            periodId = period.id,
        )
        periods.addTransaction(credited.value.transaction)
        return credited
    }
}
