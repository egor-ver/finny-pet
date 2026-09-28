package ru.finnypet.app.ui.screens.tasks

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import ru.finnypet.app.R
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.SpendCategory
import ru.finnypet.app.domain.model.TaskTopic
import ru.finnypet.app.ui.components.ButtonColumn
import ru.finnypet.app.ui.components.Coin
import ru.finnypet.app.ui.components.CoinChip
import ru.finnypet.app.ui.components.CoinFlight
import ru.finnypet.app.ui.components.FinnyButton
import ru.finnypet.app.ui.components.FinnyCard
import ru.finnypet.app.ui.components.FinnyScaffold
import ru.finnypet.app.ui.components.FinnySecondaryButton
import ru.finnypet.app.ui.components.ItemIcon
import ru.finnypet.app.ui.components.MoneyAmount
import ru.finnypet.app.ui.components.Owl
import ru.finnypet.app.ui.components.OwlRole
import ru.finnypet.app.ui.components.PlanEditor
import ru.finnypet.app.ui.components.ProgressLine
import ru.finnypet.app.ui.components.RemainderCounter
import ru.finnypet.app.ui.components.SpeechBubble
import ru.finnypet.app.ui.components.StatChangeLine
import ru.finnypet.app.ui.components.tile
import ru.finnypet.app.ui.components.TopSpeechBubble
import ru.finnypet.app.ui.components.coinsText
import ru.finnypet.app.ui.components.colors
import ru.finnypet.app.ui.components.icons.FinnyIcons
import ru.finnypet.app.ui.components.label
import ru.finnypet.app.ui.components.liveRemainder
import ru.finnypet.app.ui.components.rememberPlanDrafts
import ru.finnypet.app.ui.theme.Dimens
import ru.finnypet.app.ui.theme.FinnyTheme
import ru.finnypet.app.ui.theme.LocalAnimationsEnabled

/**
 * Одно задание (ТЗ 2.5.8): сова просит совета, ребёнок проходит шаги,
 * получает объяснение независимо от результата.
 */
@Composable
fun TaskScreen(
    onBack: () -> Unit,
    viewModel: TaskViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    TaskContent(
        state = state,
        onBack = onBack,
        onStart = viewModel::start,
        onChoose = viewModel::choose,
        onSet = viewModel::set,
        onToggle = viewModel::toggle,
        onNext = viewModel::next,
        onRetry = viewModel::retry,
        onTryAgain = viewModel::tryAgain,
    )
}

@Composable
fun TaskContent(
    state: TaskState,
    onBack: () -> Unit,
    onStart: () -> Unit = {},
    onChoose: (String) -> Unit = {},
    onSet: (SpendCategory, Coins) -> Unit = { _, _ -> },
    onToggle: (String) -> Unit = {},
    onNext: () -> Unit = {},
    onRetry: () -> Unit = {},
    onTryAgain: () -> Unit = {},
) {
    when (state) {
        TaskState.Loading -> Screen(onBack = onBack) {}

        TaskState.Failed -> Screen(
            onBack = onBack,
            bottomBar = {
                ButtonColumn {
                    FinnyButton(text = stringResource(R.string.action_retry), onClick = onRetry)
                }
            },
        ) {
            Text(
                text = stringResource(R.string.tasks_failed),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
            )
        }

        TaskState.Missing -> Screen(
            onBack = onBack,
            bottomBar = { ButtonColumn { FinnyButton(text = stringResource(R.string.task_finish), onClick = onBack) } },
        ) {
            Text(text = stringResource(R.string.task_missing), style = MaterialTheme.typography.bodyLarge)
        }

        is TaskState.Ready -> when (val stage = state.stage) {
            TaskStage.Intro -> Intro(state = state, onBack = onBack, onStart = onStart)
            is TaskStage.Step -> Step(
                state = state,
                stage = stage,
                onBack = onBack,
                onChoose = onChoose,
                onSet = onSet,
                onToggle = onToggle,
                onNext = onNext,
            )

            is TaskStage.Done -> Done(state = state, outcome = stage.outcome, onBack = onBack, onTryAgain = onTryAgain)
        }
    }
}

/**
 * [balance] — кошелёк в шапке; пока задание не загружено, его нет.
 * [walletModifier] — чтобы итог знал, куда летят монеты награды.
 */
@Composable
private fun Screen(
    onBack: () -> Unit,
    balance: Coins? = null,
    walletModifier: Modifier = Modifier,
    bottomBar: (@Composable () -> Unit)? = null,
    centered: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    FinnyScaffold(
        title = stringResource(R.string.task_title),
        onBack = onBack,
        actions = {
            balance?.let { Box(modifier = walletModifier.padding(end = Dimens.Space)) { MoneyAmount(amount = it) } }
        },
        bottomBar = bottomBar,
        spacing = Dimens.SpaceMedium,
        centered = centered,
        content = content,
    )
}

