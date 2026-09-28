package ru.finnypet.app.ui.screens.day

import ru.finnypet.app.domain.economy.GrowthEngine
import ru.finnypet.app.domain.economy.PetStateEngine
import ru.finnypet.app.domain.economy.PlanFactReport
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.Explanation
import ru.finnypet.app.domain.model.GoalId
import ru.finnypet.app.domain.model.GrowthStar
import ru.finnypet.app.domain.model.PetMood
import ru.finnypet.app.domain.model.PetState
import ru.finnypet.app.domain.model.PetStatKind
import ru.finnypet.app.domain.model.ShopItem
import ru.finnypet.app.domain.model.SpendCategory
import ru.finnypet.app.domain.model.Transaction
import ru.finnypet.app.domain.model.TransactionType
import ru.finnypet.app.ui.components.BudgetLine

/** Строка итогов дня: звезда или пустой кружок и пояснение словами (DESIGN_PLAN 3.6). */
data class DayCheck(val done: Boolean, val text: Explanation)

/**
 * Три строки итогов — три звезды дня (AD-3): «Сыт», «По плану», «Отложил».
 * Звезда строки ставится ровно по [GrowthEngine.starsFor] — тем же звёздам, что растят
 * сову, поэтому итоги не спорят с ростом.
 *
 * В день с незакрытой потребностью звёзд нет. Соблюдённые части плана и
 * реальные накопления при этом называются отдельно от награды за рост.
 *
 * [goalTitle] и [goalLeft] — цель и сколько до неё осталось после этого дня;
 * `null` — цели нет или монеты дня ушли не только в неё ([savedOnlyFor]).
 * Осталось ноль — цель собрана, и строка так и говорит, а не «осталось 0»
 * или «монеты уже ближе к цели». Без цели «ближе к цели» в голодный день
 * тоже врёт: монеты могли уйти в купленную днём цель — тогда только сумма.
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
                goalTitle != null && goalLeft == Coins.ZERO -> Explanation(
                    "day.savings.reached",
                    mapOf("saved" to "${savings.actual.amount}", "goal" to goalTitle),
                )
                !needsMet && goalTitle != null -> Explanation("day.savings.hungry", mapOf("saved" to "${savings.actual.amount}"))
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
 * Все ли монеты копилки за день легли в цель [goal]: «Копилка +35: до цели
 * осталось 50» врёт, если часть из 35 ушла в прошлую цель, купленную днём.
 * Тогда итог называет только сумму дня, без остатка до новой цели.
 */
fun savedOnlyFor(goal: GoalId, transactions: List<Transaction>): Boolean =
    transactions.none { it.type == TransactionType.GOAL_PURCHASE } &&
        transactions.filter { it.type.category == SpendCategory.SAVINGS }.all { it.goalId == goal }

/**
 * О чём грустит сова в итогах — о первой потребности вечером, до ночи (AD-2).
 * Ночь снижает все показатели, и по утреннему состоянию сова, у которой
 * не закрыт только уход, «хотела бы есть» — итог назвал бы не ту причину.
 */
fun eveningNeed(evening: PetState, pet: PetStateEngine): PetStatKind? = pet.needsOf(evening).firstOrNull()

/**
 * Одна строка итога под полосами (DESIGN_PLAN 3.6) вместо карточек «План» и
 * «Потрачено»: потраченное и отложенное — раздельно. Общая сумма складывала
 * покупки с копилкой, и «Потрачено 31» при покупках на 14 путало ребёнка.
 */
data class DayTotals(val spent: Coins, val saved: Coins)

fun dayTotals(lines: List<BudgetLine>): DayTotals = DayTotals(
    spent = lines.filter { it.category != SpendCategory.SAVINGS }.fold(Coins.ZERO) { sum, line -> sum + line.actual },
    saved = lines.filter { it.category == SpendCategory.SAVINGS }.fold(Coins.ZERO) { sum, line -> sum + line.actual },
)

/** Звезда появляется после стольких заработанных перед ней: заработанные загораются по очереди (DESIGN_PLAN 2.7). */
fun starDelayMs(done: List<Boolean>, index: Int): Int = done.take(index).count { it } * STAR_STAGGER_MS

/** 150 мс между звёздами — из DESIGN_PLAN 2.7. */
const val STAR_STAGGER_MS = 150

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
 *
 * [goalCollected] — на активную цель уже хватает: «монета в копилку
 * приближает цель» тогда звучит странно, совет зовёт купить цель (ревью F5).
 */
fun dayTip(checks: List<DayCheck>, foodSpent: Coins, shop: List<ShopItem>, goalCollected: Boolean): Explanation {
    val (needs, optional, savings) = checks
    val prices = shop.filter(::isFood).map { it.price }
    val usual = prices.maxOrNull()
    return when {
        !needs.done -> Explanation("day.tip.needs_first")
        !optional.done -> Explanation("day.tip.plan")
        !savings.done && goalCollected -> Explanation("day.tip.goal_collected")
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
