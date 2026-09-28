package ru.finnypet.app.ui.components

import org.junit.Assert.assertTrue
import org.junit.Test
import ru.finnypet.app.domain.model.GrowthStage

/**
 * Аксессуары совы сидят ровно на каждой стадии (замечание владельца 28.09):
 * стёкла очков не заходят друг на друга и обводят глаз, шарф лежит на шее и
 * не заезжает на крылья.
 */
class OwlAccessoriesTest {

    @Test
    fun `стёкла очков не перекрываются, между ними место под перемычку`() {
        GrowthStage.entries.forEach { stage ->
            val fit = glassesFit(proportionsOf(stage))
            // Внешний край стекла — радиус плюс половина обводки (4 / 2).
            val gap = 2 * fit.eyeDx - 2 * (fit.lens + 2f)
            assertTrue("$stage: стёкла сходятся, зазор $gap", gap >= 6f)
        }
    }

    @Test
    fun `стекло обводит глаз и не выходит за голову`() {
        GrowthStage.entries.forEach { stage ->
            val g = proportionsOf(stage)
            val fit = glassesFit(g)
            assertTrue("$stage: стекло меньше глаза", fit.lens > g.eye)
            assertTrue("$stage: очки шире головы", fit.eyeDx + fit.lens < g.half)
        }
    }

    @Test
    fun `шарф ниже глаз и уже тела — крылья открыты`() {
        GrowthStage.entries.forEach { stage ->
            val g = proportionsOf(stage)
            val fit = scarfFit(g)
            val eyeBottom = g.top + g.eyeAt * g.height + g.eye
            assertTrue("$stage: шарф на глазах", fit.neckY > eyeBottom)
            // Внутренний край крыла — около 0,84 полуширины тела.
            assertTrue("$stage: шарф на крыльях", fit.halfWidth < 0.84f * g.half)
            assertTrue("$stage: шарф уже шеи", fit.halfWidth > 0.6f * g.half)
        }
    }
}
