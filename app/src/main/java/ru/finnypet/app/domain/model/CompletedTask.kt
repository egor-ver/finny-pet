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
