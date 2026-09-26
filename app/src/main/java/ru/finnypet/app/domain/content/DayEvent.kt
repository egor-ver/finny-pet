package ru.finnypet.app.domain.content

import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.PetEffect
import ru.finnypet.app.domain.model.PetStatKind

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

/**
 * Эффект события на показатели совы. Одна функция для продакшн-кода
 * ([ru.finnypet.app.domain.usecase.OpenPeriodIfNeeded]) и симуляции (L8):
 * иначе денежная и статовая часть события описывались бы дважды и могли разойтись.
 */
fun DayEvent.petEffects(): List<PetEffect> = when (this) {
    is DayEvent.ExtraCare -> listOf(PetEffect(PetStatKind.CARE, -careDrop))
    is DayEvent.Gift -> emptyList()
}
