package ru.finnypet.app.ui.screens.adult

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.finnypet.app.R
import ru.finnypet.app.ui.components.ButtonColumn
import ru.finnypet.app.ui.components.FinnyButton
import ru.finnypet.app.ui.components.FinnyDialog
import ru.finnypet.app.ui.components.FinnyScaffold
import ru.finnypet.app.ui.components.Explanation
import ru.finnypet.app.ui.components.FinnySecondaryButton
import ru.finnypet.app.ui.components.Heading
import ru.finnypet.app.ui.components.LabelledLine
import ru.finnypet.app.ui.components.coinsText
import ru.finnypet.app.ui.components.label
import ru.finnypet.app.ui.theme.Dimens

/**
 * Раздел для взрослого (ТЗ 2.5.12).
 *
 * Колонка, а не список: разделов пять и все они постоянного размера — тем
 * ровно три, целей приложения четыре, и от игры их число не растёт.
 */
@Composable
fun AdultScreen(
    onBack: () -> Unit,
    viewModel: AdultViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    AdultContent(
        state = state,
        onBack = onBack,
        onRetry = viewModel::retry,
        onAward = viewModel::award,
        onDismissAward = viewModel::dismissAward,
        onSound = viewModel::setSound,
        onAnimations = viewModel::setAnimations,
    )
}

@Composable
fun AdultContent(
    state: AdultState,
    onBack: () -> Unit,
    onRetry: () -> Unit = {},
    onAward: () -> Unit = {},
    onDismissAward: () -> Unit = {},
    onSound: (Boolean) -> Unit = {},
    onAnimations: (Boolean) -> Unit = {},
) {
    when (state) {
        AdultState.Loading -> Screen(onBack) {}

        AdultState.Failed -> Screen(
            onBack = onBack,
            bottomBar = {
                ButtonColumn {
                    FinnyButton(text = stringResource(R.string.action_retry), onClick = onRetry)
                    FinnySecondaryButton(text = stringResource(R.string.action_back), onClick = onBack)
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
            onSound = onSound,
            onAnimations = onAnimations,
        )
    }
}

@Composable
private fun Ready(
    state: AdultState.Ready,
    onBack: () -> Unit,
    onAward: () -> Unit,
    onDismissAward: () -> Unit,
    onSound: (Boolean) -> Unit,
    onAnimations: (Boolean) -> Unit,
) {
    Screen(
        onBack = onBack,
        bottomBar = {
            ButtonColumn {
                FinnySecondaryButton(text = stringResource(R.string.action_back), onClick = onBack)
            }
        },
    ) {
        About(state.about)
        Topics(state.topics)
        Overview(state)
        Bonus(state, onAward)
        Settings(state, onSound, onAnimations)
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

@Composable
private fun ColumnScope.About(about: List<String>) {
    if (about.isEmpty()) return
    Heading(stringResource(R.string.adult_about))
    about.forEach { line -> Explanation(line) }
}

/**
 * Темы перечислены все, включая нетронутые, и только числами: ТЗ 2.5.12
 * запрещает негативные оценки ребёнка, поэтому «не пройдено» здесь не звучит.
 */
@Composable
private fun ColumnScope.Topics(topics: List<TopicProgress>) {
    Heading(stringResource(R.string.adult_topics))
    topics.forEach { topic ->
        LabelledLine(
            label = stringResource(topic.topic.label),
            value = when (topic.total) {
                0 -> stringResource(R.string.adult_topic_none)
                else -> stringResource(R.string.adult_topic_passed, topic.passed, topic.total)
            },
        )
    }
}

@Composable
private fun ColumnScope.Overview(state: AdultState.Ready) {
    Heading(stringResource(R.string.adult_overview, state.childName))
    LabelledLine(stringResource(R.string.adult_days), state.days.toString())
    LabelledLine(stringResource(R.string.adult_stage), stringResource(state.stage.label))
    LabelledLine(stringResource(R.string.adult_points), state.points.toString())
    LabelledLine(stringResource(R.string.adult_balance), state.balance)
    LabelledLine(stringResource(R.string.adult_saved), state.saved)
}

@Composable
private fun ColumnScope.Bonus(state: AdultState.Ready, onAward: () -> Unit) {
    val bonus = coinsText(state.bonus)
    Heading(stringResource(R.string.adult_bonus))
    Explanation(stringResource(R.string.adult_bonus_explain, bonus))
    when (state.award) {
        AwardState.AVAILABLE ->
            FinnyButton(text = stringResource(R.string.adult_bonus_action, bonus), onClick = onAward)

        AwardState.USED -> Explanation(stringResource(R.string.adult_bonus_used))
        AwardState.NO_DAY -> Explanation(stringResource(R.string.adult_bonus_no_day))
    }
}

/** Звук и анимации отключаются (ТЗ 3.6), и делает это взрослый. */
@Composable
private fun ColumnScope.Settings(
    state: AdultState.Ready,
    onSound: (Boolean) -> Unit,
    onAnimations: (Boolean) -> Unit,
) {
    Heading(stringResource(R.string.adult_settings))
    Toggle(stringResource(R.string.adult_sound), state.soundEnabled, onSound)
    Toggle(stringResource(R.string.adult_animations), state.animationsEnabled, onAnimations)
}

@Composable
private fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSmall),
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = Dimens.TouchTarget)
            // Нажимается вся строка, а не только сам переключатель: по ТЗ 3.6
            // область нажатия не меньше 48 dp, а переключатель уже.
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = null)
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
