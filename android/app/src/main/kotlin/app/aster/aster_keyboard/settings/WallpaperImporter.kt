package app.aster.aster_keyboard.settings

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.AtomicFile
import java.io.File
import kotlin.math.max

object WallpaperImporter {
    // Only the selected image is opened. No media-library permission or saved URI.
    fun import(context: Context, uri: Uri) {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Choose a supported image" }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > 1600) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: error("The image could not be opened")
        val orientation = runCatching {
            resolver.openInputStream(uri)?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1) }
        }.getOrNull() ?: 1
        val matrix = Matrix().apply { when (orientation) {
            2 -> setScale(-1f, 1f); 3 -> setRotate(180f); 4 -> setScale(1f, -1f)
            5 -> { setRotate(90f); postScale(-1f, 1f) }; 6 -> setRotate(90f)
            7 -> { setRotate(270f); postScale(-1f, 1f) }; 8 -> setRotate(270f)
        } }
        val rotated = if (matrix.isIdentity) bitmap else Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        val file = AtomicFile(File(context.filesDir, "keyboard-wallpaper.jpg"))
        val output = file.startWrite()
        try {
            check(rotated.compress(Bitmap.CompressFormat.JPEG, 90, output))
            file.finishWrite(output)
        } catch (error: Exception) {
            file.failWrite(output)
            throw error
        } finally {
            if (rotated !== bitmap) rotated.recycle()
            bitmap.recycle()
        }
        // JPEG encoding discards the selected image's EXIF/location metadata.
        SettingsStore(context).preferences.edit().putLong("wallpaperRevision", System.currentTimeMillis()).apply()
    }
}
