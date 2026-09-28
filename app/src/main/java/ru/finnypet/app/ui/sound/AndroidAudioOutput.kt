package ru.finnypet.app.ui.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
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
 *
 * Мелодия просит у системы право на звук (audio focus): без него она играла
 * бы поверх чужой музыки, видео и звонка. Короткие звуки права не просят:
 * ради звона монет на миг глушить чужую музыку хуже, чем прозвучать поверх.
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

    private val audioManager: AudioManager = context.getSystemService(AudioManager::class.java)

    private val musicAttributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    // Слушатель зовётся на главном потоке — там же, где resume/pause, поэтому
    // состояние без синхронизации (как и в GameAudio).
    private val focusRequest: AudioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(musicAttributes)
        .setOnAudioFocusChangeListener { onFocusChange(focusReaction(it)) }
        .build()

    /** Создаётся при первом показе, а не при запуске: пока мелодия выключена, файл не нужен. */
    private var music: MediaPlayer? = null

    /** GameAudio велел играть; право на звук может быть на время у другого. */
    private var wanted = false

    override fun play(sound: Sound) {
        val id = loaded[sound] ?: return
        pool.play(id, EFFECT_VOLUME, EFFECT_VOLUME, 1, 0, 1f)
    }

    override fun resumeMusic() {
        wanted = true
        // Не дали — идёт звонок: промолчим, мелодия вернётся при следующем показе.
        if (audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) startMusic()
    }

    override fun pauseMusic() {
        wanted = false
        music?.takeIf { it.isPlaying }?.pause()
        audioManager.abandonAudioFocusRequest(focusRequest)
    }

    private fun startMusic() {
        val player = music ?: MediaPlayer.create(context, R.raw.music_loop, musicAttributes, audioManager.generateAudioSessionId())
            ?.apply {
                isLooping = true
                setVolume(MUSIC_VOLUME, MUSIC_VOLUME)
            }?.also { music = it } ?: return
        player.start()
    }

    private fun onFocusChange(reaction: FocusReaction) {
        when (reaction) {
            FocusReaction.RESUME -> if (wanted) startMusic()
            FocusReaction.PAUSE -> music?.takeIf { it.isPlaying }?.pause()
            // Право отдаём: вернём его, когда ребёнок снова откроет игру
            // (onResume) или заново включит мелодию.
            FocusReaction.YIELD -> pauseMusic()
            FocusReaction.NONE -> Unit
        }
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

/** Что делать мелодии, когда другое приложение берёт звук или возвращает его. */
internal enum class FocusReaction { RESUME, PAUSE, YIELD, NONE }

/**
 * Чужая музыка или видео — надолго: мелодия уступает и право отдаёт.
 * Звонок или подсказка навигатора — на время: пауза, по возврату права
 * мелодия продолжается. Просьбу «потише» система выполняет сама.
 */
internal fun focusReaction(focusChange: Int): FocusReaction = when (focusChange) {
    AudioManager.AUDIOFOCUS_GAIN -> FocusReaction.RESUME
    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> FocusReaction.PAUSE
    AudioManager.AUDIOFOCUS_LOSS -> FocusReaction.YIELD
    else -> FocusReaction.NONE
}
