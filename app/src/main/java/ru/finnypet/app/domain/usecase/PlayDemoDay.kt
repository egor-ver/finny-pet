package ru.finnypet.app.domain.usecase

import kotlinx.coroutines.flow.first
import ru.finnypet.app.domain.economy.PeriodEngine
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
import ru.finnypet.app.domain.model.PetState
import ru.finnypet.app.domain.model.ProfileId
import ru.finnypet.app.domain.model.ShopItem
import ru.finnypet.app.domain.model.Stat
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
    private val periodEngine: PeriodEngine,
    private val recorder: OutcomeRecorder,
) {

    suspend operator fun invoke() {
        val profileId = profiles.testProfile()?.id ?: return
        val period = openPeriod(profileId)
        // Сначала заработай, потом распредели (R7): награда входит в план.
        passTask(profileId, period)
        val wallet = periods.balance(period)
        val existingPlan = periods.plan(period.id)
        // Эксперт мог купить что-то руками до нажатия «Прожить день» — демо
        // тратит только то, что от плана осталось, а не весь план заново
        // (ревью L8): иначе желаемое было бы куплено дважды.
        val transactionsToday = periods.transactions(period.id)
        val spentToday = periodEngine.factOf(transactionsToday)
        val boughtToday = transactionsToday.mapNotNull { it.itemId }.toSet()
        val state = profiles.pet(profileId)?.state ?: PetState.uniform(Stat.MAX)
        val decision = if (period.number == MISTAKE_DAY) {
            // Раздел 4 плана, день 2: фиксированный мяч сверх плана желаемого,
            // нужное не покупается. Эксперт видит день без роста, грустную
            // сову утром и разбор ошибки.
            DemoDayPlan.mistakeDay(wallet, state, content.pack().shop, pet, existingPlan, boughtToday)
        } else {
            // Тот же расчёт, что подсказывает ребёнку главный экран: самый дешёвый
            // набор, закрывающий потребности. Иначе сова в демо не росла бы (AD-3).
            DemoDayPlan.normalDay(wallet, state, content.pack().shop, pet, existingPlan, spentToday, boughtToday)
        }
        planOf(profileId, period, existingPlan, decision.plan)
        // Покупки выводятся из плана, а не пересчитаны от кошелька заново
        // (Б10-ревью): ручной план эксперта должен решать, что купит демо.
        decision.buys.forEach { buy(profileId, period, it) }
        if (closeDay(profileId) != null) {
            // Игрок видит следующий день сразу после нажатия: доход и событие
            // должны быть готовы до возврата на главный экран.
            openPeriod(profileId)
        }
    }

    /**
     * Эксперт мог распределить монеты руками до нажатия — его план и берём,
     * ничего не пересчитывая. Доля копилки уходит на цель при подтверждении
     * (R6), поэтому без цели демонстрация берёт первую: ребёнок выбирает сам,
     * а жюри важен весь цикл.
     */
    private suspend fun planOf(profileId: ProfileId, period: GamePeriod, existingPlan: BudgetPlan?, plan: BudgetPlan) {
        if (existingPlan == null) periods.savePlan(period.id, plan)
        if (period.status == PeriodStatus.PLANNING) {
            chooseGoalIfNone(profileId)
            confirmPlan(profileId)
        }
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
