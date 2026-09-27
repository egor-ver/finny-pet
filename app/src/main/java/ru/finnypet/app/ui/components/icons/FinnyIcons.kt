package ru.finnypet.app.ui.components.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Свой набор значков вместо эмодзи и текстовых знаков в коде (DESIGN_PLAN
 * 2.3, AD-16): `material-icons-extended` не подключаем — нужно около 15
 * значков, а библиотека тянет тысячи и стиль у неё «офисный».
 *
 * Цвет и подпись для TalkBack задаёт вызывающий `Icon(tint=…,
 * contentDescription=…)`, поэтому пути внутри рисуются условным чёрным —
 * `Icon` красит все пути в один RGB через `ColorFilter.tint`. Рисовать
 * деталь тем же цветом поверх заливки нельзя: она сольётся с фигурой в одно
 * пятно (так уже было с мячом и копилкой). Деталь внутри силуэта (шов
 * мяча, прорезь и глаз копилки, стержень пера) — вырез в той же заливке
 * через `PathFillType.EvenOdd`: замкнутый путь внутри другого замкнутого
 * пути становится дыркой независимо от направления обхода. Сама фигура при
 * этом остаётся в полную силу — так она видна на белом с нужным контрастом.
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
        filled {
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

    /** Мяч — «Желаемое»: шов — вырез в заливке, а не деталь того же цвета поверх нее. */
    val Ball: ImageVector = icon("FinnyBall") {
        filled(PathFillType.EvenOdd) {
            circle(cx = 12f, cy = 12f, r = 9.5f)
            moveTo(12f, 7.6f); lineTo(15.4f, 10f); lineTo(14.1f, 14.1f); lineTo(9.9f, 14.1f); lineTo(8.6f, 10f); close()
        }
    }

    /**
     * Копилка — «Накопления»: используется и для направления трат, и для
     * темы заданий. Профиль анфас узнаётся по ушам, пятачку и ножкам, а не
     * только по форме тела — иначе на 24 dp это просто гладкий овал.
     */
    val Piggy: ImageVector = icon("FinnyPiggy") {
        filled {
            // Уши — треугольники над телом.
            moveTo(8.5f, 7.6f); lineTo(6.4f, 3f); lineTo(11.4f, 6.3f); close()
            moveTo(13.4f, 6.1f); lineTo(14.2f, 2.2f); lineTo(17.2f, 5.9f); close()
        }
        path(fill = BLACK, pathFillType = PathFillType.EvenOdd) {
            // Тело.
            ellipse(cx = 11.5f, cy = 14f, rx = 8.5f, ry = 7.5f)
            // Прорезь для монет — вырез сверху.
            moveTo(8.5f, 7.1f); lineTo(13.5f, 7.1f); lineTo(13.5f, 8.4f); lineTo(8.5f, 8.4f); close()
            // Глаз — вырез.
            circle(cx = 15.2f, cy = 11.5f, r = 1f)
        }
        filled {
            // Пятачок.
            ellipse(cx = 3f, cy = 15.5f, rx = 2.4f, ry = 2f)
        }
        path(fill = BLACK, pathFillType = PathFillType.EvenOdd) {
            // Ноздри на пятачке — вырезы.
            circle(cx = 2.2f, cy = 15.5f, r = 0.4f)
            circle(cx = 3.6f, cy = 15.5f, r = 0.4f)
        }
        filled {
            // Ножки.
            moveTo(7f, 20.6f); lineTo(6.4f, 23.3f); lineTo(8.6f, 23.3f); lineTo(8.9f, 20.6f); close()
            moveTo(14.5f, 20.9f); lineTo(14.1f, 23.3f); lineTo(16.3f, 23.3f); lineTo(16.8f, 20.9f); close()
        }
    }

    /** Яблоко — показатель «Сыт». */
    val Apple: ImageVector = icon("FinnyApple") {
        filled {
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

    /** Пёрышко — показатель «Ухожен»: сова — птица, а не пушистое существо. Стержень — вырез. */
    val Feather: ImageVector = icon("FinnyFeather") {
        path(fill = BLACK, pathFillType = PathFillType.EvenOdd) {
            moveTo(19f, 3.5f)
            curveTo(19f, 3.5f, 8f, 5.5f, 6f, 12f)
            curveTo(4.4f, 17.1f, 8.5f, 20.2f, 12.3f, 19f)
            curveTo(19.8f, 16.6f, 19f, 3.5f, 19f, 3.5f)
            close()
            thickLine(x1 = 17.3f, y1 = 5.7f, x2 = 8.4f, y2 = 17.6f, width = 1.1f)
        }
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

/** Заливка фигуры в полную силу. [fillType] — `EvenOdd`, когда внутри есть вырезы. */
private fun ImageVector.Builder.filled(fillType: PathFillType = PathFillType.NonZero, block: PathBuilder.() -> Unit) {
    path(fill = BLACK, pathFillType = fillType, pathBuilder = block)
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
    ellipse(cx, cy, r, r)
}

/** Эллипс — та же кубическая аппроксимация круга с разными полуосями. */
private fun PathBuilder.ellipse(cx: Float, cy: Float, rx: Float, ry: Float) {
    val kx = rx * 0.5523f
    val ky = ry * 0.5523f
    moveTo(cx + rx, cy)
    curveTo(cx + rx, cy + ky, cx + kx, cy + ry, cx, cy + ry)
    curveTo(cx - kx, cy + ry, cx - rx, cy + ky, cx - rx, cy)
    curveTo(cx - rx, cy - ky, cx - kx, cy - ry, cx, cy - ry)
    curveTo(cx + kx, cy - ry, cx + rx, cy - ky, cx + rx, cy)
    close()
}

/** Тонкий прямоугольник вдоль отрезка — вырез-«канавка» (стержень пера, прорезь). */
private fun PathBuilder.thickLine(x1: Float, y1: Float, x2: Float, y2: Float, width: Float) {
    val dx = x2 - x1
    val dy = y2 - y1
    val len = sqrt(dx * dx + dy * dy)
    val ox = -dy / len * (width / 2f)
    val oy = dx / len * (width / 2f)
    moveTo(x1 + ox, y1 + oy)
    lineTo(x2 + ox, y2 + oy)
    lineTo(x2 - ox, y2 - oy)
    lineTo(x1 - ox, y1 - oy)
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
