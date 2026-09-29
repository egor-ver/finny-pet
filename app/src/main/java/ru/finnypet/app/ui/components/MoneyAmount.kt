package ru.finnypet.app.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.finnypet.app.R
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.ui.text.WordForm
import ru.finnypet.app.ui.text.wordFormOf
import ru.finnypet.app.ui.theme.CoinColors
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
 * «Не хватает 2 монет»: после «не хватает» число в родительном падеже, и
 * общая строка «2 монеты» ([coinsText]) здесь была бы ошибкой. Одна строка
 * на нехватку в магазине и на недобор копилки в итогах дня.
 */
@StringRes
fun shortageLine(shortfall: Coins): Int = when (wordFormOf(shortfall.amount)) {
    WordForm.ONE -> R.string.shortage_one
    WordForm.FEW -> R.string.shortage_few
    WordForm.MANY -> R.string.shortage_many
}

/**
 * «Сверх плана на 1 монету»: после «на» — винительный падеж, а [coinsText]
 * дал бы «на 1 монета» (замечено в F6).
 */
@StringRes
fun overPlanLine(gap: Coins): Int = when (wordFormOf(gap.amount)) {
    WordForm.ONE -> R.string.budget_status_over_one
    WordForm.FEW -> R.string.budget_status_over_few
    WordForm.MANY -> R.string.budget_status_over_many
}

/**
 * Чип с монетой: «+10», «Сегодня: +10», «−8». Число без слова «монет» — его
 * говорит монета; подпись для TalkBack задаёт тот, кто показывает чип, одной
 * фразой со склонением. Общий для заданий и магазина (DESIGN_PLAN 3.5, 3.8,
 * 3.9): второй рисунок одной и той же пилюли разошёлся бы в отступах.
 */
@Composable
fun CoinChip(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surface,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceTiny),
        modifier = modifier
            .clip(RoundedCornerShape(Dimens.CornerTile))
            .background(color)
            .padding(horizontal = Dimens.SpaceMedium, vertical = Dimens.SpaceTiny),
    ) {
        Coin(style = MaterialTheme.typography.labelLarge)
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

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
 *
 * Не `private`: тот же рисунок нужен в чипе награды у [FinnyButton]
 * (DESIGN_PLAN 3.1) и в [CoinChip] — заводить второй кружок монеты ради
 * видимости смысла нет.
 */
@Composable
internal fun Coin(style: TextStyle) {
    Coin(size = with(LocalDensity.current) { style.fontSize.toDp() })
}

/**
 * Монета заданного размера — там, где размер задаёт не текст рядом, а сам
 * экран: ручка ползунка плана и живой счётчик остатка (DESIGN_PLAN 3.2).
 */
@Composable
internal fun Coin(size: Dp, modifier: Modifier = Modifier) {
    val coin = FinnyTheme.palette.coin
    Canvas(modifier = modifier.size(size)) {
        drawCoin(center = center, diameter = this.size.minDimension, colors = coin)
    }
}

/**
 * Рисунок монеты для чужих `Canvas` — летящие монеты и монеты в банке
 * копилки должны выглядеть так же, как монета у числа.
 */
internal fun DrawScope.drawCoin(center: Offset, diameter: Float, colors: CoinColors) {
    // На мелком тексте десятая доля схлопнулась бы в ноль, и кант
    // потерял бы толщину — единственный признак, не зависящий от цвета.
    val edge = (diameter / 10).coerceAtLeast(1.5.dp.toPx())
    val radius = (diameter - edge) / 2
    drawCircle(color = colors.shadow, radius = radius, center = center)
    drawCircle(color = colors.face, radius = radius, center = center - Offset(0f, radius * 0.12f))
    drawCircle(color = colors.edge, radius = radius, center = center, style = Stroke(width = edge))
    drawOval(
        color = colors.highlight,
        topLeft = center + Offset(-radius * 0.55f, -radius * 0.6f),
        size = Size(radius * 0.5f, radius * 0.32f),
    )
}
