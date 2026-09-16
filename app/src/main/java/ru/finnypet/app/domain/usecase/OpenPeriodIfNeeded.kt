package ru.finnypet.app.domain.usecase

import ru.finnypet.app.domain.economy.GameBalance
import ru.finnypet.app.domain.model.GamePeriod
import ru.finnypet.app.domain.model.PeriodStatus
import ru.finnypet.app.domain.model.ProfileId
import ru.finnypet.app.domain.repository.PeriodRepository
import kotlin.coroutines.cancellation.CancellationException

/**
 * Отдаёт период, в котором сейчас идёт игра, открывая самый первый, если игра
 * только началась.
 *
 * Баланс считается от периода: стартовые деньги и доход лежат в нём, а не в
 * отдельной колонке. Поэтому без периода главному экрану нечего показать,
 * а сразу после создания питомца периодов ещё нет — PeriodEngine.openNext()
 * умеет открывать только следующий, ему нужен предыдущий.
 *
 * Числа берутся из контент-пака: правка экономики не идёт через код (ТЗ 3.2).
 */
class OpenPeriodIfNeeded(
    private val periods: PeriodRepository,
    private val balance: GameBalance,
) {

    suspend operator fun invoke(profileId: ProfileId): GamePeriod {
        periods.current(profileId)?.let { return it }

        // Закрытие периода обязано открывать следующий тем же действием: иначе
        // остаток неистраченных монет не переносится и просто пропадает.
        // Молчаливый первый период вместо потерянного скрыл бы эту ошибку.
        check(periods.lastClosed(profileId) == null) {
            "У профиля ${profileId.value} нет открытого периода, но закрытые есть: " +
                "период закрыли, не открыв следующий, и остаток потерян"
        }

        return try {
            periods.open(firstPeriod(profileId))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            // Между проверкой и вставкой период мог открыть кто-то ещё —
            // например, сброс демо-режима при уже открытом главном экране.
            // Уникальность номера внутри профиля не даст завести дубль, и это
            // не повод падать: нужный период к этому моменту уже есть.
            periods.current(profileId) ?: throw error
        }
    }

    private fun firstPeriod(profileId: ProfileId) = GamePeriod(
        id = UNSAVED,
        profileId = profileId,
        number = FIRST_NUMBER,
        income = balance.periodIncome,
        startBalance = balance.startingBalance,
        status = PeriodStatus.PLANNING,
    )

    private companion object {
        const val UNSAVED = 0L
        const val FIRST_NUMBER = 1
    }
}
