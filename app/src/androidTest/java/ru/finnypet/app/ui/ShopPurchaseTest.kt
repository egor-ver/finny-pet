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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ru.finnypet.app.data.local.FinnyDatabase
import ru.finnypet.app.data.repository.OutcomeRecorderImpl
import ru.finnypet.app.data.repository.PeriodRepositoryImpl
import ru.finnypet.app.data.repository.ProfileRepositoryImpl
import ru.finnypet.app.data.repository.SavingsRepositoryImpl
import ru.finnypet.app.domain.content.ContentOption
import ru.finnypet.app.domain.content.ContentPack
import ru.finnypet.app.domain.content.PetOptions
import ru.finnypet.app.domain.economy.BudgetEngine
import ru.finnypet.app.domain.economy.GameBalance
import ru.finnypet.app.domain.economy.GameClock
import ru.finnypet.app.domain.economy.GrowthEngine
import ru.finnypet.app.domain.economy.PeriodEngine
import ru.finnypet.app.domain.economy.PetStateEngine
import ru.finnypet.app.domain.economy.WalletEngine
import ru.finnypet.app.domain.model.Change
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.GoalId
import ru.finnypet.app.domain.model.GoalProgress
import ru.finnypet.app.domain.model.ItemId
import ru.finnypet.app.domain.model.PetAppearance
import ru.finnypet.app.domain.model.PetState
import ru.finnypet.app.domain.model.PetEffect
import ru.finnypet.app.domain.model.PetStatKind
import ru.finnypet.app.domain.model.ProfileId
import ru.finnypet.app.domain.model.RecoveryOption
import ru.finnypet.app.domain.model.ShopItem
import ru.finnypet.app.domain.model.SpendCategory
import ru.finnypet.app.domain.model.Stat
import ru.finnypet.app.domain.model.TransactionType
import ru.finnypet.app.domain.repository.ContentRepository
import ru.finnypet.app.domain.usecase.OpenPeriodIfNeeded
import ru.finnypet.app.ui.screens.shop.PurchaseOutcome
import ru.finnypet.app.ui.screens.shop.ShopState
import ru.finnypet.app.ui.screens.shop.ShopViewModel
import java.io.File

/**
 * Проверяет покупку целиком: от нажатия до операции в базе и изменённого
 * питомца (ТЗ 2.5.6).
 *
 * Экранные тесты показывают вёрстку на выдуманном состоянии, а здесь работает
 * настоящая цепочка — вьюмодель, репозитории, SQLite. Без неё осталось бы
 * непроверенным главное: что деньги списываются ровно один раз, а отказ не
 * трогает ни баланс, ни питомца.
 */
