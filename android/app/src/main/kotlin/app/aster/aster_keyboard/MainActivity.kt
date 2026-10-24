package app.aster.aster_keyboard

import android.content.ComponentName
import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import app.aster.aster_keyboard.diagnostics.EditorTestActivity
import app.aster.aster_keyboard.ime.KeyboardInputMethodService
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "app.aster/keyboard")
            .setMethodCallHandler { call, result ->
                val manager = getSystemService(InputMethodManager::class.java)
                when (call.method) {
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
}
