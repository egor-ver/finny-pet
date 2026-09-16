package ru.finnypet.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = GreenPrimary,
    onPrimary = GreenOnPrimary,
    primaryContainer = GreenContainer,
    onPrimaryContainer = GreenOnContainer,
    secondary = AmberSecondary,
    onSecondary = AmberOnSecondary,
    secondaryContainer = AmberContainer,
    onSecondaryContainer = AmberOnContainer,
    tertiary = VioletTertiary,
    onTertiary = VioletOnTertiary,
    tertiaryContainer = VioletContainer,
    onTertiaryContainer = VioletOnContainer,
    error = RedError,
    onError = RedOnError,
    errorContainer = RedContainer,
    onErrorContainer = RedOnContainer,
    background = SurfaceLight,
    onBackground = OnSurfaceLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight,
)

private val DarkColors = darkColorScheme(
    primary = GreenPrimaryDark,
    onPrimary = GreenOnPrimaryDark,
    primaryContainer = GreenContainerDark,
    onPrimaryContainer = GreenOnContainerDark,
    secondary = AmberSecondaryDark,
    onSecondary = AmberOnSecondaryDark,
    secondaryContainer = AmberContainerDark,
    onSecondaryContainer = AmberOnContainerDark,
    tertiary = VioletTertiaryDark,
    onTertiary = VioletOnTertiaryDark,
    tertiaryContainer = VioletContainerDark,
    onTertiaryContainer = VioletOnContainerDark,
    error = RedErrorDark,
    onError = RedOnErrorDark,
    errorContainer = RedContainerDark,
    onErrorContainer = RedOnContainerDark,
    background = SurfaceDark,
    onBackground = OnSurfaceDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark,
)

/**
 * Тема приложения.
 *
 * Динамический цвет Android 12+ намеренно не используется: он подменяет
 * палитру приложения цветами обоев пользователя. Для игры, где цвет несёт
 * смысл — зелёный это накопления, янтарный монеты, красный нехватка, —
 * такая подмена ломает и узнаваемость, и подобранный контраст (ТЗ 3.6).
 */
@Composable
fun FinnypetTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        content = content,
    )
}
