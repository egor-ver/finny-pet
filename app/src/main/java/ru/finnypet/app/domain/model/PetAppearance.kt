package ru.finnypet.app.domain.model

data class PetAppearance(
    val bodyId: String,
    val colorId: String,
    val accessoryId: String? = null,
) {

    init {
        require(bodyId.isNotBlank()) { "Тип тела питомца обязателен" }
        require(colorId.isNotBlank()) { "Окрас питомца обязателен" }
        require(accessoryId == null || accessoryId.isNotBlank()) {
            "Аксессуар либо отсутствует, либо назван: пустая строка недопустима"
        }
    }
}
