package ru.finnypet.app.ui.screens.savings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.finnypet.app.domain.model.Coins

/**
 * Лесенка сумм для кнопок «больше» и «меньше».
 *
 * Граница, не кратная шагу, — обычное дело: баланс после покупок почти
 * никогда не делится на пять. Последняя ступенька должна быть ровно
 * границей, а спуск с неё — на ближайшее кратное, не на «граница минус шаг».
 */
class AmountLadderTest {

    private val max = Coins(12)

    @Test
    fun `первая ступенька — шаг`() {
        assertEquals(Coins(5), AmountLadder.first(max))
    }

    @Test
    fun `граница меньше шага — единственная ступенька`() {
        val tiny = Coins(3)

        assertEquals(tiny, AmountLadder.first(tiny))
        assertFalse(AmountLadder.canGoUp(tiny, tiny))
        assertFalse(AmountLadder.canGoDown(tiny, tiny))
    }

    @Test
    fun `вверх по шагу до границы`() {
        assertEquals(Coins(10), AmountLadder.up(Coins(5), max))
        assertEquals(Coins(12), AmountLadder.up(Coins(10), max))
        assertEquals(Coins(12), AmountLadder.up(Coins(12), max))
        assertFalse(AmountLadder.canGoUp(Coins(12), max))
    }

    @Test
    fun `вниз с границы на ближайшее кратное`() {
        assertEquals(Coins(10), AmountLadder.down(Coins(12), max))
        assertEquals(Coins(5), AmountLadder.down(Coins(10), max))
        assertEquals(Coins(5), AmountLadder.down(Coins(5), max))
        assertFalse(AmountLadder.canGoDown(Coins(5), max))
        assertTrue(AmountLadder.canGoDown(Coins(10), max))
    }

    @Test
    fun `граница кратна шагу — ступеньки ровные`() {
        val even = Coins(20)

        assertEquals(Coins(20), AmountLadder.up(Coins(15), even))
        assertEquals(Coins(15), AmountLadder.down(Coins(20), even))
    }
}
