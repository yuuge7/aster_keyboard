package app.aster.aster_keyboard.diagnostics

import android.app.Activity
import android.os.Bundle
import android.os.Build
import android.content.res.Configuration
import android.view.WindowInsets
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import app.aster.aster_keyboard.settings.SettingsStore

/** Real Android editors for verifying InputConnection behavior independently of Flutter. */
class EditorTestActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val theme = SettingsStore(this).read().theme
        val dark = theme == "dark" || theme == "system" && resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        setTheme(if (dark) android.R.style.Theme_Material_NoActionBar else android.R.style.Theme_Material_Light_NoActionBar)
        super.onCreate(savedInstanceState)
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 32, 24, 32) }
        column.setOnApplyWindowInsetsListener { _, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                column.setPadding(24 + bars.left, 32 + bars.top, 24 + bars.right, 32 + bars.bottom)
            } else {
                @Suppress("DEPRECATION")
                column.setPadding(24 + insets.systemWindowInsetLeft, 32 + insets.systemWindowInsetTop, 24 + insets.systemWindowInsetRight, 32 + insets.systemWindowInsetBottom)
            }
            insets
        }
        column.addView(TextView(this).apply { text = "Aster · Native editor tests"; textSize = 24f })
        column.addView(TextView(this).apply { text = "Test content is temporary. Use Android Back to return." })
        val fields = listOf(
            Triple("Message · multiline", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, EditorInfo.IME_ACTION_NONE),
            Triple("Search", InputType.TYPE_CLASS_TEXT, EditorInfo.IME_ACTION_SEARCH),
            Triple("Email · Next", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, EditorInfo.IME_ACTION_NEXT),
            Triple("Website · Go", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI, EditorInfo.IME_ACTION_GO),
            Triple("Password · Done", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD, EditorInfo.IME_ACTION_DONE),
            Triple("Signed decimal", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED, EditorInfo.IME_ACTION_DONE),
            Triple("PIN", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD, EditorInfo.IME_ACTION_DONE),
            Triple("Phone", InputType.TYPE_CLASS_PHONE, EditorInfo.IME_ACTION_DONE)
        )
        fields.forEachIndexed { index, (label, type, action) ->
            column.addView(EditText(this).apply {
                id = 1000 + index; hint = label; inputType = type; imeOptions = action
                isSaveEnabled = false
                if (index == 0) { minLines = 2; maxLines = 4 } else setSingleLine(true)
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 20 })
        }
        setContentView(ScrollView(this).apply { addView(column) })
    }
}
