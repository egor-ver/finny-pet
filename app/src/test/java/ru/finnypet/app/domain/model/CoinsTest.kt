package ru.finnypet.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CoinsTest {

    @Test
    fun `отрицательная сумма не создаётся`() {
        assertThrows(IllegalArgumentException::class.java) { Coins(-1) }
    }

    @Test
    fun `ноль создаётся`() {
        assertEquals(0, Coins(0).amount)
    }

    @Test
    fun `ZERO равен нулю монет`() {
        assertEquals(Coins(0), Coins.ZERO)
    }

    @Test
    fun `сложение складывает суммы`() {
        assertEquals(Coins(70), Coins(50) + Coins(20))
    }

    @Test
    fun `сложение с нулём не меняет сумму`() {
        assertEquals(Coins(50), Coins(50) + Coins.ZERO)
    }

    @Test
    fun `вычитание уменьшает сумму`() {
        assertEquals(Coins(30), Coins(50) - Coins(20))
    }

    @Test
    fun `вычитание ровно до нуля допустимо`() {
        assertEquals(Coins.ZERO, Coins(50) - Coins(50))
    }

    @Test
    fun `вычитание больше доступного бросает`() {
        assertThrows(IllegalArgumentException::class.java) { Coins(50) - Coins(51) }
    }

    @Test
    fun `умножение повторяет сумму`() {
        assertEquals(Coins(150), Coins(50) * 3)
    }

    @Test
    fun `умножение на ноль даёт ноль`() {
        assertEquals(Coins.ZERO, Coins(50) * 0)
    }

    @Test
    fun `covers истинно когда суммы больше цены`() {
        assertTrue(Coins(50).covers(Coins(20)))
    }

    @Test
    fun `covers истинно когда сумма равна цене`() {
        assertTrue(Coins(50).covers(Coins(50)))
    }

    @Test
    fun `covers ложно когда не хватает одной монеты`() {
        assertFalse(Coins(49).covers(Coins(50)))
    }

    @Test
    fun `shortfallTo равен нулю когда хватает`() {
        assertEquals(Coins.ZERO, Coins(50).shortfallTo(Coins(20)))
    }

    @Test
    fun `shortfallTo равен нулю когда сумма равна цене`() {
        assertEquals(Coins.ZERO, Coins(50).shortfallTo(Coins(50)))
    }

    @Test
    fun `shortfallTo возвращает недостающую сумму`() {
        assertEquals(Coins(15), Coins(35).shortfallTo(Coins(50)))
    }

    @Test
    fun `сравнение упорядочивает суммы`() {
        assertTrue(Coins(20) < Coins(50))
        assertTrue(Coins(50) > Coins(20))
        assertTrue(Coins(50) >= Coins(50))
    }

    @Test
    fun `сортировка идёт по возрастанию`() {
        val sorted = listOf(Coins(50), Coins.ZERO, Coins(20)).sorted()
        assertEquals(listOf(Coins.ZERO, Coins(20), Coins(50)), sorted)
    }

    @Test
    fun `равенство сравнивает значения`() {
        assertEquals(Coins(50), Coins(50))
    }
}
