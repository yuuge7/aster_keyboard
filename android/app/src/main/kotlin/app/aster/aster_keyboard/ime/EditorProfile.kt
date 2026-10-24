package app.aster.aster_keyboard.ime

import android.text.InputType
import android.view.inputmethod.EditorInfo

enum class EditorKind { TEXT, EMAIL, URL, NUMBER, PHONE }

data class EditorProfile(
    val kind: EditorKind = EditorKind.TEXT,
    val sensitive: Boolean = false,
    val multiline: Boolean = false,
    val action: Int = EditorInfo.IME_ACTION_UNSPECIFIED,
    val caps: Int = 0,
    val signed: Boolean = false,
    val decimal: Boolean = false,
    val noSuggestions: Boolean = false
) {
    val assistedTyping get() = kind == EditorKind.TEXT && !sensitive && !noSuggestions
    val actionLabel: String get() = when (action) {
        EditorInfo.IME_ACTION_GO -> "Go"
        EditorInfo.IME_ACTION_SEARCH -> "Search"
        EditorInfo.IME_ACTION_SEND -> "Send"
        EditorInfo.IME_ACTION_NEXT -> "Next"
        EditorInfo.IME_ACTION_PREVIOUS -> "Prev"
        EditorInfo.IME_ACTION_DONE -> "Done"
        else -> "↵"
    }
    companion object {
        fun from(info: EditorInfo): EditorProfile {
            val type = info.inputType
            val cls = type and InputType.TYPE_MASK_CLASS
            val variation = type and InputType.TYPE_MASK_VARIATION
            val password = (cls == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD) ||
                (cls == InputType.TYPE_CLASS_TEXT && variation in setOf(InputType.TYPE_TEXT_VARIATION_PASSWORD,
                    InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD, InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD))
            val privateField = password || (info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING != 0)
            val kind = when {
                cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_DATETIME -> EditorKind.NUMBER
                cls == InputType.TYPE_CLASS_PHONE -> EditorKind.PHONE
                variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS || variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS -> EditorKind.EMAIL
                variation == InputType.TYPE_TEXT_VARIATION_URI -> EditorKind.URL
                else -> EditorKind.TEXT
            }
            return EditorProfile(kind, privateField,
                cls == InputType.TYPE_CLASS_TEXT && type and InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0,
                if (info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0) EditorInfo.IME_ACTION_NONE
                else info.imeOptions and EditorInfo.IME_MASK_ACTION,
                if (privateField || cls != InputType.TYPE_CLASS_TEXT) 0 else type and (InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS or InputType.TYPE_TEXT_FLAG_CAP_WORDS or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES),
                type and InputType.TYPE_NUMBER_FLAG_SIGNED != 0,
                type and InputType.TYPE_NUMBER_FLAG_DECIMAL != 0,
                cls == InputType.TYPE_CLASS_TEXT && type and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS != 0)
        }
    }
}
