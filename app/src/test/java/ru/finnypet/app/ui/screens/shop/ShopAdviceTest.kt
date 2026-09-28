package ru.finnypet.app.ui.screens.shop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.finnypet.app.data.content.ContentParser
import ru.finnypet.app.data.content.RealContent
import ru.finnypet.app.domain.economy.PetStateEngine
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.Explanation
import ru.finnypet.app.domain.model.PetState
import ru.finnypet.app.domain.model.PetStatKind
import ru.finnypet.app.domain.model.SpendCategory
import ru.finnypet.app.domain.model.Stat
import ru.finnypet.app.ui.screens.main.JarsLeft

/**
 * Метки карточек и фразы совы в магазине — на настоящем контент-паке и
 * эталонном дне 2 (раздел 4 плана): на желаемое по плану осталось 2.
 */
class ShopAdviceTest {

    private val pack = ContentParser().parse(RealContent.raw())
    private val porridge = pack.shop.single { it.id.value == "food-porridge" }
    private val ball = pack.shop.single { it.id.value == "toy-ball" }
    private val sticker = pack.shop.single { it.id.value == "sticker-star" }
    private val petState = PetStateEngine(pack.balance)

    /** Голодна, остальное в порядке: только сытость ниже порога. */
    private val hungry = PetState(
        mood = Stat(90),
        satiety = Stat(pack.balance.needThreshold - 1),
        care = Stat(90),
    )

    @Test
    fun `нужное закрывает потребность — нужно сейчас`() {
        assertEquals(ItemMark.NEEDED_NOW, markOf(porridge, neededNow = true, optionalLeft = Coins(2)))
    }

    /** R12: купить не запрещено, но метка честно говорит, что сове это пока не нужно. */
    @Test
    fun `нужное без потребности — пока не нужно`() {
        assertEquals(ItemMark.NOT_NEEDED, markOf(porridge, neededNow = false, optionalLeft = Coins(2)))
    }

    /** Эталон дня 2: на желаемое 2 — и мячик за 24, и наклейка за 10 не в плане. */
    @Test
    fun `желаемое дороже остатка по плану — не в плане`() {
        assertEquals(ItemMark.NOT_IN_PLAN, markOf(ball, neededNow = false, optionalLeft = Coins(2)))
        assertEquals(ItemMark.NOT_IN_PLAN, markOf(sticker, neededNow = false, optionalLeft = Coins(2)))
    }

    @Test
    fun `желаемое в пределах плана — без метки`() {
        assertEquals(ItemMark.NONE, markOf(sticker, neededNow = false, optionalLeft = Coins(10)))
    }

    /** Пока план не подтверждён, покупать нельзя вовсе — метка плана лишняя. */
    @Test
    fun `до подтверждения плана желаемое без метки`() {
        assertEquals(ItemMark.NONE, markOf(ball, neededNow = false, optionalLeft = null))
    }

    /**
     * L2/AD-4: план мягкий — превышение только цифра для подтверждения
     * покупки, эталон дня 2 (раздел 4 плана), на желаемое осталось 2.
     */
    @Test
    fun `превышение плана — цена минус остаток по своему направлению`() {
        val jars = JarsLeft(mandatory = Coins(9), optional = Coins(2))
        assertEquals(Coins(22), overPlanOf(ball.price, SpendCategory.OPTIONAL, jars))
        assertEquals(Coins(5), overPlanOf(porridge.price, SpendCategory.MANDATORY, jars))
    }

    @Test
    fun `цена в пределах плана — превышения нет`() {
        val jars = JarsLeft(mandatory = Coins(14), optional = Coins(10))
        assertEquals(null, overPlanOf(porridge.price, SpendCategory.MANDATORY, jars))
        assertEquals(null, overPlanOf(sticker.price, SpendCategory.OPTIONAL, jars))
    }

    /** Пока план не подтверждён, показывать превышение не по чему. */
    @Test
    fun `до подтверждения плана превышения нет`() {
        assertEquals(null, overPlanOf(ball.price, SpendCategory.OPTIONAL, jars = null))
    }

    /**
     * Раздел 3 плана, «доступность нужного»: показываем именно недостающую
     * сумму, а не цену нужного целиком — как и «Не хватает» при отказе.
     * В кошельке 3, нужное стоит 14 — не хватает 11, а не 14.
     */
    @Test
    fun `после покупки не хватает на нужное — считаем именно нехватку`() {
        assertEquals(Coins(11), needsShortfallOf(balanceAfter = Coins(3), needsCost = Coins(14)))
    }

    @Test
    fun `после покупки на нужное хватает`() {
        assertEquals(null, needsShortfallOf(balanceAfter = Coins(20), needsCost = Coins(14)))
    }

    /** Потребностей нет — предупреждать не о чем, даже если в кошельке пусто. */
    @Test
    fun `предупреждения нет, если потребностей не осталось`() {
        assertEquals(null, needsShortfallOf(balanceAfter = Coins.ZERO, needsCost = Coins.ZERO))
    }

    /** Покупка и так недоступна — об этом скажет отказ, а не это предупреждение. */
    @Test
    fun `предупреждения нет, если покупка недоступна`() {
        assertEquals(null, needsShortfallOf(balanceAfter = null, needsCost = Coins(14)))
    }

