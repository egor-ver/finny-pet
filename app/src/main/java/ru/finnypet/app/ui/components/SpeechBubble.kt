package ru.finnypet.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import ru.finnypet.app.ui.theme.Dimens

/**
 * Единая реплика питомца (DESIGN_PLAN 2.6) — вместо `PetSpeech` (задание),
 * `OwlBubble` (магазин) и своей строки в плане: сова слева, справа белая
 * карточка с хвостиком к ней. [content] — не только текст: у магазина в
 * ней ещё и итог покупки.
 *
 * [bubbleModifier] — для самой карточки без совы: обучению нужны её границы,
 * чтобы стрелка начиналась от облачка, а не от совы рядом.
 */
@Composable
fun SpeechBubble(
    owl: OwlLook,
    modifier: Modifier = Modifier,
    // Сова рядом с репликой обычно 88 dp, в обучении — 112 dp (DESIGN_PLAN 2.6).
    owlRole: OwlRole = OwlRole.WithSpeech,
    bubbleModifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMedium),
        modifier = modifier.fillMaxWidth(),
    ) {
        Owl(look = owl, size = owlRole.size)
        BubbleCard(modifier = Modifier.weight(1f).then(bubbleModifier), content = content)
    }
}

/** Частый случай — одна фраза без своей вёрстки (задание, план). */
@Composable
fun SpeechBubble(owl: OwlLook, text: String, modifier: Modifier = Modifier) {
    SpeechBubble(owl = owl, modifier = modifier) {
        Text(text = text, style = MaterialTheme.typography.bodyLarge)
    }
}

/**
 * Реплика над совой (DESIGN_PLAN 3.1: главный экран) — герою в центре мало
 * места сбоку, поэтому карточка во всю ширину, а хвостик указывает вниз, к
 * сове под ней, а не в сторону, как у [SpeechBubble].
 */
@Composable
fun TopSpeechBubble(text: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(Dimens.CornerCard)
    val shadowColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
    val surface = MaterialTheme.colorScheme.surface
    Box(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(elevation = Dimens.CardShadowElevation, shape = shape, ambientColor = shadowColor, spotColor = shadowColor)
                .clip(shape)
                .background(surface)
                .padding(horizontal = Dimens.Space, vertical = Dimens.SpaceMedium),
        ) {
            Text(text = text, style = MaterialTheme.typography.bodyLarge)
        }
        Canvas(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = TailWidth / 2)
                .size(width = TailHeight, height = TailWidth),
        ) {
            val tail = Path().apply {
                moveTo(0f, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width / 2, size.height)
                close()
            }
            drawPath(tail, color = surface)
        }
    }
}

/** Карточка с хвостиком слева, к сове (DESIGN_PLAN 2.6: «хвостик 10 dp в сторону совы»). */
@Composable
private fun BubbleCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(Dimens.CornerCard)
    val shadowColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
    val surface = MaterialTheme.colorScheme.surface
    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = -TailWidth / 2)
                .size(width = TailWidth, height = TailHeight),
        ) {
            val tail = Path().apply {
                moveTo(size.width, 0f)
                lineTo(size.width, size.height)
                lineTo(0f, size.height / 2)
                close()
            }
            drawPath(tail, color = surface)
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSmall),
            modifier = Modifier
                .fillMaxWidth()
                .shadow(elevation = Dimens.CardShadowElevation, shape = shape, ambientColor = shadowColor, spotColor = shadowColor)
                .clip(shape)
                .background(surface)
                .padding(horizontal = Dimens.Space, vertical = Dimens.SpaceMedium),
            content = content,
        )
    }
}

private val TailWidth = 10.dp
private val TailHeight = 14.dp
