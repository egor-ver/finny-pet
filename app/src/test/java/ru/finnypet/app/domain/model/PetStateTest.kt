package ru.finnypet.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class PetStateTest {

    private val state = PetState(mood = Stat(70), satiety = Stat(50), care = Stat(30))

    @Test
    fun `statFor возвращает показатель по каждому виду`() {
        assertEquals(Stat(70), state.statFor(PetStatKind.MOOD))
        assertEquals(Stat(50), state.statFor(PetStatKind.SATIETY))
        assertEquals(Stat(30), state.statFor(PetStatKind.CARE))
    }

    @Test
    fun `with меняет только указанный показатель`() {
        assertEquals(PetState(Stat(99), Stat(50), Stat(30)), state.with(PetStatKind.MOOD, Stat(99)))
        assertEquals(PetState(Stat(70), Stat(99), Stat(30)), state.with(PetStatKind.SATIETY, Stat(99)))
        assertEquals(PetState(Stat(70), Stat(50), Stat(99)), state.with(PetStatKind.CARE, Stat(99)))
    }

    @Test
    fun `with не меняет исходное состояние`() {
        state.with(PetStatKind.MOOD, Stat(99))
        assertEquals(Stat(70), state.mood)
    }

    @Test
    fun `uniform выставляет все показатели одинаково`() {
        assertEquals(PetState(Stat(70), Stat(70), Stat(70)), PetState.uniform(Stat(70)))
    }

    @Test
    fun `по умолчанию аксессуара нет`() {
        assertNull(PetAppearance(bodyId = "round", colorId = "mint").accessoryId)
    }

    @Test
    fun `пустой тип тела не допускается`() {
        assertThrows(IllegalArgumentException::class.java) { PetAppearance(bodyId = "", colorId = "mint") }
    }

    @Test
    fun `пустой окрас не допускается`() {
        assertThrows(IllegalArgumentException::class.java) { PetAppearance(bodyId = "round", colorId = "   ") }
    }

    @Test
    fun `пустой аксессуар не допускается`() {
        assertThrows(IllegalArgumentException::class.java) {
            PetAppearance(bodyId = "round", colorId = "mint", accessoryId = "")
        }
    }

    @Test
    fun `стадий развития ровно три`() {
        assertEquals(3, GrowthStage.entries.size)
    }

    @Test
    fun `имена стадий не меняются`() {
        assertEquals(listOf("CUB", "YOUNG", "GROWN"), GrowthStage.entries.map { it.name })
    }

    @Test
    fun `новый питомец начинает с нуля и первой стадии`() {
        assertEquals(0, PetGrowth.INITIAL.points)
        assertEquals(GrowthStage.CUB, PetGrowth.INITIAL.stage)
    }

    @Test
    fun `отрицательные очки роста не допускаются`() {
        assertThrows(IllegalArgumentException::class.java) { PetGrowth(points = -1, stage = GrowthStage.CUB) }
    }
}
