package ru.finnypet.app.ui.screens.main

import ru.finnypet.app.domain.model.BudgetPlan
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.PeriodFact
import ru.finnypet.app.domain.model.PeriodStatus
import ru.finnypet.app.domain.model.SpendCategory
import ru.finnypet.app.domain.model.Transaction
import ru.finnypet.app.domain.model.TransactionType

/** Сколько по плану ещё осталось разложить (DESIGN_PLAN 3.1: три мини-банки на плитке «План»). */
data class JarsLeft(val mandatory: Coins, val optional: Coins, val savings: Coins = Coins.ZERO)

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
        savings = fact.amountFor(SpendCategory.SAVINGS).shortfallTo(plan.savings),
    )
}

/**
 * Что показать в плашках «ещё N»: не больше, чем в кошельке. После покупки
 * нужного сверх плана остаток плана бывает больше кошелька, и «Желаемое:
 * ещё 120» при 115 монетах обещало бы то, чего нет (решение владельца 28.09).
 * Только для показа: метки «не в плане», «сверх плана на …» и итоги дня
 * сверяются с самим планом ([jarsLeft]).
 */
fun JarsLeft.shownWithin(wallet: Coins): JarsLeft = JarsLeft(
    mandatory = minOf(mandatory, wallet),
    optional = minOf(optional, wallet),
    savings = minOf(savings, wallet),
)

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
    // Покупка цели без сдачи монет не двигает: строка «+0» ничего бы не объяснила.
    return listOfNotNull(carryOver) + transactions
        .filter { it.balanceDelta != 0 }
        .sortedWith(compareBy({ it.createdAt }, { it.id }))
        .map { WalletLine(type = it.type, delta = it.balanceDelta, name = nameOf(it)) }
}
