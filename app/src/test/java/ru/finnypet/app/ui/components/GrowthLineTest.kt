package ru.finnypet.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.finnypet.app.data.content.ContentParser
import ru.finnypet.app.data.content.RealContent
import ru.finnypet.app.domain.model.GrowthStage
import ru.finnypet.app.domain.model.PetGrowth

/**
 * Строка роста на настоящем контент-паке: пороги из `balance.json` дают
 * «до подростка 4, до взрослого 6» (DESIGN_PLAN 3.6), у взрослого —
 * «{имя} вырос!» и все звёзды. Очки — эталон 3 → 3 → 6 → 9 → 12 (раздел 4 плана).
 */
class GrowthLineTest {

    private val pack = ContentParser().parse(RealContent.raw())
    private val thresholds = pack.balance.growthThresholds

    @Test
    fun `день 1 — 3 из 4 до подростка`() {
        val summary = growthSummary(PetGrowth(3, GrowthStage.CUB), thresholds, pack.texts, "Пушок")

        assertEquals(GrowthView(GrowthStage.YOUNG, points = 3, target = 4), summary.growth)
    }

    @Test
    fun `день 4 — подросток с 9 очками, 5 из 6 до взрослого`() {
        val summary = growthSummary(PetGrowth(9, GrowthStage.YOUNG), thresholds, pack.texts, "Пушок")

        assertEquals(GrowthView(GrowthStage.GROWN, points = 5, target = 6), summary.growth)
    }

    @Test
    fun `день 5 — взрослый, имя питомца, а не название игры, и все звёзды`() {
        val summary = growthSummary(PetGrowth(12, GrowthStage.GROWN), thresholds, pack.texts, "Пушок")

        assertNull(summary.growth)
        assertEquals("Пушок вырос!", summary.grownMessage)
        assertEquals(12, summary.points)
    }
}
