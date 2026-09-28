package ru.finnypet.app.ui.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import dagger.hilt.android.qualifiers.ApplicationContext
import ru.finnypet.app.R
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Звуки — через SoundPool: он держит короткие файлы распакованными в памяти и
 * играет их без задержки. Мелодия — через MediaPlayer: длинный файл по кругу
 * SoundPool не потянет. Оба стандартные, новых библиотек не нужно (AD-17).
 *
 * Файлы в `res/raw` — WAV: aapt не сжимает их, как SoundPool и требует.
 */
@Singleton
class AndroidAudioOutput @Inject constructor(
    @ApplicationContext private val context: Context,
) : AudioOutput {

    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(MAX_STREAMS)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    // Загрузка идёт в фоне SoundPool; звук, запрошенный до её конца,
    // просто пропускается — событие видно на экране и так.
    private val loaded: Map<Sound, Int> = Sound.entries.associateWith { pool.load(context, it.raw, 1) }

    /** Создаётся при первом показе, а не при запуске: пока мелодия выключена, файл не нужен. */
    private var music: MediaPlayer? = null

    override fun play(sound: Sound) {
        val id = loaded[sound] ?: return
        pool.play(id, EFFECT_VOLUME, EFFECT_VOLUME, 1, 0, 1f)
    }

    override fun resumeMusic() {
        val player = music ?: MediaPlayer.create(context, R.raw.music_loop)?.apply {
            isLooping = true
            setVolume(MUSIC_VOLUME, MUSIC_VOLUME)
        }?.also { music = it } ?: return
        player.start()
    }

    override fun pauseMusic() {
        music?.takeIf { it.isPlaying }?.pause()
    }

    private val Sound.raw: Int
        get() = when (this) {
            Sound.COINS -> R.raw.sound_coins
            Sound.PURCHASE -> R.raw.sound_purchase
            Sound.CORRECT -> R.raw.sound_correct
            Sound.WRONG -> R.raw.sound_wrong
            Sound.STAR -> R.raw.sound_star
        }

    private companion object {
        /** «Верно!» и монеты за задание звучат вместе — нужны два потока, третий про запас. */
        const val MAX_STREAMS = 3

        const val EFFECT_VOLUME = 0.8f

        /** Мелодия заметно тише звуков: фон, а не то, что надо слушать. */
        const val MUSIC_VOLUME = 0.25f
    }
}
