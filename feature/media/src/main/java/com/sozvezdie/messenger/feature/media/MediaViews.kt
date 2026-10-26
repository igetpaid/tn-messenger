package com.sozvezdie.messenger.feature.media

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.webkit.MimeTypeMap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import java.io.File

/** Что удалось получить из базы: данные, ошибка или ещё грузим. */
private sealed interface MediaLoad {
    data object Loading : MediaLoad
    data object Failed : MediaLoad
    data class Ready(val bytes: ByteArray) : MediaLoad
}

/** Декодирует байты в картинку один раз на каждый набор данных. */
private fun decode(bytes: ByteArray): Bitmap? =
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

/**
 * Картинка внутри сообщения.
 *
 * Файл лежит в Realtime Database в Base64, поэтому его нужно сначала
 * загрузить (функция load), а потом показать. Пока грузим — крутим спиннер.
 */
@Composable
fun MessageImage(
    mediaId: String,
    load: suspend (String) -> ByteArray?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    val result by produceState<MediaLoad>(initialValue = MediaLoad.Loading, mediaId) {
        val bytes = load(mediaId)
        value = if (bytes == null) MediaLoad.Failed else MediaLoad.Ready(bytes)
    }
    val bitmap = remember(result) {
        (result as? MediaLoad.Ready)?.let { decode(it.bytes) }
    }

    Box(
        modifier = modifier
            .width(220.dp)
            .heightIn(min = 140.dp, max = 280.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        when {
            bitmap != null -> Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Изображение",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 140.dp, max = 280.dp),
            )

            result is MediaLoad.Failed -> Text(
                text = "Не удалось загрузить фото",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(12.dp),
            )

            else -> CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
            )
        }
    }
}

/** Карточка файла: имя и подсказка «открыть». */
@Composable
fun FileMessageCard(
    fileName: String,
    isMine: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val contentColor = if (isMine) Color.White else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(contentColor.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Description,
                contentDescription = null,
                tint = contentColor,
            )
        }
        Column(modifier = Modifier.padding(start = 10.dp)) {
            Text(
                text = fileName,
                color = contentColor,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(170.dp),
            )
            Text(
                text = "Нажмите, чтобы открыть",
                color = contentColor.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

/** Просмотр фотографии на весь экран. */
@Composable
fun ImagePreviewDialog(
    mediaId: String,
    load: suspend (String) -> ByteArray?,
    onDismiss: () -> Unit,
) {
    val result by produceState<MediaLoad>(initialValue = MediaLoad.Loading, mediaId) {
        val bytes = load(mediaId)
        value = if (bytes == null) MediaLoad.Failed else MediaLoad.Ready(bytes)
    }
    val bitmap = remember(result) {
        (result as? MediaLoad.Ready)?.let { decode(it.bytes) }
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black, RoundedCornerShape(16.dp))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            when {
                bitmap != null -> Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Просмотр изображения",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 200.dp)
                        .padding(4.dp),
                )

                result is MediaLoad.Failed -> Text(
                    text = "Не удалось загрузить фото",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(24.dp),
                )

                else -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 200.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = Color.White)
                }
            }
        }
    }
}

/**
 * Открывает сохранённый файл другим приложением.
 *
 * FileProvider выдаёт временную ссылку на файл из кэша: так чужие
 * приложения не получают доступ ко всей памяти телефона.
 * Если открыть нечем (на эмуляторе часто так и есть), предлагаем
 * отправить файл в другое приложение через «Поделиться».
 */
fun openCachedFile(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )
    val mime = MimeTypeMap.getSingleton()
        .getMimeTypeFromExtension(file.extension.lowercase())
        ?: "application/octet-stream"

    val view = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, mime)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)

    runCatching { context.startActivity(view) }.onFailure {
        val share = Intent(Intent.ACTION_SEND)
            .setType(mime)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching {
            context.startActivity(
                Intent.createChooser(share, "Открыть файл")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}
