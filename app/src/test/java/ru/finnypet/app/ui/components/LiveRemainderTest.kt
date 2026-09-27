package ru.finnypet.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.finnypet.app.domain.model.BudgetPlan
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.SpendCategory

/**
 * Живой остаток плана (DESIGN_PLAN 3.2, правка владельца №8): число меняется,
 * пока палец тянет ползунок, а не после сохранения в базу. Эталонный день 3
 * (раздел 4 плана): в кошельке 48, в базе уже 30 на нужное, свободно 18.
 */
class LiveRemainderTest {

    private val plan = BudgetPlan(Coins(30), Coins.ZERO, Coins.ZERO)
    private val remainder = Coins(18)

    @Test
    fun `без черновиков — остаток из базы`() {
        assertEquals(Coins(18), liveRemainder(plan, remainder, emptyMap()))
    }

    @Test
    fun `тянет нужное вверх — остаток уменьшается сразу`() {
        assertEquals(Coins(11), liveRemainder(plan, remainder, mapOf(SpendCategory.MANDATORY to Coins(37))))
    }

    @Test
    fun `тянет нужное вниз — освободившиеся монеты видны сразу`() {
        assertEquals(Coins(28), liveRemainder(plan, remainder, mapOf(SpendCategory.MANDATORY to Coins(20))))
    }

    @Test
    fun `новая банка из нуля забирает из остатка`() {
        assertEquals(Coins(10), liveRemainder(plan, remainder, mapOf(SpendCategory.SAVINGS to Coins(8))))
    }

    /** Ползунок упирается в свободные монеты: остаток не уходит в минус. */
    @Test
    fun `черновик дальше свободных монет — остаток ноль, не минус`() {
        assertEquals(Coins.ZERO, liveRemainder(plan, remainder, mapOf(SpendCategory.OPTIONAL to Coins(40))))
    }

    @Test
    fun `черновик равен сохранённому — остаток прежний`() {
        assertEquals(Coins(18), liveRemainder(plan, remainder, mapOf(SpendCategory.MANDATORY to Coins(30))))
    }

    @Test
    fun `всё разложено и нужное убрали до нуля — вернулись все его монеты`() {
        val full = BudgetPlan(Coins(30), Coins(10), Coins(8))
        assertEquals(Coins(30), liveRemainder(full, Coins.ZERO, mapOf(SpendCategory.MANDATORY to Coins.ZERO)))
    }
}
