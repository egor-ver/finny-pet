package ru.finnypet.app.domain

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ru.finnypet.app.data.content.AssetContentRepository
import ru.finnypet.app.data.content.ContentParser
import ru.finnypet.app.data.local.FinnyDatabase
import ru.finnypet.app.data.repository.DayRecorderImpl
import ru.finnypet.app.data.repository.OutcomeRecorderImpl
import ru.finnypet.app.data.repository.PeriodRepositoryImpl
import ru.finnypet.app.data.repository.ProfileRepositoryImpl
import ru.finnypet.app.data.repository.SavingsRepositoryImpl
import ru.finnypet.app.data.repository.SettingsRepositoryImpl
import ru.finnypet.app.data.repository.TaskProgressRepositoryImpl
import ru.finnypet.app.domain.economy.BudgetEngine
import ru.finnypet.app.domain.economy.GameClock
import ru.finnypet.app.domain.economy.GrowthEngine
import ru.finnypet.app.domain.economy.PeriodEngine
import ru.finnypet.app.domain.economy.PetStateEngine
import ru.finnypet.app.domain.economy.SavingsEngine
import ru.finnypet.app.domain.economy.TaskEngine
import ru.finnypet.app.domain.economy.WalletEngine
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.GrowthStage
import ru.finnypet.app.domain.model.PetAppearance
import ru.finnypet.app.domain.model.Profile
import ru.finnypet.app.domain.model.SpendCategory
import ru.finnypet.app.domain.model.Stat
import ru.finnypet.app.domain.model.TaskId
import ru.finnypet.app.domain.model.Transaction
import ru.finnypet.app.domain.model.TransactionType
import ru.finnypet.app.domain.usecase.AfterDemo
import ru.finnypet.app.domain.usecase.CloseDay
import ru.finnypet.app.domain.usecase.ConfirmPlan
import ru.finnypet.app.domain.usecase.ExitDemo
import ru.finnypet.app.domain.usecase.OpenPeriodIfNeeded
import ru.finnypet.app.domain.usecase.PlayDemoDay
import ru.finnypet.app.domain.usecase.StartDemo
import ru.finnypet.app.ui.screens.demo.DemoViewModel
import java.io.File

/**
 * Демонстрационный режим (ТЗ 2.5.13) на живой базе и настоящем контент-паке:
 * игра ребёнка не страдает, а пять дней доводят питомца до последней стадии.
 */
