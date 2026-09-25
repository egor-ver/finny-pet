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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ru.finnypet.app.data.content.AssetContentRepository
import ru.finnypet.app.data.content.ContentParser
import ru.finnypet.app.data.local.FinnyDatabase
import ru.finnypet.app.data.repository.OutcomeRecorderImpl
import ru.finnypet.app.data.repository.PeriodRepositoryImpl
import ru.finnypet.app.data.repository.ProfileRepositoryImpl
import ru.finnypet.app.data.repository.SavingsRepositoryImpl
import ru.finnypet.app.data.repository.TaskProgressRepositoryImpl
import ru.finnypet.app.domain.economy.BudgetEngine
import ru.finnypet.app.domain.economy.GameBalance
import ru.finnypet.app.domain.economy.GameClock
import ru.finnypet.app.domain.economy.GrowthEngine
import ru.finnypet.app.domain.economy.PeriodEngine
import ru.finnypet.app.domain.economy.PetStateEngine
import ru.finnypet.app.domain.economy.SavingsEngine
import ru.finnypet.app.domain.economy.WalletEngine
import ru.finnypet.app.domain.model.BudgetPlan
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.GamePeriod
import ru.finnypet.app.domain.model.PeriodStatus
import ru.finnypet.app.domain.model.PetAppearance
import ru.finnypet.app.domain.model.ProfileId
import ru.finnypet.app.domain.model.TransactionType
import ru.finnypet.app.domain.usecase.AwardParentBonus
import ru.finnypet.app.domain.usecase.ConfirmPlan
import ru.finnypet.app.domain.usecase.OpenPeriodIfNeeded
import java.io.File

/**
 * Подтверждение плана на живой базе (R5, R6, AD-5): доля копилки уходит
 * на цель вместе со сменой статуса дня, а план раскладывает весь кошелёк.
 */
