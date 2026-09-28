package ru.finnypet.app.ui.screens.day

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.finnypet.app.data.content.ContentParser
import ru.finnypet.app.data.content.RealContent
import ru.finnypet.app.domain.economy.BudgetEngine
import ru.finnypet.app.domain.economy.GrowthEngine
import ru.finnypet.app.domain.economy.PetStateEngine
import ru.finnypet.app.domain.model.BudgetPlan
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.Explanation
import ru.finnypet.app.domain.model.GoalId
import ru.finnypet.app.domain.model.GrowthStar
import ru.finnypet.app.domain.model.ItemId
import ru.finnypet.app.domain.model.PeriodFact
import ru.finnypet.app.domain.model.PetMood
import ru.finnypet.app.domain.model.PetState
import ru.finnypet.app.domain.model.PetStatKind
import ru.finnypet.app.domain.model.SpendCategory
import ru.finnypet.app.domain.model.Stat
import ru.finnypet.app.domain.model.Transaction
import ru.finnypet.app.domain.model.TransactionType
import ru.finnypet.app.ui.components.BudgetLine
import ru.finnypet.app.ui.text.textOf

/**
 * Итоги дня на эталонном сценарии (раздел 4 плана): три строки ✓/✗ — ровно
 * три звезды дня (AD-3), а сова грустит только в голодный день.
 */
class DayChecksTest {

    private val texts = ContentParser().parse(RealContent.raw()).texts

    /** День 3: нужное 38, потрачено 37; желаемое 2 не тратили; копилка +8, до комиксов 16. */
    @Test
    fun `день 3 — всё выполнено, сова радуется`() {
        val checks = dayChecks(
            needsMet = true,
            report = report(plan(38, 2, 8), fact(37, 0, 8)),
            goalTitle = "Набор комиксов",
            goalLeft = Coins(16),
        )

        assertEquals(
            listOf(
                DayCheck(true, Explanation("day.needs.done", mapOf("spent" to "37"))),
                DayCheck(true, Explanation("day.plan.no_optional")),
                DayCheck(true, Explanation("day.savings.kept_goal", mapOf("saved" to "8", "goal" to "Набор комиксов", "left" to "16"))),
            ),
            checks,
        )
        assertEquals(PetMood.HAPPY, dayMood(checks))
    }

    /**
     * День 2 — ошибка: мячик за 24 куплен, каша нет. Оба направления расходов
     * и копилка по плану, но при незакрытой потребности звёзд нет (AD-3).
     */
    @Test
    fun `день 2 — потребности не закрыты, звёзд нет, сова грустит`() {
        val checks = dayChecks(needsMet = false, report = report(plan(3, 24, 8), fact(0, 24, 8)), goalTitle = "Набор комиксов", goalLeft = Coins(22))

        assertEquals(listOf(false, false, false), checks.map { it.done })
        assertEquals(Explanation("day.needs.missed"), checks[0].text)
        assertEquals(Explanation("day.plan.no_growth"), checks[1].text)
        assertEquals(Explanation("day.savings.hungry", mapOf("saved" to "8")), checks[2].text)
        assertEquals(PetMood.SAD, dayMood(checks))
    }

    /** Нужное можно купить сверх плана, но отклонение должно быть видно и в росте. */
    @Test
    fun `нужное сверх плана — без звезды расходов`() {
        val checks = dayChecks(needsMet = true, report = report(plan(15, 5, 5), fact(23, 5, 5)), goalTitle = null, goalLeft = null)

        assertEquals(listOf(true, false, true), checks.map { it.done })
        assertEquals(Explanation("day.plan.over_mandatory", mapOf("over" to "8")), checks[1].text)
        assertEquals(PetMood.CALM, dayMood(checks))
    }

