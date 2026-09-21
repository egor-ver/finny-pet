package ru.finnypet.app.ui.screens.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.finnypet.app.R
import ru.finnypet.app.ui.components.ButtonColumn
import ru.finnypet.app.ui.components.FinnyButton
import ru.finnypet.app.ui.components.FinnyListScaffold
import ru.finnypet.app.ui.components.FinnyScaffold
import ru.finnypet.app.ui.components.FinnySecondaryButton
import ru.finnypet.app.ui.components.GoalProgressBar
import ru.finnypet.app.ui.components.MoneyAmount
import ru.finnypet.app.ui.components.PlanComparison
import ru.finnypet.app.ui.components.label
import ru.finnypet.app.ui.theme.Dimens

/**
 * История и учебный прогресс (ТЗ 2.5.11).
 *
 * Итоги последнего дня, цель, пройденные задания и справочник терминов — всё
 * на одном экране, без вложенных разделов: ребёнку негде потеряться.
 */
@Composable
fun ProgressScreen(
    onBack: () -> Unit,
    viewModel: ProgressViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ProgressContent(state = state, onBack = onBack, onRetry = viewModel::retry)
}

@Composable
fun ProgressContent(state: ProgressState, onBack: () -> Unit, onRetry: () -> Unit = {}) {
    when (state) {
        ProgressState.Loading -> FinnyScaffold(
            title = stringResource(R.string.progress_title),
            onBack = onBack,
        ) {}

        ProgressState.Failed -> FinnyScaffold(
            title = stringResource(R.string.progress_title),
            onBack = onBack,
            bottomBar = {
                ButtonColumn {
                    FinnyButton(text = stringResource(R.string.action_retry), onClick = onRetry)
                    FinnySecondaryButton(text = stringResource(R.string.action_back), onClick = onBack)
                }
            },
        ) {
            Text(
                text = stringResource(R.string.progress_failed),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
            )
        }

        is ProgressState.Ready -> Ready(state = state, onBack = onBack)
    }
}

/**
 * Список, а не колонка: пройденных заданий со временем становится много.
 *
 * Ключи с префиксами: идентификаторы заданий и терминов пишет напарник в
 * контент-паке, и термин с id «goal» иначе столкнулся бы с разделом цели.
 */
@Composable
private fun Ready(state: ProgressState.Ready, onBack: () -> Unit) {
    FinnyListScaffold(
        title = stringResource(R.string.progress_title),
        onBack = onBack,
        bottomBar = {
            ButtonColumn {
                FinnySecondaryButton(text = stringResource(R.string.action_back), onClick = onBack)
            }
        },
    ) {
        lastDay(state.lastDay)
        goal(state.goal)
        tasks(state.passed)
        glossary(state.terms)
    }
}

private fun LazyListScope.lastDay(lastDay: LastDay?) {
    item(key = "day") {
        if (lastDay == null) {
            // Без номера: дня ещё не было, а «день 1» прочиталось бы как
            // итоги уже прожитого дня.
            Section(stringResource(R.string.progress_last_day_none)) {
                Explanation(stringResource(R.string.progress_no_days))
            }
            return@item
        }
        Section(stringResource(R.string.progress_last_day, lastDay.number)) {
            PlanComparison(
                lines = lastDay.lines,
                planTotal = lastDay.planTotal,
                factTotal = lastDay.factTotal,
            )
        }
    }
}

private fun LazyListScope.goal(goal: GoalSummary?) {
    item(key = "goal") {
        Section(stringResource(R.string.progress_goal)) {
            if (goal == null) {
                Explanation(stringResource(R.string.main_goal_none))
            } else {
                GoalProgressBar(title = goal.title, saved = goal.saved, price = goal.price)
            }
        }
    }
}

private fun LazyListScope.tasks(passed: List<PassedTask>) {
    item(key = "tasks") { Heading(stringResource(R.string.progress_tasks)) }
    if (passed.isEmpty()) {
        item(key = "tasks:none") { Explanation(stringResource(R.string.progress_no_tasks)) }
        return
    }
    items(passed, key = { task -> "task:${task.id.value}" }) { task -> PassedTaskTile(task) }
}

/**
 * Справочник. Термины свёрнуты: шесть объяснений подряд заняли бы экран
 * целиком, а нужны они по одному и по случаю.
 */
private fun LazyListScope.glossary(terms: List<Term>) {
    if (terms.isEmpty()) return
    item(key = "glossary") { Heading(stringResource(R.string.progress_glossary)) }
    items(terms, key = { term -> "term:${term.id}" }) { term -> TermTile(term) }
}

@Composable
private fun PassedTaskTile(task: PassedTask) {
    Tile {
        Text(text = task.title, style = MaterialTheme.typography.bodyLarge)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSmall),
            modifier = Modifier.fillMaxWidth(),
        ) {
            // Темы может не быть: задание убрали из контент-пака, а
            // прохождение осталось. Тогда её место остаётся пустым, иначе
            // награда уехала бы к левому краю, не как у соседних строк.
            val topic = task.topic
            if (topic == null) {
                Spacer(modifier = Modifier.weight(1f))
            } else {
                Text(
                    text = stringResource(topic.label),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }
            MoneyAmount(amount = task.reward, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun TermTile(term: Term) {
    var open by rememberSaveable(term.id) { mutableStateOf(false) }
    // Подпись говорит, что будет по нажатию: свёрнутый и развёрнутый термин
    // отличаются только наличием текста, а озвучке этого не видно (ТЗ 3.6).
    val action = stringResource(
        if (open) R.string.progress_term_opened else R.string.progress_term_closed,
        term.title,
    )
    Tile(onClick = { open = !open }, clickLabel = action) {
        Text(text = term.title, style = MaterialTheme.typography.titleMedium)
        if (open) {
            Explanation(term.body)
        }
    }
}

/** Заголовок раздела вместе с его содержимым: в списке это один элемент. */
@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSmall)) {
        Heading(title)
        content()
    }
}

@Composable
private fun Heading(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun Explanation(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Подложка, одинаковая у задания и термина: текст разный, вид один. */
@Composable
private fun Tile(
    onClick: (() -> Unit)? = null,
    clickLabel: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceTiny),
        modifier = Modifier
            .fillMaxWidth()
            // Скругление до нажатия: иначе отклик выходит за края подложки.
            .clip(RoundedCornerShape(Dimens.Corner))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(
                if (onClick == null) {
                    Modifier
                } else {
                    // Подпись действия, а не описание: описание заменило бы
                    // собой весь текст внутри, и объяснение термина пропало бы
                    // из озвучки (ТЗ 3.6).
                    Modifier.clickable(
                        role = Role.Button,
                        onClickLabel = clickLabel,
                        onClick = onClick,
                    )
                }
            )
            .defaultMinSize(minHeight = Dimens.TouchTarget)
            .padding(horizontal = Dimens.SpaceMedium, vertical = Dimens.SpaceSmall),
        content = content,
    )
}
