package ru.finnypet.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.dp
import ru.finnypet.app.ui.theme.FinnyTheme
import ru.finnypet.app.ui.theme.LocalAnimationsEnabled
import ru.finnypet.app.ui.theme.Motion

/**
 * Стеклянная банка со скруглённой крышкой (DESIGN_PLAN 2.5) — метафора
 * «Разложи по банкам» из реплик совы, нарисованная кодом.
 *
 * [level] — доля от нуля до единицы, до которой банка налита цветом
 * направления [color]. [coins] — сколько монет лежит на дне вместо уровня:
 * у копилки после плана полная банка при одной отложенной монете
 * обманывала бы (DESIGN_PLAN 3.2), а монеты на дне говорят «там что-то
 * есть» без ложной доли. Больше трёх не рисуем: число пишет подпись.
 *
 * Рамка стекла — `onSurfaceVariant`: значимая граница должна быть видна
 * с контрастом не меньше 3:1 (ТЗ 3.6), `outline` дал бы 1,3:1.
 * Уровень меняется за `Emphasis`, при выключенном движении — сразу.
 */
@Composable
fun Jar(
    level: Float,
    color: Color,
    modifier: Modifier = Modifier,
    coins: Int = 0,
) {
    val target = level.coerceIn(0f, 1f)
    val shown by animateFloatAsState(
        targetValue = target,
        animationSpec = if (LocalAnimationsEnabled.current) tween(Motion.EmphasisMs, easing = FastOutSlowInEasing) else snap(),
        label = "jar",
    )
    val glass = MaterialTheme.colorScheme.surface
    val rim = MaterialTheme.colorScheme.onSurfaceVariant
    val coinColors = FinnyTheme.palette.coin
    Canvas(modifier = modifier.size(JAR_WIDTH, JAR_HEIGHT)) {
        val stroke = 2.dp.toPx()
        val w = size.width
        val lidHeight = size.height * 0.14f
        val body = RoundRect(
            left = stroke / 2,
            top = size.height * 0.2f,
            right = w - stroke / 2,
            bottom = size.height - stroke / 2,
            cornerRadius = CornerRadius(w * 0.28f),
        )
        val bodyPath = Path().apply { addRoundRect(body) }
        drawPath(bodyPath, glass)
        clipPath(bodyPath) {
            val top = body.bottom - body.height * shown
            drawRect(color = color, topLeft = Offset(0f, top), size = Size(w, body.bottom - top))
            val diameter = w * 0.34f
            val floor = body.bottom - diameter / 2 - stroke
            val spots = listOf(
                Offset(w / 2 - diameter * 0.55f, floor),
                Offset(w / 2 + diameter * 0.55f, floor),
                Offset(w / 2, floor - diameter * 0.8f),
            )
            spots.take(coins.coerceIn(0, spots.size)).forEach { drawCoin(center = it, diameter = diameter, colors = coinColors) }
            // Блик стекла слева — банка читается стеклянной, а не коробкой.
            drawRoundRect(
                color = Color.White.copy(alpha = 0.35f),
                topLeft = Offset(body.left + w * 0.14f, body.top + body.height * 0.12f),
                size = Size(w * 0.1f, body.height * 0.5f),
                cornerRadius = CornerRadius(w * 0.05f),
            )
        }
        drawPath(bodyPath, rim, style = Stroke(width = stroke))
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.12f, stroke / 2),
            size = Size(w * 0.76f, lidHeight),
            cornerRadius = CornerRadius(lidHeight / 2),
        )
    }
}

// Крупно: после подтверждения банки — главный элемент экрана плана, а
// пропорции 7:9 прежние, чтобы крышка и монеты на дне не поплыли.
private val JAR_WIDTH = 96.dp
private val JAR_HEIGHT = 124.dp
