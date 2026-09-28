package ru.finnypet.app.ui.sound

import android.media.AudioManager
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Мелодия уступает другим приложениям (ревью A1): чужой музыке и видео —
 * насовсем, звонку и подсказке навигатора — на время.
 */
class FocusReactionTest {

    @Test
    fun `чужая музыка или видео — мелодия уступает`() {
        assertEquals(FocusReaction.YIELD, focusReaction(AudioManager.AUDIOFOCUS_LOSS))
    }

    @Test
    fun `звонок — пауза, после него мелодия продолжается`() {
        assertEquals(FocusReaction.PAUSE, focusReaction(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT))
        assertEquals(FocusReaction.RESUME, focusReaction(AudioManager.AUDIOFOCUS_GAIN))
    }

    /** «Потише» система выполняет сама — мелодия не останавливается. */
    @Test
    fun `просьба потише — мелодия играет дальше`() {
        assertEquals(FocusReaction.NONE, focusReaction(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK))
    }
}
