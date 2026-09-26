package ru.finnypet.app.ui.screens.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
        // Слева сверху на всех остальных экранах — «Назад» (ТЗ 3.6): если
        // положить кошелёк туда же на главном, привычное нажатие в тот же
        // угол открывает его по ошибке вместо ничего (Б23). Поэтому слева —
        // пусто, кошелёк — в действиях справа, рядом со «?» и замком.
        title = {},
        actions = {
            WalletChip(balance = state.balance, onOpen = { walletOpen = true })
            TopIcon(symbol = "?", label = stringResource(R.string.help_action), onClick = onHelp)
            TopIcon(symbol = "🔒", label = stringResource(R.string.adult_action), onClick = onAdult)
        },
        spacing = Dimens.SpaceSmall,
        bottomBar = { DayButtons(step = state.step, onPlan = onPlan, onShop = onShop, onSleep = onFinishDay) },
    ) {
        banner()

        state.event?.let { message ->
            FinnyCard { Text(text = message, style = MaterialTheme.typography.bodyLarge) }
        }

        Pet(state = state)
        GrowthRow(state = state, onOpen = onProgress)
        PetStats(state = state)
        CoinsRow(jars = state.jars, savings = state.savings, onPlan = onPlan, onSavings = onSavings)

        state.task?.let { task ->
            TaskSection(task = task, onOpen = { onTask(task.id) }, onAllTasks = onTasks)
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
    val secondary = if (step == NextStep.Shop) sleep to onSleep else shop to onShop

    // При обычном отступе кнопки в узкой половине строки на 360 dp слово
    // рвётся посреди себя же (Б21): даже сузив боковые поля до минимума,
    // ширина растёт не быстрее шрифта. При системном увеличении шрифта
    // переключаемся на столбик во всю ширину — как везде в игре.
    if (LocalDensity.current.fontScale > 1f) {
        ButtonColumn {
            FinnyButton(text = main, onClick = onMain, modifier = Modifier.heightIn(min = MAIN_BUTTON_HEIGHT))
            FinnySecondaryButton(text = secondary.first, onClick = secondary.second)
        }
    } else {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSmall),
            modifier = Modifier.fillMaxWidth(),
        ) {
            FinnySecondaryButton(
                text = secondary.first,
                onClick = secondary.second,
                contentPadding = DAY_BUTTON_PADDING,
                modifier = Modifier.weight(1f),
            )
            FinnyButton(
                text = main,
                onClick = onMain,
                contentPadding = DAY_BUTTON_PADDING,
                modifier = Modifier
                    .weight(2f)
                    .heightIn(min = MAIN_BUTTON_HEIGHT),
            )
        }
    }
}

/**
 * Сова и её фраза в одном ряду (ТЗ 2.5.9, 2.5.10): облачко раньше стояло
 * отдельной карточкой над совой и добавляло экрану лишнюю строку с отступом
 * (Б5) — сбоку оно занимает место, которое сова и так забирает под себя.
 *
 * У фразы нет ни `maxLines`, ни многоточия: самая длинная фраза дня («ждёт
 * задание») в этой колонке идёт на 7–8 строк — обрезать её значит терять
 * объяснение совы, а ТЗ 2.5.9 требует, чтобы оно было полным. Ряд просто
 * становится выше совы в эти дни — раздел 8 плана явно разрешает прокрутку
 * ради этого, если высоты не хватит.
 */
