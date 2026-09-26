package ru.finnypet.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.finnypet.app.domain.content.PetColor
import ru.finnypet.app.domain.content.PetOptions
import ru.finnypet.app.domain.model.GrowthStage
import ru.finnypet.app.domain.model.PetAppearance
import ru.finnypet.app.domain.model.PetMood
import ru.finnypet.app.domain.model.PetState
import ru.finnypet.app.domain.model.PetStatKind
import ru.finnypet.app.ui.text.textOf
import ru.finnypet.app.ui.theme.LocalAnimationsEnabled
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Всё, из чего рисуется сова, и что о ней скажет TalkBack. [wellbeing] —
 * сумма показателей: выросла — сове стало лучше, и она подпрыгивает.
 */
data class OwlLook(
    val colors: PetColor,
    val stage: GrowthStage,
    val mood: PetMood,
    val accessoryId: String?,
    val description: String,
    val wellbeing: Int = 0,
)

/** Сумма показателей: выросла — сове стало лучше, и она подпрыгивает. */
val PetState.wellbeing: Int get() = mood.value + satiety.value + care.value

/** «Совёнок Пушок грустит: хочет есть» — слова из контент-пака, а не из кода (раздел 5 плана). */
fun owlDescription(texts: Map<String, String>, name: String, mood: PetMood, sadAbout: PetStatKind?): String =
    texts.textOf("owl.describe.${mood.name}")
        .replace("{name}", name)
        .replace("{reason}", sadAbout?.let { texts.textOf("owl.reason.${it.name}") }.orEmpty())

/** Окрас ищется в pets.json; пропавший после правки файла заменяется первым, а не роняет экран. */
fun owlLook(
    pets: PetOptions,
    appearance: PetAppearance,
    stage: GrowthStage,
    mood: PetMood,
    description: String,
    wellbeing: Int = 0,
) = OwlLook(
    colors = pets.colors.firstOrNull { it.id == appearance.colorId } ?: pets.colors.first(),
    stage = stage,
    mood = mood,
    accessoryId = appearance.accessoryId,
    description = description,
    wellbeing = wellbeing,
)

/**
 * Сова, нарисованная кодом (AD-1): эмоции видны на морде, стиль одинаков на
 * всех стадиях, окрас — цвета из pets.json. Геометрия перенесена из
 * `docs/prototypes/owl.js` один к одному в его поле 240 × 262 и масштабируется
 * под размер компонента.
 */
@Composable
fun Owl(look: OwlLook, modifier: Modifier = Modifier, size: Dp = 170.dp) {
    val motion = LocalAnimationsEnabled.current
    val lift = remember { Animatable(0f) }
    // Прошлое самочувствие переживает уход в магазин и возврат: после
    // покупки сова подпрыгивает на главном, куда ребёнок вернулся.
    var seen by rememberSaveable { mutableIntStateOf(look.wellbeing) }
    LaunchedEffect(look.wellbeing) {
        if (shouldJump(seen, look.wellbeing, motion)) {
            lift.animateTo(1f, tween(JUMP_UP_MS))
            lift.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
        }
        seen = look.wellbeing
    }
    Canvas(
        modifier = modifier
            .size(size)
            .graphicsLayer { translationY = -lift.value * JUMP_HEIGHT.toPx() }
            .semantics {
                contentDescription = look.description
                role = Role.Image
            },
    ) {
        val k = min(this.size.width / FIELD_WIDTH, this.size.height / FIELD_HEIGHT)
        translate((this.size.width - FIELD_WIDTH * k) / 2, (this.size.height - FIELD_HEIGHT * k) / 2) {
            scale(k, pivot = Offset.Zero) { drawOwl(look) }
        }
    }
}

/**
 * Прыжок, когда сове стало лучше (ТЗ 2.5.9), — только с включённым движением
 * (ТЗ 3.6). Смысл движением не передаётся: выражение и описание меняются и так.
 */
internal fun shouldJump(before: Int, after: Int, motion: Boolean): Boolean = motion && after > before

/**
 * Пропорции стадии: [top] — верх головы, [bottom] — низ тела, [half] —
 * полуширина, [eye] — радиус глаза, [eyeAt] — высота глаз в долях роста,
 * [eyeDx] — глаз от центра, [tuft] — высота ушек, [rows] — рядов пёрышек.
 */
