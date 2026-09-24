package ru.finnypet.app.ui.screens.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.finnypet.app.R
import ru.finnypet.app.domain.model.PetStatKind
import ru.finnypet.app.domain.model.Stat
import ru.finnypet.app.domain.model.TaskId
import ru.finnypet.app.ui.components.ButtonColumn
import ru.finnypet.app.ui.components.FinnyButton
import ru.finnypet.app.ui.components.FinnyCard
import ru.finnypet.app.ui.components.FinnyScaffold
import ru.finnypet.app.ui.components.FinnySecondaryButton
import ru.finnypet.app.ui.components.GoalProgressBar
import ru.finnypet.app.ui.components.MoneyAmount
import ru.finnypet.app.ui.components.Owl
import ru.finnypet.app.ui.components.ProgressLine
import ru.finnypet.app.ui.components.icon
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
        // Вместо заголовка — кошелёк: сколько монет есть, ребёнок видит
        // первым делом, без инструкции (ТЗ 8.4).
        title = { MoneyAmount(amount = state.balance) },
        actions = {
            TopIcon(symbol = "?", label = stringResource(R.string.help_action), onClick = onHelp)
            TopIcon(symbol = "🔒", label = stringResource(R.string.adult_action), onClick = onAdult)
        },
        spacing = Dimens.SpaceMedium,
        bottomBar = { DayButtons(step = state.step, onPlan = onPlan, onShop = onShop, onSleep = onFinishDay) },
    ) {
        banner()

        Bubble(text = state.phrase)
        Pet(state = state)
        PetStats(state = state)

        SavingsCard(savings = state.savings, onOpen = onSavings)
        state.task?.let { task -> TaskCard(task = task, onOpen = { onTask(task.id) }) }

        // После подтверждения главная кнопка к плану больше не ведёт, а
        // сравнить план с фактом ребёнок должен иметь возможность (ТЗ 2.5.5).
        if (state.step != NextStep.Plan) Link(stringResource(R.string.budget_action_show), onPlan)
        Link(stringResource(R.string.progress_action), onProgress)
    }
}

/**
 * Вход в подсказку или раздел для взрослого — значок 48 dp в шапке. Значок
 * для озвучки молчит, TalkBack читает подпись: «?» и замок сами по себе
 * ничего не говорят (ТЗ 3.6).
 */
@Composable
private fun TopIcon(symbol: String, label: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .defaultMinSize(minWidth = Dimens.TouchTarget, minHeight = Dimens.TouchTarget)
            .semantics { contentDescription = label },
    ) {
        Text(
            text = symbol,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

/**
 * Главная кнопка идёт по фазе дня, вторая — магазин, чтобы покупки были в
 * одном нажатии с главного (ТЗ 2.5.3).
 *
 * Пока главная зовёт в магазин, вторая укладывает спать: иначе голодную сову
 * было бы не уложить, а ТЗ 2.2 разрешает ошибиться — и разобрать ошибку в итогах.
 */
@Composable
private fun DayButtons(step: NextStep, onPlan: () -> Unit, onShop: () -> Unit, onSleep: () -> Unit) {
    val shop = stringResource(R.string.shop_action)
    val sleep = stringResource(R.string.main_action_sleep)
    val (main, onMain) = when (step) {
        NextStep.Plan -> stringResource(R.string.budget_action_plan) to onPlan
        NextStep.Shop -> shop to onShop
        NextStep.Sleep -> sleep to onSleep
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSmall),
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (step == NextStep.Shop) {
            FinnySecondaryButton(text = sleep, onClick = onSleep, modifier = Modifier.weight(1f))
        } else {
            FinnySecondaryButton(text = shop, onClick = onShop, modifier = Modifier.weight(1f))
        }
        FinnyButton(
            text = main,
            onClick = onMain,
            modifier = Modifier
                .weight(2f)
                .heightIn(min = MAIN_BUTTON_HEIGHT),
        )
    }
}

/**
 * Облачко совы: почему она такая и что делать дальше (ТЗ 2.5.9, 2.5.10).
 * Одна фраза вместо подсказки внизу — говорит тот, о ком заботятся.
 */
@Composable
private fun Bubble(text: String) {
    FinnyCard {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
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
    // На низком экране сова меньше: иначе строки под ней уйдут под кнопки
    // и главный перестанет помещаться без прокрутки (раздел 8 плана).
    val low = LocalConfiguration.current.screenHeightDp < LOW_SCREEN_DP
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSmall),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Owl(look = state.owl, size = if (low) 120.dp else 170.dp)
        Text(
            text = stringResource(R.string.main_pet_stage, state.petName, stringResource(state.stage.label)),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Строка «Финни»: три показателя в ряд. Под потребностью — слово «нужно»:
 * цвет полосы не единственный признак (ТЗ 3.6). Полосы одного нейтрального
 * цвета, чтобы не спорить с цветами направлений трат (раздел 8 плана).
 */
@Composable
private fun PetStats(state: MainState.Ready) {
    Text(
        text = stringResource(R.string.main_pet_state, state.petName),
        style = MaterialTheme.typography.titleMedium,
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMedium),
        modifier = Modifier.fillMaxWidth(),
    ) {
        STATS.forEach { kind ->
            PetStat(
                kind = kind,
                stat = state.stats.statFor(kind),
                needed = kind in state.needs,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * Название и «нужно» — отдельными строками: в треть ширины 360 dp при 16 sp
 * «Уход · нужно» не помещается, а при крупном шрифте обрезалось бы (ТЗ 3.6).
 */
@Composable
private fun PetStat(kind: PetStatKind, stat: Stat, needed: Boolean, modifier: Modifier) {
    val label = stringResource(kind.label)
    val need = stringResource(R.string.main_stat_need)
    val value = stringResource(R.string.stat_description, label, stat.value, Stat.RANGE.last)
    val spoken = if (needed) "$value, $need" else value
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceTiny),
        modifier = modifier.clearAndSetSemantics { contentDescription = spoken },
    ) {
        Text(text = kind.icon, style = MaterialTheme.typography.bodyLarge)
        ProgressLine(
            fraction = stat.value.toFloat() / Stat.RANGE.last,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = label, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        if (needed) {
            Text(
                text = need,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Дорога в раздел — строкой, а не кнопкой: кнопок внизу уже две, а третья
 * вытесняет показатели питомца за край экрана при крупном шрифте.
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
 * Карточка целиком — кнопка в задание, подпись «Открыть» словом. Замка до
 * плана нет: сначала заработай, потом распредели (R7).
 */
@Composable
private fun TaskCard(task: TaskOfDay, onOpen: () -> Unit) {
    FinnyCard(onClick = onOpen) {
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
            text = stringResource(R.string.main_task_open),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.align(Alignment.End),
        )
    }
}

/** Порядок как на макете: еда первой — о ней сова просит чаще всего. */
private val STATS = listOf(PetStatKind.SATIETY, PetStatKind.MOOD, PetStatKind.CARE)

/** Ниже этой высоты сова уменьшается (раздел 8 плана); vivo V2111 выше. */
private const val LOW_SCREEN_DP = 730

private val MAIN_BUTTON_HEIGHT = 56.dp
