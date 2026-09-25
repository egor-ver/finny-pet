package ru.finnypet.app.domain.model

enum class TransactionType(
    val category: SpendCategory?,
    val isIncome: Boolean,
) {
    INCOME_PERIOD(null, true),
    INCOME_TASK(null, true),
    INCOME_PARENT(null, true),
    INCOME_GIFT(null, true),
    PURCHASE_MANDATORY(SpendCategory.MANDATORY, false),
    PURCHASE_OPTIONAL(SpendCategory.OPTIONAL, false),
    SAVINGS_DEPOSIT(SpendCategory.SAVINGS, false),
    SAVINGS_WITHDRAW(SpendCategory.SAVINGS, true),
    UNEXPECTED_EXPENSE(SpendCategory.MANDATORY, false),
    EVENT_CARE(null, false),

    /**
     * Покупка собранной цели (R13). Цена оплачена копилкой, поэтому сумма
     * операции — только сдача: отложенное сверх цены возвращается в кошелёк.
     * При точной сумме это ноль, и кошелёк не меняется. По этим операциям
     * строится коллекция вещей рядом с совой (AD-10).
     */
    GOAL_PURCHASE(null, true),
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

    val balanceDelta: Int get() = when (type) {
        TransactionType.EVENT_CARE -> 0
        else -> if (type.isIncome) amount.amount else -amount.amount
    }

    val factDelta: Int get() = -balanceDelta
}
