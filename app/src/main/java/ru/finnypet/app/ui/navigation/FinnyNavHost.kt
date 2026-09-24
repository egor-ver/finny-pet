package ru.finnypet.app.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ru.finnypet.app.R
import ru.finnypet.app.ui.screens.adult.AdultGateScreen
import ru.finnypet.app.ui.screens.adult.AdultScreen
import ru.finnypet.app.ui.screens.budget.BudgetScreen
import ru.finnypet.app.ui.screens.demo.DemoBanner
import ru.finnypet.app.ui.screens.createpet.CreatePetScreen
import ru.finnypet.app.ui.screens.day.DayScreen
import ru.finnypet.app.ui.screens.main.MainScreen
import ru.finnypet.app.ui.screens.onboarding.OnboardingScreen
import ru.finnypet.app.ui.screens.progress.ProgressScreen
import ru.finnypet.app.ui.screens.savings.SavingsScreen
import ru.finnypet.app.ui.screens.shop.ShopScreen
import ru.finnypet.app.ui.screens.tasks.TaskScreen
import ru.finnypet.app.ui.screens.tasks.TasksScreen
import ru.finnypet.app.ui.theme.LocalAnimationsEnabled

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
    val motion = LocalAnimationsEnabled.current
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        enterTransition = { screenEnter(motion) },
        exitTransition = { screenExit(motion) },
    ) {
        composable<Onboarding> {
            OnboardingScreen(
                onDone = { navController.navigate(CreatePet) },
            )
        }

        composable<Help> {
            OnboardingScreen(
                onDone = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
                doneText = stringResource(R.string.action_ok),
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
                onHelp = { navController.navigate(Help) },
                onAdult = { navController.navigate(AdultGate) },
                banner = {
                    DemoBanner(onNeedsOnboarding = {
                        navController.navigate(Onboarding) { popUpTo(Main) { inclusive = true } }
                    })
                },
            )
        }

        composable<Progress> {
            ProgressScreen(onBack = { navController.popBackStack() })
        }

        composable<AdultGate> {
            AdultGateScreen(
                // Пример убирается из стека: «назад» из раздела должен вести
                // на главный экран, а не снова спрашивать пример.
                onSolved = {
                    navController.navigate(Adult) { popUpTo(AdultGate) { inclusive = true } }
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable<Adult> {
            AdultScreen(
                onBack = { navController.popBackStack() },
                // Демонстрация начинается на главном: стек раздела взрослого
                // за спиной привёл бы «назад» в чужую уже страницу.
                onDemoStarted = {
                    navController.navigate(Main) { popUpTo(Main) { inclusive = true } }
                },
                onGameDeleted = {
                    navController.navigate(Onboarding) { popUpTo(navController.graph.id) { inclusive = true } }
                },
            )
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
                onOpen = { taskId -> navController.navigate(Task(taskId.value)) },
            )
        }

        composable<Task> {
            TaskScreen(onBack = { navController.popBackStack() })
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

/**
 * Смена экранов слушает настройку движения (ТЗ 3.6, AD-8): по умолчанию
 * навигация плавно проявляет экран, а с выключенным движением он меняется
 * сразу. Длительность — как у навигации по умолчанию.
 */
internal fun screenEnter(motion: Boolean): EnterTransition =
    if (motion) fadeIn(tween(SCREEN_FADE_MS)) else EnterTransition.None

internal fun screenExit(motion: Boolean): ExitTransition =
    if (motion) fadeOut(tween(SCREEN_FADE_MS)) else ExitTransition.None

private const val SCREEN_FADE_MS = 700
