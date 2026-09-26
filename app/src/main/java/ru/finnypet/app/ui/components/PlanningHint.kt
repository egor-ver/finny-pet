package ru.finnypet.app.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import ru.finnypet.app.R

/**
 * Пока день планируется, тратить, откладывать и проходить задания нельзя —
 * и ребёнку нужен не запрет, а дорога: карточка с объяснением и кнопкой
 * прямо в план (ТЗ 3.4 запрещает тупики). Одна на магазин, копилку и задания.
 */
@Composable
fun PlanningHint(
    text: String,
    onPlan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FinnyCard(color = MaterialTheme.colorScheme.secondaryContainer, modifier = modifier) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        FinnyButton(text = stringResource(R.string.budget_action_plan), onClick = onPlan)
    }
}
