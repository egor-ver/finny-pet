package ru.finnypet.app.ui.screens.createpet

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.finnypet.app.R
import ru.finnypet.app.ui.components.ButtonColumn
import ru.finnypet.app.ui.components.FinnyButton
import ru.finnypet.app.ui.components.FinnyScaffold
import ru.finnypet.app.ui.components.GLASSES_ID
import ru.finnypet.app.ui.components.Owl
import ru.finnypet.app.ui.components.OwlRole
import ru.finnypet.app.ui.components.SCARF_ID
import ru.finnypet.app.ui.components.TopSpeechBubble
import ru.finnypet.app.ui.components.icons.FinnyIcons
import ru.finnypet.app.ui.components.tile
import ru.finnypet.app.ui.theme.Dimens

/**
 * Создание питомца (ТЗ 2.5.2): внешность и игровые имена.
 *
 * Питомец показан на стадии детёныша — именно таким игра его и заведёт.
 *
 * Это первый экран новой игры (DESIGN_PLAN 3.4), поэтому кнопки «назад» нет:
 * возвращаться некуда, а системный «назад» просто закрывает приложение.
 */
@Composable
fun CreatePetScreen(
    onCreated: () -> Unit,
    viewModel: CreatePetViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Переход делает экран, а не ViewModel: NavController живёт только пока
    // жива композиция, и вызов из корутины после поворота ушёл бы в никуда.
    LaunchedEffect(state.created) {
        if (state.created) onCreated()
    }

    CreatePetContent(
        state = state,
        onBody = viewModel::selectBody,
        onColor = viewModel::selectColor,
        onAccessory = viewModel::selectAccessory,
        onChildName = viewModel::changeChildName,
        onPetName = viewModel::changePetName,
        onCreate = viewModel::create,
    )
}

/**
 * Отрисовка отделена от ViewModel: так экран можно показать в тесте
 * и в предпросмотре с любым состоянием, не поднимая граф зависимостей.
 */
