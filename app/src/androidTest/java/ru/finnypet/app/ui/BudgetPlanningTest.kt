package ru.finnypet.app.ui

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ru.finnypet.app.data.local.FinnyDatabase
import ru.finnypet.app.data.repository.PeriodRepositoryImpl
import ru.finnypet.app.data.repository.ProfileRepositoryImpl
import ru.finnypet.app.domain.economy.BudgetEngine
import ru.finnypet.app.domain.economy.GameBalance
import ru.finnypet.app.domain.economy.GameClock
import ru.finnypet.app.domain.economy.GrowthEngine
import ru.finnypet.app.domain.economy.PeriodEngine
import ru.finnypet.app.domain.economy.PetStateEngine
import ru.finnypet.app.domain.economy.WalletEngine
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.PeriodStatus
import ru.finnypet.app.domain.model.PetAppearance
import ru.finnypet.app.domain.model.ProfileId
import ru.finnypet.app.domain.model.SpendCategory
import ru.finnypet.app.domain.usecase.OpenPeriodIfNeeded
import ru.finnypet.app.ui.screens.budget.BudgetState
import ru.finnypet.app.ui.screens.budget.BudgetViewModel
import java.io.File

/**
 * Проверяет планирование целиком: от кнопки «плюс» до подтверждённого дня
 * в базе (ТЗ 2.5.5).
 *
 * Экранные тесты показывают вёрстку на выдуманном состоянии, а здесь работает
 * настоящая цепочка — вьюмодель, репозитории, SQLite. Без неё осталось бы
 * непроверенным главное: что план переживает выход с экрана и что превысить
 * доступную сумму нельзя.
 */
