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
    ) : TaskStep {
        init {
            require(budget > Coins.ZERO) { "Распределять нечего: бюджет шага равен нулю" }
        }
    }

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
}

sealed interface OutcomeCondition {

    data class OptionChosen(val optionId: String) : OutcomeCondition

    data class SavedAtLeast(val amount: Coins) : OutcomeCondition

    data class SpentAtMost(val amount: Coins) : OutcomeCondition

    data object Otherwise : OutcomeCondition
}

data class TaskOutcome(
    val id: String,
    val condition: OutcomeCondition,
    val reward: Coins,
    val explanationKey: String,
    val effects: List<PetEffect> = emptyList(),
) {

    init {
        require(id.isNotBlank()) { "Идентификатор исхода обязателен" }
        require(explanationKey.isNotBlank()) { "Исход обязан нести объяснение" }
    }
}

data class LearningTask(
    val id: TaskId,
    val topic: TaskTopic,
    val introKey: String,
    val steps: List<TaskStep>,
    val outcomes: List<TaskOutcome>,
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
}
