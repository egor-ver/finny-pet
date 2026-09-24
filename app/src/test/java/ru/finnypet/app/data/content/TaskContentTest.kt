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
    fun `карманные деньги — отдельно про еду и про копилку`() {
        assertEquals("task.plan_pocket_money.saved" to true, explain("plan-pocket-money", jars(20, 10, 10)))
        assertEquals("task.plan_pocket_money.food_short" to false, explain("plan-pocket-money", jars(5, 25, 10)))
        assertEquals("task.plan_pocket_money.no_savings" to false, explain("plan-pocket-money", jars(20, 20, 0)))
        assertEquals("task.plan_pocket_money.otherwise" to false, explain("plan-pocket-money", jars(5, 35, 0)))
    }

    @Test
    fun `рюкзак — украшение вместо нужного и просто неполный`() {
        assertEquals(
            "task.plan_school_supplies.success" to true,
            explain("plan-school-supplies", basket("notebook" to 10, "pens" to 12, "ruler" to 5)),
        )
        assertEquals(
            "task.plan_school_supplies.extra_first" to false,
            explain("plan-school-supplies", basket("keychain" to 15, "notebook" to 10)),
        )
        assertEquals(
            "task.plan_school_supplies.extra_first" to false,
            explain("plan-school-supplies", basket("sparkle-pad" to 14, "pens" to 12)),
        )
        assertEquals("task.plan_school_supplies.otherwise" to false, explain("plan-school-supplies", basket("notebook" to 10)))
    }

    @Test
    fun `подарок — мало отложено и ничего не отложено`() {
        assertEquals("task.save_gift_coins.success" to true, explain("save-gift-coins", jars(5, 10, 15)))
        assertEquals("task.save_gift_coins.saved_little" to false, explain("save-gift-coins", jars(5, 20, 5)))
        assertEquals("task.save_gift_coins.otherwise" to false, explain("save-gift-coins", jars(5, 25, 0)))
    }

    @Test
    fun `сок — маленький, леденец и пустая корзина`() {
        assertEquals("task.pay_smart_pack.success" to true, explain("pay-smart-pack", basket("juice_big" to 18)))
        assertEquals("task.pay_smart_pack.small" to false, explain("pay-smart-pack", basket("juice_small" to 10, "lollipop" to 7)))
        assertEquals("task.pay_smart_pack.candy" to false, explain("pay-smart-pack", basket("lollipop" to 7)))
        assertEquals("task.pay_smart_pack.otherwise" to false, explain("pay-smart-pack", basket()))
    }

    @Test
    fun `разбор — на каждый неверный вариант своё объяснение`() {
        assertEquals("task.review_hungry.success" to true, explain("review-hungry-owl", StepAnswer.Chosen("spent_on_wants")))
        assertEquals("task.review_hungry.taste" to false, explain("review-hungry-owl", StepAnswer.Chosen("dislikes_food")))
        assertEquals("task.review_hungry.shop" to false, explain("review-hungry-owl", StepAnswer.Chosen("shop_empty")))
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
