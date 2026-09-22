package ru.finnypet.app.domain.usecase

import ru.finnypet.app.domain.model.PetAppearance
import ru.finnypet.app.domain.model.Profile
import ru.finnypet.app.domain.repository.ProfileRepository
import ru.finnypet.app.domain.repository.SettingsRepository

/**
 * Демонстрационный режим для экспертной проверки (ТЗ 2.5.13).
 *
 * Играется в отдельном профиле с пометкой `isTest`: игра ребёнка не должна
 * ни меняться, ни показываться проверяющему. Удаление такого профиля уносит
 * каскадом всё его состояние — это и есть «сброс к исходному».
 *
 * Внешность и имена заданы здесь, а не в контент-паке: это не игровой
 * контент, а декорации демонстрации, и напарнику их править незачем.
 */
private val DEMO_APPEARANCE = PetAppearance(bodyId = "owl", colorId = "cream", accessoryId = null)
private const val DEMO_CHILD = "Гость"
private const val DEMO_PET = "Финни"

/**
 * Открывает демонстрацию заново: сносит прежний тестовый профиль и заводит
 * чистый. Повторный запуск — это и есть сброс к исходному состоянию.
 */
class StartDemo(
    private val profiles: ProfileRepository,
    private val settings: SettingsRepository,
) {

    suspend operator fun invoke(): Profile {
        // Запоминаем, куда возвращаться, до всякой правки: если эксперт
        // запустит демонстрацию второй раз, метка уже будет стоять, и
        // перетирать её тестовым профилем нельзя.
        profiles.active()
            ?.takeUnless { it.isTest }
            ?.let { settings.rememberProfileBeforeDemo(it.id) }

        profiles.deleteTestProfile()
        val profile = profiles.create(
            childName = DEMO_CHILD,
            petName = DEMO_PET,
            appearance = DEMO_APPEARANCE,
            isTest = true,
        )
        settings.setDemoMode(true)
        return profile
    }
}

/** Куда идти после выхода: в свою игру или на знакомство, если её не было. */
enum class AfterDemo { OWN_GAME, ONBOARDING }

/**
 * Закрывает демонстрацию и возвращает игру ребёнка.
 *
 * Профиля ребёнка может не быть вовсе: эксперт поставил приложение с нуля и
 * пошёл сразу в демонстрацию. Тогда возвращаться некуда, и экран обязан
 * увести на знакомство, а не показывать главный без профиля.
 */
class ExitDemo(
    private val profiles: ProfileRepository,
    private val settings: SettingsRepository,
) {

    suspend operator fun invoke(): AfterDemo {
        profiles.deleteTestProfile()
        settings.setDemoMode(false)

        val own = settings.profileBeforeDemo()
        settings.forgetProfileBeforeDemo()
        if (own == null || profiles.byId(own) == null) return AfterDemo.ONBOARDING

        profiles.setActive(own)
        return AfterDemo.OWN_GAME
    }
}

/**
 * Удаляет тестовый профиль, если он есть.
 *
 * Это единственное место во всём приложении, где что-то удаляется без спроса
 * ребёнка. Гарантия, что под удаление не попадёт его игра, — в самом запросе
 * [ProfileRepository.testProfile]: он отбирает только `isTest`.
 */
private suspend fun ProfileRepository.deleteTestProfile() {
    testProfile()?.let { delete(it.id) }
}