private class Proportions(
    val top: Float,
    val bottom: Float,
    val half: Float,
    val eye: Float,
    val eyeAt: Float,
    val eyeDx: Float,
    val tuft: Float,
    val rows: Int,
) {
    val height: Float get() = bottom - top
}

private fun proportionsOf(stage: GrowthStage) = when (stage) {
    GrowthStage.CUB -> Proportions(72f, 236f, 78f, 25f, 0.38f, 27f, 5f, 2)
    GrowthStage.YOUNG -> Proportions(48f, 236f, 74f, 22f, 0.31f, 24f, 14f, 3)
    GrowthStage.GROWN -> Proportions(32f, 236f, 82f, 21f, 0.28f, 26f, 22f, 4)
}

private fun DrawScope.drawOwl(look: OwlLook) {
    val g = proportionsOf(look.stage)
    val c = look.colors
    val body = Color(c.body)
    val wing = Color(c.wing)
    val face = Color(c.face)
    val ring = Color(c.ring)
    val t = g.top
    val b = g.bottom
    val w = g.half
    val h = g.height
    val er = g.eye
    val dx = g.eyeDx
    val ey = t + g.eyeAt * h
    val k = er / 27f

    drawOval(Color.Black.copy(alpha = 0.08f), Offset(CX - w * 0.72f, b + 1f), Size(w * 1.44f, 14f))
    listOf(CX - w * 0.3f, CX + w * 0.3f).forEach { fx ->
        listOf(-7f to 0f, 0f to 3f, 7f to 0f).forEach { (ox, oy) ->
            drawOval(BEAK, Offset(fx + ox - 5f, b + oy - 6.5f), Size(10f, 13f))
        }
    }

    SIDES.forEach { sd ->
        val b1 = Offset(CX + sd * 0.8f * w, t + 0.17f * h)
        val b2 = Offset(CX + sd * 0.4f * w, t + 0.015f * h)
        val tip = Offset(CX + sd * 0.86f * w, t - g.tuft)
        val tuft = Path().apply {
            moveTo(b1.x, b1.y)
            cubicTo(b1.x + sd * 3f, b1.y - 14f, tip.x, tip.y + 10f, tip.x, tip.y)
            cubicTo(tip.x - sd * 10f, tip.y + 4f, b2.x + sd * 10f, b2.y - 4f, b2.x, b2.y)
            close()
        }
        drawPath(tuft, body)
        drawPath(tuft, ring.copy(alpha = 0.45f), style = Stroke(width = 2f))
    }

    val torso = Path().apply {
        moveTo(CX, t)
        cubicTo(CX + 0.8f * w, t, CX + w, t + 0.35f * h, CX + w, t + 0.6f * h)
        cubicTo(CX + w, t + 0.9f * h, CX + 0.6f * w, b, CX, b)
        cubicTo(CX - 0.6f * w, b, CX - w, t + 0.9f * h, CX - w, t + 0.6f * h)
        cubicTo(CX - w, t + 0.35f * h, CX - 0.8f * w, t, CX, t)
        close()
    }
    drawPath(torso, body)
    drawPath(torso, ring.copy(alpha = 0.45f), style = Stroke(width = 2f))

    drawOval(face, Offset(CX - 0.62f * w, t + 0.72f * h - 0.27f * h), Size(1.24f * w, 0.54f * h))
    repeat(g.rows) { i ->
        val yy = t + 0.66f * h + i * 0.075f * h
        val n = if (i % 2 == 1) 2 else 3
        repeat(n) { j ->
            val xx = CX + (j - (n - 1) / 2f) * 20f
            drawPath(
                Path().apply { moveTo(xx - 6f, yy); quadraticTo(xx, yy + 6f, xx + 6f, yy) },
                wing,
                style = Stroke(width = 2.4f, cap = StrokeCap.Round),
            )
        }
    }

    SIDES.forEach { sd ->
        drawPath(
            Path().apply {
                moveTo(CX + sd * 0.94f * w, t + 0.5f * h)
                cubicTo(CX + sd * 1.12f * w, t + 0.62f * h, CX + sd * 1.04f * w, t + 0.9f * h, CX + sd * 0.74f * w, t + 0.95f * h)
                cubicTo(CX + sd * 0.82f * w, t + 0.8f * h, CX + sd * 0.84f * w, t + 0.62f * h, CX + sd * 0.94f * w, t + 0.5f * h)
                close()
            },
            wing,
        )
        // Пёрышки на крыльях отличают подростка и взрослого от детёныша.
        if (look.stage != GrowthStage.CUB) {
            repeat(3) { i ->
                val yy = t + (0.68f + i * 0.07f) * h
                drawPath(
                    Path().apply {
                        moveTo(CX + sd * 0.98f * w, yy)
                        quadraticTo(CX + sd * 0.9f * w, yy + 5f, CX + sd * 0.84f * w, yy + 2f)
                    },
                    ring.copy(alpha = 0.6f),
                    style = Stroke(width = 2f, cap = StrokeCap.Round),
                )
            }
        }
    }

    SIDES.forEach { sd -> drawCircle(face, er + 9f * k, Offset(CX + sd * dx, ey)) }
    if (look.mood != PetMood.SAD) {
        val blush = BLUSH.copy(alpha = if (look.mood == PetMood.HAPPY) 0.6f else 0.3f)
        SIDES.forEach { sd ->
            val center = Offset(CX + sd * (dx + er * 0.55f), ey + er + 7f * k)
            drawOval(blush, Offset(center.x - 10f * k, center.y - 5.5f * k), Size(20f * k, 11f * k))
        }
    }
    SIDES.forEach { sd -> drawEye(Offset(CX + sd * dx, ey), er, sd, look.mood, ring) }

    val kb = k * 1.15f
    val bk = ey + er * 0.42f
    drawPath(
        Path().apply {
            moveTo(CX, bk)
            quadraticTo(CX - 9f * kb, bk + 2f * kb, CX - 8f * kb, bk + 7f * kb)
            quadraticTo(CX - 4f * kb, bk + 15f * kb, CX, bk + 18f * kb)
            quadraticTo(CX + 4f * kb, bk + 15f * kb, CX + 8f * kb, bk + 7f * kb)
            quadraticTo(CX + 9f * kb, bk + 2f * kb, CX, bk)
            close()
        },
        BEAK,
    )
    drawPath(
        Path().apply { moveTo(CX - 6f * k, bk + 8f * k); quadraticTo(CX, bk + 11f * k, CX + 6f * k, bk + 8f * k) },
        BEAK_DARK,
        style = Stroke(width = 1.6f, cap = StrokeCap.Round),
    )

    val neckY = ey + er + 12f * k
    when (look.accessoryId) {
        SCARF_ID -> drawScarf(neckY, halfWidth(g, neckY) * 0.93f)
        GLASSES_ID -> drawGlasses(ey, er + 6f * k, dx)
    }
}

