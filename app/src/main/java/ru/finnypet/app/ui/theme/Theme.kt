package ru.finnypet.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * `secondary`/`tertiary` и `surfaceContainer*` без явного значения Material
 * подставляет свой (несвязанный ни с чем) базовый сиреневый и розовый — на
 * подсказках (`PlanningHint`, совет дня, разбор задания, демо-баннер) это
 * читалось как цвет «Копилки». Переиспользуем токены 2.1: `secondary` — тон
 * «Желаемого», `tertiary` — тон «Копилки»; на знакомстве (`OnboardingScreen`)
 * карточки «Желаемое» и «Копилка» тоже вернули верное соответствие
 * направлению. Карточка «Нужное» там же берёт `FinnyPalette.need` напрямую:
 * `primaryContainer` — теперь просто мягкая плашка действия («раздел
 * взрослого» и подобное), а не цвет «Нужного».
 */
private val LightColors = lightColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = Primary,
    secondary = WantFill,
    onSecondary = Color.White,
    secondaryContainer = WantContainer,
    onSecondaryContainer = WantText,
    tertiary = SaveFill,
    onTertiary = Color.White,
    tertiaryContainer = SaveContainer,
    onTertiaryContainer = SaveText,
    error = ShortageText,
    onError = Color.White,
    errorContainer = ShortageContainer,
    onErrorContainer = ShortageText,
    background = Background,
    onBackground = Ink,
    surface = Surface,
    onSurface = Ink,
    surfaceVariant = SurfaceSunken,
    onSurfaceVariant = InkSoft,
    surfaceContainerLowest = Background,
    surfaceContainerLow = SurfaceSunken,
    surfaceContainer = Surface,
    surfaceContainerHigh = Surface,
    surfaceContainerHighest = Surface,
    outline = Outline,
)

private val DarkColors = darkColorScheme(
    primary = PrimaryDark,
    onPrimary = OnPrimaryDark,
    primaryContainer = PrimaryContainerDark,
    onPrimaryContainer = PrimaryDark,
    secondary = WantFillDark,
    onSecondary = Color(0xFF4A2800),
    secondaryContainer = WantContainerDark,
    onSecondaryContainer = WantTextDark,
    tertiary = SaveFillDark,
    onTertiary = Color(0xFF2A1249),
    tertiaryContainer = SaveContainerDark,
    onTertiaryContainer = SaveTextDark,
    error = ShortageTextDark,
    onError = Color(0xFF690005),
    errorContainer = ShortageContainerDark,
    onErrorContainer = ShortageTextDark,
    background = BackgroundDark,
    onBackground = InkDark,
    surface = SurfaceDark,
    onSurface = InkDark,
    surfaceVariant = SurfaceSunkenDark,
    onSurfaceVariant = InkSoftDark,
    surfaceContainerLowest = BackgroundDark,
    surfaceContainerLow = SurfaceSunkenDark,
    surfaceContainer = SurfaceDark,
    surfaceContainerHigh = SurfaceDark,
    surfaceContainerHighest = SurfaceDark,
    outline = OutlineDark,
)

/**
 * Один цвет направления трат: заливка (полосы, иконки), текст и светлый
 * контейнер для карточек (DESIGN_PLAN 2.1). Своя группа, а не роли Material
 * `secondary`/`tertiary`: иначе, как раньше, направление снова могло бы
 * незаметно совпасть по цвету с главным действием.
 */
data class DirectionColors(val fill: Color, val text: Color, val container: Color)

/** Четыре тона монеты: грань, тень нижним полумесяцем, кант, блик (DESIGN_PLAN 2.5). */
data class CoinColors(val face: Color, val shadow: Color, val edge: Color, val highlight: Color)

/** Цвета, которых нет среди ролей Material: монета, направления, нижняя грань кнопки. */
data class FinnyPalette(
    val coin: CoinColors,
    val need: DirectionColors,
    val want: DirectionColors,
    val save: DirectionColors,
    val buttonDeep: Color,
)

private val CoinColorsShared = CoinColors(
    face = CoinFace,
    shadow = CoinShadow,
    edge = CoinEdge,
    highlight = CoinHighlight,
)

private val FinnyPaletteLight = FinnyPalette(
    coin = CoinColorsShared,
    need = DirectionColors(fill = NeedFill, text = NeedText, container = NeedContainer),
    want = DirectionColors(fill = WantFill, text = WantText, container = WantContainer),
    save = DirectionColors(fill = SaveFill, text = SaveText, container = SaveContainer),
    buttonDeep = PrimaryDeep,
)

private val FinnyPaletteDark = FinnyPalette(
    coin = CoinColorsShared,
    need = DirectionColors(fill = NeedFillDark, text = NeedTextDark, container = NeedContainerDark),
    want = DirectionColors(fill = WantFillDark, text = WantTextDark, container = WantContainerDark),
    save = DirectionColors(fill = SaveFillDark, text = SaveTextDark, container = SaveContainerDark),
    buttonDeep = PrimaryDeepDark,
)

val LocalFinnyPalette = staticCompositionLocalOf<FinnyPalette> {
    error("FinnyPalette не задан: оберните контент в FinnypetTheme")
}

/** Доступ к [FinnyPalette] в стиле `MaterialTheme.colorScheme`. */
object FinnyTheme {
    val palette: FinnyPalette
        @Composable get() = LocalFinnyPalette.current
}

/**
 * Тема приложения.
 *
 * Динамический цвет Android 12+ намеренно не используется: он подменяет
 * палитру приложения цветами обоев пользователя. Для игры, где цвет несёт
 * смысл — синий это действие, зелёный/оранжевый/фиолетовый — направления
 * трат, красный — нехватка денег, — такая подмена ломает и узнаваемость,
 * и подобранный контраст (ТЗ 3.6).
 */
@Composable
fun FinnypetTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalFinnyPalette provides if (darkTheme) FinnyPaletteDark else FinnyPaletteLight,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = Typography,
            content = content,
        )
    }
}
