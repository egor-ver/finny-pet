package ru.finnypet.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class PeriodFactTest {

    private val fact = PeriodFact.of(
        mandatory = Coins(35),
        optional = Coins(30),
        savings = Coins(10),
    )

    @Test
    fun `amountFor возвращает сумму по каждому направлению`() {
        assertEquals(Coins(35), fact.amountFor(SpendCategory.MANDATORY))
        assertEquals(Coins(30), fact.amountFor(SpendCategory.OPTIONAL))
        assertEquals(Coins(10), fact.amountFor(SpendCategory.SAVINGS))
    }

    @Test
    fun `amountFor по направлению без трат возвращает ноль`() {
        val onlyMandatory = PeriodFact(mapOf(SpendCategory.MANDATORY to Coins(35)))
        assertEquals(Coins.ZERO, onlyMandatory.amountFor(SpendCategory.OPTIONAL))
        assertEquals(Coins.ZERO, onlyMandatory.amountFor(SpendCategory.SAVINGS))
    }

    @Test
    fun `amountFor пустого факта возвращает ноль по всем направлениям`() {
        SpendCategory.entries.forEach { category ->
            assertEquals(Coins.ZERO, PeriodFact.EMPTY.amountFor(category))
        }
    }

    @Test
    fun `total суммирует все направления`() {
        assertEquals(Coins(75), fact.total)
    }

    @Test
    fun `total пустого факта равен нулю`() {
        assertEquals(Coins.ZERO, PeriodFact.EMPTY.total)
    }

    @Test
    fun `total учитывает направления без трат как ноль`() {
        val onlyOptional = PeriodFact(mapOf(SpendCategory.OPTIONAL to Coins(30)))
        assertEquals(Coins(30), onlyOptional.total)
    }

    @Test
    fun `of без аргументов даёт нули по всем направлениям`() {
        assertEquals(Coins.ZERO, PeriodFact.of().total)
    }

    @Test
    fun `of заполняет только переданное направление`() {
        val onlySavings = PeriodFact.of(savings = Coins(10))
        assertEquals(Coins(10), onlySavings.amountFor(SpendCategory.SAVINGS))
        assertEquals(Coins.ZERO, onlySavings.amountFor(SpendCategory.MANDATORY))
        assertEquals(Coins.ZERO, onlySavings.amountFor(SpendCategory.OPTIONAL))
    }
}
