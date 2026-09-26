package ru.finnypet.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import ru.finnypet.app.domain.model.SpendCategory
import ru.finnypet.app.ui.theme.Dimens

/**
 * Одна иконка на направление везде (раздел 8 плана): нужное — миска,
 * желаемое — мяч, копилка — копилка.
 */
val SpendCategory.icon: String
    get() = when (this) {
        SpendCategory.MANDATORY -> "🥣"
        SpendCategory.OPTIONAL -> "⚽"
        SpendCategory.SAVINGS -> "🐷"
    }

/** Один цвет на направление — из темы, поэтому работает и в тёмной: зелёный, оранжевый, фиолетовый. */
val SpendCategory.color: Color
    @Composable get() = when (this) {
        SpendCategory.MANDATORY -> MaterialTheme.colorScheme.primary
        SpendCategory.OPTIONAL -> MaterialTheme.colorScheme.secondary
        SpendCategory.SAVINGS -> MaterialTheme.colorScheme.tertiary
    }

/**
 * Направление иконкой, цветом и словом. Цвет никогда не единственный
 * признак (ТЗ 3.6): TalkBack читает слово, иконка для него молчит.
 */
@Composable
fun CategoryLabel(
    category: SpendCategory,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceTiny),
        modifier = modifier,
    ) {
        Text(
            text = category.icon,
            style = style,
            modifier = Modifier.clearAndSetSemantics {},
        )
        Text(
            text = stringResource(category.label),
            style = style,
            fontWeight = FontWeight.SemiBold,
            color = category.color,
        )
    }
}
