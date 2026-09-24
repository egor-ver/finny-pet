package ru.finnypet.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import ru.finnypet.app.R
import ru.finnypet.app.domain.model.BudgetPlan
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.SpendCategory
import ru.finnypet.app.ui.theme.Dimens
import kotlin.math.roundToInt

/**
 * Банка редактора: направление расхода и подпись, если её задаёт задание.
 * [label] пустой — берётся общее название направления.
 */
data class PlanJar(
    val category: SpendCategory,
    val label: String?,
)

/**
 * Раскладка суммы по трём направлениям ползунками с шагом в монету.
 *
 * Один и тот же редактор в плане дня и в задании «раздели монеты»: ребёнок
 * учится одному движению, а не двум. Ползунок, а не клавиатура: у семилетнего
 * промах по цифре ломает весь план, а лишний ноль превращает сорок монет в
 * четыреста. Шаг в монету — чтобы разложить и нечётную сумму (раздел 3 плана).
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
    onSet: (SpendCategory, Coins) -> Unit,
    /** Строка пояснения под банкой; нет — банка без пояснения. */
    hints: Map<SpendCategory, String?> = emptyMap(),
    /**
     * Банки задания: свои подписи и свой порядок. Пусто — план дня, там
     * направления называются одинаково на всех экранах.
     */
    jars: List<PlanJar> = emptyList(),
    /**
     * Цели нет — копилку раскладывать некуда: вместо ползунка кнопка выбора
     * цели (раздел 8 плана). `null` — цель есть или копилка задания.
     */
    onChooseGoal: (() -> Unit)? = null,
) {
    val rows = jars.ifEmpty { SpendCategory.entries.map { PlanJar(it, label = null) } }
    rows.forEach { jar ->
        val category = jar.category
        val amount = plan.amountFor(category)
        CategoryRow(
            category = category,
            title = jar.label ?: stringResource(category.label),
            amount = amount,
            max = amount + remainder,
            available = available,
            hint = hints[category],
            onSet = { onSet(category, it) },
            onChooseGoal = onChooseGoal.takeIf { category == SpendCategory.SAVINGS },
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

/** Одно направление: иконка и название, сумма, ползунок и пояснение. */
@Composable
private fun CategoryRow(
    category: SpendCategory,
    title: String,
    amount: Coins,
    max: Coins,
    available: Coins,
    hint: String?,
    onSet: (Coins) -> Unit,
    onChooseGoal: (() -> Unit)?,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceTiny),
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(Dimens.Corner),
            )
            .padding(horizontal = Dimens.SpaceMedium, vertical = Dimens.SpaceSmall),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSmall),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = category.icon,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.clearAndSetSemantics {},
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            // Подпись для озвучки склеивает название с суммой: иначе читается
            // «Нужное», потом отдельно «двадцать монет», и связь теряется.
            val spoken = stringResource(R.string.budget_amount, title, amount.amount)
            Row(modifier = Modifier.clearAndSetSemantics { contentDescription = spoken }) {
                MoneyAmount(amount = amount)
            }
        }
        if (onChooseGoal != null) {
            FinnySecondaryButton(text = stringResource(R.string.budget_choose_goal), onClick = onChooseGoal)
        } else {
            AmountSlider(category = category, title = title, amount = amount, max = max, available = available, onSet = onSet)
        }
        if (hint != null) {
            Text(
                text = hint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Пока палец тянет, ползунок показывает своё значение: база отстаёт на
 * время записи, и без этого бегунок прыгал бы назад. Дальше [max] бегунок
 * не идёт, но запрошенная сумма уходит наверх целиком — по ней сова узнаёт,
 * что монеты кончились.
 *
 * TalkBack читает сумму монетами, а не процентами, как по умолчанию.
 */
@Composable
private fun AmountSlider(
    category: SpendCategory,
    title: String,
    amount: Coins,
    max: Coins,
    available: Coins,
    onSet: (Coins) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val dragging by interaction.collectIsDraggedAsState()
    var held by remember { mutableFloatStateOf(amount.amount.toFloat()) }
    val top = available.amount.coerceAtLeast(1)
    val spoken = coinsText(amount)
    Slider(
        value = if (dragging) held else amount.amount.toFloat(),
        onValueChange = { value ->
            held = value.coerceAtMost(max.amount.toFloat())
            onSet(Coins(value.roundToInt()))
        },
        enabled = available > Coins.ZERO,
        valueRange = 0f..top.toFloat(),
        steps = top - 1,
        interactionSource = interaction,
        colors = SliderDefaults.colors(thumbColor = category.color, activeTrackColor = category.color),
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = title
                stateDescription = spoken
            },
    )
}
