package ru.finnypet.app.ui.text

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.finnypet.app.domain.model.Explanation

/**
 * Закрепляет склейку текстов контент-пака с числами из домена.
 *
 * Ошибки контента должны быть видны, а не спрятаны: отсутствующий текст
 * показывается ключом, незаполненное место — скобками. Иначе опечатка в
 * `explanations.json` обнаружилась бы только на защите.
 */
class ContentTextTest {

    private val texts = mapOf(
        "purchase.done" to "Осталось {balance} монет, цена была {price}.",
        "purchase.rejected" to "Не хватает {shortfall} монет.",
        "shop.ball" to "Мячик",
    )

    @Test
    fun `все аргументы подставляются`() {
        val explanation = Explanation(
            key = "purchase.done",
            args = mapOf("balance" to "28", "price" to "12"),
        )

        assertEquals("Осталось 28 монет, цена была 12.", texts.textOf(explanation))
    }

    @Test
    fun `лишний аргумент не мешает`() {
        val explanation = Explanation(
            key = "purchase.rejected",
            args = mapOf("shortfall" to "6", "price" to "18"),
        )

        assertEquals("Не хватает 6 монет.", texts.textOf(explanation))
    }

    @Test
    fun `место без аргумента остаётся скобками`() {
        val explanation = Explanation(key = "purchase.done", args = mapOf("balance" to "28"))

        assertEquals("Осталось 28 монет, цена была {price}.", texts.textOf(explanation))
    }

    @Test
    fun `без текста показывается сам ключ`() {
        assertEquals("shop.unknown", texts.textOf("shop.unknown"))
        assertEquals(
            "pet.unknown",
            texts.textOf(Explanation(key = "pet.unknown", args = mapOf("x" to "1"))),
        )
    }

    @Test
    fun `название по ключу`() {
        assertEquals("Мячик", texts.textOf("shop.ball"))
    }

    @Test
    fun `одно и то же место подставляется везде`() {
        val repeated = mapOf("k" to "{n} и ещё раз {n}")

        assertEquals("5 и ещё раз 5", repeated.textOf(Explanation(key = "k", args = mapOf("n" to "5"))))
    }
}