    /** Итоги и рост считают по одним звёздам: ✓ ровно там, где звезда. */
    @Test
    fun `галочки итогов — ровно звёзды роста`() {
        val days = listOf(
            plan(30, 5, 0) to fact(30, 12, 0),
            plan(30, 5, 10) to fact(30, 0, 4),
            plan(30, 5, 10) to fact(45, 5, 10),
            plan(3, 24, 8) to fact(0, 24, 8),
        )
        listOf(true, false).forEach { needsMet ->
            days.forEach { (p, f) ->
                val stars = GrowthEngine.starsFor(report(p, f), needsMet)
                val done = dayChecks(needsMet, report(p, f), null, null).map { it.done }
                assertEquals(listOf(GrowthStar.FED, GrowthStar.PLAN, GrowthStar.SAVED).map { it in stars }, done)
            }
        }
    }

    /** Нулевой план копилки выполняется сам собой, но очков за него нет — и ✓ тоже. */
    @Test
    fun `в копилку ничего не планировали — без галочки, сова спокойна`() {
        val checks = dayChecks(needsMet = true, report = report(plan(37, 3, 0), fact(37, 3, 0)), goalTitle = null, goalLeft = null)

        assertEquals(DayCheck(false, Explanation("day.savings.none")), checks[2])
        assertEquals(PetMood.CALM, dayMood(checks))
    }

    @Test
    fun `пополнение без плана видно в итогах без звезды при любых потребностях`() {
        val budget = report(plan(30, 5, 0), fact(30, 5, 5))
        listOf(true, false).forEach { needsMet ->
            val checks = dayChecks(needsMet, budget, goalTitle = null, goalLeft = null)

            assertEquals(false, checks[2].done)
            assertEquals(Explanation("day.savings.unplanned", mapOf("saved" to "5")), checks[2].text)
            assertEquals(
                "Копилка +5. Пополнение не планировали, поэтому звезды «Отложил» нет.",
                texts.textOf(checks[2].text),
            )
            assertEquals(false, GrowthStar.SAVED in GrowthEngine.starsFor(budget, needsMet))
        }
    }

    @Test
    fun `копилку пополнили меньше плана`() {
        val checks = dayChecks(needsMet = true, report = report(plan(30, 0, 10), fact(30, 0, 4)), goalTitle = null, goalLeft = null)

        assertEquals(DayCheck(false, Explanation("day.savings.missed", mapOf("saved" to "4", "planned" to "10"))), checks[2])
    }

    @Test
    fun `желаемое сверх плана`() {
        val checks = dayChecks(needsMet = true, report = report(plan(30, 5, 0), fact(30, 12, 0)), goalTitle = null, goalLeft = null)

        assertEquals(DayCheck(false, Explanation("day.plan.over_optional", mapOf("over" to "7"))), checks[1])
    }

    @Test
    fun `оба направления сверх плана показаны отдельно`() {
        val checks = dayChecks(true, report(plan(15, 5, 5), fact(23, 12, 5)), null, null)
        assertEquals(
            DayCheck(false, Explanation("day.plan.over_both", mapOf("mandatoryOver" to "8", "optionalOver" to "7"))),
            checks[1],
        )
    }

    @Test
    fun `в день без роста соблюдённое желаемое названо даже при перерасходе нужного`() {
        val checks = dayChecks(false, report(plan(15, 5, 5), fact(23, 0, 5)), "Набор комиксов", Coins(20))
        assertEquals(listOf(false, false, false), checks.map { it.done })
        assertEquals(Explanation("day.plan.over_mandatory", mapOf("over" to "8")), checks[1].text)
        assertEquals(Explanation("day.savings.hungry", mapOf("saved" to "5")), checks[2].text)
    }

    /** R14: голодна, а на нужное монеты есть — сова переспрашивает перед сном. */
    @Test
    fun `перед сном сова переспрашивает, только если есть на что купить`() {
        assertEquals(Explanation("owl.sleep.SATIETY"), sleepWarning(listOf(PetStatKind.SATIETY, PetStatKind.CARE), canBuyMandatory = true))
        assertNull(sleepWarning(listOf(PetStatKind.SATIETY), canBuyMandatory = false))
        assertNull(sleepWarning(emptyList(), canBuyMandatory = true))
    }

