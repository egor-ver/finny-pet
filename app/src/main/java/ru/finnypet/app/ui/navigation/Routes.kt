package ru.finnypet.app.ui.navigation

import kotlinx.serialization.Serializable

/**
 * Маршруты приложения.
 *
 * Типизированные, а не строковые: опечатка в адресе перехода становится
 * ошибкой компиляции, а не пустым экраном у ребёнка. Разбор адресов берёт
 * на себя та же сериализация, что читает контент-пак.
 *
 * Общий запечатанный интерфейс нужен, чтобы стартовым экраном нельзя было
 * передать что угодно: без него параметр пришлось бы объявлять как Any,
 * и посторонний объект уронил бы приложение при построении графа.
 *
 * Маршруты добавляются вместе со своими экранами — маршрут без экрана
 * бессмыслен.
 */
sealed interface Route

/** Выбор внешности и имени питомца — первый экран новой игры. */
@Serializable
data object CreatePet : Route

/**
 * Главный экран. [tutorial] — открыть его сразу под обучением
 *: так приходят только из создания питомца. Экран берёт значение
 * один раз как начальное, дальше шаг обучения живёт в его состоянии —
 * иначе возврат на главный с других экранов запускал бы обучение заново.
 */
@Serializable
data class Main(val tutorial: Boolean = false) : Route

/** План личного бюджета на игровой день. */
@Serializable
data object Budget : Route

/** Итоги игрового дня и его закрытие. */
@Serializable
data object Day : Route

/** Магазин: покупки и расходы. */
@Serializable
data object Shop : Route

/** История, учебный прогресс и справочник. */
@Serializable
data object Progress : Route

/** Пример, отделяющий раздел взрослого от детской части. */
@Serializable
data object AdultGate : Route

/** Раздел для взрослого. */
@Serializable
data object Adult : Route

/** Копилка и цель. */
@Serializable
data object Savings : Route

/** Список заданий. */
@Serializable
data object Tasks : Route

/** Одно задание: вступление, шаги, разбор. */
@Serializable
data class Task(val taskId: String) : Route
