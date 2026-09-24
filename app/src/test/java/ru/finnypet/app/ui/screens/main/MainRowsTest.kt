package ru.finnypet.app.ui.screens.main

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.finnypet.app.domain.model.BudgetPlan
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.GoalId
import ru.finnypet.app.domain.model.GrowthStage
import ru.finnypet.app.domain.model.ItemId
import ru.finnypet.app.domain.model.PeriodFact
import ru.finnypet.app.domain.model.PeriodStatus
import ru.finnypet.app.domain.model.PetGrowth
import ru.finnypet.app.domain.model.Transaction
import ru.finnypet.app.domain.model.TransactionType

/**
 * Строки главного экрана на эталонном сценарии (раздел 4 плана): рост,
 * остатки по банкам и «Кошелёк сегодня» должны сходиться с ним до монеты.
 */
class MainRowsTest {

    // --- Строка роста ---

    @Test
    fun `день 3 утром — до подростка 6 из 8`() {
        assertEquals(GrowthView(GrowthStage.YOUNG, points = 6, target = 8), growthOf(PetGrowth(6, GrowthStage.CUB), THRESHOLDS))
    }

    @Test
    fun `вырос — до взрослого 12 из 20`() {
        assertEquals(GrowthView(GrowthStage.GROWN, points = 12, target = 20), growthOf(PetGrowth(12, GrowthStage.YOUNG), THRESHOLDS))
    }

    @Test
    fun `взрослой сове расти некуда`() {
        assertNull(growthOf(PetGrowth(24, GrowthStage.GROWN), THRESHOLDS))
    }

    // --- Строка «Монеты» ---

    /** Черновик плана хранится и до подтверждения, но монеты ещё не разложены. */
    @Test
    fun `до подтверждения плана банок нет`() {
        assertNull(jarsLeft(PeriodStatus.PLANNING, DAY_3_PLAN, PeriodFact.EMPTY))
    }

    @Test
    fun `без плана банок нет`() {
        assertNull(jarsLeft(PeriodStatus.RUNNING, plan = null, PeriodFact.EMPTY))
    }

    @Test
    fun `день 3 после каши — на нужное ещё 24, на желаемое ещё 2`() {
        assertEquals(
            JarsLeft(mandatory = Coins(24), optional = Coins(2)),
            jarsLeft(PeriodStatus.RUNNING, DAY_3_PLAN, PeriodFact.of(mandatory = Coins(14), savings = Coins(8))),
        )
    }

    /** R4: нужное не блокируется, и сверх плана банка показывает ноль, а не минус. */
    @Test
    fun `нужное сверх плана — ещё 0`() {
        assertEquals(
            JarsLeft(mandatory = Coins.ZERO, optional = Coins(2)),
            jarsLeft(PeriodStatus.RUNNING, DAY_3_PLAN, PeriodFact.of(mandatory = Coins(45))),
        )
    }

    // --- Кошелёк сегодня ---

    /** Раздел 8 плана: «+3 со вчера», «+35 доход дня», «+10 разбор», «−8 в копилку», «−14 каша» — в кошельке 26. */
    @Test
    fun `строки кошелька по порядку и в сумме дают кошелёк`() {
        val lines = walletLines(Coins(3), listOf(porridge, toSavings, income, task)) { t ->
            when {
                t.itemId != null -> "Каша"
                t.goalId != null -> "Комиксы"
                else -> null
            }
        }

        assertEquals(
            listOf(
                WalletLine(type = null, delta = 3),
                WalletLine(TransactionType.INCOME_PERIOD, 35),
                WalletLine(TransactionType.INCOME_TASK, 10),
                WalletLine(TransactionType.SAVINGS_DEPOSIT, -8, "Комиксы"),
                WalletLine(TransactionType.PURCHASE_MANDATORY, -14, "Каша"),
            ),
            lines,
        )
        assertEquals(26, lines.sumOf { it.delta })
    }

    @Test
    fun `со вчера ничего не осталось — строки нет`() {
        val lines = walletLines(Coins.ZERO, listOf(income)) { null }

        assertEquals(listOf(WalletLine(TransactionType.INCOME_PERIOD, 35)), lines)
    }

    /** День 5: комиксы куплены ровно на 40 — сдачи нет, строка «+0» ничего бы не объяснила. */
    @Test
    fun `покупка цели без сдачи не попадает в кошелёк, со сдачей — попадает`() {
        val exact = transaction(5, TransactionType.GOAL_PURCHASE, 0, at = 300, goal = "comics")
        val change = transaction(6, TransactionType.GOAL_PURCHASE, 8, at = 400, goal = "book")

        val lines = walletLines(Coins.ZERO, listOf(income, exact, change)) { null }

        assertEquals(
            listOf(WalletLine(TransactionType.INCOME_PERIOD, 35), WalletLine(TransactionType.GOAL_PURCHASE, 8)),
            lines,
        )
    }

    /** Демо проживает день за миллисекунды: время операций совпадает, порядок держит номер. */
    @Test
    fun `операции в одну миллисекунду идут по номеру`() {
        val first = income.copy(id = 1, createdAt = 5)
        val second = task.copy(id = 2, createdAt = 5)

        assertEquals(
            listOf(TransactionType.INCOME_PERIOD, TransactionType.INCOME_TASK),
            walletLines(Coins.ZERO, listOf(second, first)) { null }.map { it.type },
        )
    }

    private companion object {
        val THRESHOLDS = listOf(0, 8, 20)
        val DAY_3_PLAN = BudgetPlan(mandatory = Coins(38), optional = Coins(2), savings = Coins(8))

        fun transaction(id: Long, type: TransactionType, amount: Int, at: Long, item: String? = null, goal: String? = null) =
            Transaction(
                id = id,
                periodId = 3,
                type = type,
                amount = Coins(amount),
                reasonKey = "reason",
                createdAt = at,
                itemId = item?.let(::ItemId),
                goalId = goal?.let(::GoalId),
            )

        val income = transaction(1, TransactionType.INCOME_PERIOD, 35, at = 100)
        val task = transaction(2, TransactionType.INCOME_TASK, 10, at = 200)
        val toSavings = transaction(3, TransactionType.SAVINGS_DEPOSIT, 8, at = 300, goal = "comics")
        val porridge = transaction(4, TransactionType.PURCHASE_MANDATORY, 14, at = 400, item = "porridge")
    }
}
