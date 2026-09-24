package ru.finnypet.app.ui.screens.tasks

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.PetMood

/**
 * Итог задания на эталонном сценарии (раздел 4 плана, шаг А6): сначала
 * неверный ответ — монет нет, затем другое задание верно — +10.
 */
class TaskResultTest {

    @Test
    fun `верно с первой попытки — плюс десять в кошелёк, сова радуется`() {
        assertEquals(RewardLine.PAID, rewardLine(correct = true, paid = Coins(10)))
        assertEquals(PetMood.HAPPY, taskMood(correct = true))
    }

    @Test
    fun `неверно — названо правило первой попытки, сова спокойна, а не грустит`() {
        assertEquals(RewardLine.FIRST_TRY_RULE, rewardLine(correct = false, paid = Coins.ZERO))
        assertEquals(PetMood.CALM, taskMood(correct = false))
    }

    /** Повтор после ошибки или лимит дня: ответ верный, но правило первой попытки тут было бы неправдой при лимите. */
    @Test
    fun `верно без монет — без правила первой попытки`() {
        assertEquals(RewardLine.NO_COINS, rewardLine(correct = true, paid = Coins.ZERO))
    }
}
