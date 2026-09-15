package ru.finnypet.app.domain.economy

import ru.finnypet.app.domain.model.Change
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.Explanation
import ru.finnypet.app.domain.model.GameResult
import ru.finnypet.app.domain.model.PetEffect
import ru.finnypet.app.domain.model.RecoveryOption
import ru.finnypet.app.domain.model.ShopItem
import ru.finnypet.app.domain.model.SpendCategory
import ru.finnypet.app.domain.model.Transaction
import ru.finnypet.app.domain.model.TransactionType

sealed interface PurchaseResult {

    data class Success(
        val newBalance: Coins,
        val transaction: Transaction,
        val effects: List<PetEffect>,
        val explanation: Explanation,
    ) : PurchaseResult

    data class Rejected(
        val shortfall: Coins,
        val options: List<RecoveryOption>,
        val explanation: Explanation,
    ) : PurchaseResult
}

class WalletEngine(private val clock: GameClock) {

    fun purchase(
        item: ShopItem,
        currentBalance: Coins,
        periodId: Long,
        savings: Coins = Coins.ZERO,
    ): PurchaseResult {
        if (!currentBalance.covers(item.price)) {
            return rejected(item, currentBalance, savings)
        }
        val newBalance = currentBalance - item.price
        return PurchaseResult.Success(
            newBalance = newBalance,
            transaction = Transaction(
                id = UNSAVED,
                periodId = periodId,
                type = purchaseTypeFor(item.category),
                amount = item.price,
                reasonKey = KEY_DONE,
                createdAt = clock.now(),
                itemId = item.id,
            ),
            effects = item.effects,
            explanation = Explanation(
                key = KEY_DONE,
                args = mapOf(
                    "price" to item.price.amount.toString(),
                    "balance" to newBalance.amount.toString(),
                ),
            ),
        )
    }

    fun credit(type: TransactionType, amount: Coins, currentBalance: Coins): GameResult<Coins> {
        require(type.isIncome) { "Начисление возможно только доходным типом, получен: ${type.name}" }
        val newBalance = currentBalance + amount
        return GameResult(
            value = newBalance,
            explanation = Explanation(
                key = KEY_CREDITED,
                args = mapOf(
                    "amount" to amount.amount.toString(),
                    "source" to type.name,
                ),
            ),
            changes = listOf(Change.Balance(from = currentBalance, to = newBalance)),
        )
    }

    private fun rejected(item: ShopItem, currentBalance: Coins, savings: Coins): PurchaseResult.Rejected {
        val shortfall = currentBalance.shortfallTo(item.price)
        val options = recoveryOptions(item, shortfall, savings)
        return PurchaseResult.Rejected(
            shortfall = shortfall,
            options = options,
            explanation = Explanation(
                key = KEY_REJECTED,
                args = mapOf(
                    "shortfall" to shortfall.amount.toString(),
                    "price" to item.price.amount.toString(),
                ),
                nextStep = options.first(),
            ),
        )
    }

    private fun recoveryOptions(
        item: ShopItem,
        shortfall: Coins,
        savings: Coins,
    ): List<RecoveryOption> = buildList {
        add(RecoveryOption.DO_TASK)
        if (item.category == SpendCategory.MANDATORY && savings.covers(shortfall)) {
            add(RecoveryOption.WITHDRAW_FROM_SAVINGS)
        }
        if (item.category == SpendCategory.OPTIONAL) {
            add(RecoveryOption.POSTPONE_PURCHASE)
        }
        add(RecoveryOption.CHOOSE_CHEAPER)
    }

    private fun purchaseTypeFor(category: SpendCategory): TransactionType = when (category) {
        SpendCategory.MANDATORY -> TransactionType.PURCHASE_MANDATORY
        SpendCategory.OPTIONAL -> TransactionType.PURCHASE_OPTIONAL
        SpendCategory.SAVINGS -> error("Товар не может относиться к накоплениям, это запрещено в ShopItem")
    }

    private companion object {
        const val UNSAVED = 0L
        const val KEY_DONE = "purchase.done"
        const val KEY_REJECTED = "purchase.rejected"
        const val KEY_CREDITED = "balance.credited"
    }
}
