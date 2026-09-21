package ru.finnypet.app.ui.screens.progress

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import ru.finnypet.app.domain.economy.BudgetEngine
import ru.finnypet.app.domain.economy.PeriodEngine
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.CompletedTask
import ru.finnypet.app.domain.model.GoalProgress
import ru.finnypet.app.domain.model.ProfileId
import ru.finnypet.app.domain.model.TaskId
import ru.finnypet.app.domain.model.TaskTopic
import ru.finnypet.app.domain.repository.ContentRepository
import ru.finnypet.app.domain.repository.PeriodRepository
import ru.finnypet.app.domain.repository.ProfileRepository
import ru.finnypet.app.domain.repository.SavingsRepository
import ru.finnypet.app.domain.repository.TaskProgressRepository
import ru.finnypet.app.ui.components.BudgetLine
import ru.finnypet.app.ui.screens.ProfileViewModel
import ru.finnypet.app.ui.text.textOf
import javax.inject.Inject

/** Итоги последнего завершённого дня. */
data class LastDay(
    val number: Int,
    val lines: List<BudgetLine>,
    val planTotal: Coins,
    val factTotal: Coins,
)

/** Цель, на которую копят сейчас. */
data class GoalSummary(
    val title: String,
    val saved: Coins,
    val price: Coins,
)

/**
 * Пройденное задание: что это было и сколько всего принесло.
 *
 * [topic] пусто, если задание исчезло из контент-пака после правки файла:
 * прохождение остаётся в истории, а вот к какой теме оно относилось — уже
 * неоткуда узнать, и выдумывать тему нельзя.
 */
data class PassedTask(
    val id: TaskId,
    val title: String,
    val topic: TaskTopic?,
    val reward: Coins,
)

/** Термин из справочника. */
data class Term(
    val id: String,
    val title: String,
    val body: String,
)

/** Что показывает раздел прогресса. */
sealed interface ProgressState {

    data object Loading : ProgressState

    /** Экран только читает, но и чтение может отказать. */
    data object Failed : ProgressState

    data class Ready(
        /** Пусто, пока ни один день не закрыт. */
        val lastDay: LastDay?,
        /** Пусто, пока цель не выбрана. */
        val goal: GoalSummary?,
        val passed: List<PassedTask>,
        val terms: List<Term>,
    ) : ProgressState
}

/**
 * История и учебный прогресс (ТЗ 2.5.11): завершённые задания, прогресс по
 * цели, итоги последнего дня и справочник терминов.
 *
 * Экран только читает. Итоги последнего дня пересчитываются из плана и
 * операций — отдельно их не храним, чтобы не заводить второй источник правды
 * рядом с операциями.
 */
@HiltViewModel
class ProgressViewModel @Inject constructor(
    profiles: ProfileRepository,
    private val periods: PeriodRepository,
    private val savings: SavingsRepository,
    private val tasks: TaskProgressRepository,
    private val budget: BudgetEngine,
    private val periodEngine: PeriodEngine,
    content: ContentRepository,
) : ProfileViewModel(profiles) {

    private val texts: Map<String, String> = content.pack().texts
    private val taskTopics = content.pack().tasks.associate { it.id to it.topic }
    private val taskTitles = content.pack().tasks.associate { it.id to texts.textOf(it.introKey) }
    private val goals = content.pack().goals.associateBy { it.id }

    /** Справочник не меняется во время игры — собирается один раз. */
    private val terms: List<Term> = content.pack().glossary.map { term ->
        Term(id = term.id, title = texts.textOf(term.titleKey), body = texts.textOf(term.bodyKey))
    }

    /** Счётчик попыток: смена значения перечитывает всё заново. */
    private val attempts = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<ProgressState> = attempts
        .flatMapLatest { screen() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = ProgressState.Loading,
        )

    /** Повтор после сбоя: ТЗ 3.4 запрещает экраны, с которых нет выхода. */
    fun retry() {
        attempts.value++
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun screen(): Flow<ProgressState> = profiles.observeActive()
        .flatMapLatest { profile ->
            if (profile == null) flowOf(ProgressState.Loading) else forProfile(profile.id)
        }
        // Итоги читаются прямо в потоке, и отказ базы иначе ушёл бы в
        // необработанные исключения viewModelScope, то есть в падение.
        .catch { emit(ProgressState.Failed) }

    /**
     * Итоги пересчитываются на смене дня, а не на каждом изменении: номер
     * текущего дня меняется ровно тогда, когда прошлый закрылся, а копилка и
     * задания меняются часто и к итогам отношения не имеют.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun forProfile(profileId: ProfileId): Flow<ProgressState> =
        periods.observeCurrent(profileId)
            .map { it?.number }
            .distinctUntilChanged()
            .flatMapLatest {
                val lastDay = lastDay(profileId)
                combine(
                    savings.observeActive(profileId),
                    tasks.observeCompleted(profileId),
                ) { progress, completed ->
                    ProgressState.Ready(
                        lastDay = lastDay,
                        goal = goalOf(progress),
                        passed = passed(completed),
                        terms = terms,
                    )
                }
            }

    private suspend fun lastDay(profileId: ProfileId): LastDay? {
        val period = periods.lastClosed(profileId) ?: return null
        val plan = periods.plan(period.id) ?: return null
        val report = budget.compare(plan, periodEngine.factOf(periods.transactions(period.id)))
        return LastDay(
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
        )
    }

    /**
     * Цель могла исчезнуть из контент-пака после правки файла — тогда
     * показывать нечего, а отложенное всё равно видно на главном экране.
     */
    private fun goalOf(progress: GoalProgress?): GoalSummary? {
        val goal = progress?.let { goals[it.goalId] } ?: return null
        return GoalSummary(
            title = texts.textOf(goal.titleKey),
            saved = progress.saved,
            price = goal.price,
        )
    }

    /**
     * Одно задание — один пункт списка. Пройти задание можно сколько угодно
     * раз, но монеты даются за одно в день, поэтому повторы встали бы рядом с
     * первым строками с нулём. Награды складываются: «сколько принесло» —
     * вопрос про задание целиком, а не про попытку.
     */
    private fun passed(completed: List<CompletedTask>): List<PassedTask> =
        completed.groupBy { it.taskId }.map { (taskId, passes) ->
            PassedTask(
                id = taskId,
                title = taskTitles[taskId] ?: taskId.value,
                topic = taskTopics[taskId],
                reward = passes.fold(Coins.ZERO) { sum, pass -> sum + pass.reward },
            )
        }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
