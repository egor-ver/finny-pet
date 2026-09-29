package ru.finnypet.app.ui.screens.tasks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.finnypet.app.R
import ru.finnypet.app.domain.economy.GameBalance
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.CompletedTask
import ru.finnypet.app.domain.model.PetMood
import ru.finnypet.app.domain.model.TaskId
import ru.finnypet.app.domain.model.Transaction
import ru.finnypet.app.domain.model.TransactionType
import ru.finnypet.app.domain.usecase.TaskSchedule

/**
 * Итог задания на эталонном сценарии (раздел 4 плана, шаг А6): сначала
 * неверный ответ — монет нет, затем другое задание верно — +10.
 */
class TaskResultTest {

    /** F7: «Шаг 1 из 1» ничего не сообщает — счётчик только у задания из нескольких шагов. */
    @Test
    fun `счётчик шагов только у задания из нескольких шагов`() {
        val step = StepView.Choice(prompt = "?", options = emptyList(), chosen = null)

        assertFalse(TaskStage.Step(index = 0, total = 1, step = step).counted)
        assertTrue(TaskStage.Step(index = 0, total = 2, step = step).counted)
    }

    @Test
    fun `верно с первой попытки — плюс десять в кошелёк, сова радуется`() {
        assertEquals(RewardLine.PAID, rewardLine(correct = true, paid = Coins(10)))
        assertEquals(PetMood.HAPPY, taskMood(correct = true))
    }

    @Test
    fun `неверно — названо правило первой попытки, сова спокойна, а не грустит`() {
        assertEquals(RewardLine.FIRST_TRY_RULE, rewardLine(correct = false, paid = Coins.ZERO))
        assertEquals(PetMood.CALM, taskMood(correct = false))
    }

    /** Повтор после ошибки или лимит дня: ответ верный, но правило первой попытки тут было бы неправдой при лимите. */
    @Test
    fun `верно без монет — без правила первой попытки`() {
        assertEquals(RewardLine.NO_COINS, rewardLine(correct = true, paid = Coins.ZERO))
    }

    /** U10+U11: число в кошельке меняется, когда монеты долетели, а не до полёта. */
    @Test
    fun `кошелёк в шапке — без награды до прилёта монет`() {
        assertEquals(Coins(45), shownBalance(Coins(55), reward = Coins(10), landed = false))
        assertEquals(Coins(55), shownBalance(Coins(55), reward = Coins(10), landed = true))
        assertEquals(Coins(55), shownBalance(Coins(55), reward = Coins.ZERO, landed = false))
    }

    /**
     * Ревью F7: ошибка утром при свободном лимите — монет сегодня не давали,
     * поэтому не «монеты получены», а «за это задание монет уже не будет».
     */
    @Test
    fun `ошибка утром при невыбранном лимите — без монет только это задание`() {
        val balance = GameBalance.PLACEHOLDER.copy(rewardedTasksPerPeriod = 1)
        val morning = listOf(
            Transaction(id = 0, periodId = 1, type = TransactionType.INCOME_PERIOD, amount = Coins(30), reasonKey = "k", createdAt = 100),
        )
        val wrong = listOf(CompletedTask(TaskId("a"), outcomeId = "otherwise", reward = Coins.ZERO, completedAt = 105))

        assertFalse(TaskSchedule.rewardable(TaskId("a"), wrong, morning, balance))
        assertEquals(R.string.task_retry_note, noCoinsNote(limitReached = !TaskSchedule.rewardAvailable(morning, balance)))
    }

    @Test
    fun `лимит дня выбран — монеты за сегодня получены`() {
        assertEquals(R.string.task_training_note, noCoinsNote(limitReached = true))
    }
}
