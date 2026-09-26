package ru.finnypet.app.data.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.GrowthStage
import ru.finnypet.app.domain.model.OutcomeCondition
import ru.finnypet.app.domain.model.PetStatKind
import ru.finnypet.app.domain.model.SpendCategory
import ru.finnypet.app.domain.content.DayEvent
import ru.finnypet.app.domain.model.TaskStep
import ru.finnypet.app.domain.model.TaskTopic
import ru.finnypet.app.domain.usecase.TaskSchedule

/**
 * Разбор проверяется на настоящих файлах из ассетов, а не на выдуманных
 * строках: так тест заодно стережёт сам контент-пак — если продакт сломает
 * формат, это видно сразу, а не на устройстве.
 */
class ContentParserTest {

    @Test
    fun `события дней 4 и 5 читаются из JSON и оставляют выбор`() {
        val pack = parser.parse(realContent())
        assertEquals(listOf(4, 5), pack.events.map { it.day })
        val care = pack.events[0] as DayEvent.ExtraCare
        val gift = pack.events[1] as DayEvent.Gift
        assertEquals(10, care.careDrop)
        assertEquals(8, gift.amount.amount)
        // После обычной ночи уход 55; событие снижает до 45, щётка закрывает потребность.
        val brush = pack.shop.first { it.id.value == "care-brush" }
        assertTrue(45 + brush.effects.single().delta >= pack.balance.needThreshold)
        assertTrue(brush.price + pack.shop.first { it.id.value == "food-porridge" }.price <= pack.balance.periodIncome)
        assertTrue(gift.amount < pack.shop.minOf { it.price } + pack.shop.filter { it.category == SpendCategory.OPTIONAL }.minOf { it.price })
    }

    @Test
    fun `повтор события в одном дне отклоняется`() {
        org.junit.Assert.assertThrows(ContentParseException::class.java) {
            parser.parse(realContent(events = """{"events":[{"id":"a","day":4,"type":"GIFT","messageKey":"event.family_gift","amount":8},{"id":"b","day":4,"type":"GIFT","messageKey":"event.family_gift","amount":8}]}"""))
        }
    }

    private val parser = ContentParser()

    @Test
    fun `настоящий контент-пак разбирается`() {
        val pack = parser.parse(realContent())

        // Конкретные числа принадлежат продакту и меняются по ходу — тест
        // проверяет, что пак разобрался, а не какие в нём цифры.
        assertEquals(GrowthStage.entries.size, pack.balance.growthThresholds.size)
        assertTrue("Товаров не разобралось", pack.shop.isNotEmpty())
        assertTrue("Целей не разобралось", pack.goals.isNotEmpty())
        assertTrue("Заданий не разобралось", pack.tasks.isNotEmpty())
        assertTrue("Терминов не разобралось", pack.glossary.isNotEmpty())
        assertTrue("Текстов не разобралось", pack.texts.isNotEmpty())
    }

    @Test
    fun `категории товаров разбираются в направления расходов`() {
        val pack = parser.parse(realContent())

        assertEquals(SpendCategory.MANDATORY, pack.shop.first { it.id.value == "food-porridge" }.category)
        assertEquals(SpendCategory.OPTIONAL, pack.shop.first { it.id.value == "toy-ball" }.category)
    }

    /** U2: только мяч и книга — игрушки, у которых питомец видимо играет после покупки. */
    @Test
    fun `игрушкой помечены только мяч и книга`() {
        val pack = parser.parse(realContent())

        assertTrue(pack.shop.first { it.id.value == "toy-ball" }.isToy)
        assertTrue(pack.shop.first { it.id.value == "toy-book" }.isToy)
        assertTrue(pack.shop.filter { it.id.value !in setOf("toy-ball", "toy-book") }.none { it.isToy })
    }

    @Test
    fun `комбинации внешности считаются с учётом варианта без аксессуара`() {
        val pets = parser.parse(
            realContent(
                pets = """
                {
                  "bodies": [{"id":"owl","titleKey":"a"},{"id":"cat","titleKey":"b"}],
                  "colors": [{"id":"mint","titleKey":"c","body":"#FFFFFF","wing":"#FFFFFF","face":"#FFFFFF","ring":"#000000"},{"id":"rose","titleKey":"d","body":"#FFFFFF","wing":"#FFFFFF","face":"#FFFFFF","ring":"#000000"}],
                  "accessories": [{"id":"scarf","titleKey":"e"},{"id":"hat","titleKey":"f"}]
                }
                """
            )
        ).pets

        // 2 тела x 2 окраса x (2 аксессуара + без) = 12
        assertEquals(12, pets.combinationCount)
    }

