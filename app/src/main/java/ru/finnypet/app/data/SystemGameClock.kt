package ru.finnypet.app.data

import ru.finnypet.app.domain.economy.GameClock
import javax.inject.Inject

/**
 * Единственная реализация часов: системное время.
 *
 * Отдельных часов для демонстрационного режима нет и не нужно. GameClock
 * проставляет только отметки времени операций и закрытия периода, а длину
 * периода не определяет: период закрывается действием ребёнка. Ничто в игре
 * не ждёт календаря, поэтому требование ТЗ 2.5.13 о прохождении цикла подряд
 * без ожидания календарных сроков выполняется само по себе.
 */
class SystemGameClock @Inject constructor() : GameClock {

    override fun now(): Long = System.currentTimeMillis()
}
