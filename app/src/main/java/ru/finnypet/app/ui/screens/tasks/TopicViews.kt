package ru.finnypet.app.ui.screens.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ru.finnypet.app.domain.model.TaskTopic
import ru.finnypet.app.ui.components.colors
import ru.finnypet.app.ui.components.icon

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

private val PLATE_SIZE = 40.dp