    @Test
    fun `все три вида шагов задания разбираются`() {
        val tasks = parser.parse(realContent(tasks = TASK_WITH_ALL_STEPS)).tasks

        val steps = tasks.single().steps
        assertTrue(steps[0] is TaskStep.Choice)
        assertTrue(steps[1] is TaskStep.Distribute)
        assertTrue(steps[2] is TaskStep.PickItems)
    }

    @Test
    fun `все четыре вида условий исхода разбираются`() {
        val outcomes = parser.parse(realContent(tasks = TASK_WITH_ALL_CONDITIONS)).tasks.single().outcomes

        assertTrue(outcomes[0].condition is OutcomeCondition.OptionChosen)
        assertTrue(outcomes[1].condition is OutcomeCondition.SavedAtLeast)
        assertTrue(outcomes[2].condition is OutcomeCondition.SpentAtMost)
        assertTrue(outcomes[3].condition is OutcomeCondition.Otherwise)
    }

    @Test
    fun `за пять периодов питомец доходит до последней стадии`() {
        val balance = parser.parse(realContent()).balance
        val reachable = balance.maxGrowthPerPeriod * DEMO_PERIODS
        val required = balance.growthThresholds.last()

        assertTrue(
            "За $DEMO_PERIODS периодов набирается $reachable очков роста, а на последнюю " +
                "стадию нужно $required. ТЗ 2.6 требует показать пять периодов и три стадии — " +
                "подними очки за период или опусти последний порог в balance.json",
            reachable >= required,
        )
    }

    /** Минимумы ТЗ 2.6: напарник правит JSON через веб, и нарушение должно ловиться здесь. */
    @Test
    fun `настоящий контент-пак закрывает минимумы ТЗ 2_6`() {
        val pack = parser.parse(realContent())

        assertTrue("Внешностей меньше 9", pack.pets.combinationCount >= 9)
        assertTrue("Товаров меньше 8", pack.shop.size >= 8)
        assertEquals(
            "Нужны товары обоих типов",
            setOf(SpendCategory.MANDATORY, SpendCategory.OPTIONAL),
            pack.shop.map { it.category }.toSet(),
        )
        assertTrue("Целей меньше 3", pack.goals.size >= 3)
        // Разбор ошибки в минимум не входит (AD-7): он показывается сам, а не выбирается.
        val listed = TaskSchedule.listed(pack.tasks)
        assertTrue("Заданий меньше 6", listed.size >= 6)
        assertEquals("Нужны задания по всем трём темам", TaskTopic.entries.toSet(), listed.map { it.topic }.toSet())
    }

    @Test
    fun `в настоящем контенте есть разбор голодной совы`() {
        val reviews = parser.parse(realContent()).tasks.filter { it.isReview }

        assertEquals(listOf(PetStatKind.SATIETY), reviews.map { it.showWhenSadAbout })
    }

    @Test
    fun `признак верного исхода читается, по умолчанию — неверный`() {
        val outcomes = parser.parse(realContent(tasks = TASK_WITH_ALL_STEPS)).tasks.single().outcomes

        assertEquals(listOf(true, false), outcomes.map { it.correct })
    }

    /** Без верного исхода задание нельзя пройти, и за него не заплатят (R8). */
    @Test
    fun `задание без верного исхода не загружается`() {
        val error = parseFailure(tasks = TASK_WITH_ALL_STEPS.replace(",\"correct\":true", ""))

        assertTrue(error.message!!.contains("correct"))
    }

    @Test
    fun `условие показа читается как разбор ошибки`() {
        val task = parser.parse(realContent(tasks = reviewTask("PET_SAD"))).tasks.single()

        assertEquals(PetStatKind.SATIETY, task.showWhenSadAbout)
    }

    @Test
    fun `незнакомое условие показа не загружается`() {
        val error = parseFailure(tasks = reviewTask("PET_BORED"))

        assertTrue(error.message!!.contains("PET_BORED"))
    }

