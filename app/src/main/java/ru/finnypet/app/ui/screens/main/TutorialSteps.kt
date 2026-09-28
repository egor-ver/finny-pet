package ru.finnypet.app.ui.screens.main

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.max

/**
 * Элементы главного экрана, на которые показывает сова в обучении
 * (DESIGN_PLAN 3.4). Каждый отмечает себя сам ([tutorialTarget]), слой
 * обучения знает только имена, а не вёрстку.
 */
enum class TutorialTarget {
    WALLET,
    PLAN_TILE,
    SAVINGS_TILE,
    TASKS_TILE,
    TASK_BUTTON,
    PLAN_BUTTON,
    SATIETY,
    MOOD,
    CARE,
}

/**
 * Одна подсветка шага: основной элемент и запасной на случай, если основного
 * в этой фазе дня нет. Кнопки «Выполнить задание» и «Спланировать день» есть
 * только в фазе планирования, а «?» открывает обучение в любой.
 */
data class TutorialSpot(val main: TutorialTarget, val fallback: TutorialTarget? = null)

/**
 * Шаг обучения: реплика совы из контент-пака и что подсветить. [arrow] `false` —
 * только подсветка и облачко: к кнопке плана стрелка шла по нижней полосе
 * кнопки задания — между ними 12 dp, обойти её негде (решение владельца 28.09).
 */
data class TutorialStep(val textKey: String, val spots: List<TutorialSpot>, val arrow: Boolean = true)

/**
 * Шесть шагов (DESIGN_PLAN 3.4 и решение владельца 28.09). «Нужное»,
 * «желаемое» и «отложить» — шаги 3–5: ТЗ 2.5.1 требует объяснить эти три
 * решения до первого действия. Последний шаг — с чего начинать день: сначала
 * план, потом всё остальное; поэтому шаг 5 говорит только о копилке.
 */
val TUTORIAL_STEPS: List<TutorialStep> = listOf(
    TutorialStep("tutorial.step.1", listOf(TutorialSpot(TutorialTarget.SAVINGS_TILE))),
    TutorialStep(
        "tutorial.step.2",
        listOf(TutorialSpot(TutorialTarget.WALLET), TutorialSpot(TutorialTarget.TASK_BUTTON, TutorialTarget.TASKS_TILE)),
    ),
    TutorialStep("tutorial.step.3", listOf(TutorialSpot(TutorialTarget.SATIETY), TutorialSpot(TutorialTarget.CARE))),
    TutorialStep("tutorial.step.4", listOf(TutorialSpot(TutorialTarget.MOOD))),
    TutorialStep("tutorial.step.5", listOf(TutorialSpot(TutorialTarget.SAVINGS_TILE))),
    TutorialStep("tutorial.step.6", listOf(TutorialSpot(TutorialTarget.PLAN_BUTTON, TutorialTarget.PLAN_TILE)), arrow = false),
)

/** Следующий шаг обучения; `null` — шаги кончились, обучение закрывается. */
fun nextTutorialStep(step: Int): Int? = (step + 1).takeIf { it < TUTORIAL_STEPS.size }

/**
 * Что подсветить из того, что есть на экране: основной элемент, иначе
 * запасной, иначе ничего — шаг тогда идёт без стрелки, текст понятен и так.
 */
fun TutorialStep.resolve(present: Set<TutorialTarget>): List<TutorialTarget> =
    spots.mapNotNull { spot ->
        spot.main.takeIf { it in present } ?: spot.fallback?.takeIf { it in present }
    }

/**
 * Верх облачка совы по высоте: в самом большом свободном от подсветки
 * промежутке экрана. Облачко поверх цели закрыло бы то, о чём говорит сова.
 * Если цель только с одной стороны промежутка, облачко встаёт к ней вплотную,
 * оставив [reach] на стрелку: длинная стрелка через полэкрана шла бы сквозь
 * другие плитки и кнопки (ревью F1: на шаге про план — сквозь кнопку
 * задания), а облачко рядом закрывает их собой. Если цели с обеих сторон —
 * посередине, чтобы обе стрелки были короче. Если нигде не помещается — всё
 * равно в самом большом промежутке, но не за краем экрана: кнопки облачка
 * важнее цели.
 *
 * [spans] — вертикальные границы подсвеченных элементов в координатах слоя.
 */
internal fun panelTop(
    spans: List<ClosedFloatingPointRange<Float>>,
    panelHeight: Float,
    height: Float,
    reach: Float,
): Float {
    val gaps = mutableListOf<ClosedFloatingPointRange<Float>>()
    var cursor = 0f
    spans.sortedBy { it.start }.forEach { span ->
        if (span.start > cursor) gaps += cursor..span.start
        cursor = max(cursor, span.endInclusive)
    }
    if (cursor < height) gaps += cursor..height
    val limit = max(0f, height - panelHeight)
    val best = gaps.maxByOrNull { it.endInclusive - it.start } ?: return limit
    val targetAbove = best.start > 0f
    val targetBelow = best.endInclusive < height
    val top = when {
        targetAbove && !targetBelow -> best.start + reach
        targetBelow && !targetAbove -> best.endInclusive - reach - panelHeight
        else -> (best.start + best.endInclusive - panelHeight) / 2
    }
    return top.coerceIn(0f, limit)
}

/**
 * Стрелка обучения — одна дуга без перегиба: из облачка выходит отвесно, к
 * вырезу подходит наклонно. [tip] — кончик наконечника, [control] — вершина
 * изгиба квадратичной кривой; наконечник смотрит от неё к кончику.
 */
data class ArrowArc(val start: Offset, val control: Offset, val tip: Offset)

/**
 * Дуга от края облачка [bubble] к краю выреза [hole] (обе рамки в одних
 * координатах). Выходит из верха или низа облачка — смотря где вырез, —
 * поэтому не пересекает ни сову сбоку, ни кнопки внутри облачка. Кончик в
 * [gap] от края выреза: не внутри и не мимо. [inset] — отступ от
 * скруглённых углов облачка и выреза.
 *
 * `null` — вырез на одной высоте с облачком (не поместилось): стрелке негде пройти.
 */
fun arrowArc(bubble: Rect, hole: Rect, inset: Float, gap: Float): ArrowArc? {
    val up = hole.bottom <= bubble.top
    if (!up && hole.top < bubble.bottom) return null
    val startX = hole.center.x.within(bubble.left + inset, bubble.right - inset)
    val tipX = startX.within(hole.left + inset, hole.right - inset)
    val start = Offset(startX, if (up) bubble.top else bubble.bottom)
    val tip = Offset(tipX, if (up) hole.bottom + gap else hole.top - gap)
    // Изгиб ближе к облачку: к вырезу дуга подходит уже наклонённой к нему,
    // а не скользит почти вдоль его края.
    val control = Offset(startX, start.y + (tip.y - start.y) * BEND)
    return ArrowArc(start = start, control = control, tip = tip)
}

/** Как `coerceIn`, но рамка уже двух отступов даёт свою середину, а не исключение. */
private fun Float.within(low: Float, high: Float): Float =
    if (low > high) (low + high) / 2 else coerceIn(low, high)

private const val BEND = 0.4f
