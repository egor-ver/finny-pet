package ru.finnypet.app.data.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.finnypet.app.domain.economy.GameClock
import ru.finnypet.app.domain.economy.TaskEngine
import ru.finnypet.app.domain.model.BudgetPlan
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.StepAnswer
import ru.finnypet.app.domain.model.TaskAttempt
import ru.finnypet.app.domain.model.TaskId
import ru.finnypet.app.domain.model.TaskStep
import ru.finnypet.app.ui.text.textOf

/**
 * Задания настоящего контент-пака (контрольная точка 4 плана): у типичной
 * ошибки своё объяснение, которое называет способ исправить (ТЗ 2.5.9), а
 * верный ответ по-прежнему засчитывается.
 */
class TaskContentTest {

    private val pack = ContentParser().parse(RealContent.raw())
    private val engine = TaskEngine(GameClock { 0L })

    private fun explain(taskId: String, answer: StepAnswer): Pair<String, Boolean> {
        val task = pack.tasks.single { it.id == TaskId(taskId) }
        val outcome = engine.outcomeFor(task, TaskAttempt(listOf(answer)))
        return outcome.explanationKey to outcome.correct
    }

    private fun jars(mandatory: Int, optional: Int, savings: Int) =
        StepAnswer.Allocated(BudgetPlan(mandatory = Coins(mandatory), optional = Coins(optional), savings = Coins(savings)))

    private fun basket(vararg items: Pair<String, Int>) =
        StepAnswer.Picked(itemIds = items.map { it.first }, spent = Coins(items.sumOf { it.second }))

    @Test
    fun `карманные деньги — запас на неожиданность, одной еды мало`() {
        assertEquals("task.plan_pocket_money.saved" to true, explain("plan-pocket-money", jars(25, 10, 5)))
        // Ловушка: положить в «Нужное» только еду — ровно 15, без запаса на мытьё.
        assertEquals("task.plan_pocket_money.no_reserve" to false, explain("plan-pocket-money", jars(15, 15, 10)))
        assertEquals("task.plan_pocket_money.otherwise" to false, explain("plan-pocket-money", jars(10, 20, 10)))
    }

    @Test
    fun `школьный список — яркая вещь не из списка вытесняет нужное`() {
        assertEquals(
            "task.plan_school_supplies.success" to true,
            explain("plan-school-supplies", basket("notebook" to 8, "pens" to 6, "ruler" to 7)),
        )
        listOf("keychain" to 12, "sparkle-pad" to 10, "markers" to 13).forEach { extra ->
            assertEquals(
                extra.first,
                "task.plan_school_supplies.extra_first" to false,
                explain("plan-school-supplies", basket(extra, "notebook" to 8, "pens" to 6)),
            )
        }
        assertEquals("task.plan_school_supplies.otherwise" to false, explain("plan-school-supplies", basket("notebook" to 8)))
    }

    /** Весь список и любая лишняя вещь не помещаются в бюджет: верный ответ нельзя «добрать» ловушкой. */
    @Test
    fun `школьный список с любой лишней вещью не помещается в кошелёк`() {
        val shelf = pack.tasks.single { it.id == TaskId("plan-school-supplies") }.steps.single() as TaskStep.Shelf
        val list = shelf.items.filter { it.id in setOf("notebook", "pens", "ruler") }.sumOf { it.price.amount }
        val cheapestExtra = shelf.items.filterNot { it.id in setOf("notebook", "pens", "ruler") }.minOf { it.price.amount }
        assertTrue(list <= shelf.budget.amount)
        assertTrue(list + cheapestExtra > shelf.budget.amount)
    }

    @Test
    fun `подарок — отложить 12 до цели, мало и ничего`() {
        assertEquals("task.save_gift_coins.success" to true, explain("save-gift-coins", jars(0, 15, 12)))
        assertEquals("task.save_gift_coins.saved_little" to false, explain("save-gift-coins", jars(0, 20, 10)))
        assertEquals("task.save_gift_coins.otherwise" to false, explain("save-gift-coins", jars(0, 30, 0)))
    }

    /**
     * Верный исход — «отложил не меньше 12», и ребёнок, положивший 15, тоже
     * здесь. Объяснение не приписывает ему ровно 12 (ревью F3-fix: «12 из
     * подарка ушли в копилку» при 15 в копилке).
     */
    @Test
    fun `подарок — верное объяснение не называет, сколько именно отложено`() {
        assertEquals("task.save_gift_coins.success" to true, explain("save-gift-coins", jars(0, 15, 15)))
        val success = pack.texts.getValue("task.save_gift_coins.success")
        assertFalse(success, "ушли в копилку" in success)
    }

