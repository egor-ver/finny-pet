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
import ru.finnypet.app.ui.components.goalChange
import ru.finnypet.app.ui.components.goalOverfilled
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
            "Отложили в копилку: 5\u00A0монет! Всего накоплено 6, до цели осталось 34.",
            texts.textOf(result.explanation),
        )
    }

    @Test
    fun `вопрос о покупке начинается с названия цели`() {
        val title = stringsXml().getValue("savings_buy_title")

        assertEquals("Набор комиксов — покупаем?", String.format(title, texts.textOf("goal.book")))
    }

    /**
     * Сверх цены копить можно, но «Накоплено 75 из 70» выглядело ошибкой
     * (ревью F3-fix): экран говорит «хватает» и называет сдачу — ровно ту, что
     * покупка вернёт в кошелёк.
     */
    @Test
    fun `накоплено сверх цели — «хватает» и сдача, которую вернёт покупка`() {
        val goal = Goal(id = GoalId("goal-game"), titleKey = "goal.game", price = Coins(70))
        val bought = engine.buy(Coins(55), GoalProgress(goalId = goal.id, saved = Coins(75)), goal, periodId = 1)
        val strings = stringsXml()

        assertEquals(Coins(5), goalChange(Coins(75), goal.price))
        assertEquals(Coins(55) + goalChange(Coins(75), goal.price), bought.value.balance)
        assertEquals(Coins.ZERO, goalChange(Coins(70), goal.price))
        assertEquals(Coins.ZERO, goalChange(Coins(30), goal.price))
        assertEquals("Накоплено 75 — хватает на цель!", String.format(strings.getValue("savings_saved_enough"), 75))
        assertEquals(
            "После покупки сдача 5 монет вернётся в кошелёк.",
            String.format(strings.getValue("savings_change"), "5 монет"),
        )
    }

    /**
     * Плитка «Копилка» на главном и озвучка цели на главном и в «Моём
     * прогрессе» при накоплено больше цены тоже не называют «44 из 40»
     * (ревью F5); на плитке короткое «Хватает!» — длинная фраза переносилась и плитка
     * становилась выше соседней «Рост» (ревью F5, круг 2). При ровно цене и меньше — прежние «6/40» и «6 из 40».
     */
    @Test
    fun `накоплено сверх цели — на главном и в озвучке «хватает на цель» без числа больше цены`() {
        val strings = stringsXml()
        val price = Coins(40)

        assertEquals(true, goalOverfilled(Coins(44), price))
        assertEquals(false, goalOverfilled(Coins(40), price))
        assertEquals(false, goalOverfilled(Coins(6), price))
        assertEquals("Хватает!", strings.getValue("main_jar_goal_enough"))
        assertEquals(
            "Копилка: Набор комиксов, накоплено 44 — хватает на цель",
            String.format(strings.getValue("main_jar_goal_enough_description"), "Набор комиксов", 44),
        )
        assertEquals(
            "Цель «Набор комиксов»: накоплено 44 — хватает на цель",
            String.format(strings.getValue("main_goal_progress_enough"), "Набор комиксов", 44),
        )
    }

    /** Строки интерфейса из `values/strings.xml`: проверяется настоящий текст, а не копия в тесте. */
    private fun stringsXml(): Map<String, String> {
        val file = File("src/main/res/values/strings.xml")
        return Regex("""<string name="([^"]+)">([^<]*)</string>""")
            .findAll(file.readText())
            .associate { it.groupValues[1] to it.groupValues[2] }
    }
}
