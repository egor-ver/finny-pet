package ru.finnypet.app.ui.screens.main

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import ru.finnypet.app.domain.economy.GameBalance
import ru.finnypet.app.domain.economy.PeriodEngine
import ru.finnypet.app.domain.economy.PetStateEngine
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.CompletedTask
import ru.finnypet.app.domain.model.GamePeriod
import ru.finnypet.app.domain.model.Goal
import ru.finnypet.app.domain.model.GoalId
import ru.finnypet.app.domain.model.GoalProgress
import ru.finnypet.app.domain.model.GrowthStage
import ru.finnypet.app.domain.model.ItemId
import ru.finnypet.app.domain.model.Pet
import ru.finnypet.app.domain.model.PetState
import ru.finnypet.app.domain.model.PetStatKind
import ru.finnypet.app.domain.model.Profile
import ru.finnypet.app.domain.model.ShopItem
import ru.finnypet.app.domain.model.SpendCategory
import ru.finnypet.app.domain.model.TaskId
import ru.finnypet.app.domain.model.TaskTopic
import ru.finnypet.app.domain.model.Transaction
import ru.finnypet.app.domain.model.totalPrice
import ru.finnypet.app.domain.repository.ContentRepository
import ru.finnypet.app.domain.repository.PeriodRepository
import ru.finnypet.app.domain.repository.ProfileRepository
import ru.finnypet.app.domain.repository.SavingsRepository
import ru.finnypet.app.domain.repository.TaskProgressRepository
import ru.finnypet.app.domain.usecase.OpenPeriodIfNeeded
import ru.finnypet.app.domain.usecase.TaskSchedule
import ru.finnypet.app.ui.components.OwlLook
import ru.finnypet.app.ui.components.owlDescription
import ru.finnypet.app.ui.components.owlLook
import ru.finnypet.app.ui.components.wellbeing
import ru.finnypet.app.ui.screens.ProfileViewModel
import ru.finnypet.app.ui.text.textOf
import javax.inject.Inject

/** Копилка: сколько отложено и на что копим. Цели может не быть — это норма. */
data class SavingsView(
    val saved: Coins,
    val goalTitle: String? = null,
    val price: Coins? = null,
)

/**
 * Задание дня (ТЗ 2.5.3: активное задание видно на главном). Заголовка у
 * задания нет — только вступление, поэтому показывается его начало и тема.
 * `null` — заданий в контент-паке нет.
 */
data class TaskOfDay(
    val id: TaskId,
    val topic: TaskTopic,
    val intro: String,
    /** Остался ли на сегодня лимит наград: «+10» или галочка «получено». */
    val rewardAvailable: Boolean,
    /** Все задания уже пройдены — предлагается повторить давнее всех. */
    val allDone: Boolean,
    /** Сколько дадут за первую верную попытку дня (R8). */
    val reward: Coins,
)

/**
 * Что показывает главный экран.
 *
 * [Failed] отдельно от [Loading]: если игровой день не удалось начать, экран
 * обязан объяснить это и дать выход, а не крутиться вечно (ТЗ 3.4).
 */
sealed interface MainState {

    data object Loading : MainState

    data object Failed : MainState

    data class Ready(
        val petName: String,
        val owl: OwlLook,
        val stage: GrowthStage,
        val stats: PetState,
        /** Чего сове не хватает: у показателя подпись «нужно». */
        val needs: List<PetStatKind>,
        /** Фраза совы в облачке — уже готовый текст из контент-пака. */
        val phrase: String,
        /** `null` — сова взрослая. */
        val growth: GrowthView?,
        val balance: Coins,
        /** Откуда пришли и куда ушли монеты за день — для «Кошелька сегодня». */
        val wallet: List<WalletLine>,
        /** `null` — план ещё не подтверждён, монеты не разложены. */
        val jars: JarsLeft?,
        val savings: SavingsView,
        val task: TaskOfDay?,
        val step: NextStep,
    ) : MainState
}

