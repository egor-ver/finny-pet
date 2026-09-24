package ru.finnypet.app.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** ТЗ 3.6, AD-8: с выключенным движением экраны сменяются без анимации. */
class ScreenTransitionsTest {

    @Test
    fun `без движения экран появляется и уходит сразу`() {
        assertEquals(EnterTransition.None, screenEnter(motion = false))
        assertEquals(ExitTransition.None, screenExit(motion = false))
    }

    @Test
    fun `с движением экран сменяется плавно`() {
        assertNotEquals(EnterTransition.None, screenEnter(motion = true))
        assertNotEquals(ExitTransition.None, screenExit(motion = true))
    }
}
