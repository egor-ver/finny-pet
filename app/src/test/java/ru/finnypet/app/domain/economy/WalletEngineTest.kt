package ru.finnypet.app.domain.economy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.finnypet.app.domain.model.Change
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.ItemId
import ru.finnypet.app.domain.model.PetEffect
import ru.finnypet.app.domain.model.PetStatKind
import ru.finnypet.app.domain.model.RecoveryOption
import ru.finnypet.app.domain.model.ShopItem
import ru.finnypet.app.domain.model.SpendCategory
import ru.finnypet.app.domain.model.TransactionType

class WalletEngineTest {

    private val now = 1_700_000_000L
    private val engine = WalletEngine(GameClock { now })

    private fun item(
        category: SpendCategory = SpendCategory.MANDATORY,
        price: Coins = Coins(25),
        effects: List<PetEffect> = listOf(PetEffect(PetStatKind.SATIETY, 15)),
    ) = ShopItem(
        id = ItemId("apple"),
        titleKey = "shop.apple",
        price = price,
        category = category,
        effects = effects,
    )

    @Test
    fun `покупка при достатке уменьшает баланс`() {
        val result = engine.purchase(item(), currentBalance = Coins(100), periodId = 1)
        assertEquals(Coins(75), (result as PurchaseResult.Success).newBalance)
    }

    @Test
    fun `покупка ровно на весь баланс проходит и обнуляет его`() {
        val result = engine.purchase(item(price = Coins(100)), currentBalance = Coins(100), periodId = 1)
        assertEquals(Coins.ZERO, (result as PurchaseResult.Success).newBalance)
    }

    @Test
    fun `нехватка одной монеты отклоняет покупку`() {
        val result = engine.purchase(item(price = Coins(100)), currentBalance = Coins(99), periodId = 1)
        assertEquals(Coins(1), (result as PurchaseResult.Rejected).shortfall)
    }

    @Test
    fun `отказ сообщает точную нехватку`() {
        val result = engine.purchase(item(price = Coins(100)), currentBalance = Coins(60), periodId = 1)
        assertEquals(Coins(40), (result as PurchaseResult.Rejected).shortfall)
    }

    @Test
    fun `обязательная покупка порождает транзакцию своего типа`() {
        val result = engine.purchase(item(SpendCategory.MANDATORY), Coins(100), periodId = 7)
        val transaction = (result as PurchaseResult.Success).transaction
        assertEquals(TransactionType.PURCHASE_MANDATORY, transaction.type)
    }

    @Test
    fun `необязательная покупка порождает транзакцию своего типа`() {
        val result = engine.purchase(item(SpendCategory.OPTIONAL), Coins(100), periodId = 7)
        val transaction = (result as PurchaseResult.Success).transaction
        assertEquals(TransactionType.PURCHASE_OPTIONAL, transaction.type)
    }

    @Test
    fun `транзакция помнит период товар и сумму`() {
        val result = engine.purchase(item(), Coins(100), periodId = 7)
        val transaction = (result as PurchaseResult.Success).transaction
        assertEquals(7L, transaction.periodId)
        assertEquals(ItemId("apple"), transaction.itemId)
        assertEquals(Coins(25), transaction.amount)
    }

    @Test
    fun `транзакция берёт время из часов`() {
        val result = engine.purchase(item(), Coins(100), periodId = 1)
        assertEquals(now, (result as PurchaseResult.Success).transaction.createdAt)
    }

    @Test
    fun `эффекты товара переносятся в результат`() {
        val effects = listOf(PetEffect(PetStatKind.MOOD, 10), PetEffect(PetStatKind.CARE, 5))
        val result = engine.purchase(item(effects = effects), Coins(100), periodId = 1)
        assertEquals(effects, (result as PurchaseResult.Success).effects)
    }

    @Test
    fun `объяснение покупки несёт цену и новый баланс`() {
        val result = engine.purchase(item(), Coins(100), periodId = 1)
        val args = (result as PurchaseResult.Success).explanation.args
        assertEquals("25", args["price"])
        assertEquals("75", args["balance"])
    }

    @Test
    fun `объяснение отказа несёт нехватку`() {
        val result = engine.purchase(item(price = Coins(100)), Coins(60), periodId = 1)
        assertEquals("40", (result as PurchaseResult.Rejected).explanation.args["shortfall"])
    }

    @Test
    fun `отказ всегда предлагает выполнить задание и выбрать дешевле`() {
        val result = engine.purchase(item(price = Coins(100)), Coins(10), periodId = 1)
        val options = (result as PurchaseResult.Rejected).options
        assertTrue(options.contains(RecoveryOption.DO_TASK))
        assertTrue(options.contains(RecoveryOption.CHOOSE_CHEAPER))
    }