/**
 * Вступление: сова просит совета. Правило дня — здесь, до первого шага:
 * если монеты за сегодня получены, ребёнок узнаёт об этом до того, как
 * вложит усилия. По центру высоты: четыре блока, прижатые к верху,
 * оставляли над кнопкой пустую половину экрана (DESIGN_PLAN 1, №6).
 */
@Composable
private fun Intro(
    state: TaskState.Ready,
    onBack: () -> Unit,
    onStart: () -> Unit,
) {
    Screen(
        onBack = onBack,
        balance = state.balance,
        centered = true,
        bottomBar = {
            ButtonColumn {
                when {
                    state.rewardAvailable -> FinnyButton(text = stringResource(R.string.task_start), onClick = onStart)
                    else -> FinnyButton(text = stringResource(R.string.task_start_training), onClick = onStart)
                }
            }
        },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMedium),
        ) {
            TopicPlate(topic = state.topic)
            Text(
                text = stringResource(state.topic.label),
                style = MaterialTheme.typography.titleSmall,
                color = state.topic.colors.text,
            )
        }
        SpeechBubble(owl = state.owl, owlRole = OwlRole.Dialog) {
            Text(text = state.intro, style = MaterialTheme.typography.bodyLarge)
        }
        // Баланс на главном другой, чем в истории: без этой строки ребёнок
        // принимает монеты задания за свои.
        BulbNote(text = stringResource(R.string.task_story_note))
        if (state.rewardAvailable) {
            val spoken = stringResource(R.string.task_reward_upto_spoken, coinsText(state.maxReward))
            CoinChip(
                text = stringResource(R.string.task_reward_upto, state.maxReward.amount),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.clearAndSetSemantics { contentDescription = spoken },
            )
        } else {
            BulbNote(text = stringResource(R.string.task_training_note))
        }
    }
}

/** Пояснение-правило с лампочкой (DESIGN_PLAN 3.8): спокойная плашка, а не предупреждение. */
@Composable
private fun BulbNote(text: String) {
    FinnyCard(color = MaterialTheme.colorScheme.primaryContainer) {
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMedium)) {
            Icon(imageVector = FinnyIcons.Bulb, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(text = text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun Step(
    state: TaskState.Ready,
    stage: TaskStage.Step,
    onBack: () -> Unit,
    onChoose: (String) -> Unit,
    onSet: (SpendCategory, Coins) -> Unit,
    onToggle: (String) -> Unit,
    onNext: () -> Unit,
) {
    val last = stage.index == stage.total - 1
    // Черновики — на весь шаг, а не внутри банок: по ним же считается счётчик
    // остатка над кнопкой. Свой набор на каждый шаг, чтобы движение ползунка
    // одного шага не досталось следующему.
    val drafts = key(stage.index) { rememberPlanDrafts() }
    Screen(
        onBack = onBack,
        balance = state.balance,
        bottomBar = {
            ButtonColumn {
                // Счётчик закреплён над «Ответить», как на плане дня (DESIGN_PLAN
                // 3.2): в конце прокрутки кнопка срезала его монету.
                val step = stage.step
                if (step is StepView.Distribute) {
                    RemainderCounter(remainder = liveRemainder(step.plan, step.remainder, drafts), overBy = Coins.ZERO)
                }
                FinnyButton(
                    text = stringResource(if (last) R.string.task_answer else R.string.task_next),
                    onClick = onNext,
                    enabled = stage.step.canProceed && !state.submitting,
                )
            }
        },
    ) {
        Text(
            text = stringResource(R.string.task_step, stage.index + 1, stage.total),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = stage.step.prompt, style = MaterialTheme.typography.titleMedium)

        when (val step = stage.step) {
            is StepView.Choice -> step.options.forEach { option ->
                OptionRow(option = option, selected = option.id == step.chosen, onClick = { onChoose(option.id) })
            }

            is StepView.Distribute -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSmall),
                    modifier = Modifier.semantics(mergeDescendants = true) {},
                ) {
                    Text(
                        text = stringResource(R.string.task_budget),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    MoneyAmount(amount = step.budget)
                }
                PlanEditor(
                    plan = step.plan,
                    available = step.budget,
                    remainder = step.remainder,
                    onSet = onSet,
                    drafts = drafts,
                    jars = step.jars,
                )
            }

            is StepView.Pick -> Shelf(step = step, topic = state.topic, onToggle = onToggle)
        }
    }
}

/**
 * Вариант — вся строка кнопка. Выбранный — рамкой, отметкой и словом, не
 * только цветом (ТЗ 3.6).
 */
@Composable
private fun OptionRow(option: OptionView, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMedium),
        modifier = Modifier
            .fillMaxWidth()
            .tile(marked = selected)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .defaultMinSize(minHeight = OPTION_HEIGHT)
            .padding(horizontal = Dimens.Space, vertical = Dimens.SpaceMedium),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceTiny), modifier = Modifier.weight(1f)) {
            Text(
                text = option.label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            )
            if (selected) {
                Text(
                    text = stringResource(R.string.task_selected),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        if (selected) {
            Icon(imageVector = FinnyIcons.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

/**
 * «Полка»: счётчик корзины и товары по два в ряд (при крупном шрифте — по
 * одному: в половине ширины длинные слова рвались бы). Товар, который не
 * влезает в бюджет, подписан «Не влезает» и не переключается. Корзина не
 * может выйти за бюджет — поэтому и красного «перебора» у полосы нет.
 */
@Composable
private fun Shelf(step: StepView.Pick, topic: TaskTopic, onToggle: (String) -> Unit) {
    // Счётчик читается одной фразой: «в корзине 18 монет из 30», а не
    // по частям и ещё раз то же самое с полосы.
    val spoken = stringResource(R.string.task_basket_progress, coinsText(step.spent), step.budget.amount)
    Column(
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSmall),
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = spoken },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSmall),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(R.string.task_basket),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            // Одна монета на пару чисел: «● 18 из 30», а не «● 18 / ● 30».
            Coin(style = MaterialTheme.typography.titleLarge)
            Text(
                text = stringResource(R.string.tasks_count, step.spent.amount, step.budget.amount),
                style = MaterialTheme.typography.titleLarge,
            )
        }
        ProgressLine(
            fraction = if (step.budget.amount == 0) 0f else step.spent.amount.toFloat() / step.budget.amount,
        )
    }

    val columns = if (LocalDensity.current.fontScale > Dimens.WIDE_FONT_SCALE) 1 else 2
    step.items.chunked(columns).forEach { row ->
        Row(
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMedium),
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Max),
        ) {
            row.forEach { item ->
                ItemTile(
                    item = item,
                    plate = topic.colors.container,
                    picked = item.id in step.picked,
                    enabled = step.canToggle(item.id),
                    onToggle = { onToggle(item.id) },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
            }
            repeat(columns - row.size) { Spacer(modifier = Modifier.weight(1f)) }
        }
    }
}

