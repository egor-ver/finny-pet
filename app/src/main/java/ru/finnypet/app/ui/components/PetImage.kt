package ru.finnypet.app.ui.components

import android.content.Context
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.finnypet.app.R
import ru.finnypet.app.domain.model.GrowthStage
import ru.finnypet.app.domain.model.PetAppearance
import ru.finnypet.app.ui.theme.Dimens

/**
 * Питомец: тело нужного окраса и стадии, поверх — аксессуар.
 *
 * Картинки лежат в контент-паке и называются по составу внешности, поэтому
 * новый окрас появляется в игре добавлением файла, без правок кода (ТЗ 2.5.14).
 *
 * Отсутствующий файл не роняет экран, а показывает заглушку: пока контент
 * наполняется, приложение обязано работать, а ТЗ 3.4 запрещает блокирующие
 * ошибки во время демонстрации.
 */
@Composable
fun PetImage(
    appearance: PetAppearance,
    stage: GrowthStage,
    modifier: Modifier = Modifier,
    size: Dp = 200.dp,
) {
    val context = LocalContext.current
    val body by loadAsset(context, bodyPath(appearance, stage))
    val accessory by loadAsset(context, appearance.accessoryId?.let { accessoryPath(it, stage) })

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        when (val picture = body) {
            null -> MissingPet()
            else -> Image(
                bitmap = picture,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
        accessory?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
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

private fun bodyPath(appearance: PetAppearance, stage: GrowthStage) =
    "content/v1/pets/${appearance.bodyId}_${appearance.colorId}_${stage.name.lowercase()}.png"

/**
 * Аксессуар свой на каждую стадию: шея у птенца и у взрослой совы в разных
 * местах, и одна картинка поверх всех трёх села бы мимо.
 */
private fun accessoryPath(accessoryId: String, stage: GrowthStage) =
    "content/v1/pets/acc_${accessoryId}_${stage.name.lowercase()}.png"

/**
 * Читает картинку из ассетов в фоне: разбор PNG на главном потоке задержал бы
 * отрисовку, а ТЗ 3.4 ограничивает отклик одной секундой.
 */
@Composable
private fun loadAsset(context: Context, path: String?) = produceState<ImageBitmap?>(null, path) {
    value = if (path == null) {
        null
    } else {
        withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open(path).use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
            }.getOrNull()
        }
    }
}
