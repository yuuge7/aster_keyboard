package app.aster.aster_keyboard.ime

import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.InputType
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.InputMethodSubtype
import app.aster.aster_keyboard.MainActivity
import app.aster.aster_keyboard.settings.KeyboardSettings
import app.aster.aster_keyboard.settings.SettingsStore
import java.util.concurrent.Executors
import java.util.concurrent.Future

class KeyboardInputMethodService : InputMethodService(), AsterKeyboardView.Listener,
    SharedPreferences.OnSharedPreferenceChangeListener {
    private var keyboard: AsterKeyboardView? = null
    private val shift = ShiftState()
    private var profile = EditorProfile()
    private val editor = InputConnectionController { currentInputConnection }
    private val session = TypingSession(editor)
    private lateinit var store: SettingsStore
    private var settings = KeyboardSettings()
    private lateinit var dictionaries: DictionaryRepository
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var suggestionTask: Future<*>? = null
    private var generation = 0
    private var active = false
    private var subtypeInitialized = false
    override fun onCreate() {
        super.onCreate()
        store = SettingsStore(this); settings = store.read()
        store.preferences.registerOnSharedPreferenceChangeListener(this)
        dictionaries = DictionaryRepository(this)
        worker.execute {
            listOf(settings.language, if (settings.language == "en") "ro" else "en").forEach { language ->
                runCatching { dictionaries.load(language) }.onFailure {
                    android.util.Log.e("AsterDictionary", "Could not load bundled $language dictionary", it)
                }
            }
            main.post { if (active) updateSuggestions() }
        }
    }
    override fun onCreateInputView(): View = AsterKeyboardView(this, this).also {
        keyboard = it; configure()
    }
    private fun configure() {
        keyboard?.configure(profile, shift.mode, settings, store.wallpaperFile(), store.recentEmoji())
        updateSuggestions()
    }
    override fun onStartInput(info: EditorInfo, restarting: Boolean) {
        // The old connection must not be queried after Android switches editors.
        session.reset(finish = false); generation++; suggestionTask?.cancel(true)
        super.onStartInput(info, restarting)
        active = true; settings = store.read(); profile = EditorProfile.from(info)
        shift.reset(); updateCapitalization(); configure()
    }
    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting); active = true; settings = store.read(); configure()
    }
    override fun onCurrentInputMethodSubtypeChanged(newSubtype: InputMethodSubtype?) {
        super.onCurrentInputMethodSubtypeChanged(newSubtype)
        val initial = !subtypeInitialized; subtypeInitialized = true
        // Android supplies its default subtype when rebinding the service. Keep
        // the language the person explicitly chose in Aster across restarts.
        if (initial && ::store.isInitialized && store.preferences.contains("language")) return
        @Suppress("DEPRECATION") val language = newSubtype?.locale?.take(2)
        if (::store.isInitialized && language in settings.enabledLanguages) store.update(mapOf("language" to language))
    }
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig); if (::store.isInitialized) configure()
    }
    override fun onEvaluateFullscreenMode() = false
    override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int,
        newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        if (session.word.isNotEmpty() && (newSelStart != candidatesEnd || newSelEnd != candidatesEnd)) {
            session.reset(); updateSuggestions()
        }
        updateCapitalization()
    }
    private fun updateCapitalization() {
        val allowed = settings.autoCapitalization && profile.assistedTyping
        shift.autoCap(allowed && editor.caps(profile.caps.takeIf { it != 0 } ?: InputType.TYPE_TEXT_FLAG_CAP_SENTENCES))
        keyboard?.updateShift(shift.mode)
    }
    private fun personal(language: String) = (if (language == "en") settings.personalWordsEn else settings.personalWordsRo)
        .lines().filter { it.isNotBlank() }.toSet()
    private fun correction(word: String): String? {
        if (!profile.assistedTyping || !settings.autoCorrect) return null
        val protected = settings.enabledLanguages.flatMap { personal(it) }.toSet()
        val foreignWord = settings.enabledLanguages.filter { it != settings.language }.any { dictionaries[it]?.isCommon(word) == true }
        return if (foreignWord) null else dictionaries[settings.language]?.correction(word, settings.restoreDiacritics, protected)
    }
    override fun onText(text: String) {
        val value = if (shift.uppercase && text.length == 1 && text[0].isLetter()) text.uppercase() else text
        if (!profile.assistedTyping) {
            // No reads, composition, correction, recents, or punctuation transforms in private editors.
            editor.commit(value)
        } else {
            session.type(value, settings.suggestions || settings.autoCorrect, ::correction,
                settings.punctuationSpace, settings.doubleSpacePeriod, SystemClock.uptimeMillis())
        }
        shift.consume(); updateCapitalization(); updateSuggestions()
    }
    override fun onSuggestion(word: String) {
        if (!profile.assistedTyping || session.word.isEmpty()) return
        session.accept(word); updateCapitalization(); updateSuggestions()
    }
    override fun onEmoji(emoji: String) {
        session.reset(); editor.commit(emoji)
        if (profile.assistedTyping) store.rememberEmoji(emoji)
        updateSuggestions()
    }
    override fun onCommand(command: String, longPress: Boolean) {
        when (command) {
            "shift" -> { if (longPress) shift.lock() else shift.tap(SystemClock.uptimeMillis()); keyboard?.updateShift(shift.mode) }
            "delete" -> { if (!profile.assistedTyping || !session.backspace()) editor.delete(profile.sensitive); updateSuggestions(); updateCapitalization() }
            "enter" -> { session.finishWord(::correction); session.reset(); editor.enter(profile); updateSuggestions(); updateCapitalization() }
            "left", "right" -> { session.reset(); editor.move(command == "right", longPress); updateSuggestions() }
            "selectAll" -> { session.reset(); editor.selectAll(); updateSuggestions() }
            "hide" -> { session.reset(); requestHideSelf(0) }
            "switch" -> if (longPress || settings.enabledLanguages.size == 1) {
                session.reset(); getSystemService(InputMethodManager::class.java).showInputMethodPicker()
            } else {
                session.reset()
                val languages = settings.enabledLanguages
                store.update(mapOf("language" to languages[(languages.indexOf(settings.language) + 1) % languages.size]))
            }
            "settings" -> {
                session.reset(); requestHideSelf(0)
                startActivity(Intent(this, MainActivity::class.java).putExtra("openSettings", true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }
    private fun updateSuggestions() {
        val token = ++generation; suggestionTask?.cancel(true)
        val word = session.word
        if (!active || !settings.suggestions || !profile.assistedTyping || word.isEmpty()) {
            keyboard?.showSuggestions(emptyList()); return
        }
        val language = settings.language; val custom = personal(language)
        suggestionTask = worker.submit {
            val candidates = dictionaries[language]?.suggestions(word, custom).orEmpty()
            main.post { if (token == generation && active && profile.assistedTyping) keyboard?.showSuggestions(listOf(word) + candidates.take(2)) }
        }
    }
    override fun onSharedPreferenceChanged(preferences: SharedPreferences, key: String?) {
        if (key == "recentEmoji") return
        session.reset(); settings = store.read(); updateCapitalization(); configure()
    }
    override fun onFinishInputView(finishingInput: Boolean) {
        keyboard?.cancelPendingTouches(); session.reset(); active = false; generation++; suggestionTask?.cancel(true)
        keyboard?.showSuggestions(emptyList()); super.onFinishInputView(finishingInput)
    }
    override fun onFinishInput() {
        session.reset(); active = false; generation++; shift.reset(); super.onFinishInput()
    }
    override fun onDestroy() {
        store.preferences.unregisterOnSharedPreferenceChangeListener(this)
        keyboard?.cancelPendingTouches(); keyboard = null; generation++; worker.shutdownNow(); main.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