@Composable
fun CreatePetContent(
    state: CreatePetState,
    onBody: (String) -> Unit,
    onColor: (String) -> Unit,
    onAccessory: (String?) -> Unit,
    onChildName: (String) -> Unit,
    onPetName: (String) -> Unit,
    onCreate: () -> Unit,
) {
    FinnyScaffold(
        title = stringResource(R.string.create_pet_title),
        // Ритм плотнее обычного, как на плане и задании: на Vivo (384 × 853 dp)
        // с отступами 16 поля имён уходили под «Готово», и ребёнок видел
        // неактивную кнопку, не видя, куда вписать имена.
        spacing = Dimens.SpaceMedium,
        bottomBar = {
            ButtonColumn {
                // Сбой сохранения объясняется прямо над кнопкой, и кнопка
                // остаётся доступной: ТЗ 3.4 не допускает тупиковых экранов.
                if (state.failed) {
                    Text(
                        text = stringResource(R.string.create_pet_failed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                FinnyButton(
                    text = stringResource(R.string.action_done),
                    onClick = onCreate,
                    enabled = state.canCreate,
                )
                // Без строки ребёнок жмёт бледную «Готово» и не понимает,
                // почему ничего не происходит (ТЗ 2.5.9: объяснение на любой исход).
                if (state.needsNames) {
                    Text(
                        text = stringResource(R.string.create_pet_needs_names),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    ) {
        // Сова здоровается сама: без этого игра начиналась бы с голой формы
        // (DESIGN_PLAN 3.3) — знакомство теперь идёт после создания.
        TopSpeechBubble(text = state.greeting)
        // Над ушками детёныша в квадрате совы ~43 dp пустого поля — ради них
        // поля имён и уходили за край. Верх рамки срезан на [OwlTopCut], но так,
        // что и в прыжке сова не задевает хвостик реплики.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(OwlRole.Create.size - OwlTopCut),
            contentAlignment = Alignment.BottomCenter,
        ) {
            // Прыжок на каждый новый выбор — ребёнок видит, что сова
            // откликается на него, а не просто перекрашивается.
            Owl(
                look = state.owl(stringResource(R.string.create_pet_preview)),
                size = OwlRole.Create.size,
                reactTo = state.appearance,
                // Сова остаётся 168 dp (DESIGN_PLAN 3.3): пустой верх её
                // квадрата рисуется поверх отступа, не занимая высоты в колонке.
                modifier = Modifier.wrapContentHeight(Alignment.Bottom, unbounded = true),
            )
        }

        // Заголовок прижат к своим рядам, а не отстоит от них, как от
        // соседней секции: так видно, к чему он относится, и экономится высота.
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSmall)) {
            Text(
                text = stringResource(R.string.create_pet_appearance),
                style = MaterialTheme.typography.titleMedium,
            )

            // Тело показывается, только когда есть из чего выбирать: пока вид
            // один, строка с единственной кнопкой занимала бы место зря.
            if (state.bodies.size > 1) {
                BodyRow(
                    options = state.bodies,
                    selectedId = state.bodyId,
                    onSelect = onBody,
                )
            }
            ColorRow(state = state, onColor = onColor)
            if (state.accessories.isNotEmpty()) {
                AccessoryRow(state = state, onAccessory = onAccessory)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSmall)) {
            Text(
                text = stringResource(R.string.create_pet_names),
                style = MaterialTheme.typography.titleMedium,
            )
            NameField(
                value = state.childName,
                onValueChange = onChildName,
                label = stringResource(R.string.create_pet_child_name),
                // Подсказка — под тем полем, к которому относится: настоящее имя
                // спросили бы именно здесь (ТЗ 3.5 — без персональных данных).
                hint = stringResource(R.string.create_pet_no_real_name),
            )
            NameField(
                value = state.petName,
                onValueChange = onPetName,
                label = stringResource(R.string.create_pet_pet_name),
                imeAction = ImeAction.Done,
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * Ряд видов питомца. Прокручивается вбок: при системном увеличении шрифта
 * названия в строку не помещаются, а переносить кнопки выбора хуже, чем
 * прокручивать.
 */
@Composable
private fun BodyRow(options: List<AppearanceOption>, selectedId: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSmall)) {
        SectionLabel(stringResource(R.string.create_pet_body))
        Row(
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSmall),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        ) {
            options.forEach { option ->
                FilterChip(
                    selected = option.id == selectedId,
                    onClick = { onSelect(option.id) },
                    label = { Text(text = option.title, style = MaterialTheme.typography.labelLarge) },
                    modifier = Modifier.defaultMinSize(minHeight = Dimens.TouchTarget),
                )
            }
        }
    }
}

/**
 * Окрас — круглые образцы цветом тела совы вместо текстовых чипов: длинное
 * «Дымчатый» в чипе обрезалось (DESIGN_PLAN 3.3). Название выбранного — в
 * подписи над рядом, а для TalkBack — у каждого образца.
 */
@Composable
private fun ColorRow(state: CreatePetState, onColor: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSmall)) {
        SectionLabel(stringResource(R.string.create_pet_color, state.colorTitle))
        // Образцы не растут со шрифтом, и пять в ряд помещаются в 360 dp;
        // прокрутка — на случай, если окрасов в контент-паке станет больше.
        Row(
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSmall),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .selectableGroup(),
        ) {
            state.colors.forEach { option ->
                Swatch(
                    color = Color(state.palette.first { it.id == option.id }.body),
                    title = option.title,
                    selected = option.id == state.colorId,
                    onClick = { onColor(option.id) },
                )
            }
        }
    }
}

/**
 * Выбранный образец отмечен кольцом и галочкой, а не только цветом (ТЗ 3.6).
 * Галочка на кружке `primary`: окрасы бывают и светлые, и тёмные, и белая
 * или тёмная галочка прямо на теле пропадала бы на одном из них.
 */
@Composable
private fun Swatch(color: Color, title: String, selected: Boolean, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(SwatchFrame)
            .clip(CircleShape)
            .then(if (selected) Modifier.border(SwatchRing, primary, CircleShape) else Modifier)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = title },
    ) {
        // Тонкий контур — белый окрас на белом фоне иначе не отличить от пустоты.
        Box(
            modifier = Modifier
                .size(SwatchSize)
                .clip(CircleShape)
                .background(color)
                .border(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = SWATCH_EDGE_ALPHA), CircleShape),
        )
        if (selected) {
            Icon(
                imageVector = FinnyIcons.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .size(CheckBadge)
                    .clip(CircleShape)
                    .background(primary)
                    .padding(Dimens.SpaceTiny),
            )
        }
    }
}

