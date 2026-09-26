package ru.finnypet.app.ui.screens.savings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.finnypet.app.domain.content.ContentOption
import ru.finnypet.app.domain.content.PetColor
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.GoalId
import ru.finnypet.app.domain.model.GrowthStage
import ru.finnypet.app.domain.model.PetMood
import ru.finnypet.app.ui.components.OwlLook

/**
 * Цели без тупика (L5, Б7): пока есть некупленная цель, купленную выбрать
 * нельзя — но когда куплены все, единственный способ копить дальше — это
 * выбрать одну из них снова.
 */
class SavingsStateTest {

    private fun goal(id: String, bought: Boolean, active: Boolean = false) = GoalView(
        id = GoalId(id),
        title = id,
        price = Coins(10),
        saved = Coins.ZERO,
        isActive = active,
        icon = "🎁",
        isBought = bought,
    )

    private val owl = OwlLook(
        colors = PetColor(
            option = ContentOption("cream", "pet.color.cream"),
            body = 0xFFF0D4AE,
            wing = 0xFFD9B488,
            face = 0xFFFFF5E6,
            ring = 0xFFC8996A,
        ),
        stage = GrowthStage.CUB,
        mood = PetMood.CALM,
        accessoryId = null,
        description = "Сова спокойна",
    )

    private fun state(goals: List<GoalView>) = SavingsState.Ready(
        goals = goals,
        periodsToGoal = null,
        usualDeposit = Coins.ZERO,
        balance = Coins.ZERO,
        canOperate = true,
        owl = owl,
    )

    @Test
    fun `купленную цель нельзя выбрать, пока есть некупленная`() {
        val ready = state(listOf(goal("book", bought = true), goal("bike", bought = false)))

        assertFalse(ready.canChoose(ready.goals[0]))
        assertTrue(ready.canChoose(ready.goals[1]))
    }

    @Test
    fun `когда куплены все цели, любую можно выбрать снова`() {
        val ready = state(listOf(goal("book", bought = true), goal("bike", bought = true)))

        ready.goals.forEach { assertTrue(ready.canChoose(it)) }
    }
}
