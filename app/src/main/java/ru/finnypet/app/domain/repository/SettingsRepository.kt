package ru.finnypet.app.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.finnypet.app.domain.model.ProfileId

/**
 * Настройки приложения: состояние сессии, а не игровые данные.
 *
 * Живут отдельно от базы, потому что переживают смену профиля и не должны
 * уходить вместе с ним при удалении (ТЗ 3.5).
 */
interface SettingsRepository {

    /** Профиль, в который надо вернуться после демонстрации. */
    suspend fun profileBeforeDemo(): ProfileId?

    suspend fun rememberProfileBeforeDemo(id: ProfileId)

    suspend fun forgetProfileBeforeDemo()

    /** Звук и анимации отключаются (ТЗ 3.6). */
    fun observeSoundEnabled(): Flow<Boolean>

    suspend fun setSoundEnabled(enabled: Boolean)

    fun observeAnimationsEnabled(): Flow<Boolean>

    suspend fun setAnimationsEnabled(enabled: Boolean)
}
