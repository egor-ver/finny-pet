package ru.finnypet.app.domain.economy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.finnypet.app.domain.model.Change
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.PetEffect
import ru.finnypet.app.domain.model.PetState
import ru.finnypet.app.domain.model.PetStatKind
import ru.finnypet.app.domain.model.RecoveryOption
import ru.finnypet.app.domain.model.SpendCategory
import ru.finnypet.app.domain.model.Stat

class PetStateEngineTest {

    private val balance = GameBalance.PLACEHOLDER
    private val engine = PetStateEngine(balance)

    private val state = PetState(mood = Stat(50), satiety = Stat(50), care = Stat(50))

    private fun report(
        mandatoryOk: Boolean = true,
        optionalOk: Boolean = true,
        savingsOk: Boolean = true,
    ): PlanFactReport {
        val lines = listOf(
            PlanFactLine(SpendCategory.MANDATORY, Coins(40), if (mandatoryOk) Coins(40) else Coins(30)),
            PlanFactLine(SpendCategory.OPTIONAL, Coins(20), if (optionalOk) Coins(20) else Coins(30)),
            PlanFactLine(SpendCategory.SAVINGS, Coins(10), if (savingsOk) Coins(10) else Coins(5)),
        )
        return PlanFactReport(
            lines = lines,
            planTotal = Coins(70),
            factTotal = lines.fold(Coins.ZERO) { acc, line -> acc + line.actual },
        )
    }

    @Test
    fun `положительный эффект поднимает показатель`() {
        val result = engine.apply(state, listOf(PetEffect(PetStatKind.SATIETY, 15)))
        assertEquals(Stat(65), result.value.satiety)
    }

    @Test
    fun `отрицательный эффект опускает показатель`() {
        val result = engine.apply(state, listOf(PetEffect(PetStatKind.CARE, -20)))
        assertEquals(Stat(30), result.value.care)
    }

    @Test
    fun `эффект упирается в потолок а не переполняет шкалу`() {
        val full = PetState.uniform(Stat(95))
        val result = engine.apply(full, listOf(PetEffect(PetStatKind.MOOD, 20)))
        assertEquals(Stat.MAX, result.value.mood)
    }

    @Test
    fun `эффект сообщает изменение показателя`() {
        val result = engine.apply(state, listOf(PetEffect(PetStatKind.SATIETY, 15)))
        assertEquals(
            listOf(Change.PetStat(PetStatKind.SATIETY, from = Stat(50), to = Stat(65))),
            result.changes,
        )
    }

    @Test
    fun `несколько эффектов дают несколько изменений`() {
        val result = engine.apply(
            state,
            listOf(PetEffect(PetStatKind.MOOD, 10), PetEffect(PetStatKind.CARE, 5)),
        )
        assertEquals(2, result.changes.size)
    }

    @Test
    fun `пустой список эффектов не даёт изменений`() {
        assertTrue(engine.apply(state, emptyList()).changes.isEmpty())
    }

    @Test
    fun `эффект упёршийся в потолок не считается изменением`() {
        val full = PetState.uniform(Stat.MAX)
        assertTrue(engine.apply(full, listOf(PetEffect(PetStatKind.MOOD, 10))).changes.isEmpty())
    }

    /** Сытая сова: ночь не упирается в предел, видно чистое падение. */
    private val fed = PetState(mood = Stat(90), satiety = Stat(90), care = Stat(90))

    @Test
    fun `ночь снижает сытость уход и настроение на свои величины`() {
        val result = engine.onPeriodClosed(fed, report(optionalOk = false))
        assertEquals(Stat(90 - balance.nightDropSatiety), result.value.satiety)
        assertEquals(Stat(90 - balance.nightDropCare), result.value.care)
        assertEquals(Stat(90 - balance.nightDropMood), result.value.mood)
    }

    /** Штраф заменён ночью: сверх неё за незакрытое нужное ничего не снимается. */
    @Test
    fun `незакрытые обязательные расходы не снижают показатели сверх ночи`() {
        val missed = engine.onPeriodClosed(fed, report(mandatoryOk = false, optionalOk = false))
        val ordinary = engine.onPeriodClosed(fed, report(optionalOk = false))
        assertEquals(ordinary.value, missed.value)
    }

    /** Раздел 4 плана, ночь после дня 2: еда 55 → 30, уход 70 → 55. */
    @Test
    fun `ночь не опускает показатель ниже предела`() {
        val evening = PetState(mood = Stat(90), satiety = Stat(55), care = Stat(70))
        val result = engine.onPeriodClosed(evening, report(optionalOk = false))
        assertEquals(Stat(balance.statFloor), result.value.satiety)
        assertEquals(Stat(55), result.value.care)
    }

    @Test
    fun `показатель уже ниже предела ночь не трогает`() {
        val low = PetState(mood = Stat(90), satiety = Stat(20), care = Stat(90))
        val result = engine.onPeriodClosed(low, report(optionalOk = false))
        assertEquals(Stat(20), result.value.satiety)
    }

    @Test
    fun `выполненный план поднимает настроение до ночи`() {
        val result = engine.onPeriodClosed(state, report())
        assertEquals(Stat(50 + balance.moodBonusPlanFollowed - balance.nightDropMood), result.value.mood)
    }

    /** Бонус упирается в потолок раньше ночи, иначе сова с полной радостью её бы не теряла. */
    @Test
    fun `бонус за план упирается в потолок до ночи`() {
        val happy = fed.copy(mood = Stat.MAX)
        val result = engine.onPeriodClosed(happy, report())
        assertEquals(Stat(Stat.MAX.value - balance.nightDropMood), result.value.mood)
    }

    @Test
    fun `превышение необязательных меняет показатели только ночью`() {
        val result = engine.onPeriodClosed(fed, report(optionalOk = false))
        assertEquals(
            PetState(
                mood = Stat(90 - balance.nightDropMood),
                satiety = Stat(90 - balance.nightDropSatiety),
                care = Stat(90 - balance.nightDropCare),
            ),
            result.value,
        )
    }

    @Test
    fun `ночь сообщает изменения показателей`() {
        val result = engine.onPeriodClosed(fed, report(optionalOk = false))
        assertEquals(PetStatKind.entries.toSet(), result.changes.map { (it as Change.PetStat).kind }.toSet())
    }

    @Test
    fun `промах по обязательным объясняется и предлагает поправить план`() {
        val result = engine.onPeriodClosed(state, report(mandatoryOk = false))
        assertEquals("pet.missed_mandatory", result.explanation.key)
        assertEquals(RecoveryOption.ADJUST_NEXT_PLAN, result.explanation.nextStep)
    }

    @Test
    fun `выполненный план объясняется своим ключом`() {
        assertEquals("pet.plan_followed", engine.onPeriodClosed(state, report()).explanation.key)
    }

    @Test
    fun `обычный период объясняется нейтрально и без подсказки`() {
        val result = engine.onPeriodClosed(state, report(optionalOk = false))
        assertEquals("pet.period_closed", result.explanation.key)
        assertNull(result.explanation.nextStep)
    }
}
