package ru.finnypet.app.ui.screens.shop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import ru.finnypet.app.R
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.ItemId
import ru.finnypet.app.domain.model.RecoveryOption
import ru.finnypet.app.domain.model.SpendCategory
import ru.finnypet.app.ui.components.ButtonColumn
import ru.finnypet.app.ui.components.CategoryLabel
import ru.finnypet.app.ui.components.Coin
import ru.finnypet.app.ui.components.CoinChip
import ru.finnypet.app.ui.components.CoinFlight
import ru.finnypet.app.ui.components.FinnyButton
import ru.finnypet.app.ui.components.FinnyDialog
import ru.finnypet.app.ui.components.FinnyListScaffold
import ru.finnypet.app.ui.components.FinnyScaffold
import ru.finnypet.app.ui.components.FinnySecondaryButton
import ru.finnypet.app.ui.components.ItemIcon
import ru.finnypet.app.ui.components.Jar
import ru.finnypet.app.ui.components.LabelledLine
import ru.finnypet.app.ui.components.MoneyAmount
import ru.finnypet.app.ui.components.OwlLook
import ru.finnypet.app.ui.components.PlanningHint
import ru.finnypet.app.ui.components.SpeechBubble
import ru.finnypet.app.ui.components.StatChip
import ru.finnypet.app.ui.components.coinsText
import ru.finnypet.app.ui.components.statChangeText
import ru.finnypet.app.ui.components.color
import ru.finnypet.app.ui.components.fill
import ru.finnypet.app.ui.components.label
import ru.finnypet.app.ui.components.shortageLine
import ru.finnypet.app.ui.components.tile
import ru.finnypet.app.ui.screens.main.JarsLeft
import ru.finnypet.app.ui.sound.Sound
import ru.finnypet.app.ui.theme.Dimens
import ru.finnypet.app.ui.theme.FinnyTheme
import ru.finnypet.app.ui.theme.LocalAnimationsEnabled

/**
 * Магазин (ТЗ 2.5.6): товары с ценой, направлением и влиянием на питомца.
 * Покупка подтверждается, итог — покупка или нехватка монет — объясняется
 * словами из контент-пака.
 */
@Composable
fun ShopScreen(
    onBack: () -> Unit,
    onPlan: () -> Unit,
    onSavings: () -> Unit,
    onTasks: () -> Unit,
    viewModel: ShopViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ShopContent(
        state = state,
        onBack = onBack,
        onPlan = onPlan,
        onSavings = onSavings,
        onTasks = onTasks,
        onBuy = viewModel::buy,
        onDismiss = viewModel::dismiss,
        onRetry = viewModel::retry,
    )
}

@Composable
fun ShopContent(
    state: ShopState,
    onBack: () -> Unit,
    onPlan: () -> Unit = {},
    onSavings: () -> Unit = {},
    onTasks: () -> Unit = {},
    onBuy: (ItemId) -> Unit = {},
    onDismiss: () -> Unit = {},
    onRetry: () -> Unit = {},
) {
    when (state) {
        ShopState.Loading -> FinnyScaffold(title = stringResource(R.string.shop_title), onBack = onBack) {}

        ShopState.Failed -> FinnyScaffold(
            title = stringResource(R.string.shop_title),
            onBack = onBack,
            bottomBar = {
                ButtonColumn {
                    FinnyButton(text = stringResource(R.string.action_retry), onClick = onRetry)
                }
            },
        ) {
            Text(
                text = stringResource(R.string.shop_failed),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
            )
        }

        is ShopState.Ready -> Ready(
            state = state,
            onBack = onBack,
            onPlan = onPlan,
            onSavings = onSavings,
            onTasks = onTasks,
            onBuy = onBuy,
            onDismiss = onDismiss,
        )
    }
}

