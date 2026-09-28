package ru.finnypet.app.ui.screens.progress

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.finnypet.app.R
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.GrowthStage
import ru.finnypet.app.domain.model.SpendCategory
import ru.finnypet.app.ui.components.BudgetLine
import ru.finnypet.app.ui.components.ButtonColumn
import ru.finnypet.app.ui.components.Explanation
import ru.finnypet.app.ui.components.FinnyButton
import ru.finnypet.app.ui.components.FinnyCard
import ru.finnypet.app.ui.components.FinnyScaffold
import ru.finnypet.app.ui.components.GrowthLine
import ru.finnypet.app.ui.components.Heading
import ru.finnypet.app.ui.components.ItemIcon
import ru.finnypet.app.ui.components.Jar
import ru.finnypet.app.ui.components.MoneyAmount
import ru.finnypet.app.ui.components.Owl
import ru.finnypet.app.ui.components.OwlLook
import ru.finnypet.app.ui.components.OwlRole
import ru.finnypet.app.ui.components.PlanFactBars
import ru.finnypet.app.ui.components.coinsText
import ru.finnypet.app.ui.components.color
import ru.finnypet.app.ui.components.colors
import ru.finnypet.app.ui.components.container
import ru.finnypet.app.ui.components.fill
import ru.finnypet.app.ui.components.icon
import ru.finnypet.app.ui.components.icons.FinnyIcons
import ru.finnypet.app.ui.components.label
import ru.finnypet.app.ui.theme.Dimens

/**
 * История и учебный прогресс (ТЗ 2.5.11), разгруженный (DESIGN_PLAN 3.10).
 *
 * На виду без прокрутки — два главных вопроса ребёнка: «как я расту?» и
 * «сколько до цели?». Вчерашний день, задания и словарик — свёрнутые строки:
 * это материал для разговора со взрослым и для любопытных. Ничего не
 * удалено, всё в одно касание (ТЗ 8.4: последствия видны; 8.3: возрастная уместность).
 */
@Composable
fun ProgressScreen(
    onBack: () -> Unit,
    viewModel: ProgressViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ProgressContent(state = state, onBack = onBack, onRetry = viewModel::retry)
}

@Composable
fun ProgressContent(state: ProgressState, onBack: () -> Unit, onRetry: () -> Unit = {}) {
    when (state) {
        ProgressState.Loading -> FinnyScaffold(
            title = stringResource(R.string.progress_title),
            onBack = onBack,
        ) {}

        ProgressState.Failed -> FinnyScaffold(
            title = stringResource(R.string.progress_title),
            onBack = onBack,
            bottomBar = {
                ButtonColumn {
                    FinnyButton(text = stringResource(R.string.action_retry), onClick = onRetry)
                }
            },
        ) {
            Text(
                text = stringResource(R.string.progress_failed),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
            )
        }

        is ProgressState.Ready -> Ready(state = state, onBack = onBack)
    }
}

@Composable
private fun Ready(state: ProgressState.Ready, onBack: () -> Unit) {
    FinnyScaffold(
        title = stringResource(R.string.progress_title),
        onBack = onBack,
        spacing = Dimens.SpaceMedium,
    ) {
        FinnyCard {
            StagePath(owl = state.owl)
            GrowthLine(summary = state.growth)
        }
        GoalCard(goal = state.goal)
        LastDaySection(lastDay = state.lastDay)
        TasksSection(passed = state.passed, topics = state.topics)
        if (state.terms.isNotEmpty()) {
            GlossarySection(terms = state.terms)
        }
    }
}

/**
 * Три стадии на тропинке: текущая — крупная сова с отметкой «здесь»,
 * остальные — приглушённые силуэты (DESIGN_PLAN 3.10). Для TalkBack — одна
 * фраза о стадии: совы тут украшение, звёзды до следующей стадии говорит
 * строка роста ниже.
 */
@Composable
private fun StagePath(owl: OwlLook) {
    val spoken = stringResource(R.string.progress_stage_now, stringResource(owl.stage.label))
    val path = MaterialTheme.colorScheme.outline
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = spoken }
            // Тропинка — пунктир на уровне лап, от первой совы до последней.
            .drawBehind {
                val y = OwlRole.StageNow.size.toPx() - PATH_LIFT.toPx()
                drawLine(
                    color = path,
                    start = Offset(size.width / 6, y),
                    end = Offset(size.width * 5 / 6, y),
                    strokeWidth = 3.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx())),
                )
            },
    ) {
        GrowthStage.entries.forEach { stage ->
            val current = stage == owl.stage
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Dimens.SpaceTiny),
                modifier = Modifier.weight(1f),
            ) {
                Box(contentAlignment = Alignment.BottomCenter, modifier = Modifier.height(OwlRole.StageNow.size)) {
                    Owl(
                        look = owl.copy(stage = stage),
                        size = if (current) OwlRole.StageNow.size else OwlRole.StageOther.size,
                        modifier = if (current) Modifier else Modifier.alpha(SILHOUETTE_ALPHA),
                    )
                }
                Text(
                    text = stringResource(stage.label),
                    style = if (current) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
                    color = if (current) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (current) {
                    Text(
                        text = stringResource(R.string.progress_stage_here),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(Dimens.CornerTile))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(horizontal = Dimens.SpaceSmall, vertical = 2.dp),
                    )
                }
            }
        }
    }
}

