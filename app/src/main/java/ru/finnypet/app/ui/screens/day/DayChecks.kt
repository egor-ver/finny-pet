package ru.finnypet.app.ui.screens.day

import ru.finnypet.app.domain.economy.GrowthEngine
import ru.finnypet.app.domain.economy.PlanFactReport
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.Explanation
import ru.finnypet.app.domain.model.GrowthStar
import ru.finnypet.app.domain.model.PetMood
import ru.finnypet.app.domain.model.PetStatKind
import ru.finnypet.app.domain.model.ShopItem
import ru.finnypet.app.domain.model.SpendCategory
import ru.finnypet.app.domain.model.Transaction

/** Строка итогов дня: ✓ или ✗ и пояснение словами (раздел 8 плана). */
data class DayCheck(val done: Boolean, val text: Explanation)

/**
 * Три строки итогов — три звезды дня (AD-3): «Сыт», «По плану», «Отложил».
 * ✓ ставится ровно по [GrowthEngine.starsFor] — тем же звёздам, что растят
 * сову, поэтому итоги не спорят с ростом.
 *
 * В день с незакрытой потребностью звёзд нет. Соблюдённые части плана и
 * реальные накопления при этом называются отдельно от награды за рост.
 *
 * [goalTitle] и [goalLeft] — цель и сколько до неё осталось после этого дня;
 * `null` — цели нет.
 */
fun dayChecks(needsMet: Boolean, report: PlanFactReport, goalTitle: String?, goalLeft: Coins?): List<DayCheck> {
    val stars = GrowthEngine.starsFor(report, needsMet)
    val mandatory = report.line(SpendCategory.MANDATORY)
    val optional = report.line(SpendCategory.OPTIONAL)
    val savings = report.line(SpendCategory.SAVINGS)
    return listOf(
        DayCheck(
            done = GrowthStar.FED in stars,
            text = if (needsMet) {
                Explanation("day.needs.done", mapOf("spent" to "${mandatory.actual.amount}"))
            } else {
                Explanation("day.needs.missed")
            },
        ),
        DayCheck(
            done = GrowthStar.PLAN in stars,
            text = when {
                !mandatory.followed && !optional.followed -> Explanation("day.plan.over_both", mapOf(
                    "mandatoryOver" to "${mandatory.deviation}", "optionalOver" to "${optional.deviation}",
                ))
                !mandatory.followed -> Explanation("day.plan.over_mandatory", mapOf("over" to "${mandatory.deviation}"))
                !optional.followed -> Explanation("day.plan.over_optional", mapOf("over" to "${optional.deviation}"))
                !needsMet -> Explanation("day.plan.no_growth")
                optional.actual == Coins.ZERO -> Explanation("day.plan.no_optional")
                else -> Explanation("day.plan.kept")
            },
        ),
        DayCheck(
            done = GrowthStar.SAVED in stars,
            text = when {
                savings.planned == Coins.ZERO && savings.actual > Coins.ZERO -> Explanation(
                    "day.savings.unplanned",
                    mapOf("saved" to "${savings.actual.amount}"),
                )
                savings.planned == Coins.ZERO -> Explanation("day.savings.none")
                !savings.followed -> Explanation(
                    "day.savings.missed",
                    mapOf("saved" to "${savings.actual.amount}", "planned" to "${savings.planned.amount}"),
                )
                !needsMet -> Explanation("day.savings.hungry", mapOf("saved" to "${savings.actual.amount}"))
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
 * Один совет в итогах (раздел 8 плана) — о первом, что не получилось, в
 * порядке строк итогов. Всё получилось, но еда обошлась дороже обычного —
 * так бывает после голодного дня — совет про еду каждый день. Иначе похвала.
 */
fun dayTip(checks: List<DayCheck>, foodSpent: Coins, shop: List<ShopItem>): Explanation {
    val (needs, optional, savings) = checks
    val prices = shop.filter(::isFood).map { it.price }
    val usual = prices.maxOrNull()
    return when {
        !needs.done -> Explanation("day.tip.needs_first")
        !optional.done -> Explanation("day.tip.plan")
        !savings.done -> Explanation("day.tip.savings")
        usual != null && foodSpent > usual -> Explanation(
            "day.tip.feed_daily",
            mapOf("min" to "${prices.min().amount}", "max" to "${usual.amount}", "spent" to "${foodSpent.amount}"),
        )
        else -> Explanation("day.tip.keep")
    }
}

/** Сколько за день ушло на еду: покупки товаров, которые поднимают сытость. */
fun foodSpent(transactions: List<Transaction>, shop: List<ShopItem>): Coins {
    val food = shop.filter(::isFood).map { it.id }.toSet()
    return Coins(transactions.filter { it.itemId in food }.sumOf { it.amount.amount })
}

private fun isFood(item: ShopItem): Boolean = item.effects.any { it.stat == PetStatKind.SATIETY && it.delta > 0 }

/**
 * Перед сном (R14): потребности не закрыты, а на нужное монеты есть — сова
 * переспрашивает. `null` — переспрашивать не о чем.
 */
fun sleepWarning(needs: List<PetStatKind>, canBuyMandatory: Boolean): Explanation? =
    needs.firstOrNull()?.takeIf { canBuyMandatory }?.let { Explanation("owl.sleep.${it.name}") }
