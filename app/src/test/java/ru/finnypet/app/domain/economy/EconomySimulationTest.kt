package ru.finnypet.app.domain.economy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.finnypet.app.data.content.ContentParser
import ru.finnypet.app.data.content.RealContent
import ru.finnypet.app.domain.content.DayEvent
import ru.finnypet.app.domain.content.petEffects
import ru.finnypet.app.domain.model.BudgetPlan
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.GamePeriod
import ru.finnypet.app.domain.model.GoalProgress
import ru.finnypet.app.domain.model.GrowthStage
import ru.finnypet.app.domain.model.GrowthStar
import ru.finnypet.app.domain.model.ItemId
import ru.finnypet.app.domain.model.PeriodFact
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
import ru.finnypet.app.domain.usecase.DemoDayPlan
import ru.finnypet.app.domain.usecase.DemoDayPlan.Decision as Choice
import ru.finnypet.app.domain.model.totalPrice

/**
 * Симуляция экономики на настоящем контент-паке: числа из balance.json,
 * shop.json и events.json должны давать эталонный сценарий раздела 4 плана,
 * награждать разумную игру и не загонять в тупик после долгого пренебрежения.
 *
 * Обычные дни и день ошибки считаются через [DemoDayPlan] — ту же функцию,
 * что использует демонстрация (`PlayDemoDay`). Раньше у теста была своя
 * копия этой логики, она не покупала желаемое и не совпадала с демо (L8).
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

    /** Утро: кошелёк после дохода, события и задания, сова после ночи и события. */
    private data class Morning(val number: Int, val wallet: Coins, val state: PetState, val event: DayEvent?)

    private data class Evening(
        val morning: Morning,
        val plan: BudgetPlan,
        val buyIds: List<ItemId>,
        val report: PlanFactReport,
        val carryOver: Coins,
        val stateBeforeNight: PetState,
        val stateAfterNight: PetState,
        val stars: Set<GrowthStar>,
        val growth: PetGrowth,
        val saved: Coins,
    )

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

            val credited = wallet.credit(TransactionType.INCOME_PERIOD, period.income, cash, period.id).value
            cash = credited.balance
            transactions += credited.transaction

            // События дня применяются до плана и до задания — как в OpenPeriodIfNeeded (L7, ТЗ 2.5.4).
            val event = pack.events.firstOrNull { it.day == period.number }
            (event as? DayEvent.Gift)?.let { gift ->
                val gifted = wallet.credit(TransactionType.INCOME_GIFT, gift.amount, cash, period.id).value
                cash = gifted.balance
                transactions += gifted.transaction
            }
            event?.petEffects()?.takeIf { it.isNotEmpty() }?.let { effects ->
                state = pet.apply(state, effects).value
            }

            val taskCredited = wallet.credit(TransactionType.INCOME_TASK, balance.taskReward, cash, period.id).value
            cash = taskCredited.balance
            transactions += taskCredited.transaction

            val morning = Morning(period.number, cash, state, event)
            val choice = choose(morning)
            if (choice.plan.savings > Coins.ZERO) {
                val deposited = savings.deposit(choice.plan.savings, cash, progress, goal, period.id).value
                cash = deposited.balance
                progress = deposited.progress
                transactions += deposited.transaction
            }
            choice.buys.forEach { item ->
                val bought = wallet.purchase(item, cash, period.id) as PurchaseResult.Success
                cash = bought.newBalance
                transactions += bought.transaction
                state = pet.apply(state, bought.effects).value
            }

            val stateBeforeNight = state
            val closed = periods.close(periods.confirmPlan(period), choice.plan, transactions, state, growth).value
            state = closed.state
            growth = closed.growth
            log += Evening(
                morning = morning,
                plan = choice.plan,
                buyIds = choice.buys.map { it.id },
                report = closed.report,
                carryOver = closed.carryOver,
                stateBeforeNight = stateBeforeNight,
                stateAfterNight = state,
                stars = GrowthEngine.starsFor(closed.report, closed.needsMet),
                growth = growth,
                saved = progress.saved,
            )
            period = periods.openNext(closed.closedPeriod, closed.carryOver).copy(id = period.id + 1)
        }
        return log
    }

    /**
     * Разумная игра: план — под точную цену закрытия потребностей, желаемое —
     * самое дешёвое доступное по плану (та же функция, что в демо, — L8).
     * Без ручных покупок до плана — в чистой симуляции им и не взяться.
     */
    private fun sensible(morning: Morning): Choice = DemoDayPlan.normalDay(
        morning.wallet, morning.state, pack.shop, pet,
        existingPlan = null, spentToday = PeriodFact.EMPTY, boughtToday = emptySet(),
    )

    /** Раздел 4 плана, день 2: фиксированный мяч сверх плана желаемого — как в демо. */
    private fun mistake(morning: Morning): Choice = DemoDayPlan.mistakeDay(
        morning.wallet, morning.state, pack.shop, pet,
        existingPlan = null, boughtToday = emptySet(),
    )

    /** Нужное — как в разумной игре, а весь остаток — в копилку: желаемое не покупается совсем. */
    private fun allSavings(morning: Morning): Choice {
        val needsBuys = sensible(morning).buys.filter { it.category == SpendCategory.MANDATORY }
        val mandatory = needsBuys.totalPrice()
        return Choice(BudgetPlan(mandatory, Coins.ZERO, morning.wallet - mandatory), needsBuys)
    }

    /** Нужное и каждый день звёздочка-наклейка на радость, если хватает после него, остаток пополам. */
    private fun withSticker(morning: Morning): Choice {
        val sticker = pack.shop.single { it.id == ItemId("sticker-star") }
        val needsBuys = sensible(morning).buys.filter { it.category == SpendCategory.MANDATORY }
        val mandatory = needsBuys.totalPrice()
        val afterNeeds = morning.wallet - mandatory
        if (!afterNeeds.covers(sticker.price)) return Choice(BudgetPlan(mandatory, Coins.ZERO, afterNeeds), needsBuys)
        val rest = afterNeeds - sticker.price
        val saved = Coins(rest.amount / 2)
        return Choice(BudgetPlan(mandatory, sticker.price + rest - saved, saved), needsBuys + sticker)
    }

    /** Всё на самое дорогое желаемое, еды нет вовсе. */
    private fun allWants(morning: Morning): Choice {
        val toy = pack.shop.filter { it.category == SpendCategory.OPTIONAL && morning.wallet.covers(it.price) }
            .maxBy { it.price.amount }
        return Choice(BudgetPlan(Coins.ZERO, toy.price, Coins.ZERO), listOf(toy))
    }

    /** AD-3: звезда — очко, подросток с 4, взрослый с 10. */
    @Test
    fun `эталонный сценарий с днём ошибки даёт 3 3 6 9 12`() {
        val days = play(5) { if (it.number == 2) mistake(it) else sensible(it) }

        assertEquals(listOf(3, 3, 6, 9, 12), days.map { it.growth.points })
        assertEquals(
            listOf(GrowthStage.CUB, GrowthStage.CUB, GrowthStage.YOUNG, GrowthStage.YOUNG, GrowthStage.GROWN),
            days.map { it.growth.stage },
        )
        assertFalse("В день ошибки сова голодна", pet.needsOf(days[1].stateBeforeNight).isEmpty())
        assertEquals(Coins.ZERO, days[1].report.line(SpendCategory.MANDATORY).actual)
    }

    /** Раздел 4 плана: дни 4 и 5 несут события L7 — без них таблица не сходится с демо. */
    @Test
    fun `события применяются до плана на дни 4 и 5`() {
        val days = play(5) { if (it.number == 2) mistake(it) else sensible(it) }

        assertEquals(null, days[0].morning.event)
        assertEquals(null, days[1].morning.event)
        assertEquals(null, days[2].morning.event)
        assertEquals(DayEvent.ExtraCare::class, days[3].morning.event!!::class)
        assertEquals(DayEvent.Gift::class, days[4].morning.event!!::class)
        assertEquals(Coins(8), (days[4].morning.event as DayEvent.Gift).amount)
    }

    /**
     * Таблица раздела 4 плана целиком, колонка за колонкой: кошелёк утром,
     * план, ID покупок, факт, перенос остатка, показатели до и после ночи,
     * звёзды, очки, стадия. Раньше эталонный тест проверял только очки,
     * стадии и нулевой факт по нужному во второй день — правка `shop.json`
     * или `balance.json` могла тихо разойтись с таблицей плана (ревью L8).
     */
    @Test
    fun `таблица раздела 4 — каждая строка и колонка`() {
        val days = play(5) { if (it.number == 2) mistake(it) else sensible(it) }

        fun item(id: String) = ItemId(id)
        fun day(i: Int) = days[i - 1]
        fun assertDay(
            n: Int,
            wallet: Int,
            plan: Triple<Int, Int, Int>,
            buys: List<String>,
            fact: Triple<Int, Int, Int>,
            carryOver: Int,
            before: Triple<Int, Int, Int>,
            after: Triple<Int, Int, Int>,
            points: Int,
            stage: GrowthStage,
        ) {
            val d = day(n)
            assertEquals("день $n: кошелёк утром", Coins(wallet), d.morning.wallet)
            assertEquals(
                "день $n: план",
                BudgetPlan(Coins(plan.first), Coins(plan.second), Coins(plan.third)),
                d.plan,
            )
            assertEquals("день $n: покупки", buys.map(::item), d.buyIds)
            assertEquals("день $n: факт нужное", Coins(fact.first), d.report.line(SpendCategory.MANDATORY).actual)
            assertEquals("день $n: факт желаемое", Coins(fact.second), d.report.line(SpendCategory.OPTIONAL).actual)
            assertEquals("день $n: факт копилка", Coins(fact.third), d.report.line(SpendCategory.SAVINGS).actual)
            assertEquals("день $n: остаток", Coins(carryOver), d.carryOver)
            assertEquals(
                "день $n: показатели до ночи",
                PetState(mood = Stat(before.first), satiety = Stat(before.second), care = Stat(before.third)),
                d.stateBeforeNight,
            )
            assertEquals(
                "день $n: показатели после ночи",
                PetState(mood = Stat(after.first), satiety = Stat(after.second), care = Stat(after.third)),
                d.stateAfterNight,
            )
            assertEquals("день $n: очки", points, d.growth.points)
            assertEquals("день $n: стадия", stage, d.growth.stage)
        }

        assertDay(
            n = 1, wallet = 45, plan = Triple(23, 11, 11),
            buys = listOf("water-fresh", "care-vitamins", "sticker-star"),
            fact = Triple(23, 10, 11), carryOver = 1,
            before = Triple(75, 80, 85), after = Triple(65, 55, 70),
            points = 3, stage = GrowthStage.CUB,
        )
        assertEquals(setOf(GrowthStar.FED, GrowthStar.PLAN, GrowthStar.SAVED), days[0].stars)

        assertDay(
            n = 2, wallet = 46, plan = Triple(8, 19, 19),
            buys = listOf("toy-ball"),
            fact = Triple(0, 24, 19), carryOver = 3,
            before = Triple(90, 55, 70), after = Triple(80, 30, 55),
            points = 3, stage = GrowthStage.CUB,
        )
        assertEquals(emptySet<GrowthStar>(), days[1].stars)

        assertDay(
            n = 3, wallet = 48, plan = Triple(37, 6, 5),
            buys = listOf("water-fresh", "food-porridge", "care-vitamins"),
            fact = Triple(37, 0, 5), carryOver = 6,
            before = Triple(80, 70, 75), after = Triple(70, 45, 60),
            points = 6, stage = GrowthStage.YOUNG,
        )
        assertEquals(setOf(GrowthStar.FED, GrowthStar.PLAN, GrowthStar.SAVED), days[2].stars)

        assertDay(
            n = 4, wallet = 51, plan = Triple(29, 11, 11),
            buys = listOf("food-porridge", "care-vitamins", "sticker-star"),
            fact = Triple(29, 10, 11), carryOver = 1,
            before = Triple(80, 70, 70), after = Triple(70, 45, 55),
            points = 9, stage = GrowthStage.YOUNG,
        )
        assertEquals(setOf(GrowthStar.FED, GrowthStar.PLAN, GrowthStar.SAVED), days[3].stars)

        assertDay(
            n = 5, wallet = 54, plan = Triple(29, 13, 12),
            buys = listOf("food-porridge", "care-vitamins", "sticker-star"),
            fact = Triple(29, 10, 12), carryOver = 3,
            before = Triple(80, 70, 75), after = Triple(70, 45, 60),
            points = 12, stage = GrowthStage.GROWN,
        )
        assertEquals(setOf(GrowthStar.FED, GrowthStar.PLAN, GrowthStar.SAVED), days[4].stars)

        assertEquals(listOf(11, 30, 35, 46, 58).map(::Coins), days.map { it.saved })
    }

    /** Раздел 4 плана, утро дня 3: еда 30, уход 55, сова грустит, закрыть потребности стоит 37. */
    @Test
    fun `после дня ошибки сова грустит и еда стоит 37`() {
        val dayThree = play(3) { if (it.number == 2) mistake(it) else sensible(it) }[2].morning

        assertEquals(Stat(30), dayThree.state.satiety)
        assertEquals(Stat(55), dayThree.state.care)
        assertTrue(dayThree.state.satiety < Stat(balance.sadThreshold))
        assertEquals(Coins(37), pet.cheapestCover(dayThree.state, pack.shop)!!.totalPrice())
    }

    @Test
    fun `разумная игра кормит сову каждый день и растит её до взрослой`() {
        val days = play(5, choose = ::sensible)

        assertTrue(days.all { pet.needsOf(it.stateBeforeNight).isEmpty() })
        assertEquals(GrowthStage.GROWN, days.last().growth.stage)
    }

    /** ТЗ 8.4: цель достижима на маршруте с ошибкой, а не только при идеальной игре. */
    @Test
    fun `цель достижима даже на маршруте с днём ошибки`() {
        val days = play(5) { if (it.number == 2) mistake(it) else sensible(it) }

        assertTrue(
            "Отложено ${days.last().saved.amount} из ${goal.price.amount}",
            days.last().saved.covers(goal.price),
        )
    }

    /**
     * Термины ТЗ: отказ от необязательной покупки — не ошибка. Кто всё
     * откладывает, растёт так же, как тот, кто радует сову: звёзды за
     * желаемое не даются (AD-3).
     */
    @Test
    fun `отказ от желаемого не стоит звёзд`() {
        val saver = play(5, choose = ::allSavings)

        assertEquals((1..5).map { it * balance.maxGrowthPerPeriod }, saver.map { it.growth.points })
    }

    /**
     * Находка ревью (Б1): бонус за план поднимал радость сам, и желаемое было
     * бесполезно. Теперь без желаемого радость только падает за ночь, а
     * наклейка каждый день держит её.
     */
    @Test
    fun `радость растёт только от желаемого`() {
        val without = play(5, choose = ::allSavings).map { it.morning.state.mood }
        val withToy = play(5, choose = ::withSticker).map { it.morning.state.mood }

        without.zipWithNext().forEach { (before, after) -> assertTrue("радость выросла сама: $before → $after", after <= before) }
        assertTrue("наклейка не подняла радость: $withToy против $without", withToy.last() > without.last())
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
        assertFalse(pet.needsOf(days.first().stateBeforeNight).isEmpty())
        assertTrue(days.drop(1).all { pet.needsOf(it.stateBeforeNight).isEmpty() })
    }

    /**
     * Длинная игра (L8, Б10-ревью): 15 периодов из минимальных показателей —
     * экономия не штрафуется и на длинной дистанции, а не только в демо на 5 дней.
     */
    @Test
    fun `15 периодов из минимальных показателей — копить и радовать сову растит одинаково`() {
        val start = PetState.uniform(Stat(balance.statFloor))
        val saver = play(LONG_RUN, start = start, choose = ::allSavings)
        val spender = play(LONG_RUN, start = start, choose = ::withSticker)

        assertEquals(saver.map { it.growth.points }, spender.map { it.growth.points })
        assertTrue(saver.drop(1).all { pet.needsOf(it.stateBeforeNight).isEmpty() })
        assertEquals(GrowthStage.GROWN, saver.last().growth.stage)
    }

    /** Та же длинная дистанция разумной игрой: цель собирается и остаётся собранной. */
    @Test
    fun `15 периодов из минимальных показателей — разумная игра не теряет накопленное`() {
        val start = PetState.uniform(Stat(balance.statFloor))
        val days = play(LONG_RUN, start = start, choose = ::sensible)

        assertTrue(days.last().saved.covers(goal.price))
        days.map { it.saved.amount }.zipWithNext().forEach { (before, after) ->
            assertTrue("копилка уменьшилась сама: $before → $after", after >= before)
        }
    }

    private companion object {
        const val LONG_RUN = 15
    }
}
