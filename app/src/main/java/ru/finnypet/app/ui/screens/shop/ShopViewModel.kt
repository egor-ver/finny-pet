package ru.finnypet.app.ui.screens.shop

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.finnypet.app.domain.economy.GameBalance
import ru.finnypet.app.domain.economy.PurchaseResult
import ru.finnypet.app.domain.economy.WalletEngine
import ru.finnypet.app.domain.model.Change
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.GamePeriod
import ru.finnypet.app.domain.model.ItemId
import ru.finnypet.app.domain.model.PeriodStatus
import ru.finnypet.app.domain.model.PetEffect
import ru.finnypet.app.domain.model.ProfileId
import ru.finnypet.app.domain.model.RecoveryOption
import ru.finnypet.app.domain.model.ShopItem
import ru.finnypet.app.domain.model.SpendCategory
import ru.finnypet.app.domain.repository.ActionOutcome
import ru.finnypet.app.domain.repository.ContentRepository
import ru.finnypet.app.domain.repository.OutcomeRecorder
import ru.finnypet.app.domain.repository.PeriodRepository
import ru.finnypet.app.domain.repository.ProfileRepository
import ru.finnypet.app.ui.screens.ProfileViewModel
import ru.finnypet.app.domain.repository.SavingsRepository
import ru.finnypet.app.domain.usecase.OpenPeriodIfNeeded
import ru.finnypet.app.domain.usecase.TaskSchedule
import ru.finnypet.app.ui.text.textOf
import javax.inject.Inject

/** Товар как его видит экран: готовое название, цена, направление, влияние на питомца. */
data class ShopItemView(
    val id: ItemId,
    val title: String,
    val price: Coins,
    val category: SpendCategory,
    val effects: List<PetEffect>,
)

/** Вариант выхода при нехватке денег с подписью из контент-пака. */
data class RecoveryChoice(
    val option: RecoveryOption,
    val label: String,
)

/**
 * Итог попытки купить.
 *
 * Лежит в состоянии, а не улетает событием: экран читает его после поворота
 * и показывает, пока ребёнок сам не закроет. Объяснение обязательно в обоих
 * случаях — ТЗ 2.5.6 и 2.5.9 требуют объяснить и покупку, и отказ.
 */
sealed interface PurchaseOutcome {

    /**
     * [changes] — что на самом деле изменилось, а не что обещал товар:
     * показатель у верхней границы не растёт, и говорить ребёнку «+15»
     * при неподвижной полосе было бы обманом (ТЗ 2.5.9). [effects] нужны,
     * чтобы отличить «товар ни на что не влияет» от «влияет, но упёрлось».
     */
    data class Done(
        val title: String,
        val text: String,
        val effects: List<PetEffect>,
        val changes: List<Change.PetStat>,
    ) : PurchaseOutcome

    /**
     * [recommended] — вариант, который домен считает лучшим. Экран делает
     * его главной кнопкой, как только у варианта появляется экран.
     */
    data class Rejected(
        val title: String,
        val text: String,
        val options: List<RecoveryChoice>,
        val recommended: RecoveryOption?,
    ) : PurchaseOutcome
}

sealed interface ShopState {

    data object Loading : ShopState

    data object Failed : ShopState

    data class Ready(
        val items: List<ShopItemView>,
        val balance: Coins,
        /**
         * Покупать можно только когда день идёт. Пока он планируется, трата
         * испортила бы сравнение плана с фактом: ребёнок распределял бы
         * сумму, которой уже нет (ТЗ 2.5.5).
         */
        val canBuy: Boolean,
        val outcome: PurchaseOutcome? = null,
    ) : ShopState
}

/**
 * Магазин (ТЗ 2.5.6): список товаров, покупка со списанием и записью
 * операции, отказ при нехватке денег с объяснением и вариантами выхода.
 *
 * Считает домен: [WalletEngine] решает, хватает ли, и собирает операцию;
 * операцию и влияние на питомца одной транзакцией пишет [OutcomeRecorder].
 * Здесь только чтение базы и тексты.
 */
