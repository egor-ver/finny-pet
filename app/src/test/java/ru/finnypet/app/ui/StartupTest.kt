package ru.finnypet.app.ui

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import ru.finnypet.app.domain.model.PetAppearance
import ru.finnypet.app.domain.model.Profile
import ru.finnypet.app.domain.model.ProfileId

/** ТЗ 2.5.1 и 2.5.13: стартовый экран решается один раз и не перестраивает граф. */
class StartupTest {

    private val profile = Profile(
        id = ProfileId("p1"),
        childName = "Аня",
        petName = "Филя",
        appearance = PetAppearance(bodyId = "owl", colorId = "cream"),
        createdAt = 0,
    )

    @Test
    fun `сохранённый профиль — сразу главный`() = runTest {
        assertEquals(listOf(Startup.HasProfile), startupOf(flowOf(profile)).toList())
    }

    /**
     * Питомец создан — профиль появился, но старт остаётся прежним: иначе
     * граф перестроился бы на обычный главный и обучение не открылось бы.
     */
    @Test
    fun `создание питомца не меняет решённый старт`() = runTest {
        val active = flowOf(null, profile)

        assertEquals(listOf(Startup.NoProfile), startupOf(active).toList())
    }
}
