package ru.finnypet.app.domain.economy

import ru.finnypet.app.domain.model.Change
import ru.finnypet.app.domain.model.Explanation
import ru.finnypet.app.domain.model.GameResult
import ru.finnypet.app.domain.model.PetEffect
import ru.finnypet.app.domain.model.PetState
import ru.finnypet.app.domain.model.PetStatKind
import ru.finnypet.app.domain.model.RecoveryOption

class PetStateEngine(private val balance: GameBalance) {

    fun apply(state: PetState, effects: List<PetEffect>): GameResult<PetState> {
        var next = state
        effects.forEach { effect ->
            next = next.with(effect.stat, next.statFor(effect.stat) + effect.delta)
        }
        return GameResult(
            value = next,
            explanation = Explanation(key = KEY_CHANGED),
            changes = changesBetween(state, next),
        )
    }

    fun onPeriodClosed(state: PetState, report: PlanFactReport): GameResult<PetState> {
        var next = state
        if (!report.mandatoryCovered) {
            next = next
                .with(PetStatKind.SATIETY, next.satiety - balance.statPenaltyMissedMandatory)
                .with(PetStatKind.CARE, next.care - balance.statPenaltyMissedMandatory)
        }
        if (report.planFollowed) {
            next = next.with(PetStatKind.MOOD, next.mood + balance.moodBonusPlanFollowed)
        }
        return GameResult(
            value = next,
            explanation = explanationFor(report),
            changes = changesBetween(state, next),
        )
    }

    private fun explanationFor(report: PlanFactReport): Explanation = when {
        !report.mandatoryCovered -> Explanation(
            key = KEY_MISSED_MANDATORY,
            nextStep = RecoveryOption.ADJUST_NEXT_PLAN,
        )

        report.planFollowed -> Explanation(key = KEY_PLAN_FOLLOWED)

        else -> Explanation(key = KEY_PERIOD_CLOSED)
    }

    private fun changesBetween(from: PetState, to: PetState): List<Change> =
        PetStatKind.entries.mapNotNull { kind ->
            val before = from.statFor(kind)
            val after = to.statFor(kind)
            if (before == after) null else Change.PetStat(kind = kind, from = before, to = after)
        }

    private companion object {
        const val KEY_CHANGED = "pet.state_changed"
        const val KEY_MISSED_MANDATORY = "pet.missed_mandatory"
        const val KEY_PLAN_FOLLOWED = "pet.plan_followed"
        const val KEY_PERIOD_CLOSED = "pet.period_closed"
    }
}
