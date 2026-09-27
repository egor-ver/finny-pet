package ru.finnypet.app.ui.components

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Путь летящих монет (DESIGN_PLAN 2.7): от источника по дуге точно в цель. */
class CoinFlightTest {

    private val from = Offset(180f, 700f)
    private val to = Offset(60f, 300f)

    @Test
    fun `в начале монета в источнике, в конце — в цели`() {
        assertNear(from, arcPoint(from, to, 0f))
        assertNear(to, arcPoint(from, to, 1f))
    }

    /** Дуга, а не прямая: на середине пути монета выше прямой между точками. */
    @Test
    fun `на середине монета выше прямого пути`() {
        val middle = arcPoint(from, to, 0.5f)
        val straight = (from + to) / 2f
        assertTrue("монета должна подпрыгнуть: ${middle.y} против ${straight.y}", middle.y < straight.y)
    }

    @Test
    fun `монеты вылетают по очереди, а долетают все к концу`() {
        val count = 5
        assertEquals(0f, coinProgress(0f, 0, count))
        assertEquals(0f, coinProgress(0.1f, 4, count))
        assertTrue(coinProgress(0.1f, 0, count) > 0f)
        repeat(count) { assertEquals(1f, coinProgress(1f, it, count)) }
    }

    private fun assertNear(expected: Offset, actual: Offset) {
        assertEquals(expected.x, actual.x, 0.01f)
        assertEquals(expected.y, actual.y, 0.01f)
    }
}
