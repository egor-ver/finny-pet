package ru.finnypet.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import ru.finnypet.app.R
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.SpendCategory
import ru.finnypet.app.ui.theme.Dimens
import ru.finnypet.app.ui.theme.LocalAnimationsEnabled
import ru.finnypet.app.ui.theme.Motion
import kotlin.math.abs

/** Строка сравнения: сколько задумали и сколько вышло на самом деле. */
data class BudgetLine(
    val category: SpendCategory,
    val planned: Coins,
    val actual: Coins,
    val followed: Boolean,
)

/**
 * Уровень банки трат — доля плана, которая ещё осталась: полная утром,
 * пустеет с каждой покупкой. Перерасход — пустая банка, а не «минус».
 * Пустой план — пустая банка: делить на ноль нечего. Одна формула у банок
 * плана и мини-банок вчерашнего дня в «Моём прогрессе», чтобы банка значила
 * одно и то же на обоих экранах.
 */
fun jarLevel(line: BudgetLine): Float {
    if (line.planned == Coins.ZERO) return 0f
    return line.actual.shortfallTo(line.planned).amount.toFloat() / line.planned.amount
}

/** Длины двойной полосы от нуля до единицы: [plan] — трек, [fact] — заливка. */
data class PlanFactFractions(val plan: Float, val fact: Float)

/**
 * Шкала строки — большее из плана и факта: в пределах плана трек во всю
 * ширину, а заливка показывает, сколько из него ушло; сверх плана заливка
 * во всю ширину, а трек кончается там, где кончился план, — перерасход
 * виден длиной, а не цветом. Ничего не планировали и не тратили — пустой
 * трек во всю ширину, а не пропавшая полоса.
 */
fun planFactFractions(planned: Coins, actual: Coins): PlanFactFractions {
    val scale = maxOf(planned.amount, actual.amount)
    if (scale == 0) return PlanFactFractions(plan = 1f, fact = 0f)
    return PlanFactFractions(
        plan = planned.amount.toFloat() / scale,
        fact = actual.amount.toFloat() / scale,
    )
}

/**
 * План против факта по трём направлениям (DESIGN_PLAN 3.6): иконка, название,
 * двойная полоса «план — трек, факт — заливка» и итог словом. Одни и те же
 * в итогах дня и в «Моём прогрессе» (3.10), поэтому живут здесь: сравнение,
 * которое ребёнок видит дважды, не должно выглядеть по-разному.
 *
 * Без своей карточки — её даёт тот, кто показывает: в прогрессе полосы
 * лежат внутри сворачиваемой строки, и вторая карточка в карточке была бы лишней.
 */
@Composable
fun PlanFactBars(lines: List<BudgetLine>, modifier: Modifier = Modifier) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.Space), modifier = modifier.fillMaxWidth()) {
        lines.forEach { line -> PlanFactRow(line) }
    }
}

/**
 * Соблюдение плана сказано словами — «по плану», «не хватает», «сверх плана»,
 * — а не цветом или длиной: ТЗ 3.6 запрещает передавать смысл только видом.
 */
@Composable
private fun PlanFactRow(line: BudgetLine) {
    val title = stringResource(line.category.label)
    val factLabel = stringResource(
        if (line.category == SpendCategory.SAVINGS) R.string.budget_saved else R.string.budget_fact,
    )
    val status = statusOf(line)
    val spoken = stringResource(
        R.string.budget_line,
        title,
        line.planned.amount,
        factLabel.lowercase(),
        line.actual.amount,
        status,
    )
    Column(
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceTiny),
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = spoken },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceTiny),
        ) {
            CategoryLabel(category = line.category, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            MoneyAmount(amount = line.actual, style = MaterialTheme.typography.titleSmall)
            Text(
                text = stringResource(R.string.plan_fact_of, line.planned.amount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        PlanFactBar(line)
        Text(
            text = status,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Трек — план, заливка — факт цветом направления. Рамка трека рисуется
 * поверх заливки: при перерасходе она показывает, где кончился план.
 * Заливка растёт за `Emphasis`, при выключенном движении — сразу (AD-8).
 */
@Composable
private fun PlanFactBar(line: BudgetLine) {
    val fractions = planFactFractions(line.planned, line.actual)
    val fact by animateFloatAsState(
        targetValue = fractions.fact,
        animationSpec = if (LocalAnimationsEnabled.current) tween(Motion.EmphasisMs, easing = FastOutSlowInEasing) else snap(),
        label = "planFact",
    )
    val shape = RoundedCornerShape(Dimens.BarHeight)
    val rim = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    Box(modifier = Modifier.fillMaxWidth().height(Dimens.BarHeight)) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fractions.plan)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceVariant, shape),
        )
        if (fact > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fact.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(line.category.fill, shape)
                    .background(
                        Brush.verticalGradient(0f to Color.White.copy(alpha = 0.3f), 0.4f to Color.Transparent),
                        shape,
                    ),
            )
        }
        if (fractions.plan > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fractions.plan)
                    .fillMaxHeight()
                    .border(1.dp, rim, shape),
            )
        }
    }
}

/**
 * Траты не соблюдены, только когда потрачено больше плана (R3), — это «сверх
 * плана». Копилку, наоборот, надо добрать до плана — там «не хватает».
 */
@Composable
private fun statusOf(line: BudgetLine): String {
    val gap = Coins(abs(line.actual.amount - line.planned.amount))
    return when {
        line.followed -> stringResource(R.string.budget_status_ok)
        line.category == SpendCategory.SAVINGS -> stringResource(R.string.budget_status_short, coinsText(gap))
        else -> stringResource(R.string.budget_status_over, coinsText(gap))
    }
}
