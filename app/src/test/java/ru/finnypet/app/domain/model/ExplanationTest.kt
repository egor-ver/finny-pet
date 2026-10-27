package ru.finnypet.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ExplanationTest {

    @Test
    fun `по умолчанию нет ни подстановок ни следующего шага`() {
        val explanation = Explanation(key = "purchase.done")
        assertTrue(explanation.args.isEmpty())
        assertNull(explanation.nextStep)
    }

    @Test
    fun `пустой ключ не допускается`() {
        assertThrows(IllegalArgumentException::class.java) { Explanation(key = "") }
    }

    @Test
    fun `ключ из пробелов не допускается`() {
        assertThrows(IllegalArgumentException::class.java) { Explanation(key = "   ") }
    }

    @Test
    fun `подстановки сохраняются`() {
        val explanation = Explanation(key = "purchase.rejected", args = mapOf("shortfall" to "15"))
        assertEquals("15", explanation.args["shortfall"])
    }

    @Test
    fun `следующий шаг сохраняется`() {
        val explanation = Explanation(key = "purchase.rejected", nextStep = RecoveryOption.DO_TASK)
        assertEquals(RecoveryOption.DO_TASK, explanation.nextStep)
    }

    @Test
    fun `объяснения с одинаковым содержимым равны`() {
        assertEquals(Explanation("task.done"), Explanation("task.done"))
    }
}
