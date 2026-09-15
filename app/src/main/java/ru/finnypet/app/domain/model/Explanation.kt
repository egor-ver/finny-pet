package ru.finnypet.app.domain.model

data class Explanation(
    val key: String,
    val args: Map<String, String> = emptyMap(),
    val nextStep: RecoveryOption? = null,
) {

    init {
        require(key.isNotBlank()) { "Ключ объяснения не может быть пустым" }
    }
}

enum class RecoveryOption {
    DO_TASK,
    POSTPONE_PURCHASE,
    ADJUST_NEXT_PLAN,
    WITHDRAW_FROM_SAVINGS,
    CHOOSE_CHEAPER,
}
