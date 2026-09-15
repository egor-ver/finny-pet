package ru.finnypet.app.domain.model

import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ShopItemTest {

    private fun item(
        category: SpendCategory = SpendCategory.MANDATORY,
        titleKey: String = "shop.apple",
    ) = ShopItem(
        id = ItemId("apple"),
        titleKey = titleKey,
        price = Coins(25),
        category = category,
    )

    @Test
    fun `по умолчанию у товара нет эффектов`() {
        assertTrue(item().effects.isEmpty())
    }

    @Test
    fun `пустой ключ названия не допускается`() {
        assertThrows(IllegalArgumentException::class.java) { item(titleKey = "") }
    }

    @Test
    fun `товар не может относиться к накоплениям`() {
        assertThrows(IllegalArgumentException::class.java) { item(category = SpendCategory.SAVINGS) }
    }

    @Test
    fun `обязательный товар создаётся`() {
        assertTrue(item(category = SpendCategory.MANDATORY).effects.isEmpty())
    }

    @Test
    fun `необязательный товар создаётся`() {
        assertTrue(item(category = SpendCategory.OPTIONAL).effects.isEmpty())
    }
}
