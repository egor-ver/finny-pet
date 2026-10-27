package ru.finnypet.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SpendCategoryTest {

    @Test
    fun `содержит ровно три направления`() {
        assertEquals(3, SpendCategory.entries.size)
    }

    @Test
    fun `имена констант не меняются`() {
        assertEquals(
            listOf("MANDATORY", "OPTIONAL", "SAVINGS"),
            SpendCategory.entries.map { it.name },
        )
    }

    @Test
    fun `valueOf разбирает сохранённые имена`() {
        assertEquals(SpendCategory.MANDATORY, SpendCategory.valueOf("MANDATORY"))
        assertEquals(SpendCategory.OPTIONAL, SpendCategory.valueOf("OPTIONAL"))
        assertEquals(SpendCategory.SAVINGS, SpendCategory.valueOf("SAVINGS"))
    }
}
