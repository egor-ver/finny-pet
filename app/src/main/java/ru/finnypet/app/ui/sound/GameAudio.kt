package ru.finnypet.app.ui.sound

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Короткие звуки только на события (решение владельца 28.09): щелчков на
 * каждую кнопку нет — ребёнок слышит, что случилось важное.
 */
enum class Sound {
    /** Монеты легли — в банки плана, в копилку, в кошелёк за задание. */
    COINS,

    /** Покупка в магазине. */
    PURCHASE,

    /** «Верно!» в задании. */
    CORRECT,

    /** Неверный ответ — тихо и без резкости: ошибка — повод разобраться. */
    WRONG,

    /** Звезда или рост в итогах дня. */
    STAR,
}

/**
 * Проигрыватель устройства: SoundPool и MediaPlayer в приложении, запись
 * вызовов в тестах. Решать, звучать ли, — не его дело, а [GameAudio].
 */
interface AudioOutput {
    fun play(sound: Sound)

    fun resumeMusic()

    fun pauseMusic()
}

/**
 * Единственное место, где решается, звучать ли: настройки «Звуки» и
 * «Мелодия» и видно ли приложение (AD-17). Экраны зовут [play], не зная про
 * настройки, — выключенный «Звуки» глушит всё сразу, а не по экрану.
 *
 * До того как прочитаны настройки, всё выключено: иначе при сохранённой
 * «Мелодия: выкл» она успела бы зазвучать на миг при запуске.
 *
 * Все вызовы — с главного потока (события экранов, жизненный цикл
 * активности), поэтому состояние без синхронизации.
 */
@Singleton
class GameAudio @Inject constructor(private val output: AudioOutput) {

    private var soundEnabled = false
    private var musicEnabled = false
    private var visible = false
    private var musicPlaying = false

    fun play(sound: Sound) {
        if (soundEnabled) output.play(sound)
    }

    fun setSoundEnabled(enabled: Boolean) {
        soundEnabled = enabled
    }

    fun setMusicEnabled(enabled: Boolean) {
        musicEnabled = enabled
        syncMusic()
    }

    /** Приложение на экране: свёрнутое или с погасшим экраном — мелодия на паузе. */
    fun setVisible(visible: Boolean) {
        this.visible = visible
        syncMusic()
    }

    /** Проигрыватель зовётся только на смену: повторный старт начал бы петлю заново. */
    private fun syncMusic() {
        val should = musicEnabled && visible
        if (should == musicPlaying) return
        musicPlaying = should
        if (should) output.resumeMusic() else output.pauseMusic()
    }
}
