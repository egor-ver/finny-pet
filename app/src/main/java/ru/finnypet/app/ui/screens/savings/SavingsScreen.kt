package ru.finnypet.app.ui.screens.savings

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.finnypet.app.R
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.GoalId
import ru.finnypet.app.domain.model.SpendCategory
import ru.finnypet.app.ui.components.ButtonColumn
import ru.finnypet.app.ui.components.Coin
import ru.finnypet.app.ui.components.CoinFlight
import ru.finnypet.app.ui.components.FinnyButton
import ru.finnypet.app.ui.components.FinnyCard
import ru.finnypet.app.ui.components.FinnyDialog
import ru.finnypet.app.ui.components.FinnyScaffold
import ru.finnypet.app.ui.components.FinnySecondaryButton
import ru.finnypet.app.ui.components.ItemIcon
import ru.finnypet.app.ui.components.Jar
import ru.finnypet.app.ui.components.LabelledLine
import ru.finnypet.app.ui.components.MoneyAmount
import ru.finnypet.app.ui.components.Owl
import ru.finnypet.app.ui.components.OwlLook
import ru.finnypet.app.ui.components.OwlRole
import ru.finnypet.app.ui.components.PlanningHint
import ru.finnypet.app.ui.components.SpeechBubble
import ru.finnypet.app.ui.components.StepButton
import ru.finnypet.app.ui.components.coinsText
import ru.finnypet.app.ui.components.color
import ru.finnypet.app.ui.components.fill
import ru.finnypet.app.ui.components.icons.FinnyIcons
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
    // Откуда и куда летят отложенные монеты: сумма кошелька и банка цели.
    var wallet by remember { mutableStateOf<Offset?>(null) }
    var jar by remember { mutableStateOf<Offset?>(null) }

    // Без цели откладывать некуда и брать нечего: вместо двух бледных кнопок
    // внизу главное действие — выбрать цель в списке, о чём и говорит сова.
    val bottomBar: (@Composable () -> Unit)? = if (state.active == null) {
        null
    } else {
        {
            ButtonColumn {
                if (state.buyIsMain) {
                    FinnySecondaryButton(
                        text = stringResource(R.string.savings_deposit),
                        onClick = onDeposit,
                        enabled = state.canDeposit,
                    )
                } else {
                    FinnyButton(
                        text = stringResource(R.string.savings_deposit),
                        onClick = onDeposit,
                        enabled = state.canDeposit,
                    )
                }
                FinnySecondaryButton(
                    text = stringResource(R.string.savings_withdraw),
                    onClick = onWithdraw,
                    enabled = state.canWithdraw,
                )
            }
        }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        Screen(onBack = onBack, bottomBar = bottomBar) {
            // Кошелёк — строка с монетой, а не отдельная карточка (DESIGN_PLAN 3.7):
            // он нужен как источник «Отложить», а не как второй герой экрана.
            LabelledLine(label = stringResource(R.string.main_balance)) {
                MoneyAmount(
                    amount = state.balance,
                    modifier = Modifier.onGloballyPositioned { wallet = it.boundsInRoot().center },
                )
            }

            if (!state.canOperate) {
                PlanningHint(text = stringResource(R.string.savings_planning_hint), onPlan = onPlan)
            }

            val active = state.active
            if (active == null) {
                SpeechBubble(owl = state.owl, text = stringResource(R.string.savings_goal_none))
            } else {
                ActiveGoal(
                    state = state,
                    goal = active,
                    onBuy = onBuy,
                    onJarPlaced = { jar = it },
                )
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
        state.outcome?.let { outcome ->
            Outcome(outcome = outcome, owl = state.reactionOwl, from = wallet, to = jar, onDismiss = onDismiss)
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
}

/**
 * Выбранная цель — герой экрана (DESIGN_PLAN 3.7): тарелка 72 dp, большая
 * банка с уровнем и рядом сова, «Накоплено 6 из 40», остаток и срок словами
 * под банкой — всё, что требует ТЗ 2.5.7 показать ребёнку о цели.
 */
@Composable
private fun ActiveGoal(
    state: SavingsState.Ready,
    goal: GoalView,
    onBuy: () -> Unit,
    onJarPlaced: (Offset) -> Unit,
) {
    var askingBuy by rememberSaveable { mutableStateOf(false) }
    FinnyCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMedium),
        ) {
            ItemIcon(icon = goal.icon, category = SpendCategory.SAVINGS, large = true)
            Column(
                verticalArrangement = Arrangement.spacedBy(Dimens.SpaceTiny),
                modifier = Modifier.weight(1f),
            ) {
                Text(text = goal.title, style = MaterialTheme.typography.titleLarge)
                LabelledLine(label = stringResource(R.string.savings_price), amount = goal.price)
            }
        }
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(Dimens.Space, Alignment.CenterHorizontally),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Jar(
                level = goal.fraction,
                color = SpendCategory.SAVINGS.fill,
                modifier = Modifier
                    .size(HERO_JAR_WIDTH, HERO_JAR_HEIGHT)
                    .onGloballyPositioned { onJarPlaced(it.boundsInRoot().center) },
            )
            Owl(look = state.owl, size = OwlRole.Standalone.size)
        }
        Text(
            text = stringResource(R.string.progress_goal_saved, goal.saved.amount, goal.price.amount),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        if (goal.isReached) {
            FinnyButton(
                text = stringResource(R.string.savings_buy, goal.buyTitle),
                onClick = { askingBuy = true },
                enabled = state.canBuy,
                modifier = Modifier.fillMaxWidth(),
            )
            // Кнопка неактивна до плана дня (canBuy = canOperate && isReached) — без
            // строки ребёнок жмёт «Собрано!» и не понимает, почему ничего не происходит.
            if (!state.canBuy) {
                Text(
                    text = stringResource(R.string.savings_buy_needs_plan),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
            // «Набор комиксов — покупаем?»: название в начале фразы, с большой буквы.
            title = stringResource(R.string.savings_buy_title, goal.title),
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

/**
 * Большая банка цели: пропорции 7:9, как у банок плана, чтобы крышка и
 * уровень не поплыли; крупнее них — здесь банка одна и она главная.
 */
private val HERO_JAR_WIDTH = 112.dp
private val HERO_JAR_HEIGHT = 144.dp

/**
 * Цель в списке — белая строка с тарелкой и ценой (DESIGN_PLAN 3.7), кнопка
 * выбора. Выбранная — рамкой цвета копилки и словом «Выбрана»: не только
 * цветом (ТЗ 3.6). Накопленное показывается у каждой: отложенное на прежнюю
 * цель не пропадает из виду при смене.
 *
 * Купленную цель нельзя выбрать снова, пока есть некупленная (`selectable`
 * приходит из [SavingsState.Ready.canChoose]) — иначе один клик стирал бы
 * выбор ещё не собранной цели. Когда куплены все, тот же ряд снова кликабелен:
 * это единственный путь копить дальше (L5).
 */
@Composable
private fun GoalRow(goal: GoalView, selectable: Boolean, onClick: () -> Unit) {
    val mark = if (goal.isActive) {
        Modifier.border(Dimens.ButtonBorderWidth, SpendCategory.SAVINGS.fill, RoundedCornerShape(Dimens.CornerCard))
    } else {
        Modifier
    }
    FinnyCard(
        onClick = onClick,
        enabled = selectable,
        modifier = Modifier
            .defaultMinSize(minHeight = Dimens.TouchTarget)
            .then(mark),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSmall),
            modifier = Modifier.fillMaxWidth(),
        ) {
            ItemIcon(icon = goal.icon, category = SpendCategory.SAVINGS)
            Column(
                verticalArrangement = Arrangement.spacedBy(Dimens.SpaceTiny),
                modifier = Modifier.weight(1f),
            ) {
                Text(text = goal.title, style = MaterialTheme.typography.titleMedium)
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
                        color = if (goal.isActive) SpendCategory.SAVINGS.color else MaterialTheme.colorScheme.onSurfaceVariant,
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

/**
 * Сумма в окне — крупным числом с монетой, по бокам «−» и «+» 48 dp
 * (DESIGN_PLAN 3.7), как у ползунков плана. Число — живая область: после
 * «+» TalkBack сам скажет новую сумму, иначе ребёнок нажимает вслепую.
 */
@Composable
private fun AmountStepper(
    draft: SavingsDraft,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
) {
    val spoken = coinsText(draft.amount)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSmall),
        modifier = Modifier.fillMaxWidth(),
    ) {
        StepButton(
            icon = FinnyIcons.Minus,
            description = stringResource(R.string.savings_amount_less),
            enabled = draft.canRemove,
            onClick = onRemove,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSmall, Alignment.CenterHorizontally),
            modifier = Modifier
                .weight(1f)
                .clearAndSetSemantics {
                    contentDescription = spoken
                    liveRegion = LiveRegionMode.Polite
                },
        ) {
            Coin(size = STEPPER_COIN)
            // При шрифте 2,0 трёхзначная сумма не помещается между «−» и «+»:
            // число уменьшается до ширины, а не обрезается (ТЗ 3.6).
            Text(
                text = draft.amount.amount.toString(),
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(
                    minFontSize = MaterialTheme.typography.titleLarge.fontSize,
                    maxFontSize = MaterialTheme.typography.displayMedium.fontSize,
                ),
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        StepButton(
            icon = FinnyIcons.Plus,
            description = stringResource(R.string.savings_amount_more),
            enabled = draft.canAdd,
            onClick = onAdd,
        )
    }
}

/** Монета у крупного числа суммы — та же, что у живого счётчика плана (DESIGN_PLAN 3.2). */
private val STEPPER_COIN = 36.dp

/**
 * Итог операции. После «Отложить» монеты сначала летят из кошелька в банку
 * цели, а «Готово!» с радостной совой — когда они легли (DESIGN_PLAN 2.7, 3.7):
 * окно поверх полёта спрятало бы, куда ушли монеты. Полёт один раз на
 * пополнение — после поворота экрана монеты уже в банке. Без движения полёта
 * нет, и окно открывается сразу; снятие и покупка цели не летят.
 */
@Composable
private fun Outcome(outcome: SavingsOutcomeView, owl: OwlLook, from: Offset?, to: Offset?, onDismiss: () -> Unit) {
    var landed by rememberSaveable(outcome.number) { mutableStateOf(!outcome.intoJar) }
    when {
        landed -> OutcomeDialog(outcome = outcome, owl = owl, onDismiss = onDismiss)
        from != null && to != null -> CoinFlight(from = from, to = listOf(to), onFinished = { landed = true })
        // Банки или кошелька не видно в разметке — лететь неоткуда, итог важнее полёта.
        else -> LaunchedEffect(Unit) { landed = true }
    }
}

@Composable
private fun OutcomeDialog(outcome: SavingsOutcomeView, owl: OwlLook, onDismiss: () -> Unit) {
    FinnyDialog(
        title = stringResource(
            if (outcome.goalReached) R.string.main_goal_reached else R.string.savings_done_title
        ),
        onDismiss = onDismiss,
        buttons = {
            FinnyButton(text = stringResource(R.string.action_ok), onClick = onDismiss)
        },
    ) {
        // Показатели копилка не меняет — прыжок не про рост, а про сам факт успеха (U2).
        Owl(look = owl, size = OwlRole.Dialog.size, reactOnAppear = true, modifier = Modifier.align(Alignment.CenterHorizontally))
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