    /**
     * На настоящем контент-паке: голодная сова, мячик её не кормит — цена
     * закрытия голода (самая дешёвая вода, 8) остаётся, и денег в 3 монеты
     * на неё не хватает ровно на 5.
     */
    @Test
    fun `мячик не кормит — предупреждение с точной нехваткой`() {
        val needsCost = needsCostAfter(petState, hungry, ball, pack.shop)
        assertEquals(Coins(8), needsCost)
        assertEquals(Coins(5), needsShortfallOf(balanceAfter = Coins(3), needsCost = needsCost))
    }

    /**
     * Еда сама закрывает голод раньше, чем считается набор для предупреждения
     * (`PetStateEngine.apply` до `cheapestCover`) — ложного предупреждения нет.
     */
    @Test
    fun `каша кормит — предупреждения о нехватке на еду нет`() {
        val needsCost = needsCostAfter(petState, hungry, porridge, pack.shop)
        assertEquals(Coins.ZERO, needsCost)
        assertEquals(null, needsShortfallOf(balanceAfter = Coins(3), needsCost = needsCost))
    }

    @Test
    fun `сова говорит о первой потребности, еда раньше ухода`() {
        val both = listOf(PetStatKind.SATIETY, PetStatKind.CARE)
        assertEquals(Explanation("owl.shop.need.SATIETY"), shopPhrase(both, PetStatKind.SATIETY))
        assertEquals(Explanation("owl.shop.need.CARE"), shopPhrase(listOf(PetStatKind.CARE), PetStatKind.CARE))
        assertEquals(Explanation("owl.shop.fed"), shopPhrase(emptyList(), null))
    }

    /** Ревью F1 (r3-12): на нужное по плану осталось 3, еда от 8 — к метке «нужно сейчас» сова не зовёт. */
    @Test
    fun `в остаток плана не помещается ничего нужного — фраза про сверх плана`() {
        val both = listOf(PetStatKind.SATIETY, PetStatKind.CARE)
        assertEquals(Explanation("owl.say.plan_short"), shopPhrase(both, null))
        assertEquals(
            Explanation("owl.say.plan_short"),
            shopPhrase(both, petState.startWith(hungry, pack.shop, needLeft = Coins(3))),
        )
    }

    /** По плану хватает только на уход — сова говорит о перьях, а не о еде. */
    @Test
    fun `в остаток помещается только уход — сова говорит о перьях`() {
        assertEquals(
            Explanation("owl.shop.need.CARE"),
            shopPhrase(listOf(PetStatKind.SATIETY, PetStatKind.CARE), PetStatKind.CARE),
        )
    }

    @Test
    fun `о ненужной каше сова говорит про сытость`() {
        assertEquals(Explanation("owl.shop.not_needed.SATIETY"), notNeededPhrase(porridge))
    }

    /** Пропавшая фраза показалась бы ребёнку сырым ключом вроде «owl.shop.fed». */
    @Test
    fun `у каждой фразы совы в магазине есть текст`() {
        val keys = listOf(shopPhrase(emptyList(), null), shopPhrase(listOf(PetStatKind.SATIETY), null)) +
            listOf(PetStatKind.SATIETY, PetStatKind.CARE).map { shopPhrase(listOf(it), it) } +
            pack.shop.filter { it.category == SpendCategory.MANDATORY }.mapNotNull(::notNeededPhrase)

        val missing = keys.map { it.key }.filterNot(pack.texts::containsKey)
        assertTrue("Нет текста в explanations.json для фраз: $missing", missing.isEmpty())
    }

    /** U13: окно товара не обещает «Радость +25» при радости 100 — прирост с учётом верхней границы. */
    @Test
    fun `прирост в окне товара — не выше верхней границы`() {
        val full = PetState(mood = Stat(100), satiety = Stat(90), care = Stat(90))
        val almost = PetState(mood = Stat(90), satiety = Stat(90), care = Stat(90))

        assertTrue(petGains(petState, full, ball).isEmpty())
        assertEquals(listOf(PetStatKind.MOOD to 10), petGains(petState, almost, ball).map { it.kind to it.delta })
        assertEquals(listOf(PetStatKind.SATIETY to 25), petGains(petState, hungry, porridge).map { it.kind to it.delta })
    }

    /** U13: у каждой игрушки своя фраза — энциклопедию не называют «новой игрушкой». */
    @Test
    fun `у каждой игрушки своя фраза в контенте`() {
        val toys = pack.shop.filter { it.isToy }
        val missing = toys.map(::toyPhraseKey).filterNot(pack.texts::containsKey)

        assertTrue("Нет фразы для игрушек: $missing", toys.isNotEmpty() && missing.isEmpty())
        assertEquals(
            "{name} листает новую энциклопедию!",
            pack.texts[toyPhraseKey(pack.shop.single { it.id.value == "toy-book" })],
        )
    }

    /** U10+U11: фраза совы в магазине не начинает строку с тире — перед ним неразрывный пробел. */
    @Test
    fun `фразы совы в магазине — тире не отрывается от слова`() {
        val phrases = pack.texts.filterKeys { it.startsWith("owl.shop.") }.values

        assertTrue(phrases.isNotEmpty())
        assertTrue(phrases.none { " —" in it })
    }
}
