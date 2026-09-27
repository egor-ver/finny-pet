package ru.finnypet.app.ui.theme

/**
 * Общий набор длительностей движения (DESIGN_PLAN 2.7) — вместо разрозненных
 * констант по файлам (`Buttons.kt`, `FinnyNavHost.kt`). Миллисекунды, а не
 * готовые `AnimationSpec`: `tween`/`spring` типизированы значением, которое
 * анимируют (`Dp`, `Float`, цвет), а не единым для всех типом.
 *
 * Всё движение дополнительно слушает [LocalAnimationsEnabled] (AD-8) — этот
 * объект задаёт только «сколько», а не «включено ли».
 */
object Motion {

    /** Нажатие кнопки, выбор плитки. */
    const val QuickMs = 120

    /** Переходы экранов, появление реплики. */
    const val StandardMs = 250

    /** Счёт суммы («катится»), заполнение полосы и банки — `FastOutSlowIn` (U9). */
    const val EmphasisMs = 400

    /** Звёзды по очереди, «Вырос!», «Верно!» — пружина `MediumBouncy` (U9, U14+U16). */
    const val CelebrateMs = 800
}
