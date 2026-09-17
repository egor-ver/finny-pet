package ru.finnypet.app.ui.screens.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.finnypet.app.R
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.SpendCategory
import ru.finnypet.app.ui.components.ButtonColumn
import ru.finnypet.app.ui.components.FinnyButton
import ru.finnypet.app.ui.components.FinnyScaffold
import ru.finnypet.app.ui.components.FinnySecondaryButton
import ru.finnypet.app.ui.components.MoneyAmount
import ru.finnypet.app.ui.components.MoneyCard
import ru.finnypet.app.ui.theme.Dimens

/**
 * План личного бюджета (ТЗ 2.5.5).
 *
 * До подтверждения ребёнок распределяет доступную сумму по трём направлениям и
 * видит остаток. После подтверждения тот же экран показывает, как план сходится
 * с настоящими тратами.
 */
@Composable
fun BudgetScreen(
    onBack: () -> Unit,
    viewModel: BudgetViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    BudgetContent(
        state = state,
        onBack = onBack,
        onAdd = viewModel::add,
        onRemove = viewModel::remove,
        onConfirm = viewModel::confirm,
        onRetry = viewModel::retry,
    )
}

@Composable
fun BudgetContent(
    state: BudgetState,
    onBack: () -> Unit,
    onAdd: (SpendCategory) -> Unit = {},
    onRemove: (SpendCategory) -> Unit = {},
    onConfirm: () -> Unit = {},
    onRetry: () -> Unit = {},
) {
    when (state) {
        BudgetState.Loading -> Screen(onBack = onBack) {}

        BudgetState.Failed -> Screen(
            onBack = onBack,
            bottomBar = {
                ButtonColumn {
                    FinnyButton(text = stringResource(R.string.action_retry), onClick = onRetry)
                    BackButton(onBack)
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
            onAdd = onAdd,
            onRemove = onRemove,
            onConfirm = onConfirm,
        )

        is BudgetState.Started -> Started(state = state, onBack = onBack)
    }
}

@Composable
private fun Screen(
    onBack: () -> Unit,
    bottomBar: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    FinnyScaffold(
        title = stringResource(R.string.budget_title),
        onBack = onBack,
        bottomBar = bottomBar,
        spacing = Dimens.SpaceMedium,
        content = content,
    )
}

@Composable
private fun BackButton(onBack: () -> Unit) {
    FinnySecondaryButton(text = stringResource(R.string.action_back), onClick = onBack)
}

@Composable
private fun Planning(
    state: BudgetState.Planning,
    onBack: () -> Unit,
    onAdd: (SpendCategory) -> Unit,
    onRemove: (SpendCategory) -> Unit,
    onConfirm: () -> Unit,
) {
    Screen(
        onBack = onBack,
        bottomBar = {
            ButtonColumn {
                FinnyButton(
                    text = stringResource(R.string.budget_confirm),
                    onClick = onConfirm,
                    enabled = state.canConfirm,
                )
                BackButton(onBack)
            }
        },
    ) {
        MoneyCard(
            label = stringResource(R.string.budget_available),
            amount = state.available,
        )

        SpendCategory.entries.forEach { category ->
            CategoryRow(
                category = category,
                amount = state.plan.amountFor(category),
                canAdd = state.canAdd(),
                canRemove = state.canRemove(category),
                onAdd = { onAdd(category) },
                onRemove = { onRemove(category) },
            )
        }

        Remainder(state = state)

        Text(
            text = stringResource(R.string.budget_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Одно направление: название, сумма и две кнопки.
 *
 * Суммы набираются кнопками, а не с клавиатуры: у семилетнего промах по цифре
 * ломает весь план, а лишний ноль превращает сорок монет в четыреста. Плюс
 * гаснет, когда распределять больше нечего — так ТЗ 2.5.5 требует не пускать
 * за пределы доступной суммы.
 */
@Composable
private fun CategoryRow(
    category: SpendCategory,
    amount: Coins,
    canAdd: Boolean,
    canRemove: Boolean,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
) {
    val title = stringResource(category.label)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSmall),
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(Dimens.Corner),
            )
            .padding(horizontal = Dimens.SpaceMedium, vertical = Dimens.SpaceSmall),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
            )
            // Подпись для озвучки склеивает название с суммой: иначе читается
            // «Нужное», потом отдельно «двадцать монет», и связь теряется.
            val spoken = stringResource(R.string.budget_amount, title, amount.amount)
            Row(modifier = Modifier.clearAndSetSemantics { contentDescription = spoken }) {
                MoneyAmount(amount = amount)
            }
        }
        StepButton(
            symbol = "−",
            description = stringResource(R.string.budget_remove, title),
            enabled = canRemove,
            onClick = onRemove,
        )
        StepButton(
            symbol = "+",
            description = stringResource(R.string.budget_add, title),
            enabled = canAdd,
            onClick = onAdd,
        )
    }
}

@Composable
private fun StepButton(
    symbol: String,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    OutlinedIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .defaultMinSize(minWidth = Dimens.TouchTarget, minHeight = Dimens.TouchTarget)
            .semantics { contentDescription = description },
    ) {
        // Знак скрыт от озвучки: подпись кнопки уже говорит, что она делает,
        // а «плюс» отдельной остановкой только мешает. Прятать через
        // clearAndSetSemantics на самой кнопке нельзя — вместе со знаком
        // пропадёт и признак «недоступна».
        Text(
            text = symbol,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

/**
 * Остаток. ТЗ 2.5.5 требует показывать его всегда, а не только когда он есть:
 * ноль — это тоже ответ, и ребёнок должен видеть, что монет больше не осталось.
 */
@Composable
private fun Remainder(state: BudgetState.Planning) {
    when {
        state.overBy > Coins.ZERO -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSmall),
            modifier = Modifier.semantics(mergeDescendants = true) {},
        ) {
            Text(
                text = stringResource(R.string.budget_over),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error,
            )
            MoneyAmount(amount = state.overBy)
        }

        state.isDistributed -> Text(
            text = stringResource(R.string.budget_distributed),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )

        else -> MoneyCard(
            label = stringResource(R.string.budget_remainder),
            amount = state.remainder,
        )
    }
}

@Composable
private fun Started(state: BudgetState.Started, onBack: () -> Unit) {
    Screen(
        onBack = onBack,
        bottomBar = { ButtonColumn { BackButton(onBack) } },
    ) {
        Text(
            text = stringResource(R.string.budget_started),
            style = MaterialTheme.typography.bodyLarge,
        )

        state.lines.forEach { line -> ComparisonRow(line = line) }

        MoneyCard(label = stringResource(R.string.budget_planned), amount = state.planTotal)
        MoneyCard(label = stringResource(R.string.budget_fact), amount = state.factTotal)
    }
}

/**
 * План против факта по одному направлению.
 *
 * Соблюдение плана показано не цветом, а словами «по плану» и «потрачено» с
 * числами: ТЗ 3.6 запрещает передавать смысл одним цветом.
 */
@Composable
private fun ComparisonRow(line: BudgetLine) {
    val title = stringResource(line.category.label)
    val spoken = stringResource(
        R.string.budget_line,
        title,
        line.planned.amount,
        line.actual.amount,
    )
    Column(
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceTiny),
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = spoken }
            .background(
                MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(Dimens.Corner),
            )
            .padding(horizontal = Dimens.SpaceMedium, vertical = Dimens.SpaceSmall),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (line.followed) FontWeight.SemiBold else FontWeight.Normal,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(Dimens.Space),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Labelled(label = stringResource(R.string.budget_planned), amount = line.planned)
            Labelled(label = stringResource(R.string.budget_fact), amount = line.actual)
        }
    }
}

@Composable
private fun Labelled(label: String, amount: Coins) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceTiny),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        MoneyAmount(amount = amount, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Названия направлений одни и те же на знакомстве, в плане и в магазине. */
internal val SpendCategory.label: Int
    get() = when (this) {
        SpendCategory.MANDATORY -> R.string.category_mandatory
        SpendCategory.OPTIONAL -> R.string.category_optional
        SpendCategory.SAVINGS -> R.string.category_savings
    }
