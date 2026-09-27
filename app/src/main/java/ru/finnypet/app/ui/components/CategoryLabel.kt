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
import ru.finnypet.app.ui.theme.FinnyTheme

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

/**
 * Один цвет на направление — из темы, поэтому работает и в тёмной: зелёный,
 * оранжевый, фиолетовый.
 *
 * Не `colorScheme.primary`: тот теперь синий цвет действия (кнопки, выбор),
 * общий для всего приложения, а не «Нужного» — иначе направление снова
 * незаметно совпало бы цветом с кнопкой (DESIGN_PLAN 2.1).
 */
val SpendCategory.color: Color
    @Composable get() = when (this) {
        SpendCategory.MANDATORY -> FinnyTheme.palette.need.text
        SpendCategory.OPTIONAL -> FinnyTheme.palette.want.text
        SpendCategory.SAVINGS -> FinnyTheme.palette.save.text
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
