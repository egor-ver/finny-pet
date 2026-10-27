package ru.finnypet.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GamePeriodTest {

    private fun period(
        number: Int = 1,
        status: PeriodStatus = PeriodStatus.PLANNING,
        closedAt: Long? = null,
        startBalance: Coins = Coins(40),
        income: Coins = Coins(60),
    ) = GamePeriod(
        id = 1,
        profileId = ProfileId("p1"),
        number = number,
        income = income,
        startBalance = startBalance,
        status = status,
        closedAt = closedAt,
    )

    @Test
    fun `доступная сумма складывает остаток и доход`() {
        assertEquals(Coins(100), period().available)
    }

    @Test
    fun `нулевой номер периода не допускается`() {
        assertThrows(IllegalArgumentException::class.java) { period(number = 0) }
    }

    @Test
    fun `закрытый период обязан нести время закрытия`() {
        assertThrows(IllegalArgumentException::class.java) {
            period(status = PeriodStatus.CLOSED, closedAt = null)
        }
    }

    @Test
    fun `незакрытый период не может нести время закрытия`() {
        assertThrows(IllegalArgumentException::class.java) {
            period(status = PeriodStatus.RUNNING, closedAt = 100)
        }
    }

    @Test
    fun `закрытый период со временем закрытия допустим`() {
        assertEquals(PeriodStatus.CLOSED, period(status = PeriodStatus.CLOSED, closedAt = 100).status)
    }

    @Test
    fun `имена статусов не меняются`() {
        assertEquals(listOf("PLANNING", "RUNNING", "CLOSED"), PeriodStatus.entries.map { it.name })
    }
}
