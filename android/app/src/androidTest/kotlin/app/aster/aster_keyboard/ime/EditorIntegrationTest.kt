package app.aster.aster_keyboard.ime

import android.text.InputType
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EditorIntegrationTest {
    private class RecordingConnection : BaseInputConnection(
        View(InstrumentationRegistry.getInstrumentation().targetContext), true) {
        var reads = 0
        var committed: String? = null
        var editorAction = -1
        var deleted = 0
        var keys = 0
        var selected: CharSequence? = null
        var preceding = "hello"
        override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence { reads++; return preceding }
        override fun getSelectedText(flags: Int): CharSequence? { reads++; return selected }
        override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean { committed = text?.toString(); return true }
        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean { deleted = beforeLength; return true }
        override fun performEditorAction(actionCode: Int): Boolean { editorAction = actionCode; return true }
        override fun sendKeyEvent(event: android.view.KeyEvent): Boolean { keys++; return true }
    }
    @Test fun passwordDeleteDoesNotReadEditorContents() {
        val connection = RecordingConnection()
        InputConnectionController { connection }.delete(true)
        assertEquals(0, connection.reads)
        assertEquals(2, connection.keys)
    }
    @Test fun deletionReplacesASelection() {
        val connection = RecordingConnection().apply { selected = "word" }
        InputConnectionController { connection }.delete(false)
        assertEquals("", connection.committed)
        assertEquals(0, connection.deleted)
    }
    @Test fun deletionPreservesUnicodeCodepoints() {
        val connection = RecordingConnection().apply { preceding = "hi😀" }
        InputConnectionController { connection }.delete(false)
        assertEquals(2, connection.deleted)
    }
    @Test fun actionAndNewlineAreDifferentOperations() {
        val connection = RecordingConnection()
        val editor = InputConnectionController { connection }
        editor.enter(EditorProfile(action = EditorInfo.IME_ACTION_SEARCH))
        assertEquals(EditorInfo.IME_ACTION_SEARCH, connection.editorAction)
        editor.enter(EditorProfile(multiline = true, action = EditorInfo.IME_ACTION_NONE))
        assertEquals("\n", connection.committed)
    }
    @Test fun editorFlagsChooseSafeLayouts() {
        val info = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            imeOptions = EditorInfo.IME_ACTION_DONE
        }
        val profile = EditorProfile.from(info)
        assertTrue(profile.sensitive)
        assertEquals(EditorKind.NUMBER, profile.kind)
        assertEquals(0, profile.caps)
        assertEquals("Done", profile.actionLabel)
    }
    @Test fun noEnterActionIsRespected() {
        val info = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            imeOptions = EditorInfo.IME_ACTION_SEND or EditorInfo.IME_FLAG_NO_ENTER_ACTION
        }
        assertEquals(EditorInfo.IME_ACTION_NONE, EditorProfile.from(info).action)
    }
}
