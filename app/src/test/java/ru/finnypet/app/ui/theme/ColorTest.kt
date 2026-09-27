package ru.finnypet.app.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Палитра (ТЗ 3.6, DESIGN_PLAN 2.1): действие и направления трат — разные
 * цвета. Раньше «Нужное» рисовалось тем же `colorScheme.primary`, что и
 * главная кнопка, — эта проверка ловит возврат к той путанице.
 */
class ColorTest {

    @Test
    fun `цвет действия не совпадает ни с одним направлением`() {
        assertNotEquals(Primary, NeedText)
        assertNotEquals(Primary, WantText)
        assertNotEquals(Primary, SaveText)
        assertNotEquals(PrimaryDark, NeedTextDark)
        assertNotEquals(PrimaryDark, WantTextDark)
        assertNotEquals(PrimaryDark, SaveTextDark)
    }

    @Test
    fun `направления не совпадают друг с другом`() {
        val light = setOf(NeedText, WantText, SaveText)
        val dark = setOf(NeedTextDark, WantTextDark, SaveTextDark)
        assertEquals(3, light.size)
        assertEquals(3, dark.size)
    }

    /**
     * Темы заданий «Планирование» и «Покупки» (DESIGN_PLAN 2.1) — свои цвета:
     * совпади тема с направлением трат, карточка задания выглядела бы
     * подсказкой «это нужное» или «это желаемое».
     */
    @Test
    fun `темы заданий не совпадают с направлениями и действием`() {
        val light = setOf(Primary, NeedText, WantText, SaveText, TopicPlanText, TopicShopText)
        val dark = setOf(PrimaryDark, NeedTextDark, WantTextDark, SaveTextDark, TopicPlanTextDark, TopicShopTextDark)
        assertEquals(6, light.size)
        assertEquals(6, dark.size)
    }

    /** Прозрачный цвет на карточке дал бы просвечивающий фон вместо направления. */
    @Test
    fun `все цвета палитры непрозрачные`() {
        val colors = listOf(
            Background, Surface, SurfaceSunken, Outline, Ink, InkSoft,
            Primary, OnPrimary, PrimaryDeep, PrimaryContainer,
            NeedFill, NeedText, NeedContainer, WantFill, WantText, WantContainer,
            SaveFill, SaveText, SaveContainer,
            TopicPlanFill, TopicPlanText, TopicPlanContainer, TopicShopFill, TopicShopText, TopicShopContainer,
            CoinFace, CoinShadow, CoinEdge, CoinHighlight,
            ShortageText, ShortageContainer,
            BackgroundDark, SurfaceDark, SurfaceSunkenDark, OutlineDark, InkDark, InkSoftDark,
            PrimaryDark, OnPrimaryDark, PrimaryDeepDark, PrimaryContainerDark,
            NeedFillDark, NeedTextDark, NeedContainerDark, WantFillDark, WantTextDark, WantContainerDark,
            SaveFillDark, SaveTextDark, SaveContainerDark,
            TopicPlanFillDark, TopicPlanTextDark, TopicPlanContainerDark,
            TopicShopFillDark, TopicShopTextDark, TopicShopContainerDark,
            ShortageTextDark, ShortageContainerDark,
        )
        colors.forEach { assertEquals(1f, it.alpha) }
    }
}
