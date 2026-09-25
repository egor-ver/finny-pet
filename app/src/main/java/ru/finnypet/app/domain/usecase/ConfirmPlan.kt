package ru.finnypet.app.domain.usecase

import ru.finnypet.app.domain.economy.BudgetEngine
import ru.finnypet.app.domain.economy.PeriodEngine
import ru.finnypet.app.domain.economy.PlanCheck
import ru.finnypet.app.domain.economy.SavingsEngine
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.PeriodStatus
import ru.finnypet.app.domain.model.ProfileId
import ru.finnypet.app.domain.repository.ActionOutcome
import ru.finnypet.app.domain.repository.ContentRepository
import ru.finnypet.app.domain.repository.OutcomeRecorder
import ru.finnypet.app.domain.repository.PeriodRepository
import ru.finnypet.app.domain.repository.SavingsRepository

/**
 * Подтверждает план дня (ТЗ 2.5.5) и сразу откладывает долю копилки на
 * активную цель (R6, ТЗ 2.5.7).
 *
 * День и копилка пишутся одной записью [OutcomeRecorder] (AD-5): сбой между
 * ними оставил бы план подтверждённым, а копилку пустой — и отложить уже
 * было бы нельзя, потому что план после подтверждения не меняется.
 *
 * Возвращает `false`, когда подтверждать нельзя: день уже идёт, план пустой
 * или не помещается в кошелёк, в копилку запланировано, а цели нет.
 */
class ConfirmPlan(
    private val openPeriod: OpenPeriodIfNeeded,
    private val periods: PeriodRepository,
    private val savings: SavingsRepository,
    private val content: ContentRepository,
    private val budget: BudgetEngine,
    private val periodEngine: PeriodEngine,
    private val savingsEngine: SavingsEngine,
    private val recorder: OutcomeRecorder,
) {

    suspend operator fun invoke(profileId: ProfileId): Boolean {
        val pending = periods.current(profileId) ?: return false
        if (pending.status != PeriodStatus.PLANNING) return false
        // Подготовка дня (доход и событие) должна завершиться до принятия
        // неизменяемого плана, даже если кнопку нажали сразу после входа.
        openPeriod(profileId)
        val period = periods.current(profileId) ?: return false
        if (period.id != pending.id || period.status != PeriodStatus.PLANNING) return false
        val plan = periods.plan(period.id) ?: return false
        if (plan.total == Coins.ZERO) return false

        val wallet = periods.balance(period)
        if (budget.check(plan, wallet) !is PlanCheck.Fits) return false
        val running = periodEngine.confirmPlan(period)

        if (plan.savings == Coins.ZERO) {
            recorder.record(profileId, ActionOutcome(period = running))
            return true
        }
        val progress = savings.activeProgress(profileId) ?: return false
        val goal = content.pack().goals.firstOrNull { it.id == progress.goalId } ?: return false
        val deposit = savingsEngine.deposit(plan.savings, wallet, progress, goal, period.id).value
        recorder.record(
            profileId,
            ActionOutcome(transaction = deposit.transaction, savings = deposit.progress, period = running),
        )
        return true
    }
}
