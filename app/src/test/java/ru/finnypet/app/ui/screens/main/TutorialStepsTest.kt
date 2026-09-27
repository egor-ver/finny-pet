package ru.finnypet.app.ui.screens.main

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.finnypet.app.data.content.ContentParser
import ru.finnypet.app.data.content.RealContent
import ru.finnypet.app.ui.screens.main.TutorialTarget.CARE
import ru.finnypet.app.ui.screens.main.TutorialTarget.MOOD
import ru.finnypet.app.ui.screens.main.TutorialTarget.PLAN_BUTTON
import ru.finnypet.app.ui.screens.main.TutorialTarget.PLAN_TILE
import ru.finnypet.app.ui.screens.main.TutorialTarget.SATIETY
import ru.finnypet.app.ui.screens.main.TutorialTarget.SAVINGS_TILE
import ru.finnypet.app.ui.screens.main.TutorialTarget.TASKS_TILE
import ru.finnypet.app.ui.screens.main.TutorialTarget.TASK_BUTTON
import ru.finnypet.app.ui.screens.main.TutorialTarget.WALLET

/**
 * Обучение поверх главного (ТЗ 2.5.1, DESIGN_PLAN 3.4): пять шагов, среди
 * них «нужное», «желаемое» и «отложить»; подсветка переходит на запасной
 * элемент, если основного нет в этой фазе дня; облачко не закрывает цель.
 */
class TutorialStepsTest {

    private val texts = ContentParser().parse(RealContent.raw()).texts

    @Test
    fun `пять шагов, и у каждого есть реплика совы в контент-паке`() {
        assertEquals(5, TUTORIAL_STEPS.size)
        TUTORIAL_STEPS.forEach { step ->
            val text = texts[step.textKey]
            assertTrue("нет текста ${step.textKey}", !text.isNullOrBlank())
            // Чисел в обучении нет: при пустой цели и нулевых суммах текст не врёт.
            assertFalse("подстановка в ${step.textKey}: $text", text!!.contains('{'))
        }
    }

    /** ТЗ 2.5.1: нужное, желаемое и отложить — обязательные шаги обучения. */
    @Test
    fun `шаги нужного, желаемого и копилки показывают свои элементы`() {
        val all = TUTORIAL_STEPS.map { step -> step.spots.map { it.main } }
        assertEquals(listOf(SATIETY, CARE), all[2])
        assertEquals(listOf(MOOD), all[3])
        assertTrue(SAVINGS_TILE in all[4])
    }

    @Test
    fun `первый экран новой игры здоровается`() {
        assertTrue(!texts["owl.say.hello"].isNullOrBlank())
    }

    @Test
    fun `утром подсвечиваются кнопки задания и плана`() {
        val everything = TutorialTarget.entries.toSet()
        assertEquals(listOf(WALLET, TASK_BUTTON), TUTORIAL_STEPS[1].resolve(everything))
        assertEquals(listOf(SAVINGS_TILE, PLAN_BUTTON), TUTORIAL_STEPS[4].resolve(everything))
    }

    /** По «?» после плана кнопок задания и плана нет — показываем на плитки. */
    @Test
    fun `без кнопок дня — запасные плитки`() {
        val afterPlan = TutorialTarget.entries.toSet() - TASK_BUTTON - PLAN_BUTTON
        assertEquals(listOf(WALLET, TASKS_TILE), TUTORIAL_STEPS[1].resolve(afterPlan))
        assertEquals(listOf(SAVINGS_TILE, PLAN_TILE), TUTORIAL_STEPS[4].resolve(afterPlan))
    }

    /** Заданий в контент-паке нет — плитки тоже нет, шаг идёт без этой стрелки. */
    @Test
    fun `нет ни основного, ни запасного — подсветка пропускается`() {
        val noTasks = TutorialTarget.entries.toSet() - TASK_BUTTON - TASKS_TILE
        assertEquals(listOf(WALLET), TUTORIAL_STEPS[1].resolve(noTasks))
        assertEquals(emptyList<TutorialTarget>(), TUTORIAL_STEPS[0].resolve(emptySet()))
    }

    @Test
    fun `дальше по шагам, после последнего обучение закрывается`() {
        assertEquals(1, nextTutorialStep(0))
        assertEquals(4, nextTutorialStep(3))
        assertNull(nextTutorialStep(4))
    }

    @Test
    fun `цель внизу — облачко над ней`() {
        val top = panelTop(listOf(600f..700f), panelHeight = 300f, height = 800f)
        assertTrue("облачко закрыло цель: $top", top + 300f <= 600f)
    }

    @Test
    fun `цель вверху — облачко под ней`() {
        val top = panelTop(listOf(0f..60f), panelHeight = 300f, height = 800f)
        assertTrue("облачко закрыло цель: $top", top >= 60f)
    }

    /** Шаг «доход»: кошелёк сверху и кнопка задания снизу — облачко между ними. */
    @Test
    fun `цели сверху и снизу — облачко посередине`() {
        val top = panelTop(listOf(0f..60f, 680f..760f), panelHeight = 300f, height = 800f)
        assertTrue("облачко закрыло цель: $top", top >= 60f && top + 300f <= 680f)
    }

    @Test
    fun `без целей — облачко по центру`() {
        assertEquals(250f, panelTop(emptyList(), panelHeight = 300f, height = 800f), 0.01f)
    }

    /** Крупный шрифт: облачко выше свободного места — не за краем экрана, кнопки видны. */
    @Test
    fun `облачко не помещается — остаётся в пределах экрана`() {
        val top = panelTop(listOf(300f..400f), panelHeight = 700f, height = 800f)
        assertTrue("за краем: $top", top >= 0f && top + 700f <= 800f)
        assertEquals(0f, panelTop(listOf(0f..800f), panelHeight = 900f, height = 800f), 0.01f)
    }
}
