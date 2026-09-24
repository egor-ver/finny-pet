package ru.finnypet.app.ui.screens.tasks

import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.PetMood

/** Что сказать про монеты в итоге задания. */
enum class RewardLine {
    /** «+10 в кошелёк». */
    PAID,

    /** Ошибка: правило R8 названо прямо, чтобы «Попробовать ещё» не ждали как второй шанс на монеты. */
    FIRST_TRY_RULE,

    /**
     * Верно, но без монет — повтор или лимит дня. Правило первой попытки
     * здесь не подходит: при лимите попытка как раз первая.
     */
    NO_COINS,
}

fun rewardLine(correct: Boolean, paid: Coins): RewardLine = when {
    paid > Coins.ZERO -> RewardLine.PAID
    !correct -> RewardLine.FIRST_TRY_RULE
    else -> RewardLine.NO_COINS
}

/** Ошибка — повод разобраться, а не грустить (раздел 8 плана): сова спокойна. */
fun taskMood(correct: Boolean): PetMood = if (correct) PetMood.HAPPY else PetMood.CALM
