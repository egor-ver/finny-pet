package ru.finnypet.app.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.GoalId
import ru.finnypet.app.domain.model.GoalProgress
import ru.finnypet.app.domain.model.ProfileId

/**
 * Накопления по финансовым целям (ТЗ 2.5.7).
 */
interface SavingsRepository {

    suspend fun progress(profileId: ProfileId, goalId: GoalId): GoalProgress

    suspend fun activeProgress(profileId: ProfileId): GoalProgress?

    fun observeActive(profileId: ProfileId): Flow<GoalProgress?>

    suspend fun save(profileId: ProfileId, progress: GoalProgress)

    /** Делает цель активной, снимая отметку со всех прочих одной транзакцией. */
    suspend fun setActive(profileId: ProfileId, progress: GoalProgress)

    suspend fun all(profileId: ProfileId): List<GoalProgress>

    /**
     * Прогресс по всем целям, которые ребёнок когда-либо открывал. Экран
     * копилки показывает накопленное рядом с каждой целью, а не только с
     * активной: отложенное на прежнюю цель не должно пропадать из виду.
     */
    fun observeAll(profileId: ProfileId): Flow<List<GoalProgress>>

    /**
     * Средняя сумма пополнения по цели — основа расчёта срока её достижения.
     *
     * ТЗ 2.5.7 требует, чтобы расчёт был понятным и опирался именно на среднюю
     * сумму регулярного пополнения, поэтому снятия в неё не входят. Ноль
     * означает, что пополнений ещё не было и срок посчитать не из чего.
     */
    suspend fun averageDeposit(profileId: ProfileId, goalId: GoalId): Coins
}
