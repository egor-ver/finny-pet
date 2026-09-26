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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ru.finnypet.app.data.local.FinnyDatabase
import ru.finnypet.app.data.repository.OutcomeRecorderImpl
import ru.finnypet.app.data.repository.PeriodRepositoryImpl
import ru.finnypet.app.data.repository.ProfileRepositoryImpl
import ru.finnypet.app.data.repository.SavingsRepositoryImpl
import ru.finnypet.app.data.repository.TaskProgressRepositoryImpl
import ru.finnypet.app.domain.content.ContentOption
import ru.finnypet.app.domain.content.ContentPack
import ru.finnypet.app.domain.content.PetOptions
import ru.finnypet.app.domain.economy.BudgetEngine
import ru.finnypet.app.domain.economy.GameBalance
import ru.finnypet.app.domain.economy.GameClock
import ru.finnypet.app.domain.economy.GrowthEngine
import ru.finnypet.app.domain.economy.PeriodEngine
import ru.finnypet.app.domain.economy.PetStateEngine
import ru.finnypet.app.domain.economy.SavingsEngine
import ru.finnypet.app.domain.economy.WalletEngine
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.Goal
import ru.finnypet.app.domain.model.GoalId
import ru.finnypet.app.domain.model.PetAppearance
import ru.finnypet.app.domain.model.ProfileId
import ru.finnypet.app.domain.model.TransactionType
import ru.finnypet.app.domain.repository.ContentRepository
import ru.finnypet.app.domain.usecase.OpenPeriodIfNeeded
import ru.finnypet.app.ui.screens.savings.SavingsDraft
import ru.finnypet.app.ui.screens.savings.SavingsState
import ru.finnypet.app.ui.screens.savings.SavingsViewModel
import java.io.File

/**
 * Копилка целиком: от выбора цели до операции в базе и пересчитанного срока
 * (ТЗ 2.5.7).
 *
 * Настоящая цепочка — вьюмодель, репозитории, SQLite. Здесь проверяется то,
 * что экранные тесты не видят: деньги уходят из кошелька в копилку ровно один
 * раз, снятие возвращает их, срок считается по средней сумме пополнений.
 */
