package ru.finnypet.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import ru.finnypet.app.data.local.entity.PeriodEntity
import ru.finnypet.app.domain.model.PeriodStatus

/**
 * Статус периода нигде не пишется строкой — ни в SQL, ни в аргументах вызова.
 * Запросы с параметром статуса закрыты обёртками ниже: вызывающий говорит,
 * что ему нужно, а не каким значением это выражается.
 */
@Dao
interface PeriodDao {

    /**
     * Возвращает выданный базой идентификатор. Вызывать до создания любых
     * операций периода: PeriodEngine.close() проверяет, что periodId каждой
     * транзакции совпадает с id периода, а openNext() отдаёт заготовку с нулём.
     */
    @Insert
    suspend fun insert(period: PeriodEntity): Long

    /** Возвращает число изменённых строк: ноль означает, что периода в базе нет. */
    @Update
    suspend fun update(period: PeriodEntity): Int

    @Query("SELECT * FROM periods WHERE id = :id")
    suspend fun byId(id: Long): PeriodEntity?

    /** Текущий незакрытый период: планируемый либо идущий. */
    suspend fun openPeriod(profileId: String): PeriodEntity? =
        lastExcludingStatus(profileId, PeriodStatus.CLOSED)

    fun observeOpenPeriod(profileId: String): Flow<PeriodEntity?> =
        observeLastExcludingStatus(profileId, PeriodStatus.CLOSED)

    /** Итоги последнего игрового периода (ТЗ 2.5.11). */
    suspend fun lastClosed(profileId: String): PeriodEntity? =
        lastWithStatus(profileId, PeriodStatus.CLOSED)

    @Query("SELECT * FROM periods WHERE profileId = :profileId ORDER BY number")
    suspend fun all(profileId: String): List<PeriodEntity>

    @Query("SELECT COUNT(*) FROM periods WHERE profileId = :profileId")
    suspend fun count(profileId: String): Int

    // --- Ниже запросы со статусом-параметром. Вызывать через обёртки выше. ---

    @Query(
        "SELECT * FROM periods WHERE profileId = :profileId AND status != :status " +
            "ORDER BY number DESC LIMIT 1"
    )
    suspend fun lastExcludingStatus(profileId: String, status: PeriodStatus): PeriodEntity?

    @Query(
        "SELECT * FROM periods WHERE profileId = :profileId AND status != :status " +
            "ORDER BY number DESC LIMIT 1"
    )
    fun observeLastExcludingStatus(profileId: String, status: PeriodStatus): Flow<PeriodEntity?>

    @Query(
        "SELECT * FROM periods WHERE profileId = :profileId AND status = :status " +
            "ORDER BY number DESC LIMIT 1"
    )
    suspend fun lastWithStatus(profileId: String, status: PeriodStatus): PeriodEntity?
}
