package ru.finnypet.app.domain.economy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.finnypet.app.domain.model.BudgetPlan
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.GamePeriod
import ru.finnypet.app.domain.model.Goal
import ru.finnypet.app.domain.model.GoalId
import ru.finnypet.app.domain.model.GoalProgress
import ru.finnypet.app.domain.model.GrowthStage
import ru.finnypet.app.domain.model.ItemId
import ru.finnypet.app.domain.model.LearningTask
import ru.finnypet.app.domain.model.OutcomeCondition
import ru.finnypet.app.domain.model.PeriodStatus
import ru.finnypet.app.domain.model.PetEffect
import ru.finnypet.app.domain.model.PetGrowth
import ru.finnypet.app.domain.model.PetState
import ru.finnypet.app.domain.model.PetStatKind
import ru.finnypet.app.domain.model.ProfileId
import ru.finnypet.app.domain.model.ShopItem
import ru.finnypet.app.domain.model.SpendCategory
import ru.finnypet.app.domain.model.Stat
import ru.finnypet.app.domain.model.StepAnswer
import ru.finnypet.app.domain.model.TaskAttempt
import ru.finnypet.app.domain.model.TaskId
import ru.finnypet.app.domain.model.TaskOption
import ru.finnypet.app.domain.model.TaskOutcome
import ru.finnypet.app.domain.model.TaskStep
import ru.finnypet.app.domain.model.TaskTopic
import ru.finnypet.app.domain.model.Transaction
import ru.finnypet.app.domain.model.TransactionType

/**
 * Сквозной прогон пяти игровых периодов на одном наборе чисел.
 *
 * Это не тест одного движка, а проверка того, что экономика сходится:
 * за период нельзя купить всё, цель достижима, питомец дорастает до последней
 * стадии, прогресс не откатывается. Когда продакт заменит PLACEHOLDER
 * на согласованные числа, этот тест первым скажет, сошлись они или нет.
 */
class FivePeriodRunTest {

    private val balance = GameBalance.PLACEHOLDER
    private val clock = GameClock { 0L }

    private val budgetEngine = BudgetEngine()
    private val walletEngine = WalletEngine(clock)
    private val savingsEngine = SavingsEngine(clock)
    private val petEngine = PetStateEngine(balance)
    private val growthEngine = GrowthEngine(balance)
    private val taskEngine = TaskEngine(clock)
    private val periodEngine = PeriodEngine(budgetEngine, petEngine, growthEngine, balance, clock)

    private val food = ShopItem(
        id = ItemId("berries"),
        titleKey = "shop.berries",
        price = Coins(25),
        category = SpendCategory.MANDATORY,
        effects = listOf(PetEffect(PetStatKind.SATIETY, 15)),
    )

    private val toy = ShopItem(
        id = ItemId("ball"),
        titleKey = "shop.ball",
        price = Coins(20),
        category = SpendCategory.OPTIONAL,
        effects = listOf(PetEffect(PetStatKind.MOOD, 10)),
    )

    /** Восемь позиций каталога — минимум по ТЗ 2.6. Сумма нужна для проверки «нельзя купить всё». */
    private val catalogue = listOf(
        food, toy,
        food.copy(id = ItemId("grain"), price = Coins(20)),
        food.copy(id = ItemId("water"), price = Coins(10)),
        food.copy(id = ItemId("brush"), price = Coins(30)),
        toy.copy(id = ItemId("bow"), price = Coins(15)),
        toy.copy(id = ItemId("book"), price = Coins(35)),
        toy.copy(id = ItemId("lamp"), price = Coins(45)),
    )

    private val goal = Goal(id = GoalId("bike"), titleKey = "goal.bike", price = Coins(75))

    private val plannedSavings = Coins(15)

    private val task = LearningTask(
        id = TaskId("save_01"),
        topic = TaskTopic.SAVING,
        introKey = "task.save_01.intro",
        steps = listOf(
            TaskStep.Choice(
                promptKey = "task.save_01.step",
                options = listOf(TaskOption("save", "opt.save"), TaskOption("spend", "opt.spend")),
            ),
        ),
        outcomes = listOf(
            TaskOutcome(
                id = "chose_save",
                condition = OutcomeCondition.OptionChosen("save"),
                reward = balance.taskReward,
                explanationKey = "task.save_01.ok",
            ),
            TaskOutcome(
                id = "chose_spend",
                condition = OutcomeCondition.Otherwise,
                reward = Coins.ZERO,
                explanationKey = "task.save_01.spent",
            ),
        ),
    )

    private data class Snapshot(
        val number: Int,
        val available: Coins,
        val planFollowed: Boolean,
        val mandatoryCovered: Boolean,
        val savedTotal: Coins,
        val growth: PetGrowth,
        val state: PetState,
        val carryOver: Coins,
    )

