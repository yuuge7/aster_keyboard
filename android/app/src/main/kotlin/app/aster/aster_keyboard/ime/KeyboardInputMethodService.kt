package app.aster.aster_keyboard.ime

import android.content.Intent
import android.content.ClipboardManager
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
import app.aster.aster_keyboard.settings.ClipboardStore
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.Future

class KeyboardInputMethodService : InputMethodService(), AsterKeyboardView.Listener,
    SharedPreferences.OnSharedPreferenceChangeListener {
    private var keyboard: AsterKeyboardView? = null
    private val shift = ShiftState()
    private var profile = EditorProfile()
    private val editor = InputConnectionController { currentInputConnection }
    private val session = TypingSession(editor)
    private lateinit var store: SettingsStore
    private lateinit var clipboardStore: ClipboardStore
    private lateinit var clipboard: ClipboardManager
    private var lastClipboardText: String? = null
    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        if (active && isInputViewShown && !profile.sensitive) {
            lastClipboardText = null
            if (settings.clipboardHistoryEnabled || keyboard?.isClipboardOpen == true) refreshClipboard(capture = true)
        }
    }
    private var settings = KeyboardSettings()
    private lateinit var dictionaries: DictionaryRepository
    private lateinit var multilingual: MultilingualSuggestions
    private val worker = ScheduledThreadPoolExecutor(1).apply { removeOnCancelPolicy = true }
    private val main = Handler(Looper.getMainLooper())
    private var suggestionTask: Future<*>? = null
    @Volatile private var generation = 0
    private var correctionResult: Pair<String, String?>? = null
    private var personalWords = emptyMap<String, Set<String>>()
    private var protectedWords = emptySet<String>()
    private var active = false
    private var subtypeInitialized = false
    override fun onCreate() {
        super.onCreate()
        store = SettingsStore(this); readSettings()
        clipboardStore = ClipboardStore(this)
        if (!settings.clipboardHistoryEnabled) clipboardStore.clear()
        clipboard = getSystemService(ClipboardManager::class.java)
        clipboard.addPrimaryClipChangedListener(clipboardListener)
        store.preferences.registerOnSharedPreferenceChangeListener(this)
        dictionaries = DictionaryRepository(this)
        multilingual = MultilingualSuggestions { dictionaries[it] }
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
        keyboard?.configure(profile, shift.mode, settings, store.wallpaperFile(), store.recentEmoji(), store.emojiCategory())
        updateSuggestions()
    }
    override fun onStartInput(info: EditorInfo, restarting: Boolean) {
        // The old connection must not be queried after Android switches editors.
        session.reset(finish = false); generation++; suggestionTask?.cancel(true)
        super.onStartInput(info, restarting)
        editor.selection.reset(info.initialSelStart, info.initialSelEnd)
        active = true; readSettings(); profile = EditorProfile.from(info)
        shift.reset(); updateCapitalization(); configure()
    }
    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting); active = true; readSettings(); configure()
        main.post { if (active && isInputViewShown && !profile.sensitive && settings.clipboardHistoryEnabled) refreshClipboard(capture = true) }
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
        val ownUpdate = editor.selection.update(newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        if (!ownUpdate && session.word.isNotEmpty() && (newSelStart != candidatesEnd || newSelEnd != candidatesEnd)) {
            session.reset(); updateSuggestions()
        }
        updateCapitalization()
    }
    private fun updateCapitalization(typedLetter: Boolean = false) {
        val allowed = settings.autoCapitalization && profile.assistedTyping
        val capitals = if (typedLetter || session.word.isNotEmpty()) profile.caps and InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS != 0
            else allowed && editor.caps(profile.caps.takeIf { it != 0 } ?: InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
        shift.autoCap(allowed && capitals)
        keyboard?.updateShift(shift.mode)
    }
    private fun readSettings() {
        settings = store.read(); correctionResult = null
        personalWords = mapOf("en" to settings.personalWordsEn, "ro" to settings.personalWordsRo)
            .mapValues { (_, words) -> words.lines().filter { it.isNotBlank() }.toSet() }
        protectedWords = (settings.enabledLanguages + MultilingualSuggestions.languages(settings.language)).distinct()
            .flatMap { personalWords[it].orEmpty() }.map(WordLexicon::normalize).toSet()
    }
    private fun protectedFromCorrection(word: String, preferences: KeyboardSettings, protected: Set<String>): Boolean =
        WordLexicon.normalize(word) in protected ||
            multilingual.protectsEnglish(word, preferences.language, preferences.restoreDiacritics) ||
            (preferences.language != "ro" && preferences.enabledLanguages.filter { it != preferences.language }.any { dictionaries[it]?.isCommon(word) == true })
    private fun correction(word: String): String? {
        if (!profile.assistedTyping || !settings.autoCorrect) return null
        correctionResult?.takeIf { it.first == word }?.let { return it.second }
        if (protectedFromCorrection(word, settings, protectedWords)) return null
        // Never run an edit-distance search or wait for the worker on a key event.
        return dictionaries[settings.language]?.immediateCorrection(word, settings.restoreDiacritics)
    }
    override fun onText(text: String) {
        val value = if (shift.uppercase && text.length == 1 && text[0].isLetter()) text.uppercase() else text
        editor.batch {
            if (!profile.assistedTyping) {
                // No reads, composition, correction, recents, or punctuation transforms in private editors.
                editor.commit(value)
            } else {
                session.type(value, settings.suggestions || settings.autoCorrect, ::correction,
                    settings.punctuationSpace, settings.doubleSpacePeriod, SystemClock.uptimeMillis())
            }
        }
        shift.consume(); updateCapitalization(typedLetter = value.length == 1 && value[0].isLetter()); updateSuggestions()
    }
    override fun onSuggestion(word: String) {
        if (!profile.assistedTyping || session.word.isEmpty()) return
        editor.batch { session.accept(word) }; updateCapitalization(); updateSuggestions()
    }
    override fun onEmoji(emoji: String) {
        editor.batch { session.reset(); editor.commit(emoji) }
        if (profile.assistedTyping) store.rememberEmoji(emoji)
        updateSuggestions()
    }
    override fun onEmojiCategory(category: String) { store.rememberEmojiCategory(category) }
    override fun onCursorMove(steps: Int) {
        if (!active || steps == 0) return
        session.reset()
        editor.moveBy(steps)
        updateSuggestions(); updateCapitalization()
    }
    private fun currentClipboardText(): String? {
        if (!active || profile.sensitive) return null
        return ClipboardText.read(clipboard)
    }
    private fun clipboardEntries(capture: Boolean): List<ClipboardEntry> {
        val current = if (capture) currentClipboardText() else null
        if (!settings.clipboardHistoryEnabled) return current?.let { listOf(ClipboardEntry(it, System.currentTimeMillis())) }.orEmpty()
        if (current != null && current != lastClipboardText) {
            clipboardStore.record(current); lastClipboardText = current
        }
        return clipboardStore.entries()
    }
    private fun refreshClipboard(capture: Boolean = false) {
        if (!active || profile.sensitive) return
        keyboard?.refreshClipboard(clipboardEntries(capture), settings.clipboardHistoryEnabled)
    }
    override fun onClipboardPaste(text: String) {
        if (!active || profile.sensitive) return
        // Paste verbatim, with no capitalization, corrections, or punctuation transforms.
        editor.batch { session.reset(); editor.commit(text) }; updateCapitalization(); updateSuggestions()
    }
    override fun onClipboardAction(action: String, text: String) {
        if (!active || profile.sensitive || !settings.clipboardHistoryEnabled) return
        when (action) {
            "pin" -> clipboardStore.togglePin(text)
            "delete" -> clipboardStore.remove(text)
            "clear" -> clipboardStore.clear()
        }
        refreshClipboard()
    }
    override fun onCommand(command: String, longPress: Boolean) {
        when (command) {
            "shift" -> { if (longPress) shift.lock() else shift.tap(SystemClock.uptimeMillis()); keyboard?.updateShift(shift.mode) }
            "delete" -> { if (!profile.assistedTyping || !session.backspace()) editor.delete(profile.sensitive); updateSuggestions(); updateCapitalization() }
            "enter" -> { editor.batch { session.finishWord(::correction); session.reset(); editor.enter(profile) }; updateSuggestions(); updateCapitalization() }
            "left", "right" -> { session.reset(); editor.move(command == "right", longPress); updateSuggestions() }
            "selectAll" -> { session.reset(); editor.selectAll(); updateSuggestions() }
            "copy", "cut" -> if (!profile.sensitive) {
                session.reset(); editor.contextAction(if (command == "copy") android.R.id.copy else android.R.id.cut); updateSuggestions()
            }
            "clipboard" -> if (!profile.sensitive) {
                session.reset(); keyboard?.showClipboard(clipboardEntries(capture = true), settings.clipboardHistoryEnabled); updateSuggestions()
            }
            "clipboardHistory" -> if (!profile.sensitive) {
                store.update(mapOf("clipboardHistoryEnabled" to !settings.clipboardHistoryEnabled))
                refreshClipboard(capture = true)
            }
            "toolbar" -> store.update(mapOf("showToolbar" to !settings.showToolbar))
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
        correctionResult = null
        if (!active || !profile.assistedTyping || word.isEmpty() || !settings.suggestions && !settings.autoCorrect) {
            keyboard?.showSuggestions(emptyList()); return
        }
        val preferences = settings; val custom = personalWords; val protected = protectedWords
        keyboard?.showSuggestions(if (preferences.suggestions) listOf(word) else emptyList())
        suggestionTask = worker.schedule({
            if (token != generation || Thread.currentThread().isInterrupted) return@schedule
            val candidates = if (preferences.suggestions) multilingual.suggestions(word, preferences.language, custom) else emptyList()
            if (token != generation || Thread.currentThread().isInterrupted) return@schedule
            val corrected = if (preferences.autoCorrect && !protectedFromCorrection(word, preferences, protected))
                dictionaries[preferences.language]?.correction(word, preferences.restoreDiacritics) else null
            main.post {
                if (token == generation && active && profile.assistedTyping && session.word == word) {
                    correctionResult = word to corrected
                    if (preferences.suggestions) keyboard?.showSuggestions(listOf(word) + candidates.take(2))
                }
            }
        }, 24, TimeUnit.MILLISECONDS)
    }
    override fun onSharedPreferenceChanged(preferences: SharedPreferences, key: String?) {
        if (key in listOf("recentEmoji", "emojiCategory")) return
        if (key == "clipboardHistoryEnabled") {
            readSettings()
            if (!settings.clipboardHistoryEnabled) { clipboardStore.clear(); lastClipboardText = null }
            refreshClipboard(capture = keyboard?.isClipboardOpen == true)
            return
        }
        session.reset(); readSettings(); updateCapitalization(); configure()
    }
    override fun onFinishInputView(finishingInput: Boolean) {
        keyboard?.cancelPendingTouches(); keyboard?.clearTransientPanels(); session.reset(); active = false; generation++; suggestionTask?.cancel(true)
        keyboard?.showSuggestions(emptyList()); super.onFinishInputView(finishingInput)
    }
    override fun onFinishInput() {
        session.reset(); active = false; generation++; suggestionTask?.cancel(true); correctionResult = null; shift.reset(); super.onFinishInput()
    }
    override fun onDestroy() {
        clipboard.removePrimaryClipChangedListener(clipboardListener)
        lastClipboardText = null
        store.preferences.unregisterOnSharedPreferenceChangeListener(this)
        keyboard?.cancelPendingTouches(); keyboard = null; generation++; worker.shutdownNow(); main.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
