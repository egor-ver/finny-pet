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
        val dayStart = transactions.filter { it.type == TransactionType.INCOME_PERIOD }.minOfOrNull { it.createdAt }
            ?: return false
        return completed.any { it.taskId == taskId && it.completedAt >= dayStart }
    }

    /** Обычные задания: разбор не в списке, не в счётчиках и не в минимуме ТЗ 2.6 (AD-7). */
    fun listed(tasks: List<LearningTask>): List<LearningTask> = tasks.filterNot { it.isReview }

    /** Пройдено — только верно (R8): ошибка учит, но заданием не засчитывается. */
    fun passed(tasks: List<LearningTask>, completed: List<CompletedTask>): Set<TaskId> =
        correctPasses(tasks, completed).mapTo(mutableSetOf()) { it.taskId }

    /**
     * Задание дня для главного экрана.
     *
     * Разбор — первым, пока сова грустит из-за своего показателя или если его
     * уже начали сегодня: иначе он пропадал бы, стоило покормить сову между
     * попытками (R9). Дальше — обычные задания, которые сегодня ещё не
     * проходили: неверный ответ не тратит лимит, и монеты можно заработать на
     * другом. Среди них первое непройденное в порядке списка (темы ТЗ 2.5.8,
     * внутри темы — порядок контент-пака), а когда пройдены все — то, которое
     * верно проходили давнее всех. Замков нет — это только подсказка.
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
            pet.statFor(stat) < Stat(balance.sadThreshold) || triedToday(review.id, completed, transactions)
        }?.let { return it }

        val ordered = listed(tasks).sortedBy { it.topic.ordinal }
        if (ordered.isEmpty()) return null
        val fresh = ordered.filterNot { triedToday(it.id, completed, transactions) }.ifEmpty { ordered }
        val lastPassed = correctPasses(tasks, completed)
            .groupBy { it.taskId }
            .mapValues { (_, passes) -> passes.maxOf { it.completedAt } }
        return fresh.firstOrNull { it.id !in lastPassed } ?: fresh.minBy { lastPassed.getValue(it.id) }
    }

    private fun correctPasses(tasks: List<LearningTask>, completed: List<CompletedTask>): List<CompletedTask> {
        val correct = tasks.flatMap { task -> task.outcomes.filter { it.correct }.map { task.id to it.id } }.toSet()
        return completed.filter { (it.taskId to it.outcomeId) in correct }
    }
}
