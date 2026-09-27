package ru.finnypet.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.sp
import ru.finnypet.app.R

/**
 * Шрифт Nunito (DESIGN_PLAN 2.2): скруглённый гротеск с кириллицей, файлы
 * лежат в `res/font` (не загружаемый шрифт — AD-15 в
 * `docs/ARCHITECTURE_DECISIONS.md`).
 */
val Nunito = FontFamily(
    Font(R.font.nunito_medium, FontWeight.Medium),
    Font(R.font.nunito_bold, FontWeight.Bold),
    Font(R.font.nunito_extrabold, FontWeight.ExtraBold),
)

/**
 * Русские переносы для многострочного текста: без явного языка перенос
 * берёт язык устройства, и на телефоне с английским интерфейсом русские
 * слова не переносились бы (DESIGN_PLAN 2.2).
 */
private val Hyphenated = LocaleList("ru")

/** Табличные цифры: при смене суммы число не «прыгает» по ширине. */
private const val TabularNumbers = "tnum"

/**
 * Размеры текста.
 *
 * ТЗ 3.6 требует основной текст не меньше 16 sp — все стили начинаются с
 * шестнадцати, мельче в приложении нет ничего. Суммы — табличные цифры
 * (`tnum`), чтобы при их изменении число не «прыгало» по ширине.
 *
 * Размеры заданы в sp, а не в dp: при системном увеличении шрифта они
 * растут вместе с ним, как того же требует ТЗ 3.6.
 */
val Typography = Typography(
    displaySmall = TextStyle(
        fontFamily = Nunito,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
    ),
    displayMedium = TextStyle(
        fontFamily = Nunito,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 40.sp,
        lineHeight = 48.sp,
        fontFeatureSettings = TabularNumbers,
    ),
    headlineMedium = TextStyle(
        fontFamily = Nunito,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = Nunito,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontFeatureSettings = TabularNumbers,
    ),
    titleMedium = TextStyle(
        fontFamily = Nunito,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        hyphens = Hyphens.Auto,
        lineBreak = LineBreak.Paragraph,
        localeList = Hyphenated,
    ),
    // Мелкие стили Material по умолчанию — 11–14 sp. Задаём их явно, иначе
    // первый же bodySmall в новом экране нарушил бы ТЗ 3.6.
    titleSmall = TextStyle(
        fontFamily = Nunito,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = Nunito,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        lineHeight = 26.sp,
        hyphens = Hyphens.Auto,
        lineBreak = LineBreak.Paragraph,
        localeList = Hyphenated,
    ),
    bodyMedium = TextStyle(
        fontFamily = Nunito,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        hyphens = Hyphens.Auto,
        lineBreak = LineBreak.Paragraph,
        localeList = Hyphenated,
    ),
    bodySmall = TextStyle(
        fontFamily = Nunito,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = Nunito,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
    ),
    // Шестнадцать, а не четырнадцать: порог ТЗ 3.6 держится во всех
    // стилях, иначе мелкий размер рано или поздно попадёт на экран.
    labelMedium = TextStyle(
        fontFamily = Nunito,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        lineHeight = 20.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = Nunito,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        lineHeight = 20.sp,
    ),
)

/**
 * Крупные суммы вне шкалы Material: кошелёк на главном, банки плана,
 * «Пройдено 2 из 6» (DESIGN_PLAN 2.2). Не входит в [Typography], потому
 * что в Material3 нет подходящей самостоятельной роли для этого размера.
 */
val NumberLarge = TextStyle(
    fontFamily = Nunito,
    fontWeight = FontWeight.ExtraBold,
    fontSize = 28.sp,
    lineHeight = 34.sp,
    fontFeatureSettings = TabularNumbers,
)
