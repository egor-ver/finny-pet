package ru.finnypet.app.ui.components

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import ru.finnypet.app.R
import ru.finnypet.app.domain.model.GrowthStage
import ru.finnypet.app.domain.model.PetStatKind
import ru.finnypet.app.domain.model.SpendCategory
import ru.finnypet.app.domain.model.TaskTopic
import ru.finnypet.app.ui.components.icons.FinnyIcons
import ru.finnypet.app.ui.theme.DirectionColors
import ru.finnypet.app.ui.theme.FinnyTheme

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
 * Чип потребности на главном — «нужна» / «нужен» по роду показателя
 * (DESIGN_PLAN 3.1: «Еда нужно» не согласовано). `MOOD` сюда не попадает —
 * [ru.finnypet.app.domain.economy.PetStateEngine.needsOf] отдаёт только еду и уход.
 */
val PetStatKind.needLabel: Int
    @StringRes get() = when (this) {
        PetStatKind.SATIETY -> R.string.main_stat_need_satiety
        PetStatKind.CARE -> R.string.main_stat_need_care
        PetStatKind.MOOD -> R.string.main_stat_need_care
    }

/**
 * Иконка показателя рядом со словом — для глаз; TalkBack читает слово (раздел 8 плана).
 *
 * У сытости не миска: она совпала бы с иконкой «Нужного» у направлений трат
 * (`SpendCategory.icon`) — на главном экране обе иконки стоят рядом, и цвет
 * остался бы единственным отличием (ТЗ 3.6 это запрещает, см. `TypographyTest`).
 */
val PetStatKind.icon: ImageVector
    get() = when (this) {
        PetStatKind.SATIETY -> FinnyIcons.Apple
        PetStatKind.MOOD -> FinnyIcons.Heart
        PetStatKind.CARE -> FinnyIcons.Feather
    }

/** Направление, которое пополняет показатель (DESIGN_PLAN 2.1): нужное чинит еду и уход, желаемое — радость. */
val PetStatKind.direction: SpendCategory
    get() = when (this) {
        PetStatKind.SATIETY -> SpendCategory.MANDATORY
        PetStatKind.CARE -> SpendCategory.MANDATORY
        PetStatKind.MOOD -> SpendCategory.OPTIONAL
    }

val TaskTopic.label: Int
    @StringRes get() = when (this) {
        TaskTopic.PLANNING -> R.string.topic_planning
        TaskTopic.SAVING -> R.string.topic_saving
        TaskTopic.PAYMENTS -> R.string.topic_payments
    }

/** Иконка темы задания — та же, что у соответствующего направления (DESIGN_PLAN 2.3). */
val TaskTopic.icon: ImageVector
    get() = when (this) {
        TaskTopic.PLANNING -> FinnyIcons.Target
        TaskTopic.SAVING -> FinnyIcons.Piggy
        TaskTopic.PAYMENTS -> FinnyIcons.Bag
    }

/**
 * Три тона темы задания (DESIGN_PLAN 2.1): «Накопления» — тона копилки, у
 * «Планирования» и «Покупок» свои. Заливка — иконке на белом, текст —
 * иконке на светлой тарелке: заливка «Планирования» к своему контейнеру
 * даёт меньше 3:1.
 */
val TaskTopic.colors: DirectionColors
    @Composable get() = when (this) {
        TaskTopic.PLANNING -> FinnyTheme.palette.topicPlan
        TaskTopic.SAVING -> FinnyTheme.palette.save
        TaskTopic.PAYMENTS -> FinnyTheme.palette.topicShop
    }

val GrowthStage.label: Int
    @StringRes get() = when (this) {
        GrowthStage.CUB -> R.string.stage_cub
        GrowthStage.YOUNG -> R.string.stage_young
        GrowthStage.GROWN -> R.string.stage_grown
    }
