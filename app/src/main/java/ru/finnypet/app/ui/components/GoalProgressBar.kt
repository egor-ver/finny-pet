package ru.finnypet.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import ru.finnypet.app.R
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.ui.theme.Dimens

/**
 * Доля пути к цели — одна формула на все экраны, чтобы полоса в копилке и
 * полоса в прогрессе не разошлись.
 *
 * Цена в домене больше нуля, но экран не должен падать и на выдуманной.
 */
fun goalFraction(saved: Coins, price: Coins): Float =
    if (price.amount == 0) 1f else (saved.amount.toFloat() / price.amount).coerceAtMost(1f)

/**
 * Цель и путь к ней: название, полоса и сколько осталось.
 *
 * Одна и та же на главном экране и в прогрессе — ребёнок видит цель в двух
 * местах, и она не должна выглядеть по-разному. Экран копилки показывает цели
 * иначе: там из них выбирают, а здесь на них смотрят.
 */
@Composable
fun ColumnScope.GoalProgressBar(title: String, saved: Coins, price: Coins) {
    Text(text = title, style = MaterialTheme.typography.titleMedium)
    ProgressLine(
        fraction = goalFraction(saved, price),
        // Полосу озвучка иначе пропустит: цифры внутри неё нет, а смысл есть.
        contentDescription = stringResource(
            R.string.main_goal_progress,
            title,
            saved.amount,
            price.amount,
        ),
    )
    if (saved.covers(price)) {
        Text(
            text = stringResource(R.string.main_goal_reached),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    } else {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSmall),
            modifier = Modifier.semantics(mergeDescendants = true) {},
        ) {
            Text(
                text = stringResource(R.string.main_goal_left),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MoneyAmount(
                amount = saved.shortfallTo(price),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}
