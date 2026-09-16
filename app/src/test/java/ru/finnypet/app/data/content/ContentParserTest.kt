package ru.finnypet.app.data.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.OutcomeCondition
import ru.finnypet.app.domain.model.SpendCategory
import ru.finnypet.app.domain.model.TaskStep
import java.io.File

/**
 * Разбор проверяется на настоящих файлах из ассетов, а не на выдуманных
 * строках: так тест заодно стережёт сам контент-пак — если продакт сломает
 * формат, это видно сразу, а не на устройстве.
 */
class ContentParserTest {

    private val parser = ContentParser()

    @Test
    fun `настоящий контент-пак разбирается`() {
        val pack = parser.parse(realContent())

        assertEquals(Coins(20), pack.balance.startingBalance)
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

    @Test
    fun `комбинации внешности считаются с учётом варианта без аксессуара`() {
        val pets = parser.parse(
            realContent(
                pets = """
                {
                  "bodies": [{"id":"owl","titleKey":"a"},{"id":"cat","titleKey":"b"}],
                  "colors": [{"id":"mint","titleKey":"c"},{"id":"rose","titleKey":"d"}],
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

    // --- Сообщения об ошибках: по ним продакт должен найти место в своём файле ---

    @Test
    fun `опечатка в имени поля называет файл`() {
        val error = parseFailure(goals = """{"goals":[{"id":"bike","titleKey":"g","cost":120}]}""")

        assertTrue("Сообщение не называет файл: ${error.message}", error.message!!.contains("goals.json"))
    }

    @Test
    fun `неизвестная категория товара перечисляет допустимые`() {
        val error = parseFailure(
            shop = """{"items":[{"id":"x","titleKey":"t","price":10,"category":"FOOD"}]}"""
        )

        val message = error.message.orEmpty()
        assertTrue("Не назван файл: $message", message.contains("shop.json"))
        assertTrue("Не назван товар: $message", message.contains("x"))
        assertTrue("Не перечислены допустимые: $message", message.contains("MANDATORY"))
    }

    @Test
    fun `товар с ценой меньше нуля называет товар`() {
        val error = parseFailure(
            shop = """{"items":[{"id":"cheap","titleKey":"t","price":-5,"category":"OPTIONAL"}]}"""
        )

        assertTrue("Не назван товар: ${error.message}", error.message!!.contains("cheap"))
    }

    @Test
    fun `товар в накоплениях отвергается`() {
        val error = parseFailure(
            shop = """{"items":[{"id":"x","titleKey":"t","price":10,"category":"SAVINGS"}]}"""
        )

        assertTrue("Не назван файл: ${error.message}", error.message!!.contains("shop.json"))
    }

    @Test
    fun `повторяющийся идентификатор товара отвергается`() {
        val error = parseFailure(
            shop = """
            {"items":[
              {"id":"same","titleKey":"a","price":10,"category":"MANDATORY"},
              {"id":"same","titleKey":"b","price":20,"category":"OPTIONAL"}
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
    ) = RawContent(
        balance = balance ?: asset("balance.json"),
        pets = pets ?: asset("pets.json"),
        shop = shop ?: asset("shop.json"),
        goals = goals ?: asset("goals.json"),
        tasks = tasks ?: asset("tasks.json"),
        glossary = asset("glossary.json"),
        explanations = asset("explanations.json"),
    )

    private fun asset(name: String): String {
        val file = File("src/main/assets/content/v1/$name")
        assertTrue("Не найден файл контент-пака: ${file.absolutePath}", file.exists())
        return file.readText()
    }

    private companion object {

        val TASK_WITH_ALL_STEPS = """
        {"tasks":[{
          "id":"all-steps","topic":"PAYMENTS","introKey":"i",
          "steps":[
            {"type":"CHOICE","promptKey":"p1","options":[{"id":"a","labelKey":"la"},{"id":"b","labelKey":"lb"}]},
            {"type":"DISTRIBUTE","promptKey":"p2","budget":40},
            {"type":"PICK_ITEMS","promptKey":"p3","itemIds":["food-porridge"],"budget":30}
          ],
          "outcomes":[
            {"id":"good","condition":{"type":"OPTION_CHOSEN","optionId":"a"},"reward":10,"explanationKey":"e1"},
            {"id":"other","condition":{"type":"OTHERWISE"},"reward":0,"explanationKey":"e2"}
          ]
        }]}
        """

        val TASK_WITH_ALL_CONDITIONS = """
        {"tasks":[{
          "id":"all-conditions","topic":"SAVING","introKey":"i",
          "steps":[
            {"type":"CHOICE","promptKey":"p1","options":[{"id":"x","labelKey":"lx"},{"id":"y","labelKey":"ly"}]},
            {"type":"DISTRIBUTE","promptKey":"p2","budget":40}
          ],
          "outcomes":[
            {"id":"a","condition":{"type":"OPTION_CHOSEN","optionId":"x"},"reward":1,"explanationKey":"e1"},
            {"id":"b","condition":{"type":"SAVED_AT_LEAST","amount":10},"reward":2,"explanationKey":"e2"},
            {"id":"c","condition":{"type":"SPENT_AT_MOST","amount":30},"reward":3,"explanationKey":"e3"},
            {"id":"d","condition":{"type":"OTHERWISE"},"reward":0,"explanationKey":"e4"}
          ]
        }]}
        """

        val TASK_WITHOUT_OTHERWISE = """
        {"tasks":[{
          "id":"no-fallback","topic":"PLANNING","introKey":"i",
          "steps":[{"type":"DISTRIBUTE","promptKey":"p","budget":40}],
          "outcomes":[
            {"id":"a","condition":{"type":"SAVED_AT_LEAST","amount":10},"reward":1,"explanationKey":"e1"},
            {"id":"b","condition":{"type":"SAVED_AT_LEAST","amount":20},"reward":2,"explanationKey":"e2"}
          ]
        }]}
        """


        val TASK_WITH_UNKNOWN_OPTION = """
        {"tasks":[{
          "id":"typo","topic":"PLANNING","introKey":"i",
          "steps":[{"type":"CHOICE","promptKey":"p","options":[{"id":"wait","labelKey":"l"},{"id":"buy","labelKey":"l2"}]}],
          "outcomes":[
            {"id":"a","condition":{"type":"OPTION_CHOSEN","optionId":"waite"},"reward":1,"explanationKey":"e1"},
            {"id":"b","condition":{"type":"OTHERWISE"},"reward":0,"explanationKey":"e2"}
          ]
        }]}
        """

        val TASK_WITH_UNKNOWN_ITEM = """
        {"tasks":[{
          "id":"basket","topic":"PAYMENTS","introKey":"i",
          "steps":[{"type":"PICK_ITEMS","promptKey":"p","itemIds":["no-such-item"],"budget":30}],
          "outcomes":[
            {"id":"a","condition":{"type":"SPENT_AT_MOST","amount":30},"reward":1,"explanationKey":"e1"},
            {"id":"b","condition":{"type":"OTHERWISE"},"reward":0,"explanationKey":"e2"}
          ]
        }]}
        """

        val TASK_WITHOUT_REWARD = """
        {"tasks":[{
          "id":"no-reward","topic":"SAVING","introKey":"i",
          "steps":[{"type":"DISTRIBUTE","promptKey":"p","budget":40}],
          "outcomes":[
            {"id":"saved","condition":{"type":"SAVED_AT_LEAST","amount":10},"explanationKey":"e1"},
            {"id":"other","condition":{"type":"OTHERWISE"},"reward":0,"explanationKey":"e2"}
          ]
        }]}
        """

        val TASK_WITH_BAD_TOPIC = """
        {"tasks":[{
          "id":"bad-topic","topic":"SHOPPING","introKey":"i",
          "steps":[{"type":"DISTRIBUTE","promptKey":"p","budget":40}],
          "outcomes":[
            {"id":"a","condition":{"type":"SAVED_AT_LEAST","amount":10},"reward":1,"explanationKey":"e1"},
            {"id":"b","condition":{"type":"OTHERWISE"},"reward":0,"explanationKey":"e2"}
          ]
        }]}
        """
    }
}