@RunWith(AndroidJUnit4::class)
class ShopPurchaseTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val balance = GameBalance.PLACEHOLDER
    private val clock = GameClock { FIXED_TIME }

    private val food = ShopItem(
        id = ItemId("food"),
        titleKey = "shop.food",
        price = Coins(12),
        category = SpendCategory.MANDATORY,
        effects = listOf(PetEffect(PetStatKind.SATIETY, 20)),
    )
    private val toy = ShopItem(
        id = ItemId("toy"),
        titleKey = "shop.toy",
        price = Coins(18),
        category = SpendCategory.OPTIONAL,
        effects = listOf(PetEffect(PetStatKind.MOOD, 15)),
    )

    /** Дороже всего дня: 20 + 60 = 80 — не хватит. */
    private val castle = ShopItem(
        id = ItemId("castle"),
        titleKey = "shop.castle",
        price = Coins(100),
        category = SpendCategory.OPTIONAL,
    )

    /** Хватает на одну покупку, на две — нет. */
    private val swing = ShopItem(
        id = ItemId("swing"),
        titleKey = "shop.swing",
        price = Coins(50),
        category = SpendCategory.OPTIONAL,
    )

    /** Обязательное и не по карману: сюда домен предлагает копилку. */
    private val vet = ShopItem(
        id = ItemId("vet"),
        titleKey = "shop.vet",
        price = Coins(100),
        category = SpendCategory.MANDATORY,
        effects = listOf(PetEffect(PetStatKind.CARE, 30)),
    )

    private lateinit var storeFile: File
    private lateinit var store: DataStore<Preferences>
    private lateinit var db: FinnyDatabase
    private lateinit var profiles: ProfileRepositoryImpl
    private lateinit var periods: PeriodRepositoryImpl
    private lateinit var savings: SavingsRepositoryImpl
    private lateinit var viewModel: ShopViewModel
    private var profileId: ProfileId = ProfileId("не создан")

    @Before
    fun setUp() = runBlocking {
        storeFile = File(context.cacheDir, "shop-${System.nanoTime()}.preferences_pb")
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
        savings = SavingsRepositoryImpl(goals = db.goalProgress(), transactions = db.transactions())
        viewModel = ShopViewModel(
            profiles = profiles,
            periods = periods,
            savings = savings,
            openPeriod = OpenPeriodIfNeeded(
                periods = periods,
                wallet = WalletEngine(clock),
                balance = balance,
            ),
            wallet = WalletEngine(clock),
            recorder = OutcomeRecorderImpl(database = db, petState = PetStateEngine(balance), clock = clock),
            balance = balance,
            content = content(),
        )
    }

    @After
    fun tearDown() {
        // Сначала останавливаем вьюмодель: её подписка на базу переживает
        // закрытие и падает на «Database is closed».
        // Отменить и дождаться: отмена не прерывает запрос, который уже ушёл
        // в SQLite, и закрытая под ним база уронила бы весь прогон.
        viewModel.viewModelScope.cancel()
        runBlocking { viewModel.viewModelScope.coroutineContext[Job]?.join() }
        db.close()
        storeFile.delete()
    }

    @Test
    fun товары_показываются_с_названиями_из_контента() = runBlocking {
        val ready = awaitReady()

        assertEquals(
            listOf("Вкусная каша", "Яркий мячик", "Замок", "Качели", "Ветеринар"),
            ready.items.map { it.title },
        )
        assertEquals(balance.startingBalance + balance.periodIncome, ready.balance)
    }

    /** ТЗ 2.5.5: пока сумма распределяется, тратить её нельзя. */
    @Test
    fun пока_день_планируется_покупка_не_проходит() = runBlocking {
        val planning = awaitReady()
        assertEquals(false, planning.canBuy)

        viewModel.buy(food.id)

        // Отказа тоже нет: экран сам не даёт нажать. Ждём, чтобы поймать
        // покупку, если бы она всё-таки прошла.
        assertNull(settle().outcome)
        assertEquals(emptyList<TransactionType>(), purchases())
    }

    @Test
    fun покупка_списывает_деньги_и_меняет_питомца() = runBlocking {
        startDay()
        val before = awaitReady()

        viewModel.buy(food.id)

        val done = awaitOutcome<PurchaseOutcome.Done>()
        assertEquals("Вкусная каша", done.title)
        assertEquals("Осталось 68 монет.", done.text)
        assertEquals(food.effects, done.effects)
        val initial = Stat(balance.initialStat)
        assertEquals(
            listOf(Change.PetStat(PetStatKind.SATIETY, from = initial, to = initial + 20)),
            done.changes,
        )

        val after = await { it.balance == before.balance - food.price }
        assertEquals(Coins(68), after.balance)

        val period = periods.current(profileId)!!
        val purchase = periods.transactions(period.id).single { !it.type.isIncome }
        assertEquals(TransactionType.PURCHASE_MANDATORY, purchase.type)
        assertEquals(food.price, purchase.amount)
        assertEquals(food.id, purchase.itemId)

        val pet = profiles.pet(profileId)!!
        assertEquals(Stat(balance.initialStat + 20), pet.state.satiety)
        assertEquals(Stat(balance.initialStat), pet.state.mood)
    }

    /**
     * ТЗ 2.5.9: объясняется то, что случилось на самом деле. Показатель у
     * верхней границы не растёт, и обещанные «+20» превращаются в ничего —
     * об этом и надо сказать, а не повторить обещание.
     */
    @Test
    fun у_границы_показателя_изменений_нет() = runBlocking {
        startDay()
        val pet = profiles.pet(profileId)!!
        profiles.savePet(profileId, pet.state.with(PetStatKind.SATIETY, Stat.MAX), pet.growth)
        awaitReady()

        viewModel.buy(food.id)

        val done = awaitOutcome<PurchaseOutcome.Done>()
        assertEquals(food.effects, done.effects)
        assertEquals(emptyList<Change.PetStat>(), done.changes)
        assertEquals(Stat.MAX, profiles.pet(profileId)!!.state.satiety)
    }

    /** ТЗ 2.5.6: отказ объясняется и предлагает выход, но ничего не списывает. */
    @Test
    fun нехватка_объясняется_и_ничего_не_списывает() = runBlocking {
        startDay()
        val before = awaitReady()

        viewModel.buy(castle.id)

        val rejected = awaitOutcome<PurchaseOutcome.Rejected>()
        assertEquals("Замок", rejected.title)
        assertEquals("Не хватает 20 монет.", rejected.text)
        assertEquals(
            listOf(RecoveryOption.DO_TASK, RecoveryOption.POSTPONE_PURCHASE, RecoveryOption.CHOOSE_CHEAPER),
            rejected.options.map { it.option },
        )
        assertEquals("Выполнить задание", rejected.options.first().label)
        assertEquals(RecoveryOption.DO_TASK, rejected.recommended)

        assertEquals(before.balance, settle().balance)
        assertEquals(emptyList<TransactionType>(), purchases())
        assertEquals(Stat(balance.initialStat), profiles.pet(profileId)!!.state.mood)
    }

    /**
     * Копилка попадает в варианты выхода только для обязательного и только
     * если в ней хватает на недостачу — значит, вьюмодель обязана передать
     * домену настоящую сумму накоплений, а не ноль.
     */
    @Test
    fun при_нехватке_на_обязательное_предлагается_копилка() = runBlocking {
        startDay()
        savings.save(profileId, GoalProgress(goalId = GoalId("bike"), saved = Coins(30), isActive = true))
        awaitReady()

        viewModel.buy(vet.id)

        val rejected = awaitOutcome<PurchaseOutcome.Rejected>()
        assertEquals(
            listOf(RecoveryOption.DO_TASK, RecoveryOption.WITHDRAW_FROM_SAVINGS, RecoveryOption.CHOOSE_CHEAPER),
            rejected.options.map { it.option },
        )
        assertEquals("Взять из копилки", rejected.options[1].label)
    }

    /**
     * Два быстрых нажатия при деньгах на одну покупку. Экран отстаёт от
     * базы на время записи, и без очереди обе покупки прочитали бы полный
     * баланс — списалось бы дважды.
     */
    @Test
    fun две_быстрых_покупки_списывают_одну() = runBlocking {
        startDay()
        awaitReady()

        viewModel.buy(swing.id)
        viewModel.buy(swing.id)

        awaitOutcome<PurchaseOutcome.Rejected>()
        assertEquals(listOf(TransactionType.PURCHASE_OPTIONAL), purchases())
        assertEquals(Coins(30), settle().balance)
    }

    @Test
    fun итог_закрывается() = runBlocking {
        startDay()
        awaitReady()
        viewModel.buy(toy.id)
        awaitOutcome<PurchaseOutcome.Done>()

        viewModel.dismiss()

        assertNull(await { it.outcome == null }.outcome)
    }

    private suspend fun startDay() {
        val period = withTimeout(TIMEOUT_MS) {
            periods.observeCurrent(profileId).first { it != null }!!
        }
        periods.save(periodEngine().confirmPlan(period))
    }

    private suspend fun purchases(): List<TransactionType> {
        val period = periods.current(profileId)!!
        return periods.transactions(period.id).map { it.type }.filterNot { it.isIncome }
    }

    private suspend fun awaitReady(): ShopState.Ready = await { true }

    /**
     * Проверка «ничего не случилось» — единственная, где приходится ждать
     * по часам: нет события, которого можно дождаться.
     */
    private suspend fun settle(): ShopState.Ready {
        delay(SETTLE_MS)
        return awaitReady()
    }

    private suspend inline fun <reified T : PurchaseOutcome> awaitOutcome(): T =
        await { it.outcome is T }.outcome as T

    private suspend fun await(
        condition: (ShopState.Ready) -> Boolean,
    ): ShopState.Ready = withTimeout(TIMEOUT_MS) {
        viewModel.state.first { it is ShopState.Ready && condition(it) }
    } as ShopState.Ready

    private fun content(): ContentRepository = object : ContentRepository {
        override fun pack() = ContentPack(
            balance = balance,
            pets = PetOptions(
                bodies = listOf(ContentOption("owl", "pet.body.owl")),
                colors = listOf(ContentOption("cream", "pet.color.cream")),
                accessories = emptyList(),
            ),
            shop = listOf(food, toy, castle, swing, vet),
            goals = emptyList(),
            tasks = emptyList(),
            glossary = emptyList(),
            texts = mapOf(
                "shop.food" to "Вкусная каша",
                "shop.toy" to "Яркий мячик",
                "shop.castle" to "Замок",
                "shop.swing" to "Качели",
                "shop.vet" to "Ветеринар",
                "purchase.done" to "Осталось {balance} монет.",
                "purchase.rejected" to "Не хватает {shortfall} монет.",
                "recovery.DO_TASK" to "Выполнить задание",
                "recovery.POSTPONE_PURCHASE" to "Купить попозже",
                "recovery.WITHDRAW_FROM_SAVINGS" to "Взять из копилки",
                "recovery.CHOOSE_CHEAPER" to "Выбрать подешевле",
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
