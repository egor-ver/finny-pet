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

/** Знакомство с игрой при первом запуске (ТЗ 2.5.1). */
@Serializable
data object Onboarding : Route

/** Выбор внешности и имени питомца (ТЗ 2.5.2). */
@Serializable
data object CreatePet : Route

/** Главный экран (ТЗ 2.5.3). */
@Serializable
data object Main : Route

/** План личного бюджета на игровой день (ТЗ 2.5.5). */
@Serializable
data object Budget : Route

/** Итоги игрового дня и его закрытие (ТЗ 2.5.9, 2.5.10). */
@Serializable
data object Day : Route

/** Магазин: покупки и расходы (ТЗ 2.5.6). */
@Serializable
data object Shop : Route

/** История, учебный прогресс и справочник (ТЗ 2.5.11). */
@Serializable
data object Progress : Route

/** Копилка и цель (ТЗ 2.5.7). */
@Serializable
data object Savings : Route

/** Список заданий (ТЗ 2.5.8). */
@Serializable
data object Tasks : Route

/** Одно задание: вступление, шаги, разбор (ТЗ 2.5.8). */
@Serializable
data class Task(val taskId: String) : Route
