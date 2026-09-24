package ru.finnypet.app.ui.screens.main

import ru.finnypet.app.domain.model.BudgetPlan
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.GrowthStage
import ru.finnypet.app.domain.model.PeriodFact
import ru.finnypet.app.domain.model.PeriodStatus
import ru.finnypet.app.domain.model.PetGrowth
import ru.finnypet.app.domain.model.SpendCategory
import ru.finnypet.app.domain.model.Transaction
import ru.finnypet.app.domain.model.TransactionType

/** Строка роста: «До подростка 6 из 8». Очки и порог — от нуля, как в «Моём прогрессе». */
data class GrowthView(val next: GrowthStage, val points: Int, val target: Int)

/** `null` — сова взрослая, дальше расти некуда. */
fun growthOf(growth: PetGrowth, thresholds: List<Int>): GrowthView? {
    val next = GrowthStage.entries.getOrNull(growth.stage.ordinal + 1) ?: return null
    val target = thresholds.getOrNull(next.ordinal) ?: return null
    return GrowthView(next = next, points = growth.points, target = target)
}

/** Сколько по плану ещё осталось на нужное и желаемое. */
data class JarsLeft(val mandatory: Coins, val optional: Coins)

/**
 * `null` — план ещё не подтверждён: черновик плана хранится и до
 * подтверждения, но монеты по банкам ещё не разложены.
 *
 * Нужное сверх плана даёт «ещё 0», а не минус: нужное не блокируется (R4),
 * и ребёнку незачем видеть отрицательную банку.
 */
fun jarsLeft(status: PeriodStatus, plan: BudgetPlan?, fact: PeriodFact): JarsLeft? {
    if (status == PeriodStatus.PLANNING || plan == null) return null
    return JarsLeft(
        mandatory = fact.amountFor(SpendCategory.MANDATORY).shortfallTo(plan.mandatory),
        optional = fact.amountFor(SpendCategory.OPTIONAL).shortfallTo(plan.optional),
    )
}

/**
 * Строка «Кошелька сегодня». [type] `null` — остаток со вчера: он не
 * операция, а стартовый баланс дня. [name] — товар или цель, если есть.
 */
data class WalletLine(val type: TransactionType?, val delta: Int, val name: String? = null)

/**
 * Откуда пришли и куда ушли монеты (ТЗ 2.5.4: баланс не меняется без
 * объяснения). Сумма строк равна кошельку — так же его считает база.
 */
fun walletLines(
    startBalance: Coins,
    transactions: List<Transaction>,
    nameOf: (Transaction) -> String?,
): List<WalletLine> {
    val carryOver = WalletLine(type = null, delta = startBalance.amount).takeIf { startBalance > Coins.ZERO }
    return listOfNotNull(carryOver) + transactions
        .sortedWith(compareBy({ it.createdAt }, { it.id }))
        .map { WalletLine(type = it.type, delta = it.balanceDelta, name = nameOf(it)) }
}
