package ru.finnypet.app.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.finnypet.app.domain.model.Pet
import ru.finnypet.app.domain.model.PetAppearance
import ru.finnypet.app.domain.model.PetGrowth
import ru.finnypet.app.domain.model.PetState
import ru.finnypet.app.domain.model.Profile
import ru.finnypet.app.domain.model.ProfileId

/**
 * Профиль игрока и его питомец.
 *
 * Активный профиль задаётся явной пометкой, а не эвристикой «самый свежий»:
 * демонстрационный режим создаёт тестовый профиль, и после выхода из него
 * ребёнок обязан вернуться в свою игру, а не в демонстрационную (ТЗ 2.5.13).
 */
interface ProfileRepository {

    fun observeActive(): Flow<Profile?>

    suspend fun active(): Profile?

    /**
     * Создаёт профиль вместе с начальным состоянием питомца и делает его
     * активным. Ни настоящего имени, ни телефона, ни почты (ТЗ 3.5).
     */
    suspend fun create(
        childName: String,
        petName: String,
        appearance: PetAppearance,
        isTest: Boolean = false,
    ): Profile

    suspend fun setActive(id: ProfileId)

    /** Удаление профиля взрослым (ТЗ 3.5): каскад уносит всё его состояние. */
    suspend fun delete(id: ProfileId)

    fun observePet(id: ProfileId): Flow<Pet?>

    suspend fun pet(id: ProfileId): Pet?

    suspend fun savePet(id: ProfileId, state: PetState, growth: PetGrowth)
}
