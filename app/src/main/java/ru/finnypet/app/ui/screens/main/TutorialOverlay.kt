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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import ru.finnypet.app.R
import ru.finnypet.app.ui.components.ButtonColumn
import ru.finnypet.app.ui.components.FinnyButton
import ru.finnypet.app.ui.components.FinnyCard
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

    val holes = resolved.mapNotNull { target -> targets.bounds[target]?.takeUnless { it.isEmpty } }
    val spec = if (motion) tween<Rect>(Motion.StandardMs) else snap()
    val shown = holes.mapIndexed { index, rect ->
        key(index) { animateRectAsState(targetValue = rect, animationSpec = spec, label = "tutorialCutout").value }
    }
    val arrived = cutoutsArrived(shown = shown, targets = holes)

    // Стрелка «дышит» один раз на шаг, без бесконечного цикла (DESIGN_PLAN 2.7), —
    // когда появилась, то есть когда вырезы доехали до целей.
    val breath = remember { Animatable(0f) }
    LaunchedEffect(step, arrived) {
        if (motion && arrived) {
            breath.snapTo(0f)
            breath.animateTo(1f, tween(Motion.EmphasisMs))
        }
    }

    // Крупный шрифт: кнопки остаются в облачке (см. [TutorialPanel]), иначе —
    // стоят на одном месте над облачком.
    val fixedButtons = LocalDensity.current.fontScale <= Dimens.WIDE_FONT_SCALE
    var origin by remember { mutableStateOf(Offset.Zero) }
    var bubble by remember { mutableStateOf(Rect.Zero) }
    var buttons by remember { mutableStateOf(Rect.Zero) }
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
            if (bubble != Rect.Zero && TUTORIAL_STEPS[step].arrow && arrived) {
                val nudge = sin(PI * breath.value).toFloat() * ARROW_NUDGE.toPx()
                val from = bubble.translate(-origin)
                // Стрелка сквозь кнопки шага не рисуется — к кошельку над
                // «Пропустить» остаётся только вырез. К настройкам справа
                // вверху она проходит: на последнем шаге правая половина ряда
                // пуста (см. [StepButtons]).
                val blocked = buttons.takeUnless { it == Rect.Zero }?.translate(-origin)
                cutouts.forEach { hole ->
                    val arc = arrowArc(bubble = from, hole = hole, inset = ARROW_INSET.toPx(), gap = ARROW_GAP.toPx())
                    if (arc != null && (blocked == null || !arc.crosses(blocked, ARROW_HEAD_WIDTH.toPx() / 2))) {
                        drawArrow(arc, nudge)
                    }
                }
            }
        }

        var area by remember { mutableStateOf(Offset.Zero) }
        var panelHeight by remember { mutableIntStateOf(0) }
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = Dimens.ScreenPadding, vertical = Dimens.Space)
                .onGloballyPositioned { area = it.positionInRoot() }
                .semantics { isTraversalGroup = true },
        ) {
            val height = constraints.maxHeight.toFloat()
            if (fixedButtons) {
                StepButtons(
                    step = step,
                    onNext = onNext,
                    onSkip = onSkip,
                    modifier = Modifier
                        .padding(top = BUTTONS_TOP)
                        .onGloballyPositioned { buttons = it.boundsInRoot() }
                        // Кнопки выше облачка, но TalkBack читает сначала шаг.
                        .semantics { traversalIndex = 1f },
                )
            }
            TutorialPanel(
                step = step,
                text = text,
                where = resolved,
                owl = owl,
                onNext = onNext.takeUnless { fixedButtons },
                onSkip = onSkip,
                onBubble = { bubble = it },
                modifier = Modifier
                    .heightIn(max = maxHeight)
                    .offset {
                        val pad = CUTOUT_PADDING.toPx()
                        val spans = holes.map { (it.top - area.y - pad)..(it.bottom - area.y + pad) }
                        val from = if (buttons == Rect.Zero) 0f else buttons.bottom - area.y + Dimens.SpaceSmall.toPx()
                        IntOffset(0, panelTop(spans, panelHeight.toFloat(), height, ARROW_REACH.toPx(), from).roundToInt())
                    }
                    .onGloballyPositioned { panelHeight = it.size.height },
            )
        }
    }
}

