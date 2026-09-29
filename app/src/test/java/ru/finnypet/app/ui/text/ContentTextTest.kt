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

        assertEquals("5 и${NBSP}ещё раз 5", repeated.textOf(Explanation(key = "k", args = mapOf("n" to "5"))))
    }

    /**
     * Ревью F7: «Я», «в», «у» оставались в конце строки — «В / копилке»,
     * «в / пакетиках». После однобуквенного слова пробел неразрывный, а внутри
     * и в конце длинных слов («Сова», «коплю») пробелы обычные.
     */
    @Test
    fun `однобуквенное слово не остаётся в конце строки`() {
        val words = mapOf("k" to "А я коплю. В копилке «Нужное» в пакетиках у меня, как и было.")

        assertEquals(
            "А${NBSP}я${NBSP}коплю. В${NBSP}копилке «Нужное» в${NBSP}пакетиках у${NBSP}меня, как и${NBSP}было.",
            words.textOf("k"),
        )
    }

    /** Контрольная точка 4 плана: нигде нет «81 монет». */
    @Test
    fun `число с монетами склоняется по русскому правилу`() {
        val words = mapOf(
            "rest" to "Осталось: {coins:balance}.",
            "word.coins.ONE" to "монета",
            "word.coins.FEW" to "монеты",
            "word.coins.MANY" to "монет",
        )
        val shown = listOf("1", "2", "5", "11", "21", "81", "104").map {
            words.textOf(Explanation("rest", mapOf("balance" to it))).replace(NBSP, ' ')
        }

        assertEquals(
            listOf(
                "Осталось: 1 монета.", "Осталось: 2 монеты.", "Осталось: 5 монет.", "Осталось: 11 монет.",
                "Осталось: 21 монета.", "Осталось: 81 монета.", "Осталось: 104 монеты.",
            ),
            shown,
        )
    }

    /** Ревью F4-fix: «монет.» уезжало одно на новую строку облачка — число и слово не разрываются. */
    @Test
    fun `число и слово «монет» не разрываются переносом`() {
        val words = mapOf("rest" to "Дадут {coins:reward}.", "word.coins.MANY" to "монет")

        assertEquals("Дадут 10${NBSP}монет.", words.textOf(Explanation("rest", mapOf("reward" to "10"))))
    }

    /**
     * F7: число прямо в тексте истории («{coins:40}», «{days:2}») согласуется
     * тем же правилом, что и число из домена, — «1 день», «2 дня», «5 дней».
     */
    @Test
    fun `число в тексте истории склоняется со словом`() {
        val words = mapOf(
            "story" to "Коплю на комиксы за {coins:40}, будут через {days:2}, а не {days:1} и не {days:5}. Монета: {coins:21}.",
            "word.coins.ONE" to "монета",
            "word.coins.FEW" to "монеты",
            "word.coins.MANY" to "монет",
            "word.days.ONE" to "день",
            "word.days.FEW" to "дня",
            "word.days.MANY" to "дней",
        )

        assertEquals(
            "Коплю на комиксы за 40${NBSP}монет, будут через 2${NBSP}дня, а${NBSP}не 1${NBSP}день и${NBSP}не 5${NBSP}дней. Монета: 21${NBSP}монета.",
            words.textOf("story"),
        )
    }

    @Test
    fun `число в тексте склоняется и в объяснении с аргументами`() {
        val words = mapOf("k" to "Дадут {coins:reward}, а было {coins:3}.", "word.coins.FEW" to "монеты", "word.coins.MANY" to "монет")

        assertEquals("Дадут 10${NBSP}монет, а${NBSP}было 3${NBSP}монеты.", words.textOf(Explanation("k", mapOf("reward" to "10"))))
    }

    @Test
    fun `не число в месте для монет остаётся как есть`() {
        assertEquals("Цена: много.", mapOf("x" to "Цена: {coins:p}.").textOf(Explanation("x", mapOf("p" to "много"))))
    }
}

private const val NBSP = '\u00A0'
