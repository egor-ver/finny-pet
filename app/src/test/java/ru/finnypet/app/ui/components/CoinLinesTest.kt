package ru.finnypet.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.finnypet.app.R
import ru.finnypet.app.domain.model.Coins
import java.io.File

/**
 * Строки с числом монет после предлога и остатки банок (ревью F6):
 * настоящий текст из `values/strings.xml`, а не копия в тесте.
 */
class CoinLinesTest {

    private val strings: Map<String, String> = Regex("""<string name="([^"]+)">([^<]*)</string>""")
        .findAll(File("src/main/res/values/strings.xml").readText())
        .associate { it.groupValues[1] to it.groupValues[2] }

    /** «Сверх плана на» требует винительного падежа: «на 1 монету», а не «на 1 монета». */
    @Test
    fun `сверх плана — в винительном падеже при любом числе`() {
        val names = mapOf(
            R.string.budget_status_over_one to "budget_status_over_one",
            R.string.budget_status_over_few to "budget_status_over_few",
            R.string.budget_status_over_many to "budget_status_over_many",
        )

        fun line(amount: Int): String =
            String.format(strings.getValue(names.getValue(overPlanLine(Coins(amount)))), amount)

        assertEquals("Сверх плана на 1 монету", line(1))
        assertEquals("Сверх плана на 2 монеты", line(2))
        assertEquals("Сверх плана на 5 монет", line(5))
        assertEquals("Сверх плана на 11 монет", line(11))
        assertEquals("Сверх плана на 21 монету", line(21))
        assertEquals("Сверх плана на 44 монеты", line(44))
    }

    /** Недобор копилки в итогах — та же строка, что нехватка в магазине: «Не хватает 1 монеты». */
    @Test
    fun `недобор копилки — в родительном падеже`() {
        assertEquals("Не хватает %1\$d монеты", strings.getValue("shortage_one"))
        assertEquals(R.string.shortage_one, shortageLine(Coins(1)))
    }

    /** На главном те же слова, что на плане и в магазине: «осталось 23», а не «ещё 23». */
    @Test
    fun `остаток банки на главном — теми же словами, что на плане`() {
        assertEquals(strings.getValue("budget_jar_left"), strings.getValue("main_jar_left"))
        assertEquals("%1\$s: осталось %2\$d", strings.getValue("main_jar_left_description"))
    }
}
