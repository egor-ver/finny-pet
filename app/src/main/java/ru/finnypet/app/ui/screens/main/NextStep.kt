package ru.finnypet.app.ui.screens.main

import ru.finnypet.app.domain.model.BudgetPlan
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.PeriodFact
import ru.finnypet.app.domain.model.PeriodStatus
import ru.finnypet.app.domain.model.SpendCategory

/**
 * Что ребёнку сделать дальше: главная кнопка и подсказка над ней.
 *
 * Порядок — цикл Приложения А ТЗ: план, задание, покупка нужного, копилка,
 * итоги дня. Это подсказка, а не замок: остальные разделы открыты, ребёнок
 * может идти в любом порядке (ТЗ 8.4: без длинной инструкции понятно, что
 * делать сейчас).
 */
sealed interface NextStep {

    data object Plan : NextStep

    data object Task : NextStep

    /** Сколько по плану осталось потратить на нужное. */
    data class Shop(val left: Coins) : NextStep

    /** Сколько по плану осталось отложить. */
    data class Save(val left: Coins) : NextStep

    /** [onPlan] — всё по плану; иначе монет на план не хватило, но день закончить можно. */
    data class Finish(val onPlan: Boolean) : NextStep
}

/**
 * Шаг предлагается, только если его можно сделать: без монет звать в магазин
 * или в копилку — значит отправить в тупик (ТЗ 3.4).
 */
fun nextStep(
    status: PeriodStatus,
    plan: BudgetPlan?,
    fact: PeriodFact,
    balance: Coins,
    cheapestMandatory: Coins?,
    taskRewardAvailable: Boolean,
): NextStep {
    if (status == PeriodStatus.PLANNING || plan == null) return NextStep.Plan
    if (taskRewardAvailable) return NextStep.Task

    val mandatory = fact.amountFor(SpendCategory.MANDATORY).shortfallTo(plan.mandatory)
    val canBuy = cheapestMandatory != null && balance.covers(cheapestMandatory)
    if (mandatory > Coins.ZERO && canBuy) return NextStep.Shop(mandatory)

    val savings = fact.amountFor(SpendCategory.SAVINGS).shortfallTo(plan.savings)
    if (savings > Coins.ZERO && balance > Coins.ZERO) return NextStep.Save(minOf(savings, balance))

    val optionalKept = fact.amountFor(SpendCategory.OPTIONAL) <= plan.optional
    return NextStep.Finish(onPlan = mandatory == Coins.ZERO && savings == Coins.ZERO && optionalKept)
}
