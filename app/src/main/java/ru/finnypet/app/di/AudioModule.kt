package ru.finnypet.app.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ru.finnypet.app.ui.sound.AndroidAudioOutput
import ru.finnypet.app.ui.sound.AudioOutput

/** Проигрыватель устройства за прослойкой: решение «звучать ли» проверяется тестом без Android (AD-17). */
@Module
@InstallIn(SingletonComponent::class)
abstract class AudioModule {

    @Binds
    abstract fun audioOutput(impl: AndroidAudioOutput): AudioOutput
}