@RunWith(AndroidJUnit4::class)
class ConfirmPlanTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val balance = GameBalance.PLACEHOLDER
    private val clock = GameClock { FIXED_TIME }
    private val content = AssetContentRepository(context, ContentParser())

    private lateinit var storeFile: File
    private lateinit var store: DataStore<Preferences>
    private lateinit var db: FinnyDatabase
    private lateinit var periods: PeriodRepositoryImpl
    private lateinit var savings: SavingsRepositoryImpl
    private lateinit var confirm: ConfirmPlan
    private lateinit var period: GamePeriod
    private var profileId: ProfileId = ProfileId("не создан")

    @Before
    fun setUp() = runBlocking {
        storeFile = File(context.cacheDir, "confirm-${System.nanoTime()}.preferences_pb")
        store = PreferenceDataStoreFactory.create { storeFile }
        db = Room.inMemoryDatabaseBuilder(context, FinnyDatabase::class.java).build()
        val profiles = ProfileRepositoryImpl(database = db, store = store, balance = balance, clock = clock)
        periods = PeriodRepositoryImpl(periods = db.periods(), plans = db.budgetPlans(), transactions = db.transactions())
        savings = SavingsRepositoryImpl(goals = db.goalProgress(), transactions = db.transactions())
        profileId = profiles.create(
            childName = "Егор",
            petName = "Пушок",
            appearance = PetAppearance(bodyId = "owl", colorId = "cream", accessoryId = null),
        ).id
        val recorder = OutcomeRecorderImpl(
            database = db,
            petState = PetStateEngine(balance),
            taskProgress = TaskProgressRepositoryImpl(db.taskProgress(), clock),
        )
        val openPeriod = OpenPeriodIfNeeded(
            periods, WalletEngine(clock), balance, content.pack().events, recorder, clock,
        )
        confirm = ConfirmPlan(
            openPeriod = openPeriod,
            periods = periods,
            savings = savings,
            content = content,
            budget = BudgetEngine(),
            periodEngine = PeriodEngine(
                budget = BudgetEngine(),
                pet = PetStateEngine(balance),
                growth = GrowthEngine(balance),
                balance = balance,
                clock = clock,
            ),
            savingsEngine = SavingsEngine(clock),
            recorder = recorder,
        )
        period = openPeriod(profileId)
    }

    @After
    fun tearDown() {
        db.close()
        storeFile.delete()
    }

    /** Раздел 4 плана, день 2: 8 монет уходят на цель сразу при подтверждении. */
    @Test
    fun доля_копилки_уходит_на_цель_при_подтверждении() = runBlocking {
        chooseGoal()
        val wallet = periods.balance(period)
        periods.savePlan(period.id, BudgetPlan(mandatory = Coins(3), optional = Coins(24), savings = Coins(8)))

        assertTrue(confirm(profileId))

        assertEquals(PeriodStatus.RUNNING, periods.current(profileId)!!.status)
        assertEquals(wallet - Coins(8), periods.balance(period))
        assertEquals(Coins(8), savings.activeProgress(profileId)!!.saved)
        assertEquals(1, periods.transactions(period.id).count { it.type == TransactionType.SAVINGS_DEPOSIT })
    }

    @Test
    fun без_цели_копилку_не_подтвердить() = runBlocking {
        periods.savePlan(period.id, BudgetPlan(mandatory = Coins(10), optional = Coins.ZERO, savings = Coins(8)))

        assertFalse(confirm(profileId))

        assertEquals(PeriodStatus.PLANNING, periods.current(profileId)!!.status)
        assertTrue(periods.transactions(period.id).none { it.type == TransactionType.SAVINGS_DEPOSIT })
    }

    @Test
    fun без_копилки_цель_не_нужна() = runBlocking {
        periods.savePlan(period.id, BudgetPlan(mandatory = Coins(10), optional = Coins(5), savings = Coins.ZERO))

        assertTrue(confirm(profileId))

        assertEquals(PeriodStatus.RUNNING, periods.current(profileId)!!.status)
    }

    @Test
    fun план_больше_кошелька_не_подтвердить() = runBlocking {
        chooseGoal()
        val wallet = periods.balance(period)
        periods.savePlan(period.id, BudgetPlan(mandatory = wallet, optional = Coins(1), savings = Coins.ZERO))

        assertFalse(confirm(profileId))

        assertEquals(PeriodStatus.PLANNING, periods.current(profileId)!!.status)
    }

    /** R5: бонус взрослого до плана тоже раскладывается. */
    @Test
    fun план_раскладывает_весь_кошелёк_с_бонусом() = runBlocking {
        AwardParentBonus(periods = periods, wallet = WalletEngine(clock), balance = balance)(profileId)
        val wallet = periods.balance(period)
        assertEquals(period.available + balance.parentBonus, wallet)
        periods.savePlan(period.id, BudgetPlan(mandatory = wallet, optional = Coins.ZERO, savings = Coins.ZERO))

        assertTrue(confirm(profileId))
    }

    @Test
    fun быстрое_подтверждение_ждёт_событие_дня_4() = runBlocking {
        periods.save(period.copy(status = PeriodStatus.CLOSED, closedAt = FIXED_TIME))
        val fourth = periods.open(GamePeriod(
            id = 0, profileId = profileId, number = 4, income = balance.periodIncome,
            startBalance = Coins.ZERO, status = PeriodStatus.PLANNING,
        ))
        periods.savePlan(fourth.id, BudgetPlan(mandatory = Coins(10), optional = Coins.ZERO, savings = Coins.ZERO))

        assertTrue(confirm(profileId))

        assertEquals(PeriodStatus.RUNNING, periods.current(profileId)!!.status)
        assertEquals(1, periods.transactions(fourth.id).count { it.type == TransactionType.EVENT_CARE })
        assertEquals(balance.initialStat - 10, db.petStates().byProfile(profileId.value)!!.care)
    }

    private suspend fun chooseGoal() {
        val goal = content.pack().goals.first()
        savings.setActive(profileId, savings.progress(profileId, goal.id).copy(isActive = true))
    }

    private companion object {
        const val FIXED_TIME = 1_700_000_000_000L
    }
}
