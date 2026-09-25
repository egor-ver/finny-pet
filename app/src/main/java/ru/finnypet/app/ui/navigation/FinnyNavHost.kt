package ru.finnypet.app.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavOptionsBuilder
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
                onDone = { navController.navigateOnce(CreatePet) },
            )
        }

        composable<Help> {
            OnboardingScreen(
                onDone = { navController.popOnce() },
                onBack = { navController.popOnce() },
                doneText = stringResource(R.string.action_ok),
            )
        }

        composable<CreatePet> {
            CreatePetScreen(
                onBack = { navController.popOnce() },
                onCreated = {
                    navController.navigateOnce(Main) {
                        popUpTo(Onboarding) { inclusive = true }
                    }
                },
            )
        }

        composable<Main> {
            MainScreen(
                onPlan = { navController.navigateOnce(Budget) },
                onShop = { navController.navigateOnce(Shop) },
                onSavings = { navController.navigateOnce(Savings) },
                onTask = { taskId -> navController.navigateOnce(Task(taskId.value)) },
                onTasks = { navController.navigateOnce(Tasks) },
                onFinishDay = { navController.navigateOnce(Day) },
                onProgress = { navController.navigateOnce(Progress) },
                onHelp = { navController.navigateOnce(Help) },
                onAdult = { navController.navigateOnce(AdultGate) },
                banner = {
                    DemoBanner(onNeedsOnboarding = {
                        navController.navigateOnce(Onboarding) { popUpTo(Main) { inclusive = true } }
                    })
                },
            )
        }

        composable<Progress> {
            ProgressScreen(onBack = { navController.popOnce() })
        }

        composable<AdultGate> {
            AdultGateScreen(
                // Пример убирается из стека: «назад» из раздела должен вести
                // на главный экран, а не снова спрашивать пример.
                onSolved = {
                    navController.navigateOnce(Adult) { popUpTo(AdultGate) { inclusive = true } }
                },
                onBack = { navController.popOnce() },
            )
        }

        composable<Adult> {
            AdultScreen(
                onBack = { navController.popOnce() },
                // Демонстрация начинается на главном: стек раздела взрослого
                // за спиной привёл бы «назад» в чужую уже страницу.
                onDemoStarted = {
                    navController.navigateOnce(Main) { popUpTo(Main) { inclusive = true } }
                },
                onGameDeleted = {
                    navController.navigateOnce(Onboarding) { popUpTo(navController.graph.id) { inclusive = true } }
                },
            )
        }

        composable<Day> {
            DayScreen(
                onBack = { navController.popOnce() },
                onPlan = {
                    // Из «день ещё планируется» дорога одна — в план, и итоги
                    // в стеке не нужны: закрывать пока нечего. Один переход,
                    // а не pop + navigate: после pop запись Main ещё в
                    // переходе (STARTED, не RESUMED), и второй шаг не
                    // сработал бы (замечание ревью L3).
                    navController.navigateOnce(Budget) { popUpTo<Day> { inclusive = true } }
                },
            )
        }

        composable<Budget> {
            BudgetScreen(
                onBack = { navController.popOnce() },
                onShop = { navController.navigateOnce(Shop) },
                onSavings = { navController.navigateOnce(Savings) },
            )
        }

        composable<Shop> {
            ShopScreen(
                onBack = { navController.popOnce() },
                // Из магазина в план — не поверх магазина, а вместо него:
                // после плана ребёнок вернётся на главный, а не в магазин,
                // где ещё минуту назад покупать было нельзя.
                onPlan = {
                    navController.navigateOnce(Budget) {
                        popUpTo<Main>()
                    }
                },
                // А копилка и задания — поверх: взял монеты, вернулся и купил.
                onSavings = { navController.navigateOnce(Savings) },
                onTasks = { navController.navigateOnce(Tasks) },
            )
        }

        composable<Tasks> {
            TasksScreen(
                onBack = { navController.popOnce() },
                onOpen = { taskId -> navController.navigateOnce(Task(taskId.value)) },
            )
        }

        composable<Task> {
            TaskScreen(onBack = { navController.popOnce() })
        }

        composable<Savings> {
            SavingsScreen(
                onBack = { navController.popOnce() },
                onPlan = {
                    navController.navigateOnce(Budget) {
                        popUpTo<Main>()
                    }
                },
            )
        }
    }
}

/**
 * Один переход на одно нажатие (Б14): экран смены (`screenExit`) ещё
 * принимает нажатия, пока идёт анимация, и двойное нажатие открывает
 * следующий экран дважды или дважды закрывает текущий. У текущей записи
 * стека `RESUMED` пропадает сразу после первого перехода и возвращается,
 * только если он не состоялся, — так второе нажатие до этого момента
 * ничего не делает. Проверка одна и та же для входа и для «назад»
 * (замечание ревью L3: второе «назад» иначе снимало бы уже следующий экран).
 */
private fun NavHostController.navigateOnce(route: Any) {
    if (currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) navigate(route)
}

private fun NavHostController.navigateOnce(route: Any, builder: NavOptionsBuilder.() -> Unit) {
    if (currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) navigate(route, builder)
}

private fun NavHostController.popOnce() {
    if (currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) popBackStack()
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
