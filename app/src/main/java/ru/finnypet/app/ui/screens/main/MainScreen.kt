package ru.finnypet.app.ui.screens.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.vector.ImageVector
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
import ru.finnypet.app.ui.components.GrowthView
import ru.finnypet.app.ui.components.ItemIcon
import ru.finnypet.app.ui.components.MoneyAmount
import ru.finnypet.app.ui.components.OneWordText
import ru.finnypet.app.ui.components.Owl
import ru.finnypet.app.ui.components.OwlRole
import ru.finnypet.app.ui.components.ProgressLine
import ru.finnypet.app.ui.components.StarMark
import ru.finnypet.app.ui.components.TopSpeechBubble
import ru.finnypet.app.ui.components.coinsText
import ru.finnypet.app.ui.components.color
import ru.finnypet.app.ui.components.container
import ru.finnypet.app.ui.components.direction
import ru.finnypet.app.ui.components.fill
import ru.finnypet.app.ui.components.goalFraction
import ru.finnypet.app.ui.components.goalOverfilled
import ru.finnypet.app.ui.components.icon
import ru.finnypet.app.ui.components.icons.FinnyIcons
import ru.finnypet.app.ui.components.label
import ru.finnypet.app.ui.components.needLabel
import ru.finnypet.app.ui.components.starsText
import ru.finnypet.app.ui.sound.Sound
import ru.finnypet.app.ui.sound.SoundOnce
import ru.finnypet.app.ui.theme.Dimens
import ru.finnypet.app.ui.theme.FinnyTheme

/**
 * Главный экран (ТЗ 2.5.3): питомец, баланс, накопления, цель, показатели
 * состояния и задание дня видны одновременно, без переходов.
 *
 * Переход в раздел для взрослого появится вместе с этим экраном: кнопка,
 * ведущая в пустоту, — тупик, а ТЗ 3.4 их запрещает.
 *
 * Обучение (ТЗ 2.5.1, DESIGN_PLAN 3.4) — слой поверх этого же экрана.
 * [startTutorial] берётся один раз как начальное значение, дальше шаг живёт
 * в сохраняемом состоянии экрана: поворот и возврат с других экранов не
 * запускают обучение заново, а «?» открывает его с первого шага.
 */
