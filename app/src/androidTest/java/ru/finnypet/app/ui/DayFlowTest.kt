package ru.finnypet.app.ui

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ru.finnypet.app.data.local.FinnyDatabase
import ru.finnypet.app.data.repository.DayRecorderImpl
import ru.finnypet.app.data.repository.PeriodRepositoryImpl
import ru.finnypet.app.data.repository.ProfileRepositoryImpl
import ru.finnypet.app.data.repository.SavingsRepositoryImpl
import ru.finnypet.app.domain.content.ContentPack
import ru.finnypet.app.domain.content.PetOptions
import ru.finnypet.app.domain.content.ContentOption
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
import ru.finnypet.app.domain.repository.ContentRepository
import ru.finnypet.app.domain.usecase.CloseDay
import ru.finnypet.app.domain.usecase.OpenPeriodIfNeeded
import ru.finnypet.app.ui.screens.day.DayState
import ru.finnypet.app.ui.screens.day.DayViewModel
import java.io.File

/**
 * Проверяет закрытие дня через вьюмодель — так, как это делает ребёнок
 * (ТЗ 2.5.9, 2.5.10).
 *
 * Отдельно от [ru.finnypet.app.domain.CloseDayTest]: там проверяется запись в
 * базу, здесь — что экран отдаёт итоги и что новый день сразу готов к игре.
 * Последнее нашлось вживую: главный экран остаётся в стеке и второй раз не
 * создаётся, поэтому начислить доход новому дню некому, кроме этого экрана.
 */
@RunWith(AndroidJUnit4::class)
class DayFlowTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val balance = GameBalance.PLACEHOLDER
    private val clock = GameClock { FIXED_TIME }

    private lateinit var storeFile: File
    private lateinit var store: DataStore<Preferences>
    private lateinit var db: FinnyDatabase
    private lateinit var profiles: ProfileRepositoryImpl
    private lateinit var periods: PeriodRepositoryImpl
    private lateinit var viewModel: DayViewModel
    private var profileId: ProfileId = ProfileId("не создан")

    @Before
    fun setUp() = runBlocking {
        storeFile = File(context.cacheDir, "dayflow-${System.nanoTime()}.preferences_pb")
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
        viewModel = DayViewModel(
            profiles = profiles,
            periods = periods,
            openPeriod = openPeriod(),
            closeDay = CloseDay(
                periods = periods,
                profiles = profiles,
                engine = periodEngine(),
                recorder = DayRecorderImpl(db),
            ),
            budget = BudgetEngine(),
            periodEngine = periodEngine(),
            petState = PetStateEngine(balance),
            savings = SavingsRepositoryImpl(goals = db.goalProgress(), transactions = db.transactions()),
            balance = balance,
            content = content(),
        )
    }

    @After
    fun tearDown() {
        viewModel.viewModelScope.cancel()
        runBlocking { viewModel.viewModelScope.coroutineContext[Job]?.join() }
        db.close()
        storeFile.delete()
    }

    /** Пока план не подтверждён, закрывать нечего — и экран это говорит. */
    @Test
    fun пока_день_планируется_итогов_нет() = runBlocking {
        val state = withTimeout(TIMEOUT_MS) { viewModel.state.first { it is DayState.Planning } }

        assertEquals(DayState.Planning, state)
    }

    @Test
    fun день_закрывается_и_показывает_итоги() = runBlocking {
        startDay()
        withTimeout(TIMEOUT_MS) { viewModel.state.first { it is DayState.Running } }

        viewModel.close()

        val closed = withTimeout(TIMEOUT_MS) {
            viewModel.state.first { it is DayState.Closed } as DayState.Closed
        }
        assertEquals(1, closed.summary.number)
        assertEquals(2, closed.summary.nextNumber)
        assertTrue("объяснение обязано быть", closed.summary.headline.isNotBlank())
        // Три строки итогов — те же три условия, за которые даются очки роста (R10).
        assertEquals(3, closed.summary.checks.size)
        assertTrue("у каждой строки обязано быть пояснение", closed.summary.checks.all { it.text.isNotBlank() })
    }

    /**
     * Найдено вживую: после закрытия ребёнок возвращался на главный экран, а
     * тот уже был создан и доход новому дню не начислял. Баланс оставался
     * вчерашним до перезапуска приложения.
     */
    @Test
    fun новый_день_сразу_с_доходом() = runBlocking {
        startDay()
        withTimeout(TIMEOUT_MS) { viewModel.state.first { it is DayState.Running } }

        viewModel.close()
        val closed = withTimeout(TIMEOUT_MS) {
            viewModel.state.first { it is DayState.Closed } as DayState.Closed
        }

        val next = periods.current(profileId)!!
        assertEquals(2, next.number)
        assertEquals(closed.summary.carryOver, next.startBalance)
        assertEquals(next.startBalance + next.income, periods.balance(next))
    }

    @Test
    fun второе_нажатие_не_закрывает_следующий_день() = runBlocking {
        startDay()
        withTimeout(TIMEOUT_MS) { viewModel.state.first { it is DayState.Running } }

        viewModel.close()
        withTimeout(TIMEOUT_MS) { viewModel.state.first { it is DayState.Closed } }
        viewModel.close()

        assertEquals(2, periods.count(profileId))
        assertEquals(2, periods.current(profileId)!!.number)
    }

    private suspend fun startDay() {
        val period = openPeriod()(profileId)
        periods.savePlan(period.id, BudgetPlan(Coins(40), Coins(20), Coins(20)))
        periods.save(periodEngine().confirmPlan(period))
    }

    private fun openPeriod() = OpenPeriodIfNeeded(
        periods = periods,
        wallet = WalletEngine(clock),
        balance = balance,
    )

    private fun periodEngine() = PeriodEngine(
        budget = BudgetEngine(),
        pet = PetStateEngine(balance),
        growth = GrowthEngine(balance),
        balance = balance,
        clock = clock,
    )

    private fun content(): ContentRepository = object : ContentRepository {
        override fun pack() = ContentPack(
            balance = balance,
            pets = PetOptions(
                bodies = listOf(ContentOption("owl", "pet.body.owl")),
                colors = listOf(testColor()),
                accessories = emptyList(),
            ),
            shop = emptyList(),
            goals = emptyList(),
            tasks = emptyList(),
            glossary = emptyList(),
            texts = mapOf(
                "period.missed_mandatory" to "Еда осталась в магазине.",
                "period.closed" to "День завершён.",
                "growth.no_points" to "В этот раз питомец отдыхает.",
            ),
        )
    }

    private companion object {
        const val FIXED_TIME = 1_700_000_000_000L
        const val TIMEOUT_MS = 5_000L
    }
}
