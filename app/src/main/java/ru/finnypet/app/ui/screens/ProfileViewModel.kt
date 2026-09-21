package ru.finnypet.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ru.finnypet.app.domain.model.ProfileId
import ru.finnypet.app.domain.repository.ProfileRepository
import kotlin.coroutines.cancellation.CancellationException

/**
 * Общее у всех экранов игры: действие выполняется от имени активного профиля
 * и никогда не роняет экран.
 *
 * ТЗ 3.4 запрещает тупики, поэтому любой сбой превращается в [failed] —
 * состояние с кнопкой повтора, а не в исключение. Профиль ждётся, а не
 * берётся разом: на экран можно попасть сразу после создания питомца, и
 * запись профиля может ещё не дойти до подписчиков.
 */
abstract class ProfileViewModel(
    protected val profiles: ProfileRepository,
) : ViewModel() {

    protected val failed = MutableStateFlow(false)

    protected fun act(block: suspend (ProfileId) -> Unit) = guarded {
        block(profiles.observeActive().filterNotNull().first().id)
    }

    /**
     * Действие, которому профиль не нужен: настройки живут отдельно от игры
     * и переживают смену профиля, ждать его было бы неправдой.
     */
    protected fun guarded(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                failed.value = true
            }
        }
    }

    /** Повтор после сбоя: сбросить признак и заново выполнить [block]. */
    protected fun retryWith(block: suspend (ProfileId) -> Unit) {
        failed.value = false
        act(block)
    }
}
