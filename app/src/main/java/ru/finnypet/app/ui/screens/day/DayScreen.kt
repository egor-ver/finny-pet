package ru.finnypet.app.ui.screens.day

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import ru.finnypet.app.R
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.ui.components.BudgetLine
import ru.finnypet.app.ui.components.ButtonColumn
import ru.finnypet.app.ui.components.Confetti
import ru.finnypet.app.ui.components.FinnyButton
import ru.finnypet.app.ui.components.FinnyCard
import ru.finnypet.app.ui.components.FinnyDialog
import ru.finnypet.app.ui.components.FinnyScaffold
import ru.finnypet.app.ui.components.FinnySecondaryButton
import ru.finnypet.app.ui.components.GrowthLine
import ru.finnypet.app.ui.components.LabelledLine
import ru.finnypet.app.ui.components.MoneyAmount
import ru.finnypet.app.ui.components.Owl
import ru.finnypet.app.ui.components.OwlRole
import ru.finnypet.app.ui.components.PlanFactBars
import ru.finnypet.app.ui.components.StarMark
import ru.finnypet.app.ui.components.TopSpeechBubble
import ru.finnypet.app.ui.components.coinsText
import ru.finnypet.app.ui.components.icons.FinnyIcons
import ru.finnypet.app.ui.components.label
import ru.finnypet.app.ui.text.WordForm
import ru.finnypet.app.ui.text.wordFormOf
import ru.finnypet.app.ui.theme.Dimens
import ru.finnypet.app.ui.theme.LocalAnimationsEnabled

/**
 * Итоги игрового дня (ТЗ 2.5.9, 2.5.10).
 *
 * Пока день идёт — сравнение плана с фактом и кнопка закончить. После
 * закрытия — объяснение, что изменилось у питомца и что перенеслось на завтра.
 */
@Composable
fun DayScreen(
    onBack: () -> Unit,
    onPlan: () -> Unit,
    viewModel: DayViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    DayContent(
        state = state,
        onBack = onBack,
        onPlan = onPlan,
        onClose = viewModel::close,
        onRetry = viewModel::retry,
    )
}

@Composable
fun DayContent(
    state: DayState,
    onBack: () -> Unit,
    onPlan: () -> Unit = {},
    onClose: () -> Unit = {},
    onRetry: () -> Unit = {},
) {
    when (state) {
        DayState.Loading -> Screen(onBack = onBack) {}

        DayState.Failed -> Screen(
            onBack = onBack,
            bottomBar = {
                ButtonColumn {
                    FinnyButton(text = stringResource(R.string.action_retry), onClick = onRetry)
                }
            },
        ) {
            Text(
                text = stringResource(R.string.day_failed),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
            )
        }

        DayState.Planning -> Screen(
            onBack = onBack,
            bottomBar = {
                ButtonColumn {
                    FinnyButton(text = stringResource(R.string.budget_action_plan), onClick = onPlan)
                }
            },
        ) {
            Text(
                text = stringResource(R.string.day_not_started),
                style = MaterialTheme.typography.bodyLarge,
            )
        }

        is DayState.Running -> Running(state = state, onBack = onBack, onClose = onClose)

        is DayState.Closed -> Closed(summary = state.summary, onBack = onBack)
    }
}

@Composable
private fun Screen(
    onBack: (() -> Unit)?,
    title: String = stringResource(R.string.day_title),
    bottomBar: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    FinnyScaffold(
        title = title,
        onBack = onBack,
        bottomBar = bottomBar,
        spacing = Dimens.SpaceMedium,
        content = content,
    )
}

/**
 * День идёт: видно, к чему ребёнок пришёл, и можно закончить.
 *
 * Окно «Закончить день?» открывается сразу, до экрана (раздел 8 плана): сюда
 * приходят по «Уложить спать», и вопрос — единственное, что тут решается.
 * День не вернуть, поэтому подтверждение обязательно (ТЗ 3.6). Если сова
 * голодна, а монеты на нужное есть, она переспрашивает сама (R14).
 */
