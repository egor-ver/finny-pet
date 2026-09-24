package ru.finnypet.app.ui.screens.day

import ru.finnypet.app.domain.economy.PlanFactReport
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.Explanation
import ru.finnypet.app.domain.model.PetMood
import ru.finnypet.app.domain.model.PetStatKind
import ru.finnypet.app.domain.model.SpendCategory

/** Строка итогов дня: ✓ или ✗ и пояснение словами (раздел 8 плана). */
data class DayCheck(val done: Boolean, val text: Explanation)

/**
 * Три строки итогов — те же три условия, за которые даются очки роста
 * (R10): потребности закрыты, желаемое по плану, копилка пополнена. Поэтому
 * ✓ ставится ровно там, где рост дал очки, и итоги не спорят с ростом.
 *
 * [goalTitle] и [goalLeft] — цель и сколько до неё осталось после этого дня;
 * `null` — цели нет.
 */
fun dayChecks(needsMet: Boolean, report: PlanFactReport, goalTitle: String?, goalLeft: Coins?): List<DayCheck> {
    val mandatory = report.line(SpendCategory.MANDATORY)
    val optional = report.line(SpendCategory.OPTIONAL)
    val savings = report.line(SpendCategory.SAVINGS)
    return listOf(
        DayCheck(
            done = needsMet,
            text = if (needsMet) {
                Explanation("day.needs.done", mapOf("spent" to "${mandatory.actual.amount}"))
            } else {
                Explanation("day.needs.missed")
            },
        ),
        DayCheck(
            done = optional.followed,
            text = when {
                !optional.followed -> Explanation("day.optional.over", mapOf("over" to "${optional.actual.amount - optional.planned.amount}"))
                optional.actual == Coins.ZERO -> Explanation("day.optional.none")
                else -> Explanation(
                    "day.optional.kept",
                    mapOf("spent" to "${optional.actual.amount}", "planned" to "${optional.planned.amount}"),
                )
            },
        ),
        // Нулевой план копилки выполняется сам собой, но очков за него нет:
        // и ✓ за него ставить нечестно.
        DayCheck(
            done = savings.planned > Coins.ZERO && savings.followed,
            text = when {
                savings.planned == Coins.ZERO -> Explanation("day.savings.none")
                !savings.followed -> Explanation(
                    "day.savings.missed",
                    mapOf("saved" to "${savings.actual.amount}", "planned" to "${savings.planned.amount}"),
                )
                goalTitle != null && goalLeft != null -> Explanation(
                    "day.savings.kept_goal",
                    mapOf("saved" to "${savings.actual.amount}", "goal" to goalTitle, "left" to "${goalLeft.amount}"),
                )
                else -> Explanation("day.savings.kept", mapOf("saved" to "${savings.actual.amount}"))
            },
        ),
    )
}

/**
 * Выражение совы за день: грустит, только если осталась без нужного — это
 * последствие, которое ребёнок должен увидеть; всё выполнено — радуется.
 */
fun dayMood(checks: List<DayCheck>): PetMood = when {
    !checks.first().done -> PetMood.SAD
    checks.all { it.done } -> PetMood.HAPPY
    else -> PetMood.CALM
}

/**
 * Перед сном (R14): потребности не закрыты, а на нужное монеты есть — сова
 * переспрашивает. `null` — переспрашивать не о чем.
 */
fun sleepWarning(needs: List<PetStatKind>, canBuyMandatory: Boolean): Explanation? =
    needs.firstOrNull()?.takeIf { canBuyMandatory }?.let { Explanation("owl.sleep.${it.name}") }
