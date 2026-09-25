package ru.finnypet.app.ui.screens.savings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.finnypet.app.R
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.GoalId
import ru.finnypet.app.ui.components.ButtonColumn
import ru.finnypet.app.ui.components.FinnyButton
import ru.finnypet.app.ui.components.FinnyDialog
import ru.finnypet.app.ui.components.FinnyScaffold
import ru.finnypet.app.ui.components.FinnySecondaryButton
import ru.finnypet.app.ui.components.ItemIcon
import ru.finnypet.app.ui.components.LabelledLine
import ru.finnypet.app.ui.components.MoneyAmount
import ru.finnypet.app.ui.components.MoneyCard
import ru.finnypet.app.ui.components.PlanningHint
import ru.finnypet.app.ui.components.ProgressLine
import ru.finnypet.app.ui.components.StepButton
import ru.finnypet.app.ui.text.WordForm
import ru.finnypet.app.ui.text.wordFormOf
import ru.finnypet.app.ui.theme.Dimens

/**
 * Копилка и цель (ТЗ 2.5.7): выбор цели, стоимость, накоплено и остаток,
 * пополнение, снятие после отдельного подтверждения с превью, срок по
 * средней сумме пополнения.
 */
@Composable
fun SavingsScreen(
    onBack: () -> Unit,
    onPlan: () -> Unit,
    viewModel: SavingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    SavingsContent(
        state = state,
        onBack = onBack,
        onPlan = onPlan,
        onChoose = viewModel::choose,
        onDeposit = viewModel::startDeposit,
        onWithdraw = viewModel::startWithdraw,
        onAdd = viewModel::add,
        onRemove = viewModel::remove,
        onConfirm = viewModel::confirm,
        onCancel = viewModel::cancel,
        onDismiss = viewModel::dismissOutcome,
        onRetry = viewModel::retry,
        onBuy = viewModel::buy,
    )
}

@Composable
fun SavingsContent(
    state: SavingsState,
    onBack: () -> Unit,
    onPlan: () -> Unit = {},
    onChoose: (GoalId) -> Unit = {},
    onDeposit: () -> Unit = {},
    onWithdraw: () -> Unit = {},
    onAdd: () -> Unit = {},
    onRemove: () -> Unit = {},
    onConfirm: () -> Unit = {},
    onCancel: () -> Unit = {},
    onDismiss: () -> Unit = {},
    onRetry: () -> Unit = {},
    onBuy: () -> Unit = {},
) {
    when (state) {
        SavingsState.Loading -> Screen(onBack = onBack) {}

        SavingsState.Failed -> Screen(
            onBack = onBack,
            bottomBar = {
                ButtonColumn {
                    FinnyButton(text = stringResource(R.string.action_retry), onClick = onRetry)
                }
            },
        ) {
            Text(
                text = stringResource(R.string.savings_failed),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
            )
        }

        is SavingsState.Ready -> Ready(
            state = state,
            onBack = onBack,
            onPlan = onPlan,
            onChoose = onChoose,
            onDeposit = onDeposit,
            onWithdraw = onWithdraw,
            onAdd = onAdd,
            onRemove = onRemove,
            onConfirm = onConfirm,
            onCancel = onCancel,
            onDismiss = onDismiss,
            onBuy = onBuy,
        )
    }
}

@Composable
private fun Screen(
    onBack: () -> Unit,
    bottomBar: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    FinnyScaffold(
        title = stringResource(R.string.savings_title),
        onBack = onBack,
        bottomBar = bottomBar,
        spacing = Dimens.SpaceMedium,
        content = content,
    )
}

