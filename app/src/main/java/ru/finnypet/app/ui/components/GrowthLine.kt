package ru.finnypet.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import ru.finnypet.app.R
import ru.finnypet.app.domain.model.Explanation
import ru.finnypet.app.domain.model.GrowthStage
import ru.finnypet.app.domain.model.PetGrowth
import ru.finnypet.app.ui.components.icons.FinnyIcons
import ru.finnypet.app.ui.text.WordForm
import ru.finnypet.app.ui.text.textOf
import ru.finnypet.app.ui.text.wordFormOf
import ru.finnypet.app.ui.theme.Dimens
import ru.finnypet.app.ui.theme.FinnyTheme

/**
 * Путь до следующей стадии: [points] звёзд из [target] внутри текущей стадии
 * — «2 из 4 до подростка», а не «6 из 10» от самого начала (DESIGN_PLAN 3.1,
 * 3.6): ребёнку важно, сколько осталось до ближайшего шага.
 */
data class GrowthView(val next: GrowthStage, val points: Int, val target: Int)

/**
 * `null` — сова взрослая, дальше расти некуда. Звёзд до стадии — разница
 * соседних порогов `growthThresholds` из `balance.json` (сейчас 0/4/10: до
 * подростка 4, до взрослого 6), не константа в коде. Стадия не падает при
 * смене порогов (`GrowthEngine.apply`), поэтому очков может оказаться меньше
 * порога своей стадии — тогда ноль, а не минус.
 */
fun growthOf(growth: PetGrowth, thresholds: List<Int>): GrowthView? {
    val next = GrowthStage.entries.getOrNull(growth.stage.ordinal + 1) ?: return null
    val from = thresholds.getOrNull(growth.stage.ordinal) ?: return null
    val to = thresholds.getOrNull(next.ordinal) ?: return null
    val target = to - from
    return GrowthView(next = next, points = (growth.points - from).coerceIn(0, target), target = target)
}

/**
 * Всё для строки роста: путь к стадии или, у взрослого, «{имя} вырос!»
 * (`growth.grown`) и все звёзды за игру.
 */
data class GrowthSummary(val growth: GrowthView?, val grownMessage: String, val points: Int)

fun growthSummary(growth: PetGrowth, thresholds: List<Int>, texts: Map<String, String>, petName: String) =
    GrowthSummary(
        growth = growthOf(growth, thresholds),
        grownMessage = texts.textOf(Explanation("growth.grown", mapOf("name" to petName))),
        points = growth.points,
    )

/**
 * Строка роста (DESIGN_PLAN 3.6, 3.10): звёзды до следующей стадии —
 * заработанные залиты, остальные контуром — и подпись «2 из 4 до подростка».
 * Одна на итоги дня и «Мой прогресс». Звёзды кусочками, а не сплошной
 * полосой: ребёнку 7–11 лет проще сосчитать звёзды, чем оценить долю.
 * `FlowRow` — если пороги в `balance.json` станут больше, звёзды уйдут на
 * вторую строку, а не за край экрана.
 *
 * Для TalkBack — одна фраза, звёзды молчат.
 */
@Composable
fun GrowthLine(summary: GrowthSummary, modifier: Modifier = Modifier) {
    val growth = summary.growth
    if (growth == null) {
        val stars = starsText(summary.points)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSmall),
            modifier = modifier
                .fillMaxWidth()
                .clearAndSetSemantics { contentDescription = "${summary.grownMessage} $stars" },
        ) {
            StarMark(filled = true, modifier = Modifier.size(STAR_SIZE))
            Text(text = summary.grownMessage, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(text = stars, style = MaterialTheme.typography.bodyMedium)
        }
        return
    }
    val caption = stringResource(
        if (growth.next == GrowthStage.GROWN) R.string.growth_to_grown else R.string.growth_to_young,
        growth.points,
        growth.target,
    )
    val spoken = stringResource(R.string.growth_description, caption)
    Column(
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceTiny),
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = spoken },
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceTiny)) {
            repeat(growth.target) { index ->
                StarMark(filled = index < growth.points, modifier = Modifier.size(STAR_SIZE))
            }
        }
        Text(text = caption, style = MaterialTheme.typography.titleSmall)
    }
}

/**
 * Звезда роста: заработанная — цвета `star`, незаработанная — спокойный
 * контур `inkSoft` (DESIGN_PLAN 2.5): жёлтый контур на белом дал бы 1,6:1, а
 * значимой графике нужно не меньше 3:1 (ТЗ 3.6). Для TalkBack молчит — смысл
 * говорит подпись рядом.
 */
@Composable
fun StarMark(filled: Boolean, modifier: Modifier = Modifier) {
    Icon(
        imageVector = if (filled) FinnyIcons.StarFilled else FinnyIcons.StarOutline,
        contentDescription = null,
        tint = if (filled) FinnyTheme.palette.star else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/** «12 звёзд» — форма слова по русскому правилу ([wordFormOf]), не по локали устройства. */
@Composable
fun starsText(amount: Int): String = stringResource(
    when (wordFormOf(amount)) {
        WordForm.ONE -> R.string.main_growth_stars_one
        WordForm.FEW -> R.string.main_growth_stars_few
        WordForm.MANY -> R.string.main_growth_stars_many
    },
    amount,
)

private val STAR_SIZE = 28.dp
