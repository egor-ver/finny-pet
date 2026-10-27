package ru.finnypet.app.domain.model

@JvmInline
value class ProfileId(val value: String) {
    init { require(value.isNotBlank()) { "Идентификатор профиля не может быть пустым" } }
}

@JvmInline
value class GoalId(val value: String) {
    init { require(value.isNotBlank()) { "Идентификатор цели не может быть пустым" } }
}

@JvmInline
value class TaskId(val value: String) {
    init { require(value.isNotBlank()) { "Идентификатор задания не может быть пустым" } }
}

@JvmInline
value class ItemId(val value: String) {
    init { require(value.isNotBlank()) { "Идентификатор товара не может быть пустым" } }
}
