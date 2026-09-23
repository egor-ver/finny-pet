package ru.finnypet.app.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.room.withTransaction
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import ru.finnypet.app.data.local.FinnyDatabase
import ru.finnypet.app.data.local.mapper.petStateEntityOf
import ru.finnypet.app.data.local.mapper.toDomain
import ru.finnypet.app.data.local.mapper.toEntity
import ru.finnypet.app.data.local.mapper.toGrowth
import ru.finnypet.app.data.local.mapper.toState
import ru.finnypet.app.data.settings.SettingsKeys
import ru.finnypet.app.domain.economy.GameBalance
import ru.finnypet.app.domain.economy.GameClock
import ru.finnypet.app.domain.model.Pet
import ru.finnypet.app.domain.model.PetAppearance
import ru.finnypet.app.domain.model.PetGrowth
import ru.finnypet.app.domain.model.PetState
import ru.finnypet.app.domain.model.Profile
import ru.finnypet.app.domain.model.ProfileId
import ru.finnypet.app.domain.model.Stat
import ru.finnypet.app.domain.repository.ProfileRepository
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileRepositoryImpl @Inject constructor(
    private val database: FinnyDatabase,
    private val store: DataStore<Preferences>,
    private val balance: GameBalance,
    private val clock: GameClock,
) : ProfileRepository {

    private val profiles = database.profiles()
    private val petStates = database.petStates()

    /**
     * Повреждённый файл настроек не должен ронять приложение: DataStore штатно
     * пробрасывает IOException в поток, а observeActive() — первое, что соберёт
     * главный экран. ТЗ 3.4 запрещает блокирующие ошибки, поэтому битые
     * настройки читаются как пустые: ребёнок попадёт в онбординг, а не в
     * бесконечное падение.
     */
    private val preferences: Flow<Preferences> = store.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeActive(): Flow<Profile?> = preferences
        .map { it[SettingsKeys.ACTIVE_PROFILE_ID] }
        .distinctUntilChanged()
        .flatMapLatest { id ->
            if (id == null) flowOf(null) else profiles.observeById(id)
        }
        .map { it?.toDomain() }

    override suspend fun active(): Profile? {
        val id = preferences.first()[SettingsKeys.ACTIVE_PROFILE_ID] ?: return null
        return profiles.byId(id)?.toDomain()
    }

    override suspend fun byId(id: ProfileId): Profile? = profiles.byId(id.value)?.toDomain()

    /**
     * Профиль и питомец записываются одной транзакцией: профиля без питомца в
     * игре не существует, а главный экран по ТЗ 2.5.3 обязан показать его
     * показатели. Прерывание между двумя записями оставило бы ребёнка на
     * экране без выхода.
     */
    override suspend fun create(
        childName: String,
        petName: String,
        appearance: PetAppearance,
        isTest: Boolean,
    ): Profile {
        val profile = Profile(
            id = ProfileId(UUID.randomUUID().toString()),
            childName = childName,
            petName = petName,
            appearance = appearance,
            createdAt = clock.now(),
            isTest = isTest,
        )
        database.withTransaction {
            profiles.upsert(profile.toEntity())
            petStates.upsert(
                petStateEntityOf(
                    profileId = profile.id,
                    state = PetState.uniform(Stat(balance.initialStat)),
                    growth = PetGrowth.INITIAL,
                )
            )
        }
        setActive(profile.id)
        return profile
    }

    override suspend fun testProfile(): Profile? = profiles.testProfile()?.toDomain()

    override suspend fun setActive(id: ProfileId) {
        store.edit { it[SettingsKeys.ACTIVE_PROFILE_ID] = id.value }
    }

    override suspend fun delete(id: ProfileId) {
        profiles.delete(id.value)
        // Указатель на удалённый профиль увёл бы приложение в пустой экран.
        store.edit { prefs ->
            if (prefs[SettingsKeys.ACTIVE_PROFILE_ID] == id.value) {
                prefs.remove(SettingsKeys.ACTIVE_PROFILE_ID)
            }
        }
    }

    override suspend fun deleteAll() {
        profiles.deleteAll()
        store.edit { it.remove(SettingsKeys.ACTIVE_PROFILE_ID) }
    }

    override fun observePet(id: ProfileId): Flow<Pet?> =
        petStates.observe(id.value).map { entity ->
            entity?.let { Pet(state = it.toState(), growth = it.toGrowth()) }
        }

    override suspend fun pet(id: ProfileId): Pet? =
        petStates.byProfile(id.value)?.let { Pet(state = it.toState(), growth = it.toGrowth()) }

    override suspend fun savePet(id: ProfileId, state: PetState, growth: PetGrowth) {
        petStates.upsert(petStateEntityOf(profileId = id, state = state, growth = growth))
    }
}
