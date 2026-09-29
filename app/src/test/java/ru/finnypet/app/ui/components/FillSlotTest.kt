package ru.finnypet.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.finnypet.app.domain.model.Coins

/**
 * Место кнопки «Положить N на нужное» (ревью R1): после нажатия кнопка
 * пропадает, но её место остаётся — банки ниже не прыгают вверх.
 */
class FillSlotTest {

    @Test
    fun `кнопки ещё не было — места нет`() {
        assertNull(fillSlot(fillAmount = null, kept = null))
    }

    @Test
    fun `кнопка нужна — на месте её сумма`() {
        assertEquals(Coins(29), fillSlot(fillAmount = Coins(29), kept = null))
    }

    @Test
    fun `нажали — место держит последняя сумма`() {
        assertEquals(Coins(29), fillSlot(fillAmount = null, kept = Coins(29)))
    }

    @Test
    fun `сумма изменилась — показывается новая`() {
        assertEquals(Coins(14), fillSlot(fillAmount = Coins(14), kept = Coins(29)))
    }
}
