package app.aster.aster_keyboard.ime

import android.icu.text.BreakIterator
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import java.util.Locale

/** Does not retain editor contents; always resolves the focused connection. */
class InputConnectionController(private val connection: () -> InputConnection?) : TextEditor {
    val selection = SelectionTracker()
    fun batch(action: () -> Unit) {
        val ic = connection()
        ic?.beginBatchEdit()
        try { action() } finally { ic?.endBatchEdit() }
    }
    override fun commit(text: String) { connection()?.let { selection.commit(text.length); if (!it.commitText(text, 1)) selection.reset() } }
    override fun compose(text: String) { connection()?.let { selection.compose(text.length); if (!it.setComposingText(text, 1)) selection.reset() } }
    override fun finish() { connection()?.let { selection.finishComposition(); it.finishComposingText() } }
    override fun before(length: Int): String = connection()?.getTextBeforeCursor(length, 0)?.toString().orEmpty()
    override fun replaceBefore(length: Int, text: String) {
        connection()?.let { ic ->
            ic.beginBatchEdit()
            try {
                selection.deleteBefore(length); ic.deleteSurroundingText(length, 0)
                if (text.isNotEmpty()) { selection.commit(text.length); ic.commitText(text, 1) }
            }
            finally { ic.endBatchEdit() }
        }
    }
    fun delete(sensitive: Boolean) {
        val ic = connection() ?: return
        // Never ask the editor for password contents, even transiently.
        if (sensitive) { key(KeyEvent.KEYCODE_DEL); return }
        if (!ic.getSelectedText(0).isNullOrEmpty()) { selection.commit(0); ic.commitText("", 1); return }
        val before = ic.getTextBeforeCursor(128, 0)?.toString().orEmpty()
        if (before.isEmpty()) { key(KeyEvent.KEYCODE_DEL); return }
        val breaks = BreakIterator.getCharacterInstance(Locale.ROOT)
        breaks.setText(before)
        val boundary = breaks.preceding(before.length)
        if (boundary >= 0) { selection.deleteBefore(before.length - boundary); ic.deleteSurroundingText(before.length - boundary, 0) }
        else { selection.reset(); ic.deleteSurroundingTextInCodePoints(1, 0) }
    }
    fun enter(profile: EditorProfile) {
        val ic = connection() ?: return
        if (profile.action in setOf(EditorInfo.IME_ACTION_GO, EditorInfo.IME_ACTION_SEARCH,
                EditorInfo.IME_ACTION_SEND, EditorInfo.IME_ACTION_NEXT,
                EditorInfo.IME_ACTION_PREVIOUS, EditorInfo.IME_ACTION_DONE)) {
            selection.reset()
            if (ic.performEditorAction(profile.action)) return
        }
        if (profile.multiline) commit("\n") else key(KeyEvent.KEYCODE_ENTER)
    }
    fun move(right: Boolean, select: Boolean = false) = key(
        if (right) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT,
        if (select) KeyEvent.META_SHIFT_ON else 0)
    fun moveBy(steps: Int) {
        val ic = connection() ?: return
        ic.beginBatchEdit()
        try { repeat(kotlin.math.abs(steps).coerceAtMost(32)) { move(steps > 0) } }
        finally { ic.endBatchEdit() }
    }
    fun selectAll() { selection.reset(); connection()?.performContextMenuAction(android.R.id.selectAll) }
    fun contextAction(action: Int) { selection.reset(); connection()?.performContextMenuAction(action) }
    fun caps(flags: Int): Boolean = flags != 0 && (connection()?.getCursorCapsMode(flags) ?: 0) != 0
    private fun key(code: Int, meta: Int = 0) {
        val ic = connection() ?: return
        selection.reset()
        ic.sendKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_DOWN, code, 0, meta))
        ic.sendKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_UP, code, 0, meta))
    }
}
