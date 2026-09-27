package ru.finnypet.app.ui.screens.main

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateRectAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import ru.finnypet.app.R
import ru.finnypet.app.ui.components.ButtonColumn
import ru.finnypet.app.ui.components.FinnyButton
import ru.finnypet.app.ui.components.FinnySecondaryButton
import ru.finnypet.app.ui.components.OwlLook
import ru.finnypet.app.ui.components.OwlRole
import ru.finnypet.app.ui.components.SpeechBubble
import ru.finnypet.app.ui.theme.Dimens
import ru.finnypet.app.ui.theme.LocalAnimationsEnabled
import ru.finnypet.app.ui.theme.Motion
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin

/**
 * Где на главном лежат элементы, которые подсвечивает обучение. Границы —
 * в координатах окна, как у [ru.finnypet.app.ui.components.CoinFlight]:
 * элементы знают, где они, но не где лежит слой.
 *
 * Отмеченный, но прокрученный за край элемент остаётся в [requesters] (к нему
 * можно прокрутить), а его видимая часть в [bounds] пуста — без стрелки.
 */
@Stable
class TutorialTargets {
    internal val bounds = mutableStateMapOf<TutorialTarget, Rect>()
    internal val requesters = mutableStateMapOf<TutorialTarget, BringIntoViewRequester>()
}

/**
 * Отмечает элемент главного для обучения. Отметка снимается вместе с
 * элементом: кнопка задания пропадает после плана, и «?» в другой фазе
 * иначе показала бы на место, где её давно нет.
 */
@Composable
fun Modifier.tutorialTarget(targets: TutorialTargets, target: TutorialTarget): Modifier {
    val requester = remember { BringIntoViewRequester() }
    DisposableEffect(targets, target, requester) {
        targets.requesters[target] = requester
        onDispose {
            targets.requesters.remove(target)
            targets.bounds.remove(target)
        }
    }
    return bringIntoViewRequester(requester).onGloballyPositioned { coordinates ->
        val bounds = coordinates.boundsInRoot()
        if (targets.bounds[target] != bounds) targets.bounds[target] = bounds
    }
}

/**
 * Обучение поверх настоящего главного (DESIGN_PLAN 3.4): затемнение с
 * вырезом вокруг элемента, стрелка к нему и сова с облачком в свободной
 * половине. Нажатия по затемнению не проходят к экрану: ребёнок не уйдёт
 * посреди объяснения случайным касанием; выход — «Пропустить», «Играть!» или
 * системный «назад».
 *
 * Своя отрисовка на `Canvas` вместо библиотеки подсказок: вырез — один
 * `BlendMode.Clear` в отдельном слое, и движение подчиняется той же
 * настройке (AD-8), что и весь экран. Без отдельного слоя
 * (`CompositingStrategy.Offscreen`) Clear стёр бы и сам главный под ним —
 * вместо выреза была бы чёрная дыра.
 */