    /** Лимит наград в день выбран — обещать монеты за задание нельзя. */
    @Test
    fun `когда задание сегодня без монет его не предлагают`() {
        val result = engine.purchase(
            item(price = Coins(100)),
            currentBalance = Coins(60),
            periodId = 1,
            taskRewardAvailable = false,
        )

        val options = (result as PurchaseResult.Rejected).options
        assertTrue(RecoveryOption.DO_TASK !in options)
        assertEquals(RecoveryOption.CHOOSE_CHEAPER, result.explanation.nextStep)
    }

    @Test
    fun `необязательную покупку предлагают отложить`() {
        val result = engine.purchase(item(SpendCategory.OPTIONAL, Coins(100)), Coins(10), periodId = 1)
        assertTrue((result as PurchaseResult.Rejected).options.contains(RecoveryOption.POSTPONE_PURCHASE))
    }

    @Test
    fun `обязательную покупку отложить не предлагают`() {
        val result = engine.purchase(item(SpendCategory.MANDATORY, Coins(100)), Coins(10), periodId = 1)
        assertFalse((result as PurchaseResult.Rejected).options.contains(RecoveryOption.POSTPONE_PURCHASE))
    }

    @Test
    fun `на обязательное предлагают снять с накоплений когда их хватает`() {
        val result = engine.purchase(
            item(SpendCategory.MANDATORY, Coins(100)),
            currentBalance = Coins(60),
            periodId = 1,
            savings = Coins(40),
        )
        assertTrue((result as PurchaseResult.Rejected).options.contains(RecoveryOption.WITHDRAW_FROM_SAVINGS))
    }

    @Test
    fun `снять с накоплений не предлагают когда их не хватает`() {
        val result = engine.purchase(
            item(SpendCategory.MANDATORY, Coins(100)),
            currentBalance = Coins(60),
            periodId = 1,
            savings = Coins(39),
        )
        assertFalse((result as PurchaseResult.Rejected).options.contains(RecoveryOption.WITHDRAW_FROM_SAVINGS))
    }

    @Test
    fun `на желаемое снять с накоплений не предлагают даже когда их хватает`() {
        val result = engine.purchase(
            item(SpendCategory.OPTIONAL, Coins(100)),
            currentBalance = Coins(60),
            periodId = 1,
            savings = Coins(500),
        )
        assertFalse((result as PurchaseResult.Rejected).options.contains(RecoveryOption.WITHDRAW_FROM_SAVINGS))
    }

    @Test
    fun `следующий шаг в объяснении совпадает с первым вариантом`() {
        val result = engine.purchase(item(price = Coins(100)), Coins(10), periodId = 1) as PurchaseResult.Rejected
        assertEquals(result.options.first(), result.explanation.nextStep)
    }

    @Test
    fun `начисление увеличивает баланс`() {
        val result = engine.credit(TransactionType.INCOME_TASK, Coins(15), Coins(60), periodId = 1)
        assertEquals(Coins(75), result.value.balance)
    }

    @Test
    fun `начисление порождает транзакцию своего типа`() {
        val result = engine.credit(TransactionType.INCOME_PERIOD, Coins(60), Coins(15), periodId = 7)
        val transaction = result.value.transaction
        assertEquals(TransactionType.INCOME_PERIOD, transaction.type)
        assertEquals(Coins(60), transaction.amount)
        assertEquals(7L, transaction.periodId)
        assertEquals(now, transaction.createdAt)
    }

    @Test
    fun `начисление сообщает изменение баланса`() {
        val result = engine.credit(TransactionType.INCOME_PERIOD, Coins(60), Coins(15), periodId = 1)
        assertEquals(listOf(Change.Balance(from = Coins(15), to = Coins(75))), result.changes)
    }

    @Test
    fun `начисление расходным типом не допускается`() {
        assertThrows(IllegalArgumentException::class.java) {
            engine.credit(TransactionType.PURCHASE_MANDATORY, Coins(15), Coins(60), periodId = 1)
        }
    }

    @Test
    fun `снятие с накоплений через начисление не допускается`() {
        assertThrows(IllegalArgumentException::class.java) {
            engine.credit(TransactionType.SAVINGS_WITHDRAW, Coins(10), Coins(20), periodId = 1)
        }
    }

    @Test
    fun `начисление нуля не допускается`() {
        assertThrows(IllegalArgumentException::class.java) {
            engine.credit(TransactionType.INCOME_TASK, Coins.ZERO, Coins(60), periodId = 1)
        }
    }
}
