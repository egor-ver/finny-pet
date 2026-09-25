package ru.finnypet.app.ui.screens.day

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.finnypet.app.data.content.ContentParser
import ru.finnypet.app.data.content.RealContent
import ru.finnypet.app.domain.economy.BudgetEngine
import ru.finnypet.app.domain.economy.GrowthEngine
import ru.finnypet.app.domain.model.BudgetPlan
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.Explanation
import ru.finnypet.app.domain.model.GrowthStar
import ru.finnypet.app.domain.model.ItemId
import ru.finnypet.app.domain.model.PeriodFact
import ru.finnypet.app.domain.model.PetMood
import ru.finnypet.app.domain.model.PetStatKind
import ru.finnypet.app.domain.model.Transaction
import ru.finnypet.app.domain.model.TransactionType
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
        val checks = dayChecks(needsMet = false, report = report(plan(3, 24, 8), fact(0, 24, 8)), goalTitle = null, goalLeft = null)

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
        val checks = dayChecks(false, report(plan(15, 5, 5), fact(23, 0, 5)), null, null)
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
            dayTip(checks, spent, shop),
        )
        assertEquals(
            "Совет: корми меня каждый день. Обычно еда стоит 8–14, а сегодня пришлось 22.",
            texts.textOf(dayTip(checks, spent, shop)),
        )
    }

    @Test
    fun `совет — о первом, что не получилось, в порядке строк итогов`() {
        val hungry = dayChecks(false, report(plan(30, 5, 0), fact(20, 12, 0)), null, null)
        val over = dayChecks(true, report(plan(30, 5, 5), fact(30, 12, 0)), null, null)
        val noSavings = dayChecks(true, report(plan(30, 5, 0), fact(30, 5, 0)), null, null)

        assertEquals("day.tip.needs_first", dayTip(hungry, Coins(8), shop).key)
        assertEquals("day.tip.plan", dayTip(over, Coins(8), shop).key)
        assertEquals("day.tip.savings", dayTip(noSavings, Coins(8), shop).key)
    }

    @Test
    fun `всё выполнено и еда по обычной цене — похвала`() {
        val checks = dayChecks(true, report(plan(30, 5, 8), fact(22, 5, 8)), null, null)
        assertEquals("day.tip.keep", dayTip(checks, Coins(14), shop).key)
    }

    @Test
    fun `у каждого совета есть текст`() {
        val keys = listOf("day.tip.needs_first", "day.tip.plan", "day.tip.savings", "day.tip.feed_daily", "day.tip.keep")
        val missing = keys.filterNot(texts::containsKey)
        assertTrue("Нет текста в explanations.json для ключей: $missing", missing.isEmpty())
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

    private fun plan(mandatory: Int, optional: Int, savings: Int) =
        BudgetPlan(mandatory = Coins(mandatory), optional = Coins(optional), savings = Coins(savings))

    private fun fact(mandatory: Int, optional: Int, savings: Int) =
        PeriodFact.of(mandatory = Coins(mandatory), optional = Coins(optional), savings = Coins(savings))

    private fun report(plan: BudgetPlan, fact: PeriodFact) = BudgetEngine().compare(plan, fact)
}
