package ru.finnypet.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LearningTaskTest {

    private val step = TaskStep.Distribute(promptKey = "task.plan_01.step1", budget = Coins(60))

    private val fallback = TaskOutcome(
        id = "spent_all",
        condition = OutcomeCondition.Otherwise,
        reward = Coins(5),
        explanationKey = "task.plan_01.all_spent",
    )

    private val good = TaskOutcome(
        id = "saved_something",
        condition = OutcomeCondition.SavedAtLeast(Coins(10)),
        reward = Coins(15),
        explanationKey = "task.plan_01.ok",
    )

    private fun task(
        steps: List<TaskStep> = listOf(step),
        outcomes: List<TaskOutcome> = listOf(good, fallback),
    ) = LearningTask(
        id = TaskId("plan_01"),
        topic = TaskTopic.PLANNING,
        introKey = "task.plan_01.intro",
        steps = steps,
        outcomes = outcomes,
    )

    @Test
    fun `тем ровно три`() {
        assertEquals(3, TaskTopic.entries.size)
    }

    @Test
    fun `имена тем не меняются`() {
        assertEquals(listOf("PLANNING", "SAVING", "PAYMENTS"), TaskTopic.entries.map { it.name })
    }

    @Test
    fun `задание находит исход по умолчанию`() {
        assertEquals(fallback, task().fallback)
    }

    @Test
    fun `задание без шагов не допускается`() {
        assertThrows(IllegalArgumentException::class.java) { task(steps = emptyList()) }
    }

    @Test
    fun `задание с одним исходом не допускается`() {
        assertThrows(IllegalArgumentException::class.java) { task(outcomes = listOf(fallback)) }
    }

    @Test
    fun `задание без исхода по умолчанию не допускается`() {
        val onlyConditional = good.copy(id = "other", condition = OutcomeCondition.SpentAtMost(Coins(50)))
        assertThrows(IllegalArgumentException::class.java) { task(outcomes = listOf(good, onlyConditional)) }
    }

    @Test
    fun `два исхода по умолчанию не допускаются`() {
        assertThrows(IllegalArgumentException::class.java) {
            task(outcomes = listOf(fallback, fallback.copy(id = "second")))
        }
    }

    @Test
    fun `повторяющиеся идентификаторы исходов не допускаются`() {
        assertThrows(IllegalArgumentException::class.java) {
            task(outcomes = listOf(good, fallback.copy(id = good.id)))
        }
    }

    @Test
    fun `исход без объяснения не допускается`() {
        assertThrows(IllegalArgumentException::class.java) {
            TaskOutcome(id = "x", condition = OutcomeCondition.Otherwise, reward = Coins(5), explanationKey = "")
        }
    }

    @Test
    fun `выбор с одним вариантом не допускается`() {
        assertThrows(IllegalArgumentException::class.java) {
            TaskStep.Choice(promptKey = "p", options = listOf(TaskOption("a", "task.a")))
        }
    }

    @Test
    fun `распределение нулевого бюджета не допускается`() {
        assertThrows(IllegalArgumentException::class.java) {
            TaskStep.Distribute(promptKey = "p", budget = Coins.ZERO)
        }
    }

    @Test
    fun `выбор товаров из пустого списка не допускается`() {
        assertThrows(IllegalArgumentException::class.java) {
            TaskStep.PickItems(promptKey = "p", itemIds = emptyList(), budget = Coins(60))
        }
    }
}
