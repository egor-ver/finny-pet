package ru.finnypet.app.domain.usecase

import ru.finnypet.app.domain.economy.GameBalance
import ru.finnypet.app.domain.model.CompletedTask
import ru.finnypet.app.domain.model.LearningTask
import ru.finnypet.app.domain.model.Transaction
import ru.finnypet.app.domain.model.TransactionType

/**
 * Правила дня для заданий: сколько уже оплачено и какое предложить.
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
     * Задание дня для главного экрана: первое ещё не пройденное в порядке
     * контент-пака; когда пройдены все — то, которое проходили давнее всех.
     * Порядок открытия ТЗ 2.5.8 оставляет команде, замков нет — это только
     * подсказка.
     */
    fun taskOfTheDay(tasks: List<LearningTask>, completed: List<CompletedTask>): LearningTask? {
        if (tasks.isEmpty()) return null
        val lastPassed = completed.groupBy { it.taskId }.mapValues { (_, passes) -> passes.maxOf { it.completedAt } }
        return tasks.firstOrNull { it.id !in lastPassed }
            ?: tasks.minBy { lastPassed.getValue(it.id) }
    }
}