    /** Пропавший текст показался бы ребёнку сырым ключом вроде «day.savings.none». */
    @Test
    fun `у каждой строки итогов и фразы перед сном есть текст`() {
        val keys = listOf(true, false).flatMap { needsMet ->
            listOf(
                plan(30, 5, 0) to fact(30, 12, 0),
                plan(30, 5, 10) to fact(30, 0, 4),
                plan(30, 5, 10) to fact(30, 5, 10),
                plan(15, 5, 5) to fact(23, 12, 5),
                plan(15, 5, 5) to fact(23, 5, 5),
                plan(30, 5, 0) to fact(30, 5, 5),
            )
                .flatMap { (p, f) ->
                    dayChecks(needsMet, report(p, f), "Цель", Coins(5)) + dayChecks(needsMet, report(p, f), null, null)
                }
        }.map { it.text.key } + listOf(PetStatKind.SATIETY, PetStatKind.CARE).map { sleepWarning(listOf(it), true)!!.key }

        val missing = keys.toSet().filterNot(texts::containsKey)
        assertTrue("Нет текста в explanations.json для ключей: $missing", missing.isEmpty())
    }

    // --- Совет дня ---

    private val shop = ContentParser().parse(RealContent.raw()).shop

    /** Эталон, день 3: каша 14 + вода 8 = 22 на еду, всё выполнено — совет про еду каждый день. */
    @Test
    fun `день 3 — всё выполнено, но еда дороже обычного — корми каждый день`() {
        val checks = dayChecks(true, report(plan(38, 2, 8), fact(37, 0, 8)), null, null)
        val spent = foodSpent(
            listOf(buy(1, "food-porridge", 14), buy(2, "water-fresh", 8), buy(3, "care-vitamins", 15)),
            shop,
        )

        assertEquals(Coins(22), spent)
        assertEquals(
            Explanation("day.tip.feed_daily", mapOf("min" to "8", "max" to "14", "spent" to "22")),
            dayTip(checks, spent, shop, goalCollected = false),
        )
        assertEquals(
            "Совет: корми меня каждый день. Обычно еда стоит 8–14, а сегодня пришлось 22.",
            texts.textOf(dayTip(checks, spent, shop, goalCollected = false)),
        )
    }

    @Test
    fun `совет — о первом, что не получилось, в порядке строк итогов`() {
        val hungry = dayChecks(false, report(plan(30, 5, 0), fact(20, 12, 0)), null, null)
        val over = dayChecks(true, report(plan(30, 5, 5), fact(30, 12, 0)), null, null)
        val noSavings = dayChecks(true, report(plan(30, 5, 0), fact(30, 5, 0)), null, null)

        assertEquals("day.tip.needs_first", dayTip(hungry, Coins(8), shop, goalCollected = false).key)
        assertEquals("day.tip.plan", dayTip(over, Coins(8), shop, goalCollected = false).key)
        assertEquals("day.tip.savings", dayTip(noSavings, Coins(8), shop, goalCollected = false).key)
    }

    /**
     * Итоги дня 2 (ревью F5): в копилке 44 при цели 40, пополнение не
     * планировали — звезды «Отложил» нет, но «монета приближает цель» уже
     * не про эту цель: совет зовёт её купить. Не собрана — прежний совет.
     */
    @Test
    fun `копилка без звезды, а на цель уже хватает — совет купить цель`() {
        val unplanned = dayChecks(true, report(plan(20, 0, 0), fact(14, 0, 44)), null, null)

        assertEquals("day.tip.goal_collected", dayTip(unplanned, Coins(8), shop, goalCollected = true).key)
        assertEquals("day.tip.savings", dayTip(unplanned, Coins(8), shop, goalCollected = false).key)
        assertEquals(
            "Совет: на цель уже хватает — её можно купить в копилке, а потом выбрать новую.",
            texts.textOf(dayTip(unplanned, Coins(8), shop, goalCollected = true)),
        )
    }

    @Test
    fun `всё выполнено и еда по обычной цене — похвала`() {
        val checks = dayChecks(true, report(plan(30, 5, 8), fact(22, 5, 8)), null, null)
        assertEquals("day.tip.keep", dayTip(checks, Coins(14), shop, goalCollected = false).key)
    }