/**
 * Кнопки шага на одном месте — сверху, где на главном стоит реплика совы
 * (под обучением она прозрачна, и целей там нет). Облачко ходит от цели к
 * цели, и «Дальше» в нём прыгало по экрану под пальцем ребёнка (ревью F3,
 * п. 20). Стрелку, как и при кнопках внутри облачка (решение F1), они не
 * пересекают: кнопки выше облачка, а стрелка сквозь кнопки не рисуется.
 * «Пропустить» слева, «Дальше» справа — вперёд по ходу чтения.
 *
 * На последнем шаге «Играть!» одна и встаёт на место «Пропустить», в левую
 * половину: правая свободна для стрелки к шестерёнке настроек в шапке
 * (пункт A1). Во всю ширину она перекрыла бы стрелку, а справа — встала бы
 * ровно под шестерёнкой. Заодно быстрые нажатия «Дальше» не проскакивают
 * последний шаг: на месте «Дальше» теперь пусто.
 */
@Composable
private fun StepButtons(step: Int, onNext: () -> Unit, onSkip: () -> Unit, modifier: Modifier = Modifier) {
    val last = nextTutorialStep(step) == null
    // Поля кнопок уже обычных: две в ряд на 328 dp, и при шрифте 1,3
    // «Пропустить» иначе не влезало бы в половину ряда одним словом.
    val padding = PaddingValues(horizontal = Dimens.SpaceSmall)
    // Верхние края вровень, а «Пропустить» высотой с лицевую грань «Дальше»:
    // по центру ряда объёмная «Дальше» (56 + нижняя грань 4) торчала выше и
    // казалась крупнее рамочной в 48 dp (ревью F5).
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSmall),
        modifier = modifier
            .fillMaxWidth(if (last) LAST_STEP_WIDTH else 1f)
            .semantics { isTraversalGroup = true },
    ) {
        // На последнем шаге пропускать уже нечего: «Играть!» одна (решение владельца 28.09).
        if (!last) {
            FinnySecondaryButton(
                text = stringResource(R.string.tutorial_skip),
                onClick = onSkip,
                contentPadding = padding,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = Dimens.ButtonHeight)
                    .semantics { traversalIndex = 1f },
            )
        }
        FinnyButton(
            text = stringResource(if (last) R.string.tutorial_play else R.string.tutorial_next),
            onClick = onNext,
            contentPadding = padding,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Сова с облачком. Для TalkBack текст — одна фраза «Шаг 3 из 6», реплика
 * совы и где искать элемент словами по сетке главного: стрелку не видно, а
 * экран под слоем скрыт от озвучки. `liveRegion` — чтобы новый шаг прочитался
 * сам после «Дальше».
 *
 * При крупном шрифте сова рядом не помещается — облачко во всю ширину без
 * неё, иначе «Пропустить» рвалось бы посреди слова. Главный тогда
 * прокручивается к целям, и цель могла бы уехать под кнопки сверху, поэтому
 * кнопки остаются внутри облачка ([onNext] не `null`): стрелка выходит из его
 * верха или низа, и кнопки ей не мешают.
 */
@Composable
private fun TutorialPanel(
    step: Int,
    text: String,
    where: List<TutorialTarget>,
    owl: OwlLook,
    /** `null` — кнопки шага стоят отдельно ([StepButtons]). */
    onNext: (() -> Unit)?,
    onSkip: () -> Unit,
    onBubble: (Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    val count = TUTORIAL_STEPS.size
    val places = where.map { stringResource(it.where) }
    val spoken = buildString {
        append(stringResource(R.string.tutorial_spoken, step + 1, count, text))
        // Места через «;»: в каждом уже есть запятая («кошелёк, вверху слева»),
        // и TalkBack склеил бы два места в одно перечисление.
        if (places.isNotEmpty()) append(' ').append(stringResource(R.string.tutorial_where, places.joinToString("; ")))
    }
    val last = nextTutorialStep(step) == null
    val placed = Modifier.onGloballyPositioned { onBubble(it.boundsInRoot()) }
    val content: @Composable ColumnScope.() -> Unit = {
        // Текст прокручивается сам, а кнопки под ним — нет: при крупном
        // шрифте реплика длиннее экрана, но кнопки не теряются.
        Column(
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSmall),
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
        if (onNext != null) {
            ButtonColumn {
                FinnyButton(
                    text = stringResource(if (last) R.string.tutorial_play else R.string.tutorial_next),
                    onClick = onNext,
                )
                // На последнем шаге пропускать уже нечего: «Играть!» одна (решение владельца 28.09).
                if (!last) FinnySecondaryButton(text = stringResource(R.string.tutorial_skip), onClick = onSkip)
            }
        }
    }
    if (LocalDensity.current.fontScale > Dimens.WIDE_FONT_SCALE) {
        FinnyCard(modifier = modifier.then(placed), content = content)
    } else {
        SpeechBubble(owl = owl, owlRole = OwlRole.Dialog, modifier = modifier, bubbleModifier = placed, content = content)
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
 * Дуга [arc] и наконечник-треугольник по её направлению. Линия кончается у
 * основания наконечника: иначе её край торчал бы из острия. Слишком короткая
 * стрелка (облачко вплотную к цели) не рисуется — от неё остался бы один
 * наконечник. [nudge] отводит кончик к облачку и возвращает — «вдох» шага.
 */
private fun DrawScope.drawArrow(arc: ArrowArc, nudge: Float) {
    val head = ARROW_HEAD.toPx()
    // Длина проверяется до «вдоха»: иначе короткая стрелка у облачка вплотную
    // к цели мигала бы, пропадая на середине движения.
    if ((arc.tip - arc.start).getDistance() < head * 2) return
    val tip = arc.tip.copy(y = arc.tip.y + sign(arc.start.y - arc.tip.y) * nudge)
    val toward = (tip - arc.control).let { it / it.getDistance() }
    val base = tip - toward * head
    val side = Offset(-toward.y, toward.x) * (ARROW_HEAD_WIDTH.toPx() / 2)
    val line = Path().apply {
        moveTo(arc.start.x, arc.start.y)
        quadraticTo(arc.control.x, arc.control.y, base.x, base.y)
    }
    val arrowhead = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(base.x + side.x, base.y + side.y)
        lineTo(base.x - side.x, base.y - side.y)
        close()
    }
    // Тёмная обводка под стрелкой: на шаге 3 облачку негде встать, кроме как
    // под «Едой», и стрелка к ней идёт через плитку плана — без обводки белая
    // линия сливалась с буквами «Монеты ещё не разложены» (ревью F6).
    val halo = ARROW_HALO.toPx()
    drawPath(line, color = SCRIM, style = Stroke(width = ARROW_WIDTH.toPx() + halo * 2, cap = StrokeCap.Butt))
    drawPath(arrowhead, color = SCRIM, style = Stroke(width = halo * 2, join = StrokeJoin.Round))
    drawPath(line, color = ARROW, style = Stroke(width = ARROW_WIDTH.toPx(), cap = StrokeCap.Butt))
    drawPath(arrowhead, color = ARROW)
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
        TutorialTarget.SETTINGS -> R.string.tutorial_where_settings
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

/** Не шире зазора до выреза с его полями: обводка у кончика не должна темнить цель. */
private val ARROW_HALO = 3.dp
private val ARROW_HEAD = 12.dp
private val ARROW_HEAD_WIDTH = 14.dp
private val ARROW_GAP = 2.dp

/**
 * Место на стрелку между облачком и вырезом. Не больше: на шаге про план
 * облачко тогда закрывает надпись кнопки задания над целью, и короткая
 * стрелка проходит только по её нижнему краю (между кнопками 12 dp).
 * Не меньше: стрелка должна остаться длиннее двух наконечников.
 */
private val ARROW_REACH = 28.dp

/**
 * Кнопки шага — сразу под шапкой главного (TopAppBar 64 dp; слой уже отступил
 * от статус-бара и на 16 dp сверху), на месте прозрачной под обучением реплики.
 */
private val BUTTONS_TOP = TopAppBarDefaults.TopAppBarExpandedHeight - Dimens.Space + Dimens.SpaceSmall
private val ARROW_INSET = 28.dp

/** «Играть!» на последнем шаге — половина ряда, как «Пропустить» до неё. */
private const val LAST_STEP_WIDTH = 0.5f
private val ARROW_NUDGE = 6.dp
private val DOT = 10.dp
private val DOT_GAP = 6.dp
private val DOT_STROKE = 1.5.dp
