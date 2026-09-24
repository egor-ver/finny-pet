package ru.finnypet.app.ui.screens.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.finnypet.app.R
import ru.finnypet.app.domain.model.PeriodStatus
import ru.finnypet.app.domain.model.TaskId
import ru.finnypet.app.ui.components.ButtonColumn
import ru.finnypet.app.ui.components.FinnyButton
import ru.finnypet.app.ui.components.FinnyCard
import ru.finnypet.app.ui.components.FinnyScaffold
import ru.finnypet.app.ui.components.FinnySecondaryButton
import ru.finnypet.app.ui.components.GoalProgressBar
import ru.finnypet.app.ui.components.MoneyAmount
import ru.finnypet.app.ui.components.MoneyCard
import ru.finnypet.app.ui.components.PetImage
import ru.finnypet.app.ui.components.StatBar
import ru.finnypet.app.ui.components.coinsText
import ru.finnypet.app.ui.components.label
import ru.finnypet.app.ui.theme.Dimens

/**
 * Главный экран (ТЗ 2.5.3): питомец, баланс, накопления, цель, показатели
 * состояния и задание дня видны одновременно, без переходов.
 *
 * Переход в раздел для взрослого появится вместе с этим экраном: кнопка,
 * ведущая в пустоту, — тупик, а ТЗ 3.4 их запрещает.
 */
@Composable
fun MainScreen(
    onPlan: () -> Unit,
    onProgress: () -> Unit,
    onHelp: () -> Unit,
    onAdult: () -> Unit,
    onShop: () -> Unit,
    onSavings: () -> Unit,
    onTask: (TaskId) -> Unit,
    onFinishDay: () -> Unit,
    banner: @Composable () -> Unit = {},
    viewModel: MainViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    MainContent(
        state = state,
        onRetry = viewModel::retry,
        onPlan = onPlan,
        onShop = onShop,
        onSavings = onSavings,
        onTask = onTask,
        onFinishDay = onFinishDay,
        onProgress = onProgress,
        onHelp = onHelp,
        onAdult = onAdult,
        banner = banner,
    )
}

/**
 * Отрисовка отделена от ViewModel: так экран показывается в тесте с любым
 * состоянием, не поднимая граф зависимостей.
 */
@Composable
fun MainContent(
    state: MainState,
    onRetry: () -> Unit = {},
    onPlan: () -> Unit = {},
    onShop: () -> Unit = {},
    onSavings: () -> Unit = {},
    onTask: (TaskId) -> Unit = {},
    onFinishDay: () -> Unit = {},
    onProgress: () -> Unit = {},
    onHelp: () -> Unit = {},
    onAdult: () -> Unit = {},
    banner: @Composable () -> Unit = {},
) {
    when (state) {
        MainState.Loading -> LoadingScreen()
        MainState.Failed -> FailedScreen(onRetry = onRetry)
        is MainState.Ready -> ReadyScreen(
            state = state,
            onPlan = onPlan,
            onShop = onShop,
            onSavings = onSavings,
            onTask = onTask,
            onFinishDay = onFinishDay,
            onProgress = onProgress,
            onHelp = onHelp,
            onAdult = onAdult,
            banner = banner,
        )
    }
}

/**
 * Пустой экран без надписей: чтение профиля занимает миллисекунды, и текст
 * «загружаем» успел бы только мигнуть. Фон держится, чтобы не мелькало белым.
 */
@Composable
private fun LoadingScreen() {
    FinnyScaffold(title = stringResource(R.string.main_title)) {}
}

