package app.aster.aster_keyboard.ime

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.aster.aster_keyboard.settings.SettingsStore
import app.aster.aster_keyboard.settings.KeyboardSettings
import app.aster.aster_keyboard.settings.WallpaperImporter
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsAndWallpaperTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun romanianKeepsTheSameCompactHeightAndNumberRowRemainsOptional() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val keyboard = AsterKeyboardView(context, object : AsterKeyboardView.Listener {
                override fun onText(text: String) = Unit
                override fun onCommand(command: String, longPress: Boolean) = Unit
                override fun onSuggestion(word: String) = Unit
                override fun onEmoji(emoji: String) = Unit
            })
            fun height(language: String, numbers: Boolean = false): Int {
                keyboard.configure(EditorProfile(), ShiftMode.OFF, KeyboardSettings(
                    language = language, numberRow = numbers, suggestions = false,
                    keySpacing = 5, keyCornerRadius = 18, keyBorders = true,
                ), null, emptyList())
                val width = (360 * context.resources.displayMetrics.density).toInt()
                keyboard.measure(android.view.View.MeasureSpec.makeMeasureSpec(width, android.view.View.MeasureSpec.EXACTLY),
                    android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED))
                keyboard.layout(0, 0, width, keyboard.measuredHeight)
                assertEquals(width, keyboard.measuredWidth)
                return keyboard.measuredHeight
            }
            val englishHeight = height("en")
            assertEquals(englishHeight, height("ro"))
            assertTrue(height("ro", true) > englishHeight)
        }
    }
    @Test fun wallpaperImportResizesRotatesAndStripsMetadata() {
        val output = File(context.filesDir, "keyboard-wallpaper.jpg")
        val backup = output.takeIf { it.exists() }?.readBytes()
        val source = File(context.cacheDir, "wallpaper-test.jpg")
        try {
            val bitmap = Bitmap.createBitmap(3200, 1600, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.BLUE)
            source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }; bitmap.recycle()
            ExifInterface(source.absolutePath).apply {
                setAttribute(ExifInterface.TAG_MAKE, "Test camera")
                setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
                saveAttributes()
            }
            WallpaperImporter.import(context, Uri.fromFile(source))
            val imported = BitmapFactory.decodeFile(output.absolutePath)
            assertEquals(800, imported.width); assertEquals(1600, imported.height); imported.recycle()
            assertNull(ExifInterface(output.absolutePath).getAttribute(ExifInterface.TAG_MAKE))
            assertTrue(SettingsStore(context).snapshot()["wallpaperPath"].toString().isNotEmpty())
            source.writeText("not an image")
            val previous = output.readBytes()
            try { WallpaperImporter.import(context, Uri.fromFile(source)); fail("Invalid image was accepted") }
            catch (_: IllegalArgumentException) { assertArrayEquals(previous, output.readBytes()) }
        } finally {
            if (backup == null) output.delete() else output.writeBytes(backup)
            source.delete()
        }
    }
    @Test fun settingsPersistValidateAndKeepAtLeastOneLanguage() {
        // Restore the exact settings after testing this shared native store.
        val store = SettingsStore(context)
        val before = store.read()
        try {
            store.update(mapOf("theme" to "dark", "language" to "ro", "romanianEnabled" to true,
                "autoCapitalization" to false, "keyHeight" to 999,
                "keySpacing" to 4, "keyCornerRadius" to 12, "keyBorders" to true,
                "secondarySymbols" to false, "longPressDelay" to 650,
                "personalWordsRo" to "Andreea\nȘtefan\nAndreea"))
            val restored = SettingsStore(context).read()
            assertEquals("dark", restored.theme); assertEquals("ro", restored.language)
            assertFalse(restored.autoCapitalization); assertEquals(66, restored.keyHeight)
            assertEquals(4, restored.keySpacing); assertEquals(12, restored.keyCornerRadius)
            assertTrue(restored.keyBorders); assertFalse(restored.secondarySymbols)
            assertEquals(650, restored.longPressDelay)
            assertEquals("Andreea\nȘtefan", restored.personalWordsRo)
            try { store.update(mapOf("englishEnabled" to false, "romanianEnabled" to false)); fail("Disabled every language") }
            catch (_: IllegalArgumentException) { assertTrue(store.read().romanianEnabled) }
        } finally { store.update(before.toMap()) }
    }
}
