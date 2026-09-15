package ru.finnypet.app.domain.economy

import ru.finnypet.app.domain.model.Change
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.Explanation
import ru.finnypet.app.domain.model.GameResult
import ru.finnypet.app.domain.model.GrowthStage
import ru.finnypet.app.domain.model.PetGrowth
import ru.finnypet.app.domain.model.SpendCategory

class GrowthEngine(private val balance: GameBalance) {

    fun pointsFor(report: PlanFactReport): Int =
        (if (report.earnsGrowth(SpendCategory.MANDATORY)) balance.growthForMandatoryCovered else 0) +
            (if (report.planFollowed) balance.growthForPlanFollowed else 0) +
            (if (report.earnsGrowth(SpendCategory.SAVINGS)) balance.growthForSavingsKept else 0)

    /**
     * Направление приносит очки, только если по нему было что распределять.
     * Нулевой план выполняется тривиально (0 >= 0), и награждать за него значит
     * платить ребёнку за направление, которого он не касался.
     */
    private fun PlanFactReport.earnsGrowth(category: SpendCategory): Boolean =
        line(category).let { it.planned > Coins.ZERO && it.followed }

    fun stageFor(points: Int): GrowthStage {
        require(points >= 0) { "Очки роста не могут быть отрицательными: $points" }
        return GrowthStage.entries[balance.growthThresholds.indexOfLast { it <= points }]
    }

    /**
     * Стадия берётся как максимум из текущей и посчитанной по очкам: она не падает
     * даже если пороги изменились после того, как прогресс был сохранён (ТЗ 2.2).
     */
    fun apply(current: PetGrowth, report: PlanFactReport): GameResult<PetGrowth> {
        val earned = pointsFor(report)
        val points = current.points + earned
        val stage = maxOf(current.stage, stageFor(points))
        val grew = stage != current.stage

        return GameResult(
            value = PetGrowth(points = points, stage = stage),
            explanation = Explanation(
                key = when {
                    grew -> KEY_STAGE_UP
                    earned > 0 -> KEY_POINTS_ADDED
                    else -> KEY_NO_POINTS
                },
                args = mapOf(
                    "earned" to earned.toString(),
                    "points" to points.toString(),
                ),
            ),
            changes = if (grew) listOf(Change.Stage(from = current.stage, to = stage)) else emptyList(),
        )
    }

    private companion object {
        const val KEY_STAGE_UP = "growth.stage_up"
        const val KEY_POINTS_ADDED = "growth.points_added"
        const val KEY_NO_POINTS = "growth.no_points"
    }
}
