package ru.finnypet.app.domain.economy

import ru.finnypet.app.domain.model.Change
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.Explanation
import ru.finnypet.app.domain.model.GameResult
import ru.finnypet.app.domain.model.PetEffect
import ru.finnypet.app.domain.model.PetMood
import ru.finnypet.app.domain.model.PetState
import ru.finnypet.app.domain.model.PetStatKind
import ru.finnypet.app.domain.model.RecoveryOption
import ru.finnypet.app.domain.model.ShopItem
import ru.finnypet.app.domain.model.SpendCategory
import ru.finnypet.app.domain.model.Stat
import ru.finnypet.app.domain.model.totalPrice

class PetStateEngine(private val balance: GameBalance) {

    fun apply(state: PetState, effects: List<PetEffect>): GameResult<PetState> {
        var next = state
        effects.forEach { effect ->
            next = next.with(effect.stat, next.statFor(effect.stat) + effect.delta)
        }
        return GameResult(
            value = next,
            explanation = Explanation(key = KEY_CHANGED),
            changes = changesBetween(state, next),
        )
    }

    /**
     * Настроение (R11): грусть — только после пропущенного дня, когда сытость
     * или уход ниже порога грусти; радость — когда потребностей нет и радость
     * не ниже порога потребности. Иначе спокойствие: утро с потребностями —
     * повод позаботиться, а не грустить (ТЗ 3.5).
     */
    fun moodOf(state: PetState): PetMood = when {
        sadAbout(state) != null -> PetMood.SAD
        needsOf(state).isEmpty() && state.mood >= Stat(balance.needThreshold) -> PetMood.HAPPY
        else -> PetMood.CALM
    }

    /** Из-за чего сова грустит — первым голод, как и в разборе ошибки; `null` — не грустит. */
    fun sadAbout(state: PetState): PetStatKind? =
        NEEDS.firstOrNull { state.statFor(it) < Stat(balance.sadThreshold) }

    /** Чего сове не хватает прямо сейчас (R2): сытость или уход ниже порога. */
    fun needsOf(state: PetState): List<PetStatKind> =
        NEEDS.filter { state.statFor(it) < Stat(balance.needThreshold) }

    /**
     * Самый дешёвый набор нужного, который закрывает все потребности (R2).
     * Товар можно взять несколько раз: две воды бывают дешевле каши. Пустой
     * набор — потребностей нет; null — какую-то из них в магазине нечем закрыть.
     */
    fun cheapestCover(state: PetState, shop: List<ShopItem>): List<ShopItem>? =
        needsOf(state).flatMap { kind ->
            cheapestFor(kind, balance.needThreshold - state.statFor(kind).value, shop) ?: return null
        }

    /**
     * Цена закрытия каждой потребности отдельно — для пояснения в плане:
     * «еда 22, уход 15». `null` — какую-то из них в магазине нечем закрыть.
     */
    fun coverByNeed(state: PetState, shop: List<ShopItem>): Map<PetStatKind, Coins>? =
        needsOf(state).associateWith { kind ->
            cheapestFor(kind, balance.needThreshold - state.statFor(kind).value, shop)?.totalPrice() ?: return null
        }

    /**
     * Каждая потребность считается отдельно: нужное в магазине поднимает один
     * показатель. best[d] — самый дешёвый набор, дающий не меньше d.
     */
    private fun cheapestFor(kind: PetStatKind, deficit: Int, shop: List<ShopItem>): List<ShopItem>? {
        val gains = shop
            .filter { it.category == SpendCategory.MANDATORY }
            .map { item -> item to item.effects.filter { it.stat == kind }.sumOf { it.delta } }
            .filter { (_, gain) -> gain > 0 }
        val best = arrayOfNulls<List<ShopItem>>(deficit + 1)
        best[0] = emptyList()
        for (d in 1..deficit) {
            best[d] = gains
                .mapNotNull { (item, gain) -> best[maxOf(0, d - gain)]?.plus(item) }
                .minByOrNull { it.totalPrice().amount }
        }
        return best[deficit]
    }

    /**
     * Сначала итоги дня, потом ночь (AD-2): бонус за план не отменяет
     * завтрашних потребностей, а упирается в тот же потолок шкалы.
     */
    fun onPeriodClosed(state: PetState, report: PlanFactReport): GameResult<PetState> {
        var next = state
        if (report.planFollowed) {
            next = next.with(PetStatKind.MOOD, next.mood + balance.moodBonusPlanFollowed)
        }
        next = night(next)
        return GameResult(
            value = next,
            explanation = explanationFor(needsMet = needsOf(state).isEmpty(), report = report),
            changes = changesBetween(state, next),
        )
    }

    private fun explanationFor(needsMet: Boolean, report: PlanFactReport): Explanation = when {
        !needsMet -> Explanation(
            key = KEY_MISSED_MANDATORY,
            nextStep = RecoveryOption.ADJUST_NEXT_PLAN,
        )

        report.planFollowed -> Explanation(key = KEY_PLAN_FOLLOWED)

        else -> Explanation(key = KEY_PERIOD_CLOSED)
    }

    private fun night(state: PetState): PetState =
        PetStatKind.entries.fold(state) { next, kind ->
            val stat = next.statFor(kind)
            // Показатель ниже предела ночь не трогает: иначе она подняла бы его до предела.
            val floor = minOf(stat, Stat(balance.statFloor))
            next.with(kind, maxOf(stat - nightDropOf(kind), floor))
        }

    private fun nightDropOf(kind: PetStatKind): Int = when (kind) {
        PetStatKind.SATIETY -> balance.nightDropSatiety
        PetStatKind.CARE -> balance.nightDropCare
        PetStatKind.MOOD -> balance.nightDropMood
    }

    private fun changesBetween(from: PetState, to: PetState): List<Change> =
        PetStatKind.entries.mapNotNull { kind ->
            val before = from.statFor(kind)
            val after = to.statFor(kind)
            if (before == after) null else Change.PetStat(kind = kind, from = before, to = after)
        }

    private companion object {
        /** Радость не потребность: её растит желаемое, а желаемое необязательно. */
        val NEEDS = listOf(PetStatKind.SATIETY, PetStatKind.CARE)

        const val KEY_CHANGED = "pet.state_changed"
        const val KEY_MISSED_MANDATORY = "pet.missed_mandatory"
        const val KEY_PLAN_FOLLOWED = "pet.plan_followed"
        const val KEY_PERIOD_CLOSED = "pet.period_closed"
    }
}
