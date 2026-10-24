package app.aster.aster_keyboard

import android.content.ComponentName
import android.content.Intent
import android.app.Activity
import app.aster.aster_keyboard.settings.SettingsStore
import app.aster.aster_keyboard.settings.WallpaperImporter
import java.util.concurrent.Executors
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import app.aster.aster_keyboard.diagnostics.EditorTestActivity
import app.aster.aster_keyboard.ime.KeyboardInputMethodService
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    private lateinit var keyboardChannel: MethodChannel
    private var wallpaperResult: MethodChannel.Result? = null
    private val imageWorker = Executors.newSingleThreadExecutor()
    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        keyboardChannel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "app.aster/keyboard")
        keyboardChannel.setMethodCallHandler { call, result ->
                val manager = getSystemService(InputMethodManager::class.java)
                when (call.method) {
                    "getSettings" -> result.success(SettingsStore(this).snapshot())
                    "updateSettings" -> try {
                        SettingsStore(this).update(call.arguments as? Map<*, *> ?: emptyMap<String, Any>())
                        result.success(SettingsStore(this).snapshot())
                    } catch (error: IllegalArgumentException) { result.error("invalid_setting", error.message, null) }
                    "getAppVersion" -> result.success(packageManager.getPackageInfo(packageName, 0).versionName)
                    "consumeSettingsRequest" -> {
                        result.success(intent.getBooleanExtra("openSettings", false)); intent.removeExtra("openSettings")
                    }
                    "chooseWallpaper" -> {
                        if (wallpaperResult != null) result.error("picker_busy", "Finish choosing the current image first", null)
                        else {
                            wallpaperResult = result
                            try {
                                @Suppress("DEPRECATION")
                                startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                                    type = "image/*"; addCategory(Intent.CATEGORY_OPENABLE)
                                }, 4021)
                            } catch (_: Exception) {
                                wallpaperResult = null; result.error("picker_unavailable", "No image picker is available on this device", null)
                            }
                        }
                    }
                    "removeWallpaper" -> { SettingsStore(this).removeWallpaper(); result.success(SettingsStore(this).snapshot()) }
                    "getStatus" -> {
                        val id = ComponentName(this, KeyboardInputMethodService::class.java).flattenToShortString()
                        result.success(mapOf(
                            "enabled" to manager.enabledInputMethodList.any { it.id == id },
                            "selected" to (Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) == id),
                            "androidVersion" to android.os.Build.VERSION.RELEASE,
                            "apiLevel" to android.os.Build.VERSION.SDK_INT
                        ))
                    }
                    "openInputSettings" -> { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)); result.success(null) }
                    "showInputPicker" -> { manager.showInputMethodPicker(); result.success(null) }
                    "openEditorTests" -> { startActivity(Intent(this, EditorTestActivity::class.java)); result.success(null) }
                    else -> result.notImplemented()
                }
            }
    }
    @Deprecated("Android activity result callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 4021) return
        val result = wallpaperResult ?: return
        wallpaperResult = null
        val uri = data?.data
        if (resultCode != Activity.RESULT_OK || uri == null) {
            result.success(SettingsStore(this).snapshot()); return
        }
        imageWorker.execute {
            try {
                WallpaperImporter.import(applicationContext, uri)
                runOnUiThread { result.success(SettingsStore(this).snapshot()) }
            } catch (_: Exception) {
                runOnUiThread { result.error("image_unavailable", "This image could not be opened. Try a JPEG or PNG photo.", null) }
            }
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent); setIntent(intent)
        if (intent.getBooleanExtra("openSettings", false) && ::keyboardChannel.isInitialized) keyboardChannel.invokeMethod("openSettings", null)
    }
    override fun onDestroy() {
        wallpaperResult?.error("picker_closed", "The image picker was closed", null)
        wallpaperResult = null; imageWorker.shutdown(); super.onDestroy()
    }
}
