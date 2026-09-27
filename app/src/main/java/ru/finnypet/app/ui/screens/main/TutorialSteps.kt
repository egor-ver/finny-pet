package ru.finnypet.app.ui.screens.main

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

/** Шаг обучения: реплика совы из контент-пака и что подсветить. */
data class TutorialStep(val textKey: String, val spots: List<TutorialSpot>)

/**
 * Пять шагов (DESIGN_PLAN 3.4). «Нужное», «желаемое» и «отложить» — шаги
 * 3–5: ТЗ 2.5.1 требует объяснить ровно эти три решения до первого действия.
 */
val TUTORIAL_STEPS: List<TutorialStep> = listOf(
    TutorialStep("tutorial.step.1", listOf(TutorialSpot(TutorialTarget.SAVINGS_TILE))),
    TutorialStep(
        "tutorial.step.2",
        listOf(TutorialSpot(TutorialTarget.WALLET), TutorialSpot(TutorialTarget.TASK_BUTTON, TutorialTarget.TASKS_TILE)),
    ),
    TutorialStep("tutorial.step.3", listOf(TutorialSpot(TutorialTarget.SATIETY), TutorialSpot(TutorialTarget.CARE))),
    TutorialStep("tutorial.step.4", listOf(TutorialSpot(TutorialTarget.MOOD))),
    TutorialStep(
        "tutorial.step.5",
        listOf(TutorialSpot(TutorialTarget.SAVINGS_TILE), TutorialSpot(TutorialTarget.PLAN_BUTTON, TutorialTarget.PLAN_TILE)),
    ),
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
 * промежутке экрана, по его середине. Облачко поверх цели закрыло бы то,
 * о чём говорит сова. Если нигде не помещается — всё равно в самом большом
 * промежутке, но не за краем экрана: кнопки облачка важнее цели.
 *
 * [spans] — вертикальные границы подсвеченных элементов в координатах слоя.
 */
internal fun panelTop(spans: List<ClosedFloatingPointRange<Float>>, panelHeight: Float, height: Float): Float {
    val gaps = mutableListOf<ClosedFloatingPointRange<Float>>()
    var cursor = 0f
    spans.sortedBy { it.start }.forEach { span ->
        if (span.start > cursor) gaps += cursor..span.start
        cursor = max(cursor, span.endInclusive)
    }
    if (cursor < height) gaps += cursor..height
    val limit = max(0f, height - panelHeight)
    val best = gaps.maxByOrNull { it.endInclusive - it.start } ?: return limit
    return ((best.start + best.endInclusive - panelHeight) / 2).coerceIn(0f, limit)
}
