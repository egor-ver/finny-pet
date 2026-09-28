package ru.finnypet.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import ru.finnypet.app.domain.model.Profile
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

    val state: StateFlow<Startup> = startupOf(profiles.observeActive())
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = Startup.Loading,
        )
}

/**
 * Стартовый экран решается один раз, по первому значению профиля. Дальше
 * переходы ведёт сам граф: если бы решение следило за профилем, создание
 * питомца перестроило бы граф со стартом на обычном главном, и переход
 * «главный под обучением» потерялся бы (ТЗ 2.5.1).
 */
internal fun startupOf(active: Flow<Profile?>): Flow<Startup> = flow {
    emit(if (active.first() == null) Startup.NoProfile else Startup.HasProfile)
}
