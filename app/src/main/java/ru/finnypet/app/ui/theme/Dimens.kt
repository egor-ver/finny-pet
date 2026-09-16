package ru.finnypet.app.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Размеры, общие для всех экранов.
 *
 * Нужны, чтобы отступы и размеры кнопок не расходились от экрана к экрану:
 * ТЗ 3.6 требует единообразия навигации и расположения кнопки возврата.
 */
object Dimens {

    /**
     * Минимальный размер того, по чему ребёнок нажимает.
     *
     * ТЗ 3.6 рекомендует не меньше 48x48 dp. Все кнопки и нажимаемые
     * карточки обязаны проходить по этому размеру, даже если рисунок
     * внутри мельче.
     */
    val TouchTarget = 48.dp

    val SpaceTiny = 4.dp
    val SpaceSmall = 8.dp
    val SpaceMedium = 12.dp
    val Space = 16.dp

    /** Поля экрана. На 360 dp по ширине это оставляет 328 dp содержимого. */
    val ScreenPadding = 16.dp

    val Corner = 16.dp

    val BarHeight = 12.dp
}
