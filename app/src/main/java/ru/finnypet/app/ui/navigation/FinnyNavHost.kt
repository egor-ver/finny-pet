package ru.finnypet.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ru.finnypet.app.ui.screens.CreatePetScreen
import ru.finnypet.app.ui.screens.MainScreen
import ru.finnypet.app.ui.screens.OnboardingScreen

/**
 * Граф переходов.
 *
 * Онбординг и создание питомца из стека убираются: пройдя их один раз,
 * ребёнок не должен попадать туда кнопкой «назад» с главного экрана.
 *
 * Стартовый экран задаётся снаружи, а не зашит здесь: при сохранённом
 * профиле приложение обязано открываться сразу на главном (ТЗ 2.5.13,
 * шаг 11 Приложения А). Кто спрашивает репозиторий о профиле, решится
 * на шаге 6 вместе с появлением первой ViewModel.
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
                onStart = { navController.navigate(CreatePet) },
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
            MainScreen()
        }
    }
}
