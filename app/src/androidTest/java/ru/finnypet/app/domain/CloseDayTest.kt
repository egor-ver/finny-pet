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
import org.junit.Assert.assertTrue
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
import ru.finnypet.app.domain.economy.PurchaseResult
import ru.finnypet.app.domain.economy.PeriodEngine
import ru.finnypet.app.domain.economy.PetStateEngine
import ru.finnypet.app.domain.economy.WalletEngine
import ru.finnypet.app.domain.model.BudgetPlan
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.ItemId
import ru.finnypet.app.domain.model.PeriodStatus
import ru.finnypet.app.domain.model.PetAppearance
import ru.finnypet.app.domain.model.ProfileId
import ru.finnypet.app.domain.model.ShopItem
import ru.finnypet.app.domain.model.SpendCategory
import ru.finnypet.app.domain.model.TransactionType
import ru.finnypet.app.domain.usecase.CloseDay
import ru.finnypet.app.domain.usecase.OpenPeriodIfNeeded
import java.io.File

/**
 * Проверяет закрытие игрового дня (ТЗ 2.5.9, 2.5.10).
 *
 * Главное здесь — атомарность: день, питомец и следующий день меняются одной
 * транзакцией. Закрытый день без следующего означал бы потерянный остаток, и
 * OpenPeriodIfNeeded справедливо считает такое состояние ошибкой.
 */
@RunWith(AndroidJUnit4::class)
class CloseDayTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val balance = GameBalance.PLACEHOLDER
    private val clock = GameClock { FIXED_TIME }

    private val food = ShopItem(
        id = ItemId("food"),
        titleKey = "shop.food",
        price = Coins(12),
        category = SpendCategory.MANDATORY,
    )

    private lateinit var storeFile: File
    private lateinit var store: DataStore<Preferences>
    private lateinit var db: FinnyDatabase
    private lateinit var profiles: ProfileRepositoryImpl
    private lateinit var periods: PeriodRepositoryImpl
    private lateinit var openPeriod: OpenPeriodIfNeeded
    private lateinit var closeDay: CloseDay
    private lateinit var engine: PeriodEngine
    private var profileId: ProfileId = ProfileId("не создан")

    @Before
    fun setUp() = runBlocking {
        storeFile = File(context.cacheDir, "day-${System.nanoTime()}.preferences_pb")
        store = PreferenceDataStoreFactory.create { storeFile }
        db = Room.inMemoryDatabaseBuilder(context, FinnyDatabase::class.java).build()
        profiles = ProfileRepositoryImpl(
            database = db,
            store = store,
            balance = balance,
            clock = clock,
        )
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
        openPeriod = OpenPeriodIfNeeded(
            periods = periods,
            wallet = WalletEngine(clock),
            balance = balance,
        )
        closeDay = CloseDay(
            periods = periods,
            profiles = profiles,
            engine = engine,
            recorder = DayRecorderImpl(db),
        )
    }

    @After
    fun tearDown() {
        db.close()
        storeFile.delete()
    }

    @Test
    fun день_закрывается_и_следующий_открывается_сразу() = runBlocking {
        val first = startDay(BudgetPlan(mandatory = Coins(40), optional = Coins(20), savings = Coins(20)))

        val result = closeDay(profileId)

        assertNotNull(result)
        val closed = result!!.value
        assertEquals(PeriodStatus.CLOSED, closed.outcome.closedPeriod.status)
        assertEquals(FIXED_TIME, closed.outcome.closedPeriod.closedAt)
        assertEquals(first.number + 1, closed.nextPeriod.number)
        assertEquals(PeriodStatus.PLANNING, closed.nextPeriod.status)
        assertEquals(2, periods.count(profileId))
        // Текущим стал новый день, а не закрытый.
        assertEquals(closed.nextPeriod.id, periods.current(profileId)?.id)
    }

    /** Неистраченное не сгорает: ТЗ 2.5.4 и `carryOverUnspent` в экономике. */
    @Test
    fun остаток_переносится_на_следующий_день() = runBlocking {
        val first = startDay(BudgetPlan(mandatory = Coins(40), optional = Coins(20), savings = Coins(20)))
        buy(first.id)
        val left = periods.balance(periods.current(profileId)!!)

        val closed = closeDay(profileId)!!.value

        assertEquals(left, closed.outcome.carryOver)
        assertEquals(left, closed.nextPeriod.startBalance)
    }

    /** ТЗ 2.5.9: после действия меняется и питомец, и день — вместе. */
    @Test
    fun питомец_записывается_вместе_с_днём() = runBlocking {
        val before = profiles.pet(profileId)!!
        startDay(BudgetPlan(mandatory = Coins(40), optional = Coins(20), savings = Coins(20)))

        val closed = closeDay(profileId)!!.value
        val after = profiles.pet(profileId)!!

        assertEquals(closed.outcome.state, after.state)
        assertEquals(closed.outcome.growth, after.growth)
        // Обязательное не куплено — сытость и уход просели, это и есть урок.
        assertTrue("показатели обязаны измениться", after.state != before.state)
    }

    /** Доход нового дня приходит при первом входе на любой экран игры. */
    @Test
    fun доход_нового_дня_начисляется_при_следующем_входе() = runBlocking {
        startDay(BudgetPlan(mandatory = Coins(40), optional = Coins(20), savings = Coins(20)))
        val closed = closeDay(profileId)!!.value
        assertEquals(0, incomeCount(closed.nextPeriod.id))

        val opened = openPeriod(profileId)

        assertEquals(closed.nextPeriod.id, opened.id)
        assertEquals(1, incomeCount(opened.id))
        assertEquals(opened.startBalance + opened.income, periods.balance(opened))
    }

    @Test
    fun пока_день_планируется_закрывать_нечего() = runBlocking {
        openPeriod(profileId)
        periods.savePlan(periods.current(profileId)!!.id, BudgetPlan(Coins(10), Coins.ZERO, Coins.ZERO))

        assertNull(closeDay(profileId))
        assertEquals(1, periods.count(profileId))
    }

    @Test
    fun повторное_закрытие_не_проходит() = runBlocking {
        startDay(BudgetPlan(mandatory = Coins(40), optional = Coins(20), savings = Coins(20)))
        closeDay(profileId)

        assertNull(closeDay(profileId))
        assertEquals(2, periods.count(profileId))
    }

    /** Открывает день, кладёт план и подтверждает его — как это делает ребёнок. */
    private suspend fun startDay(plan: BudgetPlan) = openPeriod(profileId).also { period ->
        periods.savePlan(period.id, plan)
        periods.save(engine.confirmPlan(period))
    }

    private suspend fun buy(periodId: Long) {
        val wallet = WalletEngine(clock)
        val current = periods.current(profileId)!!
        val result = wallet.purchase(
            item = food,
            currentBalance = periods.balance(current),
            periodId = periodId,
            optionalLeft = Coins.ZERO,
        )
        val success = result as PurchaseResult.Success
        periods.addTransaction(success.transaction)
    }

    private suspend fun incomeCount(periodId: Long): Int =
        periods.transactions(periodId).count { it.type == TransactionType.INCOME_PERIOD }

    private companion object {
        const val FIXED_TIME = 1_700_000_000_000L
    }
}