    @Test
    fun `у каждого совета есть текст`() {
        val keys = listOf("day.tip.needs_first", "day.tip.plan", "day.tip.savings", "day.tip.goal_collected", "day.tip.feed_daily", "day.tip.keep")
        val missing = keys.filterNot(texts::containsKey)
        assertTrue("Нет текста в explanations.json для ключей: $missing", missing.isEmpty())
    }

    // --- Конец дня без повторов (DESIGN_PLAN 3.6) ---

    /**
     * День 2 эталона: тратил по плану, но сова не сыта. Строка плана стоит
     * рядом с пустым кружком и говорит, почему звезды нет (решение владельца
     * 28.09, F4): «по плану» без причины выглядело противоречием. Копилка
     * причину не повторяет и признаёт сделанное (раздел 3 плана). Вся
     * карточка звучит одним голосом — безлично, как соседние строки: «Тратил»
     * без подлежащего рядом с «Еда или уход остались…» было непонятно, кто
     * тратил (ревью F4, решение F5).
     */
    @Test
    fun `голодный день — строка плана объясняет пустой кружок, копилка не повторяет причину`() {
        val checks = dayChecks(needsMet = false, report = report(plan(3, 24, 8), fact(0, 24, 8)), goalTitle = "Набор комиксов", goalLeft = Coins(22))
        val lines = checks.map { texts.textOf(it.text) }

        assertFalse(checks[1].done)
        assertEquals("Нужное и желаемое — по плану. Звезда будет, когда еда и уход куплены.", lines[1])
        lines.forEach { line -> assertFalse("Не безлично: $line", Regex("""(^|\s)(я|мне|меня)(\s|$)""").containsMatchIn(line.lowercase())) }
        assertEquals("Копилка +8 — монеты уже ближе к цели.", lines[2])
        assertFalse("незакрыт" in lines[2] || "звёзд" in lines[2])
        assertEquals("Звёзды растут, только когда питомец сыт. Прогресс никуда не делся.", texts.textOf("growth.no_points"))
    }

    /** Итог «День закончился» не называет день успешным: сова в такой день бывает и спокойной. */
    @Test
    fun `итог обычного дня не хвалит за успех`() {
        assertEquals("День закончился. Посмотрим, что получилось!", texts.textOf("period.closed"))
    }

    /**
     * Вечером сыта (70), но уход 60 — не закрыт. Ночь снизит еду до 45, и по
     * утреннему состоянию сова «хотела бы есть»; итог называет вечернюю причину.
     */
    @Test
    fun `сова в итогах грустит о том, что не закрыто вечером, а не после ночи`() {
        val balance = ContentParser().parse(RealContent.raw()).balance
        val pet = PetStateEngine(balance)
        val evening = PetState(mood = Stat(80), satiety = Stat(70), care = Stat(60))

        assertEquals(PetStatKind.CARE, eveningNeed(evening, pet))
        assertEquals(PetStatKind.SATIETY, pet.needsOf(pet.onPeriodClosed(evening).value).first())
    }

    /** Эталон, день 3: куплено на 37, в копилку 8 — не «Потрачено 45». */
    @Test
    fun `итог под полосами — потраченное и отложенное раздельно`() {
        val lines = listOf(
            BudgetLine(SpendCategory.MANDATORY, Coins(38), Coins(37), followed = true),
            BudgetLine(SpendCategory.OPTIONAL, Coins(2), Coins(0), followed = true),
            BudgetLine(SpendCategory.SAVINGS, Coins(8), Coins(8), followed = true),
        )

        assertEquals(DayTotals(spent = Coins(37), saved = Coins(8)), dayTotals(lines))
    }

