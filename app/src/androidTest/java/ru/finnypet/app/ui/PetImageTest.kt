package ru.finnypet.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.finnypet.app.R
import ru.finnypet.app.domain.model.GrowthStage
import ru.finnypet.app.domain.model.PetAppearance
import ru.finnypet.app.ui.components.PetImage
import ru.finnypet.app.ui.theme.FinnypetTheme

/**
 * Картинка питомца на настоящих ассетах: какой файл показан.
 *
 * Тег картинки — путь файла, из которого она прочитана, поэтому видно не
 * только «что-то нарисовано», а именно сова в шарфе или запасная без него.
 * Файлы берутся те, что напарник не удалит: кремовая сова и её шарф.
 */
@RunWith(AndroidJUnit4::class)
class PetImageTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun с_аксессуаром_показана_сова_в_аксессуаре() {
        show(PetAppearance(bodyId = "owl", colorId = "cream", accessoryId = "scarf"))

        awaitTag("content/v1/pets/owl_cream_cub_scarf.png")
    }

    /** Аксессуар объявлен, а файла нет — та же сова без него, не заглушка. */
    @Test
    fun без_файла_аксессуара_показана_сова_без_него() {
        show(PetAppearance(bodyId = "owl", colorId = "cream", accessoryId = "nothing"))

        awaitTag("content/v1/pets/owl_cream_cub.png")
        compose.onNodeWithText(context.getString(R.string.create_pet_image_missing)).assertDoesNotExist()
    }

    /** Нет даже базовой совы — заглушка с подписью, чтобы было видно, чего не хватает. */
    @Test
    fun без_базовой_картинки_заглушка() {
        show(PetAppearance(bodyId = "owl", colorId = "nocolor", accessoryId = null))

        val missing = context.getString(R.string.create_pet_image_missing)
        compose.waitUntil(TIMEOUT_MS) {
            compose.onAllNodesWithText(missing).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(missing).assertIsDisplayed()
    }

    private fun show(appearance: PetAppearance) {
        compose.setContent {
            FinnypetTheme {
                PetImage(appearance = appearance, stage = GrowthStage.CUB)
            }
        }
    }

    /** Картинка читается в фоне, и ждать её надо по тегу, а не по простою композиции. */
    private fun awaitTag(path: String) {
        compose.waitUntil(TIMEOUT_MS) {
            compose.onAllNodesWithTag(path).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag(path).assertIsDisplayed()
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }
}
