package ru.finnypet.app.domain.economy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.finnypet.app.domain.model.BudgetPlan
import ru.finnypet.app.domain.model.Change
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.ItemId
import ru.finnypet.app.domain.model.LearningTask
import ru.finnypet.app.domain.model.OutcomeCondition
import ru.finnypet.app.domain.model.PetEffect
import ru.finnypet.app.domain.model.PetStatKind
import ru.finnypet.app.domain.model.ShelfItem
import ru.finnypet.app.domain.model.SpendCategory
import ru.finnypet.app.domain.model.StepAnswer
import ru.finnypet.app.domain.model.TaskAttempt
import ru.finnypet.app.domain.model.TaskId
import ru.finnypet.app.domain.model.TaskOption
import ru.finnypet.app.domain.model.TaskOutcome
import ru.finnypet.app.domain.model.TaskStep
import ru.finnypet.app.domain.model.TaskTopic
import ru.finnypet.app.domain.model.TransactionType

class TaskEngineTest {

    private val now = 1_700_000_000L
    private val engine = TaskEngine(GameClock { now })

    private val distributeStep = TaskStep.Distribute(promptKey = "step.distribute", budget = Coins(60))

    private val savedEnough = TaskOutcome(
        id = "saved",
        condition = OutcomeCondition.SavedAtLeast(Coins(10)),
        reward = Coins(15),
        explanationKey = "task.saved",
        effects = listOf(PetEffect(PetStatKind.MOOD, 5)),
    )

    private val fallback = TaskOutcome(
        id = "spent_all",
        condition = OutcomeCondition.Otherwise,
        reward = Coins(5),
        explanationKey = "task.spent_all",
    )

    private fun task(
        steps: List<TaskStep> = listOf(distributeStep),
        outcomes: List<TaskOutcome> = listOf(savedEnough, fallback),
    ) = LearningTask(
        id = TaskId("plan_01"),
        topic = TaskTopic.PLANNING,
        introKey = "task.intro",
        steps = steps,
        outcomes = outcomes,
    )

    private fun allocated(mandatory: Int, optional: Int, savings: Int) = TaskAttempt(
        listOf(
            StepAnswer.Allocated(
                BudgetPlan(Coins(mandatory), Coins(optional), Coins(savings)),
            ),
        ),
    )

    /** Лимит наград в день выбран: исход и объяснение те же, монет и операции нет. */
    @Test
    fun `без права на награду исход тот же а монет нет`() {
        val result = engine.evaluate(task(), allocated(30, 20, 10), Coins(60), periodId = 1, rewardable = false)

        assertEquals("saved", result.value.outcome.id)
        assertEquals(Coins(60), result.value.newBalance)
        assertNull(result.value.transaction)
        assertEquals(emptyList<Change>(), result.changes)
        assertEquals("0", result.explanation.args["reward"])
        assertEquals("60", result.explanation.args["balance"])
        assertEquals(savedEnough.effects, result.value.effects)
    }

    @Test
    fun `по умолчанию награда выплачивается`() {
        val result = engine.evaluate(task(), allocated(30, 20, 10), Coins(60), periodId = 1)

        assertEquals(Coins(75), result.value.newBalance)
        assertEquals(TransactionType.INCOME_TASK, result.value.transaction?.type)
        assertEquals("15", result.explanation.args["reward"])
    }

    @Test
    fun `выполненное условие выбирает свой исход`() {
        val result = engine.evaluate(task(), allocated(30, 20, 10), Coins(60), periodId = 1)
        assertEquals("saved", result.value.outcome.id)
    }

    @Test
    fun `невыполненное условие уводит в исход по умолчанию`() {
        val result = engine.evaluate(task(), allocated(40, 20, 0), Coins(60), periodId = 1)
        assertEquals("spent_all", result.value.outcome.id)
    }

    @Test
    fun `условие на границе считается выполненным`() {
        val result = engine.evaluate(task(), allocated(30, 20, 10), Coins(60), periodId = 1)
        assertEquals("saved", result.value.outcome.id)
    }