@Composable
fun MainScreen(
    startTutorial: Boolean,
    onPlan: () -> Unit,
    onProgress: () -> Unit,
    onAdult: () -> Unit,
    onShop: () -> Unit,
    onSavings: () -> Unit,
    onTask: (TaskId) -> Unit,
    onTasks: () -> Unit,
    onFinishDay: () -> Unit,
    banner: @Composable () -> Unit = {},
    onShown: () -> Unit = {},
    viewModel: MainViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (state !is MainState.Loading) SideEffect(onShown)
    var tutorialStep by rememberSaveable { mutableStateOf(if (startTutorial) 0 else null) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    if (settingsOpen) SettingsDialog(onDismiss = { settingsOpen = false })

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
        onHelp = { tutorialStep = 0 },
        onSettings = { settingsOpen = true },
        onAdult = onAdult,
        banner = banner,
        tutorialStep = tutorialStep,
        onTutorialStep = { tutorialStep = it },
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
    onSettings: () -> Unit = {},
    onAdult: () -> Unit = {},
    banner: @Composable () -> Unit = {},
    /** Шаг обучения поверх экрана; `null` — обучения нет. */
    tutorialStep: Int? = null,
    onTutorialStep: (Int?) -> Unit = {},
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
            onSettings = onSettings,
            onAdult = onAdult,
            banner = banner,
            tutorialStep = tutorialStep,
            onTutorialStep = onTutorialStep,
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
    onSettings: () -> Unit,
    onAdult: () -> Unit,
    banner: @Composable () -> Unit,
    tutorialStep: Int?,
    onTutorialStep: (Int?) -> Unit,
) {
    var walletOpen by rememberSaveable { mutableStateOf(false) }
    if (walletOpen) {
        WalletDialog(balance = state.balance, lines = state.wallet, onDismiss = { walletOpen = false })
    }
    val targets = remember { TutorialTargets() }

    Box(modifier = Modifier.fillMaxSize()) {
        MainLayout(
            state = state,
            targets = targets,
            // Под обучением экран скрыт от TalkBack: слой модальный, иначе
            // озвучка уводила бы на кнопки, нажать которые сейчас нельзя.
            modifier = if (tutorialStep != null) Modifier.clearAndSetSemantics {} else Modifier,
            owlSilent = tutorialStep != null,
            onWallet = { walletOpen = true },
            onPlan = onPlan,
            onShop = onShop,
            onSavings = onSavings,
            onTask = onTask,
            onTasks = onTasks,
            onFinishDay = onFinishDay,
            onProgress = onProgress,
            onHelp = onHelp,
            onSettings = onSettings,
            onAdult = onAdult,
            banner = banner,
        )
        if (tutorialStep != null) {
            TutorialOverlay(
                step = tutorialStep,
                text = state.tutorial.getOrElse(tutorialStep) { "" },
                owl = state.owl,
                targets = targets,
                onNext = { onTutorialStep(nextTutorialStep(tutorialStep)) },
                onSkip = { onTutorialStep(null) },
            )
        }
    }
}

/** Сам главный — под слоем обучения он тот же, только молчит для TalkBack. */
@Composable
private fun MainLayout(
    state: MainState.Ready,
    targets: TutorialTargets,
    modifier: Modifier,
    owlSilent: Boolean,
    onWallet: () -> Unit,
    onPlan: () -> Unit,
    onShop: () -> Unit,
    onSavings: () -> Unit,
    onTask: (TaskId) -> Unit,
    onTasks: () -> Unit,
    onFinishDay: () -> Unit,
    onProgress: () -> Unit,
    onHelp: () -> Unit,
    onSettings: () -> Unit,
    onAdult: () -> Unit,
    banner: @Composable () -> Unit,
) {
    // Блоков много и все обязаны поместиться сразу (ТЗ 2.5.3), поэтому шаг
    // между ними меньше обычного.
    FinnyScaffold(
        modifier = modifier,
        // Кошелёк — слева, как заголовок (DESIGN_PLAN 3.1). Раньше он стоял
        // справа именно затем, чтобы не совпасть с местом «Назад» на других
        // экранах (Б23); теперь это осознанный выбор владельца — ошибочное
        // нажатие лишь откроет безобидное окно «Кошелёк сегодня», а не уводит
        // с экрана, поэтому цена совпадения ниже, чем была у настоящей навигации.
        title = {
            WalletChip(
                balance = state.balance,
                onOpen = onWallet,
                modifier = Modifier.tutorialTarget(targets, TutorialTarget.WALLET),
            )
        },
        actions = {
            banner()
            TopIcon(icon = FinnyIcons.Help, label = stringResource(R.string.help_action), onClick = onHelp)
            TopIcon(
                icon = FinnyIcons.Settings,
                label = stringResource(R.string.settings_title),
                onClick = onSettings,
                modifier = Modifier.tutorialTarget(targets, TutorialTarget.SETTINGS),
            )
            TopIcon(icon = FinnyIcons.Grownup, label = stringResource(R.string.adult_action), onClick = onAdult)
        },
        spacing = Dimens.SpaceSmall,
        verticalPadding = Dimens.SpaceSmall,
        bottomBar = {
            DayButtons(
                step = state.step,
                task = state.task,
                targets = targets,
                onTask = { state.task?.let { onTask(it.id) } },
                onPlan = onPlan,
                onShop = onShop,
                onSleep = onFinishDay,
            )
        },
    ) {
        // Под обучением говорит сова обучения; облачко главного лишь
        // прозрачно, а не убрано — иначе раскладка съехала бы и вырезы
        // обучения указывали бы не туда, а стрелка к кошельку шла бы сквозь текст.
        TopSpeechBubble(text = state.phrase, modifier = Modifier.alpha(if (owlSilent) 0f else 1f))
        // Раз на показ фразы: возврат с задания её не повторяет — состояние
        // главного в стеке переходов сохраняется, — а к следующему утру
        // фраза успевает смениться, и звук снова новый.
        if (state.incomeArrived) SoundOnce(Sound.COINS)
        // Имя прижато к сове (4 dp, бюджет высот DESIGN_PLAN 3.1): низ рамки
        // совы и так занят полянкой, а каждый десяток точек нужен плиткам.
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceTiny)) {
            OwlWithThings(state = state)
            PetNameStage(state = state)
        }
        PetStats(state = state, targets = targets)
        TileGrid(
            state = state,
            targets = targets,
            onPlan = onPlan,
            onSavings = onSavings,
            onProgress = onProgress,
            onTasks = onTasks,
        )
    }
}