    private fun reviewTask(type: String) = TASK_WITH_ALL_STEPS.replace(
        "\"titleKey\":\"i\",",
        "\"titleKey\":\"i\",\"showWhen\":{\"type\":\"$type\",\"stat\":\"SATIETY\"},",
    )

    /** Пропавший текст ребёнок видит сырым ключом вроде «task.….intro» — ловим здесь. */
    @Test
    fun `у каждого ключа текста в контент-паке есть текст`() {
        val texts = parser.parse(realContent()).texts
        val missing = CONTENT_FILES.flatMap { file ->
            TEXT_KEY.findAll(RealContent.asset(file)).map { it.groupValues[1] }
                .filterNot(texts::containsKey)
                .map { "$file: $it" }
        }

        assertTrue("Нет текста в explanations.json для ключей: $missing", missing.isEmpty())
    }

    // --- Сообщения об ошибках: по ним продакт должен найти место в своём файле ---

    /** Раздел 3 плана: запас для фразы совы «мне столько не нужно». */
    @Test
    fun `запас на нужное читается из чисел экономики`() {
        assertEquals(9, parser.parse(realContent()).balance.needSlack)
    }

    @Test
    fun `лимит заданий с наградой читается из чисел экономики`() {
        assertEquals(1, parser.parse(realContent()).balance.rewardedTasksPerPeriod)
    }

    /** Поле появилось позже остальных: старый файл без него читается как «одно в день». */
    @Test
    fun `без поля лимита заданий подставляется единица`() {
        // Выражением, а не подстрокой с переводом строки: в рабочей копии файл
        // бывает и с CRLF, и с LF, и поле не обязано стоять последним.
        val without = RealContent.asset("balance.json")
            .replace(Regex(""",?\s*"rewardedTasksPerPeriod"\s*:\s*\d+"""), "")
        assertTrue("поле должно было удалиться из копии", "rewardedTasksPerPeriod" !in without)

        assertEquals(1, parser.parse(realContent(balance = without)).balance.rewardedTasksPerPeriod)
    }

    @Test
    fun `опечатка в имени поля называет файл`() {
        val error = parseFailure(goals = """{"goals":[{"id":"bike","titleKey":"g","cost":120}]}""")

        assertTrue("Сообщение не называет файл: ${error.message}", error.message!!.contains("goals.json"))
    }

    @Test
    fun `неизвестная категория товара перечисляет допустимые`() {
        val error = parseFailure(
            shop = """{"items":[{"id":"x","titleKey":"t","icon":"🧪","price":10,"category":"FOOD"}]}"""
        )

        val message = error.message.orEmpty()
        assertTrue("Не назван файл: $message", message.contains("shop.json"))
        assertTrue("Не назван товар: $message", message.contains("x"))
        assertTrue("Не перечислены допустимые: $message", message.contains("MANDATORY"))
    }

    @Test
    fun `товар с ценой меньше нуля называет товар`() {
        val error = parseFailure(
            shop = """{"items":[{"id":"cheap","titleKey":"t","icon":"🧪","price":-5,"category":"OPTIONAL"}]}"""
        )

        assertTrue("Не назван товар: ${error.message}", error.message!!.contains("cheap"))
    }

    @Test
    fun `товар в накоплениях отвергается`() {
        val error = parseFailure(
            shop = """{"items":[{"id":"x","titleKey":"t","icon":"🧪","price":10,"category":"SAVINGS"}]}"""
        )

        assertTrue("Не назван файл: ${error.message}", error.message!!.contains("shop.json"))
    }

    /** AD-11: у каждого товара и цели картинка-эмодзи из контента. */
    @Test
    fun `у каждого товара и цели есть картинка`() {
        val pack = parser.parse(realContent())

        assertTrue(pack.shop.all { it.icon.isNotBlank() })
        assertTrue(pack.goals.all { it.icon.isNotBlank() })
    }

    @Test
    fun `товар с пустой картинкой называет товар`() {
        val error = parseFailure(
            shop = """{"items":[{"id":"blank","titleKey":"t","icon":" ","price":10,"category":"OPTIONAL"}]}"""
        )

        assertTrue("Не назван товар: ${error.message}", error.message!!.contains("blank"))
    }

