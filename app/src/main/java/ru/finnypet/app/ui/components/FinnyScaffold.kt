package ru.finnypet.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import ru.finnypet.app.R
import ru.finnypet.app.ui.theme.Dimens

/**
 * Каркас экрана с прокруткой.
 *
 * Заголовок и кнопка возврата всегда на одном месте — ТЗ 3.6 требует
 * единообразия навигации и расположения кнопки возврата. Поэтому экраны
 * не собирают Scaffold сами, а берут этот.
 *
 * Содержимое прокручивается всегда. При системном увеличении шрифта любой
 * экран перестаёт помещаться, и без прокрутки нижняя часть просто пропала
 * бы — а ТЗ 3.6 требует сохранять читаемость при увеличенном шрифте.
 *
 * Для экранов со списками есть [FinnyListScaffold]: вложить LazyColumn
 * сюда нельзя, он получит бесконечную высоту и упадёт.
 */
@Composable
fun FinnyScaffold(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: (@Composable () -> Unit)? = null,
    spacing: Dp = Dimens.Space,
    content: @Composable ColumnScope.() -> Unit,
) = FinnyScaffold(
    title = { TitleText(title) },
    modifier = modifier,
    onBack = onBack,
    actions = actions,
    bottomBar = bottomBar,
    spacing = spacing,
    content = content,
)

/**
 * Каркас с шапкой из своих элементов — для главного экрана: там вместо
 * заголовка кошелёк, а справа входы в подсказку и раздел для взрослого
 * (раздел 8 плана). Место и отступы шапки те же, что у всех экранов (ТЗ 3.6).
 */
@Composable
fun FinnyScaffold(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: (@Composable () -> Unit)? = null,
    spacing: Dp = Dimens.Space,
    content: @Composable ColumnScope.() -> Unit,
) {
    ScaffoldChrome(
        title = title,
        modifier = modifier,
        onBack = onBack,
        actions = actions,
        bottomBar = bottomBar,
    ) { insets ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.ScreenPadding, vertical = Dimens.Space),
            // Одинаковый ритм на всех экранах: расстояние между блоками
            // задаётся здесь, а не каждым экраном по-своему (ТЗ 3.6).
            // Экран может его сузить, если блоков много — например главный,
            // где по ТЗ 2.5.3 всё должно поместиться сразу.
            verticalArrangement = Arrangement.spacedBy(spacing),
            content = content,
        )
    }
}

/**
 * Каркас экрана со списком.
 *
 * Отдельный от [FinnyScaffold], потому что вложенный LazyColumn внутри
 * прокручиваемого Column получает неограниченную высоту и падает. Списки
 * нужны магазину (ТЗ 2.5.6), заданиям (2.5.8) и истории (2.5.11), поэтому
 * вариант заведён сразу, а не когда упадёт.
 *
 * Элементам списка обязательно давать key — иначе при изменении данных
 * Compose пересоберёт весь список вместо изменившихся строк.
 */
@Composable
fun FinnyListScaffold(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    bottomBar: (@Composable () -> Unit)? = null,
    spacing: Dp = Dimens.SpaceMedium,
    content: LazyListScope.() -> Unit,
) {
    ScaffoldChrome(
        title = { TitleText(title) },
        modifier = modifier,
        onBack = onBack,
        actions = {},
        bottomBar = bottomBar,
    ) { insets ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets),
            contentPadding = PaddingValues(
                horizontal = Dimens.ScreenPadding,
                vertical = Dimens.Space,
            ),
            verticalArrangement = Arrangement.spacedBy(spacing),
            content = content,
        )
    }
}

/**
 * Общая обвязка обоих каркасов: заголовок, возврат, нижняя панель и
 * отступы системных панелей.
 *
 * Отступы считаются по safeDrawing — это объединение системных панелей,
 * выреза под камеру и клавиатуры. Одно правило вместо трёх, и в альбомной
 * ориентации содержимое не уезжает под вырез.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScaffoldChrome(
    title: @Composable () -> Unit,
    modifier: Modifier,
    onBack: (() -> Unit)?,
    actions: @Composable RowScope.() -> Unit,
    bottomBar: (@Composable () -> Unit)?,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        // Вертикальные отступы распределяются вручную: верхняя панель берёт
        // свои сама, нижняя — ниже. Scaffold добавил бы их ещё раз поверх.
        // Горизонтальные оставлены ему: без них содержимое уходит под вырез.
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
        topBar = {
            TopAppBar(
                title = title,
                actions = actions,
                navigationIcon = {
                    if (onBack != null) {
                        val back = stringResource(R.string.action_back)
                        TextButton(
                            onClick = onBack,
                            // defaultMinSize, а не size: при системном
                            // увеличении шрифта глиф перерастает 48 dp,
                            // и жёсткая рамка обрезала бы его (ТЗ 3.6).
                            modifier = Modifier
                                .defaultMinSize(
                                    minWidth = Dimens.TouchTarget,
                                    minHeight = Dimens.TouchTarget,
                                )
                                .semantics { contentDescription = back },
                        ) {
                            Text(text = "‹", style = MaterialTheme.typography.headlineMedium)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        bottomBar = {
            // Панель собирается только когда есть что показать: пустая
            // всё равно занимала бы высоту своих отступов, а на экране
            // 360 dp по ТЗ 2.5.3 дорог каждый десяток точек.
            if (bottomBar != null) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        // Одним правилом: панель навигации, вырез камеры
                        // и клавиатура. Кнопка не должна уходить ни под
                        // что из этого — ТЗ 3.6 требует удобного нажатия.
                        .windowInsetsPadding(
                            WindowInsets.safeDrawing
                                .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
                        )
                        .padding(
                            start = Dimens.ScreenPadding,
                            end = Dimens.ScreenPadding,
                            bottom = Dimens.Space,
                        ),
                ) {
                    bottomBar()
                }
            }
        },
    ) { insets ->
        // Когда нижней панели нет, её отступ снизу взять неоткуда —
        // последний элемент ушёл бы под панель навигации.
        val bottom = if (bottomBar == null) {
            Modifier.windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)
            )
        } else {
            Modifier
        }
        Box(modifier = bottom) { content(insets) }
    }
}

@Composable
private fun TitleText(title: String) {
    Text(text = title, style = MaterialTheme.typography.titleLarge)
}