/**
 * Вход в подсказку, настройки или раздел для взрослого — значок 48 dp в
 * шапке. Значок для озвучки молчит, TalkBack читает подпись: «?», шестерёнка
 * и замок сами по себе ничего не говорят (ТЗ 3.6).
 */
@Composable
private fun TopIcon(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(
        onClick = onClick,
        modifier = modifier
            .defaultMinSize(minWidth = Dimens.TouchTarget, minHeight = Dimens.TouchTarget)
            .semantics { contentDescription = label },
    ) {
        Icon(imageVector = icon, contentDescription = null)
    }
}

/**
 * Главная кнопка идёт по фазе дня (DESIGN_PLAN 3.1, правка владельца №2):
 * пока день не спланирован и за задание ещё дают монеты — задание первым,
 * план — второстепенной; без награды — план один, без второй кнопки:
 * магазин в эту фазу не открывается, он и сейчас просит сначала
 * спланировать (`PlanningDialog`). После плана — как раньше: «В магазин» /
 * «Уложить спать» и вторая кнопка навстречу.
 */
@Composable
private fun DayButtons(
    step: NextStep,
    task: TaskOfDay?,
    targets: TutorialTargets,
    onTask: () -> Unit,
    onPlan: () -> Unit,
    onShop: () -> Unit,
    onSleep: () -> Unit,
) {
    // Повтор пройденного: «Выполнить задание +10» рядом с плиткой «6 из 6»
    // выглядело ошибкой (ревью F4-fix). Разбор — не повтор, даже когда все пройдены.
    val taskText = stringResource(if (task?.repeat == true) R.string.main_task_repeat_action else R.string.main_task_action)
    val planText = stringResource(R.string.budget_action_plan)
    val shopText = stringResource(R.string.shop_action)
    val sleepText = stringResource(R.string.main_action_sleep)

    if (step == NextStep.Plan) {
        FinnyButton(
            text = planText,
            onClick = onPlan,
            modifier = Modifier
                .heightIn(min = MAIN_BUTTON_HEIGHT)
                .tutorialTarget(targets, TutorialTarget.PLAN_BUTTON),
        )
        return
    }

    val main: String
    val onMain: () -> Unit
    val reward: Coins?
    val secondary: String
    val onSecondary: () -> Unit
    when (step) {
        NextStep.Task -> {
            main = taskText; onMain = onTask; reward = task?.reward?.takeIf { task.rewardAvailable }
            secondary = planText; onSecondary = onPlan
        }
        NextStep.Shop -> {
            main = shopText; onMain = onShop; reward = null
            secondary = sleepText; onSecondary = onSleep
        }
        else -> {
            main = sleepText; onMain = onSleep; reward = null
            secondary = shopText; onSecondary = onShop
        }
    }

    // Столбик во всю ширину, а не пара в строке (Б21, ужесточено этим
    // коммитом): бюджет высот главного (DESIGN_PLAN 3.1) считает кнопки как
    // «56 + 8 + 48» — это уже столбик, не строка. В узкой половине строки
    // «Уложить спать»/«Спланировать день» переносятся по слову, а чип
    // награды — по цифре за скругление кнопки (проверено на vivo V2111,
    // 384 dp — шире эталонных 360 dp, но и там не помещается); в столбик обе
    // кнопки к тому же одной ширины, а не визуально разного размера.
    // Обучению нужны кнопки задания и плана — вместе они есть только утром (DESIGN_PLAN 3.4).
    val morning = step == NextStep.Task
    ButtonColumn {
        FinnyButton(
            text = main,
            onClick = onMain,
            reward = reward,
            modifier = Modifier
                .heightIn(min = MAIN_BUTTON_HEIGHT)
                .then(if (morning) Modifier.tutorialTarget(targets, TutorialTarget.TASK_BUTTON) else Modifier),
        )
        FinnySecondaryButton(
            text = secondary,
            onClick = onSecondary,
            modifier = if (morning) Modifier.tutorialTarget(targets, TutorialTarget.PLAN_BUTTON) else Modifier,
        )
    }
}