@Composable
private fun Running(state: DayState.Running, onBack: () -> Unit, onClose: () -> Unit) {
    var asking by rememberSaveable { mutableStateOf(true) }

    Screen(
        onBack = onBack,
        bottomBar = {
            ButtonColumn {
                FinnyButton(
                    text = stringResource(R.string.day_action_close),
                    onClick = { asking = true },
                    enabled = !state.closing,
                )
            }
        },
    ) {
        Text(
            text = stringResource(R.string.day_running, state.number),
            style = MaterialTheme.typography.titleMedium,
        )
        FinnyCard {
            PlanFactBars(lines = state.lines)
        }
        TotalsLine(state.lines)
    }

    if (asking) {
        FinnyDialog(
            title = stringResource(R.string.day_confirm_title),
            onDismiss = { asking = false },
            buttons = {
                FinnyButton(
                    text = stringResource(R.string.day_action_close),
                    onClick = {
                        asking = false
                        onClose()
                    },
                )
                // Передумал — обратно на главный: итогов ещё нет, смотреть здесь нечего.
                FinnySecondaryButton(
                    text = stringResource(R.string.action_back),
                    onClick = {
                        asking = false
                        onBack()
                    },
                )
            },
        ) {
            Text(
                text = state.warning ?: stringResource(R.string.day_confirm_text),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

/** «Потрачено 14 · Отложено 17» одной строкой — вместо двух карточек с общими суммами (DESIGN_PLAN 3.6). */
@Composable
private fun TotalsLine(lines: List<BudgetLine>) {
    val totals = dayTotals(lines)
    val spent = stringResource(R.string.budget_fact)
    val saved = stringResource(R.string.budget_saved)
    val spoken = "$spent ${coinsText(totals.spent)}, ${saved.lowercase()} ${coinsText(totals.saved)}"
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Dimens.Space),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceTiny),
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = spoken },
    ) {
        Total(label = spent, amount = totals.spent)
        Total(label = saved, amount = totals.saved)
    }
}

@Composable
private fun Total(label: String, amount: Coins) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSmall)) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        MoneyAmount(amount = amount, style = MaterialTheme.typography.titleMedium)
    }
}

/**
 * Итоги одним экраном (DESIGN_PLAN 3.6): реплика-итог над совой с
 * настроением дня, три звезды дня, строки объяснения, рост, что перешло на
 * завтра и совет. Смена стадии — карточка с конфетти под совой новой
 * стадии. Единственное действие — начать следующий день: «Назад» здесь некуда.
 */
@Composable
private fun Closed(summary: DaySummary, onBack: () -> Unit) {
    Screen(
        onBack = null,
        title = stringResource(R.string.day_closed, summary.number),
        bottomBar = {
            ButtonColumn {
                FinnyButton(
                    text = stringResource(R.string.day_action_next, summary.nextNumber),
                    onClick = onBack,
                )
            }
        },
    ) {
        TopSpeechBubble(text = summary.headline)
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMedium),
                modifier = Modifier.fillMaxWidth(),
            ) {
                // Сова уже новой стадии и подпрыгивает, когда выросла.
                Owl(look = summary.owl, size = OwlRole.DayEnd.size, reactOnAppear = summary.newStage != null)
                summary.newStage?.let { stage ->
                    FinnyCard(color = MaterialTheme.colorScheme.primaryContainer) {
                        Text(
                            text = stringResource(R.string.day_new_stage, stringResource(stage.label)),
                            style = MaterialTheme.typography.titleLarge,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            if (summary.newStage != null) {
                Confetti(modifier = Modifier.matchParentSize())
            }
        }
        DayStars(done = summary.checks.map { it.done })
        FinnyCard {
            summary.checks.forEach { check -> CheckLine(check) }
        }
        FinnyCard {
            GrowthLine(summary = summary.growth)
            GrowthNote(summary)
        }
        LabelledLine(label = stringResource(R.string.day_carry_over), amount = summary.carryOver)
        Tip(summary.tip)
    }
}

/**
 * Три звезды-слота 64 dp с подписями «Сыт», «По плану», «Отложил»
 * (DESIGN_PLAN 3.6). Заработанные загораются по очереди с небольшим
 * увеличением, незаработанные — спокойный контур. Звезда на месте всегда:
 * при выключенном движении она просто сразу горит (AD-8).
 */
@Composable
private fun DayStars(done: List<Boolean>) {
    Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        modifier = Modifier.fillMaxWidth(),
    ) {
        STAR_LABELS.forEachIndexed { index, label ->
            StarSlot(
                done = done.getOrElse(index) { false },
                label = stringResource(label),
                delayMs = starDelayMs(done, index),
            )
        }
    }
}

