package ru.finnypet.app.data.repository

import androidx.room.withTransaction
import ru.finnypet.app.data.local.FinnyDatabase
import ru.finnypet.app.data.local.mapper.petStateEntityOf
import ru.finnypet.app.data.local.mapper.toDomain
import ru.finnypet.app.data.local.mapper.toEntity
import ru.finnypet.app.domain.model.GamePeriod
import ru.finnypet.app.domain.model.ProfileId
import ru.finnypet.app.domain.repository.DayClosure
import ru.finnypet.app.domain.repository.DayRecorder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Закрытие дня одной транзакцией Room.
 *
 * Порядок важен: сначала закрывается прошедший день, потом открывается новый.
 * Уникальность номера внутри профиля не даст завести второй день с тем же
 * номером, а общая транзакция гарантирует, что закрытый день без следующего
 * не останется — именно это состояние OpenPeriodIfNeeded считает потерей
 * остатка и справедливо отвергает.
 */
@Singleton
class DayRecorderImpl @Inject constructor(
    private val database: FinnyDatabase,
) : DayRecorder {

    override suspend fun close(profileId: ProfileId, closure: DayClosure): GamePeriod =
        database.withTransaction {
            val updated = database.periods().update(closure.closedPeriod.toEntity())
            check(updated == 1) {
                "День ${closure.closedPeriod.number} не найден в базе: обновлено строк $updated"
            }

            database.petStates().upsert(
                petStateEntityOf(
                    profileId = profileId,
                    state = closure.state,
                    growth = closure.growth,
                )
            )

            val id = database.periods().insert(closure.nextPeriod.toEntity())
            closure.nextPeriod.copy(id = id)
        }
}