@RunWith(AndroidJUnit4::class)
class SavingsFlowTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val balance = GameBalance.PLACEHOLDER
    private val clock = GameClock { FIXED_TIME }

    private val scooter = Goal(id = GoalId("scooter"), titleKey = "goal.scooter", price = Coins(30))
    private val book = Goal(id = GoalId("book"), titleKey = "goal.book", price = Coins(15))

    private lateinit var storeFile: File
    private lateinit var store: DataStore<Preferences>
    private lateinit var db: FinnyDatabase
    private lateinit var profiles: ProfileRepositoryImpl
    private lateinit var periods: PeriodRepositoryImpl
    private lateinit var savings: SavingsRepositoryImpl
    private lateinit var viewModel: SavingsViewModel
    private var profileId: ProfileId = ProfileId("не создан")

    @Before
    fun setUp() = runBlocking {
        storeFile = File(context.cacheDir, "savings-${System.nanoTime()}.preferences_pb")
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
        savings = SavingsRepositoryImpl(goals = db.goalProgress(), transactions = db.transactions())
        profileId = profiles.create(
            childName = "Егор",
            petName = "Пушок",
            appearance = PetAppearance(bodyId = "owl", colorId = "cream", accessoryId = null),
        ).id
        viewModel = SavingsViewModel(
            profiles = profiles,
            periods = periods,
            savings = savings,
            openPeriod = OpenPeriodIfNeeded(
                periods = periods,
                wallet = WalletEngine(clock),
                balance = balance,
            ),
            engine = SavingsEngine(clock),
            recorder = OutcomeRecorderImpl(database = db, petState = PetStateEngine(balance), taskProgress = TaskProgressRepositoryImpl(db.taskProgress(), clock)),
            petState = PetStateEngine(balance),
            content = content(),
        )
    }

    @After
    fun tearDown() {
        // Отменить и дождаться: отмена не прерывает запрос, который уже ушёл
        // в SQLite, и закрытая под ним база уронила бы весь прогон.
        viewModel.viewModelScope.cancel()
        runBlocking { viewModel.viewModelScope.coroutineContext[Job]?.join() }
        db.close()
        storeFile.delete()
    }

    @Test
    fun цели_показываются_с_названиями_и_ценами_без_выбранной() = runBlocking {
        val ready = awaitReady()

        assertEquals(listOf("Самокат", "Книжка"), ready.goals.map { it.title })
        assertEquals(listOf(Coins(30), Coins(15)), ready.goals.map { it.price })
        assertNull(ready.active)
        assertEquals(false, ready.canDeposit)
    }

    @Test
    fun выбор_цели_делает_её_активной() = runBlocking {
        awaitReady()

        viewModel.choose(scooter.id)

        val ready = await { it.active != null }
        assertEquals("Самокат", ready.active?.title)
        assertEquals(Coins.ZERO, ready.active?.saved)
        assertEquals(Coins(30), ready.active?.remaining)
        assertNull("пополнений не было — срок считать не из чего", ready.periodsToGoal)
    }

    /** ТЗ 2.5.5: пока сумма распределяется, в копилку её не отложить. */
    @Test
    fun пока_день_планируется_отложить_нельзя() = runBlocking {
        awaitReady()
        viewModel.choose(scooter.id)
        val planning = await { it.active != null }
        assertEquals(false, planning.canOperate)
        assertEquals(false, planning.canDeposit)

        viewModel.startDeposit()

        assertNull(settle().draft)
    }

    @Test
    fun пополнение_переносит_монеты_из_кошелька_в_копилку() = runBlocking {
        startDay()
        awaitReady()
        viewModel.choose(scooter.id)
        val before = await { it.active != null }

        viewModel.startDeposit()
        val draft = await { it.draft is SavingsDraft.Deposit }.draft as SavingsDraft.Deposit
        assertEquals(Coins(5), draft.amount)
        assertEquals(before.balance, draft.max)

        viewModel.add()
        await { it.draft?.amount == Coins(10) }
        viewModel.confirm()

        val after = await { it.outcome != null && it.active?.saved == Coins(10) }
        assertEquals("Отложили 10, всего 10, осталось 20.", after.outcome?.text)
        assertEquals(false, after.outcome?.goalReached)
        assertEquals(before.balance - Coins(10), after.balance)
        assertNull("черновик закрыт после подтверждения", after.draft)

        val period = periods.current(profileId)!!
        val deposit = periods.transactions(period.id).single { it.type == TransactionType.SAVINGS_DEPOSIT }
        assertEquals(Coins(10), deposit.amount)
        assertEquals(scooter.id, deposit.goalId)
    }

    /** ТЗ 2.5.7: срок считается по средней сумме регулярного пополнения. */
    @Test
    fun срок_считается_по_среднему_пополнению() = runBlocking {
        startDay()
        awaitReady()
        viewModel.choose(scooter.id)
        await { it.active != null }

        deposit(Coins(10))
        // Остаток 20, средний взнос 10 — два дня.
        val first = await { it.active?.saved == Coins(10) && it.outcome == null }
        assertEquals(2, first.periodsToGoal)

        deposit(Coins(5))
        // Остаток 15, средний взнос (10 + 5) / 2 = 7 — три дня с округлением вверх.
        val second = await { it.active?.saved == Coins(15) && it.outcome == null }
        assertEquals(3, second.periodsToGoal)
    }

    @Test
    fun цель_собирается_и_об_этом_говорится() = runBlocking {
        startDay()
        awaitReady()
        viewModel.choose(book.id)
        await { it.active != null }

        val outcome = deposit(Coins(15)).outcome

        assertEquals("Ура, собрали 15!", outcome?.text)
        assertEquals(true, outcome?.goalReached)
        val reached = await { it.active?.saved == Coins(15) }
        assertEquals(true, reached.active?.isReached)
        assertEquals(0, reached.periodsToGoal)
    }

    /**
     * ТЗ 2.5.7: снятие — после отдельного подтверждения, и до него видно,
     * сколько останется и как отодвинется срок.
     */
    @Test
    fun снятие_показывает_превью_и_возвращает_монеты_в_кошелёк() = runBlocking {
        startDay()
        awaitReady()
        viewModel.choose(scooter.id)
        await { it.active != null }
        deposit(Coins(10))
        val saved = await { it.active?.saved == Coins(10) && it.outcome == null }

        viewModel.startWithdraw()

        val draft = await { it.draft is SavingsDraft.Withdraw }.draft as SavingsDraft.Withdraw
        assertEquals(Coins(5), draft.amount)
        assertEquals(Coins(10), draft.max)
        assertEquals(Coins(10), draft.preview.savingsBefore)
        assertEquals(Coins(5), draft.preview.savingsAfter)
        assertEquals(2, draft.preview.periodsBefore)
        assertEquals(3, draft.preview.periodsAfter)

        // Ничего не списано, пока не подтверждено.
        assertEquals(Coins(10), savings.activeProgress(profileId)?.saved)

        viewModel.confirm()

        val after = await { it.active?.saved == Coins(5) }
        assertEquals("Взяли 5, осталось 5.", after.outcome?.text)
        assertEquals(saved.balance + Coins(5), after.balance)
        val period = periods.current(profileId)!!
        assertTrue(periods.transactions(period.id).any { it.type == TransactionType.SAVINGS_WITHDRAW })
    }

    @Test
    fun снять_больше_накопленного_нельзя() = runBlocking {
        startDay()
        awaitReady()
        viewModel.choose(scooter.id)
        await { it.active != null }
        deposit(Coins(5))
        await { it.active?.saved == Coins(5) && it.outcome == null }

        viewModel.startWithdraw()
        val draft = await { it.draft is SavingsDraft.Withdraw }.draft!!
        assertEquals(Coins(5), draft.amount)
        assertEquals(false, draft.canAdd)

        viewModel.add()

        assertEquals(Coins(5), settle().draft?.amount)
    }

    /** Два быстрых подтверждения одного черновика дают одно пополнение. */
    @Test
    fun двойное_подтверждение_откладывает_один_раз() = runBlocking {
        startDay()
        awaitReady()
        viewModel.choose(scooter.id)
        await { it.active != null }
        viewModel.startDeposit()
        await { it.draft != null }

        viewModel.confirm()
        viewModel.confirm()

        val after = await { it.outcome != null && it.active?.saved == Coins(5) }
        assertNull("второе подтверждение не открыло новый черновик", settle().draft)
        assertEquals(Coins(5), settle().active?.saved)
        assertEquals(after.balance, settle().balance)
        val period = periods.current(profileId)!!
        assertEquals(1, periods.transactions(period.id).count { it.type == TransactionType.SAVINGS_DEPOSIT })
    }

    /** Черновик считался по прежней цели — при смене цели он закрывается. */
    @Test
    fun смена_цели_закрывает_черновик() = runBlocking {
        startDay()
        awaitReady()
        viewModel.choose(scooter.id)
        await { it.active?.id == scooter.id }
        viewModel.startDeposit()
        await { it.draft != null }

        viewModel.choose(book.id)

        val after = await { it.active?.id == book.id }
        assertNull(after.draft)
        assertEquals(Coins.ZERO, after.active?.saved)
    }

    @Test
    fun отмена_закрывает_черновик_без_операции() = runBlocking {
        startDay()
        awaitReady()
        viewModel.choose(scooter.id)
        await { it.active != null }
        viewModel.startDeposit()
        await { it.draft != null }

        viewModel.cancel()

        val after = await { it.draft == null }
        assertEquals(Coins.ZERO, after.active?.saved)
    }

    /**
     * Цели без тупика (L5, Б7): пока самокат не куплен, вернуться к купленной
     * книжке нельзя; когда куплено всё — это единственный способ копить
     * дальше. Повторная покупка не плодит книжку в списке купленных (Б8) и
     * возвращает сдачу в кошелёк, а выбор цели переживает перезапуск.
     */
    @Test
    fun цели_без_тупика_повторный_выбор_и_повторная_покупка() = runBlocking {
        startDay()
        awaitReady()

        // Книжка куплена, самокат — ещё нет.
        viewModel.choose(book.id)
        await { it.active?.id == book.id }
        deposit(Coins(15))
        await { it.active?.isReached == true }
        buy()

        // Некупленный самокат есть — вернуться к книжке нельзя.
        viewModel.choose(scooter.id)
        await { it.active?.id == scooter.id }
        viewModel.choose(book.id)
        assertEquals(
            "самокат ещё не куплен — выбор не должен смениться на книжку",
            scooter.id,
            settle().active?.id,
        )

        // Купить и самокат — некупленных больше нет.
        deposit(Coins(30))
        await { it.active?.isReached == true }
        buy()

        // Книжку снова можно выбрать — свежий прогресс, а не тупик.
        viewModel.choose(book.id)
        val repeat = await { it.active?.id == book.id }
        assertEquals(Coins.ZERO, repeat.active?.saved)

        // Перезапуск: новая вьюмодель на той же базе видит тот же выбор.
        val restarted = SavingsViewModel(
            profiles = profiles,
            periods = periods,
            savings = savings,
            openPeriod = OpenPeriodIfNeeded(periods = periods, wallet = WalletEngine(clock), balance = balance),
            engine = SavingsEngine(clock),
            recorder = OutcomeRecorderImpl(
                database = db,
                petState = PetStateEngine(balance),
                taskProgress = TaskProgressRepositoryImpl(db.taskProgress(), clock),
            ),
            petState = PetStateEngine(balance),
            content = content(),
        )
        val restartedReady = withTimeout(TIMEOUT_MS) {
            restarted.state.first { it is SavingsState.Ready } as SavingsState.Ready
        }
        assertEquals(book.id, restartedReady.active?.id)
        restarted.viewModelScope.cancel()
        restarted.viewModelScope.coroutineContext[Job]?.join()

        // Отложить до цены, снять часть, доложить с запасом и купить второй раз.
        deposit(Coins(15))
        await { it.active?.saved == Coins(15) && it.outcome == null }
        withdraw(Coins(5))
        await { it.active?.saved == Coins(10) && it.outcome == null }
        deposit(Coins(10))
        val readyToBuy = await { it.active?.saved == Coins(20) && it.outcome == null }

        val afterBuy = buy()

        assertEquals("сдача 5 монет вернулась в кошелёк", readyToBuy.balance + Coins(5), afterBuy.balance)
        val period = periods.current(profileId)!!
        val bookPurchases = periods.transactions(period.id)
            .filter { it.type == TransactionType.GOAL_PURCHASE && it.goalId == book.id }
        assertEquals("в истории — обе покупки книжки", 2, bookPurchases.size)
        assertEquals(
            "в списке купленных книжка — одна вещь, а не дубль",
            listOf(book.id, scooter.id),
            savings.observeBought(profileId).first(),
        )
    }

    /**
     * Набирает сумму по лесенке, подтверждает и закрывает итог. Возвращает
     * состояние с итогом: прогресс в нём может быть ещё старым — итог приходит
     * из вьюмодели сразу, а прогресс из базы чуть позже, и его ждут отдельно.
     */
    private suspend fun deposit(amount: Coins): SavingsState.Ready {
        viewModel.startDeposit()
        var current = await { it.draft is SavingsDraft.Deposit }.draft!!.amount
        while (current < amount) {
            viewModel.add()
            current = await { it.draft != null && it.draft!!.amount > current }.draft!!.amount
        }
        viewModel.confirm()
        val done = await { it.outcome != null }
        viewModel.dismissOutcome()
        await { it.outcome == null }
        return done
    }

    /** То же самое, что [deposit], но для снятия — с тем же превью-подтверждением. */
    private suspend fun withdraw(amount: Coins): SavingsState.Ready {
        viewModel.startWithdraw()
        var current = await { it.draft is SavingsDraft.Withdraw }.draft!!.amount
        while (current < amount) {
            viewModel.add()
            current = await { it.draft != null && it.draft!!.amount > current }.draft!!.amount
        }
        viewModel.confirm()
        val done = await { it.outcome != null }
        viewModel.dismissOutcome()
        await { it.outcome == null }
        return done
    }

    /** Покупает собранную цель и закрывает итог, дожидаясь, чтобы прогресс в базе уже сбросился. */
    private suspend fun buy(): SavingsState.Ready {
        viewModel.buy()
        val done = await { it.outcome != null && it.active == null }
        viewModel.dismissOutcome()
        await { it.outcome == null }
        return done
    }

    private suspend fun startDay() {
        val period = withTimeout(TIMEOUT_MS) {
            periods.observeCurrent(profileId).first { it != null }!!
        }
        periods.save(periodEngine().confirmPlan(period))
    }

    private suspend fun awaitReady(): SavingsState.Ready = await { true }

    /** Проверка «ничего не случилось» — единственная, где приходится ждать по часам. */
    private suspend fun settle(): SavingsState.Ready {
        delay(SETTLE_MS)
        return awaitReady()
    }

    private suspend fun await(
        condition: (SavingsState.Ready) -> Boolean,
    ): SavingsState.Ready = withTimeout(TIMEOUT_MS) {
        viewModel.state.first { it is SavingsState.Ready && condition(it) }
    } as SavingsState.Ready

    private fun content(): ContentRepository = object : ContentRepository {
        override fun pack() = ContentPack(
            balance = balance,
            pets = PetOptions(
                bodies = listOf(ContentOption("owl", "pet.body.owl")),
                colors = listOf(testColor()),
                accessories = emptyList(),
            ),
            shop = emptyList(),
            goals = listOf(scooter, book),
            tasks = emptyList(),
            glossary = emptyList(),
            texts = mapOf(
                "goal.scooter" to "Самокат",
                "goal.book" to "Книжка",
                "savings.deposited" to "Отложили {amount}, всего {saved}, осталось {remaining}.",
                "savings.goal_reached" to "Ура, собрали {saved}!",
                "savings.withdrawn" to "Взяли {amount}, осталось {saved}.",
            ),
        )
    }

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
        const val SETTLE_MS = 300L
    }
}
