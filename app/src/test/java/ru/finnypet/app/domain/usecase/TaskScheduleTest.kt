package ru.finnypet.app.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.finnypet.app.domain.economy.GameBalance
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.CompletedTask
import ru.finnypet.app.domain.model.LearningTask
import ru.finnypet.app.domain.model.OutcomeCondition
import ru.finnypet.app.domain.model.PetState
import ru.finnypet.app.domain.model.PetStatKind
import ru.finnypet.app.domain.model.Stat
import ru.finnypet.app.domain.model.TaskId
import ru.finnypet.app.domain.model.TaskOutcome
import ru.finnypet.app.domain.model.TaskStep
import ru.finnypet.app.domain.model.TaskTopic
import ru.finnypet.app.domain.model.Transaction
import ru.finnypet.app.domain.model.TransactionType

/** Правила дня для заданий: лимит наград, первая попытка дня, «пройдено» и задание дня. */
class TaskScheduleTest {

    private val balance = GameBalance.PLACEHOLDER.copy(rewardedTasksPerPeriod = 1)

    private val fed = PetState.uniform(Stat(90))
    private val hungry = fed.copy(satiety = Stat(balance.sadThreshold - 1))

    private fun task(id: String, topic: TaskTopic = TaskTopic.PLANNING, review: Boolean = false) = LearningTask(
        id = TaskId(id),
        topic = topic,
        introKey = "task.$id.intro",
        steps = listOf(TaskStep.Distribute(promptKey = "task.$id.step", budget = Coins(40))),
        outcomes = listOf(
            TaskOutcome(id = "ok", condition = OutcomeCondition.SavedAtLeast(Coins(10)), reward = Coins(15), explanationKey = "k", correct = true),
            TaskOutcome(id = "otherwise", condition = OutcomeCondition.Otherwise, reward = Coins(5), explanationKey = "k2"),
        ),
        showWhenSadAbout = if (review) PetStatKind.SATIETY else null,
    )

    private fun transaction(type: TransactionType, at: Long = 0) = Transaction(
        id = 0,
        periodId = 1,
        type = type,
        amount = Coins(15),
        reasonKey = "k",
        createdAt = at,
    )

    private fun passed(id: String, at: Long, outcome: String = "ok") = CompletedTask(
        taskId = TaskId(id),
        outcomeId = outcome,
        reward = Coins(15),
        completedAt = at,
    )

    /** День начался в [DAY_START]: доход дня пришёл тогда. */
    private val today = listOf(transaction(TransactionType.INCOME_PERIOD, at = DAY_START))

    private fun dayTask(
        tasks: List<LearningTask>,
        completed: List<CompletedTask>,
        transactions: List<Transaction> = emptyList(),
        pet: PetState = fed,
    ) = TaskSchedule.taskOfTheDay(tasks, completed, transactions, pet, balance)

    @Test
    fun `оплаченные задания считаются по операциям награды`() {
        val transactions = listOf(
            transaction(TransactionType.INCOME_PERIOD),
            transaction(TransactionType.INCOME_TASK),
            transaction(TransactionType.PURCHASE_MANDATORY),
        )

        assertEquals(1, TaskSchedule.paidToday(transactions))
    }

    @Test
    fun `лимит свободен пока оплачено меньше нормы`() {
        assertTrue(TaskSchedule.rewardAvailable(listOf(transaction(TransactionType.INCOME_PERIOD)), balance))
        assertFalse(TaskSchedule.rewardAvailable(listOf(transaction(TransactionType.INCOME_TASK)), balance))
    }

    @Test
    fun `нулевая норма — монет за задания нет вовсе`() {
        assertFalse(TaskSchedule.rewardAvailable(emptyList(), balance.copy(rewardedTasksPerPeriod = 0)))
    }

    @Test
    fun `первая попытка дня оплачивается`() {
        assertTrue(TaskSchedule.rewardable(TaskId("a"), emptyList(), today, balance))
    }

    /** R8: повтор — тренировка, иначе ответ подбирался бы перебором. */
    @Test
    fun `повтор в тот же день не оплачивается даже после ошибки`() {
        val wrongToday = listOf(passed("a", DAY_START + 5, outcome = "otherwise"))
        assertFalse(TaskSchedule.rewardable(TaskId("a"), wrongToday, today, balance))
    }

    @Test
    fun `на следующий день то же задание снова оплачивается`() {
        val yesterday = listOf(passed("a", DAY_START - 5))
        assertTrue(TaskSchedule.rewardable(TaskId("a"), yesterday, today, balance))
    }

    @Test
    fun `выбранный лимит закрывает оплату любого задания`() {
        assertFalse(TaskSchedule.rewardable(TaskId("a"), emptyList(), today + transaction(TransactionType.INCOME_TASK), balance))
    }

