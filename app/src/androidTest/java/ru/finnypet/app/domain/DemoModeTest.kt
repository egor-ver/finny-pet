package ru.finnypet.app.domain

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
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
import ru.finnypet.app.domain.economy.WalletEngine
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.GrowthStage
import ru.finnypet.app.domain.model.PetAppearance
import ru.finnypet.app.domain.model.Profile
import ru.finnypet.app.domain.model.SpendCategory
import ru.finnypet.app.domain.usecase.AfterDemo
import ru.finnypet.app.domain.usecase.CloseDay
import ru.finnypet.app.domain.usecase.ExitDemo
import ru.finnypet.app.domain.usecase.OpenPeriodIfNeeded
import ru.finnypet.app.domain.usecase.PlayDemoDay
import ru.finnypet.app.domain.usecase.StartDemo
import java.io.File

/**
 * Проверяет демонстрационный режим (ТЗ 2.5.13) на живой базе и настоящем
 * контент-паке.
 *
 * Два обещания, которые нельзя проверить глазами: игра ребёнка не страдает
 * ни при входе, ни при сбросе, ни при выходе, — и пять дней подряд доводят
 * питомца до последней стадии. Второе заодно доказывает минимум ТЗ 2.6 про
 * пять игровых периодов и три стадии.
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

        periodEngine = PeriodEngine(
            budget = BudgetEngine(),
            pet = PetStateEngine(balance),
            growth = GrowthEngine(balance),
            balance = balance,
            clock = clock,
        )
        startDemo = StartDemo(profiles, settings)
        exitDemo = ExitDemo(profiles, settings)
        playDay = PlayDemoDay(
            periods = periods,
            savings = savings,
            content = content,
            openPeriod = OpenPeriodIfNeeded(periods, WalletEngine(clock), balance),
            closeDay = CloseDay(periods, profiles, periodEngine, DayRecorderImpl(db)),
            wallet = WalletEngine(clock),
            savingsEngine = SavingsEngine(clock),
            periodEngine = periodEngine,
            recorder = OutcomeRecorderImpl(
                database = db,
                petState = PetStateEngine(balance),
                taskProgress = TaskProgressRepositoryImpl(db.taskProgress(), clock),
            ),
        )
    }

    @After
    fun tearDown() {
        db.close()
        storeFile.delete()
    }

    @Test
    fun запуск_заводит_тестовый_профиль_и_поднимает_флаг() = runBlocking {
        val own = createOwnGame()

        val demo = startDemo()

        assertTrue(demo.isTest)
        assertNotEquals(own.id, demo.id)
        assertEquals(demo.id, profiles.active()?.id)
        assertTrue(settings.demoMode())
    }

    /** Сброс к исходному состоянию: повторный запуск начинает демонстрацию заново. */
    @Test
    fun повторный_запуск_сбрасывает_демонстрацию() = runBlocking {
        createOwnGame()
        val first = startDemo()
        playDay(first.id)
        playDay(first.id)
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
    fun выход_сносит_тестовый_профиль_и_снимает_флаг() = runBlocking {
        createOwnGame()
        val demo = startDemo()

        exitDemo()

        assertNull(profiles.byId(demo.id))
        assertNull(profiles.testProfile())
        assertTrue(!settings.demoMode())
    }

    /** Чистая установка: возвращаться некуда, экран обязан увести на знакомство. */
    @Test
    fun без_своей_игры_выход_ведёт_на_знакомство() = runBlocking {
        startDemo()

        assertEquals(AfterDemo.ONBOARDING, exitDemo())
        assertNull(profiles.active())
    }

    /**
     * Доказательство ТЗ 2.6: пять игровых периодов и три стадии развития.
     * Дни проживаются настоящими движками, а не подставляются в базу.
     */
    @Test
    fun пять_дней_подряд_доводят_питомца_до_последней_стадии() = runBlocking {
        val demo = startDemo()

        repeat(DEMO_DAYS) { playDay(demo.id) }

        // Закрытых дней пять, плюс шестой открыт под продолжение игры.
        assertEquals(DEMO_DAYS, periods.lastClosed(demo.id)?.number)
        assertEquals(DEMO_DAYS + 1, periods.count(demo.id))
        assertEquals(GrowthStage.GROWN, profiles.pet(demo.id)?.growth?.stage)
    }

    @Test
    fun прожитый_день_тратит_и_откладывает() = runBlocking {
        val demo = startDemo()

        playDay(demo.id)

        val first = periods.lastClosed(demo.id)!!
        val fact = periodEngine.factOf(periods.transactions(first.id))
        assertTrue("обязательное не куплено", fact.amountFor(SpendCategory.MANDATORY) > Coins.ZERO)
        assertTrue("в копилку не отложено", savings.activeProgress(demo.id)!!.saved > Coins.ZERO)
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
    }
}
