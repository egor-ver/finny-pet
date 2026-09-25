package ru.finnypet.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionTest {

    private fun transaction(
        type: TransactionType = TransactionType.PURCHASE_MANDATORY,
        amount: Coins = Coins(25),
        reasonKey: String = "purchase.done",
    ) = Transaction(
        id = 1,
        periodId = 1,
        type = type,
        amount = amount,
        reasonKey = reasonKey,
        createdAt = 0,
    )

    @Test
    fun `по умолчанию не привязана ни к товару ни к цели`() {
        assertNull(transaction().itemId)
        assertNull(transaction().goalId)
    }

    @Test
    fun `пустой ключ объяснения не допускается`() {
        assertThrows(IllegalArgumentException::class.java) { transaction(reasonKey = "") }
    }

    @Test
    fun `доход увеличивает баланс`() {
        assertEquals(60, transaction(TransactionType.INCOME_PERIOD, Coins(60)).balanceDelta)
    }

    @Test
    fun `покупка уменьшает баланс`() {
        assertEquals(-25, transaction(TransactionType.PURCHASE_MANDATORY, Coins(25)).balanceDelta)
    }

    @Test
    fun `пополнение накоплений уменьшает баланс`() {
        assertEquals(-10, transaction(TransactionType.SAVINGS_DEPOSIT, Coins(10)).balanceDelta)
    }

    @Test
    fun `снятие с накоплений увеличивает баланс`() {
        assertEquals(10, transaction(TransactionType.SAVINGS_WITHDRAW, Coins(10)).balanceDelta)
    }

    @Test
    fun `вклад в факт противоположен движению баланса`() {
        val purchase = transaction(TransactionType.PURCHASE_OPTIONAL, Coins(30))
        assertEquals(-purchase.balanceDelta, purchase.factDelta)
    }

    @Test
    fun `пополнение накоплений увеличивает факт по накоплениям`() {
        assertEquals(10, transaction(TransactionType.SAVINGS_DEPOSIT, Coins(10)).factDelta)
    }

    @Test
    fun `снятие с накоплений уменьшает факт по накоплениям`() {
        assertEquals(-10, transaction(TransactionType.SAVINGS_WITHDRAW, Coins(10)).factDelta)
    }

    @Test
    fun `доход не относится ни к одному направлению плана`() {
        assertNull(TransactionType.INCOME_PERIOD.category)
        assertNull(TransactionType.INCOME_TASK.category)
        assertNull(TransactionType.INCOME_PARENT.category)
    }

    @Test
    fun `покупки размечены по своим направлениям`() {
        assertEquals(SpendCategory.MANDATORY, TransactionType.PURCHASE_MANDATORY.category)
        assertEquals(SpendCategory.OPTIONAL, TransactionType.PURCHASE_OPTIONAL.category)
    }

    @Test
    fun `обе операции с накоплениями размечены накоплениями`() {
        assertEquals(SpendCategory.SAVINGS, TransactionType.SAVINGS_DEPOSIT.category)
        assertEquals(SpendCategory.SAVINGS, TransactionType.SAVINGS_WITHDRAW.category)
    }

    @Test
    fun `непредвиденный расход относится к обязательным`() {
        assertEquals(SpendCategory.MANDATORY, TransactionType.UNEXPECTED_EXPENSE.category)
    }

    @Test
    fun `доходом считаются начисления включая подарок, снятие и сдача с цели`() {
        val income = TransactionType.entries.filter { it.isIncome }
        assertEquals(
            listOf(
                TransactionType.INCOME_PERIOD,
                TransactionType.INCOME_TASK,
                TransactionType.INCOME_PARENT,
                TransactionType.INCOME_GIFT,
                TransactionType.SAVINGS_WITHDRAW,
                TransactionType.GOAL_PURCHASE,
            ),
            income,
        )
    }

    /** Цена оплачена копилкой: в кошелёк приходит только сдача, при точной сумме — ноль. */
    @Test
    fun `покупка цели двигает кошелёк только на сдачу и не входит в факт плана`() {
        assertEquals(0, transaction(TransactionType.GOAL_PURCHASE, Coins.ZERO).balanceDelta)
        assertEquals(8, transaction(TransactionType.GOAL_PURCHASE, Coins(8)).balanceDelta)
        assertEquals(null, TransactionType.GOAL_PURCHASE.category)
    }

    @Test
    fun `расходы не считаются доходом`() {
        assertFalse(TransactionType.PURCHASE_MANDATORY.isIncome)
        assertFalse(TransactionType.UNEXPECTED_EXPENSE.isIncome)
        assertFalse(TransactionType.EVENT_CARE.isIncome)
        assertEquals(0, transaction(TransactionType.EVENT_CARE, Coins(10)).balanceDelta)
        assertTrue(TransactionType.SAVINGS_WITHDRAW.isIncome)
    }

    @Test
    fun `имена типов транзакций не меняются`() {
        assertEquals(
            listOf(
                "INCOME_PERIOD", "INCOME_TASK", "INCOME_PARENT", "INCOME_GIFT",
                "PURCHASE_MANDATORY", "PURCHASE_OPTIONAL",
                "SAVINGS_DEPOSIT", "SAVINGS_WITHDRAW",
                "UNEXPECTED_EXPENSE", "EVENT_CARE", "GOAL_PURCHASE",
            ),
            TransactionType.entries.map { it.name },
        )
    }
}
