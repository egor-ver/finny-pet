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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ru.finnypet.app.data.local.FinnyDatabase
import ru.finnypet.app.data.repository.DayRecorderImpl
import ru.finnypet.app.data.repository.PeriodRepositoryImpl
import ru.finnypet.app.data.repository.ProfileRepositoryImpl
import ru.finnypet.app.data.repository.SavingsRepositoryImpl
import ru.finnypet.app.data.repository.TaskProgressRepositoryImpl
import ru.finnypet.app.domain.content.ContentOption
import ru.finnypet.app.domain.content.ContentPack
import ru.finnypet.app.domain.content.GlossaryTerm
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
import ru.finnypet.app.domain.model.Goal
import ru.finnypet.app.domain.model.GoalId
import ru.finnypet.app.domain.model.GoalProgress
import ru.finnypet.app.domain.model.LearningTask
import ru.finnypet.app.domain.model.OutcomeCondition
import ru.finnypet.app.domain.model.PetAppearance
import ru.finnypet.app.domain.model.ProfileId
import ru.finnypet.app.domain.model.SpendCategory
import ru.finnypet.app.domain.model.TaskId
import ru.finnypet.app.domain.model.TaskOutcome
import ru.finnypet.app.domain.model.TaskStep
import ru.finnypet.app.domain.model.TaskTopic
import ru.finnypet.app.domain.repository.ContentRepository
import ru.finnypet.app.domain.usecase.CloseDay
import ru.finnypet.app.domain.usecase.OpenPeriodIfNeeded
import ru.finnypet.app.ui.screens.progress.ProgressState
import ru.finnypet.app.ui.screens.progress.ProgressViewModel
import java.io.File

/**
 * Проверяет раздел прогресса на живой базе (ТЗ 2.5.11).
 *
 * Главное здесь — итоги последнего дня: они не хранятся, а пересчитываются из
 * плана и операций закрытого дня. Экранные тесты этого не поймают, они
 * работают на выдуманном состоянии.
 */
