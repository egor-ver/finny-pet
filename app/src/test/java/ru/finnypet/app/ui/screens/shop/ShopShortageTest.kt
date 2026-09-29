package ru.finnypet.app.ui.screens.shop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.finnypet.app.R
import ru.finnypet.app.data.content.ContentParser
import ru.finnypet.app.data.content.RealContent
import ru.finnypet.app.domain.economy.GameClock
import ru.finnypet.app.domain.economy.WalletEngine
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.RecoveryOption
import ru.finnypet.app.ui.components.shortageLine
import java.io.File

/**
 * Нехватка монет прямо в окне товара (DESIGN_PLAN 3.5): тот же отказ
 * домена, что приходил вторым окном, с подписями из настоящего контент-пака,
 * и строка «Не хватает N монет» в нужном падеже.
 */
class ShopShortageTest {

    private val pack = ContentParser().parse(RealContent.raw())
    private val wallet = WalletEngine(GameClock { 0L })
    private val porridge = pack.shop.single { it.id.value == "food-porridge" }
    private val ball = pack.shop.single { it.id.value == "toy-ball" }

    /** Мячик за 24 при 22 в кошельке: задание, «купить попозже», «подешевле»; главное — задание. */
    @Test
    fun `желаемое не по карману — нехватка и варианты домена с подписями`() {
        val result = wallet.purchase(ball, Coins(22), periodId = 1, savings = Coins(30), taskRewardAvailable = true)

        val shortage = shortageOf(result, pack.texts)!!

        assertEquals(Coins(2), shortage.shortfall)
        assertEquals(
            listOf("Выполнить задание", "Купить попозже", "Выбрать подешевле"),
            shortage.options.map { it.label },
        )
        assertEquals(RecoveryOption.DO_TASK, shortage.recommended)
    }

    /** Нужное: копилка покрывает нехватку — можно взять из неё; монеты за задание уже получены. */
    @Test
    fun `нужное без награды за задание — главным становится копилка`() {
        val result = wallet.purchase(porridge, Coins(12), periodId = 1, savings = Coins(2), taskRewardAvailable = false)

        val shortage = shortageOf(result, pack.texts)!!

        assertEquals(
            listOf(RecoveryOption.WITHDRAW_FROM_SAVINGS, RecoveryOption.CHOOSE_CHEAPER),
            shortage.options.map { it.option },
        )
        assertEquals(RecoveryOption.WITHDRAW_FROM_SAVINGS, shortage.recommended)
    }

    /** Копилка меньше нехватки — её не предлагают: вариант, который не поможет, был бы обманом. */
    @Test
    fun `копилка не покрывает нехватку — её нет среди вариантов`() {
        val result = wallet.purchase(porridge, Coins(10), periodId = 1, savings = Coins(3), taskRewardAvailable = false)

        assertEquals(listOf(RecoveryOption.CHOOSE_CHEAPER), shortageOf(result, pack.texts)!!.options.map { it.option })
    }

    @Test
    fun `по карману — нехватки нет`() {
        val result = wallet.purchase(ball, Coins(24), periodId = 1)

        assertNull(shortageOf(result, pack.texts))
    }

    /** У каждого варианта домена есть подпись — иначе на кнопке был бы сырой ключ «recovery.DO_TASK». */
    @Test
    fun `у каждого варианта выхода есть подпись в контенте`() {
        val missing = RecoveryOption.entries.map { "recovery.${it.name}" }.filterNot(pack.texts::containsKey)

        assertEquals(emptyList<String>(), missing)
    }

    /** «Не хватает» требует родительного падежа: «1 монеты», «2 монет», «11 монет», «21 монеты». */
    @Test
    fun `строка нехватки — в родительном падеже при любом числе`() {
        val names = mapOf(
            R.string.shortage_one to "shortage_one",
            R.string.shortage_few to "shortage_few",
            R.string.shortage_many to "shortage_many",
        )
        val strings = stringsXml()

        fun line(amount: Int): String =
            String.format(strings.getValue(names.getValue(shortageLine(Coins(amount)))), amount)

        assertEquals("Не хватает 1 монеты", line(1))
        assertEquals("Не хватает 2 монет", line(2))
        assertEquals("Не хватает 4 монет", line(4))
        assertEquals("Не хватает 5 монет", line(5))
        assertEquals("Не хватает 11 монет", line(11))
        assertEquals("Не хватает 21 монеты", line(21))
        assertEquals("Не хватает 24 монет", line(24))
    }

    /** Строки интерфейса из `values/strings.xml`: проверяется настоящий текст, а не копия в тесте. */
    private fun stringsXml(): Map<String, String> {
        val file = File("src/main/res/values/strings.xml")
        return Regex("""<string name="([^"]+)">([^<]*)</string>""")
            .findAll(file.readText())
            .associate { it.groupValues[1] to it.groupValues[2] }
    }
}
