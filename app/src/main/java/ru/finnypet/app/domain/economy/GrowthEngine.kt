package ru.finnypet.app.domain.economy

import ru.finnypet.app.domain.model.Change
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.Explanation
import ru.finnypet.app.domain.model.GameResult
import ru.finnypet.app.domain.model.GrowthStage
import ru.finnypet.app.domain.model.GrowthStar
import ru.finnypet.app.domain.model.PetGrowth
import ru.finnypet.app.domain.model.SpendCategory

class GrowthEngine(private val balance: GameBalance) {

    /** Звезда стоит столько очков, сколько задано в balance.json: звёзды и очки — одно и то же. */
    fun pointsFor(report: PlanFactReport, needsMet: Boolean): Int =
        starsFor(report, needsMet).sumOf { star ->
            when (star) {
                GrowthStar.FED -> balance.growthForMandatoryCovered
                GrowthStar.PLAN -> balance.growthForPlanFollowed
                GrowthStar.SAVED -> balance.growthForSavingsKept
            }
        }

    fun stageFor(points: Int): GrowthStage {
        require(points >= 0) { "Очки роста не могут быть отрицательными: $points" }
        return GrowthStage.entries[balance.growthThresholds.indexOfLast { it <= points }]
    }

    /**
     * Стадия берётся как максимум из текущей и посчитанной по очкам: она не падает
     * даже если пороги изменились после того, как прогресс был сохранён (ТЗ 2.2).
     */
    fun apply(current: PetGrowth, report: PlanFactReport, needsMet: Boolean): GameResult<PetGrowth> {
        val earned = pointsFor(report, needsMet)
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

    companion object {

        /**
         * Какие звёзды принёс день (AD-3). В голодный день — ни одной: сова не
         * растёт, если её не кормят, а прогресс не отнимается (ТЗ 2.2).
         *
         * «По плану» — желаемого куплено не больше плана. Нужное сверх плана
         * звезду не отнимает: заботу о сове план не ограничивает (AD-4).
         * «Отложил» — копилка пополнена по плану; нулевой план выполняется сам
         * собой, и звезда за него платила бы за направление, которого ребёнок
         * не касался.
         */
        fun starsFor(report: PlanFactReport, needsMet: Boolean): Set<GrowthStar> {
            if (!needsMet) return emptySet()
            val savings = report.line(SpendCategory.SAVINGS)
            return buildSet {
                add(GrowthStar.FED)
                if (report.line(SpendCategory.OPTIONAL).followed) add(GrowthStar.PLAN)
                if (savings.planned > Coins.ZERO && savings.followed) add(GrowthStar.SAVED)
            }
        }

        private const val KEY_STAGE_UP = "growth.stage_up"
        private const val KEY_POINTS_ADDED = "growth.points_added"
        private const val KEY_NO_POINTS = "growth.no_points"
    }
}
