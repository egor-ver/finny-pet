package ru.finnypet.app.ui.screens.day

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
import ru.finnypet.app.domain.economy.BudgetEngine
import ru.finnypet.app.domain.economy.PeriodEngine
import ru.finnypet.app.domain.model.BudgetPlan
import ru.finnypet.app.domain.model.Change
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.Explanation
import ru.finnypet.app.domain.model.GamePeriod
import ru.finnypet.app.domain.model.GrowthStage
import ru.finnypet.app.domain.model.PeriodStatus
import ru.finnypet.app.domain.model.ProfileId
import ru.finnypet.app.domain.model.Transaction
import ru.finnypet.app.domain.repository.ContentRepository
import ru.finnypet.app.domain.repository.PeriodRepository
import ru.finnypet.app.domain.repository.ProfileRepository
import ru.finnypet.app.domain.usecase.CloseDay
import ru.finnypet.app.domain.usecase.ClosedDay
import ru.finnypet.app.domain.usecase.OpenPeriodIfNeeded
import ru.finnypet.app.ui.components.BudgetLine
import ru.finnypet.app.ui.screens.ProfileViewModel
import ru.finnypet.app.ui.text.textOf
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

/** Итоги закрытого дня — всё, что ребёнок должен увидеть после «Закончить день». */
data class DaySummary(
    val number: Int,
    val nextNumber: Int,
    /** Почему день закончился так: объяснение из контент-пака. */
    val headline: String,
    /** Что стало с ростом: прибавка очков или новая стадия. */
    val growthText: String,
    val lines: List<BudgetLine>,
    val planTotal: Coins,
    val factTotal: Coins,
    val statChanges: List<Change.PetStat>,
    val newStage: GrowthStage?,
    val carryOver: Coins,
)

/**
 * Что показывает экран итогов дня.
 *
 * [Planning] — день ещё не подтверждён, закрывать нечего: ТЗ 2.5.5 требует
 * сравнивать факт с подтверждённым планом, а его пока нет.
 */
sealed interface DayState {

    data object Loading : DayState

    data object Failed : DayState

    data object Planning : DayState

    data class Running(
        val number: Int,
        val lines: List<BudgetLine>,
        val planTotal: Coins,
        val factTotal: Coins,
        /** Закрытие уже идёт: кнопка гаснет, второе нажатие не нужно. */
        val closing: Boolean = false,
    ) : DayState

    data class Closed(val summary: DaySummary) : DayState
}

/**
 * Закрытие игрового дня и его итоги (ТЗ 2.5.9, 2.5.10).
 *
 * Пока день идёт, экран показывает то же сравнение плана с фактом, что и
 * экран плана, — ребёнок видит, к чему пришёл, до того как решит закончить.
 * После закрытия на том же месте появляются итоги: объяснение, изменения
 * показателей питомца, рост и остаток, перенесённый на завтра.
 */
