package ru.finnypet.app.domain.model

enum class PeriodStatus {
    PLANNING,
    RUNNING,
    CLOSED,
}

data class GamePeriod(
    val id: Long,
    val profileId: ProfileId,
    val number: Int,
    val income: Coins,
    val startBalance: Coins,
    val status: PeriodStatus,
    val closedAt: Long? = null,
) {

    init {
        require(number >= 1) { "Номера периодов начинаются с единицы, получен: $number" }
        require((status == PeriodStatus.CLOSED) == (closedAt != null)) {
            "Время закрытия есть только у закрытого периода: статус $status, closedAt $closedAt"
        }
    }

    val available: Coins get() = startBalance + income
}