/** Радость — глаза дугой; спокойствие — зрачок по центру; грусть — зрачок ниже и веко. */
private fun DrawScope.drawEye(at: Offset, r: Float, side: Float, mood: PetMood, ring: Color) {
    if (mood == PetMood.HAPPY) {
        drawPath(
            Path().apply {
                moveTo(at.x - r * 0.72f, at.y + r * 0.25f)
                quadraticTo(at.x, at.y - r * 0.95f, at.x + r * 0.72f, at.y + r * 0.25f)
            },
            DARK,
            style = Stroke(width = r * 0.2f, cap = StrokeCap.Round),
        )
        return
    }
    val pupilY = if (mood == PetMood.CALM) at.y + r * 0.08f else at.y + r * 0.28f
    drawCircle(Color.White, r, at)
    drawCircle(ring, r, at, style = Stroke(width = 3f))
    drawCircle(DARK, r * 0.62f, Offset(at.x, pupilY))
    drawCircle(Color.White, r * 0.24f, Offset(at.x - r * 0.26f, pupilY - r * 0.26f))
    drawCircle(Color.White, r * 0.1f, Offset(at.x + r * 0.24f, pupilY + r * 0.26f))
    if (mood == PetMood.SAD) {
        // Веко — сегмент круга над наклонной хордой: внешний край ниже внутреннего.
        val start = if (side < 0) 170f else 215f
        val startRad = Math.toRadians(start.toDouble())
        drawPath(
            Path().apply {
                moveTo(at.x + r * cos(startRad).toFloat(), at.y + r * sin(startRad).toFloat())
                arcTo(Rect(at, r), start, LID_SWEEP, forceMoveTo = false)
                close()
            },
            ring,
        )
        drawCircle(ring, r, at, style = Stroke(width = 3f))
    }
}

