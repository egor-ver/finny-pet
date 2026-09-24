package ru.finnypet.app.ui.screens.adult

import ru.finnypet.app.domain.model.Explanation
import ru.finnypet.app.domain.model.PetStatKind

/**
 * Карточка «О чём поговорить сегодня»: одна из двух подсказок (раздел 8
 * плана). Голод — по порогу потребности, а не грусти: взрослый узнаёт о нём
 * тогда же, когда магазин пишет «нужно сейчас», а не на день позже.
 */
fun talkHint(needs: List<PetStatKind>): Explanation =
    if (PetStatKind.SATIETY in needs) Explanation("adult.talk.hungry") else Explanation("adult.talk.plan")
