package ru.finnypet.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PetEffectTest {

    @Test
    fun `эффект без изменения не создаётся`() {
        assertThrows(IllegalArgumentException::class.java) { PetEffect(PetStatKind.MOOD, 0) }
    }

    @Test
    fun `положительный эффект распознаётся`() {
        assertTrue(PetEffect(PetStatKind.SATIETY, 15).isPositive)
    }

    @Test
    fun `отрицательный эффект распознаётся`() {
        assertFalse(PetEffect(PetStatKind.CARE, -10).isPositive)
    }

    @Test
    fun `показателей питомца ровно три`() {
        assertEquals(3, PetStatKind.entries.size)
    }

    @Test
    fun `имена показателей не меняются`() {
        assertEquals(listOf("MOOD", "SATIETY", "CARE"), PetStatKind.entries.map { it.name })
    }
}
