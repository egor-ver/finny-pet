package ru.finnypet.app.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.finnypet.app.domain.model.BudgetPlan
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.GamePeriod
import ru.finnypet.app.domain.model.ProfileId
import ru.finnypet.app.domain.model.Transaction

/**
 * Игровой период вместе с его планом, операциями и балансом.
 */
interface PeriodRepository {

    /**
     * Сохраняет период и возвращает его с выданным базой идентификатором.
     *
     * Вызывать до создания любых операций: PeriodEngine.close() требует,
     * чтобы periodId каждой транзакции совпадал с id периода, а openNext()
     * отдаёт заготовку с нулём.
     */
    suspend fun open(period: GamePeriod): GamePeriod

    suspend fun save(period: GamePeriod)

    suspend fun current(profileId: ProfileId): GamePeriod?

    fun observeCurrent(profileId: ProfileId): Flow<GamePeriod?>

    /** Итоги последнего завершённого периода (ТЗ 2.5.11). */
    suspend fun lastClosed(profileId: ProfileId): GamePeriod?

    suspend fun count(profileId: ProfileId): Int

    suspend fun savePlan(periodId: Long, plan: BudgetPlan)

    suspend fun plan(periodId: Long): BudgetPlan?

    fun observePlan(periodId: Long): Flow<BudgetPlan?>

    suspend fun addTransaction(transaction: Transaction): Transaction

    suspend fun transactions(periodId: Long): List<Transaction>

    fun observeTransactions(periodId: Long): Flow<List<Transaction>>

    /**
     * Стартовый остаток периода плюс сумма balanceDelta его операций.
     *
     * Колонки «баланс» нет намеренно: счётчик рядом с историей разошёлся бы
     * с ней, и ребёнок увидел бы сумму, не сходящуюся с собственными
     * покупками (ТЗ 2.5.4 — баланс не меняется без объяснения).
     */
    suspend fun balance(period: GamePeriod): Coins

    fun observeBalance(period: GamePeriod): Flow<Coins>
}
