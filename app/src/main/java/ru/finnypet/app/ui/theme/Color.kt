package ru.finnypet.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Палитра приложения (DESIGN_PLAN, раздел 2.1).
 *
 * Раньше зелёный был одновременно цветом главной кнопки и цветом «Нужного»,
 * поэтому смысл цвета размывался. Теперь действие (`primary`) — синее, а
 * каждое направление трат (`need`/`want`/`save` в `Theme.kt`) получает свой
 * цвет и никогда не используется для кнопок. Контраст текста везде не
 * меньше 4,5:1 на своём фоне (посчитано по WCAG 2.1 при подготовке плана).
 */

// Светлая тема — общий фон и текст
val Background = Color(0xFFFFF7EC)
val Surface = Color(0xFFFFFFFF)
val SurfaceSunken = Color(0xFFF1E8DA)
val Outline = Color(0xFFE4D8C6)
val Ink = Color(0xFF2A2140)
val InkSoft = Color(0xFF62597A)

// Светлая тема — действие
val Primary = Color(0xFF1F63D6)
val OnPrimary = Color(0xFFFFFFFF)
val PrimaryDeep = Color(0xFF164AA6)
val PrimaryContainer = Color(0xFFE3ECFF)

// Светлая тема — направления трат
val NeedFill = Color(0xFF2A9154)
val NeedText = Color(0xFF17733F)
val NeedContainer = Color(0xFFDFF6E8)
val WantFill = Color(0xFFC96500)
val WantText = Color(0xFFA04B00)
val WantContainer = Color(0xFFFFEBD2)
val SaveFill = Color(0xFF9460E0)
val SaveText = Color(0xFF7B3FC4)
val SaveContainer = Color(0xFFEFE4FF)

// Светлая тема — темы заданий; «Накопления» берут цвет копилки (DESIGN_PLAN 2.1)
val TopicPlanFill = Color(0xFF17A2B8)
val TopicPlanText = Color(0xFF0B6E7D)
val TopicPlanContainer = Color(0xFFDDF4F7)
val TopicShopFill = Color(0xFFE0567F)
val TopicShopText = Color(0xFFA8325A)
val TopicShopContainer = Color(0xFFFCE3EB)

// Светлая тема — монета (одна и та же в обеих темах)
val CoinFace = Color(0xFFFFC933)
val CoinShadow = Color(0xFFF2A516)
val CoinEdge = Color(0xFFC98400)
val CoinHighlight = Color(0xFFFFF1B3)

/** Звезда роста — одна и та же в обеих темах (DESIGN_PLAN 2.1). */
val StarFill = Color(0xFFFFC52E)

// Светлая тема — нехватка монет (единственное место для красного)
val ShortageText = Color(0xFFB3261E)
val ShortageContainer = Color(0xFFFDE4E1)

// Тёмная тема — общий фон и текст
val BackgroundDark = Color(0xFF17131F)
val SurfaceDark = Color(0xFF231D2E)
val SurfaceSunkenDark = Color(0xFF332B40)
val OutlineDark = Color(0xFF4A4058)
val InkDark = Color(0xFFF3EEF9)
val InkSoftDark = Color(0xFFBDB3CF)

// Тёмная тема — действие
val PrimaryDark = Color(0xFF8DB4FF)
val OnPrimaryDark = Color(0xFF0B2A66)
val PrimaryDeepDark = Color(0xFF5E8AE0)
val PrimaryContainerDark = Color(0xFF1D2F5C)

// Тёмная тема — направления трат
val NeedFillDark = Color(0xFF6FD39A)
val NeedTextDark = Color(0xFF6FD39A)
val NeedContainerDark = Color(0xFF16392A)
val WantFillDark = Color(0xFFFFB060)
val WantTextDark = Color(0xFFFFB060)
val WantContainerDark = Color(0xFF45300F)
val SaveFillDark = Color(0xFFC9A6FF)
val SaveTextDark = Color(0xFFC9A6FF)
val SaveContainerDark = Color(0xFF34264F)

// Тёмная тема — темы заданий
val TopicPlanFillDark = Color(0xFF6FD6E6)
val TopicPlanTextDark = Color(0xFF6FD6E6)
val TopicPlanContainerDark = Color(0xFF123A40)
val TopicShopFillDark = Color(0xFFFF9AB8)
val TopicShopTextDark = Color(0xFFFF9AB8)
val TopicShopContainerDark = Color(0xFF4A1E2C)

// Тёмная тема — нехватка монет
val ShortageTextDark = Color(0xFFFFB4AB)
val ShortageContainerDark = Color(0xFF5C1A14)
