package ru.finnypet.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import ru.finnypet.app.R
import ru.finnypet.app.domain.model.BudgetPlan
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.SpendCategory
import ru.finnypet.app.ui.theme.Dimens

/**
 * Банка редактора: направление расхода и подпись, если её задаёт задание.
 * [label] пустой — берётся общее название направления.
 */
data class PlanJar(
    val category: SpendCategory,
    val label: String?,
)

/**
 * Раскладка суммы по трём направлениям кнопками.
 *
 * Один и тот же редактор в плане дня и в задании «раздели монеты»: ребёнок
 * учится одному движению, а не двум. Суммы набираются кнопками, а не с
 * клавиатуры: у семилетнего промах по цифре ломает весь план, а лишний ноль
 * превращает сорок монет в четыреста. Полоса под суммой показывает, какая
 * доля от всего ушла в направление — «банка наполняется».
 *
 * Остаток показывается всегда (ТЗ 2.5.5): ноль — тоже ответ, ребёнок должен
 * видеть, что монет больше не осталось. Перебор назван словом и числом,
 * а не цветом.
 */
@Composable
fun PlanEditor(
    plan: BudgetPlan,
    available: Coins,
    remainder: Coins,
    overBy: Coins,
    canAdd: Boolean,
    canRemove: (SpendCategory) -> Boolean,
    onAdd: (SpendCategory) -> Unit,
    onRemove: (SpendCategory) -> Unit,
    /**
     * Банки задания: свои подписи и свой порядок. Пусто — план дня, там
     * направления называются одинаково на всех экранах.
     */
    jars: List<PlanJar> = emptyList(),
) {
    val rows = jars.ifEmpty { SpendCategory.entries.map { PlanJar(it, label = null) } }
    rows.forEach { jar ->
        val category = jar.category
        CategoryRow(
            title = jar.label ?: stringResource(category.label),
            amount = plan.amountFor(category),
            available = available,
            canAdd = canAdd,
            canRemove = canRemove(category),
            onAdd = { onAdd(category) },
            onRemove = { onRemove(category) },
        )
    }

    when {
        overBy > Coins.ZERO -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSmall),
            modifier = Modifier.semantics(mergeDescendants = true) {},
        ) {
            Text(
                text = stringResource(R.string.budget_over),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error,
            )
            MoneyAmount(amount = overBy)
        }

        remainder == Coins.ZERO -> Text(
            text = stringResource(R.string.budget_distributed),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )

        else -> MoneyCard(
            label = stringResource(R.string.budget_remainder),
            amount = remainder,
        )
    }
}

/** Одно направление: название, сумма, полоса доли и две кнопки. */
@Composable
private fun CategoryRow(
    title: String,
    amount: Coins,
    available: Coins,
    canAdd: Boolean,
    canRemove: Boolean,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
) {
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
        Column(
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceTiny),
            modifier = Modifier.weight(1f),
        ) {
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
            // Полоса без своей подписи: число уже прочитано строкой выше.
            ProgressLine(
                fraction = if (available.amount == 0) 0f else amount.amount.toFloat() / available.amount,
                modifier = Modifier.padding(end = Dimens.SpaceSmall),
            )
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
