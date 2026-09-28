package ru.finnypet.app.ui.screens.adult

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.finnypet.app.data.content.ContentParser
import ru.finnypet.app.data.content.RealContent
import java.io.File

/**
 * Раздел взрослого и вход (DESIGN_PLAN 3.11, раздел 4) на настоящих строках
 * интерфейса и настоящем контенте: ко взрослому — на «вы» и одним словом
 * «взрослый», цели игры — от одного субъекта «Игра».
 */
class AdultTextsTest {

    private val strings = stringsXml()
    private val texts = ContentParser().parse(RealContent.raw()).texts

    @Test
    fun `вход обращается ко взрослому на «вы»`() {
        assertEquals("Этот раздел — для взрослого. Решите пример, чтобы открыть его.", strings.getValue("adult_gate_explain"))
        assertEquals("Ответ не подошёл. Попробуйте ещё раз.", strings.getValue("adult_gate_wrong"))
    }

    @Test
    fun `в разделе одно слово — «взрослый», без «родителя»`() {
        val parent = strings.filter { (key, value) -> key.startsWith("adult_") && "родител" in value.lowercase() }
        assertTrue("Строки со словом «родитель»: ${parent.keys}", parent.isEmpty())
    }

    /** Крупный пример не рвётся между числами: «14 ×» на одной строке и «3?» на другой читались бы как два вопроса. */
    @Test
    fun `пример держится одной строкой`() {
        assertEquals(
            "Сколько будет 14 × 3?",
            String.format(strings.getValue("adult_gate_prompt"), 14, 3),
        )
    }

    @Test
    fun `цели игры названы от лица игры`() {
        val about = listOf("adult.about.1", "adult.about.2", "adult.about.3").map(texts::getValue)
        about.forEach { line -> assertTrue("Не от лица игры: $line", line.startsWith("Игра ")) }
    }

    @Test
    fun `свёрнутый раздел «Чему учит игра» показывает только первый абзац`() {
        val about = listOf("первый", "второй", "третий")

        assertEquals(listOf("первый"), aboutShown(about, open = false))
        assertEquals(about, aboutShown(about, open = true))
        assertEquals(listOf("один"), aboutShown(listOf("один"), open = false))
    }

    /** Строки интерфейса из `values/strings.xml`: проверяется настоящий текст, а не копия в тесте. */
    private fun stringsXml(): Map<String, String> {
        val file = File("src/main/res/values/strings.xml")
        return Regex("""<string name="([^"]+)">([^<]*)</string>""")
            .findAll(file.readText())
            .associate { it.groupValues[1] to it.groupValues[2] }
    }
}
