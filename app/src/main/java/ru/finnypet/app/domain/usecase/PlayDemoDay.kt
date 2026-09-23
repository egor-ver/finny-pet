package ru.finnypet.app.domain.usecase

import ru.finnypet.app.domain.economy.PeriodEngine
import ru.finnypet.app.domain.economy.PurchaseResult
import ru.finnypet.app.domain.economy.SavingsEngine
import ru.finnypet.app.domain.economy.WalletEngine
import ru.finnypet.app.domain.model.BudgetPlan
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.GamePeriod
import ru.finnypet.app.domain.model.PeriodStatus
import ru.finnypet.app.domain.model.ProfileId
import ru.finnypet.app.domain.model.ShopItem
import ru.finnypet.app.domain.model.SpendCategory
import ru.finnypet.app.domain.repository.ActionOutcome
import ru.finnypet.app.domain.repository.ContentRepository
import ru.finnypet.app.domain.repository.OutcomeRecorder
import ru.finnypet.app.domain.repository.PeriodRepository
import ru.finnypet.app.domain.repository.ProfileRepository
import ru.finnypet.app.domain.repository.SavingsRepository

/**
 * Проживает день демонстрации целиком (ТЗ 2.5.13): эксперт видит пять
 * периодов и три стадии роста за пять нажатий.
 *
 * Ничего не имитирует — план, покупка, копилка и закрытие идут через те же
 * движки и записи, что и действия ребёнка. Играет только тестовый профиль:
 * без него это пустое действие, и день ребёнка сюда не попадёт.
 */
class PlayDemoDay(
    private val profiles: ProfileRepository,
    private val periods: PeriodRepository,
    private val savings: SavingsRepository,
    private val content: ContentRepository,
    private val openPeriod: OpenPeriodIfNeeded,
    private val closeDay: CloseDay,
    private val wallet: WalletEngine,
    private val savingsEngine: SavingsEngine,
    private val periodEngine: PeriodEngine,
    private val recorder: OutcomeRecorder,
) {

    suspend operator fun invoke() {
        val profileId = profiles.testProfile()?.id ?: return
        val period = openPeriod(profileId)
        val need = cheapestMandatory(period.available)
        val plan = planOf(period, need)

        need?.let { buy(profileId, period, it) }
        deposit(profileId, period, plan.savings)
        closeDay(profileId)
    }

    /**
     * День уже спланирован — берём его план: эксперт мог распределить монеты
     * руками и только потом нажать «прожить день».
     */
    private suspend fun planOf(period: GamePeriod, need: ShopItem?): BudgetPlan {
        if (period.status == PeriodStatus.RUNNING) {
            periods.plan(period.id)?.let { return it }
        }
        val plan = newPlan(period.available, need?.price ?: Coins.ZERO)
        periods.savePlan(period.id, plan)
        periods.save(periodEngine.confirmPlan(period))
        return plan
    }

    /**
     * Разумная игра: сначала ровно столько, сколько стоит нужное, остальное
     * пополам между желаемым и копилкой.
     *
     * План строится под покупку, а не наоборот: очко роста за обязательные
     * даётся, когда потрачено не меньше запланированного, и план «на глазок»
     * своей же покупкой бы и не выполнился.
     */
    private fun newPlan(available: Coins, mandatory: Coins): BudgetPlan {
        val rest = available - mandatory
        val savings = Coins(rest.amount / 2)
        return BudgetPlan(mandatory = mandatory, optional = rest - savings, savings = savings)
    }

    private fun cheapestMandatory(budget: Coins): ShopItem? = content.pack().shop
        .filter { it.category == SpendCategory.MANDATORY && budget.covers(it.price) }
        .minByOrNull { it.price.amount }

    private suspend fun buy(profileId: ProfileId, period: GamePeriod, item: ShopItem) {
        val result = wallet.purchase(
            item = item,
            currentBalance = periods.balance(period),
            periodId = period.id,
        )
        if (result !is PurchaseResult.Success) return
        recorder.record(
            profileId = profileId,
            outcome = ActionOutcome(transaction = result.transaction, effects = result.effects),
        )
    }

    /** Откладывает запланированное: очки роста дают и за накопления. */
    private suspend fun deposit(profileId: ProfileId, period: GamePeriod, amount: Coins) {
        val goal = content.pack().goals.firstOrNull() ?: return
        val balance = periods.balance(period)
        if (amount == Coins.ZERO || !balance.covers(amount)) return

        val outcome = savingsEngine.deposit(
            amount = amount,
            currentBalance = balance,
            progress = savings.progress(profileId, goal.id),
            goal = goal,
            periodId = period.id,
        ).value
        recorder.record(
            profileId = profileId,
            outcome = ActionOutcome(
                transaction = outcome.transaction,
                savings = outcome.progress.copy(isActive = true),
            ),
        )
    }
}
