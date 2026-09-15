package ru.finnypet.app.domain.economy

import ru.finnypet.app.domain.model.Change
import ru.finnypet.app.domain.model.Explanation
import ru.finnypet.app.domain.model.GameResult
import ru.finnypet.app.domain.model.GrowthStage
import ru.finnypet.app.domain.model.PetGrowth

class GrowthEngine(private val balance: GameBalance) {

    fun pointsFor(report: PlanFactReport): Int =
        (if (report.mandatoryCovered) balance.growthForMandatoryCovered else 0) +
            (if (report.planFollowed) balance.growthForPlanFollowed else 0) +
            (if (report.savingsKept) balance.growthForSavingsKept else 0)

    fun stageFor(points: Int): GrowthStage {
        val index = balance.growthThresholds.indexOfLast { it <= points }
        return GrowthStage.entries[index]
    }

    fun apply(current: PetGrowth, report: PlanFactReport): GameResult<PetGrowth> {
        val earned = pointsFor(report)
        val next = PetGrowth(
            points = current.points + earned,
            stage = stageFor(current.points + earned),
        )
        val grew = next.stage != current.stage
        return GameResult(
            value = next,
            explanation = Explanation(
                key = when {
                    grew -> KEY_STAGE_UP
                    earned > 0 -> KEY_POINTS_ADDED
                    else -> KEY_NO_POINTS
                },
                args = mapOf(
                    "earned" to earned.toString(),
                    "points" to next.points.toString(),
                ),
            ),
            changes = if (grew) listOf(Change.Stage(from = current.stage, to = next.stage)) else emptyList(),
        )
    }

    private companion object {
        const val KEY_STAGE_UP = "growth.stage_up"
        const val KEY_POINTS_ADDED = "growth.points_added"
        const val KEY_NO_POINTS = "growth.no_points"
    }
}
