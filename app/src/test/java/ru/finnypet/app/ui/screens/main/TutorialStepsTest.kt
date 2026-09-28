package ru.finnypet.app.ui.screens.main

import androidx.compose.ui.geometry.Rect
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
 * Обучение поверх главного (ТЗ 2.5.1, DESIGN_PLAN 3.4): шесть шагов, среди
 * них «нужное», «желаемое» и «отложить», последний — про план дня; подсветка переходит на запасной
 * элемент, если основного нет в этой фазе дня; облачко не закрывает цель.
 */
class TutorialStepsTest {

    private val texts = ContentParser().parse(RealContent.raw()).texts

    @Test
    fun `шесть шагов, и у каждого есть реплика совы в контент-паке`() {
        assertEquals(6, TUTORIAL_STEPS.size)
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
        assertEquals(listOf(SAVINGS_TILE), all[4])
    }

    /** Решение владельца 28.09: день начинают с плана — последний шаг показывает на него. */
    @Test
    fun `последний шаг — план дня, запасная — плитка План`() {
        val everything = TutorialTarget.entries.toSet()
        assertEquals(listOf(PLAN_BUTTON), TUTORIAL_STEPS.last().resolve(everything))
        assertEquals(listOf(PLAN_TILE), TUTORIAL_STEPS.last().resolve(everything - PLAN_BUTTON))
    }

    /**
     * Решение владельца 28.09: к кнопке плана стрелка шла бы по кнопке задания
     * (между ними 12 dp) — на шаге 6 только подсветка и облачко, у остальных стрелка есть.
     */
    @Test
    fun `на шаге плана стрелки нет, на остальных есть`() {
        assertEquals(listOf(true, true, true, true, true, false), TUTORIAL_STEPS.map { it.arrow })
    }

    @Test
    fun `первый экран новой игры здоровается`() {
        assertTrue(!texts["owl.say.hello"].isNullOrBlank())
    }

    @Test
    fun `утром подсвечиваются кнопки задания и плана`() {
        val everything = TutorialTarget.entries.toSet()
        assertEquals(listOf(WALLET, TASK_BUTTON), TUTORIAL_STEPS[1].resolve(everything))
        assertEquals(listOf(PLAN_BUTTON), TUTORIAL_STEPS[5].resolve(everything))
    }

    /** По «?» после плана кнопок задания и плана нет — показываем на плитки. */
    @Test
    fun `без кнопок дня — запасные плитки`() {
        val afterPlan = TutorialTarget.entries.toSet() - TASK_BUTTON - PLAN_BUTTON
        assertEquals(listOf(WALLET, TASKS_TILE), TUTORIAL_STEPS[1].resolve(afterPlan))
        assertEquals(listOf(PLAN_TILE), TUTORIAL_STEPS[5].resolve(afterPlan))
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
        assertEquals(5, nextTutorialStep(4))
        assertNull(nextTutorialStep(5))
    }

    /**
     * Ревью F1, шаг про план: кнопка плана в самом низу. Облачко встаёт
     * вплотную над ней, оставив место на стрелку, а не посреди экрана —
     * иначе стрелка шла бы сквозь кнопку задания и плитки.
     */
    @Test
    fun `цель внизу — облачко вплотную над ней`() {
        val top = panelTop(listOf(600f..700f), panelHeight = 300f, height = 800f, reach = 28f)
        assertEquals(600f - 28f - 300f, top, 0.01f)
    }

    @Test
    fun `цель вверху — облачко вплотную под ней`() {
        val top = panelTop(listOf(0f..60f), panelHeight = 300f, height = 800f, reach = 28f)
        assertEquals(60f + 28f, top, 0.01f)
    }

    /** Цель посреди экрана: облачко в большем промежутке и вплотную к цели. */
    @Test
    fun `цель посередине — облачко вплотную в большем промежутке`() {
        val top = panelTop(listOf(500f..560f), panelHeight = 300f, height = 800f, reach = 28f)
        assertEquals(500f - 28f - 300f, top, 0.01f)
    }

    /** Шаг «доход»: кошелёк сверху и кнопка задания снизу — облачко между ними. */
    @Test
    fun `цели сверху и снизу — облачко посередине`() {
        val top = panelTop(listOf(0f..60f, 680f..760f), panelHeight = 300f, height = 800f, reach = 28f)
        assertEquals(220f, top, 0.01f)
    }

    @Test
    fun `без целей — облачко по центру`() {
        assertEquals(250f, panelTop(emptyList(), panelHeight = 300f, height = 800f, reach = 28f), 0.01f)
    }

    /** Крупный шрифт: облачко выше свободного места — не за краем экрана, кнопки видны. */
    @Test
    fun `облачко не помещается — остаётся в пределах экрана`() {
        val top = panelTop(listOf(300f..400f), panelHeight = 700f, height = 800f, reach = 28f)
        assertTrue("за краем: $top", top >= 0f && top + 700f <= 800f)
        assertEquals(0f, panelTop(listOf(0f..800f), panelHeight = 900f, height = 800f, reach = 28f), 0.01f)
    }

    /** Кошелёк над облачком: дуга выходит из верха облачка, кончик — у нижнего края выреза. */
    @Test
    fun `цель выше — стрелка из верха облачка к нижнему краю выреза`() {
        val bubble = Rect(140f, 300f, 344f, 540f)
        val wallet = Rect(10f, 10f, 100f, 70f)
        val arc = arrowArc(bubble, wallet, inset = 28f, gap = 2f)!!

        assertEquals(bubble.top, arc.start.y, 0.01f)
        assertTrue(arc.start.x in 168f..316f)
        assertEquals(wallet.bottom + 2f, arc.tip.y, 0.01f)
        assertTrue(arc.tip.x in 38f..72f)
        // Одна дуга без перегиба: изгиб между началом и кончиком, выход отвесный.
        assertEquals(arc.start.x, arc.control.x, 0.01f)
        assertTrue(arc.control.y < arc.start.y && arc.control.y > arc.tip.y)
    }

    /** Кнопка задания под облачком: стрелка из низа облачка, кнопки внутри облачка ей не мешают. */
    @Test
    fun `цель ниже — стрелка из низа облачка к верхнему краю выреза`() {
        val bubble = Rect(140f, 100f, 344f, 340f)
        val button = Rect(10f, 620f, 350f, 690f)
        val arc = arrowArc(bubble, button, inset = 28f, gap = 2f)!!

        assertEquals(bubble.bottom, arc.start.y, 0.01f)
        assertEquals(button.top - 2f, arc.tip.y, 0.01f)
        // Широкая цель прямо под облачком — стрелка отвесная, без лишнего изгиба.
        assertEquals(arc.start.x, arc.tip.x, 0.01f)
    }

    @Test
    fun `вырез на одной высоте с облачком — стрелки нет`() {
        assertNull(arrowArc(Rect(140f, 300f, 344f, 540f), Rect(10f, 400f, 100f, 460f), inset = 28f, gap = 2f))
    }

    /** Узкая цель уже двух отступов — кончик по её середине, а не исключение. */
    @Test
    fun `узкая цель — кончик по середине`() {
        val arc = arrowArc(Rect(140f, 300f, 344f, 540f), Rect(20f, 10f, 60f, 70f), inset = 28f, gap = 2f)!!
        assertEquals(40f, arc.tip.x, 0.01f)
    }
}
