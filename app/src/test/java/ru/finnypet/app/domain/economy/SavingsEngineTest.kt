package ru.finnypet.app.domain.economy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.finnypet.app.domain.model.Change
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.Goal
import ru.finnypet.app.domain.model.GoalId
import ru.finnypet.app.domain.model.GoalProgress
import ru.finnypet.app.domain.model.TransactionType

class SavingsEngineTest {

    private val now = 1_700_000_000L
    private val engine = SavingsEngine(GameClock { now })

    private val goal = Goal(id = GoalId("bike"), titleKey = "goal.bike", price = Coins(100))

    private fun progress(saved: Coins) = GoalProgress(goalId = GoalId("bike"), saved = saved)

    @Test
    fun `пополнение увеличивает накопления`() {
        val result = engine.deposit(Coins(20), Coins(60), progress(Coins(30)), goal, periodId = 1)
        assertEquals(Coins(50), result.value.progress.saved)
    }

    @Test
    fun `пополнение уменьшает баланс`() {
        val result = engine.deposit(Coins(20), Coins(60), progress(Coins(30)), goal, periodId = 1)
        assertEquals(Coins(40), result.value.balance)
    }

    @Test
    fun `пополнение сообщает оба изменения`() {
        val result = engine.deposit(Coins(20), Coins(60), progress(Coins(30)), goal, periodId = 1)
        assertEquals(
            listOf(
                Change.Balance(from = Coins(60), to = Coins(40)),
                Change.Savings(from = Coins(30), to = Coins(50)),
            ),
            result.changes,
        )
    }

    @Test
    fun `пополнение порождает транзакцию своего типа с привязкой к цели`() {
        val result = engine.deposit(Coins(20), Coins(60), progress(Coins(30)), goal, periodId = 7)
        val transaction = result.value.transaction
        assertEquals(TransactionType.SAVINGS_DEPOSIT, transaction.type)
        assertEquals(GoalId("bike"), transaction.goalId)
        assertEquals(7L, transaction.periodId)
        assertEquals(now, transaction.createdAt)
    }

    @Test
    fun `пополнение ровно на остаток достигает цели`() {
        val result = engine.deposit(Coins(40), Coins(60), progress(Coins(60)), goal, periodId = 1)
        assertTrue(result.value.goalReached)
    }

    @Test
    fun `пополнение сверх остатка тоже достигает цели и излишек сохраняется`() {
        val result = engine.deposit(Coins(50), Coins(60), progress(Coins(60)), goal, periodId = 1)
        assertTrue(result.value.goalReached)
        assertEquals(Coins(110), result.value.progress.saved)
    }

    @Test
    fun `пополнение до цели не достигает её`() {
        val result = engine.deposit(Coins(39), Coins(60), progress(Coins(60)), goal, periodId = 1)
        assertFalse(result.value.goalReached)
    }

    @Test
    fun `объяснение меняет ключ при достижении цели`() {
        val short = engine.deposit(Coins(10), Coins(60), progress(Coins(30)), goal, periodId = 1)
        val reached = engine.deposit(Coins(40), Coins(60), progress(Coins(60)), goal, periodId = 1)
        assertEquals("savings.deposited", short.explanation.key)
        assertEquals("savings.goal_reached", reached.explanation.key)
    }

    @Test
    fun `объяснение несёт сумму накопленное и остаток`() {
        val result = engine.deposit(Coins(20), Coins(60), progress(Coins(30)), goal, periodId = 1)
        assertEquals("20", result.explanation.args["amount"])
        assertEquals("50", result.explanation.args["saved"])
        assertEquals("50", result.explanation.args["remaining"])
    }

    @Test
    fun `нельзя отложить больше чем есть на балансе`() {
        assertThrows(IllegalArgumentException::class.java) {
            engine.deposit(Coins(61), Coins(60), progress(Coins(30)), goal, periodId = 1)
        }
    }

    @Test
    fun `нельзя отложить ноль`() {
        assertThrows(IllegalArgumentException::class.java) {
            engine.deposit(Coins.ZERO, Coins(60), progress(Coins(30)), goal, periodId = 1)
        }
    }

    @Test
    fun `срок делит остаток на средний взнос`() {
        assertEquals(4, engine.periodsToGoal(progress(Coins(60)), goal, avgDeposit = Coins(10)))
    }

