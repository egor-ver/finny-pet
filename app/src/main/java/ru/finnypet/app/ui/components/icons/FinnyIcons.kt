package ru.finnypet.app.ui.components.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Свой набор значков вместо эмодзи и текстовых знаков в коде (DESIGN_PLAN
 * 2.3, AD-16): `material-icons-extended` не подключаем — нужно около 15
 * значков, а библиотека тянет тысячи и стиль у неё «офисный».
 *
 * Цвет и подпись для TalkBack задаёт вызывающий `Icon(tint=…,
 * contentDescription=…)`, поэтому пути внутри рисуются условным чёрным —
 * `Icon` красит все пути в один RGB через `ColorFilter.tint`, но альфа-канал
 * каждого пути сохраняет. Поэтому у силуэтов с деталью того же цвета поверх
 * заливки (миска, мяч, копилка, яблоко, пёрышко) основа рисуется через
 * `filled(alpha = 0.3f)` — второй тон по DESIGN_PLAN 2.3 («тот же цвет на
 * 30% прозрачности»): без него деталь полной силы на сплошной заливке той
 * же силы не отличить (была видна как один сплошной круг/пятно).
 */
object FinnyIcons {

    val Back: ImageVector = icon("FinnyBack") {
        outlined { moveTo(15f, 5f); lineTo(9f, 12f); lineTo(15f, 19f) }
    }

    val Chevron: ImageVector = icon("FinnyChevron") {
        outlined { moveTo(9f, 5f); lineTo(15f, 12f); lineTo(9f, 19f) }
    }

    val Check: ImageVector = icon("FinnyCheck") {
        outlined { moveTo(4f, 12.5f); lineTo(9.5f, 18f); lineTo(20f, 6f) }
    }

    val Help: ImageVector = icon("FinnyHelp") {
        outlined(1.8f) { circle(cx = 12f, cy = 12f, r = 9f) }
        outlined(1.8f) {
            moveTo(9.3f, 9.3f)
            curveTo(9.3f, 7.2f, 10.8f, 6f, 12.3f, 6f)
            curveTo(13.9f, 6f, 15.2f, 7.1f, 15.2f, 8.6f)
            curveTo(15.2f, 10.6f, 12.6f, 10.9f, 12.4f, 13.1f)
        }
        filled { circle(cx = 12.35f, cy = 16.3f, r = 1.1f) }
    }

    val Grownup: ImageVector = icon("FinnyGrownup") {
        filled {
            circle(cx = 15f, cy = 7.2f, r = 3.1f)
            moveTo(9.5f, 20f)
            curveTo(9.5f, 15.6f, 12f, 12.7f, 15f, 12.7f)
            curveTo(18f, 12.7f, 20.5f, 15.6f, 20.5f, 20f)
            close()
        }
        filled {
            circle(cx = 6.6f, cy = 12.6f, r = 2.4f)
            moveTo(3f, 21f)
            curveTo(3f, 17.7f, 4.7f, 15.6f, 6.6f, 15.6f)
            curveTo(8.5f, 15.6f, 10.2f, 17.7f, 10.2f, 21f)
            close()
        }
    }

    /** Миска — «Нужное» и еда: раздел 8 плана держит одну иконку на направление везде. */
    val Bowl: ImageVector = icon("FinnyBowl") {
        filled(0.3f) {
            moveTo(3.5f, 11f)
            curveTo(3.5f, 10.4f, 4f, 10f, 4.6f, 10f)
            lineTo(19.4f, 10f)
            curveTo(20f, 10f, 20.5f, 10.4f, 20.5f, 11f)
            curveTo(20.5f, 15.5f, 16.9f, 19f, 12f, 19f)
            curveTo(7.1f, 19f, 3.5f, 15.5f, 3.5f, 11f)
            close()
        }
        filled {
            moveTo(4.2f, 8.4f)
            curveTo(4.2f, 7.6f, 7.7f, 7f, 12f, 7f)
            curveTo(16.3f, 7f, 19.8f, 7.6f, 19.8f, 8.4f)
            curveTo(19.8f, 9.2f, 16.3f, 9.8f, 12f, 9.8f)
            curveTo(7.7f, 9.8f, 4.2f, 9.2f, 4.2f, 8.4f)
            close()
        }
    }

