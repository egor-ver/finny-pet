package ru.finnypet.app.domain.repository

import ru.finnypet.app.domain.model.Change
import ru.finnypet.app.domain.model.GamePeriod
import ru.finnypet.app.domain.model.GoalProgress
import ru.finnypet.app.domain.model.PetEffect
import ru.finnypet.app.domain.model.ProfileId
import ru.finnypet.app.domain.model.TaskCompletion
import ru.finnypet.app.domain.model.Transaction

/**
 * Последствия одного действия ребёнка, которые надо записать вместе:
 * операция, влияние на питомца, прогресс копилки, пройденное задание,
 * новый статус дня. Чего нет — того не пишется.
 */
data class ActionOutcome(
    val transaction: Transaction? = null,
    val effects: List<PetEffect> = emptyList(),
    val savings: GoalProgress? = null,
    val taskCompletion: TaskCompletion? = null,
    val period: GamePeriod? = null,
)

/**
 * Записывает последствия действия одной транзакцией.
 *
 * Покупка, пополнение копилки и задание трогают разные таблицы. Без общей
 * транзакции сбой между записями оставил бы монеты списанными, а питомца
 * прежним — ТЗ 2.5.9 требует, чтобы после действия менялись баланс,
 * накопления и показатель вместе. Применение эффектов к питомцу тоже здесь:
 * это единственное место, где состояние питомца меняется от действия ребёнка,
 * и оно возвращает настоящие изменения с учётом границ показателей.
 */
interface OutcomeRecorder {

    suspend fun record(profileId: ProfileId, outcome: ActionOutcome): List<Change.PetStat>

    /** Маркер события, деньги и показатель сохраняются вместе; повтор после перезапуска ничего не меняет. */
    suspend fun recordEventOnce(profileId: ProfileId, outcome: ActionOutcome): Boolean
}
