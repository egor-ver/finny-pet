package ru.finnypet.app.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.finnypet.app.data.local.dao.BudgetPlanDao
import ru.finnypet.app.data.local.dao.PeriodDao
import ru.finnypet.app.data.local.dao.TransactionDao
import ru.finnypet.app.data.local.entity.TransactionEntity
import ru.finnypet.app.data.local.mapper.toDomain
import ru.finnypet.app.data.local.mapper.toEntity
import ru.finnypet.app.domain.model.BudgetPlan
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.GamePeriod
import ru.finnypet.app.domain.model.ProfileId
import ru.finnypet.app.domain.model.Transaction
import ru.finnypet.app.domain.repository.PeriodRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PeriodRepositoryImpl @Inject constructor(
    private val periods: PeriodDao,
    private val plans: BudgetPlanDao,
    private val transactions: TransactionDao,
) : PeriodRepository {

    override suspend fun open(period: GamePeriod): GamePeriod {
        require(period.id == UNSAVED) {
            "Период ${period.number} уже сохранён под id ${period.id}: " +
                "open() заводит новый, для изменения существующего есть save()"
        }
        val id = periods.insert(period.toEntity())
        return period.copy(id = id)
    }

    /**
     * @Update ищет строку по первичному ключу и молча не делает ничего, если
     * её нет. Для несохранённого периода это означало бы потерю прогресса без
     * единого признака ошибки, поэтому оба случая проверяются явно.
     */
    override suspend fun save(period: GamePeriod) {
        require(period.id != UNSAVED) {
            "Период ${period.number} ещё не сохранён: сначала open(), он выдаёт идентификатор"
        }
        val updated = periods.update(period.toEntity())
        check(updated == 1) {
            "Период ${period.number} с id ${period.id} не найден в базе: обновлено строк $updated"
        }
    }

    override suspend fun current(profileId: ProfileId): GamePeriod? =
        periods.openPeriod(profileId.value)?.toDomain()

    override fun observeCurrent(profileId: ProfileId): Flow<GamePeriod?> =
        periods.observeOpenPeriod(profileId.value).map { it?.toDomain() }

    override suspend fun lastClosed(profileId: ProfileId): GamePeriod? =
        periods.lastClosed(profileId.value)?.toDomain()

    override suspend fun count(profileId: ProfileId): Int = periods.count(profileId.value)

    override suspend fun savePlan(periodId: Long, plan: BudgetPlan) =
        plans.upsert(plan.toEntity(periodId))

    override suspend fun plan(periodId: Long): BudgetPlan? =
        plans.byPeriod(periodId)?.toDomain()

    override fun observePlan(periodId: Long): Flow<BudgetPlan?> =
        plans.observe(periodId).map { it?.toDomain() }

    override suspend fun addTransaction(transaction: Transaction): Transaction {
        val id = transactions.insert(transaction.toEntity())
        return transaction.copy(id = id)
    }

    override suspend fun transactions(periodId: Long): List<Transaction> =
        transactions.byPeriod(periodId).map(TransactionEntity::toDomain)

    override fun observeTransactions(periodId: Long): Flow<List<Transaction>> =
        transactions.observeByPeriod(periodId).map { rows -> rows.map(TransactionEntity::toDomain) }

    /**
     * Путь команды: отрицательный итог означает покупку в обход WalletEngine,
     * и операция обязана остановиться с внятным сообщением, а не записать
     * невозможное состояние (ТЗ 2.5.6 запрещает уходить в минус).
     */
    override suspend fun balance(period: GamePeriod): Coins {
        val total = rawBalance(period, transactions.byPeriod(period.id))
        require(total >= 0) {
            "Баланс периода ${period.number} ушёл в минус ($total): " +
                "проверь, что покупки идут через WalletEngine"
        }
        return Coins(total)
    }

    /**
     * Путь отображения не бросает исключений: оно дошло бы до сборщика в
     * Compose и уронило бы главный экран при каждом открытии, а ТЗ 3.4
     * запрещает тупиковые экраны. Ошибка данных должна остановить операцию,
     * а не отрезать ребёнка от прогресса, поэтому здесь минус зажимается.
     */
    override fun observeBalance(period: GamePeriod): Flow<Coins> =
        transactions.observeByPeriod(period.id).map { rows ->
            Coins(rawBalance(period, rows).coerceAtLeast(0))
        }

    /** Баланс не хранится колонкой: стартовый остаток плюс сумма операций. */
    private fun rawBalance(period: GamePeriod, rows: List<TransactionEntity>): Int =
        period.startBalance.amount + rows.sumOf { it.toDomain().balanceDelta }

    private companion object {
        const val UNSAVED = 0L
    }
}
