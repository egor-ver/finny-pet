package ru.finnypet.app.data.content

import ru.finnypet.app.domain.model.GrowthStage
import ru.finnypet.app.domain.model.PetAppearance

/**
 * Имена картинок питомца в контент-паке.
 *
 * Сова рисуется сразу в аксессуаре — отдельный файл на каждый окрас и стадию:
 * `owl_cream_cub_scarf.png`. Накладка поверх тела была бы вчетверо дешевле
 * по картинкам, но инструмент напарника не умеет стереть сову, сохранив
 * положение аксессуара на холсте, — он перерисовывает кадр заново.
 *
 * Имя собирается из идентификаторов `pets.json`, поэтому новый окрас или
 * аксессуар появляется в игре добавлением файлов, без правок кода (ТЗ 2.5.14).
 */
object PetImageFiles {

    const val DIR = "content/v1/pets"

    /** `owl_cream_cub.png` без аксессуара, `owl_cream_cub_scarf.png` с ним. */
    fun name(
        bodyId: String,
        colorId: String,
        stage: GrowthStage,
        accessoryId: String? = null,
    ): String = buildString {
        append(bodyId).append('_').append(colorId).append('_').append(stage.name.lowercase())
        if (accessoryId != null) append('_').append(accessoryId)
        append(".png")
    }

    /**
     * Пути в порядке предпочтения: сначала сова в аксессуаре, потом та же
     * сова без него. Недорисованный аксессуар не должен превращать питомца
     * в заглушку — пока контент наполняется, приложение обязано работать.
     */
    fun candidates(appearance: PetAppearance, stage: GrowthStage): List<String> = buildList {
        appearance.accessoryId?.let { accessory ->
            add("$DIR/${name(appearance.bodyId, appearance.colorId, stage, accessory)}")
        }
        add("$DIR/${name(appearance.bodyId, appearance.colorId, stage)}")
    }
}
