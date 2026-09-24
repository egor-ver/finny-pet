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

    /**
     * Желаемое сверх остатка по плану (R4). Выхода вроде «взять из копилки»
     * нет намеренно: необязательная покупка переносится на завтра без
     * наказания, а не добывается любой ценой (AD-4).
     */
    data class NotInPlan(
        val left: Coins,
        val explanation: Explanation,
    ) : PurchaseResult
}

data class CreditOutcome(
    val balance: Coins,
    val transaction: Transaction,
)

class WalletEngine(private val clock: GameClock) {

    /**
     * [optionalLeft] — сколько по плану ещё можно потратить на желаемое.
     * Лимит проверяется раньше денег: полный кошелёк не делает мячик сверх
     * плана доступным. Нужное планом не ограничено никогда (AD-4).
     *
     * [taskRewardAvailable] — есть ли сегодня ещё задание с монетами: когда
     * лимит дня выбран, «выполнить задание» не предлагается — это было бы
     * обещание без денег.
     */
    fun purchase(
        item: ShopItem,
        currentBalance: Coins,
        periodId: Long,
        optionalLeft: Coins,
        savings: Coins = Coins.ZERO,
        taskRewardAvailable: Boolean = true,
    ): PurchaseResult {
        if (item.category == SpendCategory.OPTIONAL && !optionalLeft.covers(item.price)) {
            return PurchaseResult.NotInPlan(
                left = optionalLeft,
                explanation = Explanation(
                    key = KEY_NOT_IN_PLAN,
                    args = mapOf("left" to optionalLeft.amount.toString()),
                ),
            )
        }
        if (!currentBalance.covers(item.price)) {
            return rejected(item, currentBalance, savings, taskRewardAvailable)
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

    /**
     * Начисляет доход и порождает транзакцию: баланс считается только по ним,
     * поэтому доход обязан попасть в историю, а не остаться голым числом.
     *
     * [TransactionType.SAVINGS_WITHDRAW] сюда не принимается, хотя и помечен доходом:
     * снятие меняет ещё и копилку, и живёт в [SavingsEngine.withdraw].
     */
    fun credit(
        type: TransactionType,
        amount: Coins,
        currentBalance: Coins,
        periodId: Long,
    ): GameResult<CreditOutcome> {
        require(type in CREDITABLE) {
            "Начислить можно доход периода, награду за задание или бонус родителя, " +
                "получен: ${type.name}"
        }
        require(amount > Coins.ZERO) { "Начисление нуля не имеет смысла" }

        val newBalance = currentBalance + amount
        return GameResult(
            value = CreditOutcome(
                balance = newBalance,
                transaction = Transaction(
                    id = UNSAVED,
                    periodId = periodId,
                    type = type,
                    amount = amount,
                    reasonKey = KEY_CREDITED,
                    createdAt = clock.now(),
                ),
            ),
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

    private fun rejected(
        item: ShopItem,
        currentBalance: Coins,
        savings: Coins,
        taskRewardAvailable: Boolean,
    ): PurchaseResult.Rejected {
        val shortfall = currentBalance.shortfallTo(item.price)
        val options = recoveryOptions(item, shortfall, savings, taskRewardAvailable)
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
        taskRewardAvailable: Boolean,
    ): List<RecoveryOption> = buildList {
        if (taskRewardAvailable) add(RecoveryOption.DO_TASK)
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
        val CREDITABLE = setOf(
            TransactionType.INCOME_PERIOD,
            TransactionType.INCOME_TASK,
            TransactionType.INCOME_PARENT,
        )
        const val KEY_DONE = "purchase.done"
        const val KEY_REJECTED = "purchase.rejected"
        const val KEY_NOT_IN_PLAN = "purchase.not_in_plan"
        const val KEY_CREDITED = "balance.credited"
    }
}
