package ru.finnypet.app.data.content

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import ru.finnypet.app.domain.content.ContentPack
import ru.finnypet.app.domain.repository.ContentRepository
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Читает контент-пак из ассетов и разбирает один раз за запуск.
 *
 * Отсутствующий файл — ошибка сборки, а не ситуация, из которой игра может
 * выйти: без товаров и заданий играть не во что. Поэтому загрузка падает
 * с именем файла, а не подставляет пустой список.
 *
 * Версия в пути нужна, чтобы обновление контента было заменой папки,
 * а не правкой кода (ТЗ 2.5.14).
 */
@Singleton
class AssetContentRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val parser: ContentParser,
) : ContentRepository {

    private val pack: ContentPack by lazy {
        parser.parse(
            RawContent(
                balance = read("balance.json"),
                pets = read("pets.json"),
                shop = read("shop.json"),
                goals = read("goals.json"),
                tasks = read("tasks.json"),
                glossary = read("glossary.json"),
                explanations = read("explanations.json"),
            )
        )
    }

    override fun pack(): ContentPack = pack

    private fun read(name: String): String = try {
        context.assets.open("$FOLDER/$name").bufferedReader().use { it.readText() }
    } catch (error: IOException) {
        throw ContentParseException("Не найден файл контент-пака $FOLDER/$name в ассетах", error)
    }

    private companion object {
        const val FOLDER = "content/v1"
    }
}
