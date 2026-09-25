package ru.finnypet.app.ui.screens.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.GrowthStage
import ru.finnypet.app.domain.model.PetStatKind
import ru.finnypet.app.domain.model.SpendCategory
import ru.finnypet.app.domain.model.Stat
import ru.finnypet.app.domain.model.TaskId
import ru.finnypet.app.domain.model.TransactionType
import ru.finnypet.app.ui.components.ButtonColumn
import ru.finnypet.app.ui.components.FinnyButton
import ru.finnypet.app.ui.components.FinnyCard
import ru.finnypet.app.ui.components.FinnyDialog
import ru.finnypet.app.ui.components.FinnyScaffold
import ru.finnypet.app.ui.components.FinnySecondaryButton
import ru.finnypet.app.ui.components.MoneyAmount
import ru.finnypet.app.ui.components.Owl
import ru.finnypet.app.ui.components.ProgressLine
import ru.finnypet.app.ui.components.color
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
    onTasks: () -> Unit,
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
        onTasks = onTasks,
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
    onTasks: () -> Unit = {},
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
            onTasks = onTasks,
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
    onTasks: () -> Unit,
    onFinishDay: () -> Unit,
    onProgress: () -> Unit,
    onHelp: () -> Unit,
    onAdult: () -> Unit,
    banner: @Composable () -> Unit,
) {
    var walletOpen by rememberSaveable { mutableStateOf(false) }
    if (walletOpen) {
        WalletDialog(balance = state.balance, lines = state.wallet, onDismiss = { walletOpen = false })
    }

    // Блоков много и все обязаны поместиться сразу (ТЗ 2.5.3), поэтому шаг
    // между ними меньше обычного.
    FinnyScaffold(
        // Вместо заголовка — кошелёк: сколько монет есть, ребёнок видит
        // первым делом, без инструкции (ТЗ 8.4).
        title = { WalletChip(balance = state.balance, onOpen = { walletOpen = true }) },
        actions = {
            TopIcon(symbol = "?", label = stringResource(R.string.help_action), onClick = onHelp)
            TopIcon(symbol = "🔒", label = stringResource(R.string.adult_action), onClick = onAdult)
        },
        spacing = Dimens.SpaceMedium,
        bottomBar = { DayButtons(step = state.step, onPlan = onPlan, onShop = onShop, onSleep = onFinishDay) },
    ) {
        banner()

        state.event?.let { message ->
            FinnyCard { Text(text = message, style = MaterialTheme.typography.bodyLarge) }
        }

        Bubble(text = state.phrase)
        Pet(state = state)
        GrowthRow(growth = state.growth, onOpen = onProgress)
        PetStats(state = state)
        CoinsRow(jars = state.jars, savings = state.savings, onPlan = onPlan, onSavings = onSavings)

        state.task?.let { task ->
            TaskRow(task = task, onOpen = { onTask(task.id) })
            // ТЗ 2.5.3: с главного доступны задания, а не только задание дня.
            Link(
                text = stringResource(if (task.allDone) R.string.main_task_all_done else R.string.main_tasks_all),
                onOpen = onTasks,
            )
        }
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
        // Вещи сбоку, а не под совой: высота главного не растёт (раздел 8 плана).
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSmall),
        ) {
            Owl(look = state.owl, size = if (low) 120.dp else 170.dp)
            Things(state.things)
        }
        Text(
            text = stringResource(R.string.main_pet_stage, state.petName, stringResource(state.stage.label)),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
    }
}

/** Купленные цели столбиком; озвучиваются одной фразой «Мои вещи: комиксы». */
@Composable
private fun Things(things: List<Thing>) {
    if (things.isEmpty()) return
    val spoken = stringResource(R.string.main_things, things.joinToString { it.title.replaceFirstChar { c -> c.lowercase() } })
    Column(modifier = Modifier.clearAndSetSemantics { contentDescription = spoken }) {
        things.forEach { Text(text = it.icon, style = MaterialTheme.typography.headlineMedium) }
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
 * Кошелёк в шапке — кнопка в «Кошелёк сегодня». Подложка показывает, что
 * это кнопка, не только цветом, а формой (ТЗ 3.6).
 */
@Composable
private fun WalletChip(balance: Coins, onOpen: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(Dimens.Corner))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.wallet_open), onClick = onOpen)
            .defaultMinSize(minHeight = Dimens.TouchTarget)
            .padding(horizontal = Dimens.SpaceMedium),
    ) {
        MoneyAmount(amount = balance)
    }
}

