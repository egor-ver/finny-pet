package ru.finnypet.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import ru.finnypet.app.ui.theme.Dimens

/**
 * Карточка во всю ширину на скруглённой подложке. С [onClick] нажимается
 * целиком; что она нажимается, должна сказать подпись внутри (ТЗ 3.6).
 */
@Composable
fun FinnyCard(
    color: Color = MaterialTheme.colorScheme.surfaceVariant,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSmall),
        modifier = Modifier
            .fillMaxWidth()
            // Скругление до нажатия: иначе отклик выходит за края подложки.
            .clip(RoundedCornerShape(Dimens.Corner))
            .background(color)
            .then(if (onClick == null) Modifier else Modifier.clickable(role = Role.Button, onClick = onClick))
            .padding(horizontal = Dimens.Space, vertical = Dimens.SpaceMedium),
        content = content,
    )
}