@RunWith(AndroidJUnit4::class)
class BudgetPlanningTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val balance = GameBalance.PLACEHOLDER
    private val clock = GameClock { FIXED_TIME }

    private lateinit var storeFile: File
    private lateinit var store: DataStore<Preferences>
    private lateinit var db: FinnyDatabase
    private lateinit var profiles: ProfileRepositoryImpl
    private lateinit var periods: PeriodRepositoryImpl
    private lateinit var viewModel: BudgetViewModel
    private var profileId: ProfileId = ProfileId("не создан")

    @Before
    fun setUp() = runBlocking {
        storeFile = File(context.cacheDir, "budget-${System.nanoTime()}.preferences_pb")
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
        viewModel = BudgetViewModel(
            profiles = profiles,
            periods = periods,
            openPeriod = OpenPeriodIfNeeded(
                periods = periods,
                wallet = WalletEngine(clock),
                balance = balance,
            ),
            budget = BudgetEngine(),
            periodEngine = periodEngine(),
        )
    }

    @After
    fun tearDown() {
        // Сначала останавливаем вьюмодель и ждём, пока она остановится: её
        // подписка на базу переживает закрытие и падает на «Database is closed»,
        // а отмена не прерывает запрос, который уже ушёл в SQLite.
        viewModel.viewModelScope.cancel()
        runBlocking { viewModel.viewModelScope.coroutineContext[Job]?.join() }
        db.close()
        storeFile.delete()
    }

    @Test
    fun доступна_вся_сумма_дня() = runBlocking {
        val planning = awaitPlanning()

        assertEquals(balance.startingBalance + balance.periodIncome, planning.available)
        assertEquals(planning.available, planning.remainder)
        assertEquals(Coins.ZERO, planning.plan.total)
    }

    /** Пустой план подтверждать нечего: день без решений ничему не учит. */
    @Test
    fun пустой_план_подтвердить_нельзя() = runBlocking {
        assertEquals(false, awaitPlanning().canConfirm)
    }

    @Test
    fun монеты_раскладываются_по_направлениям() = runBlocking {
        awaitPlanning()

        viewModel.add(SpendCategory.MANDATORY)
        viewModel.add(SpendCategory.MANDATORY)
        viewModel.add(SpendCategory.SAVINGS)

        val planning = await { it.plan.total == Coins(15) }
        assertEquals(Coins(10), planning.plan.mandatory)
        assertEquals(Coins(5), planning.plan.savings)
        assertEquals(planning.available - Coins(15), planning.remainder)
        assertTrue(planning.canConfirm)
    }

    /** План должен пережить выход с экрана: ТЗ 2.5.5 разрешает передумать. */
    @Test
    fun черновик_плана_остаётся_в_базе() = runBlocking {
        awaitPlanning()

        viewModel.add(SpendCategory.OPTIONAL)

        await { it.plan.optional == Coins(5) }
        val period = periods.current(profileId)!!
        assertEquals(Coins(5), periods.plan(period.id)?.optional)
    }

    /** ТЗ 2.5.5: приложение само не даёт выйти за доступную сумму. */
    @Test
    fun больше_доступного_распределить_нельзя() = runBlocking {
        val planning = awaitPlanning()
        val steps = planning.available.amount / planning.step

        repeat(steps) { viewModel.add(SpendCategory.SAVINGS) }
        val full = await { it.remainder == Coins.ZERO }
        assertEquals(false, full.canAdd())

        viewModel.add(SpendCategory.SAVINGS)

        assertEquals(planning.available, await { true }.plan.total)
    }

    /**
     * Доступная сумма не обязана делиться на шаг: числа экономики правит
     * контент-пак. Последние монеты должны добираться неполным шагом, иначе
     * остаток нельзя обнулить и план не подтвердить целиком.
     */
    @Test
    fun последние_монеты_добираются_неполным_шагом() = runBlocking {
        // Дожидаемся, пока вьюмодель из setUp откроет день первому профилю.
        // Иначе она всё ещё ждёт активный профиль, дожидается уже второго и
        // открывает день ему — своими числами, а не теми, что проверяем.
        awaitPlanning()

        // 22 + 60 = 82 — на пять не делится, в конце останется две монеты.
        val odd = balance.copy(startingBalance = Coins(22))
        profiles.create(
            childName = "Аня",
            petName = "Сова",
            appearance = PetAppearance(bodyId = "owl", colorId = "white", accessoryId = null),
        )
        // Активный профиль хранится в DataStore, и запись доходит до
        // подписчиков не мгновенно. Без ожидания вьюмодель успевала взять
        // прежний профиль и открывала его день вместо нового.
        withTimeout(TIMEOUT_MS) {
            profiles.observeActive().first { it?.childName == "Аня" }
        }
        val second = BudgetViewModel(
            profiles = profiles,
            periods = periods,
            openPeriod = OpenPeriodIfNeeded(
                periods = periods,
                wallet = WalletEngine(clock),
                balance = odd,
            ),
            budget = BudgetEngine(),
            periodEngine = periodEngine(),
        )
        try {
            val start = withTimeout(TIMEOUT_MS) {
                second.state.first { it is BudgetState.Planning } as BudgetState.Planning
            }
            assertEquals(Coins(82), start.available)

            repeat(start.available.amount / start.step) { second.add(SpendCategory.MANDATORY) }
            val almost = withTimeout(TIMEOUT_MS) {
                second.state.first {
                    it is BudgetState.Planning && it.remainder == Coins(2)
                } as BudgetState.Planning
            }
            assertTrue("остаток меньше шага, а кнопка недоступна", almost.canAdd())

            second.add(SpendCategory.MANDATORY)

            val full = withTimeout(TIMEOUT_MS) {
                second.state.first {
                    it is BudgetState.Planning && it.remainder == Coins.ZERO
                } as BudgetState.Planning
            }
            assertEquals(Coins(82), full.plan.mandatory)
            assertTrue(full.isDistributed)
            assertTrue(full.canConfirm)
        } finally {
            second.viewModelScope.cancel()
        }
    }

    @Test
    fun убрать_монеты_можно_обратно() = runBlocking {
        awaitPlanning()
        viewModel.add(SpendCategory.MANDATORY)
        await { it.plan.mandatory == Coins(5) }

        viewModel.remove(SpendCategory.MANDATORY)

        val planning = await { it.plan.mandatory == Coins.ZERO }
        assertEquals(false, planning.canRemove(SpendCategory.MANDATORY))
    }

    @Test
    fun подтверждение_запускает_день_и_открывает_сравнение() = runBlocking {
        awaitPlanning()
        viewModel.add(SpendCategory.MANDATORY)
        await { it.plan.mandatory == Coins(5) }

        viewModel.confirm()

        val started = withTimeout(TIMEOUT_MS) {
            viewModel.state.first { it is BudgetState.Started } as BudgetState.Started
        }
        assertEquals(Coins(5), started.planTotal)
        assertEquals(Coins.ZERO, started.factTotal)
        assertEquals(PeriodStatus.RUNNING, periods.current(profileId)!!.status)
        assertEquals(3, started.lines.size)
    }

    private suspend fun awaitPlanning(): BudgetState.Planning = await { true }

    private suspend fun await(
        condition: (BudgetState.Planning) -> Boolean,
    ): BudgetState.Planning = withTimeout(TIMEOUT_MS) {
        viewModel.state.first { it is BudgetState.Planning && condition(it) }
    } as BudgetState.Planning

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
