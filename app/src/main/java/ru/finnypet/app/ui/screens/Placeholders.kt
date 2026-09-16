package ru.finnypet.app.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import ru.finnypet.app.R
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.Stat
import ru.finnypet.app.ui.components.ButtonColumn
import ru.finnypet.app.ui.components.FinnyButton
import ru.finnypet.app.ui.components.FinnyScaffold
import ru.finnypet.app.ui.components.FinnySecondaryButton
import ru.finnypet.app.ui.components.MoneyCard
import ru.finnypet.app.ui.components.StatBar

/**
 * Временные экраны шага 5.
 *
 * Нужны, чтобы проверить навигацию и дизайн-систему на устройстве до того,
 * как появятся настоящие экраны. Каждый будет заменён на своём шаге:
 * онбординг и создание питомца — на шестом, главный — на седьмом.
 */

@Composable
fun OnboardingScreen(onStart: () -> Unit) {
    FinnyScaffold(
        title = stringResource(R.string.onboarding_title),
        bottomBar = {
            FinnyButton(text = stringResource(R.string.action_start), onClick = onStart)
        },
    ) {
        Text(
            text = stringResource(R.string.onboarding_placeholder),
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = stringResource(R.string.onboarding_three_choices),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun CreatePetScreen(onBack: () -> Unit, onCreated: () -> Unit) {
    FinnyScaffold(
        title = stringResource(R.string.create_pet_title),
        onBack = onBack,
        bottomBar = {
            ButtonColumn {
                FinnyButton(text = stringResource(R.string.action_done), onClick = onCreated)
                FinnySecondaryButton(text = stringResource(R.string.action_back), onClick = onBack)
            }
        },
    ) {
        Text(
            text = stringResource(R.string.create_pet_placeholder),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
fun MainScreen() {
    FinnyScaffold(title = stringResource(R.string.main_title)) {
        MoneyCard(label = stringResource(R.string.main_balance), amount = Coins(80))
        MoneyCard(label = stringResource(R.string.main_savings), amount = Coins(30))

        Text(
            text = stringResource(R.string.main_pet_state),
            style = MaterialTheme.typography.titleMedium,
        )
        StatBar(label = stringResource(R.string.stat_mood), stat = Stat(80))
        StatBar(
            label = stringResource(R.string.stat_satiety),
            stat = Stat(55),
            color = MaterialTheme.colorScheme.secondary,
        )
        StatBar(
            label = stringResource(R.string.stat_care),
            stat = Stat(30),
            color = MaterialTheme.colorScheme.tertiary,
        )
    }
}
