package ru.finnypet.app.domain.economy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.finnypet.app.data.content.ContentParser
import ru.finnypet.app.data.content.RealContent
import ru.finnypet.app.domain.model.BudgetPlan
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.GamePeriod
import ru.finnypet.app.domain.model.GoalProgress
import ru.finnypet.app.domain.model.GrowthStage
import ru.finnypet.app.domain.model.ItemId
import ru.finnypet.app.domain.model.PeriodStatus
import ru.finnypet.app.domain.model.PetGrowth
import ru.finnypet.app.domain.model.PetState
import ru.finnypet.app.domain.model.PetStatKind
import ru.finnypet.app.domain.model.ProfileId
import ru.finnypet.app.domain.model.ShopItem
import ru.finnypet.app.domain.model.SpendCategory
import ru.finnypet.app.domain.model.Stat
import ru.finnypet.app.domain.model.Transaction
import ru.finnypet.app.domain.model.TransactionType
import ru.finnypet.app.domain.model.totalPrice

/**
 * Симуляция экономики на настоящем контент-паке: числа из balance.json и
 * shop.json должны давать эталонный сценарий раздела 4 плана, награждать
 * разумную игру и не загонять в тупик после долгого пренебрежения.
 *
 * Каждый день — как у ребёнка: доход, задание верно с первой попытки (R7,
 * R8), план с копилкой при подтверждении (R6), покупки, итоги и ночь.
 */
class EconomySimulationTest {

    private val pack = ContentParser().parse(RealContent.raw())
    private val balance = pack.balance
    private val clock = GameClock { 0L }
    private val wallet = WalletEngine(clock)
    private val savings = SavingsEngine(clock)
    private val pet = PetStateEngine(balance)
    private val periods = PeriodEngine(BudgetEngine(), pet, GrowthEngine(balance), balance, clock)
    private val goal = pack.goals.minBy { it.price.amount }
    private val ball = pack.shop.single { it.id == ItemId("toy-ball") }

    /** Утро: кошелёк после дохода и задания, сова после ночи. */
    private data class Morning(val number: Int, val wallet: Coins, val state: PetState)

    /** Что ребёнок решил: план и покупки по нему. */
    private data class Choice(val plan: BudgetPlan, val buys: List<ShopItem>)

    private data class Evening(val morning: Morning, val needsMet: Boolean, val growth: PetGrowth, val saved: Coins)

    private fun play(
        days: Int,
        start: PetState = PetState.uniform(Stat(balance.initialStat)),
        choose: (Morning) -> Choice,
    ): List<Evening> {
        var period = GamePeriod(
            id = 1,
            profileId = ProfileId("sim"),
            number = 1,
            income = balance.periodIncome,
            startBalance = balance.startingBalance,
            status = PeriodStatus.PLANNING,
        )
        var state = start
        var growth = PetGrowth.INITIAL
        var progress = GoalProgress(goalId = goal.id, isActive = true)
        val log = mutableListOf<Evening>()

        repeat(days) {
            val transactions = mutableListOf<Transaction>()
            var cash = period.startBalance
            listOf(TransactionType.INCOME_PERIOD to period.income, TransactionType.INCOME_TASK to balance.taskReward)
                .forEach { (type, amount) ->
                    val credited = wallet.credit(type, amount, cash, period.id).value
                    cash = credited.balance
                    transactions += credited.transaction
                }

            val morning = Morning(period.number, cash, state)
            val choice = choose(morning)
            if (choice.plan.savings > Coins.ZERO) {
                val deposited = savings.deposit(choice.plan.savings, cash, progress, goal, period.id).value
                cash = deposited.balance
                progress = deposited.progress
                transactions += deposited.transaction
            }
            var optionalLeft = choice.plan.optional
            choice.buys.forEach { item ->
                val bought = wallet.purchase(item, cash, period.id, optionalLeft) as PurchaseResult.Success
                cash = bought.newBalance
                if (item.category == SpendCategory.OPTIONAL) optionalLeft -= item.price
                transactions += bought.transaction
                state = pet.apply(state, bought.effects).value
            }

            val closed = periods.close(periods.confirmPlan(period), choice.plan, transactions, state, growth).value
            state = closed.state
            growth = closed.growth
            log += Evening(morning, closed.needsMet, growth, progress.saved)
            period = periods.openNext(closed.closedPeriod, closed.carryOver).copy(id = period.id + 1)
        }
        return log
    }