    @Test
    fun `повторяющийся идентификатор товара отвергается`() {
        val error = parseFailure(
            shop = """
            {"items":[
              {"id":"same","titleKey":"a","icon":"🧪","price":10,"category":"MANDATORY"},
              {"id":"same","titleKey":"b","icon":"🧪","price":20,"category":"OPTIONAL"}
            ]}
            """
        )

        assertTrue("Не назван дубликат: ${error.message}", error.message!!.contains("same"))
    }

    @Test
    fun `задание без исхода по умолчанию отвергается с именем задания`() {
        val error = parseFailure(tasks = TASK_WITHOUT_OTHERWISE)

        val message = error.message.orEmpty()
        assertTrue("Не назван файл: $message", message.contains("tasks.json"))
        assertTrue("Не названо задание: $message", message.contains("no-fallback"))
    }

    @Test
    fun `неизвестная тема задания перечисляет допустимые`() {
        val error = parseFailure(tasks = TASK_WITH_BAD_TOPIC)

        assertTrue("Не перечислены темы: ${error.message}", error.message!!.contains("PLANNING"))
    }

    @Test
    fun `исход со ссылкой на несуществующий вариант отвергается`() {
        val error = parseFailure(tasks = TASK_WITH_UNKNOWN_OPTION)

        val message = error.message.orEmpty()
        assertTrue("Не назван файл: $message", message.contains("tasks.json"))
        assertTrue("Не назван вариант: $message", message.contains("waite"))
    }

    @Test
    fun `корзина со ссылкой на несуществующий товар отвергается`() {
        val error = parseFailure(tasks = TASK_WITH_UNKNOWN_ITEM)

        val message = error.message.orEmpty()
        assertTrue("Не назван товар: $message", message.contains("no-such-item"))
        assertTrue("Не назван shop.json: $message", message.contains("shop.json"))
    }

    @Test
    fun `исход без награды получает taskReward из чисел экономики`() {
        val pack = parser.parse(realContent(tasks = TASK_WITHOUT_REWARD))

        val outcomes = pack.tasks.single().outcomes
        // У исхода "saved" награда не указана, у "other" указан ноль.
        assertEquals(pack.balance.taskReward, outcomes.first { it.id == "saved" }.reward)
        assertEquals(Coins.ZERO, outcomes.first { it.id == "other" }.reward)
    }

    @Test
    fun `три банки разбираются с подписями и порядком из контента`() {
        val pack = parser.parse(realContent(tasks = TASK_WITH_JARS_AND_SHELF))

        val step = pack.tasks.single().steps.first() as TaskStep.Distribute
        assertEquals(Coins(40), step.budget)
        // Порядок банок — решение продакта: в задании про подарок копилка
        // стоит первой, чтобы ребёнок начинал с неё.
        assertEquals(
            listOf(SpendCategory.SAVINGS, SpendCategory.MANDATORY, SpendCategory.OPTIONAL),
            step.jars.map { it.category },
        )
        assertEquals("l.savings", step.jars.first().labelKey)
    }

    @Test
    fun `прилавок разбирается в товары задания, обязательный — в обязательный расход`() {
        val pack = parser.parse(realContent(tasks = TASK_WITH_JARS_AND_SHELF))

        val step = pack.tasks.single().steps[1] as TaskStep.Shelf
        assertEquals(Coins(25), step.budget)
        assertEquals(listOf("juice", "candy"), step.items.map { it.id })
        assertEquals(Coins(18), step.items.first().price)
        assertEquals(SpendCategory.MANDATORY, step.items.first().category)
        // Пометки нет — значит покупка необязательная.
        assertEquals(SpendCategory.OPTIONAL, step.items[1].category)
    }

    @Test
    fun `условие по банкам читает минимумы по направлениям`() {
        val pack = parser.parse(realContent(tasks = TASK_WITH_JARS_AND_SHELF))

        val condition = pack.tasks.single().outcomes
            .map { it.condition }
            .filterIsInstance<OutcomeCondition.JarsAtLeast>()
            .single()
        assertEquals(Coins(15), condition.mandatory)
        assertEquals(Coins(5), condition.savings)
        // Про желания в задании ничего не сказано — условие их не проверяет.
        assertEquals(null, condition.optional)
    }

