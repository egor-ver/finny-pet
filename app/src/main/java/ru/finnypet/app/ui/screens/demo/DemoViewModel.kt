package ru.finnypet.app.ui.screens.demo

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

/** Демонстрационный режим для экспертной проверки (ТЗ 2.5.13). */
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

    private val leftWithoutGame = MutableStateFlow(false)

    /** Факт для экрана: демонстрация закрыта, а своей игры нет — пора на знакомство. */
    val needsOnboarding: StateFlow<Boolean> = leftWithoutGame.asStateFlow()

    /** Против двойного нажатия: день и выход не должны пересекаться друг с другом. */
    private val busy = Mutex()

    fun playDay() = guarded { busy.withLock { playDemoDay() } }

    fun exit() = guarded {
        busy.withLock {
            if (exitDemo() == AfterDemo.ONBOARDING) leftWithoutGame.value = true
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