/**
 * Сова в центре на полянке (DESIGN_PLAN 3.1): реплика теперь отдельным
 * блоком над ней ([TopSpeechBubble]), а купленные цели стоят рядом, не
 * занимая отдельной строки — блок совы и так самый высокий на экране.
 */
@Composable
private fun OwlWithThings(state: MainState.Ready) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Owl(look = state.owl, size = OwlRole.Hero.size)
        if (state.things.isNotEmpty()) {
            Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                Things(state.things)
            }
        }
    }
}

/** Купленные цели столбиком; озвучиваются одной фразой «Мои вещи: комиксы». */
@Composable
private fun Things(things: List<Thing>) {
    val spoken = stringResource(R.string.main_things, things.joinToString { it.title.replaceFirstChar { c -> c.lowercase() } })
    Column(modifier = Modifier.clearAndSetSemantics { contentDescription = spoken }) {
        things.forEach { ItemIcon(icon = it.icon, category = SpendCategory.SAVINGS) }
    }
}

/**
 * Имя и стадия — одна строка по центру под совой (DESIGN_PLAN 3.1): звёзды
 * и полоса роста переехали в плитку «Рост», иначе имя длиной в 20 символов
 * (максимум профиля) вместе с ними не поместилось бы в строку (Б5). Для
 * TalkBack рост никуда не делся — читается вместе со стадией одной фразой.
 */
@Composable
private fun PetNameStage(state: MainState.Ready) {
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
    Text(
        text = nameStage,
        style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = spoken },
    )
}

/**
 * Три показателя в ряд, без заголовка сверху: у каждого свой значок и
 * подпись, а «Как себя чувствует Финни» над ними дублировало то же самое
 * ещё одной строкой с отступом (Б5). Метка потребности — чип на
 * need-контейнере направления, согласованный по роду («нужна»/«нужен»,
 * DESIGN_PLAN 3.1): цвет полосы не единственный признак (ТЗ 3.6).
 */
@Composable
private fun PetStats(state: MainState.Ready, targets: TutorialTargets) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMedium),
        modifier = Modifier.fillMaxWidth(),
    ) {
        STATS.forEach { kind ->
            PetStat(
                kind = kind,
                stat = state.stats.statFor(kind),
                needed = kind in state.needs,
                modifier = Modifier
                    .weight(1f)
                    .tutorialTarget(targets, kind.tutorialTarget),
            )
        }
    }
}

