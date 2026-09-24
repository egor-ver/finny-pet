package ru.finnypet.app.domain.economy

import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.SpendCategory

data class PlanFactLine(
    val category: SpendCategory,
    val planned: Coins,
    val actual: Coins,
) {

    val deviation: Int get() = actual.amount - planned.amount

    /**
     * Траты соблюдены, если потрачено не больше плана: сэкономил на нужном —
     * остаток переходит на завтра без наказания (R3). Сыта ли сова, план не
     * знает — это потребности (AD-3). Копилку, наоборот, надо пополнить.
     */
    val followed: Boolean
        get() = when (category) {
            SpendCategory.MANDATORY, SpendCategory.OPTIONAL -> actual <= planned
            SpendCategory.SAVINGS -> actual >= planned
        }
}

data class PlanFactReport(
    val lines: List<PlanFactLine>,
    val planTotal: Coins,
    val factTotal: Coins,
) {

    init {
        require(lines.map { it.category } == SpendCategory.entries) {
            "Отчёт обязан содержать ровно по одной строке на направление в порядке " +
                "${SpendCategory.entries}, получено: ${lines.map { it.category }}"
        }
    }

    fun line(category: SpendCategory): PlanFactLine = lines.first { it.category == category }

    val savingsKept: Boolean get() = line(SpendCategory.SAVINGS).followed

    val planFollowed: Boolean get() = lines.all { it.followed }
}
