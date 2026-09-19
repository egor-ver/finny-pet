package ru.finnypet.app.ui.components

import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import ru.finnypet.app.ui.theme.Dimens

/**
 * Кнопка «плюс» или «минус» для набора суммы.
 *
 * Суммы набираются кнопками, а не с клавиатуры: у семилетнего промах по
 * цифре ломает весь план, а лишний ноль превращает сорок монет в четыреста.
 * Не меньше 48 dp по обеим сторонам (ТЗ 3.6).
 */
@Composable
fun StepButton(
    symbol: String,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .defaultMinSize(minWidth = Dimens.TouchTarget, minHeight = Dimens.TouchTarget)
            .semantics { contentDescription = description },
    ) {
        // Знак скрыт от озвучки: подпись кнопки уже говорит, что она делает,
        // а «плюс» отдельной остановкой только мешает. Прятать через
        // clearAndSetSemantics на самой кнопке нельзя — вместе со знаком
        // пропадёт и признак «недоступна».
        Text(
            text = symbol,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}