    @Test
    fun `пройдено только верно`() {
        val tasks = listOf(task("a"), task("b"))
        val completed = listOf(passed("a", 10, outcome = "otherwise"), passed("b", 20))

        assertEquals(setOf(TaskId("b")), TaskSchedule.passed(tasks, completed))
    }

    @Test
    fun `верно пройденный разбор не считается пройденным заданием`() {
        val tasks = listOf(task("a"), task("review", review = true))
        val completed = listOf(passed("a", 10), passed("review", 20))

        assertEquals(setOf(TaskId("a")), TaskSchedule.passed(tasks, completed))
    }

    @Test
    fun `задание дня — первое непройденное в порядке контента`() {
        val tasks = listOf(task("a"), task("b"), task("c"))

        assertEquals(TaskId("b"), dayTask(tasks, listOf(passed("a", 10)))?.id)
    }

    @Test
    fun `когда все пройдены — то что проходили давнее всех`() {
        val tasks = listOf(task("a"), task("b"))
        // «a» проходили дважды, последний раз позже «b».
        val completed = listOf(passed("a", 10), passed("b", 20), passed("a", 30))

        assertEquals(TaskId("b"), dayTask(tasks, completed)?.id)
    }

    /** Тот же порядок, что в списке: сначала планирование, потом накопления, потом покупки. */
    @Test
    fun `задание дня идёт по темам а не по порядку файла`() {
        val tasks = listOf(task("pay", TaskTopic.PAYMENTS), task("save", TaskTopic.SAVING), task("plan", TaskTopic.PLANNING))

        assertEquals(TaskId("plan"), dayTask(tasks, emptyList())?.id)
        assertEquals(TaskId("save"), dayTask(tasks, listOf(passed("plan", 1)))?.id)
    }

    /**
     * Решение владельца 29.09: выполнено — только верный ответ, после ошибки
     * возвращается это же задание, пока не будет решено. Раньше кнопка
     * уводила на другое, и нерешённое всплывало снова на следующий день.
     */
    @Test
    fun `после ошибки задание дня — то же задание`() {
        val tasks = listOf(task("a"), task("b"))
        val wrongToday = listOf(passed("a", DAY_START + 5, outcome = "otherwise"))

        assertEquals(TaskId("a"), dayTask(tasks, wrongToday, today)?.id)
    }

    /** Ревью F7: то, что главный кладёт в TaskOfDay.unsolvedToday, — кнопка задания после ошибки. */
    @Test
    fun `сегодня ошиблись и не решили — задание нерешённое сегодня`() {
        val a = task("a")
        val wrongToday = listOf(passed("a", DAY_START + 5, outcome = "otherwise"))

        assertTrue(TaskSchedule.unsolvedToday(a, wrongToday, today))
        assertFalse("ещё не пробовали", TaskSchedule.unsolvedToday(a, emptyList(), today))
        assertFalse("решили после ошибки", TaskSchedule.unsolvedToday(a, wrongToday + passed("a", DAY_START + 9), today))
        assertFalse("ошибка вчера", TaskSchedule.unsolvedToday(a, listOf(passed("a", DAY_START - 5, outcome = "otherwise")), today))
    }

    /** Разбор после ошибки: он же задание дня и он же нерешённый сегодня — кнопка на главном ведёт в него. */
    @Test
    fun `разбор с ошибкой сегодня — задание дня и нерешённое сегодня`() {
        val review = task("review", review = true)
        val tasks = listOf(task("a"), review)
        val wrong = listOf(passed("review", DAY_START + 5, outcome = "otherwise"))

        val day = dayTask(tasks, wrong, today, pet = fed)
        assertEquals(TaskId("review"), day?.id)
        assertTrue(TaskSchedule.unsolvedToday(day!!, wrong, today))
    }

    @Test
    fun `нерешённое задание остаётся заданием дня и назавтра`() {
        val tasks = listOf(task("a"), task("b"))
        val wrongYesterday = listOf(passed("a", DAY_START - 5, outcome = "otherwise"))

        assertEquals(TaskId("a"), dayTask(tasks, wrongYesterday, today)?.id)
    }

    /** Ошибка в задании из середины списка — возвращается оно, а не первое непройденное. */
    @Test
    fun `после ошибки не по порядку списка возвращается то задание где ошиблись`() {
        val tasks = listOf(task("a"), task("b"), task("c"))
        val wrongOnC = listOf(passed("c", DAY_START + 5, outcome = "otherwise"))

        assertEquals(TaskId("c"), dayTask(tasks, wrongOnC, today)?.id)
    }

    @Test
    fun `после верного ответа — следующее непройденное по списку`() {
        val tasks = listOf(task("a"), task("b"), task("c"))
        val completed = listOf(
            passed("a", DAY_START + 1, outcome = "otherwise"),
            passed("a", DAY_START + 5),
        )

        assertEquals(TaskId("b"), dayTask(tasks, completed, today)?.id)
    }

