package ru.finnypet.app.ui.screens.main

import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.Explanation
import ru.finnypet.app.domain.model.PeriodStatus
import ru.finnypet.app.domain.model.PetStatKind

/**
 * Главная кнопка по фазе дня (раздел 8 плана): спланировать, купить нужное,
 * уложить спать. Задание в кнопку не входит — о нём говорит сова, а открыть
 * его можно карточкой: кнопка остаётся одной и той же дорогой через день.
 */
sealed interface NextStep {

    data object Plan : NextStep

    data object Shop : NextStep

    data object Sleep : NextStep
}

/**
 * В магазин зовут потребности совы, а не недотраченный план: иначе кнопка
 * учила бы «потрать всё, что запланировал на нужное» (R3). И только если
 * хватает хотя бы на самое дешёвое ИЗ НУЖНОГО ИМЕННО СЕЙЧАС — иначе кнопка
 * звала бы в магазин по цене товара, который сове не поможет (Б6: сова
 * просит уход, а хватает только на воду, поднимающую сытость), либо вела бы
 * в тупик (ТЗ 3.4).
 */
fun nextStep(
    status: PeriodStatus,
    hasNeeds: Boolean,
    wallet: Coins,
    cheapestNeeded: Coins?,
): NextStep {
    if (status == PeriodStatus.PLANNING) return NextStep.Plan
    val canBuy = cheapestNeeded != null && wallet.covers(cheapestNeeded)
    return if (hasNeeds && canBuy) NextStep.Shop else NextStep.Sleep
}

/**
 * Что сова говорит в облачке: почему она такая и что делать дальше
 * (ТЗ 2.5.9, 2.5.10). Слова — в `explanations.json`, здесь только выбор.
 *
 * Утром грусть объясняется раньше всего: ребёнок должен понять, что вчерашний
 * голод — следствие решения, а не случайность. Задание при этом не подаётся
 * как обязательный первый шаг — кнопка всё равно ведёт в план (Б4): фраза
 * лишь напоминает, что оно есть и сколько за него дадут. Утренние фразы
 * называют выбор между тремя направлениями, а не готовый ответ (ТЗ 8.4).
 *
 * [needs] — потребности по порядку важности, еда первой; [cover] — цена
 * закрытия всех потребностей, `null` — в магазине их не закрыть целиком;
 * [reward] — сколько дадут за задание, `null` — сегодня уже не дадут.
 */
fun owlPhrase(
    step: NextStep,
    needs: List<PetStatKind>,
    sadAbout: PetStatKind?,
    cover: Coins?,
    wallet: Coins,
    reward: Coins?,
    income: Coins,
): Explanation {
    val first = needs.firstOrNull()
    val morning = mapOf("income" to income.amount.toString())
    return when (step) {
        NextStep.Plan -> when {
            sadAbout != null -> Explanation("owl.say.sad.${sadAbout.name}")
            reward != null -> Explanation("owl.say.task", morning + ("reward" to reward.amount.toString()))
            first != null -> Explanation("owl.say.morning.${first.name}", morning)
            else -> Explanation("owl.say.morning", morning)
        }
        else -> when {
            first == null -> Explanation("owl.say.done")
            step == NextStep.Sleep -> Explanation("owl.say.no_coins")
            cover != null && wallet.covers(cover) ->
                Explanation("owl.say.shop.${first.name}", mapOf("price" to cover.amount.toString()))
            else -> Explanation("owl.say.not_all.${first.name}")
        }
    }
}
