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

    /**
     * Чей профиль играл до входа в демонстрационный режим. Переживает
     * закрытие приложения: эксперт может выйти из него как угодно, а ребёнок
     * обязан вернуться в свою игру, а не в чужую.
     */
    val PROFILE_BEFORE_DEMO = stringPreferencesKey("profile_before_demo")

    val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")

    val ANIMATIONS_ENABLED = booleanPreferencesKey("animations_enabled")
}
