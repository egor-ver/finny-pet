package ru.finnypet.app.domain.model

data class Goal(
    val id: GoalId,
    val titleKey: String,
    val price: Coins,
    /** Картинка — эмодзи из контента (AD-11). */
    val icon: String = "",
) {

    init {
        require(titleKey.isNotBlank()) { "Ключ названия цели не может быть пустым" }
        require(price > Coins.ZERO) { "Цель со стоимостью ноль не имеет смысла: ${id.value}" }
    }
}

data class GoalProgress(
    val goalId: GoalId,
    val saved: Coins = Coins.ZERO,
    val isActive: Boolean = false,
) {

    fun remaining(goal: Goal): Coins {
        checkSameGoal(goal)
        return saved.shortfallTo(goal.price)
    }

    fun isReached(goal: Goal): Boolean {
        checkSameGoal(goal)
        return saved.covers(goal.price)
    }

    private fun checkSameGoal(goal: Goal) {
        require(goal.id == goalId) {
            "Прогресс относится к другой цели: ${goalId.value} против ${goal.id.value}"
        }
    }
}
