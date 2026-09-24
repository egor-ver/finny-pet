package ru.finnypet.app.domain.economy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.finnypet.app.domain.model.Change
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.ItemId
import ru.finnypet.app.domain.model.PetEffect
import ru.finnypet.app.domain.model.PetMood
import ru.finnypet.app.domain.model.PetState
import ru.finnypet.app.domain.model.PetStatKind
import ru.finnypet.app.domain.model.RecoveryOption
import ru.finnypet.app.domain.model.ShopItem
import ru.finnypet.app.domain.model.SpendCategory
import ru.finnypet.app.domain.model.Stat
import ru.finnypet.app.domain.model.totalPrice

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
            PlanFactLine(SpendCategory.MANDATORY, Coins(40), if (mandatoryOk) Coins(40) else Coins(50)),
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

    /** Итог дня решают потребности, а не план: план выполнен, а сова голодна. */
    @Test
    fun `незакрытые потребности объясняются и предлагают поправить план`() {
        val hungry = fed.copy(satiety = Stat(balance.needThreshold - 1))
        val result = engine.onPeriodClosed(hungry, report())
        assertEquals("pet.missed_mandatory", result.explanation.key)
        assertEquals(RecoveryOption.ADJUST_NEXT_PLAN, result.explanation.nextStep)
    }

    @Test
    fun `выполненный план объясняется своим ключом`() {
        assertEquals("pet.plan_followed", engine.onPeriodClosed(fed, report()).explanation.key)
    }

    @Test
    fun `обычный период объясняется нейтрально и без подсказки`() {
        val result = engine.onPeriodClosed(fed, report(optionalOk = false))
        assertEquals("pet.period_closed", result.explanation.key)
        assertNull(result.explanation.nextStep)
    }

    private fun item(id: String, price: Int, stat: PetStatKind, delta: Int, category: SpendCategory = SpendCategory.MANDATORY) =
        ShopItem(ItemId(id), "shop.$id", Coins(price), category, listOf(PetEffect(stat, delta)))

    /** Нужное из shop.json: цены и влияние те же, что в разделе 4 плана. */
    private val shop = listOf(
        item("porridge", 14, PetStatKind.SATIETY, 25),
        item("water", 8, PetStatKind.SATIETY, 15),
        item("brush", 18, PetStatKind.CARE, 25),
        item("vitamins", 15, PetStatKind.CARE, 20),
        item("ball", 24, PetStatKind.MOOD, 25, SpendCategory.OPTIONAL),
    )

    private fun pet(satiety: Int, care: Int, mood: Int = 90) =
        PetState(mood = Stat(mood), satiety = Stat(satiety), care = Stat(care))

    @Test
    fun `потребность — сытость или уход ниже порога`() {
        val threshold = balance.needThreshold
        assertEquals(listOf(PetStatKind.SATIETY), engine.needsOf(pet(satiety = threshold - 1, care = threshold)))
    }

    @Test
    fun `грустная сова без голода потребностей не имеет`() {
        assertTrue(engine.needsOf(pet(satiety = 90, care = 90, mood = 10)).isEmpty())
    }

    /** Раздел 4 плана, утро дня 3: еда 30 → каша и вода 22, уход 55 → витамины 15. */
    @Test
    fun `цена закрытия потребностей из эталонного сценария — 37`() {
        val cover = engine.cheapestCover(pet(satiety = 30, care = 55), shop)!!
        assertEquals(Coins(37), cover.totalPrice())
        assertEquals(listOf("porridge", "vitamins", "water"), cover.map { it.id.value }.sorted())
    }

    /** Раздел 8 плана: «Финни нужно не меньше 37: еда 22, уход 15». */
    @Test
    fun `цена каждой потребности отдельно — еда 22, уход 15`() {
        assertEquals(
            mapOf(PetStatKind.SATIETY to Coins(22), PetStatKind.CARE to Coins(15)),
            engine.coverByNeed(pet(satiety = 30, care = 55), shop),
        )
    }

    @Test
    fun `цена по потребностям — null, если одну нечем закрыть`() {
        val noCare = shop.filter { it.effects.none { effect -> effect.stat == PetStatKind.CARE } }
        assertNull(engine.coverByNeed(pet(satiety = 30, care = 50), noCare))
    }

    /** R12: каша при еде 30 закрывает потребность — «нужно сейчас». */
    @Test
    fun `нужное, которое поднимает показатель ниже порога, нужно сейчас`() {
        val porridge = shop.single { it.id.value == "porridge" }
        assertTrue(engine.neededNow(pet(satiety = 30, care = 90), porridge))
    }

    @Test
    fun `нужное для сытого показателя сейчас не нужно`() {
        val vitamins = shop.single { it.id.value == "vitamins" }
        assertFalse(engine.neededNow(pet(satiety = 30, care = 90), vitamins))
    }

    @Test
    fun `желаемое нужно сейчас не бывает`() {
        val candy = item("candy", 1, PetStatKind.SATIETY, 50, SpendCategory.OPTIONAL)
        assertFalse(engine.neededNow(pet(satiety = 30, care = 30), candy))
    }

    @Test
    fun `две воды дешевле каши с водой`() {
        val cover = engine.cheapestCover(pet(satiety = 40, care = 90), shop)!!
        assertEquals(listOf("water", "water"), cover.map { it.id.value })
    }

    @Test
    fun `без потребностей набор пустой`() {
        assertEquals(emptyList<ShopItem>(), engine.cheapestCover(pet(satiety = 90, care = 90), shop))
    }

    @Test
    fun `желаемое потребности не закрывает`() {
        val cheapFood = item("candy", 1, PetStatKind.SATIETY, 50, SpendCategory.OPTIONAL)
        val cover = engine.cheapestCover(pet(satiety = 60, care = 90), shop + cheapFood)!!
        assertEquals(listOf("water"), cover.map { it.id.value })
    }

    @Test
    fun `если потребность нечем закрыть — набора нет`() {
        val noCare = shop.filter { it.effects.none { effect -> effect.stat == PetStatKind.CARE } }
        assertNull(engine.cheapestCover(pet(satiety = 90, care = 50), noCare))
    }

    /** R11, раздел 4 плана: утро дня 3, еда 30 — сова грустит от голода. */
    @Test
    fun `после пропущенного дня сова грустит от голода`() {
        val hungry = pet(satiety = 30, care = 55)

        assertEquals(PetMood.SAD, engine.moodOf(hungry))
        assertEquals(PetStatKind.SATIETY, engine.sadAbout(hungry))
    }

    @Test
    fun `голод называется раньше растрёпанных перьев`() {
        assertEquals(PetStatKind.SATIETY, engine.sadAbout(pet(satiety = 30, care = 30)))
        assertEquals(PetStatKind.CARE, engine.sadAbout(pet(satiety = 90, care = 30)))
    }

    /** Утро с потребностями — повод позаботиться, а не грустить (ТЗ 3.5). */
    @Test
    fun `утро с потребностями — сова спокойна`() {
        assertEquals(PetMood.CALM, engine.moodOf(pet(satiety = 55, care = 70)))
        assertNull(engine.sadAbout(pet(satiety = 55, care = 70)))
    }

    @Test
    fun `сытая и довольная сова радуется`() {
        assertEquals(PetMood.HAPPY, engine.moodOf(pet(satiety = 90, care = 90, mood = balance.needThreshold)))
    }

    @Test
    fun `сытая, но скучающая сова спокойна`() {
        assertEquals(PetMood.CALM, engine.moodOf(pet(satiety = 90, care = 90, mood = balance.needThreshold - 1)))
    }
}
