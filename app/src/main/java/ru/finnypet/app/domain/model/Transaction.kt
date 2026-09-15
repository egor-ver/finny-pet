package ru.finnypet.app.domain.model

enum class TransactionType(
    val category: SpendCategory?,
    val isIncome: Boolean,
) {
    INCOME_PERIOD(null, true),
    INCOME_TASK(null, true),
    PURCHASE_MANDATORY(SpendCategory.MANDATORY, false),
    PURCHASE_OPTIONAL(SpendCategory.OPTIONAL, false),
    SAVINGS_DEPOSIT(SpendCategory.SAVINGS, false),
    SAVINGS_WITHDRAW(SpendCategory.SAVINGS, true),
    UNEXPECTED_EXPENSE(SpendCategory.MANDATORY, false),
}

data class Transaction(
    val id: Long,
    val periodId: Long,
    val type: TransactionType,
    val amount: Coins,
    val reasonKey: String,
    val createdAt: Long,
    val itemId: ItemId? = null,
    val goalId: GoalId? = null,
) {

    init {
        require(reasonKey.isNotBlank()) { "Транзакция обязана нести ключ объяснения" }
    }

    val balanceDelta: Int get() = if (type.isIncome) amount.amount else -amount.amount

    val factDelta: Int get() = -balanceDelta
}
