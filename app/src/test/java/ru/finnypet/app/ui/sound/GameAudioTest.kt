package ru.finnypet.app.ui.sound

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Решение «звучать ли» (пункт A1, AD-17): выключенный «Звуки» — тишина везде,
 * выключенная «Мелодия» — сразу тишина, свёрнутое приложение — мелодия на паузе.
 * Проигрыватель подменён записью вызовов: SoundPool и MediaPlayer в тесте не нужны.
 */
class GameAudioTest {

    private val calls = mutableListOf<String>()

    private val output = object : AudioOutput {
        override fun play(sound: Sound) {
            calls += "play $sound"
        }

        override fun resumeMusic() {
            calls += "resume"
        }

        override fun pauseMusic() {
            calls += "pause"
        }
    }

    private val audio = GameAudio(output)

    @Test
    fun `звуки включены — событие звучит`() {
        audio.setSoundEnabled(true)
        audio.play(Sound.CORRECT)

        assertEquals(listOf("play CORRECT"), calls)
    }

    @Test
    fun `звуки выключены — проигрыватель не вызывается`() {
        audio.setSoundEnabled(true)
        audio.setSoundEnabled(false)
        Sound.entries.forEach(audio::play)

        assertEquals(emptyList<String>(), calls)
    }

    /** Пока настройки не прочитаны — тишина: сохранённое «выкл» не должно прозвучать на миг. */
    @Test
    fun `до чтения настроек ничего не звучит`() {
        audio.setVisible(true)
        audio.play(Sound.COINS)

        assertEquals(emptyList<String>(), calls)
    }

    @Test
    fun `мелодия играет, пока приложение на экране`() {
        audio.setMusicEnabled(true)
        audio.setVisible(true)
        audio.setVisible(false)
        audio.setVisible(true)

        assertEquals(listOf("resume", "pause", "resume"), calls)
    }

    @Test
    fun `мелодия выключена — проигрыватель мелодии не вызывается`() {
        audio.setMusicEnabled(false)
        audio.setVisible(true)
        audio.setVisible(false)
        audio.setVisible(true)

        assertEquals(emptyList<String>(), calls)
    }

    @Test
    fun `выключение мелодии на экране — сразу пауза, включение — снова играет`() {
        audio.setVisible(true)
        audio.setMusicEnabled(true)
        audio.setMusicEnabled(false)
        audio.setMusicEnabled(true)

        assertEquals(listOf("resume", "pause", "resume"), calls)
    }

    /** Повтор той же настройки не перезапускает петлю с начала. */
    @Test
    fun `повторное включение не дёргает проигрыватель`() {
        audio.setVisible(true)
        audio.setMusicEnabled(true)
        audio.setMusicEnabled(true)
        audio.setVisible(true)

        assertEquals(listOf("resume"), calls)
    }

    /** Звуки и мелодия — независимые настройки. */
    @Test
    fun `без мелодии звуки звучат, без звуков мелодия играет`() {
        audio.setVisible(true)
        audio.setSoundEnabled(true)
        audio.setMusicEnabled(false)
        audio.play(Sound.STAR)
        audio.setSoundEnabled(false)
        audio.setMusicEnabled(true)
        audio.play(Sound.STAR)

        assertEquals(listOf("play STAR", "resume"), calls)
    }
}
