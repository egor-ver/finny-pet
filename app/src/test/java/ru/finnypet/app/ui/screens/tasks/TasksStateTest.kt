package ru.finnypet.app.ui.screens.tasks

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.finnypet.app.domain.content.ContentOption
import ru.finnypet.app.domain.content.PetColor
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.GrowthStage
import ru.finnypet.app.domain.model.OutcomeCondition
import ru.finnypet.app.domain.model.LearningTask
import ru.finnypet.app.domain.model.PetMood
import ru.finnypet.app.domain.model.TaskId
import ru.finnypet.app.domain.model.TaskOption
import ru.finnypet.app.domain.model.TaskOutcome
import ru.finnypet.app.domain.model.TaskStep
import ru.finnypet.app.domain.model.TaskTopic
import ru.finnypet.app.ui.components.OwlLook

/**
 * Список заданий (DESIGN_PLAN 3.9): «Пройдено 2 из 6» и «1 из 2» у темы
 * считаются по тем же отметкам, что стоят на карточках, а справа на
 * карточке — отметка (с наградой, если за повтор платят), награда или «тренировка».
 */
class TasksStateTest {

    @Test
    fun `пройдено считается по всем темам и по каждой отдельно`() {
        val state = ready(
            TaskGroup(TaskTopic.PLANNING, listOf(row("a", completed = true), row("b", completed = true))),
            TaskGroup(TaskTopic.SAVING, listOf(row("c", completed = false), row("d", completed = true))),
            TaskGroup(TaskTopic.PAYMENTS, listOf(row("e", completed = false), row("f", completed = false))),
        )

        assertEquals(3, state.passed)
        assertEquals(6, state.total)
        assertEquals(listOf(2, 1, 0), state.groups.map { it.passed })
    }

    @Test
    fun `пустой список — ноль из нуля, без деления и падений`() {
        val state = ready()

        assertEquals(0, state.passed)
        assertEquals(0, state.total)
    }

    /** Шапка обещает «Сегодня: +10» — пройденное, за которое снова платят, показывает награду рядом с отметкой. */
    @Test
    fun `пройденное задание отмечено и с наградой, если за него снова платят`() {
        assertEquals(TaskBadge.Passed(Coins(10)), row("a", completed = true, reward = Coins(10)).badge)
        assertEquals(TaskBadge.Passed(null), row("a", completed = true, reward = null).badge)
    }

    @Test
    fun `непройденное — награда, а без неё тренировка`() {
        assertEquals(TaskBadge.Reward(Coins(10)), row("a", completed = false, reward = Coins(10)).badge)
        assertEquals(TaskBadge.Training, row("a", completed = false, reward = null).badge)
    }

    /** «до +15» на вступлении и «+15» в списке — наибольшая из верных наград, неверные не в счёт. */
    @Test
    fun `наибольшая награда берётся только из верных исходов`() {
        val task = LearningTask(
            id = TaskId("t"),
            topic = TaskTopic.SAVING,
            introKey = "i",
            steps = listOf(TaskStep.Choice(promptKey = "p", options = listOf(TaskOption("a", "l"), TaskOption("b", "l")))),
            outcomes = listOf(
                TaskOutcome("best", OutcomeCondition.OptionChosen("a"), Coins(15), "e", correct = true),
                TaskOutcome("fine", OutcomeCondition.OptionChosen("b"), Coins(10), "e", correct = true),
                TaskOutcome("other", OutcomeCondition.Otherwise, Coins(20), "e"),
            ),
        )

        assertEquals(Coins(15), task.maxReward)
    }

    private fun row(id: String, completed: Boolean, reward: Coins? = null) =
        TaskRow(id = TaskId(id), topic = TaskTopic.PLANNING, intro = "Задание $id", completed = completed, reward = reward)

    private fun ready(vararg groups: TaskGroup) = TasksState.Ready(
        groups = groups.toList(),
        owl = OwlLook(
            colors = PetColor(option = ContentOption("cream", "pet.color.cream"), body = 0, wing = 0, face = 0, ring = 0),
            stage = GrowthStage.CUB,
            mood = PetMood.CALM,
            accessoryId = null,
            description = "",
        ),
        rewardAvailable = true,
        reward = Coins(10),
    )
}
