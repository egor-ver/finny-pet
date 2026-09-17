package ru.finnypet.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ru.finnypet.app.ui.screens.budget.BudgetScreen
import ru.finnypet.app.ui.screens.createpet.CreatePetScreen
import ru.finnypet.app.ui.screens.main.MainScreen
import ru.finnypet.app.ui.screens.onboarding.OnboardingScreen

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
            MainScreen(onPlan = { navController.navigate(Budget) })
        }

        composable<Budget> {
            BudgetScreen(onBack = { navController.popBackStack() })
        }
    }
}
