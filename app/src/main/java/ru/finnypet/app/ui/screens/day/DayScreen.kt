package ru.finnypet.app.ui.screens.day

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.finnypet.app.R
import ru.finnypet.app.ui.components.ButtonColumn
import ru.finnypet.app.ui.components.FinnyButton
import ru.finnypet.app.ui.components.FinnyDialog
import ru.finnypet.app.ui.components.FinnyScaffold
import ru.finnypet.app.ui.components.FinnySecondaryButton
import ru.finnypet.app.ui.components.MoneyCard
import ru.finnypet.app.ui.components.PlanComparison
import ru.finnypet.app.ui.components.StatChangeLine
import ru.finnypet.app.ui.components.label
import ru.finnypet.app.ui.theme.Dimens

/**
 * Итоги игрового дня (ТЗ 2.5.9, 2.5.10).
 *
 * Пока день идёт — сравнение плана с фактом и кнопка закончить. После
 * закрытия — объяснение, что изменилось у питомца и что перенеслось на завтра.
 */
@Composable
fun DayScreen(
    onBack: () -> Unit,
    onPlan: () -> Unit,
    viewModel: DayViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    DayContent(
        state = state,
        onBack = onBack,
        onPlan = onPlan,
        onClose = viewModel::close,
        onRetry = viewModel::retry,
    )
}

@Composable
fun DayContent(
    state: DayState,
    onBack: () -> Unit,
    onPlan: () -> Unit = {},
    onClose: () -> Unit = {},
    onRetry: () -> Unit = {},
) {
    when (state) {
        DayState.Loading -> Screen(onBack = onBack) {}

        DayState.Failed -> Screen(
            onBack = onBack,
            bottomBar = {
                ButtonColumn {
                    FinnyButton(text = stringResource(R.string.action_retry), onClick = onRetry)
                    BackButton(onBack)
                }
            },
        ) {
            Text(
                text = stringResource(R.string.day_failed),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
            )
        }

        DayState.Planning -> Screen(
            onBack = onBack,
            bottomBar = {
                ButtonColumn {
                    FinnyButton(text = stringResource(R.string.budget_action_plan), onClick = onPlan)
                    BackButton(onBack)
                }
            },
        ) {
            Text(
                text = stringResource(R.string.day_not_started),
                style = MaterialTheme.typography.bodyLarge,
            )
        }

        is DayState.Running -> Running(state = state, onBack = onBack, onClose = onClose)

        is DayState.Closed -> Closed(summary = state.summary, onBack = onBack)
    }
}

@Composable
private fun Screen(
    onBack: () -> Unit,
    bottomBar: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    FinnyScaffold(
        title = stringResource(R.string.day_title),
        onBack = onBack,
        bottomBar = bottomBar,
        spacing = Dimens.SpaceMedium,
        content = content,
    )
}

@Composable
private fun BackButton(onBack: () -> Unit) {
    FinnySecondaryButton(text = stringResource(R.string.action_back), onClick = onBack)
}

/**
 * День идёт: видно, к чему ребёнок пришёл, и можно закончить.
 *
 * Закрытие спрашивает подтверждения: день не вернуть, а кнопка стоит там же,
 * где на других экранах стоит безобидное действие.
 */
@Composable
private fun Running(state: DayState.Running, onBack: () -> Unit, onClose: () -> Unit) {
    var asking by rememberSaveable { mutableStateOf(false) }

    Screen(
        onBack = onBack,
        bottomBar = {
            ButtonColumn {
                FinnyButton(
                    text = stringResource(R.string.day_action_close),
                    onClick = { asking = true },
                    enabled = !state.closing,
                )
                BackButton(onBack)
            }
        },
    ) {
        Text(
            text = stringResource(R.string.day_running, state.number),
            style = MaterialTheme.typography.titleMedium,
        )
        PlanComparison(
            lines = state.lines,
            planTotal = state.planTotal,
            factTotal = state.factTotal,
        )
    }

    if (asking) {
        FinnyDialog(
            title = stringResource(R.string.day_confirm_title),
            onDismiss = { asking = false },
            buttons = {
                FinnyButton(
                    text = stringResource(R.string.day_action_close),
                    onClick = {
                        asking = false
                        onClose()
                    },
                )
                FinnySecondaryButton(
                    text = stringResource(R.string.action_back),
                    onClick = { asking = false },
                )
            },
        ) {
            Text(
                text = stringResource(R.string.day_confirm_text),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

/**
 * Итоги: объяснение, сравнение, что стало с питомцем и что перешло на завтра.
 *
 * Объяснение стоит первым: ТЗ 2.5.9 требует объяснить причину и следствие, а
 * не показать одни цифры.
 */
@Composable
private fun Closed(summary: DaySummary, onBack: () -> Unit) {
    Screen(
        onBack = onBack,
        bottomBar = {
            ButtonColumn {
                FinnyButton(
                    text = stringResource(R.string.day_action_next, summary.nextNumber),
                    onClick = onBack,
                )
            }
        },
    ) {
        Text(
            text = stringResource(R.string.day_closed, summary.number),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = summary.headline, style = MaterialTheme.typography.bodyLarge)

        PlanComparison(
            lines = summary.lines,
            planTotal = summary.planTotal,
            factTotal = summary.factTotal,
        )

        Text(
            text = stringResource(R.string.day_pet_changes),
            style = MaterialTheme.typography.titleMedium,
        )
        if (summary.statChanges.isEmpty()) {
            Text(
                text = stringResource(R.string.day_pet_same),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            summary.statChanges.forEach { change -> StatChangeLine(change = change) }
        }

        Text(text = summary.growthText, style = MaterialTheme.typography.bodyLarge)
        summary.newStage?.let { stage ->
            Text(
                text = stringResource(R.string.day_new_stage, stringResource(stage.label)),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        MoneyCard(label = stringResource(R.string.day_carry_over), amount = summary.carryOver)
    }
}