@Composable
private fun PetStat(kind: PetStatKind, stat: Stat, needed: Boolean, modifier: Modifier) {
    val label = stringResource(kind.label)
    val need = stringResource(kind.needLabel)
    val value = stringResource(R.string.stat_description, label, stat.value, Stat.RANGE.last)
    val spoken = if (needed) "$value, $need" else value
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceTiny),
        modifier = modifier.clearAndSetSemantics { contentDescription = spoken },
    ) {
        // Значок слева от полосы, а не над ней: так ряд показателей ниже на 24 dp,
        // и на 360 dp нижний ряд плиток не уходит под кнопки (F8).
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceTiny),
        ) {
            Icon(imageVector = kind.icon, contentDescription = null, tint = kind.direction.fill, modifier = Modifier.size(JAR_ICON))
            // Цвет направления, которое показатель пополняет (DESIGN_PLAN 2.1):
            // еда и уход — «Нужное», радость — «Желаемое».
            ProgressLine(
                fraction = stat.value.toFloat() / Stat.RANGE.last,
                color = kind.direction.fill,
                modifier = Modifier.weight(1f),
            )
        }
        // В трети 360 dp при шрифте 2,0 «Радость» иначе рвалась посреди слова (F8).
        OneWordText(text = label, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        if (needed) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(Dimens.Corner))
                    .background(kind.direction.container)
                    .padding(horizontal = Dimens.SpaceSmall, vertical = CHIP_PADDING),
            ) {
                // Чип — стилем меток (DESIGN_PLAN 2.2, 16/20): ниже строки
                // текста, и ряд показателей не выталкивает плитки под кнопки.
                OneWordText(
                    text = need,
                    style = MaterialTheme.typography.labelMedium,
                    color = kind.direction.color,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * Кошелёк в шапке — кнопка в «Кошелёк сегодня». Подложка показывает, что
 * это кнопка, не только цветом, а формой (ТЗ 3.6).
 */
@Composable
private fun WalletChip(balance: Coins, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(Dimens.Corner))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.wallet_open), onClick = onOpen)
            .defaultMinSize(minHeight = Dimens.TouchTarget)
            .padding(horizontal = Dimens.SpaceMedium),
    ) {
        // Без слова «Кошелёк» TalkBack читал одно «80 монет» — непонятно, чьих.
        val spoken = stringResource(R.string.wallet_description, coinsText(balance))
        Box(modifier = Modifier.clearAndSetSemantics { contentDescription = spoken }) {
            MoneyAmount(amount = balance)
        }
    }
}

/**
 * «Кошелёк сегодня»: у каждого начисления и списания источник и сумма
 * (ТЗ 2.5.4). Окно, а не выезжающая панель: у окна нет анимации, которую
 * пришлось бы отдельно выключать настройкой движения (ТЗ 3.6, AD-8).
 *
 * Сумма — `displaySmall` (DESIGN_PLAN 3.1), крупнее обычного заголовка
 * диалога; «+» — цветом «Нужного» (`need.text`), «−» — приглушённым
 * `onSurfaceVariant`, а не красным: расход — не ошибка (ТЗ 3.5).
 */
