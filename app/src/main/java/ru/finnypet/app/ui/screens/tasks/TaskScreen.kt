package ru.finnypet.app.ui.screens.tasks

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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.finnypet.app.R
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.GrowthStage
import ru.finnypet.app.domain.model.PetAppearance
import ru.finnypet.app.domain.model.SpendCategory
import ru.finnypet.app.ui.components.ButtonColumn
import ru.finnypet.app.ui.components.FinnyButton
import ru.finnypet.app.ui.components.FinnyScaffold
import ru.finnypet.app.ui.components.FinnySecondaryButton
import ru.finnypet.app.ui.components.MoneyAmount
import ru.finnypet.app.ui.components.StatChangeLine
import ru.finnypet.app.ui.components.PetImage
import ru.finnypet.app.ui.components.PlanEditor
import ru.finnypet.app.ui.components.ProgressLine
import ru.finnypet.app.ui.components.label
import ru.finnypet.app.ui.theme.Dimens

/**
 * Одно задание (ТЗ 2.5.8): сова просит совета, ребёнок проходит шаги,
 * получает объяснение независимо от результата.
 */
@Composable
fun TaskScreen(
    onBack: () -> Unit,
    viewModel: TaskViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    TaskContent(
        state = state,
        onBack = onBack,
        onStart = viewModel::start,
        onChoose = viewModel::choose,
        onAdd = viewModel::add,
        onRemove = viewModel::remove,
        onToggle = viewModel::toggle,
        onNext = viewModel::next,
        onRetry = viewModel::retry,
    )
}

@Composable
fun TaskContent(
    state: TaskState,
    onBack: () -> Unit,
    onStart: () -> Unit = {},
    onChoose: (String) -> Unit = {},
    onAdd: (SpendCategory) -> Unit = {},
    onRemove: (SpendCategory) -> Unit = {},
    onToggle: (String) -> Unit = {},
    onNext: () -> Unit = {},
    onRetry: () -> Unit = {},
) {
    when (state) {
        TaskState.Loading -> Screen(onBack = onBack) {}

        TaskState.Failed -> Screen(
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

        TaskState.Missing -> Screen(
            onBack = onBack,
            bottomBar = { ButtonColumn { FinnyButton(text = stringResource(R.string.task_finish), onClick = onBack) } },
        ) {
            Text(text = stringResource(R.string.task_missing), style = MaterialTheme.typography.bodyLarge)
        }

        is TaskState.Ready -> when (val stage = state.stage) {
            TaskStage.Intro -> Intro(state = state, onBack = onBack, onStart = onStart)
            is TaskStage.Step -> Step(
                state = state,
                stage = stage,
                onBack = onBack,
                onChoose = onChoose,
                onAdd = onAdd,
                onRemove = onRemove,
                onToggle = onToggle,
                onNext = onNext,
            )

            is TaskStage.Done -> Done(outcome = stage.outcome, appearance = state.appearance, onBack = onBack)
        }
    }
}

@Composable
private fun Screen(
    onBack: () -> Unit,
    bottomBar: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    FinnyScaffold(
        title = stringResource(R.string.task_title),
        onBack = onBack,
        bottomBar = bottomBar,
        spacing = Dimens.SpaceMedium,
        content = content,
    )
}

/**
 * Вступление: сова просит совета. Правило дня — здесь, до первого шага:
 * если монеты за сегодня получены, ребёнок узнаёт об этом до того, как
 * вложит усилия.
 */
@Composable
private fun Intro(
    state: TaskState.Ready,
    onBack: () -> Unit,
    onStart: () -> Unit,
) {
    Screen(
        onBack = onBack,
        bottomBar = {
            ButtonColumn {
                when {
                    state.rewardAvailable -> FinnyButton(text = stringResource(R.string.task_start), onClick = onStart)
                    else -> FinnyButton(text = stringResource(R.string.task_start_training), onClick = onStart)
                }
                FinnySecondaryButton(text = stringResource(R.string.action_back), onClick = onBack)
            }
        },
    ) {
        Text(
            text = stringResource(state.topic.label),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PetSpeech(text = state.intro, appearance = state.appearance)
        // Баланс на главном другой, чем в истории: без этой строки ребёнок
        // принимает монеты задания за свои.
        Text(
            text = stringResource(R.string.task_story_note),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when {
            state.rewardAvailable -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSmall),
                modifier = Modifier.semantics(mergeDescendants = true) {},
            ) {
                Text(text = stringResource(R.string.task_reward_upto), style = MaterialTheme.typography.bodyLarge)
                MoneyAmount(amount = state.maxReward)
            }

            else -> Note(text = stringResource(R.string.task_training_note))
        }
    }
}

/** Сова и её реплика в «пузыре»: задание — это просьба питомца, а не тест. */
@Composable
private fun PetSpeech(text: String, appearance: PetAppearance) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMedium),
        modifier = Modifier.fillMaxWidth(),
    ) {
        PetImage(appearance = appearance, stage = GrowthStage.CUB, size = 96.dp)
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .weight(1f)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(Dimens.Corner),
                )
                .padding(Dimens.Space),
        )
    }
}

@Composable
private fun Note(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.secondaryContainer,
                RoundedCornerShape(Dimens.Corner),
            )
            .padding(Dimens.Space),
    )
}

