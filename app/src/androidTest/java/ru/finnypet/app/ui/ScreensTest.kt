package ru.finnypet.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.finnypet.app.R
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.GrowthStage
import ru.finnypet.app.domain.model.PeriodStatus
import ru.finnypet.app.domain.model.PetAppearance
import ru.finnypet.app.domain.model.PetState
import ru.finnypet.app.domain.model.Stat
import ru.finnypet.app.ui.screens.createpet.AppearanceOption
import ru.finnypet.app.ui.screens.createpet.CreatePetContent
import ru.finnypet.app.ui.screens.createpet.CreatePetState
import ru.finnypet.app.ui.screens.main.MainContent
import ru.finnypet.app.ui.screens.main.MainState
import ru.finnypet.app.ui.screens.main.SavingsView
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

    private fun text(id: Int, vararg args: Any) = context.getString(id, *args)

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

    // --- Главный экран (ТЗ 2.5.3) ---

    /**
     * ТЗ 2.5.3 требует, чтобы питомец, баланс, накопления, цель и показатели
     * жили на одном экране, без переходов и меню. На 360 dp — минимальной
     * ширине по ТЗ 3.1 — всё это в один экран не помещается и прокручивается,
     * поэтому проверяем, что каждый блок есть и до него можно доскроллить,
     * не уходя с экрана.
     */
    @Test
    fun `главный_экран_показывает_всё_разом`() {
        showMain(readyState())

        // Приветствие стоит в шапке экрана, а не в прокручиваемой части.
        compose.onNodeWithText(text(R.string.main_hello, "Егор")).assertIsDisplayed()
        scrollToText("Пушок")
        scrollToText(text(R.string.stage_cub))
        scrollToDescription("80 монет")
        scrollToText("Самокат мечты")
        scrollToDescription("30 монет")
        scrollToDescription("90 монет")
        scrollToText(text(R.string.main_pet_state, "Пушок"))
        scrollToDescription(text(R.string.stat_mood) + ": 75 из 100")
        scrollToDescription(text(R.string.stat_satiety) + ": 80 из 100")
        scrollToDescription(text(R.string.stat_care) + ": 60 из 100")
    }

    /** Отложенные монеты не должны исчезать с экрана из-за невыбранной цели. */
    @Test
    fun `без_цели_накопления_всё_равно_видны`() {
        showMain(readyState(savings = SavingsView(saved = Coins(30))))

        scrollToText(text(R.string.main_goal_none))
        scrollToDescription("30 монет")
    }

    @Test
    fun `собранная_цель_названа_собранной`() {
        showMain(
            readyState(
                savings = SavingsView(
                    saved = Coins(120),
                    goalTitle = "Самокат мечты",
                    price = Coins(120),
                )
            )
        )

        scrollToText(text(R.string.main_goal_reached))
    }

    /** ТЗ 3.4: сбой не оставляет экран без выхода. */
    @Test
    fun `сбой_игрового_дня_предлагает_повтор`() {
        var retried = false
        showMain(MainState.Failed, onRetry = { retried = true })

        compose.onNodeWithText(text(R.string.main_failed)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.action_retry)).performClick()

        assertTrue(retried)
    }

    private fun showMain(state: MainState, onRetry: () -> Unit = {}) {
        compose.setContent {
            FinnypetTheme {
                MainContent(state = state, onRetry = onRetry)
            }
        }
    }

    private fun scrollToText(label: String) {
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(label))
        compose.onNodeWithText(label).assertIsDisplayed()
    }

    /**
     * Ищем вхождением: карточки склеивают подписи потомков в одну фразу для
     * озвучки, и точное сравнение с частью этой фразы не сошлось бы.
     */
    private fun scrollToDescription(spoken: String) {
        compose.onNode(hasScrollAction())
            .performScrollToNode(hasContentDescription(spoken, substring = true))
        compose.onNodeWithContentDescription(spoken, substring = true).assertIsDisplayed()
    }

    private fun readyState(
        savings: SavingsView = SavingsView(
            saved = Coins(30),
            goalTitle = "Самокат мечты",
            price = Coins(120),
        ),
    ) = MainState.Ready(
        childName = "Егор",
        petName = "Пушок",
        appearance = PetAppearance(bodyId = "owl", colorId = "cream", accessoryId = null),
        stage = GrowthStage.CUB,
        stats = PetState(mood = Stat(75), satiety = Stat(80), care = Stat(60)),
        balance = Coins(80),
        savings = savings,
        periodNumber = 1,
        periodStatus = PeriodStatus.PLANNING,
    )

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
