package ru.finnypet.app.domain.model

@JvmInline
value class Stat(val value: Int) : Comparable<Stat> {

    init {
        require(value in RANGE) { "Показатель питомца задаётся в пределах $RANGE, получено: $value" }
    }

    operator fun plus(delta: Int): Stat = Stat((value + delta).coerceIn(RANGE))

    operator fun minus(delta: Int): Stat = Stat((value - delta).coerceIn(RANGE))

    override fun compareTo(other: Stat): Int = value.compareTo(other.value)

    override fun toString(): String = value.toString()

    companion object {
        val RANGE = 0..100
        val MIN = Stat(RANGE.first)
        val MAX = Stat(RANGE.last)
    }
}
