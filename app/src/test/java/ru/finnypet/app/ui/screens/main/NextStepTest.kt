package ru.finnypet.app.ui.screens.main

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.finnypet.app.domain.model.BudgetPlan
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.PeriodFact
import ru.finnypet.app.domain.model.PeriodStatus
import ru.finnypet.app.domain.model.SpendCategory

/**
 * Следующий шаг идёт по циклу Приложения А: план, задание, нужное, копилка, итоги.
 * В магазин зовут потребности совы, а не недотраченный план (R3).
 */
class NextStepTest {

    private val plan = BudgetPlan(mandatory = Coins(20), optional = Coins(10), savings = Coins(15))

    @Test
    fun `пока день планируется — план`() {
        assertEquals(NextStep.Plan, step(PeriodStatus.PLANNING, taskReward = true))
    }

    @Test
    fun `день идёт и награда за задание ждёт — задание`() {
        assertEquals(NextStep.Task, step(taskReward = true))
    }

    @Test
    fun `после задания — магазин на цену закрытия потребностей`() {
        assertEquals(NextStep.Shop(Coins(37)), step(needs = 37))
    }

    /** Находка на vivo: сова сыта, а кнопка звала потратить остаток плана на нужное. */
    @Test
    fun `сова сыта, а план на нужное не потрачен — не магазин`() {
        assertEquals(NextStep.Save(Coins(15)), step(fact = fact(mandatory = 8)))
    }

    @Test
    fun `нужное куплено — копилка на остаток по плану`() {
        assertEquals(NextStep.Save(Coins(5)), step(fact = fact(mandatory = 20, savings = 10)))
    }

    /** Все монеты потрачены — звать в магазин и копилку незачем, это тупик. */
    @Test
    fun `без монет — итоги не по плану, а не магазин и копилка`() {
        assertEquals(NextStep.Finish(onPlan = false), step(fact = fact(mandatory = 8), balance = 0, needs = 22))
    }

    @Test
    fun `на нужное не хватает даже на самое дешёвое — сразу копилка`() {
        assertEquals(NextStep.Save(Coins(5)), step(fact = fact(mandatory = 8), balance = 5, needs = 22))
    }

    @Test
    fun `в копилку предлагается не больше, чем есть`() {
        assertEquals(NextStep.Save(Coins(3)), step(fact = fact(mandatory = 20), balance = 3))
    }

    @Test
    fun `желаемое сверх плана — итоги не по плану`() {
        assertEquals(NextStep.Finish(onPlan = false), step(fact = fact(mandatory = 20, optional = 11, savings = 15)))
    }

    /** Желаемое не шаг: отказ от него ТЗ не считает ошибкой. */
    @Test
    fun `нужное и копилка по плану — итоги, даже без желаемого`() {
        assertEquals(NextStep.Finish(onPlan = true), step(fact = fact(mandatory = 20, savings = 15)))
    }

    @Test
    fun `сэкономил на нужном — всё равно по плану`() {
        assertEquals(NextStep.Finish(onPlan = true), step(fact = fact(mandatory = 14, savings = 15)))
    }

    @Test
    fun `нужное сверх плана — итоги не по плану`() {
        assertEquals(NextStep.Finish(onPlan = false), step(fact = fact(mandatory = 25, savings = 15)))
    }

    private fun step(
        status: PeriodStatus = PeriodStatus.RUNNING,
        fact: PeriodFact = fact(),
        balance: Int = 50,
        needs: Int = 0,
        taskReward: Boolean = false,
    ) = nextStep(
        status = status,
        plan = plan,
        fact = fact,
        balance = Coins(balance),
        needs = Coins(needs),
        cheapestMandatory = CHEAPEST_MANDATORY,
        taskRewardAvailable = taskReward,
    )

    private fun fact(mandatory: Int = 0, optional: Int = 0, savings: Int = 0) = PeriodFact(
        mapOf(
            SpendCategory.MANDATORY to Coins(mandatory),
            SpendCategory.OPTIONAL to Coins(optional),
            SpendCategory.SAVINGS to Coins(savings),
        ),
    )

    private companion object {
        val CHEAPEST_MANDATORY = Coins(8)
    }
}
