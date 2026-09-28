package ru.finnypet.app.ui.sound

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Звуки раздаются экранам так же, как настройка движения (AD-8): компоненту
 * не нужно знать ни про настройки, ни про проигрыватель. `null` — звука нет
 * (экранные тесты без активности).
 */
val LocalGameAudio = staticCompositionLocalOf<GameAudio?> { null }

/**
 * Звук события один раз на показ: поворот экрана его не повторяет — как и
 * полёт монет, который после поворота уже не летит.
 */
@Composable
fun SoundOnce(sound: Sound) {
    val audio = LocalGameAudio.current
    var played by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!played) {
            played = true
            audio?.play(sound)
        }
    }
}
