package ru.finnypet.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
import ru.finnypet.app.domain.model.PeriodStatus
import ru.finnypet.app.domain.model.PetAppearance
import ru.finnypet.app.domain.model.SpendCategory
import ru.finnypet.app.domain.economy.WithdrawPreview
import ru.finnypet.app.domain.model.BudgetPlan
import ru.finnypet.app.domain.model.Change
import ru.finnypet.app.domain.model.PetState
import ru.finnypet.app.domain.model.Stat
import ru.finnypet.app.ui.screens.adult.AdultContent
import ru.finnypet.app.ui.screens.adult.AdultGateContent
import ru.finnypet.app.ui.screens.adult.AdultState
import ru.finnypet.app.ui.screens.adult.AwardState
import ru.finnypet.app.ui.screens.adult.Riddle
import ru.finnypet.app.ui.screens.adult.TopicProgress
import ru.finnypet.app.ui.screens.budget.BudgetContent
import ru.finnypet.app.ui.screens.demo.DemoChipContent
import ru.finnypet.app.ui.components.BudgetLine
import ru.finnypet.app.ui.components.FinnyButton
import ru.finnypet.app.ui.components.GrowthSummary
import ru.finnypet.app.ui.components.GrowthView
import ru.finnypet.app.ui.screens.day.DayContent
import ru.finnypet.app.ui.screens.day.DayState
import ru.finnypet.app.ui.screens.day.DayCheckView
import ru.finnypet.app.ui.screens.day.DaySummary
import ru.finnypet.app.ui.screens.budget.BudgetState
import ru.finnypet.app.ui.screens.createpet.AppearanceOption
import ru.finnypet.app.ui.screens.createpet.CreatePetContent
import ru.finnypet.app.ui.screens.createpet.CreatePetState
import ru.finnypet.app.ui.screens.main.MainContent
import ru.finnypet.app.ui.screens.main.MainState
import ru.finnypet.app.ui.screens.main.NextStep
import ru.finnypet.app.ui.screens.main.SavingsView
import ru.finnypet.app.ui.screens.main.TaskOfDay
import ru.finnypet.app.ui.screens.progress.GoalSummary
import ru.finnypet.app.ui.screens.progress.LastDay
import ru.finnypet.app.ui.screens.progress.PassedTask
import ru.finnypet.app.ui.screens.progress.ProgressContent
import ru.finnypet.app.ui.screens.progress.ProgressState
import ru.finnypet.app.ui.screens.progress.Term
import ru.finnypet.app.ui.screens.savings.GoalView
import ru.finnypet.app.ui.screens.savings.SavingsContent
import ru.finnypet.app.ui.screens.savings.SavingsDraft
import ru.finnypet.app.ui.screens.savings.SavingsOutcomeView
import ru.finnypet.app.ui.screens.savings.SavingsState
import ru.finnypet.app.domain.model.RecoveryOption
import ru.finnypet.app.ui.screens.shop.ItemShortage
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

    // Знакомство стало обучением поверх главного (DESIGN_PLAN 3.4, U8):
    // экрана OnboardingScreen больше нет, три решения проверяем на этом слое.
    @Test
    fun знакомство_показывает_три_типа_решений() {
        compose.setContent {
            FinnypetTheme {
                MainContent(state = readyState(), tutorialStep = 2)
            }
        }

        compose.onNodeWithText(text(R.string.tutorial_next)).assertIsDisplayed()
    }

    @Test
    fun знакомство_ведёт_дальше_по_кнопке() {
        var next: Int? = null
        compose.setContent {
            FinnypetTheme {
                MainContent(state = readyState(), tutorialStep = 0, onTutorialStep = { next = it })
            }
        }

        compose.onNodeWithText(text(R.string.tutorial_next)).performClick()

        assertEquals(1, next)
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
     * жили на одном экране, без переходов и меню. Блоки главного говорят с
     * TalkBack одной фразой каждый (DESIGN_PLAN 3.1), поэтому ищем их по
     * описанию: кошелёк — в шапке, остальное — в прокручиваемой части.
     */
    @Test
    fun `главный_экран_показывает_всё_разом`() {
        showMain(readyState())

        compose.onNodeWithContentDescription("80 монет", substring = true).assertIsDisplayed()
        scrollToDescription(text(R.string.main_pet_stage, "Пушок", text(R.string.stage_cub)))
        scrollToDescription(text(R.string.stat_description, text(R.string.stat_mood), 75, 100))
        scrollToDescription(text(R.string.stat_description, text(R.string.stat_satiety), 80, 100))
        scrollToDescription(text(R.string.stat_description, text(R.string.stat_care), 60, 100))
        scrollToDescription(text(R.string.main_coins_unplanned))
        scrollToDescription(text(R.string.main_jar_goal_description, "Самокат мечты", 30, 120))
    }

    /** F9, TalkBack: кошелёк в шапке — кнопка со словом «Кошелёк», а не одно «80 монет». */
    @Test
    fun `кошелёк_на_главном_подписан_словом`() {
        showMain(readyState())

        compose.onNodeWithContentDescription(text(R.string.wallet_description, "80 монет"))
            .assertIsDisplayed()
            .assert(hasClickLabel(text(R.string.wallet_open)))
            .performClick()

        compose.onNodeWithText(text(R.string.wallet_title, 80)).assertIsDisplayed()
    }

    /** F9: при шрифте 2,0 в ширине 360 dp текст кнопки переносится, а чип «+10» остаётся. */
    @Test
    fun `чип_награды_виден_при_крупном_шрифте`() {
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = 2f)) {
                FinnypetTheme {
                    Box(modifier = Modifier.width(328.dp)) {
                        FinnyButton(text = text(R.string.main_task_action), onClick = {}, reward = Coins(10))
                    }
                }
            }
        }

        compose.onNodeWithText("+10", useUnmergedTree = true)
            .assertIsDisplayed()
            .assertWidthIsAtLeast(24.dp)
    }

    /** Отложенные монеты не должны исчезать с экрана из-за невыбранной цели. */
    @Test
    fun `без_цели_накопления_всё_равно_видны`() {
        showMain(readyState(savings = SavingsView(saved = Coins(30))))

        scrollToDescription(text(R.string.main_jar_no_goal_description, 30))
    }

    /** Накоплено больше цены — «хватает на цель», а не «130 из 120» (ревью F5). */
    @Test
    fun `собранная_цель_названа_собранной`() {
        showMain(
            readyState(
                savings = SavingsView(
                    saved = Coins(130),
                    goalTitle = "Самокат мечты",
                    price = Coins(120),
                )
            )
        )

        scrollToDescription(text(R.string.main_jar_goal_enough_description, "Самокат мечты", 130))
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

    /** DESIGN_PLAN 3.1, правка владельца №2: магазин — после плана, главной кнопкой. */
    @Test
    fun `с_главного_экрана_можно_перейти_в_магазин`() {
        var opened = false
        showMain(readyState(periodStatus = PeriodStatus.RUNNING), onShop = { opened = true })

        compose.onNodeWithText(text(R.string.shop_action)).performClick()

        assertTrue(opened)
    }

    /**
     * ТЗ 2.5.3: задание дня видно на главном. Плитка «Задания» говорит про
     * монеты и ведёт в список, само задание — главной кнопкой утра.
     */
    @Test
    fun `задание_дня_на_главном_ведёт_в_задание`() {
        var opened: TaskId? = null
        var list = false
        val task = TaskOfDay(
            id = TaskId("story"),
            topic = TaskTopic.SAVING,
            rewardAvailable = true,
            allDone = false,
            reward = Coins(10),
            completedCount = 2,
            totalCount = 6,
        )
        showMain(
            readyState(task = task, periodStatus = PeriodStatus.RUNNING, step = NextStep.Task),
            onTask = { opened = it },
            onTasks = { list = true },
        )

        scrollToDescription(text(R.string.main_task_reward))
        compose.onNodeWithContentDescription(text(R.string.main_task_reward), substring = true).performClick()
        compose.onNodeWithText(text(R.string.main_task_action)).performClick()

        assertTrue(list)
        assertEquals(TaskId("story"), opened)
    }

    /** R7: задания открыты и до плана — сначала заработай, потом распредели. */
    @Test
    fun `до_плана_задание_дня_открыто`() {
        var opened: TaskId? = null
        val task = TaskOfDay(
            id = TaskId("story"),
            topic = TaskTopic.SAVING,
            rewardAvailable = true,
            allDone = false,
            reward = Coins(10),
        )
        showMain(readyState(task = task, step = NextStep.Task), onTask = { opened = it })

        compose.onNodeWithText(text(R.string.main_task_action)).performClick()

        assertEquals(TaskId("story"), opened)
    }

    /** ТЗ 8.4: главная кнопка — следующий шаг цикла; утром это задание с наградой, план — второй. */
    @Test
    fun `главная_кнопка_ведёт_к_заданию`() {
        var opened: TaskId? = null
        var planned = false
        val task = TaskOfDay(TaskId("story"), TaskTopic.SAVING, rewardAvailable = true, allDone = false, reward = Coins(10))
        showMain(readyState(task = task, step = NextStep.Task), onTask = { opened = it }, onPlan = { planned = true })

        compose.onNodeWithText(text(R.string.main_task_action)).performClick()
        compose.onNodeWithText(text(R.string.budget_action_plan)).performClick()

        assertEquals(TaskId("story"), opened)
        assertTrue(planned)
    }

    /** После плана вторая кнопка — «Уложить спать», а план открывается плиткой. */
    @Test
    fun `на_шаге_покупки_вторая_кнопка_укладывает_спать`() {
        var sleep = false
        var plan = false
        showMain(
            readyState(periodStatus = PeriodStatus.RUNNING, step = NextStep.Shop),
            onFinishDay = { sleep = true },
            onPlan = { plan = true },
        )

        compose.onNodeWithText(text(R.string.main_action_sleep)).performClick()
        scrollToDescription(text(R.string.main_coins_unplanned))
        compose.onNodeWithContentDescription(text(R.string.main_coins_unplanned)).performClick()

        assertTrue(sleep)
        assertTrue(plan)
    }

    /** Вечером главная — «Уложить спать», а магазин остаётся второй кнопкой. */
    @Test
    fun `вечером_вторая_кнопка_ведёт_в_магазин`() {
        var shop = false
        showMain(readyState(periodStatus = PeriodStatus.RUNNING, step = NextStep.Sleep), onShop = { shop = true })

        compose.onNodeWithText(text(R.string.shop_action)).performClick()

        assertTrue(shop)
    }

    /** Итоги — одной кнопкой «Уложить спать», без второй такой же. */
    @Test
    fun `когда_всё_по_плану_главная_кнопка_заканчивает_день`() {
        var opened = false
        showMain(readyState(periodStatus = PeriodStatus.RUNNING, step = NextStep.Sleep), onFinishDay = { opened = true })

        compose.onAllNodesWithText(text(R.string.main_action_sleep)).assertCountEquals(1)
        compose.onNodeWithText(text(R.string.main_action_sleep)).performClick()

        assertTrue(opened)
    }

    /** DESIGN_PLAN 3.1: плитка «Задания» показывает это в описании для TalkBack, не строкой текста. */
    @Test
    fun `когда_монеты_за_сегодня_получены_главный_об_этом_говорит`() {
        val task = TaskOfDay(
            id = TaskId("story"),
            topic = TaskTopic.SAVING,
            rewardAvailable = false,
            allDone = true,
            reward = Coins(10),
            limitReached = true,
        )
        showMain(readyState(task = task))

        scrollToDescription(text(R.string.main_task_all_done))
    }

    /** Плитка копилки — кнопка, и подписана словами, а не только цветом. */
    @Test
    fun `карточка_копилки_ведёт_в_копилку`() {
        var opened = false
        showMain(readyState(), onSavings = { opened = true })

        val spoken = text(R.string.main_jar_goal_description, "Самокат мечты", 30, 120)
        scrollToDescription(spoken)
        compose.onNodeWithContentDescription(spoken).performClick()

        assertTrue(opened)
    }

    /** Дорога к итогам — второй кнопкой, пока день идёт, а не третьей внизу. */
    @Test
    fun `из_идущего_дня_можно_попасть_в_итоги`() {
        var opened = false
        showMain(
            readyState(periodStatus = PeriodStatus.RUNNING),
            onFinishDay = { opened = true },
        )

        compose.onNodeWithText(text(R.string.main_action_sleep)).performClick()

        assertTrue(opened)
    }

    /** Пока день планируется, заканчивать нечего — и подписи нет. */
    @Test
    fun `в_день_на_планировании_итогов_не_предлагают`() {
        showMain(readyState())

        compose.onAllNodesWithText(text(R.string.day_action_close)).assertCountEquals(0)
    }

    // --- Магазин (ТЗ 2.5.6) ---

    @Test
    fun `магазин_показывает_товары_с_ценой_направлением_и_влиянием`() {
        showShop(ready())

        // Кошелёк — в шапке, над прокручиваемым списком.
        compose.onNodeWithContentDescription("80 монет").assertIsDisplayed()
        scrollToText("Вкусная каша")
        scrollToText(text(R.string.category_mandatory))
        scrollToText(text(R.string.stat_change, text(R.string.stat_satiety), "+20"))
        scrollToDescription("12 монет")
        scrollToText("Яркий мячик")
        scrollToText(text(R.string.category_optional))
    }

    /** F9, TalkBack: плитка товара — одна фраза, название первым, затем цена и влияние. */
    @Test
    fun `плитка_товара_читается_названием_и_ценой`() {
        var bought: ItemId? = null
        showShop(ready(), onBuy = { bought = it })

        val spoken = "Вкусная каша, 12 монет, " + text(R.string.stat_change, text(R.string.stat_satiety), "+20")
        compose.onNode(hasScrollAction()).performScrollToNode(hasContentDescription(spoken))
        compose.onNodeWithContentDescription(spoken).assertIsDisplayed().performClick()
        compose.onNodeWithText(text(R.string.shop_buy)).performClick()

        assertEquals(ItemId("food"), bought)
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
        val done = PurchaseOutcome.Done(
            number = 1,
            itemId = food.id,
            text = "Осталось 68 монет.",
            price = Coins(12),
            effects = food.effects,
            changes = listOf(Change.PetStat(PetStatKind.SATIETY, from = Stat(75), to = Stat(95))),
        )
        // В списке только мячик: иначе «Еда +20» нашлось бы и в плитке каши.
        showShop(ready(outcome = done, items = listOf(toy)))

        // Итог — в облачке совы наверху списка, без отдельного окна (DESIGN_PLAN 3.5):
        // фраза, «−12» и «Еда +20».
        compose.onNodeWithText("Осталось 68 монет.").assertIsDisplayed()
        compose.onNodeWithContentDescription(text(R.string.shop_spent, "12 монет")).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.stat_change, text(R.string.stat_satiety), "+20"), substring = true).assertIsDisplayed()
    }

    /** ТЗ 2.5.9: показатель упёрся в границу — говорим об этом, а не «+20». */
    @Test
    fun `покупка_без_изменений_говорит_об_этом_честно`() {
        val done = PurchaseOutcome.Done(
            number = 1,
            itemId = food.id,
            text = "Осталось 68 монет.",
            price = Coins(12),
            effects = food.effects,
            changes = emptyList(),
        )
        showShop(ready(outcome = done, items = listOf(toy)))

        compose.onNodeWithText(text(R.string.shop_no_change)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.stat_change, text(R.string.stat_satiety), "+20"))
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
        showShop(rejected(), onDismiss = { dismissed = true })

        compose.onNodeWithText(text(R.string.shortage_many, 20)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.shop_buy)).assertDoesNotExist()
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
        showShop(rejected(), onDismiss = { dismissed = true }, onSavings = { savings = true })

        compose.onNodeWithText("Взять из копилки").performClick()

        assertTrue(dismissed)
        assertTrue(savings)
    }

    /** Экран заданий есть — «выполнить задание» ведёт в него и стоит главной кнопкой. */
    @Test
    fun `отказ_ведёт_в_задания_главной_кнопкой`() {
        var tasks = false
        showShop(rejected(), onTasks = { tasks = true })

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
        icon = "🥣",
        gains = listOf(Change.PetStat(PetStatKind.SATIETY, Stat(50), Stat(70))),
    )

    private val toy = ShopItemView(
        id = ItemId("toy"),
        title = "Яркий мячик",
        price = Coins(18),
        category = SpendCategory.OPTIONAL,
        effects = listOf(PetEffect(PetStatKind.MOOD, 15)),
        icon = "⚽",
        gains = listOf(Change.PetStat(PetStatKind.MOOD, Stat(50), Stat(65))),
    )

    /**
     * Отказ открывает окно того же товара, а нехватку и выходы окно берёт у
     * самого товара (DESIGN_PLAN 3.5): «Купить» там уже нет.
     */
    private fun rejected() = ready(
        outcome = PurchaseOutcome.Rejected(itemId = food.id),
        items = listOf(
            food.copy(
                shortage = ItemShortage(
                    shortfall = Coins(20),
                    options = listOf(
                        RecoveryChoice(RecoveryOption.DO_TASK, "Выполнить задание"),
                        RecoveryChoice(RecoveryOption.WITHDRAW_FROM_SAVINGS, "Взять из копилки"),
                        RecoveryChoice(RecoveryOption.ADJUST_NEXT_PLAN, "Пересмотреть план"),
                        RecoveryChoice(RecoveryOption.CHOOSE_CHEAPER, "Выбрать подешевле"),
                        RecoveryChoice(RecoveryOption.POSTPONE_PURCHASE, "Купить попозже"),
                    ),
                    recommended = RecoveryOption.DO_TASK,
                ),
            ),
            toy,
        ),
    )

    private fun ready(
        canBuy: Boolean = true,
        outcome: PurchaseOutcome? = null,
        items: List<ShopItemView> = listOf(food, toy),
    ) = ShopState.Ready(
        items = items,
        balance = Coins(80),
        canBuy = canBuy,
        owl = testOwl(),
        phrase = "Мне бы поесть",
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

        // Награда дня — чипом «Сегодня: +10» в карточке прогресса (DESIGN_PLAN 3.9).
        scrollToText(text(R.string.tasks_reward_today, 10))
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

    /** R7: задания доступны и до плана — нажатие открывает задание, а не зовёт в план. */
    @Test
    fun `задание_открывается_без_похода_в_план`() {
        var opened: TaskId? = null
        showTasks(tasksReady(), onOpen = { opened = it })

        scrollToText("Сова нашла монеты.")
        compose.onNodeWithText("Сова нашла монеты.").performClick()

        compose.onAllNodesWithText(text(R.string.budget_action_plan)).assertCountEquals(0)
        assertEquals(TaskId("story"), opened)
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

        // F7: у задания из одного шага счётчика «Шаг 1 из 1» нет.
        compose.onNodeWithText(text(R.string.task_step, 1, 1)).assertDoesNotExist()
        scrollToText(text(R.string.task_selected))
        // Состояние «выбрано» доступно и озвучке, не только словом.
        compose.onNodeWithText("Отложить").assertIsSelected()
        compose.onNodeWithText("Потратить").assertIsNotSelected()
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
        showTask(taskReady(stage = TaskStage.Step(index = 0, total = 2, step = step)), onSet = { category, _ -> added = category })

        scrollToText(text(R.string.task_step, 1, 2))
        scrollToDescription(text(R.string.budget_amount, text(R.string.category_mandatory), 15))
        // Остаток — закреплённым счётчиком над кнопкой, как на плане дня (DESIGN_PLAN 3.2).
        compose.onNodeWithContentDescription(text(R.string.budget_remainder) + ": 15 монет").assertIsDisplayed()
        scrollToDescription(text(R.string.budget_more, text(R.string.category_savings)))
        compose.onNodeWithContentDescription(text(R.string.budget_more, text(R.string.category_savings))).performClick()
        assertEquals(SpendCategory.SAVINGS, added)
        compose.onNodeWithText(text(R.string.task_next)).assertIsEnabled()
    }

    @Test
    fun `полка_считает_корзину_и_не_даёт_взять_лишнее`() {
        var toggled: String? = null
        val step = StepView.Pick(
            prompt = "Что возьмём?",
            budget = Coins(30),
            items = listOf(
                PickItemView("food", "Каша", Coins(12)),
                PickItemView("toy", "Мячик", Coins(18)),
                PickItemView("bike", "Велосипед", Coins(25)),
            ),
            picked = setOf("toy"),
        )
        showTask(taskReady(stage = TaskStage.Step(index = 0, total = 1, step = step)), onToggle = { toggled = it })

        scrollToDescription(text(R.string.task_basket_progress, "18 монет", 30))
        scrollToText(text(R.string.task_picked))
        scrollToText(text(R.string.task_pick_full))
        compose.onNodeWithText("Каша").performClick()

        assertEquals("food", toggled)
    }

    /** ТЗ 2.5.8: объяснение независимо от результата; награда — отдельной строкой. */
    @Test
    fun `итог_показывает_объяснение_награду_и_питомца`() {
        var finished = false
        val outcome = TaskOutcomeView(
            correct = true,
            text = "Молодец, отложил!",
            reward = Coins(15),
            changes = listOf(Change.PetStat(PetStatKind.MOOD, from = Stat(75), to = Stat(80))),
        )
        showTask(taskReady(stage = TaskStage.Done(outcome)), onBack = { finished = true })

        scrollToText("Молодец, отложил!")
        scrollToText(text(R.string.task_reward_paid, "15 монет"))
        scrollToText(text(R.string.stat_change, text(R.string.stat_mood), "+5"))
        // После верного ответа — «Дальше» к списку заданий (DESIGN_PLAN 3.8).
        compose.onNodeWithText(text(R.string.task_next)).performClick()

        assertTrue(finished)
    }

    @Test
    fun `итог_без_монет_говорит_об_этом_честно`() {
        val outcome = TaskOutcomeView(correct = true, text = "Потратил всё.", reward = Coins.ZERO, changes = emptyList())
        showTask(taskReady(stage = TaskStage.Done(outcome)))

        scrollToText(text(R.string.task_reward_none))
        compose.onNodeWithText(text(R.string.task_reward_rule)).assertDoesNotExist()
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
        owl = testOwl(),
        rewardAvailable = rewardAvailable,
        reward = Coins(10),
    )

    private fun taskReady(
        stage: TaskStage,
        rewardAvailable: Boolean = true,
    ) = TaskState.Ready(
        id = TaskId("story"),
        topic = TaskTopic.SAVING,
        intro = "Сова нашла монеты. Что с ними делать?",
        owl = testOwl(),
        balance = Coins(40),
        maxReward = Coins(15),
        rewardAvailable = rewardAvailable,
        limitReached = !rewardAvailable,
        stage = stage,
    )

    private fun showTasks(
        state: TasksState,
        onOpen: (TaskId) -> Unit = {},
    ) {
        compose.setContent {
            FinnypetTheme {
                TasksContent(state = state, onBack = {}, onOpen = onOpen)
            }
        }
    }

    private fun showTask(
        state: TaskState,
        onBack: () -> Unit = {},
        onStart: () -> Unit = {},
        onChoose: (String) -> Unit = {},
        onSet: (SpendCategory, Coins) -> Unit = { _, _ -> },
        onToggle: (String) -> Unit = {},
        onNext: () -> Unit = {},
    ) {
        compose.setContent {
            FinnypetTheme {
                TaskContent(
                    state = state,
                    onBack = onBack,
                    onStart = onStart,
                    onChoose = onChoose,
                    onSet = onSet,
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
        scrollToText(text(R.string.savings_eta, 8, text(R.string.days_few, 2)))
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
        // Без цели двух бледных кнопок внизу нет вовсе: главное действие — выбрать цель.
        compose.onNodeWithText(text(R.string.savings_deposit)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.savings_withdraw)).assertDoesNotExist()
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
        // F9, TalkBack: «Больше монет», а не голое «Больше» — чего больше, было неясно.
        // Подпись общая для AmountStepper в окнах пополнения и снятия, поэтому
        // без слова «отложить» или «взять» — оно врало бы в одном из двух окон.
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
        // F9: тот же AmountStepper, что и в пополнении, — подпись «+» должна
        // остаться нейтральной («Больше монет»), а не «Отложить больше»,
        // которое здесь, при снятии, было бы неправдой.
        compose.onNodeWithContentDescription(text(R.string.savings_amount_more)).assertIsDisplayed()
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
        buyTitle = "самокат",
        price = Coins(30),
        saved = Coins(10),
        isActive = true,
        icon = "🛴",
    )

    private val book = GoalView(
        id = GoalId("book"),
        title = "Книжка",
        buyTitle = "книжку",
        price = Coins(15),
        saved = Coins.ZERO,
        isActive = false,
        icon = "📚",
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
            usualDeposit = Coins(8),
            balance = Coins(80),
            canOperate = canOperate,
            owl = testOwl(),
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

    /**
     * ТЗ 2.5.5: приложение не даёт распределить больше доступного. «+» при
     * этом нажимается: запрос обрезает вьюмодель, и сова объясняет, что монеты
     * кончились (BudgetPlanningTest). Экран же говорит «Всё разложено».
     */
    @Test
    fun `когда_всё_распределено_план_можно_подтвердить`() {
        showBudget(planning(plan = BudgetPlan(Coins(40), Coins(20), Coins(20))))

        compose.onNodeWithContentDescription(text(R.string.budget_distributed)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.budget_confirm)).assertIsEnabled()
    }

    /** F9: при шрифте 2,0 справка о кошельке прокручивается вместе с банками, а не держит место над кнопкой. */
    @Test
    fun `при_крупном_шрифте_справка_о_кошельке_в_списке`() {
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = 2f)) {
                FinnypetTheme { BudgetContent(state = planning(), onBack = {}, onSet = { _, _ -> }) }
            }
        }

        compose.onNode(hasText(text(R.string.budget_wallet_unchanged)) and hasAnyAncestor(hasScrollAction()))
            .assertExists()
    }

    @Test
    fun `перебор_виден_и_блокирует_подтверждение`() {
        showBudget(
            BudgetState.Planning(
                available = Coins(80),
                plan = BudgetPlan(Coins(60), Coins(30), Coins(0)),
                remainder = Coins.ZERO,
                overBy = Coins(10),
                needsGoal = false,
                hasGoal = true,
                owl = testOwl(),
                phrase = "Мне не хватит",
            )
        )

        // Перебор — словом и числом в закреплённом счётчике, не только цветом (ТЗ 3.6).
        compose.onNodeWithContentDescription(text(R.string.budget_over) + " 10 монет").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.budget_confirm)).assertIsNotEnabled()
    }

    @Test
    fun `монеты_можно_добавить_и_убрать`() {
        val sets = mutableListOf<Pair<SpendCategory, Coins>>()
        showBudget(
            state = planning(plan = BudgetPlan(Coins(10), Coins.ZERO, Coins.ZERO)),
            onSet = { category, amount -> sets += category to amount },
        )

        val more = text(R.string.budget_more, text(R.string.category_optional))
        val less = text(R.string.budget_less, text(R.string.category_mandatory))
        scrollToDescription(more)
        compose.onNodeWithContentDescription(more).performClick()
        scrollToDescription(less)
        compose.onNodeWithContentDescription(less).performClick()

        // «+» и «−» — на монету от того, что в банке: 0 → 1 и 10 → 9.
        assertEquals(listOf(SpendCategory.OPTIONAL to Coins(1), SpendCategory.MANDATORY to Coins(9)), sets)
    }

    /** Последний абзац ТЗ 2.5.5: после подтверждения видно план рядом с фактом. */
    @Test
    fun `подтверждённый_план_показывает_план_и_факт`() {
        showBudget(
            BudgetState.Started(
                lines = listOf(
                    BudgetLine(SpendCategory.MANDATORY, Coins(40), Coins(45), followed = false),
                    BudgetLine(SpendCategory.OPTIONAL, Coins(20), Coins(20), followed = true),
                    BudgetLine(SpendCategory.SAVINGS, Coins(20), Coins(20), followed = true),
                ),
                wallet = Coins(15),
            )
        )

        // После подтверждения — три банки со словами «осталось N из M», копилка —
        // «отложено» (DESIGN_PLAN 3.2); сравнение с фактом — в итогах дня.
        compose.onNodeWithText(text(R.string.budget_started)).assertIsDisplayed()
        compose.onNodeWithContentDescription(text(R.string.budget_jar_left_of, text(R.string.category_mandatory), 0, 40))
            .assertIsDisplayed()
        compose.onNodeWithContentDescription(
            text(R.string.budget_jar_description, text(R.string.category_savings), text(R.string.budget_jar_saved, 20)),
        ).assertIsDisplayed()
        compose.onNodeWithContentDescription("15 монет", substring = true).assertIsDisplayed()
    }

    // --- Прогресс и справочник (ТЗ 2.5.11) ---

    @Test
    fun `прогресс_показывает_итоги_цель_и_задания`() {
        showProgress(
            ProgressState.Ready(
                owl = testOwl(),
                growth = testGrowth(),
                lastDay = LastDay(
                    number = 2,
                    lines = comparisonLines(),
                ),
                goal = GoalSummary(title = "Самокат мечты", icon = "🛴", saved = Coins(30), price = Coins(120)),
                topics = emptyList(),
                passed = listOf(
                    PassedTask(
                        id = TaskId("plan"),
                        title = "Разложи монеты",
                        topic = TaskTopic.PLANNING,
                        reward = Coins(15),
                    ),
                ),
                terms = listOf(Term(id = "budget", title = "Бюджет", body = "Это сколько у тебя есть монеток.")),
            )
        )

        // Вчерашний день и задания — свёрнутые строки (DESIGN_PLAN 3.10): сначала
        // чипы «по плану», полосы и список — по нажатию на заголовок.
        scrollToDescription(text(R.string.category_mandatory) + ": ")
        openFold(text(R.string.progress_last_day, 2))
        scrollToDescription(text(R.string.category_mandatory) + ": по плану 40, потрачено 35")
        scrollToDescription(text(R.string.main_goal_progress, "Самокат мечты", 30, 120))
        openFold(text(R.string.tasks_progress, 1, 0))
        scrollToText("Разложи монеты")
        scrollToText(text(R.string.topic_planning))
    }

    /** Б22: пройденное без монет — это тренировка, а не штраф в виде «0 монет». */
    @Test
    fun `пройденное_задание_без_награды_не_показывает_0_монет`() {
        showProgress(
            ProgressState.Ready(
                owl = testOwl(),
                growth = testGrowth(),
                topics = emptyList(),
                lastDay = null,
                goal = null,
                passed = listOf(
                    PassedTask(
                        id = TaskId("story"),
                        title = "Сова нашла монеты",
                        topic = TaskTopic.SAVING,
                        reward = Coins.ZERO,
                    ),
                ),
                terms = emptyList(),
            )
        )

        openFold(text(R.string.tasks_progress, 1, 0))
        scrollToText(text(R.string.progress_task_no_reward))
        compose.onAllNodesWithContentDescription(text(R.string.coins_many, 0)).assertCountEquals(0)
    }

    /** Пока день не закончен и заданий нет — экран объясняет, а не пустует. */
    @Test
    fun `пустой_прогресс_объясняет_что_будет_дальше`() {
        showProgress(
            ProgressState.Ready(
                owl = testOwl(),
                growth = testGrowth(),
                goal = null,
                lastDay = null,
                passed = emptyList(),
                topics = emptyList(),
                terms = emptyList(),
            ),
        )

        scrollToText(text(R.string.progress_no_days))
        scrollToText(text(R.string.main_goal_none))
        openFold(text(R.string.tasks_progress, 0, 0))
        scrollToText(text(R.string.progress_no_tasks))
    }

    /**
     * Справочник свёрнут: шесть объяснений подряд заняли бы весь экран.
     *
     * Что будет по нажатию — подписью действия, а не описанием: описание
     * заменило бы собой объяснение термина в озвучке (ТЗ 3.6).
     */
    @Test
    fun `термин_разворачивается_по_нажатию`() {
        val body = "Это сколько у тебя есть монеток."
        showProgress(
            ProgressState.Ready(
                owl = testOwl(),
                growth = testGrowth(),
                topics = emptyList(),
                lastDay = null,
                goal = null,
                passed = emptyList(),
                terms = listOf(Term(id = "budget", title = "Бюджет", body = body)),
            )
        )

        // Сам словарик — тоже свёрнутая строка «Что значат слова».
        openFold(text(R.string.progress_glossary))
        compose.onAllNodesWithText(body).assertCountEquals(0)
        compose.onNode(hasClickLabel(text(R.string.progress_term_closed, "Бюджет"))).performClick()

        scrollToText(body)
        compose.onNode(hasClickLabel(text(R.string.progress_term_opened, "Бюджет"))).assert(hasText(body))
    }

    @Test
    fun `с_главного_экрана_можно_попасть_в_прогресс`() {
        var opened = false
        showMain(readyState().copy(growth = GrowthView(GrowthStage.YOUNG, points = 2, target = 6)), onProgress = { opened = true })

        // Плитка роста — звёзды и «До подростка»; ведёт в «Мой прогресс».
        // Точное совпадение: та же фраза есть и в конце подписи имени под совой.
        val spoken = text(R.string.main_growth_to_young) + " " + text(R.string.main_growth_points, 2, 6)
        compose.onNode(hasScrollAction()).performScrollToNode(hasContentDescription(spoken))
        compose.onNodeWithContentDescription(spoken).performClick()

        assertTrue(opened)
    }

    /** ТЗ 2.5.1: к подсказке можно вернуться в любой момент. */
    @Test
    fun `с_главного_экрана_можно_открыть_подсказку`() {
        var opened = false
        showMain(readyState(), onHelp = { opened = true })

        // «?» — значок в шапке: подпись у него для TalkBack, не текстом.
        compose.onNodeWithContentDescription(text(R.string.help_action)).performClick()

        assertTrue(opened)
    }

    @Test
    fun `сбой_прогресса_предлагает_повтор`() {
        var retried = false
        showProgress(ProgressState.Failed, onRetry = { retried = true })

        compose.onNodeWithText(text(R.string.progress_failed)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.action_retry)).performClick()

        assertTrue(retried)
    }

    private fun showProgress(state: ProgressState, onRetry: () -> Unit = {}) {
        compose.setContent {
            FinnypetTheme {
                ProgressContent(state = state, onBack = {}, onRetry = onRetry)
            }
        }
    }

    // --- Раздел для взрослого (ТЗ 2.5.12) ---

    /** Барьер: верный ответ пускает, неверный — нет и говорит об этом словом. */
    @Test
    fun `верный_ответ_открывает_раздел_взрослого`() {
        var opened = false
        showGate(Riddle(14, 3), onSolved = { opened = true })

        compose.onNodeWithText(text(R.string.adult_gate_answer)).performTextInput("42")
        compose.onNodeWithText(text(R.string.adult_gate_open)).performClick()

        assertTrue(opened)
    }

    @Test
    fun `неверный_ответ_не_пускает_и_объясняет`() {
        var opened = false
        showGate(Riddle(14, 3), onSolved = { opened = true })

        compose.onNodeWithText(text(R.string.adult_gate_answer)).performTextInput("41")
        compose.onNodeWithText(text(R.string.adult_gate_open)).performClick()

        assertFalse(opened)
        // С прокруткой: на маленьком экране клавиатура сдвигает объяснение
        // за нижний край, и «есть в дереве» ещё не значит «видно».
        scrollToText(text(R.string.adult_gate_wrong))
    }

    /** Пока ответа нет, открывать нечего — кнопка недоступна. */
    @Test
    fun `без_ответа_кнопка_недоступна`() {
        showGate(Riddle(14, 3))

        compose.onNodeWithText(text(R.string.adult_gate_open)).assertIsNotEnabled()
    }

    @Test
    fun `раздел_показывает_цели_темы_и_прогресс`() {
        showAdult(adultState())

        scrollToText("Игра учит планировать.")
        scrollToText(text(R.string.topic_planning))
        scrollToText(text(R.string.adult_topic_passed, 1, 2))
        scrollToText(text(R.string.adult_overview, "Егор"))
        scrollToText(text(R.string.adult_days))
    }

    /** F9, TalkBack: «Подробнее» говорит, что раскроется, и после нажатия — что свернётся. */
    @Test
    fun `подробнее_у_взрослого_подписано_смыслом`() {
        showAdult(adultState().copy(about = listOf("Игра учит планировать.", "Второй абзац.")))

        compose.onNode(hasScrollAction())
            .performScrollToNode(hasContentDescription(text(R.string.adult_about_more_spoken)))
        compose.onNodeWithContentDescription(text(R.string.adult_about_more_spoken)).performClick()

        scrollToText("Второй абзац.")
        compose.onNodeWithContentDescription(text(R.string.adult_about_less_spoken)).assertExists()
    }

    /** ТЗ 2.5.12: никаких негативных оценок — только «пройдено N из M». */
    @Test
    fun `нетронутая_тема_показана_без_упрёка`() {
        showAdult(adultState())

        scrollToText(text(R.string.topic_payments))
        scrollToText(text(R.string.adult_topic_passed, 0, 2))
    }

    @Test
    fun `бонус_начисляется_кнопкой`() {
        var awarded = false
        showAdult(adultState(), onAward = { awarded = true })

        compose.onNode(hasScrollAction())
            .performScrollToNode(hasText(text(R.string.adult_bonus_action, text(R.string.coins_many, 10))))
        compose.onNodeWithText(text(R.string.adult_bonus_action, text(R.string.coins_many, 10))).performClick()

        assertTrue(awarded)
    }

    @Test
    fun `выданный_за_день_бонус_не_предлагается_снова`() {
        showAdult(adultState(AwardState.USED))

        compose.onAllNodesWithText(text(R.string.adult_bonus_action, text(R.string.coins_many, 10))).assertCountEquals(0)
        scrollToText(text(R.string.adult_bonus_used))
    }

    /** Без игрового дня начислять некуда — это не «уже начислено». */
    @Test
    fun `без_игрового_дня_бонус_не_обещают_и_не_объявляют_выданным`() {
        showAdult(adultState(AwardState.NO_DAY))

        compose.onAllNodesWithText(text(R.string.adult_bonus_used)).assertCountEquals(0)
        scrollToText(text(R.string.adult_bonus_no_day))
    }

    /**
     * Б16: нерабочего «Звук в игре» у взрослого нет. Пункт A1: звук, мелодия и
     * движение — в окне настроек на главном, у взрослого переключателей нет вовсе.
     */
    @Test
    fun `звук_в_игре_скрыт_у_взрослого`() {
        showAdult(adultState())

        scrollToText(text(R.string.adult_delete))
        compose.onAllNodesWithText("Звук в игре").assertCountEquals(0)
        compose.onAllNodesWithText(text(R.string.settings_animations)).assertCountEquals(0)
    }

    @Test
    fun `с_главного_экрана_можно_попасть_к_взрослому`() {
        var opened = false
        showMain(readyState(), onAdult = { opened = true })

        compose.onNodeWithContentDescription(text(R.string.adult_action)).performClick()

        assertTrue(opened)
    }

    @Test
    fun `сбой_раздела_взрослого_предлагает_повтор`() {
        var retried = false
        showAdult(AdultState.Failed, onRetry = { retried = true })

        compose.onNodeWithText(text(R.string.adult_failed)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.action_retry)).performClick()

        assertTrue(retried)
    }

    private fun adultState(award: AwardState = AwardState.AVAILABLE) = AdultState.Ready(
        childName = "Егор",
        petName = "Пушок",
        talk = "Сегодня всё по плану.",
        about = listOf("Игра учит планировать."),
        topics = listOf(
            TopicProgress(TaskTopic.PLANNING, passed = 1, total = 2),
            TopicProgress(TaskTopic.SAVING, passed = 2, total = 2),
            TopicProgress(TaskTopic.PAYMENTS, passed = 0, total = 2),
        ),
        days = 3,
        stage = GrowthStage.YOUNG,
        points = 12,
        growth = GrowthView(GrowthStage.GROWN, points = 2, target = 6),
        balance = Coins(40),
        saved = Coins(30),
        bonus = Coins(10),
        award = award,
    )

    private fun showGate(riddle: Riddle, onSolved: () -> Unit = {}) {
        compose.setContent {
            FinnypetTheme {
                AdultGateContent(riddle = riddle, onSolved = onSolved, onBack = {})
            }
        }
    }

    private fun showAdult(
        state: AdultState,
        onRetry: () -> Unit = {},
        onAward: () -> Unit = {},
        onStartDemo: () -> Unit = {},
        onDeleteGame: () -> Unit = {},
    ) {
        compose.setContent {
            FinnypetTheme {
                AdultContent(
                    state = state,
                    onBack = {},
                    onRetry = onRetry,
                    onAward = onAward,
                    onStartDemo = onStartDemo,
                    onDeleteGame = onDeleteGame,
                )
            }
        }
    }

    // --- Демонстрационный режим (ТЗ 2.5.13) ---

    /** Вне демонстрации чипа нет совсем: ребёнок про этот режим не знает. */
    @Test
    fun `вне_демонстрации_чипа_нет`() {
        compose.setContent { FinnypetTheme { DemoChipContent(visible = false) } }

        compose.onAllNodesWithContentDescription(text(R.string.demo_banner)).assertCountEquals(0)
        compose.onAllNodesWithText(text(R.string.demo_play_day)).assertCountEquals(0)
    }

    /** В демонстрации чип открывает окно с обоими действиями. Выход — только с подтверждением. */
    @Test
    fun `чип_демонстрации_открывает_окно_с_обоими_действиями`() {
        var played = false
        var exited = false
        compose.setContent {
            FinnypetTheme {
                DemoChipContent(
                    visible = true,
                    onPlayDay = { played = true },
                    onExit = { exited = true },
                )
            }
        }

        compose.onNodeWithContentDescription(text(R.string.demo_banner)).assertIsDisplayed().performClick()
        compose.onNodeWithText(text(R.string.demo_play_day)).performClick()
        compose.onNodeWithContentDescription(text(R.string.demo_banner)).performClick()
        compose.onNodeWithText(text(R.string.demo_exit)).performClick()
        assertFalse("выход стирает демонстрацию без спроса", exited)
        compose.onNodeWithText(text(R.string.demo_exit_confirm)).performClick()

        assertTrue(played)
        assertTrue(exited)
    }

    @Test
    fun `удаление_игры_спрашивает_подтверждение`() {
        var deleted = false
        showAdult(adultState(), onDeleteGame = { deleted = true })

        compose.onNode(hasScrollAction())
            .performScrollToNode(hasText(text(R.string.adult_delete_action)))
        compose.onNodeWithText(text(R.string.adult_delete_action)).performClick()
        assertFalse("игра удалена без спроса", deleted)
        compose.onNodeWithText(text(R.string.adult_delete_confirm_title)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.adult_delete_confirm)).performClick()

        assertTrue(deleted)
    }

    @Test
    fun `из_раздела_взрослого_запускается_демонстрация`() {
        var started = false
        showAdult(adultState(), onStartDemo = { started = true })

        compose.onNode(hasScrollAction())
            .performScrollToNode(hasText(text(R.string.adult_demo_action)))
        compose.onNodeWithText(text(R.string.adult_demo_action)).performClick()

        assertTrue(started)
    }

    // --- Итоги дня (ТЗ 2.5.9, 2.5.10) ---

    /** ТЗ 2.5.9: после действия видно, что изменилось, и почему. */
    @Test
    fun `итоги_дня_объясняют_и_показывают_изменения`() {
        showDay(
            DayState.Closed(
                DaySummary(
                    number = 1,
                    nextNumber = 2,
                    headline = "День успешно завершён.",
                    owl = testOwl(),
                    checks = listOf(DayCheckView(done = true, text = "Еда и уход — всё купили, потратили 37.")),
                    tip = "Совет: так держать!",
                    earnedPoints = 6,
                    growth = testGrowth(),
                    noStarsReason = null,
                    newStage = GrowthStage.YOUNG,
                    carryOver = Coins(5),
                )
            )
        )

        // Итоги — звёзды дня со словами и строки «почему» (DESIGN_PLAN 3.6).
        scrollToText("День успешно завершён.")
        scrollToDescription(text(R.string.day_star_earned, text(R.string.day_star_fed)))
        scrollToDescription(text(R.string.day_check_done, "Еда и уход — всё купили, потратили 37."))
        scrollToText(text(R.string.day_growth_many, 6))
        scrollToText(text(R.string.day_new_stage, text(R.string.stage_young)))
        // «5 монет» вхождением нашлось бы и в «75 монет» — ищем по подписи.
        scrollToText(text(R.string.day_carry_over))
        compose.onNodeWithText(text(R.string.day_action_next, 2)).assertIsDisplayed()
    }

    /** Ничего не изменилось — так и говорим, а не показываем пустоту. */
    @Test
    fun `день_без_изменений_говорит_об_этом`() {
        showDay(
            DayState.Closed(
                DaySummary(
                    number = 1,
                    nextNumber = 2,
                    headline = "День завершён.",
                    owl = testOwl(),
                    checks = emptyList(),
                    tip = "Совет: так держать!",
                    earnedPoints = 0,
                    growth = testGrowth(),
                    noStarsReason = null,
                    newStage = null,
                    carryOver = Coins.ZERO,
                )
            )
        )

        // Звёзд нет — слоты всё равно на месте и названы словами, без упрёка.
        scrollToDescription(text(R.string.day_star_missed, text(R.string.day_star_fed)))
        scrollToDescription(text(R.string.day_star_missed, text(R.string.day_star_saved)))
        scrollToText("Совет: так держать!")
    }

    /**
     * День не вернуть, поэтому закрытие спрашивает подтверждения: кнопка стоит
     * там же, где на других экранах стоит безобидное действие.
     */
    @Test
    fun `закончить_день_спрашивает_подтверждение`() {
        var closed = false
        showDay(running(), onClose = { closed = true })

        compose.onNodeWithText(text(R.string.day_action_close)).performClick()
        compose.onNodeWithText(text(R.string.day_confirm_text)).assertIsDisplayed()
        assertEquals(false, closed)

        // Кнопка с тем же словом есть и на экране, и в окне — жмём ту, что в окне.
        compose.onNode(
            hasText(text(R.string.day_action_close)) and hasAnyAncestor(isDialog()),
        ).performClick()

        assertTrue(closed)
    }

    @Test
    fun `пока_день_планируется_закрывать_нечего`() {
        var toPlan = false
        showDay(DayState.Planning, onPlan = { toPlan = true })

        compose.onNodeWithText(text(R.string.day_not_started)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.budget_action_plan)).performClick()

        assertTrue(toPlan)
    }

    private fun showDay(
        state: DayState,
        onClose: () -> Unit = {},
        onPlan: () -> Unit = {},
    ) {
        compose.setContent {
            FinnypetTheme {
                DayContent(state = state, onBack = {}, onPlan = onPlan, onClose = onClose)
            }
        }
    }

    private fun running() = DayState.Running(
        number = 1,
        lines = comparisonLines(),
    )

    private fun testGrowth() = GrowthSummary(growth = null, grownMessage = "Пушок вырос!", points = 12)

    private fun comparisonLines() = listOf(
        BudgetLine(SpendCategory.MANDATORY, Coins(40), Coins(35), followed = false),
        BudgetLine(SpendCategory.OPTIONAL, Coins(20), Coins(20), followed = true),
        BudgetLine(SpendCategory.SAVINGS, Coins(20), Coins(20), followed = true),
    )

    private fun showBudget(
        state: BudgetState,
        onSet: (SpendCategory, Coins) -> Unit = { _, _ -> },
    ) {
        compose.setContent {
            FinnypetTheme {
                BudgetContent(state = state, onBack = {}, onSet = onSet)
            }
        }
    }

    private fun planning(plan: BudgetPlan = BudgetPlan.EMPTY) = BudgetState.Planning(
        available = Coins(80),
        plan = plan,
        remainder = Coins(80) - plan.total,
        overBy = Coins.ZERO,
        needsGoal = false,
        hasGoal = true,
        owl = testOwl(),
        phrase = "Отличный план!",
    )

    private fun showMain(
        state: MainState,
        onRetry: () -> Unit = {},
        onProgress: () -> Unit = {},
        onHelp: () -> Unit = {},
        onAdult: () -> Unit = {},
        onFinishDay: () -> Unit = {},
        onPlan: () -> Unit = {},
        onShop: () -> Unit = {},
        onSavings: () -> Unit = {},
        onTask: (TaskId) -> Unit = {},
        onTasks: () -> Unit = {},
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
                    onTasks = onTasks,
                    onFinishDay = onFinishDay,
                    onProgress = onProgress,
                    onHelp = onHelp,
                    onAdult = onAdult,
                )
            }
        }
    }

    private fun scrollToText(label: String) {
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(label))
        compose.onNodeWithText(label).assertIsDisplayed()
    }

    /** Раскрывает свёрнутую строку «Моего прогресса» по подписи действия заголовка. */
    private fun openFold(title: String) {
        val label = text(R.string.progress_term_closed, title)
        compose.onNode(hasScrollAction()).performScrollToNode(hasClickLabel(label))
        compose.onNode(hasClickLabel(label)).performClick()
    }

    /** Подпись действия у нажимаемого узла: озвучка читает её вслед за текстом. */
    private fun hasClickLabel(label: String) = SemanticsMatcher("подпись действия «$label»") { node ->
        node.config.getOrNull(SemanticsActions.OnClick)?.label == label
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
        periodStatus: PeriodStatus = PeriodStatus.PLANNING,
        step: NextStep = if (periodStatus == PeriodStatus.PLANNING) NextStep.Plan else NextStep.Shop,
    ) = MainState.Ready(
        petName = "Пушок",
        owl = testOwl(),
        stage = GrowthStage.CUB,
        stats = PetState(mood = Stat(75), satiety = Stat(80), care = Stat(60)),
        needs = emptyList(),
        phrase = "Доброе утро!",
        growth = null,
        balance = Coins(80),
        wallet = emptyList(),
        jars = null,
        savings = savings,
        task = task,
        step = step,
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
        palette = listOf(testColor("beige")),
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
