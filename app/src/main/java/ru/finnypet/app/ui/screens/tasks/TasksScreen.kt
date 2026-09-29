package ru.finnypet.app.ui.screens.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.finnypet.app.R
import ru.finnypet.app.domain.model.TaskId
import ru.finnypet.app.ui.components.ButtonColumn
import ru.finnypet.app.ui.components.CoinChip
import ru.finnypet.app.ui.components.FinnyButton
import ru.finnypet.app.ui.components.FinnyCard
import ru.finnypet.app.ui.components.FinnyListScaffold
import ru.finnypet.app.ui.components.FinnyScaffold
import ru.finnypet.app.ui.components.OneWordText
import ru.finnypet.app.ui.components.Owl
import ru.finnypet.app.ui.components.OwlRole
import ru.finnypet.app.ui.components.SegmentLine
import ru.finnypet.app.ui.components.coinsText
import ru.finnypet.app.ui.components.colors
import ru.finnypet.app.ui.components.icons.FinnyIcons
import ru.finnypet.app.ui.components.label
import ru.finnypet.app.ui.theme.Dimens

/**
 * Список заданий (ТЗ 2.5.8; DESIGN_PLAN 3.9): сверху «Пройдено 2 из 6»
 * кусочками и награда дня, ниже темы в своём цвете. Все задания доступны
 * сразу, пройденные отмечены и открываются снова.
 */
@Composable
fun TasksScreen(
    onBack: () -> Unit,
    onOpen: (TaskId) -> Unit,
    viewModel: TasksViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    TasksContent(
        state = state,
        onBack = onBack,
        onOpen = onOpen,
        onRetry = viewModel::retry,
    )
}

@Composable
fun TasksContent(
    state: TasksState,
    onBack: () -> Unit,
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
                }
            },
        ) {
            Text(
                text = stringResource(R.string.tasks_failed),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
            )
        }

        is TasksState.Ready -> Ready(state = state, onBack = onBack, onOpen = onOpen)
    }
}

