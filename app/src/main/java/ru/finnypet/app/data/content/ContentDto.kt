package ru.finnypet.app.data.content

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

/**
 * Зеркало файлов контент-пака. Здесь нет ни одной проверки: DTO описывают
 * форму JSON, а смысл проверяют доменные типы в ContentParser.
 *
 * Имена полей совпадают с ключами в файлах — их видит и правит продакт,
 * поэтому переименование DTO ломает контент и делается только вместе с ним.
 */

@Serializable
data class BalanceDto(
    val startingBalance: Int,
    val periodIncome: Int,
    val taskReward: Int,
    val initialStat: Int,
    val nightDropSatiety: Int,
    val nightDropCare: Int,
    val nightDropMood: Int,
    val statFloor: Int,
    val needThreshold: Int,
    val sadThreshold: Int,
    val needSlack: Int,
    val growthForMandatoryCovered: Int,
    val growthForPlanFollowed: Int,
    val growthForSavingsKept: Int,
    val growthThresholds: List<Int>,
    val carryOverUnspent: Boolean,
    /** Появилось позже остальных: старый balance.json без него читается как «одно в день». */
    val rewardedTasksPerPeriod: Int = 1,
    /** Бонус родителя (ТЗ 2.5.12). Без него пак читается со значением по умолчанию. */
    val parentBonus: Int = 10,
)

@Serializable
data class EventsDto(val events: List<EventDto>)

@Serializable
data class EventDto(
    val id: String,
    val day: Int,
    val type: String,
    val messageKey: String,
    val careDrop: Int? = null,
    val amount: Int? = null,
)

@Serializable
data class OptionDto(
    val id: String,
    val titleKey: String,
)

/** Окрас: цвета тела, крыльев, лица и обводки в виде `#RRGGBB`. */
@Serializable
data class ColorDto(
    val id: String,
    val titleKey: String,
    val body: String,
    val wing: String,
    val face: String,
    val ring: String,
)

@Serializable
data class PetsDto(
    val bodies: List<OptionDto>,
    val colors: List<ColorDto>,
    val accessories: List<OptionDto> = emptyList(),
)

@Serializable
data class EffectDto(
    val stat: String,
    val delta: Int,
)

@Serializable
data class ShopDto(val items: List<ShopItemDto>)

@Serializable
data class ShopItemDto(
    val id: String,
    val titleKey: String,
    val price: Int,
    val category: String,
    val effects: List<EffectDto> = emptyList(),
    val icon: String,
    val isToy: Boolean = false,
)

@Serializable
data class GoalsDto(val goals: List<GoalDto>)

@Serializable
data class GoalDto(
    val id: String,
    val titleKey: String,
    val price: Int,
    val icon: String,
)

@Serializable
data class GlossaryDto(val terms: List<TermDto>)

@Serializable
data class TermDto(
    val id: String,
    val titleKey: String,
    val bodyKey: String,
)

// --- Задания ---

/**
 * Вид шага и вид условия различаются полем "type" в самом объекте: так
 * продакту не приходится держать в голове вложенные обёртки, а добавление
 * нового вида остаётся правкой одного файла.
 */

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed interface TaskStepDto {

    val promptKey: String

    @Serializable
    @SerialName("CHOICE")
    data class Choice(
        override val promptKey: String,
        val options: List<TaskOptionDto>,
    ) : TaskStepDto

    @Serializable
    @SerialName("DISTRIBUTE")
    data class Distribute(
        override val promptKey: String,
        val budget: Int,
    ) : TaskStepDto

    @Serializable
    @SerialName("PICK_ITEMS")
    data class PickItems(
        override val promptKey: String,
        val itemIds: List<String>,
        val budget: Int,
    ) : TaskStepDto

    /**
     * «Три банки»: то же распределение, что и DISTRIBUTE, но подписи и порядок
     * банок задаёт контент. Одни и те же монеты в разных заданиях делятся
     * на «нужное и желания» или на «копилку и остальное».
     */
    @Serializable
    @SerialName("THREE_JARS")
    data class ThreeJars(
        override val promptKey: String,
        val totalCoins: Int,
        val jars: List<JarDto>,
    ) : TaskStepDto

    /** «Прилавок»: корзина из товаров самого задания, а не из магазина. */
    @Serializable
    @SerialName("SHELF")
    data class Shelf(
        override val promptKey: String,
        val budget: Int,
        val items: List<ShelfItemDto>,
    ) : TaskStepDto
}

@Serializable
data class TaskOptionDto(
    val id: String,
    val textKey: String,
)

/** Идентификатор банки — направление расхода: mandatory, wants, savings. */
@Serializable
data class JarDto(
    val id: String,
    val labelKey: String,
)

@Serializable
data class ShelfItemDto(
    val id: String,
    val titleKey: String,
    val price: Int,
    /** Обязательная покупка — то, без чего набор не собрать. */
    val isMandatory: Boolean = false,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed interface ConditionDto {

    @Serializable
    @SerialName("OPTION_CHOSEN")
    data class OptionChosen(val optionId: String) : ConditionDto

    @Serializable
    @SerialName("SAVED_AT_LEAST")
    data class SavedAtLeast(val amount: Int) : ConditionDto

    @Serializable
    @SerialName("SPENT_AT_MOST")
    data class SpentAtMost(val amount: Int) : ConditionDto

    /**
     * Выбран один из вариантов. Пишется и одним ключом, и списком: разные
     * решения с одним уроком получают одно объяснение.
     */
    @Serializable
    @SerialName("SELECTED_OPTION")
    data class SelectedOption(
        val optionId: String? = null,
        val optionIds: List<String> = emptyList(),
    ) : ConditionDto

    /** Сколько монет должно лежать в каждой банке; не названа — не проверяется. */
    @Serializable
    @SerialName("JARS_DISTRIBUTION")
    data class JarsDistribution(
        val minMandatory: Int? = null,
        val minWants: Int? = null,
        val minSavings: Int? = null,
    ) : ConditionDto

    @Serializable
    @SerialName("BASKET_CONTAINS")
    data class BasketContains(val itemId: String) : ConditionDto

    @Serializable
    @SerialName("BASKET_CONTAINS_ALL")
    data class BasketContainsAll(val requiredItemIds: List<String>) : ConditionDto

    @Serializable
    @SerialName("OTHERWISE")
    data object Otherwise : ConditionDto
}

@Serializable
data class OutcomeDto(
    /** Не указан — парсер назовёт исход по его месту в списке. */
    val id: String? = null,
    val condition: ConditionDto,
    /** Не указана — подставится taskReward из balance.json. */
    val reward: Int? = null,
    val explanationKey: String,
    val effects: List<EffectDto> = emptyList(),
    /** Не указан — ответ неверный: монеты случайно не раздаются. */
    val correct: Boolean = false,
)

@Serializable
data class TasksDto(val tasks: List<TaskDto>)

/** Когда задание показывается само: пока `type` — только PET_SAD. */
@Serializable
data class ShowWhenDto(
    val type: String,
    val stat: String,
)

@Serializable
data class TaskDto(
    val id: String,
    val topic: String,
    /** Заголовок задания, он же вступление на экране прохождения. */
    val titleKey: String,
    val steps: List<TaskStepDto>,
    val outcomes: List<OutcomeDto>,
    /** Есть — это разбор ошибки, а не обычное задание (AD-7). */
    val showWhen: ShowWhenDto? = null,
)
