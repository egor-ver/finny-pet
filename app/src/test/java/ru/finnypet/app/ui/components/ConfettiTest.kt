package ru.finnypet.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Конфетти на смену стадии (DESIGN_PLAN 2.7): один проход сверху вниз, без случайности. */
class ConfettiTest {

    private val pieces = 0 until CONFETTI_PIECES

    @Test
    fun `в начале все кусочки над слоем или у верхнего края`() {
        assertTrue(pieces.all { confettiPiece(it, 0f).y <= 0f })
    }

    /** Эффект разовый: к концу хода кусочки у нижнего края и погасли, повторов нет. */
    @Test
    fun `к концу все кусочки внизу и погасли`() {
        pieces.forEach { index ->
            val end = confettiPiece(index, 1f)
            assertEquals(1f, end.y, 0.001f)
            assertEquals(0f, end.alpha, 0.001f)
        }
    }

    @Test
    fun `кусочки разбросаны по всей ширине и не выходят далеко за края`() {
        val xs = pieces.map { confettiPiece(it, 0.5f).x }

        assertTrue(xs.all { it in -0.05f..1.05f })
        assertTrue("кусочки сбились в кучу", xs.maxOrNull()!! - xs.minOrNull()!! > 0.8f)
    }

    /** Без случайности: один и тот же ход даёт один и тот же рисунок. */
    @Test
    fun `рисунок одинаков при каждом показе`() {
        assertEquals(pieces.map { confettiPiece(it, 0.3f) }, pieces.map { confettiPiece(it, 0.3f) })
    }
}
