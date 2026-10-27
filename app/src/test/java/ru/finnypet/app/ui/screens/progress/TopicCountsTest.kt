package ru.finnypet.app.ui.screens.progress

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.finnypet.app.data.content.ContentParser
import ru.finnypet.app.data.content.RealContent
import ru.finnypet.app.domain.model.TaskTopic
import ru.finnypet.app.domain.usecase.TaskSchedule

/**
 * Чипы тем на свёрнутой строке заданий «Моего прогресса»
 * — на настоящем контент-паке: шесть заданий, по два в каждой из трёх тем
 *, разбор в счёт не входит.
 */
class TopicCountsTest {

    private val listed = TaskSchedule.listed(ContentParser().parse(RealContent.raw()).tasks)

    @Test
    fun `ничего не пройдено — по 0 из 2 в каждой теме, всего 6`() {
        val counts = topicCounts(listed, passed = emptySet())

        assertEquals(TaskTopic.entries.map { TopicCount(it, passed = 0, total = 2) }, counts)
        assertEquals(6, counts.sumOf { it.total })
    }

    @Test
    fun `пройденное задание засчитано своей теме`() {
        val first = listed.first()

        val counts = topicCounts(listed, passed = setOf(first.id))

        assertEquals(1, counts.single { it.topic == first.topic }.passed)
        assertEquals(1, counts.sumOf { it.passed })
    }
}
