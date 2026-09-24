package ru.finnypet.app.domain.economy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.finnypet.app.domain.model.BudgetPlan
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.PeriodFact
import ru.finnypet.app.domain.model.SpendCategory

class BudgetEngineTest {

    /** Шаг кнопкой: добавить не больше, чем осталось, убрать не больше, чем лежит. */
    @Test
    fun `ползунок вверх урезается по свободным монетам`() {
        val plan = BudgetPlan(Coins(30), Coins(5), Coins(0))

        assertEquals(Coins(10), BudgetEngine().clamped(plan, SpendCategory.OPTIONAL, Coins(10), Coins(40)))
        assertEquals(Coins(7), BudgetEngine().clamped(plan, SpendCategory.OPTIONAL, Coins(10), Coins(37)))
        assertEquals(Coins(5), BudgetEngine().clamped(plan, SpendCategory.OPTIONAL, Coins(10), Coins(35)))
    }

    @Test
    fun `ползунок вниз двигается свободно до нуля`() {
        val plan = BudgetPlan(Coins(30), Coins(3), Coins(0))

        assertEquals(Coins(0), BudgetEngine().clamped(plan, SpendCategory.OPTIONAL, Coins(0), Coins(40)))
        assertEquals(Coins(25), BudgetEngine().clamped(plan, SpendCategory.MANDATORY, Coins(25), Coins(40)))
        assertEquals(Coins(0), BudgetEngine().clamped(plan, SpendCategory.SAVINGS, Coins(0), Coins(40)))
    }

    /** Кошелёк уменьшился после черновика: банке остаётся только то, что не занято другими. */
    @Test
    fun `когда другие банки заняли весь кошелёк — ноль`() {
        val plan = BudgetPlan(Coins(30), Coins(15), Coins(0))

        assertEquals(Coins(0), BudgetEngine().clamped(plan, SpendCategory.SAVINGS, Coins(5), Coins(40)))
    }

    private val engine = BudgetEngine()

    private val plan = BudgetPlan(
        mandatory = Coins(40),
        optional = Coins(20),
        savings = Coins(10),
    )

    @Test
    fun `план меньше бюджета укладывается и оставляет остаток`() {
        assertEquals(PlanCheck.Fits(Coins(30)), engine.check(plan, Coins(100)))
    }

    @Test
    fun `план ровно на весь бюджет укладывается с нулевым остатком`() {
        assertEquals(PlanCheck.Fits(Coins.ZERO), engine.check(plan, Coins(70)))
    }

    @Test
    fun `план на монету больше бюджета отклоняется`() {
        assertEquals(PlanCheck.Exceeds(Coins(1)), engine.check(plan, Coins(69)))
    }

    @Test
    fun `отклонённый план сообщает размер превышения`() {
        assertEquals(PlanCheck.Exceeds(Coins(20)), engine.check(plan, Coins(50)))
    }

    @Test
    fun `пустой план укладывается и оставляет весь бюджет`() {
        assertEquals(PlanCheck.Fits(Coins(100)), engine.check(BudgetPlan.EMPTY, Coins(100)))
    }

    @Test
    fun `пустой план при нулевом бюджете укладывается`() {
        assertEquals(PlanCheck.Fits(Coins.ZERO), engine.check(BudgetPlan.EMPTY, Coins.ZERO))
    }

    @Test
    fun `отчёт содержит строку по каждому направлению`() {
        val report = engine.compare(plan, PeriodFact.of())
        assertEquals(SpendCategory.entries.toSet(), report.lines.map { it.category }.toSet())
    }

    @Test
    fun `отчёт переносит планируемые и фактические суммы`() {
        val fact = PeriodFact.of(mandatory = Coins(35), optional = Coins(30), savings = Coins(10))
        val line = engine.compare(plan, fact).line(SpendCategory.OPTIONAL)
        assertEquals(Coins(20), line.planned)
        assertEquals(Coins(30), line.actual)
    }

    @Test
    fun `отчёт считает итоги плана и факта`() {
        val fact = PeriodFact.of(mandatory = Coins(35), optional = Coins(30), savings = Coins(10))
        val report = engine.compare(plan, fact)
        assertEquals(Coins(70), report.planTotal)
        assertEquals(Coins(75), report.factTotal)
    }

    @Test
    fun `отклонение положительно когда потратил больше плана`() {
        val fact = PeriodFact.of(optional = Coins(30))
        assertEquals(10, engine.compare(plan, fact).line(SpendCategory.OPTIONAL).deviation)
    }

    @Test
    fun `отклонение отрицательно когда потратил меньше плана`() {
        val fact = PeriodFact.of(optional = Coins(5))
        assertEquals(-15, engine.compare(plan, fact).line(SpendCategory.OPTIONAL).deviation)
    }

    @Test
    fun `обязательные засчитаны когда потрачено ровно по плану`() {
        val fact = PeriodFact.of(mandatory = Coins(40))
        assertTrue(engine.compare(plan, fact).line(SpendCategory.MANDATORY).followed)
    }

