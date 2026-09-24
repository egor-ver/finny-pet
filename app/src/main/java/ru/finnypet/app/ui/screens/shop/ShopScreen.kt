package ru.finnypet.app.ui.screens.shop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.finnypet.app.R
import ru.finnypet.app.domain.model.ItemId
import ru.finnypet.app.domain.model.PetStatKind
import ru.finnypet.app.domain.model.RecoveryOption
import ru.finnypet.app.ui.components.ButtonColumn
import ru.finnypet.app.ui.components.CategoryLabel
import ru.finnypet.app.ui.components.FinnyButton
import ru.finnypet.app.ui.components.FinnyDialog
import ru.finnypet.app.ui.components.FinnyListScaffold
import ru.finnypet.app.ui.components.FinnyScaffold
import ru.finnypet.app.ui.components.FinnySecondaryButton
import ru.finnypet.app.ui.components.ItemIcon
import ru.finnypet.app.ui.components.MoneyAmount
import ru.finnypet.app.ui.components.MoneyCard
import ru.finnypet.app.ui.components.PlanningHint
import ru.finnypet.app.ui.components.StatChangeLine
import ru.finnypet.app.ui.components.StatEffectLine
import ru.finnypet.app.ui.components.label
import ru.finnypet.app.ui.theme.Dimens

/**
 * Магазин (ТЗ 2.5.6): товары с ценой, направлением и влиянием на питомца.
 * Покупка подтверждается, итог — покупка или отказ — объясняется словами
 * из контент-пака.
 */
@Composable
fun ShopScreen(
    onBack: () -> Unit,
    onPlan: () -> Unit,
    onSavings: () -> Unit,
    onTasks: () -> Unit,
    viewModel: ShopViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ShopContent(
        state = state,
        onBack = onBack,
        onPlan = onPlan,
        onSavings = onSavings,
        onTasks = onTasks,
        onBuy = viewModel::buy,
        onDismiss = viewModel::dismiss,
        onRetry = viewModel::retry,
    )
}

@Composable
fun ShopContent(
    state: ShopState,
    onBack: () -> Unit,
    onPlan: () -> Unit = {},
    onSavings: () -> Unit = {},
    onTasks: () -> Unit = {},
    onBuy: (ItemId) -> Unit = {},
    onDismiss: () -> Unit = {},
    onRetry: () -> Unit = {},
) {
    when (state) {
        ShopState.Loading -> FinnyScaffold(title = stringResource(R.string.shop_title), onBack = onBack) {}

        ShopState.Failed -> FinnyScaffold(
            title = stringResource(R.string.shop_title),
            onBack = onBack,
            bottomBar = {
                ButtonColumn {
                    FinnyButton(text = stringResource(R.string.action_retry), onClick = onRetry)
                }
            },
        ) {
            Text(
                text = stringResource(R.string.shop_failed),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
            )
        }

        is ShopState.Ready -> Ready(
            state = state,
            onBack = onBack,
            onPlan = onPlan,
            onSavings = onSavings,
            onTasks = onTasks,
            onBuy = onBuy,
            onDismiss = onDismiss,
        )
    }
}