/** Цель: эмодзи на тарелке, «Накоплено 6 из 40», «Осталось 34» и банка с уровнем (DESIGN_PLAN 3.10). */
@Composable
private fun GoalCard(goal: GoalSummary?) {
    if (goal == null) {
        FinnyCard {
            Heading(stringResource(R.string.progress_goal))
            Explanation(stringResource(R.string.main_goal_none))
        }
        return
    }
    val reached = goal.saved.covers(goal.price)
    val left = goal.saved.shortfallTo(goal.price)
    val spoken = stringResource(R.string.main_goal_progress, goal.title, goal.saved.amount, goal.price.amount) + ". " +
        if (reached) stringResource(R.string.main_goal_reached) else "${stringResource(R.string.main_goal_left)} ${coinsText(left)}"
    FinnyCard(modifier = Modifier.clearAndSetSemantics { contentDescription = spoken }) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMedium),
        ) {
            ItemIcon(icon = goal.icon, category = SpendCategory.SAVINGS)
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceTiny), modifier = Modifier.weight(1f)) {
                Text(text = goal.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = stringResource(R.string.progress_goal_saved, goal.saved.amount, goal.price.amount),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (reached) {
                    Text(
                        text = stringResource(R.string.main_goal_reached),
                        style = MaterialTheme.typography.titleSmall,
                        color = SpendCategory.SAVINGS.color,
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSmall)) {
                        Text(
                            text = stringResource(R.string.main_goal_left),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        MoneyAmount(amount = left, style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
            Jar(level = goal.fraction, color = SpendCategory.SAVINGS.fill, modifier = Modifier.size(GOAL_JAR_WIDTH, GOAL_JAR_HEIGHT))
        }
    }
}

/**
 * Вчерашний день — одна строка «Как прошёл день 2» с тремя мини-банками;
 * внутри — полосы план/факт из итогов дня. Банки — те же, что после
 * подтверждения плана: сколько осталось от задуманного, у копилки — монеты на дне.
 */
@Composable
private fun LastDaySection(lastDay: LastDay?) {
    if (lastDay == null) {
        // Без номера: дня ещё не было, а «день 1» прочиталось бы как итоги уже прожитого дня.
        FinnyCard {
            Heading(stringResource(R.string.progress_last_day_none))
            Explanation(stringResource(R.string.progress_no_days))
        }
        return
    }
    Fold(
        title = stringResource(R.string.progress_last_day, lastDay.number),
        key = "day",
        summary = {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSmall),
                verticalArrangement = Arrangement.spacedBy(Dimens.SpaceTiny),
            ) {
                lastDay.lines.forEach { line -> DayChip(line) }
            }
        },
    ) {
        PlanFactBars(lines = lastDay.lines)
    }
}

/**
 * Направление вчерашнего дня: иконка и «по плану» / «сверх плана» словом.
 * Мини-банки с остатком плана тут путали: идеальный день, где потрачено
 * всё задуманное, выглядел теми же пустыми банками, что и перерасход.
 */
@Composable
private fun DayChip(line: BudgetLine) {
    val word = stringResource(lastDayWord(line))
    val spoken = "${stringResource(line.category.label)}: $word"
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceTiny),
        modifier = Modifier
            .clip(RoundedCornerShape(Dimens.CornerTile))
            .background(line.category.container)
            .padding(horizontal = Dimens.SpaceSmall, vertical = Dimens.SpaceTiny)
            .clearAndSetSemantics { contentDescription = spoken },
    ) {
        Icon(imageVector = line.category.icon, contentDescription = null, tint = line.category.color, modifier = Modifier.size(20.dp))
        Text(text = word, style = MaterialTheme.typography.labelMedium, color = line.category.color)
    }
}

/** Словом, соблюдён ли план направления: трата — не больше плана, копилка — не меньше (R3). */
@StringRes
internal fun lastDayWord(line: BudgetLine): Int = when {
    line.followed -> R.string.progress_day_kept
    line.category == SpendCategory.SAVINGS -> R.string.progress_day_short
    else -> R.string.progress_day_over
}

/** Задания — «Пройдено 1 из 6» с чипами тем; внутри — список пройденного. */
@Composable
private fun TasksSection(passed: List<PassedTask>, topics: List<TopicCount>) {
    Fold(
        title = stringResource(R.string.tasks_progress, passed.size, topics.sumOf { it.total }),
        key = "tasks",
        summary = {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSmall),
                verticalArrangement = Arrangement.spacedBy(Dimens.SpaceTiny),
            ) {
                topics.forEach { count -> TopicChip(count) }
            }
        },
    ) {
        if (passed.isEmpty()) {
            Explanation(stringResource(R.string.progress_no_tasks))
        } else {
            passed.forEach { task -> PassedTaskLine(task) }
        }
    }
}

