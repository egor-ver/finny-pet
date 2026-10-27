package ru.finnypet.app.ui.text

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Закрепляет русское правило склонения.
 *
 * Правило живёт в коде, а не в `plurals`, потому что Android выбирает форму по
 * локали устройства: на телефоне с английским языком русские варианты
 * подставлялись по английскому правилу, и баланс читался как «80 монеты».
 * Проверено вживую на эмуляторе — отсюда и эти тесты.
 */
class PluralTest {

    @Test
    fun `одна монета`() {
        listOf(1, 21, 101, 1001).forEach {
            assertEquals("число $it", WordForm.ONE, wordFormOf(it))
        }
    }

    @Test
    fun `две три четыре монеты`() {
        listOf(2, 3, 4, 22, 33, 44, 102).forEach {
            assertEquals("число $it", WordForm.FEW, wordFormOf(it))
        }
    }

    @Test
    fun `пять и больше монет`() {
        listOf(0, 5, 9, 20, 25, 30, 80, 90, 100).forEach {
            assertEquals("число $it", WordForm.MANY, wordFormOf(it))
        }
    }

    /** Одиннадцать и соседи обманывают последней цифрой: «21 монета», но «11 монет». */
    @Test
    fun `подростковые числа всегда монет`() {
        (11..19).forEach {
            assertEquals("число $it", WordForm.MANY, wordFormOf(it))
            assertEquals("число ${100 + it}", WordForm.MANY, wordFormOf(100 + it))
        }
    }
}