@Composable
private fun Step(
    state: TaskState.Ready,
    stage: TaskStage.Step,
    onBack: () -> Unit,
    onChoose: (String) -> Unit,
    onAdd: (SpendCategory) -> Unit,
    onRemove: (SpendCategory) -> Unit,
    onToggle: (String) -> Unit,
    onNext: () -> Unit,
) {
    val last = stage.index == stage.total - 1
    Screen(
        onBack = onBack,
        bottomBar = {
            ButtonColumn {
                FinnyButton(
                    text = stringResource(if (last) R.string.task_answer else R.string.task_next),
                    onClick = onNext,
                    enabled = stage.step.canProceed && !state.submitting,
                )
                FinnySecondaryButton(text = stringResource(R.string.action_back), onClick = onBack)
            }
        },
    ) {
        Text(
            text = stringResource(R.string.task_step, stage.index + 1, stage.total),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = stage.step.prompt, style = MaterialTheme.typography.titleMedium)

        when (val step = stage.step) {
            is StepView.Choice -> step.options.forEach { option ->
                OptionRow(option = option, selected = option.id == step.chosen, onClick = { onChoose(option.id) })
            }

            is StepView.Distribute -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSmall),
                    modifier = Modifier.semantics(mergeDescendants = true) {},
                ) {
                    Text(
                        text = stringResource(R.string.task_budget),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    MoneyAmount(amount = step.budget)
                }
                PlanEditor(
                    plan = step.plan,
                    available = step.budget,
                    remainder = step.remainder,
                    overBy = Coins.ZERO,
                    canAdd = step.canAdd,
                    canRemove = step::canRemove,
                    onAdd = onAdd,
                    onRemove = onRemove,
                    jars = step.jars,
                )
            }

            is StepView.Pick -> Shelf(step = step, onToggle = onToggle)
        }
    }
}

/** Вариант — вся строка кнопка; выбранный подписан словом, не только цветом (ТЗ 3.6). */
@Composable
private fun OptionRow(option: OptionView, selected: Boolean, onClick: () -> Unit) {
    val container = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    Column(
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceTiny),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.Corner))
            .background(container)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .defaultMinSize(minHeight = Dimens.TouchTarget)
            .padding(horizontal = Dimens.Space, vertical = Dimens.SpaceMedium),
    ) {
        Text(
            text = option.label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
        if (selected) {
            Text(
                text = stringResource(R.string.task_selected),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * «Полка»: карточки товаров по две в ряд и счётчик корзины. Товар, который
 * не влезает в бюджет, подписан «не влезает» и не переключается.
 */
@Composable
private fun Shelf(step: StepView.Pick, onToggle: (String) -> Unit) {
    // Счётчик читается одной фразой: «в корзине 18 из 30 монет», а не
    // «корзина, 18 монет, слеш, 30 монет» и ещё раз то же самое с полосы.
    val spoken = stringResource(R.string.task_basket_progress, step.spent.amount, step.budget.amount)
    Column(
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceTiny),
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = spoken },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSmall),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(R.string.task_basket),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            MoneyAmount(amount = step.spent)
            Text(text = "/", style = MaterialTheme.typography.bodyMedium)
            MoneyAmount(amount = step.budget)
        }
        ProgressLine(
            fraction = if (step.budget.amount == 0) 0f else step.spent.amount.toFloat() / step.budget.amount,
        )
    }

    step.items.chunked(2).forEach { pair ->
        Row(
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMedium),
            modifier = Modifier.fillMaxWidth(),
        ) {
            pair.forEach { item ->
                ItemCard(
                    item = item,
                    picked = item.id in step.picked,
                    enabled = step.canToggle(item.id),
                    onToggle = { onToggle(item.id) },
                    modifier = Modifier.weight(1f),
                )
            }
            if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun ItemCard(
    item: PickItemView,
    picked: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = if (picked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    Column(
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceTiny),
        modifier = modifier
            .clip(RoundedCornerShape(Dimens.Corner))
            .background(container)
            .toggleable(value = picked, enabled = enabled, role = Role.Checkbox, onValueChange = { onToggle() })
            .defaultMinSize(minHeight = Dimens.TouchTarget)
            .padding(Dimens.SpaceMedium),
    ) {
        Text(text = item.title, style = MaterialTheme.typography.titleMedium)
        Text(
            text = stringResource(item.category.label),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        MoneyAmount(amount = item.price)
        Text(
            text = stringResource(
                when {
                    picked -> R.string.task_picked
                    enabled -> R.string.task_pick
                    else -> R.string.task_pick_full
                }
            ),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = if (picked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Итог: объяснение независимо от результата (ТЗ 2.5.8), награда и питомец. */
@Composable
private fun Done(outcome: TaskOutcomeView, appearance: PetAppearance, onBack: () -> Unit) {
    Screen(
        onBack = onBack,
        bottomBar = { ButtonColumn { FinnyButton(text = stringResource(R.string.task_finish), onClick = onBack) } },
    ) {
        Text(text = stringResource(R.string.task_done_title), style = MaterialTheme.typography.titleLarge)
        PetSpeech(text = outcome.text, appearance = appearance)
        if (outcome.rewardable) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSmall),
                modifier = Modifier.semantics(mergeDescendants = true) {},
            ) {
                Text(text = stringResource(R.string.task_reward), style = MaterialTheme.typography.bodyLarge)
                MoneyAmount(amount = outcome.reward)
            }
        } else {
            Text(text = stringResource(R.string.task_reward_none), style = MaterialTheme.typography.bodyLarge)
        }
        outcome.changes.forEach { change ->
            StatChangeLine(change = change)
        }
    }
}
