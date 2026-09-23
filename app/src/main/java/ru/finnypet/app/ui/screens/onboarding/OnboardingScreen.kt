package ru.finnypet.app.ui.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import ru.finnypet.app.R
import ru.finnypet.app.ui.components.FinnyButton
import ru.finnypet.app.ui.components.FinnyScaffold
import ru.finnypet.app.ui.theme.Dimens

/**
 * Знакомство с игрой (ТЗ 2.5.1).
 *
 * Показывает цель игры и три типа решений — потратить на нужное, потратить
 * на желаемое, отложить. Ровно те три направления, между которыми ребёнок
 * будет делить бюджет.
 *
 * Экран не только стартовый: ТЗ 2.5.1 требует возможности вернуться к
 * подсказке в любой момент, поэтому он открывается и с главного экрана —
 * тогда кнопка возврата ведёт назад, а не дальше по игре.
 */
@Composable
fun OnboardingScreen(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    doneText: String = stringResource(R.string.action_start),
) {
    FinnyScaffold(
        title = stringResource(R.string.onboarding_title),
        modifier = modifier,
        onBack = onBack,
        bottomBar = {
            FinnyButton(text = doneText, onClick = onDone)
        },
    ) {
        Text(
            text = stringResource(R.string.onboarding_goal),
            style = MaterialTheme.typography.bodyLarge,
        )

        ChoiceCard(
            title = stringResource(R.string.onboarding_choice_mandatory_title),
            body = stringResource(R.string.onboarding_choice_mandatory_body),
            container = MaterialTheme.colorScheme.primaryContainer,
            content = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        ChoiceCard(
            title = stringResource(R.string.onboarding_choice_optional_title),
            body = stringResource(R.string.onboarding_choice_optional_body),
            container = MaterialTheme.colorScheme.secondaryContainer,
            content = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        ChoiceCard(
            title = stringResource(R.string.onboarding_choice_savings_title),
            body = stringResource(R.string.onboarding_choice_savings_body),
            container = MaterialTheme.colorScheme.tertiaryContainer,
            content = MaterialTheme.colorScheme.onTertiaryContainer,
        )

        Text(
            text = stringResource(R.string.onboarding_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Карточка одного типа решения.
 *
 * Цвет здесь не единственный признак: у каждой карточки своё название и
 * своё объяснение, поэтому три типа различимы и в чёрно-белом виде, и при
 * дальтонизме (ТЗ 3.6).
 */
@Composable
private fun ChoiceCard(
    title: String,
    body: String,
    container: Color,
    content: Color,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceTiny),
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .background(container, RoundedCornerShape(Dimens.Corner))
            .padding(Dimens.Space),
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium, color = content)
        Text(text = body, style = MaterialTheme.typography.bodyMedium, color = content)
    }
}
