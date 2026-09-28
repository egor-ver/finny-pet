package ru.finnypet.app.data.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.finnypet.app.domain.economy.GameClock
import ru.finnypet.app.domain.economy.TaskEngine
import ru.finnypet.app.domain.model.BudgetPlan
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.StepAnswer
import ru.finnypet.app.domain.model.TaskAttempt
import ru.finnypet.app.domain.model.TaskId
import ru.finnypet.app.domain.model.TaskStep

/**
 * Задания настоящего контент-пака (контрольная точка 4 плана): у типичной
 * ошибки своё объяснение, которое называет способ исправить (ТЗ 2.5.9), а
 * верный ответ по-прежнему засчитывается.
 */
class TaskContentTest {

    private val pack = ContentParser().parse(RealContent.raw())
    private val engine = TaskEngine(GameClock { 0L })

    private fun explain(taskId: String, answer: StepAnswer): Pair<String, Boolean> {
        val task = pack.tasks.single { it.id == TaskId(taskId) }
        val outcome = engine.outcomeFor(task, TaskAttempt(listOf(answer)))
        return outcome.explanationKey to outcome.correct
    }

    private fun jars(mandatory: Int, optional: Int, savings: Int) =
        StepAnswer.Allocated(BudgetPlan(mandatory = Coins(mandatory), optional = Coins(optional), savings = Coins(savings)))

    private fun basket(vararg items: Pair<String, Int>) =
        StepAnswer.Picked(itemIds = items.map { it.first }, spent = Coins(items.sumOf { it.second }))

    @Test
    fun `карманные деньги — запас на неожиданность, одной еды мало`() {
        assertEquals("task.plan_pocket_money.saved" to true, explain("plan-pocket-money", jars(25, 10, 5)))
        // Ловушка: положить в «Нужное» только еду — ровно 15, без запаса на мытьё.
        assertEquals("task.plan_pocket_money.no_reserve" to false, explain("plan-pocket-money", jars(15, 15, 10)))
        assertEquals("task.plan_pocket_money.otherwise" to false, explain("plan-pocket-money", jars(10, 20, 10)))
    }

    @Test
    fun `школьный список — яркая вещь не из списка вытесняет нужное`() {
        assertEquals(
            "task.plan_school_supplies.success" to true,
            explain("plan-school-supplies", basket("notebook" to 8, "pens" to 6, "ruler" to 7)),
        )
        listOf("keychain" to 12, "sparkle-pad" to 10, "markers" to 13).forEach { extra ->
            assertEquals(
                extra.first,
                "task.plan_school_supplies.extra_first" to false,
                explain("plan-school-supplies", basket(extra, "notebook" to 8, "pens" to 6)),
            )
        }
        assertEquals("task.plan_school_supplies.otherwise" to false, explain("plan-school-supplies", basket("notebook" to 8)))
    }

    /** Весь список и любая лишняя вещь не помещаются в бюджет: верный ответ нельзя «добрать» ловушкой. */
    @Test
    fun `школьный список с любой лишней вещью не помещается в кошелёк`() {
        val shelf = pack.tasks.single { it.id == TaskId("plan-school-supplies") }.steps.single() as TaskStep.Shelf
        val list = shelf.items.filter { it.id in setOf("notebook", "pens", "ruler") }.sumOf { it.price.amount }
        val cheapestExtra = shelf.items.filterNot { it.id in setOf("notebook", "pens", "ruler") }.minOf { it.price.amount }
        assertTrue(list <= shelf.budget.amount)
        assertTrue(list + cheapestExtra > shelf.budget.amount)
    }

    @Test
    fun `подарок — отложить 12 до цели, мало и ничего`() {
        assertEquals("task.save_gift_coins.success" to true, explain("save-gift-coins", jars(0, 15, 12)))
        assertEquals("task.save_gift_coins.saved_little" to false, explain("save-gift-coins", jars(0, 20, 10)))
        assertEquals("task.save_gift_coins.otherwise" to false, explain("save-gift-coins", jars(0, 30, 0)))
    }