@Composable
private fun StarSlot(done: Boolean, label: String, delayMs: Int) {
    val animate = done && LocalAnimationsEnabled.current
    val scale = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (animate) {
            delay(delayMs.toLong())
            scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
        }
    }
    val spoken = stringResource(if (done) R.string.day_star_earned else R.string.day_star_missed, label)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceTiny),
        modifier = Modifier
            .widthIn(min = STAR_SLOT)
            .clearAndSetSemantics { contentDescription = spoken },
    ) {
        // Контур под звездой остаётся и у заработанной — тонкий кант вокруг
        // жёлтого, без него звезда на белом почти не видна (ТЗ 3.6).
        Box(modifier = Modifier.size(STAR_SLOT)) {
            StarMark(filled = false, modifier = Modifier.matchParentSize())
            if (done) {
                StarMark(
                    filled = true,
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer {
                            scaleX = scale.value
                            scaleY = scale.value
                        },
                )
            }
        }
        Text(text = label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
    }
}

/**
 * Строка объяснения: звезда — если звезда за неё есть, иначе пустой
 * кружок, а не ✗: «копилка без пополнения» — не ошибка (DESIGN_PLAN 3.6).
 * Без звезды TalkBack читает просто текст — упрёка в озвучке тоже нет.
 */
@Composable
private fun CheckLine(check: DayCheckView) {
    val spoken = if (check.done) stringResource(R.string.day_check_done, check.text) else check.text
    Row(
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSmall),
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = spoken },
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(MARK_SIZE)) {
            if (check.done) {
                StarMark(filled = true, modifier = Modifier.matchParentSize())
            } else {
                Box(
                    modifier = Modifier
                        .size(EMPTY_MARK_SIZE)
                        .border(2.dp, MaterialTheme.colorScheme.onSurfaceVariant, CircleShape),
                )
            }
        }
        Text(text = check.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    }
}

/**
 * Под полосой роста — причина нуля при пустом «Сыт», сказанная ровно один
 * раз, или прирост дня. В сытый день без звёзд (только при другой настройке
 * весов в `balance.json`) строки нет: остальные строки уже всё объяснили.
 */
@Composable
private fun GrowthNote(summary: DaySummary) {
    val reason = summary.noStarsReason
    val text = when {
        reason != null -> reason
        summary.earnedPoints > 0 -> stringResource(
            when (wordFormOf(summary.earnedPoints)) {
                WordForm.ONE -> R.string.day_growth_one
                WordForm.FEW -> R.string.day_growth_few
                WordForm.MANY -> R.string.day_growth_many
            },
            summary.earnedPoints,
        )
        else -> return
    }
    Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Совет дня — карточка с лампочкой (DESIGN_PLAN 3.6); лампочка молчит, совет говорит сам. */
@Composable
private fun Tip(text: String) {
    FinnyCard(color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.semantics(mergeDescendants = true) {}) {
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSmall)) {
            Icon(imageVector = FinnyIcons.Bulb, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(text = text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        }
    }
}

/** Подписи слотов в порядке строк итогов — «Сыт», «По плану», «Отложил» (AD-3). */
private val STAR_LABELS = listOf(R.string.day_star_fed, R.string.day_star_plan, R.string.day_star_saved)

private val STAR_SLOT = 64.dp
private val MARK_SIZE = 24.dp
private val EMPTY_MARK_SIZE = 18.dp
