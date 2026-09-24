package ru.finnypet.app.ui

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import ru.finnypet.app.data.local.FinnyDatabase
import ru.finnypet.app.data.repository.OutcomeRecorderImpl
import ru.finnypet.app.data.repository.PeriodRepositoryImpl
import ru.finnypet.app.data.repository.ProfileRepositoryImpl
import ru.finnypet.app.data.repository.SavingsRepositoryImpl
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
import ru.finnypet.app.domain.economy.WalletEngine
import ru.finnypet.app.domain.model.BudgetPlan
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.LearningTask
import ru.finnypet.app.domain.model.OutcomeCondition
import ru.finnypet.app.domain.model.PetAppearance
import ru.finnypet.app.domain.model.ProfileId
import ru.finnypet.app.domain.model.TaskId
import ru.finnypet.app.domain.model.TaskOutcome
import ru.finnypet.app.domain.model.TaskStep
import ru.finnypet.app.domain.model.TaskTopic
import ru.finnypet.app.domain.model.TaskTopic.PAYMENTS
import ru.finnypet.app.domain.model.TaskTopic.PLANNING
import ru.finnypet.app.domain.model.TaskTopic.SAVING
import ru.finnypet.app.domain.repository.ActionOutcome
import ru.finnypet.app.domain.repository.ContentRepository
import ru.finnypet.app.domain.usecase.OpenPeriodIfNeeded
import ru.finnypet.app.domain.model.TaskCompletion
import ru.finnypet.app.domain.model.Transaction
import ru.finnypet.app.domain.model.TransactionType
import ru.finnypet.app.ui.screens.main.MainState
import ru.finnypet.app.ui.screens.main.MainViewModel
import ru.finnypet.app.ui.screens.main.NextStep
import ru.finnypet.app.ui.screens.tasks.TasksState
import ru.finnypet.app.ui.screens.tasks.TasksViewModel
import java.io.File

/**
 * Список заданий и задание дня на настоящей базе: пометки «пройдено»,
 * порядок тем, лимит наград по операциям и ротация задания дня на главном.
 */