    @Test
    fun `спиннер — стоит 2 дня копилки, «всего на день» и «ничего не изменится» неверны`() {
        assertEquals(
            "task.save_fair_temptation.success" to true,
            explain("save-fair-temptation", StepAnswer.Chosen("two_days_longer")),
        )
        listOf("one_day_longer", "no_change").forEach { trap ->
            assertEquals(
                trap,
                "task.save_fair_temptation.otherwise" to false,
                explain("save-fair-temptation", StepAnswer.Chosen(trap)),
            )
        }
    }

    @Test
    fun `корм в поход — коробка на 3 дня выгоднее пачки с пакетиком и мультика`() {
        assertEquals("task.pay_smart_pack.success" to true, explain("pay-smart-pack", basket("feed_big" to 18)))
        // Коробка с леденцом — тоже верно: фраза success не обещает сдачу.
        assertEquals("task.pay_smart_pack.success" to true, explain("pay-smart-pack", basket("feed_big" to 18, "lollipop" to 5)))
        // Главная ловушка: пачка на 2 дня и пакетик на 1 — три дня за 22, помещается в 25, но дороже.
        assertEquals("task.pay_smart_pack.two_days" to false, explain("pay-smart-pack", basket("feed_two" to 15, "feed_day" to 7)))
        assertEquals("task.pay_smart_pack.cartoon" to false, explain("pay-smart-pack", basket("feed_cartoon" to 10, "feed_day" to 7)))
        assertEquals("task.pay_smart_pack.day" to false, explain("pay-smart-pack", basket("feed_day" to 7, "lollipop" to 5)))
        assertEquals("task.pay_smart_pack.otherwise" to false, explain("pay-smart-pack", basket("lollipop" to 5)))
    }

    @Test
    fun `кино — только билет, набор «только сегодня» и билет с попкорном неверны`() {
        assertEquals("task.pay_cinema_cashier.success" to true, explain("pay-cinema-cashier", StepAnswer.Chosen("only_ticket")))
        assertEquals("task.pay_cinema_cashier.combo" to false, explain("pay-cinema-cashier", StepAnswer.Chosen("combo_with_cup")))
        assertEquals("task.pay_cinema_cashier.otherwise" to false, explain("pay-cinema-cashier", StepAnswer.Chosen("ticket_popcorn")))
    }

    @Test
    fun `разбор — каша и вода, а мячик, ягоды и наклейка вытесняют еду`() {
        val porridge = "food-porridge" to 14
        val water = "water-fresh" to 8
        assertEquals("task.review_hungry.success" to true, explain("review-hungry-owl", basket(porridge, water)))
        assertEquals("task.review_hungry.ball" to false, explain("review-hungry-owl", basket("toy-ball" to 24)))
        assertEquals("task.review_hungry.berry" to false, explain("review-hungry-owl", basket("treat-berry" to 12, water)))
        assertEquals("task.review_hungry.sticker" to false, explain("review-hungry-owl", basket("sticker-star" to 10, porridge)))
        assertEquals("task.review_hungry.otherwise" to false, explain("review-hungry-owl", basket(water)))
    }

    /** Новые исходы дописаны в конец: имена прежних по месту в списке не сдвинулись, прохождения в базе верны. */
    @Test
    fun `верный исход каждого задания остаётся первым`() {
        pack.tasks.forEach { task ->
            assertEquals(task.id.value, "outcome-1", task.outcomes.first().id)
            assertTrue(task.id.value, task.outcomes.first().correct)
        }
    }

    @Test
    fun `у каждого исхода есть текст и неверный исход есть у каждого задания`() {
        val missing = pack.tasks.flatMap { it.outcomes }.map { it.explanationKey }.filterNot(pack.texts::containsKey)
        assertTrue("Нет текста в explanations.json для ключей: $missing", missing.isEmpty())
        pack.tasks.forEach { task -> assertFalse(task.id.value, task.outcomes.all { it.correct }) }
    }
}
