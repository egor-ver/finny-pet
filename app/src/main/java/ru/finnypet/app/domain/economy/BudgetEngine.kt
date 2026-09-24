package ru.finnypet.app.domain.economy

import ru.finnypet.app.domain.model.BudgetPlan
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.PeriodFact
import ru.finnypet.app.domain.model.SpendCategory

sealed interface PlanCheck {
    data class Fits(val remainder: Coins) : PlanCheck
    data class Exceeds(val overBy: Coins) : PlanCheck
}

class BudgetEngine {

    fun check(plan: BudgetPlan, available: Coins): PlanCheck {
        val total = plan.total
        return if (available.covers(total)) {
            PlanCheck.Fits(available - total)
        } else {
            PlanCheck.Exceeds(total - available)
        }
    }

    /**
     * Сумма, которую ребёнок выставил ползунком, урезанная по кошельку: в
     * банку помещается то, что в ней лежит, плюс нераспределённое. Так
     * ползунок не уходит дальше монет (ТЗ 2.5.5). Одно правило для плана дня
     * и для задания «раздели монеты».
     */
    fun clamped(plan: BudgetPlan, category: SpendCategory, amount: Coins, available: Coins): Coins {
        val others = plan.total.amount - plan.amountFor(category).amount
        return Coins(amount.amount.coerceIn(0, (available.amount - others).coerceAtLeast(0)))
    }

    fun compare(plan: BudgetPlan, fact: PeriodFact): PlanFactReport {
        val lines = SpendCategory.entries.map { category ->
            PlanFactLine(
                category = category,
                planned = plan.amountFor(category),
                actual = fact.amountFor(category),
            )
        }
        return PlanFactReport(lines = lines, planTotal = plan.total, factTotal = fact.total)
    }
}
