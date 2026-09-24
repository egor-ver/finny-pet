package ru.finnypet.app.domain.economy

import ru.finnypet.app.domain.model.Change
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.Explanation
import ru.finnypet.app.domain.model.GameResult
import ru.finnypet.app.domain.model.LearningTask
import ru.finnypet.app.domain.model.OutcomeCondition
import ru.finnypet.app.domain.model.PetEffect
import ru.finnypet.app.domain.model.StepAnswer
import ru.finnypet.app.domain.model.TaskAttempt
import ru.finnypet.app.domain.model.TaskOutcome
import ru.finnypet.app.domain.model.TaskStep
import ru.finnypet.app.domain.model.Transaction
import ru.finnypet.app.domain.model.TransactionType

data class TaskResult(
    val outcome: TaskOutcome,
    val newBalance: Coins,
    val transaction: Transaction?,
    val effects: List<PetEffect>,
)

class TaskEngine(private val clock: GameClock) {

    /**
     * Разбирает попытку: исход, награду, влияние на питомца и объяснение.
     *
     * [rewardable] — можно ли за это прохождение платить: лимит наград в день
     * и первую попытку дня считает вызывающий ([ru.finnypet.app.domain.usecase.TaskSchedule]).
     * Когда платить нельзя или ответ неверный, исход и объяснение те же,
     * награда — ноль, операции нет: ребёнок учится, а экономика не раздувается.
     */
    fun evaluate(
        task: LearningTask,
        attempt: TaskAttempt,
        currentBalance: Coins,
        periodId: Long,
        rewardable: Boolean = true,
    ): GameResult<TaskResult> {
        checkAnswersMatchSteps(task, attempt)

        val outcome = outcomeFor(task, attempt)
        val reward = if (rewardable && outcome.correct) outcome.reward else Coins.ZERO
        val newBalance = currentBalance + reward
        val rewarded = reward > Coins.ZERO

        return GameResult(
            value = TaskResult(
                outcome = outcome,
                newBalance = newBalance,
                transaction = if (rewarded) rewardTransaction(outcome, periodId) else null,
                effects = outcome.effects,
            ),
            explanation = Explanation(
                key = outcome.explanationKey,
                args = mapOf(
                    "reward" to reward.amount.toString(),
                    "balance" to newBalance.amount.toString(),
                ),
            ),
            changes = if (rewarded) {
                listOf(Change.Balance(from = currentBalance, to = newBalance))
            } else {
                emptyList()
            },
        )
    }

    /**
     * Исход по умолчанию проверяется последним независимо от его места в списке:
     * порядок записей в контент-паке не должен влиять на логику.
     */
    fun outcomeFor(task: LearningTask, attempt: TaskAttempt): TaskOutcome = task.outcomes
        .firstOrNull { it.condition !is OutcomeCondition.Otherwise && matches(it.condition, attempt) }
        ?: task.fallback

    private fun matches(condition: OutcomeCondition, attempt: TaskAttempt): Boolean = when (condition) {
        is OutcomeCondition.OptionChosen -> condition.optionId in attempt.chosenOptionIds
        is OutcomeCondition.AnyOptionChosen -> condition.optionIds.any { it in attempt.chosenOptionIds }
        is OutcomeCondition.SavedAtLeast -> attempt.saved >= condition.amount
        is OutcomeCondition.SpentAtMost -> attempt.spent <= condition.amount

        is OutcomeCondition.JarsAtLeast -> {
            // Неназванная банка условию не мешает: «отложи хотя бы 15» ничего
            // не говорит о том, сколько ушло на желания.
            val plan = attempt.allocated
            (condition.mandatory?.let { plan.mandatory >= it } ?: true) &&
                (condition.optional?.let { plan.optional >= it } ?: true) &&
                (condition.savings?.let { plan.savings >= it } ?: true)
        }

        is OutcomeCondition.BasketContains -> attempt.pickedItemIds.containsAll(condition.itemIds)

        OutcomeCondition.Otherwise -> true
    }

    private fun checkAnswersMatchSteps(task: LearningTask, attempt: TaskAttempt) {
        require(attempt.answers.size == task.steps.size) {
            "Ответов ${attempt.answers.size}, а шагов ${task.steps.size}: задание ${task.id.value}"
        }
        task.steps.zip(attempt.answers).forEachIndexed { index, (step, answer) ->
            require(answerFits(step, answer)) {
                "Шаг ${index + 1} задания ${task.id.value} ожидает другой вид ответа"
            }
        }
    }

    private fun answerFits(step: TaskStep, answer: StepAnswer): Boolean = when (step) {
        is TaskStep.Choice -> answer is StepAnswer.Chosen &&
            step.options.any { it.id == answer.optionId }

        is TaskStep.Distribute -> answer is StepAnswer.Allocated &&
            answer.plan.total <= step.budget

        is TaskStep.PickItems -> answer is StepAnswer.Picked &&
            answer.spent <= step.budget &&
            step.itemIds.map { it.value }.containsAll(answer.itemIds)

        is TaskStep.Shelf -> answer is StepAnswer.Picked &&
            answer.spent <= step.budget &&
            step.items.map { it.id }.containsAll(answer.itemIds)
    }

    private fun rewardTransaction(outcome: TaskOutcome, periodId: Long) = Transaction(
        id = UNSAVED,
        periodId = periodId,
        type = TransactionType.INCOME_TASK,
        amount = outcome.reward,
        reasonKey = outcome.explanationKey,
        createdAt = clock.now(),
    )

    private companion object {
        const val UNSAVED = 0L
    }
}
