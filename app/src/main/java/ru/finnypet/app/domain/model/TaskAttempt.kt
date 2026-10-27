package ru.finnypet.app.domain.model

sealed interface StepAnswer {

    data class Chosen(val optionId: String) : StepAnswer {
        init {
            require(optionId.isNotBlank()) { "Выбранный вариант обязан быть назван" }
        }
    }

    data class Allocated(val plan: BudgetPlan) : StepAnswer

    /**
     * Корзина.
     *
     * Идентификаторы простые строки, а не [ItemId]: на прилавке задания лежат
     * свои товары — тетради, сок, — которых в магазине нет и быть не должно.
     */
    data class Picked(val itemIds: List<String>, val spent: Coins) : StepAnswer
}

data class TaskAttempt(val answers: List<StepAnswer>) {

    init {
        require(answers.isNotEmpty()) { "Попытка без ответов не имеет смысла" }
    }

    val chosenOptionIds: List<String>
        get() = answers.filterIsInstance<StepAnswer.Chosen>().map { it.optionId }

    /** Что лежит в корзинах: условия по товарам смотрят сюда. */
    val pickedItemIds: List<String>
        get() = answers.filterIsInstance<StepAnswer.Picked>().flatMap { it.itemIds }

    /**
     * Все раскладки шага сложены по банкам. Условия по банкам смотрят сюда:
     * им важно, сколько попало в каждую, а не общая сумма.
     */
    val allocated: BudgetPlan
        get() = answers.filterIsInstance<StepAnswer.Allocated>()
            .fold(BudgetPlan.EMPTY) { total, answer ->
                BudgetPlan(
                    mandatory = total.mandatory + answer.plan.mandatory,
                    optional = total.optional + answer.plan.optional,
                    savings = total.savings + answer.plan.savings,
                )
            }

    val saved: Coins get() = allocated.savings

    val spent: Coins
        get() = answers.fold(Coins.ZERO) { total, answer ->
            total + when (answer) {
                is StepAnswer.Chosen -> Coins.ZERO
                is StepAnswer.Allocated -> answer.plan.mandatory + answer.plan.optional
                is StepAnswer.Picked -> answer.spent
            }
        }
}
