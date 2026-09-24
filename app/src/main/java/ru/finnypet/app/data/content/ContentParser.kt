package ru.finnypet.app.data.content

import kotlinx.serialization.json.Json
import ru.finnypet.app.domain.content.ContentOption
import ru.finnypet.app.domain.content.ContentPack
import ru.finnypet.app.domain.content.GlossaryTerm
import ru.finnypet.app.domain.content.PetColor
import ru.finnypet.app.domain.content.PetOptions
import ru.finnypet.app.domain.economy.GameBalance
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.Goal
import ru.finnypet.app.domain.model.GoalId
import ru.finnypet.app.domain.model.ItemId
import ru.finnypet.app.domain.model.Jar
import ru.finnypet.app.domain.model.LearningTask
import ru.finnypet.app.domain.model.OutcomeCondition
import ru.finnypet.app.domain.model.PetEffect
import ru.finnypet.app.domain.model.PetStatKind
import ru.finnypet.app.domain.model.ShelfItem
import ru.finnypet.app.domain.model.ShopItem
import ru.finnypet.app.domain.model.SpendCategory
import ru.finnypet.app.domain.model.TaskId
import ru.finnypet.app.domain.model.TaskOption
import ru.finnypet.app.domain.model.TaskOutcome
import ru.finnypet.app.domain.model.TaskStep
import ru.finnypet.app.domain.model.TaskTopic
import javax.inject.Inject

/** Ошибка в контент-паке. Сообщение всегда называет файл и место. */
class ContentParseException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Содержимое семи файлов как есть, до разбора. */
data class RawContent(
    val balance: String,
    val pets: String,
    val shop: String,
    val goals: String,
    val tasks: String,
    val glossary: String,
    val explanations: String,
)

/**
 * Превращает файлы контент-пака в доменные типы.
 *
 * Проверок здесь почти нет — их делают сами доменные типы: Coins не бывает
 * отрицательным, у задания обязан быть ровно один исход по умолчанию, товар
 * не может относиться к накоплениям. Задача парсера в другом: поймать эти
 * отказы и превратить в сообщение, по которому продакт найдёт строку в своём
 * файле, не открывая код.
 */
class ContentParser @Inject constructor() {

    /**
     * Неизвестные ключи не игнорируются: опечатка в названии поля иначе
     * прошла бы молча, и продакт бы не понял, почему правка не подействовала.
     */
    private val json = Json { ignoreUnknownKeys = false }

    fun parse(raw: RawContent): ContentPack {
        // Числа и товары разбираются первыми: задания ссылаются на товары
        // по идентификатору и берут награду по умолчанию из чисел экономики.
        val balance = parseBalance(raw.balance)
        val shop = parseShop(raw.shop)
        return ContentPack(
            balance = balance,
            pets = parsePets(raw.pets),
            shop = shop,
            goals = parseGoals(raw.goals),
            tasks = parseTasks(raw.tasks, shop.map { it.id }.toSet(), balance.taskReward),
            glossary = parseGlossary(raw.glossary),
            texts = decode<Map<String, String>>(EXPLANATIONS, raw.explanations),
        )
    }

    private fun parseBalance(raw: String): GameBalance {
        val dto = decode<BalanceDto>(BALANCE, raw)
        return at(BALANCE, "числа экономики") {
            GameBalance(
                startingBalance = Coins(dto.startingBalance),
                periodIncome = Coins(dto.periodIncome),
                taskReward = Coins(dto.taskReward),
                initialStat = dto.initialStat,
                nightDropSatiety = dto.nightDropSatiety,
                nightDropCare = dto.nightDropCare,
                nightDropMood = dto.nightDropMood,
                statFloor = dto.statFloor,
                needThreshold = dto.needThreshold,
                sadThreshold = dto.sadThreshold,
                moodBonusPlanFollowed = dto.moodBonusPlanFollowed,
                growthForMandatoryCovered = dto.growthForMandatoryCovered,
                growthForPlanFollowed = dto.growthForPlanFollowed,
                growthForSavingsKept = dto.growthForSavingsKept,
                growthThresholds = dto.growthThresholds,
                // Генератора непредвиденных расходов в проекте нет: в
                // обязательный минимум ТЗ они не входят, поле остаётся нулевым.
                unexpectedExpenseChance = 0,
                carryOverUnspent = dto.carryOverUnspent,
                rewardedTasksPerPeriod = dto.rewardedTasksPerPeriod,
                parentBonus = Coins(dto.parentBonus),
            )
        }
    }

