package ru.finnypet.app.domain.economy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.finnypet.app.domain.model.Change
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.GrowthStage
import ru.finnypet.app.domain.model.PetGrowth
import ru.finnypet.app.domain.model.SpendCategory

class GrowthEngineTest {

    private val balance = GameBalance.PLACEHOLDER
    private val engine = GrowthEngine(balance)

    private fun report(
        mandatoryOk: Boolean = true,
        optionalOk: Boolean = true,
        savingsOk: Boolean = true,
    ): PlanFactReport {
        val lines = listOf(
            PlanFactLine(SpendCategory.MANDATORY, Coins(40), if (mandatoryOk) Coins(40) else Coins(50)),
            PlanFactLine(SpendCategory.OPTIONAL, Coins(20), if (optionalOk) Coins(20) else Coins(30)),
            PlanFactLine(SpendCategory.SAVINGS, Coins(10), if (savingsOk) Coins(10) else Coins(5)),
        )
        return PlanFactReport(
            lines = lines,
            planTotal = Coins(70),
            factTotal = lines.fold(Coins.ZERO) { acc, line -> acc + line.actual },
        )
    }

    private val bad = report(mandatoryOk = false, optionalOk = false, savingsOk = false)

    @Test
    fun `безупречный период даёт максимум очков`() {
        assertEquals(balance.maxGrowthPerPeriod, engine.pointsFor(report(), needsMet = true))
    }

    @Test
    fun `промах по всем направлениям не даёт очков`() {
        assertEquals(0, engine.pointsFor(bad, needsMet = false))
    }

    @Test
    fun `закрытые потребности дают свои очки`() {
        assertEquals(balance.growthForMandatoryCovered, engine.pointsFor(bad, needsMet = true))
    }

    @Test
    fun `сохранённые накопления дают свои очки`() {
        val onlySavings = report(mandatoryOk = false, optionalOk = false, savingsOk = true)
        assertEquals(
            balance.growthForMandatoryCovered + balance.growthForSavingsKept,
            engine.pointsFor(onlySavings, needsMet = true),
        )
    }

    /** AD-3: сова не растёт, если её не кормят, как бы ни был выполнен план. */
    @Test
    fun `в голодный день очков нет даже при выполненном плане и копилке`() {
        assertEquals(0, engine.pointsFor(report(), needsMet = false))
    }

    /** Раздел 4 плана, день 2: мячик куплен, копилка пополнена, каша — нет. */
    @Test
    fun `день с ошибкой из эталонного сценария не приносит очков`() {
        val dayTwo = PlanFactReport(
            lines = listOf(
                PlanFactLine(SpendCategory.MANDATORY, Coins(3), Coins.ZERO),
                PlanFactLine(SpendCategory.OPTIONAL, Coins(24), Coins(24)),
                PlanFactLine(SpendCategory.SAVINGS, Coins(8), Coins(8)),
            ),
            planTotal = Coins(35),
            factTotal = Coins(32),
        )
        val result = engine.apply(PetGrowth(points = 6, stage = GrowthStage.CUB), dayTwo, needsMet = false)
        assertEquals(6, result.value.points)
        assertEquals("growth.no_points", result.explanation.key)
    }

    @Test
    fun `новый питомец на первой стадии`() {
        assertEquals(GrowthStage.CUB, engine.stageFor(0))
    }

    @Test
    fun `очков на единицу меньше порога оставляют на прежней стадии`() {
        assertEquals(GrowthStage.CUB, engine.stageFor(balance.growthThresholds[1] - 1))
    }

    @Test
    fun `ровно на пороге стадия повышается`() {
        assertEquals(GrowthStage.YOUNG, engine.stageFor(balance.growthThresholds[1]))
    }

    @Test
    fun `очки выше последнего порога оставляют на последней стадии`() {
        assertEquals(GrowthStage.GROWN, engine.stageFor(balance.growthThresholds.last() + 100))
    }

    @Test
    fun `очки накапливаются между периодами`() {
        val after = engine.apply(PetGrowth(points = 3, stage = GrowthStage.CUB), report(), needsMet = true)
        assertEquals(3 + balance.maxGrowthPerPeriod, after.value.points)
    }

    @Test
    fun `переход порога меняет стадию`() {
        val nearly = PetGrowth(points = balance.growthThresholds[1] - 1, stage = GrowthStage.CUB)
        assertEquals(GrowthStage.YOUNG, engine.apply(nearly, report(), needsMet = true).value.stage)
    }

    @Test
    fun `смена стадии сообщается отдельным изменением`() {
        val nearly = PetGrowth(points = balance.growthThresholds[1] - 1, stage = GrowthStage.CUB)
        assertEquals(
            listOf(Change.Stage(from = GrowthStage.CUB, to = GrowthStage.YOUNG)),
            engine.apply(nearly, report(), needsMet = true).changes,
        )
    }

