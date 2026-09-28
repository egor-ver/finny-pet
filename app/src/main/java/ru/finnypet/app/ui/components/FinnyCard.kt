package ru.finnypet.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import ru.finnypet.app.ui.theme.Dimens

/**
 * Карточка во всю ширину на белой подложке с лёгкой тенью (DESIGN_PLAN 2.4):
 * на кремовом фоне это даёт глубину, которой раньше не было ни у одной
 * плашки. С [onClick] нажимается целиком; что она нажимается, должна
 * сказать подпись внутри (ТЗ 3.6).
 *
 * [color] переопределяют там, где карточка — цветной контейнер направления
 * (план, итоги), а не обычная белая плашка.
 *
 * [enabled] и [onClickLabel] нужны только при [onClick] — строке выбора,
 * которая может быть временно недоступна ([SavingsScreen] `GoalRow`), или
 * действию, для которого TalkBack должен назвать не описание, а команду
 * ([ProgressScreen] `Tile` — термин против задания).
 */
@Composable
fun FinnyCard(
    color: Color = MaterialTheme.colorScheme.surface,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    onClickLabel: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(Dimens.CornerCard)
    val shadowColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
    Column(
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSmall),
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = Dimens.CardShadowElevation,
                shape = shape,
                ambientColor = shadowColor,
                spotColor = shadowColor,
            )
            // Скругление до нажатия: иначе отклик выходит за края подложки.
            .clip(shape)
            .background(color)
            .then(
                if (onClick == null) {
                    Modifier
                } else {
                    Modifier.clickable(enabled = enabled, onClickLabel = onClickLabel, role = Role.Button, onClick = onClick)
                },
            )
            .padding(horizontal = Dimens.Space, vertical = Dimens.SpaceMedium),
        content = content,
    )
}

/**
 * Белая плитка с тенью, как карточка; отмеченная (выбранный вариант, товар в
 * корзине, аксессуар совы) — рамкой `primary`. Серо-зелёная заливка ушла из
 * карточек совсем (DESIGN_PLAN 2.4).
 *
 * [markColor] меняет магазин: там рамка значит не «выбрано», а «нужно сейчас»,
 * и она цвета «Нужного», как чип рядом (DESIGN_PLAN 3.5).
 */
@Composable
fun Modifier.tile(marked: Boolean, markColor: Color = MaterialTheme.colorScheme.primary): Modifier {
    val shape = RoundedCornerShape(Dimens.CornerTile)
    val shadowColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
    return this
        .shadow(elevation = Dimens.CardShadowElevation, shape = shape, ambientColor = shadowColor, spotColor = shadowColor)
        .clip(shape)
        .background(MaterialTheme.colorScheme.surface)
        .then(if (marked) Modifier.border(Dimens.ButtonBorderWidth, markColor, shape) else Modifier)
}
