package ru.finnypet.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
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
import ru.finnypet.app.domain.model.ItemId
import ru.finnypet.app.domain.model.PetEffect
import ru.finnypet.app.domain.model.PetStatKind
import ru.finnypet.app.domain.model.RecoveryOption
import ru.finnypet.app.domain.model.PeriodStatus
import ru.finnypet.app.domain.model.PetAppearance
import ru.finnypet.app.domain.model.SpendCategory
import ru.finnypet.app.domain.model.BudgetPlan
import ru.finnypet.app.domain.model.Change
import ru.finnypet.app.domain.model.PetState
import ru.finnypet.app.domain.model.Stat
import ru.finnypet.app.ui.screens.budget.BudgetContent
import ru.finnypet.app.ui.screens.budget.BudgetLine
import ru.finnypet.app.ui.screens.budget.BudgetState
import ru.finnypet.app.ui.screens.createpet.AppearanceOption
import ru.finnypet.app.ui.screens.createpet.CreatePetContent
import ru.finnypet.app.ui.screens.createpet.CreatePetState
import ru.finnypet.app.ui.screens.main.MainContent
import ru.finnypet.app.ui.screens.main.MainState
import ru.finnypet.app.ui.screens.main.SavingsView
import ru.finnypet.app.ui.screens.onboarding.OnboardingScreen
import ru.finnypet.app.ui.screens.shop.PurchaseOutcome
import ru.finnypet.app.ui.screens.shop.RecoveryChoice
import ru.finnypet.app.ui.screens.shop.ShopContent
import ru.finnypet.app.ui.screens.shop.ShopItemView
import ru.finnypet.app.ui.screens.shop.ShopState
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

    @Test
    fun `с_главного_экрана_можно_перейти_к_плану`() {
        var opened = false
        showMain(readyState(), onPlan = { opened = true })

        compose.onNodeWithText(text(R.string.budget_action_plan)).performClick()

        assertTrue(opened)
    }

    @Test
    fun `с_главного_экрана_можно_перейти_в_магазин`() {
        var opened = false
        showMain(readyState(), onShop = { opened = true })

        compose.onNodeWithText(text(R.string.shop_action)).performClick()

        assertTrue(opened)
    }

    // --- Магазин (ТЗ 2.5.6) ---

    @Test
    fun `магазин_показывает_товары_с_ценой_направлением_и_влиянием`() {
        showShop(ready())

        scrollToDescription("80 монет")
        scrollToText("Вкусная каша")
        scrollToText(text(R.string.category_mandatory))
        scrollToText(text(R.string.shop_effect, text(R.string.stat_satiety), "+20"))
        scrollToDescription("12 монет")
        scrollToText("Яркий мячик")
        scrollToText(text(R.string.category_optional))
    }

    /** Покупка — решение, и до списания ребёнок видит цену и что изменится. */
    @Test
    fun `покупка_подтверждается_перед_списанием`() {
        var bought: ItemId? = null
        showShop(ready(), onBuy = { bought = it })

        compose.onNodeWithText("Вкусная каша").performClick()

        compose.onNodeWithText(text(R.string.shop_pet_change)).assertIsDisplayed()
        assertEquals(null, bought)

        compose.onNodeWithText(text(R.string.shop_buy)).performClick()

        assertEquals(ItemId("food"), bought)
    }

    @Test
    fun `от_покупки_можно_отказаться`() {
        var bought: ItemId? = null
        showShop(ready(), onBuy = { bought = it })
        compose.onNodeWithText("Вкусная каша").performClick()

        compose.onNodeWithText(text(R.string.shop_not_now)).performClick()

        compose.onNodeWithText(text(R.string.shop_buy)).assertDoesNotExist()
        assertEquals(null, bought)
    }

    /** ТЗ 2.5.5 и 3.4: пока день планируется, покупать нельзя, но дорога в план есть. */
    @Test
    fun `пока_день_планируется_подсказка_ведёт_в_план`() {
        var planned = false
        showShop(ready(canBuy = false), onPlan = { planned = true })

        compose.onNodeWithText(text(R.string.shop_planning_hint)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.budget_action_plan)).performClick()

        assertTrue(planned)
    }

    /** Нажатие на товар в это время — не немой тупик, а та же дорога в план. */
    @Test
    fun `пока_день_планируется_товар_зовёт_в_план_а_не_продаётся`() {
        var planned = false
        var bought: ItemId? = null
        showShop(ready(canBuy = false), onPlan = { planned = true }, onBuy = { bought = it })

        compose.onNodeWithText("Вкусная каша").performClick()

        compose.onNodeWithText(text(R.string.shop_buy)).assertDoesNotExist()
        // Кнопка в план теперь и в подсказке, и в окне — жмём ту, что в окне.
        compose.onAllNodesWithText(text(R.string.budget_action_plan))[1].performClick()

        assertTrue(planned)
        assertEquals(null, bought)
    }

    @Test
    fun `покупка_объясняется_словами_из_контента`() {
        var dismissed = false
        val done = PurchaseOutcome.Done(
            title = "Вкусная каша",
            text = "Осталось 68 монет.",
            effects = food.effects,
            changes = listOf(Change.PetStat(PetStatKind.SATIETY, from = Stat(75), to = Stat(95))),
        )
        // В списке только мячик: иначе «Сытость +20» нашлось бы и в строке каши.
        showShop(ready(outcome = done, items = listOf(toy)), onDismiss = { dismissed = true })

        compose.onNodeWithText("Осталось 68 монет.").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.shop_effect, text(R.string.stat_satiety), "+20")).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.action_ok)).performClick()

        assertTrue(dismissed)
    }

    /** ТЗ 2.5.9: показатель упёрся в границу — говорим об этом, а не «+20». */
    @Test
    fun `покупка_без_изменений_говорит_об_этом_честно`() {
        val done = PurchaseOutcome.Done(
            title = "Вкусная каша",
            text = "Осталось 68 монет.",
            effects = food.effects,
            changes = emptyList(),
        )
        showShop(ready(outcome = done, items = listOf(toy)))

        compose.onNodeWithText(text(R.string.shop_no_change)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.shop_effect, text(R.string.stat_satiety), "+20"))
            .assertDoesNotExist()
    }

    /**
     * ТЗ 2.5.6: отказ объясняет нехватку и предлагает выход. Кнопками
     * становятся только варианты, у которых есть куда вести; остальные —
     * подсказкой, чтобы не было кнопки в пустоту (ТЗ 3.4).
     */
    @Test
    fun `отказ_объясняет_и_предлагает_выход`() {
        var dismissed = false
        val rejected = PurchaseOutcome.Rejected(
            title = "Замок",
            text = "Не хватает 20 монет.",
            options = listOf(
                RecoveryChoice(RecoveryOption.DO_TASK, "Выполнить задание"),
                RecoveryChoice(RecoveryOption.POSTPONE_PURCHASE, "Купить попозже"),
                RecoveryChoice(RecoveryOption.CHOOSE_CHEAPER, "Выбрать подешевле"),
            ),
            recommended = RecoveryOption.DO_TASK,
        )
        showShop(ready(outcome = rejected), onDismiss = { dismissed = true })

        compose.onNodeWithText("Не хватает 20 монет.").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.shop_option_hint, "Выполнить задание")).assertIsDisplayed()
        compose.onNodeWithText("Выбрать подешевле").assertIsDisplayed()
        compose.onNodeWithText("Купить попозже").performClick()

        assertTrue(dismissed)
    }

    /** Идентификаторы товаров пишет напарник, и «balance» — законное имя. */
    @Test
    fun `товар_с_id_как_у_шапки_списка_не_роняет_экран`() {
        showShop(ready(items = listOf(food.copy(id = ItemId("balance")), toy.copy(id = ItemId("planning")))))

        scrollToText("Вкусная каша")
        scrollToText("Яркий мячик")
    }

    /** ТЗ 3.4: сбой не оставляет экран без выхода. */
    @Test
    fun `сбой_магазина_предлагает_повтор`() {
        var retried = false
        showShop(ShopState.Failed, onRetry = { retried = true })

        compose.onNodeWithText(text(R.string.shop_failed)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.action_retry)).performClick()

        assertTrue(retried)
    }

    private val food = ShopItemView(
        id = ItemId("food"),
        title = "Вкусная каша",
        price = Coins(12),
        category = SpendCategory.MANDATORY,
        effects = listOf(PetEffect(PetStatKind.SATIETY, 20)),
    )

    private val toy = ShopItemView(
        id = ItemId("toy"),
        title = "Яркий мячик",
        price = Coins(18),
        category = SpendCategory.OPTIONAL,
        effects = listOf(PetEffect(PetStatKind.MOOD, 15)),
    )

    private fun ready(
        canBuy: Boolean = true,
        outcome: PurchaseOutcome? = null,
        items: List<ShopItemView> = listOf(food, toy),
    ) = ShopState.Ready(
        items = items,
        balance = Coins(80),
        canBuy = canBuy,
        outcome = outcome,
    )

    private fun showShop(
        state: ShopState,
        onPlan: () -> Unit = {},
        onBuy: (ItemId) -> Unit = {},
        onDismiss: () -> Unit = {},
        onRetry: () -> Unit = {},
    ) {
        compose.setContent {
            FinnypetTheme {
                ShopContent(
                    state = state,
                    onBack = {},
                    onPlan = onPlan,
                    onBuy = onBuy,
                    onDismiss = onDismiss,
                    onRetry = onRetry,
                )
            }
        }
    }

    // --- План бюджета (ТЗ 2.5.5) ---

    @Test
    fun `пустой_план_подтвердить_нельзя`() {
        showBudget(planning())

        compose.onNodeWithText(text(R.string.budget_confirm)).assertIsNotEnabled()
    }

    /** ТЗ 2.5.5: приложение не даёт распределить больше доступного. */
    @Test
    fun `когда_всё_распределено_плюс_недоступен`() {
        showBudget(planning(plan = BudgetPlan(Coins(40), Coins(20), Coins(20))))

        scrollToText(text(R.string.budget_distributed))
        compose.onNodeWithContentDescription(
            text(R.string.budget_add, text(R.string.category_mandatory)),
        ).assertIsNotEnabled()
        compose.onNodeWithText(text(R.string.budget_confirm)).assertIsEnabled()
    }

    @Test
    fun `перебор_виден_и_блокирует_подтверждение`() {
        showBudget(
            BudgetState.Planning(
                available = Coins(80),
                plan = BudgetPlan(Coins(60), Coins(30), Coins(0)),
                remainder = Coins.ZERO,
                overBy = Coins(10),
                step = 5,
            )
        )

        scrollToText(text(R.string.budget_over))
        compose.onNodeWithText(text(R.string.budget_confirm)).assertIsNotEnabled()
    }

    @Test
    fun `монеты_можно_добавить_и_убрать`() {
        var added: SpendCategory? = null
        var removed: SpendCategory? = null
        showBudget(
            state = planning(plan = BudgetPlan(Coins(10), Coins.ZERO, Coins.ZERO)),
            onAdd = { added = it },
            onRemove = { removed = it },
        )

        compose.onNodeWithContentDescription(
            text(R.string.budget_add, text(R.string.category_optional)),
        ).performClick()
        compose.onNodeWithContentDescription(
            text(R.string.budget_remove, text(R.string.category_mandatory)),
        ).performClick()

        assertEquals(SpendCategory.OPTIONAL, added)
        assertEquals(SpendCategory.MANDATORY, removed)
    }

    /** Последний абзац ТЗ 2.5.5: после подтверждения видно план рядом с фактом. */
    @Test
    fun `подтверждённый_план_показывает_план_и_факт`() {
        showBudget(
            BudgetState.Started(
                lines = listOf(
                    BudgetLine(SpendCategory.MANDATORY, Coins(40), Coins(35), followed = false),
                    BudgetLine(SpendCategory.OPTIONAL, Coins(20), Coins(20), followed = true),
                    BudgetLine(SpendCategory.SAVINGS, Coins(20), Coins(20), followed = true),
                ),
                planTotal = Coins(80),
                factTotal = Coins(75),
            )
        )

        scrollToText(text(R.string.budget_started))
        scrollToDescription(
            text(R.string.category_mandatory) + ": по плану 40, потрачено 35",
        )
        scrollToDescription("75 монет")
    }

    private fun showBudget(
        state: BudgetState,
        onAdd: (SpendCategory) -> Unit = {},
        onRemove: (SpendCategory) -> Unit = {},
    ) {
        compose.setContent {
            FinnypetTheme {
                BudgetContent(state = state, onBack = {}, onAdd = onAdd, onRemove = onRemove)
            }
        }
    }

    private fun planning(plan: BudgetPlan = BudgetPlan.EMPTY) = BudgetState.Planning(
        available = Coins(80),
        plan = plan,
        remainder = Coins(80) - plan.total,
        overBy = Coins.ZERO,
        step = 5,
    )

    private fun showMain(
        state: MainState,
        onRetry: () -> Unit = {},
        onPlan: () -> Unit = {},
        onShop: () -> Unit = {},
    ) {
        compose.setContent {
            FinnypetTheme {
                MainContent(state = state, onRetry = onRetry, onPlan = onPlan, onShop = onShop)
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
