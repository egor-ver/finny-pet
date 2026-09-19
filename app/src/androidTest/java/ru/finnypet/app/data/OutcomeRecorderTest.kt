package ru.finnypet.app.data

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ru.finnypet.app.data.local.FinnyDatabase
import ru.finnypet.app.data.repository.OutcomeRecorderImpl
import ru.finnypet.app.data.repository.PeriodRepositoryImpl
import ru.finnypet.app.data.repository.ProfileRepositoryImpl
import ru.finnypet.app.data.repository.SavingsRepositoryImpl
import ru.finnypet.app.data.repository.TaskProgressRepositoryImpl
import ru.finnypet.app.domain.economy.GameBalance
import ru.finnypet.app.domain.economy.GameClock
import ru.finnypet.app.domain.economy.PetStateEngine
import ru.finnypet.app.domain.model.Change
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.GamePeriod
import ru.finnypet.app.domain.model.GoalId
import ru.finnypet.app.domain.model.GoalProgress
import ru.finnypet.app.domain.model.PeriodStatus
import ru.finnypet.app.domain.model.PetAppearance
import ru.finnypet.app.domain.model.PetEffect
import ru.finnypet.app.domain.model.PetStatKind
import ru.finnypet.app.domain.model.ProfileId
import ru.finnypet.app.domain.model.Stat
import ru.finnypet.app.domain.model.TaskCompletion
import ru.finnypet.app.domain.model.TaskId
import ru.finnypet.app.domain.model.Transaction
import ru.finnypet.app.domain.model.TransactionType
import ru.finnypet.app.domain.repository.ActionOutcome
import java.io.File

/**
 * Последствия действия пишутся вместе или не пишутся вовсе.
 *
 * Магазин, копилка и задания трогают разные таблицы; ТЗ 2.5.9 требует,
 * чтобы после действия менялись баланс, накопления и показатель вместе.
 * Здесь проверяется именно «вместе»: сбой на последней записи откатывает
 * все предыдущие.
 */
@RunWith(AndroidJUnit4::class)
class OutcomeRecorderTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val balance = GameBalance.PLACEHOLDER
    private val clock = GameClock { FIXED_TIME }

    private lateinit var storeFile: File
    private lateinit var store: DataStore<Preferences>
    private lateinit var db: FinnyDatabase
    private lateinit var profiles: ProfileRepositoryImpl
    private lateinit var periods: PeriodRepositoryImpl
    private lateinit var savings: SavingsRepositoryImpl
    private lateinit var tasks: TaskProgressRepositoryImpl
    private lateinit var recorder: OutcomeRecorderImpl
    private var profileId: ProfileId = ProfileId("не создан")
    private var periodId: Long = 0

    @Before
    fun setUp() = runBlocking {
        storeFile = File(context.cacheDir, "recorder-${System.nanoTime()}.preferences_pb")
        store = PreferenceDataStoreFactory.create { storeFile }
        db = Room.inMemoryDatabaseBuilder(context, FinnyDatabase::class.java).build()
        profiles = ProfileRepositoryImpl(database = db, store = store, balance = balance, clock = clock)
        periods = PeriodRepositoryImpl(periods = db.periods(), plans = db.budgetPlans(), transactions = db.transactions())
        savings = SavingsRepositoryImpl(goals = db.goalProgress(), transactions = db.transactions())
        tasks = TaskProgressRepositoryImpl(tasks = db.taskProgress(), clock = clock)
        recorder = OutcomeRecorderImpl(database = db, petState = PetStateEngine(balance), taskProgress = tasks)

        profileId = profiles.create(
            childName = "Егор",
            petName = "Пушок",
            appearance = PetAppearance(bodyId = "owl", colorId = "cream", accessoryId = null),
        ).id
        periodId = periods.open(
            GamePeriod(
                id = 0,
                profileId = profileId,
                number = 1,
                income = balance.periodIncome,
                startBalance = balance.startingBalance,
                status = PeriodStatus.RUNNING,
            )
        ).id
    }

    @After
    fun tearDown() {
        db.close()
        storeFile.delete()
    }

    @Test
    fun пишет_операцию_питомца_копилку_и_задание_вместе() = runBlocking {
        val changes = recorder.record(
            profileId,
            ActionOutcome(
                transaction = deposit(periodId),
                effects = listOf(PetEffect(PetStatKind.MOOD, 10)),
                savings = GoalProgress(goalId = GoalId("bike"), saved = Coins(5), isActive = true),
                taskCompletion = TaskCompletion(taskId = TaskId("plan"), outcomeId = "saved", reward = Coins(15)),
            ),
        )

        val initial = Stat(balance.initialStat)
        assertEquals(listOf(Change.PetStat(PetStatKind.MOOD, from = initial, to = initial + 10)), changes)
        assertEquals(initial + 10, profiles.pet(profileId)!!.state.mood)
        assertEquals(TransactionType.SAVINGS_DEPOSIT, periods.transactions(periodId).single().type)
        assertEquals(Coins(5), savings.activeProgress(profileId)?.saved)
        assertEquals(setOf(TaskId("plan")), tasks.completedIds(profileId))
    }

    /** Операция пишется последней: её сбой обязан откатить всё, что записано до неё. */
    @Test
    fun сбой_последней_записи_откатывает_остальные() = runBlocking {
        val bogusPeriod = periodId + 1_000

        try {
            recorder.record(
                profileId,
                ActionOutcome(
                    transaction = deposit(bogusPeriod),
                    effects = listOf(PetEffect(PetStatKind.MOOD, 10)),
                    savings = GoalProgress(goalId = GoalId("bike"), saved = Coins(5), isActive = true),
                    taskCompletion = TaskCompletion(taskId = TaskId("plan"), outcomeId = "saved", reward = Coins(15)),
                ),
            )
            fail("операция с чужим периодом должна была отвергнуться")
        } catch (_: SQLiteConstraintException) {
            // Ожидаемо: внешний ключ на период.
        }

        assertEquals(Stat(balance.initialStat), profiles.pet(profileId)!!.state.mood)
        assertNull(savings.activeProgress(profileId))
        assertTrue(tasks.completedIds(profileId).isEmpty())
        assertTrue(periods.transactions(periodId).isEmpty())
    }

    @Test
    fun эффект_у_границы_показателя_не_даёт_изменений() = runBlocking {
        val pet = profiles.pet(profileId)!!
        profiles.savePet(profileId, pet.state.with(PetStatKind.MOOD, Stat.MAX), pet.growth)

        val changes = recorder.record(profileId, ActionOutcome(effects = listOf(PetEffect(PetStatKind.MOOD, 10))))

        assertEquals(emptyList<Change.PetStat>(), changes)
        assertEquals(Stat.MAX, profiles.pet(profileId)!!.state.mood)
    }

    @Test
    fun пустой_итог_ничего_не_пишет() = runBlocking {
        val changes = recorder.record(profileId, ActionOutcome())

        assertEquals(emptyList<Change.PetStat>(), changes)
        assertTrue(periods.transactions(periodId).isEmpty())
    }

    private fun deposit(period: Long) = Transaction(
        id = 0,
        periodId = period,
        type = TransactionType.SAVINGS_DEPOSIT,
        amount = Coins(5),
        reasonKey = "savings.deposited",
        createdAt = FIXED_TIME,
        goalId = GoalId("bike"),
    )

    private companion object {
        const val FIXED_TIME = 1_700_000_000_000L
    }
}