    @Test
    fun `срок округляется вверх`() {
        assertEquals(5, engine.periodsToGoal(progress(Coins(55)), goal, avgDeposit = Coins(10)))
    }

    @Test
    fun `срок равен нулю когда цель уже достигнута`() {
        assertEquals(0, engine.periodsToGoal(progress(Coins(100)), goal, avgDeposit = Coins(10)))
    }

    @Test
    fun `срок не определён без регулярных пополнений`() {
        assertNull(engine.periodsToGoal(progress(Coins(60)), goal, avgDeposit = Coins.ZERO))
    }

    @Test
    fun `превью снятия показывает накопления до и после`() {
        val preview = engine.previewWithdraw(Coins(20), progress(Coins(60)), goal, avgDeposit = Coins(10))
        assertEquals(Coins(60), preview.savingsBefore)
        assertEquals(Coins(40), preview.savingsAfter)
    }

    @Test
    fun `превью снятия показывает как отодвигается срок`() {
        val preview = engine.previewWithdraw(Coins(20), progress(Coins(60)), goal, avgDeposit = Coins(10))
        assertEquals(4, preview.periodsBefore)
        assertEquals(6, preview.periodsAfter)
    }

    @Test
    fun `превью не показывает срок без регулярных пополнений`() {
        val preview = engine.previewWithdraw(Coins(20), progress(Coins(60)), goal, avgDeposit = Coins.ZERO)
        assertNull(preview.periodsBefore)
        assertNull(preview.periodsAfter)
    }

    @Test
    fun `нельзя посмотреть превью снятия сверх накопленного`() {
        assertThrows(IllegalArgumentException::class.java) {
            engine.previewWithdraw(Coins(61), progress(Coins(60)), goal, avgDeposit = Coins(10))
        }
    }

    @Test
    fun `снятие уменьшает накопления и увеличивает баланс`() {
        val result = engine.withdraw(Coins(20), Coins(10), progress(Coins(60)), goal, periodId = 1)
        assertEquals(Coins(40), result.value.progress.saved)
        assertEquals(Coins(30), result.value.balance)
    }

    @Test
    fun `снятие порождает транзакцию своего типа`() {
        val result = engine.withdraw(Coins(20), Coins(10), progress(Coins(60)), goal, periodId = 1)
        assertEquals(TransactionType.SAVINGS_WITHDRAW, result.value.transaction.type)
    }

    @Test
    fun `снятие сообщает оба изменения`() {
        val result = engine.withdraw(Coins(20), Coins(10), progress(Coins(60)), goal, periodId = 1)
        assertEquals(
            listOf(
                Change.Balance(from = Coins(10), to = Coins(30)),
                Change.Savings(from = Coins(60), to = Coins(40)),
            ),
            result.changes,
        )
    }

    @Test
    fun `нельзя снять больше чем накоплено`() {
        assertThrows(IllegalArgumentException::class.java) {
            engine.withdraw(Coins(61), Coins(10), progress(Coins(60)), goal, periodId = 1)
        }
    }

    @Test
    fun `снятие ровно всех накоплений допустимо`() {
        val result = engine.withdraw(Coins(60), Coins(10), progress(Coins(60)), goal, periodId = 1)
        assertEquals(Coins.ZERO, result.value.progress.saved)
    }

    @Test
    fun `снятие из перенакопленной копилки сохраняет признак достигнутой цели`() {
        val result = engine.withdraw(Coins(10), Coins.ZERO, progress(Coins(150)), goal, periodId = 1)
        assertTrue(result.value.goalReached)
    }

    @Test
    fun `снятие ниже цены цели снимает признак достижения`() {
        val result = engine.withdraw(Coins(10), Coins.ZERO, progress(Coins(105)), goal, periodId = 1)
        assertFalse(result.value.goalReached)
    }

    @Test
    fun `нельзя посмотреть превью снятия нуля`() {
        assertThrows(IllegalArgumentException::class.java) {
            engine.previewWithdraw(Coins.ZERO, progress(Coins(60)), goal, avgDeposit = Coins(10))
        }
    }

    @Test
    fun `нельзя снять ноль`() {
        assertThrows(IllegalArgumentException::class.java) {
            engine.withdraw(Coins.ZERO, Coins(10), progress(Coins(60)), goal, periodId = 1)
        }
    }
}
