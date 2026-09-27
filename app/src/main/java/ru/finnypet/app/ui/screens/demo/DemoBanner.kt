package ru.finnypet.app.ui.screens.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
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
 * Чип демонстрационного режима в шапке главного экрана (ТЗ 2.5.13,
 * DESIGN_PLAN 3.1: раньше полоса ~64 dp над совой, теперь — одна кнопка
 * рядом с кошельком). Своя вьюмодель: демонстрация — не часть игры ребёнка,
 * MainViewModel о ней не знает.
 */
@Composable
fun DemoChip(onNeedsOnboarding: () -> Unit, viewModel: DemoViewModel = hiltViewModel()) {
    val active by viewModel.active.collectAsStateWithLifecycle()
    val needsOnboarding by viewModel.needsOnboarding.collectAsStateWithLifecycle()

    LaunchedEffect(needsOnboarding) {
        if (needsOnboarding) onNeedsOnboarding()
    }

    DemoChipContent(visible = active, onPlayDay = viewModel::playDay, onExit = viewModel::exit)
}

/**
 * Нажатие открывает окно с двумя действиями — «Прожить день» и выход;
 * выход отдельно подтверждается (ниже), потому что стирает демонстрацию без возврата.
 */
@Composable
fun DemoChipContent(visible: Boolean, onPlayDay: () -> Unit = {}, onExit: () -> Unit = {}) {
    if (!visible) return
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    var askingExit by rememberSaveable { mutableStateOf(false) }
    val badge = stringResource(R.string.demo_badge)
    val description = stringResource(R.string.demo_banner)

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(Dimens.Corner))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .clickable(role = Role.Button, onClickLabel = description, onClick = { menuOpen = true })
            .defaultMinSize(minWidth = Dimens.TouchTarget, minHeight = Dimens.TouchTarget)
            .padding(horizontal = Dimens.SpaceMedium)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        Text(text = badge, style = MaterialTheme.typography.labelLarge)
    }

    if (menuOpen) {
        FinnyDialog(
            title = badge,
            onDismiss = { menuOpen = false },
            buttons = {
                FinnyButton(
                    text = stringResource(R.string.demo_play_day),
                    onClick = { menuOpen = false; onPlayDay() },
                )
                FinnySecondaryButton(
                    text = stringResource(R.string.demo_exit),
                    onClick = { menuOpen = false; askingExit = true },
                )
            },
            content = {},
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
