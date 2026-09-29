package ru.finnypet.app.ui.screens.main

import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.Explanation
import ru.finnypet.app.domain.model.PeriodStatus
import ru.finnypet.app.domain.model.PetStatKind

/**
 * Главная кнопка по фазе дня (раздел 8 плана, DESIGN_PLAN 3.1): выполнить
 * задание, спланировать, купить нужное, уложить спать.
 */
sealed interface NextStep {

    /**
     * Пока день не спланирован и за задание ещё дают монеты — выполнить его
     * первым (правка владельца №2, утверждено 27.09): план всё равно ждёт
     * следующим шагом, магазин в эту фазу не открывается.
     */
    data object Task : NextStep

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
 *
 * [taskReward] — награда за задание, если её сегодня ещё не забрали;
 * `null` вне зависимости от фазы значит «кнопка задания не нужна».
 * [taskUnsolved] — задание дня сегодня пробовали и не решили: кнопка
 * остаётся и без награды, чтобы ошибку можно было исправить сразу (ревью F7).
 */
fun nextStep(
    status: PeriodStatus,
    hasNeeds: Boolean,
    wallet: Coins,
    cheapestNeeded: Coins?,
    taskReward: Coins? = null,
    taskUnsolved: Boolean = false,
): NextStep {
    if (status == PeriodStatus.PLANNING) return if (taskReward != null || taskUnsolved) NextStep.Task else NextStep.Plan
    val canBuy = cheapestNeeded != null && wallet.covers(cheapestNeeded)
    return if (hasNeeds && canBuy) NextStep.Shop else NextStep.Sleep
}

/**
 * Что сова говорит в облачке: почему она такая и что делать дальше
 * (ТЗ 2.5.9, 2.5.10). Слова — в `explanations.json`, здесь только выбор.
 *
 * Утром порядок такой (DESIGN_PLAN 3.1: грусть событие не вытесняет,
 * объясняется раньше всего, иначе ребёнок не свяжет её со вчерашним
 * решением):
 * 1. событие и грусть в один день — общая фраза `<eventKey>.sad.<KIND>`;
 * 2. грусть без события — как раньше, `owl.say.sad.*`;
 * 3. событие без грусти — сама [eventKey];
 * 4. дальше задание и утро, как раньше — кнопка при этом не подаёт задание
 *    как обязательный первый шаг, план всё равно доступен (Б4). Утренние
 *    фразы называют выбор между тремя направлениями, а не готовый ответ (ТЗ 8.4).
 *
 * [needs] — потребности по порядку важности, еда первой; [cover] — цена
 * закрытия всех потребностей, `null` — в магазине их не закрыть целиком;
 * [reward] — сколько дадут за задание, `null` — сегодня уже не дадут;
 * [repeat] — все задания пройдены, награда — за повтор одного из них: сова
 * говорит «повторим», чтобы «6 из 6» и «+10» рядом не выглядели ошибкой;
 * [eventKey]/[eventArgs] — событие дня (L7), `null` — событий сегодня нет;
 * [needLeft] — сколько осталось в банке «Нужное» по плану, `null` — план не
 * подтверждён; [startWith] — с какой потребности начать по плану
 * ([ru.finnypet.app.domain.economy.PetStateEngine.startWith]), `null` — в
 * остаток «Нужного» не помещается ни один нужный товар.
 *
 * Если всё нужное дороже остатка банки, сова не называет полную цену целью
 * похода: кошелёк её и выдержит, но фраза звала бы тратить сверх плана
 * (ТЗ 2.5.5). Она предлагает начать с самой важной потребности, на которую
 * остатка хватает. Если не хватает ни на одну, в магазин она не зовёт вовсе
 * при любом кошельке: любая покупка уже была бы сверх плана.
 */
fun owlPhrase(
    step: NextStep,
    needs: List<PetStatKind>,
    sadAbout: PetStatKind?,
    cover: Coins?,
    wallet: Coins,
    reward: Coins?,
    income: Coins,
    repeat: Boolean = false,
    eventKey: String? = null,
    eventArgs: Map<String, String> = emptyMap(),
    needLeft: Coins? = null,
    startWith: PetStatKind?,
): Explanation {
    val first = needs.firstOrNull()
    val morning = mapOf("income" to income.amount.toString())
    return when (step) {
        NextStep.Task, NextStep.Plan -> when {
            eventKey != null && sadAbout != null -> Explanation("$eventKey.sad.${sadAbout.name}", eventArgs)
            sadAbout != null -> Explanation("owl.say.sad.${sadAbout.name}")
            eventKey != null -> Explanation(eventKey, eventArgs)
            step == NextStep.Task && reward != null ->
                Explanation(if (repeat) "owl.say.task_repeat" else "owl.say.task", morning + ("reward" to reward.amount.toString()))
            first != null -> Explanation("owl.say.morning.${first.name}", morning)
            else -> Explanation("owl.say.morning", morning)
        }
        else -> when {
            first == null -> Explanation("owl.say.done")
            step == NextStep.Sleep -> Explanation("owl.say.no_coins")
            // До веток про кошелёк: при малом кошельке иначе выпало бы not_all
            // («начнём с еды») — тот же зов тратить сверх плана.
            startWith == null -> Explanation("owl.say.plan_short")
            cover != null && wallet.covers(cover) && needLeft != null && !needLeft.covers(cover) ->
                Explanation("owl.say.plan_part.${startWith.name}")
            cover != null && wallet.covers(cover) ->
                Explanation("owl.say.shop.${first.name}", mapOf("price" to cover.amount.toString()))
            else -> Explanation("owl.say.not_all.${startWith.name}")
        }
    }
}

/**
 * Сова говорит «Пришло ещё N монет» — на это звучат монеты (ревью A1): доход
 * приходит без полёта монет, и без звука «монеты пришли» он был бы тихим.
 * Фразы события и грусти доход не называют — и звука нет, чтобы он не
 * звучал непонятно к чему.
 */
fun Explanation.namesIncome(): Boolean = "income" in args
