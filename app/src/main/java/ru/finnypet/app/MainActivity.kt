package ru.finnypet.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import ru.finnypet.app.domain.repository.SettingsRepository
import ru.finnypet.app.ui.navigation.FinnyNavHost
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

            FinnypetTheme {
                CompositionLocalProvider(
                    LocalAnimationsEnabled provides animations,
                    LocalSoundEnabled provides sound,
                ) {
                    FinnyNavHost()
                }
            }
        }
    }
}
