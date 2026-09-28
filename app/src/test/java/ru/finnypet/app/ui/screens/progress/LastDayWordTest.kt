package ru.finnypet.app.ui.screens.progress

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.finnypet.app.R
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.SpendCategory
import ru.finnypet.app.ui.components.BudgetLine

/**
 * Свёрнутая строка «Как прошёл день» (U14+U16): идеальный день и перерасход
 * различаются словом, а не пустыми банками остатка. Числа — эталон раздела 4:
 * день 1 (всё по плану) и день 2 (мяч за 24 при плане желаемого 19).
 */
class LastDayWordTest {

    @Test
    fun `потрачено ровно по плану — по плану`() {
        assertEquals(R.string.progress_day_kept, lastDayWord(line(SpendCategory.MANDATORY, planned = 23, actual = 23)))
    }

    @Test
    fun `трата больше плана — сверх плана`() {
        assertEquals(R.string.progress_day_over, lastDayWord(line(SpendCategory.OPTIONAL, planned = 19, actual = 24)))
    }

    @Test
    fun `в копилку меньше плана — меньше плана, а больше — по плану`() {
        assertEquals(R.string.progress_day_short, lastDayWord(line(SpendCategory.SAVINGS, planned = 11, actual = 5)))
        assertEquals(R.string.progress_day_kept, lastDayWord(line(SpendCategory.SAVINGS, planned = 5, actual = 35)))
    }

    private fun line(category: SpendCategory, planned: Int, actual: Int) = BudgetLine(
        category = category,
        planned = Coins(planned),
        actual = Coins(actual),
        followed = if (category == SpendCategory.SAVINGS) actual >= planned else actual <= planned,
    )
}
