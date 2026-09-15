package ru.finnypet.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import ru.finnypet.app.data.local.entity.TransactionEntity

@Dao
interface TransactionDao {

    @Insert
    suspend fun insert(transaction: TransactionEntity): Long

    /** Возвращает выданные базой идентификаторы в порядке переданного списка. */
    @Insert
    suspend fun insertAll(transactions: List<TransactionEntity>): List<Long>

    /**
     * Операции периода в порядке появления. Баланс считается из них доменным
     * Transaction.balanceDelta, а не выражением CASE в SQL: иначе понятие
     * дохода оказалось бы описано в двух местах сразу и разошлось бы при
     * первом же изменении TransactionType.
     */
    @Query("SELECT * FROM transactions WHERE periodId = :periodId ORDER BY createdAt, id")
    suspend fun byPeriod(periodId: Long): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE periodId = :periodId ORDER BY createdAt, id")
    fun observeByPeriod(periodId: Long): Flow<List<TransactionEntity>>

    /**
     * История пополнений и снятий по цели — основа средней суммы пополнения,
     * из которой SavingsEngine считает срок достижения цели (ТЗ 2.5.7).
     *
     * Соединение с периодами обязательно: идентификаторы целей приходят из
     * общего контент-пака и повторяются у всех профилей, поэтому фильтр
     * только по goalId смешал бы накопления разных детей.
     */
    @Query(
        "SELECT t.* FROM transactions AS t " +
            "INNER JOIN periods AS p ON p.id = t.periodId " +
            "WHERE p.profileId = :profileId AND t.goalId = :goalId " +
            "ORDER BY t.createdAt, t.id"
    )
    suspend fun byGoal(profileId: String, goalId: String): List<TransactionEntity>
}
