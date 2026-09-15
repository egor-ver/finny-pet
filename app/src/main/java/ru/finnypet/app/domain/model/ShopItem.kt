package ru.finnypet.app.domain.model

data class ShopItem(
    val id: ItemId,
    val titleKey: String,
    val price: Coins,
    val category: SpendCategory,
    val effects: List<PetEffect> = emptyList(),
) {

    init {
        require(titleKey.isNotBlank()) { "Ключ названия товара не может быть пустым" }
        require(category != SpendCategory.SAVINGS) {
            "Товар относится либо к обязательным, либо к необязательным расходам"
        }
    }
}
