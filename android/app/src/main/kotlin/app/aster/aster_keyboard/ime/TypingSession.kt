package app.aster.aster_keyboard.ime

/** Pure typing policy; editor access is supplied by the Android adapter. */
interface TextEditor {
    fun compose(text: String)
    fun commit(text: String)
    fun finish()
    fun before(length: Int): String
    fun replaceBefore(length: Int, text: String)
}

class TypingSession(private val editor: TextEditor) {
    var word = ""
        private set
    private var automaticSpace = false
    private var lastSpace = -1000L
    private var undo: Pair<String, String>? = null
    fun reset(finish: Boolean = true) {
        if (finish && word.isNotEmpty()) editor.finish()
        word = ""; automaticSpace = false; undo = null; lastSpace = -1000L
    }
    fun finishWord(correction: (String) -> String?): String? {
        val corrected = word.takeIf { it.isNotEmpty() }?.let(correction)
        if (word.isNotEmpty()) {
            if (corrected != null && corrected != word) editor.commit(corrected) else editor.finish()
            word = ""
        }
        return corrected
    }
    fun type(text: String, compose: Boolean, correction: (String) -> String?,
        punctuationSpace: Boolean, doubleSpacePeriod: Boolean, now: Long) {
        if (text.length == 1 && (text[0].isLetter() || text in listOf("'", "’") && word.isNotEmpty()) && compose) {
            undo = null; automaticSpace = false
            if (word.length >= 40) { editor.finish(); word = "" }
            word += text; editor.compose(word); return
        }
        val original = word
        val corrected = finishWord(correction)
        undo = null
        var delimiter = text
        val numericSeparator = text in listOf(",", ":") && editor.before(1).lastOrNull()?.isDigit() == true
        if (punctuationSpace && !numericSeparator && text in listOf(",", ";", ":", "!", "?")) {
            if (automaticSpace && editor.before(1) == " ") editor.replaceBefore(1, "")
            delimiter = "$text "; automaticSpace = true
        } else if (text == " " && automaticSpace && editor.before(1) == " ") {
            automaticSpace = false; return
        } else {
            automaticSpace = false
            if (text == " " && doubleSpacePeriod && now - lastSpace in 0..700) {
                val before = editor.before(2)
                if (before.length == 2 && before[1] == ' ' && before[0].isLetterOrDigit()) {
                    editor.replaceBefore(1, ". "); lastSpace = -1000L; return
                }
            }
        }
        editor.commit(delimiter)
        lastSpace = if (text == " ") now else -1000L
        if (corrected != null && corrected != original) undo = original to (corrected + delimiter)
    }
    fun accept(candidate: String) {
        editor.commit(candidate); editor.commit(" "); word = ""; undo = null; automaticSpace = false
    }
    /** Returns false when the Android adapter should perform ordinary deletion. */
    fun backspace(): Boolean {
        if (word.isNotEmpty()) {
            word = word.dropLast(1); editor.compose(word)
            if (word.isEmpty()) editor.finish()
            return true
        }
        undo?.let { (original, replacement) ->
            undo = null
            if (editor.before(replacement.length) == replacement) {
                editor.replaceBefore(replacement.length, ""); word = original; editor.compose(original)
                automaticSpace = false; return true
            }
        }
        automaticSpace = false; lastSpace = -1000L
        return false
    }
}
