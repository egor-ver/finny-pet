package ru.finnypet.app

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
import dagger.hilt.android.AndroidEntryPoint
import ru.finnypet.app.domain.repository.SettingsRepository
import ru.finnypet.app.ui.Startup
import ru.finnypet.app.ui.StartupViewModel
import ru.finnypet.app.ui.navigation.FinnyNavHost
import ru.finnypet.app.ui.navigation.Main
import ru.finnypet.app.ui.navigation.Onboarding
import ru.finnypet.app.ui.theme.FinnypetTheme
import ru.finnypet.app.ui.theme.LocalAnimationsEnabled
import ru.finnypet.app.ui.theme.LocalSoundEnabled
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settings: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // Настройки доступности читаются один раз на всё приложение и
            // раздаются через CompositionLocal: ТЗ 3.6 требует, чтобы звук
            // и анимации отключались, и флаг должен доходить до компонентов,
            // а не лежать в хранилище без дела.
            val animations by settings.observeAnimationsEnabled()
                .collectAsStateWithLifecycle(initialValue = true)
            val sound by settings.observeSoundEnabled()
                .collectAsStateWithLifecycle(initialValue = true)

            val startup: StartupViewModel = hiltViewModel()
            val state by startup.state.collectAsStateWithLifecycle()

            FinnypetTheme {
                CompositionLocalProvider(
                    LocalAnimationsEnabled provides animations,
                    LocalSoundEnabled provides sound,
                ) {
                    // Граф строится только когда известно, есть ли профиль:
                    // стартовый экран после сборки уже не поменять, а начать
                    // со знакомства при готовом профиле значит нарушить
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

                        Startup.NoProfile -> FinnyNavHost(startDestination = Onboarding)
                        Startup.HasProfile -> FinnyNavHost(startDestination = Main)
                    }
                }
            }
        }
    }
}
