package ru.finnypet.app.domain.economy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.finnypet.app.domain.model.BudgetPlan
import ru.finnypet.app.domain.model.Change
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.GamePeriod
import ru.finnypet.app.domain.model.GrowthStage
import ru.finnypet.app.domain.model.PeriodStatus
import ru.finnypet.app.domain.model.PetGrowth
import ru.finnypet.app.domain.model.PetState
import ru.finnypet.app.domain.model.ProfileId
import ru.finnypet.app.domain.model.RecoveryOption
import ru.finnypet.app.domain.model.SpendCategory
import ru.finnypet.app.domain.model.Stat
import ru.finnypet.app.domain.model.Transaction
import ru.finnypet.app.domain.model.TransactionType

class PeriodEngineTest {

    private val now = 1_700_000_000L
    private val balance = GameBalance.PLACEHOLDER

    private fun engine(gameBalance: GameBalance = balance) = PeriodEngine(
        budget = BudgetEngine(),
        pet = PetStateEngine(gameBalance),
        growth = GrowthEngine(gameBalance),
        balance = gameBalance,
        clock = GameClock { now },
    )

    private val plan = BudgetPlan(mandatory = Coins(40), optional = Coins(20), savings = Coins(10))

    private val period = GamePeriod(
        id = 1,
        profileId = ProfileId("p1"),
        number = 3,
        income = Coins(60),
        startBalance = Coins(40),
        status = PeriodStatus.RUNNING,
    )

    /** Сытая сова: потребностей нет, итог дня решают план и копилка. */
    private val state = PetState.uniform(Stat(90))

    private var nextId = 0L

    private fun transaction(type: TransactionType, amount: Coins) = Transaction(
        id = ++nextId,
        periodId = period.id,
        type = type,
        amount = amount,
        reasonKey = "test",
        createdAt = now,
    )

    private val onPlan = listOf(
        transaction(TransactionType.INCOME_PERIOD, Coins(60)),
        transaction(TransactionType.PURCHASE_MANDATORY, Coins(40)),
        transaction(TransactionType.PURCHASE_OPTIONAL, Coins(20)),
        transaction(TransactionType.SAVINGS_DEPOSIT, Coins(10)),
    )

    @Test
    fun `факт группирует траты по направлениям`() {
        val fact = engine().factOf(onPlan)
        assertEquals(Coins(40), fact.amountFor(SpendCategory.MANDATORY))
        assertEquals(Coins(20), fact.amountFor(SpendCategory.OPTIONAL))
        assertEquals(Coins(10), fact.amountFor(SpendCategory.SAVINGS))
    }

    @Test
    fun `доход не попадает ни в одно направление факта`() {
        val onlyIncome = listOf(transaction(TransactionType.INCOME_PERIOD, Coins(60)))
        assertEquals(Coins.ZERO, engine().factOf(onlyIncome).total)
    }

    @Test
    fun `снятие с накоплений уменьшает факт по накоплениям`() {
        val mixed = listOf(
            transaction(TransactionType.SAVINGS_DEPOSIT, Coins(30)),
            transaction(TransactionType.SAVINGS_WITHDRAW, Coins(10)),
        )
        assertEquals(Coins(20), engine().factOf(mixed).amountFor(SpendCategory.SAVINGS))
    }

    @Test
    fun `снятие сверх пополнения даёт ноль а не отрицательный факт`() {
        val mixed = listOf(
            transaction(TransactionType.SAVINGS_DEPOSIT, Coins(10)),
            transaction(TransactionType.SAVINGS_WITHDRAW, Coins(30)),
        )
        assertEquals(Coins.ZERO, engine().factOf(mixed).amountFor(SpendCategory.SAVINGS))
    }

    @Test
    fun `непредвиденный расход попадает в обязательные`() {
        val unexpected = listOf(transaction(TransactionType.UNEXPECTED_EXPENSE, Coins(25)))
        assertEquals(Coins(25), engine().factOf(unexpected).amountFor(SpendCategory.MANDATORY))
    }

