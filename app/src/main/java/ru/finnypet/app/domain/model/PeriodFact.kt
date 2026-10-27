package ru.finnypet.app.domain.model

data class PeriodFact(val spent: Map<SpendCategory, Coins>) {

    fun amountFor(category: SpendCategory): Coins = spent[category] ?: Coins.ZERO

    val total: Coins
        get() = SpendCategory.entries.fold(Coins.ZERO) { acc, category -> acc + amountFor(category) }

    companion object {

        val EMPTY = PeriodFact(emptyMap())

        fun of(
            mandatory: Coins = Coins.ZERO,
            optional: Coins = Coins.ZERO,
            savings: Coins = Coins.ZERO,
        ): PeriodFact = PeriodFact(
            mapOf(
                SpendCategory.MANDATORY to mandatory,
                SpendCategory.OPTIONAL to optional,
                SpendCategory.SAVINGS to savings,
            )
        )
    }
}
