package ru.finnypet.app.ui.text

import ru.finnypet.app.domain.model.Explanation

/**
 * Текст из контент-пака по ключу.
 *
 * Нет текста — показывается сам ключ. Так дыра в контенте видна на экране
 * и чинится в `explanations.json`, а не прячется за пустой строкой.
 */
fun Map<String, String>.textOf(key: String): String = this[key] ?: key

/**
 * Текст объяснения с подставленными числами.
 *
 * В контент-паке места для чисел записаны в фигурных скобках: «осталось
 * {balance} монет». Домен отдаёт ключ и аргументы, склеивает их этот слой —
 * учебный текст правится без кода (ТЗ 3.2).
 *
 * Аргумент без места в тексте не мешает, место без аргумента остаётся
 * скобками: это тоже ошибка контента, и её должно быть видно.
 */
fun Map<String, String>.textOf(explanation: Explanation): String =
    explanation.args.entries.fold(textOf(explanation.key)) { text, (name, value) ->
        text.replace("{$name}", value)
    }
