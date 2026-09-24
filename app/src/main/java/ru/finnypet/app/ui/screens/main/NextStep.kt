package ru.finnypet.app.ui.screens.main

import ru.finnypet.app.domain.model.BudgetPlan
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.PeriodFact
import ru.finnypet.app.domain.model.PeriodStatus
import ru.finnypet.app.domain.model.SpendCategory

/**
 * Что ребёнку сделать дальше: главная кнопка и подсказка над ней.
 *
 * Порядок — цикл Приложения А ТЗ: задание, план, покупка нужного, копилка,
 * итоги дня. Это подсказка, а не замок: остальные разделы открыты, ребёнок
 * может идти в любом порядке (ТЗ 8.4: без длинной инструкции понятно, что
 * делать сейчас).
 */
sealed interface NextStep {

    data object Plan : NextStep

    data object Task : NextStep

    /** Во сколько обойдётся закрыть потребности совы самым дешёвым набором. */
    data class Shop(val needs: Coins) : NextStep

    /** Сколько по плану осталось отложить. */
    data class Save(val left: Coins) : NextStep

    /** [onPlan] — всё по плану; иначе монет на план не хватило, но день закончить можно. */
    data class Finish(val onPlan: Boolean) : NextStep
}

/**
 * Шаг предлагается, только если его можно сделать: без монет звать в магазин
 * или в копилку — значит отправить в тупик (ТЗ 3.4).
 *
 * В магазин зовут потребности совы, а не недотраченный план: иначе кнопка
 * учила бы «потрать всё, что запланировал на нужное» (R3). [needs] — цена
 * закрытия потребностей, ноль — их нет.
 */
fun nextStep(
    status: PeriodStatus,
    plan: BudgetPlan?,
    fact: PeriodFact,
    balance: Coins,
    needs: Coins,
    cheapestMandatory: Coins?,
    taskRewardAvailable: Boolean,
): NextStep {
    // Сначала заработай, потом распредели (R7): награда входит в план.
    if (taskRewardAvailable) return NextStep.Task
    if (status == PeriodStatus.PLANNING || plan == null) return NextStep.Plan

    val canBuy = cheapestMandatory != null && balance.covers(cheapestMandatory)
    if (needs > Coins.ZERO && canBuy) return NextStep.Shop(needs)

    val savings = fact.amountFor(SpendCategory.SAVINGS).shortfallTo(plan.savings)
    if (savings > Coins.ZERO && balance > Coins.ZERO) return NextStep.Save(minOf(savings, balance))

    val spentKept = fact.amountFor(SpendCategory.MANDATORY) <= plan.mandatory &&
        fact.amountFor(SpendCategory.OPTIONAL) <= plan.optional
    return NextStep.Finish(onPlan = needs == Coins.ZERO && savings == Coins.ZERO && spentKept)
}
