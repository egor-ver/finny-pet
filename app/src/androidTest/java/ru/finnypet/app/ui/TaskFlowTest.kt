package ru.finnypet.app.ui

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ru.finnypet.app.data.local.FinnyDatabase
import ru.finnypet.app.data.repository.OutcomeRecorderImpl
import ru.finnypet.app.data.repository.PeriodRepositoryImpl
import ru.finnypet.app.data.repository.ProfileRepositoryImpl
import ru.finnypet.app.data.repository.TaskProgressRepositoryImpl
import ru.finnypet.app.domain.content.ContentOption
import ru.finnypet.app.domain.content.ContentPack
import ru.finnypet.app.domain.content.PetOptions
import ru.finnypet.app.domain.economy.BudgetEngine
import ru.finnypet.app.domain.economy.GameBalance
import ru.finnypet.app.domain.economy.GameClock
import ru.finnypet.app.domain.economy.GrowthEngine
import ru.finnypet.app.domain.economy.PeriodEngine
import ru.finnypet.app.domain.economy.PetStateEngine
import ru.finnypet.app.domain.economy.TaskEngine
import ru.finnypet.app.domain.economy.WalletEngine
import ru.finnypet.app.domain.model.Change
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.ItemId
import ru.finnypet.app.domain.model.LearningTask
import ru.finnypet.app.domain.model.OutcomeCondition
import ru.finnypet.app.domain.model.PetAppearance
import ru.finnypet.app.domain.model.PeriodStatus
import ru.finnypet.app.domain.model.PetEffect
import ru.finnypet.app.domain.model.PetStatKind
import ru.finnypet.app.domain.model.ProfileId
import ru.finnypet.app.domain.model.ShopItem
import ru.finnypet.app.domain.model.SpendCategory
import ru.finnypet.app.domain.model.Stat
import ru.finnypet.app.domain.model.TaskId
import ru.finnypet.app.domain.model.TaskOption
import ru.finnypet.app.domain.model.TaskOutcome
import ru.finnypet.app.domain.model.TaskStep
import ru.finnypet.app.domain.model.TaskTopic
import ru.finnypet.app.domain.model.TransactionType
import ru.finnypet.app.domain.repository.ContentRepository
import ru.finnypet.app.domain.usecase.OpenPeriodIfNeeded
import ru.finnypet.app.ui.screens.tasks.StepView
import ru.finnypet.app.ui.screens.tasks.TaskStage
import ru.finnypet.app.ui.screens.tasks.TaskState
import ru.finnypet.app.ui.screens.tasks.TaskViewModel
import java.io.File

/**
 * Задание целиком: от вступления до операции награды, изменённого питомца и
 * записи о прохождении (ТЗ 2.5.8, 2.5.4, 2.5.9).
 *
 * Настоящая цепочка — вьюмодель, репозитории, SQLite. Здесь проверяется
 * лимит дня: второе задание в тот же день объясняется и меняет питомца, но
 * монет не даёт; и что три вида шагов доходят до нужного исхода.
 */
