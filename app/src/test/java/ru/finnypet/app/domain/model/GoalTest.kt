package ru.finnypet.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GoalTest {

    private val goal = Goal(id = GoalId("bike"), titleKey = "goal.bike", price = Coins(100))

    private fun progress(saved: Coins) = GoalProgress(goalId = GoalId("bike"), saved = saved)

    @Test
    fun `пустой ключ названия не допускается`() {
        assertThrows(IllegalArgumentException::class.java) {
            Goal(GoalId("bike"), titleKey = "", price = Coins(100))
        }
    }

    @Test
    fun `цель со стоимостью ноль не допускается`() {
        assertThrows(IllegalArgumentException::class.java) {
            Goal(GoalId("bike"), titleKey = "goal.bike", price = Coins.ZERO)
        }
    }

    @Test
    fun `новый прогресс пуст и не активен`() {
        val fresh = GoalProgress(goalId = GoalId("bike"))
        assertEquals(Coins.ZERO, fresh.saved)
        assertFalse(fresh.isActive)
    }

    @Test
    fun `остаток равен разнице когда накоплено меньше`() {
        assertEquals(Coins(40), progress(Coins(60)).remaining(goal))
    }

    @Test
    fun `остаток равен нулю когда накоплено ровно`() {
        assertEquals(Coins.ZERO, progress(Coins(100)).remaining(goal))
    }

    @Test
    fun `остаток равен нулю когда накоплено больше`() {
        assertEquals(Coins.ZERO, progress(Coins(120)).remaining(goal))
    }

    @Test
    fun `цель достигнута при точном совпадении`() {
        assertTrue(progress(Coins(100)).isReached(goal))
    }

    @Test
    fun `цель не достигнута при нехватке одной монеты`() {
        assertFalse(progress(Coins(99)).isReached(goal))
    }

    @Test
    fun `цель достигнута при излишке`() {
        assertTrue(progress(Coins(120)).isReached(goal))
    }

    @Test
    fun `прогресс от чужой цели не принимается`() {
        val other = GoalProgress(goalId = GoalId("scooter"), saved = Coins(50))
        assertThrows(IllegalArgumentException::class.java) { other.remaining(goal) }
    }

    @Test
    fun `проверка достижения чужой цели не принимается`() {
        val other = GoalProgress(goalId = GoalId("scooter"), saved = Coins(50))
        assertThrows(IllegalArgumentException::class.java) { other.isReached(goal) }
    }
}