@RunWith(AndroidJUnit4::class)
class TasksFlowTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val balance = GameBalance.PLACEHOLDER.copy(rewardedTasksPerPeriod = 1)
    private val clock = GameClock { FIXED_TIME }

    private val planning = task("jars", PLANNING)
    private val saving = task("story", SAVING)
    private val payments = task("shelf", PAYMENTS)

    private lateinit var storeFile: File
    private lateinit var store: DataStore<Preferences>
    private lateinit var db: FinnyDatabase
    private lateinit var profiles: ProfileRepositoryImpl
    private lateinit var periods: PeriodRepositoryImpl
    private lateinit var progress: TaskProgressRepositoryImpl
    private lateinit var recorder: OutcomeRecorderImpl
    private val viewModels = mutableListOf<androidx.lifecycle.ViewModel>()
    private var profileId: ProfileId = ProfileId("не создан")

    @Before
    fun setUp() = runBlocking {
        storeFile = File(context.cacheDir, "tasks-${System.nanoTime()}.preferences_pb")
        store = PreferenceDataStoreFactory.create { storeFile }
        db = Room.inMemoryDatabaseBuilder(context, FinnyDatabase::class.java).build()
        profiles = ProfileRepositoryImpl(database = db, store = store, balance = balance, clock = clock)
        periods = PeriodRepositoryImpl(periods = db.periods(), plans = db.budgetPlans(), transactions = db.transactions())
        progress = TaskProgressRepositoryImpl(tasks = db.taskProgress(), clock = clock)
        recorder = OutcomeRecorderImpl(database = db, petState = PetStateEngine(balance), taskProgress = progress)
        profileId = profiles.create(
            childName = "Егор",
            petName = "Пушок",
            appearance = PetAppearance(bodyId = "owl", colorId = "cream", accessoryId = null),
        ).id
        val period = OpenPeriodIfNeeded(periods, WalletEngine(clock), balance).invoke(profileId)
        periods.save(periodEngine().confirmPlan(period))
    }

    @After
    fun tearDown() {
        viewModels.forEach { it.viewModelScope.cancel() }
        runBlocking { viewModels.forEach { it.viewModelScope.coroutineContext[Job]?.join() } }
        db.close()
        storeFile.delete()
    }

    @Test
    fun список_в_порядке_тем_с_пометками_и_свободным_лимитом() = runBlocking {
        val vm = tasksViewModel()

        val ready = vm.awaitReady()

        assertEquals(listOf(PLANNING, SAVING, PAYMENTS), ready.groups.map { it.topic })
        assertEquals(listOf("Разложи монеты.", "Сова нашла монеты.", "Собери обед."), ready.groups.flatMap { g -> g.tasks.map { it.intro } })
        assertTrue(ready.groups.flatMap { it.tasks }.none { it.completed })
        assertTrue(ready.rewardAvailable)
        assertEquals(1, ready.rewardLimit)
        assertTrue(ready.canStart)
    }

    /** Пометка «пройдено» и лимит приходят из базы: одно записано прохождение — одно помечено, лимит выбран. */
    @Test
    fun после_прохождения_задание_помечено_а_лимит_выбран() = runBlocking {
        val vm = tasksViewModel()
        vm.awaitReady()

        pass(saving, reward = Coins(15))

        // Пометка и лимит приходят из разных потоков базы и обновляются не
        // одновременно. Ждём оба признака, иначе проверка ловит промежуточное
        // состояние — и тем чаще, чем быстрее устройство.
        val ready = vm.await { state ->
            state.groups.flatMap { g -> g.tasks }.any { it.completed } && !state.rewardAvailable
        }
        assertEquals(listOf("story"), ready.groups.flatMap { g -> g.tasks }.filter { it.completed }.map { it.id.value })
        assertFalse(ready.rewardAvailable)
    }

    /** Задание дня на главном: первое непройденное, после всех — давнее всех. */
    @Test
    fun задание_дня_на_главном_идёт_по_кругу() = runBlocking {
        val main = mainViewModel()
        assertEquals(TaskId("jars"), main.awaitReady().task?.id)

        pass(planning, reward = Coins(15))
        assertEquals(TaskId("story"), main.await { it.task?.id == TaskId("story") }.task?.id)
        assertFalse("лимит дня выбран", main.awaitReady().task!!.rewardAvailable)
        assertFalse(main.awaitReady().task!!.allDone)

        pass(saving, reward = Coins.ZERO)
        pass(payments, reward = Coins.ZERO)
        // Все пройдены: «jars» проходили первым и давнее всех — снова оно.
        val allDone = main.await { it.task?.allDone == true }
        assertEquals(TaskId("jars"), allDone.task?.id)
    }

    /** Подсказка на главном идёт за игрой: награда за задание получена — дальше нужное по плану. */
    @Ignore("Экран переделывается, обновим в коммите 21")
    @Test
    fun следующий_шаг_после_задания_ведёт_к_нужному() = runBlocking {
        val plan = BudgetPlan(mandatory = Coins(10), optional = Coins.ZERO, savings = Coins.ZERO)
        periods.savePlan(periods.current(profileId)!!.id, plan)
        val main = mainViewModel()
        main.await { it.step == NextStep.Task }

        pass(planning, reward = Coins(15))

        assertEquals(NextStep.Shop(Coins(10)), main.await { it.step is NextStep.Shop }.step)
    }

    private suspend fun pass(task: LearningTask, reward: Coins) {
        val period = periods.current(profileId)!!
        recorder.record(
            profileId,
            ActionOutcome(
                transaction = if (reward > Coins.ZERO) {
                    Transaction(
                        id = 0,
                        periodId = period.id,
                        type = TransactionType.INCOME_TASK,
                        amount = reward,
                        reasonKey = "task.done",
                        createdAt = FIXED_TIME,
                    )
                } else {
                    null
                },
                taskCompletion = TaskCompletion(taskId = task.id, outcomeId = "ok", reward = reward),
            ),
        )
    }

    private fun tasksViewModel(): TasksViewModel = TasksViewModel(
        profiles = profiles,
        periods = periods,
        progress = progress,
        openPeriod = OpenPeriodIfNeeded(periods, WalletEngine(clock), balance),
        balance = balance,
        content = content(),
    ).also { viewModels += it }

    private fun mainViewModel(): MainViewModel = MainViewModel(
        profiles = profiles,
        periods = periods,
        savings = SavingsRepositoryImpl(goals = db.goalProgress(), transactions = db.transactions()),
        taskProgress = progress,
        openPeriod = OpenPeriodIfNeeded(periods, WalletEngine(clock), balance),
        periodEngine = periodEngine(),
        petState = PetStateEngine(balance),
        balance = balance,
        content = content(),
    ).also { viewModels += it }

    private suspend fun TasksViewModel.awaitReady(): TasksState.Ready = await { true }

    private suspend fun TasksViewModel.await(condition: (TasksState.Ready) -> Boolean): TasksState.Ready =
        withTimeout(TIMEOUT_MS) { state.first { it is TasksState.Ready && condition(it) } } as TasksState.Ready

    private suspend fun MainViewModel.awaitReady(): MainState.Ready = await { true }

    private suspend fun MainViewModel.await(condition: (MainState.Ready) -> Boolean): MainState.Ready =
        withTimeout(TIMEOUT_MS) { state.first { it is MainState.Ready && condition(it) } } as MainState.Ready

    private fun task(id: String, topic: TaskTopic) = LearningTask(
        id = TaskId(id),
        topic = topic,
        introKey = "task.$id.intro",
        steps = listOf(TaskStep.Distribute(promptKey = "task.$id.step", budget = Coins(40))),
        outcomes = listOf(
            TaskOutcome(id = "ok", condition = OutcomeCondition.SavedAtLeast(Coins(10)), reward = Coins(15), explanationKey = "task.$id.ok"),
            TaskOutcome(id = "otherwise", condition = OutcomeCondition.Otherwise, reward = Coins(5), explanationKey = "task.$id.no"),
        ),
    )

    private fun content(): ContentRepository = object : ContentRepository {
        override fun pack() = ContentPack(
            balance = balance,
            pets = PetOptions(
                bodies = listOf(ContentOption("owl", "pet.body.owl")),
                colors = listOf(ContentOption("cream", "pet.color.cream")),
                accessories = emptyList(),
            ),
            shop = emptyList(),
            goals = emptyList(),
            // Нарочно не в порядке тем: экран обязан выстроить их сам.
            tasks = listOf(saving, payments, planning),
            glossary = emptyList(),
            texts = mapOf(
                "task.jars.intro" to "Разложи монеты.",
                "task.story.intro" to "Сова нашла монеты.",
                "task.shelf.intro" to "Собери обед.",
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
    }
}
