package ru.finnypet.app.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.ui.theme.Dimens
import ru.finnypet.app.ui.theme.FinnyTheme
import ru.finnypet.app.ui.theme.LocalAnimationsEnabled
import ru.finnypet.app.ui.theme.Motion

/**
 * Главное действие экрана.
 *
 * Плоская заливка не читалась как нажимаемая (диагноз DESIGN_PLAN, раздел 1,
 * №1 и №5) — нижняя грань `primaryDeep` даёт кнопке объём, а при нажатии
 * верхняя грань опускается на [Dimens.ButtonPressOffset] (DESIGN_PLAN 2.4).
 * Своя отрисовка вместо `Button` из Material: тому неоткуда взять вторую,
 * более тёмную грань снизу.
 *
 * Высота лицевой грани — не жёсткие 56 dp, а минимум [Dimens.ButtonHeight]:
 * при крупном системном шрифте текст переносится на вторую строку, и
 * кнопка растёт вместе с ним. Своя `Layout`, а не `Modifier.height`, —
 * нижняя грань должна повторять фактическую высоту верхней, а не
 * фиксированную; входящий `minHeight` (например, `Modifier.heightIn(min = …)`
 * с главного экрана, где кнопки в паре должны быть одной высоты) измерению
 * не мешает — своя разметка передаёт его дальше, а не обнуляет.
 *
 * [reward] — чип «+10» с монетой внутри кнопки (DESIGN_PLAN 3.1: кнопка
 * задания на главном), `null` — кнопка без чипа, как везде. Текст и чип
 * склеены в одну подпись для TalkBack: два отдельных узла озвучились бы
 * двумя остановками, а чип без подписи вообще не сказал бы ничего.
 */
@Composable
fun FinnyButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    // Уже у обычного текста хватает места на 24 dp с каждой стороны; переопределяют
    // только те, кому тесно — например, кнопки дня в узкой половине строки (Б21).
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    reward: Coins? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val topOffset by animateDpAsState(
        targetValue = if (pressed) Dimens.ButtonDepth - Dimens.ButtonPressOffset else 0.dp,
        animationSpec = if (LocalAnimationsEnabled.current) tween(Motion.QuickMs) else snap(),
        label = "buttonPress",
    )
    val shape = RoundedCornerShape(Dimens.CornerTile)
    val deepColor = FinnyTheme.palette.buttonDeep
    val faceColor = MaterialTheme.colorScheme.primary
    val onFaceColor = MaterialTheme.colorScheme.onPrimary
    val labelStyle = MaterialTheme.typography.labelLarge
    val spoken = reward?.let { "$text, ${coinsText(it)}" }

    Layout(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else DISABLED_ALPHA),
        content = {
            // Нижняя грань: видна полоской под верхней, даёт кнопке объём.
            // Размер ей задаёт не модификатор, а измерение ниже — она всегда
            // повторяет фактический размер верхней грани.
            Box(Modifier.clip(shape).background(deepColor))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .defaultMinSize(minHeight = Dimens.ButtonHeight)
                    .clip(shape)
                    .background(faceColor)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        enabled = enabled,
                        role = Role.Button,
                        onClick = onClick,
                    )
                    .padding(contentPadding)
                    // Чип рядом с текстом — свой узел для TalkBack, без общей
                    // подписи он озвучился бы отдельной немой остановкой. Без
                    // чипа узел остаётся как был — просто подпись кнопки.
                    .then(
                        if (spoken == null) {
                            Modifier
                        } else {
                            Modifier.semantics(mergeDescendants = true) { contentDescription = spoken }
                        },
                    ),
            ) {
                if (reward == null) {
                    Text(text = text, style = labelStyle, textAlign = TextAlign.Center, color = onFaceColor)
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSmall),
                    ) {
                        Text(text = text, style = labelStyle, color = onFaceColor)
                        RewardChip(reward = reward, contentColor = onFaceColor)
                    }
                }
            }
        },
    ) { measurables, constraints ->
        val (deep, face) = measurables
        // Входящий minHeight (heightIn с вызывающего экрана) передаём как
        // есть: обнуление стёрло бы требование к паре кнопок быть одной
        // высоты на главном экране. Верхняя граница остаётся снятой — текст
        // должен иметь возможность растить кнопку выше этого минимума.
        val facePlaceable = face.measure(constraints)
        val depthPx = Dimens.ButtonDepth.roundToPx()
        val deepPlaceable = deep.measure(Constraints.fixed(facePlaceable.width, facePlaceable.height))
        val offsetPx = topOffset.roundToPx()
        layout(facePlaceable.width, facePlaceable.height + depthPx) {
            deepPlaceable.placeRelative(0, depthPx)
            facePlaceable.placeRelative(0, offsetPx)
        }
    }
}

/** Второстепенное действие: отказаться, перенести, вернуться. */
@Composable
fun FinnySecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(Dimens.CornerTile),
        // Рамка толще и цветом primary: `outline` даёт на белом только 1,3:1,
        // граница второстепенной кнопки была бы почти не видна (DESIGN_PLAN 2.4).
        border = BorderStroke(Dimens.ButtonBorderWidth, MaterialTheme.colorScheme.primary),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
        ),
        contentPadding = contentPadding,
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

/** Чип награды внутри кнопки — монета из [MoneyAmount], а не отдельный рисунок. */
@Composable
private fun RewardChip(reward: Coins, contentColor: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceTiny),
        modifier = Modifier
            .clip(RoundedCornerShape(Dimens.CornerTile))
            .background(contentColor.copy(alpha = REWARD_CHIP_ALPHA))
            .padding(horizontal = Dimens.SpaceSmall, vertical = Dimens.SpaceTiny),
    ) {
        Coin(style = MaterialTheme.typography.labelLarge)
        // maxLines = 1: без него цифра при нехватке ширины переносится за
        // скругление чипа, а не остаётся числом (проверено на vivo V2111).
        Text(text = "+${reward.amount}", style = MaterialTheme.typography.labelLarge, color = contentColor, maxLines = 1)
    }
}

private const val DISABLED_ALPHA = 0.5f
private const val REWARD_CHIP_ALPHA = 0.18f
