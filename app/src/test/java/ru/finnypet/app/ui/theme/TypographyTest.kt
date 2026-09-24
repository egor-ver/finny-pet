package ru.finnypet.app.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.finnypet.app.domain.model.SpendCategory
import ru.finnypet.app.ui.components.icon

/** Дизайн-система (ТЗ 3.6, раздел 8 плана): текст от 16 sp, по иконке на направление. */
class TypographyTest {

    private val styles = with(Typography) {
        mapOf(
            "displayLarge" to displayLarge, "displayMedium" to displayMedium, "displaySmall" to displaySmall,
            "headlineLarge" to headlineLarge, "headlineMedium" to headlineMedium, "headlineSmall" to headlineSmall,
            "titleLarge" to titleLarge, "titleMedium" to titleMedium, "titleSmall" to titleSmall,
            "bodyLarge" to bodyLarge, "bodyMedium" to bodyMedium, "bodySmall" to bodySmall,
            "labelLarge" to labelLarge, "labelMedium" to labelMedium, "labelSmall" to labelSmall,
        )
    }

    /** Любой стиль, даже непривычный bodySmall, — не мельче 16 sp. */
    @Test
    fun `все стили текста не меньше 16 sp`() {
        styles.forEach { (name, style) ->
            assertTrue("$name: ${style.fontSize}", style.fontSize.value >= MIN_TEXT_SP)
        }
    }

    /** Числа — 20–24 sp: сумма в монетах читается раньше подписи. */
    @Test
    fun `стиль чисел от 20 до 24 sp`() {
        assertTrue(Typography.titleLarge.fontSize.value in 20f..24f)
    }

    @Test
    fun `у каждого направления своя иконка`() {
        assertEquals(SpendCategory.entries.size, SpendCategory.entries.map { it.icon }.toSet().size)
    }

    private companion object {
        const val MIN_TEXT_SP = 16f
    }
}