@Composable
fun TutorialOverlay(
    step: Int,
    text: String,
    owl: OwlLook,
    targets: TutorialTargets,
    onNext: () -> Unit,
    onSkip: () -> Unit,
) {
    val motion = LocalAnimationsEnabled.current
    val resolved = TUTORIAL_STEPS[step].resolve(targets.requesters.keys)
    BackHandler(onBack = onSkip)

    // Цель за краем (крупный шрифт) — главный сам прокручивается к ней.
    // Ключ и по целям: при первом показе элементы главного отмечаются в той же
    // композиции, что и слой, и на старте шага список ещё пуст.
    LaunchedEffect(step, resolved) {
        resolved.forEach { targets.requesters[it]?.bringIntoView() }
    }

    val appear = remember { Animatable(if (motion) 0f else 1f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, tween(Motion.StandardMs)) }
    // Стрелка «дышит» один раз на шаг, без бесконечного цикла (DESIGN_PLAN 2.7).
    val breath = remember { Animatable(0f) }
    LaunchedEffect(step) {
        if (motion) {
            breath.snapTo(0f)
            breath.animateTo(1f, tween(Motion.EmphasisMs))
        }
    }

    val holes = resolved.mapNotNull { target -> targets.bounds[target]?.takeUnless { it.isEmpty } }
    val spec = if (motion) tween<Rect>(Motion.StandardMs) else snap()
    val shown = holes.mapIndexed { index, rect ->
        key(index) { animateRectAsState(targetValue = rect, animationSpec = spec, label = "tutorialCutout").value }
    }

    var origin by remember { mutableStateOf(Offset.Zero) }
    var panel by remember { mutableStateOf(Rect.Zero) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = appear.value }
            .pointerInput(Unit) { detectTapGestures { } }
            .onGloballyPositioned { origin = it.positionInRoot() },
    ) {
        Canvas(modifier = Modifier.fillMaxSize().graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)) {
            drawRect(SCRIM)
            val pad = CUTOUT_PADDING.toPx()
            val cutouts = shown.map { it.translate(-origin).inflate(pad) }
            cutouts.forEach { hole ->
                drawRoundRect(
                    color = Color.Black,
                    topLeft = hole.topLeft,
                    size = hole.size,
                    cornerRadius = CornerRadius(CUTOUT_CORNER.toPx()),
                    blendMode = BlendMode.Clear,
                )
            }
            if (panel != Rect.Zero) {
                val nudge = sin(PI * breath.value).toFloat() * ARROW_NUDGE.toPx()
                cutouts.forEach { drawArrow(from = panel.translate(-origin), to = it, nudge = nudge) }
            }
        }

        var area by remember { mutableStateOf(Offset.Zero) }
        var panelHeight by remember { mutableIntStateOf(0) }
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = Dimens.ScreenPadding, vertical = Dimens.Space)
                .onGloballyPositioned { area = it.positionInRoot() },
        ) {
            val height = constraints.maxHeight.toFloat()
            TutorialPanel(
                step = step,
                text = text,
                where = resolved,
                owl = owl,
                onNext = onNext,
                onSkip = onSkip,
                modifier = Modifier
                    .heightIn(max = maxHeight)
                    .offset {
                        val pad = CUTOUT_PADDING.toPx()
                        val spans = holes.map { (it.top - area.y - pad)..(it.bottom - area.y + pad) }
                        IntOffset(0, panelTop(spans, panelHeight.toFloat(), height).roundToInt())
                    }
                    .onGloballyPositioned {
                        panelHeight = it.size.height
                        panel = it.boundsInRoot()
                    },
            )
        }
    }
}

/**
 * Сова с облачком и кнопки шага. Для TalkBack облачко — одна фраза «Шаг 3
 * из 5», текст совы и где искать элемент словами по сетке главного: стрелку
 * не видно, а экран под слоем скрыт от озвучки. `liveRegion` — чтобы новый
 * шаг прочитался сам после «Дальше».
 */
@Composable
private fun TutorialPanel(
    step: Int,
    text: String,
    where: List<TutorialTarget>,
    owl: OwlLook,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val count = TUTORIAL_STEPS.size
    val places = where.map { stringResource(it.where) }
    val spoken = buildString {
        append(stringResource(R.string.tutorial_spoken, step + 1, count, text))
        if (places.isNotEmpty()) append(' ').append(stringResource(R.string.tutorial_where, places.joinToString()))
    }
    val last = nextTutorialStep(step) == null
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMedium), modifier = modifier.fillMaxWidth()) {
        // Облачко прокручивается само, а кнопки под ним — нет: при крупном
        // шрифте текст длиннее экрана, но «Дальше» и «Пропустить» не теряются.
        SpeechBubble(
            owl = owl,
            owlRole = OwlRole.Dialog,
            modifier = Modifier
                .weight(1f, fill = false)
                .clearAndSetSemantics {
                    contentDescription = spoken
                    liveRegion = LiveRegionMode.Polite
                }
                .verticalScroll(rememberScrollState()),
        ) {
            StepDots(step = step, count = count)
            Text(text = text, style = MaterialTheme.typography.bodyLarge)
        }
        ButtonColumn {
            FinnyButton(
                text = stringResource(if (last) R.string.tutorial_play else R.string.tutorial_next),
                onClick = onNext,
            )
            FinnySecondaryButton(text = stringResource(R.string.tutorial_skip), onClick = onSkip)
        }
    }
}

