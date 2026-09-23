package ru.finnypet.app.domain.usecase

import kotlinx.coroutines.flow.first
import ru.finnypet.app.domain.economy.PeriodEngine
import ru.finnypet.app.domain.economy.PurchaseResult
import ru.finnypet.app.domain.economy.SavingsEngine
import ru.finnypet.app.domain.economy.TaskEngine
import ru.finnypet.app.domain.economy.WalletEngine
import ru.finnypet.app.domain.model.BudgetPlan
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.GamePeriod
import ru.finnypet.app.domain.model.LearningTask
import ru.finnypet.app.domain.model.OutcomeCondition
import ru.finnypet.app.domain.model.PeriodStatus
import ru.finnypet.app.domain.model.ProfileId
import ru.finnypet.app.domain.model.ShopItem
import ru.finnypet.app.domain.model.SpendCategory
import ru.finnypet.app.domain.model.StepAnswer
import ru.finnypet.app.domain.model.TaskAttempt
import ru.finnypet.app.domain.model.TaskCompletion
import ru.finnypet.app.domain.model.TaskStep
import ru.finnypet.app.domain.repository.ActionOutcome
import ru.finnypet.app.domain.repository.ContentRepository
import ru.finnypet.app.domain.repository.OutcomeRecorder
import ru.finnypet.app.domain.repository.PeriodRepository
import ru.finnypet.app.domain.repository.ProfileRepository
import ru.finnypet.app.domain.repository.SavingsRepository
import ru.finnypet.app.domain.repository.TaskProgressRepository

/**
 * Проживает день демонстрации целиком (ТЗ 2.5.13, шаги 5–10 Приложения А):
 * план, задание, обязательная и необязательная покупки, копилка, закрытие.
 * Эксперт видит пять периодов и три стадии роста за пять нажатий.
 *
 * Ничего не имитирует — всё идёт через те же движки и записи, что и действия
 * ребёнка. Играет только тестовый профиль: без него это пустое действие, и
 * день ребёнка сюда не попадёт.
 */
class PlayDemoDay(
    private val profiles: ProfileRepository,
    private val periods: PeriodRepository,
    private val savings: SavingsRepository,
    private val tasks: TaskProgressRepository,
    private val content: ContentRepository,
    private val openPeriod: OpenPeriodIfNeeded,
    private val closeDay: CloseDay,
    private val wallet: WalletEngine,
    private val savingsEngine: SavingsEngine,
    private val taskEngine: TaskEngine,
    private val periodEngine: PeriodEngine,
    private val recorder: OutcomeRecorder,
) {

    suspend operator fun invoke() {
        val profileId = profiles.testProfile()?.id ?: return
        val period = openPeriod(profileId)
        val need = cheapest(SpendCategory.MANDATORY, period.available)
        val plan = planOf(period, need)

        passTask(profileId, period)
        need?.let { buy(profileId, period, it) }
        cheapest(SpendCategory.OPTIONAL, plan.optional)?.let { buy(profileId, period, it) }
        deposit(profileId, period, plan.savings)
        closeDay(profileId)
    }

    /** Эксперт мог распределить монеты руками до нажатия — его план и берём. */
    private suspend fun planOf(period: GamePeriod, need: ShopItem?): BudgetPlan {
        val plan = periods.plan(period.id)
            ?: newPlan(period.available, need?.price ?: Coins.ZERO).also { periods.savePlan(period.id, it) }
        if (period.status == PeriodStatus.PLANNING) periods.save(periodEngine.confirmPlan(period))
        return plan
    }

    /**
     * Разумная игра: сначала ровно столько, сколько стоит нужное, остальное
     * пополам между желаемым и копилкой. План строится под покупку: очко роста
     * за обязательные даётся, когда потрачено не меньше запланированного.
     */
    private fun newPlan(available: Coins, mandatory: Coins): BudgetPlan {
        val rest = available - mandatory
        val savings = Coins(rest.amount / 2)
        return BudgetPlan(mandatory = mandatory, optional = rest - savings, savings = savings)
    }

    private fun cheapest(category: SpendCategory, budget: Coins): ShopItem? = content.pack().shop
        .filter { it.category == category && budget.covers(it.price) }
        .minByOrNull { it.price.amount }

    /** Задание дня на исход с наибольшей наградой: эксперт видит начисление и объяснение. */
    private suspend fun passTask(profileId: ProfileId, period: GamePeriod) {
        val pack = content.pack()
        val task = TaskSchedule.taskOfTheDay(pack.tasks, tasks.observeCompleted(profileId).first()) ?: return
        val result = taskEngine.evaluate(
            task = task,
            attempt = bestAttempt(task),
            currentBalance = periods.balance(period),
            periodId = period.id,
            rewardable = TaskSchedule.rewardAvailable(periods.transactions(period.id), pack.balance),
        ).value
        recorder.record(
            profileId = profileId,
            outcome = ActionOutcome(
                transaction = result.transaction,
                effects = result.effects,
                taskCompletion = TaskCompletion(
                    taskId = task.id,
                    outcomeId = result.outcome.id,
                    reward = result.transaction?.amount ?: Coins.ZERO,
                ),
            ),
        )
    }

    private fun bestAttempt(task: LearningTask): TaskAttempt {
        val goal = task.outcomes.maxBy { it.reward.amount }.condition
        return TaskAttempt(task.steps.map { answerFor(it, goal) })
    }

    private fun answerFor(step: TaskStep, goal: OutcomeCondition): StepAnswer = when (step) {
        is TaskStep.Choice -> StepAnswer.Chosen(
            step.options.firstOrNull { it.id in goal.wanted }?.id ?: step.options.first().id,
        )
        is TaskStep.Distribute -> {
            val jars = goal as? OutcomeCondition.JarsAtLeast
            val mandatory = jars?.mandatory ?: Coins.ZERO
            val optional = jars?.optional ?: Coins.ZERO
            StepAnswer.Allocated(BudgetPlan(mandatory, optional, step.budget - mandatory - optional))
        }
        is TaskStep.PickItems -> picked(
            content.pack().shop.filter { it.id in step.itemIds }.associate { it.id.value to it.price },
            goal,
        )
        is TaskStep.Shelf -> picked(step.items.associate { it.id to it.price }, goal)
    }

    private fun picked(prices: Map<String, Coins>, goal: OutcomeCondition): StepAnswer.Picked {
        val ids = prices.keys.filter { it in goal.wanted }
        return StepAnswer.Picked(ids, ids.fold(Coins.ZERO) { total, id -> total + prices.getValue(id) })
    }

    /** Что исход просит выбрать или положить в корзину. */
    private val OutcomeCondition.wanted: List<String>
        get() = when (this) {
            is OutcomeCondition.OptionChosen -> listOf(optionId)
            is OutcomeCondition.AnyOptionChosen -> optionIds
            is OutcomeCondition.BasketContains -> itemIds
            else -> emptyList()
        }

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
