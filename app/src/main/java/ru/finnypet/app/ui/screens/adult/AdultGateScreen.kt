package ru.finnypet.app.ui.screens.adult

import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import ru.finnypet.app.R
import ru.finnypet.app.ui.components.ButtonColumn
import ru.finnypet.app.ui.components.FinnyButton
import ru.finnypet.app.ui.components.FinnyScaffold
import ru.finnypet.app.ui.components.FinnySecondaryButton
import ru.finnypet.app.ui.theme.Dimens

/**
 * Пример, который отделяет раздел взрослого от детской части (ТЗ 2.5.12).
 *
 * Умножение двузначного на однозначное: взрослый считает в уме, ребёнок
 * 7–11 лет обычно нет. Это барьер, а не защита — ТЗ просит именно простой.
 */
data class Riddle(val left: Int, val right: Int) {

    val answer: Int get() = left * right

    companion object {
        val LEFT = 12..19
        val RIGHT = 3..9
    }
}

@Composable
fun AdultGateScreen(onSolved: () -> Unit, onBack: () -> Unit) {
    // Числа переживают поворот по отдельности: свой Saver ради двух чисел —
    // лишний класс, а новый пример после поворота выглядел бы как подмена
    // вопроса на полпути.
    val left by rememberSaveable { mutableIntStateOf(Riddle.LEFT.random()) }
    val right by rememberSaveable { mutableIntStateOf(Riddle.RIGHT.random()) }

    AdultGateContent(riddle = Riddle(left, right), onSolved = onSolved, onBack = onBack)
}

/** Трёхзначного ответа хватает: наибольшее произведение — 19 × 9. */
private const val MAX_DIGITS = 3

@Composable
fun AdultGateContent(riddle: Riddle, onSolved: () -> Unit, onBack: () -> Unit) {
    var typed by rememberSaveable { mutableStateOf("") }
    var wrong by rememberSaveable { mutableStateOf(false) }

    FinnyScaffold(
        title = stringResource(R.string.adult_gate_title),
        onBack = onBack,
        bottomBar = {
            ButtonColumn {
                FinnyButton(
                    text = stringResource(R.string.adult_gate_open),
                    enabled = typed.isNotBlank(),
                    onClick = {
                        if (typed.toIntOrNull() == riddle.answer) onSolved() else wrong = true
                    },
                )
                FinnySecondaryButton(text = stringResource(R.string.action_back), onClick = onBack)
            }
        },
    ) {
        Text(
            text = stringResource(R.string.adult_gate_explain),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.adult_gate_prompt, riddle.left, riddle.right),
            style = MaterialTheme.typography.titleLarge,
        )
        OutlinedTextField(
            value = typed,
            onValueChange = { entered ->
                typed = entered.filter(Char::isDigit).take(MAX_DIGITS)
                wrong = false
            },
            label = { Text(text = stringResource(R.string.adult_gate_answer)) },
            singleLine = true,
            isError = wrong,
            textStyle = MaterialTheme.typography.bodyLarge,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = Dimens.TouchTarget),
        )
        if (wrong) {
            // Словом, а не только красной рамкой: цвет не единственный
            // признак ошибки (ТЗ 3.6).
            Text(
                text = stringResource(R.string.adult_gate_wrong),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
