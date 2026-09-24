package ru.finnypet.app.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import ru.finnypet.app.R
import ru.finnypet.app.domain.model.Change
import ru.finnypet.app.domain.model.PetEffect
import ru.finnypet.app.domain.model.PetStatKind

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
 * Обещанное товаром или заданием влияние — до того, как оно применилось.
 *
 * Отличается от [StatChangeLine] по смыслу, но не по виду: ребёнку важно
 * узнавать одну и ту же запись «показатель — сдвиг» в витрине и в итогах.
 */
@Composable
fun StatEffectLine(effect: PetEffect, modifier: Modifier = Modifier) {
    Line(kind = effect.stat, delta = effect.delta, modifier = modifier)
}

@Composable
private fun Line(kind: PetStatKind, delta: Int, modifier: Modifier) {
    val signed = if (delta > 0) "+$delta" else delta.toString()
    Text(
        text = stringResource(R.string.stat_change, stringResource(kind.label), signed),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}
