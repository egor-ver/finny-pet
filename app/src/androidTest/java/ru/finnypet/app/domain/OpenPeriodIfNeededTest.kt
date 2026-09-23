package ru.finnypet.app.domain

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ru.finnypet.app.data.local.FinnyDatabase
import ru.finnypet.app.data.repository.PeriodRepositoryImpl
import ru.finnypet.app.data.repository.ProfileRepositoryImpl
import ru.finnypet.app.domain.economy.GameBalance
import ru.finnypet.app.domain.economy.GameClock
import ru.finnypet.app.domain.economy.WalletEngine
import ru.finnypet.app.domain.model.GamePeriod
import ru.finnypet.app.domain.model.PeriodStatus
import ru.finnypet.app.domain.model.PetAppearance
import ru.finnypet.app.domain.model.Profile
import ru.finnypet.app.domain.model.ProfileId
import ru.finnypet.app.domain.model.TransactionType
import ru.finnypet.app.domain.repository.PeriodRepository
import ru.finnypet.app.domain.usecase.OpenPeriodIfNeeded
import java.io.File

/**
 * Проверяет вход в игру: у только что созданного питомца периода нет, а без
 * периода не из чего считать баланс — главный экран (ТЗ 2.5.3) остался бы
 * пустым.
 *
 * База настоящая, а не выдуманный репозиторий: проверяется не только то, какой
 * период получается, но и то, что он действительно один на профиль — а это
 * держит уникальный индекс в SQLite, а не код.
 */