@RunWith(AndroidJUnit4::class)
class ProgressFlowTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val balance = GameBalance.PLACEHOLDER
    private val clock = GameClock { FIXED_TIME }

    private val bike = Goal(id = GoalId("bike"), titleKey = "goal.bike", price = Coins(120))

    private val task = LearningTask(
        id = TaskId("jars"),
        topic = TaskTopic.SAVING,
        introKey = "task.jars.intro",
        steps = listOf(TaskStep.Distribute(promptKey = "task.jars.step", budget = Coins(40))),
        outcomes = listOf(
            TaskOutcome(
                id = "ok",
                condition = OutcomeCondition.SavedAtLeast(Coins(10)),
                reward = Coins(15),
                explanationKey = "task.jars.ok",
                correct = true,
            ),
            TaskOutcome(
                id = "otherwise",
                condition = OutcomeCondition.Otherwise,
                reward = Coins.ZERO,
                explanationKey = "task.jars.no",
            ),
        ),
    )

    private lateinit var storeFile: File
    private lateinit var store: DataStore<Preferences>
    private lateinit var db: FinnyDatabase
    private lateinit var profiles: ProfileRepositoryImpl
    private lateinit var periods: PeriodRepositoryImpl
    private lateinit var savings: SavingsRepositoryImpl
    private lateinit var tasks: TaskProgressRepositoryImpl
    private var viewModel: ProgressViewModel? = null
    private var profileId: ProfileId = ProfileId("не создан")

    @Before
    fun setUp() = runBlocking {
        storeFile = File(context.cacheDir, "progress-${System.nanoTime()}.preferences_pb")
        store = PreferenceDataStoreFactory.create { storeFile }
        db = Room.inMemoryDatabaseBuilder(context, FinnyDatabase::class.java).build()
        profiles = ProfileRepositoryImpl(database = db, store = store, balance = balance, clock = clock)
        periods = PeriodRepositoryImpl(
            periods = db.periods(),
            plans = db.budgetPlans(),
            transactions = db.transactions(),
        )
        savings = SavingsRepositoryImpl(goals = db.goalProgress(), transactions = db.transactions())
        tasks = TaskProgressRepositoryImpl(db.taskProgress(), clock)
        profileId = profiles.create(
            childName = "Егор",
            petName = "Пушок",
            appearance = PetAppearance(bodyId = "owl", colorId = "cream", accessoryId = null),
        ).id
    }

    @After
    fun tearDown() {
        val model = viewModel
        if (model != null) {
            model.viewModelScope.cancel()
            runBlocking { model.viewModelScope.coroutineContext[Job]?.join() }
        }
        db.close()
        storeFile.delete()
    }

    /** День уже открыт к моменту создания вьюмодели — итогов всё равно нет. */
    @Test
    fun пока_день_не_закончен_итогов_нет() = runBlocking {
        openPeriod()(profileId)

        val ready = await { true }

        assertNull(ready.lastDay)
        assertNull(ready.goal)
        assertEquals(emptyList<Any>(), ready.passed)
    }

    /** Итоги пересчитываются из плана и операций закрытого дня. */
    @Test
    fun после_закрытия_дня_видны_его_итоги() = runBlocking {
        closeFirstDay()

        val ready = await { it.lastDay != null }

        val lastDay = ready.lastDay!!
        assertEquals(1, lastDay.number)
        assertEquals(Coins(80), lastDay.planTotal)
        assertEquals(3, lastDay.lines.size)
        assertEquals(Coins(40), lastDay.lines.first { it.category == SpendCategory.MANDATORY }.planned)
    }

    @Test
    fun пройденное_задание_попадает_в_список() = runBlocking {
        tasks.complete(profileId, task.id, outcomeId = "ok", reward = Coins(15))

        val ready = await { it.passed.isNotEmpty() }

        assertEquals(1, ready.passed.size)
        assertEquals(task.id, ready.passed.first().id)
        assertEquals("Сова нашла монеты.", ready.passed.first().title)
        assertEquals(TaskTopic.SAVING, ready.passed.first().topic)
        assertEquals(Coins(15), ready.passed.first().reward)
    }

    /**
     * Пройти задание можно не раз, а монеты дают за одно в день: повторы
     * встали бы в список строками с нулём рядом с первой.
     */
    @Test
    fun повторное_прохождение_не_плодит_строк() = runBlocking {
        tasks.complete(profileId, task.id, outcomeId = "ok", reward = Coins(15))
        tasks.complete(profileId, task.id, outcomeId = "ok", reward = Coins.ZERO)

        val ready = await { it.passed.isNotEmpty() }

        assertEquals(1, ready.passed.size)
        assertEquals(Coins(15), ready.passed.first().reward)
    }

    @Test
    fun выбранная_цель_показывается_с_прогрессом() = runBlocking {
        savings.setActive(profileId, GoalProgress(goalId = bike.id, saved = Coins(30), isActive = true))

        val ready = await { it.goal != null }

        assertEquals("Самокат мечты", ready.goal!!.title)
        assertEquals(Coins(30), ready.goal!!.saved)
        assertEquals(Coins(120), ready.goal!!.price)
    }

    /** Справочник приходит из контент-пака и не зависит от прогресса. */
    @Test
    fun справочник_показывается_всегда() = runBlocking {
        val ready = await { true }

        assertEquals(listOf("budget"), ready.terms.map { it.id })
        assertEquals(listOf("Бюджет"), ready.terms.map { it.title })
        assertEquals("Это сколько у тебя есть монеток.", ready.terms.first().body)
    }

    private suspend fun closeFirstDay() {
        val period = openPeriod()(profileId)
        periods.savePlan(period.id, BudgetPlan(Coins(40), Coins(20), Coins(20)))
        periods.save(periodEngine().confirmPlan(period))
        CloseDay(
            periods = periods,
            profiles = profiles,
            engine = periodEngine(),
            recorder = DayRecorderImpl(db),
        )(profileId)
    }

    /**
     * Вьюмодель создаётся здесь, а не в setUp: тогда её первое состояние
     * собрано уже по подготовленным данным, и ожидание не может поймать
     * состояние, снятое раньше проверяемого события.
     */
    private suspend fun await(condition: (ProgressState.Ready) -> Boolean): ProgressState.Ready {
        val model = viewModel ?: ProgressViewModel(
            profiles = profiles,
            periods = periods,
            savings = savings,
            tasks = tasks,
            budget = BudgetEngine(),
            periodEngine = periodEngine(),
            content = content(),
        ).also { viewModel = it }
        return withTimeout(TIMEOUT_MS) {
            model.state.first { it is ProgressState.Ready && condition(it) }
        } as ProgressState.Ready
    }

    private fun openPeriod() = OpenPeriodIfNeeded(
        periods = periods,
        wallet = WalletEngine(clock),
        balance = balance,
    )

    private fun periodEngine() = PeriodEngine(
        budget = BudgetEngine(),
        pet = PetStateEngine(balance),
        growth = GrowthEngine(balance),
        balance = balance,
        clock = clock,
    )

    private fun content(): ContentRepository = object : ContentRepository {
        override fun pack() = ContentPack(
            balance = balance,
            pets = PetOptions(
                bodies = listOf(ContentOption("owl", "pet.body.owl")),
                colors = listOf(testColor()),
                accessories = emptyList(),
            ),
            shop = emptyList(),
            goals = listOf(bike),
            tasks = listOf(task),
            glossary = listOf(GlossaryTerm("budget", "glossary.budget.title", "glossary.budget.body")),
            texts = mapOf(
                "goal.bike" to "Самокат мечты",
                "task.jars.intro" to "Сова нашла монеты.",
                "glossary.budget.title" to "Бюджет",
                "glossary.budget.body" to "Это сколько у тебя есть монеток.",
            ),
        )
    }

    private companion object {
        const val FIXED_TIME = 1_700_000_000_000L
        const val TIMEOUT_MS = 5_000L
    }
}
