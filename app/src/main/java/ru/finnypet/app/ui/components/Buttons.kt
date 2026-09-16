package ru.finnypet.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import ru.finnypet.app.ui.theme.Dimens

/**
 * Главное действие экрана.
 *
 * Высота не опускается ниже 48 dp: ТЗ 3.6 требует, чтобы по элементу было
 * удобно попасть ребёнку. Ограничение стоит здесь, а не на каждом экране,
 * чтобы его нельзя было случайно потерять.
 */
@Composable
fun FinnyButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(Dimens.Corner),
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = Dimens.TouchTarget),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = Dimens.SpaceSmall),
        )
    }
}

/** Второстепенное действие: отказаться, перенести, вернуться. */
@Composable
fun FinnySecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(Dimens.Corner),
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = Dimens.TouchTarget),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = Dimens.SpaceSmall),
        )
    }
}

/**
 * Столбик кнопок внизу экрана.
 *
 * Главное действие всегда сверху, второстепенные под ним — порядок один и
 * тот же на всех экранах, чтобы ребёнку не приходилось искать заново.
 */
@Composable
fun ButtonColumn(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMedium),
        modifier = modifier.fillMaxWidth(),
    ) {
        content()
    }
}
