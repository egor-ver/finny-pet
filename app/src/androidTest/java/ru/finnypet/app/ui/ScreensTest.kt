package ru.finnypet.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.finnypet.app.R
import ru.finnypet.app.ui.screens.createpet.AppearanceOption
import ru.finnypet.app.ui.screens.createpet.CreatePetContent
import ru.finnypet.app.ui.screens.createpet.CreatePetState
import ru.finnypet.app.ui.screens.onboarding.OnboardingScreen
import ru.finnypet.app.ui.theme.FinnypetTheme

/**
 * Проверяет поведение первых двух экранов на состоянии, а не на живом
 * графе зависимостей: отрисовка вынесена из ViewModel именно для этого.
 *
 * Закрепляем то, что легко сломать молча: три типа решений на знакомстве
 * (ТЗ 2.5.1), запрет на создание питомца без имён и отсутствие тупика при
 * сбое сохранения (ТЗ 3.4).
 */
@RunWith(AndroidJUnit4::class)
class ScreensTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun text(id: Int) = context.getString(id)

    @Test
    fun знакомство_показывает_три_типа_решений() {
        compose.setContent {
            FinnypetTheme {
                OnboardingScreen(onDone = {})
            }
        }

        compose.onNodeWithText(text(R.string.onboarding_choice_mandatory_title))
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText(text(R.string.onboarding_choice_optional_title))
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText(text(R.string.onboarding_choice_savings_title))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun знакомство_ведёт_дальше_по_кнопке() {
        var done = false
        compose.setContent {
            FinnypetTheme {
                OnboardingScreen(onDone = { done = true })
            }
        }

        compose.onNodeWithText(text(R.string.action_start)).performClick()

        assertTrue(done)
    }

    @Test
    fun питомца_нельзя_создать_без_имён() {
        showCreatePet(state())

        compose.onNodeWithText(text(R.string.action_done)).assertIsNotEnabled()
    }

    @Test
    fun с_именами_создание_доступно() {
        var created = false
        showCreatePet(
            state = state(childName = "Егор", petName = "Финни"),
            onCreate = { created = true },
        )

        compose.onNodeWithText(text(R.string.action_done))
            .assertIsEnabled()
            .performClick()

        assertTrue(created)
    }

    /** ТЗ 3.4: сбой не должен запирать экран без выхода. */
    @Test
    fun после_сбоя_видно_объяснение_и_можно_повторить() {
        showCreatePet(state(childName = "Егор", petName = "Финни", failed = true))

        compose.onNodeWithText(text(R.string.create_pet_failed)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.action_done)).assertIsEnabled()
    }

    @Test
    fun аксессуар_можно_снять() {
        var selected: String? = "bow"
        showCreatePet(
            state = state(accessoryId = "bow"),
            onAccessory = { selected = it },
        )

        compose.onNodeWithText(text(R.string.create_pet_no_accessory))
            .performScrollTo()
            .performClick()

        assertEquals(null, selected)
    }

    private fun showCreatePet(
        state: CreatePetState,
        onCreate: () -> Unit = {},
        onAccessory: (String?) -> Unit = {},
    ) {
        compose.setContent {
            FinnypetTheme {
                CreatePetContent(
                    state = state,
                    onBack = {},
                    onBody = {},
                    onColor = {},
                    onAccessory = onAccessory,
                    onChildName = {},
                    onPetName = {},
                    onCreate = onCreate,
                )
            }
        }
    }

    private fun state(
        childName: String = "",
        petName: String = "",
        accessoryId: String? = null,
        failed: Boolean = false,
    ) = CreatePetState(
        bodies = listOf(AppearanceOption("owl", "Совёнок")),
        colors = listOf(AppearanceOption("beige", "Бежевый")),
        accessories = listOf(AppearanceOption("bow", "Бантик")),
        bodyId = "owl",
        colorId = "beige",
        accessoryId = accessoryId,
        childName = childName,
        petName = petName,
        failed = failed,
    )
}
