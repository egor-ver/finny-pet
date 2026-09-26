package ru.finnypet.app.ui.screens.shop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.finnypet.app.R
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.ItemId
import ru.finnypet.app.domain.model.PetStatKind
import ru.finnypet.app.domain.model.RecoveryOption
import ru.finnypet.app.domain.model.SpendCategory
import ru.finnypet.app.ui.components.ButtonColumn
import ru.finnypet.app.ui.components.CategoryLabel
import ru.finnypet.app.ui.components.FinnyButton
import ru.finnypet.app.ui.components.FinnyCard
import ru.finnypet.app.ui.components.FinnyDialog
import ru.finnypet.app.ui.components.FinnyListScaffold
import ru.finnypet.app.ui.components.FinnyScaffold
import ru.finnypet.app.ui.components.FinnySecondaryButton
import ru.finnypet.app.ui.components.ItemIcon
import ru.finnypet.app.ui.components.LabelledLine
import ru.finnypet.app.ui.components.MoneyAmount
import ru.finnypet.app.ui.components.Owl
import ru.finnypet.app.ui.components.OwlLook
import ru.finnypet.app.ui.components.PlanningHint
import ru.finnypet.app.ui.components.StatChangeLine
import ru.finnypet.app.ui.components.StatEffectLine
import ru.finnypet.app.ui.components.coinsText
import ru.finnypet.app.ui.components.color
import ru.finnypet.app.ui.components.icon
import ru.finnypet.app.ui.components.label
import ru.finnypet.app.ui.screens.main.JarsLeft
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
    // и товар с id «owl» иначе столкнулся бы с шапкой списка.
    FinnyListScaffold(
        title = stringResource(R.string.shop_title),
        onBack = onBack,
        actions = {
            Box(modifier = Modifier.padding(end = Dimens.Space)) { MoneyAmount(amount = state.balance) }
        },
    ) {
        item(key = "header:owl") {
            OwlBubble(owl = state.owl, phrase = state.phrase, done = state.outcome as? PurchaseOutcome.Done)
        }
        state.jars?.let { jars -> item(key = "header:jars") { JarChips(jars = jars) } }
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
        // Секции по направлениям (раздел 8 плана), по две карточки в ряд:
        // при 16 sp в четверть ширины 360 dp не помещается ни название, ни метка.
        SpendCategory.entries.forEach { category ->
            val section = state.items.filter { it.category == category }
            if (section.isEmpty()) return@forEach
            item(key = "section:${category.name}") {
                CategoryLabel(category = category)
            }
            items(section.chunked(2), key = { row -> "row:" + row.joinToString { it.id.value } }) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSmall)) {
                    row.forEach { item ->
                        ShopCard(
                            item = item,
                            onClick = { pendingId = item.id.value },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                }
            }
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
            jars = state.jars,
            balance = state.balance,
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
 * Сова с облачком: что ей нужно сейчас, а после покупки — что купили, что
 * изменилось и сколько ушло монет (ТЗ 2.5.9). Изменения — настоящие, а не
 * обещанные: у верхней границы показатель не растёт, и так и сказано.
 */
@Composable
private fun OwlBubble(owl: OwlLook, phrase: String, done: PurchaseOutcome.Done?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSmall),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Owl(look = owl, size = 88.dp)
        Box(modifier = Modifier.weight(1f)) {
            FinnyCard {
                if (done != null) {
                    Text(text = done.title, style = MaterialTheme.typography.titleMedium)
                    Text(text = done.text, style = MaterialTheme.typography.bodyLarge)
                    // Игрушка — не просто цифры: короткая фраза показывает, что питомец играет с ней (U2, ТЗ 2.5.9).
                    done.toyPhrase?.let { toyPhrase ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSmall),
                        ) {
                            ItemIcon(icon = done.icon)
                            Text(text = toyPhrase, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    done.changes.forEach { change -> StatChangeLine(change = change) }
                    if (done.effects.isNotEmpty() && done.changes.isEmpty()) {
                        Text(
                            text = stringResource(R.string.shop_no_change),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = stringResource(R.string.shop_spent, coinsText(done.price)),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Text(text = phrase, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

/**
 * «🥣 Нужное: ещё 24» — сколько по плану ещё можно, как на главном.
 *
 * Столбцом, а не в ряд: в ряд без переноса при крупном шрифте вторая
 * подпись зажималась до ширины уже без места даже на одно слово и рвала
 * его посередине («Желаемо/е») — против ТЗ 3.6.
 */
@Composable
private fun JarChips(jars: JarsLeft) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSmall)) {
        JarChip(category = SpendCategory.MANDATORY, left = jars.mandatory)
        JarChip(category = SpendCategory.OPTIONAL, left = jars.optional)
    }
}

@Composable
private fun JarChip(category: SpendCategory, left: Coins) {
    Text(
        text = category.icon + " " + stringResource(R.string.shop_jar_left, stringResource(category.label), left.amount),
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.SemiBold,
        color = category.color,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(Dimens.Corner))
            .padding(horizontal = Dimens.SpaceMedium, vertical = Dimens.SpaceSmall),
    )
}

/**
 * Карточка товара — вся целиком кнопка: картинка, название, цена, влияние
 * и метка. Метка словами: цвет и приглушение не единственный признак (ТЗ 3.6).
 * Товар «не в плане» не приглушён: план мягкий (AD-4), покупка сверх него —
 * обычное решение, а не то, что нужно оправдывать видом карточки.
 */
@Composable
private fun ShopCard(item: ShopItemView, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceTiny),
        modifier = modifier
            .clip(RoundedCornerShape(Dimens.Corner))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(role = Role.Button, onClick = onClick)
            .defaultMinSize(minHeight = Dimens.TouchTarget)
            .padding(Dimens.SpaceMedium),
    ) {
        ItemIcon(icon = item.icon)
        Text(text = item.title, style = MaterialTheme.typography.titleMedium)
        MoneyAmount(amount = item.price)
        item.effects.forEach { effect -> StatEffectLine(effect = effect) }
        item.mark.label?.let { label ->
            Text(
                text = stringResource(label),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (item.mark == ItemMark.NEEDED_NOW) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val ItemMark.label: Int?
    get() = when (this) {
        ItemMark.NEEDED_NOW -> R.string.shop_mark_needed
        ItemMark.NOT_NEEDED -> R.string.shop_mark_not_needed
        ItemMark.NOT_IN_PLAN -> R.string.shop_mark_not_in_plan
        ItemMark.NONE -> null
    }

/**
 * Подтверждение: что покупаем, за сколько и что от этого изменится.
 *
 * Покупка сверх плана не блокируется отдельным окном (AD-4): здесь же, без
 * упрёка, видно, насколько это больше плана, что останется в кошельке и
 * хватит ли потом на нужное (раздел 3 плана, «доступность нужного»; ТЗ 2.5.9,
 * 8.4) — ребёнок решает сам, а не получает стену вместо покупки.
 */
@Composable
private fun ConfirmDialog(
    item: ShopItemView,
    jars: JarsLeft?,
    balance: Coins,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val overPlan = overPlanOf(item.price, item.category, jars)
    val balanceAfter = balance.takeIf { it.covers(item.price) }?.let { it - item.price }
    val needsShortfall = needsShortfallOf(balanceAfter, item.needsCostAfter)

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
        // Нужное, которое сове пока не нужно: купить можно, но сова спрашивает (R12).
        item.warning?.let { Text(text = it, style = MaterialTheme.typography.bodyLarge) }
        // Сверх плана — не запрет, а честная цифра рядом с ценой (AD-4, ТЗ 8.4).
        if (overPlan != null) LabelledLine(label = stringResource(R.string.shop_over_plan), amount = overPlan)
        if (balanceAfter != null) LabelledLine(label = stringResource(R.string.shop_balance_after), amount = balanceAfter)
        // Раздел 3 плана: предупреждение честное, но не пугает — просто цифра рядом с остатком.
        if (needsShortfall != null) LabelledLine(label = stringResource(R.string.shop_needs_shortfall), amount = needsShortfall)
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
        // Покупка показывается в облачке совы, окно не нужно.
        is PurchaseOutcome.Done -> Unit

        is PurchaseOutcome.Rejected -> RejectedDialog(
            outcome = outcome,
            onDismiss = onDismiss,
            onSavings = onSavings,
            onTasks = onTasks,
        )
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
 * «Еда +20»: показатель словом и знак числом — цвет здесь не нужен вовсе.
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
