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

    data object Finish : NextStep
}

fun nextStep(
    status: PeriodStatus,
    plan: BudgetPlan?,
    fact: PeriodFact,
    taskRewardAvailable: Boolean,
): NextStep {
    if (status == PeriodStatus.PLANNING || plan == null) return NextStep.Plan
    if (taskRewardAvailable) return NextStep.Task

    val mandatory = fact.amountFor(SpendCategory.MANDATORY).shortfallTo(plan.mandatory)
    if (mandatory > Coins.ZERO) return NextStep.Shop(mandatory)

    val savings = fact.amountFor(SpendCategory.SAVINGS).shortfallTo(plan.savings)
    if (savings > Coins.ZERO) return NextStep.Save(savings)

    return NextStep.Finish
}
