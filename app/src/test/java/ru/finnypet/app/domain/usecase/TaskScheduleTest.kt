package ru.finnypet.app.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.finnypet.app.domain.economy.GameBalance
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.CompletedTask
import ru.finnypet.app.domain.model.LearningTask
import ru.finnypet.app.domain.model.OutcomeCondition
import ru.finnypet.app.domain.model.TaskId
import ru.finnypet.app.domain.model.TaskOutcome
import ru.finnypet.app.domain.model.TaskStep
import ru.finnypet.app.domain.model.TaskTopic
import ru.finnypet.app.domain.model.Transaction
import ru.finnypet.app.domain.model.TransactionType

/** Правила дня для заданий: лимит наград по операциям и выбор задания дня. */
class TaskScheduleTest {

    private val balance = GameBalance.PLACEHOLDER.copy(rewardedTasksPerPeriod = 1)

    private fun task(id: String) = LearningTask(
        id = TaskId(id),
        topic = TaskTopic.PLANNING,
        introKey = "task.$id.intro",
        steps = listOf(TaskStep.Distribute(promptKey = "task.$id.step", budget = Coins(40))),
        outcomes = listOf(
            TaskOutcome(id = "ok", condition = OutcomeCondition.SavedAtLeast(Coins(10)), reward = Coins(15), explanationKey = "k"),
            TaskOutcome(id = "otherwise", condition = OutcomeCondition.Otherwise, reward = Coins(5), explanationKey = "k2"),
        ),
    )

    private fun transaction(type: TransactionType) = Transaction(
        id = 0,
        periodId = 1,
        type = type,
        amount = Coins(15),
        reasonKey = "k",
        createdAt = 0,
    )

    private fun passed(id: String, at: Long) = CompletedTask(
        taskId = TaskId(id),
        outcomeId = "ok",
        reward = Coins(15),
        completedAt = at,
    )

    @Test
    fun `оплаченные задания считаются по операциям награды`() {
        val transactions = listOf(
            transaction(TransactionType.INCOME_PERIOD),
            transaction(TransactionType.INCOME_TASK),
            transaction(TransactionType.PURCHASE_MANDATORY),
        )

        assertEquals(1, TaskSchedule.paidToday(transactions))
    }

    @Test
    fun `лимит свободен пока оплачено меньше нормы`() {
        assertTrue(TaskSchedule.rewardAvailable(listOf(transaction(TransactionType.INCOME_PERIOD)), balance))
        assertFalse(TaskSchedule.rewardAvailable(listOf(transaction(TransactionType.INCOME_TASK)), balance))
    }

    @Test
    fun `нулевая норма — монет за задания нет вовсе`() {
        assertFalse(TaskSchedule.rewardAvailable(emptyList(), balance.copy(rewardedTasksPerPeriod = 0)))
    }

    @Test
    fun `задание дня — первое непройденное в порядке контента`() {
        val tasks = listOf(task("a"), task("b"), task("c"))

        assertEquals(TaskId("b"), TaskSchedule.taskOfTheDay(tasks, listOf(passed("a", 10)))?.id)
    }

    @Test
    fun `когда все пройдены — то что проходили давнее всех`() {
        val tasks = listOf(task("a"), task("b"))
        // «a» проходили дважды, последний раз позже «b».
        val completed = listOf(passed("a", 10), passed("b", 20), passed("a", 30))

        assertEquals(TaskId("b"), TaskSchedule.taskOfTheDay(tasks, completed)?.id)
    }

    @Test
    fun `без заданий в контенте задания дня нет`() {
        assertNull(TaskSchedule.taskOfTheDay(emptyList(), emptyList()))
    }
}
