package ru.finnypet.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class IdsTest {

    @Test
    fun `пустой идентификатор профиля не создаётся`() {
        assertThrows(IllegalArgumentException::class.java) { ProfileId("") }
    }

    @Test
    fun `идентификатор из пробелов не создаётся`() {
        assertThrows(IllegalArgumentException::class.java) { GoalId("   ") }
    }

    @Test
    fun `пустой идентификатор задания не создаётся`() {
        assertThrows(IllegalArgumentException::class.java) { TaskId("") }
    }

    @Test
    fun `пустой идентификатор товара не создаётся`() {
        assertThrows(IllegalArgumentException::class.java) { ItemId("") }
    }

    @Test
    fun `идентификаторы с одинаковым значением равны`() {
        assertEquals(ItemId("apple"), ItemId("apple"))
    }

    @Test
    fun `значение доступно как строка`() {
        assertEquals("plan_01", TaskId("plan_01").value)
    }
}
