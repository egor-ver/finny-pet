package ru.finnypet.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import ru.finnypet.app.R
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.SpendCategory
import ru.finnypet.app.ui.theme.Dimens

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
 * Соблюдение плана показано не цветом, а словами «по плану» и «потрачено» с
 * числами: ТЗ 3.6 запрещает передавать смысл одним цветом.
 */
@Composable
private fun ComparisonRow(line: BudgetLine) {
    val title = stringResource(line.category.label)
    val spoken = stringResource(
        R.string.budget_line,
        title,
        line.planned.amount,
        line.actual.amount,
    )
    Column(
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceTiny),
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = spoken }
            .background(
                MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(Dimens.Corner),
            )
            .padding(horizontal = Dimens.SpaceMedium, vertical = Dimens.SpaceSmall),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (line.followed) FontWeight.SemiBold else FontWeight.Normal,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(Dimens.Space),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Labelled(label = stringResource(R.string.budget_planned), amount = line.planned)
            Labelled(label = stringResource(R.string.budget_fact), amount = line.actual)
        }
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
