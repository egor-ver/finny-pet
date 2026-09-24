package ru.finnypet.app.ui.screens.day

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.finnypet.app.R
import ru.finnypet.app.ui.components.ButtonColumn
import ru.finnypet.app.ui.components.FinnyButton
import ru.finnypet.app.ui.components.FinnyCard
import ru.finnypet.app.ui.components.FinnyDialog
import ru.finnypet.app.ui.components.FinnyScaffold
import ru.finnypet.app.ui.components.FinnySecondaryButton
import ru.finnypet.app.ui.components.MoneyCard
import ru.finnypet.app.ui.components.Owl
import ru.finnypet.app.ui.components.PlanComparison
import ru.finnypet.app.ui.components.ProgressLine
import ru.finnypet.app.ui.components.label
import ru.finnypet.app.ui.screens.main.GrowthView
import ru.finnypet.app.ui.text.WordForm
import ru.finnypet.app.ui.text.wordFormOf
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
    onBack: (() -> Unit)?,
    title: String = stringResource(R.string.day_title),
    bottomBar: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    FinnyScaffold(
        title = title,
        onBack = onBack,
        bottomBar = bottomBar,
        spacing = Dimens.SpaceMedium,
        content = content,
    )
}

/**
 * День идёт: видно, к чему ребёнок пришёл, и можно закончить.
 *
 * Окно «Закончить день?» открывается сразу, до экрана (раздел 8 плана): сюда
 * приходят по «Уложить спать», и вопрос — единственное, что тут решается.
 * День не вернуть, поэтому подтверждение обязательно (ТЗ 3.6). Если сова
 * голодна, а монеты на нужное есть, она переспрашивает сама (R14).
 */
@Composable
private fun Running(state: DayState.Running, onBack: () -> Unit, onClose: () -> Unit) {
    var asking by rememberSaveable { mutableStateOf(true) }

    Screen(
        onBack = onBack,
        bottomBar = {
            ButtonColumn {
                FinnyButton(
                    text = stringResource(R.string.day_action_close),
                    onClick = { asking = true },
                    enabled = !state.closing,
                )
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
                // Передумал — обратно на главный: итогов ещё нет, смотреть здесь нечего.
                FinnySecondaryButton(
                    text = stringResource(R.string.action_back),
                    onClick = {
                        asking = false
                        onBack()
                    },
                )
            },
        ) {
            Text(
                text = state.warning ?: stringResource(R.string.day_confirm_text),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

/**
 * Итоги одним экраном (раздел 8 плана): облачко с объяснением, сова с
 * выражением дня, новая стадия, три строки ✓/✗, очки роста и что перешло на
 * завтра. Единственное действие — начать следующий день: «Назад» здесь некуда.
 */
@Composable
private fun Closed(summary: DaySummary, onBack: () -> Unit) {
    Screen(
        onBack = null,
        title = stringResource(R.string.day_closed, summary.number),
        bottomBar = {
            ButtonColumn {
                FinnyButton(
                    text = stringResource(R.string.day_action_next, summary.nextNumber),
                    onClick = onBack,
                )
            }
        },
    ) {
        FinnyCard {
            Text(text = summary.headline, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
        Owl(look = summary.owl, size = 140.dp, modifier = Modifier.align(Alignment.CenterHorizontally))
        summary.newStage?.let { stage ->
            FinnyCard(color = MaterialTheme.colorScheme.primaryContainer) {
                Text(
                    text = stringResource(R.string.day_new_stage, stringResource(stage.label)),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
        summary.checks.forEach { check -> CheckLine(check) }
        Growth(earned = summary.earnedPoints, growth = summary.growth)
        MoneyCard(label = stringResource(R.string.day_carry_over), amount = summary.carryOver)
        FinnyCard(color = MaterialTheme.colorScheme.secondaryContainer) {
            Text(text = summary.tip, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/** ✓ или ✗ — знаком и словом для TalkBack: цвет тут не нужен вовсе (ТЗ 3.6). */
@Composable
private fun CheckLine(check: DayCheckView) {
    val spoken = stringResource(if (check.done) R.string.day_check_done else R.string.day_check_missed) + ". " + check.text
    FinnyCard {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSmall),
            modifier = Modifier.clearAndSetSemantics { contentDescription = spoken },
        ) {
            Text(text = if (check.done) "\u2713" else "\u2717", style = MaterialTheme.typography.titleMedium)
            Text(text = check.text, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/** «+6 очков роста» и полоса «12 из 20» — до следующей стадии, как на главном. */
@Composable
private fun Growth(earned: Int, growth: GrowthView?) {
    Text(
        text = stringResource(
            when (wordFormOf(earned)) {
                WordForm.ONE -> R.string.day_growth_one
                WordForm.FEW -> R.string.day_growth_few
                WordForm.MANY -> R.string.day_growth_many
            },
            earned,
        ),
        style = MaterialTheme.typography.titleMedium,
    )
    if (growth != null) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSmall),
        ) {
            ProgressLine(
                fraction = growth.points.toFloat() / growth.target,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                contentDescription = stringResource(R.string.main_growth_points, growth.points, growth.target),
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.main_growth_points, growth.points, growth.target),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
    }
}