@HiltViewModel
class ShopViewModel @Inject constructor(
    profiles: ProfileRepository,
    private val periods: PeriodRepository,
    private val savings: SavingsRepository,
    private val openPeriod: OpenPeriodIfNeeded,
    private val wallet: WalletEngine,
    private val recorder: OutcomeRecorder,
    private val balance: GameBalance,
    content: ContentRepository,
) : ProfileViewModel(profiles) {

    private val items: List<ShopItem> = content.pack().shop
    private val texts: Map<String, String> = content.pack().texts

    /** Витрина не зависит от баланса, собирается один раз. */
    private val itemViews: List<ShopItemView> = items.map { item ->
        ShopItemView(
            id = item.id,
            title = texts.textOf(item.titleKey),
            price = item.price,
            category = item.category,
            effects = item.effects,
        )
    }

    private val outcome = MutableStateFlow<PurchaseOutcome?>(null)

    /**
     * Покупки идут по одной. Два быстрых нажатия при деньгах на одну покупку
     * иначе прочитали бы один и тот же баланс и списали бы его дважды.
     */
    private val buying = Mutex()

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<ShopState> =
        combine(profiles.observeActive(), failed) { profile, isFailed -> profile to isFailed }
            .flatMapLatest { (profile, isFailed) ->
                when {
                    isFailed -> flowOf(ShopState.Failed)
                    profile == null -> flowOf(ShopState.Loading)
                    else -> forProfile(profile.id)
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = ShopState.Loading,
            )

    init {
        // В магазин можно попасть и после перезапуска приложения, когда
        // главный экран не успел открыть игровой день.
        act { openPeriod(it) }
    }

    fun retry() = retryWith { openPeriod(it) }

    fun buy(itemId: ItemId) {
        act { profileId ->
            buying.withLock { purchase(profileId, itemId) }
        }
    }

    fun dismiss() {
        outcome.value = null
    }

    /**
     * Баланс и копилка берутся из базы в момент покупки, а не из состояния
     * экрана: экран отстаёт от базы на время записи.
     */
    private suspend fun purchase(profileId: ProfileId, itemId: ItemId) {
        val item = items.firstOrNull { it.id == itemId } ?: return
        val period = periods.current(profileId) ?: return
        if (period.status != PeriodStatus.RUNNING) return

        val saved = savings.activeProgress(profileId)?.saved ?: Coins.ZERO
        val result = wallet.purchase(
            item = item,
            currentBalance = periods.balance(period),
            periodId = period.id,
            savings = saved,
            // «Выполнить задание» обещает монеты — только пока лимит дня не выбран.
            taskRewardAvailable = TaskSchedule.rewardAvailable(periods.transactions(period.id), balance),
        )
        val title = texts.textOf(item.titleKey)
        outcome.value = when (result) {
            is PurchaseResult.Success -> {
                val changes = recorder.record(
                    profileId,
                    ActionOutcome(transaction = result.transaction, effects = result.effects),
                )
                PurchaseOutcome.Done(
                    title = title,
                    text = texts.textOf(result.explanation),
                    effects = result.effects,
                    changes = changes,
                )
            }

            is PurchaseResult.Rejected -> PurchaseOutcome.Rejected(
                title = title,
                text = texts.textOf(result.explanation),
                options = result.options.map { option ->
                    RecoveryChoice(option = option, label = texts.textOf("recovery.${option.name}"))
                },
                recommended = result.explanation.nextStep,
            )
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun forProfile(profileId: ProfileId): Flow<ShopState> =
        periods.observeCurrent(profileId).flatMapLatest { period ->
            if (period == null) {
                flowOf(ShopState.Loading)
            } else {
                combine(periods.observeBalance(period), outcome) { balance, outcome ->
                    ready(period, balance, outcome)
                }
            }
        }

    private fun ready(period: GamePeriod, balance: Coins, outcome: PurchaseOutcome?) = ShopState.Ready(
        items = itemViews,
        balance = balance,
        canBuy = period.status == PeriodStatus.RUNNING,
        outcome = outcome,
    )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
