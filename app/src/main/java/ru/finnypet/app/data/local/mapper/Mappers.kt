package ru.finnypet.app.data.local.mapper

import ru.finnypet.app.data.local.entity.BudgetPlanEntity
import ru.finnypet.app.data.local.entity.GoalProgressEntity
import ru.finnypet.app.data.local.entity.PeriodEntity
import ru.finnypet.app.data.local.entity.PetStateEntity
import ru.finnypet.app.data.local.entity.ProfileEntity
import ru.finnypet.app.data.local.entity.TaskProgressEntity
import ru.finnypet.app.data.local.entity.TransactionEntity
import ru.finnypet.app.domain.model.BudgetPlan
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.GamePeriod
import ru.finnypet.app.domain.model.GoalId
import ru.finnypet.app.domain.model.GoalProgress
import ru.finnypet.app.domain.model.ItemId
import ru.finnypet.app.domain.model.PetAppearance
import ru.finnypet.app.domain.model.PetGrowth
import ru.finnypet.app.domain.model.PetState
import ru.finnypet.app.domain.model.Profile
import ru.finnypet.app.domain.model.ProfileId
import ru.finnypet.app.domain.model.Stat
import ru.finnypet.app.domain.model.TaskId
import ru.finnypet.app.domain.model.Transaction

/**
 * Граница между хранилищем и доменом.
 *
 * Сущности Room — плоские строки таблиц без единой проверки; доменные типы
 * несут инварианты (Coins не бывает отрицательным, Stat лежит в 0..100).
 * Восстановление идёт здесь, поэтому битая строка в базе падает на чтении
 * с внятным сообщением, а не растекается по экранам невозможным состоянием.
 */

// --- Профиль ---

fun ProfileEntity.toDomain(): Profile = Profile(
    id = ProfileId(id),
    childName = childName,
    petName = petName,
    appearance = PetAppearance(
        bodyId = bodyId,
        colorId = colorId,
        accessoryId = accessoryId,
    ),
    createdAt = createdAt,
    isTest = isTest,
)

fun Profile.toEntity(): ProfileEntity = ProfileEntity(
    id = id.value,
    childName = childName,
    petName = petName,
    bodyId = appearance.bodyId,
    colorId = appearance.colorId,
    accessoryId = appearance.accessoryId,
    createdAt = createdAt,
    isTest = isTest,
)

// --- Питомец: состояние и рост лежат в одной строке, но это два доменных типа ---

fun PetStateEntity.toState(): PetState = PetState(
    mood = Stat(mood),
    satiety = Stat(satiety),
    care = Stat(care),
)

fun PetStateEntity.toGrowth(): PetGrowth = PetGrowth(
    points = growthPoints,
    stage = stage,
)

fun petStateEntityOf(
    profileId: ProfileId,
    state: PetState,
    growth: PetGrowth,
): PetStateEntity = PetStateEntity(
    profileId = profileId.value,
    mood = state.mood.value,
    satiety = state.satiety.value,
    care = state.care.value,
    growthPoints = growth.points,
    stage = growth.stage,
)

// --- Период ---

fun PeriodEntity.toDomain(): GamePeriod = GamePeriod(
    id = id,
    profileId = ProfileId(profileId),
    number = number,
    income = Coins(income),
    startBalance = Coins(startBalance),
    status = status,
    closedAt = closedAt,
)

fun GamePeriod.toEntity(): PeriodEntity = PeriodEntity(
    id = id,
    profileId = profileId.value,
    number = number,
    income = income.amount,
    startBalance = startBalance.amount,
    status = status,
    closedAt = closedAt,
)

// --- План бюджета ---

fun BudgetPlanEntity.toDomain(): BudgetPlan = BudgetPlan(
    mandatory = Coins(mandatory),
    optional = Coins(optional),
    savings = Coins(savings),
)

fun BudgetPlan.toEntity(periodId: Long): BudgetPlanEntity = BudgetPlanEntity(
    periodId = periodId,
    mandatory = mandatory.amount,
    optional = optional.amount,
    savings = savings.amount,
)

// --- Операции ---

fun TransactionEntity.toDomain(): Transaction = Transaction(
    id = id,
    periodId = periodId,
    type = type,
    amount = Coins(amount),
    reasonKey = reasonKey,
    createdAt = createdAt,
    itemId = itemId?.let(::ItemId),
    goalId = goalId?.let(::GoalId),
)

fun Transaction.toEntity(): TransactionEntity = TransactionEntity(
    id = id,
    periodId = periodId,
    type = type,
    amount = amount.amount,
    reasonKey = reasonKey,
    createdAt = createdAt,
    itemId = itemId?.value,
    goalId = goalId?.value,
)

// --- Накопления ---

fun GoalProgressEntity.toDomain(): GoalProgress = GoalProgress(
    goalId = GoalId(goalId),
    saved = Coins(saved),
    isActive = isActive,
)

fun GoalProgress.toEntity(profileId: ProfileId): GoalProgressEntity = GoalProgressEntity(
    profileId = profileId.value,
    goalId = goalId.value,
    saved = saved.amount,
    isActive = isActive,
)

// --- Учебный прогресс ---

fun TaskProgressEntity.toTaskId(): TaskId = TaskId(taskId)
