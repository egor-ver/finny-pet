package ru.finnypet.app.ui.screens.adult

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.finnypet.app.domain.economy.GameBalance
import ru.finnypet.app.domain.economy.PetStateEngine
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.CompletedTask
import ru.finnypet.app.domain.model.GamePeriod
import ru.finnypet.app.domain.model.GoalProgress
import ru.finnypet.app.domain.model.GrowthStage
import ru.finnypet.app.domain.model.Pet
import ru.finnypet.app.domain.model.Profile
import ru.finnypet.app.domain.model.TaskId
import ru.finnypet.app.domain.model.TaskTopic
import ru.finnypet.app.domain.model.TransactionType
import ru.finnypet.app.domain.repository.ContentRepository
import ru.finnypet.app.domain.repository.PeriodRepository
import ru.finnypet.app.domain.repository.ProfileRepository
import ru.finnypet.app.domain.repository.SavingsRepository
import ru.finnypet.app.domain.repository.SettingsRepository
import ru.finnypet.app.domain.repository.TaskProgressRepository
import ru.finnypet.app.domain.usecase.AwardParentBonus
import ru.finnypet.app.domain.usecase.DeleteGame
import ru.finnypet.app.domain.usecase.StartDemo
import ru.finnypet.app.domain.usecase.TaskSchedule
import ru.finnypet.app.ui.screens.ProfileViewModel
import ru.finnypet.app.ui.text.textOf
import javax.inject.Inject

/**
 * Сколько заданий темы ребёнок прошёл из тех, что есть в контент-паке.
 *
 * Только «сколько из скольких»: ТЗ 2.5.12 запрещает негативные оценки
 * ребёнка, поэтому непройденное здесь не зовётся ни ошибкой, ни отставанием.
 */
data class TopicProgress(
    val topic: TaskTopic,
    val passed: Int,
    val total: Int,
)

/** Что сегодня можно сказать про бонус. */
enum class AwardState {
    /** Бонус этого дня ещё не выдан. */
    AVAILABLE,

    /** Бонус этого дня уже выдан. */
    USED,

    /** Игрового дня нет — начислять некуда, а не «уже начислено». */
    NO_DAY,
}

/** Куда уводит раздел после действия взрослого: переход делает экран. */
enum class AdultExit { DEMO_STARTED, GAME_DELETED }

/** Что видит взрослый. */
sealed interface AdultState {

    data object Loading : AdultState

    data object Failed : AdultState

    data class Ready(
        val childName: String,
        val petName: String,
        /** «О чём поговорить сегодня». */
        val talk: String,
        /** Цели приложения из контент-пака. */
        val about: List<String>,
        val topics: List<TopicProgress>,
        val days: Int,
        val stage: GrowthStage,
        val points: Int,
        val balance: Coins,
        val saved: Coins,
        val bonus: Coins,
        val award: AwardState,
        val soundEnabled: Boolean,
        val animationsEnabled: Boolean,
        /** Текст начисления, пока взрослый его не закрыл. */
        val awarded: String? = null,
    ) : AdultState
}

/**
 * Раздел для взрослого (ТЗ 2.5.12): цели приложения, пройденные темы и общий
 * прогресс ребёнка. Сюда же собран переключатель движения — ТЗ 3.6 требует
 * его отключаемости, а на детских экранах настройкам не место. Звук
 * (`soundEnabled`) хранится тем же способом, но без переключателя на экране:
 * звуков в игре нет, а нерабочая настройка обманывала бы ожидание (Б16, U3).
 *
 * Действия взрослого: бонус через [AwardParentBonus] одной операцией, запуск
 * демонстрации и удаление игры (ТЗ 3.5).
 */