    private fun parsePets(raw: String): PetOptions {
        val dto = decode<PetsDto>(PETS, raw)
        return at(PETS, "внешность питомца") {
            // Без тела или окраса питомца не собрать: экран создания упал бы
            // на попытке взять первый вариант. Ловим здесь, где ошибка
            // называет файл, а не в рантайме у ребёнка.
            require(dto.bodies.isNotEmpty()) { "нет ни одного тела питомца" }
            require(dto.colors.isNotEmpty()) { "нет ни одного окраса" }
            // Аксессуары могут отсутствовать: вариант «без» приложение
            // предлагает само, он всегда доступен.
            PetOptions(
                bodies = dto.bodies.map { ContentOption(it.id, it.titleKey) }.unique(PETS, "тело"),
                colors = dto.colors.map(::petColor)
                    .also { colors -> colors.map { it.id }.requireUnique(PETS, "окрас") },
                accessories = dto.accessories.map { ContentOption(it.id, it.titleKey) }
                    .unique(PETS, "аксессуар"),
            )
        }
    }

    private fun parseShop(raw: String): List<ShopItem> {
        val dto = decode<ShopDto>(SHOP, raw)
        return dto.items.map { item ->
            at(SHOP, "товар ${item.id}") {
                ShopItem(
                    id = ItemId(item.id),
                    titleKey = item.titleKey,
                    price = Coins(item.price),
                    category = enum<SpendCategory>(item.category, "category"),
                    effects = item.effects.map(::effect),
                    icon = icon(item.icon),
                )
            }
        }.also { items -> items.map { it.id.value }.requireUnique(SHOP, "товар") }
    }

    private fun parseGoals(raw: String): List<Goal> {
        val dto = decode<GoalsDto>(GOALS, raw)
        return dto.goals.map { goal ->
            at(GOALS, "цель ${goal.id}") {
                Goal(id = GoalId(goal.id), titleKey = goal.titleKey, price = Coins(goal.price), icon = icon(goal.icon))
            }
        }.also { goals -> goals.map { it.id.value }.requireUnique(GOALS, "цель") }
    }

    private fun parseTasks(
        raw: String,
        knownItems: Set<ItemId>,
        defaultReward: Coins,
    ): List<LearningTask> {
        val dto = decode<TasksDto>(TASKS, raw)
        return dto.tasks.map { task ->
            at(TASKS, "задание ${task.id}") {
                LearningTask(
                    id = TaskId(task.id),
                    topic = enum<TaskTopic>(task.topic, "topic"),
                    introKey = task.titleKey,
                    steps = task.steps.map(::step),
                    outcomes = task.outcomes.mapIndexed { index, it -> outcome(it, index, defaultReward) },
                    showWhenSadAbout = task.showWhen?.let(::sadAbout),
                ).also {
                    checkReferences(it, knownItems)
                    // Без верного исхода задание нельзя пройти и за него не заплатят (R8).
                    require(it.outcomes.any { outcome -> outcome.correct }) { "нет ни одного исхода с \"correct\": true" }
                }
            }
        }.also { tasks -> tasks.map { it.id.value }.requireUnique(TASKS, "задание") }
    }

    /**
     * Ссылки внутри задания должны вести туда, где что-то есть.
     *
     * Опечатка в optionId проходит и разбор, и запуск: TaskEngine просто не
     * найдёт совпадения и отдаст исход по умолчанию. Ребёнок ответит верно,
     * а увидит объяснение для ошибки, и заметить это можно только пройдя
     * задание руками.
     */
    private fun checkReferences(task: LearningTask, knownItems: Set<ItemId>) {
        val optionIds = task.steps.filterIsInstance<TaskStep.Choice>()
            .flatMap { it.options }
            .map { it.id }
            .toSet()

        // Товары для условий по корзине берутся из обоих видов корзины: из
        // магазина у PICK_ITEMS и с прилавка самого задания у SHELF.
        val basketIds = task.steps.filterIsInstance<TaskStep.PickItems>()
            .flatMap { step -> step.itemIds.map { it.value } }
            .plus(task.steps.filterIsInstance<TaskStep.Shelf>().flatMap { step -> step.items.map { it.id } })
            .toSet()

        task.outcomes.forEach { outcome ->
            when (val condition = outcome.condition) {
                is OutcomeCondition.OptionChosen ->
                    checkOptions(outcome.id, listOf(condition.optionId), optionIds)

                is OutcomeCondition.AnyOptionChosen ->
                    checkOptions(outcome.id, condition.optionIds, optionIds)

                is OutcomeCondition.BasketContains -> {
                    val unknown = condition.itemIds.filterNot { it in basketIds }
                    require(unknown.isEmpty()) {
                        "исход ${outcome.id} ждёт в корзине ${unknown.joinToString()}, " +
                            "а взять в задании можно только ${basketIds.joinToString()}"
                    }
                }

                else -> Unit
            }
        }

        task.steps.filterIsInstance<TaskStep.PickItems>().forEach { step ->
            val unknown = step.itemIds.filterNot { it in knownItems }
            require(unknown.isEmpty()) {
                "шаг с корзиной ссылается на товары, которых нет в shop.json: " +
                    unknown.joinToString { it.value }
            }
        }
    }

