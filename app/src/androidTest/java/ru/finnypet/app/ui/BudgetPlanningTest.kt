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
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.PeriodStatus
import ru.finnypet.app.domain.model.PetAppearance
import ru.finnypet.app.domain.model.ProfileId
import ru.finnypet.app.domain.model.SpendCategory
import ru.finnypet.app.domain.usecase.ConfirmPlan
import ru.finnypet.app.domain.usecase.OpenPeriodIfNeeded
import ru.finnypet.app.ui.screens.budget.BudgetState
import ru.finnypet.app.ui.screens.budget.BudgetViewModel
import java.io.File

/**
 * Проверяет планирование целиком: от ползунка до подтверждённого дня
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
            savings = savings(),
            confirmPlan = confirmPlan(),
            budget = BudgetEngine(),
            periodEngine = periodEngine(),
            petState = PetStateEngine(balance),
            savingsEngine = SavingsEngine(clock),
            balance = balance,
            content = AssetContentRepository(context, ContentParser()),
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

        viewModel.set(SpendCategory.MANDATORY, Coins(10))
        await { it.plan.mandatory == Coins(10) }
        viewModel.set(SpendCategory.SAVINGS, Coins(5))

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

        viewModel.set(SpendCategory.OPTIONAL, Coins(5))

        await { it.plan.optional == Coins(5) }
        val period = periods.current(profileId)!!
        assertEquals(Coins(5), periods.plan(period.id)?.optional)
    }

    /** ТЗ 2.5.5: приложение само не даёт выйти за доступную сумму. */
    @Test
    fun больше_доступного_распределить_нельзя() = runBlocking {
        val planning = awaitPlanning()

        viewModel.set(SpendCategory.SAVINGS, planning.available)
        await { it.remainder == Coins.ZERO }

        // Ползунок другой банки дальше свободных монет не идёт: их нет.
        viewModel.set(SpendCategory.OPTIONAL, Coins(5))

        // Сова объясняет, почему бегунок стоит: монеты кончились.
        val full = await { it.phrase.startsWith("Монеты кончились") }
        assertEquals(planning.available, full.plan.total)
        assertEquals(Coins.ZERO, full.plan.optional)
    }

    /**
     * Доступная сумма не обязана быть круглой: числа экономики правит
     * контент-пак. Ползунок с шагом в монету раскладывает и нечётную сумму,
     * а остаток в одну монету — не ошибка, он переходит на завтра (R5).
     */
    @Test
    fun нечётная_сумма_раскладывается_до_монеты() = runBlocking {
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
            savings = savings(),
            confirmPlan = confirmPlan(),
            budget = BudgetEngine(),
            periodEngine = periodEngine(),
            petState = PetStateEngine(odd),
            savingsEngine = SavingsEngine(clock),
            balance = odd,
            content = AssetContentRepository(context, ContentParser()),
        )
        try {
            val start = withTimeout(TIMEOUT_MS) {
                second.state.first { it is BudgetState.Planning } as BudgetState.Planning
            }
            assertEquals(Coins(82), start.available)

            second.set(SpendCategory.MANDATORY, Coins(81))
            val almost = withTimeout(TIMEOUT_MS) {
                second.state.first {
                    it is BudgetState.Planning && it.remainder == Coins(1)
                } as BudgetState.Planning
            }
            assertTrue("остаток в монету не мешает подтвердить", almost.canConfirm)

            second.set(SpendCategory.MANDATORY, Coins(82))

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
        viewModel.set(SpendCategory.MANDATORY, Coins(5))
        await { it.plan.mandatory == Coins(5) }

        viewModel.set(SpendCategory.MANDATORY, Coins.ZERO)

        val planning = await { it.plan.mandatory == Coins.ZERO }
        assertEquals(false, planning.canConfirm)
    }

    @Test
    fun подтверждение_запускает_день_и_открывает_сравнение() = runBlocking {
        awaitPlanning()
        viewModel.set(SpendCategory.MANDATORY, Coins(5))
        await { it.plan.mandatory == Coins(5) }

        viewModel.confirm()

        val started = withTimeout(TIMEOUT_MS) {
            viewModel.state.first { it is BudgetState.Started } as BudgetState.Started
        }
        assertEquals(Coins(5), started.lines.single { it.category == SpendCategory.MANDATORY }.planned)
        assertTrue(started.lines.all { it.actual == Coins.ZERO })
        assertEquals(PeriodStatus.RUNNING, periods.current(profileId)!!.status)
        assertEquals(3, started.lines.size)
    }

    /** Раздел 8 плана: без цели копилку не разложить — экран зовёт её выбрать. */
    @Test
    fun без_цели_копилку_не_разложить() = runBlocking {
        assertEquals(false, awaitPlanning().hasGoal)
    }

    private suspend fun awaitPlanning(): BudgetState.Planning = await { true }

    private suspend fun await(
        condition: (BudgetState.Planning) -> Boolean,
    ): BudgetState.Planning = withTimeout(TIMEOUT_MS) {
        viewModel.state.first { it is BudgetState.Planning && condition(it) }
    } as BudgetState.Planning

    private fun savings() = SavingsRepositoryImpl(goals = db.goalProgress(), transactions = db.transactions())

    private fun confirmPlan() = ConfirmPlan(
        periods = periods,
        savings = savings(),
        content = AssetContentRepository(context, ContentParser()),
        budget = BudgetEngine(),
        periodEngine = periodEngine(),
        savingsEngine = SavingsEngine(clock),
        recorder = OutcomeRecorderImpl(
            database = db,
            petState = PetStateEngine(balance),
            taskProgress = TaskProgressRepositoryImpl(db.taskProgress(), clock),
        ),
    )

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
