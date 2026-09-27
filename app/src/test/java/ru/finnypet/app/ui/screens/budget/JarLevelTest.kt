package ru.finnypet.app.ui.screens.budget

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.SpendCategory
import ru.finnypet.app.ui.components.BudgetLine

/**
 * Уровень банки после подтверждения (DESIGN_PLAN 3.2): «осталось / было».
 * Числа — эталонный день 1 (раздел 4 плана): нужное 23, куплено на 23.
 */
class JarLevelTest {

    @Test
    fun `утром ничего не куплено — банка полная`() {
        assertEquals(1f, jarLevel(line(planned = 23, actual = 0)))
    }

    @Test
    fun `потрачена часть — уровень равен доле остатка`() {
        assertEquals(0.5f, jarLevel(line(planned = 22, actual = 11)), 0.001f)
    }

    @Test
    fun `потрачено всё — банка пустая`() {
        assertEquals(0f, jarLevel(line(planned = 23, actual = 23)))
    }

    /** День 2 эталона: мяч за 24 при плане желаемого 19 — пусто, а не «минус». */
    @Test
    fun `сверх плана — банка пустая, не отрицательная`() {
        assertEquals(0f, jarLevel(line(planned = 19, actual = 24, category = SpendCategory.OPTIONAL)))
    }

    @Test
    fun `в банку ничего не планировали — пустая, без деления на ноль`() {
        assertEquals(0f, jarLevel(line(planned = 0, actual = 0)))
    }

    private fun line(planned: Int, actual: Int, category: SpendCategory = SpendCategory.MANDATORY) =
        BudgetLine(category, Coins(planned), Coins(actual), followed = actual <= planned)
}
