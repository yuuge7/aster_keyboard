package app.aster.aster_keyboard.ime

import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.os.SystemClock
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import app.aster.aster_keyboard.MainActivity

class KeyboardInputMethodService : InputMethodService(), AsterKeyboardView.Listener {
    private var keyboard: AsterKeyboardView? = null
    private val shift = ShiftState()
    private var profile = EditorProfile()
    private val editor = InputConnectionController { currentInputConnection }
    override fun onCreateInputView(): View = AsterKeyboardView(this, this).also {
        keyboard = it
        it.configure(profile, shift.mode)
    }
    override fun onStartInput(info: EditorInfo, restarting: Boolean) {
        super.onStartInput(info, restarting)
        keyboard?.cancelPendingTouches()
        shift.reset()
        profile = EditorProfile.from(info)
        shift.autoCap(!profile.sensitive && editor.caps(profile.caps))
        keyboard?.configure(profile, shift.mode)
    }
    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        keyboard?.configure(profile, shift.mode)
    }
    override fun onEvaluateFullscreenMode() = false
    override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int,
        newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        if (profile.caps != 0 && !profile.sensitive) {
            shift.autoCap(editor.caps(profile.caps))
            keyboard?.updateShift(shift.mode)
        }
    }
    override fun onText(text: String) {
        editor.commit(if (shift.uppercase && text.length == 1 && text[0] in 'a'..'z') text.uppercase() else text)
        shift.consume()
        keyboard?.updateShift(shift.mode)
    }
    override fun onCommand(command: String, longPress: Boolean) {
        when (command) {
            "shift" -> { if (longPress) shift.lock() else shift.tap(SystemClock.uptimeMillis()); keyboard?.updateShift(shift.mode) }
            "delete" -> editor.delete(profile.sensitive)
            "enter" -> editor.enter(profile)
            "left" -> editor.move(false, longPress)
            "right" -> editor.move(true, longPress)
            "selectAll" -> editor.selectAll()
            "hide" -> requestHideSelf(0)
            "switch" -> getSystemService(InputMethodManager::class.java).showInputMethodPicker()
            "settings" -> startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
    override fun onFinishInputView(finishingInput: Boolean) {
        keyboard?.cancelPendingTouches()
        super.onFinishInputView(finishingInput)
    }
    override fun onFinishInput() {
        keyboard?.cancelPendingTouches()
        shift.reset()
        super.onFinishInput()
    }
    override fun onDestroy() {
        keyboard?.cancelPendingTouches()
        keyboard = null
        super.onDestroy()
    }
}
