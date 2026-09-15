package ru.finnypet.app.domain.model

data class BudgetPlan(
    val mandatory: Coins,
    val optional: Coins,
    val savings: Coins,
) {

    val total: Coins get() = mandatory + optional + savings

    fun amountFor(category: SpendCategory): Coins = when (category) {
        SpendCategory.MANDATORY -> mandatory
        SpendCategory.OPTIONAL -> optional
        SpendCategory.SAVINGS -> savings
    }

    fun with(category: SpendCategory, amount: Coins): BudgetPlan = when (category) {
        SpendCategory.MANDATORY -> copy(mandatory = amount)
        SpendCategory.OPTIONAL -> copy(optional = amount)
        SpendCategory.SAVINGS -> copy(savings = amount)
    }

    companion object {
        val EMPTY = BudgetPlan(Coins.ZERO, Coins.ZERO, Coins.ZERO)
    }
}
