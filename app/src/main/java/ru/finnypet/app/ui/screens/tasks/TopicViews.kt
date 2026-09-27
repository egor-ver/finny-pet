package ru.finnypet.app.ui.screens.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ru.finnypet.app.domain.model.TaskTopic
import ru.finnypet.app.ui.components.Coin
import ru.finnypet.app.ui.components.colors
import ru.finnypet.app.ui.components.icon
import ru.finnypet.app.ui.theme.Dimens

/**
 * Иконка темы на круглой тарелке — общая для списка заданий и вступления
 * (DESIGN_PLAN 3.8, 3.9). Значок тоном текста темы, а не заливкой: на
 * светлой тарелке заливка «Планирования» даёт меньше 3:1. [plate] меняют
 * на карточке задания — она сама цвета темы, и тарелка того же цвета
 * исчезла бы. Для TalkBack молчит: рядом всегда название темы.
 */
@Composable
internal fun TopicPlate(
    topic: TaskTopic,
    modifier: Modifier = Modifier,
    plate: Color = topic.colors.container,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(PLATE_SIZE)
            .clip(CircleShape)
            .background(plate),
    ) {
        Icon(imageVector = topic.icon, contentDescription = null, tint = topic.colors.text)
    }
}

/**
 * Чип награды с монетой: «+10», «Сегодня: +10», «до +10». Число без слова
 * «монет» — его говорит монета; подпись для TalkBack задаёт тот, кто
 * показывает чип, одной фразой со склонением.
 */
@Composable
internal fun CoinChip(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surface,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceTiny),
        modifier = modifier
            .clip(RoundedCornerShape(Dimens.CornerTile))
            .background(color)
            .padding(horizontal = Dimens.SpaceMedium, vertical = Dimens.SpaceTiny),
    ) {
        Coin(style = MaterialTheme.typography.labelLarge)
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private val PLATE_SIZE = 40.dp
