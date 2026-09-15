package ru.finnypet.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskAttemptTest {

    private fun allocated(mandatory: Int, optional: Int, savings: Int) =
        StepAnswer.Allocated(BudgetPlan(Coins(mandatory), Coins(optional), Coins(savings)))

    @Test
    fun `попытка без ответов не создаётся`() {
        assertThrows(IllegalArgumentException::class.java) { TaskAttempt(emptyList()) }
    }

    @Test
    fun `пустой выбранный вариант не создаётся`() {
        assertThrows(IllegalArgumentException::class.java) { StepAnswer.Chosen("") }
    }

    @Test
    fun `отложенное берётся из накоплений распределения`() {
        assertEquals(Coins(10), TaskAttempt(listOf(allocated(30, 20, 10))).saved)
    }

    @Test
    fun `отложенное складывается по всем распределениям`() {
        val attempt = TaskAttempt(listOf(allocated(30, 20, 10), allocated(10, 5, 15)))
        assertEquals(Coins(25), attempt.saved)
    }

    @Test
    fun `потраченное не считает накопления`() {
        assertEquals(Coins(50), TaskAttempt(listOf(allocated(30, 20, 10))).spent)
    }

    @Test
    fun `потраченное учитывает набранные товары`() {
        val attempt = TaskAttempt(
            listOf(
                allocated(30, 20, 10),
                StepAnswer.Picked(listOf(ItemId("apple")), Coins(25)),
            ),
        )
        assertEquals(Coins(75), attempt.spent)
    }

    @Test
    fun `выбор варианта ничего не тратит и не откладывает`() {
        val attempt = TaskAttempt(listOf(StepAnswer.Chosen("save")))
        assertEquals(Coins.ZERO, attempt.spent)
        assertEquals(Coins.ZERO, attempt.saved)
    }

    @Test
    fun `выбранные варианты собираются в список`() {
        val attempt = TaskAttempt(listOf(StepAnswer.Chosen("save"), StepAnswer.Chosen("wait")))
        assertEquals(listOf("save", "wait"), attempt.chosenOptionIds)
    }

    @Test
    fun `без шагов выбора список вариантов пуст`() {
        assertTrue(TaskAttempt(listOf(allocated(30, 20, 10))).chosenOptionIds.isEmpty())
    }
}
