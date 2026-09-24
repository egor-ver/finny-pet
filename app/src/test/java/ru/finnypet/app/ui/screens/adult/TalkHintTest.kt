package ru.finnypet.app.ui.screens.adult

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.finnypet.app.data.content.ContentParser
import ru.finnypet.app.data.content.RealContent
import ru.finnypet.app.domain.economy.PetStateEngine
import ru.finnypet.app.domain.model.PetState
import ru.finnypet.app.domain.model.PetStatKind
import ru.finnypet.app.domain.model.Stat

/** «О чём поговорить сегодня»: две подсказки, голод — по порогу потребности (70). */
class TalkHintTest {

    private val pack = ContentParser().parse(RealContent.raw())
    private val engine = PetStateEngine(pack.balance)

    @Test
    fun `сытость ниже порога потребности — про еду, хотя сова ещё не грустит`() {
        val hint = talkHint(engine.needsOf(PetState(mood = Stat(80), satiety = Stat(60), care = Stat(80))))
        assertEquals("adult.talk.hungry", hint.key)
    }

    /** Уход ниже порога — не голод: про уход подсказки нет, остаётся «всё по плану». */
    @Test
    fun `сыта — всё по плану`() {
        assertEquals("adult.talk.plan", talkHint(engine.needsOf(PetState(Stat(80), Stat(80), Stat(80)))).key)
        assertEquals("adult.talk.plan", talkHint(engine.needsOf(PetState(Stat(80), Stat(80), Stat(30)))).key)
    }

    @Test
    fun `у обеих подсказок есть текст`() {
        val keys = listOf(listOf(PetStatKind.SATIETY), emptyList()).map { talkHint(it).key }
        val missing = keys.filterNot(pack.texts::containsKey)
        assertTrue("Нет текста в explanations.json для ключей: $missing", missing.isEmpty())
    }
}
