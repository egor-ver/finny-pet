package ru.finnypet.app

import android.media.AudioManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import ru.finnypet.app.domain.repository.SettingsRepository
import ru.finnypet.app.ui.Startup
import ru.finnypet.app.ui.StartupViewModel
import ru.finnypet.app.ui.navigation.CreatePet
import ru.finnypet.app.ui.navigation.FinnyNavHost
import ru.finnypet.app.ui.navigation.Main
import ru.finnypet.app.ui.theme.FinnypetTheme
import ru.finnypet.app.ui.sound.GameAudio
import ru.finnypet.app.ui.sound.LocalGameAudio
import ru.finnypet.app.ui.theme.LocalAnimationsEnabled
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settings: SettingsRepository

    @Inject
    lateinit var audio: GameAudio

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Кнопки громкости правят громкость игры (звуки и мелодия — поток
        // музыки), даже когда сейчас ничего не звучит; иначе между звуками они
        // правили бы звонок, и ребёнок не смог бы сделать игру тише.
        volumeControlStream = AudioManager.STREAM_MUSIC
        // Настройки звука слушаются и в свёрнутом приложении: выключенная там
        // мелодия не должна зазвучать при возврате даже на миг (AD-17).
        lifecycleScope.launch { settings.observeSoundEnabled().collect(audio::setSoundEnabled) }
        lifecycleScope.launch { settings.observeMusicEnabled().collect(audio::setMusicEnabled) }
        setContent {
            // Настройки доступности читаются один раз на всё приложение и
            // раздаются через CompositionLocal: ТЗ 3.6 требует, чтобы звук
            // и анимации отключались, и флаг должен доходить до компонентов,
            // а не лежать в хранилище без дела.
            val animations by settings.observeAnimationsEnabled()
                .collectAsStateWithLifecycle(initialValue = true)

            val startup: StartupViewModel = hiltViewModel()
            val state by startup.state.collectAsStateWithLifecycle()

            FinnypetTheme {
                CompositionLocalProvider(
                    LocalAnimationsEnabled provides animations,
                    LocalGameAudio provides audio,
                ) {
                    // Граф строится только когда известно, есть ли профиль:
                    // стартовый экран после сборки уже не поменять, а начать
                    // с создания питомца при готовом профиле значит нарушить
                    // ТЗ 2.5.13 о сохранении состояния.
                    when (state) {
                        // Чтение профиля занимает миллисекунды, но за них
                        // не должно мелькать белое системное окно поверх
                        // тёмной темы — держим фон приложения.
                        Startup.Loading -> Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background),
                        )

                        Startup.NoProfile -> FinnyNavHost(startDestination = CreatePet)
                        Startup.HasProfile -> FinnyNavHost(startDestination = Main())
                    }
                }
            }
        }
    }

    // onResume/onPause, а не onStart/onStop: погасший экран на части телефонов
    // только ставит активность на паузу, и мелодия играла бы в темноте.
    override fun onResume() {
        super.onResume()
        audio.setVisible(true)
    }

    override fun onPause() {
        audio.setVisible(false)
        super.onPause()
    }
}
