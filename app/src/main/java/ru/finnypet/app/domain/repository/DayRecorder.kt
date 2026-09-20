package ru.finnypet.app.domain.repository

import ru.finnypet.app.domain.model.GamePeriod
import ru.finnypet.app.domain.model.PetGrowth
import ru.finnypet.app.domain.model.PetState
import ru.finnypet.app.domain.model.ProfileId

/**
 * Что меняется при закрытии игрового дня: сам день, питомец и следующий день.
 *
 * [nextPeriod] приходит несохранённым — идентификатор выдаст база.
 */
data class DayClosure(
    val closedPeriod: GamePeriod,
    val state: PetState,
    val growth: PetGrowth,
    val nextPeriod: GamePeriod,
)

/**
 * Закрывает день одной транзакцией.
 *
 * Закрытие обязано открывать следующий день тем же действием: иначе
 * неистраченный остаток не переносится, а OpenPeriodIfNeeded справедливо
 * посчитает такое состояние ошибкой. Питомец меняется здесь же — ТЗ 2.5.9
 * требует, чтобы после действия менялось всё вместе.
 *
 * Доход нового дня не начисляется: это делает OpenPeriodIfNeeded при первом
 * входе на любой экран игры, и он же чинит начисление, если приложение
 * закроется сразу после закрытия дня.
 */
interface DayRecorder {

    /** Возвращает открытый следующий день — уже с идентификатором из базы. */
    suspend fun close(profileId: ProfileId, closure: DayClosure): GamePeriod
}