@RunWith(AndroidJUnit4::class)
class OpenPeriodIfNeededTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val balance = GameBalance.PLACEHOLDER
    private val clock = GameClock { FIXED_TIME }

    private lateinit var storeFile: File
    private lateinit var store: DataStore<Preferences>
    private lateinit var db: FinnyDatabase
    private lateinit var profiles: ProfileRepositoryImpl
    private lateinit var periods: PeriodRepositoryImpl
    private lateinit var openPeriod: OpenPeriodIfNeeded

    @Before
    fun setUp() {
        // Имя файла своё на каждый тест: DataStore не допускает двух
        // экземпляров на один файл, а тесты идут в одном процессе.
        storeFile = File(context.cacheDir, "period-${System.nanoTime()}.preferences_pb")
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
        openPeriod = OpenPeriodIfNeeded(
            periods = periods,
            wallet = WalletEngine(clock),
            balance = balance,
        )
    }

    @After
    fun tearDown() {
        db.close()
        storeFile.delete()
    }

    @Test
    fun `первый_период_открывается_на_числах_из_баланса`() = runTest {
        val profile = newProfile()

        val period = openPeriod(profile.id)

        assertEquals(1, period.number)
        assertEquals(balance.periodIncome, period.income)
        assertEquals(balance.startingBalance, period.startBalance)
        assertEquals(PeriodStatus.PLANNING, period.status)
        assertEquals(balance.startingBalance + balance.periodIncome, period.available)
        assertNotEquals(0L, period.id)
    }

    /**
     * ТЗ 2.5.5: ребёнок планирует доступную сумму, а это стартовый остаток
     * вместе с доходом. Значит доход обязан лежать на балансе до планирования.
     */
    @Test
    fun `доход_периода_начисляется_при_открытии`() = runTest {
        val profile = newProfile()

        val period = openPeriod(profile.id)

        assertEquals(balance.startingBalance + balance.periodIncome, periods.balance(period))
        assertEquals(1, incomeCount(period.id))
    }

    @Test
    fun `доход_не_начисляется_дважды`() = runTest {
        val profile = newProfile()

        val period = openPeriod(profile.id)
        openPeriod(profile.id)

        assertEquals(1, incomeCount(period.id))
        assertEquals(balance.startingBalance + balance.periodIncome, periods.balance(period))
    }

    /**
     * Экраны открывают день каждый из своего init: главный ещё начисляет доход,
     * а ребёнок уже открыл магазин. Доход обязан лечь один раз.
     */
    @Test
    fun `одновременные_входы_начисляют_доход_один_раз`() = runBlocking {
        val profile = newProfile()
        val bare = periods.open(
            GamePeriod(
                id = 0L,
                profileId = profile.id,
                number = 1,
                income = balance.periodIncome,
                startBalance = balance.startingBalance,
                status = PeriodStatus.PLANNING,
            )
        )

        List(CONCURRENT_ENTRIES) { async(Dispatchers.IO) { openPeriod(profile.id) } }.awaitAll()

        assertEquals(1, incomeCount(bare.id))
    }

    /**
     * Между открытием периода и записью начисления приложение может закрыться.
     * Без восстановления период остался бы без дохода навсегда.
     */
    @Test
    fun `потерянное_начисление_восстанавливается_при_следующем_входе`() = runTest {
        val profile = newProfile()
        val bare = periods.open(
            GamePeriod(
                id = 0L,
                profileId = profile.id,
                number = 1,
                income = balance.periodIncome,
                startBalance = balance.startingBalance,
                status = PeriodStatus.PLANNING,
            )
        )
        assertEquals(balance.startingBalance, periods.balance(bare))

        openPeriod(profile.id)

        assertEquals(1, incomeCount(bare.id))
        assertEquals(balance.startingBalance + balance.periodIncome, periods.balance(bare))
    }

    @Test
    fun `повторный_вход_не_открывает_второй_период`() = runTest {
        val profile = newProfile()

        val first = openPeriod(profile.id)
        val again = openPeriod(profile.id)

        assertEquals(first, again)
        assertEquals(1, periods.count(profile.id))
    }

    /**
     * Самый частый вход в игру: план уже подтверждён и период идёт. Открыть
     * здесь новый период значило бы обнулить подтверждённый план посреди игры.
     */
    @Test
    fun `в_идущий_период_игра_возвращается_в_него_же`() = runTest {
        val profile = newProfile()
        val started = openPeriod(profile.id).copy(status = PeriodStatus.RUNNING)
        periods.save(started)

        val again = openPeriod(profile.id)

        assertEquals(started, again)
        assertEquals(1, periods.count(profile.id))
    }

    @Test
    fun `у_каждого_профиля_свой_первый_период`() = runTest {
        val one = newProfile(childName = "Егор")
        val two = newProfile(childName = "Аня")

        val periodOne = openPeriod(one.id)
        val periodTwo = openPeriod(two.id)

        assertNotEquals(periodOne.id, periodTwo.id)
        assertEquals(1, periodOne.number)
        assertEquals(1, periodTwo.number)
        assertEquals(1, periods.count(one.id))
        assertEquals(1, periods.count(two.id))
    }

    /**
     * Два вызова сразу — например, сброс демо-режима при уже открытом главном
     * экране. Опоздавший не должен ни падать, ни заводить второй период.
     */
    @Test
    fun `опоздавший_вызов_подхватывает_чужой_период`() = runTest {
        val profile = newProfile()
        val existing = openPeriod(profile.id)
        val late = OpenPeriodIfNeeded(
            periods = BlindOnce(periods),
            wallet = WalletEngine(clock),
            balance = balance,
        )

        val period = late(profile.id)

        assertEquals(existing, period)
        assertEquals(1, periods.count(profile.id))
    }

    /**
     * Закрытие периода обязано открывать следующий тем же действием, иначе
     * неистраченный остаток не переносится. Тихо открыть период номер один
     * поверх закрытых значило бы обнулить прогресс молча.
     */
    @Test
    fun `закрытый_период_без_следующего_считается_ошибкой`() = runTest {
        val profile = newProfile()
        val period = openPeriod(profile.id)
        periods.save(period.copy(status = PeriodStatus.CLOSED, closedAt = FIXED_TIME))

        val error = runCatching { openPeriod(profile.id) }.exceptionOrNull()

        assertTrue("ожидали отказ, получили $error", error is IllegalStateException)
        assertTrue(error!!.message!!.contains(profile.id.value))
    }

    private suspend fun incomeCount(periodId: Long): Int =
        periods.transactions(periodId).count { it.type == TransactionType.INCOME_PERIOD }

    private suspend fun newProfile(childName: String = "Егор"): Profile = profiles.create(
        childName = childName,
        petName = "Финни",
        appearance = PetAppearance(bodyId = "owl", colorId = "mint", accessoryId = null),
    )

    /**
     * Настоящее хранилище, но первый вопрос «какой период сейчас» остаётся без
     * ответа. Так воспроизводится гонка: период уже вставлен, а этот вызов его
     * ещё не видит и пойдёт вставлять свой.
     */
    private class BlindOnce(private val real: PeriodRepository) : PeriodRepository by real {

        private var asked = false

        override suspend fun current(profileId: ProfileId): GamePeriod? {
            if (!asked) {
                asked = true
                return null
            }
            return real.current(profileId)
        }
    }

    private companion object {
        const val FIXED_TIME = 1_700_000_000_000L
        const val CONCURRENT_ENTRIES = 10
    }
}
