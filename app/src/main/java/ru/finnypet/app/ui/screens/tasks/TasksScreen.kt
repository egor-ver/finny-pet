package ru.finnypet.app.ui.screens.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.finnypet.app.R
import ru.finnypet.app.domain.model.TaskId
import ru.finnypet.app.ui.components.ButtonColumn
import ru.finnypet.app.ui.components.FinnyButton
import ru.finnypet.app.ui.components.FinnyDialog
import ru.finnypet.app.ui.components.FinnyListScaffold
import ru.finnypet.app.ui.components.FinnyScaffold
import ru.finnypet.app.ui.components.FinnySecondaryButton
import ru.finnypet.app.ui.components.label
import ru.finnypet.app.ui.theme.Dimens

/**
 * Список заданий (ТЗ 2.5.8): три темы, все задания доступны сразу,
 * пройденные подписаны словом и открываются снова.
 */
@Composable
fun TasksScreen(
    onBack: () -> Unit,
    onPlan: () -> Unit,
    onOpen: (TaskId) -> Unit,
    viewModel: TasksViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    TasksContent(
        state = state,
        onBack = onBack,
        onPlan = onPlan,
        onOpen = onOpen,
        onRetry = viewModel::retry,
    )
}

@Composable
fun TasksContent(
    state: TasksState,
    onBack: () -> Unit,
    onPlan: () -> Unit = {},
    onOpen: (TaskId) -> Unit = {},
    onRetry: () -> Unit = {},
) {
    when (state) {
        TasksState.Loading -> FinnyScaffold(title = stringResource(R.string.tasks_title), onBack = onBack) {}

        TasksState.Failed -> FinnyScaffold(
            title = stringResource(R.string.tasks_title),
            onBack = onBack,
            bottomBar = {
                ButtonColumn {
                    FinnyButton(text = stringResource(R.string.action_retry), onClick = onRetry)
                    FinnySecondaryButton(text = stringResource(R.string.action_back), onClick = onBack)
                }
            },
        ) {
            Text(
                text = stringResource(R.string.tasks_failed),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
            )
        }

        is TasksState.Ready -> Ready(state = state, onBack = onBack, onPlan = onPlan, onOpen = onOpen)
    }
}

@Composable
private fun Ready(
    state: TasksState.Ready,
    onBack: () -> Unit,
    onPlan: () -> Unit,
    onOpen: (TaskId) -> Unit,
) {
    // Пока день планируется, нажатие на задание — не тупик, а дорога в план.
    var askingPlan by rememberSaveable { mutableStateOf(false) }

    FinnyListScaffold(title = stringResource(R.string.tasks_title), onBack = onBack) {
        item(key = "header:reward") { RewardNote(available = state.rewardAvailable) }
        if (!state.canStart) {
            item(key = "header:planning") { PlanningHint(onPlan = onPlan) }
        }
        if (state.groups.isEmpty()) {
            item(key = "header:empty") {
                Text(
                    text = stringResource(R.string.tasks_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        state.groups.forEach { group ->
            item(key = "topic:${group.topic.name}") {
                Text(
                    text = stringResource(group.topic.label),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = Dimens.SpaceSmall),
                )
            }
            items(group.tasks, key = { "task:${it.id.value}" }) { task ->
                TaskRowCard(
                    task = task,
                    onClick = { if (state.canStart) onOpen(task.id) else askingPlan = true },
                )
            }
        }
    }

    if (askingPlan) {
        FinnyDialog(
            title = stringResource(R.string.budget_title),
            onDismiss = { askingPlan = false },
            buttons = {
                FinnyButton(
                    text = stringResource(R.string.budget_action_plan),
                    onClick = {
                        askingPlan = false
                        onPlan()
                    },
                )
                FinnySecondaryButton(text = stringResource(R.string.action_not_now), onClick = { askingPlan = false })
            },
        ) {
            Text(text = stringResource(R.string.tasks_planning_hint), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/** Правило дня — до выбора задания, чтобы ребёнок знал, за что монеты, а за что нет. */
@Composable
private fun RewardNote(available: Boolean) {
    Text(
        text = stringResource(if (available) R.string.tasks_reward_available else R.string.tasks_reward_taken),
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(Dimens.Corner),
            )
            .padding(Dimens.Space),
    )
}

@Composable
private fun PlanningHint(onPlan: () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMedium),
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.secondaryContainer,
                RoundedCornerShape(Dimens.Corner),
            )
            .padding(Dimens.Space),
    ) {
        Text(
            text = stringResource(R.string.tasks_planning_hint),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        FinnyButton(text = stringResource(R.string.budget_action_plan), onClick = onPlan)
    }
}

/**
 * Задание в списке — вся строка кнопка. У задания нет заголовка (в контенте
 * только вступление), поэтому показываются первые строки вступления.
 * «Пройдено» — словом, не цветом (ТЗ 3.6).
 */
@Composable
private fun TaskRowCard(task: TaskRow, onClick: () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceTiny),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.Corner))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(role = Role.Button, onClick = onClick)
            .defaultMinSize(minHeight = Dimens.TouchTarget)
            .padding(horizontal = Dimens.Space, vertical = Dimens.SpaceMedium),
    ) {
        Text(
            text = task.intro,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (task.completed) {
            Text(
                text = stringResource(R.string.tasks_completed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
