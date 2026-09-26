package ru.finnypet.app.ui.components

import androidx.annotation.StringRes
import ru.finnypet.app.R
import ru.finnypet.app.domain.model.GrowthStage
import ru.finnypet.app.domain.model.PetStatKind
import ru.finnypet.app.domain.model.SpendCategory
import ru.finnypet.app.domain.model.TaskTopic

/**
 * Подписи доменных понятий — одни и те же на всех экранах (ТЗ 3.6 требует
 * единообразия): направление трат зовётся «Нужное» и на знакомстве, и в
 * плане, и в магазине, и в заданиях.
 */

val SpendCategory.label: Int
    @StringRes get() = when (this) {
        SpendCategory.MANDATORY -> R.string.category_mandatory
        SpendCategory.OPTIONAL -> R.string.category_optional
        SpendCategory.SAVINGS -> R.string.category_savings
    }

val PetStatKind.label: Int
    @StringRes get() = when (this) {
        PetStatKind.MOOD -> R.string.stat_mood
        PetStatKind.SATIETY -> R.string.stat_satiety
        PetStatKind.CARE -> R.string.stat_care
    }

/**
 * Иконка показателя рядом со словом — для глаз; TalkBack читает слово (раздел 8 плана).
 *
 * У сытости не миска: она совпала бы с иконкой «Нужного» у направлений трат
 * (`SpendCategory.icon`) — на главном экране обе иконки стоят рядом, и цвет
 * остался бы единственным отличием (ТЗ 3.6 это запрещает, см. `TypographyTest`).
 */
val PetStatKind.icon: String
    get() = when (this) {
        PetStatKind.SATIETY -> "🍎"
        PetStatKind.MOOD -> "😊"
        PetStatKind.CARE -> "✨"
    }

val TaskTopic.label: Int
    @StringRes get() = when (this) {
        TaskTopic.PLANNING -> R.string.topic_planning
        TaskTopic.SAVING -> R.string.topic_saving
        TaskTopic.PAYMENTS -> R.string.topic_payments
    }

val GrowthStage.label: Int
    @StringRes get() = when (this) {
        GrowthStage.CUB -> R.string.stage_cub
        GrowthStage.YOUNG -> R.string.stage_young
        GrowthStage.GROWN -> R.string.stage_grown
    }
