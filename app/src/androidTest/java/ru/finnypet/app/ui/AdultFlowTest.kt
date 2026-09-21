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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ru.finnypet.app.data.local.FinnyDatabase
import ru.finnypet.app.data.repository.PeriodRepositoryImpl
import ru.finnypet.app.data.repository.ProfileRepositoryImpl
import ru.finnypet.app.data.repository.SavingsRepositoryImpl
import ru.finnypet.app.data.repository.SettingsRepositoryImpl
import ru.finnypet.app.data.repository.TaskProgressRepositoryImpl
import ru.finnypet.app.domain.content.ContentOption
import ru.finnypet.app.domain.content.ContentPack
import ru.finnypet.app.domain.content.PetOptions
import ru.finnypet.app.domain.economy.GameBalance
import ru.finnypet.app.domain.economy.GameClock
import ru.finnypet.app.domain.economy.WalletEngine
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.LearningTask
import ru.finnypet.app.domain.model.OutcomeCondition
import ru.finnypet.app.domain.model.PetAppearance
import ru.finnypet.app.domain.model.ProfileId
import ru.finnypet.app.domain.model.TaskId
import ru.finnypet.app.domain.model.TaskOutcome
import ru.finnypet.app.domain.model.TaskStep
import ru.finnypet.app.domain.model.TaskTopic
import ru.finnypet.app.domain.repository.ContentRepository
import ru.finnypet.app.domain.usecase.AwardParentBonus
import ru.finnypet.app.domain.usecase.OpenPeriodIfNeeded
import ru.finnypet.app.ui.screens.adult.AdultState
import ru.finnypet.app.ui.screens.adult.AdultViewModel
import java.io.File

/**
 * Проверяет раздел для взрослого на живой базе (ТЗ 2.5.12).
 *
 * Экранные тесты работают на выдуманном состоянии и не поймают главного:
 * что темы считаются по контент-паку, а бонус проходит через операции и
 * сам себя запирает до следующего дня.
 */
@RunWith(AndroidJUnit4::class)
class AdultFlowTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val balance = GameBalance.PLACEHOLDER
    private val clock = GameClock { FIXED_TIME }

    private val jars = task(TaskId("jars"), TaskTopic.SAVING)
    private val plan = task(TaskId("plan"), TaskTopic.PLANNING)

    private lateinit var storeFile: File
    private lateinit var store: DataStore<Preferences>
    private lateinit var db: FinnyDatabase
    private lateinit var profiles: ProfileRepositoryImpl
    private lateinit var periods: PeriodRepositoryImpl
    private lateinit var savings: SavingsRepositoryImpl
    private lateinit var tasks: TaskProgressRepositoryImpl
    private lateinit var settings: SettingsRepositoryImpl
    private var viewModel: AdultViewModel? = null
    private var profileId: ProfileId = ProfileId("не создан")

    @Before
    fun setUp() = runBlocking {
        storeFile = File(context.cacheDir, "adult-${System.nanoTime()}.preferences_pb")
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
        settings = SettingsRepositoryImpl(store)
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

    /** Тема, к которой ребёнок не прикасался, показывается нулём из двух. */
    @Test
    fun темы_считаются_по_контент_паку() = runBlocking {
        openPeriod()
        tasks.complete(profileId, jars.id, outcomeId = "ok", reward = Coins(15))

        val ready = await { it.topics.isNotEmpty() && it.topics.any { topic -> topic.passed > 0 } }

        val byTopic = ready.topics.associateBy { it.topic }
        assertEquals(1, byTopic.getValue(TaskTopic.SAVING).passed)
        assertEquals(1, byTopic.getValue(TaskTopic.SAVING).total)
        assertEquals(0, byTopic.getValue(TaskTopic.PAYMENTS).passed)
        assertEquals(0, byTopic.getValue(TaskTopic.PAYMENTS).total)
    }

    /** Повторное прохождение не считается вторым: тем всего столько, сколько в паке. */
    @Test
    fun повторное_прохождение_не_увеличивает_счёт_темы() = runBlocking {
        openPeriod()
        tasks.complete(profileId, plan.id, outcomeId = "ok", reward = Coins(15))
        tasks.complete(profileId, plan.id, outcomeId = "ok", reward = Coins.ZERO)

        val ready = await { it.topics.any { topic -> topic.passed > 0 } }

        assertEquals(1, ready.topics.first { it.topic == TaskTopic.PLANNING }.passed)
    }

    @Test
    fun бонус_виден_на_балансе_и_запирается_до_нового_дня() = runBlocking {
        val period = openPeriod()
        val model = viewModel()
        val before = await { it.bonusAvailable }.balance

        model.award()

        // Ждём оба признака разом: баланс и операции приходят из разных
        // запросов к базе, и между ними есть состояние «бонус уже выдан, а
        // баланс ещё прежний» — доли секунды, но тест его ловит.
        val expected = before + balance.parentBonus
        val after = await { !it.bonusAvailable && it.balance == expected }

        assertNotNull(after.awarded)
        assertEquals(expected, periods.balance(period))
    }

    @Test
    fun звук_и_анимации_переключаются_взрослым() = runBlocking {
        openPeriod()
        val model = viewModel()
        assertTrue(await { it.soundEnabled }.animationsEnabled)

        model.setSound(false)
        model.setAnimations(false)

        val off = await { !it.soundEnabled && !it.animationsEnabled }
        assertFalse(off.soundEnabled)
        assertFalse(off.animationsEnabled)
        assertFalse(settings.observeSoundEnabled().first())
    }

    private suspend fun openPeriod() = OpenPeriodIfNeeded(
        periods = periods,
        wallet = WalletEngine(clock),
        balance = balance,
    )(profileId)

    /**
     * Вьюмодель создаётся на первом обращении — уже после подготовки данных,
     * чтобы ожидание не поймало состояние, снятое раньше проверяемого события.
     */
    private fun viewModel(): AdultViewModel = viewModel ?: AdultViewModel(
        profiles = profiles,
        periods = periods,
        savings = savings,
        tasks = tasks,
        settings = settings,
        awardBonus = AwardParentBonus(periods, WalletEngine(clock), balance),
        gameBalance = balance,
        content = content(),
    ).also { viewModel = it }

    private suspend fun await(condition: (AdultState.Ready) -> Boolean): AdultState.Ready {
        val model = viewModel()
        return withTimeout(TIMEOUT_MS) {
            model.state.first { it is AdultState.Ready && condition(it) }
        } as AdultState.Ready
    }

    private fun task(id: TaskId, topic: TaskTopic) = LearningTask(
        id = id,
        topic = topic,
        introKey = "task.intro",
        steps = listOf(TaskStep.Distribute(promptKey = "task.step", budget = Coins(40))),
        outcomes = listOf(
            TaskOutcome(
                id = "ok",
                condition = OutcomeCondition.SavedAtLeast(Coins(10)),
                reward = Coins(15),
                explanationKey = "task.ok",
            ),
            TaskOutcome(
                id = "otherwise",
                condition = OutcomeCondition.Otherwise,
                reward = Coins.ZERO,
                explanationKey = "task.no",
            ),
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
            tasks = listOf(jars, plan),
            glossary = emptyList(),
            texts = mapOf(
                "adult.about.1" to "Игра учит планировать.",
                "balance.credited" to "Начислено {amount} монет.",
            ),
        )
    }

    private companion object {
        const val FIXED_TIME = 1_700_000_000_000L
        const val TIMEOUT_MS = 5_000L
    }
}