@HiltViewModel
class AdultViewModel @Inject constructor(
    profiles: ProfileRepository,
    private val periods: PeriodRepository,
    private val savings: SavingsRepository,
    private val tasks: TaskProgressRepository,
    private val settings: SettingsRepository,
    private val awardBonus: AwardParentBonus,
    private val startDemo: StartDemo,
    private val deleteGame: DeleteGame,
    private val gameBalance: GameBalance,
    private val petState: PetStateEngine,
    content: ContentRepository,
) : ProfileViewModel(profiles) {

    private val texts: Map<String, String> = content.pack().texts

    private val allTasks = content.pack().tasks

    /** Сколько заданий в теме — из контент-пака, а не из прохождений; разбор не в счёт (AD-7). */
    private val topicTasks: Map<TaskTopic, List<TaskId>> =
        TaskSchedule.listed(allTasks).groupBy({ it.topic }, { it.id })

    /** Ключа может не быть — тогда строки просто нет, а не «adult.about.4» на экране. */
    private val about: List<String> = ABOUT_KEYS.mapNotNull(texts::get)

    /** Смена значения перечитывает раздел заново — повтор после сбоя. */
    private val attempts = MutableStateFlow(0)
    private val awarded = MutableStateFlow<String?>(null)
    private val awarding = Mutex()
    private val leaving = Mutex()
    private val left = MutableStateFlow<AdultExit?>(null)

    /** Факт для экрана: профиль сменился, раздел пора закрыть. */
    val exit: StateFlow<AdultExit?> = left.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<AdultState> = attempts
        .flatMapLatest { screen() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = AdultState.Loading,
        )

    /** Повтор после сбоя: ТЗ 3.4 запрещает экраны, с которых нет выхода. */
    fun retry() {
        failed.value = false
        attempts.value++
    }

    fun award() = act { profileId ->
        // Замок против двойного нажатия: обе корутины успели бы прочитать
        // операции дня до того, как первая записала бонус, и начислили бы
        // дважды. Проверка «сегодня уже выдан» живёт внутри сценария, и
        // попасть в неё второй вызов должен уже после записи первого.
        awarding.withLock {
            val credited = awardBonus(profileId) ?: return@withLock
            awarded.value = texts.textOf(credited.explanation)
        }
    }

    fun startDemo() = leave(AdultExit.DEMO_STARTED) { startDemo.invoke() }

    fun deleteGame() = leave(AdultExit.GAME_DELETED) { deleteGame.invoke() }

    /** Замок: два запуска демонстрации вперемешку завели бы два тестовых профиля. */
    private fun leave(exit: AdultExit, action: suspend () -> Unit) = guarded {
        leaving.withLock {
            if (left.value != null) return@withLock
            action()
            left.value = exit
        }
    }

    fun dismissAward() {
        awarded.value = null
    }

    fun setSound(enabled: Boolean) = guarded { settings.setSoundEnabled(enabled) }

    fun setAnimations(enabled: Boolean) = guarded { settings.setAnimationsEnabled(enabled) }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun screen(): Flow<AdultState> =
        combine(profiles.observeActive(), failed) { profile, isFailed -> profile to isFailed }
            .flatMapLatest { (profile, isFailed) ->
                when {
                    isFailed -> flowOf(AdultState.Failed)
                    profile == null -> flowOf(AdultState.Loading)
                    else -> forProfile(profile)
                }
            }
            // Число сыгранных дней читается прямо в потоке, и отказ базы иначе
            // ушёл бы в необработанные исключения viewModelScope — в падение.
            .catch { emit(AdultState.Failed) }

    /**
     * Дни считаются на смене дня, а не на каждом изменении: копилка и задания
     * меняются часто и к счётчику отношения не имеют. Баланс и операции
     * наблюдаются отдельно, потому что зависят от дня.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun forProfile(profile: Profile): Flow<AdultState> =
        periods.observeCurrent(profile.id)
            .distinctUntilChanged { old, new -> old?.number == new?.number }
            .flatMapLatest { period ->
                val days = periods.count(profile.id)
                combine(progressOf(profile), moneyOf(period), viewOf()) { progress, money, view ->
                    ready(profile, days, progress, money, view)
                }
            }

    private fun progressOf(profile: Profile): Flow<Progress> = combine(
        profiles.observePet(profile.id),
        savings.observeActive(profile.id),
        tasks.observeCompleted(profile.id),
    ) { pet, goal, completed -> Progress(pet, goal, completed) }

    private fun moneyOf(period: GamePeriod?): Flow<Money> {
        if (period == null) return flowOf(Money(Coins.ZERO, AwardState.NO_DAY))
        return combine(
            periods.observeBalance(period),
            periods.observeTransactions(period.id),
        ) { balance, transactions ->
            val used = transactions.any { it.type == TransactionType.INCOME_PARENT }
            Money(balance, if (used) AwardState.USED else AwardState.AVAILABLE)
        }
    }

    private fun viewOf(): Flow<View> = combine(
        settings.observeSoundEnabled(),
        settings.observeAnimationsEnabled(),
        awarded,
    ) { sound, animations, awarded -> View(sound, animations, awarded) }

    private fun ready(
        profile: Profile,
        days: Int,
        progress: Progress,
        money: Money,
        view: View,
    ): AdultState {
        val pet = progress.pet ?: return AdultState.Loading
        return AdultState.Ready(
            childName = profile.childName,
            petName = profile.petName,
            talk = texts.textOf(talkHint(petState.needsOf(pet.state))),
            about = about,
            topics = topicsOf(progress.completed),
            days = days,
            stage = pet.growth.stage,
            points = pet.growth.points,
            balance = money.balance,
            saved = progress.goal?.saved ?: Coins.ZERO,
            bonus = gameBalance.parentBonus,
            award = money.award,
            soundEnabled = view.sound,
            animationsEnabled = view.animations,
            awarded = view.awarded,
        )
    }

    /**
     * Темы перечисляются все три, даже пустые: взрослому важно, чего ребёнок
     * ещё не касался, а «нет темы в списке» этого не скажет.
     */
    private fun topicsOf(completed: List<CompletedTask>): List<TopicProgress> {
        val passed = TaskSchedule.passed(allTasks, completed)
        return TaskTopic.entries.map { topic ->
            val ids = topicTasks[topic].orEmpty()
            TopicProgress(topic = topic, passed = ids.count(passed::contains), total = ids.size)
        }
    }

    private data class Progress(
        val pet: Pet?,
        val goal: GoalProgress?,
        val completed: List<CompletedTask>,
    )

    private data class Money(val balance: Coins, val award: AwardState)

    private data class View(val sound: Boolean, val animations: Boolean, val awarded: String?)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        val ABOUT_KEYS = listOf("adult.about.1", "adult.about.2", "adult.about.3", "adult.about.4")
    }
}