@Composable
private fun Ready(
    state: TasksState.Ready,
    onBack: () -> Unit,
    onOpen: (TaskId) -> Unit,
) {
    FinnyListScaffold(title = stringResource(R.string.tasks_title), onBack = onBack) {
        if (state.groups.isEmpty()) {
            item(key = "header:empty") {
                Text(
                    text = stringResource(R.string.tasks_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            item(key = "header:progress") { ProgressCard(state = state) }
        }
        state.groups.forEach { group ->
            item(key = "topic:${group.topic.name}") { TopicHeader(group = group) }
            items(group.tasks, key = { "task:${it.id.value}" }) { task ->
                TaskCard(task = task, onClick = { onOpen(task.id) })
            }
        }
    }
}

/**
 * Общий прогресс: сова, «Пройдено 2 из 6», кусочек на задание и награда дня.
 * Правило дня — здесь, до выбора задания: ребёнок знает, за что монеты,
 * ещё до того, как вложит усилия. Чип заменил прежнюю плашку с правилом.
 */
@Composable
private fun ProgressCard(state: TasksState.Ready) {
    val spoken = listOf(
        stringResource(R.string.tasks_progress, state.passed, state.total),
        if (state.rewardAvailable) {
            "${stringResource(R.string.tasks_reward_available)}. " +
                stringResource(R.string.tasks_card_reward, coinsText(state.reward))
        } else {
            stringResource(R.string.tasks_reward_taken)
        },
    ).joinToString(". ")
    FinnyCard(modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = spoken }) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMedium),
        ) {
            // Сова здесь — украшение: о прогрессе говорит подпись карточки.
            Owl(look = state.owl, size = OwlRole.WithSpeech.size, modifier = Modifier.clearAndSetSemantics {})
            Column(modifier = Modifier.weight(1f)) {
                Text(text = stringResource(R.string.tasks_progress_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    text = stringResource(R.string.tasks_count, state.passed, state.total),
                    style = MaterialTheme.typography.displaySmall,
                )
            }
        }
        SegmentLine(done = state.passed, total = state.total)
        if (state.rewardAvailable) {
            CoinChip(
                text = stringResource(R.string.tasks_reward_today, state.reward.amount),
                color = MaterialTheme.colorScheme.primaryContainer,
            )
        } else {
            Text(
                text = stringResource(R.string.tasks_reward_taken),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Заголовок темы: иконка на тарелке цвета темы, название и «1 из 2» мини-полосой. */
@Composable
private fun TopicHeader(group: TaskGroup) {
    val title = stringResource(group.topic.label)
    val spoken = "$title. ${stringResource(R.string.tasks_progress, group.passed, group.tasks.size)}"
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMedium),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Dimens.SpaceSmall)
            .semantics(mergeDescendants = true) {
                heading()
                contentDescription = spoken
            },
    ) {
        TopicPlate(topic = group.topic)
        // Название темы — одно слово: при шрифте 2,0 «Планирование» рвалось посреди (F8).
        OneWordText(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(Dimens.SpaceTiny)) {
            Text(
                text = stringResource(R.string.tasks_count, group.passed, group.tasks.size),
                style = MaterialTheme.typography.titleSmall,
            )
            // Тон текста, а не заливки: заливка «Планирования» к треку даёт меньше 3:1.
            SegmentLine(
                done = group.passed,
                total = group.tasks.size,
                color = group.topic.colors.text,
                modifier = Modifier.width(MINI_LINE_WIDTH),
            )
        }
    }
}

/**
 * Задание — вся карточка кнопка, в контейнере цвета темы. У задания нет
 * заголовка (в контенте только вступление), поэтому показывается оно само —
 * целиком, без многоточия: обрезанная фраза не говорит, о чём задание.
 * Пройденное — отметкой и словом, не только цветом (ТЗ 3.6), и чуть
 * светлее, но не серое: его можно пройти снова.
 *
 * TalkBack читает карточку одной фразой: тема, задание, «пройдено» и/или награда.
 */
@Composable
private fun TaskCard(task: TaskRow, onClick: () -> Unit) {
    val colors = task.topic.colors
    val surface = MaterialTheme.colorScheme.surface
    val badge = task.badge
    val status = when (badge) {
        is TaskBadge.Passed -> badge.reward?.let {
            "${stringResource(R.string.tasks_completed)}. ${stringResource(R.string.tasks_card_reward, coinsText(it))}"
        } ?: stringResource(R.string.tasks_completed)
        is TaskBadge.Reward -> stringResource(R.string.tasks_card_reward, coinsText(badge.coins))
        TaskBadge.Training -> stringResource(R.string.tasks_training)
    }
    val spoken = "${stringResource(task.topic.label)}. ${task.intro} $status"
    FinnyCard(
        color = if (task.completed) lerp(colors.container, surface, PASSED_FADE) else colors.container,
        onClick = onClick,
        modifier = Modifier.semantics { contentDescription = spoken },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMedium)) {
            TopicPlate(topic = task.topic, plate = surface)
            Text(
                text = task.intro,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
        }
        // Под текстом, у правого края: сбоку от текста при крупном шрифте
        // не осталось бы места на слова задания.
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            when (badge) {
                is TaskBadge.Passed -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMedium),
                ) {
                    PassedMark(color = colors.fill)
                    badge.reward?.let { CoinChip(text = "+${it.amount}") }
                }
                is TaskBadge.Reward -> CoinChip(text = "+${badge.coins.amount}")
                TaskBadge.Training -> Text(
                    text = stringResource(R.string.tasks_training),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Круглая отметка цвета темы и слово «пройдено». */
@Composable
private fun PassedMark(color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSmall),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(CHECK_SIZE)
                .clip(CircleShape)
                .background(color),
        ) {
            Icon(
                imageVector = FinnyIcons.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.surface,
                modifier = Modifier.size(CHECK_ICON_SIZE),
            )
        }
        Text(
            text = stringResource(R.string.tasks_completed),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

/** Доля белого в контейнере пройденного задания: заметно светлее, но цвет темы узнаётся. */
private const val PASSED_FADE = 0.5f
private val MINI_LINE_WIDTH = 56.dp
private val CHECK_SIZE = 28.dp
private val CHECK_ICON_SIZE = 18.dp
