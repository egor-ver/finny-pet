package ru.finnypet.app.domain.model

data class ShopItem(
    val id: ItemId,
    val titleKey: String,
    val price: Coins,
    val category: SpendCategory,
    val effects: List<PetEffect> = emptyList(),
    /** Картинка — эмодзи из контента (AD-11): новый товар с картинкой — правка JSON. */
    val icon: String = "",
    /** Игрушка (мяч, книга): после покупки сова видимо играет с ней (U2, ТЗ 2.5.9). */
    val isToy: Boolean = false,
) {

    init {
        require(titleKey.isNotBlank()) { "Ключ названия товара не может быть пустым" }
        require(category != SpendCategory.SAVINGS) {
            "Товар относится либо к обязательным, либо к необязательным расходам"
        }
    }
}

fun List<ShopItem>.totalPrice(): Coins = fold(Coins.ZERO) { total, item -> total + item.price }
