package ru.finnypet.app.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.finnypet.app.data.local.dao.GoalProgressDao
import ru.finnypet.app.data.local.dao.TransactionDao
import ru.finnypet.app.data.local.entity.GoalProgressEntity
import ru.finnypet.app.data.local.mapper.toDomain
import ru.finnypet.app.data.local.mapper.toEntity
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.GoalId
import ru.finnypet.app.domain.model.GoalProgress
import ru.finnypet.app.domain.model.ProfileId
import ru.finnypet.app.domain.model.TransactionType
import ru.finnypet.app.domain.repository.SavingsRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SavingsRepositoryImpl @Inject constructor(
    private val goals: GoalProgressDao,
    private val transactions: TransactionDao,
) : SavingsRepository {

    /** Цель, которую ещё не открывали, отдаётся нулевым прогрессом, а не null. */
    override suspend fun progress(profileId: ProfileId, goalId: GoalId): GoalProgress =
        goals.byGoal(profileId.value, goalId.value)?.toDomain()
            ?: GoalProgress(goalId = goalId, saved = Coins.ZERO, isActive = false)

    override suspend fun activeProgress(profileId: ProfileId): GoalProgress? =
        goals.active(profileId.value)?.toDomain()

    override fun observeActive(profileId: ProfileId): Flow<GoalProgress?> =
        goals.observeActive(profileId.value).map { it?.toDomain() }

    /**
     * Активная цель на профиль ровно одна, поэтому сохранение активного
     * прогресса идёт тем же транзакционным путём, что и её выбор. Голый upsert
     * завёл бы вторую активную цель, и главный экран показывал бы одну, а
     * пополнение уходило бы в другую.
     */
    override suspend fun save(profileId: ProfileId, progress: GoalProgress) =
        if (progress.isActive) {
            goals.setActive(progress.toEntity(profileId))
        } else {
            goals.upsert(progress.toEntity(profileId))
        }

    override suspend fun setActive(profileId: ProfileId, progress: GoalProgress) =
        goals.setActive(progress.toEntity(profileId))

    override suspend fun all(profileId: ProfileId): List<GoalProgress> =
        goals.all(profileId.value).map(GoalProgressEntity::toDomain)

    override fun observeAll(profileId: ProfileId): Flow<List<GoalProgress>> =
        goals.observeAll(profileId.value).map { rows -> rows.map(GoalProgressEntity::toDomain) }

    override suspend fun averageDeposit(profileId: ProfileId, goalId: GoalId): Coins {
        val deposits = transactions.byGoal(profileId.value, goalId.value)
            .filter { it.type == TransactionType.SAVINGS_DEPOSIT }
        if (deposits.isEmpty()) return Coins.ZERO
        return Coins(deposits.sumOf { it.amount } / deposits.size)
    }

    override fun observeBought(profileId: ProfileId): Flow<List<GoalId>> =
        transactions.observeGoalsOfType(profileId.value, TransactionType.GOAL_PURCHASE)
            .map { ids -> ids.map(::GoalId).distinct() }
}
