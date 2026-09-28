package ru.finnypet.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import ru.finnypet.app.ui.theme.Dimens

/**
 * Круглая кнопка «−» или «+» 48 dp для набора суммы — в стиле второстепенной:
 * белая, рамка и значок `primary` (DESIGN_PLAN 3.2, 3.7). Одна на ползунки
 * плана и окно суммы копилки: два рисунка одной кнопки разошлись бы видом.
 *
 * Суммы набираются кнопками, а не с клавиатуры: у семилетнего промах по
 * цифре ломает весь план, а лишний ноль превращает сорок монет в четыреста.
 * Подпись [description] говорит TalkBack, что делает кнопка; значок молчит.
 */
@Composable
fun StepButton(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    // Выключенная — серая, как выключенная второстепенная кнопка: бледно-голубая
    // «−» на нуле казалась сбоем, а не «меньше нельзя» (замечание владельца 28.09).
    OutlinedIconButton(
        onClick = onClick,
        enabled = enabled,
        border = BorderStroke(Dimens.ButtonBorderWidth, if (enabled) primary else disabledColor()),
        colors = IconButtonDefaults.outlinedIconButtonColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = primary,
            disabledContainerColor = MaterialTheme.colorScheme.surface,
            disabledContentColor = disabledColor(),
        ),
        modifier = modifier.size(Dimens.TouchTarget),
    ) {
        Icon(imageVector = icon, contentDescription = description)
    }
}