    @Test
    fun `спиннер — стоит 2 дня копилки, «всего на день» и «ничего не изменится» неверны`() {
        assertEquals(
            "task.save_fair_temptation.success" to true,
            explain("save-fair-temptation", StepAnswer.Chosen("two_days_longer")),
        )
        listOf("one_day_longer", "no_change").forEach { trap ->
            assertEquals(
                trap,
                "task.save_fair_temptation.otherwise" to false,
                explain("save-fair-temptation", StepAnswer.Chosen(trap)),
            )
        }
    }

    @Test
    fun `корм в поход — коробка на 3 дня выгоднее пачки с пакетиком и мультика`() {
        assertEquals("task.pay_smart_pack.success" to true, explain("pay-smart-pack", basket("feed_big" to 18)))
        // Коробка с леденцом — тоже верно: фраза success не обещает сдачу.
        assertEquals("task.pay_smart_pack.success" to true, explain("pay-smart-pack", basket("feed_big" to 18, "lollipop" to 5)))
        // Главная ловушка: пачка на 2 дня и пакетик на 1 — три дня за 22, помещается в 25, но дороже.
        assertEquals("task.pay_smart_pack.two_days" to false, explain("pay-smart-pack", basket("feed_two" to 15, "feed_day" to 7)))
        assertEquals("task.pay_smart_pack.cartoon" to false, explain("pay-smart-pack", basket("feed_cartoon" to 10, "feed_day" to 7)))
        assertEquals("task.pay_smart_pack.day" to false, explain("pay-smart-pack", basket("feed_day" to 7, "lollipop" to 5)))
        assertEquals("task.pay_smart_pack.otherwise" to false, explain("pay-smart-pack", basket("lollipop" to 5)))
    }

    @Test
    fun `кино — только билет, набор «только сегодня» и билет с попкорном неверны`() {
        assertEquals("task.pay_cinema_cashier.success" to true, explain("pay-cinema-cashier", StepAnswer.Chosen("only_ticket")))
        assertEquals("task.pay_cinema_cashier.combo" to false, explain("pay-cinema-cashier", StepAnswer.Chosen("combo_with_cup")))
        assertEquals("task.pay_cinema_cashier.otherwise" to false, explain("pay-cinema-cashier", StepAnswer.Chosen("ticket_popcorn")))
    }

    @Test
    fun `разбор — каша и вода, а мячик, ягоды и наклейка вытесняют еду`() {
        val porridge = "food-porridge" to 14
        val water = "water-fresh" to 8
        assertEquals("task.review_hungry.success" to true, explain("review-hungry-owl", basket(porridge, water)))
        assertEquals("task.review_hungry.ball" to false, explain("review-hungry-owl", basket("toy-ball" to 24)))
        assertEquals("task.review_hungry.berry" to false, explain("review-hungry-owl", basket("treat-berry" to 12, water)))
        assertEquals("task.review_hungry.sticker" to false, explain("review-hungry-owl", basket("sticker-star" to 10, porridge)))
        assertEquals("task.review_hungry.otherwise" to false, explain("review-hungry-owl", basket(water)))
    }

    /** Ревью F7: сыт — от еды и питья вместе, вопрос говорит это заранее, а не только разбор ошибки. */
    @Test
    fun `разбор — вопрос говорит, что нужны и еда, и питьё`() {
        assertTrue("и еда, и питьё" in pack.texts.textOf("task.review_hungry.step1"))
        assertEquals("task.review_hungry.otherwise" to false, explain("review-hungry-owl", basket("food-porridge" to 14)))
        assertTrue("и еда, и питьё" in pack.texts.textOf("task.review_hungry.otherwise"))
    }

    /** Ревью F7: верно всё от 12 до 30 в копилке — фраза не называет «12 лежат», это ложь при 30. */
    @Test
    fun `подарок — всё в копилку тоже верно, и фраза остаётся правдой`() {
        val all = jars(mandatory = 0, optional = 0, savings = 30)
        assertEquals("task.save_gift_coins.success" to true, explain("save-gift-coins", all))
        assertFalse("12${NBSP}монет лежат" in pack.texts.textOf("task.save_gift_coins.success"))
    }

    /** Новые исходы дописаны в конец: имена прежних по месту в списке не сдвинулись, прохождения в базе верны. */
    @Test
    fun `верный исход каждого задания остаётся первым`() {
        pack.tasks.forEach { task ->
            assertEquals(task.id.value, "outcome-1", task.outcomes.first().id)
            assertTrue(task.id.value, task.outcomes.first().correct)
        }
    }

