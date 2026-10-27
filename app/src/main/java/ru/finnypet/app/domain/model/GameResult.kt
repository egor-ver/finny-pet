package ru.finnypet.app.domain.model

data class GameResult<out T>(
    val value: T,
    val explanation: Explanation,
    val changes: List<Change> = emptyList(),
)

sealed interface Change {

    data class Balance(val from: Coins, val to: Coins) : Change {
        val delta: Int get() = to.amount - from.amount
    }

    data class Savings(val from: Coins, val to: Coins) : Change {
        val delta: Int get() = to.amount - from.amount
    }

    data class PetStat(val kind: PetStatKind, val from: Stat, val to: Stat) : Change {
        val delta: Int get() = to.value - from.value
    }

    data class Stage(val from: GrowthStage, val to: GrowthStage) : Change
}
