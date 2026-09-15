package ru.finnypet.app.domain.model

enum class PetStatKind {
    MOOD,
    SATIETY,
    CARE,
}

data class PetEffect(
    val stat: PetStatKind,
    val delta: Int,
) {

    init {
        require(delta != 0) { "Эффект, который ничего не меняет, не имеет смысла: ${stat.name}" }
    }

    val isPositive: Boolean get() = delta > 0
}
