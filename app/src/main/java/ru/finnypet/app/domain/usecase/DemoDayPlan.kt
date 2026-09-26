package ru.finnypet.app.domain.usecase

import ru.finnypet.app.domain.economy.PetStateEngine
import ru.finnypet.app.domain.model.BudgetPlan
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.ItemId
import ru.finnypet.app.domain.model.PeriodFact
import ru.finnypet.app.domain.model.PetState
import ru.finnypet.app.domain.model.ShopItem
import ru.finnypet.app.domain.model.SpendCategory
import ru.finnypet.app.domain.model.totalPrice

/**
 * Одна стратегия дня для демонстрации ([PlayDemoDay]) и симуляции экономики
 * (тесты `EconomySimulationTest`): что запланировать и что купить. Раньше
 * обе стороны считали план и покупки отдельными приблизительными копиями
 * друг друга и расходились в желаемом (раздел 4 плана, L8) — теперь это
 * одна функция без побочных эффектов, а кто её вызывает решает, как
 * сохранить план и провести покупки.
 *
 * Если план на день уже есть — берётся он, а не пересчитывается заново:
 * ручной план эксперта должен определять покупки, а не быть просто
 * записью рядом с настоящими действиями (Б10-ревью). [spentToday] и
 * [boughtToday] — то, что по этому плану уже потрачено и куплено до нажатия
 * «Прожить день»: без них демо докупало бы желаемое ещё раз сверху того, что
 * эксперт уже выбрал руками (ревью L8).
 */
object DemoDayPlan {

    data class Decision(val plan: BudgetPlan, val buys: List<ShopItem>)

    /** Второй день эталона (раздел 4 плана): фиксированная необдуманная покупка. */
    val MISTAKE_ITEM: ItemId = ItemId("toy-ball")

    /** Обычный день: план — под точную цену закрытия потребностей совы. */
    fun normalDay(
        wallet: Coins,
        state: PetState,
        shop: List<ShopItem>,
        pet: PetStateEngine,
        existingPlan: BudgetPlan?,
        spentToday: PeriodFact,
        boughtToday: Set<ItemId>,
    ): Decision {
        val needs = pet.cheapestCover(state, shop).orEmpty()
        val plan = existingPlan ?: splitPlan(wallet, mandatory = minOf(needs.totalPrice(), wallet))
        val mandatoryLeft = remaining(plan.mandatory, spentToday.amountFor(SpendCategory.MANDATORY))
        val optionalLeft = remaining(plan.optional, spentToday.amountFor(SpendCategory.OPTIONAL))
        val optionalBuy = cheapestAffordable(shop, optionalLeft, boughtToday)
        return Decision(plan, withinBudget(needs, mandatoryLeft) + listOfNotNull(optionalBuy))
    }

    /**
     * Ошибочный день (раздел 4 плана, день 2): фиксированный мяч сверх плана
     * желаемого, нужное не покупается вовсе. План мягкий (AD-4) — мяч берём,
     * даже если он дороже запланированного желаемого; кошелёк проверит сама
     * покупка. Не покупаем второй раз, если эксперт уже взял его руками.
     */
    fun mistakeDay(
        wallet: Coins,
        state: PetState,
        shop: List<ShopItem>,
        pet: PetStateEngine,
        existingPlan: BudgetPlan?,
        boughtToday: Set<ItemId>,
    ): Decision {
        val plan = existingPlan ?: run {
            val needs = pet.cheapestCover(state, shop).orEmpty()
            splitPlan(wallet, mandatory = minOf(needs.totalPrice(), wallet))
        }
        val ball = shop.singleOrNull { it.id == MISTAKE_ITEM }?.takeUnless { it.id in boughtToday }
        return Decision(plan, listOfNotNull(ball))
    }

    /** Остаток после нужного делится пополам между желаемым и копилкой. */
    private fun splitPlan(available: Coins, mandatory: Coins): BudgetPlan {
        val rest = available - mandatory
        val savings = Coins(rest.amount / 2)
        return BudgetPlan(mandatory = mandatory, optional = rest - savings, savings = savings)
    }

    /** Сколько от плана осталось после уже проведённых сегодня трат; не бывает отрицательным. */
    private fun remaining(planned: Coins, spent: Coins): Coins =
        Coins((planned.amount - spent.amount).coerceAtLeast(0))

    /**
     * Из готового минимального набора нужного берём товары по возрастанию
     * цены, пока хватает плана. Кошелёк нужное не блокирует (AD-4) — блокирует
     * только бюджет демонстрации: план решает, что войдёт в показ дня.
     */
    private fun withinBudget(items: List<ShopItem>, budget: Coins): List<ShopItem> {
        var left = budget
        return items.sortedBy { it.price.amount }.filter { item ->
            (left.covers(item.price)).also { fits -> if (fits) left -= item.price }
        }
    }

    private fun cheapestAffordable(shop: List<ShopItem>, budget: Coins, exclude: Set<ItemId>): ShopItem? =
        shop.filter { it.category == SpendCategory.OPTIONAL && it.id !in exclude && budget.covers(it.price) }
            .minByOrNull { it.price.amount }
}