/** Иконка темы и «1 из 2» на контейнере темы; для TalkBack — с названием темы. */
@Composable
private fun TopicChip(count: TopicCount) {
    val colors = count.topic.colors
    val text = stringResource(R.string.tasks_count, count.passed, count.total)
    val spoken = "${stringResource(count.topic.label)}: $text"
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceTiny),
        modifier = Modifier
            .clip(RoundedCornerShape(Dimens.CornerTile))
            .background(colors.container)
            .padding(horizontal = Dimens.SpaceSmall, vertical = Dimens.SpaceTiny)
            .clearAndSetSemantics { contentDescription = spoken },
    ) {
        Icon(imageVector = count.topic.icon, contentDescription = null, tint = colors.text, modifier = Modifier.size(20.dp))
        Text(text = text, style = MaterialTheme.typography.labelMedium, color = colors.text)
    }
}

@Composable
private fun PassedTaskLine(task: PassedTask) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceTiny)) {
        Text(text = task.title, style = MaterialTheme.typography.bodyLarge)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSmall),
            modifier = Modifier.fillMaxWidth(),
        ) {
            // Темы может не быть: задание убрали из контент-пака, а
            // прохождение осталось. Тогда её место остаётся пустым, иначе
            // награда уехала бы к левому краю, не как у соседних строк.
            val topic = task.topic
            if (topic == null) {
                Spacer(modifier = Modifier.weight(1f))
            } else {
                Text(
                    text = stringResource(topic.label),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }
            if (task.reward == Coins.ZERO) {
                Text(
                    text = stringResource(R.string.progress_task_no_reward),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                MoneyAmount(amount = task.reward, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

/** Словарик — строка с книгой «Что значат слова»; внутри термины, каждый тоже раскрывается. */
@Composable
private fun GlossarySection(terms: List<Term>) {
    Fold(
        title = stringResource(R.string.progress_glossary),
        key = "glossary",
        leading = {
            Icon(imageVector = FinnyIcons.Book, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        },
    ) {
        terms.forEach { term -> TermLine(term) }
    }
}

/**
 * Термины свёрнуты: шесть объяснений подряд заняли бы экран целиком, а
 * нужны они по одному и по случаю.
 */
@Composable
private fun TermLine(term: Term) {
    var open by rememberSaveable(term.id) { mutableStateOf(false) }
    // Подпись говорит, что будет по нажатию: свёрнутый и развёрнутый термин
    // отличаются только наличием текста, а озвучке этого не видно (ТЗ 3.6).
    val action = stringResource(
        if (open) R.string.progress_term_opened else R.string.progress_term_closed,
        term.title,
    )
    Column(
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceTiny),
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = Dimens.TouchTarget)
            .clickable(onClickLabel = action, role = Role.Button) { open = !open },
    ) {
        Text(text = term.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        if (open) {
            Explanation(term.body)
        }
    }
}

/**
 * Сворачиваемая строка «Моего прогресса» (DESIGN_PLAN 3.10): закрыта по
 * умолчанию, нажимается заголовок целиком, шеврон поворачивается. Нажимается
 * только заголовок, а не вся карточка: внутри словарика свои раскрывающиеся
 * термины. Что будет по нажатию — подписью действия, как у терминов (ТЗ 3.6).
 */
@Composable
private fun Fold(
    title: String,
    key: String,
    leading: (@Composable () -> Unit)? = null,
    summary: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    var open by rememberSaveable(key) { mutableStateOf(false) }
    val action = stringResource(if (open) R.string.progress_term_opened else R.string.progress_term_closed, title)
    FinnyCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSmall),
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = Dimens.TouchTarget)
                .clickable(onClickLabel = action, role = Role.Button) { open = !open },
        ) {
            leading?.invoke()
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceTiny), modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                summary?.invoke()
            }
            Icon(
                imageVector = FinnyIcons.Chevron,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.rotate(if (open) -90f else 90f),
            )
        }
        if (open) {
            content()
        }
    }
}

/** Силуэты других стадий — приглушены, чтобы «здесь» читалось сразу; подпись стадии остаётся в полную силу. */
private const val SILHOUETTE_ALPHA = 0.4f

/** Тропинка чуть выше нижнего края совы — на уровне «полянки». */
private val PATH_LIFT = 8.dp

// Банка цели — в пропорции основной банки 96 × 124 (Jar.kt):
// размер задаёт внешний модификатор, рисунок масштабируется сам.
private val GOAL_JAR_WIDTH = 56.dp
private val GOAL_JAR_HEIGHT = 72.dp