/** Точки шагов и «3 из 5»: текущая залита, остальные — контуром. */
@Composable
private fun StepDots(step: Int, count: Int) {
    val current = MaterialTheme.colorScheme.primary
    val other = MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSmall)) {
        Canvas(modifier = Modifier.size(width = DOT * count + DOT_GAP * (count - 1), height = DOT)) {
            val radius = DOT.toPx() / 2
            val stroke = DOT_STROKE.toPx()
            repeat(count) { index ->
                val center = Offset(radius + index * (DOT + DOT_GAP).toPx(), size.height / 2)
                if (index == step) {
                    drawCircle(color = current, radius = radius, center = center)
                } else {
                    drawCircle(color = other, radius = radius - stroke / 2, center = center, style = Stroke(stroke))
                }
            }
        }
        Text(
            text = stringResource(R.string.tutorial_progress, step + 1, count),
            style = MaterialTheme.typography.bodyMedium,
            color = other,
        )
    }
}

/**
 * Кривая от облачка к вырезу со стрелкой на конце. Вырез на одной высоте с
 * облачком (не поместилось) — без стрелки: ей негде пройти.
 */
private fun DrawScope.drawArrow(from: Rect, to: Rect, nudge: Float) {
    val gap = ARROW_GAP.toPx()
    val (start, end) = when {
        to.bottom <= from.top -> Offset(from.center.x, from.top) to Offset(to.center.x, to.bottom + gap - nudge)
        to.top >= from.bottom -> Offset(from.center.x, from.bottom) to Offset(to.center.x, to.top - gap + nudge)
        else -> return
    }
    val middle = (start.y + end.y) / 2
    val path = Path().apply {
        moveTo(start.x, start.y)
        cubicTo(start.x, middle, end.x, middle, end.x, end.y)
    }
    val width = ARROW_WIDTH.toPx()
    drawPath(path, color = ARROW, style = Stroke(width = width, cap = StrokeCap.Round))
    val head = ARROW_HEAD.toPx()
    val back = -sign(end.y - start.y) * head
    drawLine(ARROW, end, Offset(end.x - head, end.y + back), strokeWidth = width, cap = StrokeCap.Round)
    drawLine(ARROW, end, Offset(end.x + head, end.y + back), strokeWidth = width, cap = StrokeCap.Round)
}

private val TutorialTarget.where: Int
    get() = when (this) {
        TutorialTarget.WALLET -> R.string.tutorial_where_wallet
        TutorialTarget.PLAN_TILE -> R.string.tutorial_where_plan_tile
        TutorialTarget.SAVINGS_TILE -> R.string.tutorial_where_savings_tile
        TutorialTarget.TASKS_TILE -> R.string.tutorial_where_tasks_tile
        TutorialTarget.TASK_BUTTON -> R.string.tutorial_where_task_button
        TutorialTarget.PLAN_BUTTON -> R.string.tutorial_where_plan_button
        TutorialTarget.SATIETY -> R.string.tutorial_where_satiety
        TutorialTarget.MOOD -> R.string.tutorial_where_mood
        TutorialTarget.CARE -> R.string.tutorial_where_care
    }

/**
 * Затемнение и стрелка не зависят от темы: слой всегда тёмный, и белая
 * стрелка на нём видна и днём, и в тёмной теме.
 */
private val SCRIM = Color.Black.copy(alpha = 0.6f)
private val ARROW = Color.White

private val CUTOUT_PADDING = 6.dp
private val CUTOUT_CORNER = 20.dp
private val ARROW_WIDTH = 3.dp
private val ARROW_HEAD = 10.dp
private val ARROW_GAP = 10.dp
private val ARROW_NUDGE = 6.dp
private val DOT = 10.dp
private val DOT_GAP = 6.dp
private val DOT_STROKE = 1.5.dp