/**
 * Товар на полке — вся плитка переключатель. Эмодзи на тарелке цвета темы,
 * а не направления, и без подписи направления: в отличие от магазина, здесь
 * они выдавали бы верный ответ (ТЗ 2.5.8).
 */
@Composable
private fun ItemTile(
    item: PickItemView,
    plate: Color,
    picked: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSmall),
        modifier = modifier
            .tile(marked = picked)
            .toggleable(value = picked, enabled = enabled, role = Role.Checkbox, onValueChange = { onToggle() })
            .defaultMinSize(minHeight = Dimens.TouchTarget)
            .padding(Dimens.SpaceMedium),
    ) {
        if (item.icon.isNotBlank()) ItemIcon(icon = item.icon, plate = plate)
        Text(text = item.title, style = MaterialTheme.typography.titleMedium)
        MoneyAmount(amount = item.price, style = MaterialTheme.typography.titleMedium)
        // Чипы — по нижнему краю: в паре плиток разной высоты они стоят на одной линии.
        Spacer(modifier = Modifier.weight(1f))
        when {
            picked -> FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSmall),
                verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSmall),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceTiny),
                ) {
                    Icon(
                        imageVector = FinnyIcons.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(BADGE_ICON_SIZE),
                    )
                    Text(
                        text = stringResource(R.string.task_picked),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                PickChip(text = stringResource(R.string.task_unpick), filled = false)
            }

            enabled -> PickChip(text = stringResource(R.string.task_pick), filled = true)

            else -> Text(
                text = stringResource(R.string.task_pick_full),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Чип действия плитки: «Взять» — залитый, как главная кнопка, «Убрать» —
 * контуром, как второстепенная. Нажимается вся плитка, чип только
 * подсказывает, что случится.
 */
@Composable
private fun PickChip(text: String, filled: Boolean) {
    val shape = RoundedCornerShape(Dimens.CornerTile)
    val primary = MaterialTheme.colorScheme.primary
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = if (filled) MaterialTheme.colorScheme.onPrimary else primary,
        modifier = Modifier
            .clip(shape)
            .then(if (filled) Modifier.background(primary) else Modifier.border(Dimens.ButtonBorderWidth, primary, shape))
            .padding(horizontal = Dimens.SpaceMedium, vertical = Dimens.SpaceTiny),
    )
}

/**
 * Итог: объяснение независимо от результата (ТЗ 2.5.8), награда и питомец.
 * После ошибки главная кнопка — повтор, но и «Дальше» есть: неверный ответ
 * не тратит лимит (R8), и монеты можно заработать на другом задании.
 *
 * «Верно!» — сова радуется и подпрыгивает, вокруг загораются искры, монеты
 * награды летят в кошелёк в шапке. «Не совсем» — спокойная сова и правило
 * первой попытки плашкой, без красного: ошибка — повод разобраться.
 */
@Composable
private fun Done(
    state: TaskState.Ready,
    outcome: TaskOutcomeView,
    onBack: () -> Unit,
    onTryAgain: () -> Unit,
) {
    var wallet by remember { mutableStateOf<Offset?>(null) }
    var source by remember { mutableStateOf<Offset?>(null) }
    // Полёт — один раз: после поворота экрана монеты уже в кошельке.
    var landed by rememberSaveable { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxSize()) {
        Screen(
            onBack = onBack,
            balance = shownBalance(state.balance, outcome.reward, landed),
            walletModifier = Modifier.onGloballyPositioned { wallet = it.boundsInRoot().center },
            centered = true,
            bottomBar = {
                ButtonColumn {
                    if (outcome.correct) {
                        FinnyButton(text = stringResource(R.string.task_next), onClick = onBack)
                    } else {
                        FinnyButton(text = stringResource(R.string.task_try_again), onClick = onTryAgain)
                        FinnySecondaryButton(text = stringResource(R.string.task_next), onClick = onBack)
                    }
                }
            },
        ) {
            Text(
                text = stringResource(if (outcome.correct) R.string.task_correct else R.string.task_wrong),
                style = MaterialTheme.typography.displaySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { heading() },
            )
            TopSpeechBubble(text = outcome.text)
            Box(contentAlignment = Alignment.Center, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Owl(look = state.owl, size = OwlRole.TaskResult.size, reactOnAppear = outcome.correct)
                if (outcome.correct) Sparkles(modifier = Modifier.matchParentSize())
            }
            when (rewardLine(outcome.correct, outcome.reward)) {
                RewardLine.PAID -> Text(
                    text = stringResource(R.string.task_reward_paid, coinsText(outcome.reward)),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { source = it.boundsInRoot().center },
                )

                RewardLine.FIRST_TRY_RULE -> BulbNote(text = stringResource(R.string.task_reward_rule))

                RewardLine.NO_COINS -> Text(
                    text = stringResource(R.string.task_reward_none),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            outcome.changes.forEach { change ->
                StatChangeLine(change = change)
            }
        }
        val from = source
        val to = wallet
        // Ждём, пока разметка скажет, где строка награды и кошелёк: иначе
        // монеты вылетели бы из угла экрана.
        if (outcome.reward > Coins.ZERO && !landed && from != null && to != null) {
            CoinFlight(from = from, to = listOf(to), onFinished = { landed = true })
        }
    }
}

/**
 * Звёздочки-искры вокруг совы при «Верно!» (DESIGN_PLAN 3.8, `Celebrate`):
 * загораются по очереди с пружиной. Без движения стоят сразу — радость
 * видна и так, а конечный кадр тот же.
 */
@Composable
private fun Sparkles(modifier: Modifier = Modifier) {
    val motion = LocalAnimationsEnabled.current
    val star = FinnyTheme.palette.star
    Box(modifier = modifier) {
        SPARKS.forEachIndexed { index, (place, size) ->
            val scale = remember { Animatable(if (motion) 0f else 1f) }
            LaunchedEffect(Unit) {
                if (motion) {
                    delay(SPARK_STAGGER_MS * index)
                    scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                }
            }
            Icon(
                imageVector = FinnyIcons.StarFilled,
                contentDescription = null,
                tint = star,
                modifier = Modifier
                    .align(place)
                    .size(size)
                    .graphicsLayer {
                        scaleX = scale.value
                        scaleY = scale.value
                    },
            )
        }
    }
}

/** Места искр по краям рамки совы — в пустых углах, не на морде. */
private val SPARKS = listOf(
    BiasAlignment(-0.95f, -0.75f) to 24.dp,
    BiasAlignment(0.95f, -0.9f) to 20.dp,
    BiasAlignment(-0.8f, 0.35f) to 16.dp,
    BiasAlignment(0.9f, 0.2f) to 22.dp,
)

/** Интервал между искрами — как у звёзд итогов дня (DESIGN_PLAN 2.7). */
private const val SPARK_STAGGER_MS = 150L

/** Варианты ответа выше общего минимума (раздел 8 плана): ребёнок не промахивается между соседними. */
private val OPTION_HEIGHT = 52.dp

private val BADGE_ICON_SIZE = 18.dp
