package ru.finnypet.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import ru.finnypet.app.R
import ru.finnypet.app.domain.model.BudgetPlan
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.SpendCategory
import ru.finnypet.app.ui.components.icons.FinnyIcons
import ru.finnypet.app.ui.theme.Dimens
import ru.finnypet.app.ui.theme.FinnyTheme
import ru.finnypet.app.ui.theme.LocalAnimationsEnabled
import ru.finnypet.app.ui.theme.Motion
import ru.finnypet.app.ui.theme.NumberLarge
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
 * Черновики банок — суммы, которые палец тянет прямо сейчас и которых ещё
 * нет в базе (Б11). Живут у экрана, а не внутри ползунка: по ним же
 * [RemainderCounter] считает остаток во время движения (DESIGN_PLAN 3.2).
 */
typealias PlanDrafts = MutableMap<SpendCategory, Coins>

@Composable
fun rememberPlanDrafts(): PlanDrafts = remember { mutableStateMapOf() }

/**
 * Остаток с учётом черновиков: `remainder` из базы плюс сохранённая сумма
 * банки минус черновик. Черновик дальше свободных монет не идёт — ползунок
 * там упирается, — поэтому и остаток не уходит ниже нуля.
 */
fun liveRemainder(plan: BudgetPlan, remainder: Coins, drafts: Map<SpendCategory, Coins>): Coins {
    val moved = drafts.entries.sumOf { (category, draft) ->
        val stored = plan.amountFor(category)
        stored.amount - draft.coerceAtMost(stored + remainder).amount
    }
    return Coins((remainder.amount + moved).coerceAtLeast(0))
}

/**
 * Раскладка суммы по трём направлениям ползунками с шагом в монету.
 *
 * Один и тот же редактор в плане дня и в задании «раздели монеты»: ребёнок
 * учится одному движению, а не двум. Ползунок, а не клавиатура: у семилетнего
 * промах по цифре ломает весь план, а лишний ноль превращает сорок монет в
 * четыреста. Шаг в монету — чтобы разложить и нечётную сумму (раздел 3 плана).
 *
 * Остаток редактор не рисует: его показывает [RemainderCounter] там, где
 * решает экран, — в плане дня он закреплён над кнопкой подтверждения и не
 * уезжает при прокрутке (DESIGN_PLAN 3.2).
 */
@Composable
fun PlanEditor(
    plan: BudgetPlan,
    available: Coins,
    remainder: Coins,
    onSet: (SpendCategory, Coins) -> Unit,
    drafts: PlanDrafts,
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
    /**
     * Сколько стоит закрыть все непокрытые потребности — кнопка «Положить N на
     * нужное» подставляет ровно эту сумму, не трогая другие банки. `null` —
     * банка задания (свои [jars]) или считать нечего.
     */
    mandatoryCover: Coins? = null,
) {
    val rows = jars.ifEmpty { SpendCategory.entries.map { PlanJar(it, label = null) } }
    rows.forEach { jar ->
        val category = jar.category
        val amount = plan.amountFor(category)
        val max = amount + remainder
        CategoryRow(
            category = category,
            title = jar.label ?: stringResource(category.label),
            amount = amount,
            max = max,
            available = available,
            drafts = drafts,
            hint = hints[category],
            onSet = { onSet(category, it) },
            onChooseGoal = onChooseGoal.takeIf { category == SpendCategory.SAVINGS },
            // Не обязательна: видна, только пока не хватает и остатка хватит
            // без урезания — иначе кнопка обещала бы сумму, которую не даст.
            fillAmount = mandatoryCover
                ?.takeIf { category == SpendCategory.MANDATORY && it > amount && it <= max },
        )
    }
}

/**
 * Крупный остаток «Осталось распределить» (правка владельца №8). Число
 * «катится» к новому значению, при выключенном движении — сразу. Ноль —
 * тоже ответ: вместо числа чип «Всё разложено». Перебор назван словом и
 * числом, а не только цветом (ТЗ 3.6).
 *
 * TalkBack читает остаток как живую область: ребёнок, двигающий ползунок
 * жестом, слышит, сколько монет ещё свободно, не уводя фокус с ползунка.
 */