/**
 * «Кошелёк сегодня»: у каждого начисления и списания источник и сумма
 * (ТЗ 2.5.4). Окно, а не выезжающая панель: у окна нет анимации, которую
 * пришлось бы отдельно выключать настройкой движения (ТЗ 3.6, AD-8).
 */
@Composable
private fun WalletDialog(balance: Coins, lines: List<WalletLine>, onDismiss: () -> Unit) {
    FinnyDialog(
        title = stringResource(R.string.wallet_title, balance.amount),
        onDismiss = onDismiss,
        buttons = { FinnyButton(text = stringResource(R.string.action_ok), onClick = onDismiss) },
    ) {
        lines.forEach { line ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSmall),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics(mergeDescendants = true) {},
            ) {
                Text(text = walletLabel(line), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(
                    text = if (line.delta >= 0) "+${line.delta}" else "−${-line.delta}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

/** Покупка называется товаром — «Каша»; остальное — источником, с целью, если она есть. */
@Composable
private fun walletLabel(line: WalletLine): String {
    val source = stringResource(
        when (line.type) {
            null -> R.string.wallet_carry_over
            TransactionType.INCOME_PERIOD -> R.string.wallet_income
            TransactionType.INCOME_TASK -> R.string.wallet_task
            TransactionType.INCOME_PARENT -> R.string.wallet_parent
            TransactionType.INCOME_GIFT -> R.string.wallet_gift
            TransactionType.PURCHASE_MANDATORY, TransactionType.PURCHASE_OPTIONAL -> R.string.wallet_purchase
            TransactionType.SAVINGS_DEPOSIT -> R.string.wallet_to_savings
            TransactionType.SAVINGS_WITHDRAW -> R.string.wallet_from_savings
            TransactionType.UNEXPECTED_EXPENSE -> R.string.wallet_unexpected
            TransactionType.EVENT_CARE -> R.string.wallet_care_event
            TransactionType.GOAL_PURCHASE -> R.string.wallet_goal_change
        },
    )
    val name = line.name ?: return source
    return when (line.type) {
        TransactionType.PURCHASE_MANDATORY, TransactionType.PURCHASE_OPTIONAL -> name
        else -> stringResource(R.string.wallet_named, source, name)
    }
}

/**
 * Строка роста — дорога в «Мой прогресс» (ТЗ 2.5.10): сколько очков и до
 * какой стадии. Полоса нейтрального цвета, как у показателей.
 */
@Composable
private fun GrowthRow(growth: GrowthView?, onOpen: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSmall),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.Corner))
            .clickable(role = Role.Button, onClick = onOpen)
            .defaultMinSize(minHeight = Dimens.TouchTarget)
            .semantics(mergeDescendants = true) {},
    ) {
        Text(text = "⭐", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.clearAndSetSemantics {})
        if (growth == null) {
            Text(
                text = stringResource(R.string.main_growth_done),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
        } else {
            Text(
                text = stringResource(
                    if (growth.next == GrowthStage.GROWN) R.string.main_growth_to_grown else R.string.main_growth_to_young,
                ),
                style = MaterialTheme.typography.bodyLarge,
            )
            ProgressLine(
                fraction = growth.points.toFloat() / growth.target,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.main_growth_points, growth.points, growth.target),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        Chevron()
    }
}

/**
 * Строка «Монеты»: сколько по плану осталось в банках нужного и желаемого,
 * и копилка с целью (ТЗ 2.5.3). Строка — дорога в план, банка копилки — в
 * копилку. Копилка отдельной строкой: три банки в ряд при 16 sp на 360 dp
 * не помещаются.
 */
@Composable
private fun CoinsRow(jars: JarsLeft?, savings: SavingsView, onPlan: () -> Unit, onSavings: () -> Unit) {
    Text(text = stringResource(R.string.main_coins), style = MaterialTheme.typography.titleMedium)
    FinnyCard(onClick = onPlan) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMedium),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (jars == null) {
                Text(
                    text = stringResource(R.string.main_coins_unplanned),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            } else {
                JarLeft(category = SpendCategory.MANDATORY, left = jars.mandatory)
                JarLeft(category = SpendCategory.OPTIONAL, left = jars.optional, modifier = Modifier.weight(1f))
            }
            Chevron()
        }
        SavingsJar(savings = savings, onOpen = onSavings)
    }
}

