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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ru.finnypet.app.data.local.FinnyDatabase
import ru.finnypet.app.data.repository.DayRecorderImpl
import ru.finnypet.app.data.repository.PeriodRepositoryImpl
import ru.finnypet.app.data.repository.ProfileRepositoryImpl
import ru.finnypet.app.domain.economy.BudgetEngine
import ru.finnypet.app.domain.economy.GameBalance
import ru.finnypet.app.domain.economy.GameClock
import ru.finnypet.app.domain.economy.GrowthEngine
import ru.finnypet.app.domain.economy.PeriodEngine
import ru.finnypet.app.domain.economy.PetStateEngine
import ru.finnypet.app.domain.economy.WalletEngine
import ru.finnypet.app.domain.model.BudgetPlan
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.PetAppearance
import ru.finnypet.app.domain.model.ProfileId
import ru.finnypet.app.domain.model.TransactionType
import ru.finnypet.app.domain.usecase.AwardParentBonus
import ru.finnypet.app.domain.usecase.CloseDay
import ru.finnypet.app.domain.usecase.OpenPeriodIfNeeded
import java.io.File

/**
 * Проверяет бонус родителя (ТЗ 2.5.12) на живой базе.
 *
 * Главное здесь — что бонус живёт по правилам денег: он операция, а не
 * отдельное число, поэтому виден в балансе, ограничен одним разом в день и
 * не попадает в факт по направлениям — иначе сравнение плана с тратами
 * показывало бы расход, которого не было.
 */
@RunWith(AndroidJUnit4::class)
class AwardParentBonusTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val balance = GameBalance.PLACEHOLDER
    private val clock = GameClock { FIXED_TIME }

    private lateinit var storeFile: File
    private lateinit var store: DataStore<Preferences>
    private lateinit var db: FinnyDatabase
    private lateinit var profiles: ProfileRepositoryImpl
    private lateinit var periods: PeriodRepositoryImpl
    private lateinit var engine: PeriodEngine
    private lateinit var openPeriod: OpenPeriodIfNeeded
    private lateinit var award: AwardParentBonus
    private var profileId: ProfileId = ProfileId("не создан")

    @Before
    fun setUp() = runBlocking {
        storeFile = File(context.cacheDir, "bonus-${System.nanoTime()}.preferences_pb")
        store = PreferenceDataStoreFactory.create { storeFile }
        db = Room.inMemoryDatabaseBuilder(context, FinnyDatabase::class.java).build()
        profiles = ProfileRepositoryImpl(database = db, store = store, balance = balance, clock = clock)
        periods = PeriodRepositoryImpl(
            periods = db.periods(),
            plans = db.budgetPlans(),
            transactions = db.transactions(),
        )
        profileId = profiles.create(
            childName = "Егор",
            petName = "Пушок",
            appearance = PetAppearance(bodyId = "owl", colorId = "cream", accessoryId = null),
        ).id
        engine = PeriodEngine(
            budget = BudgetEngine(),
            pet = PetStateEngine(balance),
            growth = GrowthEngine(balance),
            balance = balance,
            clock = clock,
        )
        openPeriod = OpenPeriodIfNeeded(periods = periods, wallet = WalletEngine(clock), balance = balance)
        award = AwardParentBonus(periods = periods, wallet = WalletEngine(clock), balance = balance)
    }

    @After
    fun tearDown() {
        db.close()
        storeFile.delete()
    }

    @Test
    fun бонус_попадает_на_баланс_отдельной_операцией() = runBlocking {
        val period = openPeriod(profileId)
        val before = periods.balance(period)

        val result = award(profileId)

        assertNotNull(result)
        assertEquals(before + balance.parentBonus, periods.balance(period))
        assertEquals(1, bonusCount(period.id))
    }

    /** Правило команды: один бонус в игровой день. */
    @Test
    fun второй_бонус_в_тот_же_день_не_начисляется() = runBlocking {
        val period = openPeriod(profileId)
        award(profileId)
        val after = periods.balance(period)

        assertNull(award(profileId))

        assertEquals(after, periods.balance(period))
        assertEquals(1, bonusCount(period.id))
    }

    @Test
    fun после_закрытия_дня_бонус_снова_доступен() = runBlocking {
        val first = openPeriod(profileId)
        award(profileId)
        closeDay()

        val next = periods.current(profileId)!!
        assertNotNull(award(profileId))

        assertEquals(1, bonusCount(first.id))
        assertEquals(1, bonusCount(next.id))
    }

    /**
     * Бонус не расход: в сравнении плана с фактом ему места нет, иначе
     * ребёнок увидел бы трату, которой не делал (ТЗ 2.5.5).
     */
    @Test
    fun бонус_не_попадает_в_факт_по_направлениям() = runBlocking {
        val period = openPeriod(profileId)
        award(profileId)

        val fact = engine.factOf(periods.transactions(period.id))

        assertEquals(Coins.ZERO, fact.total)
    }

    @Test
    fun без_открытого_дня_начислять_нечего() = runBlocking {
        assertNull(award(profileId))
    }

    private suspend fun closeDay() {
        val period = periods.current(profileId)!!
        periods.savePlan(period.id, BudgetPlan(Coins(40), Coins(20), Coins(20)))
        periods.save(engine.confirmPlan(period))
        CloseDay(
            periods = periods,
            profiles = profiles,
            engine = engine,
            recorder = DayRecorderImpl(db),
        )(profileId)
    }

    private suspend fun bonusCount(periodId: Long): Int =
        periods.transactions(periodId).count { it.type == TransactionType.INCOME_PARENT }

    private companion object {
        const val FIXED_TIME = 1_700_000_000_000L
    }
}
