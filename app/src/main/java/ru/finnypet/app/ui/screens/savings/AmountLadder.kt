package ru.finnypet.app.ui.screens.savings

import ru.finnypet.app.domain.model.Coins

/**
 * Лесенка сумм для кнопок «больше» и «меньше»: шаг, два шага, три… и верхняя
 * граница, если она на шаг не делится. Ниже одного шага не спуститься —
 * ноль откладывать бессмысленно, а если граница меньше шага, она и есть
 * единственная ступенька.
 *
 * Пример при шаге 5 и границе 12: 5 → 10 → 12 и обратно.
 */
internal object AmountLadder {

    /** Пять монет: суммы в игре двузначные, по одной набирать ребёнку долго. */
    const val STEP = 5

    /** Первая ступенька. */
    fun first(max: Coins): Coins = Coins(minOf(STEP, max.amount))

    fun up(current: Coins, max: Coins): Coins {
        val next = (current.amount / STEP + 1) * STEP
        return Coins(minOf(next, max.amount))
    }

    fun down(current: Coins, max: Coins): Coins {
        val previous = ((current.amount - 1) / STEP) * STEP
        return Coins(maxOf(previous, first(max).amount))
    }

    fun canGoUp(current: Coins, max: Coins): Boolean = current < max

    fun canGoDown(current: Coins, max: Coins): Boolean = current > first(max)
}
