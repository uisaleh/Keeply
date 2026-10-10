package app.keeply.attachments

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.max

/** Longest side, in pixels, that imported images are scaled down to. */
object ImageSize {
    /** Receipts and warranty cards: enough to read small print. */
    const val PHOTO = 1600
    /** Logos and profile photos are only ever shown small. */
    const val ICON = 512
}

/**
 * Attachment files in app-private storage (`files/attachments/{id}.jpg`). Imports are
 * downscaled, rotated upright from EXIF and re-encoded as JPEG, which also strips the
 * original's metadata (location, camera serial) before anything is uploaded.
 */
class AttachmentFiles(context: Context) {
    private val appContext = context.applicationContext
    private val dir: File get() = File(appContext.filesDir, "attachments").apply { mkdirs() }

    fun file(id: String) = File(dir, "$id.jpg")
    fun exists(id: String) = file(id).let { it.exists() && it.length() > 0 }

    /** Copies [uri] into a new attachment file and returns its id, or null if it isn't a readable image. */
    suspend fun import(uri: Uri, maxSide: Int, id: String = UUID.randomUUID().toString()): String? =
        withContext(Dispatchers.IO) { if (writeScaled(uri, file(id), maxSide)) id else null }

    /** Same processing, written to [target] (used for the profile photo, which isn't an attachment). */
    suspend fun importTo(uri: Uri, target: File, maxSide: Int): Boolean =
        withContext(Dispatchers.IO) { writeScaled(uri, target, maxSide) }

    fun delete(ids: Collection<String>) = ids.forEach { file(it).delete() }

    /** Signing out: nothing of the previous account may stay on the device. */
    fun deleteAll() {
        dir.listFiles()?.forEach { it.delete() }
    }

    private fun writeScaled(uri: Uri, target: File, maxSide: Int): Boolean {
        val resolver = appContext.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return false
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false

        // Decode at the smallest power-of-two sample that still covers maxSide, then scale exactly.
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return false

        val orientation = runCatching {
            resolver.openInputStream(uri)?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL

        val scale = minOf(1f, maxSide.toFloat() / max(decoded.width, decoded.height))
        val matrix = Matrix().apply {
            postScale(scale, scale)
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
            }
        }
        val output = if (matrix.isIdentity) decoded else Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.outputStream().use { output.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
        if (output !== decoded) output.recycle()
        decoded.recycle()
        return tmp.renameTo(target)
    }

    private companion object {
        const val JPEG_QUALITY = 85
    }
}

