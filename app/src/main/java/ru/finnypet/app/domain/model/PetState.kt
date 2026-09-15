package ru.finnypet.app.domain.model

data class PetState(
    val mood: Stat,
    val satiety: Stat,
    val care: Stat,
) {

    fun statFor(kind: PetStatKind): Stat = when (kind) {
        PetStatKind.MOOD -> mood
        PetStatKind.SATIETY -> satiety
        PetStatKind.CARE -> care
    }

    fun with(kind: PetStatKind, stat: Stat): PetState = when (kind) {
        PetStatKind.MOOD -> copy(mood = stat)
        PetStatKind.SATIETY -> copy(satiety = stat)
        PetStatKind.CARE -> copy(care = stat)
    }

    companion object {
        fun uniform(stat: Stat) = PetState(mood = stat, satiety = stat, care = stat)
    }
}