    @Test
    fun `исход без имени зовётся по месту в списке`() {
        val pack = parser.parse(realContent(tasks = TASK_WITH_JARS_AND_SHELF))

        assertEquals(
            listOf("outcome-1", "outcome-2", "outcome-3"),
            pack.tasks.single().outcomes.map { it.id },
        )
    }

    @Test
    fun `неизвестная банка перечисляет допустимые`() {
        val error = parseFailure(tasks = TASK_WITH_BAD_JAR)

        val message = error.message.orEmpty()
        assertTrue("Не названа банка: $message", message.contains("pocket"))
        assertTrue("Не перечислены допустимые: $message", message.contains("savings"))
    }

    @Test
    fun `выбор списком и выбор одним вариантом разбираются по-разному`() {
        val pack = parser.parse(realContent(tasks = TASK_WITH_SELECTED_OPTIONS))

        val outcomes = pack.tasks.single().outcomes.associateBy { it.id }
        assertEquals(
            OutcomeCondition.AnyOptionChosen(listOf("save", "pause")),
            outcomes.getValue("patient").condition,
        )
        // Один вариант остаётся простым условием: списка из одного не бывает.
        assertEquals(
            OutcomeCondition.OptionChosen("buy"),
            outcomes.getValue("hasty").condition,
        )
    }

    @Test
    fun `условие по корзине читает и один товар, и список`() {
        val one = parser.parse(realContent(tasks = TASK_WITH_JARS_AND_SHELF))
            .tasks.single().outcomes
            .map { it.condition }
            .filterIsInstance<OutcomeCondition.BasketContains>()
            .single()
        assertEquals(listOf("juice"), one.itemIds)

        val all = parser.parse(realContent(tasks = TASK_WITH_BASKET_ALL))
            .tasks.single().outcomes
            .map { it.condition }
            .filterIsInstance<OutcomeCondition.BasketContains>()
            .single()
        assertEquals(listOf("notebook", "pen"), all.itemIds)
    }

    @Test
    fun `исход со ссылкой на товар не с прилавка отвергается`() {
        val error = parseFailure(tasks = TASK_WITH_ITEM_OFF_SHELF)

        val message = error.message.orEmpty()
        assertTrue("Не назван товар: $message", message.contains("ruler"))
        assertTrue("Не названо задание: $message", message.contains("ghost"))
    }

    @Test
    fun `настоящие задания используют и банки, и прилавок`() {
        val steps = parser.parse(realContent()).tasks.flatMap { it.steps }

        assertTrue(
            "В контент-паке нет шага с тремя банками",
            steps.filterIsInstance<TaskStep.Distribute>().any { it.jars.size == 3 },
        )
        assertTrue("В контент-паке нет шага с прилавком", steps.any { it is TaskStep.Shelf })
    }

    // --- Вспомогательное ---

    private fun parseFailure(
        balance: String? = null,
        pets: String? = null,
        shop: String? = null,
        goals: String? = null,
        tasks: String? = null,
    ): ContentParseException = runCatching {
        parser.parse(realContent(balance, pets, shop, goals, tasks))
    }.exceptionOrNull().let { error ->
        assertTrue("Ожидалась ContentParseException, получено: $error", error is ContentParseException)
        error as ContentParseException
    }

    private fun realContent(
        balance: String? = null,
        pets: String? = null,
        shop: String? = null,
        goals: String? = null,
        tasks: String? = null,
        events: String? = null,
    ) = RealContent.raw(balance, pets, shop, goals, tasks, events)

