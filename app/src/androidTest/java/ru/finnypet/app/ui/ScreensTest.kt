package ru.finnypet.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.SemanticsMatcher
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
import ru.finnypet.app.domain.model.GoalId
import ru.finnypet.app.domain.model.GrowthStage
import ru.finnypet.app.domain.model.ItemId
import ru.finnypet.app.domain.model.TaskId
import ru.finnypet.app.domain.model.TaskTopic
import ru.finnypet.app.domain.model.PetEffect
import ru.finnypet.app.domain.model.PetStatKind
import ru.finnypet.app.domain.model.RecoveryOption
import ru.finnypet.app.domain.model.PeriodStatus
import ru.finnypet.app.domain.model.PetAppearance
import ru.finnypet.app.domain.model.SpendCategory
import ru.finnypet.app.domain.economy.WithdrawPreview
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
import ru.finnypet.app.ui.screens.main.TaskOfDay
import ru.finnypet.app.ui.screens.onboarding.OnboardingScreen
import ru.finnypet.app.ui.screens.savings.GoalView
import ru.finnypet.app.ui.screens.savings.SavingsContent
import ru.finnypet.app.ui.screens.savings.SavingsDraft
import ru.finnypet.app.ui.screens.savings.SavingsOutcomeView
import ru.finnypet.app.ui.screens.savings.SavingsState
import ru.finnypet.app.ui.screens.shop.PurchaseOutcome
import ru.finnypet.app.ui.screens.shop.RecoveryChoice
import ru.finnypet.app.ui.screens.shop.ShopContent
import ru.finnypet.app.ui.screens.shop.ShopItemView
import ru.finnypet.app.ui.screens.shop.ShopState
import ru.finnypet.app.ui.screens.tasks.OptionView
import ru.finnypet.app.ui.screens.tasks.PickItemView
import ru.finnypet.app.ui.screens.tasks.StepView
import ru.finnypet.app.ui.screens.tasks.TaskContent
import ru.finnypet.app.ui.screens.tasks.TaskGroup
import ru.finnypet.app.ui.screens.tasks.TaskOutcomeView
import ru.finnypet.app.ui.screens.tasks.TaskRow
import ru.finnypet.app.ui.screens.tasks.TaskStage
import ru.finnypet.app.ui.screens.tasks.TaskState
import ru.finnypet.app.ui.screens.tasks.TasksContent
import ru.finnypet.app.ui.screens.tasks.TasksState
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

    /** ТЗ 2.5.3: активное задание видно на главном, карточка — кнопка в него. */
    @Test
    fun `задание_дня_на_главном_ведёт_в_задание`() {
        var opened: TaskId? = null
        val task = TaskOfDay(
            id = TaskId("story"),
            topic = TaskTopic.SAVING,
            intro = "Сова нашла монеты. Что с ними делать?",
            rewardAvailable = true,
            allDone = false,
        )
        showMain(readyState(task = task), onTask = { opened = it })

        scrollToText(text(R.string.main_task))
        scrollToText("Сова нашла монеты. Что с ними делать?")
        scrollToText(text(R.string.main_task_reward))
        compose.onNodeWithText(text(R.string.main_task_open)).performClick()

        assertEquals(TaskId("story"), opened)
    }

    @Test
    fun `когда_монеты_за_сегодня_получены_главный_об_этом_говорит`() {
        val task = TaskOfDay(
            id = TaskId("story"),
            topic = TaskTopic.SAVING,
            intro = "Вступление",
            rewardAvailable = false,
            allDone = true,
        )
        showMain(readyState(task = task))

        scrollToText(text(R.string.main_task_all_done))
    }

    /** Карточка копилки — кнопка, и подписана словами, а не только цветом. */
    @Test
    fun `карточка_копилки_ведёт_в_копилку`() {
        var opened = false
        showMain(readyState(), onSavings = { opened = true })

        scrollToText(text(R.string.savings_open))
        compose.onNodeWithText(text(R.string.savings_open)).performClick()

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

        compose.onNodeWithText(text(R.string.action_not_now)).performClick()

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
     * становятся только варианты, у которых есть куда вести; вариант без
     * экрана — подсказкой, чтобы не было кнопки в пустоту (ТЗ 3.4).
     */
    @Test
    fun `отказ_объясняет_и_предлагает_выход`() {
        var dismissed = false
        val rejected = PurchaseOutcome.Rejected(
            title = "Замок",
            text = "Не хватает 20 монет.",
            options = listOf(
                RecoveryChoice(RecoveryOption.ADJUST_NEXT_PLAN, "Пересмотреть план"),
                RecoveryChoice(RecoveryOption.POSTPONE_PURCHASE, "Купить попозже"),
                RecoveryChoice(RecoveryOption.CHOOSE_CHEAPER, "Выбрать подешевле"),
            ),
            recommended = RecoveryOption.ADJUST_NEXT_PLAN,
        )
        showShop(ready(outcome = rejected), onDismiss = { dismissed = true })

        compose.onNodeWithText("Не хватает 20 монет.").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.shop_option_hint, "Пересмотреть план")).assertIsDisplayed()
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

    /** Копилка теперь есть — «взять из копилки» ведёт в неё, а не остаётся подсказкой. */
    @Test
    fun `отказ_ведёт_в_копилку_когда_она_поможет`() {
        var dismissed = false
        var savings = false
        val rejected = PurchaseOutcome.Rejected(
            title = "Ветеринар",
            text = "Не хватает 20 монет.",
            options = listOf(
                RecoveryChoice(RecoveryOption.DO_TASK, "Выполнить задание"),
                RecoveryChoice(RecoveryOption.WITHDRAW_FROM_SAVINGS, "Взять из копилки"),
                RecoveryChoice(RecoveryOption.CHOOSE_CHEAPER, "Выбрать подешевле"),
            ),
            recommended = RecoveryOption.DO_TASK,
        )
        showShop(ready(outcome = rejected), onDismiss = { dismissed = true }, onSavings = { savings = true })

        compose.onNodeWithText("Взять из копилки").performClick()

        assertTrue(dismissed)
        assertTrue(savings)
    }

    /** Экран заданий есть — «выполнить задание» ведёт в него и стоит главной кнопкой. */
    @Test
    fun `отказ_ведёт_в_задания_главной_кнопкой`() {
        var tasks = false
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
        showShop(ready(outcome = rejected), onTasks = { tasks = true })

        compose.onNodeWithText(text(R.string.shop_option_hint, "Выполнить задание")).assertDoesNotExist()
        compose.onNodeWithText("Выполнить задание").performClick()

        assertTrue(tasks)
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
        onSavings: () -> Unit = {},
        onTasks: () -> Unit = {},
    ) {
        compose.setContent {
            FinnypetTheme {
                ShopContent(
                    state = state,
                    onBack = {},
                    onPlan = onPlan,
                    onSavings = onSavings,
                    onTasks = onTasks,
                    onBuy = onBuy,
                    onDismiss = onDismiss,
                    onRetry = onRetry,
                )
            }
        }
    }

    // --- Задания (ТЗ 2.5.8) ---

    @Test
    fun `список_заданий_по_темам_с_пометкой_пройдено`() {
        showTasks(tasksReady())

        scrollToText(text(R.string.tasks_reward_available))
        scrollToText(text(R.string.topic_planning))
        scrollToText("Разложи сорок монет.")
        scrollToText(text(R.string.tasks_completed))
        scrollToText(text(R.string.topic_saving))
        scrollToText("Сова нашла монеты.")
    }

    @Test
    fun `задание_открывается_нажатием`() {
        var opened: TaskId? = null
        showTasks(tasksReady(), onOpen = { opened = it })

        scrollToText("Сова нашла монеты.")
        compose.onNodeWithText("Сова нашла монеты.").performClick()

        assertEquals(TaskId("story"), opened)
    }

    /** ТЗ 2.5.5 и 3.4: пока день планируется, задание не открыть, но дорога в план есть. */
    @Test
    fun `пока_день_планируется_задание_зовёт_в_план`() {
        var planned = false
        var opened: TaskId? = null
        showTasks(tasksReady(canStart = false), onPlan = { planned = true }, onOpen = { opened = it })

        scrollToText("Сова нашла монеты.")
        compose.onNodeWithText("Сова нашла монеты.").performClick()
        compose.onAllNodesWithText(text(R.string.budget_action_plan))[1].performClick()

        assertTrue(planned)
        assertEquals(null, opened)
    }

    @Test
    fun `когда_монеты_за_сегодня_получены_список_предупреждает`() {
        showTasks(tasksReady(rewardAvailable = false))

        scrollToText(text(R.string.tasks_reward_taken))
    }

    /** Правило дня — до старта, а не после: ребёнок знает, за что монеты. */
    @Test
    fun `вступление_показывает_сову_тему_и_награду`() {
        var started = false
        showTask(taskReady(stage = TaskStage.Intro), onStart = { started = true })

        scrollToText(text(R.string.topic_saving))
        scrollToText("Сова нашла монеты. Что с ними делать?")
        scrollToDescription("15 монет")
        compose.onNodeWithText(text(R.string.task_start)).performClick()

        assertTrue(started)
    }

    @Test
    fun `без_права_на_монеты_вступление_предупреждает_до_старта`() {
        showTask(taskReady(stage = TaskStage.Intro, rewardAvailable = false))

        scrollToText(text(R.string.task_training_note))
        compose.onNodeWithText(text(R.string.task_start_training)).assertIsDisplayed()
    }

    @Test
    fun `история_выбранный_вариант_подписан_словом_и_пускает_дальше`() {
        var chosen: String? = null
        var next = false
        val step = StepView.Choice(
            prompt = "Отложить или потратить?",
            options = listOf(OptionView("save", "Отложить"), OptionView("spend", "Потратить")),
            chosen = "save",
        )
        showTask(taskReady(stage = TaskStage.Step(index = 0, total = 1, step = step)), onChoose = { chosen = it }, onNext = { next = true })

        scrollToText(text(R.string.task_step, 1, 1))
        scrollToText(text(R.string.task_selected))
        compose.onNodeWithText("Потратить").performClick()
        assertEquals("spend", chosen)
        compose.onNodeWithText(text(R.string.task_answer)).performClick()

        assertTrue(next)
    }

    @Test
    fun `история_без_выбора_дальше_не_пускает`() {
        val step = StepView.Choice(
            prompt = "Отложить или потратить?",
            options = listOf(OptionView("save", "Отложить"), OptionView("spend", "Потратить")),
            chosen = null,
        )
        showTask(taskReady(stage = TaskStage.Step(index = 0, total = 1, step = step)))

        compose.onNodeWithText(text(R.string.task_answer)).assertIsNotEnabled()
    }

    @Test
    fun `три_банки_показывают_бюджет_остаток_и_кнопки`() {
        var added: SpendCategory? = null
        val step = StepView.Distribute(
            prompt = "Сколько куда?",
            budget = Coins(40),
            plan = BudgetPlan(Coins(15), Coins(10), Coins(0)),
        )
        showTask(taskReady(stage = TaskStage.Step(index = 0, total = 2, step = step)), onAdd = { added = it })

        scrollToText(text(R.string.task_step, 1, 2))
        scrollToDescription(text(R.string.budget_amount, text(R.string.category_mandatory), 15))
        scrollToDescription("15 монет")
        compose.onNodeWithContentDescription(text(R.string.budget_add, text(R.string.category_savings))).performClick()
        assertEquals(SpendCategory.SAVINGS, added)
        compose.onNodeWithText(text(R.string.task_next)).assertIsEnabled()
    }

    @Test
    fun `полка_считает_корзину_и_не_даёт_взять_лишнее`() {
        var toggled: ItemId? = null
        val step = StepView.Pick(
            prompt = "Что возьмём?",
            budget = Coins(30),
            items = listOf(
                PickItemView(ItemId("food"), "Каша", Coins(12), SpendCategory.MANDATORY),
                PickItemView(ItemId("toy"), "Мячик", Coins(18), SpendCategory.OPTIONAL),
                PickItemView(ItemId("bike"), "Велосипед", Coins(25), SpendCategory.OPTIONAL),
            ),
            picked = setOf(ItemId("toy")),
        )
        showTask(taskReady(stage = TaskStage.Step(index = 0, total = 1, step = step)), onToggle = { toggled = it })

        scrollToDescription(text(R.string.task_basket_progress, 18, 30))
        scrollToText(text(R.string.task_picked))
        scrollToText(text(R.string.task_pick_full))
        compose.onNodeWithText("Каша").performClick()

        assertEquals(ItemId("food"), toggled)
    }

    /** ТЗ 2.5.8: объяснение независимо от результата; награда — отдельной строкой. */
    @Test
    fun `итог_показывает_объяснение_награду_и_питомца`() {
        var finished = false
        val outcome = TaskOutcomeView(
            text = "Молодец, отложил!",
            reward = Coins(15),
            rewardable = true,
            changes = listOf(Change.PetStat(PetStatKind.MOOD, from = Stat(75), to = Stat(80))),
        )
        showTask(taskReady(stage = TaskStage.Done(outcome)), onBack = { finished = true })

        scrollToText("Молодец, отложил!")
        scrollToText(text(R.string.task_reward))
        scrollToDescription("15 монет")
        scrollToText(text(R.string.shop_effect, text(R.string.stat_mood), "+5"))
        compose.onNodeWithText(text(R.string.task_finish)).performClick()

        assertTrue(finished)
    }

    @Test
    fun `итог_без_монет_говорит_об_этом_честно`() {
        val outcome = TaskOutcomeView(text = "Потратил всё.", reward = Coins.ZERO, rewardable = false, changes = emptyList())
        showTask(taskReady(stage = TaskStage.Done(outcome)))

        scrollToText(text(R.string.task_reward_none))
        compose.onNodeWithText(text(R.string.task_reward)).assertDoesNotExist()
    }

    @Test
    fun `несуществующее_задание_не_тупик`() {
        var back = false
        showTask(TaskState.Missing, onBack = { back = true })

        compose.onNodeWithText(text(R.string.task_missing)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.task_finish)).performClick()

        assertTrue(back)
    }

    private fun tasksReady(
        rewardAvailable: Boolean = true,
        canStart: Boolean = true,
    ) = TasksState.Ready(
        groups = listOf(
            TaskGroup(
                topic = TaskTopic.PLANNING,
                tasks = listOf(TaskRow(TaskId("jars"), TaskTopic.PLANNING, "Разложи сорок монет.", completed = true)),
            ),
            TaskGroup(
                topic = TaskTopic.SAVING,
                tasks = listOf(TaskRow(TaskId("story"), TaskTopic.SAVING, "Сова нашла монеты.", completed = false)),
            ),
        ),
        rewardAvailable = rewardAvailable,
        canStart = canStart,
    )

    private fun taskReady(
        stage: TaskStage,
        rewardAvailable: Boolean = true,
        canStart: Boolean = true,
    ) = TaskState.Ready(
        id = TaskId("story"),
        topic = TaskTopic.SAVING,
        intro = "Сова нашла монеты. Что с ними делать?",
        appearance = PetAppearance(bodyId = "owl", colorId = "cream", accessoryId = null),
        maxReward = Coins(15),
        rewardAvailable = rewardAvailable,
        canStart = canStart,
        stage = stage,
    )

    private fun showTasks(
        state: TasksState,
        onPlan: () -> Unit = {},
        onOpen: (TaskId) -> Unit = {},
    ) {
        compose.setContent {
            FinnypetTheme {
                TasksContent(state = state, onBack = {}, onPlan = onPlan, onOpen = onOpen)
            }
        }
    }

    private fun showTask(
        state: TaskState,
        onBack: () -> Unit = {},
        onStart: () -> Unit = {},
        onChoose: (String) -> Unit = {},
        onAdd: (SpendCategory) -> Unit = {},
        onToggle: (ItemId) -> Unit = {},
        onNext: () -> Unit = {},
    ) {
        compose.setContent {
            FinnypetTheme {
                TaskContent(
                    state = state,
                    onBack = onBack,
                    onStart = onStart,
                    onChoose = onChoose,
                    onAdd = onAdd,
                    onToggle = onToggle,
                    onNext = onNext,
                )
            }
        }
    }

    // --- Копилка и цель (ТЗ 2.5.7) ---

    /** ТЗ 2.5.7: цель, её стоимость, накоплено, остаток и срок — всё на одном экране. */
    @Test
    fun `копилка_показывает_цель_накопленное_остаток_и_срок`() {
        showSavings(savingsReady(periodsToGoal = 2))

        scrollToDescription("80 монет")
        // Название и цена цели стоят и в карточке, и в списке — ищем любой.
        scrollToAny(hasText("Самокат"))
        scrollToAny(hasContentDescription("30 монет", substring = true))
        scrollToAny(hasContentDescription("10 монет", substring = true))
        scrollToDescription("20 монет")
        scrollToText(text(R.string.savings_eta, text(R.string.days_few, 2)))
        scrollToText(text(R.string.savings_goal_active))
        scrollToText("Книжка")
    }

    @Test
    fun `без_пополнений_срок_не_обещается`() {
        showSavings(savingsReady(periodsToGoal = null))

        scrollToText(text(R.string.savings_eta_unknown))
    }

    @Test
    fun `без_цели_копилка_зовёт_выбрать_и_отложить_нельзя`() {
        showSavings(savingsReady(active = false))

        scrollToText(text(R.string.savings_goal_none))
        compose.onNodeWithText(text(R.string.savings_deposit)).assertIsNotEnabled()
        compose.onNodeWithText(text(R.string.savings_withdraw)).assertIsNotEnabled()
    }

    @Test
    fun `цель_выбирается_нажатием`() {
        var chosen: GoalId? = null
        showSavings(savingsReady(), onChoose = { chosen = it })

        scrollToText("Книжка")
        compose.onNodeWithText("Книжка").performClick()

        assertEquals(GoalId("book"), chosen)
    }

    /** ТЗ 2.5.5 и 3.4: во время планирования копилка закрыта, но дорога в план есть. */
    @Test
    fun `пока_день_планируется_копилка_закрыта_и_зовёт_в_план`() {
        var planned = false
        showSavings(savingsReady(canOperate = false), onPlan = { planned = true })

        scrollToText(text(R.string.savings_planning_hint))
        compose.onNodeWithText(text(R.string.savings_deposit)).assertIsNotEnabled()
        compose.onNodeWithText(text(R.string.budget_action_plan)).performClick()

        assertTrue(planned)
    }

    @Test
    fun `пополнение_набирается_кнопками_и_подтверждается`() {
        var added = false
        var confirmed = false
        val draft = SavingsDraft.Deposit(amount = Coins(5), max = Coins(80))
        showSavings(savingsReady(draft = draft), onAdd = { added = true }, onConfirm = { confirmed = true })

        compose.onNodeWithText(text(R.string.savings_deposit_title)).assertIsDisplayed()
        compose.onNodeWithContentDescription(text(R.string.savings_amount_less)).assertIsNotEnabled()
        compose.onNodeWithContentDescription(text(R.string.savings_amount_more)).performClick()
        assertTrue(added)

        // «Отложить» есть и внизу экрана, и в окне — жмём ту, что в окне.
        compose.onNode(hasText(text(R.string.savings_deposit)) and hasAnyAncestor(isDialog())).performClick()

        assertTrue(confirmed)
    }

    /**
     * ТЗ 2.5.7: до подтверждения снятия видно, сколько останется и как
     * отодвинется цель.
     */
    @Test
    fun `снятие_показывает_остаток_и_срок_до_подтверждения`() {
        var confirmed = false
        val draft = SavingsDraft.Withdraw(
            amount = Coins(5),
            max = Coins(10),
            preview = WithdrawPreview(
                savingsBefore = Coins(10),
                savingsAfter = Coins(5),
                periodsBefore = 2,
                periodsAfter = 3,
            ),
        )
        showSavings(savingsReady(draft = draft), onConfirm = { confirmed = true })

        compose.onNodeWithText(text(R.string.savings_withdraw_title)).assertIsDisplayed()
        // Подпись и сумма склеены в один узел: текст у подписи, озвучка у суммы.
        compose.onNode(hasText(text(R.string.savings_withdraw_left)) and hasContentDescription("5 монет"))
            .assertIsDisplayed()
        compose.onNodeWithText(
            text(R.string.savings_withdraw_eta, text(R.string.days_few, 2), text(R.string.days_few, 3))
        ).assertIsDisplayed()
        assertEquals(false, confirmed)

        compose.onNodeWithText(text(R.string.savings_withdraw_confirm)).performClick()

        assertTrue(confirmed)
    }

    /** Собранная цель — не «через 0 дней». */
    @Test
    fun `снятие_из_собранной_цели_не_обещает_ноль_дней`() {
        val draft = SavingsDraft.Withdraw(
            amount = Coins(5),
            max = Coins(30),
            preview = WithdrawPreview(
                savingsBefore = Coins(30),
                savingsAfter = Coins(25),
                periodsBefore = 0,
                periodsAfter = 1,
            ),
        )
        showSavings(savingsReady(draft = draft))

        compose.onNodeWithText(text(R.string.savings_withdraw_eta_reached, text(R.string.days_one, 1)))
            .assertIsDisplayed()
    }

    @Test
    fun `от_снятия_можно_отказаться`() {
        var cancelled = false
        val draft = SavingsDraft.Deposit(amount = Coins(5), max = Coins(80))
        showSavings(savingsReady(draft = draft), onCancel = { cancelled = true })

        compose.onNodeWithText(text(R.string.action_not_now)).performClick()

        assertTrue(cancelled)
    }

    @Test
    fun `итог_объясняется_словами_из_контента`() {
        var dismissed = false
        val outcome = SavingsOutcomeView(text = "Отложили 5 монет в копилку!", goalReached = false)
        showSavings(savingsReady(outcome = outcome), onDismiss = { dismissed = true })

        compose.onNodeWithText("Отложили 5 монет в копилку!").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.action_ok)).performClick()

        assertTrue(dismissed)
    }

    @Test
    fun `сбой_копилки_предлагает_повтор`() {
        var retried = false
        showSavings(SavingsState.Failed, onRetry = { retried = true })

        compose.onNodeWithText(text(R.string.savings_failed)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.action_retry)).performClick()

        assertTrue(retried)
    }

    private val scooter = GoalView(
        id = GoalId("scooter"),
        title = "Самокат",
        price = Coins(30),
        saved = Coins(10),
        isActive = true,
    )

    private val book = GoalView(
        id = GoalId("book"),
        title = "Книжка",
        price = Coins(15),
        saved = Coins.ZERO,
        isActive = false,
    )

    private fun savingsReady(
        active: Boolean = true,
        periodsToGoal: Int? = 2,
        canOperate: Boolean = true,
        draft: SavingsDraft? = null,
        outcome: SavingsOutcomeView? = null,
    ): SavingsState.Ready {
        val goals = if (active) listOf(scooter, book) else listOf(scooter.copy(isActive = false), book)
        return SavingsState.Ready(
            goals = goals,
            periodsToGoal = if (active) periodsToGoal else null,
            balance = Coins(80),
            canOperate = canOperate,
            draft = draft,
            outcome = outcome,
        )
    }

    private fun showSavings(
        state: SavingsState,
        onPlan: () -> Unit = {},
        onChoose: (GoalId) -> Unit = {},
        onAdd: () -> Unit = {},
        onConfirm: () -> Unit = {},
        onCancel: () -> Unit = {},
        onDismiss: () -> Unit = {},
        onRetry: () -> Unit = {},
    ) {
        compose.setContent {
            FinnypetTheme {
                SavingsContent(
                    state = state,
                    onBack = {},
                    onPlan = onPlan,
                    onChoose = onChoose,
                    onAdd = onAdd,
                    onConfirm = onConfirm,
                    onCancel = onCancel,
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
        onSavings: () -> Unit = {},
        onTask: (TaskId) -> Unit = {},
    ) {
        compose.setContent {
            FinnypetTheme {
                MainContent(
                    state = state,
                    onRetry = onRetry,
                    onPlan = onPlan,
                    onShop = onShop,
                    onSavings = onSavings,
                    onTask = onTask,
                )
            }
        }
    }

    private fun scrollToText(label: String) {
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(label))
        compose.onNodeWithText(label).assertIsDisplayed()
    }

    /** Когда узлов с такой подписью несколько — достаточно, чтобы показался первый. */
    private fun scrollToAny(matcher: SemanticsMatcher) {
        compose.onNode(hasScrollAction()).performScrollToNode(matcher)
        compose.onAllNodes(matcher).onFirst().assertIsDisplayed()
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
        task: TaskOfDay? = null,
    ) = MainState.Ready(
        childName = "Егор",
        petName = "Пушок",
        appearance = PetAppearance(bodyId = "owl", colorId = "cream", accessoryId = null),
        stage = GrowthStage.CUB,
        stats = PetState(mood = Stat(75), satiety = Stat(80), care = Stat(60)),
        balance = Coins(80),
        savings = savings,
        task = task,
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
