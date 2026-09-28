package ru.finnypet.app.ui.screens.savings

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.finnypet.app.data.content.ContentParser
import ru.finnypet.app.data.content.RealContent
import ru.finnypet.app.domain.economy.GameClock
import ru.finnypet.app.domain.economy.SavingsEngine
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.Goal
import ru.finnypet.app.domain.model.GoalId
import ru.finnypet.app.domain.model.GoalProgress
import ru.finnypet.app.ui.text.textOf
import java.io.File

/**
 * Тексты копилки (DESIGN_PLAN 3.7, раздел 4) на настоящем контенте и
 * настоящих строках интерфейса, с подстановкой чисел.
 */
class SavingsTextsTest {

    private val engine = SavingsEngine(GameClock { 0L })
    private val texts = ContentParser().parse(RealContent.raw()).texts

    @Test
    fun `после пополнения говорится о цели, а не о мечте`() {
        val goal = Goal(id = GoalId("goal-book"), titleKey = "goal.book", price = Coins(40))
        val result = engine.deposit(Coins(5), Coins(45), GoalProgress(goalId = goal.id, saved = Coins(1)), goal, periodId = 1)

        assertEquals(
            "Отложили в копилку: 5 монет! Всего накоплено 6, до цели осталось 34.",
            texts.textOf(result.explanation),
        )
    }

    @Test
    fun `вопрос о покупке начинается с названия цели`() {
        val title = stringsXml().getValue("savings_buy_title")

        assertEquals("Набор комиксов — покупаем?", String.format(title, texts.textOf("goal.book")))
    }

    /** Строки интерфейса из `values/strings.xml`: проверяется настоящий текст, а не копия в тесте. */
    private fun stringsXml(): Map<String, String> {
        val file = File("src/main/res/values/strings.xml")
        return Regex("""<string name="([^"]+)">([^<]*)</string>""")
            .findAll(file.readText())
            .associate { it.groupValues[1] to it.groupValues[2] }
    }
}
