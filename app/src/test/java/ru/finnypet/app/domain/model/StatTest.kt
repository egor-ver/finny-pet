package ru.finnypet.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class StatTest {

    @Test
    fun `показатель ниже нуля не создаётся`() {
        assertThrows(IllegalArgumentException::class.java) { Stat(-1) }
    }

    @Test
    fun `показатель выше ста не создаётся`() {
        assertThrows(IllegalArgumentException::class.java) { Stat(101) }
    }

    @Test
    fun `границы шкалы допустимы`() {
        assertEquals(0, Stat.MIN.value)
        assertEquals(100, Stat.MAX.value)
    }

    @Test
    fun `прибавление увеличивает показатель`() {
        assertEquals(Stat(85), Stat(70) + 15)
    }

    @Test
    fun `прибавление упирается в потолок а не падает`() {
        assertEquals(Stat.MAX, Stat(95) + 20)
    }

    @Test
    fun `вычитание уменьшает показатель`() {
        assertEquals(Stat(55), Stat(70) - 15)
    }

    @Test
    fun `вычитание упирается в пол а не падает`() {
        assertEquals(Stat.MIN, Stat(10) - 20)
    }

    @Test
    fun `прибавление отрицательной дельты уменьшает показатель`() {
        assertEquals(Stat(60), Stat(70) + (-10))
    }

    @Test
    fun `сравнение упорядочивает показатели`() {
        assertTrue(Stat(30) < Stat(70))
        assertTrue(Stat(70) >= Stat(70))
    }

    @Test
    fun `показатели с одинаковым значением равны`() {
        assertEquals(Stat(70), Stat(70))
    }
}