@Composable
private fun WalletDialog(balance: Coins, lines: List<WalletLine>, onDismiss: () -> Unit) {
    FinnyDialog(
        title = { Text(text = stringResource(R.string.wallet_title, balance.amount), style = MaterialTheme.typography.displaySmall) },
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
                    color = if (line.delta >= 0) FinnyTheme.palette.need.text else MaterialTheme.colorScheme.onSurfaceVariant,
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
 * Плитки 2 × 2 (DESIGN_PLAN 3.1): план и копилка сверху, рост и задания
 * снизу. Каждая нажимается целиком и ведёт на свой экран.
 *
 * Плитки ряда одной высоты — по самой высокой: три строки банок плана при
 * системном шрифте чуть крупнее обычного перерастали копилку на ~5 dp, и
 * ряд выглядел неровным (ревью F5). Высоту ряда это не меняет.
 */
@Composable
private fun TileGrid(
    state: MainState.Ready,
    targets: TutorialTargets,
    onPlan: () -> Unit,
    onSavings: () -> Unit,
    onProgress: () -> Unit,
    onTasks: () -> Unit,
) {
    // При крупном шрифте — столбец во всю ширину, как товары магазина: в
    // половине 360 dp подписи плиток рвались посреди слова, а «2 из 6»
    // уходило под кнопки (F8).
    if (LocalDensity.current.fontScale > Dimens.WIDE_FONT_SCALE) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSmall)) {
            PlanTile(
                jars = state.jars?.shownWithin(state.balance),
                onOpen = onPlan,
                modifier = Modifier.fillMaxWidth().tutorialTarget(targets, TutorialTarget.PLAN_TILE),
            )
            SavingsTile(
                savings = state.savings,
                onOpen = onSavings,
                modifier = Modifier.fillMaxWidth().tutorialTarget(targets, TutorialTarget.SAVINGS_TILE),
            )
            GrowthTile(
                grownMessage = state.grownMessage,
                growthPoints = state.growthPoints,
                growth = state.growth,
                onOpen = onProgress,
                modifier = Modifier.fillMaxWidth(),
            )
            TasksTile(
                task = state.task,
                onOpen = onTasks,
                modifier = Modifier.fillMaxWidth().tutorialTarget(targets, TutorialTarget.TASKS_TILE),
            )
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSmall)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSmall), modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            PlanTile(
                jars = state.jars?.shownWithin(state.balance),
                onOpen = onPlan,
                modifier = Modifier.weight(1f).fillMaxHeight().tutorialTarget(targets, TutorialTarget.PLAN_TILE),
            )
            SavingsTile(
                savings = state.savings,
                onOpen = onSavings,
                modifier = Modifier.weight(1f).fillMaxHeight().tutorialTarget(targets, TutorialTarget.SAVINGS_TILE),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSmall), modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            GrowthTile(
                grownMessage = state.grownMessage,
                growthPoints = state.growthPoints,
                growth = state.growth,
                onOpen = onProgress,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            TasksTile(
                task = state.task,
                onOpen = onTasks,
                modifier = Modifier.weight(1f).fillMaxHeight().tutorialTarget(targets, TutorialTarget.TASKS_TILE),
            )
        }
    }
}

/** Сколько по плану ещё осталось на нужное, желаемое и копилку (три мини-банки). */
@Composable
private fun PlanTile(jars: JarsLeft?, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val unplanned = stringResource(R.string.main_coins_unplanned)
    val spoken = if (jars == null) {
        unplanned
    } else {
        listOf(
            SpendCategory.MANDATORY to jars.mandatory,
            SpendCategory.OPTIONAL to jars.optional,
            SpendCategory.SAVINGS to jars.savings,
        ).map { (category, left) ->
            stringResource(R.string.main_jar_left_description, stringResource(category.label), left.amount)
        }.joinToString(". ")
    }
    FinnyCard(
        modifier = modifier
            .clickable(role = Role.Button, onClick = onOpen)
            .defaultMinSize(minHeight = TILE_HEIGHT)
            .clearAndSetSemantics { contentDescription = spoken },
    ) {
        if (jars == null) {
            Text(text = unplanned, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            // Строки банок вплотную, без общего шага карточки: с ним плитка
            // вырастала на 16 dp выше соседней и уводила нижний ряд под кнопки.
            Column {
                JarLeft(category = SpendCategory.MANDATORY, left = jars.mandatory)
                JarLeft(category = SpendCategory.OPTIONAL, left = jars.optional)
                JarLeft(category = SpendCategory.SAVINGS, left = jars.savings)
            }
        }
    }
}

/**
 * Иконка направления и «осталось 38»: цветом и словом, для TalkBack — словом (ТЗ 3.6).
 * Строка — стилем меток (16/20) с иконкой 20 dp: три строки по 24 dp делали
 * плитку плана выше соседней на 12 dp, и вечером, с чипом «нужен» и репликой
 * в три строки, нижний ряд плиток упирался в кнопки (ревью F2-fix).
 */
@Composable
private fun JarLeft(category: SpendCategory, left: Coins, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceTiny),
        modifier = modifier,
    ) {
        Icon(imageVector = category.icon, contentDescription = null, tint = category.fill, modifier = Modifier.size(JAR_ICON))
        Text(
            text = stringResource(R.string.main_jar_left, left.amount),
            style = MaterialTheme.typography.labelMedium,
            color = category.color,
        )
    }
}

