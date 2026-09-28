package ru.finnypet.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import ru.finnypet.app.ui.theme.FinnyTheme
import ru.finnypet.app.ui.theme.LocalAnimationsEnabled
import kotlin.math.PI
import kotlin.math.sin

/**
 * Конфетти при смене стадии роста (DESIGN_PLAN 2.7): [CONFETTI_PIECES]
 * кусочков цветов направлений один раз падают сверху вниз за [CONFETTI_MS].
 * Только на смену стадии — это самое редкое событие игры, и праздник на
 * каждую звезду обесценил бы его.
 *
 * Кусочки разложены по формуле, а не случайно: раздел 5 плана исключает
 * случайность в игре, а одинаковый рисунок проще проверить тестом.
 * Слой прозрачен для нажатий и озвучки — о росте говорит надпись рядом.
 * При выключенном движении не рисуется вовсе (AD-8): надпись остаётся.
 */
@Composable
fun Confetti(modifier: Modifier = Modifier) {
    if (!LocalAnimationsEnabled.current) return
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(CONFETTI_MS, easing = LinearEasing))
    }
    val palette = FinnyTheme.palette
    val colors = listOf(palette.need.fill, palette.want.fill, palette.save.fill)
    Canvas(modifier = modifier) {
        val t = progress.value
        if (t >= 1f) return@Canvas
        val piece = Size(PIECE_WIDTH.toPx(), PIECE_HEIGHT.toPx())
        repeat(CONFETTI_PIECES) { index ->
            val p = confettiPiece(index, t)
            if (p.alpha <= 0f) return@repeat
            val center = Offset(p.x * size.width, p.y * size.height)
            rotate(degrees = p.angle, pivot = center) {
                drawRect(
                    color = colors[index % colors.size],
                    topLeft = center - Offset(piece.width / 2, piece.height / 2),
                    size = piece,
                    alpha = p.alpha,
                )
            }
        }
    }
}

/** Кусочек в долях слоя: [x], [y] — центр, [angle] — поворот в градусах, [alpha] — прозрачность. */
internal data class ConfettiPiece(val x: Float, val y: Float, val angle: Float, val alpha: Float)

/**
 * Где кусочек номер [index] при общем ходе [t] от нуля до единицы. Столбцы
 * раскиданы шагом золотого сечения — ровно, но без видимой сетки; кусочки
 * стартуют чуть вразнобой и чуть выше слоя, покачиваются и гаснут к концу пути.
 */
internal fun confettiPiece(index: Int, t: Float): ConfettiPiece {
    val delay = (index % START_GROUPS) * START_STEP
    val local = ((t - delay) / (1f - MAX_DELAY)).coerceIn(0f, 1f)
    val column = (index * GOLDEN_STEP) % 1f
    val sway = sin(local * 2f * PI.toFloat() + index) * SWAY
    val spin = if (index % 2 == 0) 1f else -1f
    return ConfettiPiece(
        x = column + sway,
        y = START_Y + local * (1f - START_Y),
        angle = index * 37f + spin * local * 360f,
        alpha = if (local < FADE_FROM) 1f else (1f - local) / (1f - FADE_FROM),
    )
}

/** 900 мс и 24 кусочка — из DESIGN_PLAN 2.7. */
internal const val CONFETTI_MS = 900
internal const val CONFETTI_PIECES = 24

private const val GOLDEN_STEP = 0.618034f
private const val START_GROUPS = 4
private const val START_STEP = 0.06f
private const val MAX_DELAY = START_STEP * (START_GROUPS - 1)
private const val START_Y = -0.1f
private const val SWAY = 0.03f
private const val FADE_FROM = 0.8f
private val PIECE_WIDTH = 8.dp
private val PIECE_HEIGHT = 5.dp
