package ru.finnypet.app.ui.screens.main

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.finnypet.app.data.content.ContentParser
import ru.finnypet.app.data.content.RealContent
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.Explanation
import ru.finnypet.app.domain.model.PeriodStatus
import ru.finnypet.app.domain.model.PetStatKind
import ru.finnypet.app.domain.model.PetStatKind.CARE
import ru.finnypet.app.domain.model.PetStatKind.SATIETY

/**
 * Главная кнопка идёт по фазе дня: план, магазин, сон. В магазин зовут
 * потребности совы, а не недотраченный план (R3). Сова в облачке объясняет,
 * почему она такая, и предлагает следующий шаг (ТЗ 2.5.9, 2.5.10).
 */
class NextStepTest {

    @Test
    fun `пока день планируется — план`() {
        assertEquals(NextStep.Plan, step(PeriodStatus.PLANNING, hasNeeds = true))
    }

    /** R7: сначала заработай, потом распредели — но зовёт к заданию сова, а кнопка остаётся одной дорогой. */
    @Test
    fun `утром за задание платят — кнопка план, о задании говорит сова`() {
        assertEquals(NextStep.Plan, step(PeriodStatus.PLANNING))
        assertEquals(
            Explanation("owl.say.task", mapOf("income" to "35", "reward" to "10")),
            phrase(NextStep.Plan, reward = 10),
        )
    }

    @Test
    fun `день идёт, сова хочет есть и монеты есть — магазин`() {
        assertEquals(NextStep.Shop, step(hasNeeds = true))
    }

    /** Находка на vivo: сова сыта, а кнопка звала потратить остаток плана на нужное. */
    @Test
    fun `сова сыта — спать, а не магазин`() {
        assertEquals(NextStep.Sleep, step(hasNeeds = false))
    }

    /** Все монеты потрачены — звать в магазин незачем, это тупик (ТЗ 3.4). */
    @Test
    fun `без монет — спать, а не магазин`() {
        assertEquals(NextStep.Sleep, step(hasNeeds = true, wallet = 0))
    }

    @Test
    fun `на нужное не хватает даже на самое дешёвое — спать`() {
        assertEquals(NextStep.Sleep, step(hasNeeds = true, wallet = 7))
    }

    @Test
    fun `монет ровно на самое дешёвое нужное — магазин`() {
        assertEquals(NextStep.Shop, step(hasNeeds = true, wallet = 8))
    }

    @Test
    fun `нужного в магазине нет — спать, а не магазин`() {
        assertEquals(NextStep.Sleep, nextStep(PeriodStatus.RUNNING, hasNeeds = true, Coins(50), cheapestNeeded = null))
    }

    /**
     * Б6: сове нужен только уход, самое дешёвое нужное для него — 15
     * (эталон `PetStateEngineTest`), а не 8 за воду, которая ухода не
     * поднимает. На 10 монет не хватает даже на уход — кнопка ведёт спать,
     * а не в магазин, где нужный товар всё равно не купить.
     */
    @Test
    fun `хватает на воду, но нужен только уход — спать, а не магазин`() {
        assertEquals(NextStep.Sleep, nextStep(PeriodStatus.RUNNING, hasNeeds = true, Coins(10), cheapestNeeded = Coins(15)))
    }

    /** Днём награда за задание не перебивает голод: сначала сова. */
    @Test
    fun `днём награда ждёт, а сова голодна — сова зовёт в магазин`() {
        assertEquals(
            Explanation("owl.say.shop.SATIETY", mapOf("price" to "37")),
            phrase(NextStep.Shop, needs = listOf(SATIETY, CARE), cover = 37, wallet = 48, reward = 10),
        )
    }

    /** ТЗ 2.5.10: причина грусти объясняется раньше задания и дохода. */
    @Test
    fun `утро после голодного дня — сова объясняет, почему грустит`() {
        assertEquals(
            Explanation("owl.say.sad.SATIETY"),
            phrase(NextStep.Plan, needs = listOf(SATIETY, CARE), sadAbout = SATIETY, reward = 10),
        )
    }

