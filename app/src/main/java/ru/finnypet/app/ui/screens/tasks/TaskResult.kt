package ru.finnypet.app.ui.screens.tasks

import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.LearningTask
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

/**
 * Кошелёк в шапке итога: награда уже записана в базу, но показывается,
 * только когда монеты долетели, — иначе число менялось бы раньше полёта.
 */
fun shownBalance(balance: Coins, reward: Coins, landed: Boolean): Coins =
    if (!landed && balance.covers(reward)) balance - reward else balance

/** Ошибка — повод разобраться, а не грустить (раздел 8 плана): сова спокойна. */
fun taskMood(correct: Boolean): PetMood = if (correct) PetMood.HAPPY else PetMood.CALM

/** Наибольшая награда за верный исход — «до +10» на вступлении и «+10» в списке. */
val LearningTask.maxReward: Coins
    get() = outcomes.filter { it.correct }.maxOf { it.reward }