    @Test
    fun `пустой список транзакций даёт пустой факт`() {
        assertEquals(Coins.ZERO, engine().factOf(emptyList()).total)
    }

    @Test
    fun `закрытие строит отчёт по плану и транзакциям`() {
        val report = engine().close(period, plan, onPlan, state, PetGrowth.INITIAL).value.report
        assertTrue(report.planFollowed)
        assertEquals(Coins(70), report.planTotal)
        assertEquals(Coins(70), report.factTotal)
    }

    @Test
    fun `закрытие помечает период закрытым и ставит время из часов`() {
        val closed = engine().close(period, plan, onPlan, state, PetGrowth.INITIAL).value.closedPeriod
        assertEquals(PeriodStatus.CLOSED, closed.status)
        assertEquals(now, closed.closedAt)
    }

    @Test
    fun `закрытие начисляет очки роста`() {
        val outcome = engine().close(period, plan, onPlan, state, PetGrowth.INITIAL).value
        assertEquals(balance.maxGrowthPerPeriod, outcome.growth.points)
    }

    @Test
    fun `закрытие меняет состояние питомца по отчёту и ночи`() {
        val outcome = engine().close(period, plan, onPlan, state, PetGrowth.INITIAL).value
        assertEquals(Stat(90 + balance.moodBonusPlanFollowed - balance.nightDropMood), outcome.state.mood)
    }

    @Test
    fun `перенос равен нетронутому остатку`() {
        val outcome = engine().close(period, plan, onPlan, state, PetGrowth.INITIAL).value
        assertEquals(Coins(30), outcome.carryOver)
    }

    @Test
    fun `перенос обнуляется когда правила его запрещают`() {
        val noCarry = balance.copy(carryOverUnspent = false)
        val outcome = engine(noCarry).close(period, plan, onPlan, state, PetGrowth.INITIAL).value
        assertEquals(Coins.ZERO, outcome.carryOver)
    }

    @Test
    fun `баланс ушедший в минус останавливает закрытие`() {
        val broken = onPlan + transaction(TransactionType.PURCHASE_OPTIONAL, Coins(1000))
        assertThrows(IllegalArgumentException::class.java) {
            engine().close(period, plan, broken, state, PetGrowth.INITIAL)
        }
    }

    @Test
    fun `изменения состояния и роста объединяются`() {
        val nearly = PetGrowth(points = balance.growthThresholds[1] - 1, stage = GrowthStage.CUB)
        val changes = engine().close(period, plan, onPlan, state, nearly).changes
        assertTrue(changes.any { it is Change.PetStat })
        assertTrue(changes.any { it is Change.Stage })
    }

    @Test
    fun `незакрытые потребности ведут объяснение и предлагают поправить план`() {
        val hungry = state.copy(satiety = Stat(balance.needThreshold - 1))
        val result = engine().close(period, plan, onPlan, hungry, PetGrowth.INITIAL)
        assertEquals("period.missed_mandatory", result.explanation.key)
        assertEquals(RecoveryOption.ADJUST_NEXT_PLAN, result.explanation.nextStep)
        assertFalse(result.value.needsMet)
        assertEquals(0, result.value.growth.points)
    }

    /** Находка на vivo: каша дешевле плана не должна считаться промахом. */
    @Test
    fun `нужное дешевле плана при сытой сове не промах`() {
        val underspent = listOf(
            transaction(TransactionType.INCOME_PERIOD, Coins(60)),
            transaction(TransactionType.PURCHASE_MANDATORY, Coins(14)),
        )
        val result = engine().close(period, plan, underspent, state, PetGrowth.INITIAL)
        assertTrue(result.value.needsMet)
        assertNotEquals("period.missed_mandatory", result.explanation.key)
    }

    @Test
    fun `повышение стадии ведёт объяснение когда промаха нет`() {
        val nearly = PetGrowth(points = balance.growthThresholds[1] - 1, stage = GrowthStage.CUB)
        val result = engine().close(period, plan, onPlan, state, nearly)
        assertEquals("period.stage_up", result.explanation.key)
    }

