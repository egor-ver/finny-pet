package ru.finnypet.app.domain.economy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.finnypet.app.domain.model.Coins

class GameBalanceTest {

    private fun balance(
        growthThresholds: List<Int> = listOf(0, 10, 25),
        growthForMandatoryCovered: Int = 2,
        unexpectedExpenseChance: Int = 15,
    ) = GameBalance(
        startingBalance = Coins(100),
        periodIncome = Coins(60),
        taskReward = Coins(15),
        growthForMandatoryCovered = growthForMandatoryCovered,
        growthForPlanFollowed = 2,
        growthForSavingsKept = 1,
        growthThresholds = growthThresholds,
        unexpectedExpenseChance = unexpectedExpenseChance,
        carryOverUnspent = true,
    )

    @Test
    fun `корректный баланс создаётся`() {
        assertEquals(listOf(0, 10, 25), balance().growthThresholds)
    }

    @Test
    fun `меньше трёх порогов не допускается`() {
        assertThrows(IllegalArgumentException::class.java) { balance(growthThresholds = listOf(0, 10)) }
    }

    @Test
    fun `первый порог обязан быть нулевым`() {
        assertThrows(IllegalArgumentException::class.java) { balance(growthThresholds = listOf(5, 10, 25)) }
    }

    @Test
    fun `пороги вразнобой не допускаются`() {
        assertThrows(IllegalArgumentException::class.java) { balance(growthThresholds = listOf(0, 25, 10)) }
    }

    @Test
    fun `повторяющиеся пороги не допускаются`() {
        assertThrows(IllegalArgumentException::class.java) { balance(growthThresholds = listOf(0, 10, 10)) }
    }

    @Test
    fun `отрицательные очки роста не допускаются`() {
        assertThrows(IllegalArgumentException::class.java) { balance(growthForMandatoryCovered = -1) }
    }

    @Test
    fun `шанс непредвиденных расходов больше ста не допускается`() {
        assertThrows(IllegalArgumentException::class.java) { balance(unexpectedExpenseChance = 101) }
    }

    @Test
    fun `шанс непредвиденных расходов меньше нуля не допускается`() {
        assertThrows(IllegalArgumentException::class.java) { balance(unexpectedExpenseChance = -1) }
    }

    @Test
    fun `maxGrowthPerPeriod складывает три источника очков`() {
        assertEquals(5, balance().maxGrowthPerPeriod)
    }

    @Test
    fun `PLACEHOLDER не нарушает собственных инвариантов`() {
        assertTrue(GameBalance.PLACEHOLDER.growthThresholds.size >= 3)
    }

    @Test
    fun `PLACEHOLDER доводит до последней стадии ровно за пять периодов`() {
        val perfect = GameBalance.PLACEHOLDER.maxGrowthPerPeriod * 5
        assertEquals(GameBalance.PLACEHOLDER.growthThresholds.last(), perfect)
    }
}
