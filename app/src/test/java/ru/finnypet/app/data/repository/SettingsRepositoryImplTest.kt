package ru.finnypet.app.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Три настройки окна на главном (пункт A1): по умолчанию включены, меняются
 * независимо и переживают перезапуск — новое хранилище над тем же файлом.
 */
class SettingsRepositoryImplTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val scopes = mutableListOf<CoroutineScope>()

    @After
    fun tearDown() {
        scopes.forEach { it.cancel() }
    }

    /**
     * Своя область на каждое «включение приложения»: два живых DataStore над
     * одним файлом — исключение, поэтому прежний закрывается перед новым.
     */
    private fun open(file: File): Pair<SettingsRepositoryImpl, CoroutineScope> {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        scopes += scope
        return SettingsRepositoryImpl(PreferenceDataStoreFactory.create(scope = scope) { file }) to scope
    }

    @Test
    fun `звуки, мелодия и движение включены по умолчанию`() = runBlocking {
        val (settings, _) = open(File(folder.root, "defaults.preferences_pb"))

        assertEquals(true, settings.observeSoundEnabled().first())
        assertEquals(true, settings.observeMusicEnabled().first())
        assertEquals(true, settings.observeAnimationsEnabled().first())
    }

    @Test
    fun `каждая настройка меняется отдельно`() = runBlocking {
        val (settings, _) = open(File(folder.root, "separate.preferences_pb"))

        settings.setMusicEnabled(false)

        assertEquals(true, settings.observeSoundEnabled().first())
        assertEquals(false, settings.observeMusicEnabled().first())
        assertEquals(true, settings.observeAnimationsEnabled().first())
    }

    @Test
    fun `выключенные настройки переживают перезапуск`() = runBlocking {
        val file = File(folder.root, "restart.preferences_pb")
        val (before, scope) = open(file)
        before.setSoundEnabled(false)
        before.setMusicEnabled(false)
        before.setAnimationsEnabled(false)
        scope.cancel()
        scope.coroutineContext[Job]?.join()

        val (after, _) = open(file)

        assertEquals(false, after.observeSoundEnabled().first())
        assertEquals(false, after.observeMusicEnabled().first())
        assertEquals(false, after.observeAnimationsEnabled().first())
    }
}
