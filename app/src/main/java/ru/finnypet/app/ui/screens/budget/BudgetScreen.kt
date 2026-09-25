package ru.finnypet.app.ui.screens.budget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.finnypet.app.R
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.SpendCategory
import ru.finnypet.app.ui.components.BudgetLine
import ru.finnypet.app.ui.components.ButtonColumn
import ru.finnypet.app.ui.components.FinnyButton
import ru.finnypet.app.ui.components.FinnyCard
import ru.finnypet.app.ui.components.FinnyDialog
import ru.finnypet.app.ui.components.FinnyScaffold
import ru.finnypet.app.ui.components.FinnySecondaryButton
import ru.finnypet.app.ui.components.MoneyAmount
import ru.finnypet.app.ui.components.Owl
import ru.finnypet.app.ui.components.PlanEditor
import ru.finnypet.app.ui.components.ProgressLine
import ru.finnypet.app.ui.components.coinsText
import ru.finnypet.app.ui.components.color
import ru.finnypet.app.ui.components.icon
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
    onShop: () -> Unit,
    onSavings: () -> Unit,
    viewModel: BudgetViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    BudgetContent(
        state = state,
        onBack = onBack,
        onShop = onShop,
        onSavings = onSavings,
        onSet = viewModel::set,
        onConfirm = viewModel::confirm,
        onRetry = viewModel::retry,
    )
}

@Composable
fun BudgetContent(
    state: BudgetState,
    onBack: () -> Unit,
    onShop: () -> Unit = {},
    onSavings: () -> Unit = {},
    onSet: (SpendCategory, Coins) -> Unit = { _, _ -> },
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
            onSet = onSet,
            onConfirm = onConfirm,
            onSavings = onSavings,
        )

        is BudgetState.Started -> Started(state = state, onBack = onBack, onShop = onShop)
    }
}

@Composable
private fun Screen(
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    FinnyScaffold(
        title = stringResource(R.string.budget_title),
        onBack = onBack,
        actions = actions,
        bottomBar = bottomBar,
        spacing = Dimens.SpaceMedium,
        content = content,
    )
}

@Composable
private fun Planning(
    state: BudgetState.Planning,
    onBack: () -> Unit,
    onSet: (SpendCategory, Coins) -> Unit,
    onConfirm: () -> Unit,
    onSavings: () -> Unit,
) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    if (confirming) {
        ConfirmDialog(
            savings = state.plan.savings,
            onConfirm = {
                confirming = false
                onConfirm()
            },
            onCancel = { confirming = false },
        )
    }

    Screen(
        onBack = onBack,
        // Кошелёк в шапке: весь он и раскладывается (R5), а место под банки дорого.
        actions = {
            Box(modifier = Modifier.padding(end = Dimens.Space)) { MoneyAmount(amount = state.available) }
        },
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
                    onClick = { confirming = true },
                    enabled = state.canConfirm,
                )
                if (state.plan.savings > Coins.ZERO) {
                    Text(
                        text = stringResource(R.string.budget_to_savings, coinsText(state.plan.savings)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    ) {
        // Сова отвечает на каждое движение ползунка: последствие плана видно
        // до решения, а не только вечером (ТЗ 2.2).
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSmall),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Owl(look = state.owl, size = 88.dp)
            Box(modifier = Modifier.weight(1f)) {
                FinnyCard {
                    Text(text = state.phrase, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }

        PlanEditor(
            plan = state.plan,
            available = state.available,
            remainder = state.remainder,
            overBy = state.overBy,
            onSet = onSet,
            hints = state.hints,
            onChooseGoal = onSavings.takeUnless { state.hasGoal },
            mandatoryCover = state.mandatoryCover,
        )

        // Остаток — не ошибка: он переходит на завтра (R5).
        if (state.remainder > Coins.ZERO) {
            Text(
                text = stringResource(R.string.budget_left_for_tomorrow, coinsText(state.remainder)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Подтверждение — отдельным окном: после него план не меняется до вечера,
 * а доля копилки сразу уходит на цель (R6). ТЗ 3.6 требует подтверждения
 * действий, заметно меняющих прогресс.
 */
@Composable
private fun ConfirmDialog(savings: Coins, onConfirm: () -> Unit, onCancel: () -> Unit) {
    FinnyDialog(
        title = stringResource(R.string.budget_confirm_title),
        onDismiss = onCancel,
        buttons = {
            FinnyButton(text = stringResource(R.string.budget_confirm_yes), onClick = onConfirm)
            FinnySecondaryButton(text = stringResource(R.string.action_not_now), onClick = onCancel)
        },
    ) {
        Text(text = stringResource(R.string.budget_confirm_body), style = MaterialTheme.typography.bodyLarge)
        if (savings > Coins.ZERO) {
            Text(
                text = stringResource(R.string.budget_to_savings, coinsText(savings)),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

/**
 * План после подтверждения: в каждой банке сколько уже ушло и сколько
 * осталось (раздел 8 плана). Полоса — доля плана, которая потрачена; число
 * рядом словами, цвет — не единственный признак (ТЗ 3.6).
 */
@Composable
private fun Started(state: BudgetState.Started, onBack: () -> Unit, onShop: () -> Unit) {
    Screen(
        onBack = onBack,
        bottomBar = {
            ButtonColumn {
                FinnyButton(text = stringResource(R.string.shop_action), onClick = onShop)
            }
        },
    ) {
        Text(
            text = stringResource(R.string.budget_started),
            style = MaterialTheme.typography.bodyLarge,
        )
        state.lines.forEach { line -> JarProgress(line) }
    }
}

/** Копилка — «отложено»; траты — «потрачено, осталось», а пока не тратили — только остаток. */
@Composable
private fun JarProgress(line: BudgetLine) {
    val title = stringResource(line.category.label)
    val left = line.actual.shortfallTo(line.planned).amount
    val status = when {
        line.category == SpendCategory.SAVINGS -> stringResource(R.string.budget_jar_saved, line.actual.amount)
        line.actual == Coins.ZERO -> stringResource(R.string.budget_jar_left, left)
        else -> stringResource(R.string.budget_jar_spent_left, line.actual.amount, left)
    }
    val spoken = stringResource(R.string.budget_jar_description, title, status)
    FinnyCard {
        Column(
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceTiny),
            modifier = Modifier.clearAndSetSemantics { contentDescription = spoken },
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSmall),
            ) {
                Text(text = line.category.icon, style = MaterialTheme.typography.titleMedium)
                Text(text = title, style = MaterialTheme.typography.titleMedium)
            }
            ProgressLine(
                fraction = if (line.planned == Coins.ZERO) 0f else line.actual.amount.toFloat() / line.planned.amount,
                color = line.category.color,
            )
            Text(text = status, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
