package ru.finnypet.app.data.local.mapper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import ru.finnypet.app.data.local.entity.PetStateEntity
import ru.finnypet.app.data.local.entity.TaskProgressEntity
import ru.finnypet.app.data.local.entity.TransactionEntity
import ru.finnypet.app.domain.model.BudgetPlan
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.GamePeriod
import ru.finnypet.app.domain.model.GoalId
import ru.finnypet.app.domain.model.GoalProgress
import ru.finnypet.app.domain.model.GrowthStage
import ru.finnypet.app.domain.model.ItemId
import ru.finnypet.app.domain.model.PeriodStatus
import ru.finnypet.app.domain.model.PetAppearance
import ru.finnypet.app.domain.model.PetGrowth
import ru.finnypet.app.domain.model.PetState
import ru.finnypet.app.domain.model.Profile
import ru.finnypet.app.domain.model.ProfileId
import ru.finnypet.app.domain.model.Stat
import ru.finnypet.app.domain.model.TaskId
import ru.finnypet.app.domain.model.Transaction
import ru.finnypet.app.domain.model.TransactionType

/**
 * Круговой проход domain -> entity -> domain обязан давать исходный объект.
 * Любая потеря поля здесь означает потерю прогресса у ребёнка (ТЗ 2.5.13).
 */
class MappersTest {

    private val profileId = ProfileId("profile-1")

    @Test
    fun `профиль переживает круговой проход`() {
        val profile = Profile(
            id = profileId,
            childName = "Егор",
            petName = "Финни",
            appearance = PetAppearance(bodyId = "owl", colorId = "mint", accessoryId = "scarf"),
            createdAt = 1_700_000_000_000L,
            isTest = false,
        )

        assertEquals(profile, profile.toEntity().toDomain())
    }

    @Test
    fun `питомец без аксессуара переживает круговой проход`() {
        val profile = Profile(
            id = profileId,
            childName = "Егор",
            petName = "Финни",
            appearance = PetAppearance(bodyId = "owl", colorId = "mint", accessoryId = null),
            createdAt = 1L,
            isTest = true,
        )

        val restored = profile.toEntity().toDomain()

        assertEquals(profile, restored)
        assertEquals(null, restored.appearance.accessoryId)
    }

    @Test
    fun `состояние и рост питомца переживают круговой проход`() {
        val state = PetState(mood = Stat(80), satiety = Stat(65), care = Stat(50))
        val growth = PetGrowth(points = 12, stage = GrowthStage.YOUNG)

        val entity = petStateEntityOf(profileId, state, growth)

        assertEquals(state, entity.toState())
        assertEquals(growth, entity.toGrowth())
    }

    @Test
    fun `идущий период переживает круговой проход`() {
        val period = GamePeriod(
            id = 7,
            profileId = profileId,
            number = 3,
            income = Coins(60),
            startBalance = Coins(20),
            status = PeriodStatus.RUNNING,
            closedAt = null,
        )

        assertEquals(period, period.toEntity().toDomain())
    }

    @Test
    fun `закрытый период сохраняет время закрытия`() {
        val period = GamePeriod(
            id = 7,
            profileId = profileId,
            number = 3,
            income = Coins(60),
            startBalance = Coins(20),
            status = PeriodStatus.CLOSED,
            closedAt = 1_700_000_000_000L,
        )

        assertEquals(period, period.toEntity().toDomain())
    }

    @Test
    fun `план бюджета переживает круговой проход`() {
        val plan = BudgetPlan(mandatory = Coins(30), optional = Coins(20), savings = Coins(10))

        assertEquals(plan, plan.toEntity(periodId = 7).toDomain())
    }

    @Test
    fun `план запоминает период, к которому относится`() {
        val plan = BudgetPlan(mandatory = Coins(30), optional = Coins(20), savings = Coins(10))

        assertEquals(7L, plan.toEntity(periodId = 7).periodId)
    }

    @Test
    fun `покупка переживает круговой проход вместе с товаром`() {
        val transaction = Transaction(
            id = 5,
            periodId = 7,
            type = TransactionType.PURCHASE_MANDATORY,
            amount = Coins(15),
            reasonKey = "purchase.done",
            createdAt = 1_700_000_000_000L,
            itemId = ItemId("food-1"),
            goalId = null,
        )

        assertEquals(transaction, transaction.toEntity().toDomain())
    }

    @Test
    fun `пополнение накоплений переживает круговой проход вместе с целью`() {
        val transaction = Transaction(
            id = 5,
            periodId = 7,
            type = TransactionType.SAVINGS_DEPOSIT,
            amount = Coins(10),
            reasonKey = "savings.deposited",
            createdAt = 1L,
            itemId = null,
            goalId = GoalId("bike"),
        )

        assertEquals(transaction, transaction.toEntity().toDomain())
    }

    @Test
    fun `доход переживает круговой проход без товара и цели`() {
        val transaction = Transaction(
            id = 0,
            periodId = 7,
            type = TransactionType.INCOME_PERIOD,
            amount = Coins(60),
            reasonKey = "balance.credited",
            createdAt = 1L,
        )

        val restored = transaction.toEntity().toDomain()

        assertEquals(transaction, restored)
        assertEquals(null, restored.itemId)
        assertEquals(null, restored.goalId)
    }

    @Test
    fun `прогресс по цели переживает круговой проход`() {
        val progress = GoalProgress(goalId = GoalId("bike"), saved = Coins(45), isActive = true)

        assertEquals(progress, progress.toEntity(profileId).toDomain())
    }

    @Test
    fun `прогресс по цели запоминает профиль`() {
        val progress = GoalProgress(goalId = GoalId("bike"), saved = Coins(45), isActive = true)

        assertEquals(profileId.value, progress.toEntity(profileId).profileId)
    }

    @Test
    fun `завершённое задание отдаёт идентификатор задания`() {
        val entity = TaskProgressEntity(
            id = 1,
            profileId = profileId.value,
            taskId = "plan-1",
            outcomeId = "good",
            reward = 15,
            completedAt = 1L,
        )

        assertEquals(TaskId("plan-1"), entity.toTaskId())
    }

    @Test
    fun `отрицательная сумма в базе падает на чтении, а не растекается по экранам`() {
        val broken = TransactionEntity(
            id = 1,
            periodId = 7,
            type = TransactionType.PURCHASE_OPTIONAL,
            amount = -5,
            reasonKey = "purchase.done",
            createdAt = 1L,
            itemId = null,
            goalId = null,
        )

        assertThrows(IllegalArgumentException::class.java) { broken.toDomain() }
    }

    @Test
    fun `показатель питомца вне допустимого диапазона падает на чтении`() {
        val broken = PetStateEntity(
            profileId = profileId.value,
            mood = 140,
            satiety = 70,
            care = 70,
            growthPoints = 0,
            stage = GrowthStage.CUB,
        )

        assertThrows(IllegalArgumentException::class.java) { broken.toState() }
    }
}
