package ru.finnypet.app.ui.components

import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.sp

/**
 * Подпись из одного слова в узкой колонке — «Радость», «детёныш»,
 * «Планирование». Одной строкой, а при нехватке ширины уменьшается до неё:
 * Compose иначе рвёт слово посреди («Радост/ь»), а переносов по слогам в
 * нём нет. При шрифте 1,0 на 360 dp слова помещаются и не уменьшаются;
 * при 2,0 не мельче 9 sp — это 18 dp, не мельче обычного текста (16 sp) при
 * шрифте 1,0. При 12 sp «подросток» не влезал в треть карточки прогресса (F8).
 */
@Composable
fun OneWordText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
) {
    Text(
        text = text,
        style = style,
        color = color,
        textAlign = textAlign,
        maxLines = 1,
        autoSize = TextAutoSize.StepBased(minFontSize = MIN_WORD_SIZE, maxFontSize = style.fontSize),
        modifier = modifier,
    )
}

private val MIN_WORD_SIZE = 9.sp
