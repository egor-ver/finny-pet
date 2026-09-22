package ru.finnypet.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.finnypet.app.domain.model.Coins
import ru.finnypet.app.domain.model.ItemId
import ru.finnypet.app.domain.model.PetAppearance
import ru.finnypet.app.domain.model.TaskId
import ru.finnypet.app.domain.model.TaskTopic
import ru.finnypet.app.domain.model.SpendCategory
import ru.finnypet.app.domain.model.Stat
import ru.finnypet.app.ui.components.FinnyButton
import ru.finnypet.app.ui.components.FinnyListScaffold
import ru.finnypet.app.ui.components.FinnyScaffold
import ru.finnypet.app.ui.components.FinnySecondaryButton
import ru.finnypet.app.ui.components.MoneyAmount
import ru.finnypet.app.ui.components.MoneyCard
import ru.finnypet.app.ui.components.StatBar
import ru.finnypet.app.ui.components.StepButton
import ru.finnypet.app.ui.screens.shop.ShopContent
import ru.finnypet.app.ui.screens.shop.ShopItemView
import ru.finnypet.app.ui.screens.shop.ShopState
import ru.finnypet.app.ui.screens.tasks.OptionView
import ru.finnypet.app.ui.screens.tasks.PickItemView
import ru.finnypet.app.ui.screens.tasks.StepView
import ru.finnypet.app.ui.screens.tasks.TaskContent
import ru.finnypet.app.ui.screens.tasks.TaskStage
import ru.finnypet.app.ui.screens.tasks.TaskState
import ru.finnypet.app.ui.theme.FinnypetTheme
import ru.finnypet.app.ui.theme.LocalAnimationsEnabled

/**
 * Закрепляет требования доступности из ТЗ 3.6 в коде, а не в обещаниях.
 *
 * Размер нажимаемой области и прокрутка при длинном содержимом — это то,
 * что мы заявляем в документации. Без теста любая правка компонента могла
 * бы сломать их молча, и узнали бы мы об этом на защите.
 */
@RunWith(AndroidJUnit4::class)
class DesignSystemTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun главная_кнопка_не_меньше_48_dp() {
        compose.setContent {
            FinnypetTheme {
                FinnyButton(text = "Начать", onClick = {})
            }
        }