@Composable
private fun FailedScreen(onRetry: () -> Unit) {
    FinnyScaffold(
        title = stringResource(R.string.main_title),
        bottomBar = {
            ButtonColumn {
                FinnyButton(text = stringResource(R.string.action_retry), onClick = onRetry)
            }
        },
    ) {
        Text(
            text = stringResource(R.string.main_failed),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun ReadyScreen(
    state: MainState.Ready,
    onPlan: () -> Unit,
    onShop: () -> Unit,
    onSavings: () -> Unit,
    onTask: (TaskId) -> Unit,
    onFinishDay: () -> Unit,
    onProgress: () -> Unit,
    onHelp: () -> Unit,
    onAdult: () -> Unit,
    banner: @Composable () -> Unit,
) {
    // Блоков много и все обязаны поместиться сразу (ТЗ 2.5.3), поэтому шаг
    // между ними меньше обычного.
    FinnyScaffold(
        // Заголовком стоит приветствие, а не название игры: ребёнок должен
        // видеть, чей это профиль, а место на экране дорого — по ТЗ 2.5.3
        // сюда обязаны поместиться шесть блоков сразу.
        title = stringResource(R.string.main_hello, state.childName),
        spacing = Dimens.SpaceMedium,
        bottomBar = {
            NextStepBar(
                state = state,
                onPlan = onPlan,
                onTask = onTask,
                onShop = onShop,
                onSavings = onSavings,
                onFinishDay = onFinishDay,
            )
        },
    ) {
        banner()

        DayLine(state = state, onFinishDay = onFinishDay)

        Pet(state = state)

        MoneyCard(label = stringResource(R.string.main_balance), amount = state.balance)
        SavingsCard(savings = state.savings, onOpen = onSavings)
        state.task?.let { task ->
            TaskCard(
                task = task,
                locked = state.periodStatus == PeriodStatus.PLANNING,
                onOpen = { onTask(task.id) },
            )
        }

        Text(
            text = stringResource(R.string.main_pet_state, state.petName),
            style = MaterialTheme.typography.titleMedium,
        )
        StatBar(label = stringResource(R.string.stat_mood), stat = state.stats.mood)
        StatBar(
            label = stringResource(R.string.stat_satiety),
            stat = state.stats.satiety,
            color = MaterialTheme.colorScheme.secondary,
        )
        StatBar(
            label = stringResource(R.string.stat_care),
            stat = state.stats.care,
            color = MaterialTheme.colorScheme.tertiary,
        )

        Link(stringResource(R.string.progress_action), onProgress)
        Link(stringResource(R.string.help_action), onHelp)
        Link(stringResource(R.string.adult_action), onAdult)
    }
}

/**
 * Один следующий шаг: подсказка словами и главная кнопка к нему. Вторая
 * кнопка — магазин, чтобы покупки были в одном нажатии с главного (ТЗ 2.5.3);
 * когда магазин и есть следующий шаг, вторая ведёт к плану.
 */
@Composable
private fun NextStepBar(
    state: MainState.Ready,
    onPlan: () -> Unit,
    onTask: (TaskId) -> Unit,
    onShop: () -> Unit,
    onSavings: () -> Unit,
    onFinishDay: () -> Unit,
) {
    val step = state.step
    val (hint, action) = when (step) {
        NextStep.Plan -> stringResource(R.string.main_step_plan) to stringResource(R.string.budget_action_plan)
        NextStep.Task -> stringResource(R.string.main_step_task) to stringResource(R.string.main_step_task_action)
        is NextStep.Shop -> stringResource(R.string.main_step_shop, coinsText(step.needs)) to
            stringResource(R.string.shop_action)
        is NextStep.Save -> stringResource(R.string.main_step_save, coinsText(step.left)) to
            stringResource(R.string.main_step_save_action)
        is NextStep.Finish -> stringResource(
            if (step.onPlan) R.string.main_step_finish else R.string.main_step_finish_off_plan,
        ) to stringResource(R.string.day_action_close)
    }
    val onAction: () -> Unit = when (step) {
        NextStep.Plan -> onPlan
        NextStep.Task -> { { state.task?.let { onTask(it.id) } } }
        is NextStep.Shop -> onShop
        is NextStep.Save -> onSavings
        is NextStep.Finish -> onFinishDay
    }

    ButtonColumn {
        // Черта отделяет подсказку от прокрутки: без неё она читается как
        // продолжение карточки, обрезанной краем панели.
        HorizontalDivider()
        Text(text = hint, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        FinnyButton(text = action, onClick = onAction)
        if (step is NextStep.Shop) {
            FinnySecondaryButton(text = stringResource(R.string.budget_action_show), onClick = onPlan)
        } else {
            FinnySecondaryButton(text = stringResource(R.string.shop_action), onClick = onShop)
        }
    }
}

/**
 * Дорога в раздел — строкой внизу, а не кнопкой: кнопок внизу уже две, а
 * третья вытесняет показатели питомца за край экрана при крупном шрифте.
 *
 * Текст называет действие словом: цвет — не единственный признак того, что
 * строка нажимается (ТЗ 3.6).
 */
@Composable
private fun Link(text: String, onOpen: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.Corner))
            .clickable(role = Role.Button, onClick = onOpen)
            .defaultMinSize(minHeight = Dimens.TouchTarget)
            .padding(vertical = Dimens.SpaceSmall),
    )
}

/**
 * Строка игрового дня. Пока день идёт — ещё и дорога к его итогам: закончить
 * день можно в любой момент, не дожидаясь, пока подсказка дойдёт до итогов.
 * Когда итоги и есть следующий шаг, они уже на главной кнопке.
 */
@Composable
private fun DayLine(state: MainState.Ready, onFinishDay: () -> Unit) {
    val line = stringResource(
        R.string.main_period,
        state.periodNumber,
        stringResource(state.periodStatus.label),
    )
    if (state.periodStatus == PeriodStatus.PLANNING || state.step is NextStep.Finish) {
        Text(
            text = line,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSmall),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.Corner))
            .clickable(role = Role.Button, onClick = onFinishDay)
            .defaultMinSize(minHeight = Dimens.TouchTarget)
            .semantics(mergeDescendants = true) {},
    ) {
        Text(
            text = line,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        // Подпись говорит, что строка нажимается: цветом это не передашь (ТЗ 3.6).
        Text(
            text = stringResource(R.string.day_action_close),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/**
 * Питомец с именем и стадией.
 *
 * Стадия написана словом, а не только нарисована: по картинке отличить
 * подростка от взрослого труднее, чем прочитать, и озвучке картинка недоступна
 * вовсе (ТЗ 3.6). Заодно это выполняет ТЗ 2.5.10 — стадия видна ребёнку.
 */
@Composable
private fun Pet(state: MainState.Ready) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSmall),
        modifier = Modifier.fillMaxWidth(),
    ) {
        // Меньше, чем по умолчанию: питомец остаётся главным на экране, но
        // не выталкивает показатели состояния за нижний край.
        PetImage(appearance = state.appearance, stage = state.stage, size = 140.dp)
        Text(
            text = state.petName,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(state.stage.label),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Накопления и цель.
 *
 * Цели может не быть — ребёнок ещё не выбрал. Тогда сумма всё равно
 * показывается: отложенные монеты не должны пропадать с экрана из-за того,
 * что цель не назначена.
 *
 * Карточка целиком — кнопка в копилку: третья кнопка внизу вытеснила бы
 * показатели питомца с экрана, а ТЗ 2.5.3 требует показать всё сразу.
 * Подпись «Открыть копилку» говорит, что карточка нажимается, — цветом это
 * не передашь (ТЗ 3.6).
 */
@Composable
private fun SavingsCard(savings: SavingsView, onOpen: () -> Unit) {
    FinnyCard(onClick = onOpen) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSmall),
            modifier = Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {},
        ) {
            Text(
                text = stringResource(R.string.main_savings),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            MoneyAmount(amount = savings.saved)
        }

        val title = savings.goalTitle
        val price = savings.price
        if (title == null || price == null) {
            Text(
                text = stringResource(R.string.main_goal_none),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            GoalProgressBar(title = title, saved = savings.saved, price = price)
        }

        Text(
            text = stringResource(R.string.savings_open),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.align(Alignment.End),
        )
    }
}

/**
 * Задание дня (ТЗ 2.5.3): тема, начало вступления и состояние награды.
 * Карточка целиком — кнопка в задание, подпись «Открыть» словом. Пока день
 * не спланирован, задания закрыты — карточка так и говорит, а не зовёт внутрь.
 */
@Composable
private fun TaskCard(task: TaskOfDay, locked: Boolean, onOpen: () -> Unit) {
    FinnyCard(onClick = onOpen.takeUnless { locked }) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSmall),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(R.string.main_task),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(task.topic.label),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = task.intro,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        // Обе подписи независимы: «всё пройдено» не должно прятать, что
        // награда за сегодня ещё ждёт — иначе повторять незачем.
        if (task.allDone) {
            Text(
                text = stringResource(R.string.main_task_all_done),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = stringResource(
                if (task.rewardAvailable) R.string.main_task_reward else R.string.main_task_reward_taken
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(if (locked) R.string.main_task_locked else R.string.main_task_open),
            style = MaterialTheme.typography.bodyMedium,
            color = if (locked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
            modifier = Modifier.align(Alignment.End),
        )
    }
}


private val PeriodStatus.label: Int
    get() = when (this) {
        PeriodStatus.PLANNING -> R.string.main_period_planning
        PeriodStatus.RUNNING -> R.string.main_period_running
        // На главный экран закрытый период не попадает — текущим считается
        // незакрытый. Подпись нужна, чтобы разбор был полным и честным.
        PeriodStatus.CLOSED -> R.string.main_period_closed
    }
