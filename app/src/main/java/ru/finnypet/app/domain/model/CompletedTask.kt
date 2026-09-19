package ru.finnypet.app.domain.model

/**
 * Пройденное задание для раздела прогресса (ТЗ 2.5.11).
 *
 * Хранится каждое прохождение, а не факт «задание пройдено»: ребёнок может
 * вернуться к заданию, и прежний результат от этого не исчезает.
 */
data class CompletedTask(
    val taskId: TaskId,
    val outcomeId: String,
    val reward: Coins,
    val completedAt: Long,
) {

    init {
        require(outcomeId.isNotBlank()) { "Пройденное задание обязано нести исход" }
    }
}

/**
 * Что записать о только что пройденном задании. Время проставит хранилище:
 * у экрана часов нет, а домен их и не должен знать.
 */
data class TaskCompletion(
    val taskId: TaskId,
    val outcomeId: String,
    val reward: Coins,
) {

    init {
        require(outcomeId.isNotBlank()) { "Пройденное задание обязано нести исход" }
    }
}
