package ru.finnypet.app.domain.model

enum class GrowthStage {
    CUB,
    YOUNG,
    GROWN,
}

data class PetGrowth(
    val points: Int,
    val stage: GrowthStage,
) {

    init {
        require(points >= 0) { "Очки роста не могут быть отрицательными: $points" }
    }

    companion object {
        val INITIAL = PetGrowth(points = 0, stage = GrowthStage.CUB)
    }
}