    @Test
    fun `выполненный план объясняется своим ключом`() {
        val result = engine().close(period, plan, onPlan, state, PetGrowth.INITIAL)
        assertEquals("period.plan_followed", result.explanation.key)
    }

    @Test
    fun `обычный период объясняется нейтрально и без подсказки`() {
        val overspent = listOf(
            transaction(TransactionType.INCOME_PERIOD, Coins(60)),
            transaction(TransactionType.PURCHASE_MANDATORY, Coins(40)),
            transaction(TransactionType.PURCHASE_OPTIONAL, Coins(30)),
            transaction(TransactionType.SAVINGS_DEPOSIT, Coins(10)),
        )
        val result = engine().close(period, plan, overspent, state, PetGrowth.INITIAL)
        assertEquals("period.closed", result.explanation.key)
        assertNull(result.explanation.nextStep)
    }

    @Test
    fun `следующий период продолжает нумерацию и профиль`() {
        val next = engine().openNext(period, carryOver = Coins(30))
        assertEquals(4, next.number)
        assertEquals(period.profileId, next.profileId)
    }

    @Test
    fun `следующий период стартует с перенесённого остатка и планового дохода`() {
        val next = engine().openNext(period, carryOver = Coins(30))
        assertEquals(Coins(30), next.startBalance)
        assertEquals(balance.periodIncome, next.income)
        assertEquals(Coins(30) + balance.periodIncome, next.available)
    }

    @Test
    fun `следующий период открывается на этапе планирования`() {
        val next = engine().openNext(period, carryOver = Coins(30))
        assertEquals(PeriodStatus.PLANNING, next.status)
        assertNull(next.closedAt)
    }

    @Test
    fun `период на этапе планирования закрыть нельзя`() {
        val planning = period.copy(status = PeriodStatus.PLANNING)
        assertThrows(IllegalArgumentException::class.java) {
            engine().close(planning, plan, onPlan, state, PetGrowth.INITIAL)
        }
    }

    @Test
    fun `уже закрытый период закрыть повторно нельзя`() {
        val closed = period.copy(status = PeriodStatus.CLOSED, closedAt = 1)
        assertThrows(IllegalArgumentException::class.java) {
            engine().close(closed, plan, onPlan, state, PetGrowth.INITIAL)
        }
    }

    @Test
    fun `пустой план закрыть нельзя`() {
        assertThrows(IllegalArgumentException::class.java) {
            engine().close(period, BudgetPlan.EMPTY, onPlan, state, PetGrowth.INITIAL)
        }
    }

    @Test
    fun `операции чужого периода не принимаются`() {
        val alien = transaction(TransactionType.PURCHASE_MANDATORY, Coins(10)).copy(periodId = 999)
        assertThrows(IllegalArgumentException::class.java) {
            engine().close(period, plan, onPlan + alien, state, PetGrowth.INITIAL)
        }
    }

    @Test
    fun `подтверждение плана переводит период в работу`() {
        val planning = period.copy(status = PeriodStatus.PLANNING)
        assertEquals(PeriodStatus.RUNNING, engine().confirmPlan(planning).status)
    }

    @Test
    fun `подтверждение плана не трогает остальные поля периода`() {
        val planning = period.copy(status = PeriodStatus.PLANNING)
        val confirmed = engine().confirmPlan(planning)
        assertEquals(planning.copy(status = PeriodStatus.RUNNING), confirmed)
    }

    @Test
    fun `подтверждённый план повторно не подтверждается`() {
        assertThrows(IllegalArgumentException::class.java) { engine().confirmPlan(period) }
    }

    @Test
    fun `закрытый период не возвращается в работу`() {
        val closed = period.copy(status = PeriodStatus.CLOSED, closedAt = 1)
        assertThrows(IllegalArgumentException::class.java) { engine().confirmPlan(closed) }
    }

    @Test
    fun `подтверждённый план можно закрыть`() {
        val planning = period.copy(status = PeriodStatus.PLANNING)
        val running = engine().confirmPlan(planning)
        assertEquals(PeriodStatus.CLOSED, engine().close(running, plan, onPlan, state, PetGrowth.INITIAL).value.closedPeriod.status)
    }
}
