package ru.finnypet.app.ui.screens.adult

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.finnypet.app.R
import ru.finnypet.app.domain.model.GrowthStage
import ru.finnypet.app.ui.components.ButtonColumn
import ru.finnypet.app.ui.components.FinnyButton
import ru.finnypet.app.ui.components.FinnyCard
import ru.finnypet.app.ui.components.FinnyDialog
import ru.finnypet.app.ui.components.FinnyScaffold
import ru.finnypet.app.ui.components.Explanation
import ru.finnypet.app.ui.components.FinnySecondaryButton
import ru.finnypet.app.ui.components.Heading
import ru.finnypet.app.ui.components.LabelledLine
import ru.finnypet.app.ui.components.coinsText
import ru.finnypet.app.ui.components.icons.FinnyIcons
import ru.finnypet.app.ui.components.label
import ru.finnypet.app.ui.theme.Dimens

/**
 * Раздел для взрослого (ТЗ 2.5.12).
 *
 * Колонка, а не список: разделов пять и все они постоянного размера — тем
 * ровно три, целей приложения четыре, и от игры их число не растёт.
 *
 * Вид спокойный, «взрослый» (DESIGN_PLAN 3.11): без совы и эффектов, каждый
 * раздел — белая карточка, значения строк одним стилем `titleMedium`.
 */
@Composable
fun AdultScreen(
    onBack: () -> Unit,
    onDemoStarted: () -> Unit,
    onGameDeleted: () -> Unit,
    viewModel: AdultViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val exit by viewModel.exit.collectAsStateWithLifecycle()

    LaunchedEffect(exit) {
        when (exit) {
            AdultExit.DEMO_STARTED -> onDemoStarted()
            AdultExit.GAME_DELETED -> onGameDeleted()
            null -> Unit
        }
    }

    AdultContent(
        state = state,
        onBack = onBack,
        onRetry = viewModel::retry,
        onAward = viewModel::award,
        onDismissAward = viewModel::dismissAward,
        onStartDemo = viewModel::startDemo,
        onDeleteGame = viewModel::deleteGame,
    )
}

@Composable
fun AdultContent(
    state: AdultState,
    onBack: () -> Unit,
    onRetry: () -> Unit = {},
    onAward: () -> Unit = {},
    onDismissAward: () -> Unit = {},
    onStartDemo: () -> Unit = {},
    onDeleteGame: () -> Unit = {},
) {
    when (state) {
        AdultState.Loading -> Screen(onBack) {}

        AdultState.Failed -> Screen(
            onBack = onBack,
            bottomBar = {
                ButtonColumn {
                    FinnyButton(text = stringResource(R.string.action_retry), onClick = onRetry)
                }
            },
        ) {
            Text(
                text = stringResource(R.string.adult_failed),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
            )
        }

        is AdultState.Ready -> Ready(
            state = state,
            onBack = onBack,
            onAward = onAward,
            onDismissAward = onDismissAward,
            onStartDemo = onStartDemo,
            onDeleteGame = onDeleteGame,
        )
    }
}

