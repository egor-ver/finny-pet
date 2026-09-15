package ru.finnypet.app.domain.model

@JvmInline
value class Coins(val amount: Int) : Comparable<Coins> {

    init {
        require(amount >= 0) { "Монет не может быть меньше нуля: $amount" }
    }

    operator fun plus(other: Coins): Coins = Coins(amount + other.amount)

    operator fun minus(other: Coins): Coins = Coins(amount - other.amount)

    operator fun times(factor: Int): Coins = Coins(amount * factor)

    override fun compareTo(other: Coins): Int = amount.compareTo(other.amount)

    fun covers(price: Coins): Boolean = amount >= price.amount

    fun shortfallTo(price: Coins): Coins =
        if (covers(price)) ZERO else Coins(price.amount - amount)

    companion object {
        val ZERO = Coins(0)
    }
}
