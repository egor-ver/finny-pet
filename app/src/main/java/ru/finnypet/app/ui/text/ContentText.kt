package ru.finnypet.app.ui.text

import ru.finnypet.app.domain.model.Explanation

/**
 * Текст из контент-пака по ключу.
 *
 * Нет текста — показывается сам ключ. Так дыра в контенте видна на экране
 * и чинится в `explanations.json`, а не прячется за пустой строкой.
 */
fun Map<String, String>.textOf(key: String): String {
    val text = this[key] ?: return key
    return FIXED_NUMBER.replace(text) { numberWithWord(it.groupValues[1], it.groupValues[2]) }
}

/**
 * Число, записанное прямо в тексте истории: «{coins:40}», «{days:2}». Форма
 * слова выбирается тем же правилом, что и для чисел из домена, — иначе
 * «монет» и «дня» в текстах заданий согласовывались бы вручную и с ошибками.
 * Формы — именительного падежа, как у `{coins:balance}`: где падеж другой
 * («за 21 монету», «из 22 монет»), число в контенте пишется без слова.
 */
private val FIXED_NUMBER = Regex("""\{(coins|days):(\d+)\}""")

/**
 * Текст объяснения с подставленными числами.
 *
 * В контент-паке места для чисел записаны в фигурных скобках: «осталось
 * {coins:balance}». Домен отдаёт ключ и аргументы, склеивает их этот слой —
 * учебный текст правится без кода (ТЗ 3.2).
 *
 * Аргумент без места в тексте не мешает, место без аргумента остаётся
 * скобками: это тоже ошибка контента, и её должно быть видно.
 */
fun Map<String, String>.textOf(explanation: Explanation): String =
    explanation.args.entries.fold(textOf(explanation.key)) { text, (name, value) ->
        text.replace("{coins:$name}", numberWithWord("coins", value)).replace("{$name}", value)
    }

/**
 * «{coins:balance}» — число со словом в нужной форме: «81 монета», а не
 * «81 монет». Формы слова — в контент-паке (`word.coins.*`, `word.days.*`),
 * выбор формы — по русскому правилу ([wordFormOf]). Между числом и словом
 * неразрывный пробел: иначе «монет.» уезжало одно на следующую строку
 * облачка (ревью F4-fix).
 */
private fun Map<String, String>.numberWithWord(word: String, value: String): String {
    val amount = value.toIntOrNull() ?: return value
    return "$value$NBSP${textOf("word.$word.${wordFormOf(amount).name}")}"
}

private const val NBSP = '\u00A0'

