package ru.finnypet.app.data

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
import ru.finnypet.app.domain.economy.GameBalance
import ru.finnypet.app.domain.economy.GameClock
import ru.finnypet.app.domain.model.BudgetPlan
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.GamePeriod
import ru.finnypet.app.domain.model.GoalId
import ru.finnypet.app.domain.model.GoalProgress
import ru.finnypet.app.domain.model.PeriodStatus
import ru.finnypet.app.domain.model.PetAppearance
import ru.finnypet.app.domain.model.ProfileId
import ru.finnypet.app.domain.model.TaskId
import ru.finnypet.app.domain.model.Transaction
import ru.finnypet.app.domain.model.TransactionType
import java.io.File

/**
 * Первая настоящая проверка хранения: запросы выполняются на живом SQLite,
 * а не только разбираются компилятором.
 *
 * База файловая, а не в памяти, потому что главное здесь — переживают ли
 * данные закрытие и повторное открытие приложения (ТЗ 2.5.13, шаг 11
 * Приложения А).
 *
 * Файлы у каждого теста свои: все тесты идут в одном процессе, а DataStore
 * запрещает два экземпляра на один файл и падает при попытке.
 */
@RunWith(AndroidJUnit4::class)
class StorageTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val clock = GameClock { FIXED_TIME }

    private lateinit var dbFile: File
    private lateinit var storeFile: File
    private lateinit var db: FinnyDatabase
    private lateinit var store: DataStore<Preferences>

    private lateinit var profiles: ProfileRepositoryImpl
    private lateinit var periods: PeriodRepositoryImpl
    private lateinit var savings: SavingsRepositoryImpl
    private lateinit var tasks: TaskProgressRepositoryImpl
    private lateinit var settings: SettingsRepositoryImpl

    @Before
    fun setUp() {
        val unique = System.nanoTime()
        dbFile = File(context.cacheDir, "storage-$unique.db")
        storeFile = File(context.cacheDir, "storage-$unique.preferences_pb")
        // DataStore в приложении — синглтон на весь процесс, поэтому он
        // создаётся один раз на тест и переживает перезапуск базы.
        store = PreferenceDataStoreFactory.create { storeFile }
        settings = SettingsRepositoryImpl(store)
        openDatabase()
    }

    @After
    fun tearDown() {
        db.close()
        dbFile.delete()
        storeFile.delete()
    }

    /** Ровно то, что делает Hilt, только руками: тест проверяет хранение, не граф. */
    private fun openDatabase() {
        db = Room.databaseBuilder(context, FinnyDatabase::class.java, dbFile.absolutePath)
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
        profiles = ProfileRepositoryImpl(
            database = db,
            store = store,
            balance = GameBalance.PLACEHOLDER,
            clock = clock,
        )
        periods = PeriodRepositoryImpl(
            periods = db.periods(),
            plans = db.budgetPlans(),
            transactions = db.transactions(),
        )
        savings = SavingsRepositoryImpl(
            goals = db.goalProgress(),
            transactions = db.transactions(),
        )
        tasks = TaskProgressRepositoryImpl(tasks = db.taskProgress(), clock = clock)
    }

    // --- Профиль и питомец ---

    @Test
    fun `профиль_создаётся_вместе_с_питомцем_и_становится_активным`() = runTest {
        val profile = profiles.create(
            childName = "Егор",
            petName = "Финни",
            appearance = PetAppearance("owl", "mint", "scarf"),
        )

        assertEquals(profile, profiles.active())
        val pet = profiles.pet(profile.id)
        assertNotNull(pet)
        assertEquals(GameBalance.PLACEHOLDER.initialStat, pet!!.state.mood.value)
    }

    @Test
    fun `без_активного_профиля_репозиторий_отдаёт_пусто`() = runTest {
        assertNull(profiles.active())
        assertNull(profiles.observeActive().first())
    }

    // --- Ключевой тест: ТЗ 2.5.13 ---

    @Test
    fun `прогресс_переживает_закрытие_и_повторное_открытие`() = runTest {
        val profile = profiles.create("Егор", "Финни", PetAppearance("owl", "mint", null))
        val period = periods.open(runningPeriod(profile.id))
        periods.addTransaction(income(period.id, 60))
        periods.addTransaction(purchase(period.id, 25))
        savings.setActive(profile.id, GoalProgress(GoalId("bike"), Coins(30)))
        tasks.complete(profile.id, TaskId("plan-1"), "good", Coins(15))

        db.close()
        openDatabase()

        assertEquals(profile, profiles.active())
        assertNotNull(profiles.pet(profile.id))
        assertEquals(2, periods.transactions(period.id).size)
        // 20 стартовых + 60 дохода − 25 покупки
        assertEquals(Coins(55), periods.balance(periods.current(profile.id)!!))
        assertEquals(Coins(30), savings.activeProgress(profile.id)?.saved)
        assertEquals(setOf(TaskId("plan-1")), tasks.completedIds(profile.id))
    }

    // --- Каскадное удаление: ТЗ 3.5 ---

    @Test
    fun `удаление_профиля_уносит_периоды_планы_и_операции`() = runTest {
        val profile = profiles.create("Егор", "Финни", PetAppearance("owl", "mint", null))
        val period = periods.open(runningPeriod(profile.id))
        periods.addTransaction(income(period.id, 60))
        periods.savePlan(period.id, plan())
        tasks.complete(profile.id, TaskId("plan-1"), "good", Coins(15))

        profiles.delete(profile.id)

        assertNull(profiles.active())
        assertNull(profiles.pet(profile.id))
        assertNull(periods.current(profile.id))
        // Операции висят на периоде, а не на профиле: каскад двухуровневый.
        assertTrue(periods.transactions(period.id).isEmpty())
        assertNull(periods.plan(period.id))
        assertTrue(tasks.completedIds(profile.id).isEmpty())
    }

    // --- Уникальный индекс ---

    @Test
    fun `двум_периодам_с_одним_номером_в_профиле_не_ужиться`() = runTest {
        val profile = profiles.create("Егор", "Финни", PetAppearance("owl", "mint", null))
        periods.open(runningPeriod(profile.id))

        val error = runCatching { periods.open(runningPeriod(profile.id)) }.exceptionOrNull()

        assertTrue(
            "Ожидалось нарушение уникального индекса, получено: $error",
            error is SQLiteConstraintException,
        )
    }

    // --- Защита от молчаливой потери периода ---

    @Test
    fun `сохранение_несохранённого_периода_отвергается`() = runTest {
        val profile = profiles.create("Егор", "Финни", PetAppearance("owl", "mint", null))

        val error = runCatching { periods.save(runningPeriod(profile.id)) }.exceptionOrNull()

        assertTrue(
            "Ожидался отказ вместо молчаливого обновления нуля строк, получено: $error",
            error is IllegalArgumentException,
        )
    }

    @Test
    fun `повторное_открытие_сохранённого_периода_отвергается`() = runTest {
        val profile = profiles.create("Егор", "Финни", PetAppearance("owl", "mint", null))
        val period = periods.open(runningPeriod(profile.id))

        val error = runCatching { periods.open(period) }.exceptionOrNull()

        assertTrue(
            "Ожидался отказ вместо повторной вставки, получено: $error",
            error is IllegalArgumentException,
        )
    }

    @Test
    fun `закрытие_периода_сохраняется`() = runTest {
        val profile = profiles.create("Егор", "Финни", PetAppearance("owl", "mint", null))
        val period = periods.open(runningPeriod(profile.id))

        periods.save(period.copy(status = PeriodStatus.CLOSED, closedAt = FIXED_TIME))

        assertNull(periods.current(profile.id))
        assertEquals(period.id, periods.lastClosed(profile.id)?.id)
    }

    // --- Баланс ---

    @Test
    fun `баланс_складывается_из_операций_поверх_стартового_остатка`() = runTest {
        val profile = profiles.create("Егор", "Финни", PetAppearance("owl", "mint", null))
        val period = periods.open(runningPeriod(profile.id))

        periods.addTransaction(income(period.id, 60))
        periods.addTransaction(income(period.id, 15, TransactionType.INCOME_TASK))
        periods.addTransaction(purchase(period.id, 30))
        periods.addTransaction(purchase(period.id, 5, TransactionType.PURCHASE_OPTIONAL))
        periods.addTransaction(purchase(period.id, 10, TransactionType.SAVINGS_DEPOSIT))

        // 20 + 60 + 15 − 30 − 5 − 10
        assertEquals(Coins(50), periods.balance(period))
    }

    @Test
    fun `баланс_обновляется_в_Flow_после_записи`() = runTest {
        val profile = profiles.create("Егор", "Финни", PetAppearance("owl", "mint", null))
        val period = periods.open(runningPeriod(profile.id))

        assertEquals(Coins(20), periods.observeBalance(period).first())
        periods.addTransaction(income(period.id, 60))
        assertEquals(Coins(80), periods.observeBalance(period).first())
    }

    @Test
    fun `период_и_операция_получают_выданные_базой_идентификаторы`() = runTest {
        val profile = profiles.create("Егор", "Финни", PetAppearance("owl", "mint", null))

        val period = periods.open(runningPeriod(profile.id))
        val saved = periods.addTransaction(income(period.id, 60))

        assertTrue("Идентификатор периода остался нулевым", period.id != 0L)
        assertTrue("Идентификатор операции остался нулевым", saved.id != 0L)
    }

    // --- Накопления: проверка находки 1 из ревью ---

    @Test
    fun `средняя_сумма_пополнения_не_смешивает_профили`() = runTest {
        val goal = GoalId("bike")

        val first = profiles.create("Егор", "Финни", PetAppearance("owl", "mint", null))
        val firstPeriod = periods.open(runningPeriod(first.id))
        periods.addTransaction(deposit(firstPeriod.id, 10, goal))

        val second = profiles.create("Аня", "Лапа", PetAppearance("cat", "rose", null))
        val secondPeriod = periods.open(runningPeriod(second.id))
        periods.addTransaction(deposit(secondPeriod.id, 50, goal))

        assertEquals(Coins(10), savings.averageDeposit(first.id, goal))
        assertEquals(Coins(50), savings.averageDeposit(second.id, goal))
    }

    @Test
    fun `снятие_не_участвует_в_средней_сумме_пополнения`() = runTest {
        val goal = GoalId("bike")
        val profile = profiles.create("Егор", "Финни", PetAppearance("owl", "mint", null))
        val period = periods.open(runningPeriod(profile.id))

        periods.addTransaction(deposit(period.id, 10, goal))
        periods.addTransaction(deposit(period.id, 20, goal))
        periods.addTransaction(
            deposit(period.id, 100, goal).copy(type = TransactionType.SAVINGS_WITHDRAW)
        )

        assertEquals(Coins(15), savings.averageDeposit(profile.id, goal))
    }

    @Test
    fun `активной_целью_остаётся_только_последняя_выбранная`() = runTest {
        val profile = profiles.create("Егор", "Финни", PetAppearance("owl", "mint", null))

        savings.setActive(profile.id, GoalProgress(GoalId("bike"), Coins(10)))
        savings.setActive(profile.id, GoalProgress(GoalId("ball"), Coins(0)))

        assertEquals(GoalId("ball"), savings.activeProgress(profile.id)?.goalId)
        assertEquals(1, savings.all(profile.id).count { it.isActive })
        // Прежняя цель осталась в базе с накопленным, просто перестала быть активной.
        assertEquals(Coins(10), savings.progress(profile.id, GoalId("bike")).saved)
    }

    @Test
    fun `сохранение_активного_прогресса_не_заводит_вторую_активную_цель`() = runTest {
        val profile = profiles.create("Егор", "Финни", PetAppearance("owl", "mint", null))
        savings.setActive(profile.id, GoalProgress(GoalId("bike"), Coins(10)))

        // Прогресс другой цели, по недосмотру помеченный активным.
        savings.save(profile.id, GoalProgress(GoalId("ball"), Coins(5), isActive = true))

        assertEquals(1, savings.all(profile.id).count { it.isActive })
        assertEquals(GoalId("ball"), savings.activeProgress(profile.id)?.goalId)
    }

    // --- Учебный прогресс: ТЗ 2.5.11 ---

    @Test
    fun `повторное_прохождение_задания_не_дублирует_его_в_списке_тем`() = runTest {
        val profile = profiles.create("Егор", "Финни", PetAppearance("owl", "mint", null))

        tasks.complete(profile.id, TaskId("plan-1"), "good", Coins(15))
        tasks.complete(profile.id, TaskId("plan-1"), "bad", Coins(5))
        tasks.complete(profile.id, TaskId("save-1"), "good", Coins(15))

        assertEquals(setOf(TaskId("plan-1"), TaskId("save-1")), tasks.completedIds(profile.id))
        // Оба прохождения сохранены: прежний результат не затирается.
        assertEquals(3, tasks.observeCompleted(profile.id).first().size)
    }

    // --- Настройки: ТЗ 3.6 ---

    @Test
    fun `звук_и_анимации_включены_по_умолчанию`() = runTest {
        assertEquals(true, settings.observeSoundEnabled().first())
        assertEquals(true, settings.observeAnimationsEnabled().first())

        settings.setSoundEnabled(false)
        settings.setAnimationsEnabled(false)

        assertEquals(false, settings.observeSoundEnabled().first())
        assertEquals(false, settings.observeAnimationsEnabled().first())
    }

    // --- Заготовки ---

    private fun runningPeriod(profileId: ProfileId) = GamePeriod(
        id = 0,
        profileId = profileId,
        number = 1,
        income = Coins(60),
        startBalance = Coins(20),
        status = PeriodStatus.RUNNING,
    )

    private fun plan() = BudgetPlan(
        mandatory = Coins(30),
        optional = Coins(20),
        savings = Coins(10),
    )

    private fun income(
        periodId: Long,
        amount: Int,
        type: TransactionType = TransactionType.INCOME_PERIOD,
    ) = Transaction(
        id = 0,
        periodId = periodId,
        type = type,
        amount = Coins(amount),
        reasonKey = "balance.credited",
        createdAt = FIXED_TIME,
    )

    private fun purchase(
        periodId: Long,
        amount: Int,
        type: TransactionType = TransactionType.PURCHASE_MANDATORY,
    ) = Transaction(
        id = 0,
        periodId = periodId,
        type = type,
        amount = Coins(amount),
        reasonKey = "purchase.done",
        createdAt = FIXED_TIME,
    )

    private fun deposit(periodId: Long, amount: Int, goalId: GoalId) = Transaction(
        id = 0,
        periodId = periodId,
        type = TransactionType.SAVINGS_DEPOSIT,
        amount = Coins(amount),
        reasonKey = "savings.deposited",
        createdAt = FIXED_TIME,
        goalId = goalId,
    )

    private companion object {
        const val FIXED_TIME = 1_700_000_000_000L
    }
}
