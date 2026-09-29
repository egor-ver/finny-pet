package ru.finnypet.app.domain.usecase

import ru.finnypet.app.domain.economy.GameBalance
import ru.finnypet.app.domain.model.CompletedTask
import ru.finnypet.app.domain.model.LearningTask
import ru.finnypet.app.domain.model.PetState
import ru.finnypet.app.domain.model.Stat
import ru.finnypet.app.domain.model.TaskId
import ru.finnypet.app.domain.model.Transaction
import ru.finnypet.app.domain.model.TransactionType

/**
 * Правила дня для заданий: сколько уже оплачено, за что ещё платят и какое
 * задание предложить.
 *
 * Оплаченные задания считаются по операциям периода, а не по отдельному
 * счётчику: баланс считается только по операциям, и второй источник правды
 * разошёлся бы с первым.
 */
object TaskSchedule {

    /** Сколько заданий в этом периоде уже принесли монеты. */
    fun paidToday(transactions: List<Transaction>): Int =
        transactions.count { it.type == TransactionType.INCOME_TASK }

    /** Остался ли на сегодня лимит наград (`GameBalance.rewardedTasksPerPeriod`). */
    fun rewardAvailable(transactions: List<Transaction>, balance: GameBalance): Boolean =
        paidToday(transactions) < balance.rewardedTasksPerPeriod

    /**
     * Платят ли за это задание сегодня (R8): лимит не выбран и сегодня его ещё
     * не проходили. Повтор — тренировка без монет, иначе ответ подбирался бы
     * перебором. Верен ли ответ, решает [ru.finnypet.app.domain.economy.TaskEngine].
     */
    fun rewardable(
        taskId: TaskId,
        completed: List<CompletedTask>,
        transactions: List<Transaction>,
        balance: GameBalance,
    ): Boolean = rewardAvailable(transactions, balance) && !triedToday(taskId, completed, transactions)

    /**
     * Проходили ли задание в этот игровой день. Начало дня — время операции
     * дохода дня (AD-6): схема базы не меняется, а в демо, где дни идут
     * секундами, правило работает так же. Оба времени — от одних часов.
     */
    fun triedToday(taskId: TaskId, completed: List<CompletedTask>, transactions: List<Transaction>): Boolean {
        val dayStart = dayStart(transactions) ?: return false
        return completed.any { it.taskId == taskId && it.completedAt >= dayStart }
    }

    /**
     * Сегодня пробовали, но верно ещё не решили — после ошибки главный зовёт
     * на это же задание уже без награды (ревью F7): иначе ошибка прятала бы
     * кнопку до завтра, а разбор, которого нет в списке заданий, пропадал бы
     * совсем.
     */
    fun unsolvedToday(task: LearningTask, completed: List<CompletedTask>, transactions: List<Transaction>): Boolean =
        triedToday(task.id, completed, transactions) && !solvedToday(task, completed, transactions)

    private fun dayStart(transactions: List<Transaction>): Long? =
        transactions.filter { it.type == TransactionType.INCOME_PERIOD }.minOfOrNull { it.createdAt }

    /** Обычные задания: разбор не в списке, не в счётчиках и не в минимуме ТЗ 2.6 (AD-7). */
    fun listed(tasks: List<LearningTask>): List<LearningTask> = tasks.filterNot { it.isReview }

    /**
     * Пройдено — только верно (R8): ошибка учит, но заданием не засчитывается.
     * Разбор не в счётчиках (AD-7): иначе главный показывал бы «7 из 6».
     */
    fun passed(tasks: List<LearningTask>, completed: List<CompletedTask>): Set<TaskId> =
        correctPasses(listed(tasks), completed).mapTo(mutableSetOf()) { it.taskId }

    /**
     * Задание дня для главного экрана.
     *
     * Разбор — первым, пока сова грустит из-за своего показателя или если его
     * уже начали сегодня: иначе он пропадал бы, стоило покормить сову между
     * попытками (R9). Решённый сегодня верно разбор уходит — иначе кнопка
     * весь день вела бы в уже решённое.
     *
     * Дальше — обычные задания. Выполнено только верно (решение владельца
     * 29.09), поэтому после ошибки возвращается это же задание — сегодня и в
     * следующие дни, пока его не решат. Иначе ребёнок уходил бы на другое, а
     * нерешённое всплывало снова. Нерешённых начатых нет — первое непройденное
     * в порядке списка (темы ТЗ 2.5.8, внутри темы — порядок контент-пака), а
     * когда пройдены все — то, которое верно проходили давнее всех. Замков
     * нет — это только подсказка.
     */
    fun taskOfTheDay(
        tasks: List<LearningTask>,
        completed: List<CompletedTask>,
        transactions: List<Transaction>,
        pet: PetState,
        balance: GameBalance,
    ): LearningTask? {
        tasks.firstOrNull { review ->
            val stat = review.showWhenSadAbout ?: return@firstOrNull false
            val wanted = pet.statFor(stat) < Stat(balance.sadThreshold) || triedToday(review.id, completed, transactions)
            wanted && !solvedToday(review, completed, transactions)
        }?.let { return it }

        val ordered = listed(tasks).sortedBy { it.topic.ordinal }
        if (ordered.isEmpty()) return null
        val lastPassed = correctPasses(tasks, completed)
            .groupBy { it.taskId }
            .mapValues { (_, passes) -> passes.maxOf { it.completedAt } }
        val unsolved = ordered.filterNot { it.id in lastPassed }
        if (unsolved.isEmpty()) return ordered.minBy { lastPassed.getValue(it.id) }
        val lastTry = completed.groupBy { it.taskId }.mapValues { (_, tries) -> tries.maxOf { it.completedAt } }
        return unsolved.filter { it.id in lastTry }.maxByOrNull { lastTry.getValue(it.id) } ?: unsolved.first()
    }

    private fun solvedToday(task: LearningTask, completed: List<CompletedTask>, transactions: List<Transaction>): Boolean {
        val dayStart = dayStart(transactions) ?: return false
        return correctPasses(listOf(task), completed).any { it.completedAt >= dayStart }
    }

    private fun correctPasses(tasks: List<LearningTask>, completed: List<CompletedTask>): List<CompletedTask> {
        val correct = tasks.flatMap { task -> task.outcomes.filter { it.correct }.map { task.id to it.id } }.toSet()
        return completed.filter { (it.taskId to it.outcomeId) in correct }
    }
}
