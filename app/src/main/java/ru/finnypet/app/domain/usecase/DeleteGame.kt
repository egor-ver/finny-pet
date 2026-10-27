package ru.finnypet.app.domain.usecase

import ru.finnypet.app.domain.repository.ProfileRepository
import ru.finnypet.app.domain.repository.SettingsRepository
import javax.inject.Inject

/**
 * Стирает игру по просьбе взрослого: профили, питомцев, монеты,
 * копилку и задания. Звук и движение остаются — это настройки устройства.
 */
class DeleteGame @Inject constructor(
    private val profiles: ProfileRepository,
    private val settings: SettingsRepository,
) {

    suspend operator fun invoke() {
        profiles.deleteAll()
        settings.forgetProfileBeforeDemo()
    }
}
