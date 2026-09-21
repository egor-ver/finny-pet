package ru.finnypet.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ru.finnypet.app.ui.screens.budget.BudgetScreen
import ru.finnypet.app.ui.screens.createpet.CreatePetScreen
import ru.finnypet.app.ui.screens.day.DayScreen
import ru.finnypet.app.ui.screens.main.MainScreen
import ru.finnypet.app.ui.screens.onboarding.OnboardingScreen
import ru.finnypet.app.ui.screens.progress.ProgressScreen
import ru.finnypet.app.ui.screens.savings.SavingsScreen
import ru.finnypet.app.ui.screens.shop.ShopScreen
import ru.finnypet.app.ui.screens.tasks.TaskScreen
import ru.finnypet.app.ui.screens.tasks.TasksScreen

/**
 * Граф переходов.
 *
 * Онбординг и создание питомца из стека убираются: пройдя их один раз,
 * ребёнок не должен попадать туда кнопкой «назад» с главного экрана.
 *
 * Стартовый экран задаётся снаружи: при сохранённом профиле приложение
 * открывается сразу на главном (ТЗ 2.5.13, шаг 11 Приложения А).
 */
@Composable
fun FinnyNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startDestination: Route = Onboarding,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        composable<Onboarding> {
            OnboardingScreen(
                onDone = { navController.navigate(CreatePet) },
            )
        }

        composable<CreatePet> {
            CreatePetScreen(
                onBack = { navController.popBackStack() },
                onCreated = {
                    navController.navigate(Main) {
                        popUpTo(Onboarding) { inclusive = true }
                    }
                },
            )
        }

        composable<Main> {
            MainScreen(
                onPlan = { navController.navigate(Budget) },
                onShop = { navController.navigate(Shop) },
                onSavings = { navController.navigate(Savings) },
                onTask = { taskId -> navController.navigate(Task(taskId.value)) },
                onFinishDay = { navController.navigate(Day) },
                onProgress = { navController.navigate(Progress) },
            )
        }

        composable<Progress> {
            ProgressScreen(onBack = { navController.popBackStack() })
        }

        composable<Day> {
            DayScreen(
                onBack = { navController.popBackStack() },
                onPlan = {
                    // Из «день ещё планируется» дорога одна — в план, и итоги
                    // в стеке не нужны: закрывать пока нечего.
                    navController.popBackStack()
                    navController.navigate(Budget)
                },
            )
        }

        composable<Budget> {
            BudgetScreen(onBack = { navController.popBackStack() })
        }

        composable<Shop> {
            ShopScreen(
                onBack = { navController.popBackStack() },
                // Из магазина в план — не поверх магазина, а вместо него:
                // после плана ребёнок вернётся на главный, а не в магазин,
                // где ещё минуту назад покупать было нельзя.
                onPlan = {
                    navController.navigate(Budget) {
                        popUpTo<Main>()
                    }
                },
                // А копилка и задания — поверх: взял монеты, вернулся и купил.
                onSavings = { navController.navigate(Savings) },
                onTasks = { navController.navigate(Tasks) },
            )
        }

        composable<Tasks> {
            TasksScreen(
                onBack = { navController.popBackStack() },
                onPlan = {
                    navController.navigate(Budget) {
                        popUpTo<Main>()
                    }
                },
                onOpen = { taskId -> navController.navigate(Task(taskId.value)) },
            )
        }

        composable<Task> {
            TaskScreen(
                onBack = { navController.popBackStack() },
                onPlan = {
                    navController.navigate(Budget) {
                        popUpTo<Main>()
                    }
                },
            )
        }

        composable<Savings> {
            SavingsScreen(
                onBack = { navController.popBackStack() },
                onPlan = {
                    navController.navigate(Budget) {
                        popUpTo<Main>()
                    }
                },
            )
        }
    }
}