    @Test
    fun `у каждого исхода есть текст и неверный исход есть у каждого задания`() {
        val missing = pack.tasks.flatMap { it.outcomes }.map { it.explanationKey }.filterNot(pack.texts::containsKey)
        assertTrue("Нет текста в explanations.json для ключей: $missing", missing.isEmpty())
        pack.tasks.forEach { task -> assertFalse(task.id.value, task.outcomes.all { it.correct }) }
    }

    /**
     * «9» оставалось в конце строки, а «монет» уезжало на следующую (ревью
     * F5). С F7 тексты заданий пишут число со словом через `{coins:9}`, и
     * неразрывный пробел ставит склейка текста.
     */
    @Test
    fun `число и «монет» в текстах не разрываются`() {
        val broken = pack.texts.filterValues { Regex("""\d монет""").containsMatchIn(it) }.keys
        assertTrue("Обычный пробел между числом и «монет»: $broken", broken.isEmpty())
        assertTrue(pack.texts.textOf("task.plan_school_supplies.success").contains("9\u00A0монет"))
    }

    /** Все тексты заданий так, как их видит ребёнок: вступление, вопрос, варианты, товары, объяснения. */
    private fun shownTexts(): Map<String, String> = pack.tasks.flatMap { task ->
        listOf(task.introKey) + task.steps.flatMap { step ->
            listOf(step.promptKey) + when (step) {
                is TaskStep.Choice -> step.options.map { it.labelKey }
                is TaskStep.Shelf -> step.items.map { it.titleKey }
                is TaskStep.Distribute -> step.jars.map { it.labelKey }
                is TaskStep.PickItems -> emptyList()
            }
        } + task.outcomes.map { it.explanationKey }
    }.associateWith { pack.texts.textOf(it) }

    /**
     * F7: число и слово согласуются правилом (`{coins:N}`, `{days:N}`), а не
     * вручную, и не разрываются переносом. Незаполненное место — ошибка
     * контента, его видно скобками.
     */
    @Test
    fun `тексты заданий без незаполненных мест и число не отрывается от слова`() {
        val shown = shownTexts()
        val unfilled = shown.filterValues { '{' in it }.keys
        // Пример «12 + 15 = 27» и «меньше 30» тоже не рвутся строкой (ревью F7).
        val broken = shown.filterValues { Regex("""\d (монет|дн|ден)|\d [+=]|[+=] \d|меньше \d""").containsMatchIn(it) }.keys
        assertTrue("Незаполненные места: $unfilled", unfilled.isEmpty())
        assertTrue("Обычный пробел между числом и словом: $broken", broken.isEmpty())
    }

    /**
     * F7: вопрос шага понятен без вступления — полными предложениями, без
     * «+10 в день» и «Цель 40» (фидбек: «уточните, что цель — это комиксы»).
     */
    @Test
    fun `вопросы и варианты без сокращений вида «+10» и «Цель 40»`() {
        val keys = pack.tasks.flatMap { task ->
            task.steps.flatMap { step -> listOf(step.promptKey) + ((step as? TaskStep.Choice)?.options?.map { it.labelKey } ?: emptyList()) }
        }
        val shorthand = keys.filter { Regex("""\+\d|[Цц]ель \d""").containsMatchIn(pack.texts.textOf(it)) }
        assertTrue("Сокращения в вопросах: $shorthand", shorthand.isEmpty())
    }

    /** Эталон владельца 29.09: вопрос называет вещь, варианты — одной формы «когда — насколько позже». */
    @Test
    fun `ярмарка — вопрос называет комиксы, варианты одной формы`() {
        val prompt = pack.texts.textOf("task.save_fair_temptation.step1")
        assertTrue(prompt, "комиксы за 40${NBSP}монет" in prompt)
        assertTrue(prompt, "когда я смогу купить комиксы?" in prompt)
        assertEquals(
            listOf("Через 3${NBSP}дня — на день позже", "Через 4${NBSP}дня — на 2${NBSP}дня позже", "Через 2${NBSP}дня — как и было"),
            listOf("one_day_longer", "two_days_longer", "no_change").map { pack.texts.textOf("task.choice.$it") },
        )
    }

    /** Кино: все варианты — «что за сколько», верный не выделяется ни длиной, ни восклицанием. */
    @Test
    fun `кино — варианты одной формы «что за сколько монет»`() {
        val options = listOf("only_ticket", "combo_with_cup", "ticket_popcorn").map { pack.texts.textOf("task.choice.$it") }
        options.forEach { assertTrue(it, Regex("""за \d+${NBSP}монет$""").containsMatchIn(it)) }
    }
}

private const val NBSP = ' '