    @Test
    fun `нехватка одной монеты до условия уводит в исход по умолчанию`() {
        val result = engine.evaluate(task(), allocated(31, 20, 9), Coins(60), periodId = 1)
        assertEquals("spent_all", result.value.outcome.id)
    }

    @Test
    fun `исход по умолчанию проверяется последним даже если записан первым`() {
        val reordered = task(outcomes = listOf(fallback, savedEnough))
        val result = engine.evaluate(reordered, allocated(30, 20, 10), Coins(60), periodId = 1)
        assertEquals("saved", result.value.outcome.id)
    }

    @Test
    fun `награда увеличивает баланс`() {
        val result = engine.evaluate(task(), allocated(30, 20, 10), Coins(60), periodId = 1)
        assertEquals(Coins(75), result.value.newBalance)
    }

    @Test
    fun `награда порождает транзакцию дохода за задание`() {
        val result = engine.evaluate(task(), allocated(30, 20, 10), Coins(60), periodId = 7)
        val transaction = requireNotNull(result.value.transaction)
        assertEquals(TransactionType.INCOME_TASK, transaction.type)
        assertEquals(Coins(15), transaction.amount)
        assertEquals(7L, transaction.periodId)
        assertEquals(now, transaction.createdAt)
    }

    @Test
    fun `нулевая награда не порождает транзакцию`() {
        val free = task(outcomes = listOf(savedEnough, fallback.copy(reward = Coins.ZERO)))
        val result = engine.evaluate(free, allocated(40, 20, 0), Coins(60), periodId = 1)
        assertNull(result.value.transaction)
        assertTrue(result.changes.isEmpty())
    }

    @Test
    fun `награда сообщается изменением баланса`() {
        val result = engine.evaluate(task(), allocated(30, 20, 10), Coins(60), periodId = 1)
        assertEquals(listOf(Change.Balance(from = Coins(60), to = Coins(75))), result.changes)
    }

    @Test
    fun `эффекты исхода переносятся в результат`() {
        val result = engine.evaluate(task(), allocated(30, 20, 10), Coins(60), periodId = 1)
        assertEquals(listOf(PetEffect(PetStatKind.MOOD, 5)), result.value.effects)
    }

    @Test
    fun `объяснение берётся у исхода и несёт награду`() {
        val result = engine.evaluate(task(), allocated(30, 20, 10), Coins(60), periodId = 1)
        assertEquals("task.saved", result.explanation.key)
        assertEquals("15", result.explanation.args["reward"])
        assertEquals("75", result.explanation.args["balance"])
    }

    @Test
    fun `ошибочный ответ тоже получает объяснение`() {
        val result = engine.evaluate(task(), allocated(40, 20, 0), Coins(60), periodId = 1)
        assertEquals("task.spent_all", result.explanation.key)
    }

    @Test
    fun `выбранный вариант находит свой исход`() {
        val choice = TaskStep.Choice(
            promptKey = "step.choice",
            options = listOf(TaskOption("save", "opt.save"), TaskOption("spend", "opt.spend")),
        )
        val chosen = TaskOutcome(
            id = "chose_save",
            condition = OutcomeCondition.OptionChosen("save"),
            reward = Coins(10),
            explanationKey = "task.chose_save",
        )
        val withChoice = task(steps = listOf(choice), outcomes = listOf(chosen, fallback))
        val attempt = TaskAttempt(listOf(StepAnswer.Chosen("save")))
        assertEquals("chose_save", engine.evaluate(withChoice, attempt, Coins(60), 1).value.outcome.id)
    }

