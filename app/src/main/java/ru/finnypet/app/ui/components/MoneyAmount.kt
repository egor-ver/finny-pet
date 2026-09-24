package ru.finnypet.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.finnypet.app.R
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.ui.text.WordForm
import ru.finnypet.app.ui.text.wordFormOf
import ru.finnypet.app.ui.theme.Dimens

/**
 * Сумма в монетах.
 *
 * Кружок слева — это сама монета: сумма опознаётся не только цветом, но и
 * формой рядом с числом (ТЗ 3.6 запрещает передавать смысл одним цветом).
 *
 * Для чтения вслух склеивается в одну подпись со склонением: «одна монета»,
 * «две монеты», «пять монет». Иначе озвучка произнесёт «кружок, сорок».
 */
@Composable
fun MoneyAmount(
    amount: Coins,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleLarge,
) {
    val spoken = coinsText(amount)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSmall),
        modifier = modifier.clearAndSetSemantics { contentDescription = spoken },
    ) {
        Coin(style = style)
        Text(
            text = amount.amount.toString(),
            style = style,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * Склонение по числу: одна монета, две монеты, пять монет.
 *
 * Нужно везде, где число монет попадает внутрь фразы, а не рисуется рядом со
 * значком: подставить «монет» строкой нельзя, форму выбирает правило языка.
 */
@Composable
fun coinsText(amount: Coins): String = stringResource(
    when (wordFormOf(amount.amount)) {
        WordForm.ONE -> R.string.coins_one
        WordForm.FEW -> R.string.coins_few
        WordForm.MANY -> R.string.coins_many
    },
    amount.amount,
)

/**
 * Монета нарисована формой, а не символом: знаки валют есть не во всех
 * шрифтах, и на части устройств вместо монеты появился бы пустой
 * прямоугольник.
 *
 * Размер считается от размера текста рядом, поэтому монета растёт вместе
 * с системным увеличением шрифта и не вылезает за свой кружок (ТЗ 3.6).
 */
@Composable
private fun Coin(style: TextStyle) {
    val size = with(LocalDensity.current) { style.fontSize.toDp() }
    Box(
        modifier = Modifier
            .size(size)
            .background(MaterialTheme.colorScheme.secondary, CircleShape)
            .border(
                // На мелком тексте десятая доля схлопнулась бы в ноль,
                // и монета потеряла бы форму — единственный признак,
                // не зависящий от цвета (ТЗ 3.6).
                width = (size / 10).coerceAtLeast(1.5.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = CircleShape,
            ),
    )
}

/**
 * Сумма на подложке — для главного экрана, где баланс и накопления должны
 * читаться с одного взгляда (ТЗ 2.5.3).
 *
 * Подпись занимает всё свободное место и при нехватке ширины сокращается
 * многоточием: при системном увеличении шрифта длинная подпись иначе
 * вытолкнула бы саму сумму за край.
 */
@Composable
fun MoneyCard(
    label: String,
    amount: Coins,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSmall),
        modifier = modifier
            // Иначе озвучка произнесёт подпись и сумму двумя остановками,
            // и связь между ними потеряется.
            .semantics(mergeDescendants = true) {}
            .background(
                MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(Dimens.Corner),
            )
            .padding(horizontal = Dimens.Space, vertical = Dimens.SpaceMedium),
    ) {
        Label(text = label)
        MoneyAmount(amount = amount)
    }
}

@Composable
private fun RowScope.Label(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f),
    )
}