    /** U14+U16: цель собрана — строка так и говорит, а не «осталось 0» или «монеты уже ближе к цели». */
    @Test
    fun `цель собрана — копилка говорит, что собрана`() {
        val fed = dayChecks(needsMet = true, report = report(plan(38, 2, 8), fact(37, 0, 8)), goalTitle = "Набор комиксов", goalLeft = Coins.ZERO)
        val hungry = dayChecks(needsMet = false, report = report(plan(38, 2, 4), fact(0, 0, 4)), goalTitle = "Набор комиксов", goalLeft = Coins.ZERO)

        val reached = Explanation("day.savings.reached", mapOf("saved" to "8", "goal" to "Набор комиксов"))
        assertEquals(DayCheck(true, reached), fed[2])
        assertEquals(Explanation("day.savings.reached", mapOf("saved" to "4", "goal" to "Набор комиксов")), hungry[2].text)
        assertEquals("Копилка +4: цель «Набор комиксов» собрана!", texts.textOf(hungry[2].text))
    }

    /** U15: 35 из копилки дня ушли и в купленную днём цель — остаток до новой цели рядом с ними врал бы. */
    @Test
    fun `монеты дня ушли не только в текущую цель — без остатка до неё`() {
        val board = GoalId("goal-board")
        val onlyBoard = listOf(deposit(1, board, 5), buy(2, "food-porridge", 14))
        val withBought = listOf(deposit(1, GoalId("goal-book"), 30), goalPurchase(2, GoalId("goal-book")), deposit(3, board, 5))

        assertTrue(savedOnlyFor(board, onlyBoard))
        assertEquals(false, savedOnlyFor(board, withBought))
        assertEquals(false, savedOnlyFor(board, listOf(deposit(1, GoalId("goal-book"), 5))))
    }

    /** F3-fix: голодный день, монеты дня ушли и в купленную цель — «уже ближе к цели» врало бы, только сумма. */
    @Test
    fun `голодный день без остатка до цели — копилка без слов о цели`() {
        val checks = dayChecks(needsMet = false, report = report(plan(3, 24, 8), fact(0, 24, 8)), goalTitle = null, goalLeft = null)

        assertEquals(false, checks[2].done)
        assertEquals(Explanation("day.savings.kept", mapOf("saved" to "8")), checks[2].text)
        assertEquals("Копилка +8.", texts.textOf(checks[2].text))
    }

    /** «Накопления тоже по плану» при плане 5 и копилке +35 звучало как «ровно 5» — фраза говорит «не меньше». */
    @Test
    fun `итог дня по плану не спорит с копилкой больше плана`() {
        assertEquals(
            "Всё по плану: потратили не больше задуманного, а отложили не меньше!",
            texts.textOf("period.plan_followed"),
        )
    }

    /** Заработанные звёзды загораются по очереди через 150 мс; пустой слот очередь не занимает. */
    @Test
    fun `звёзды дня загораются по очереди, пропуская пустые слоты`() {
        val done = listOf(true, false, true)

        assertEquals(listOf(0, 150, 150), done.indices.map { starDelayMs(done, it) })
    }

    private fun buy(id: Long, item: String, price: Int) = Transaction(
        id = id,
        periodId = 3,
        type = TransactionType.PURCHASE_MANDATORY,
        amount = Coins(price),
        reasonKey = "purchase.done",
        createdAt = id,
        itemId = ItemId(item),
    )

    private fun deposit(id: Long, goal: GoalId, amount: Int) = Transaction(
        id = id,
        periodId = 3,
        type = TransactionType.SAVINGS_DEPOSIT,
        amount = Coins(amount),
        reasonKey = "savings.deposited",
        createdAt = id,
        goalId = goal,
    )

    private fun goalPurchase(id: Long, goal: GoalId) = Transaction(
        id = id,
        periodId = 3,
        type = TransactionType.GOAL_PURCHASE,
        amount = Coins.ZERO,
        reasonKey = "savings.goal_bought",
        createdAt = id,
        goalId = goal,
    )

    private fun plan(mandatory: Int, optional: Int, savings: Int) =
        BudgetPlan(mandatory = Coins(mandatory), optional = Coins(optional), savings = Coins(savings))

    private fun fact(mandatory: Int, optional: Int, savings: Int) =
        PeriodFact.of(mandatory = Coins(mandatory), optional = Coins(optional), savings = Coins(savings))

    private fun report(plan: BudgetPlan, fact: PeriodFact) = BudgetEngine().compare(plan, fact)
}
