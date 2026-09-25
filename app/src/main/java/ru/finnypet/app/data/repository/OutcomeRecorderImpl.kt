package ru.finnypet.app.data.repository

import androidx.room.withTransaction
import ru.finnypet.app.data.local.FinnyDatabase
import ru.finnypet.app.data.local.mapper.petStateEntityOf
import ru.finnypet.app.data.local.mapper.toEntity
import ru.finnypet.app.data.local.mapper.toGrowth
import ru.finnypet.app.data.local.mapper.toState
import ru.finnypet.app.domain.economy.PetStateEngine
import ru.finnypet.app.domain.model.Change
import ru.finnypet.app.domain.model.PetEffect
import ru.finnypet.app.domain.model.ProfileId
import ru.finnypet.app.domain.model.PeriodStatus
import ru.finnypet.app.domain.model.TransactionType
import ru.finnypet.app.domain.repository.ActionOutcome
import ru.finnypet.app.domain.repository.OutcomeRecorder
import ru.finnypet.app.domain.repository.TaskProgressRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Одна транзакция Room на все последствия действия.
 *
 * Операция пишется последней: у неё внешний ключ на период, и если период
 * не тот, откатится всё, что записано до неё, — так проверяется атомарность.
 */
@Singleton
class OutcomeRecorderImpl @Inject constructor(
    private val database: FinnyDatabase,
    private val petState: PetStateEngine,
    private val taskProgress: TaskProgressRepository,
) : OutcomeRecorder {

    override suspend fun recordEventOnce(profileId: ProfileId, outcome: ActionOutcome): Boolean =
        database.withTransaction {
            val marker = requireNotNull(outcome.transaction)
            check(marker.type == TransactionType.EVENT_CARE || marker.type == TransactionType.INCOME_GIFT)
            val period = checkNotNull(database.periods().byId(marker.periodId)) { "День события не найден" }
            check(period.profileId == profileId.value) { "Событие относится к другому профилю" }
            if (period.status != PeriodStatus.PLANNING ||
                database.transactions().byPeriod(marker.periodId).any { it.reasonKey == marker.reasonKey }) {
                false
            } else {
                check(database.petStates().byProfile(profileId.value) != null) { "У события нет питомца" }
                applyEffects(profileId, outcome.effects)
                database.transactions().insert(marker.toEntity())
                true
            }
        }

    override suspend fun record(profileId: ProfileId, outcome: ActionOutcome): List<Change.PetStat> =
        database.withTransaction {
            val changes = applyEffects(profileId, outcome.effects)
            outcome.savings?.let { progress ->
                val entity = progress.toEntity(profileId)
                // Активная цель на профиль ровно одна: смена флага идёт тем же
                // путём, что и выбор цели, иначе завелась бы вторая активная.
                if (progress.isActive) database.goalProgress().setActive(entity) else database.goalProgress().upsert(entity)
            }
            // Запись о прохождении — через репозиторий: у таблицы один писатель,
            // а транзакция Room подхватывает и его.
            outcome.taskCompletion?.let { completion ->
                taskProgress.complete(profileId, completion.taskId, completion.outcomeId, completion.reward)
            }
            // Подтверждение плана пишет день вместе с пополнением копилки (AD-5).
            outcome.period?.let { database.periods().update(it.toEntity()) }
            outcome.transaction?.let { database.transactions().insert(it.toEntity()) }
            changes
        }

    /** Что изменилось на самом деле: показатель у границы не растёт. */
    private suspend fun applyEffects(profileId: ProfileId, effects: List<PetEffect>): List<Change.PetStat> {
        if (effects.isEmpty()) return emptyList()
        val entity = database.petStates().byProfile(profileId.value) ?: return emptyList()
        val applied = petState.apply(entity.toState(), effects)
        database.petStates().upsert(petStateEntityOf(profileId = profileId, state = applied.value, growth = entity.toGrowth()))
        return applied.changes.filterIsInstance<Change.PetStat>()
    }
}
