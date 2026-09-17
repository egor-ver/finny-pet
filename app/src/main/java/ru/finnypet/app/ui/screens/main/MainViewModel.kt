package ru.finnypet.app.ui.screens.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.Goal
import ru.finnypet.app.domain.model.GoalId
import ru.finnypet.app.domain.model.GoalProgress
import ru.finnypet.app.domain.model.GrowthStage
import ru.finnypet.app.domain.model.PeriodStatus
import ru.finnypet.app.domain.model.PetAppearance
import ru.finnypet.app.domain.model.PetState
import ru.finnypet.app.domain.model.Profile
import ru.finnypet.app.domain.repository.ContentRepository
import ru.finnypet.app.domain.repository.PeriodRepository
import ru.finnypet.app.domain.repository.ProfileRepository
import ru.finnypet.app.domain.repository.SavingsRepository
import ru.finnypet.app.domain.usecase.OpenPeriodIfNeeded
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

/** Копилка: сколько отложено и на что копим. Цели может не быть — это норма. */
data class SavingsView(
    val saved: Coins,
    val goalTitle: String? = null,
    val price: Coins? = null,
) {

    val remaining: Coins get() = price?.let(saved::shortfallTo) ?: Coins.ZERO

    val isReached: Boolean get() = price != null && saved.covers(price)

    val fraction: Float
        get() = when {
            price == null || price.amount == 0 -> 0f
            else -> saved.amount.toFloat() / price.amount
        }
}

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
    private val profiles: ProfileRepository,
    private val periods: PeriodRepository,
    private val savings: SavingsRepository,
    private val openPeriod: OpenPeriodIfNeeded,
    content: ContentRepository,
) : ViewModel() {

    private val goals: Map<GoalId, Goal> = content.pack().goals.associateBy { it.id }
    private val texts: Map<String, String> = content.pack().texts

    private val failed = MutableStateFlow(false)

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
        startDay()
    }

    /** Повтор после сбоя: ТЗ 3.4 запрещает экраны, с которых нет выхода. */
    fun retry() {
        failed.value = false
        startDay()
    }

    /**
     * Игровой день должен быть открыт до того, как экран покажет баланс:
     * без периода баланса не существует. Ждём профиль, а не берём его разом —
     * на главный экран можно попасть сразу после создания питомца, и запись
     * профиля может ещё не дойти до подписчиков.
     */
    private fun startDay() {
        viewModelScope.launch {
            val profile = profiles.observeActive().filterNotNull().first()
            try {
                openPeriod(profile.id)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                failed.value = true
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun ready(profile: Profile): Flow<MainState> = combine(
        profiles.observePet(profile.id),
        periods.observeCurrent(profile.id),
        savings.observeActive(profile.id),
    ) { pet, period, progress -> Triple(pet, period, progress) }
        .flatMapLatest { (pet, period, progress) ->
            if (pet == null || period == null) {
                flowOf(MainState.Loading)
            } else {
                // Баланс наблюдается отдельно, потому что зависит от периода:
                // он считается по его операциям, а не хранится числом.
                periods.observeBalance(period).map { balance ->
                    MainState.Ready(
                        childName = profile.childName,
                        petName = profile.petName,
                        appearance = profile.appearance,
                        stage = pet.growth.stage,
                        stats = pet.state,
                        balance = balance,
                        savings = savingsOf(progress),
                        periodNumber = period.number,
                        periodStatus = period.status,
                    )
                }
            }
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
