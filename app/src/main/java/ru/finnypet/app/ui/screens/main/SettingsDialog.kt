package ru.finnypet.app.ui.screens.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.finnypet.app.R
import ru.finnypet.app.domain.repository.SettingsRepository
import ru.finnypet.app.ui.components.FinnyButton
import ru.finnypet.app.ui.components.FinnyDialog
import ru.finnypet.app.ui.theme.Dimens
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

/** Три переключателя окна настроек. */
data class SettingsView(val sound: Boolean, val music: Boolean, val animations: Boolean)

/**
 * Настройки звука и движения на главном (решение владельца 28.09): ребёнок
 * выключает их сам, без раздела взрослого. Хранятся в [SettingsRepository] —
 * переживают перезапуск и смену профиля; приложение слушает их оттуда же
 * (`MainActivity`), поэтому переключатель действует сразу.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
) : ViewModel() {

    /** `null` — настройки ещё читаются: окно без переключателей, а не с ложными «вкл». */
    val state: StateFlow<SettingsView?> = combine(
        settings.observeSoundEnabled(),
        settings.observeMusicEnabled(),
        settings.observeAnimationsEnabled(),
        ::SettingsView,
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    fun setSound(enabled: Boolean) = save { settings.setSoundEnabled(enabled) }

    fun setMusic(enabled: Boolean) = save { settings.setMusicEnabled(enabled) }

    fun setAnimations(enabled: Boolean) = save { settings.setAnimationsEnabled(enabled) }

    /**
     * Сбой записи не роняет игру (ТЗ 3.4): переключатель показывает
     * сохранённое, поэтому просто останется как был — это и видно.
     */
    private fun save(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                // Значение не записалось — экран и так показывает прежнее.
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

/** Окно настроек поверх главного — без нового экрана и маршрута. */
@Composable
fun SettingsDialog(onDismiss: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SettingsContent(
        state = state,
        onSound = viewModel::setSound,
        onMusic = viewModel::setMusic,
        onAnimations = viewModel::setAnimations,
        onDismiss = onDismiss,
    )
}

/** Отрисовка отдельно от ViewModel — окно показывается в тесте с любым состоянием. */
@Composable
fun SettingsContent(
    state: SettingsView?,
    onSound: (Boolean) -> Unit,
    onMusic: (Boolean) -> Unit,
    onAnimations: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    FinnyDialog(
        title = stringResource(R.string.settings_title),
        onDismiss = onDismiss,
        buttons = { FinnyButton(text = stringResource(R.string.action_done), onClick = onDismiss) },
    ) {
        if (state != null) {
            Toggle(stringResource(R.string.settings_sound), state.sound, onSound)
            Toggle(stringResource(R.string.settings_music), state.music, onMusic)
            Toggle(stringResource(R.string.settings_animations), state.animations, onAnimations)
        }
    }
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
        Switch(
            checked = checked,
            onCheckedChange = null,
            // `outline` (#E4D8C6) почти сливается с кремовым фоном — 1,3:1.
            // Выключенное состояние берёт свои цвета явно, а не outline.
            colors = SwitchDefaults.colors(
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        )
    }
}