@Composable
private fun Pet(state: MainState.Ready) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSmall),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Owl(look = state.owl, size = OWL_SIZE)
        Text(
            text = state.phrase,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Things(state.things)
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
 * Три показателя в ряд, без заголовка сверху: у каждого свой значок и
 * подпись, а «Как себя чувствует Финни» над ними дублировало то же самое
 * ещё одной строкой с отступом (Б5). Под потребностью — слово «нужно»:
 * цвет полосы не единственный признак (ТЗ 3.6). Полосы одного нейтрального
 * цвета, чтобы не спорить с цветами направлений трат (раздел 8 плана).
 */
@Composable
private fun PetStats(state: MainState.Ready) {
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
 * Имя, стадия и рост — в одной строке, дорога в «Мой прогресс» (ТЗ 2.5.10).
 * Раньше «Имя · стадия» стояло отдельной строкой под совой — при имени в
 * 20 символов (максимум профиля) вместе с «До подростка», полосой и числом
 * очков это не поместилось бы ни в одну строку (Б5).
 *
 * Слово «До подростка»/«До взрослого» с экрана убрано — имени с весом
 * `weight(1f)` и так может не хватить места на длинное имя, а числа очков
 * подвинуть нельзя. Для TalkBack оно никуда не делось: озвучивается вместе
 * со стадией и прогрессом одной фразой (ТЗ 3.6 — смысл не только по цвету/
 * количеству звёзд).
 */
@Composable
private fun GrowthRow(state: MainState.Ready, onOpen: () -> Unit) {
    val nameStage = stringResource(R.string.main_pet_stage, state.petName, stringResource(state.stage.label))
    val growth = state.growth
    val spoken = if (growth == null) {
        "$nameStage. ${stringResource(R.string.main_growth_done)}"
    } else {
        val toStage = stringResource(
            if (growth.next == GrowthStage.GROWN) R.string.main_growth_to_grown else R.string.main_growth_to_young,
        )
        val points = stringResource(R.string.main_growth_points, growth.points, growth.target)
        "$nameStage. $toStage $points"
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
        // Имя переносится по словам, если не помещается, но не вытесняет
        // звёзды и число очков справа — у них фиксированная ширина.
        Text(
            text = nameStage,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        Text(text = "⭐", style = MaterialTheme.typography.bodyLarge)
        if (growth != null) {
            ProgressLine(
                fraction = growth.points.toFloat() / growth.target,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(GROWTH_BAR_WIDTH),
            )
            Text(
                text = stringResource(R.string.main_growth_points_short, growth.points, growth.target),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        Chevron()
    }
}

/**
 * Сколько по плану осталось в банках нужного и желаемого, и копилка с целью
 * (ТЗ 2.5.3), без заголовка «Монеты» сверху — иконки и подписи банок и так
 * называют деньги, а строка с отступом над карточкой только повторяла это
 * (Б5). Карточка — дорога в план, банка копилки — в копилку. Копилка
 * отдельной строкой: три банки в ряд при 16 sp на 360 dp не помещаются.
 */
@Composable
private fun CoinsRow(jars: JarsLeft?, savings: SavingsView, onPlan: () -> Unit, onSavings: () -> Unit) {
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
 * Задание дня и переход ко всем заданиям рядом, одной строкой (ТЗ 2.5.3).
 * Раньше «Все задания» шли отдельной строкой под карточкой — при их
 * объединении в саму карточку высота почти не менялась (кнопка всё равно
 * не бывает меньше 48 dp), а два разных действия — открыть это задание или
 * список — слились бы в одно. Компактная кнопка сбоку решает высоту, не
 * трогая смысл (Б5).
 */
@Composable
private fun TaskSection(task: TaskOfDay, onOpen: () -> Unit, onAllTasks: () -> Unit) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSmall),
        modifier = Modifier.fillMaxWidth(),
    ) {
        TaskCard(task = task, onOpen = onOpen, modifier = Modifier.weight(1f))
        AllTasksButton(onOpen = onAllTasks)
    }
}

/**
 * Тема задания — в заголовке карточки вместо статичного «Задание дня»:
 * задание должно быть узнаваемо (ТЗ 2.5.3), а не просто присутствовать.
 * Вступление задания сюда больше не выводится — со свободным по высоте
 * облачком совы над рядом с фразой дня оно бы дублировало текст задания.
 *
 * Все задания пройдены — это состояние всей игры, а не конкретного
 * задания, поэтому сообщение об этом строкой в самой карточке, а не в
 * кнопке «Все ›»: полный текст `main_task_all_done` в 48 dp не поместился
 * бы, а без слов ребёнок не поймёт, что произошло (ТЗ 3.6).
 */
@Composable
private fun TaskCard(task: TaskOfDay, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val reward = stringResource(if (task.rewardAvailable) R.string.main_task_reward else R.string.main_task_reward_taken)
    FinnyCard(onClick = onOpen, modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSmall),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = "🎯", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.clearAndSetSemantics {})
            Text(
                text = stringResource(task.topic.label),
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
        if (task.allDone) {
            Text(text = stringResource(R.string.main_task_all_done), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/**
 * Компактный переход ко всем заданиям — словом, а не только значком
 * (ТЗ 3.6): ребёнку 7 лет значок без подписи не говорит, что он делает, а
 * полная подпись для озвучки зрячему не помогает.
 */
@Composable
private fun AllTasksButton(onOpen: () -> Unit) {
    val label = stringResource(R.string.main_tasks_all)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSmall),
        modifier = Modifier
            .clip(RoundedCornerShape(Dimens.Corner))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(role = Role.Button, onClick = onOpen)
            .defaultMinSize(minWidth = Dimens.TouchTarget, minHeight = Dimens.TouchTarget)
            .padding(horizontal = Dimens.SpaceSmall)
            .clearAndSetSemantics { contentDescription = label },
    ) {
        Text(text = stringResource(R.string.main_tasks_short), style = MaterialTheme.typography.bodyLarge)
        Chevron()
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

/**
 * Сова на главном всегда этого размера (раздел 8 плана). На vivo раньше
 * стояла сова 170 dp — уменьшена ради высоты экрана: без остальных правок
 * этого пункта главный не помещался без прокрутки (Б5).
 */
private val OWL_SIZE = 120.dp

/** Ширина полосы роста рядом со звёздами — фиксированная, весь вес у имени. */
private val GROWTH_BAR_WIDTH = 56.dp

private val MAIN_BUTTON_HEIGHT = 56.dp

/** Уже отступа кнопки хватает под «Магазин»/«Уложить спать» в половину строки (Б21). */
private val DAY_BUTTON_PADDING = PaddingValues(horizontal = Dimens.SpaceMedium, vertical = Dimens.SpaceSmall)
