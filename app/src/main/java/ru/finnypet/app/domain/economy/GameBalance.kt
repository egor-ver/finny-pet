package ru.finnypet.app.domain.economy

import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.GrowthStage
import ru.finnypet.app.domain.model.Stat

data class GameBalance(
    val startingBalance: Coins,
    val periodIncome: Coins,
    val taskReward: Coins,
    val initialStat: Int,
    val growthForMandatoryCovered: Int,
    val growthForPlanFollowed: Int,
    val growthForSavingsKept: Int,
    val growthThresholds: List<Int>,
    val unexpectedExpenseChance: Int,
    val carryOverUnspent: Boolean,
) {

    init {
        require(growthThresholds.size == GrowthStage.entries.size) {
            "Порогов должно быть столько же, сколько стадий развития " +
                "(${GrowthStage.entries.size}), задано: ${growthThresholds.size}"
        }
        require(growthThresholds.first() == 0) {
            "Первый порог стадии должен быть нулевым, задан: ${growthThresholds.first()}"
        }
        require(growthThresholds.zipWithNext().all { (a, b) -> a < b }) {
            "Пороги стадий должны строго возрастать, заданы: $growthThresholds"
        }
        require(initialStat in Stat.RANGE) {
            "Стартовый показатель питомца задаётся в пределах ${Stat.RANGE}, задан: $initialStat"
        }
        require(growthForMandatoryCovered >= 0 && growthForPlanFollowed >= 0 && growthForSavingsKept >= 0) {
            "Очки роста не могут быть отрицательными"
        }
        require(unexpectedExpenseChance in 0..100) {
            "Шанс непредвиденных расходов задаётся в процентах, задан: $unexpectedExpenseChance"
        }
    }

    val maxGrowthPerPeriod: Int
        get() = growthForMandatoryCovered + growthForPlanFollowed + growthForSavingsKept

    companion object {

        val PLACEHOLDER = GameBalance(
            startingBalance = Coins(100),
            periodIncome = Coins(60),
            taskReward = Coins(15),
            initialStat = 70,
            growthForMandatoryCovered = 2,
            growthForPlanFollowed = 2,
            growthForSavingsKept = 1,
            growthThresholds = listOf(0, 10, 25),
            unexpectedExpenseChance = 15,
            carryOverUnspent = true,
        )
    }
}
