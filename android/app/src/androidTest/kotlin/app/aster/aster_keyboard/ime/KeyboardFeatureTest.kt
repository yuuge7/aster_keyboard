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
            "punctuationSpace" to true, "restoreDiacritics" to true))
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
            if (node.contentDescription?.toString() == description) return node
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
        val bounds = android.graphics.Rect()
        node("a, hold for alternatives")!!.getBoundsInScreen(bounds)
        val start = android.os.SystemClock.uptimeMillis()
        fun touch(action: Int) {
            val event = android.view.MotionEvent.obtain(start, android.os.SystemClock.uptimeMillis(),
                action, bounds.exactCenterX(), bounds.exactCenterY(), 0)
            try { assertTrue(automation.injectInputEvent(event, true)) } finally { event.recycle() }
        }
        touch(android.view.MotionEvent.ACTION_DOWN)
        android.os.SystemClock.sleep(200)
        assertNull("Accents must wait for the configured hold delay", node("ă"))
        assertEquals("", text())
        await { node("ă") != null }
        touch(android.view.MotionEvent.ACTION_UP)
        click("ă")
        await { text() == "ă" }
    }
    @Test fun commaSpacingAndEmojiPanelWorkInRealEditor() {
        type("hello"); click(",, hold for alternatives")
        type("world"); await { text() == "hello, world" }
        click("Emoji"); click(EmojiCatalog.description("😀"))
        await { text() == "hello, world😀" }
    }
}
