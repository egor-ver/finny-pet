package ru.finnypet.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
import ru.finnypet.app.ui.theme.FinnyTheme
import ru.finnypet.app.ui.theme.LocalAnimationsEnabled

/**
 * Монеты летят по дуге от источника к целям за [FLIGHT_MS] (DESIGN_PLAN 2.7):
 * так ребёнок видит, куда ушли монеты, — из плана в банки, из кошелька к
 * товару, в копилку. Один эффект на все такие места, а не свой на каждом.
 *
 * [from] и [to] — в координатах окна (`boundsInRoot`), слой сам переводит их в
 * свои: экраны знают, где их кнопки и банки, но не где лежит слой.
 * Слой прозрачен для нажатий и озвучки — смысл передаёт текст на экране.
 *
 * При выключенном движении ничего не рисует и сразу зовёт [onFinished]: смысл
 * не теряется, конечное состояние видно без полёта (AD-8).
 */
@Composable
fun CoinFlight(
    from: Offset,
    to: List<Offset>,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val animate = LocalAnimationsEnabled.current && to.isNotEmpty()
    val progress = remember { Animatable(0f) }
    val finish by rememberUpdatedState(onFinished)
    LaunchedEffect(Unit) {
        if (animate) progress.animateTo(1f, tween(FLIGHT_MS, easing = FastOutSlowInEasing))
        finish()
    }
    if (!animate) return

    var origin by remember { mutableStateOf<Offset?>(null) }
    val colors = FinnyTheme.palette.coin
    Canvas(modifier = modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInRoot() }) {
        val shift = origin ?: return@Canvas
        repeat(FLIGHT_COINS) { index ->
            val t = coinProgress(progress.value, index, FLIGHT_COINS)
            // Ещё не вылетела или уже в банке — не рисуем: монета «исчезает» в цели.
            if (t <= 0f || t >= 1f) return@repeat
            val target = to[index % to.size]
            drawCoin(center = arcPoint(from - shift, target - shift, t), diameter = COIN_SIZE.toPx(), colors = colors)
        }
    }
}

/**
 * Где на пути монета номер [index] из [count] при общем ходе [progress]:
 * монеты вылетают по очереди, а долетают все к концу общего хода.
 */
internal fun coinProgress(progress: Float, index: Int, count: Int): Float {
    val delay = STAGGER * index
    val span = 1f - STAGGER * (count - 1)
    return ((progress - delay) / span).coerceIn(0f, 1f)
}

/**
 * Точка на дуге: квадратичная кривая с вершиной над серединой пути — монета
 * подпрыгивает, а не ползёт по прямой. Высота дуги — доля длины пути, чтобы
 * короткий и длинный полёт выглядели одинаково.
 */
internal fun arcPoint(from: Offset, to: Offset, t: Float): Offset {
    val middle = (from + to) / 2f
    val lift = (to - from).getDistance() * ARC_LIFT
    val control = Offset(middle.x, minOf(from.y, to.y) - lift)
    val u = 1f - t
    return from * (u * u) + control * (2f * u * t) + to * (t * t)
}

/** 500 мс — длительность полёта из DESIGN_PLAN 2.7. */
private const val FLIGHT_MS = 500

/** Пять монет — верх диапазона «3–5» из DESIGN_PLAN 2.7: на три банки хватает всем. */
private const val FLIGHT_COINS = 5
private const val STAGGER = 0.08f
private const val ARC_LIFT = 0.3f
private val COIN_SIZE = 20.dp
