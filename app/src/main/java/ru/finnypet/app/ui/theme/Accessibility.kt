package ru.finnypet.app.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Настройки доступности, доступные любому компоненту без проброса через
 * все экраны.
 *
 * ТЗ 3.6 требует, чтобы звук и анимации можно было отключить, а критически
 * важная информация не передавалась только звуком или движением. Флаги
 * читаются из настроек в MainActivity и раздаются отсюда: компоненту не
 * нужно знать ни про репозиторий, ни про корутины.
 *
 * По умолчанию включено — отключение это осознанный выбор пользователя,
 * а не состояние по умолчанию.
 */
val LocalAnimationsEnabled = staticCompositionLocalOf { true }

val LocalSoundEnabled = staticCompositionLocalOf { true }
