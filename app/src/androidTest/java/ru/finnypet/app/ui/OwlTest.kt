package ru.finnypet.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.finnypet.app.domain.content.ContentOption
import ru.finnypet.app.domain.content.PetColor
import ru.finnypet.app.domain.model.GrowthStage
import ru.finnypet.app.domain.model.PetMood
import ru.finnypet.app.ui.components.Owl
import ru.finnypet.app.ui.components.OwlLook
import ru.finnypet.app.ui.theme.FinnypetTheme

/**
 * Сова, нарисованная кодом (AD-1): рисуется в любом сочетании и для TalkBack
 * описана словами — картинка озвучке недоступна (ТЗ 3.6).
 */
@RunWith(AndroidJUnit4::class)
class OwlTest {

    @get:Rule
    val compose = createComposeRule()

    private val cream = PetColor(
        option = ContentOption("cream", "pet.color.cream"),
        body = 0xFFF0D4AE,
        wing = 0xFFD9B488,
        face = 0xFFFFF5E6,
        ring = 0xFFC8996A,
    )

    @Test
    fun сова_в_аксессуаре_видна_и_описана() {
        show(look(accessoryId = "scarf", mood = PetMood.SAD, description = "Сова Пушок грустит: хочет есть"))

        compose.onNodeWithContentDescription("Сова Пушок грустит: хочет есть").assertIsDisplayed()
    }

    /** Аксессуар из pets.json, которого код не умеет рисовать, — та же сова без него, не пустое место. */
    @Test
    fun незнакомый_аксессуар_не_мешает_сове() {
        show(look(accessoryId = "nothing", mood = PetMood.CALM, description = "Сова Пушок спокойна"))

        compose.onNodeWithContentDescription("Сова Пушок спокойна").assertIsDisplayed()
    }

    @Test
    fun взрослая_радостная_сова_рисуется() {
        show(look(accessoryId = "glasses", mood = PetMood.HAPPY, description = "Сова Пушок радуется", stage = GrowthStage.GROWN))

        compose.onNodeWithContentDescription("Сова Пушок радуется").assertIsDisplayed()
    }

    private fun look(accessoryId: String?, mood: PetMood, description: String, stage: GrowthStage = GrowthStage.CUB) =
        OwlLook(colors = cream, stage = stage, mood = mood, accessoryId = accessoryId, description = description)

    private fun show(look: OwlLook) {
        compose.setContent {
            FinnypetTheme { Owl(look = look) }
        }
    }
}
