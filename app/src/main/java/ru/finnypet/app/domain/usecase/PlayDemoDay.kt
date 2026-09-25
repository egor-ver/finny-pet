package ru.finnypet.app.domain.usecase

import kotlinx.coroutines.flow.first
import ru.finnypet.app.domain.economy.PetStateEngine
import ru.finnypet.app.domain.economy.PurchaseResult
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
import ru.finnypet.app.domain.model.totalPrice
import ru.finnypet.app.domain.repository.ActionOutcome
import ru.finnypet.app.domain.repository.ContentRepository
import ru.finnypet.app.domain.repository.OutcomeRecorder
import ru.finnypet.app.domain.repository.PeriodRepository
import ru.finnypet.app.domain.repository.ProfileRepository
import ru.finnypet.app.domain.repository.SavingsRepository
import ru.finnypet.app.domain.repository.TaskProgressRepository

/**
 * Проживает день демонстрации целиком (ТЗ 2.5.13, шаги 5–10 Приложения А):
 * задание, план с копилкой, обязательная и необязательная покупки, закрытие.
 * Эксперт видит пять периодов и три стадии роста за пять нажатий; второй
 * день — с ошибкой, чтобы показать разбор (ТЗ 2.5.9).
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
    private val confirmPlan: ConfirmPlan,
    private val wallet: WalletEngine,
    private val taskEngine: TaskEngine,
    private val pet: PetStateEngine,
    private val recorder: OutcomeRecorder,
) {

    suspend operator fun invoke() {
        val profileId = profiles.testProfile()?.id ?: return
        val period = openPeriod(profileId)
        // Сначала заработай, потом распредели (R7): награда входит в план.
        passTask(profileId, period)
        val wallet = periods.balance(period)
        if (period.number == MISTAKE_DAY) {
            // Раздел 4 плана, день 2: самая дорогая игрушка вместо еды. Эксперт
            // видит день без роста, грустную сову утром и разбор ошибки.
            val toy = priciest(SpendCategory.OPTIONAL, wallet)
            planOf(profileId, period) { mistakePlan(wallet, toy?.price ?: Coins.ZERO) }
            toy?.let { buy(profileId, period, it) }
        } else {
            // Тот же расчёт, что подсказывает ребёнку главный экран: самый дешёвый
            // набор, закрывающий потребности. Иначе сова в демо не росла бы (AD-3).
            val needs = profiles.pet(profileId)
                ?.let { pet.cheapestCover(it.state, content.pack().shop) }
                .orEmpty()
            val plan = planOf(profileId, period) { newPlan(wallet, minOf(needs.totalPrice(), wallet)) }
            // Желаемое покупается одно и после нужного: весь план на него ещё свободен.
            needs.forEach { buy(profileId, period, it) }
            cheapest(SpendCategory.OPTIONAL, plan.optional)?.let { buy(profileId, period, it) }
        }
        closeDay(profileId)
    }

    /**
     * Эксперт мог распределить монеты руками до нажатия — его план и берём.
     * Доля копилки уходит на цель при подтверждении (R6), поэтому без цели
     * демонстрация берёт первую: ребёнок выбирает сам, а жюри важен весь цикл.
     */
    private suspend fun planOf(profileId: ProfileId, period: GamePeriod, draft: () -> BudgetPlan): BudgetPlan {
        val plan = periods.plan(period.id) ?: draft().also { periods.savePlan(period.id, it) }
        if (period.status == PeriodStatus.PLANNING) {
            chooseGoalIfNone(profileId)
            confirmPlan(profileId)
        }
        return plan
    }

    /**
     * Без активной цели демонстрация берёт первую некупленную (L5, Б8):
     * иначе после покупки первой же цели демо выбрало бы её опять, и рядом
     * с совой встал бы дубль. Некупленных не осталось — копим ещё один
     * экземпляр первой, как и на настоящем экране копилки.
     */
    private suspend fun chooseGoalIfNone(profileId: ProfileId) {
        if (savings.activeProgress(profileId) != null) return
        val goals = content.pack().goals
        if (goals.isEmpty()) return
        val bought = savings.observeBought(profileId).first().toSet()
        val goal = goals.firstOrNull { it.id !in bought } ?: goals.first()
        savings.setActive(profileId, savings.progress(profileId, goal.id).copy(isActive = true))
    }

    /**
     * Разумная игра: сначала ровно столько, сколько стоит нужное, остальное
     * пополам между желаемым и копилкой. План строится под покупку: нужное
     * соблюдено, когда потрачено не больше запланированного (R3).
     */
    private fun newPlan(available: Coins, mandatory: Coins): BudgetPlan {
        val rest = available - mandatory
        val savings = Coins(rest.amount / 2)
        return BudgetPlan(mandatory = mandatory, optional = rest - savings, savings = savings)
    }

    /** Ошибка дня: желаемое — вся игрушка, остаток пополам между копилкой и нужным, на которое не хватит. */
    private fun mistakePlan(available: Coins, toy: Coins): BudgetPlan {
        val rest = available - toy
        val savings = Coins(rest.amount / 2)
        return BudgetPlan(mandatory = rest - savings, optional = toy, savings = savings)
    }

    private fun cheapest(category: SpendCategory, budget: Coins): ShopItem? = affordable(category, budget)
        .minByOrNull { it.price.amount }

    private fun priciest(category: SpendCategory, budget: Coins): ShopItem? = affordable(category, budget)
        .maxByOrNull { it.price.amount }

    private fun affordable(category: SpendCategory, budget: Coins): List<ShopItem> = content.pack().shop
        .filter { it.category == category && budget.covers(it.price) }

    /** Задание дня на верный исход с наибольшей наградой: эксперт видит начисление и объяснение. */
    private suspend fun passTask(profileId: ProfileId, period: GamePeriod) {
        val pack = content.pack()
        val state = profiles.pet(profileId)?.state ?: return
        val completed = tasks.observeCompleted(profileId).first()
        val transactions = periods.transactions(period.id)
        val task = TaskSchedule.taskOfTheDay(pack.tasks, completed, transactions, state, pack.balance) ?: return
        val result = taskEngine.evaluate(
            task = task,
            attempt = bestAttempt(task),
            currentBalance = periods.balance(period),
            periodId = period.id,
            rewardable = TaskSchedule.rewardable(task.id, completed, transactions, pack.balance),
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
        val goal = task.outcomes.filter { it.correct }.maxBy { it.reward.amount }.condition
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

    private companion object {
        /** Второй день демо — с ошибкой, как в эталонном сценарии (раздел 4 плана). */
        const val MISTAKE_DAY = 2
    }
}
