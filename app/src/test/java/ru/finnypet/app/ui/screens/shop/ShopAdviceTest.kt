package ru.finnypet.app.ui.screens.shop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.finnypet.app.data.content.ContentParser
import ru.finnypet.app.data.content.RealContent
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.Explanation
import ru.finnypet.app.domain.model.PetStatKind
import ru.finnypet.app.domain.model.SpendCategory

/**
 * Метки карточек и фразы совы в магазине — на настоящем контент-паке и
 * эталонном дне 3 (раздел 4 плана): на желаемое по плану осталось 2.
 */
class ShopAdviceTest {

    private val pack = ContentParser().parse(RealContent.raw())
    private val porridge = pack.shop.single { it.id.value == "food-porridge" }
    private val ball = pack.shop.single { it.id.value == "toy-ball" }
    private val sticker = pack.shop.single { it.id.value == "sticker-star" }

    @Test
    fun `нужное закрывает потребность — нужно сейчас`() {
        assertEquals(ItemMark.NEEDED_NOW, markOf(porridge, neededNow = true, optionalLeft = Coins(2)))
    }

    /** R12: купить не запрещено, но метка честно говорит, что сове это пока не нужно. */
    @Test
    fun `нужное без потребности — пока не нужно`() {
        assertEquals(ItemMark.NOT_NEEDED, markOf(porridge, neededNow = false, optionalLeft = Coins(2)))
    }

    /** Эталон дня 3: на желаемое 2 — и мячик за 24, и наклейка за 10 не в плане. */
    @Test
    fun `желаемое дороже остатка по плану — не в плане`() {
        assertEquals(ItemMark.NOT_IN_PLAN, markOf(ball, neededNow = false, optionalLeft = Coins(2)))
        assertEquals(ItemMark.NOT_IN_PLAN, markOf(sticker, neededNow = false, optionalLeft = Coins(2)))
    }

    @Test
    fun `желаемое в пределах плана — без метки`() {
        assertEquals(ItemMark.NONE, markOf(sticker, neededNow = false, optionalLeft = Coins(10)))
    }

    /** Пока план не подтверждён, покупать нельзя вовсе — метка плана лишняя. */
    @Test
    fun `до подтверждения плана желаемое без метки`() {
        assertEquals(ItemMark.NONE, markOf(ball, neededNow = false, optionalLeft = null))
    }

    @Test
    fun `сова говорит о первой потребности, еда раньше ухода`() {
        assertEquals(Explanation("owl.shop.need.SATIETY"), shopPhrase(listOf(PetStatKind.SATIETY, PetStatKind.CARE)))
        assertEquals(Explanation("owl.shop.need.CARE"), shopPhrase(listOf(PetStatKind.CARE)))
        assertEquals(Explanation("owl.shop.fed"), shopPhrase(emptyList()))
    }

    @Test
    fun `о ненужной каше сова говорит про сытость`() {
        assertEquals(Explanation("owl.shop.not_needed.SATIETY"), notNeededPhrase(porridge))
    }

    /** Пропавшая фраза показалась бы ребёнку сырым ключом вроде «owl.shop.fed». */
    @Test
    fun `у каждой фразы совы в магазине есть текст`() {
        val keys = listOf(shopPhrase(emptyList())) +
            listOf(PetStatKind.SATIETY, PetStatKind.CARE).map { shopPhrase(listOf(it)) } +
            pack.shop.filter { it.category == SpendCategory.MANDATORY }.mapNotNull(::notNeededPhrase)

        val missing = keys.map { it.key }.filterNot(pack.texts::containsKey)
        assertTrue("Нет текста в explanations.json для фраз: $missing", missing.isEmpty())
    }
}
