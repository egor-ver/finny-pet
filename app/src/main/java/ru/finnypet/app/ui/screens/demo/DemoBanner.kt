package ru.finnypet.app.ui.screens.demo

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.finnypet.app.R
import ru.finnypet.app.ui.components.FinnyButton
import ru.finnypet.app.ui.components.FinnyCard
import ru.finnypet.app.ui.components.FinnySecondaryButton

/**
 * Полоса демонстрационного режима над главным экраном (ТЗ 2.5.13). Своя
 * вьюмодель: демонстрация — не часть игры ребёнка, MainViewModel о ней не знает.
 */
@Composable
fun DemoBanner(onNeedsOnboarding: () -> Unit, viewModel: DemoViewModel = hiltViewModel()) {
    val active by viewModel.active.collectAsStateWithLifecycle()
    val needsOnboarding by viewModel.needsOnboarding.collectAsStateWithLifecycle()

    LaunchedEffect(needsOnboarding) {
        if (needsOnboarding) onNeedsOnboarding()
    }

    DemoBannerContent(visible = active, onPlayDay = viewModel::playDay, onExit = viewModel::exit)
}

@Composable
fun DemoBannerContent(visible: Boolean, onPlayDay: () -> Unit = {}, onExit: () -> Unit = {}) {
    if (!visible) return

    FinnyCard(color = MaterialTheme.colorScheme.secondaryContainer) {
        // Словом, а не только цветом подложки: ребёнок, которому оставили
        // приложение в демонстрации, иначе не поймёт, чей это питомец.
        Text(
            text = stringResource(R.string.demo_banner),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        FinnyButton(text = stringResource(R.string.demo_play_day), onClick = onPlayDay)
        FinnySecondaryButton(text = stringResource(R.string.demo_exit), onClick = onExit)
    }
}
