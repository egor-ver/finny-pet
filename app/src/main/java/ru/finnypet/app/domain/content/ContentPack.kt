package ru.finnypet.app.domain.content

import ru.finnypet.app.domain.economy.GameBalance
import ru.finnypet.app.domain.model.Goal
import ru.finnypet.app.domain.model.LearningTask
import ru.finnypet.app.domain.model.ShopItem

/**
 * Учебный контент целиком, уже разобранный в доменные типы.
 *
 * Живёт в domain, а не в data: это предметные понятия игры, а не детали
 * хранения. ТЗ 3.2 требует отделить учебный контент от интерфейсного кода,
 * ТЗ 2.5.14 — добавлять задание без переработки логики; и то и другое
 * выполняется тем, что контент приходит снаружи готовым набором.
 */
data class ContentPack(
    val balance: GameBalance,
    val pets: PetOptions,
    val shop: List<ShopItem>,
    val goals: List<Goal>,
    val tasks: List<LearningTask>,
    val glossary: List<GlossaryTerm>,
    val texts: Map<String, String>,
) {

    companion object {
        /**
         * Папка контент-пака в ассетах. Одна на JSON и картинки: смена версии
         * контента — правка одной строки, а не двух в разных слоях.
         */
        const val FOLDER = "content/v1"
    }
}

/** Один выбираемый вариант контента: идентификатор и ключ названия. */
data class ContentOption(
    val id: String,
    val titleKey: String,
) {

    init {
        require(id.isNotBlank()) { "Идентификатор варианта обязателен" }
        require(titleKey.isNotBlank()) { "Ключ названия варианта обязателен: $id" }
    }
}

/**
 * Составные части внешности питомца. Ребёнок выбирает каждую отдельно,
 * поэтому списки не схлопываются в готовые комбинации.
 */
data class PetOptions(
    val bodies: List<ContentOption>,
    val colors: List<PetColor>,
    val accessories: List<ContentOption>,
) {

    /**
     * Сколько различимых внешностей получается. Вариант «без аксессуара»
     * приложение предлагает само, поэтому аксессуаров на один больше.
     */
    val combinationCount: Int get() = bodies.size * colors.size * (accessories.size + 1)
}

/** Термин справочного раздела (ТЗ 2.5.11). */
data class GlossaryTerm(
    val id: String,
    val titleKey: String,
    val bodyKey: String,
) {

    init {
        require(id.isNotBlank()) { "Идентификатор термина обязателен" }
        require(titleKey.isNotBlank()) { "Ключ названия термина обязателен: $id" }
        require(bodyKey.isNotBlank()) { "Ключ объяснения термина обязателен: $id" }
    }
}

/**
 * Окрас совы: вариант выбора и цвета, которыми она рисуется (AD-1). Цвета —
 * ARGB-числа, а не цвета интерфейса: домен не знает про Compose, а новый
 * окрас добавляется записью в pets.json без правки кода (ТЗ 2.5.14).
 */
data class PetColor(
    val option: ContentOption,
    val body: Long,
    val wing: Long,
    val face: Long,
    val ring: Long,
) {

    val id: String get() = option.id
}
