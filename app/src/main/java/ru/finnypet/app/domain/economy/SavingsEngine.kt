package ru.finnypet.app.domain.economy

import ru.finnypet.app.domain.model.Change
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.Explanation
import ru.finnypet.app.domain.model.GameResult
import ru.finnypet.app.domain.model.Goal
import ru.finnypet.app.domain.model.GoalProgress
import ru.finnypet.app.domain.model.Transaction
import ru.finnypet.app.domain.model.TransactionType

data class WithdrawPreview(
    val savingsBefore: Coins,
    val savingsAfter: Coins,
    val periodsBefore: Int?,
    val periodsAfter: Int?,
)

data class SavingsOutcome(
    val progress: GoalProgress,
    val balance: Coins,
    val transaction: Transaction,
    val goalReached: Boolean,
)

class SavingsEngine(private val clock: GameClock) {

    fun deposit(
        amount: Coins,
        currentBalance: Coins,
        progress: GoalProgress,
        goal: Goal,
        periodId: Long,
    ): GameResult<SavingsOutcome> {
        require(amount > Coins.ZERO) { "Пополнение на ноль не имеет смысла" }
        require(currentBalance.covers(amount)) { "Нельзя отложить больше, чем есть на балансе" }

        val newProgress = progress.copy(saved = progress.saved + amount)
        val newBalance = currentBalance - amount
        val reached = newProgress.isReached(goal)
        val key = if (reached) KEY_REACHED else KEY_DEPOSITED

        return GameResult(
            value = SavingsOutcome(
                progress = newProgress,
                balance = newBalance,
                transaction = transaction(TransactionType.SAVINGS_DEPOSIT, amount, key, goal, periodId),
                goalReached = reached,
            ),
            explanation = Explanation(
                key = key,
                args = mapOf(
                    "amount" to amount.amount.toString(),
                    "saved" to newProgress.saved.amount.toString(),
                    "remaining" to newProgress.remaining(goal).amount.toString(),
                ),
            ),
            changes = listOf(
                Change.Balance(from = currentBalance, to = newBalance),
                Change.Savings(from = progress.saved, to = newProgress.saved),
            ),
        )
    }

    fun previewWithdraw(
        amount: Coins,
        progress: GoalProgress,
        goal: Goal,
        avgDeposit: Coins,
    ): WithdrawPreview {
        require(amount > Coins.ZERO) { "Превью снятия нуля не имеет смысла" }
        require(progress.saved.covers(amount)) { "Нельзя снять больше, чем накоплено" }
        val after = progress.copy(saved = progress.saved - amount)
        return WithdrawPreview(
            savingsBefore = progress.saved,
            savingsAfter = after.saved,
            periodsBefore = periodsToGoal(progress, goal, avgDeposit),
            periodsAfter = periodsToGoal(after, goal, avgDeposit),
        )
    }

    fun withdraw(
        amount: Coins,
        currentBalance: Coins,
        progress: GoalProgress,
        goal: Goal,
        periodId: Long,
    ): GameResult<SavingsOutcome> {
        require(amount > Coins.ZERO) { "Снятие на ноль не имеет смысла" }
        require(progress.saved.covers(amount)) { "Нельзя снять больше, чем накоплено" }

        val newProgress = progress.copy(saved = progress.saved - amount)
        val newBalance = currentBalance + amount

        return GameResult(
            value = SavingsOutcome(
                progress = newProgress,
                balance = newBalance,
                transaction = transaction(TransactionType.SAVINGS_WITHDRAW, amount, KEY_WITHDRAWN, goal, periodId),
                goalReached = newProgress.isReached(goal),
            ),
            explanation = Explanation(
                key = KEY_WITHDRAWN,
                args = mapOf(
                    "amount" to amount.amount.toString(),
                    "saved" to newProgress.saved.amount.toString(),
                    "remaining" to newProgress.remaining(goal).amount.toString(),
                ),
            ),
            changes = listOf(
                Change.Balance(from = currentBalance, to = newBalance),
                Change.Savings(from = progress.saved, to = newProgress.saved),
            ),
        )
    }

    /**
     * Покупка собранной цели (R13): копилка по цели обнуляется, цель
     * перестаёт быть активной — ребёнок выбирает следующую. Отложенное сверх
     * цены не пропадает, а возвращается в кошелёк сдачей.
     */
    fun buy(
        currentBalance: Coins,
        progress: GoalProgress,
        goal: Goal,
        periodId: Long,
    ): GameResult<SavingsOutcome> {
        require(progress.isReached(goal)) { "Купить можно только собранную цель" }

        val change = progress.saved - goal.price
        val newBalance = currentBalance + change
        val newProgress = progress.copy(saved = Coins.ZERO, isActive = false)
        val key = if (change > Coins.ZERO) KEY_BOUGHT_CHANGE else KEY_BOUGHT

        return GameResult(
            value = SavingsOutcome(
                progress = newProgress,
                balance = newBalance,
                transaction = transaction(TransactionType.GOAL_PURCHASE, change, key, goal, periodId),
                goalReached = false,
            ),
            explanation = Explanation(
                key = key,
                args = mapOf("icon" to goal.icon, "change" to change.amount.toString()),
            ),
            changes = listOfNotNull(
                Change.Balance(from = currentBalance, to = newBalance).takeIf { change > Coins.ZERO },
                Change.Savings(from = progress.saved, to = Coins.ZERO),
            ),
        )
    }

    fun periodsToGoal(progress: GoalProgress, goal: Goal, avgDeposit: Coins): Int? {
        val remaining = progress.remaining(goal)
        if (remaining == Coins.ZERO) return 0
        if (avgDeposit == Coins.ZERO) return null
        return ceilDiv(remaining.amount, avgDeposit.amount)
    }

    private fun transaction(
        type: TransactionType,
        amount: Coins,
        key: String,
        goal: Goal,
        periodId: Long,
    ) = Transaction(
        id = UNSAVED,
        periodId = periodId,
        type = type,
        amount = amount,
        reasonKey = key,
        createdAt = clock.now(),
        goalId = goal.id,
    )

    private fun ceilDiv(value: Int, divisor: Int): Int = (value + divisor - 1) / divisor

    private companion object {
        const val UNSAVED = 0L
        const val KEY_DEPOSITED = "savings.deposited"
        const val KEY_REACHED = "savings.goal_reached"
        const val KEY_WITHDRAWN = "savings.withdrawn"
        const val KEY_BOUGHT = "savings.goal_bought"
        const val KEY_BOUGHT_CHANGE = "savings.goal_bought_change"
    }
}
