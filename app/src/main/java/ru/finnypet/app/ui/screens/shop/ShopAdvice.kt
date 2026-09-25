package ru.finnypet.app.ui.screens.shop

import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.Explanation
import ru.finnypet.app.domain.model.PetStatKind
import ru.finnypet.app.domain.model.ShopItem
import ru.finnypet.app.domain.model.SpendCategory
import ru.finnypet.app.ui.screens.main.JarsLeft

/** Метка на карточке товара (раздел 8 плана). Цвет не единственный признак — метка словами. */
enum class ItemMark { NEEDED_NOW, NOT_NEEDED, NOT_IN_PLAN, NONE }

/**
 * Нужное — «нужно сейчас» или «пока не нужно» (R12); желаемое дороже
 * остатка по плану — «не в плане» (R4). [optionalLeft] `null` — план ещё не
 * подтверждён, тогда покупать нельзя вовсе, и метка плана не нужна.
 */
fun markOf(item: ShopItem, neededNow: Boolean, optionalLeft: Coins?): ItemMark = when {
    item.category == SpendCategory.MANDATORY -> if (neededNow) ItemMark.NEEDED_NOW else ItemMark.NOT_NEEDED
    optionalLeft != null && !optionalLeft.covers(item.price) -> ItemMark.NOT_IN_PLAN
    else -> ItemMark.NONE
}

/**
 * Насколько цена больше того, что осталось по плану на направление товара
 * (AD-4): план мягкий, поэтому это только цифра для честного подтверждения
 * покупки, а не запрет. `null` — план ещё не подтверждён или цена в него
 * укладывается, показывать нечего.
 */
fun overPlanOf(price: Coins, category: SpendCategory, jars: JarsLeft?): Coins? {
    val left = when (category) {
        SpendCategory.MANDATORY -> jars?.mandatory
        SpendCategory.OPTIONAL -> jars?.optional
        SpendCategory.SAVINGS -> null
    } ?: return null
    return left.shortfallTo(price).takeIf { it > Coins.ZERO }
}

/**
 * Не хватит ли после покупки на нужное (раздел 3 плана, «доступность
 * нужного»): [needsCost] — цена самого дешёвого набора, закрывающего
 * потребности совы после этой покупки (`PetStateEngine.cheapestCover` на
 * состоянии с применёнными эффектами товара). `null` — покупка недоступна
 * (об этом скажет отказ, не это предупреждение), потребностей после неё не
 * остаётся, или денег хватает и на неё, и на остальное нужное.
 */
fun needsShortfallOf(balanceAfter: Coins?, needsCost: Coins): Coins? {
    if (balanceAfter == null || needsCost == Coins.ZERO) return null
    return needsCost.takeIf { !balanceAfter.covers(it) }
}

/** Что сова говорит в магазине: о первой потребности — еда раньше ухода, как везде. */
fun shopPhrase(needs: List<PetStatKind>): Explanation =
    Explanation(needs.firstOrNull()?.let { "owl.shop.need.${it.name}" } ?: "owl.shop.fed")

/**
 * «Я уже сыт — сохраним монеты?» для нужного, которое сове пока не нужно:
 * по показателю, который товар поднимает. `null` — товар ничего не поднимает.
 */
fun notNeededPhrase(item: ShopItem): Explanation? =
    item.effects.firstOrNull { it.delta > 0 }?.let { Explanation("owl.shop.not_needed.${it.stat.name}") }
