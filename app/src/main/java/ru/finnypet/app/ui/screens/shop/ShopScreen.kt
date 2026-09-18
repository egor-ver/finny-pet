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
import ru.finnypet.app.domain.model.PetEffect
import ru.finnypet.app.domain.model.PetStatKind
import ru.finnypet.app.domain.model.RecoveryOption
import ru.finnypet.app.ui.components.ButtonColumn
import ru.finnypet.app.ui.components.FinnyButton
import ru.finnypet.app.ui.components.FinnyDialog
import ru.finnypet.app.ui.components.FinnyListScaffold
import ru.finnypet.app.ui.components.FinnyScaffold
import ru.finnypet.app.ui.components.FinnySecondaryButton
import ru.finnypet.app.ui.components.MoneyAmount
import ru.finnypet.app.ui.components.MoneyCard
import ru.finnypet.app.ui.screens.budget.label
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
    viewModel: ShopViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ShopContent(
        state = state,
        onBack = onBack,
        onPlan = onPlan,
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
                    FinnySecondaryButton(text = stringResource(R.string.action_back), onClick = onBack)
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
    onBuy: (ItemId) -> Unit,
    onDismiss: () -> Unit,
) {
    // Товар, который ребёнок собрался купить. Живёт в экране, а не во
    // вьюмодели: это ещё не действие, а вопрос. Поворот переживает.
    var pendingId by rememberSaveable { mutableStateOf<String?>(null) }
    val pending = state.items.firstOrNull { it.id.value == pendingId }

    FinnyListScaffold(title = stringResource(R.string.shop_title), onBack = onBack) {
        item(key = "balance") {
            MoneyCard(label = stringResource(R.string.main_balance), amount = state.balance)
        }
        if (!state.canBuy) {
            item(key = "planning") { PlanningHint(onPlan = onPlan) }
        }
        if (state.items.isEmpty()) {
            item(key = "empty") {
                Text(
                    text = stringResource(R.string.shop_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(state.items, key = { it.id.value }) { item ->
            ShopItemRow(
                item = item,
                enabled = state.canBuy,
                onClick = { pendingId = item.id.value },
            )
        }
    }

    if (pending != null) {
        ConfirmDialog(
            item = pending,
            onConfirm = {
                pendingId = null
                onBuy(pending.id)
            },
            onDismiss = { pendingId = null },
        )
    }
    state.outcome?.let { OutcomeDialog(outcome = it, onDismiss = onDismiss) }
}

/**
 * Пока день планируется, покупать нельзя — и ребёнку нужен не запрет, а
 * дорога: кнопка ведёт прямо в план (ТЗ 3.4 запрещает тупики).
 */
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
            text = stringResource(R.string.shop_planning_hint),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        FinnyButton(text = stringResource(R.string.budget_action_plan), onClick = onPlan)
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
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSmall),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.Corner))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .defaultMinSize(minHeight = Dimens.TouchTarget)
            .padding(horizontal = Dimens.Space, vertical = Dimens.SpaceMedium),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceTiny),
            modifier = Modifier.weight(1f),
        ) {
            Text(text = item.title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = stringResource(item.category.label),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            item.effects.forEach { effect -> EffectLine(effect) }
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
            FinnySecondaryButton(text = stringResource(R.string.shop_not_now), onClick = onDismiss)
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
        Text(
            text = stringResource(item.category.label),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (item.effects.isNotEmpty()) {
            Text(text = stringResource(R.string.shop_pet_change), style = MaterialTheme.typography.titleMedium)
            item.effects.forEach { effect -> EffectLine(effect) }
        }
    }
}

@Composable
private fun OutcomeDialog(outcome: PurchaseOutcome, onDismiss: () -> Unit) {
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
            outcome.effects.forEach { effect -> EffectLine(effect) }
        }

        is PurchaseOutcome.Rejected -> RejectedDialog(outcome = outcome, onDismiss = onDismiss)
    }
}

/**
 * Отказ с объяснением и вариантами выхода (ТЗ 2.5.6, 2.5.9).
 *
 * Кнопками становятся только варианты, у которых есть куда вести: «купить
 * попозже» и «выбрать подешевле» возвращают к списку. Задание и копилка
 * показываются подсказкой и станут кнопками вместе со своими экранами —
 * кнопка в пустоту была бы тупиком (ТЗ 3.4).
 */
@Composable
private fun RejectedDialog(outcome: PurchaseOutcome.Rejected, onDismiss: () -> Unit) {
    val (closing, hints) = outcome.options.partition { it.option.closesDialog }
    FinnyDialog(
        title = stringResource(R.string.shop_rejected_title),
        onDismiss = onDismiss,
        buttons = {
            closing.forEachIndexed { index, choice ->
                if (index == 0) {
                    FinnyButton(text = choice.label, onClick = onDismiss)
                } else {
                    FinnySecondaryButton(text = choice.label, onClick = onDismiss)
                }
            }
            if (closing.isEmpty()) {
                FinnyButton(text = stringResource(R.string.action_ok), onClick = onDismiss)
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

/** «Сытость +20»: показатель словом и знак числом — цвет здесь не нужен вовсе. */
@Composable
private fun EffectLine(effect: PetEffect) {
    Text(
        text = stringResource(
            R.string.shop_effect,
            stringResource(effect.stat.label),
            "%+d".format(effect.delta),
        ),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private val RecoveryOption.closesDialog: Boolean
    get() = when (this) {
        RecoveryOption.POSTPONE_PURCHASE, RecoveryOption.CHOOSE_CHEAPER -> true
        RecoveryOption.DO_TASK, RecoveryOption.WITHDRAW_FROM_SAVINGS, RecoveryOption.ADJUST_NEXT_PLAN -> false
    }

private val PetStatKind.label: Int
    get() = when (this) {
        PetStatKind.MOOD -> R.string.stat_mood
        PetStatKind.SATIETY -> R.string.stat_satiety
        PetStatKind.CARE -> R.string.stat_care
    }