private fun DrawScope.drawScarf(neckY: Float, w: Float) {
    val sag = 7f
    drawPath(
        Path().apply {
            moveTo(CX - w, neckY)
            quadraticTo(CX, neckY + 2 * sag, CX + w, neckY)
            lineTo(CX + w, neckY + 15f)
            quadraticTo(CX, neckY + 15f + 2 * sag, CX - w, neckY + 15f)
            close()
        },
        SCARF,
    )
    listOf(-0.7f, -0.35f, 0f, 0.35f, 0.7f).forEach { u ->
        val top = neckY + sag * (1 - u * u)
        drawRect(SCARF_STRIPE, Offset(CX + u * w - 3.5f, top), Size(7f, 15f))
    }
    val tail = Offset(CX + w * 0.42f, neckY + 10f)
    rotate(-10f, pivot = tail) {
        drawRoundRect(SCARF, Offset(tail.x - 8f, tail.y), Size(16f, 44f), CornerRadius(5f))
        drawRect(SCARF_STRIPE, Offset(tail.x - 8f, tail.y + 12f), Size(16f, 6f))
        drawRect(SCARF_STRIPE, Offset(tail.x - 8f, tail.y + 26f), Size(16f, 6f))
        repeat(4) { i ->
            val x = tail.x - 6f + i * 4f
            drawLine(SCARF, Offset(x, tail.y + 44f), Offset(x, tail.y + 50f), strokeWidth = 2f, cap = StrokeCap.Round)
        }
    }
}

private fun DrawScope.drawGlasses(ey: Float, r: Float, dx: Float) {
    SIDES.forEach { sd -> drawCircle(GLASSES, r, Offset(CX + sd * dx, ey), style = Stroke(width = 4f)) }
    drawPath(
        Path().apply {
            moveTo(CX - dx + r * 0.92f, ey - r * 0.35f)
            quadraticTo(CX, ey - r * 0.75f, CX + dx - r * 0.92f, ey - r * 0.35f)
        },
        GLASSES,
        style = Stroke(width = 4f),
    )
}

/** Полуширина тела на высоте [y]: шарф ложится ровно по контуру, а не торчит. */
private fun halfWidth(g: Proportions, y: Float): Float {
    val t = g.top
    val w = g.half
    val h = g.height
    val segments = listOf(
        listOf(Offset(CX, t), Offset(CX + 0.8f * w, t), Offset(CX + w, t + 0.35f * h), Offset(CX + w, t + 0.6f * h)),
        listOf(Offset(CX + w, t + 0.6f * h), Offset(CX + w, t + 0.9f * h), Offset(CX + 0.6f * w, g.bottom), Offset(CX, g.bottom)),
    )
    val closest = segments
        .flatMap { (p0, p1, p2, p3) -> (0..SAMPLES).map { cubic(p0, p1, p2, p3, it / SAMPLES.toFloat()) } }
        .minBy { abs(it.y - y) }
    return closest.x - CX
}

private fun cubic(p0: Offset, p1: Offset, p2: Offset, p3: Offset, t: Float): Offset {
    val u = 1 - t
    return p0 * (u * u * u) + p1 * (3 * u * u * t) + p2 * (3 * u * t * t) + p3 * (t * t * t)
}

private const val JUMP_UP_MS = 160
private val JUMP_HEIGHT = 14.dp
private const val FIELD_WIDTH = 240f
private const val FIELD_HEIGHT = 262f
private const val CX = 120f
private const val SAMPLES = 60
private const val LID_SWEEP = 155f
private const val SCARF_ID = "scarf"
private const val GLASSES_ID = "glasses"
private val SIDES = listOf(-1f, 1f)

private val DARK = Color(0xFF2A2320)
private val BEAK = Color(0xFFF4A340)
private val BEAK_DARK = Color(0xFFD98420)
private val SCARF = Color(0xFFE05252)
private val SCARF_STRIPE = Color(0xFFFFE1E1)
private val GLASSES = Color(0xFF3A3A40)
private val BLUSH = Color(0xFFF7A1B5)
