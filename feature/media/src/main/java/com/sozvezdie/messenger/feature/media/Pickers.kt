package com.sozvezdie.messenger.feature.media

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable

/**
 * Системный выбор фотографии. Разрешения на галерею не нужны:
 * Android отдаёт приложению только выбранный файл.
 *
 * Возвращает функцию, которую надо вызвать по нажатию на кнопку.
 */
@Composable
fun rememberPhotoPicker(onPicked: (Uri) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) onPicked(uri)
    }

    return {
        launcher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
        )
    }
}

/**
 * Системный выбор любого документа (PDF, Word, архив и т.д.).
 * Тоже работает без разрешений — через SAF.
 */
@Composable
fun rememberFilePicker(onPicked: (Uri) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) onPicked(uri)
    }

    return {
        // "*/*" — показать все типы файлов.
        launcher.launch(arrayOf("*/*"))
    }
}
