package ru.finnypet.app.ui.screens.budget

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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.finnypet.app.R
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.SpendCategory
import ru.finnypet.app.ui.components.ButtonColumn
import ru.finnypet.app.ui.components.FinnyButton
import ru.finnypet.app.ui.components.FinnyScaffold
import ru.finnypet.app.ui.components.MoneyAmount
import ru.finnypet.app.ui.components.MoneyCard
import ru.finnypet.app.ui.components.PlanComparison
import ru.finnypet.app.ui.components.PlanEditor
import ru.finnypet.app.ui.components.label
import ru.finnypet.app.ui.theme.Dimens

/**
 * План личного бюджета (ТЗ 2.5.5).
 *
 * До подтверждения ребёнок распределяет доступную сумму по трём направлениям и
 * видит остаток. После подтверждения тот же экран показывает, как план сходится
 * с настоящими тратами.
 */
@Composable
fun BudgetScreen(
    onBack: () -> Unit,
    viewModel: BudgetViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    BudgetContent(
        state = state,
        onBack = onBack,
        onAdd = viewModel::add,
        onRemove = viewModel::remove,
        onConfirm = viewModel::confirm,
        onRetry = viewModel::retry,
    )
}

@Composable
fun BudgetContent(
    state: BudgetState,
    onBack: () -> Unit,
    onAdd: (SpendCategory) -> Unit = {},
    onRemove: (SpendCategory) -> Unit = {},
    onConfirm: () -> Unit = {},
    onRetry: () -> Unit = {},
) {
    when (state) {
        BudgetState.Loading -> Screen(onBack = onBack) {}

        BudgetState.Failed -> Screen(
            onBack = onBack,
            bottomBar = {
                ButtonColumn {
                    FinnyButton(text = stringResource(R.string.action_retry), onClick = onRetry)
                }
            },
        ) {
            Text(
                text = stringResource(R.string.budget_failed),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
            )
        }

        is BudgetState.Planning -> Planning(
            state = state,
            onBack = onBack,
            onAdd = onAdd,
            onRemove = onRemove,
            onConfirm = onConfirm,
        )

        is BudgetState.Started -> Started(state = state, onBack = onBack)
    }
}

@Composable
private fun Screen(
    onBack: () -> Unit,
    bottomBar: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    FinnyScaffold(
        title = stringResource(R.string.budget_title),
        onBack = onBack,
        bottomBar = bottomBar,
        spacing = Dimens.SpaceMedium,
        content = content,
    )
}

@Composable
private fun Planning(
    state: BudgetState.Planning,
    onBack: () -> Unit,
    onAdd: (SpendCategory) -> Unit,
    onRemove: (SpendCategory) -> Unit,
    onConfirm: () -> Unit,
) {
    Screen(
        onBack = onBack,
        bottomBar = {
            ButtonColumn {
                if (state.needsGoal) {
                    Text(
                        text = stringResource(R.string.budget_needs_goal),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                FinnyButton(
                    text = stringResource(R.string.budget_confirm),
                    onClick = onConfirm,
                    enabled = state.canConfirm,
                )
            }
        },
    ) {
        MoneyCard(
            label = stringResource(R.string.budget_available),
            amount = state.available,
        )

        PlanEditor(
            plan = state.plan,
            available = state.available,
            remainder = state.remainder,
            overBy = state.overBy,
            canAdd = state.canAdd(),
            canRemove = state::canRemove,
            onAdd = onAdd,
            onRemove = onRemove,
        )

        Text(
            text = stringResource(R.string.budget_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Started(state: BudgetState.Started, onBack: () -> Unit) {
    Screen(
        onBack = onBack,
    ) {
        Text(
            text = stringResource(R.string.budget_started),
            style = MaterialTheme.typography.bodyLarge,
        )

        PlanComparison(
            lines = state.lines,
            planTotal = state.planTotal,
            factTotal = state.factTotal,
        )
    }
}

