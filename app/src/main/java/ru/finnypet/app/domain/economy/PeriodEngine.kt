package ru.finnypet.app.domain.economy

import ru.finnypet.app.domain.model.BudgetPlan
import ru.finnypet.app.domain.model.Change
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.Explanation
import ru.finnypet.app.domain.model.GamePeriod
import ru.finnypet.app.domain.model.GameResult
import ru.finnypet.app.domain.model.PeriodFact
import ru.finnypet.app.domain.model.PeriodStatus
import ru.finnypet.app.domain.model.PetGrowth
import ru.finnypet.app.domain.model.PetState
import ru.finnypet.app.domain.model.RecoveryOption
import ru.finnypet.app.domain.model.Transaction

data class PeriodOutcome(
    val closedPeriod: GamePeriod,
    val report: PlanFactReport,
    val state: PetState,
    val growth: PetGrowth,
    val carryOver: Coins,
)

class PeriodEngine(
    private val budget: BudgetEngine,
    private val pet: PetStateEngine,
    private val growth: GrowthEngine,
    private val balance: GameBalance,
    private val clock: GameClock,
) {

    fun factOf(transactions: List<Transaction>): PeriodFact = transactions
        .mapNotNull { transaction -> transaction.type.category?.let { it to transaction.factDelta } }
        .groupBy({ (category, _) -> category }, { (_, delta) -> delta })
        .mapValues { (_, deltas) -> Coins(deltas.sum().coerceAtLeast(0)) }
        .let(::PeriodFact)

    fun close(
        period: GamePeriod,
        plan: BudgetPlan,
        transactions: List<Transaction>,
        state: PetState,
        currentGrowth: PetGrowth,
    ): GameResult<PeriodOutcome> {
        val report = budget.compare(plan, factOf(transactions))
        val petResult = pet.onPeriodClosed(state, report)
        val growthResult = growth.apply(currentGrowth, report)
        val carryOver = carryOverOf(period, transactions)
        val stageChanged = growthResult.changes.any { it is Change.Stage }

        return GameResult(
            value = PeriodOutcome(
                closedPeriod = period.copy(status = PeriodStatus.CLOSED, closedAt = clock.now()),
                report = report,
                state = petResult.value,
                growth = growthResult.value,
                carryOver = carryOver,
            ),
            explanation = headline(report, stageChanged),
            changes = petResult.changes + growthResult.changes,
        )
    }

    fun openNext(previous: GamePeriod, carryOver: Coins): GamePeriod = GamePeriod(
        id = UNSAVED,
        profileId = previous.profileId,
        number = previous.number + 1,
        income = balance.periodIncome,
        startBalance = carryOver,
        status = PeriodStatus.PLANNING,
    )

    private fun carryOverOf(period: GamePeriod, transactions: List<Transaction>): Coins {
        val left = period.startBalance.amount + transactions.sumOf { it.balanceDelta }
        require(left >= 0) {
            "Баланс периода ${period.number} ушёл в минус ($left): проверь, что покупки идут через WalletEngine"
        }
        return if (balance.carryOverUnspent) Coins(left) else Coins.ZERO
    }

    private fun headline(report: PlanFactReport, stageChanged: Boolean): Explanation = when {
        !report.mandatoryCovered -> Explanation(
            key = KEY_MISSED_MANDATORY,
            nextStep = RecoveryOption.ADJUST_NEXT_PLAN,
        )

        stageChanged -> Explanation(key = KEY_STAGE_UP)

        report.planFollowed -> Explanation(key = KEY_PLAN_FOLLOWED)

        else -> Explanation(key = KEY_CLOSED)
    }

    private companion object {
        const val UNSAVED = 0L
        const val KEY_MISSED_MANDATORY = "period.missed_mandatory"
        const val KEY_STAGE_UP = "period.stage_up"
        const val KEY_PLAN_FOLLOWED = "period.plan_followed"
        const val KEY_CLOSED = "period.closed"
    }
}
