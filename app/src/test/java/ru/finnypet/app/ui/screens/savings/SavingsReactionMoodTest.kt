package ru.finnypet.app.ui.screens.savings

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.finnypet.app.domain.model.PetMood

/**
 * U2: копилка не меняет показатели питомца, поэтому в окне с итогом операции
 * грустное лицо рядом с «Готово»/«Ура» выглядело бы отрицанием успеха.
 */
class SavingsReactionMoodTest {

    @Test
    fun `грусть в окне итога заменяется на спокойствие`() {
        assertEquals(PetMood.CALM, reactionMood(PetMood.SAD))
    }

    @Test
    fun `спокойствие и радость не подделываются`() {
        assertEquals(PetMood.CALM, reactionMood(PetMood.CALM))
        assertEquals(PetMood.HAPPY, reactionMood(PetMood.HAPPY))
    }
}