@Composable
private fun Ready(
    state: SavingsState.Ready,
    onBack: () -> Unit,
    onPlan: () -> Unit,
    onChoose: (GoalId) -> Unit,
    onDeposit: () -> Unit,
    onWithdraw: () -> Unit,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
    onBuy: () -> Unit,
) {
    Screen(
        onBack = onBack,
        bottomBar = {
            ButtonColumn {
                FinnyButton(
                    text = stringResource(R.string.savings_deposit),
                    onClick = onDeposit,
                    enabled = state.canDeposit,
                )
                FinnySecondaryButton(
                    text = stringResource(R.string.savings_withdraw),
                    onClick = onWithdraw,
                    enabled = state.canWithdraw,
                )
            }
        },
    ) {
        MoneyCard(label = stringResource(R.string.main_balance), amount = state.balance)

        if (!state.canOperate) {
            PlanningHint(text = stringResource(R.string.savings_planning_hint), onPlan = onPlan)
        }

        val active = state.active
        if (active == null) {
            Text(
                text = stringResource(R.string.savings_goal_none),
                style = MaterialTheme.typography.bodyLarge,
            )
        } else {
            ActiveGoal(state = state, goal = active, onBuy = onBuy)
        }

        Text(
            text = stringResource(R.string.savings_goal_choose),
            style = MaterialTheme.typography.titleMedium,
        )
        if (state.goals.isEmpty()) {
            Text(
                text = stringResource(R.string.savings_goals_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        state.goals.forEach { goal ->
            GoalRow(goal = goal, selectable = state.canChoose(goal), onClick = { onChoose(goal.id) })
        }
    }

    when (val draft = state.draft) {
        null -> Unit
        is SavingsDraft.Deposit -> DepositDialog(
            draft = draft,
            onAdd = onAdd,
            onRemove = onRemove,
            onConfirm = onConfirm,
            onCancel = onCancel,
        )

        is SavingsDraft.Withdraw -> WithdrawDialog(
            draft = draft,
            onAdd = onAdd,
            onRemove = onRemove,
            onConfirm = onConfirm,
            onCancel = onCancel,
        )
    }
    state.outcome?.let { OutcomeDialog(outcome = it, onDismiss = onDismiss) }
}

/** Пока день планируется, копилка закрыта — и дорога в план тут же (ТЗ 3.4). */

/**
 * Выбранная цель: стоимость, накоплено, остаток и срок — всё, что требует
 * ТЗ 2.5.7 показать ребёнку о цели.
 */
@Composable
private fun ActiveGoal(state: SavingsState.Ready, goal: GoalView, onBuy: () -> Unit) {
    var askingBuy by rememberSaveable { mutableStateOf(false) }
    // «Купить комиксы», а не «Купить Комиксы»: название стоит внутри фразы.
    val thing = goal.title.replaceFirstChar { it.lowercase() }
    Column(
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSmall),
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(Dimens.Corner),
            )
            .padding(horizontal = Dimens.Space, vertical = Dimens.SpaceMedium),
    ) {
        Text(text = goal.title, style = MaterialTheme.typography.titleLarge)
        LabelledLine(label = stringResource(R.string.savings_price), amount = goal.price)
        LabelledLine(label = stringResource(R.string.main_savings), amount = goal.saved)
        ProgressLine(
            fraction = goal.fraction,
            contentDescription = stringResource(
                R.string.main_goal_progress,
                goal.title,
                goal.saved.amount,
                goal.price.amount,
            ),
        )
        if (goal.isReached) {
            FinnyButton(
                text = stringResource(R.string.savings_buy, thing),
                onClick = { askingBuy = true },
                enabled = state.canBuy,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = BUY_BUTTON_HEIGHT),
            )
        } else {
            LabelledLine(label = stringResource(R.string.main_goal_left), amount = goal.remaining)
            val periodsToGoal = state.periodsToGoal
            Text(
                text = when (periodsToGoal) {
                    null -> stringResource(R.string.savings_eta_unknown)
                    else -> stringResource(R.string.savings_eta, state.usualDeposit.amount, daysText(periodsToGoal))
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    // Покупка необратима и опустошает копилку — сначала переспрашиваем.
    if (askingBuy) {
        FinnyDialog(
            title = stringResource(R.string.savings_buy_title, thing),
            onDismiss = { askingBuy = false },
            buttons = {
                FinnyButton(
                    text = stringResource(R.string.savings_buy_confirm),
                    onClick = {
                        askingBuy = false
                        onBuy()
                    },
                )
                FinnySecondaryButton(text = stringResource(R.string.action_not_now), onClick = { askingBuy = false })
            },
        ) {
            Text(text = stringResource(R.string.savings_buy_text, goal.icon), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/** Покупка — главное действие цели: кнопка крупнее обычной (раздел 8 плана). */
private val BUY_BUTTON_HEIGHT = 56.dp

/**
 * Цель в списке — кнопка выбора. Выбранная подписана словом, а не только
 * выделена цветом (ТЗ 3.6). Накопленное показывается у каждой: отложенное на
 * прежнюю цель не пропадает из виду при смене.
 *
 * Купленную цель нельзя выбрать снова, пока есть некупленная (`selectable`
 * приходит из [SavingsState.Ready.canChoose]) — иначе один клик стирал бы
 * выбор ещё не собранной цели. Когда куплены все, тот же ряд снова кликабелен:
 * это единственный путь копить дальше (L5).
 */
@Composable
private fun GoalRow(goal: GoalView, selectable: Boolean, onClick: () -> Unit) {
    val container = if (goal.isActive) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSmall),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.Corner))
            .background(container)
            .clickable(enabled = selectable, role = Role.Button, onClick = onClick)
            .defaultMinSize(minHeight = Dimens.TouchTarget)
            .padding(horizontal = Dimens.Space, vertical = Dimens.SpaceMedium),
    ) {
        ItemIcon(icon = goal.icon)
        Column(
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceTiny),
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = goal.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (goal.isActive) FontWeight.Bold else FontWeight.Normal,
            )
            if (goal.isActive || goal.isBought) {
                val labelRes = when {
                    // Активна и куплена — значит копит на ещё один экземпляр (L5).
                    goal.isActive && goal.isBought -> R.string.savings_goal_repeat
                    goal.isBought -> R.string.savings_goal_bought
                    else -> R.string.savings_goal_active
                }
                Text(
                    text = stringResource(labelRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (goal.saved > Coins.ZERO) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSmall),
                ) {
                    Text(
                        text = stringResource(R.string.main_savings),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    MoneyAmount(amount = goal.saved, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        MoneyAmount(amount = goal.price)
    }
}

@Composable
private fun DepositDialog(
    draft: SavingsDraft.Deposit,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    FinnyDialog(
        title = stringResource(R.string.savings_deposit_title),
        onDismiss = onCancel,
        buttons = {
            FinnyButton(text = stringResource(R.string.savings_deposit), onClick = onConfirm)
            FinnySecondaryButton(text = stringResource(R.string.action_not_now), onClick = onCancel)
        },
    ) {
        AmountStepper(draft = draft, onAdd = onAdd, onRemove = onRemove)
    }
}

/**
 * Снятие — только после отдельного подтверждения, и до него ребёнок видит,
 * сколько останется в копилке и как отодвинется цель (ТЗ 2.5.7).
 */
@Composable
private fun WithdrawDialog(
    draft: SavingsDraft.Withdraw,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    FinnyDialog(
        title = stringResource(R.string.savings_withdraw_title),
        onDismiss = onCancel,
        buttons = {
            FinnyButton(text = stringResource(R.string.savings_withdraw_confirm), onClick = onConfirm)
            FinnySecondaryButton(text = stringResource(R.string.action_not_now), onClick = onCancel)
        },
    ) {
        AmountStepper(draft = draft, onAdd = onAdd, onRemove = onRemove)
        LabelledLine(label = stringResource(R.string.savings_withdraw_left), amount = draft.preview.savingsAfter)
        val before = draft.preview.periodsBefore
        val after = draft.preview.periodsAfter
        Text(
            text = when {
                before == null || after == null -> stringResource(R.string.savings_withdraw_eta_unknown)
                // Ноль дней — это «уже собрана», а не срок; говорить «через 0 дней» ребёнку нельзя.
                before == 0 -> stringResource(R.string.savings_withdraw_eta_reached, daysText(after))
                else -> stringResource(R.string.savings_withdraw_eta, daysText(before), daysText(after))
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AmountStepper(
    draft: SavingsDraft,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSmall),
        modifier = Modifier.fillMaxWidth(),
    ) {
        StepButton(
            symbol = "−",
            description = stringResource(R.string.savings_amount_less),
            enabled = draft.canRemove,
            onClick = onRemove,
        )
        MoneyAmount(
            amount = draft.amount,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f),
        )
        StepButton(
            symbol = "+",
            description = stringResource(R.string.savings_amount_more),
            enabled = draft.canAdd,
            onClick = onAdd,
        )
    }
}

@Composable
private fun OutcomeDialog(outcome: SavingsOutcomeView, onDismiss: () -> Unit) {
    FinnyDialog(
        title = stringResource(
            if (outcome.goalReached) R.string.main_goal_reached else R.string.savings_done_title
        ),
        onDismiss = onDismiss,
        buttons = {
            FinnyButton(text = stringResource(R.string.action_ok), onClick = onDismiss)
        },
    ) {
        Text(text = outcome.text, style = MaterialTheme.typography.bodyLarge)
    }
}

/** «3 дня», «5 дней»: игровой день — это период, склонение по русскому правилу. */
@Composable
private fun daysText(days: Int): String = stringResource(
    when (wordFormOf(days)) {
        WordForm.ONE -> R.string.days_one
        WordForm.FEW -> R.string.days_few
        WordForm.MANY -> R.string.days_many
    },
    days,
)