    @Test
    fun `обязательные не засчитаны когда потрачено больше плана`() {
        val fact = PeriodFact.of(mandatory = Coins(45))
        assertFalse(engine.compare(plan, fact).line(SpendCategory.MANDATORY).followed)
    }

    /** R3: сэкономил на нужном — не ошибка, остаток переходит на завтра. */
    @Test
    fun `обязательные засчитаны когда потрачено меньше плана`() {
        val fact = PeriodFact.of(mandatory = Coins(39))
        assertTrue(engine.compare(plan, fact).line(SpendCategory.MANDATORY).followed)
    }

    @Test
    fun `накопления засчитаны когда отложено не меньше плана`() {
        val fact = PeriodFact.of(savings = Coins(10))
        assertTrue(engine.compare(plan, fact).savingsKept)
    }

    @Test
    fun `накопления не засчитаны когда отложено меньше плана`() {
        val fact = PeriodFact.of(savings = Coins(9))
        assertFalse(engine.compare(plan, fact).savingsKept)
    }

    @Test
    fun `необязательные засчитаны когда потрачено меньше плана`() {
        val fact = PeriodFact.of(optional = Coins(5))
        assertTrue(engine.compare(plan, fact).line(SpendCategory.OPTIONAL).followed)
    }

    @Test
    fun `необязательные засчитаны когда не потрачено ничего`() {
        val fact = PeriodFact.of(optional = Coins.ZERO)
        assertTrue(engine.compare(plan, fact).line(SpendCategory.OPTIONAL).followed)
    }

    @Test
    fun `необязательные не засчитаны когда потрачено больше плана`() {
        val fact = PeriodFact.of(optional = Coins(21))
        assertFalse(engine.compare(plan, fact).line(SpendCategory.OPTIONAL).followed)
    }

    @Test
    fun `план исполнен когда все три направления засчитаны`() {
        val fact = PeriodFact.of(mandatory = Coins(40), optional = Coins(20), savings = Coins(10))
        assertTrue(engine.compare(plan, fact).planFollowed)
    }

    @Test
    fun `план не исполнен когда превышены необязательные`() {
        val fact = PeriodFact.of(mandatory = Coins(40), optional = Coins(25), savings = Coins(10))
        assertFalse(engine.compare(plan, fact).planFollowed)
    }

    @Test
    fun `план не исполнен когда обязательные сверх плана`() {
        val fact = PeriodFact.of(mandatory = Coins(45), optional = Coins(20), savings = Coins(10))
        assertFalse(engine.compare(plan, fact).planFollowed)
    }

    /** Находка на vivo: каша за 14 при плане 40 не должна ломать план. */
    @Test
    fun `экономия на обязательных не ломает исполнение плана`() {
        val fact = PeriodFact.of(mandatory = Coins(14), optional = Coins(20), savings = Coins(10))
        assertTrue(engine.compare(plan, fact).planFollowed)
    }

    @Test
    fun `отказ от необязательной покупки не ломает исполнение плана`() {
        val fact = PeriodFact.of(mandatory = Coins(40), optional = Coins.ZERO, savings = Coins(10))
        assertTrue(engine.compare(plan, fact).planFollowed)
    }

    @Test
    fun `период без единой траты соблюдает траты но не накопления`() {
        val report = engine.compare(plan, PeriodFact.EMPTY)
        assertTrue(report.line(SpendCategory.MANDATORY).followed)
        assertFalse(report.savingsKept)
        assertTrue(report.line(SpendCategory.OPTIONAL).followed)
    }

    @Test
    fun `отчёт с повторяющимся направлением не собирается`() {
        assertThrows(IllegalArgumentException::class.java) {
            PlanFactReport(
                lines = listOf(
                    PlanFactLine(SpendCategory.MANDATORY, Coins(40), Coins(40)),
                    PlanFactLine(SpendCategory.MANDATORY, Coins(40), Coins(10)),
                    PlanFactLine(SpendCategory.OPTIONAL, Coins(20), Coins(20)),
                    PlanFactLine(SpendCategory.SAVINGS, Coins(10), Coins(10)),
                ),
                planTotal = Coins(70),
                factTotal = Coins(80),
            )
        }
    }

    @Test
    fun `отчёт с переставленными направлениями не собирается`() {
        assertThrows(IllegalArgumentException::class.java) {
            PlanFactReport(
                lines = listOf(
                    PlanFactLine(SpendCategory.SAVINGS, Coins(10), Coins(10)),
                    PlanFactLine(SpendCategory.OPTIONAL, Coins(20), Coins(20)),
                    PlanFactLine(SpendCategory.MANDATORY, Coins(40), Coins(40)),
                ),
                planTotal = Coins(70),
                factTotal = Coins(70),
            )
        }
    }

    @Test
    fun `движок строит строки в порядке направлений`() {
        val report = engine.compare(plan, PeriodFact.EMPTY)
        assertEquals(SpendCategory.entries.toList(), report.lines.map { it.category })
    }
}