    /** Пройденное не предлагается, пока есть непройденные, — даже если его пробовали позже всех. */
    @Test
    fun `пройденное с поздней ошибкой не возвращается пока есть непройденные`() {
        val tasks = listOf(task("a"), task("b"))
        val completed = listOf(passed("a", 10), passed("a", DAY_START + 5, outcome = "otherwise"))

        assertEquals(TaskId("b"), dayTask(tasks, completed, today)?.id)
    }

    @Test
    fun `все пройдены и последнюю попытку провалили — всё равно давнее всех`() {
        val tasks = listOf(task("a"), task("b"))
        val completed = listOf(passed("a", 10), passed("b", 20), passed("b", DAY_START + 5, outcome = "otherwise"))

        assertEquals(TaskId("a"), dayTask(tasks, completed, today)?.id)
    }

    @Test
    fun `без заданий в контенте задания дня нет`() {
        assertNull(dayTask(emptyList(), emptyList()))
    }

    @Test
    fun `разбор не входит в список заданий`() {
        val tasks = listOf(task("a"), task("review", review = true))

        assertEquals(listOf(TaskId("a")), TaskSchedule.listed(tasks).map { it.id })
    }

    /** R9, раздел 4 плана, утро дня 3: сова грустит — задание дня разбор. */
    @Test
    fun `сова грустит от голода — задание дня разбор`() {
        val tasks = listOf(task("a"), task("review", review = true))

        assertEquals(TaskId("review"), dayTask(tasks, emptyList(), today, pet = hungry)?.id)
    }

    @Test
    fun `сытой сове разбор не предлагается`() {
        val tasks = listOf(task("a"), task("review", review = true))

        assertEquals(TaskId("a"), dayTask(tasks, emptyList(), today, pet = fed)?.id)
    }

    /** Начатый разбор не пропадает, если сову покормили между попытками. */
    @Test
    fun `начатый сегодня разбор остаётся заданием дня после еды`() {
        val tasks = listOf(task("a"), task("review", review = true))
        val triedReview = listOf(passed("review", DAY_START + 5, outcome = "otherwise"))

        assertEquals(TaskId("review"), dayTask(tasks, triedReview, today, pet = fed)?.id)
    }

    /** Разбор решён верно — кнопка главного больше не ведёт в него весь день. */
    @Test
    fun `верно пройденный сегодня разбор больше не задание дня`() {
        val tasks = listOf(task("a"), task("review", review = true))
        val solved = listOf(
            passed("review", DAY_START + 1, outcome = "otherwise"),
            passed("review", DAY_START + 5),
        )

        assertEquals(TaskId("a"), dayTask(tasks, solved, today, pet = fed)?.id)
        assertEquals(TaskId("a"), dayTask(tasks, solved, today, pet = hungry)?.id)
    }

    @Test
    fun `вчера решённый разбор голодной сове предлагается снова`() {
        val tasks = listOf(task("a"), task("review", review = true))
        val solvedYesterday = listOf(passed("review", DAY_START - 5))

        assertEquals(TaskId("review"), dayTask(tasks, solvedYesterday, today, pet = hungry)?.id)
    }

    @Test
    fun `вчерашний разбор сытой сове не возвращается`() {
        val tasks = listOf(task("a"), task("review", review = true))
        val yesterday = listOf(passed("review", DAY_START - 5))

        assertEquals(TaskId("a"), dayTask(tasks, yesterday, today, pet = fed)?.id)
    }

    /**
     * Граница дня — включительно: совпадение отметки прохождения с началом
     * дня (грубые секундные часы демо) — тот же день, а не два разных (AD-6).
     */
    @Test
    fun `совпадение отметки с началом дня — тот же день`() {
        val sameInstant = listOf(passed("review", DAY_START))

        assertTrue(TaskSchedule.triedToday(TaskId("review"), sameInstant, today))
    }

    /**
     * Б10: разбор застревал заданием дня, потому что часы демо были
     * заморожены, и `dayStart` нового периода совпадал с `completedAt`
     * прошлого — включительное сравнение путало прошлый день с текущим.
     * В коде `TaskSchedule` для этого никакого отдельного правила нет — это
     * допущение (AD-6): часы обязаны сдвинуться хотя бы на отметку между
     * периодами, а обеспечивают это часы, а не движок. Тест документирует
     * само допущение: при сдвинутых часах прохождение прошлого периода в
     * текущий не попадает.
     */
    @Test
    fun `прохождение прошлого периода не считается сегодняшним, если часы периода продвинулись`() {
        val passedLastPeriod = listOf(passed("review", DAY_START))
        val nextPeriodStarted = listOf(transaction(TransactionType.INCOME_PERIOD, at = DAY_START + 1))

        assertFalse(TaskSchedule.triedToday(TaskId("review"), passedLastPeriod, nextPeriodStarted))
    }

    private companion object {
        const val DAY_START = 1_000L
    }
}
