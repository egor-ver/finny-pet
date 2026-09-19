package ru.finnypet.app.data.content

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.finnypet.app.domain.content.PetImageFiles
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

    /**
     * У каждого аксессуара картинки на всех окрасах и стадиях. Приложение
     * прячет пропуск, показывая сову без аксессуара, поэтому ловить его
     * должен тест. Известные дыры перечислены явно: `main` остаётся зелёным,
     * пока напарник дорисовывает, а любая новая дыра — красная.
     */
    @Test
    fun `у каждого аксессуара есть картинки на всех окрасах и стадиях`() {
        val present = imageFiles().map { it.name }.toSet()
        val options = pets()

        val missing = options.accessories.flatMap { accessory ->
            options.bodies.flatMap { body ->
                options.colors.flatMap { color ->
                    GrowthStage.entries.map { stage -> PetImageFiles.name(body.id, color.id, stage, accessory.id) }
                }
            }
        }.filterNot { it in present }

        assertEquals(
            "нет картинок аксессуаров сверх известных дыр: ${missing - KNOWN_GAPS}",
            emptyList<String>(),
            missing - KNOWN_GAPS,
        )
        assertEquals(
            "дыра закрыта — убери из KNOWN_GAPS: ${KNOWN_GAPS - missing.toSet()}",
            emptySet<String>(),
            KNOWN_GAPS - missing.toSet(),
        )
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

    private fun pets(): PetOptions = parser.parse(RealContent.raw()).pets

    private fun imageFiles(): List<File> = petsDir().listFiles().orEmpty().filter { it.extension == "png" }

    private fun petsDir(): File = RealContent.petsDir()

    private companion object {
        /**
         * Картинки аксессуаров, которых пока нет и это известно. Пусто —
         * значит все аксессуары из `pets.json` нарисованы целиком. Добавлять
         * сюда только вместе с заданием напарнику, убирать — как только
         * он закрыл дыру: иначе тест напомнит сам.
         */
        val KNOWN_GAPS = emptySet<String>()
    }
}
