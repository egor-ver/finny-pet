package ru.finnypet.app.ui.screens.main

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import ru.finnypet.app.R
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.Stat
import ru.finnypet.app.ui.components.FinnyScaffold
import ru.finnypet.app.ui.components.MoneyCard
import ru.finnypet.app.ui.components.StatBar

/**
 * Временный главный экран.
 *
 * Показывает вёрстку на выдуманных числах. Настоящие данные — профиль,
 * баланс, накопления, цель, показатели питомца и активное задание
 * одновременно, как требует ТЗ 2.5.3, — появятся на шаге 7.
 */
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
