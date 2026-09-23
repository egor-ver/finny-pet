package ru.finnypet.app.domain.usecase

import ru.finnypet.app.domain.model.PetAppearance
import ru.finnypet.app.domain.model.Profile
import ru.finnypet.app.domain.repository.ProfileRepository
import ru.finnypet.app.domain.repository.SettingsRepository

// Демонстрационный режим для экспертной проверки (ТЗ 2.5.13) играется в
// отдельном профиле с пометкой `isTest`. Он же и признак режима: игра ребёнка
// не меняется и проверяющему не показывается, а удаление профиля каскадом
// уносит всё его состояние — это и есть сброс к исходному.

// Декорации демонстрации, а не игровой контент: в контент-паке им не место.
private val DEMO_APPEARANCE = PetAppearance(bodyId = "owl", colorId = "cream", accessoryId = null)
private const val DEMO_CHILD = "Гость"
private const val DEMO_PET = "Финни"

/** Открывает демонстрацию заново: повторный запуск и есть сброс. */
class StartDemo(
    private val profiles: ProfileRepository,
    private val settings: SettingsRepository,
) {

    suspend operator fun invoke(): Profile {
        // При повторном запуске активен уже тестовый профиль — метку
        // возврата им перетирать нельзя.
        profiles.active()
            ?.takeUnless { it.isTest }
            ?.let { settings.rememberProfileBeforeDemo(it.id) }

        profiles.deleteTestProfile()
        return profiles.create(
            childName = DEMO_CHILD,
            petName = DEMO_PET,
            appearance = DEMO_APPEARANCE,
            isTest = true,
        )
    }
}

/** Куда идти после выхода: в свою игру или на знакомство, если её не было. */
enum class AfterDemo { OWN_GAME, ONBOARDING }

/**
 * Закрывает демонстрацию и возвращает игру ребёнка. Своей игры может не быть:
 * эксперт поставил приложение с нуля и сразу пошёл в демонстрацию.
 */
class ExitDemo(
    private val profiles: ProfileRepository,
    private val settings: SettingsRepository,
) {

    suspend operator fun invoke(): AfterDemo {
        profiles.deleteTestProfile()

        val own = settings.profileBeforeDemo()
        settings.forgetProfileBeforeDemo()
        if (own == null || profiles.byId(own) == null) return AfterDemo.ONBOARDING

        profiles.setActive(own)
        return AfterDemo.OWN_GAME
    }
}

/**
 * Единственное удаление без спроса ребёнка. Его игру не заденет:
 * [ProfileRepository.testProfile] отбирает только `isTest`.
 */
private suspend fun ProfileRepository.deleteTestProfile() {
    testProfile()?.let { delete(it.id) }
}