@Composable
private fun Ready(
    state: ShopState.Ready,
    onBack: () -> Unit,
    onPlan: () -> Unit,
    onSavings: () -> Unit,
    onTasks: () -> Unit,
    onBuy: (ItemId) -> Unit,
    onDismiss: () -> Unit,
) {
    // Товар, который ребёнок собрался купить. Живёт в экране, а не во
    // вьюмодели: это ещё не действие, а вопрос. Поворот переживает.
    var pendingId by rememberSaveable { mutableStateOf<String?>(null) }
    val pending = state.items.firstOrNull { it.id.value == pendingId }

    // Ключи с префиксами: идентификаторы товаров пишет напарник в shop.json,
    // и товар с id «balance» иначе столкнулся бы с шапкой списка.
    FinnyListScaffold(title = stringResource(R.string.shop_title), onBack = onBack) {
        item(key = "header:balance") {
            MoneyCard(label = stringResource(R.string.main_balance), amount = state.balance)
        }
        if (!state.canBuy) {
            item(key = "header:planning") { PlanningHint(text = stringResource(R.string.shop_planning_hint), onPlan = onPlan) }
        }
        if (state.items.isEmpty()) {
            item(key = "header:empty") {
                Text(
                    text = stringResource(R.string.shop_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(state.items, key = { "item:${it.id.value}" }) { item ->
            ShopItemRow(item = item, onClick = { pendingId = item.id.value })
        }
    }

    when {
        pending == null -> Unit

        // Пока день планируется, нажатие на товар — не немой тупик, а та же
        // дорога в план, что и в подсказке сверху (ТЗ 3.4).
        !state.canBuy -> PlanningDialog(
            onPlan = {
                pendingId = null
                onPlan()
            },
            onDismiss = { pendingId = null },
        )

        else -> ConfirmDialog(
            item = pending,
            onConfirm = {
                pendingId = null
                onBuy(pending.id)
            },
            onDismiss = { pendingId = null },
        )
    }
    state.outcome?.let { OutcomeDialog(outcome = it, onDismiss = onDismiss, onSavings = onSavings, onTasks = onTasks) }
}


@Composable
private fun PlanningDialog(onPlan: () -> Unit, onDismiss: () -> Unit) {
    FinnyDialog(
        title = stringResource(R.string.budget_title),
        onDismiss = onDismiss,
        buttons = {
            FinnyButton(text = stringResource(R.string.budget_action_plan), onClick = onPlan)
            FinnySecondaryButton(text = stringResource(R.string.action_not_now), onClick = onDismiss)
        },
    ) {
        Text(text = stringResource(R.string.shop_planning_hint), style = MaterialTheme.typography.bodyLarge)
    }
}

/**
 * Строка товара — вся целиком кнопка: по ней проще попасть, чем по маленькой
 * «Купить» справа, и озвучка читает её одной фразой — название, направление,
 * влияние, цена.
 */
@Composable
private fun ShopItemRow(
    item: ShopItemView,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSmall),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.Corner))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(role = Role.Button, onClick = onClick)
            .defaultMinSize(minHeight = Dimens.TouchTarget)
            .padding(horizontal = Dimens.Space, vertical = Dimens.SpaceMedium),
    ) {
        ItemIcon(icon = item.icon)
        Column(
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceTiny),
            modifier = Modifier.weight(1f),
        ) {
            Text(text = item.title, style = MaterialTheme.typography.titleMedium)
            CategoryLabel(category = item.category)
            item.effects.forEach { effect -> StatEffectLine(effect = effect) }
        }
        MoneyAmount(amount = item.price)
    }
}

/** Подтверждение: что покупаем, за сколько и что от этого изменится. */
@Composable
private fun ConfirmDialog(
    item: ShopItemView,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    FinnyDialog(
        title = item.title,
        onDismiss = onDismiss,
        buttons = {
            FinnyButton(text = stringResource(R.string.shop_buy), onClick = onConfirm)
            FinnySecondaryButton(text = stringResource(R.string.action_not_now), onClick = onDismiss)
        },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSmall),
            modifier = Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {},
        ) {
            Text(
                text = stringResource(R.string.shop_price),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            MoneyAmount(amount = item.price)
        }
        CategoryLabel(category = item.category)
        if (item.effects.isNotEmpty()) {
            Text(text = stringResource(R.string.shop_pet_change), style = MaterialTheme.typography.titleMedium)
            item.effects.forEach { effect -> StatEffectLine(effect = effect) }
        }
    }
}