    /** Мяч — «Желаемое». */
    val Ball: ImageVector = icon("FinnyBall") {
        filled(0.3f) { circle(cx = 12f, cy = 12f, r = 8.5f) }
        outlined(1.3f) {
            moveTo(12f, 7.5f); lineTo(15.2f, 9.8f); lineTo(14f, 13.6f); lineTo(10f, 13.6f); lineTo(8.8f, 9.8f); close()
        }
    }

    /** Копилка — «Накопления»: используется и для направления трат, и для темы заданий. */
    val Piggy: ImageVector = icon("FinnyPiggy") {
        filled(0.3f) {
            moveTo(4f, 13f)
            curveTo(4f, 9.4f, 7.4f, 6.5f, 12f, 6.5f)
            curveTo(15.6f, 6.5f, 18.6f, 8.3f, 19.6f, 10.8f)
            lineTo(21.5f, 11.2f)
            curveTo(22f, 11.3f, 22f, 12.1f, 21.5f, 12.2f)
            lineTo(19.9f, 12.6f)
            curveTo(19.5f, 16.2f, 16.1f, 19f, 12f, 19f)
            curveTo(7.4f, 19f, 4f, 16.1f, 4f, 13f)
            close()
        }
        filled { circle(cx = 3.8f, cy = 12.3f, r = 1.6f) }
        outlined(1.4f) { moveTo(15f, 13.2f); lineTo(15f, 15.4f) }
    }

    /** Яблоко — показатель «Сыт». */
    val Apple: ImageVector = icon("FinnyApple") {
        filled(0.3f) {
            moveTo(12.3f, 9f)
            curveTo(15.7f, 9f, 18.5f, 11.7f, 18.5f, 15f)
            curveTo(18.5f, 18.3f, 16.2f, 20.5f, 13.2f, 20.5f)
            curveTo(12.5f, 20.5f, 11.9f, 20.3f, 11.5f, 20.1f)
            curveTo(11.1f, 20.3f, 10.5f, 20.5f, 9.8f, 20.5f)
            curveTo(6.8f, 20.5f, 4.5f, 18.3f, 4.5f, 15f)
            curveTo(4.5f, 11.7f, 7.3f, 9f, 10.7f, 9f)
            curveTo(11.3f, 9f, 11.7f, 9.1f, 12.3f, 9f)
            close()
        }
        filled {
            moveTo(13.5f, 4.4f)
            curveTo(15f, 4.1f, 16f, 5f, 16.2f, 6.2f)
            curveTo(14.7f, 6.5f, 13.7f, 5.7f, 13.5f, 4.4f)
            close()
        }
        outlined(1.5f) { moveTo(12f, 8.6f); curveTo(12f, 7f, 12.6f, 5.5f, 13.5f, 4.6f) }
    }

    /** Сердце — показатель «Весел». */
    val Heart: ImageVector = icon("FinnyHeart") {
        filled {
            moveTo(12f, 20f)
            curveTo(12f, 20f, 3.5f, 14.9f, 3.5f, 9.4f)
            curveTo(3.5f, 6.6f, 5.7f, 4.5f, 8.3f, 4.5f)
            curveTo(9.8f, 4.5f, 11.1f, 5.2f, 12f, 6.3f)
            curveTo(12.9f, 5.2f, 14.2f, 4.5f, 15.7f, 4.5f)
            curveTo(18.3f, 4.5f, 20.5f, 6.6f, 20.5f, 9.4f)
            curveTo(20.5f, 14.9f, 12f, 20f, 12f, 20f)
            close()
        }
    }