    /**
     * Разумная игра: сначала потребности, еда первой; что не влезло в кошелёк —
     * завтра. Остаток пополам между копилкой и желаемым.
     */
    private fun sensible(morning: Morning): Choice {
        val cover = pet.cheapestCover(morning.state, pack.shop)!!
            .sortedByDescending { item -> item.effects.any { it.stat == PetStatKind.SATIETY } }
        val buys = mutableListOf<ShopItem>()
        var left = morning.wallet
        cover.forEach { item ->
            if (left.covers(item.price)) {
                buys += item
                left -= item.price
            }
        }
        val saved = Coins(left.amount / 2)
        return Choice(BudgetPlan(buys.totalPrice(), left - saved, saved), buys)
    }

    /** Раздел 4 плана, день 2: нужное 3, желаемое 24, копилка 8, куплен только мячик. */
    private fun mistake(): Choice = Choice(BudgetPlan(Coins(3), ball.price, Coins(8)), listOf(ball))

    /** Всё на самое дорогое желаемое, еды нет вовсе. */
    private fun allWants(morning: Morning): Choice {
        val toy = pack.shop.filter { it.category == SpendCategory.OPTIONAL && morning.wallet.covers(it.price) }
            .maxBy { it.price.amount }
        return Choice(BudgetPlan(Coins.ZERO, toy.price, Coins.ZERO), listOf(toy))
    }

    @Test
    fun `эталонный сценарий с днём ошибки даёт 6 6 12 18 24`() {
        val days = play(5) { if (it.number == 2) mistake() else sensible(it) }

        assertEquals(listOf(6, 6, 12, 18, 24), days.map { it.growth.points })
        assertEquals(
            listOf(GrowthStage.CUB, GrowthStage.CUB, GrowthStage.YOUNG, GrowthStage.YOUNG, GrowthStage.GROWN),
            days.map { it.growth.stage },
        )
        assertFalse("В день ошибки сова голодна", days[1].needsMet)
    }

    /** Раздел 4 плана, утро дня 3: еда 30, уход 55, сова грустит, закрыть потребности стоит 37. */
    @Test
    fun `после дня ошибки сова грустит и еда стоит 37`() {
        val dayThree = play(3) { if (it.number == 2) mistake() else sensible(it) }[2].morning

        assertEquals(Stat(30), dayThree.state.satiety)
        assertEquals(Stat(55), dayThree.state.care)
        assertTrue(dayThree.state.satiety < Stat(balance.sadThreshold))
        assertEquals(Coins(37), pet.cheapestCover(dayThree.state, pack.shop)!!.totalPrice())
    }

    @Test
    fun `разумная игра кормит сову каждый день и растит её до взрослой`() {
        val days = play(5, choose = ::sensible)

        assertTrue(days.all { it.needsMet })
        assertEquals(GrowthStage.GROWN, days.last().growth.stage)
    }

    /** ТЗ 8.4: цель достижима — ребёнок видит результат накоплений за время демо. */
    @Test
    fun `разумная игра собирает самую дешёвую цель за пять дней`() {
        val days = play(5, choose = ::sensible)

        assertTrue("Отложено ${days.last().saved.amount} из ${goal.price.amount}", days.last().saved.covers(goal.price))
    }

    /** Желаемое вместо еды не растит сову, а грусть не превращается в голодание (ТЗ 3.5). */
    @Test
    fun `всё на желаемое — роста нет, но показатели не ниже предела`() {
        val days = play(5, choose = ::allWants)

        assertEquals(0, days.last().growth.points)
        days.forEach { day ->
            PetStatKind.entries.forEach { kind ->
                assertTrue(day.morning.state.statFor(kind) >= Stat(balance.statFloor))
            }
        }
    }

    /**
     * Худший случай из раздела 4: еда 30 и уход 30 стоят дороже кошелька.
     * Тупика нет — за первый день закрывается еда и часть ухода, за второй остальное.
     */
    @Test
    fun `после долгого пренебрежения потребности закрываются за два дня`() {
        val start = PetState(mood = Stat(balance.statFloor), satiety = Stat(balance.statFloor), care = Stat(balance.statFloor))
        val days = play(5, start = start, choose = ::sensible)

        val firstDay = days.first().morning
        assertTrue(pet.cheapestCover(firstDay.state, pack.shop)!!.totalPrice() > firstDay.wallet)
        assertFalse(days.first().needsMet)
        assertTrue(days.drop(1).all { it.needsMet })
    }
}
