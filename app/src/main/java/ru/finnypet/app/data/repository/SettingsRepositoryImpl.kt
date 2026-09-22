package ru.finnypet.app.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import ru.finnypet.app.data.settings.SettingsKeys
import ru.finnypet.app.domain.model.ProfileId
import ru.finnypet.app.domain.repository.SettingsRepository
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val store: DataStore<Preferences>,
) : SettingsRepository {

    /**
     * Повреждённый файл настроек читается как пустой: настройки — не тот
     * повод ронять приложение, а ТЗ 3.4 запрещает блокирующие ошибки во время
     * демонстрационного сценария. Значения вернутся к умолчаниям.
     */
    private val preferences: Flow<Preferences> = store.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }

    override fun observeDemoMode(): Flow<Boolean> =
        preferences.map { it[SettingsKeys.DEMO_MODE] ?: false }

    override suspend fun demoMode(): Boolean = observeDemoMode().first()

    override suspend fun setDemoMode(enabled: Boolean) {
        store.edit { it[SettingsKeys.DEMO_MODE] = enabled }
    }

    override suspend fun profileBeforeDemo(): ProfileId? =
        preferences.first()[SettingsKeys.PROFILE_BEFORE_DEMO]?.let(::ProfileId)

    override suspend fun rememberProfileBeforeDemo(id: ProfileId) {
        store.edit { it[SettingsKeys.PROFILE_BEFORE_DEMO] = id.value }
    }

    override suspend fun forgetProfileBeforeDemo() {
        store.edit { it.remove(SettingsKeys.PROFILE_BEFORE_DEMO) }
    }

    // Звук и анимации включены по умолчанию: отключение — осознанный выбор
    // пользователя, а не состояние по умолчанию (ТЗ 3.6).

    override fun observeSoundEnabled(): Flow<Boolean> =
        preferences.map { it[SettingsKeys.SOUND_ENABLED] ?: true }

    override suspend fun setSoundEnabled(enabled: Boolean) {
        store.edit { it[SettingsKeys.SOUND_ENABLED] = enabled }
    }

    override fun observeAnimationsEnabled(): Flow<Boolean> =
        preferences.map { it[SettingsKeys.ANIMATIONS_ENABLED] ?: true }

    override suspend fun setAnimationsEnabled(enabled: Boolean) {
        store.edit { it[SettingsKeys.ANIMATIONS_ENABLED] = enabled }
    }
}