    /** Пёрышко — показатель «Ухожен»: сова — птица, а не пушистое существо. */
    val Feather: ImageVector = icon("FinnyFeather") {
        filled(0.3f) {
            moveTo(19f, 3.5f)
            curveTo(19f, 3.5f, 8f, 5.5f, 6f, 12f)
            curveTo(4.4f, 17.1f, 8.5f, 20.2f, 12.3f, 19f)
            curveTo(19.8f, 16.6f, 19f, 3.5f, 19f, 3.5f)
            close()
        }
        outlined(1.3f) { moveTo(17.5f, 5.5f); lineTo(8.3f, 17.8f) }
    }

    val StarFilled: ImageVector = icon("FinnyStarFilled") {
        filled { star(cx = 12f, cy = 12f, outerR = 9.2f, innerR = 3.6f) }
    }

    val StarOutline: ImageVector = icon("FinnyStarOutline") {
        path(
            fill = null,
            stroke = BLACK,
            strokeLineWidth = 1.6f,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = { star(cx = 12f, cy = 12f, outerR = 9.2f, innerR = 3.6f) },
        )
    }

    /** Мишень — тема заданий «Планирование». */
    val Target: ImageVector = icon("FinnyTarget") {
        outlined(1.8f) { circle(cx = 12f, cy = 12f, r = 9f) }
        outlined(1.8f) { circle(cx = 12f, cy = 12f, r = 5.2f) }
        filled { circle(cx = 12f, cy = 12f, r = 1.6f) }
    }

    /** Сумка — тема заданий «Покупки». */
    val Bag: ImageVector = icon("FinnyBag") {
        outlined(1.8f) {
            moveTo(9f, 7.5f)
            curveTo(9f, 5.3f, 10.3f, 4f, 12f, 4f)
            curveTo(13.7f, 4f, 15f, 5.3f, 15f, 7.5f)
        }
        filled {
            moveTo(4.8f, 8.5f)
            lineTo(19.2f, 8.5f)
            lineTo(20.2f, 20.5f)
            curveTo(20.2f, 21.1f, 19.7f, 21.5f, 19.1f, 21.5f)
            lineTo(4.9f, 21.5f)
            curveTo(4.3f, 21.5f, 3.8f, 21.1f, 3.8f, 20.5f)
            close()
        }
    }
}

private const val VIEWPORT = 24f
private val BLACK = SolidColor(Color.Black)

private fun icon(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = VIEWPORT,
        viewportHeight = VIEWPORT,
    ).apply(block).build()

/**
 * Заливка. [alpha] — второй тон (DESIGN_PLAN 2.3): основа силуэта на 30%,
 * деталь поверх — в полную силу, иначе [androidx.compose.material3.Icon]
 * красит оба пути в один RGB и деталь пропадает на заливке того же тона.
 */
private fun ImageVector.Builder.filled(alpha: Float = 1f, block: PathBuilder.() -> Unit) {
    path(fill = BLACK, fillAlpha = alpha, pathBuilder = block)
}

private fun ImageVector.Builder.outlined(width: Float = 2f, block: PathBuilder.() -> Unit) {
    path(
        fill = null,
        stroke = BLACK,
        strokeLineWidth = width,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathBuilder = block,
    )
}

/** Окружность через четыре кубические дуги (константа каппы для круга безье). */
private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
    val k = r * 0.5523f
    moveTo(cx + r, cy)
    curveTo(cx + r, cy + k, cx + k, cy + r, cx, cy + r)
    curveTo(cx - k, cy + r, cx - r, cy + k, cx - r, cy)
    curveTo(cx - r, cy - k, cx - k, cy - r, cx, cy - r)
    curveTo(cx + k, cy - r, cx + r, cy - k, cx + r, cy)
    close()
}

/** Пятиконечная звезда — 10 вершин через шаг в 36°, начиная сверху. */
private fun PathBuilder.star(cx: Float, cy: Float, outerR: Float, innerR: Float) {
    val step = (Math.PI / 5).toFloat()
    var angle = -(Math.PI / 2).toFloat()
    moveTo(cx + outerR * cos(angle), cy + outerR * sin(angle))
    for (i in 1 until 10) {
        angle += step
        val r = if (i % 2 == 0) outerR else innerR
        lineTo(cx + r * cos(angle), cy + r * sin(angle))
    }
    close()
}