    @Test
    fun `утро с потребностью — сова называет её и доход`() {
        assertEquals(
            Explanation("owl.say.morning.CARE", mapOf("income" to "35")),
            phrase(NextStep.Plan, needs = listOf(CARE)),
        )
    }

    /** ТЗ 8.4: утром сова называет выбор из трёх направлений, а не готовый ответ. */
    @Test
    fun `утро без потребностей — выбор из трёх направлений`() {
        assertEquals(Explanation("owl.say.morning", mapOf("income" to "35")), phrase(NextStep.Plan))
    }

    /** Эталон раздела 4, день 3: каша, вода и витамины — 37 монет. */
    @Test
    fun `хватает на всё нужное — сова называет цену`() {
        assertEquals(
            Explanation("owl.say.shop.SATIETY", mapOf("price" to "37")),
            phrase(NextStep.Shop, needs = listOf(SATIETY, CARE), cover = 37, wallet = 40),
        )
    }

    /** Худший случай раздела 4: еда и уход дороже кошелька — начинаем с еды, тупика нет. */
    @Test
    fun `на всё нужное не хватает — начнём с еды`() {
        assertEquals(
            Explanation("owl.say.not_all.SATIETY"),
            phrase(NextStep.Shop, needs = listOf(SATIETY, CARE), cover = 52, wallet = 45),
        )
    }

    @Test
    fun `потребность в магазине целиком не закрыть — начнём с того, что есть`() {
        assertEquals(Explanation("owl.say.not_all.CARE"), phrase(NextStep.Shop, needs = listOf(CARE), cover = null))
    }

    @Test
    fun `потребности закрыты — можно спать`() {
        assertEquals(Explanation("owl.say.done"), phrase(NextStep.Sleep))
    }

    /** ТЗ 3.5: без стыда — сова не упрекает, а говорит, что будет завтра. */
    @Test
    fun `на нужное не хватает — завтра начнём с совы`() {
        assertEquals(Explanation("owl.say.no_coins"), phrase(NextStep.Sleep, needs = listOf(SATIETY), wallet = 3))
    }

    /** Пропавшая фраза показалась бы ребёнку сырым ключом вроде «owl.say.shop.CARE». */
    @Test
    fun `у каждой фразы совы есть текст в контент-паке`() {
        val texts = ContentParser().parse(RealContent.raw()).texts
        val kinds = listOf(SATIETY, CARE)
        val keys = buildSet {
            for (step in listOf(NextStep.Plan, NextStep.Shop, NextStep.Sleep))
                for (needs in listOf(emptyList<PetStatKind>()) + kinds.map { listOf(it) })
                    for (sad in listOf(null) + kinds)
                        for (cover in listOf(null, 5, 100))
                            for (reward in listOf(null, 10))
                                add(phrase(step, needs, sad, cover, wallet = 50, reward = reward).key)
        }

        val missing = keys.filterNot(texts::containsKey)
        assertTrue("Нет текста в explanations.json для фраз: $missing", missing.isEmpty())
    }

    private fun step(
        status: PeriodStatus = PeriodStatus.RUNNING,
        hasNeeds: Boolean = false,
        wallet: Int = 50,
    ) = nextStep(status, hasNeeds, Coins(wallet), CHEAPEST_NEEDED)

    private fun phrase(
        step: NextStep,
        needs: List<PetStatKind> = emptyList(),
        sadAbout: PetStatKind? = null,
        cover: Int? = null,
        wallet: Int = 50,
        reward: Int? = null,
    ) = owlPhrase(
        step = step,
        needs = needs,
        sadAbout = sadAbout,
        cover = cover?.let(::Coins),
        wallet = Coins(wallet),
        reward = reward?.let(::Coins),
        income = Coins(35),
    )

    private companion object {
        val CHEAPEST_NEEDED = Coins(8)
    }
}
