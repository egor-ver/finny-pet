package ru.finnypet.app.ui.screens.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.finnypet.app.R
import ru.finnypet.app.ui.components.FinnyButton
import ru.finnypet.app.ui.components.FinnyDialog
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

/**
 * Тонкая полоса в одну строку (Б24): раньше карточка с кнопками столбиком
 * занимала ~170 dp, а на главном по ТЗ 2.5.3 дорог каждый десяток. При
 * крупном системном шрифте `FlowRow` сам переносит слово, которому не
 * хватило места, на вторую строку — вместо того чтобы сжимать его до обрезки.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DemoBannerContent(visible: Boolean, onPlayDay: () -> Unit = {}, onExit: () -> Unit = {}) {
    if (!visible) return
    var askingExit by rememberSaveable { mutableStateOf(false) }
    val demoBannerDescription = stringResource(R.string.demo_banner)

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSmall),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSmall),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.Corner))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = Dimens.Space, vertical = Dimens.SpaceSmall),
    ) {
        Text(
            text = stringResource(R.string.demo_badge),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.clearAndSetSemantics { contentDescription = demoBannerDescription },
        )
        DemoAction(text = stringResource(R.string.demo_play_day), onClick = onPlayDay)
        DemoAction(
            text = stringResource(R.string.demo_exit_short),
            description = stringResource(R.string.demo_exit),
            onClick = { askingExit = true },
        )
    }

    // Выход стирает всё сделанное в демонстрации — без спроса нельзя (ТЗ 3.6).
    if (askingExit) {
        FinnyDialog(
            title = stringResource(R.string.demo_exit_confirm_title),
            onDismiss = { askingExit = false },
            buttons = {
                FinnyButton(
                    text = stringResource(R.string.demo_exit_confirm),
                    onClick = {
                        askingExit = false
                        onExit()
                    },
                )
                FinnySecondaryButton(
                    text = stringResource(R.string.action_back),
                    onClick = { askingExit = false },
                )
            },
        ) {
            Text(
                text = stringResource(R.string.demo_exit_confirm_text),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

/**
 * Действие полосы словом, а не только цветом подложки (ТЗ 3.6). Видимое
 * слово короче полного — оно не помещается на тонкой полосе (Б21), поэтому
 * для озвучки при необходимости передаётся полная подпись отдельно.
 */
@Composable
private fun DemoAction(text: String, onClick: () -> Unit, description: String? = null) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(Dimens.Corner))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(role = Role.Button, onClick = onClick)
            .defaultMinSize(minWidth = Dimens.TouchTarget, minHeight = Dimens.TouchTarget)
            .padding(horizontal = Dimens.SpaceMedium)
            .then(
                if (description == null) Modifier else Modifier.clearAndSetSemantics { contentDescription = description },
            ),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}
