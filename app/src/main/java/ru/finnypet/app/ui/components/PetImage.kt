package ru.finnypet.app.ui.components

import android.content.res.AssetManager
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.finnypet.app.R
import ru.finnypet.app.domain.content.PetImageFiles
import ru.finnypet.app.domain.model.GrowthStage
import ru.finnypet.app.domain.model.PetAppearance
import ru.finnypet.app.ui.theme.Dimens
import java.io.IOException

/**
 * Питомец нужного окраса и стадии, в аксессуаре, если он выбран.
 *
 * Одна картинка, а не тело с накладкой: имена и причина — в [PetImageFiles].
 * Нет файла в аксессуаре — показывается та же сова без него; нет и её —
 * заглушка. Отсутствующий файл не роняет экран: пока контент наполняется,
 * приложение обязано работать, а ТЗ 3.4 запрещает блокирующие ошибки во
 * время демонстрации.
 *
 * Картинка помечена тегом с путём файла, из которого она прочитана: так тест
 * видит, что показана именно сова в шарфе, а не запасная без него.
 */
@Composable
fun PetImage(
    appearance: PetAppearance,
    stage: GrowthStage,
    modifier: Modifier = Modifier,
    size: Dp = 200.dp,
) {
    val picture = loadPet(appearance, stage, size)

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        when (picture) {
            // Пока грузится — пусто: заглушка с надписью «картинки нет»
            // мигала бы при каждом выборе окраса, обманывая ребёнка.
            PetPicture.Loading -> Unit
            PetPicture.Missing -> MissingPet()
            is PetPicture.Loaded -> Image(
                bitmap = picture.bitmap,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(picture.path),
            )
        }
    }
}

/** Пока картинок нет — силуэт с подписью, чтобы было видно, чего не хватает. */
@Composable
private fun MissingPet() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.create_pet_image_missing),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(Dimens.Space),
        )
    }
}

private sealed interface PetPicture {
    data object Loading : PetPicture
    data object Missing : PetPicture
    data class Loaded(val bitmap: ImageBitmap, val path: String) : PetPicture
}

/**
 * Читает первую найденную картинку из ассетов в фоне: разбор PNG на главном
 * потоке задержал бы отрисовку, а ТЗ 3.4 ограничивает отклик одной секундой.
 *
 * При смене внешности прежняя сова остаётся на экране, пока не прочитана
 * новая: сбрасывать в пустоту — значит мигать на каждое нажатие.
 */
@Composable
private fun loadPet(appearance: PetAppearance, stage: GrowthStage, size: Dp): PetPicture {
    val assets = LocalContext.current.assets
    val targetPx = with(LocalDensity.current) { size.roundToPx() }
    var picture by remember { mutableStateOf<PetPicture>(PetPicture.Loading) }

    LaunchedEffect(appearance, stage, targetPx) {
        picture = withContext(Dispatchers.IO) {
            PetImageFiles.candidates(appearance, stage).firstNotNullOfOrNull { path ->
                decode(assets, path, targetPx)?.let { PetPicture.Loaded(it, path) }
            }
        } ?: PetPicture.Missing
    }

    return picture
}

/**
 * Читает картинку не крупнее, чем нужно на экране: исходник 600×600, а на
 * главном экране сова 140 dp — полноразмерный битмап был бы вчетверо тяжелее
 * без пользы. Нет файла — `null`; всё прочее — настоящая ошибка, её не прячем.
 */
private fun decode(assets: AssetManager, path: String, targetPx: Int): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    try {
        assets.open(path).use { BitmapFactory.decodeStream(it, null, bounds) }
    } catch (_: IOException) {
        return null
    }
    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, targetPx)
    }
    return try {
        assets.open(path).use { BitmapFactory.decodeStream(it, null, options) }?.asImageBitmap()
    } catch (_: IOException) {
        null
    }
}

/** Степень двойки, при которой картинка остаётся не меньше нужного размера. */
private fun sampleSize(width: Int, height: Int, targetPx: Int): Int {
    if (targetPx <= 0) return 1
    var sample = 1
    while (width / (sample * 2) >= targetPx && height / (sample * 2) >= targetPx) {
        sample *= 2
    }
    return sample
}
