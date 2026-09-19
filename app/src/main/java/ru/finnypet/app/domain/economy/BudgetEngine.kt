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
     * Новая сумма направления после шага кнопкой или `null`, если двигать
     * некуда. Шаг урезается по месту: добавить можно не больше, чем осталось
     * нераспределённого, а убрать — не больше, чем лежит. Так последние
     * монеты не застревают, когда сумма не делится на шаг нацело. Одно
     * правило для плана дня и для задания «раздели монеты».
     */
    fun stepped(plan: BudgetPlan, category: SpendCategory, delta: Int, available: Coins): Coins? {
        val current = plan.amountFor(category).amount
        val next = if (delta > 0) {
            val free = available.amount - plan.total.amount
            if (free <= 0) return null
            current + minOf(delta, free)
        } else {
            if (current == 0) return null
            current - minOf(-delta, current)
        }
        return Coins(next)
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
