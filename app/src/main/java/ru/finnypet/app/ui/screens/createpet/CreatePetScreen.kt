package ru.finnypet.app.ui.screens.createpet

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.finnypet.app.R
import ru.finnypet.app.domain.model.GrowthStage
import ru.finnypet.app.ui.components.ButtonColumn
import ru.finnypet.app.ui.components.FinnyButton
import ru.finnypet.app.ui.components.FinnyScaffold
import ru.finnypet.app.ui.components.FinnySecondaryButton
import ru.finnypet.app.ui.components.PetImage
import ru.finnypet.app.ui.theme.Dimens

/**
 * Создание питомца (ТЗ 2.5.2): внешность и игровые имена.
 *
 * Питомец показан на стадии детёныша — именно таким игра его и заведёт.
 */
@Composable
fun CreatePetScreen(
    onBack: () -> Unit,
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
        onBack = onBack,
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
    onBack: () -> Unit,
    onBody: (String) -> Unit,
    onColor: (String) -> Unit,
    onAccessory: (String?) -> Unit,
    onChildName: (String) -> Unit,
    onPetName: (String) -> Unit,
    onCreate: () -> Unit,
) {
    FinnyScaffold(
        title = stringResource(R.string.create_pet_title),
        onBack = onBack,
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
                FinnySecondaryButton(text = stringResource(R.string.action_back), onClick = onBack)
            }
        },
    ) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            PetImage(appearance = state.appearance, stage = GrowthStage.CUB)
        }

        Text(
            text = stringResource(R.string.create_pet_appearance),
            style = MaterialTheme.typography.titleMedium,
        )

        // Тело показывается, только когда есть из чего выбирать: пока вид
        // один, строка с единственной кнопкой занимала бы место зря.
        if (state.bodies.size > 1) {
            OptionRow(
                label = stringResource(R.string.create_pet_body),
                options = state.bodies,
                selectedId = state.bodyId,
                // Вариант «без» есть только у аксессуара, поэтому сюда null
                // не приходит — но тип общий, и его надо развернуть.
                onSelect = { id -> id?.let(onBody) },
            )
        }
        OptionRow(
            label = stringResource(R.string.create_pet_color),
            options = state.colors,
            selectedId = state.colorId,
            onSelect = { id -> id?.let(onColor) },
        )
        if (state.accessories.isNotEmpty()) {
            OptionRow(
                label = stringResource(R.string.create_pet_accessory),
                options = state.accessories,
                selectedId = state.accessoryId,
                onSelect = onAccessory,
                noneLabel = stringResource(R.string.create_pet_no_accessory),
            )
        }

        Text(
            text = stringResource(R.string.create_pet_names),
            style = MaterialTheme.typography.titleMedium,
        )
        NameField(
            value = state.childName,
            onValueChange = onChildName,
            label = stringResource(R.string.create_pet_child_name),
        )
        NameField(
            value = state.petName,
            onValueChange = onPetName,
            label = stringResource(R.string.create_pet_pet_name),
            imeAction = ImeAction.Done,
        )
        Text(
            text = stringResource(R.string.create_pet_no_real_name),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Ряд вариантов. Прокручивается вбок, потому что при системном увеличении
 * шрифта названия в строку не помещаются, а переносить кнопки выбора хуже,
 * чем прокручивать.
 *
 * Когда задан [noneLabel], первым идёт вариант «без» — отсутствие аксессуара
 * такой же выбор, как и любой другой.
 */
@Composable
private fun OptionRow(
    label: String,
    options: List<AppearanceOption>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    noneLabel: String? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSmall)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSmall),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        ) {
            if (noneLabel != null) {
                OptionChip(
                    title = noneLabel,
                    selected = selectedId == null,
                    onClick = { onSelect(null) },
                )
            }
            options.forEach { option ->
                OptionChip(
                    title = option.title,
                    selected = option.id == selectedId,
                    onClick = { onSelect(option.id) },
                )
            }
        }
    }
}

@Composable
private fun OptionChip(title: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text = title, style = MaterialTheme.typography.labelLarge) },
        modifier = Modifier.defaultMinSize(minHeight = Dimens.TouchTarget),
    )
}

@Composable
private fun NameField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    imeAction: ImeAction = ImeAction.Next,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(text = label, style = MaterialTheme.typography.bodyMedium) },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge,
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
