package ru.finnypet.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.finnypet.app.domain.model.Coins

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
}