@RunWith(AndroidJUnit4::class)
class TaskFlowTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val balance = GameBalance.PLACEHOLDER.copy(rewardedTasksPerPeriod = 1)
    private val clock = GameClock { FIXED_TIME }

    private val food = ShopItem(id = ItemId("food"), titleKey = "shop.food", price = Coins(12), category = SpendCategory.MANDATORY)
    private val toy = ShopItem(id = ItemId("toy"), titleKey = "shop.toy", price = Coins(18), category = SpendCategory.OPTIONAL)
    private val bike = ShopItem(id = ItemId("bike"), titleKey = "shop.bike", price = Coins(25), category = SpendCategory.OPTIONAL)

    /** «История»: сохранить или потратить. */
    private val story = LearningTask(
        id = TaskId("story"),
        topic = TaskTopic.SAVING,
        introKey = "task.story.intro",
        steps = listOf(
            TaskStep.Choice(
                promptKey = "task.story.step",
                options = listOf(TaskOption("save", "task.story.save"), TaskOption("spend", "task.story.spend")),
            )
        ),
        outcomes = listOf(
            TaskOutcome(
                id = "saved",
                condition = OutcomeCondition.OptionChosen("save"),
                reward = Coins(15),
                explanationKey = "task.story.saved",
                effects = listOf(PetEffect(PetStatKind.MOOD, 5)),
                correct = true,
            ),
            TaskOutcome(id = "otherwise", condition = OutcomeCondition.Otherwise, reward = Coins(5), explanationKey = "task.story.spent"),
        ),
    )

    /** «Три банки»: отложить хотя бы десять. */
    private val jars = LearningTask(
        id = TaskId("jars"),
        topic = TaskTopic.PLANNING,
        introKey = "task.jars.intro",
        steps = listOf(TaskStep.Distribute(promptKey = "task.jars.step", budget = Coins(40))),
        outcomes = listOf(
            TaskOutcome(id = "saved", condition = OutcomeCondition.SavedAtLeast(Coins(10)), reward = Coins(15), explanationKey = "task.jars.saved", correct = true),
            TaskOutcome(id = "otherwise", condition = OutcomeCondition.Otherwise, reward = Coins(5), explanationKey = "task.jars.spent"),
        ),
    )

    /** Сценарий из двух шагов: история, потом полка — исход по обоим ответам. */
    private val scenario = LearningTask(
        id = TaskId("scenario"),
        topic = TaskTopic.PAYMENTS,
        introKey = "task.scenario.intro",
        steps = listOf(
            TaskStep.Choice(
                promptKey = "task.scenario.step1",
                options = listOf(TaskOption("list", "task.scenario.list"), TaskOption("rush", "task.scenario.rush")),
            ),
            TaskStep.PickItems(promptKey = "task.scenario.step2", itemIds = listOf(food.id, toy.id), budget = Coins(30)),
        ),
        outcomes = listOf(
            TaskOutcome(id = "careful", condition = OutcomeCondition.OptionChosen("list"), reward = Coins(15), explanationKey = "task.scenario.careful", correct = true),
            TaskOutcome(id = "otherwise", condition = OutcomeCondition.Otherwise, reward = Coins(5), explanationKey = "task.scenario.rushed"),
        ),
    )

    /** «Полка»: уложиться в тридцать. */
    private val shelf = LearningTask(
        id = TaskId("shelf"),
        topic = TaskTopic.PAYMENTS,
        introKey = "task.shelf.intro",
        steps = listOf(TaskStep.PickItems(promptKey = "task.shelf.step", itemIds = listOf(food.id, toy.id, bike.id), budget = Coins(30))),
        outcomes = listOf(
            TaskOutcome(id = "thrifty", condition = OutcomeCondition.SpentAtMost(Coins(20)), reward = Coins(15), explanationKey = "task.shelf.thrifty", correct = true),
            TaskOutcome(id = "otherwise", condition = OutcomeCondition.Otherwise, reward = Coins(5), explanationKey = "task.shelf.full"),
        ),
    )

    private lateinit var storeFile: File
    private lateinit var store: DataStore<Preferences>
    private lateinit var db: FinnyDatabase
    private lateinit var profiles: ProfileRepositoryImpl
    private lateinit var periods: PeriodRepositoryImpl
    private lateinit var progress: TaskProgressRepositoryImpl
    private val viewModels = mutableListOf<TaskViewModel>()
    private var profileId: ProfileId = ProfileId("не создан")

    @Before
    fun setUp() = runBlocking {
        storeFile = File(context.cacheDir, "task-${System.nanoTime()}.preferences_pb")
        store = PreferenceDataStoreFactory.create { storeFile }
        db = Room.inMemoryDatabaseBuilder(context, FinnyDatabase::class.java).build()
        profiles = ProfileRepositoryImpl(database = db, store = store, balance = balance, clock = clock)
        periods = PeriodRepositoryImpl(periods = db.periods(), plans = db.budgetPlans(), transactions = db.transactions())
        progress = TaskProgressRepositoryImpl(tasks = db.taskProgress(), clock = clock)
        profileId = profiles.create(
            childName = "Егор",
            petName = "Пушок",
            appearance = PetAppearance(bodyId = "owl", colorId = "cream", accessoryId = null),
        ).id
    }

    @After
    fun tearDown() {
        viewModels.forEach { it.viewModelScope.cancel() }
        runBlocking { viewModels.forEach { it.viewModelScope.coroutineContext[Job]?.join() } }
        db.close()
        storeFile.delete()
    }

    @Test
    fun вступление_знает_тему_награду_и_право_на_монеты() = runBlocking {
        startDay()
        val vm = viewModel(story)

        val ready = vm.awaitReady()

        assertEquals(TaskTopic.SAVING, ready.topic)
        assertEquals("Сова нашла монеты. Что с ними делать?", ready.intro)
        assertEquals(Coins(15), ready.maxReward)
        assertTrue(ready.rewardAvailable)
        assertEquals(TaskStage.Intro, ready.stage)
    }

    @Test
    fun выбор_варианта_даёт_исход_награду_питомца_и_запись() = runBlocking {
        startDay()
        val vm = viewModel(story)
        val before = vm.awaitReady()

        vm.start()
        val step = vm.await { it.stage is TaskStage.Step }.stage as TaskStage.Step
        assertEquals(2, (step.step as StepView.Choice).options.size)
        assertFalse(step.step.canProceed)

        vm.choose("save")
        assertTrue((vm.await { (it.stage as? TaskStage.Step)?.step?.canProceed == true }.stage as TaskStage.Step).step.canProceed)
        vm.next()

        val done = vm.await { it.stage is TaskStage.Done }.stage as TaskStage.Done
        assertEquals("Молодец, отложил! +15", done.outcome.text)
        assertEquals(Coins(15), done.outcome.reward)
        assertTrue(done.outcome.rewardable)
        val initial = Stat(balance.initialStat)
        assertEquals(listOf(Change.PetStat(PetStatKind.MOOD, from = initial, to = initial + 5)), done.outcome.changes)

        val period = periods.current(profileId)!!
        val reward = periods.transactions(period.id).single { it.type == TransactionType.INCOME_TASK }
        assertEquals(Coins(15), reward.amount)
        assertEquals(balance.startingBalance + balance.periodIncome + Coins(15), periods.balance(period))
        assertEquals(setOf(story.id), progress.completedIds(profileId))
        assertEquals(initial + 5, profiles.pet(profileId)!!.state.mood)
        assertFalse("лимит дня выбран", vm.await { !it.rewardAvailable }.rewardAvailable)
        assertEquals(TaskStage.Intro, before.stage)
    }

    /** Лимит дня: второе задание объясняется и меняет питомца, но монет не даёт. */
    @Test
    fun второе_задание_в_тот_же_день_без_монет() = runBlocking {
        startDay()
        pass(viewModel(story)) { it.choose("save") }
        val second = viewModel(story)
        assertFalse(second.awaitReady().rewardAvailable)

        val done = pass(second) { it.choose("save") }

        assertFalse(done.outcome.rewardable)
        assertEquals(Coins.ZERO, done.outcome.reward)
        assertEquals("Молодец, отложил! +0", done.outcome.text)
        assertEquals(1, done.outcome.changes.size)
        val period = periods.current(profileId)!!
        assertEquals(1, periods.transactions(period.id).count { it.type == TransactionType.INCOME_TASK })
        assertEquals(2, progress.observeCompleted(profileId).first().size)
    }

    @Test
    fun три_банки_с_накоплением_доходят_до_своего_исхода() = runBlocking {
        startDay()
        val vm = viewModel(jars)
        vm.start()
        vm.await { it.stage is TaskStage.Step }

        repeat(3) { vm.add(SpendCategory.MANDATORY) }
        repeat(2) { vm.add(SpendCategory.SAVINGS) }
        val step = vm.await { ((it.stage as? TaskStage.Step)?.step as? StepView.Distribute)?.plan?.total == Coins(25) }
        assertEquals(Coins(15), ((step.stage as TaskStage.Step).step as StepView.Distribute).remainder)
        vm.next()

        val done = vm.await { it.stage is TaskStage.Done }.stage as TaskStage.Done
        assertEquals("Отложил десять — цель ближе.", done.outcome.text)
        assertEquals(Coins(15), done.outcome.reward)
    }

    /** Два шага: ответ первого сохраняется, второй начинается с чистого черновика, исход — по обоим. */
    @Test
    fun сценарий_из_двух_шагов_проходится_по_очереди() = runBlocking {
        startDay()
        val vm = viewModel(scenario)
        vm.start()
        val first = vm.await { it.stage is TaskStage.Step }.stage as TaskStage.Step
        assertEquals(0, first.index)
        assertEquals(2, first.total)

        vm.choose("list")
        vm.await { (it.stage as? TaskStage.Step)?.step?.canProceed == true }
        vm.next()

        val second = vm.await { (it.stage as? TaskStage.Step)?.index == 1 }.stage as TaskStage.Step
        assertTrue(second.step is StepView.Pick)
        assertEquals(emptySet<String>(), (second.step as StepView.Pick).picked)
        // Ответ первого шага не потерян: «дальше» на втором шаге разбирает оба.
        vm.toggle(food.id.value)
        vm.await { pick(it)?.picked == setOf(food.id.value) }
        vm.next()

        val done = vm.await { it.stage is TaskStage.Done }.stage as TaskStage.Done
        assertEquals("Составил список — молодец.", done.outcome.text)
        assertEquals(Coins(15), done.outcome.reward)
    }

    @Test
    fun пустой_план_дальше_не_пускает() = runBlocking {
        startDay()
        val vm = viewModel(jars)
        vm.start()
        val step = vm.await { it.stage is TaskStage.Step }.stage as TaskStage.Step

        assertFalse(step.step.canProceed)
        vm.next()

        assertTrue(settle(vm).stage is TaskStage.Step)
    }

    @Test
    fun полка_не_даёт_взять_сверх_бюджета_и_считает_корзину() = runBlocking {
        startDay()
        val vm = viewModel(shelf)
        vm.start()
        vm.await { it.stage is TaskStage.Step }

        vm.toggle(toy.id.value)
        val picked = vm.await { pick(it)?.picked == setOf(toy.id.value) }
        assertEquals(Coins(18), pick(picked)!!.spent)
        assertFalse("велосипед за 25 к мячику за 18 не влезает в 30", pick(picked)!!.canToggle(bike.id.value))

        vm.toggle(bike.id.value)
        assertEquals(setOf(toy.id.value), pick(settle(vm))!!.picked)

        // Каша за 12 к мячику — ровно 30, влезает; но это больше двадцати.
        vm.toggle(food.id.value)
        assertEquals(Coins(30), pick(vm.await { pick(it)?.picked == setOf(toy.id.value, food.id.value) })!!.spent)
        vm.next()

        val done = vm.await { it.stage is TaskStage.Done }.stage as TaskStage.Done
        assertEquals("Потратил больше двадцати — в другой раз посмотри на цены.", done.outcome.text)
        // R8: объяснение есть, монет за неверный ответ нет, хоть в исходе и записана награда.
        assertEquals(Coins.ZERO, done.outcome.reward)
    }

    @Test
    fun экономная_корзина_доходит_до_своего_исхода() = runBlocking {
        startDay()
        val vm = viewModel(shelf)
        vm.start()
        vm.await { it.stage is TaskStage.Step }
        vm.toggle(food.id.value)
        vm.await { pick(it)?.picked == setOf(food.id.value) }

        vm.next()

        val done = vm.await { it.stage is TaskStage.Done }.stage as TaskStage.Done
        assertEquals("Уложился в двадцать!", done.outcome.text)
        assertEquals(Coins(15), done.outcome.reward)
    }

    /** R7: задания доступны до плана — награда входит в сумму, которую ребёнок распределит. */
    @Test
    fun пока_день_планируется_задание_проходится_и_платит() = runBlocking {
        val vm = viewModel(story)
        vm.awaitReady()

        vm.start()
        vm.choose("save")
        vm.await { (it.stage as? TaskStage.Step)?.step?.canProceed == true }
        vm.next()

        vm.await { it.stage is TaskStage.Done }
        val period = periods.current(profileId)!!
        assertEquals(PeriodStatus.PLANNING, period.status)
        assertEquals(1, periods.transactions(period.id).count { it.type == TransactionType.INCOME_TASK })
    }

    @Test
    fun ссылка_на_несуществующее_задание_не_роняет_экран() = runBlocking {
        val vm = viewModel(TaskId("нет-такого"))

        val state = withTimeout(TIMEOUT_MS) { vm.state.first { it !is TaskState.Loading } }

        assertEquals(TaskState.Missing, state)
    }

    /** Два быстрых «Ответить» на последнем шаге дают одну награду. */
    @Test
    fun двойной_ответ_награждает_один_раз() = runBlocking {
        startDay()
        val vm = viewModel(story)
        vm.start()
        vm.await { it.stage is TaskStage.Step }
        vm.choose("save")
        vm.await { (it.stage as? TaskStage.Step)?.step?.canProceed == true }

        vm.next()
        vm.next()

        vm.await { it.stage is TaskStage.Done }
        val period = periods.current(profileId)!!
        assertEquals(1, periods.transactions(period.id).count { it.type == TransactionType.INCOME_TASK })
        assertEquals(1, progress.observeCompleted(profileId).first().size)
    }

    private suspend fun pass(vm: TaskViewModel, answer: (TaskViewModel) -> Unit): TaskStage.Done {
        vm.awaitReady()
        vm.start()
        vm.await { it.stage is TaskStage.Step }
        answer(vm)
        vm.await { (it.stage as? TaskStage.Step)?.step?.canProceed == true }
        vm.next()
        return vm.await { it.stage is TaskStage.Done }.stage as TaskStage.Done
    }

    private fun pick(state: TaskState.Ready): StepView.Pick? = (state.stage as? TaskStage.Step)?.step as? StepView.Pick

    private fun viewModel(task: LearningTask): TaskViewModel = viewModel(task.id)

    private fun viewModel(taskId: TaskId): TaskViewModel = TaskViewModel(
        savedState = SavedStateHandle(mapOf("taskId" to taskId.value)),
        profiles = profiles,
        periods = periods,
        openPeriod = OpenPeriodIfNeeded(periods = periods, wallet = WalletEngine(clock), balance = balance),
        taskProgress = progress,
        engine = TaskEngine(clock),
        budget = BudgetEngine(),
        recorder = OutcomeRecorderImpl(database = db, petState = PetStateEngine(balance), taskProgress = progress),
        balance = balance,
        content = content(),
    ).also { viewModels += it }

    /** День открывается и подтверждается здесь: вьюмодель может появиться позже. */
    private suspend fun startDay() {
        val period = OpenPeriodIfNeeded(periods, WalletEngine(clock), balance).invoke(profileId)
        periods.save(periodEngine().confirmPlan(period))
    }

    private suspend fun TaskViewModel.awaitReady(): TaskState.Ready = await { true }

    private suspend fun TaskViewModel.await(condition: (TaskState.Ready) -> Boolean): TaskState.Ready =
        withTimeout(TIMEOUT_MS) {
            state.first { it is TaskState.Ready && condition(it) }
        } as TaskState.Ready

    /** Проверка «ничего не случилось» — единственная, где приходится ждать по часам. */
    private suspend fun settle(vm: TaskViewModel): TaskState.Ready {
        delay(SETTLE_MS)
        return vm.awaitReady()
    }

    private fun content(): ContentRepository = object : ContentRepository {
        override fun pack() = ContentPack(
            balance = balance,
            pets = PetOptions(
                bodies = listOf(ContentOption("owl", "pet.body.owl")),
                colors = listOf(testColor()),
                accessories = emptyList(),
            ),
            shop = listOf(food, toy, bike),
            goals = emptyList(),
            tasks = listOf(story, jars, shelf, scenario),
            glossary = emptyList(),
            texts = mapOf(
                "shop.food" to "Каша",
                "shop.toy" to "Мячик",
                "shop.bike" to "Велосипед",
                "task.story.intro" to "Сова нашла монеты. Что с ними делать?",
                "task.story.step" to "Отложить или потратить?",
                "task.story.save" to "Отложить",
                "task.story.spend" to "Потратить",
                "task.story.saved" to "Молодец, отложил! +{reward}",
                "task.story.spent" to "Потратил всё.",
                "task.jars.intro" to "Разложи сорок монет.",
                "task.jars.step" to "Сколько куда?",
                "task.jars.saved" to "Отложил десять — цель ближе.",
                "task.jars.spent" to "Всё ушло на покупки.",
                "task.shelf.intro" to "Собери обед.",
                "task.shelf.step" to "Что возьмём?",
                "task.shelf.thrifty" to "Уложился в двадцать!",
                "task.shelf.full" to "Потратил больше двадцати — в другой раз посмотри на цены.",
                "task.scenario.intro" to "Идём в магазин.",
                "task.scenario.step1" to "Список или как получится?",
                "task.scenario.list" to "Составить список",
                "task.scenario.rush" to "Как получится",
                "task.scenario.step2" to "Что берём?",
                "task.scenario.careful" to "Составил список — молодец.",
                "task.scenario.rushed" to "Без списка вышло дороже.",
            ),
        )
    }

    private fun periodEngine() = PeriodEngine(
        budget = BudgetEngine(),
        pet = PetStateEngine(balance),
        growth = GrowthEngine(balance),
        balance = balance,
        clock = clock,
    )

    private companion object {
        const val FIXED_TIME = 1_700_000_000_000L
        const val TIMEOUT_MS = 5_000L
        const val SETTLE_MS = 300L
    }
}
