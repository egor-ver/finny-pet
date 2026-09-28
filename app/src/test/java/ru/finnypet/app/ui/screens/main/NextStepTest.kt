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
import ru.finnypet.app.ui.text.textOf

/**
 * Главная кнопка идёт по фазе дня: план, магазин, сон. В магазин зовут
 * потребности совы, а не недотраченный план (R3). Сова в облачке объясняет,
 * почему она такая, и предлагает следующий шаг (ТЗ 2.5.9, 2.5.10).
 */
class NextStepTest {

    @Test
    fun `пока день планируется и задание не ждёт — план`() {
        assertEquals(NextStep.Plan, step(PeriodStatus.PLANNING, hasNeeds = true))
    }

    /** Правка владельца №2 (DESIGN_PLAN 3.1): пока задание ждёт награду, кнопка — задание, а не план. */
    @Test
    fun `утром за задание платят — кнопка задание, о нём говорит сова`() {
        assertEquals(NextStep.Task, step(PeriodStatus.PLANNING, taskReward = 10))
        assertEquals(
            Explanation("owl.say.task", mapOf("income" to "35", "reward" to "10")),
            phrase(NextStep.Task, reward = 10),
        )
    }

    /**
     * Все задания пройдены, а награда ещё ждёт — сова зовёт повторить: «6 из 6»
     * на плитке и «+10» на кнопке иначе выглядели ошибкой (ревью F4-fix).
     * Награда та же.
     */
    @Test
    fun `все задания пройдены — сова зовёт повторить за ту же награду`() {
        assertEquals(
            Explanation("owl.say.task_repeat", mapOf("income" to "35", "reward" to "10")),
            phrase(NextStep.Task, reward = 10, repeat = true),
        )
        val texts = ContentParser().parse(RealContent.raw()).texts
        assertTrue("повтор" in texts.textOf(Explanation("owl.say.task_repeat", mapOf("income" to "35", "reward" to "10"))).lowercase())
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

    /**
     * Снимок ревью U10+U11: в банке «Нужное» 12, всё нужное стоит 15. Кошелёк
     * выдержит, но сова не зовёт тратить сверх плана — начнём с еды.
     */
    @Test
    fun `всё нужное дороже остатка плана — сова не называет полную цену`() {
        assertEquals(
            Explanation("owl.say.plan_part.SATIETY"),
            phrase(NextStep.Shop, needs = listOf(SATIETY, CARE), cover = 15, wallet = 40, needLeft = 12),
        )
        assertEquals(
            Explanation("owl.say.plan_part.CARE"),
            phrase(NextStep.Shop, needs = listOf(CARE), cover = 15, wallet = 40, needLeft = 8),
        )
    }

    /** Остаток 11, самое дешёвое нужное 8 — в план помещается, начнём с еды. */
    @Test
    fun `в остаток плана помещается самое дешёвое нужное — начнём с еды`() {
        assertEquals(
            Explanation("owl.say.plan_part.SATIETY"),
            phrase(NextStep.Shop, needs = listOf(SATIETY, CARE), cover = 15, wallet = 40, needLeft = 11),
        )
    }

    /**
     * Снимок ревью F1/47: осталось 4, еда от 8. Любая покупка — сверх плана,
     * поэтому сова в магазин не зовёт.
     */
    @Test
    fun `в остаток плана не помещается ни один нужный товар — сова не зовёт в магазин`() {
        assertEquals(
            Explanation("owl.say.plan_short"),
            phrase(NextStep.Shop, needs = listOf(SATIETY, CARE), cover = 15, wallet = 40, needLeft = 4, startWith = null),
        )
        assertEquals(
            Explanation("owl.say.plan_short"),
            phrase(NextStep.Shop, needs = listOf(CARE), cover = 15, wallet = 40, needLeft = 0, startWith = null),
        )
    }

    /**
     * Ревью F1: кошелёк 10 не выдерживает всё нужное (22 или «целиком не
     * закрыть»), но и в остаток 3 не помещается даже еда за 8. Раньше выпадало
     * «начнём с еды» — зов тратить сверх плана.
     */
    @Test
    fun `в остаток плана не помещается ничего — plan_short при любом кошельке`() {
        assertEquals(
            Explanation("owl.say.plan_short"),
            phrase(NextStep.Shop, needs = listOf(SATIETY, CARE), cover = 22, wallet = 10, needLeft = 3, startWith = null),
        )
        assertEquals(
            Explanation("owl.say.plan_short"),
            phrase(NextStep.Shop, needs = listOf(SATIETY, CARE), cover = null, wallet = 10, needLeft = 3, startWith = null),
        )
    }

    /**
     * Ревью F1: в остаток 12 помещается только уход, еда дороже. «Начнём с
     * еды» звало бы купить её сверх плана — сова называет то, на что хватает.
     */
    @Test
    fun `в остаток плана помещается только уход — начнём с перьев, а не с еды`() {
        assertEquals(
            Explanation("owl.say.plan_part.CARE"),
            phrase(NextStep.Shop, needs = listOf(SATIETY, CARE), cover = 30, wallet = 40, needLeft = 12, startWith = CARE),
        )
        assertEquals(
            Explanation("owl.say.not_all.CARE"),
            phrase(NextStep.Shop, needs = listOf(SATIETY, CARE), cover = 52, wallet = 45, needLeft = 12, startWith = CARE),
        )
    }

    @Test
    fun `всё нужное помещается в остаток плана — сова называет цену`() {
        assertEquals(
            Explanation("owl.say.shop.SATIETY", mapOf("price" to "15")),
            phrase(NextStep.Shop, needs = listOf(SATIETY, CARE), cover = 15, wallet = 40, needLeft = 15),
        )
    }

    /** Кошелька не хватает на всё — прежняя фраза, план тут уже ни при чём. */
    @Test
    fun `кошелька не хватает на всё нужное — начнём с еды, даже если план мал`() {
        assertEquals(
            Explanation("owl.say.not_all.SATIETY"),
            phrase(NextStep.Shop, needs = listOf(SATIETY, CARE), cover = 52, wallet = 45, needLeft = 12),
        )
    }

    /**
     * Снимок ревью U12: «Пришло 35 монет», а в кошельке 45 — остаток со вчера.
     * Утренняя фраза говорит, что монеты пришли к тем, что уже были.
     */
    @Test
    fun `утренние фразы не выдают приход за весь кошелёк`() {
        val texts = ContentParser().parse(RealContent.raw()).texts
        val morning = listOf(
            phrase(NextStep.Plan),
            phrase(NextStep.Plan, needs = listOf(SATIETY)),
            phrase(NextStep.Plan, needs = listOf(CARE)),
            phrase(NextStep.Task, reward = 10),
        )

        for (explanation in morning) {
            val text = texts.textOf(explanation).replace('\u00A0', ' ')
            assertTrue(text, "Пришло ещё 35 монет" in text)
        }
    }

    /**
     * С потребностью без грусти сова нарисована и описана для TalkBack
     * спокойной (R11) — фраза просит поесть, а не жалуется «Я голодный!»,
     * иначе картинка и слова расходятся.
     */
    @Test
    fun `потребность без грусти — сова просит поесть, а не жалуется на голод`() {
        val texts = ContentParser().parse(RealContent.raw()).texts
        val calm = listOf(
            phrase(NextStep.Plan, needs = listOf(SATIETY)),
            phrase(NextStep.Shop, needs = listOf(SATIETY), cover = 14),
            phrase(NextStep.Shop, needs = listOf(SATIETY), cover = 14, needLeft = 8),
        )

        for (explanation in calm) {
            val text = texts.textOf(explanation)
            assertTrue(text, "Хочу есть" in text && "голодн" !in text)
        }
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

    /** DESIGN_PLAN 3.1: событие и грусть в один день — общая фраза, а не две подряд. */
    @Test
    fun `событие и грусть в один день — общая фраза`() {
        assertEquals(
            Explanation("event.dirty_feathers.sad.SATIETY", mapOf("care" to "10")),
            phrase(
                NextStep.Plan,
                needs = listOf(SATIETY, CARE),
                sadAbout = SATIETY,
                eventKey = "event.dirty_feathers",
                eventArgs = mapOf("care" to "10"),
            ),
        )
    }

    /** Без грусти событие говорит само за себя, задание в этот день не упоминается. */
    @Test
    fun `событие без грусти — фраза события, а не задания`() {
        assertEquals(
            Explanation("event.family_gift", mapOf("amount" to "8")),
            phrase(NextStep.Task, eventKey = "event.family_gift", eventArgs = mapOf("amount" to "8"), reward = 10),
        )
    }

    /** Грусть без события — как раньше, событие ни при чём. */
    @Test
    fun `грусть без события — прежняя фраза`() {
        assertEquals(
            Explanation("owl.say.sad.CARE"),
            phrase(NextStep.Task, sadAbout = CARE, reward = 10),
        )
    }

    /** Пропавшая фраза показалась бы ребёнку сырым ключом вроде «owl.say.shop.CARE». */
    @Test
    fun `у каждой фразы совы есть текст в контент-паке`() {
        val texts = ContentParser().parse(RealContent.raw()).texts
        val kinds = listOf(SATIETY, CARE)
        val keys = buildSet {
            for (step in listOf(NextStep.Task, NextStep.Plan, NextStep.Shop, NextStep.Sleep))
                for (needs in listOf(emptyList<PetStatKind>()) + kinds.map { listOf(it) })
                    for (sad in listOf(null) + kinds)
                        for (cover in listOf(null, 5, 100))
                            for (reward in listOf(null, 10))
                                for (needLeft in listOf(null, 0, 100))
                                    for (repeat in listOf(false, true))
                                        add(phrase(step, needs, sad, cover, wallet = 50, reward = reward, needLeft = needLeft, repeat = repeat).key)
        }

        val missing = keys.filterNot(texts::containsKey)
        assertTrue("Нет текста в explanations.json для фраз: $missing", missing.isEmpty())
    }

    /** Событийные ключи (L7) собраны вручную — их не строит перебор выше, но текст всё равно обязателен. */
    @Test
    fun `у событийных фраз тоже есть текст в контент-паке`() {
        val texts = ContentParser().parse(RealContent.raw()).texts
        val keys = listOf(
            "event.dirty_feathers", "event.family_gift",
            "event.dirty_feathers.sad.SATIETY", "event.dirty_feathers.sad.CARE",
            "event.family_gift.sad.SATIETY", "event.family_gift.sad.CARE",
        )

        val missing = keys.filterNot(texts::containsKey)
        assertTrue("Нет текста в explanations.json для фраз: $missing", missing.isEmpty())
    }

    private fun step(
        status: PeriodStatus = PeriodStatus.RUNNING,
        hasNeeds: Boolean = false,
        wallet: Int = 50,
        taskReward: Int? = null,
    ) = nextStep(status, hasNeeds, Coins(wallet), CHEAPEST_NEEDED, taskReward?.let(::Coins))

    private fun phrase(
        step: NextStep,
        needs: List<PetStatKind> = emptyList(),
        sadAbout: PetStatKind? = null,
        cover: Int? = null,
        wallet: Int = 50,
        reward: Int? = null,
        eventKey: String? = null,
        eventArgs: Map<String, String> = emptyMap(),
        needLeft: Int? = null,
        startWith: PetStatKind? = needs.firstOrNull(),
        repeat: Boolean = false,
    ) = owlPhrase(
        step = step,
        needs = needs,
        sadAbout = sadAbout,
        cover = cover?.let(::Coins),
        wallet = Coins(wallet),
        reward = reward?.let(::Coins),
        income = Coins(35),
        repeat = repeat,
        eventKey = eventKey,
        eventArgs = eventArgs,
        needLeft = needLeft?.let(::Coins),
        startWith = startWith,
    )

    private companion object {
        val CHEAPEST_NEEDED = Coins(8)
    }
}