@Composable
private fun Ready(
    state: ShopState.Ready,
    onBack: () -> Unit,
    onPlan: () -> Unit,
    onSavings: () -> Unit,
    onTasks: () -> Unit,
    onBuy: (ItemId) -> Unit,
    onDismiss: () -> Unit,
) {
    // Товар, который ребёнок собрался купить. Живёт в экране, а не во
    // вьюмодели: это ещё не действие, а вопрос. Поворот переживает.
    var pendingId by rememberSaveable { mutableStateOf<String?>(null) }
    // Отказ после «Купить» открывает то же окно товара — уже с нехваткой.
    val rejected = state.outcome as? PurchaseOutcome.Rejected
    val shownId = pendingId ?: rejected?.itemId?.value
    val shown = state.items.firstOrNull { it.id.value == shownId }
    val close = {
        pendingId = null
        if (rejected != null) onDismiss()
    }

    // Откуда и куда летят монеты покупки: кошелёк в шапке и плитка товара.
    // Плитки — в обычной карте, а не в состоянии: их место меняется на каждом
    // кадре прокрутки, а читать его нужно только в момент покупки.
    var wallet by remember { mutableStateOf<Offset?>(null) }
    val tiles = remember { mutableMapOf<ItemId, Offset>() }
    val columns = if (LocalDensity.current.fontScale > Dimens.WIDE_FONT_SCALE) 1 else 2
    val done = state.outcome as? PurchaseOutcome.Done
    // Итог покупки — в облачке совы наверху списка. Купили снизу — облачко
    // за краем, и ребёнок не видит, что изменилось: после полёта монет
    // список сам возвращается к нему. Место в списке при этом теряется
    // (ревью F6), но наверху не только облачко, а и банки с новым «потрачено»:
    // оставшись внизу, ребёнок не увидел бы ни того, ни другого.
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val motion = LocalAnimationsEnabled.current
    val showOutcome: () -> Unit = {
        scope.launch { if (motion) list.animateScrollToItem(0) else list.scrollToItem(0) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Ключи с префиксами: идентификаторы товаров пишет напарник в shop.json,
        // и товар с id «owl» иначе столкнулся бы с шапкой списка.
        FinnyListScaffold(
            title = stringResource(R.string.shop_title),
            onBack = onBack,
            listState = list,
            actions = {
                Box(
                    modifier = Modifier
                        .padding(end = Dimens.Space)
                        .onGloballyPositioned { wallet = it.boundsInRoot().center },
                ) { MoneyAmount(amount = state.balance) }
            },
        ) {
            item(key = "header:owl") {
                OwlBubble(owl = state.owl, phrase = state.phrase, done = done)
            }
            if (state.planJars.isNotEmpty()) item(key = "header:jars") { PlanJars(jars = state.planJars) }
            if (!state.canBuy) {
                item(key = "header:planning") { PlanningHint(text = stringResource(R.string.shop_planning_hint), onPlan = onPlan) }
            }
            if (state.items.isEmpty()) {
                item(key = "header:empty") {
                    Text(
                        text = stringResource(R.string.shop_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // Секции по направлениям (раздел 8 плана), по две плитки в ряд, а при
            // крупном шрифте по одной: в четверть ширины 360 dp не помещается ни
            // название, ни метка, а в половину при шрифте 2,0 рвутся слова.
            SpendCategory.entries.forEach { category ->
                val section = state.items.filter { it.category == category }
                if (section.isEmpty()) return@forEach
                item(key = "section:${category.name}") {
                    CategoryLabel(category = category)
                }
                items(section.chunked(columns), key = { row -> "row:" + row.joinToString { it.id.value } }) { row ->
                    // Плитки ряда одной высоты: метки стоят на одной линии.
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSmall),
                        modifier = Modifier.height(IntrinsicSize.Max),
                    ) {
                        row.forEach { item ->
                            ShopTile(
                                item = item,
                                onClick = { pendingId = item.id.value },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .onGloballyPositioned { tiles[item.id] = it.boundsInRoot().center },
                            )
                        }
                        repeat(columns - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                    }
                }
            }
        }
        done?.let { PurchaseFlight(done = it, from = wallet, to = tiles[it.itemId], onLanded = showOutcome) }
    }

    when {
        shown == null -> Unit

        // Пока день планируется, нажатие на товар — не немой тупик, а та же
        // дорога в план, что и в подсказке сверху (ТЗ 3.4).
        !state.canBuy -> PlanningDialog(
            onPlan = {
                close()
                onPlan()
            },
            onDismiss = close,
        )

        else -> ItemDialog(
            item = shown,
            jars = state.jars,
            balance = state.balance,
            onBuy = {
                close()
                onBuy(shown.id)
            },
            onClose = close,
            onRecovery = { action ->
                close()
                when (action) {
                    RecoveryAction.Savings -> onSavings()
                    RecoveryAction.Tasks -> onTasks()
                    RecoveryAction.Close, RecoveryAction.Hint -> Unit
                }
            },
        )
    }
}

/**
 * Монеты летят из кошелька к купленному товару (DESIGN_PLAN 2.7) — один раз
 * на покупку: после поворота экрана они уже потрачены. Без движения полёта
 * нет, а смысл остаётся в облачке совы и кошельке. [onLanded] — монеты
 * легли (без движения — сразу).
 */
@Composable
private fun PurchaseFlight(done: PurchaseOutcome.Done, from: Offset?, to: Offset?, onLanded: () -> Unit) {
    var landed by rememberSaveable(done.number) { mutableStateOf(false) }
    if (!landed && from != null && to != null) {
        CoinFlight(
            from = from,
            to = listOf(to),
            sound = Sound.PURCHASE,
            onFinished = {
                landed = true
                onLanded()
            },
        )
    }
}

@Composable
private fun PlanningDialog(onPlan: () -> Unit, onDismiss: () -> Unit) {
    FinnyDialog(
        title = stringResource(R.string.budget_title),
        onDismiss = onDismiss,
        buttons = {
            FinnyButton(text = stringResource(R.string.budget_action_plan), onClick = onPlan)
            FinnySecondaryButton(text = stringResource(R.string.action_not_now), onClick = onDismiss)
        },
    ) {
        Text(text = stringResource(R.string.shop_planning_hint), style = MaterialTheme.typography.bodyLarge)
    }
}

/**
 * Сова с облачком: что ей нужно сейчас, а после покупки — одна фраза и
 * чипы «−8» и «Еда +15» (DESIGN_PLAN 3.5, ТЗ 2.5.9). Изменения — настоящие,
 * а не обещанные: у верхней границы показатель не растёт, и так и сказано.
 */
@Composable
private fun OwlBubble(owl: OwlLook, phrase: String, done: PurchaseOutcome.Done?) {
    SpeechBubble(owl = owl) {
        if (done == null) {
            Text(text = phrase, style = MaterialTheme.typography.bodyLarge)
        } else {
            // Честность важнее игрушки: «играет» при неподвижной радости звучало бы как «+15».
            val said = when {
                done.effects.isNotEmpty() && done.changes.isEmpty() -> stringResource(R.string.shop_no_change)
                // Игрушка — не просто цифры: питомец играет с ней (U2, ТЗ 2.5.9).
                else -> done.toyPhrase ?: done.text
            }
            // Фраза на всю ширину облачка: тарелка товара рядом сжимала её до 4–5 строк (DESIGN_PLAN 3.5).
            Text(text = said, style = MaterialTheme.typography.bodyLarge)
            val spent = stringResource(R.string.shop_spent, coinsText(done.price))
            // Между чипами 4 dp, как у чипов на плитке товара: при 8 dp «−10» и
            // «Радость +10» (220 dp) не помещались в облачко на 384 dp (220 dp)
            // и уходили в две строки, облачко росло и сдвигало банки (ревью F6).
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceTiny),
                verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSmall),
            ) {
                CoinChip(
                    text = "−${done.price.amount}",
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.clearAndSetSemantics { contentDescription = spent },
                )
                done.changes.forEach { change -> StatChip(kind = change.kind, delta = change.delta) }
            }
        }
    }
}

/**
 * Банки плана над товарами: «Нужное — потрачено 12 из 35, осталось 23».
 * Прежние чипы «[миска] ещё 23» ребёнок не понимал: чего «ещё» и что за
 * миска (просьба владельца 28.09). Теперь направление названо словом, рядом
 * та же банка, что на плане, а слова те же, что в итогах дня («Потрачено
 * 14 из 20») и на плане («осталось 23»). Белая плитка — как у товаров ниже.
 */
@Composable
private fun PlanJars(jars: List<ShopJar>) {
    Column(
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMedium),
        modifier = Modifier
            .fillMaxWidth()
            .tile(marked = false)
            .padding(horizontal = Dimens.Space, vertical = Dimens.SpaceMedium),
    ) {
        jars.forEach { jar -> PlanJarRow(jar) }
    }
}

