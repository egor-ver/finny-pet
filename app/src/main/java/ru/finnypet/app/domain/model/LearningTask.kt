package ru.finnypet.app.domain.model

enum class TaskTopic {
    PLANNING,
    SAVING,
    PAYMENTS,
}

data class TaskOption(
    val id: String,
    val labelKey: String,
) {

    init {
        require(id.isNotBlank()) { "Идентификатор варианта обязателен" }
        require(labelKey.isNotBlank()) { "Ключ текста варианта обязателен" }
    }
}

/**
 * Банка на шаге «раздели монеты»: направление расхода и подпись из
 * контент-пака. Порядок банок задаёт контент — в задании про подарок
 * накопления идут первыми, чтобы ребёнок начинал с них, а не с трат.
 */
data class Jar(
    val category: SpendCategory,
    val labelKey: String,
) {

    init {
        require(labelKey.isNotBlank()) { "Ключ подписи банки обязателен" }
    }
}

/**
 * Товар на прилавке задания.
 *
 * Живёт в самом задании, а не в магазине: тетради из школьного набора и сок
 * из киоска покупаются на уроке, а не питомцу, и в shop.json им не место.
 */
data class ShelfItem(
    val id: String,
    val titleKey: String,
    val price: Coins,
    val category: SpendCategory,
) {

    init {
        require(id.isNotBlank()) { "Идентификатор товара на прилавке обязателен" }
        require(titleKey.isNotBlank()) { "Ключ названия товара обязателен" }
        require(price > Coins.ZERO) { "Товар на прилавке не может быть бесплатным: $id" }
    }
}

sealed interface TaskStep {

    val promptKey: String

    data class Choice(
        override val promptKey: String,
        val options: List<TaskOption>,
    ) : TaskStep {
        init {
            require(options.size >= 2) { "У выбора должно быть не менее двух вариантов" }
        }
    }

    data class Distribute(
        override val promptKey: String,
        val budget: Coins,
        /** Пусто — редактор возьмёт свои названия и обычный порядок направлений. */
        val jars: List<Jar> = emptyList(),
    ) : TaskStep {
        init {
            require(budget > Coins.ZERO) { "Распределять нечего: бюджет шага равен нулю" }
            require(jars.map { it.category }.toSet().size == jars.size) {
                "Две банки на одно направление: ребёнок увидит одну сумму дважды"
            }
        }
    }

    /** Корзина из товаров магазина: цены и направления берутся из shop.json. */
    data class PickItems(
        override val promptKey: String,
        val itemIds: List<ItemId>,
        val budget: Coins,
    ) : TaskStep {
        init {
            require(itemIds.isNotEmpty()) { "Нечего выбирать: список товаров пуст" }
            require(budget > Coins.ZERO) { "Покупать не на что: бюджет шага равен нулю" }
        }
    }

    /** Корзина из товаров самого задания: прилавок описан прямо в нём. */
    data class Shelf(
        override val promptKey: String,
        val items: List<ShelfItem>,
        val budget: Coins,
    ) : TaskStep {
        init {
            require(items.isNotEmpty()) { "Прилавок без товаров ничему не учит" }
            require(budget > Coins.ZERO) { "Покупать не на что: бюджет шага равен нулю" }
            require(items.map { it.id }.toSet().size == items.size) {
                "Товары на прилавке повторяют идентификатор: ${items.map { it.id }.joinToString()}"
            }
        }
    }
}

sealed interface OutcomeCondition {

    data class OptionChosen(val optionId: String) : OutcomeCondition

    /**
     * Любой из перечисленных вариантов.
     *
     * Отложить на цель и переждать день — разные решения, но урок один:
     * с покупкой можно не спешить. Один исход на оба избавляет продакта от
     * двух одинаковых объяснений.
     */
    data class AnyOptionChosen(val optionIds: List<String>) : OutcomeCondition {
        init {
            require(optionIds.isNotEmpty()) { "Условие по выбору не называет ни одного варианта" }
            require(optionIds.toSet().size == optionIds.size) {
                "Вариант назван дважды: ${optionIds.joinToString()}"
            }
        }
    }

    data class SavedAtLeast(val amount: Coins) : OutcomeCondition

    data class SpentAtMost(val amount: Coins) : OutcomeCondition

    /**
     * В каждой названной банке не меньше задуманного; `null` — про эту банку
     * условия нет. Проверяет именно раскладку, а не общую сумму: «отложил 15»
     * и «отложил 5, но и на обязательное хватило» — разные уроки.
     */
    data class JarsAtLeast(
        val mandatory: Coins? = null,
        val optional: Coins? = null,
        val savings: Coins? = null,
    ) : OutcomeCondition {
        init {
            require(mandatory != null || optional != null || savings != null) {
                "Условие по банкам не названо ни для одной банки"
            }
        }
    }

    /** В корзине есть всё перечисленное — проверка выбора, а не суммы. */
    data class BasketContains(val itemIds: List<String>) : OutcomeCondition {
        init {
            require(itemIds.isNotEmpty()) { "Условие по корзине не называет ни одного товара" }
        }
    }

    data object Otherwise : OutcomeCondition
}

/**
 * [correct] — верный ли это ответ. Монеты и отметка «пройдено» — только за
 * верный (R8): иначе ошибиться в задании было бы нельзя, любой ответ платил.
 */
data class TaskOutcome(
    val id: String,
    val condition: OutcomeCondition,
    val reward: Coins,
    val explanationKey: String,
    val effects: List<PetEffect> = emptyList(),
    val correct: Boolean = false,
) {

    init {
        require(id.isNotBlank()) { "Идентификатор исхода обязателен" }
        require(explanationKey.isNotBlank()) { "Исход обязан нести объяснение" }
    }
}

/**
 * [showWhenSadAbout] — задание-разбор ошибки (AD-7): показывается само, пока
 * сова грустит из-за этого показателя, и не входит в список заданий.
 */
data class LearningTask(
    val id: TaskId,
    val topic: TaskTopic,
    val introKey: String,
    val steps: List<TaskStep>,
    val outcomes: List<TaskOutcome>,
    val showWhenSadAbout: PetStatKind? = null,
) {

    init {
        require(introKey.isNotBlank()) { "Ключ вступления обязателен" }
        require(steps.isNotEmpty()) { "Задание без шагов не имеет смысла: ${id.value}" }
        require(outcomes.size >= 2) { "У задания должно быть не менее двух исходов: ${id.value}" }
        require(outcomes.count { it.condition is OutcomeCondition.Otherwise } == 1) {
            "У задания обязан быть ровно один исход по умолчанию: ${id.value}"
        }
        require(outcomes.map { it.id }.toSet().size == outcomes.size) {
            "Идентификаторы исходов повторяются: ${id.value}"
        }
    }

    val fallback: TaskOutcome get() = outcomes.first { it.condition is OutcomeCondition.Otherwise }

    val isReview: Boolean get() = showWhenSadAbout != null
}
