package ru.finnypet.app.domain.economy

import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.GrowthStage
import ru.finnypet.app.domain.model.Stat

data class GameBalance(
    val startingBalance: Coins,
    val periodIncome: Coins,
    val taskReward: Coins,
    val initialStat: Int,
    /**
     * На сколько падают показатели за ночь при закрытии игрового дня. Ночь
     * создаёт потребности на завтра: без неё сытая сова оставалась бы сытой
     * навсегда и нужное теряло бы смысл (AD-2).
     */
    val nightDropSatiety: Int,
    val nightDropCare: Int,
    val nightDropMood: Int,
    /** Ниже этого ночь показатель не опускает: без «голодания» (ТЗ 3.5). */
    val statFloor: Int,
    /**
     * Сытость или уход ниже порога — потребность дня (R2). Нужное считается
     * выполненным, когда потребностей нет, а не когда потрачен план: иначе
     * игра учила бы «потрать всё, что запланировал» (AD-3).
     */
    val needThreshold: Int,
    /** Сытость или уход ниже этого — сова грустит; тогда задание дня — разбор ошибки (AD-7). */
    val sadThreshold: Int,
    /**
     * На сколько план на нужное может превышать цену потребностей, прежде
     * чем сова скажет «мне столько не нужно». Без запаса сова ворчала бы на
     * план, где нужное взято с копеечным округлением; без фразы лазейка
     * «всё в нужное» оставалась бы незамеченной (раздел 3 плана).
     */
    val needSlack: Int,
    /** Очки за звёзды дня (AD-3): «сыт», «по плану», «отложил». */
    val growthForMandatoryCovered: Int,
    val growthForPlanFollowed: Int,
    val growthForSavingsKept: Int,
    val growthThresholds: List<Int>,
    val unexpectedExpenseChance: Int,
    val carryOverUnspent: Boolean,
    /**
     * Сколько заданий в игровой день приносят монеты. Остальные играются
     * ради объяснения и питомца. Из дохода на желаемое не остаётся ничего:
     * доход уходит на нужное и копилку, и задание дня — те самые «деньги на
     * игрушку». Без лимита шесть заданий давали бы вдвое больше дохода и
     * обесценивали план (ТЗ 2.5.5); «один раз за задание навсегда» убивал бы
     * стимул и путь «выполнить задание» из отказа в магазине (ТЗ 2.5.9).
     */
    val rewardedTasksPerPeriod: Int = 1,
    /**
     * Сколько монет взрослый может начислить ребёнку за дело вне игры
     * (ТЗ 2.5.12). Правило начисления команда задаёт сама; у нас это раз в
     * игровой день, иначе бонус обесценил бы план: доход дня известен
     * заранее, а бонус без предела делает его неважным.
     */
    val parentBonus: Coins,
) {

    init {
        require(growthThresholds.size == GrowthStage.entries.size) {
            "Порогов должно быть столько же, сколько стадий развития " +
                "(${GrowthStage.entries.size}), задано: ${growthThresholds.size}"
        }
        require(growthThresholds.first() == 0) {
            "Первый порог стадии должен быть нулевым, задан: ${growthThresholds.first()}"
        }
        require(growthThresholds.zipWithNext().all { (a, b) -> a < b }) {
            "Пороги стадий должны строго возрастать, заданы: $growthThresholds"
        }
        require(initialStat in Stat.RANGE) {
            "Стартовый показатель питомца задаётся в пределах ${Stat.RANGE}, задан: $initialStat"
        }
        require(nightDropSatiety >= 0 && nightDropCare >= 0 && nightDropMood >= 0) {
            "Изменения показателей задаются неотрицательными величинами"
        }
        require(statFloor in Stat.RANGE) {
            "Нижний предел показателя задаётся в пределах ${Stat.RANGE}, задан: $statFloor"
        }
        require(needThreshold in Stat.RANGE) {
            "Порог потребности задаётся в пределах ${Stat.RANGE}, задан: $needThreshold"
        }
        require(sadThreshold in Stat.RANGE) {
            "Порог грусти задаётся в пределах ${Stat.RANGE}, задан: $sadThreshold"
        }
        require(needSlack >= 0) {
            "Запас на нужное не может быть отрицательным, задан: $needSlack"
        }
        require(growthForMandatoryCovered >= 0 && growthForPlanFollowed >= 0 && growthForSavingsKept >= 0) {
            "Очки роста не могут быть отрицательными"
        }
        require(unexpectedExpenseChance in 0..100) {
            "Шанс непредвиденных расходов задаётся в процентах, задан: $unexpectedExpenseChance"
        }
        require(rewardedTasksPerPeriod >= 0) {
            "Число заданий с наградой в день не может быть отрицательным, задано: $rewardedTasksPerPeriod"
        }
        require(parentBonus > Coins.ZERO) {
            "Бонус родителя должен быть больше нуля, задан: ${parentBonus.amount}"
        }
    }

    val maxGrowthPerPeriod: Int
        get() = growthForMandatoryCovered + growthForPlanFollowed + growthForSavingsKept

    companion object {

        val PLACEHOLDER = GameBalance(
            startingBalance = Coins(20),
            periodIncome = Coins(60),
            taskReward = Coins(15),
            initialStat = 70,
            nightDropSatiety = 25,
            nightDropCare = 15,
            nightDropMood = 10,
            statFloor = 30,
            needThreshold = 70,
            sadThreshold = 40,
            needSlack = 9,
            growthForMandatoryCovered = 2,
            growthForPlanFollowed = 2,
            growthForSavingsKept = 1,
            growthThresholds = listOf(0, 10, 25),
            unexpectedExpenseChance = 15,
            carryOverUnspent = true,
            parentBonus = Coins(10),
        )
    }
}
