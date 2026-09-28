package ru.finnypet.app.ui.screens.createpet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.finnypet.app.domain.content.ContentOption
import ru.finnypet.app.domain.content.PetColor

/** DESIGN_PLAN 3.3: подпись окраса и причина неактивной «Готово». */
class CreatePetStateTest {

    @Test
    fun `подпись окраса — название выбранного строчными`() {
        assertEquals("дымчатый", state(colorId = "black").colorTitle)
        assertEquals("кремовый", state(colorId = "cream").colorTitle)
    }

    @Test
    fun `без любого из имён создать нельзя и причина объясняется`() {
        listOf(state(), state(childName = "Егор"), state(petName = "Финни"), state(childName = "  ", petName = "Финни"))
            .forEach {
                assertTrue(it.needsNames)
                assertFalse(it.canCreate)
            }
    }

    @Test
    fun `с обоими именами создать можно, объяснять нечего`() {
        val ready = state(childName = "Егор", petName = "Финни")

        assertFalse(ready.needsNames)
        assertTrue(ready.canCreate)
    }

    /** Во время сохранения кнопка гаснет, но имена на месте — строка про имена была бы неправдой. */
    @Test
    fun `при сохранении кнопка неактивна без объяснения про имена`() {
        val saving = state(childName = "Егор", petName = "Финни").copy(saving = true)

        assertFalse(saving.canCreate)
        assertFalse(saving.needsNames)
    }

    private fun state(colorId: String = "cream", childName: String = "", petName: String = "") = CreatePetState(
        palette = listOf(color("cream"), color("black")),
        bodies = listOf(AppearanceOption("owl", "Совёнок")),
        colors = listOf(AppearanceOption("cream", "Кремовый"), AppearanceOption("black", "Дымчатый")),
        accessories = listOf(AppearanceOption("scarf", "Шарфик")),
        bodyId = "owl",
        colorId = colorId,
        childName = childName,
        petName = petName,
    )

    private fun color(id: String) =
        PetColor(option = ContentOption(id, "pet.color.$id"), body = 0, wing = 0, face = 0, ring = 0)
}