/**
 * Аксессуар — плитки с иконкой и подписью; «без» — первая, такой же выбор,
 * как любой другой. `FlowRow`: при крупном шрифте плитки переходят на новую
 * строку целиком, а не рвут подпись посреди слова.
 */
@Composable
private fun AccessoryRow(state: CreatePetState, onAccessory: (String?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSmall)) {
        SectionLabel(stringResource(R.string.create_pet_accessory))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSmall),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSmall),
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup(),
        ) {
            AccessoryTile(
                icon = FinnyIcons.None,
                title = stringResource(R.string.create_pet_no_accessory),
                selected = state.accessoryId == null,
                onClick = { onAccessory(null) },
            )
            state.accessories.forEach { option ->
                AccessoryTile(
                    icon = accessoryIcon(option.id),
                    title = option.title,
                    selected = option.id == state.accessoryId,
                    onClick = { onAccessory(option.id) },
                )
            }
        }
    }
}

/** Новый аксессуар из контент-пака, которого нет среди иконок, показывается одной подписью. */
private fun accessoryIcon(id: String): ImageVector? = when (id) {
    SCARF_ID -> FinnyIcons.Scarf
    GLASSES_ID -> FinnyIcons.Glasses
    else -> null
}

@Composable
private fun AccessoryTile(icon: ImageVector?, title: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .tile(marked = selected)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .defaultMinSize(minWidth = TileSize, minHeight = TileSize),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceTiny, Alignment.CenterVertically),
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = Dimens.SpaceMedium, vertical = Dimens.SpaceSmall),
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(TileIcon),
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Bold,
            )
        }
        if (selected) {
            Icon(
                imageVector = FinnyIcons.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(Dimens.SpaceTiny)
                    .size(TileCheck),
            )
        }
    }
}

/**
 * Поле белое и со скруглением плиток (DESIGN_PLAN 3.3). Рамка — `inkSoft`,
 * а не декоративный `outline`: на кремовом фоне белое поле почти не видно,
 * и граница — единственное, что показывает, куда писать (ТЗ 3.6).
 */
@Composable
private fun NameField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    imeAction: ImeAction = ImeAction.Next,
    hint: String? = null,
) {
    val surface = MaterialTheme.colorScheme.surface
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(text = label, style = MaterialTheme.typography.bodyMedium) },
        supportingText = hint?.let { { Text(text = it, style = MaterialTheme.typography.bodyMedium) } },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge,
        shape = RoundedCornerShape(Dimens.CornerTile),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = surface,
            unfocusedContainerColor = surface,
            unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        // Автозамена выключена намеренно: имена здесь выдуманные, и клавиатура
        // исправляет их на словарные — «Финни» превращается в «Финик».
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Words,
            autoCorrectEnabled = false,
            imeAction = imeAction,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = Dimens.TouchTarget),
    )
}

/**
 * Ушки детёныша — в 43 dp от верха квадрата 168 dp. Срез 32 dp оставляет над
 * ними 11 dp, и с отступом 12 dp прыжок на 14 dp не доходит до реплики.
 */
private val OwlTopCut = 32.dp

/** Образец 48 dp в рамке 56 dp: кольцо 3 dp и зазор 1 dp — пять в ряд на 328 dp. */
private val SwatchSize = 48.dp
private val SwatchFrame = 56.dp
private val SwatchRing = 3.dp
private val CheckBadge = 24.dp
private const val SWATCH_EDGE_ALPHA = 0.6f

private val TileSize = 72.dp
private val TileIcon = 32.dp
private val TileCheck = 18.dp
