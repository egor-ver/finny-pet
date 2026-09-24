package ru.finnypet.app.ui.screens.budget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.finnypet.app.data.content.ContentParser
import ru.finnypet.app.data.content.RealContent
import ru.finnypet.app.domain.model.BudgetPlan
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.Explanation
import ru.finnypet.app.domain.model.PetMood
import ru.finnypet.app.domain.model.PetStatKind
import ru.finnypet.app.domain.model.SpendCategory

/**
 * Сова и пояснения на экране плана — на эталонном дне 3 (раздел 4 плана):
 * в кошельке 48, потребности стоят 37 (еда 22, уход 15), копилка 16 из 40.
 */
class PlanAdviceTest {

    private val pack = ContentParser().parse(RealContent.raw())
    private val texts = pack.texts
    private val wants = pack.shop.filter { it.category == SpendCategory.OPTIONAL }

    // --- Сова ---

    @Test
    fun `эталонный план дня 3 — отличный план`() {
        assertEquals(PlanOwl(PetMood.HAPPY, Explanation("owl.plan.good")), owl(plan(38, 2, 8)))
    }

    /** Утро без грусти: пока ничего не разложено, сова не судит план. */
    @Test
    fun `пустой план — сова зовёт разложить весь кошелёк`() {
        assertEquals(PlanOwl(PetMood.CALM, Explanation("owl.plan.start", mapOf("wallet" to "48"))), owl(plan(0, 0, 0)))
    }

    /** День 2 эталона: нужное 3 при потребностях 37 — сова грустит до решения, а не вечером. */
    @Test
    fun `на нужное меньше потребностей — мне не хватит`() {
        assertEquals(
            PlanOwl(PetMood.SAD, Explanation("owl.plan.not_enough", mapOf("need" to "37"))),
            owl(plan(3, 24, 8)),
        )
    }

    @Test
    fun `нужное ровно на потребности — хватит`() {
        assertEquals(PetMood.HAPPY, owl(plan(37, 3, 8)).mood)
    }

    /** Лазейка «всё в нужное» (раздел 3 плана): запас 9 ещё можно, 10 — уже нет. */
    @Test
    fun `нужное сверх потребностей и запаса — мне столько не нужно`() {
        assertEquals(PetMood.HAPPY, owl(plan(46, 0, 2)).mood)
        assertEquals(Explanation("owl.plan.too_much"), owl(plan(47, 0, 1)).phrase)
    }

    @Test
    fun `цель есть, а в копилку ноль — сова предлагает отложить`() {
        assertEquals(
            Explanation("owl.plan.no_savings", mapOf("goal" to "Комиксы")),
            owl(plan(38, 10, 0)).phrase,
        )
    }

    @Test
    fun `без цели копилка не обязательна`() {
        assertEquals(PetMood.HAPPY, owl(plan(38, 10, 0), goal = null).mood)
    }

    /** Ползунок упёрся в конец кошелька — сова объясняет это раньше всего остального. */
    @Test
    fun `монеты кончились — сначала забери из другой банки`() {
        assertEquals(Explanation("owl.plan.limit"), owl(plan(3, 37, 8), hitLimit = true).phrase)
    }

    /** Потребность нечем закрыть в магазине — о нужном сова молчит, а не выдумывает число. */
    @Test
    fun `цена потребностей неизвестна — о нужном ни слова`() {
        assertEquals(Explanation("owl.plan.good"), owl(plan(0, 10, 8), cover = null).phrase)
    }

    // --- Пояснения под банками ---

    @Test
    fun `нужное — сколько стоит каждая потребность`() {
        assertEquals(
            "Финни нужно не меньше 37: еда 22, уход 15",
            mandatoryHint(texts, mapOf(PetStatKind.SATIETY to Coins(22), PetStatKind.CARE to Coins(15))),
        )
    }

    @Test
    fun `нужное без потребностей и без известной цены`() {
        assertEquals("Финни сегодня ничего не нужно.", mandatoryHint(texts, emptyMap()))
        assertNull(mandatoryHint(texts, null))
    }

    @Test
    fun `желаемое — самое дорогое, на что хватает`() {
        assertEquals("Хватит на покупку: ⚽ Яркий мячик", optionalHint(texts, Coins(30), wants))
        assertEquals("Хватит на покупку: ⭐ Звёздочка-наклейка", optionalHint(texts, Coins(10), wants))
    }

    /** Эталон дня 3: на желаемое 2 — пока ни на что, и это не ошибка. */
    @Test
    fun `желаемое меньше самого дешёвого и ноль`() {
        assertEquals("Пока ни на что: самое дешёвое стоит 10.", optionalHint(texts, Coins(2), wants))
        assertEquals("Можно ничего не тратить на желаемое — это не ошибка.", optionalHint(texts, Coins.ZERO, wants))
    }

    /** Раздел 8 плана: копилка 16 из 40, по 8 в день — три дня. */
    @Test
    fun `копилка — срок до цели при такой сумме`() {
        assertEquals("Если откладывать по 8 в день — Комиксы через 3 дня.", savingsHint(texts, Coins(8), "Комиксы", days = 3))
        assertEquals("Если откладывать по 24 в день — Комиксы через 1 день.", savingsHint(texts, Coins(24), "Комиксы", days = 1))
        assertEquals("Если откладывать по 1 в день — Комиксы через 24 дня.", savingsHint(texts, Coins(1), "Комиксы", days = 24))
        assertEquals("Если откладывать по 2 в день — Комиксы через 12 дней.", savingsHint(texts, Coins(2), "Комиксы", days = 12))
    }

    @Test
    fun `копилка — ноль, цель собрана, цели нет`() {
        assertEquals("Если ничего не откладывать, до цели «Комиксы» не дойдём.", savingsHint(texts, Coins.ZERO, "Комиксы", days = null))
        assertEquals("На цель «Комиксы» уже собрано!", savingsHint(texts, Coins.ZERO, "Комиксы", days = 0))
        assertNull(savingsHint(texts, Coins(8), goalTitle = null, days = null))
    }

    /** Пропавшая фраза показалась бы ребёнку сырым ключом вроде «owl.plan.limit». */
    @Test
    fun `у каждой фразы совы на плане есть текст`() {
        val keys = listOf(
            owl(plan(0, 0, 0)), owl(plan(3, 0, 0)), owl(plan(47, 0, 1)), owl(plan(38, 10, 0)),
            owl(plan(38, 2, 8)), owl(plan(38, 2, 8), hitLimit = true),
        ).map { it.phrase.key }

        val missing = keys.filterNot(texts::containsKey)
        assertTrue("Нет текста в explanations.json для фраз: $missing", missing.isEmpty())
    }

    private fun plan(mandatory: Int, optional: Int, savings: Int) =
        BudgetPlan(mandatory = Coins(mandatory), optional = Coins(optional), savings = Coins(savings))

    private fun owl(
        plan: BudgetPlan,
        cover: Coins? = Coins(37),
        goal: String? = "Комиксы",
        hitLimit: Boolean = false,
    ) = planOwl(plan, wallet = Coins(48), cover = cover, slack = 9, goalTitle = goal, hitLimit = hitLimit)
}