    /** Проигрывает пять периодов поведением ребёнка, который держится плана. */
    private fun play(): List<Snapshot> {
        var period = GamePeriod(
            id = 1,
            profileId = ProfileId("p1"),
            number = 1,
            income = balance.periodIncome,
            startBalance = balance.startingBalance,
            status = PeriodStatus.PLANNING,
        )
        var state = PetState.uniform(Stat(balance.initialStat))
        var growth = PetGrowth.INITIAL
        var progress = GoalProgress(goalId = goal.id, isActive = true)
        val log = mutableListOf<Snapshot>()

        repeat(PERIODS) {
            val available = period.available
            val plan = BudgetPlan(
                mandatory = food.price,
                optional = toy.price,
                savings = plannedSavings,
            )
            assertTrue(
                "Период ${period.number}: план не уложился в доступную сумму",
                budgetEngine.check(plan, available) is PlanCheck.Fits,
            )

            val transactions = mutableListOf<Transaction>()
            var cash = period.startBalance + period.income
            transactions += income(period)

            val boughtFood = walletEngine.purchase(food, cash, period.id) as PurchaseResult.Success
            cash = boughtFood.newBalance
            transactions += boughtFood.transaction
            state = petEngine.apply(state, boughtFood.effects).value

            val boughtToy = walletEngine.purchase(toy, cash, period.id) as PurchaseResult.Success
            cash = boughtToy.newBalance
            transactions += boughtToy.transaction
            state = petEngine.apply(state, boughtToy.effects).value

            val attempt = TaskAttempt(listOf(StepAnswer.Chosen("save")))
            val solved = taskEngine.evaluate(task, attempt, cash, period.id)
            cash = solved.value.newBalance
            solved.value.transaction?.let { transactions += it }

            val deposited = savingsEngine.deposit(plannedSavings, cash, progress, goal, period.id)
            cash = deposited.value.balance
            progress = deposited.value.progress
            transactions += deposited.value.transaction

            val closed = periodEngine.close(period, plan, transactions, state, growth)
            state = closed.value.state
            growth = closed.value.growth

            log += Snapshot(
                number = period.number,
                available = available,
                planFollowed = closed.value.report.planFollowed,
                mandatoryCovered = closed.value.report.mandatoryCovered,
                savedTotal = progress.saved,
                growth = growth,
                state = state,
                carryOver = closed.value.carryOver,
            )

            period = periodEngine.openNext(period, closed.value.carryOver).copy(id = period.id + 1)
        }
        return log
    }

    private fun income(period: GamePeriod) = Transaction(
        id = 0,
        periodId = period.id,
        type = TransactionType.INCOME_PERIOD,
        amount = period.income,
        reasonKey = "income.period",
        createdAt = 0,
    )

    @Test
    fun `пять периодов проигрываются подряд без падений`() {
        assertEquals(PERIODS, play().size)
    }

    @Test
    fun `за один период нельзя купить весь каталог`() {
        val catalogueCost = catalogue.fold(Coins.ZERO) { total, item -> total + item.price }
        play().forEach { snapshot ->
            assertTrue(
                "Период ${snapshot.number}: доступно ${snapshot.available}, весь каталог стоит $catalogueCost",
                snapshot.available < catalogueCost,
            )
        }
    }

    @Test
    fun `обязательные расходы закрываются каждый период`() {
        play().forEach { snapshot ->
            assertTrue("Период ${snapshot.number}: обязательные не закрыты", snapshot.mandatoryCovered)
        }
    }

    @Test
    fun `план выполняется каждый период`() {
        play().forEach { snapshot ->
            assertTrue("Период ${snapshot.number}: план не выполнен", snapshot.planFollowed)
        }
    }

    @Test
    fun `цель достигается к пятому периоду`() {
        val last = play().last()
        assertTrue(
            "Накоплено ${last.savedTotal} из ${goal.price} за $PERIODS периодов",
            last.savedTotal >= goal.price,
        )
    }

    @Test
    fun `цель не достигается раньше третьего периода`() {
        val early = play().take(2)
        early.forEach { snapshot ->
            assertTrue(
                "Период ${snapshot.number}: цель взята слишком рано, копить было незачем",
                snapshot.savedTotal < goal.price,
            )
        }
    }

    @Test
    fun `питомец дорастает до последней стадии`() {
        assertEquals(GrowthStage.GROWN, play().last().growth.stage)
    }

    @Test
    fun `очки роста ни разу не уменьшаются`() {
        play().zipWithNext().forEach { (before, after) ->
            assertTrue(
                "Период ${after.number}: очки упали с ${before.growth.points} до ${after.growth.points}",
                after.growth.points >= before.growth.points,
            )
        }
    }

    @Test
    fun `показатели питомца остаются в пределах шкалы`() {
        play().forEach { snapshot ->
            listOf(snapshot.state.mood, snapshot.state.satiety, snapshot.state.care).forEach { stat ->
                assertTrue("Период ${snapshot.number}: показатель вне шкалы", stat.value in Stat.RANGE)
            }
        }
    }

    private companion object {
        const val PERIODS = 5
    }
}
