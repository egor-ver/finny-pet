package ru.finnypet.app.domain.content

import ru.finnypet.app.domain.model.Coins

/** Фиксированное событие игрового дня, происходящее до составления плана. */
sealed interface DayEvent {
    val id: String
    val day: Int
    val messageKey: String

    data class ExtraCare(
        override val id: String,
        override val day: Int,
        override val messageKey: String,
        val careDrop: Int,
    ) : DayEvent {
        init { require(careDrop in 1..100) }
    }

    data class Gift(
        override val id: String,
        override val day: Int,
        override val messageKey: String,
        val amount: Coins,
    ) : DayEvent {
        init { require(amount > Coins.ZERO) }
    }
}
