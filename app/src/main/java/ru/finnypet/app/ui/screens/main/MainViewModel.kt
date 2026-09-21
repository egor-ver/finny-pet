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
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.CompletedTask
import ru.finnypet.app.domain.model.GamePeriod
import ru.finnypet.app.domain.model.Pet
import ru.finnypet.app.domain.model.Transaction
import ru.finnypet.app.domain.model.Goal
import ru.finnypet.app.domain.model.GoalId
import ru.finnypet.app.domain.model.GoalProgress
import ru.finnypet.app.domain.model.GrowthStage
import ru.finnypet.app.domain.model.PeriodStatus
import ru.finnypet.app.domain.model.PetAppearance
import ru.finnypet.app.domain.model.PetState
import ru.finnypet.app.domain.model.Profile
import ru.finnypet.app.domain.model.TaskId
import ru.finnypet.app.domain.model.TaskTopic
import ru.finnypet.app.domain.repository.ContentRepository
import ru.finnypet.app.domain.repository.PeriodRepository
import ru.finnypet.app.domain.repository.ProfileRepository
import ru.finnypet.app.ui.screens.ProfileViewModel
import ru.finnypet.app.domain.repository.SavingsRepository
import ru.finnypet.app.domain.repository.TaskProgressRepository
import ru.finnypet.app.domain.usecase.OpenPeriodIfNeeded
import ru.finnypet.app.domain.usecase.TaskSchedule
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
    /** Остался ли на сегодня лимит наград: подпись «награда не получена» или «получена». */
    val rewardAvailable: Boolean,
    /** Все задания уже пройдены — предлагается повторить давнее всех. */
    val allDone: Boolean,
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
        val childName: String,
        val petName: String,
        val appearance: PetAppearance,
        val stage: GrowthStage,
        val stats: PetState,
        val balance: Coins,
        val savings: SavingsView,
        val task: TaskOfDay?,
        val periodNumber: Int,
        val periodStatus: PeriodStatus,
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
    private val balance: GameBalance,
    content: ContentRepository,
) : ProfileViewModel(profiles) {

    private val goals: Map<GoalId, Goal> = content.pack().goals.associateBy { it.id }
    private val tasks = content.pack().tasks
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
                // Баланс и операции наблюдаются отдельно, потому что зависят
                // от периода: баланс считается по операциям, а не хранится
                // числом, и лимит наград за задания — по ним же.
                combine(
                    periods.observeBalance(period),
                    periods.observeTransactions(period.id),
                ) { balance, transactions ->
                    MainState.Ready(
                        childName = profile.childName,
                        petName = profile.petName,
                        appearance = profile.appearance,
                        stage = pet.growth.stage,
                        stats = pet.state,
                        balance = balance,
                        savings = savingsOf(progress),
                        task = taskOf(completed, transactions),
                        periodNumber = period.number,
                        periodStatus = period.status,
                    )
                }
            }
        }

    /** Четыре источника разом: у combine нет Triple на четверых. */
    private data class Sources(
        val pet: Pet?,
        val period: GamePeriod?,
        val progress: GoalProgress?,
        val completed: List<CompletedTask>,
    )

    private fun taskOf(completed: List<CompletedTask>, transactions: List<Transaction>): TaskOfDay? {
        val task = TaskSchedule.taskOfTheDay(tasks, completed) ?: return null
        val done = completed.map { it.taskId }.toSet()
        return TaskOfDay(
            id = task.id,
            topic = task.topic,
            intro = texts.textOf(task.introKey),
            rewardAvailable = TaskSchedule.rewardAvailable(transactions, this.balance),
            allDone = tasks.all { it.id in done },
        )
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
