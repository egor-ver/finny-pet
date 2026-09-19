package ru.finnypet.app.data.repository

import androidx.room.withTransaction
import ru.finnypet.app.data.local.FinnyDatabase
import ru.finnypet.app.data.local.entity.TaskProgressEntity
import ru.finnypet.app.data.local.mapper.petStateEntityOf
import ru.finnypet.app.data.local.mapper.toEntity
import ru.finnypet.app.data.local.mapper.toGrowth
import ru.finnypet.app.data.local.mapper.toState
import ru.finnypet.app.domain.economy.GameClock
import ru.finnypet.app.domain.economy.PetStateEngine
import ru.finnypet.app.domain.model.Change
import ru.finnypet.app.domain.model.PetEffect
import ru.finnypet.app.domain.model.ProfileId
import ru.finnypet.app.domain.repository.ActionOutcome
import ru.finnypet.app.domain.repository.OutcomeRecorder
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
    private val clock: GameClock,
) : OutcomeRecorder {

    override suspend fun record(profileId: ProfileId, outcome: ActionOutcome): List<Change.PetStat> =
        database.withTransaction {
            val changes = applyEffects(profileId, outcome.effects)
            outcome.savings?.let { progress ->
                val entity = progress.toEntity(profileId)
                // Активная цель на профиль ровно одна: смена флага идёт тем же
                // путём, что и выбор цели, иначе завелась бы вторая активная.
                if (progress.isActive) database.goalProgress().setActive(entity) else database.goalProgress().upsert(entity)
            }
            outcome.taskCompletion?.let { completion ->
                database.taskProgress().insert(
                    TaskProgressEntity(
                        profileId = profileId.value,
                        taskId = completion.taskId.value,
                        outcomeId = completion.outcomeId,
                        reward = completion.reward.amount,
                        completedAt = clock.now(),
                    )
                )
            }
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
