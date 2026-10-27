package ru.finnypet.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class BudgetPlanTest {

    private val plan = BudgetPlan(Coins(40), Coins(20), Coins(10))

    @Test
    fun `total складывает три направления`() {
        assertEquals(Coins(70), plan.total)
    }

    @Test
    fun `total пустого плана равен нулю`() {
        assertEquals(Coins.ZERO, BudgetPlan.EMPTY.total)
    }

    @Test
    fun `EMPTY обнуляет все три направления`() {
        assertEquals(Coins.ZERO, BudgetPlan.EMPTY.mandatory)
        assertEquals(Coins.ZERO, BudgetPlan.EMPTY.optional)
        assertEquals(Coins.ZERO, BudgetPlan.EMPTY.savings)
    }

    @Test
    fun `amountFor возвращает сумму по каждому направлению`() {
        assertEquals(Coins(40), plan.amountFor(SpendCategory.MANDATORY))
        assertEquals(Coins(20), plan.amountFor(SpendCategory.OPTIONAL))
        assertEquals(Coins(10), plan.amountFor(SpendCategory.SAVINGS))
    }

    @Test
    fun `with меняет только указанное направление`() {
        assertEquals(
            BudgetPlan(Coins(99), Coins(20), Coins(10)),
            plan.with(SpendCategory.MANDATORY, Coins(99)),
        )
        assertEquals(
            BudgetPlan(Coins(40), Coins(99), Coins(10)),
            plan.with(SpendCategory.OPTIONAL, Coins(99)),
        )
        assertEquals(
            BudgetPlan(Coins(40), Coins(20), Coins(99)),
            plan.with(SpendCategory.SAVINGS, Coins(99)),
        )
    }

    @Test
    fun `with не меняет исходный план`() {
        val changed = plan.with(SpendCategory.MANDATORY, Coins(99))
        assertEquals(Coins(40), plan.mandatory)
        assertNotEquals(plan, changed)
    }

    @Test
    fun `with на ту же сумму даёт равный план`() {
        assertEquals(plan, plan.with(SpendCategory.MANDATORY, Coins(40)))
    }

    @Test
    fun `with пересчитывает total`() {
        assertEquals(Coins(130), plan.with(SpendCategory.MANDATORY, Coins(100)).total)
    }

    @Test
    fun `планы с одинаковыми суммами равны`() {
        assertEquals(BudgetPlan(Coins(40), Coins(20), Coins(10)), plan)
    }
}