@HiltViewModel
class DayViewModel @Inject constructor(
    profiles: ProfileRepository,
    private val periods: PeriodRepository,
    private val openPeriod: OpenPeriodIfNeeded,
    private val closeDay: CloseDay,
    private val budget: BudgetEngine,
    private val periodEngine: PeriodEngine,
    content: ContentRepository,
) : ProfileViewModel(profiles) {

    private val texts: Map<String, String> = content.pack().texts

    /** Закрытие идёт по одному: второй запуск закрыл бы уже следующий день. */
    private val closing = Mutex()

    private val summary = MutableStateFlow<DaySummary?>(null)
    private val working = MutableStateFlow(false)

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<DayState> =
        combine(profiles.observeActive(), failed) { profile, isFailed -> profile to isFailed }
            .flatMapLatest { (profile, isFailed) ->
                when {
                    isFailed -> flowOf(DayState.Failed)
                    profile == null -> flowOf(DayState.Loading)
                    else -> forProfile(profile.id)
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = DayState.Loading,
            )

    init {
        act { openPeriod(it) }
    }

    fun retry() = retryWith { openPeriod(it) }

    fun close() {
        act { profileId ->
            // Флаг ставится уже внутри: снаружи он остался бы поднятым
            // навсегда, если бы профиль так и не пришёл, и кнопка погасла бы
            // насовсем.
            working.value = true
            try {
                closing.withLock {
                    if (summary.value != null) return@withLock

                    // Пусто — значит закрывать нечего: день уже не идёт или
                    // план не подтверждён. Отдельного сообщения не нужно,
                    // состояние экрана пересчитается само и покажет, что
                    // происходит на самом деле.
                    val result = closeDay(profileId) ?: return@withLock

                    // Новый день уже открыт закрытием, но дохода на нём ещё
                    // нет. Главный экран остался в стеке и второй раз не
                    // создаётся, поэтому начислить надо здесь — иначе ребёнок
                    // вернулся бы ко вчерашнему остатку без сегодняшних монет.
                    //
                    // До показа итогов, а не после: увидев их, ребёнок уходит
                    // играть дальше, и новый день к этому моменту обязан быть
                    // готов целиком.
                    //
                    // Сбой начисления не отменяет итоги: день уже закрыт, и
                    // показать вместо итогов ошибку значило бы соврать.
                    // Начисление починится при следующем входе на любой экран.
                    try {
                        openPeriod(profileId)
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (_: Exception) {
                        // Начислится при следующем входе на любой экран игры.
                    }

                    summary.value = summaryOf(result.value, result.explanation, result.changes)
                }
            } finally {
                working.value = false
            }
        }
    }

    private fun summaryOf(
        closed: ClosedDay,
        explanation: Explanation,
        changes: List<Change>,
    ): DaySummary {
        val report = closed.outcome.report
        val stage = changes.filterIsInstance<Change.Stage>().firstOrNull()
        return DaySummary(
            number = closed.outcome.closedPeriod.number,
            nextNumber = closed.nextPeriod.number,
            headline = texts.textOf(explanation),
            growthText = growthText(closed.earnedPoints, grew = stage != null),
            lines = report.lines.map { line ->
                BudgetLine(
                    category = line.category,
                    planned = line.planned,
                    actual = line.actual,
                    followed = line.followed,
                )
            },
            planTotal = report.planTotal,
            factTotal = report.factTotal,
            statChanges = changes.filterIsInstance<Change.PetStat>(),
            newStage = stage?.to,
            carryOver = closed.outcome.carryOver,
        )
    }

    /**
     * Слова про рост берутся из контент-пака теми же тремя ключами, что и в
     * домене: выросла стадия, прибавились очки или день прошёл без прибавки.
     */
    private fun growthText(earned: Int, grew: Boolean): String = when {
        grew -> texts.textOf(KEY_STAGE_UP)
        earned > 0 -> texts.textOf(KEY_POINTS_ADDED).replace("{earned}", earned.toString())
        else -> texts.textOf(KEY_NO_POINTS)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun forProfile(profileId: ProfileId): Flow<DayState> =
        periods.observeCurrent(profileId).flatMapLatest { period ->
            if (period == null) {
                flowOf(DayState.Loading)
            } else {
                combine(
                    periods.observePlan(period.id),
                    periods.observeTransactions(period.id),
                    summary,
                    working,
                ) { plan, transactions, done, isWorking ->
                    stateOf(period, plan, transactions, done, isWorking)
                }
            }
        }

    private fun stateOf(
        period: GamePeriod,
        plan: BudgetPlan?,
        transactions: List<Transaction>,
        done: DaySummary?,
        isWorking: Boolean,
    ): DayState = when {
        // Итоги показываются, пока ребёнок сам не уйдёт: день уже следующий,
        // но экран обязан объяснить, чем закончился прошлый (ТЗ 2.5.9).
        done != null -> DayState.Closed(done)
        period.status != PeriodStatus.RUNNING || plan == null -> DayState.Planning
        else -> {
            val report = budget.compare(plan, periodEngine.factOf(transactions))
            DayState.Running(
                number = period.number,
                lines = report.lines.map { line ->
                    BudgetLine(
                        category = line.category,
                        planned = line.planned,
                        actual = line.actual,
                        followed = line.followed,
                    )
                },
                planTotal = report.planTotal,
                factTotal = report.factTotal,
                closing = isWorking,
            )
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val KEY_STAGE_UP = "growth.stage_up"
        const val KEY_POINTS_ADDED = "growth.points_added"
        const val KEY_NO_POINTS = "growth.no_points"
    }
}