/**
 * Копилка и цель: эмодзи цели на тарелке и мини-полоса «6/40» («Хватает!»,
 * если накоплено больше цены, — коротко, чтобы влезть в одну строку и не
 * сделать плитку выше соседней плитки плана; полная фраза — в озвучке) — без
 * заголовка словами, эмодзи и так узнаётся (DESIGN_PLAN 3.1). Цели может не
 * быть — отложенные монеты всё равно видны в описании для TalkBack, строка
 * зовёт выбрать цель.
 */
@Composable
private fun SavingsTile(savings: SavingsView, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val title = savings.goalTitle
    val price = savings.price
    val saved = savings.saved.amount
    val overfilled = price != null && goalOverfilled(savings.saved, price)
    val spoken = if (title != null && price != null) {
        if (overfilled) {
            stringResource(R.string.main_jar_goal_enough_description, title, saved)
        } else {
            stringResource(R.string.main_jar_goal_description, title, saved, price.amount)
        }
    } else {
        stringResource(R.string.main_jar_no_goal_description, saved)
    }
    FinnyCard(
        modifier = modifier
            .clickable(role = Role.Button, onClick = onOpen)
            .defaultMinSize(minHeight = TILE_HEIGHT)
            .clearAndSetSemantics { contentDescription = spoken },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSmall),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (savings.goalIcon != null) {
                ItemIcon(icon = savings.goalIcon, category = SpendCategory.SAVINGS)
            } else {
                Icon(imageVector = SpendCategory.SAVINGS.icon, contentDescription = null, tint = SpendCategory.SAVINGS.fill)
            }
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceTiny), modifier = Modifier.weight(1f)) {
                if (title != null && price != null) {
                    ProgressLine(fraction = goalFraction(savings.saved, price), color = SpendCategory.SAVINGS.fill)
                    Text(
                        text = if (overfilled) {
                            stringResource(R.string.main_jar_goal_enough)
                        } else {
                            stringResource(R.string.main_growth_points_short, saved, price.amount)
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = SpendCategory.SAVINGS.color,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.main_jar_no_goal),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = SpendCategory.SAVINGS.color,
                    )
                }
            }
        }
    }
}

/**
 * Звёзды до следующей стадии (заработанные залиты) и подпись «до
 * подростка» — переехали сюда из строки имени (DESIGN_PLAN 3.1). Взрослый —
 * последняя стадия: «{имя} вырос!» из контент-пака (ключ `growth.grown`) и
 * общее число звёзд без полосы, ведёт туда же, в «Мой прогресс».
 */
