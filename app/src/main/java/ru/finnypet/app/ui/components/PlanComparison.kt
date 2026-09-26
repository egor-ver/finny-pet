package ru.finnypet.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import ru.finnypet.app.R
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.SpendCategory
import ru.finnypet.app.ui.theme.Dimens
import kotlin.math.abs

/** Строка сравнения: сколько задумали и сколько вышло на самом деле. */
data class BudgetLine(
    val category: SpendCategory,
    val planned: Coins,
    val actual: Coins,
    val followed: Boolean,
)

/**
 * План против факта по трём направлениям и обе суммы итогом.
 *
 * Один и тот же вид на экране плана и в итогах дня: ребёнок видит сравнение
 * дважды — пока день идёт и когда он закончился, — и оно не должно выглядеть
 * по-разному.
 */
@Composable
fun ColumnScope.PlanComparison(
    lines: List<BudgetLine>,
    planTotal: Coins,
    factTotal: Coins,
) {
    lines.forEach { line -> ComparisonRow(line = line) }
    MoneyCard(label = stringResource(R.string.budget_planned), amount = planTotal)
    MoneyCard(label = stringResource(R.string.budget_fact), amount = factTotal)
}

/**
 * Соблюдение плана сказано словами — «по плану», «не хватает», «сверх плана»,
 * — а не цветом или жирностью: ТЗ 3.6 запрещает передавать смысл только видом.
 */
@Composable
private fun ComparisonRow(line: BudgetLine) {
    val title = stringResource(line.category.label)
    val factLabel = stringResource(
        if (line.category == SpendCategory.SAVINGS) R.string.budget_saved else R.string.budget_fact,
    )
    val status = statusOf(line)
    val spoken = stringResource(
        R.string.budget_line,
        title,
        line.planned.amount,
        factLabel.lowercase(),
        line.actual.amount,
        status,
    )
    FinnyCard(modifier = Modifier.clearAndSetSemantics { contentDescription = spoken }) {
        CategoryLabel(category = line.category, style = MaterialTheme.typography.titleMedium)
        Row(
            horizontalArrangement = Arrangement.spacedBy(Dimens.Space),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Labelled(label = stringResource(R.string.budget_planned), amount = line.planned)
            Labelled(label = factLabel, amount = line.actual)
        }
        Text(
            text = status,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Траты не соблюдены, только когда потрачено больше плана (R3), — это «сверх
 * плана». Копилку, наоборот, надо добрать до плана — там «не хватает».
 */
@Composable
private fun statusOf(line: BudgetLine): String {
    val gap = Coins(abs(line.actual.amount - line.planned.amount))
    return when {
        line.followed -> stringResource(R.string.budget_status_ok)
        line.category == SpendCategory.SAVINGS -> stringResource(R.string.budget_status_short, coinsText(gap))
        else -> stringResource(R.string.budget_status_over, coinsText(gap))
    }
}

@Composable
private fun Labelled(label: String, amount: Coins) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceTiny),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        MoneyAmount(amount = amount, style = MaterialTheme.typography.bodyLarge)
    }
}