    @Test
    fun `условие на потраченное не больше проверяется по обоим направлениям`() {
        val thrifty = TaskOutcome(
            id = "thrifty",
            condition = OutcomeCondition.SpentAtMost(Coins(45)),
            reward = Coins(10),
            explanationKey = "task.thrifty",
        )
        val withLimit = task(outcomes = listOf(thrifty, fallback))
        assertEquals("thrifty", engine.evaluate(withLimit, allocated(30, 15, 10), Coins(60), 1).value.outcome.id)
        assertEquals("spent_all", engine.evaluate(withLimit, allocated(30, 16, 10), Coins(60), 1).value.outcome.id)
    }

    @Test
    fun `число ответов должно совпадать с числом шагов`() {
        val twoSteps = task(steps = listOf(distributeStep, distributeStep))
        assertThrows(IllegalArgumentException::class.java) {
            engine.evaluate(twoSteps, allocated(30, 20, 10), Coins(60), periodId = 1)
        }
    }

    @Test
    fun `ответ не того вида не принимается`() {
        val attempt = TaskAttempt(listOf(StepAnswer.Chosen("save")))
        assertThrows(IllegalArgumentException::class.java) {
            engine.evaluate(task(), attempt, Coins(60), periodId = 1)
        }
    }

    @Test
    fun `выбор варианта которого нет в шаге не принимается`() {
        val choice = TaskStep.Choice(
            promptKey = "step.choice",
            options = listOf(TaskOption("save", "opt.save"), TaskOption("spend", "opt.spend")),
        )
        val withChoice = task(steps = listOf(choice))
        val attempt = TaskAttempt(listOf(StepAnswer.Chosen("fly_away")))
        assertThrows(IllegalArgumentException::class.java) {
            engine.evaluate(withChoice, attempt, Coins(60), periodId = 1)
        }
    }

    @Test
    fun `распределение сверх бюджета шага не принимается`() {
        assertThrows(IllegalArgumentException::class.java) {
            engine.evaluate(task(), allocated(40, 20, 10), Coins(100), periodId = 1)
        }
    }

    @Test
    fun `распределение ровно в бюджет шага принимается`() {
        val result = engine.evaluate(task(), allocated(30, 20, 10), Coins(60), periodId = 1)
        assertEquals("saved", result.value.outcome.id)
    }

    @Test
    fun `набор товаров сверх бюджета шага не принимается`() {
        val pick = TaskStep.PickItems(
            promptKey = "step.pick",
            itemIds = listOf(ItemId("apple"), ItemId("toy")),
            budget = Coins(50),
        )
        val withPick = task(steps = listOf(pick))
        val attempt = TaskAttempt(listOf(StepAnswer.Picked(listOf("apple"), Coins(51))))
        assertThrows(IllegalArgumentException::class.java) {
            engine.evaluate(withPick, attempt, Coins(60), periodId = 1)
        }
    }

    @Test
    fun `товар не из списка шага не принимается`() {
        val pick = TaskStep.PickItems(
            promptKey = "step.pick",
            itemIds = listOf(ItemId("apple"), ItemId("toy")),
            budget = Coins(50),
        )
        val withPick = task(steps = listOf(pick))
        val attempt = TaskAttempt(listOf(StepAnswer.Picked(listOf("rocket"), Coins(10))))
        assertThrows(IllegalArgumentException::class.java) {
            engine.evaluate(withPick, attempt, Coins(60), periodId = 1)
        }
    }

    // --- Условия из контент-пака ---

    /**
     * Условие по банкам смотрит на раскладку, а не на общую сумму: отложить
     * пятнадцать и при этом не закрыть обязательное — другой урок.
     */
    @Test
    fun `условие по банкам проверяет каждую названную банку`() {
        val jars = TaskOutcome(
            id = "balanced",
            condition = OutcomeCondition.JarsAtLeast(mandatory = Coins(15), savings = Coins(5)),
            reward = Coins(10),
            explanationKey = "task.balanced",
        )
        val withJars = task(outcomes = listOf(jars, fallback))

        assertEquals("balanced", engine.evaluate(withJars, allocated(15, 40, 5), Coins(60), 1).value.outcome.id)
        // Копилки хватает, а на обязательное отложено меньше — исход другой.
        assertEquals("spent_all", engine.evaluate(withJars, allocated(14, 40, 6), Coins(60), 1).value.outcome.id)
    }

