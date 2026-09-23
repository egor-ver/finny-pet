package ru.finnypet.app.ui.screens.demo

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.finnypet.app.domain.repository.ProfileRepository
import ru.finnypet.app.domain.usecase.AfterDemo
import ru.finnypet.app.domain.usecase.ExitDemo
import ru.finnypet.app.domain.usecase.PlayDemoDay
import ru.finnypet.app.ui.screens.ProfileViewModel
import javax.inject.Inject

/**
 * Демонстрационный режим для экспертной проверки (ТЗ 2.5.13).
 *
 * Живёт отдельно от главного экрана: игра ребёнка про него ничего не знает,
 * а полоса появляется поверх любого состояния.
 */
@HiltViewModel
class DemoViewModel @Inject constructor(
    profiles: ProfileRepository,
    private val playDemoDay: PlayDemoDay,
    private val exitDemo: ExitDemo,
) : ProfileViewModel(profiles) {

    val active: StateFlow<Boolean> = profiles.observeActive()
        .map { it?.isTest == true }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = false,
        )

    /** Против двойного нажатия: второй вызов начал бы день, пока первый не дописал свой. */
    private val playing = Mutex()

    fun playDay() = guarded { playing.withLock { playDemoDay() } }

    /** Куда идти после выхода, решает экран: своей игры могло и не быть. */
    fun exit(onEnded: (AfterDemo) -> Unit) = guarded { onEnded(exitDemo()) }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
