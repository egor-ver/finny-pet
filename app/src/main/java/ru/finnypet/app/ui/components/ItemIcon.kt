package ru.finnypet.app.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics

/**
 * Картинка товара или цели — эмодзи из контента (AD-11).
 *
 * Для TalkBack молчит: рядом всегда название, а описание эмодзи («миска с
 * ложкой») звучало бы вместо «каша» и путало ребёнка.
 */
@Composable
fun ItemIcon(icon: String, modifier: Modifier = Modifier) {
    Text(
        text = icon,
        style = MaterialTheme.typography.headlineMedium,
        modifier = modifier.clearAndSetSemantics {},
    )
}
