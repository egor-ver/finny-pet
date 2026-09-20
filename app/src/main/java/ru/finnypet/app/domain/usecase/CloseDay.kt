package ru.finnypet.app.domain.usecase

import ru.finnypet.app.domain.economy.PeriodEngine
import ru.finnypet.app.domain.economy.PeriodOutcome
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.GamePeriod
import ru.finnypet.app.domain.model.GameResult
import ru.finnypet.app.domain.model.PeriodStatus
import ru.finnypet.app.domain.model.ProfileId
import ru.finnypet.app.domain.repository.DayClosure
import ru.finnypet.app.domain.repository.DayRecorder
import ru.finnypet.app.domain.repository.PeriodRepository
import ru.finnypet.app.domain.repository.ProfileRepository

/**
 * Итоги закрытого дня вместе с уже открытым следующим.
 *
 * Следующий день лежит здесь, а не добывается отдельным запросом: он открыт
 * той же транзакцией, и экрану нужен его номер, чтобы позвать ребёнка дальше.
 */
data class ClosedDay(
    val outcome: PeriodOutcome,
    val nextPeriod: GamePeriod,
    /** Сколько очков роста принёс этот день: разница до и после. */
    val earnedPoints: Int,
)

/**
 * Закрывает игровой день и открывает следующий (ТЗ 2.5.9, 2.5.10).
 *
 * Считает [PeriodEngine]: сравнивает план с фактом, меняет состояние питомца,
 * начисляет очки роста и переносит неистраченный остаток. Записывает
 * [DayRecorder] одной транзакцией — день, питомец и следующий день меняются
 * вместе или не меняются вовсе.
 *
 * Возвращает `null`, когда закрывать нечего: день ещё планируется, плана нет
 * или у профиля нет питомца. Это не ошибка — экран просто не покажет итогов.
 */
class CloseDay(
    private val periods: PeriodRepository,
    private val profiles: ProfileRepository,
    private val engine: PeriodEngine,
    private val recorder: DayRecorder,
) {

    suspend operator fun invoke(profileId: ProfileId): GameResult<ClosedDay>? {
        val period = periods.current(profileId) ?: return null
        if (period.status != PeriodStatus.RUNNING) return null

        val plan = periods.plan(period.id) ?: return null
        if (plan.total == Coins.ZERO) return null

        val pet = profiles.pet(profileId) ?: return null

        val result = engine.close(
            period = period,
            plan = plan,
            transactions = periods.transactions(period.id),
            state = pet.state,
            currentGrowth = pet.growth,
        )
        val outcome = result.value

        val nextPeriod = recorder.close(
            profileId = profileId,
            closure = DayClosure(
                closedPeriod = outcome.closedPeriod,
                state = outcome.state,
                growth = outcome.growth,
                nextPeriod = engine.openNext(outcome.closedPeriod, outcome.carryOver),
            ),
        )

        return GameResult(
            value = ClosedDay(
                outcome = outcome,
                nextPeriod = nextPeriod,
                earnedPoints = outcome.growth.points - pet.growth.points,
            ),
            explanation = result.explanation,
            changes = result.changes,
        )
    }
}
