package ru.finnypet.app.data.settings

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

/**
 * Ключи DataStore. Здесь живёт состояние сессии, а не игровые данные:
 * оно переживает смену профиля и не уходит вместе с ним при удалении.
 */
object SettingsKeys {

    /** Какой профиль сейчас играет. Пусто — профиля ещё нет, нужен онбординг. */
    val ACTIVE_PROFILE_ID = stringPreferencesKey("active_profile_id")

    val DEMO_MODE = booleanPreferencesKey("demo_mode")

    val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")

    val ANIMATIONS_ENABLED = booleanPreferencesKey("animations_enabled")
}
