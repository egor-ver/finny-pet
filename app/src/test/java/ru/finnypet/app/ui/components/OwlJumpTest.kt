package ru.finnypet.app.ui.components

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** ТЗ 2.5.9 и 3.6: сова подпрыгивает, когда ей стало лучше, и только с включённым движением. */
class OwlJumpTest {

    @Test
    fun `после покупки еды сова подпрыгивает`() {
        assertTrue(shouldJump(before = 150, after = 175, motion = true))
    }

    @Test
    fun `без движения сова не прыгает`() {
        assertFalse(shouldJump(before = 150, after = 175, motion = false))
    }

    /** Ночь и новый день радости не добавляют — прыгать не с чего. */
    @Test
    fun `когда стало хуже или не изменилось, прыжка нет`() {
        assertFalse(shouldJump(before = 175, after = 125, motion = true))
        assertFalse(shouldJump(before = 175, after = 175, motion = true))
    }

    /** Копилка не меняет показатели, поэтому реакция на успех — отдельный повод для прыжка (U2). */
    @Test
    fun `реакция на появление срабатывает только с движением`() {
        assertTrue(shouldReact(reactOnAppear = true, motion = true))
        assertFalse(shouldReact(reactOnAppear = true, motion = false))
        assertFalse(shouldReact(reactOnAppear = false, motion = true))
    }

    /** DESIGN_PLAN 3.3: сова откликается на новый окрас или аксессуар, но не на первый показ экрана. */
    @Test
    fun `новый выбор внешности — прыжок, тот же выбор или без движения — нет`() {
        assertTrue(shouldReactToChange(before = "cream", after = "black", motion = true))
        assertFalse(shouldReactToChange(before = "cream", after = "cream", motion = true))
        assertFalse(shouldReactToChange(before = "cream", after = "black", motion = false))
    }

    /**
     * Ревью U12: быстрый перевыбор A→B→A отменяет прыжок к B на середине, как
     * LaunchedEffect при смене ключа. Возврат к A всё равно должен дать прыжок.
     */
    @Test
    fun `быстрый перевыбор прерывает прыжок и начинает новый`() = runTest {
        var chosen: Any? = "cream"
        var jumps = 0
        val jump: suspend () -> Unit = { jumps++; delay(500) }

        val toBlack = launch { reactToChoice(chosen, "black", motion = true, onChosen = { chosen = it }, jump = jump) }
        advanceTimeBy(100)
        toBlack.cancel()
        reactToChoice(chosen, "cream", motion = true, onChosen = { chosen = it }, jump = jump)

        assertEquals(2, jumps)
        assertEquals("cream", chosen)
    }
}
