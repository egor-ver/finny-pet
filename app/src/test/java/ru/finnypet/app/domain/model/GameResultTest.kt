package ru.finnypet.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameResultTest {

    private val explanation = Explanation("period.closed")

    @Test
    fun `по умолчанию изменений нет`() {
        val result = GameResult(value = Coins(50), explanation = explanation)
        assertTrue(result.changes.isEmpty())
    }

    @Test
    fun `результат хранит значение и объяснение`() {
        val result = GameResult(value = Coins(50), explanation = explanation)
        assertEquals(Coins(50), result.value)
        assertEquals(explanation, result.explanation)
    }

    @Test
    fun `результат хранит список изменений`() {
        val change = Change.Balance(from = Coins(100), to = Coins(75))
        val result = GameResult(value = Coins(75), explanation = explanation, changes = listOf(change))
        assertEquals(listOf(change), result.changes)
    }

    @Test
    fun `изменение баланса в минус даёт отрицательную дельту`() {
        assertEquals(-25, Change.Balance(from = Coins(100), to = Coins(75)).delta)
    }

    @Test
    fun `изменение баланса в плюс даёт положительную дельту`() {
        assertEquals(15, Change.Balance(from = Coins(60), to = Coins(75)).delta)
    }

    @Test
    fun `изменение баланса без движения даёт нулевую дельту`() {
        assertEquals(0, Change.Balance(from = Coins(75), to = Coins(75)).delta)
    }

    @Test
    fun `пополнение накоплений даёт положительную дельту`() {
        assertEquals(10, Change.Savings(from = Coins(20), to = Coins(30)).delta)
    }

    @Test
    fun `снятие с накоплений даёт отрицательную дельту`() {
        assertEquals(-10, Change.Savings(from = Coins(30), to = Coins(20)).delta)
    }

    @Test
    fun `одно действие может нести несколько изменений`() {
        val result = GameResult(
            value = Coins(65),
            explanation = explanation,
            changes = listOf(
                Change.Balance(from = Coins(75), to = Coins(65)),
                Change.Savings(from = Coins(20), to = Coins(30)),
            ),
        )
        assertEquals(2, result.changes.size)
    }
}