@Composable
fun RemainderCounter(remainder: Coins, overBy: Coins, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.budget_remainder)
    val distributed = stringResource(R.string.budget_distributed)
    val over = stringResource(R.string.budget_over)
    val spoken = when {
        overBy > Coins.ZERO -> "$over ${coinsText(overBy)}"
        remainder == Coins.ZERO -> distributed
        else -> "$label: ${coinsText(remainder)}"
    }
    val shown by animateIntAsState(
        targetValue = remainder.amount,
        animationSpec = if (LocalAnimationsEnabled.current) tween(Motion.EmphasisMs, easing = FastOutSlowInEasing) else snap(),
        label = "remainder",
    )
    val area = modifier
        .fillMaxWidth()
        .clearAndSetSemantics {
            contentDescription = spoken
            liveRegion = LiveRegionMode.Polite
        }
    when {
        overBy > Coins.ZERO -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSmall),
            modifier = area,
        ) {
            Text(text = over, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
            MoneyAmount(amount = overBy)
        }

        remainder == Coins.ZERO -> Row(modifier = area, horizontalArrangement = Arrangement.Center) {
            DistributedChip(text = distributed)
        }

        // FlowRow: при крупном шрифте число уходит под подпись, а не
        // сжимает её до переноса посреди слова «распределить».
        else -> FlowRow(
            horizontalArrangement = Arrangement.SpaceBetween,
            itemVerticalAlignment = Alignment.CenterVertically,
            modifier = area,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSmall),
            ) {
                Coin(size = COUNTER_COIN)
                Text(
                    text = shown.toString(),
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun DistributedChip(text: String) {
    val need = FinnyTheme.palette.need
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSmall),
        modifier = Modifier
            .background(need.container, CircleShape)
            .padding(horizontal = Dimens.Space, vertical = Dimens.SpaceSmall),
    ) {
        Icon(imageVector = FinnyIcons.Check, contentDescription = null, tint = need.text, modifier = Modifier.size(24.dp))
        Text(text = text, style = MaterialTheme.typography.titleMedium, color = need.text)
    }
}

/**
 * Одно направление — карточка цвета своей банки (DESIGN_PLAN 3.2): иконка и
 * название, сумма крупно, ползунок с «−»/«+» и пояснение с лампочкой.
 */
@Composable
private fun CategoryRow(
    category: SpendCategory,
    title: String,
    amount: Coins,
    max: Coins,
    available: Coins,
    drafts: PlanDrafts,
    hint: String?,
    onSet: (Coins) -> Unit,
    onChooseGoal: (() -> Unit)?,
    fillAmount: Coins?,
) {
    // База прислала своё значение — правка дошла (или её обогнало что-то
    // другое) — черновик больше не нужен. Сама база могла остаться прежней,
    // если запрос обрезался до уже сохранённой суммы (потянули за предел):
    // тогда стейт-флоу не пришлёт новое значение, и без ключа на [max]
    // обрезанный черновик пережил бы освобождение места в другой банке и
    // показал бы сумму, которой в плане уже нет.
    LaunchedEffect(amount, max) { drafts.remove(category) }
    val shown = (drafts[category] ?: amount).coerceAtMost(max)

    // Не CategoryLabel: title — своя подпись задания из labelKey (TaskViewModel),
    // не всегда общее название направления, а CategoryLabel всегда читает его
    // из category.label и потеряло бы эту подмену.
    FinnyCard(color = category.container) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSmall),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                imageVector = category.icon,
                contentDescription = null,
                tint = category.fill,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = category.color,
                modifier = Modifier.weight(1f),
            )
            // Подпись для озвучки склеивает название с суммой: иначе читается
            // «Нужное», потом отдельно «двадцать монет», и связь теряется.
            val spoken = stringResource(R.string.budget_amount, title, shown.amount)
            Row(modifier = Modifier.clearAndSetSemantics { contentDescription = spoken }) {
                MoneyAmount(amount = shown, style = NumberLarge)
            }
        }
        if (onChooseGoal != null) {
            FinnySecondaryButton(text = stringResource(R.string.budget_choose_goal), onClick = onChooseGoal)
        } else {
            AmountSlider(
                category = category,
                title = title,
                shown = shown,
                available = available,
                onDraft = { drafts[category] = it },
                // Из карты в момент отпускания, а не из значения при отрисовке:
                // отпускание может прийти в том же кадре, что и последний сдвиг.
                onRelease = { drafts[category]?.let(onSet) },
                onSet = onSet,
            )
        }
        if (fillAmount != null) {
            FinnySecondaryButton(
                text = stringResource(R.string.budget_fill_mandatory, coinsText(fillAmount)),
                onClick = { onSet(fillAmount) },
            )
        }
        if (hint != null) HintLine(hint)
    }
}

