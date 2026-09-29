package ru.finnypet.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ru.finnypet.app.R
import ru.finnypet.app.domain.model.Change
import ru.finnypet.app.domain.model.PetStatKind
import ru.finnypet.app.ui.theme.Dimens

/**
 * Насколько сдвинулся показатель питомца: «Радость +15».
 *
 * Показывается настоящее изменение, а не обещанное: у верхней границы
 * показатель не растёт, и говорить «+15» при неподвижной полосе значило бы
 * обманывать (ТЗ 2.5.9 требует объяснять последствия честно).
 *
 * Знак пишется словом-символом, а не только цветом: ТЗ 3.6 запрещает
 * передавать смысл одним цветом.
 */
@Composable
fun StatChangeLine(change: Change.PetStat, modifier: Modifier = Modifier) {
    Line(kind = change.kind, delta = change.delta, modifier = modifier)
}

/**
 * Влияние на показатель чипом «[яблоко] Еда +25» (DESIGN_PLAN 3.5): на плитке
 * товара, в окне покупки и в облачке после неё. Тон — направления, которое
 * пополняет показатель (DESIGN_PLAN 2.1), но смысл несут иконка и слово, а не
 * цвет (ТЗ 3.6). Иконка для TalkBack молчит — он читает «Еда +25».
 *
 * Тот же знак и те же слова, что у [StatChangeLine]: ребёнок узнаёт запись
 * «показатель — сдвиг» и в витрине, и в итогах задания.
 */
@Composable
fun StatChip(kind: PetStatKind, delta: Int, modifier: Modifier = Modifier) {
    val direction = kind.direction
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceTiny),
        modifier = modifier
            .clip(RoundedCornerShape(Dimens.CornerTile))
            .background(direction.container)
            .padding(horizontal = Dimens.SpaceSmall, vertical = Dimens.SpaceTiny),
    ) {
        // Тоном текста, а не заливки: на светлом контейнере заливка бледнее (DESIGN_PLAN 2.1).
        Icon(imageVector = kind.icon, contentDescription = null, tint = direction.color, modifier = Modifier.size(STAT_CHIP_ICON))
        Text(text = statChangeText(kind, delta), style = MaterialTheme.typography.labelMedium, color = direction.color)
    }
}

@Composable
private fun Line(kind: PetStatKind, delta: Int, modifier: Modifier) {
    Text(
        text = statChangeText(kind, delta),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/** «Еда +15» — и для чипа, и для подписи плитки товара в TalkBack. */
@Composable
fun statChangeText(kind: PetStatKind, delta: Int): String {
    val signed = if (delta > 0) "+$delta" else delta.toString()
    return stringResource(R.string.stat_change, stringResource(kind.label), signed)
}

private val STAT_CHIP_ICON = 18.dp
