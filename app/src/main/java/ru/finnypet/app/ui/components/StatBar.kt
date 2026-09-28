package ru.finnypet.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.finnypet.app.R
import ru.finnypet.app.domain.model.Stat
import ru.finnypet.app.ui.theme.Dimens
import ru.finnypet.app.ui.theme.LocalAnimationsEnabled

/**
 * Показатель питомца: подпись, полоса и число.
 *
 * Число рядом с полосой стоит не для красоты. ТЗ 3.6 запрещает передавать
 * состояние одним лишь цветом, а длина полосы плюс цифра читаются и в
 * чёрно-белом виде, и при дальтонизме.
 */
@Composable
fun StatBar(
    label: String,
    stat: Stat,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val spoken = stringResource(R.string.stat_description, label, stat.value, Stat.RANGE.last)
    Column(
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceTiny),
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = spoken },
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Label(text = label)
            Text(
                text = stat.value.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        ProgressLine(
            fraction = stat.value.toFloat() / Stat.RANGE.last,
            color = color,
        )
    }
}

@Composable
private fun RowScope.Label(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f, fill = false),
    )
}

/**
 * Полоса заполнения от нуля до единицы.
 *
 * Изменение проигрывается плавно, чтобы ребёнок заметил, что показатель
 * сдвинулся после его решения (ТЗ 2.5.9 — последствие должно быть видно).
 * Если анимации отключены в настройках, полоса меняется мгновенно: ТЗ 3.6
 * требует, чтобы движение можно было выключить и ничего при этом не терялось.
 *
 * Своей подписи для чтения вслух у полосы нет — её задаёт тот, кто
 * показывает: у [StatBar] это подпись с числом. При самостоятельном
 * использовании подпись обязательна, иначе озвучка пропустит полосу.
 *
 * Трек — не серо-зелёный `surfaceVariant`, а `surfaceSunken`: на кремовом
 * фоне он почти не виден без рамки (DESIGN_PLAN 2.4), поэтому у трека есть
 * тонкая рамка. Заливка получает лёгкий блеск сверху — так полоса выглядит
 * приподнятой, а не плоской закраской.
 */
@Composable
fun ProgressLine(
    fraction: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    contentDescription: String? = null,
) {
    val target = fraction.coerceIn(0f, 1f)
    val animated by animateFloatAsState(targetValue = target, label = "progress")
    val shown = if (LocalAnimationsEnabled.current) animated else target
    ProgressTrack(
        fraction = shown,
        color = color,
        modifier = modifier.then(
            if (contentDescription == null) {
                Modifier
            } else {
                Modifier.clearAndSetSemantics { this.contentDescription = contentDescription }
            }
        ),
    )
}

/**
 * Та же полоса без собственной анимации — для кусочков [SegmentLine]: они
 * закрашиваются целиком, плавному росту там расти нечему.
 */
@Composable
internal fun ProgressTrack(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(Dimens.BarHeight)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(Dimens.BarHeight)
            .background(MaterialTheme.colorScheme.surfaceVariant, shape)
            .border(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), shape),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(color, shape)
                .background(
                    Brush.verticalGradient(0f to Color.White.copy(alpha = 0.3f), 0.4f to Color.Transparent),
                    shape,
                ),
        )
    }
}

/**
 * Полоса из [total] кусочков, первые [done] закрашены (DESIGN_PLAN 3.9):
 * ребёнку 7–11 лет проще сосчитать кусочки, чем оценить долю сплошной
 * полосы. Кусочек — тот же [ProgressTrack], чтобы полосы не расходились видом.
 * Своей подписи нет: число рядом говорит то же словами.
 */
@Composable
fun SegmentLine(
    done: Int,
    total: Int,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceTiny),
        modifier = modifier.fillMaxWidth(),
    ) {
        repeat(total) { index ->
            ProgressTrack(fraction = if (index < done) 1f else 0f, color = color, modifier = Modifier.weight(1f))
        }
    }
}
