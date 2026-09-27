package ru.finnypet.app.ui.screens.budget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.finnypet.app.R
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.SpendCategory
import ru.finnypet.app.ui.components.BudgetLine
import ru.finnypet.app.ui.components.ButtonColumn
import ru.finnypet.app.ui.components.CoinFlight
import ru.finnypet.app.ui.components.FinnyButton
import ru.finnypet.app.ui.components.FinnyDialog
import ru.finnypet.app.ui.components.FinnyScaffold
import ru.finnypet.app.ui.components.FinnySecondaryButton
import ru.finnypet.app.ui.components.Jar
import ru.finnypet.app.ui.components.MoneyAmount
import ru.finnypet.app.ui.components.PlanEditor
import ru.finnypet.app.ui.components.RemainderCounter
import ru.finnypet.app.ui.components.SpeechBubble
import ru.finnypet.app.ui.components.coinsText
import ru.finnypet.app.ui.components.color
import ru.finnypet.app.ui.components.fill
import ru.finnypet.app.ui.components.label
import ru.finnypet.app.ui.components.liveRemainder
import ru.finnypet.app.ui.components.rememberPlanDrafts
import ru.finnypet.app.ui.theme.Dimens

/**
 * План личного бюджета (ТЗ 2.5.5).
 *
 * До подтверждения ребёнок распределяет доступную сумму по трём направлениям и
 * видит остаток. После подтверждения тот же экран показывает, сколько
 * осталось в каждой банке.
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
    // Выше `when`: подтверждение нажато на экране планирования, а монеты
    // летят уже на экране банок, который приходит следом из базы.
    var justConfirmed by remember { mutableStateOf(false) }
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
            onConfirm = {
                justConfirmed = true
                onConfirm()
            },
            onSavings = onSavings,
        )

        is BudgetState.Started -> Started(
            state = state,
            onBack = onBack,
            onShop = onShop,
            flying = justConfirmed,
            onLanded = { justConfirmed = false },
        )
    }
}

@Composable
private fun Screen(
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: (@Composable () -> Unit)? = null,
    centered: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    FinnyScaffold(
        title = stringResource(R.string.budget_title),
        onBack = onBack,
        actions = actions,
        bottomBar = bottomBar,
        spacing = Dimens.SpaceMedium,
        centered = centered,
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
    val drafts = rememberPlanDrafts()
    val remainder = liveRemainder(state.plan, state.remainder, drafts)

    Screen(
        onBack = onBack,
        // Кошелёк в шапке: весь он и раскладывается (R5), а место под банки дорого.
        actions = {
            Box(modifier = Modifier.padding(end = Dimens.Space)) { MoneyAmount(amount = state.available) }
        },
        // Остаток закреплён над кнопкой, а не в конце списка: раньше кнопка
        // его перекрывала, и ребёнок не видел, сколько ещё раскладывать
        // (DESIGN_PLAN 1, №5).
        bottomBar = {
            ButtonColumn {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceTiny)) {
                    RemainderCounter(remainder = remainder, overBy = state.overBy)
                    // План — только решение: монеты ещё не потрачены, поэтому
                    // кошелёк в шапке до подтверждения не уменьшается.
                    Text(
                        text = stringResource(R.string.budget_wallet_unchanged),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
        SpeechBubble(owl = state.owl, text = state.phrase)

        PlanEditor(
            plan = state.plan,
            available = state.available,
            remainder = state.remainder,
            onSet = onSet,
            drafts = drafts,
            hints = state.hints,
            onChooseGoal = onSavings.takeUnless { state.hasGoal },
            mandatoryCover = state.mandatoryCover,
        )

        // Остаток — не ошибка: он переходит на завтра (R5).
        if (remainder > Coins.ZERO) {
            Text(
                text = stringResource(R.string.budget_left_for_tomorrow, coinsText(remainder)),
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
 * План после подтверждения: три нарисованные банки в ряд (DESIGN_PLAN 3.2),
 * уровень — сколько от плана ещё осталось, подпись — то же словами: цвет и
 * уровень не единственный признак (ТЗ 3.6).
 *
 * Сразу после подтверждения монеты летят от кнопки — того места, где стоял
 * счётчик остатка, — в банки, куда их разложили. Подтверждение к этому
 * моменту уже записано: полёт только показывает, куда ушли монеты, и уход с
 * экрана посреди него ничего не теряет.
 */
