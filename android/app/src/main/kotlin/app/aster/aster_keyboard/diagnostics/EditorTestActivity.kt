package app.aster.aster_keyboard.diagnostics

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Real Android editors for verifying InputConnection behavior independently of Flutter. */
class EditorTestActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 32, 24, 32) }
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
