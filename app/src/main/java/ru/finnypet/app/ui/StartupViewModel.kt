package ru.finnypet.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import ru.finnypet.app.domain.repository.ProfileRepository
import javax.inject.Inject

/**
 * С какого экрана начинать.
 *
 * ТЗ 2.5.13 требует, чтобы профиль переживал закрытие приложения, а шаг 11
 * Приложения А проверяет это прямо: эксперт закрывает игру и открывает
 * заново, ожидая увидеть свой прогресс. Поэтому стартовый экран зависит от
 * того, есть ли сохранённый профиль.
 *
 * [Loading] нужен отдельно от [NoProfile]: без него на долю секунды
 * показалось бы знакомство с игрой даже тому, у кого профиль давно есть.
 */
sealed interface Startup {

    data object Loading : Startup

    data object NoProfile : Startup

    data object HasProfile : Startup
}

@HiltViewModel
class StartupViewModel @Inject constructor(
    profiles: ProfileRepository,
) : ViewModel() {

    val state: StateFlow<Startup> = profiles.observeActive()
        .map { profile -> if (profile == null) Startup.NoProfile else Startup.HasProfile }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = Startup.Loading,
        )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
