package ru.finnypet.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import ru.finnypet.app.domain.model.SpendCategory

/**
 * Картинка товара или цели — эмодзи из контента (AD-11) на круглой «тарелке»
 * 56 dp цвета контейнера направления [category] (DESIGN_PLAN 2.3): тарелка
 * выравнивает разные эмодзи прошивок в один стиль.
 *
 * Для TalkBack молчит: рядом всегда название, а описание эмодзи («миска с
 * ложкой») звучало бы вместо «каша» и путало ребёнка.
 */
@Composable
fun ItemIcon(icon: String, category: SpendCategory, modifier: Modifier = Modifier) {
    ItemIcon(icon = icon, plate = category.container, modifier = modifier)
}

/**
 * Та же тарелка другого цвета — на полке задания она цвета темы, а не
 * направления: направление там и есть ответ, который ищет ребёнок (DESIGN_PLAN 3.8).
 */
@Composable
fun ItemIcon(icon: String, plate: Color, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(PLATE_SIZE)
            .clip(CircleShape)
            .background(plate),
    ) {
        Text(
            text = icon,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

private val PLATE_SIZE = 56.dp
