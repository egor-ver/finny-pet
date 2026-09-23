package ru.finnypet.app.ui.screens.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.finnypet.app.R
import ru.finnypet.app.ui.components.FinnyButton
import ru.finnypet.app.ui.components.FinnySecondaryButton
import ru.finnypet.app.ui.theme.Dimens

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

    Column(
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSmall),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.Corner))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = Dimens.Space, vertical = Dimens.SpaceMedium),
    ) {
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
