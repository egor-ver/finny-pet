package ru.finnypet.app.data.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.finnypet.app.domain.model.GrowthStage
import ru.finnypet.app.domain.model.PetAppearance
import ru.finnypet.app.domain.model.PetMood
import ru.finnypet.app.domain.model.PetStatKind
import ru.finnypet.app.ui.components.owlDescription
import ru.finnypet.app.ui.components.owlLook

/**
 * Внешность совы из `pets.json`: сова рисуется кодом (AD-1), поэтому окрас —
 * это набор цветов, а не картинки. Опечатка в цвете ловится здесь, а не
 * пустой совой на экране.
 */
class PetColorsTest {

    private val parser = ContentParser()
    private val pets = parser.parse(RealContent.raw()).pets

    /** ТЗ 2.6: не меньше 9 внешностей, даже без варианта «без аксессуара». */
    @Test
    fun `окрасы и аксессуары дают не меньше девяти внешностей`() {
        assertEquals(5, pets.colors.size)
        assertTrue(pets.bodies.size * pets.colors.size * pets.accessories.size >= 9)
    }

    @Test
    fun `рыжий окрас есть и рисуется своими цветами`() {
        val ginger = pets.colors.single { it.id == "ginger" }

        assertEquals(0xFFEC9447L, ginger.body)
        assertEquals("pet.color.ginger", ginger.option.titleKey)
    }

    @Test
    fun `все цвета непрозрачные`() {
        pets.colors.forEach { color ->
            listOf(color.body, color.wing, color.face, color.ring).forEach { argb ->
                assertEquals("Окрас ${color.id}", 0xFF, (argb shr 24).toInt())
            }
        }
    }

    @Test
    fun `цвет не в формате RRGGBB называет окрас`() {
        val broken = RealContent.asset("pets.json").replace("\"#EC9447\"", "\"рыжий\"")

        val error = runCatching { parser.parse(RealContent.raw(pets = broken)) }.exceptionOrNull()

        assertTrue("Не назван окрас: ${error?.message}", error is ContentParseException && error.message!!.contains("ginger"))
    }

    /** Окрас пропал из pets.json после правки — сова рисуется первым, а не роняет экран. */
    @Test
    fun `неизвестный окрас заменяется первым`() {
        val look = owlLook(
            pets = pets,
            appearance = PetAppearance(bodyId = "owl", colorId = "nocolor", accessoryId = null),
            stage = GrowthStage.CUB,
            mood = PetMood.CALM,
            description = "",
        )

        assertEquals(pets.colors.first(), look.colors)
    }

    @Test
    fun `описание для TalkBack говорит, почему сова грустит`() {
        val texts = parser.parse(RealContent.raw()).texts

        assertEquals("Совёнок Пушок грустит: хочет есть", owlDescription(texts, "Пушок", PetMood.SAD, PetStatKind.SATIETY))
        assertEquals("Совёнок Пушок радуется", owlDescription(texts, "Пушок", PetMood.HAPPY, sadAbout = null))
    }
}
