package ru.finnypet.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class ProfileTest {

    private val appearance = PetAppearance(bodyId = "round", colorId = "mint")

    private fun profile(childName: String = "Егор", petName: String = "Финни") = Profile(
        id = ProfileId("p1"),
        childName = childName,
        petName = petName,
        appearance = appearance,
        createdAt = 0,
    )

    @Test
    fun `по умолчанию профиль не тестовый`() {
        assertFalse(profile().isTest)
    }

    @Test
    fun `пустое игровое имя не допускается`() {
        assertThrows(IllegalArgumentException::class.java) { profile(childName = "  ") }
    }

    @Test
    fun `пустое имя питомца не допускается`() {
        assertThrows(IllegalArgumentException::class.java) { profile(petName = "") }
    }

    @Test
    fun `слишком длинное игровое имя не допускается`() {
        assertThrows(IllegalArgumentException::class.java) {
            profile(childName = "я".repeat(Profile.MAX_NAME_LENGTH + 1))
        }
    }

    @Test
    fun `имя на границе длины допускается`() {
        assertEquals(
            Profile.MAX_NAME_LENGTH,
            profile(childName = "я".repeat(Profile.MAX_NAME_LENGTH)).childName.length,
        )
    }
}