    private fun checkOptions(outcomeId: String, wanted: List<String>, known: Set<String>) {
        val unknown = wanted.filterNot { it in known }
        require(unknown.isEmpty()) {
            "исход $outcomeId ждёт вариант \"${unknown.joinToString("\", \"")}\", " +
                "а в шагах задания есть только ${known.joinToString()}"
        }
    }

    private fun parseGlossary(raw: String): List<GlossaryTerm> {
        val dto = decode<GlossaryDto>(GLOSSARY, raw)
        return dto.terms.map { term ->
            at(GLOSSARY, "термин ${term.id}") {
                GlossaryTerm(id = term.id, titleKey = term.titleKey, bodyKey = term.bodyKey)
            }
        }.also { terms -> terms.map { it.id }.requireUnique(GLOSSARY, "термин") }
    }

    private fun step(dto: TaskStepDto): TaskStep = when (dto) {
        is TaskStepDto.Choice -> TaskStep.Choice(
            promptKey = dto.promptKey,
            options = dto.options.map { TaskOption(id = it.id, labelKey = it.textKey) },
        )

        is TaskStepDto.Distribute -> TaskStep.Distribute(
            promptKey = dto.promptKey,
            budget = Coins(dto.budget),
        )

        is TaskStepDto.ThreeJars -> TaskStep.Distribute(
            promptKey = dto.promptKey,
            budget = Coins(dto.totalCoins),
            jars = dto.jars.map { Jar(category = jarCategory(it.id), labelKey = it.labelKey) },
        )

        is TaskStepDto.PickItems -> TaskStep.PickItems(
            promptKey = dto.promptKey,
            itemIds = dto.itemIds.map(::ItemId),
            budget = Coins(dto.budget),
        )

        is TaskStepDto.Shelf -> TaskStep.Shelf(
            promptKey = dto.promptKey,
            budget = Coins(dto.budget),
            items = dto.items.map { item ->
                ShelfItem(
                    id = item.id,
                    titleKey = item.titleKey,
                    price = Coins(item.price),
                    // Обязательная покупка и обязательный расход — одно и то
                    // же направление: ребёнок уже видел его в плане дня.
                    category = if (item.isMandatory) SpendCategory.MANDATORY else SpendCategory.OPTIONAL,
                )
            },
        )
    }

    /**
     * Банка называется направлением расхода. Своё имя вместо трёх известных —
     * это опечатка продакта: молча отдать монеты не в ту банку хуже, чем
     * назвать файл и строку.
     */
    private fun jarCategory(id: String): SpendCategory = when (id) {
        "mandatory" -> SpendCategory.MANDATORY
        "wants" -> SpendCategory.OPTIONAL
        "savings" -> SpendCategory.SAVINGS
        else -> throw IllegalArgumentException(
            "банка \"$id\": допустимы mandatory, wants, savings"
        )
    }

    /**
     * Награда необязательна: не указана — берётся taskReward из balance.json.
     * Имя исхода тоже: без него исход зовётся по месту в списке, и продакту
     * не приходится выдумывать идентификатор ради записи о прохождении.
     */
    private fun outcome(dto: OutcomeDto, index: Int, defaultReward: Coins) = TaskOutcome(
        id = dto.id ?: "outcome-${index + 1}",
        condition = condition(dto.condition),
        reward = dto.reward?.let(::Coins) ?: defaultReward,
        explanationKey = dto.explanationKey,
        effects = dto.effects.map(::effect),
        correct = dto.correct,
    )

    private fun condition(dto: ConditionDto): OutcomeCondition = when (dto) {
        is ConditionDto.OptionChosen -> OutcomeCondition.OptionChosen(dto.optionId)
        is ConditionDto.SavedAtLeast -> OutcomeCondition.SavedAtLeast(Coins(dto.amount))
        is ConditionDto.SpentAtMost -> OutcomeCondition.SpentAtMost(Coins(dto.amount))

        is ConditionDto.SelectedOption -> {
            val ids = listOfNotNull(dto.optionId) + dto.optionIds
            require(ids.isNotEmpty()) {
                "условие SELECTED_OPTION: назовите optionId или optionIds"
            }
            if (ids.size == 1) {
                OutcomeCondition.OptionChosen(ids.single())
            } else {
                OutcomeCondition.AnyOptionChosen(ids)
            }
        }

        is ConditionDto.JarsDistribution -> OutcomeCondition.JarsAtLeast(
            mandatory = dto.minMandatory?.let(::Coins),
            optional = dto.minWants?.let(::Coins),
            savings = dto.minSavings?.let(::Coins),
        )

        is ConditionDto.BasketContains -> OutcomeCondition.BasketContains(listOf(dto.itemId))

        is ConditionDto.BasketContainsAll ->
            OutcomeCondition.BasketContains(dto.requiredItemIds)

        ConditionDto.Otherwise -> OutcomeCondition.Otherwise
    }

    private fun sadAbout(dto: ShowWhenDto): PetStatKind {
        require(dto.type == "PET_SAD") { "условие показа \"${dto.type}\": допустимо только PET_SAD" }
        return enum<PetStatKind>(dto.stat, "stat")
    }

    /** Без картинки карточка в магазине и копилке выглядела бы сломанной. */
    private fun icon(value: String): String {
        require(value.isNotBlank()) { "поле icon пустое: нужен эмодзи" }
        return value
    }

    private fun effect(dto: EffectDto) = PetEffect(
        stat = enum<PetStatKind>(dto.stat, "stat"),
        delta = dto.delta,
    )

    private inline fun <reified T> decode(file: String, raw: String): T = try {
        json.decodeFromString<T>(raw)
    } catch (error: Exception) {
        throw ContentParseException("$file: не разобрался JSON. ${error.message}", error)
    }

    private inline fun <reified T : Enum<T>> enum(value: String, field: String): T =
        enumValues<T>().firstOrNull { it.name == value }
            ?: throw IllegalArgumentException(
                "поле $field: неизвестное значение \"$value\", допустимы " +
                    enumValues<T>().joinToString { it.name }
            )

    private inline fun <T> at(file: String, what: String, block: () -> T): T = try {
        block()
    } catch (error: ContentParseException) {
        throw error
    } catch (error: Exception) {
        throw ContentParseException("$file, $what: ${error.message}", error)
    }

    private fun petColor(dto: ColorDto) = PetColor(
        option = ContentOption(dto.id, dto.titleKey),
        body = hex(dto.id, dto.body),
        wing = hex(dto.id, dto.wing),
        face = hex(dto.id, dto.face),
        ring = hex(dto.id, dto.ring),
    )

    /** `#RRGGBB` в непрозрачный ARGB. Опечатка в цвете называет окрас, а не роняет рисование. */
    private fun hex(colorId: String, value: String): Long {
        require(HEX_COLOR.matches(value)) { "окрас $colorId: цвет \"$value\" должен быть вида #RRGGBB" }
        return 0xFF000000L or value.drop(1).toLong(16)
    }

    private fun List<ContentOption>.unique(file: String, what: String): List<ContentOption> =
        also { options -> options.map { it.id }.requireUnique(file, what) }

    private fun List<String>.requireUnique(file: String, what: String) {
        val duplicates = groupBy { it }.filterValues { it.size > 1 }.keys
        if (duplicates.isNotEmpty()) {
            throw ContentParseException(
                "$file: $what с повторяющимся идентификатором — ${duplicates.joinToString()}"
            )
        }
    }

    private companion object {
        const val BALANCE = "balance.json"
        const val PETS = "pets.json"
        const val SHOP = "shop.json"
        const val GOALS = "goals.json"
        const val TASKS = "tasks.json"
        const val GLOSSARY = "glossary.json"
        const val EXPLANATIONS = "explanations.json"

        val HEX_COLOR = Regex("#[0-9A-Fa-f]{6}")
    }
}
