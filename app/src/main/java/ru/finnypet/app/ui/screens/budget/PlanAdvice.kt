package ru.finnypet.app.ui.screens.budget

import ru.finnypet.app.domain.model.BudgetPlan
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.Explanation
import ru.finnypet.app.domain.model.PetMood
import ru.finnypet.app.domain.model.PetStatKind
import ru.finnypet.app.domain.model.ShopItem
import ru.finnypet.app.ui.text.textOf
import ru.finnypet.app.ui.text.wordFormOf

/** Как сова смотрит на план и что говорит (раздел 8 плана). */
data class PlanOwl(val mood: PetMood, val phrase: Explanation)

/**
 * Сова отвечает на каждое движение ползунка. Грустит она только от плана,
 * в котором ей не хватит на потребности: это последствие, которое ребёнок
 * видит до решения, а не после (ТЗ 2.2).
 *
 * [cover] — цена закрытия потребностей, `null` — в магазине их не закрыть
 * целиком, тогда о нужном сова молчит. [hitLimit] — ползунок только что
 * упёрся в конец кошелька. [goalTitle] — цель, `null` — её нет.
 */
fun planOwl(
    plan: BudgetPlan,
    wallet: Coins,
    cover: Coins?,
    slack: Int,
    goalTitle: String?,
    hitLimit: Boolean,
): PlanOwl = when {
    hitLimit -> PlanOwl(PetMood.CALM, Explanation("owl.plan.limit"))
    // Утро без грусти: пока ребёнок ничего не разложил, сова не судит план.
    plan.total == Coins.ZERO -> PlanOwl(PetMood.CALM, Explanation("owl.plan.start", mapOf("wallet" to "${wallet.amount}")))
    // Нужное не тронуто, но разложить ещё есть что (в плане осталось место) —
    // рано грустить: ребёнок мог начать с копилки и вернуться к нужному
    // следующим ходом (Б15). Но и молчать нельзя: план уже можно подтвердить,
    // и о нехватке на еду и уход сова предупреждает сразу, только спокойно.
    cover != null && cover > Coins.ZERO && plan.mandatory == Coins.ZERO && plan.total < wallet ->
        PlanOwl(PetMood.CALM, Explanation("owl.plan.not_enough", mapOf("need" to "${cover.amount}")))
    cover != null && plan.mandatory < cover ->
        PlanOwl(PetMood.SAD, Explanation("owl.plan.not_enough", mapOf("need" to "${cover.amount}")))
    // Лазейка «всё в нужное»: план соблюдён, а выбора не было (раздел 3 плана).
    cover != null && plan.mandatory.amount > cover.amount + slack -> PlanOwl(PetMood.CALM, Explanation("owl.plan.too_much"))
    goalTitle != null && plan.savings == Coins.ZERO ->
        PlanOwl(PetMood.CALM, Explanation("owl.plan.no_savings", mapOf("goal" to goalTitle)))
    else -> PlanOwl(PetMood.HAPPY, Explanation("owl.plan.good"))
}

/**
 * «Финни нужно не меньше 37: еда 22, уход 15» — сколько стоит закрыть
 * каждую потребность. `null` — потребность в магазине нечем закрыть, и
 * называть число было бы неправдой.
 */
fun mandatoryHint(texts: Map<String, String>, coverByNeed: Map<PetStatKind, Coins>?): String? {
    if (coverByNeed == null) return null
    if (coverByNeed.isEmpty()) return texts.textOf("plan.hint.no_needs")
    val parts = coverByNeed.entries.joinToString(", ") { (kind, price) ->
        texts.textOf("plan.need.${kind.name}").replace("{price}", "${price.amount}")
    }
    val total = coverByNeed.values.sumOf { it.amount }
    return texts.textOf(Explanation("plan.hint.needs", mapOf("total" to "$total", "parts" to parts)))
}

/**
 * Что можно купить на желаемое — самое дорогое, на что хватает: так видно,
 * на что именно ребёнок копит внутри дня. Ноль — не ошибка (термины ТЗ).
 */
fun optionalHint(texts: Map<String, String>, amount: Coins, wants: List<ShopItem>): String {
    if (amount == Coins.ZERO) return texts.textOf("plan.hint.optional_zero")
    val best = wants.filter { amount.covers(it.price) }.maxByOrNull { it.price }
    if (best != null) {
        val item = "${best.icon} ${texts.textOf(best.titleKey)}".trim()
        return texts.textOf(Explanation("plan.hint.optional_enough", mapOf("item" to item)))
    }
    val cheapest = wants.minOfOrNull { it.price } ?: return texts.textOf("plan.hint.optional_zero")
    return texts.textOf(Explanation("plan.hint.optional_none", mapOf("price" to "${cheapest.amount}")))
}

/**
 * «Если откладывать по 8 в день — Комиксы через 3 дня». Срок — условие, а
 * не обещание: считается от того, что ребёнок кладёт сейчас (ТЗ 2.5.7).
 * [days] — сколько дней до цели при такой сумме, `null` — цели нет.
 */
fun savingsHint(texts: Map<String, String>, amount: Coins, goalTitle: String?, days: Int?): String? {
    if (goalTitle == null) return null
    val args = mapOf("goal" to goalTitle, "amount" to "${amount.amount}", "days" to "$days")
    val key = when {
        days == 0 -> "plan.hint.savings_reached"
        amount == Coins.ZERO || days == null -> "plan.hint.savings_zero"
        else -> "plan.hint.savings_days.${wordFormOf(days).name}"
    }
    return texts.textOf(Explanation(key, args))
}
