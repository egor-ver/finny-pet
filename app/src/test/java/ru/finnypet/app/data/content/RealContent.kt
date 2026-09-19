package ru.finnypet.app.data.content

import org.junit.Assert.assertTrue
import ru.finnypet.app.domain.content.ContentPack
import ru.finnypet.app.domain.content.PetImageFiles
import java.io.File

/**
 * Настоящий контент-пак из `src/main/assets` для тестов.
 *
 * Тесты читают файлы напрямую, а не через `AssetManager`: юнит-тесты живут
 * на JVM без Android. Папка объявлена входом тестовой задачи в
 * `build.gradle.kts`, поэтому правка контента перезапускает тесты.
 */
object RealContent {

    /** Сырые файлы; любой можно подменить, чтобы проверить разбор ошибки. */
    fun raw(
        balance: String? = null,
        pets: String? = null,
        shop: String? = null,
        goals: String? = null,
        tasks: String? = null,
    ) = RawContent(
        balance = balance ?: asset("balance.json"),
        pets = pets ?: asset("pets.json"),
        shop = shop ?: asset("shop.json"),
        goals = goals ?: asset("goals.json"),
        tasks = tasks ?: asset("tasks.json"),
        glossary = asset("glossary.json"),
        explanations = asset("explanations.json"),
    )

    fun asset(name: String): String {
        val file = File("src/main/assets/${ContentPack.FOLDER}/$name")
        assertTrue("Не найден файл контент-пака: ${file.absolutePath}", file.exists())
        return file.readText()
    }

    /** Папка с картинками питомца. */
    fun petsDir(): File = File("src/main/assets/${PetImageFiles.DIR}").also {
        assertTrue("Не найдена папка картинок: ${it.absolutePath}", it.isDirectory)
    }
}
