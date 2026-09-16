package ru.finnypet.app.domain

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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
import ru.finnypet.app.domain.model.PeriodStatus
import ru.finnypet.app.domain.model.PetAppearance
import ru.finnypet.app.domain.model.Profile
import ru.finnypet.app.domain.usecase.OpenPeriodIfNeeded
import java.io.File

/**
 * Проверяет вход в игру: у только что созданного питомца периода нет, а без
 * периода не из чего считать баланс — главный экран (ТЗ 2.5.3) остался бы
 * пустым.
 *
 * База настоящая, потому что проверяются в том числе уникальность номера
 * периода внутри профиля и связь со строкой профиля — это ограничения SQLite,
 * а не Kotlin.
 */
@RunWith(AndroidJUnit4::class)
class OpenPeriodIfNeededTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val balance = GameBalance.PLACEHOLDER

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
            clock = GameClock { FIXED_TIME },
        )
        periods = PeriodRepositoryImpl(
            periods = db.periods(),
            plans = db.budgetPlans(),
            transactions = db.transactions(),
        )
        openPeriod = OpenPeriodIfNeeded(periods = periods, balance = balance)
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

    @Test
    fun `повторный_вход_не_открывает_второй_период`() = runTest {
        val profile = newProfile()

        val first = openPeriod(profile.id)
        val again = openPeriod(profile.id)

        assertEquals(first, again)
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

    private suspend fun newProfile(childName: String = "Егор"): Profile = profiles.create(
        childName = childName,
        petName = "Финни",
        appearance = PetAppearance(bodyId = "owl", colorId = "mint", accessoryId = null),
    )

    private companion object {
        const val FIXED_TIME = 1_700_000_000_000L
    }
}