@Composable
private fun Ready(
    state: AdultState.Ready,
    onBack: () -> Unit,
    onAward: () -> Unit,
    onDismissAward: () -> Unit,
    onStartDemo: () -> Unit,
    onDeleteGame: () -> Unit,
) {
    var askingDelete by rememberSaveable { mutableStateOf(false) }

    Screen(
        onBack = onBack,
    ) {
        Talk(state.talk)
        About(state.about)
        Topics(state.topics)
        Overview(state)
        Bonus(state, onAward)
        Demo(onStartDemo)
        DeleteGame(onAsk = { askingDelete = true })
    }

    if (askingDelete) {
        FinnyDialog(
            title = stringResource(R.string.adult_delete_confirm_title),
            onDismiss = { askingDelete = false },
            buttons = {
                FinnyButton(
                    text = stringResource(R.string.adult_delete_confirm),
                    onClick = {
                        askingDelete = false
                        onDeleteGame()
                    },
                )
                FinnySecondaryButton(
                    text = stringResource(R.string.action_back),
                    onClick = { askingDelete = false },
                )
            },
        ) {
            Text(
                text = stringResource(R.string.adult_delete_confirm_text),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }

    state.awarded?.let { text ->
        FinnyDialog(
            title = stringResource(R.string.adult_bonus),
            onDismiss = onDismissAward,
            buttons = {
                FinnyButton(text = stringResource(R.string.action_ok), onClick = onDismissAward)
            },
        ) {
            Text(text = text, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/**
 * Первой — подсказка для разговора: ради неё взрослый и открывает раздел.
 * Синяя плашка с лампочкой, а не персиковая: персиковый в игре значит
 * «Желаемое», и совет читался бы как про покупки (DESIGN_PLAN 3.11).
 */
@Composable
private fun Talk(text: String) {
    FinnyCard(
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.semantics(mergeDescendants = true) {},
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSmall),
        ) {
            Icon(imageVector = FinnyIcons.Bulb, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Heading(stringResource(R.string.adult_talk))
        }
        Text(text = text, style = MaterialTheme.typography.bodyLarge)
    }
}

/**
 * «Чему учит игра» — первый абзац на виду, остальные по «Подробнее»:
 * четыре абзаца подряд отодвигали темы и прогресс ребёнка за экран, а
 * читают их один раз.
 */
@Composable
private fun About(about: List<String>) {
    if (about.isEmpty()) return
    var open by rememberSaveable { mutableStateOf(false) }
    FinnyCard {
        Heading(stringResource(R.string.adult_about))
        aboutShown(about, open).forEach { line -> Explanation(line) }
        if (about.size > 1) {
            // Без внутренних полей кнопки: слово стоит ровно по левому краю
            // абзаца, а не сдвинуто на 12 dp; высота нажатия — те же 48 dp.
            TextButton(
                onClick = { open = !open },
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.defaultMinSize(minHeight = Dimens.TouchTarget),
            ) {
                Text(
                    text = stringResource(if (open) R.string.adult_about_less else R.string.adult_about_more),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

/** Свёрнутый раздел показывает только первый абзац — он и отвечает на «чему учит». */
internal fun aboutShown(about: List<String>, open: Boolean): List<String> =
    if (open) about else about.take(1)

/**
 * Темы перечислены все, включая нетронутые, и только числами: ТЗ 2.5.12
 * запрещает негативные оценки ребёнка, поэтому «не пройдено» здесь не звучит.
 */
@Composable
private fun Topics(topics: List<TopicProgress>) {
    FinnyCard {
        Heading(stringResource(R.string.adult_topics))
        topics.forEach { topic ->
            LabelledLine(
                label = stringResource(topic.topic.label),
                value = when (topic.total) {
                    0 -> stringResource(R.string.adult_topic_none)
                    else -> stringResource(R.string.adult_topic_passed, topic.passed, topic.total)
                },
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
private fun Overview(state: AdultState.Ready) {
    val style = MaterialTheme.typography.titleMedium
    FinnyCard {
        Heading(stringResource(R.string.adult_overview, state.childName))
        LabelledLine(stringResource(R.string.adult_days), state.days.toString(), style)
        LabelledLine(stringResource(R.string.adult_stage), stringResource(state.stage.label), style)
        // Два счёта роста рядом и подписаны: «Очки роста 4» без пояснения
        // спорили с «До взрослого 0 из 6» на главном — там звёзды считаются
        // внутри стадии (DESIGN_PLAN 3.1), а здесь взрослому нужен и общий итог.
        LabelledLine(stringResource(R.string.adult_points), state.points.toString(), style)
        state.growth?.let { growth ->
            LabelledLine(
                stringResource(if (growth.next == GrowthStage.GROWN) R.string.main_growth_to_grown else R.string.main_growth_to_young),
                stringResource(R.string.main_growth_points, growth.points, growth.target),
                style,
            )
        }
        LabelledLine(stringResource(R.string.adult_balance), state.balance, style)
        LabelledLine(stringResource(R.string.adult_saved), state.saved, style)
    }
}

@Composable
private fun Bonus(state: AdultState.Ready, onAward: () -> Unit) {
    val bonus = coinsText(state.bonus)
    FinnyCard {
        Heading(stringResource(R.string.adult_bonus))
        Explanation(stringResource(R.string.adult_bonus_explain, bonus))
        when (state.award) {
            AwardState.AVAILABLE ->
                FinnyButton(text = stringResource(R.string.adult_bonus_action, bonus), onClick = onAward)

            AwardState.USED -> Explanation(stringResource(R.string.adult_bonus_used))
            AwardState.NO_DAY -> Explanation(stringResource(R.string.adult_bonus_no_day))
        }
    }
}

/**
 * Демонстрационный режим для проверяющего (ТЗ 2.5.13). Живёт здесь, за
 * барьером: ребёнку он не нужен, а эксперт этот раздел откроет по ТЗ.
 */
@Composable
private fun Demo(onStart: () -> Unit) {
    FinnyCard {
        Heading(stringResource(R.string.adult_demo))
        Explanation(stringResource(R.string.adult_demo_explain))
        FinnyButton(text = stringResource(R.string.adult_demo_action), onClick = onStart)
    }
}

/** Удаление игры (ТЗ 3.5): последним разделом и с подтверждением (ТЗ 3.6). */
@Composable
private fun DeleteGame(onAsk: () -> Unit) {
    FinnyCard {
        Heading(stringResource(R.string.adult_delete))
        Explanation(stringResource(R.string.adult_delete_explain))
        FinnySecondaryButton(text = stringResource(R.string.adult_delete_action), onClick = onAsk)
    }
}

@Composable
private fun Screen(
    onBack: () -> Unit,
    bottomBar: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    FinnyScaffold(
        title = stringResource(R.string.adult_title),
        onBack = onBack,
        spacing = Dimens.SpaceMedium,
        bottomBar = bottomBar,
        content = content,
    )
}