    @Test
    fun `неназванная банка условию не мешает`() {
        val onlySavings = TaskOutcome(
            id = "thrifty",
            condition = OutcomeCondition.JarsAtLeast(savings = Coins(15)),
            reward = Coins(10),
            explanationKey = "task.thrifty",
        )
        val withJars = task(outcomes = listOf(onlySavings, fallback))

        // Про обязательное и желания в условии ничего нет — они любые.
        assertEquals("thrifty", engine.evaluate(withJars, allocated(0, 45, 15), Coins(60), 1).value.outcome.id)
        assertEquals("thrifty", engine.evaluate(withJars, allocated(45, 0, 15), Coins(60), 1).value.outcome.id)
    }

    @Test
    fun `любой из перечисленных вариантов ведёт в один исход`() {
        val step = TaskStep.Choice(
            promptKey = "step.choice",
            options = listOf(
                TaskOption("save", "o.save"),
                TaskOption("pause", "o.pause"),
                TaskOption("buy", "o.buy"),
            ),
        )
        val patient = TaskOutcome(
            id = "patient",
            condition = OutcomeCondition.AnyOptionChosen(listOf("save", "pause")),
            reward = Coins(10),
            explanationKey = "task.patient",
        )
        val withChoice = task(steps = listOf(step), outcomes = listOf(patient, fallback))

        listOf("save", "pause").forEach { chosen ->
            val attempt = TaskAttempt(listOf(StepAnswer.Chosen(chosen)))
            assertEquals(chosen, "patient", engine.evaluate(withChoice, attempt, Coins(60), 1).value.outcome.id)
        }
        val hasty = TaskAttempt(listOf(StepAnswer.Chosen("buy")))
        assertEquals("spent_all", engine.evaluate(withChoice, hasty, Coins(60), 1).value.outcome.id)
    }

    @Test
    fun `условие по корзине требует все названные товары`() {
        val shelf = TaskStep.Shelf(
            promptKey = "step.shelf",
            items = listOf(
                ShelfItem("notebook", "t.notebook", Coins(10), SpendCategory.MANDATORY),
                ShelfItem("pen", "t.pen", Coins(12), SpendCategory.MANDATORY),
                ShelfItem("sticker", "t.sticker", Coins(5), SpendCategory.OPTIONAL),
            ),
            budget = Coins(35),
        )
        val ready = TaskOutcome(
            id = "ready",
            condition = OutcomeCondition.BasketContains(listOf("notebook", "pen")),
            reward = Coins(10),
            explanationKey = "task.ready",
        )
        val withShelf = task(steps = listOf(shelf), outcomes = listOf(ready, fallback))

        val full = TaskAttempt(listOf(StepAnswer.Picked(listOf("notebook", "pen"), Coins(22))))
        assertEquals("ready", engine.evaluate(withShelf, full, Coins(60), 1).value.outcome.id)

        // Одна обязательная покупка забыта — набор не собран.
        val partial = TaskAttempt(listOf(StepAnswer.Picked(listOf("notebook", "sticker"), Coins(15))))
        assertEquals("spent_all", engine.evaluate(withShelf, partial, Coins(60), 1).value.outcome.id)
    }

    @Test
    fun `товар не с прилавка не принимается`() {
        val shelf = TaskStep.Shelf(
            promptKey = "step.shelf",
            items = listOf(ShelfItem("notebook", "t.notebook", Coins(10), SpendCategory.MANDATORY)),
            budget = Coins(35),
        )
        val withShelf = task(steps = listOf(shelf))
        val attempt = TaskAttempt(listOf(StepAnswer.Picked(listOf("rocket"), Coins(10))))

        assertThrows(IllegalArgumentException::class.java) {
            engine.evaluate(withShelf, attempt, Coins(60), periodId = 1)
        }
    }
}