@RunWith(AndroidJUnit4::class)
class DemoModeTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val clock = GameClock { FIXED_TIME }
    private val content = AssetContentRepository(context, ContentParser())
    private val balance = content.pack().balance

    private lateinit var storeFile: File
    private lateinit var store: DataStore<Preferences>
    private lateinit var db: FinnyDatabase
    private lateinit var profiles: ProfileRepositoryImpl
    private lateinit var periods: PeriodRepositoryImpl
    private lateinit var savings: SavingsRepositoryImpl
    private lateinit var settings: SettingsRepositoryImpl
    private lateinit var tasks: TaskProgressRepositoryImpl
    private lateinit var startDemo: StartDemo
    private lateinit var exitDemo: ExitDemo
    private lateinit var playDay: PlayDemoDay
    private lateinit var periodEngine: PeriodEngine

    @Before
    fun setUp() {
        storeFile = File(context.cacheDir, "demo-${System.nanoTime()}.preferences_pb")
        store = PreferenceDataStoreFactory.create { storeFile }
        db = Room.inMemoryDatabaseBuilder(context, FinnyDatabase::class.java).build()
        profiles = ProfileRepositoryImpl(database = db, store = store, balance = balance, clock = clock)
        periods = PeriodRepositoryImpl(
            periods = db.periods(),
            plans = db.budgetPlans(),
            transactions = db.transactions(),
        )
        savings = SavingsRepositoryImpl(goals = db.goalProgress(), transactions = db.transactions())
        settings = SettingsRepositoryImpl(store)
        tasks = TaskProgressRepositoryImpl(db.taskProgress(), clock)

        periodEngine = PeriodEngine(
            budget = BudgetEngine(),
            pet = PetStateEngine(balance),
            growth = GrowthEngine(balance),
            balance = balance,
            clock = clock,
        )
        startDemo = StartDemo(profiles, settings)
        exitDemo = ExitDemo(profiles, settings)
        val recorder = OutcomeRecorderImpl(
            database = db,
            petState = PetStateEngine(balance),
            taskProgress = tasks,
        )
        playDay = PlayDemoDay(
            profiles = profiles,
            periods = periods,
            savings = savings,
            tasks = tasks,
            content = content,
            openPeriod = OpenPeriodIfNeeded(periods, WalletEngine(clock), balance),
            closeDay = CloseDay(periods, profiles, periodEngine, DayRecorderImpl(db)),
            confirmPlan = ConfirmPlan(
                periods = periods,
                savings = savings,
                content = content,
                budget = BudgetEngine(),
                periodEngine = periodEngine,
                savingsEngine = SavingsEngine(clock),
                recorder = recorder,
            ),
            wallet = WalletEngine(clock),
            taskEngine = TaskEngine(clock),
            pet = PetStateEngine(balance),
            recorder = recorder,
        )
    }

    @After
    fun tearDown() {
        db.close()
        storeFile.delete()
    }

    @Test
    fun запуск_заводит_и_делает_активным_тестовый_профиль() = runBlocking {
        val own = createOwnGame()

        val demo = startDemo()

        assertTrue(demo.isTest)
        assertNotEquals(own.id, demo.id)
        assertEquals(demo.id, profiles.active()?.id)
    }

    /** Сброс к исходному состоянию: повторный запуск начинает демонстрацию заново. */
    @Test
    fun повторный_запуск_сбрасывает_демонстрацию() = runBlocking {
        createOwnGame()
        val first = startDemo()
        playDay()
        playDay()
        assertEquals(3, periods.count(first.id))

        val second = startDemo()

        assertNotEquals(first.id, second.id)
        assertNull("прежний тестовый профиль должен быть снесён", profiles.byId(first.id))
        assertEquals(0, periods.count(second.id))
    }

    @Test
    fun игра_ребёнка_переживает_вход_сброс_и_выход() = runBlocking {
        val own = createOwnGame()
        val ownPeriods = periods.count(own.id)

        startDemo()
        startDemo()
        val after = exitDemo()

        assertEquals(AfterDemo.OWN_GAME, after)
        assertEquals(own.id, profiles.active()?.id)
        assertEquals(ownPeriods, periods.count(own.id))
        assertNotNull(profiles.pet(own.id))
    }

    @Test
    fun выход_сносит_тестовый_профиль() = runBlocking {
        createOwnGame()
        val demo = startDemo()

        exitDemo()

        assertNull(profiles.byId(demo.id))
        assertNull(profiles.testProfile())
    }

    /** Двойное «Выйти»: второй вызов метки возврата не найдёт, но игру ребёнка не теряет. */
    @Test
    fun повторный_выход_оставляет_свою_игру() = runBlocking {
        val own = createOwnGame()
        startDemo()
        exitDemo()

        assertEquals(AfterDemo.OWN_GAME, exitDemo())
        assertEquals(own.id, profiles.active()?.id)
    }

    /** Чистая установка: возвращаться некуда, экран обязан увести на знакомство. */
    @Test
    fun без_своей_игры_выход_ведёт_на_знакомство() = runBlocking {
        startDemo()

        assertEquals(AfterDemo.ONBOARDING, exitDemo())
        assertNull(profiles.active())
    }

    @Test
    fun без_своей_игры_вьюмодель_ставит_факт_для_знакомства() = runBlocking {
        startDemo()
        val model = DemoViewModel(profiles, playDay, exitDemo)

        model.exit()

        withTimeout(TIMEOUT_MS) { model.needsOnboarding.first { it } }
        assertNull(profiles.testProfile())
    }

    /**
     * Доказательство ТЗ 2.6: пять игровых периодов и три стадии развития.
     * Дни проживаются настоящими движками, а не подставляются в базу.
     */
    @Test
    fun пять_дней_подряд_доводят_питомца_до_последней_стадии() = runBlocking {
        val demo = startDemo()

        repeat(DEMO_DAYS) { playDay() }

        // Закрытых дней пять, плюс шестой открыт под продолжение игры.
        assertEquals(DEMO_DAYS, periods.lastClosed(demo.id)?.number)
        assertEquals(DEMO_DAYS + 1, periods.count(demo.id))
        assertEquals(GrowthStage.GROWN, profiles.pet(demo.id)?.growth?.stage)
        // Каждый день — новое задание: в разделе взрослого темы не нулевые.
        assertEquals(DEMO_DAYS, tasks.completedIds(demo.id).size)
    }

    /** Раздел 4 плана: второй день с ошибкой — роста нет, сова грустит, утром разбор. */
    @Test
    fun второй_день_демо_с_ошибкой_и_разбором_наутро() = runBlocking {
        val demo = startDemo()
        playDay()
        val afterFirst = profiles.pet(demo.id)!!.growth

        playDay()

        val second = periods.lastClosed(demo.id)!!
        val fact = periodEngine.factOf(periods.transactions(second.id))
        assertEquals(Coins.ZERO, fact.amountFor(SpendCategory.MANDATORY))
        assertEquals(afterFirst, profiles.pet(demo.id)!!.growth)
        assertTrue(profiles.pet(demo.id)!!.state.satiety < Stat(balance.sadThreshold))

        playDay()

        assertTrue(TaskId("review-hungry-owl") in tasks.completedIds(demo.id))
    }

    @Test
    fun прожитый_день_проходит_весь_цикл() = runBlocking {
        val demo = startDemo()

        playDay()

        val first = periods.lastClosed(demo.id)!!
        val transactions = periods.transactions(first.id)
        val fact = periodEngine.factOf(transactions)
        assertTrue("задание не принесло монет", transactions.any { it.type == TransactionType.INCOME_TASK })
        assertTrue("обязательное не куплено", fact.amountFor(SpendCategory.MANDATORY) > Coins.ZERO)
        assertTrue("желаемое не куплено", fact.amountFor(SpendCategory.OPTIONAL) > Coins.ZERO)
        assertTrue("в копилку не отложено", savings.activeProgress(demo.id)!!.saved > Coins.ZERO)
    }

    /** L5, Б8: если первая цель уже куплена, демо копит на следующую, а не переизбирает купленную. */
    @Test
    fun демо_обходит_купленную_цель_и_берёт_следующую() = runBlocking {
        val demo = startDemo()
        val goals = content.pack().goals
        val bought = goals.first()
        val period = OpenPeriodIfNeeded(periods, WalletEngine(clock), balance)(demo.id)
        periods.addTransaction(
            Transaction(
                id = 0,
                periodId = period.id,
                type = TransactionType.GOAL_PURCHASE,
                amount = Coins.ZERO,
                reasonKey = "savings.goal_bought",
                createdAt = FIXED_TIME,
                goalId = bought.id,
            )
        )

        playDay()

        assertNotEquals(bought.id, savings.activeProgress(demo.id)?.goalId)
    }

    /** День уже идёт, а плана нет: демонстрация доигрывает его, а не падает. */
    @Test
    fun идущий_день_без_плана_проживается() = runBlocking {
        val demo = startDemo()
        val period = OpenPeriodIfNeeded(periods, WalletEngine(clock), balance)(demo.id)
        periods.save(periodEngine.confirmPlan(period))

        playDay()

        assertEquals(1, periods.lastClosed(demo.id)?.number)
    }

    /** Даже если активна игра ребёнка, день проживает только тестовый профиль. */
    @Test
    fun прожить_день_не_трогает_игру_ребёнка() = runBlocking {
        val own = createOwnGame()
        val demo = startDemo()
        profiles.setActive(own.id)
        val ownPeriod = periods.current(own.id)!!
        val ownBalance = periods.balance(ownPeriod)

        playDay()

        assertEquals(ownPeriod, periods.current(own.id))
        assertEquals(ownBalance, periods.balance(ownPeriod))
        assertEquals(1, periods.lastClosed(demo.id)?.number)
    }

    private suspend fun createOwnGame(): Profile {
        val own = profiles.create(
            childName = "Егор",
            petName = "Пушок",
            appearance = PetAppearance(bodyId = "owl", colorId = "cream", accessoryId = null),
        )
        OpenPeriodIfNeeded(periods, WalletEngine(clock), balance)(own.id)
        return own
    }

    private companion object {
        const val FIXED_TIME = 1_700_000_000_000L
        const val DEMO_DAYS = 5
        const val TIMEOUT_MS = 5_000L
    }
}