/**
 * Собирает главный экран (ТЗ 2.5.3): питомец, баланс, накопления, цель и
 * показатели состояния должны быть видны одновременно, поэтому состояние
 * склеивается из всех источников сразу, а не подгружается по частям.
 *
 * Названия целей берутся из контент-пака здесь: экран получает готовый текст
 * и ничего не знает про ключи.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    profiles: ProfileRepository,
    private val periods: PeriodRepository,
    private val savings: SavingsRepository,
    private val taskProgress: TaskProgressRepository,
    private val openPeriod: OpenPeriodIfNeeded,
    private val periodEngine: PeriodEngine,
    private val petState: PetStateEngine,
    private val balance: GameBalance,
    content: ContentRepository,
) : ProfileViewModel(profiles) {

    private val goals: Map<GoalId, Goal> = content.pack().goals.associateBy { it.id }
    private val tasks = content.pack().tasks
    private val shop = content.pack().shop
    private val shopItems: Map<ItemId, ShopItem> = shop.associateBy { it.id }
    private val pets = content.pack().pets
    private val cheapestMandatory: Coins? = shop
        .filter { it.category == SpendCategory.MANDATORY }
        .minOfOrNull { it.price }
    private val texts: Map<String, String> = content.pack().texts

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<MainState> =
        combine(profiles.observeActive(), failed) { profile, isFailed -> profile to isFailed }
            .flatMapLatest { (profile, isFailed) ->
                when {
                    isFailed -> flowOf(MainState.Failed)
                    profile == null -> flowOf(MainState.Loading)
                    else -> ready(profile)
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = MainState.Loading,
            )

    init {
        // Игровой день должен быть открыт до того, как экран покажет баланс:
        // без периода баланса не существует.
        act { openPeriod(it) }
    }

    /** Повтор после сбоя: ТЗ 3.4 запрещает экраны, с которых нет выхода. */
    fun retry() = retryWith { openPeriod(it) }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun ready(profile: Profile): Flow<MainState> = combine(
        profiles.observePet(profile.id),
        periods.observeCurrent(profile.id),
        savings.observeActive(profile.id),
        taskProgress.observeCompleted(profile.id),
    ) { pet, period, progress, completed -> Sources(pet, period, progress, completed) }
        .flatMapLatest { (pet, period, progress, completed) ->
            if (pet == null || period == null) {
                flowOf(MainState.Loading)
            } else {
                // Баланс, операции и план наблюдаются отдельно, потому что
                // зависят от периода: баланс считается по операциям, лимит
                // наград за задания и остатки по банкам — по ним же.
                combine(
                    periods.observeBalance(period),
                    periods.observeTransactions(period.id),
                    periods.observePlan(period.id),
                ) { wallet, transactions, plan ->
                    val task = taskOf(completed, transactions, pet.state)
                    val needs = petState.needsOf(pet.state)
                    val step = nextStep(period.status, needs.isNotEmpty(), wallet, cheapestMandatory)
                    val phrase = owlPhrase(
                        step = step,
                        needs = needs,
                        sadAbout = petState.sadAbout(pet.state),
                        cover = petState.cheapestCover(pet.state, shop)?.totalPrice(),
                        wallet = wallet,
                        reward = balance.taskReward.takeIf { task?.rewardAvailable == true },
                        income = balance.periodIncome,
                    )
                    MainState.Ready(
                        petName = profile.petName,
                        owl = owlOf(profile, pet),
                        stage = pet.growth.stage,
                        stats = pet.state,
                        needs = needs,
                        phrase = texts.textOf(phrase),
                        growth = growthOf(pet.growth, balance.growthThresholds),
                        balance = wallet,
                        wallet = walletLines(period.startBalance, transactions, ::nameOf),
                        jars = jarsLeft(period.status, plan, periodEngine.factOf(transactions)),
                        savings = savingsOf(progress),
                        task = task,
                        step = step,
                    )
                }
            }
        }

    /**
     * Настроение по показателям (R11), описание для TalkBack («Сова Пушок
     * грустит: хочет есть») и самочувствие, по росту которого сова подпрыгивает.
     */
    private fun owlOf(profile: Profile, pet: Pet): OwlLook {
        val mood = petState.moodOf(pet.state)
        return owlLook(
            pets = pets,
            appearance = profile.appearance,
            stage = pet.growth.stage,
            mood = mood,
            description = owlDescription(texts, profile.petName, mood, petState.sadAbout(pet.state)),
            wellbeing = pet.state.wellbeing,
        )
    }

    /** Четыре источника разом: у combine нет Triple на четверых. */
    private data class Sources(
        val pet: Pet?,
        val period: GamePeriod?,
        val progress: GoalProgress?,
        val completed: List<CompletedTask>,
    )

    private fun taskOf(completed: List<CompletedTask>, transactions: List<Transaction>, pet: PetState): TaskOfDay? {
        val task = TaskSchedule.taskOfTheDay(tasks, completed, transactions, pet, balance) ?: return null
        val done = TaskSchedule.passed(tasks, completed)
        return TaskOfDay(
            id = task.id,
            topic = task.topic,
            intro = texts.textOf(task.introKey),
            rewardAvailable = TaskSchedule.rewardable(task.id, completed, transactions, balance),
            allDone = TaskSchedule.listed(tasks).all { it.id in done },
            reward = balance.taskReward,
        )
    }

    /** Товар или цель операции словами; пропавшие из контент-пака — без имени, но с суммой. */
    private fun nameOf(transaction: Transaction): String? {
        val key = transaction.itemId?.let { shopItems[it]?.titleKey }
            ?: transaction.goalId?.let { goals[it]?.titleKey }
        return key?.let(texts::textOf)
    }

    /**
     * Цель могла исчезнуть из контент-пака после обновления содержимого —
     * тогда накопления всё равно показываются, просто без цели. Терять
     * отложенные монеты из-за правки файла нельзя.
     */
    private fun savingsOf(progress: GoalProgress?): SavingsView {
        if (progress == null) return SavingsView(saved = Coins.ZERO)
        val goal = goals[progress.goalId] ?: return SavingsView(saved = progress.saved)
        return SavingsView(
            saved = progress.saved,
            goalTitle = texts[goal.titleKey] ?: goal.titleKey,
            price = goal.price,
        )
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