    @Test
    fun `без смены стадии изменений нет`() {
        assertTrue(engine.apply(PetGrowth.INITIAL, report(), needsMet = true).changes.isEmpty())
    }

    @Test
    fun `стадия не падает после неудачного периода`() {
        val grown = PetGrowth(points = balance.growthThresholds.last(), stage = GrowthStage.GROWN)
        val after = engine.apply(grown, bad, needsMet = false)
        assertEquals(GrowthStage.GROWN, after.value.stage)
        assertEquals(grown.points, after.value.points)
    }

    @Test
    fun `повышение стадии объясняется своим ключом`() {
        val nearly = PetGrowth(points = balance.growthThresholds[1] - 1, stage = GrowthStage.CUB)
        assertEquals("growth.stage_up", engine.apply(nearly, report(), needsMet = true).explanation.key)
    }

    @Test
    fun `набранные очки без повышения объясняются своим ключом`() {
        assertEquals("growth.points_added", engine.apply(PetGrowth.INITIAL, report(), needsMet = true).explanation.key)
    }

    @Test
    fun `отсутствие очков объясняется своим ключом`() {
        assertEquals("growth.no_points", engine.apply(PetGrowth.INITIAL, bad, needsMet = false).explanation.key)
    }

    @Test
    fun `объяснение несёт заработанные и накопленные очки`() {
        val result = engine.apply(PetGrowth(points = 3, stage = GrowthStage.CUB), report(), needsMet = true)
        assertEquals(balance.maxGrowthPerPeriod.toString(), result.explanation.args["earned"])
        assertEquals((3 + balance.maxGrowthPerPeriod).toString(), result.explanation.args["points"])
    }

    @Test
    fun `стадия не падает когда пороги подняли под уже сохранённым прогрессом`() {
        val raised = GrowthEngine(balance.copy(growthThresholds = listOf(0, 20, 40)))
        val stale = PetGrowth(points = 15, stage = GrowthStage.YOUNG)
        val result = raised.apply(stale, bad, needsMet = false)
        assertEquals(GrowthStage.YOUNG, result.value.stage)
    }

    @Test
    fun `удержание стадии не объявляется повышением`() {
        val raised = GrowthEngine(balance.copy(growthThresholds = listOf(0, 20, 40)))
        val stale = PetGrowth(points = 15, stage = GrowthStage.YOUNG)
        val result = raised.apply(stale, bad, needsMet = false)
        assertEquals("growth.no_points", result.explanation.key)
        assertTrue(result.changes.isEmpty())
    }

    @Test
    fun `отрицательные очки не принимаются`() {
        assertThrows(IllegalArgumentException::class.java) { engine.stageFor(-1) }
    }

    /** План, где распределены только обязательные: два других направления ребёнок не трогал. */
    private fun onlyMandatoryPlanned() = PlanFactReport(
        lines = listOf(
            PlanFactLine(SpendCategory.MANDATORY, Coins(40), Coins(40)),
            PlanFactLine(SpendCategory.OPTIONAL, Coins.ZERO, Coins.ZERO),
            PlanFactLine(SpendCategory.SAVINGS, Coins.ZERO, Coins.ZERO),
        ),
        planTotal = Coins(40),
        factTotal = Coins(40),
    )

    @Test
    fun `нераспределённые накопления не приносят очков`() {
        val expected = balance.growthForMandatoryCovered + balance.growthForPlanFollowed
        assertEquals(expected, engine.pointsFor(onlyMandatoryPlanned(), needsMet = true))
    }

    @Test
    fun `нераспределённое направление не даёт максимум очков`() {
        assertTrue(engine.pointsFor(onlyMandatoryPlanned(), needsMet = true) < balance.maxGrowthPerPeriod)
    }

    /** Замечание 13: раньше нулевой план на нужное засчитывался и сова росла без еды. */
    @Test
    fun `нулевой план на нужное при голодной сове не приносит очков`() {
        assertEquals(0, engine.pointsFor(nothingForNeeds(), needsMet = false))
    }

    /** Сыта ли сова, решают потребности, а не сумма в плане на нужное (R3). */
    @Test
    fun `нулевой план на нужное при сытой сове приносит очки за потребности`() {
        val expected = balance.growthForMandatoryCovered + balance.growthForSavingsKept + balance.growthForPlanFollowed
        assertEquals(expected, engine.pointsFor(nothingForNeeds(), needsMet = true))
    }

    private fun nothingForNeeds() = PlanFactReport(
            lines = listOf(
                PlanFactLine(SpendCategory.MANDATORY, Coins.ZERO, Coins.ZERO),
                PlanFactLine(SpendCategory.OPTIONAL, Coins.ZERO, Coins.ZERO),
                PlanFactLine(SpendCategory.SAVINGS, Coins(15), Coins(15)),
            ),
            planTotal = Coins(15),
            factTotal = Coins(15),
        )
}
