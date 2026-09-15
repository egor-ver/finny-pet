package ru.finnypet.app.domain.economy

import org.junit.Assert.assertEquals
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
            PlanFactLine(SpendCategory.MANDATORY, Coins(40), if (mandatoryOk) Coins(40) else Coins(30)),
            PlanFactLine(SpendCategory.OPTIONAL, Coins(20), if (optionalOk) Coins(20) else Coins(30)),
            PlanFactLine(SpendCategory.SAVINGS, Coins(10), if (savingsOk) Coins(10) else Coins(5)),
        )
        return PlanFactReport(
            lines = lines,
            planTotal = Coins(70),
            factTotal = lines.fold(Coins.ZERO) { acc, line -> acc + line.actual },
        )
    }

    @Test
    fun `безупречный период даёт максимум очков`() {
        assertEquals(balance.maxGrowthPerPeriod, engine.pointsFor(report()))
    }

    @Test
    fun `промах по всем направлениям не даёт очков`() {
        val bad = report(mandatoryOk = false, optionalOk = false, savingsOk = false)
        assertEquals(0, engine.pointsFor(bad))
    }

    @Test
    fun `закрытые обязательные дают свои очки`() {
        val onlyMandatory = report(mandatoryOk = true, optionalOk = false, savingsOk = false)
        assertEquals(balance.growthForMandatoryCovered, engine.pointsFor(onlyMandatory))
    }

    @Test
    fun `сохранённые накопления дают свои очки`() {
        val noMandatory = report(mandatoryOk = false, optionalOk = false, savingsOk = true)
        assertEquals(balance.growthForSavingsKept, engine.pointsFor(noMandatory))
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
        val after = engine.apply(PetGrowth(points = 3, stage = GrowthStage.CUB), report())
        assertEquals(3 + balance.maxGrowthPerPeriod, after.value.points)
    }

    @Test
    fun `переход порога меняет стадию`() {
        val nearly = PetGrowth(points = balance.growthThresholds[1] - 1, stage = GrowthStage.CUB)
        assertEquals(GrowthStage.YOUNG, engine.apply(nearly, report()).value.stage)
    }

    @Test
    fun `смена стадии сообщается отдельным изменением`() {
        val nearly = PetGrowth(points = balance.growthThresholds[1] - 1, stage = GrowthStage.CUB)
        assertEquals(
            listOf(Change.Stage(from = GrowthStage.CUB, to = GrowthStage.YOUNG)),
            engine.apply(nearly, report()).changes,
        )
    }

    @Test
    fun `без смены стадии изменений нет`() {
        assertTrue(engine.apply(PetGrowth.INITIAL, report()).changes.isEmpty())
    }

    @Test
    fun `стадия не падает после неудачного периода`() {
        val grown = PetGrowth(points = balance.growthThresholds.last(), stage = GrowthStage.GROWN)
        val bad = report(mandatoryOk = false, optionalOk = false, savingsOk = false)
        val after = engine.apply(grown, bad)
        assertEquals(GrowthStage.GROWN, after.value.stage)
        assertEquals(grown.points, after.value.points)
    }

    @Test
    fun `повышение стадии объясняется своим ключом`() {
        val nearly = PetGrowth(points = balance.growthThresholds[1] - 1, stage = GrowthStage.CUB)
        assertEquals("growth.stage_up", engine.apply(nearly, report()).explanation.key)
    }

    @Test
    fun `набранные очки без повышения объясняются своим ключом`() {
        assertEquals("growth.points_added", engine.apply(PetGrowth.INITIAL, report()).explanation.key)
    }

    @Test
    fun `отсутствие очков объясняется своим ключом`() {
        val bad = report(mandatoryOk = false, optionalOk = false, savingsOk = false)
        assertEquals("growth.no_points", engine.apply(PetGrowth.INITIAL, bad).explanation.key)
    }

    @Test
    fun `объяснение несёт заработанные и накопленные очки`() {
        val result = engine.apply(PetGrowth(points = 3, stage = GrowthStage.CUB), report())
        assertEquals(balance.maxGrowthPerPeriod.toString(), result.explanation.args["earned"])
        assertEquals((3 + balance.maxGrowthPerPeriod).toString(), result.explanation.args["points"])
    }
}