@Composable
private fun GrowthTile(
    grownMessage: String,
    growthPoints: Int,
    growth: GrowthView?,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spoken = if (growth == null) {
        "$grownMessage ${starsText(growthPoints)}"
    } else {
        val toStage = stringResource(
            if (growth.next == GrowthStage.GROWN) R.string.main_growth_to_grown else R.string.main_growth_to_young,
        )
        "$toStage ${stringResource(R.string.main_growth_points, growth.points, growth.target)}"
    }
    FinnyCard(
        modifier = modifier
            .clickable(role = Role.Button, onClick = onOpen)
            .defaultMinSize(minHeight = TILE_HEIGHT)
            .clearAndSetSemantics { contentDescription = spoken },
    ) {
        if (growth == null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSmall),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(imageVector = FinnyIcons.StarFilled, contentDescription = null, tint = FinnyTheme.palette.star)
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceTiny), modifier = Modifier.weight(1f)) {
                    Text(text = grownMessage, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = growthPoints.toString(),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        } else {
            // Звёзды кусочками, как в итогах и «Моём прогрессе»: сосчитать их
            // проще, чем оценить долю полосы. Мельче, чем в итогах, чтобы шесть
            // уместились в половину 360 dp одной строкой.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(TILE_STAR_GAP),
                verticalArrangement = Arrangement.spacedBy(TILE_STAR_GAP),
            ) {
                repeat(growth.target) { index ->
                    StarMark(filled = index < growth.points, modifier = Modifier.size(TILE_STAR))
                }
            }
            Text(
                text = stringResource(
                    if (growth.next == GrowthStage.GROWN) R.string.main_growth_to_grown else R.string.main_growth_to_young,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/**
 * Подпись «Задания», «2 из 6» и чип «+10» (пока за задание дают монеты, в
 * любой фазе дня — ТЗ 2.5.3). Иконка своя, а не темы задания дня: у темы
 * «Накопления» это копилка, и плитка выглядела бы второй копилкой рядом с
 * настоящей. Плитка ведёт в список заданий; выполнить задание дня можно
 * главной кнопкой внизу, пока она — «Выполнить задание» ([DayButtons]).
 *
 * Все задания пройдены — это состояние всей игры (полный текст
 * `main_task_all_done` в описании для TalkBack), а не конкретного задания,
 * поэтому визуально плитка просто показывает «N из N» и галочку.
 */
@Composable
private fun TasksTile(task: TaskOfDay?, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    if (task == null) return
    val title = stringResource(R.string.tasks_title)
    val progress = stringResource(R.string.main_tasks_progress, task.completedCount, task.totalCount)
    val reward = tileRewardText(task)?.let { " ${stringResource(it)}" }.orEmpty()
    val spoken = if (task.allDone) {
        val repeat = if (task.rewardAvailable) reward else ""
        "$title: $progress. ${stringResource(R.string.main_task_all_done)}$repeat"
    } else {
        "$title: $progress.$reward"
    }
    FinnyCard(
        modifier = modifier
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.main_tasks_all), onClick = onOpen)
            .defaultMinSize(minHeight = TILE_HEIGHT)
            .clearAndSetSemantics { contentDescription = spoken },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSmall),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(imageVector = FinnyIcons.Tasks, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            // «+10» и галочка — в строке с «2 из 6», а не отдельным столбцом справа:
            // столбец отнимал у «Задания» ширину, и на 360 dp слово рвалось (F8).
            // FlowRow переносит «+10» целиком, если строке тесно.
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceTiny), modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.bodyMedium)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSmall),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = progress,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (task.rewardAvailable) {
                        Text(
                            text = "+${task.reward.amount}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    } else if (task.limitReached) {
                        Icon(imageVector = FinnyIcons.Check, contentDescription = null)
                    }
                }
            }
        }
    }
}

/**
 * Что плитка говорит про монеты. Галочка «получено» — только при выбранном
 * лимите. После ошибки утром монет не давали, без них лишь задание дня (R8),
 * а плитка ведёт в список, где за другие ещё платят, — там она о монетах
 * молчит: «за это задание монет не будет» сказано в самом задании (ревью F7).
 */
internal fun tileRewardText(task: TaskOfDay): Int? = when {
    task.rewardAvailable -> R.string.main_task_reward
    task.limitReached -> R.string.main_task_reward_taken
    else -> null
}

private val PetStatKind.tutorialTarget: TutorialTarget
    get() = when (this) {
        PetStatKind.SATIETY -> TutorialTarget.SATIETY
        PetStatKind.MOOD -> TutorialTarget.MOOD
        PetStatKind.CARE -> TutorialTarget.CARE
    }

/** Порядок как на макете: еда первой — о ней сова просит чаще всего. */
private val STATS = listOf(PetStatKind.SATIETY, PetStatKind.MOOD, PetStatKind.CARE)

private val MAIN_BUTTON_HEIGHT = 56.dp

private val CHIP_PADDING = 2.dp

private val JAR_ICON = 20.dp

/** Минимальная высота плитки 2 × 2 (DESIGN_PLAN 3.1: бюджет высот главного). */
private val TILE_HEIGHT = 84.dp

/**
 * Звёзды роста на плитке: шесть по 20 dp с зазором 1 dp — 125 dp. На экране 360 dp
 * внутри плитки 128 dp, так что крупнее или шире зазор — и шестая звезда уйдёт
 * на вторую строку. По 16 dp звёзды выглядели бледно.
 */
private val TILE_STAR = 20.dp
private val TILE_STAR_GAP = 1.dp