    private companion object {

        /** ТЗ 2.6: в демонстрационном режиме показываем пять периодов. */
        const val DEMO_PERIODS = 5

        /** Файлы, где лежат ссылки на тексты: поля с именем на «Key». */
        val CONTENT_FILES = listOf("tasks.json", "shop.json", "goals.json", "pets.json", "glossary.json", "events.json")
        val TEXT_KEY = Regex(""""\w*Key"\s*:\s*"([^"]+)"""")

        val TASK_WITH_ALL_STEPS = """
        {"tasks":[{
          "id":"all-steps","topic":"PAYMENTS","titleKey":"i",
          "steps":[
            {"type":"CHOICE","promptKey":"p1","options":[{"id":"a","textKey":"la"},{"id":"b","textKey":"lb"}]},
            {"type":"DISTRIBUTE","promptKey":"p2","budget":40},
            {"type":"PICK_ITEMS","promptKey":"p3","itemIds":["food-porridge"],"budget":30}
          ],
          "outcomes":[
            {"id":"good","condition":{"type":"OPTION_CHOSEN","optionId":"a"},"reward":10,"explanationKey":"e1","correct":true},
            {"id":"other","condition":{"type":"OTHERWISE"},"reward":0,"explanationKey":"e2"}
          ]
        }]}
        """

        val TASK_WITH_ALL_CONDITIONS = """
        {"tasks":[{
          "id":"all-conditions","topic":"SAVING","titleKey":"i",
          "steps":[
            {"type":"CHOICE","promptKey":"p1","options":[{"id":"x","textKey":"lx"},{"id":"y","textKey":"ly"}]},
            {"type":"DISTRIBUTE","promptKey":"p2","budget":40}
          ],
          "outcomes":[
            {"id":"a","condition":{"type":"OPTION_CHOSEN","optionId":"x"},"reward":1,"explanationKey":"e1","correct":true},
            {"id":"b","condition":{"type":"SAVED_AT_LEAST","amount":10},"reward":2,"explanationKey":"e2"},
            {"id":"c","condition":{"type":"SPENT_AT_MOST","amount":30},"reward":3,"explanationKey":"e3"},
            {"id":"d","condition":{"type":"OTHERWISE"},"reward":0,"explanationKey":"e4"}
          ]
        }]}
        """

        val TASK_WITHOUT_OTHERWISE = """
        {"tasks":[{
          "id":"no-fallback","topic":"PLANNING","titleKey":"i",
          "steps":[{"type":"DISTRIBUTE","promptKey":"p","budget":40}],
          "outcomes":[
            {"id":"a","condition":{"type":"SAVED_AT_LEAST","amount":10},"reward":1,"explanationKey":"e1","correct":true},
            {"id":"b","condition":{"type":"SAVED_AT_LEAST","amount":20},"reward":2,"explanationKey":"e2"}
          ]
        }]}
        """


        val TASK_WITH_UNKNOWN_OPTION = """
        {"tasks":[{
          "id":"typo","topic":"PLANNING","titleKey":"i",
          "steps":[{"type":"CHOICE","promptKey":"p","options":[{"id":"wait","textKey":"l"},{"id":"buy","textKey":"l2"}]}],
          "outcomes":[
            {"id":"a","condition":{"type":"OPTION_CHOSEN","optionId":"waite"},"reward":1,"explanationKey":"e1","correct":true},
            {"id":"b","condition":{"type":"OTHERWISE"},"reward":0,"explanationKey":"e2"}
          ]
        }]}
        """

        val TASK_WITH_UNKNOWN_ITEM = """
        {"tasks":[{
          "id":"basket","topic":"PAYMENTS","titleKey":"i",
          "steps":[{"type":"PICK_ITEMS","promptKey":"p","itemIds":["no-such-item"],"budget":30}],
          "outcomes":[
            {"id":"a","condition":{"type":"SPENT_AT_MOST","amount":30},"reward":1,"explanationKey":"e1","correct":true},
            {"id":"b","condition":{"type":"OTHERWISE"},"reward":0,"explanationKey":"e2"}
          ]
        }]}
        """

        val TASK_WITHOUT_REWARD = """
        {"tasks":[{
          "id":"no-reward","topic":"SAVING","titleKey":"i",
          "steps":[{"type":"DISTRIBUTE","promptKey":"p","budget":40}],
          "outcomes":[
            {"id":"saved","condition":{"type":"SAVED_AT_LEAST","amount":10},"explanationKey":"e1","correct":true},
            {"id":"other","condition":{"type":"OTHERWISE"},"reward":0,"explanationKey":"e2"}
          ]
        }]}
        """

        /** Виды шагов и условий, которыми написан настоящий контент-пак. */
        val TASK_WITH_JARS_AND_SHELF = """
        {"tasks":[{
          "id":"jars-and-shelf","topic":"PLANNING","titleKey":"i",
          "steps":[
            {"type":"THREE_JARS","promptKey":"p1","totalCoins":40,"jars":[
              {"id":"savings","labelKey":"l.savings"},
              {"id":"mandatory","labelKey":"l.mandatory"},
              {"id":"wants","labelKey":"l.wants"}
            ]},
            {"type":"SHELF","promptKey":"p2","budget":25,"items":[
              {"id":"juice","titleKey":"t.juice","price":18,"isMandatory":true},
              {"id":"candy","titleKey":"t.candy","price":7}
            ]}
          ],
          "outcomes":[
            {"condition":{"type":"JARS_DISTRIBUTION","minMandatory":15,"minSavings":5},"explanationKey":"e1","correct":true},
            {"condition":{"type":"BASKET_CONTAINS","itemId":"juice"},"explanationKey":"e2"},
            {"condition":{"type":"OTHERWISE"},"explanationKey":"e3"}
          ]
        }]}
        """

        val TASK_WITH_BAD_JAR = """
        {"tasks":[{
          "id":"bad-jar","topic":"PLANNING","titleKey":"i",
          "steps":[{"type":"THREE_JARS","promptKey":"p","totalCoins":40,"jars":[
            {"id":"pocket","labelKey":"l"}
          ]}],
          "outcomes":[
            {"id":"a","condition":{"type":"SAVED_AT_LEAST","amount":10},"explanationKey":"e1","correct":true},
            {"id":"b","condition":{"type":"OTHERWISE"},"explanationKey":"e2"}
          ]
        }]}
        """

        val TASK_WITH_SELECTED_OPTIONS = """
        {"tasks":[{
          "id":"selected","topic":"SAVING","titleKey":"i",
          "steps":[{"type":"CHOICE","promptKey":"p","options":[
            {"id":"save","textKey":"l1"},
            {"id":"pause","textKey":"l2"},
            {"id":"buy","textKey":"l3"}
          ]}],
          "outcomes":[
            {"id":"patient","condition":{"type":"SELECTED_OPTION","optionIds":["save","pause"]},"explanationKey":"e1","correct":true},
            {"id":"hasty","condition":{"type":"SELECTED_OPTION","optionId":"buy"},"explanationKey":"e2"},
            {"id":"other","condition":{"type":"OTHERWISE"},"explanationKey":"e3"}
          ]
        }]}
        """

        val TASK_WITH_BASKET_ALL = """
        {"tasks":[{
          "id":"basket-all","topic":"PAYMENTS","titleKey":"i",
          "steps":[{"type":"SHELF","promptKey":"p","budget":35,"items":[
            {"id":"notebook","titleKey":"t1","price":10,"isMandatory":true},
            {"id":"pen","titleKey":"t2","price":12,"isMandatory":true}
          ]}],
          "outcomes":[
            {"id":"a","condition":{"type":"BASKET_CONTAINS_ALL","requiredItemIds":["notebook","pen"]},"explanationKey":"e1","correct":true},
            {"id":"b","condition":{"type":"OTHERWISE"},"explanationKey":"e2"}
          ]
        }]}
        """

        val TASK_WITH_ITEM_OFF_SHELF = """
        {"tasks":[{
          "id":"ghost","topic":"PAYMENTS","titleKey":"i",
          "steps":[{"type":"SHELF","promptKey":"p","budget":35,"items":[
            {"id":"notebook","titleKey":"t1","price":10,"isMandatory":true}
          ]}],
          "outcomes":[
            {"id":"a","condition":{"type":"BASKET_CONTAINS","itemId":"ruler"},"explanationKey":"e1","correct":true},
            {"id":"b","condition":{"type":"OTHERWISE"},"explanationKey":"e2"}
          ]
        }]}
        """

        val TASK_WITH_BAD_TOPIC = """
        {"tasks":[{
          "id":"bad-topic","topic":"SHOPPING","titleKey":"i",
          "steps":[{"type":"DISTRIBUTE","promptKey":"p","budget":40}],
          "outcomes":[
            {"id":"a","condition":{"type":"SAVED_AT_LEAST","amount":10},"reward":1,"explanationKey":"e1","correct":true},
            {"id":"b","condition":{"type":"OTHERWISE"},"reward":0,"explanationKey":"e2"}
          ]
        }]}
        """
    }
}
