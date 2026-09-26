package ru.finnypet.app.ui.components

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
}
