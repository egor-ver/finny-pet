package ru.finnypet.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.finnypet.app.R
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.ui.text.WordForm
import ru.finnypet.app.ui.text.wordFormOf
import ru.finnypet.app.ui.theme.Dimens
import ru.finnypet.app.ui.theme.FinnyTheme

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
 *
 * Грань сдвинута вверх относительно тени того же радиуса — снизу остаётся
 * полумесяц тени, дающий монете объём (DESIGN_PLAN 2.5).
 */
@Composable
private fun Coin(style: TextStyle) {
    val size = with(LocalDensity.current) { style.fontSize.toDp() }
    val coin = FinnyTheme.palette.coin
    Canvas(modifier = Modifier.size(size)) {
        // На мелком тексте десятая доля схлопнулась бы в ноль, и кант
        // потерял бы толщину — единственный признак, не зависящий от цвета.
        val edge = (this.size.minDimension / 10).coerceAtLeast(1.5.dp.toPx())
        val radius = (this.size.minDimension - edge) / 2
        drawCircle(color = coin.shadow, radius = radius, center = center)
        drawCircle(color = coin.face, radius = radius, center = center - Offset(0f, radius * 0.12f))
        drawCircle(color = coin.edge, radius = radius, center = center, style = Stroke(width = edge))
        drawOval(
            color = coin.highlight,
            topLeft = center + Offset(-radius * 0.55f, -radius * 0.6f),
            size = Size(radius * 0.5f, radius * 0.32f),
        )
    }
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
    // Иначе озвучка произнесёт подпись и сумму двумя остановками, и связь
    // между ними потеряется.
    FinnyCard(modifier = modifier.semantics(mergeDescendants = true) {}) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSmall),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Label(text = label)
            MoneyAmount(amount = amount)
        }
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
