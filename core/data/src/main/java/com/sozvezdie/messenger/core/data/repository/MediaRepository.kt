package com.sozvezdie.messenger.core.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import android.util.LruCache
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

/** Результат подготовки файла: id записи в базе и исходное имя. */
data class UploadedMedia(
    val mediaId: String,
    val fileName: String,
)

/**
 * Фото и документы без Firebase Storage.
 *
 * В бесплатном тарифе Storage недоступен, поэтому файл кладётся прямо
 * в Realtime Database одной строкой в кодировке Base64:
 *
 *   media/{chatId}/{mediaId} = { data, name, mime, size }
 *
 * У такого способа есть цена: база не предназначена для больших файлов.
 * Поэтому фотографии сжимаются до 1280 px по длинной стороне, а документы
 * ограничены 2 МБ. Этого хватает для прототипа и не выходит за лимиты
 * бесплатного тарифа.
 */
class MediaRepository(
    private val context: Context,
    private val database: FirebaseDatabase,
) {

    private val contentResolver get() = context.contentResolver

    // Небольшой кэш в памяти: одно и то же фото не скачивается при каждом показе.
    private val cache = object : LruCache<String, ByteArray>(CACHE_BYTES) {
        override fun sizeOf(key: String, value: ByteArray): Int = value.size
    }

    suspend fun uploadImage(chatId: String, uri: Uri): Result<UploadedMedia> =
        upload(chatId, uri, isImage = true)

    suspend fun uploadFile(chatId: String, uri: Uri): Result<UploadedMedia> =
        upload(chatId, uri, isImage = false)

    /**
     * Читает выбранный файл, при необходимости сжимает картинку
     * и сохраняет Base64-строку в узел media.
     */
    private suspend fun upload(
        chatId: String,
        uri: Uri,
        isImage: Boolean,
    ): Result<UploadedMedia> = withContext(Dispatchers.IO) {
        runCatching {
            val fileName = queryName(uri)
            val mime = contentResolver.getType(uri) ?: "application/octet-stream"
            val declaredSize = querySize(uri)

            if (!isImage && declaredSize > MAX_FILE_BYTES) {
                error("Файл больше 2 МБ: в прототипе файлы хранятся в базе данных")
            }
            if (declaredSize > MAX_RAW_IMAGE_BYTES) {
                error("Файл слишком большой (максимум 15 МБ)")
            }

            val raw = readBytes(uri) ?: error("Не удалось прочитать выбранный файл")
            val bytes = if (isImage) compressImage(raw, mime) else raw

            if (bytes.size > MAX_FILE_BYTES) {
                error("Файл больше 2 МБ: в прототипе файлы хранятся в базе данных")
            }

            val mediaId = database.reference
                .child("media").child(chatId).push().key
                ?: error("Не удалось получить id файла")

            val payload = mapOf(
                "data" to Base64.encodeToString(bytes, Base64.NO_WRAP),
                "name" to fileName,
                "mime" to mime,
                "size" to bytes.size,
            )
            database.reference
                .child("media").child(chatId).child(mediaId)
                .setValue(payload)
                .await()

            UploadedMedia(mediaId = mediaId, fileName = fileName)
        }.let { result ->
            result.exceptionOrNull()?.let { error ->
                Result.failure(Exception(error.message ?: "Не удалось отправить файл"))
            } ?: result
        }
    }

    /**
     * Достаёт файл из базы. Возвращает null, если записи нет
     * (например, её удалили) или база недоступна.
     */
    suspend fun loadBytes(chatId: String, mediaId: String): ByteArray? =
        withContext(Dispatchers.IO) {
            if (mediaId.isBlank()) return@withContext null
            val key = "$chatId/$mediaId"
            cache.get(key)?.let { return@withContext it }

            val snapshot = runCatching {
                database.reference.child("media").child(chatId).child(mediaId).get().await()
            }.getOrNull() ?: return@withContext null

            val encoded = snapshot.child("data").getValue(String::class.java)
                ?: return@withContext null
            val bytes = runCatching { Base64.decode(encoded, Base64.DEFAULT) }.getOrNull()
                ?: return@withContext null

            cache.put(key, bytes)
            bytes
        }

    /**
     * Сохраняет файл во временную папку приложения, чтобы его можно было
     * открыть другим приложением через FileProvider.
     */
    suspend fun saveToCache(chatId: String, mediaId: String, fileName: String): File? =
        withContext(Dispatchers.IO) {
            val bytes = loadBytes(chatId, mediaId) ?: return@withContext null
            val dir = File(context.cacheDir, "media").apply { mkdirs() }
            val safeName = fileName.ifBlank { "file" }
                .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            val file = File(dir, "${mediaId}_$safeName")
            file.writeBytes(bytes)
            file
        }

    /**
     * Сжимает фото до разумного размера: без этого снимок с телефона
     * весит 4–8 МБ и не помещается в базу.
     */
    private fun compressImage(raw: ByteArray, mime: String): ByteArray {
        // GIF может быть анимированным: его не пережимаем, чтобы не потерять анимацию.
        if (mime == "image/gif") return raw

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return raw

        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_IMAGE_SIDE * 2) {
            sample *= 2
        }
        val decoded = BitmapFactory.decodeByteArray(
            raw,
            0,
            raw.size,
            BitmapFactory.Options().apply { inSampleSize = sample },
        ) ?: return raw

        val scaled = scaleDown(decoded)
        var result = toJpeg(scaled, quality = 80)
        if (result.size > MAX_IMAGE_BYTES) {
            result = toJpeg(scaled, quality = 55)
        }
        if (result.size > MAX_IMAGE_BYTES) {
            result = toJpeg(scaleDown(scaled, maxSide = 960), quality = 55)
        }
        return result
    }

    private fun scaleDown(bitmap: Bitmap, maxSide: Int = MAX_IMAGE_SIDE): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= maxSide) return bitmap
        val ratio = maxSide.toFloat() / longest
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).toInt().coerceAtLeast(1),
            (bitmap.height * ratio).toInt().coerceAtLeast(1),
            true,
        )
    }

    private fun toJpeg(bitmap: Bitmap, quality: Int): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        return stream.toByteArray()
    }

    private fun readBytes(uri: Uri): ByteArray? =
        contentResolver.openInputStream(uri)?.use { it.readBytes() }

    private fun queryName(uri: Uri): String {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) return cursor.getString(index)
                }
            }
        return "Файл"
    }

    private fun querySize(uri: Uri): Long {
        contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (index >= 0 && !cursor.isNull(index)) return cursor.getLong(index)
                }
            }
        return 0L
    }

    private companion object {
        // Больше 2 МБ база данных обрабатывает медленно, а бесплатный лимит тратится быстро.
        const val MAX_FILE_BYTES = 2 * 1024 * 1024
        const val MAX_RAW_IMAGE_BYTES = 15 * 1024 * 1024
        const val MAX_IMAGE_BYTES = 1_500_000
        const val MAX_IMAGE_SIDE = 1280
        const val CACHE_BYTES = 8 * 1024 * 1024
    }
}