        compose.onNodeWithText("Начать")
            .assertHeightIsAtLeast(48.dp)
            .assertWidthIsAtLeast(48.dp)
    }

    @Test
    fun второстепенная_кнопка_не_меньше_48_dp() {
        compose.setContent {
            FinnypetTheme {
                FinnySecondaryButton(text = "Назад", onClick = {})
            }
        }

        compose.onNodeWithText("Назад")
            .assertHeightIsAtLeast(48.dp)
            .assertWidthIsAtLeast(48.dp)
    }

    @Test
    fun кнопка_возврата_не_меньше_48_dp() {
        compose.setContent {
            FinnypetTheme {
                FinnyScaffold(title = "Экран", onBack = {}) {
                    Text("Содержимое")
                }
            }
        }

        compose.onNodeWithContentDescription("Назад")
            .assertHeightIsAtLeast(48.dp)
            .assertWidthIsAtLeast(48.dp)
    }

    @Test
    fun кнопка_шага_не_меньше_48_dp() {
        compose.setContent {
            FinnypetTheme {
                StepButton(symbol = "+", description = "Больше", enabled = true, onClick = {})
            }
        }

        compose.onNodeWithContentDescription("Больше")
            .assertHeightIsAtLeast(48.dp)
            .assertWidthIsAtLeast(48.dp)
    }

    /** Карточка товара на полке задания — кнопка, и не меньше 48 dp. */
    @Test
    fun карточка_полки_не_меньше_48_dp() {
        compose.setContent {
            FinnypetTheme {
                TaskContent(
                    state = taskState(
                        StepView.Pick(
                            prompt = "Что возьмём?",
                            budget = Coins(30),
                            items = listOf(PickItemView("food", "Каша", Coins(12), SpendCategory.MANDATORY)),
                            picked = emptySet(),
                        )
                    ),
                    onBack = {},
                )
            }
        }

        compose.onNodeWithText("Каша").assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
    }

    /** Вариант в задании — кнопка, и не меньше 48 dp. */
    @Test
    fun вариант_задания_не_меньше_48_dp() {
        compose.setContent {
            FinnypetTheme {
                TaskContent(
                    state = taskState(
                        StepView.Choice(
                            prompt = "Отложить?",
                            options = listOf(OptionView("a", "Да"), OptionView("b", "Нет")),
                            chosen = null,
                        )
                    ),
                    onBack = {},
                )
            }
        }

        compose.onNodeWithText("Да").assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
    }

    private fun taskState(step: StepView) = TaskState.Ready(
        id = TaskId("t"),
        topic = TaskTopic.PAYMENTS,
        intro = "Вступление",
        appearance = PetAppearance(bodyId = "owl", colorId = "cream", accessoryId = null),
        maxReward = Coins(15),
        rewardAvailable = true,
        canStart = true,
        stage = TaskStage.Step(index = 0, total = 1, step = step),
    )

    /** Строка товара в магазине — вся целиком кнопка, и как кнопка не меньше 48 dp. */
    @Test
    fun строка_товара_не_меньше_48_dp() {
        compose.setContent {
            FinnypetTheme {
                ShopContent(
                    state = ShopState.Ready(
                        items = listOf(
                            ShopItemView(
                                id = ItemId("food"),
                                title = "Каша",
                                price = Coins(12),
                                category = SpendCategory.MANDATORY,
                                effects = emptyList(),
                            )
                        ),
                        balance = Coins(80),
                        canBuy = true,
                    ),
                    onBack = {},
                )
            }
        }

        compose.onNodeWithText("Каша")
            .assertHeightIsAtLeast(48.dp)
            .assertWidthIsAtLeast(48.dp)
    }

    @Test
    fun длинное_содержимое_прокручивается_до_последнего_элемента() {
        compose.setContent {
            FinnypetTheme {
                FinnyScaffold(title = "Экран") {
                    repeat(30) { index ->
                        MoneyCard(
                            label = "Строка $index",
                            amount = Coins(index),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Text("Последний элемент")
                }
            }
        }

        // Без прокрутки в каркасе этот элемент был бы недостижим:
        // при увеличенном шрифте так пропадала бы нижняя часть экрана.
        compose.onNodeWithText("Последний элемент")
            .performScrollTo()
            .assertIsDisplayed()
    }

    /**
     * Форма слова проверяется точным совпадением, а не вхождением: «80 монеты»
     * содержит в себе «80 монет», и проверка вхождением пропустила бы ошибку.
     * Она и пропускала, пока склонение выбирала локаль устройства.
     */
    @Test
    fun сумма_склоняется_по_русски() {
        compose.setContent {
            FinnypetTheme {
                Column {
                    MoneyAmount(amount = Coins(1))
                    MoneyAmount(amount = Coins(2))
                    MoneyAmount(amount = Coins(11))
                    MoneyAmount(amount = Coins(80))
                }
            }
        }

        compose.onNodeWithContentDescription("1 монета").assertIsDisplayed()
        compose.onNodeWithContentDescription("2 монеты").assertIsDisplayed()
        compose.onNodeWithContentDescription("11 монет").assertIsDisplayed()
        compose.onNodeWithContentDescription("80 монет").assertIsDisplayed()
    }

    @Test
    fun сумма_читается_вслух_одной_фразой() {
        compose.setContent {
            FinnypetTheme {
                MoneyCard(label = "Баланс", amount = Coins(80))
            }
        }

        compose.onNodeWithContentDescription("80 монет", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun список_прокручивается_до_последнего_элемента() {
        compose.setContent {
            FinnypetTheme {
                // Раньше сюда нельзя было положить LazyColumn: внутри
                // прокручиваемого Column он получал бесконечную высоту
                // и падал. Для списков заведён отдельный каркас.
                FinnyListScaffold(title = "Список") {
                    items(count = 40, key = { it }) { index ->
                        MoneyCard(
                            label = "Строка $index",
                            amount = Coins(index),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    item(key = "last") { Text("Последняя строка") }
                }
            }
        }

        // В LazyColumn элементы вне экрана не существуют в дереве, поэтому
        // искать их обычной прокруткой нельзя — нужно попросить сам список
        // домотать до нужного узла.
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Последняя строка"))
        compose.onNodeWithText("Последняя строка").assertIsDisplayed()
    }

    @Test
    fun отключённые_анимации_не_ломают_показатель() {
        compose.setContent {
            FinnypetTheme {
                CompositionLocalProvider(LocalAnimationsEnabled provides false) {
                    StatBar(label = "Сытость", stat = Stat(55))
                }
            }
        }

        // Полоса без анимации всё равно несёт подпись и число:
        // ТЗ 3.6 требует, чтобы при отключённом движении ничего не терялось.
        compose.onNodeWithContentDescription("Сытость: 55 из 100").assertIsDisplayed()
    }
}
