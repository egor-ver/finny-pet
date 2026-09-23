package ru.finnypet.app.domain.usecase

import ru.finnypet.app.domain.repository.ProfileRepository
import ru.finnypet.app.domain.repository.SettingsRepository

/**
 * Стирает игру по просьбе взрослого (ТЗ 2.3, 3.5): профили, питомцев, монеты,
 * копилку и задания. Звук и движение остаются — это настройки устройства.
 */
class DeleteGame(
    private val profiles: ProfileRepository,
    private val settings: SettingsRepository,
) {

    suspend operator fun invoke() {
        profiles.deleteAll()
        settings.forgetProfileBeforeDemo()
    }
}
