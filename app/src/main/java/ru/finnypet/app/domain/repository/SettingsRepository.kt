package ru.finnypet.app.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.finnypet.app.domain.model.ProfileId

/**
 * Настройки приложения: состояние сессии, а не игровые данные.
 *
 * Живут отдельно от базы, потому что переживают смену профиля и не должны
 * уходить вместе с ним при удалении.
 */
interface SettingsRepository {

    /** Профиль, в который надо вернуться после демонстрации. */
    suspend fun profileBeforeDemo(): ProfileId?

    suspend fun rememberProfileBeforeDemo(id: ProfileId)

    suspend fun forgetProfileBeforeDemo()

    /** Звук и анимации отключаются. */
    fun observeSoundEnabled(): Flow<Boolean>

    suspend fun setSoundEnabled(enabled: Boolean)

    /** Тихая мелодия по кругу — отдельно от звуков событий. */
    fun observeMusicEnabled(): Flow<Boolean>

    suspend fun setMusicEnabled(enabled: Boolean)

    fun observeAnimationsEnabled(): Flow<Boolean>

    suspend fun setAnimationsEnabled(enabled: Boolean)
}
