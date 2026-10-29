package app.aster.aster_keyboard.ime

import android.app.Activity
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.aster.aster_keyboard.diagnostics.EditorTestActivity
import app.aster.aster_keyboard.settings.SettingsStore
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Real service and real editor: keys are pressed through Android accessibility. */
@RunWith(AndroidJUnit4::class)
class KeyboardFeatureTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val store = SettingsStore(context)
    private lateinit var activity: Activity
    private lateinit var field: EditText
    private var oldIme: String? = null
    private var oldHardwareSetting: String? = null
    private var previous: Map<String, *> = emptyMap<String, Any?>()
    private val automation get() = instrumentation.uiAutomation
    private fun shell(command: String): String = automation.executeShellCommand(command).use { descriptor ->
        android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().readText()
    }
    @Before fun setUp() {
        oldIme = Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        oldHardwareSetting = Settings.Secure.getString(context.contentResolver, "show_ime_with_hard_keyboard")
        previous = store.preferences.all.toMap()
        store.update(mapOf("language" to "en", "englishEnabled" to true, "romanianEnabled" to true,
            "autoCapitalization" to false, "autoCorrect" to true, "suggestions" to true,
            "punctuationSpace" to true, "restoreDiacritics" to true,
            "showToolbar" to true, "clipboardHistoryEnabled" to false))
        store.rememberEmojiCategory("Recent")
        automation.serviceInfo = automation.serviceInfo.apply { flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS }
        shell("ime enable app.aster.aster_keyboard/.ime.KeyboardInputMethodService")
        shell("ime set app.aster.aster_keyboard/.ime.KeyboardInputMethodService")
        shell("settings put secure show_ime_with_hard_keyboard 1")
        activity = instrumentation.startActivitySync(Intent(context, EditorTestActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        instrumentation.runOnMainSync {
            field = activity.findViewById(1000); field.requestFocus()
            field.postDelayed({ activity.getSystemService(InputMethodManager::class.java).showSoftInput(field, InputMethodManager.SHOW_IMPLICIT) }, 400)
        }
        await { node("Space, hold to switch language") != null }
    }
    @After fun tearDown() {
        if (::activity.isInitialized) instrumentation.runOnMainSync { activity.finish() }
        oldIme?.takeIf { it.isNotBlank() }?.let { shell("ime set $it") }
        if (oldHardwareSetting == null) shell("settings delete secure show_ime_with_hard_keyboard")
        else shell("settings put secure show_ime_with_hard_keyboard $oldHardwareSetting")
        store.preferences.edit().apply {
            clear()
            previous.forEach { (key, value) -> when (value) {
                is String -> putString(key, value); is Boolean -> putBoolean(key, value)
                is Int -> putInt(key, value); is Long -> putLong(key, value); is Float -> putFloat(key, value)
            } }
        }.commit()
    }
    private fun await(message: String = "Keyboard condition was not reached within 10 seconds", condition: () -> Boolean) {
        val until = System.currentTimeMillis() + 10000
        while (System.currentTimeMillis() < until) { if (condition()) return; Thread.sleep(40) }
        fail(message)
    }
    private fun node(description: String): AccessibilityNodeInfo? {
        fun search(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if (node == null) return null
            if (node.contentDescription?.toString() == description && node.packageName?.toString() == context.packageName) return node
            for (index in 0 until node.childCount) search(node.getChild(index))?.let { return it }
            return null
        }
        return automation.windows.firstNotNullOfOrNull { search(it.root) }
    }
    private fun click(description: String) {
        // A suggestion update can replace a node between lookup and ACTION_CLICK.
        await("Could not click $description") {
            node(description)?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
        }
        instrumentation.waitForIdleSync()
    }
    private fun type(word: String) { word.forEach {
        val alternative = "$it, hold for alternatives"
        await { node(alternative) != null || node(it.toString()) != null }
        click(if (node(alternative) != null) alternative else it.toString())
    } }
    private fun text(): String {
        var value = ""; instrumentation.runOnMainSync { value = field.text.toString() }; return value
    }
    private fun bounds(description: String) = android.graphics.Rect().also {
        await { node(description) != null }
        node(description)!!.getBoundsInScreen(it)
    }
    private fun motion(action: Int, down: Long, points: List<Pair<Int, Pair<Float, Float>>>) {
        val properties = points.map { (id, _) -> android.view.MotionEvent.PointerProperties().apply {
            this.id = id; toolType = android.view.MotionEvent.TOOL_TYPE_FINGER
        } }.toTypedArray()
        val coordinates = points.map { (_, point) -> android.view.MotionEvent.PointerCoords().apply {
            x = point.first; y = point.second; pressure = 1f; size = .1f
        } }.toTypedArray()
        val event = android.view.MotionEvent.obtain(down, android.os.SystemClock.uptimeMillis(), action,
            points.size, properties, coordinates, 0, 0, 1f, 1f, 0, 0, android.view.InputDevice.SOURCE_TOUCHSCREEN, 0)
        try { assertTrue("Touch injection failed", automation.injectInputEvent(event, false)) } finally { event.recycle() }
    }
    private fun rapidType(value: String) {
        automation.waitForIdle(200, 3000)
        val keys = value.toSet().associateWith { char ->
            val description = when {
                char == ' ' -> "Space, hold to switch language"
                node("$char, hold for alternatives") != null -> "$char, hold for alternatives"
                else -> char.toString()
            }
            val key = bounds(description)
            key.exactCenterX() to key.exactCenterY()
        }
        value.chunked(2).forEach { pair ->
            val down = android.os.SystemClock.uptimeMillis()
            val first = 0 to keys.getValue(pair[0])
            motion(android.view.MotionEvent.ACTION_DOWN, down, listOf(first))
            try {
                android.os.SystemClock.sleep(8)
                if (pair.length == 2) {
                    val second = 1 to keys.getValue(pair[1])
                    motion(android.view.MotionEvent.ACTION_POINTER_DOWN or (1 shl android.view.MotionEvent.ACTION_POINTER_INDEX_SHIFT), down, listOf(first, second))
                    android.os.SystemClock.sleep(8)
                    motion(android.view.MotionEvent.ACTION_POINTER_UP or (1 shl android.view.MotionEvent.ACTION_POINTER_INDEX_SHIFT), down, listOf(first, second))
                }
                android.os.SystemClock.sleep(8)
            } finally { motion(android.view.MotionEvent.ACTION_UP, down, listOf(first)) }
        }
    }
    @Test fun overlappingThumbsKeepPressOrder() {
        store.update(mapOf("autoCorrect" to false, "suggestions" to false))
        instrumentation.waitForIdleSync()
        val value = "alallalaaaaa"
        rapidType(value)
        await("Overlapping taps must retain key-down order; received ${text()}") { text() == value }
    }
    @Test fun smallFingerRollAndKeyGapsDoNotDropLetters() {
        store.update(mapOf("autoCorrect" to false, "suggestions" to false, "keySpacing" to 5))
        instrumentation.waitForIdleSync(); automation.waitForIdle(200, 3000)
        val key = bounds("a, hold for alternatives")
        val d = context.resources.displayMetrics.density
        val down = android.os.SystemClock.uptimeMillis()
        val point = 0 to (key.right - 2f to key.exactCenterY())
        motion(android.view.MotionEvent.ACTION_DOWN, down, listOf(point))
        motion(android.view.MotionEvent.ACTION_MOVE, down, listOf(0 to (key.right + 3 * d to key.exactCenterY())))
        motion(android.view.MotionEvent.ACTION_UP, down, listOf(0 to (key.right + 3 * d to key.exactCenterY())))
        val next = android.os.SystemClock.uptimeMillis()
        val gap = 0 to (key.left - 2 * d to key.exactCenterY())
        motion(android.view.MotionEvent.ACTION_DOWN, next, listOf(gap))
        motion(android.view.MotionEvent.ACTION_UP, next, listOf(gap))
        await("A small roll and a tap in a key's spacing must both type") { text() == "aa" }
    }
    @Test fun rapidRollingTypingKeepsWordsAndCorrectionsWithAssistanceEnabled() {
        store.update(mapOf("autoCorrect" to true, "suggestions" to true, "doubleSpacePeriod" to false))
        instrumentation.waitForIdleSync()
        val input = "teh keyboard hello world this is a fast keyboard ".repeat(4)
        val expected = "the keyboard hello world this is a fast keyboard ".repeat(4)
        rapidType(input)
        await("Rapid words must keep every character and immediate common corrections; received ${text()}") {
            text() == expected
        }
    }
    @Test fun rapidRomanianTypingRestoresCommonAccentsWithoutBlockingEnglish() {
        store.update(mapOf("language" to "ro", "autoCorrect" to true, "suggestions" to true, "doubleSpacePeriod" to false))
        instrumentation.waitForIdleSync()
        // Wait for the bundled dictionaries, rather than timing their cold load.
        type("multumesc"); await { node("Insert suggestion mulțumesc") != null }
        instrumentation.runOnMainSync {
            field.setText(""); activity.getSystemService(InputMethodManager::class.java).restartInput(field)
        }
        rapidType("buna multumesc keyboard maine ")
        await("Rapid Romanian typing must keep words intact; received ${text()}") { text() == "bună mulțumesc keyboard mâine " }
    }
    @Test fun rapidPrivateTypingDoesNotLoseRepeatedKeysOrEnableAssistance() {
        instrumentation.runOnMainSync {
            field.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            activity.getSystemService(InputMethodManager::class.java).restartInput(field)
        }
        await { node("▣ unavailable in private fields") != null }
        rapidType("privatepasswordletters")
        await { text() == "privatepasswordletters" }
        assertNull(node("Insert suggestion private"))
    }
    @Test fun rapidTypingWithHiddenToolbarDoesNotResizeKeysDuringABurst() {
        store.update(mapOf("showToolbar" to false, "autoCorrect" to false, "suggestions" to true, "doubleSpacePeriod" to false))
        instrumentation.waitForIdleSync()
        val value = "hello world this is a fast keyboard ".repeat(3)
        rapidType(value)
        await("Hidden-toolbar typing must keep all characters; received ${text()}") { text() == value }
    }
    @Test fun spacebarDragMovesCursorWithoutInsertingSpacesOrSwitchingLanguage() {
        store.update(mapOf("autoCorrect" to false))
        instrumentation.waitForIdleSync()
        type("hello"); click("Space, hold to switch language"); type("world")
        automation.waitForIdle(200, 3000)
        val space = bounds("Space, hold to switch language")
        val down = android.os.SystemClock.uptimeMillis()
        val x = space.exactCenterX(); val y = space.exactCenterY()
        motion(android.view.MotionEvent.ACTION_DOWN, down, listOf(0 to (x to y)))
        try {
            val step = 12 * context.resources.displayMetrics.density
            motion(android.view.MotionEvent.ACTION_MOVE, down, listOf(0 to (x - step * 3.2f to y)))
        } finally { motion(android.view.MotionEvent.ACTION_UP, down, listOf(0 to (x to y))) }
        await { var cursor = -1; instrumentation.runOnMainSync { cursor = field.selectionStart }; cursor == 8 }
        assertEquals("hello world", text()); assertEquals("en", store.read().language)
        type("x"); await { text() == "hello woxrld" }
    }
    @Test fun movingInsideAComposingWordPreservesTextAndStartsANewInsertion() {
        store.update(mapOf("autoCorrect" to false))
        instrumentation.waitForIdleSync()
        type("hello")
        instrumentation.runOnMainSync { field.setSelection(2) }
        await { node("Insert suggestion hello") == null }
        type("x")
        await { text() == "hexllo" }
    }
    @Test fun canceledPressDoesNotInsertTextAndSpaceHoldStillSwitchesLanguage() {
        automation.waitForIdle(200, 3000)
        val a = bounds("a, hold for alternatives")
        val down = android.os.SystemClock.uptimeMillis()
        val point = listOf(0 to (a.exactCenterX() to a.exactCenterY()))
        motion(android.view.MotionEvent.ACTION_DOWN, down, point)
        motion(android.view.MotionEvent.ACTION_CANCEL, down, point)
        instrumentation.waitForIdleSync(); assertEquals("", text())
        val space = bounds("Space, hold to switch language")
        val hold = android.os.SystemClock.uptimeMillis()
        val finger = listOf(0 to (space.exactCenterX() to space.exactCenterY()))
        motion(android.view.MotionEvent.ACTION_DOWN, hold, finger)
        try { await { store.read().language == "ro" } }
        finally { motion(android.view.MotionEvent.ACTION_UP, hold, finger) }
        assertEquals("", text())
    }
    @Test fun englishCorrectionAndImmediateUndoUseRealInputConnection() {
        type("teh"); await { node("Insert suggestion the") != null }
        click("Space, hold to switch language"); await { text() == "the " }
        click("Delete, hold to repeat"); await { text() == "teh" }
    }
    @Test fun romanianDiacriticsAndLanguageSwitchUseRealKeys() {
        click("Switch English and Romanian, hold to choose another keyboard")
        await { store.read().language == "ro" && node("a, hold for alternatives") != null }
        assertNull("Romanian must not have a dedicated diacritics row", node("ă"))
        type("multumesc"); await { node("Insert suggestion mulțumesc") != null }
        click("Space, hold to switch language"); await { text() == "mulțumesc " }
        listOf("a" to "ă", "a" to "â", "i" to "î", "s" to "ș", "t" to "ț").forEach { (key, accent) ->
            assertTrue(node("$key, hold for alternatives")!!.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK))
            click(accent)
        }
        await { text() == "mulțumesc ăâîșț" }
    }
    @Test fun configuredHoldDelayAndReleaseControlRepeatedDeletion() {
        store.update(mapOf("longPressDelay" to 800))
        instrumentation.waitForIdleSync()
        automation.waitForIdle(200, 3000)
        type("abcdef")
        val bounds = android.graphics.Rect()
        node("Delete, hold to repeat")!!.getBoundsInScreen(bounds)
        val start = android.os.SystemClock.uptimeMillis()
        fun touch(action: Int) {
            val event = android.view.MotionEvent.obtain(start, android.os.SystemClock.uptimeMillis(),
                action, bounds.exactCenterX(), bounds.exactCenterY(), 0)
            try { assertTrue(automation.injectInputEvent(event, true)) } finally { event.recycle() }
        }
        touch(android.view.MotionEvent.ACTION_DOWN)
        android.os.SystemClock.sleep(200)
        assertEquals("abcdef", text())
        android.os.SystemClock.sleep(750)
        assertTrue("Holding should start repeat after the configured delay", text().length < 6)
        touch(android.view.MotionEvent.ACTION_UP)
        val afterRelease = text()
        android.os.SystemClock.sleep(180)
        assertEquals("Deletion must stop on release", afterRelease, text())
    }
    @Test fun romanianSuggestsEnglishAndPersonalWordsWithEnglishLayoutDisabled() {
        store.update(mapOf("language" to "ro", "englishEnabled" to false, "personalWordsEn" to "Asterium"))
        instrumentation.waitForIdleSync()
        type("keybord")
        click("Insert suggestion keyboard")
        await { text() == "keyboard " }
        type("multumesc")
        await { node("Insert suggestion mulțumesc") != null }
        click("Space, hold to switch language")
        await { text() == "keyboard mulțumesc " }
        type("asteriu")
        click("Insert suggestion Asterium")
        await { text() == "keyboard mulțumesc Asterium " }
        assertEquals("ro", store.read().language)
        assertFalse(store.read().englishEnabled)
    }
    @Test fun romanianKeepsEnglishWordsAndStillRestoresCommonDiacritics() {
        store.update(mapOf("language" to "ro", "englishEnabled" to false))
        instrumentation.waitForIdleSync()
        type("keyboard"); click("Space, hold to switch language")
        await { text() == "keyboard " }
        type("buna"); click("Space, hold to switch language")
        await { text() == "keyboard bună " }
        type("maine"); click("Space, hold to switch language")
        await { text() == "keyboard bună mâine " }
    }
    @Test fun configuredHoldDelayOpensAccentsWithoutTypingTheBaseLetter() {
        store.update(mapOf("longPressDelay" to 800))
        instrumentation.waitForIdleSync()
        automation.waitForIdle(200, 3000)
        val bounds = android.graphics.Rect()
        node("a, hold for alternatives")!!.getBoundsInScreen(bounds)
        val start = android.os.SystemClock.uptimeMillis()
        fun touch(action: Int) {
            val event = android.view.MotionEvent.obtain(start, android.os.SystemClock.uptimeMillis(),
                action, bounds.exactCenterX(), bounds.exactCenterY(), 0)
            try { assertTrue(automation.injectInputEvent(event, true)) } finally { event.recycle() }
        }
        touch(android.view.MotionEvent.ACTION_DOWN)
        try {
            android.os.SystemClock.sleep(200)
            assertNull("Accents must wait for the configured hold delay", node("ă"))
            assertEquals("", text())
            await { node("ă") != null }
        } finally { touch(android.view.MotionEvent.ACTION_UP) }
        click("ă")
        await { text() == "ă" }
    }
    @Test fun commaSpacingAndEmojiPanelWorkInRealEditor() {
        type("hello"); click(",, hold for alternatives")
        type("world"); await { text() == "hello, world" }
        click("Emoji"); click("Faces emoji"); click(EmojiCatalog.description("😀"))
        await { text() == "hello, world😀" }
    }
    @Test fun romanianUnsignedAndSignedDecimalPadsAlwaysHaveADirectPoint() {
        store.update(mapOf("language" to "ro"))
        fun number(signed: Boolean) {
            instrumentation.runOnMainSync {
                field.setText("")
                field.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL or
                    if (signed) android.text.InputType.TYPE_NUMBER_FLAG_SIGNED else 0
                activity.getSystemService(InputMethodManager::class.java).restartInput(field)
                activity.getSystemService(InputMethodManager::class.java).showSoftInput(field, InputMethodManager.SHOW_IMPLICIT)
            }
            await { node("., hold for alternatives") != null }
        }
        number(false)
        assertNotNull("Comma remains available once, alongside a point", node(","))
        click("1"); click("., hold for alternatives"); click("5")
        await { text() == "1.5" }
        number(true)
        click("−"); click("1"); click("., hold for alternatives"); click("5")
        await { text() == "-1.5" }
    }
    @Test fun emptySuggestionsLeaveNoGapAndHiddenToolbarKeepsToolsReachable() {
        val language = android.graphics.Rect()
        val letter = android.graphics.Rect()
        node("Switch English and Romanian, hold to choose another keyboard")!!.getBoundsInScreen(language)
        node("q, hold for alternatives")!!.getBoundsInScreen(letter)
        assertTrue("Empty suggestions must not reserve a blank row", letter.top - language.bottom <= 8 * context.resources.displayMetrics.density)
        click("Keyboard tools"); click("Hide toolbar")
        await { !store.read().showToolbar }
        assertNull(node("Open keyboard settings"))
        click("Keyboard tools"); click("Clipboard")
        await { node("Turn history on") != null }
        click("Letters"); click("Keyboard tools"); click("Show toolbar")
        await { store.read().showToolbar && node("Open keyboard settings") != null }
    }
    @Test fun emojiStartsWithRecentAndRemembersCategoryAcrossEditorRestarts() {
        click("Emoji")
        assertTrue(node("Recent emoji")!!.isSelected)
        click("Nature emoji"); click(EmojiCatalog.description("🐱")); click("Letters")
        await { text() == "🐱" }
        instrumentation.runOnMainSync { activity.getSystemService(InputMethodManager::class.java).restartInput(field) }
        click("Emoji")
        assertTrue(node("Nature emoji")!!.isSelected)
        assertEquals("Nature", SettingsStore(context).emojiCategory())
        click("Recent emoji")
        await { node("Recent emoji")?.isSelected == true && node(EmojiCatalog.description("🐱")) != null }
    }
    private fun copyClip(text: String, sensitive: Boolean = false) {
        instrumentation.runOnMainSync {
            val clip = android.content.ClipData.newPlainText("Test clip", text)
            if (sensitive) clip.description.extras = android.os.PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
            context.getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(clip)
            if (sensitive) assertTrue("Android must retain the sensitive clip marker",
                context.getSystemService(android.content.ClipboardManager::class.java).primaryClipDescription?.extras?.getBoolean("android.content.extra.IS_SENSITIVE", false) == true)
            if (sensitive) assertNull("Sensitive clipboard text must be rejected by the reader",
                ClipboardText.read(context.getSystemService(android.content.ClipboardManager::class.java)))
        }
        instrumentation.waitForIdleSync()
    }
    @Test fun clipboardIsOptInPastesVerbatimAndSupportsPinDeleteClearAndSensitiveClips() {
        copyClip("lowercase,\nverbatim!")
        click("Clipboard")
        click("Paste clip: lowercase,\nverbatim!")
        await { text() == "lowercase,\nverbatim!" }
        assertFalse(store.read().clipboardHistoryEnabled)
        assertTrue(app.aster.aster_keyboard.settings.ClipboardStore(context).entries().isEmpty())
        click("Turn history on")
        click("Pin")
        assertTrue(app.aster.aster_keyboard.settings.ClipboardStore(context).entries().single().pinned)
        click("Delete clip")
        assertTrue(app.aster.aster_keyboard.settings.ClipboardStore(context).entries().isEmpty())
        copyClip("second clip")
        await { node("Paste clip: second clip") != null }
        copyClip("secret clip", sensitive = true)
        assertFalse("Sensitive clips must not be stored", app.aster.aster_keyboard.settings.ClipboardStore(context).entries().any { it.text == "secret clip" })
        assertNull("Sensitive clips must not appear in the panel", node("Paste clip: secret clip"))
        click("Clear all")
        assertTrue(app.aster.aster_keyboard.settings.ClipboardStore(context).entries().isEmpty())
        copyClip("third clip")
        await { node("Paste clip: third clip") != null }
        click("Turn history off")
        assertTrue(app.aster.aster_keyboard.settings.ClipboardStore(context).entries().isEmpty())
    }
    @Test fun privateEditorsCannotExposeOrCollectClipboardContents() {
        store.update(mapOf("clipboardHistoryEnabled" to true))
        instrumentation.runOnMainSync {
            field.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            activity.getSystemService(InputMethodManager::class.java).restartInput(field)
        }
        await { node("▣ unavailable in private fields") != null }
        copyClip("private test clip")
        assertNull(node("Clipboard"))
        assertFalse(app.aster.aster_keyboard.settings.ClipboardStore(context).entries().any { it.text == "private test clip" })
        type("hello")
        await { text() == "hello" }
    }
    @Test fun toolsCopyAndCutSelectionThroughTheRealEditor() {
        type("hello")
        click("Keyboard tools"); click("Select all"); click("Copy selection")
        click("Clipboard"); click("Paste clip: hello")
        await { text() == "hello" }
        click("Letters"); click("Keyboard tools"); click("Select all"); click("Cut selection")
        await { text().isEmpty() }
        click("Clipboard"); click("Paste clip: hello")
        await { text() == "hello" }
    }
}