/**
 * Одна банка. Название и остаток — в ряд с переносом: при шрифте 2,0
 * остаток уходит под название целиком, а не рвётся (ТЗ 3.6). Сверх плана —
 * «сверх плана на 5», как в итогах, без красного: это не ошибка (AD-4).
 * TalkBack читает направление словом и оба числа.
 */
@Composable
private fun PlanJarRow(jar: ShopJar) {
    val title = stringResource(jar.category.label)
    val spent = stringResource(R.string.shop_jar_spent, jar.spent.amount, jar.planned.amount)
    val rest = if (jar.over > Coins.ZERO) {
        stringResource(R.string.shop_jar_over, jar.over.amount)
    } else {
        stringResource(R.string.budget_jar_left, jar.left.amount)
    }
    val spoken = stringResource(R.string.shop_jar_description, title, spent, rest)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMedium),
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = spoken },
    ) {
        Jar(level = jar.level, color = jar.category.fill, modifier = Modifier.size(JAR_WIDTH, JAR_HEIGHT))
        Column(modifier = Modifier.weight(1f)) {
            FlowRow(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = title, style = MaterialTheme.typography.titleSmall, color = jar.category.color)
                Text(text = rest, style = MaterialTheme.typography.labelMedium, color = jar.category.color)
            }
            Text(
                text = spent,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Плитка товара — вся целиком кнопка (DESIGN_PLAN 3.5): эмодзи на тарелке,
 * цена бейджем в правом верхнем углу, название, влияние чипами и метка.
 * «Нужно сейчас» — чип и рамка цвета «Нужного», но метка всегда и словом:
 * цвет не единственный признак (ТЗ 3.6). Товар «не в плане» не приглушён:
 * план мягкий (AD-4), покупка сверх него — обычное решение, а не то, что
 * нужно оправдывать видом плитки.
 */
@Composable
private fun ShopTile(item: ShopItemView, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val needed = item.mark == ItemMark.NEEDED_NOW
    val need = FinnyTheme.palette.need
    // Одной фразой, название первым: по порядку на плитке TalkBack начинал с
    // цены — «8 монет, Свежая вода», и было не понять, о чём число (F9).
    val spoken = (
        listOf(item.title, coinsText(item.price)) +
            item.effects.map { statChangeText(it.stat, it.delta) } +
            listOfNotNull(item.mark.label?.let { stringResource(it) })
        ).joinToString(", ")
    Column(
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSmall),
        modifier = modifier
            .tile(marked = needed, markColor = need.fill)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = spoken }
            .defaultMinSize(minHeight = Dimens.TouchTarget)
            .padding(Dimens.SpaceMedium),
    ) {
        // В ряд с переносом: при крупном шрифте бейдж уходит под тарелку, а не наезжает на неё.
        FlowRow(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSmall),
            modifier = Modifier.fillMaxWidth(),
        ) {
            ItemIcon(icon = item.icon, category = item.category)
            PriceBadge(price = item.price)
        }
        Text(text = item.title, style = MaterialTheme.typography.titleMedium)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceTiny),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceTiny),
        ) {
            item.effects.forEach { effect -> StatChip(kind = effect.stat, delta = effect.delta) }
        }
        // Метки — по нижнему краю: в ряду плиток разной высоты они на одной линии.
        Spacer(modifier = Modifier.weight(1f))
        item.mark.label?.let { label ->
            if (needed) {
                Text(
                    text = stringResource(label),
                    style = MaterialTheme.typography.labelMedium,
                    color = need.text,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(need.container)
                        .padding(horizontal = Dimens.SpaceMedium, vertical = Dimens.SpaceTiny),
                )
            } else {
                Text(
                    text = stringResource(label),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Цена бейджем с монетой; TalkBack слышит её в подписи плитки — «12 монет», а не «12». */
@Composable
private fun PriceBadge(price: Coins) {
    CoinChip(
        text = price.amount.toString(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.clearAndSetSemantics {},
    )
}

private val ItemMark.label: Int?
    get() = when (this) {
        ItemMark.NEEDED_NOW -> R.string.shop_mark_needed
        ItemMark.NOT_NEEDED -> R.string.shop_mark_not_needed
        ItemMark.NOT_IN_PLAN -> R.string.shop_mark_not_in_plan
        ItemMark.NONE -> null
    }

/**
 * Окно товара: что покупаем, за сколько и что от этого изменится.
 *
 * Покупка сверх плана не блокируется отдельным окном (AD-4): здесь же, без
 * упрёка, видно, насколько это больше плана, что останется в кошельке и
 * хватит ли потом на нужное (раздел 3 плана, «доступность нужного»; ТЗ 2.5.9,
 * 8.4) — ребёнок решает сам, а не получает стену вместо покупки.
 *
 * Не по карману — в этом же окне вместо «Купить» сразу «Не хватает 2 монет»
 * и варианты выхода (DESIGN_PLAN 3.5): активная «Купить», за которой
 * приходит второе окно с отказом, обещала бы покупку, которой не будет.
 */
@Composable
private fun ItemDialog(
    item: ShopItemView,
    jars: JarsLeft?,
    balance: Coins,
    onBuy: () -> Unit,
    onClose: () -> Unit,
    onRecovery: (RecoveryAction) -> Unit,
) {
    val shortage = item.shortage
    FinnyDialog(
        title = item.title,
        onDismiss = onClose,
        buttons = {
            if (shortage == null) {
                FinnyButton(text = stringResource(R.string.shop_buy), onClick = onBuy)
                FinnySecondaryButton(text = stringResource(R.string.action_not_now), onClick = onClose)
            } else {
                RecoveryButtons(shortage = shortage, onClose = onClose, onRecovery = onRecovery)
            }
        },
    ) {
        ItemIcon(
            icon = item.icon,
            category = item.category,
            large = true,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSmall),
            modifier = Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {},
        ) {
            Text(
                text = stringResource(R.string.shop_price),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            MoneyAmount(amount = item.price)
        }
        CategoryLabel(category = item.category)
        if (item.effects.isNotEmpty()) {
            Text(text = stringResource(R.string.shop_pet_change), style = MaterialTheme.typography.titleMedium)
            // Прирост — настоящий, с учётом верхней границы: обещать «+15» при 100 из 100 было бы неправдой.
            if (item.gains.isEmpty()) {
                Text(text = stringResource(R.string.shop_pet_change_none), style = MaterialTheme.typography.bodyLarge)
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSmall),
                    verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSmall),
                ) {
                    item.gains.forEach { gain -> StatChip(kind = gain.kind, delta = gain.delta) }
                }
            }
        }
        // Нужное, которое сове пока не нужно: купить можно, но сова спрашивает (R12).
        item.warning?.let { Text(text = it, style = MaterialTheme.typography.bodyLarge) }
        if (shortage == null) {
            PurchaseLines(item = item, jars = jars, balance = balance)
        } else {
            ShortageLine(shortfall = shortage.shortfall)
            RecoveryHints(shortage = shortage)
        }
    }
}

/** Сверх плана — не запрет, а честная цифра рядом с ценой (AD-4, ТЗ 8.4). */
@Composable
private fun PurchaseLines(item: ShopItemView, jars: JarsLeft?, balance: Coins) {
    val overPlan = overPlanOf(item.price, item.category, jars)
    val balanceAfter = balance.takeIf { it.covers(item.price) }?.let { it - item.price }
    val needsShortfall = needsShortfallOf(balanceAfter, item.needsCostAfter)
    if (overPlan != null) LabelledLine(label = stringResource(R.string.shop_over_plan), amount = overPlan)
    if (balanceAfter != null) LabelledLine(label = stringResource(R.string.shop_balance_after), amount = balanceAfter)
    // Раздел 3 плана: предупреждение честное, но не пугает — просто цифра рядом с остатком.
    if (needsShortfall != null) LabelledLine(label = stringResource(R.string.shop_needs_shortfall), amount = needsShortfall)
}

/**
 * Нехватка — единственное место красного в игре (DESIGN_PLAN 2.1): здесь
 * нужно остановиться. Всегда со значком монеты и словами, не одним цветом.
 */
@Composable
private fun ShortageLine(shortfall: Coins) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSmall),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.CornerTile))
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = Dimens.Space, vertical = Dimens.SpaceMedium),
    ) {
        Coin(style = MaterialTheme.typography.titleMedium)
        Text(
            text = stringResource(shortageLine(shortfall), shortfall.amount),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

/** Вариант без экрана — подсказкой, а не кнопкой в пустоту (ТЗ 3.4). */
@Composable
private fun RecoveryHints(shortage: ItemShortage) {
    val hints = shortage.options.filter { it.option.action == RecoveryAction.Hint }
    if (hints.isEmpty()) return
    Text(text = stringResource(R.string.shop_options), style = MaterialTheme.typography.titleMedium)
    hints.forEach { choice ->
        Text(
            text = stringResource(R.string.shop_option_hint, choice.label),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

/**
 * Варианты выхода кнопками (ТЗ 2.5.6, 2.5.9): «выполнить задание» ведёт в
 * задания, «взять из копилки» — в копилку, «купить попозже» и «выбрать
 * подешевле» возвращают к списку. Главная кнопка — вариант, который
 * рекомендует домен.
 */
@Composable
private fun RecoveryButtons(
    shortage: ItemShortage,
    onClose: () -> Unit,
    onRecovery: (RecoveryAction) -> Unit,
) {
    val actionable = shortage.options.filter { it.option.action != RecoveryAction.Hint }
    val primary = actionable.firstOrNull { it.option == shortage.recommended } ?: actionable.firstOrNull()
    // Домен всегда добавляет «выбрать подешевле», так что кнопка есть.
    // Запасная — на случай, если это правило когда-нибудь изменится:
    // окно без кнопки было бы тупиком.
    if (primary == null) {
        FinnyButton(text = stringResource(R.string.action_ok), onClick = onClose)
    } else {
        FinnyButton(text = primary.label, onClick = { onRecovery(primary.option.action) })
    }
    actionable.filter { it != primary }.forEach { choice ->
        FinnySecondaryButton(text = choice.label, onClick = { onRecovery(choice.option.action) })
    }
}

/** Куда ведёт вариант выхода: назад к списку, в копилку, в задания или пока никуда. */
private enum class RecoveryAction { Close, Savings, Tasks, Hint }

private val RecoveryOption.action: RecoveryAction
    get() = when (this) {
        RecoveryOption.POSTPONE_PURCHASE, RecoveryOption.CHOOSE_CHEAPER -> RecoveryAction.Close
        RecoveryOption.WITHDRAW_FROM_SAVINGS -> RecoveryAction.Savings
        RecoveryOption.DO_TASK -> RecoveryAction.Tasks
        // Пересмотр плана — совет на завтра, экрана у него нет.
        RecoveryOption.ADJUST_NEXT_PLAN -> RecoveryAction.Hint
    }

// Банка у строки плана — мини-версия банок плана: на две строки текста.
private val JAR_WIDTH = 28.dp
private val JAR_HEIGHT = 36.dp
