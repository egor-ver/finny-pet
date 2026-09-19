package ru.finnypet.app.data.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.finnypet.app.domain.content.PetOptions
import ru.finnypet.app.domain.model.GrowthStage
import ru.finnypet.app.domain.model.PetAppearance
import java.io.File

/**
 * Имена картинок питомца и сами картинки в контент-паке.
 *
 * Картинки рисует напарник, и приложение ищет их строго по имени: опечатка
 * или перепутанный окрас в имени не ломают сборку, а тихо оставляют игру
 * без совы. Поэтому имена сверяются с `pets.json` здесь, а не на защите.
 */
class PetImagesTest {

    private val parser = ContentParser()

    @Test
    fun `имя без аксессуара`() {
        assertEquals("owl_cream_cub.png", PetImageFiles.name("owl", "cream", GrowthStage.CUB))
    }

    @Test
    fun `имя с аксессуаром`() {
        assertEquals(
            "owl_black_grown_scarf.png",
            PetImageFiles.name("owl", "black", GrowthStage.GROWN, accessoryId = "scarf"),
        )
    }

    /** Сначала сова в аксессуаре, потом та же без него: недорисованный аксессуар не даёт заглушку. */
    @Test
    fun `кандидаты в порядке предпочтения`() {
        val withScarf = PetAppearance(bodyId = "owl", colorId = "white", accessoryId = "scarf")

        assertEquals(
            listOf("content/v1/pets/owl_white_young_scarf.png", "content/v1/pets/owl_white_young.png"),
            PetImageFiles.candidates(withScarf, GrowthStage.YOUNG),
        )
        assertEquals(
            listOf("content/v1/pets/owl_white_young.png"),
            PetImageFiles.candidates(withScarf.copy(accessoryId = null), GrowthStage.YOUNG),
        )
    }

    /**
     * Каждый файл в папке — это объявленные в `pets.json` тело, окрас, стадия
     * и, возможно, аксессуар. Лишний файл — либо опечатка в имени, либо
     * забытая запись в `pets.json`; и то и другое оставляет игру без картинки.
     */
    @Test
    fun `каждая картинка соответствует объявленной внешности`() {
        val expected = allNames(pets())

        val unknown = imageFiles().map { it.name }.filterNot { it in expected }

        assertEquals("файлы, которых нет в pets.json: $unknown", emptyList<String>(), unknown)
    }

    /** Без базовой совы на всех трёх стадиях окрас нельзя выбрать — ребёнок увидит заглушку. */
    @Test
    fun `у каждого окраса есть три базовые стадии`() {
        val present = imageFiles().map { it.name }.toSet()
        val options = pets()

        val missing = options.bodies.flatMap { body ->
            options.colors.flatMap { color ->
                GrowthStage.entries.map { stage -> PetImageFiles.name(body.id, color.id, stage) }
            }
        }.filterNot { it in present }

        assertEquals("нет базовых картинок: $missing", emptyList<String>(), missing)
    }

    @Test
    fun `в папке только png`() {
        val others = petsDir().listFiles().orEmpty().filterNot { it.extension == "png" }.map { it.name }

        assertEquals("в папке картинок лишние файлы: $others", emptyList<String>(), others)
    }

    /**
     * Все имена, которые приложение вообще может запросить: тела × окрасы ×
     * стадии × (аксессуары + без аксессуара).
     */
    private fun allNames(options: PetOptions): Set<String> = buildSet {
        options.bodies.forEach { body ->
            options.colors.forEach { color ->
                GrowthStage.entries.forEach { stage ->
                    add(PetImageFiles.name(body.id, color.id, stage))
                    options.accessories.forEach { accessory ->
                        add(PetImageFiles.name(body.id, color.id, stage, accessory.id))
                    }
                }
            }
        }
    }

    private fun pets(): PetOptions = parser.parse(
        RawContent(
            balance = asset("balance.json"),
            pets = asset("pets.json"),
            shop = asset("shop.json"),
            goals = asset("goals.json"),
            tasks = asset("tasks.json"),
            glossary = asset("glossary.json"),
            explanations = asset("explanations.json"),
        )
    ).pets

    private fun imageFiles(): List<File> = petsDir().listFiles().orEmpty().filter { it.extension == "png" }

    private fun petsDir(): File = File("src/main/assets/${PetImageFiles.DIR}").also {
        assertTrue("Не найдена папка картинок: ${it.absolutePath}", it.isDirectory)
    }

    private fun asset(name: String): String {
        val file = File("src/main/assets/content/v1/$name")
        assertTrue("Не найден файл контент-пака: ${file.absolutePath}", file.exists())
        return file.readText()
    }
}
