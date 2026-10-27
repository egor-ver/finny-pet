package ru.finnypet.app.domain.model

data class Profile(
    val id: ProfileId,
    val childName: String,
    val petName: String,
    val appearance: PetAppearance,
    val createdAt: Long,
    val isTest: Boolean = false,
) {

    init {
        require(childName.isNotBlank()) { "Игровое имя игрока обязательно" }
        require(petName.isNotBlank()) { "Имя питомца обязательно" }
        require(childName.length <= MAX_NAME_LENGTH) { "Игровое имя длиннее $MAX_NAME_LENGTH символов" }
        require(petName.length <= MAX_NAME_LENGTH) { "Имя питомца длиннее $MAX_NAME_LENGTH символов" }
    }

    companion object {
        const val MAX_NAME_LENGTH = 20
    }
}