/** «🥣 ещё 38»: направление иконкой и цветом, для TalkBack — словом (ТЗ 3.6). */
@Composable
private fun JarLeft(category: SpendCategory, left: Coins, modifier: Modifier = Modifier) {
    val spoken = stringResource(R.string.main_jar_left_description, stringResource(category.label), left.amount)
    Text(
        text = category.icon + " " + stringResource(R.string.main_jar_left, left.amount),
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.SemiBold,
        color = category.color,
        modifier = modifier.clearAndSetSemantics { contentDescription = spoken },
    )
}

/**
 * Копилка и цель. Цели может не быть — ребёнок ещё не выбрал; отложенные
 * монеты всё равно видны, а строка зовёт выбрать цель.
 */
@Composable
private fun SavingsJar(savings: SavingsView, onOpen: () -> Unit) {
    val title = savings.goalTitle
    val price = savings.price
    val saved = savings.saved.amount
    val (shown, spoken) = if (title != null && price != null) {
        stringResource(R.string.main_jar_goal, title, saved, price.amount) to
            stringResource(R.string.main_jar_goal_description, title, saved, price.amount)
    } else {
        stringResource(R.string.main_jar_no_goal, saved) to stringResource(R.string.main_jar_no_goal_description, saved)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSmall),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.Corner))
            .clickable(role = Role.Button, onClick = onOpen)
            .defaultMinSize(minHeight = Dimens.TouchTarget)
            .clearAndSetSemantics { contentDescription = spoken },
    ) {
        Text(
            text = SpendCategory.SAVINGS.icon + " " + shown,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = SpendCategory.SAVINGS.color,
            modifier = Modifier.weight(1f),
        )
        Chevron()
    }
}

/**
 * Задание дня (ТЗ 2.5.3): тема, начало вступления и награда. Награду уже
 * получили — вместо «+10» галочка, TalkBack читает её словами.
 */
@Composable
private fun TaskRow(task: TaskOfDay, onOpen: () -> Unit) {
    val reward = stringResource(if (task.rewardAvailable) R.string.main_task_reward else R.string.main_task_reward_taken)
    FinnyCard(onClick = onOpen) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSmall),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = "🎯", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.clearAndSetSemantics {})
            Text(
                text = stringResource(R.string.main_task),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (task.rewardAvailable) "+${task.reward.amount}" else "✓",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clearAndSetSemantics { contentDescription = reward },
            )
            Chevron()
        }
        Text(
            text = stringResource(R.string.main_task_intro, stringResource(task.topic.label), task.intro),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** «›» — строка нажимается; озвучке он не нужен, у строки роль кнопки. */
@Composable
private fun Chevron() {
    Text(
        text = "›",
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.clearAndSetSemantics {},
    )
}

/** Порядок как на макете: еда первой — о ней сова просит чаще всего. */
private val STATS = listOf(PetStatKind.SATIETY, PetStatKind.MOOD, PetStatKind.CARE)

/** Ниже этой высоты сова уменьшается (раздел 8 плана); vivo V2111 выше. */
private const val LOW_SCREEN_DP = 730

private val MAIN_BUTTON_HEIGHT = 56.dp
