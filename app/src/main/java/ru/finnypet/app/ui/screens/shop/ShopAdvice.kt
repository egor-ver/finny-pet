package ru.finnypet.app.ui.screens.shop

import androidx.annotation.StringRes
import ru.finnypet.app.R
import ru.finnypet.app.domain.economy.PetStateEngine
import ru.finnypet.app.domain.economy.PurchaseResult
import ru.finnypet.app.domain.model.Change
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.Explanation
import ru.finnypet.app.domain.model.PetState
import ru.finnypet.app.domain.model.PetStatKind
import ru.finnypet.app.domain.model.ShopItem
import ru.finnypet.app.domain.model.SpendCategory
import ru.finnypet.app.domain.model.totalPrice
import ru.finnypet.app.ui.screens.main.JarsLeft
import ru.finnypet.app.ui.text.WordForm
import ru.finnypet.app.ui.text.textOf
import ru.finnypet.app.ui.text.wordFormOf

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
 * Цена самого дешёвого набора, закрывающего потребности совы, если этот
 * товар уже куплен (раздел 3 плана, «доступность нужного»): эффекты
 * применяются раньше `cheapestCover`, поэтому сама еда, закрывшая голод, не
 * предупреждает о нехватке на голод. Ноль — закрывать нечего.
 */
fun needsCostAfter(petState: PetStateEngine, state: PetState, item: ShopItem, shop: List<ShopItem>): Coins {
    val projected = petState.apply(state, item.effects).value
    return petState.cheapestCover(projected, shop)?.totalPrice() ?: Coins.ZERO
}

/**
 * Что покупка изменит у питомца на самом деле (ТЗ 2.5.9): у верхней границы
 * показатель не растёт, и окно товара не обещает «Радость +15» при радости
 * 100 из 100. Тот же расчёт, что после покупки ([PetStateEngine.apply]).
 */
fun petGains(petState: PetStateEngine, state: PetState, item: ShopItem): List<Change.PetStat> =
    petState.apply(state, item.effects).changes.filterIsInstance<Change.PetStat>()

/**
 * Ключ фразы «питомец играет» — своя у каждой игрушки: «играет с новой
 * игрушкой» про энциклопедию звучало бы странно. Лежит рядом с названием
 * товара в контент-паке, новая игрушка добавляется правкой JSON.
 */
fun toyPhraseKey(item: ShopItem): String = "${item.titleKey}.playing"

/**
 * Насколько после покупки не хватит на нужное (раздел 3 плана, «доступность
 * нужного»): [needsCost] — из [needsCostAfter]. `null` — покупка недоступна
 * (об этом скажет отказ, не это предупреждение), потребностей после неё не
 * остаётся, или на них хватает и так.
 */
fun needsShortfallOf(balanceAfter: Coins?, needsCost: Coins): Coins? {
    if (balanceAfter == null || needsCost == Coins.ZERO) return null
    return balanceAfter.shortfallTo(needsCost).takeIf { it > Coins.ZERO }
}

/**
 * Что сова говорит в магазине: о потребности [startWith] — первой, на которую
 * хватает остатка «Нужного» по плану, еда раньше ухода, как на главном.
 * Не хватает ни на одну — та же фраза, что на главном: иначе сова звала бы
 * к метке «нужно сейчас», хотя любая такая покупка уже сверх плана.
 */
fun shopPhrase(needs: List<PetStatKind>, startWith: PetStatKind?): Explanation = when {
    needs.isEmpty() -> Explanation("owl.shop.fed")
    startWith == null -> Explanation("owl.say.plan_short")
    else -> Explanation("owl.shop.need.${startWith.name}")
}

/**
 * «Я уже сыт — сохраним монеты?» для нужного, которое сове пока не нужно:
 * по показателю, который товар поднимает. `null` — товар ничего не поднимает.
 */
fun notNeededPhrase(item: ShopItem): Explanation? =
    item.effects.firstOrNull { it.delta > 0 }?.let { Explanation("owl.shop.not_needed.${it.stat.name}") }

/**
 * Нехватка для окна товара (DESIGN_PLAN 3.5) — из того же отказа
 * [ru.finnypet.app.domain.economy.WalletEngine.purchase], что приходил
 * вторым окном после «Купить»: варианты и их порядок решает домен, здесь
 * только подписи из контент-пака. `null` — покупка по карману.
 */
fun shortageOf(result: PurchaseResult, texts: Map<String, String>): ItemShortage? {
    val rejected = result as? PurchaseResult.Rejected ?: return null
    return ItemShortage(
        shortfall = rejected.shortfall,
        options = rejected.options.map { option ->
            RecoveryChoice(option = option, label = texts.textOf("recovery.${option.name}"))
        },
        recommended = rejected.explanation.nextStep,
    )
}

/**
 * «Не хватает 2 монет»: после «не хватает» число в родительном падеже, и
 * общая строка «2 монеты» ([ru.finnypet.app.ui.components.coinsText]) здесь
 * была бы ошибкой. Форма — по русскому правилу, как у монет.
 */
@StringRes
fun shortageLine(shortfall: Coins): Int = when (wordFormOf(shortfall.amount)) {
    WordForm.ONE -> R.string.shop_shortage_one
    WordForm.FEW -> R.string.shop_shortage_few
    WordForm.MANY -> R.string.shop_shortage_many
}