@Composable
private fun Started(
    state: BudgetState.Started,
    onBack: () -> Unit,
    onShop: () -> Unit,
    flying: Boolean,
    onLanded: () -> Unit,
) {
    val targets = remember { mutableStateMapOf<SpendCategory, Offset>() }
    var source by remember { mutableStateOf<Offset?>(null) }
    // Летят только в банки, куда что-то положили: монета в пустую банку
    // противоречила бы плану.
    val filled = state.lines.filter { it.planned > Coins.ZERO }.map { it.category }
    Box(modifier = Modifier.fillMaxSize()) {
        Screen(
            onBack = onBack,
            bottomBar = {
                ButtonColumn(modifier = Modifier.onGloballyPositioned { source = it.boundsInRoot().center }) {
                    FinnyButton(text = stringResource(R.string.budget_go_shop), onClick = onShop)
                }
            },
            // После подтверждения на экране только фраза и банки: прижатые к
            // верху, они оставляли над кнопкой пустую половину (DESIGN_PLAN 1, №6).
            centered = true,
        ) {
            Text(
                text = stringResource(R.string.budget_started),
                style = MaterialTheme.typography.bodyLarge,
            )
            // FlowRow: при крупном шрифте подписи не влезают втроём в 328 dp,
            // и третья банка уходит на новую строку, а не рвёт слова.
            FlowRow(
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalArrangement = Arrangement.spacedBy(Dimens.Space),
                modifier = Modifier.fillMaxWidth(),
            ) {
                state.lines.forEach { line ->
                    JarColumn(line = line, onPlaced = { targets[line.category] = it })
                }
            }
        }
        val from = source
        val to = filled.mapNotNull { targets[it] }
        // Ждём, пока разметка скажет, где кнопка и все банки: иначе монеты
        // полетели бы из угла экрана или не во все банки.
        if (flying && from != null && to.size == filled.size) {
            CoinFlight(from = from, to = to, onFinished = onLanded)
        }
    }
}

/**
 * Одна банка: рисунок, название и остаток. Траты — «осталось 23» с уровнем
 * «осталось / было»; копилка — «отложено 1» и монеты на дне без уровня.
 */
@Composable
private fun JarColumn(line: BudgetLine, onPlaced: (Offset) -> Unit) {
    val title = stringResource(line.category.label)
    val left = line.actual.shortfallTo(line.planned).amount
    val savings = line.category == SpendCategory.SAVINGS
    val status = if (savings) {
        stringResource(R.string.budget_jar_saved, line.actual.amount)
    } else {
        stringResource(R.string.budget_jar_left, left)
    }
    val spoken = if (savings) {
        stringResource(R.string.budget_jar_description, title, status)
    } else {
        stringResource(R.string.budget_jar_left_of, title, left, line.planned.amount)
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceTiny),
        modifier = Modifier
            .widthIn(min = JAR_COLUMN_MIN)
            .clearAndSetSemantics { contentDescription = spoken },
    ) {
        Jar(
            level = if (savings) 0f else jarLevel(line),
            color = line.category.fill,
            coins = if (savings) line.actual.amount.coerceAtMost(JAR_MAX_COINS) else 0,
            modifier = Modifier.onGloballyPositioned { onPlaced(it.boundsInRoot().center) },
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = line.category.color,
            textAlign = TextAlign.Center,
        )
        Text(
            text = status,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Уровень банки трат — доля плана, которая ещё осталась: полная утром,
 * пустеет с каждой покупкой. Перерасход — пустая банка, а не «минус».
 * Пустой план — пустая банка: делить на ноль нечего.
 */
internal fun jarLevel(line: BudgetLine): Float {
    if (line.planned == Coins.ZERO) return 0f
    return line.actual.shortfallTo(line.planned).amount.toFloat() / line.planned.amount
}

// Три колонки по 104 dp с зазорами влезают в 328 dp (экран 360 dp без полей):
// банки — главное на экране, но при обычном шрифте стоят в один ряд.
private val JAR_COLUMN_MIN = 104.dp
private const val JAR_MAX_COINS = 3
