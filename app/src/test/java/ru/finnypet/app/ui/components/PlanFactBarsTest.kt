package ru.finnypet.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.SpendCategory

/**
 * Двойная полоса итогов (DESIGN_PLAN 3.6): трек — план, заливка — факт.
 * Числа — эталонный сценарий (раздел 4 плана).
 */
class PlanFactBarsTest {

    /** День 1: нужное 23 из 23 — трек и заливка во всю ширину. */
    @Test
    fun `потрачено ровно по плану — обе полосы полные`() {
        assertEquals(PlanFactFractions(plan = 1f, fact = 1f), planFactFractions(Coins(23), Coins(23)))
    }

    /** День 1: желаемое 10 из 11 — план во всю ширину, заливка чуть короче. */
    @Test
    fun `экономия — трек полный, заливка короче`() {
        val fractions = planFactFractions(Coins(11), Coins(10))

        assertEquals(1f, fractions.plan)
        assertEquals(10f / 11, fractions.fact, 0.001f)
    }

    /** День 2: мяч за 24 при плане желаемого 19 — заливка полная, трек кончается там, где кончился план. */
    @Test
    fun `сверх плана — заливка полная, трек показывает, где кончился план`() {
        val fractions = planFactFractions(Coins(19), Coins(24))

        assertEquals(19f / 24, fractions.plan, 0.001f)
        assertEquals(1f, fractions.fact)
    }

    /** День 2: нужное не планировали и не покупали — пустой трек во всю ширину, без деления на ноль. */
    @Test
    fun `ничего не планировали и не тратили — пустой трек`() {
        assertEquals(PlanFactFractions(plan = 1f, fact = 0f), planFactFractions(Coins.ZERO, Coins.ZERO))
    }

    @Test
    fun `не планировали, но потратили — только заливка`() {
        assertEquals(PlanFactFractions(plan = 0f, fact = 1f), planFactFractions(Coins.ZERO, Coins(8)))
    }

    /**
     * Итоги дня 2 (ревью F5): копилку не планировали, а отложили 44 — полоса
     * полная, и подпись «по плану» спорила со строкой «Пополнение не
     * планировали». Сверх плана копилки — так и сказано.
     */
    @Test
    fun `копилку не планировали, а отложили — сверх плана`() {
        assertEquals(PlanFactStatus.OVER, planFactStatus(savings(planned = 0, actual = 44)))
        assertEquals(PlanFactStatus.OK, planFactStatus(savings(planned = 0, actual = 0)))
        assertEquals(PlanFactStatus.OK, planFactStatus(savings(planned = 5, actual = 35)))
        assertEquals(PlanFactStatus.SHORT, planFactStatus(savings(planned = 11, actual = 5)))
    }

    @Test
    fun `трата сверх плана — сверх плана, в пределах — по плану`() {
        assertEquals(PlanFactStatus.OVER, planFactStatus(BudgetLine(SpendCategory.OPTIONAL, Coins(19), Coins(24), followed = false)))
        assertEquals(PlanFactStatus.OK, planFactStatus(BudgetLine(SpendCategory.OPTIONAL, Coins(0), Coins(0), followed = true)))
    }

    private fun savings(planned: Int, actual: Int) =
        BudgetLine(SpendCategory.SAVINGS, Coins(planned), Coins(actual), followed = actual >= planned)
}
