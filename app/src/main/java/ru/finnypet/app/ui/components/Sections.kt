package ru.finnypet.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.ui.theme.Dimens

/**
 * Куски текста, из которых собраны разделы экранов.
 *
 * Живут здесь, а не в каждом экране по копии: прогресс, раздел взрослого и
 * копилка показывают одни и те же заголовки, пояснения и строки «подпись —
 * значение», и выглядеть они обязаны одинаково (ТЗ 3.6).
 */

/** Заголовок раздела внутри экрана. */
@Composable
fun Heading(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleMedium)
}

/** Пояснение под заголовком или вместо содержимого, когда его ещё нет. */
@Composable
fun Explanation(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Подпись слева, значение справа. Строка озвучивается целиком, а не по кускам. */
@Composable
fun LabelledLine(label: String, value: @Composable () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSmall),
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        value()
    }
}

/**
 * [style] меняет только раздел взрослого: там все значения одного размера
 * `titleMedium` (DESIGN_PLAN 3.11), а игровые экраны выделяют суммы крупнее.
 */
@Composable
fun LabelledLine(label: String, value: String, style: TextStyle = MaterialTheme.typography.bodyLarge) =
    LabelledLine(label) {
        Text(text = value, style = style)
    }

@Composable
fun LabelledLine(label: String, amount: Coins, style: TextStyle = MaterialTheme.typography.titleLarge) =
    LabelledLine(label) {
        MoneyAmount(amount = amount, style = style)
    }