@Composable
private fun OutcomeDialog(
    outcome: PurchaseOutcome,
    onDismiss: () -> Unit,
    onSavings: () -> Unit,
    onTasks: () -> Unit,
) {
    when (outcome) {
        is PurchaseOutcome.Done -> FinnyDialog(
            title = stringResource(R.string.shop_done_title),
            onDismiss = onDismiss,
            buttons = {
                FinnyButton(text = stringResource(R.string.action_ok), onClick = onDismiss)
            },
        ) {
            Text(text = outcome.title, style = MaterialTheme.typography.titleMedium)
            Text(text = outcome.text, style = MaterialTheme.typography.bodyLarge)
            // Показываем, что изменилось на самом деле. Если товар влияет,
            // а показатель упёрся в границу — так и говорим, а не «+15».
            outcome.changes.forEach { change -> StatChangeLine(change = change) }
            if (outcome.effects.isNotEmpty() && outcome.changes.isEmpty()) {
                Text(
                    text = stringResource(R.string.shop_no_change),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        is PurchaseOutcome.Rejected -> RejectedDialog(
            outcome = outcome,
            onDismiss = onDismiss,
            onSavings = onSavings,
            onTasks = onTasks,
        )

        is PurchaseOutcome.NotInPlan -> FinnyDialog(
            title = stringResource(R.string.shop_not_in_plan_title),
            onDismiss = onDismiss,
            buttons = {
                FinnyButton(text = stringResource(R.string.action_ok), onClick = onDismiss)
            },
        ) {
            Text(text = outcome.title, style = MaterialTheme.typography.titleMedium)
            Text(text = outcome.text, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/**
 * Отказ с объяснением и вариантами выхода (ТЗ 2.5.6, 2.5.9).
 *
 * Все три пути восстановления — кнопки: «выполнить задание» ведёт в задания,
 * «взять из копилки» — в копилку, «купить попозже» и «выбрать подешевле»
 * возвращают к списку. Главная кнопка — вариант, который рекомендует домен;
 * без экрана вариант показывался бы подсказкой, кнопка в пустоту — тупик
 * (ТЗ 3.4).
 */
@Composable
private fun RejectedDialog(
    outcome: PurchaseOutcome.Rejected,
    onDismiss: () -> Unit,
    onSavings: () -> Unit,
    onTasks: () -> Unit,
) {
    val (actionable, hints) = outcome.options.partition { it.option.action != RecoveryAction.Hint }
    val primary = actionable.firstOrNull { it.option == outcome.recommended } ?: actionable.firstOrNull()
    val act: (RecoveryChoice) -> Unit = { choice ->
        onDismiss()
        when (choice.option.action) {
            RecoveryAction.Savings -> onSavings()
            RecoveryAction.Tasks -> onTasks()
            RecoveryAction.Close, RecoveryAction.Hint -> Unit
        }
    }
    FinnyDialog(
        title = stringResource(R.string.shop_rejected_title),
        onDismiss = onDismiss,
        buttons = {
            // Домен всегда добавляет «выбрать подешевле», так что кнопка есть.
            // Запасная — на случай, если это правило когда-нибудь изменится:
            // окно без кнопки было бы тупиком.
            if (primary == null) {
                FinnyButton(text = stringResource(R.string.action_ok), onClick = onDismiss)
            } else {
                FinnyButton(text = primary.label, onClick = { act(primary) })
            }
            actionable.filter { it != primary }.forEach { choice ->
                FinnySecondaryButton(text = choice.label, onClick = { act(choice) })
            }
        },
    ) {
        Text(text = outcome.title, style = MaterialTheme.typography.titleMedium)
        Text(text = outcome.text, style = MaterialTheme.typography.bodyLarge)
        if (hints.isNotEmpty()) {
            Text(text = stringResource(R.string.shop_options), style = MaterialTheme.typography.titleMedium)
            hints.forEach { choice ->
                Text(
                    text = stringResource(R.string.shop_option_hint, choice.label),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

/**
 * «Сытость +20»: показатель словом и знак числом — цвет здесь не нужен вовсе.
 *
 * Знак ставится руками, а не через `%+d`: тот форматирует по локали
 * устройства и на арабской подставил бы свои цифры рядом с нашими.
 */

/** Куда ведёт вариант выхода: назад к списку, в копилку, в задания или пока никуда. */
private enum class RecoveryAction { Close, Savings, Tasks, Hint }

private val RecoveryOption.action: RecoveryAction
    get() = when (this) {
        RecoveryOption.POSTPONE_PURCHASE, RecoveryOption.CHOOSE_CHEAPER -> RecoveryAction.Close
        RecoveryOption.WITHDRAW_FROM_SAVINGS -> RecoveryAction.Savings
        RecoveryOption.DO_TASK -> RecoveryAction.Tasks
        // Пересмотр плана — совет на завтра, экрана у него нет.
        RecoveryOption.ADJUST_NEXT_PLAN -> RecoveryAction.Hint
    }
