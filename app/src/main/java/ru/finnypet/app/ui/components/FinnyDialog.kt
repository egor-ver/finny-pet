package ru.finnypet.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import ru.finnypet.app.ui.theme.Dimens

/**
 * Окно поверх экрана: подтверждение действия или его итог.
 *
 * Своё, а не AlertDialog: тому нужны две кнопки в ряд, а кнопки игры — во
 * всю ширину и столбиком, главная сверху, как везде (ТЗ 3.6 требует
 * единообразия). Содержимое прокручивается: при крупном системном шрифте
 * объяснение из контент-пака иначе вылезло бы за край.
 */
@Composable
fun FinnyDialog(
    title: String,
    onDismiss: () -> Unit,
    buttons: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) = FinnyDialog(
    title = { Text(text = title, style = MaterialTheme.typography.titleLarge) },
    onDismiss = onDismiss,
    buttons = buttons,
    content = content,
)

/** Заголовок своим составом — «Кошелёк сегодня» показывает сумму крупнее обычного [titleLarge]. */
@Composable
fun FinnyDialog(
    title: @Composable () -> Unit,
    onDismiss: () -> Unit,
    buttons: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(Dimens.CornerDialog),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = Dimens.CardShadowElevation,
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMedium),
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(Dimens.Space),
            ) {
                title()
                content()
                ButtonColumn(content = buttons)
            }
        }
    }
}
