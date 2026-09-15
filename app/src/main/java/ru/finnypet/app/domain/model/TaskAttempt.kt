package ru.finnypet.app.domain.model

sealed interface StepAnswer {

    data class Chosen(val optionId: String) : StepAnswer {
        init {
            require(optionId.isNotBlank()) { "Выбранный вариант обязан быть назван" }
        }
    }

    data class Allocated(val plan: BudgetPlan) : StepAnswer

    data class Picked(val itemIds: List<ItemId>, val spent: Coins) : StepAnswer
}

data class TaskAttempt(val answers: List<StepAnswer>) {

    init {
        require(answers.isNotEmpty()) { "Попытка без ответов не имеет смысла" }
    }

    val chosenOptionIds: List<String>
        get() = answers.filterIsInstance<StepAnswer.Chosen>().map { it.optionId }

    val saved: Coins
        get() = answers.filterIsInstance<StepAnswer.Allocated>()
            .fold(Coins.ZERO) { total, answer -> total + answer.plan.savings }

    val spent: Coins
        get() = answers.fold(Coins.ZERO) { total, answer ->
            total + when (answer) {
                is StepAnswer.Chosen -> Coins.ZERO
                is StepAnswer.Allocated -> answer.plan.mandatory + answer.plan.optional
                is StepAnswer.Picked -> answer.spent
            }
        }
}