/** Пояснение под банкой: лампочка отличает совет от суммы и названия (DESIGN_PLAN 3.2). */
@Composable
private fun HintLine(text: String) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSmall),
    ) {
        Icon(
            imageVector = FinnyIcons.Bulb,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Пока палец тянет, банка показывает черновик и в базу не пишет: запись
 * под замком на каждый шаг — это ~45 обращений на одно движение (Б11).
 * Уходит в базу только итог — когда палец отпущен или шаг сделан жестом
 * TalkBack (для него отдельного жеста отпускания нет, и колбэк срабатывает
 * сразу). Дальше свободных монет бегунок не идёт, но запрошенная сумма
 * уходит целиком — по ней сова узнаёт, что монеты кончились.
 *
 * Без делений (`steps = 0`) и с округлением до монеты в `onValueChange`: с
 * шагом в монету Material рисовал дорожку пунктиром из десятков точек
 * (DESIGN_PLAN 1, №5). «−» и «+» по бокам — точная подстройка на монету без
 * мелкой моторики и для TalkBack, у которого жест ползунка шагает на десятую
 * часть шкалы.
 *
 * TalkBack читает сумму монетами, а не процентами, как по умолчанию.
 *
 * Слоты `thumb`/`track` у `Slider` пока экспериментальные — без них не
 * заменить ручку на монету и пунктир делений на сплошную дорожку.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AmountSlider(
    category: SpendCategory,
    title: String,
    shown: Coins,
    available: Coins,
    onDraft: (Coins) -> Unit,
    onRelease: () -> Unit,
    onSet: (Coins) -> Unit,
) {
    val top = available.amount.coerceAtLeast(1)
    val enabled = available > Coins.ZERO
    val spoken = coinsText(shown)
    val fill = category.fill
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceTiny),
    ) {
        StepIconButton(
            icon = FinnyIcons.Minus,
            description = stringResource(R.string.budget_less, title),
            enabled = enabled && shown > Coins.ZERO,
            onClick = { onSet(Coins(shown.amount - 1)) },
        )
        Slider(
            value = shown.amount.toFloat(),
            onValueChange = { value -> onDraft(Coins(value.roundToInt())) },
            onValueChangeFinished = onRelease,
            enabled = enabled,
            valueRange = 0f..top.toFloat(),
            thumb = {
                Coin(
                    size = THUMB_SIZE,
                    modifier = Modifier.shadow(elevation = 3.dp, shape = CircleShape),
                )
            },
            track = { ProgressTrack(fraction = shown.amount.toFloat() / top, color = fill) },
            modifier = Modifier
                .weight(1f)
                .semantics {
                    contentDescription = title
                    stateDescription = spoken
                },
        )
        StepIconButton(
            icon = FinnyIcons.Plus,
            description = stringResource(R.string.budget_more, title),
            enabled = enabled,
            onClick = { onSet(Coins(shown.amount + 1)) },
        )
    }
}

/** Круглая кнопка 48 dp в стиле второстепенной: белая, рамка и значок `primary`. */
@Composable
private fun StepIconButton(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    OutlinedIconButton(
        onClick = onClick,
        enabled = enabled,
        border = BorderStroke(Dimens.ButtonBorderWidth, if (enabled) primary else primary.copy(alpha = DISABLED_ALPHA)),
        colors = IconButtonDefaults.outlinedIconButtonColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = primary,
            disabledContainerColor = MaterialTheme.colorScheme.surface,
        ),
        modifier = Modifier.size(Dimens.TouchTarget),
    ) {
        Icon(imageVector = icon, contentDescription = description)
    }
}

private val THUMB_SIZE = 32.dp
private val COUNTER_COIN = 36.dp
private const val DISABLED_ALPHA = 0.38f
