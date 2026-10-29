package app.aster.aster_keyboard.ime

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.*
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.os.Build
import android.widget.*
import app.aster.aster_keyboard.settings.KeyboardSettings
import java.io.File

/** Native IME: settings, photos, suggestions and emoji never start Flutter. */
class AsterKeyboardView(context: Context, private val listener: Listener) : LinearLayout(context) {
    interface Listener {
        fun onText(text: String)
        fun onCommand(command: String, longPress: Boolean = false)
        fun onSuggestion(word: String)
        fun onEmoji(emoji: String)
        fun onEmojiCategory(category: String) {}
        fun onClipboardPaste(text: String) {}
        fun onClipboardAction(action: String, text: String) {}
        fun onCursorMove(steps: Int) {}
    }
    private data class Key(val label: String, val value: String = label, val command: String? = null,
        val weight: Float = 1f, val alternates: List<String> = emptyList())
    private val handler = Handler(Looper.getMainLooper())
    private val touches = KeyTouchCoordinator()
    private val letters = mutableListOf<Pair<TextView, String>>()
    private var shiftKey: TextView? = null
    private var suggestions: LinearLayout? = null
    private var toolbarRow: LinearLayout? = null
    private var lastKeyTouch = -1000L
    private val suggestionVisibility = Runnable { applySuggestionVisibility() }
    private var popup: PopupWindow? = null
    private var profile = EditorProfile()
    private var settings = KeyboardSettings()
    private var shiftMode = ShiftMode.OFF
    private var drawnShift: ShiftMode? = null
    private var suggestionWords = emptyList<String>()
    private val suggestionButtons = mutableListOf<TextView>()
    private var page = 0
    private var emojiCategory = "Recent"
    private var recent = emptyList<String>()
    private var clips = emptyList<ClipboardEntry>()
    private var historyEnabled = false
    val isClipboardOpen get() = page == 4
    private var photo: Bitmap? = null
    private var photoStamp = ""
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val photoBounds = RectF()
    private var dark = false
    private var ink = 0; private var surface = 0; private var modifier = 0; private var accent = 0; private var keyColor = 0
    private val landscape get() = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    init {
        isMotionEventSplittingEnabled = true
        orientation = VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_LTR; setWillNotDraw(false)
        setPadding(dp(4), dp(3), dp(4), dp(6))
        setOnApplyWindowInsetsListener { _, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(WindowInsets.Type.navigationBars() or WindowInsets.Type.displayCutout())
                applyKeyboardInsets(bars.left, bars.right, bars.bottom)
            } else {
                @Suppress("DEPRECATION")
                applyKeyboardInsets(insets.systemWindowInsetLeft, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            }
            insets
        }
    }
    private fun applyKeyboardInsets(left: Int, right: Int, bottom: Int) {
        val start = dp(4) + left; val end = dp(4) + right; val lower = dp(6) + bottom
        if (paddingLeft == start && paddingRight == end && paddingBottom == lower) return
        setPadding(start, dp(3), end, lower)
        // Insets can arrive inside the first window traversal, after its height
        // was chosen. Request a fresh traversal so the last key row stays visible.
        post { requestLayout() }
    }
    fun configure(editor: EditorProfile, shift: ShiftMode, preferences: KeyboardSettings, wallpaper: File?, recents: List<String>, lastEmojiCategory: String = "Recent") {
        cancelPendingTouches(); profile = editor; shiftMode = shift; settings = preferences; recent = recents; page = 0
        clips = emptyList(); historyEnabled = preferences.clipboardHistoryEnabled
        emojiCategory = lastEmojiCategory.takeIf { it == "Recent" || it in EmojiCatalog.categories } ?: "Recent"
        dark = settings.theme == "dark" || settings.theme == "system" && resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        ink = Color.parseColor(if (dark) "#E6EDF8" else "#1A273B")
        surface = Color.parseColor(if (dark) "#101826" else "#E9EFF6")
        modifier = Color.parseColor(if (dark) "#30425C" else "#D5E0EF")
        accent = Color.parseColor(if (dark) "#ADC9FF" else "#25569D")
        keyColor = Color.parseColor(if (dark) "#233249" else "#FFFFFF")
        val stamp = wallpaper?.let { "${it.absolutePath}:${it.lastModified()}" }.orEmpty()
        if (stamp != photoStamp) {
            photo = wallpaper?.let { BitmapFactory.decodeFile(it.absolutePath) }; photoStamp = stamp
        }
        setBackgroundColor(surface); render(); invalidate()
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        photo?.takeIf { !it.isRecycled && width > 0 && height > 0 }?.let { bitmap ->
            val scale = maxOf(width.toFloat() / bitmap.width, height.toFloat() / bitmap.height)
            val w = bitmap.width * scale; val h = bitmap.height * scale
            photoBounds.set((width - w) / 2, (height - h) / 2, (width + w) / 2, (height + h) / 2)
            canvas.drawBitmap(bitmap, null, photoBounds, paint)
            canvas.drawColor(Color.argb((settings.wallpaperDim * 255).toInt(), 0, 0, 0))
        }
    }
    fun updateShift(mode: ShiftMode) {
        shiftMode = mode
        if (drawnShift == mode) return
        drawnShift = mode
        letters.forEach { (view, value) ->
            view.text = if (mode == ShiftMode.OFF) value else value.uppercase()
            (view as? KeyLabelView)?.uppercaseHint = mode != ShiftMode.OFF
        }
        shiftKey?.apply {
            text = if (mode == ShiftMode.LOCKED) "⇪" else "⇧"
            contentDescription = when (mode) { ShiftMode.OFF -> "Shift"; ShiftMode.ONCE -> "Shift on"; ShiftMode.LOCKED -> "Caps lock on" }
            background = backgroundFor(if (mode == ShiftMode.OFF) modifier else accent)
            setTextColor(if (mode == ShiftMode.OFF) ink else if (dark) surface else Color.WHITE)
        }
    }
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        lastKeyTouch = SystemClock.uptimeMillis()
        if (event.actionMasked == MotionEvent.ACTION_DOWN || event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) touches.finishRolling()
        val handled = super.dispatchTouchEvent(event)
        if (!settings.showToolbar && suggestions != null) {
            handler.removeCallbacks(suggestionVisibility); handler.postDelayed(suggestionVisibility, 500)
        }
        return handled
    }
    fun cancelPendingTouches() { touches.cancelAll(); handler.removeCallbacksAndMessages(null); popup?.dismiss(); popup = null }
    override fun onDetachedFromWindow() { cancelPendingTouches(); super.onDetachedFromWindow() }
    private fun render() {
        cancelPendingTouches(); removeAllViews(); letters.clear(); shiftKey = null; suggestions = null; drawnShift = null
        suggestionButtons.clear(); suggestionWords = emptyList(); toolbarRow = null
        val topBar = if (settings.showToolbar) FrameLayout(context).also { strip ->
            toolbarRow = toolbar().also { strip.addView(it, FrameLayout.LayoutParams(-1, -1)) }
            addView(strip, LayoutParams(-1, dp(42)))
        } else null
        if (page == 3) { emojiPanel(); return }
        if (page == 4) { clipboardPanel(); return }
        if (page == 5) { toolsPanel(); return }
        if (profile.assistedTyping && settings.suggestions && profile.kind == EditorKind.TEXT) {
            suggestions = LinearLayout(context).apply { gravity = Gravity.CENTER; isBaselineAligned = false; visibility = GONE }
            if (topBar != null) topBar.addView(suggestions, FrameLayout.LayoutParams(-1, -1))
            else addView(suggestions, LayoutParams(-1, dp(if (landscape) 32 else 40)))
            repeat(3) {
                val button = TextView(context).apply {
                    textSize = 15f; setTextColor(ink); gravity = Gravity.CENTER
                    maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
                    setPadding(dp(6), 0, dp(6), 0); background = backgroundFor(modifier); isClickable = true
                    setOnClickListener { if (text.isNotEmpty()) { feedback(this); listener.onSuggestion(text.toString()) } }
                }
                suggestionButtons.add(button)
                suggestions!!.addView(button, LayoutParams(0, -1, 1f).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) })
            }
            if (topBar != null) suggestions!!.addView(makeKey(Key("☷", command = "tools"), toolbar = true), LayoutParams(dp(38), -1))
        }
        if (profile.kind in listOf(EditorKind.NUMBER, EditorKind.PHONE)) numberPad()
        else if (page == 0) alphabet() else symbols()
        updateShift(shiftMode)
    }
    private fun toolbar(): LinearLayout {
        val row = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL; isBaselineAligned = false }
        row.addView(makeKey(Key(if (profile.sensitive) "Private field" else if (settings.language == "ro") "Română ▾" else "English ▾", command = "switch"), toolbar = true).apply { textSize = 13f }, LayoutParams(0, dp(42), 1f))
        listOf(Key("‹", command = "left"), Key("›", command = "right"), Key("☷", command = "tools"), Key("▣", command = "clipboard"),
            Key("⚙", command = "settings"), Key("⌄", command = "hide")).forEach {
            row.addView(makeKey(it, toolbar = true), LayoutParams(dp(38), dp(42)))
        }
        return row
    }
    fun showSuggestions(words: List<String>) {
        if (suggestions == null) return
        val next = words.distinct().take(3)
        if (suggestionWords == next) return
        suggestionWords = next
        applySuggestionVisibility()
        suggestionButtons.forEachIndexed { index, button ->
            val word = next.getOrNull(index).orEmpty()
            if (button.text.toString() != word) { button.text = word; button.contentDescription = "Insert suggestion $word" }
            button.visibility = if (word.isEmpty()) INVISIBLE else VISIBLE
        }
    }
    private fun applySuggestionVisibility() {
        val row = suggestions ?: return
        val visibility = if (suggestionWords.isEmpty()) GONE else VISIBLE
        if (toolbarRow == null && row.visibility != visibility && (touches.hasTouches || SystemClock.uptimeMillis() - lastKeyTouch < 500)) {
            handler.removeCallbacks(suggestionVisibility); handler.postDelayed(suggestionVisibility, 500); return
        }
        // Suggestions replace the toolbar in the same fixed-height slot. Never
        // resize the IME for each word while fingers are already targeting keys.
        row.visibility = visibility
        toolbarRow?.visibility = if (visibility == GONE) VISIBLE else GONE
    }
    private fun alphabet() {
        if (settings.numberRow) row("1234567890".map { Key(it.toString()) }, compact = true)
        row("qwertyuiop".mapIndexed { index, c -> Key(c.toString(), alternates = listOf(if (index == 9) "0" else "${index + 1}") + accents(c)) })
        row("asdfghjkl".map { Key(it.toString(), alternates = accents(it)) }, inset = 12)
        row(listOf(Key("⇧", command = "shift", weight = 1.35f)) + "zxcvbnm".map { Key(it.toString(), alternates = accents(it)) } + Key("⌫", command = "delete", weight = 1.35f))
        val contextKey = when (profile.kind) { EditorKind.EMAIL -> "@"; EditorKind.URL -> "/"; else -> "," }
        row(listOf(Key("?123", command = "symbols", weight = 1.3f), panelKey(),
            Key(contextKey, alternates = listOf(";", ":")), Key(if (settings.language == "ro") "Română" else "English", " ", weight = 3.2f),
            Key(".", alternates = listOf("?", "!", "…")), Key(profile.actionLabel, command = "enter", weight = 1.45f)))
    }
    private fun accents(char: Char): List<String> = when (char) {
        'a' -> listOf("ă", "â", "à", "á", "ä", "æ")
        'i' -> listOf("î", "í", "ì", "ï")
        's' -> listOf("ș", "ß", "ś", "š")
        't' -> listOf("ț", "ţ")
        'e' -> listOf("é", "è", "ê", "ë", "€")
        'o' -> listOf("ó", "ò", "ô", "ö", "œ")
        'u' -> listOf("ú", "ù", "û", "ü")
        'c' -> listOf("ç", "ć", "č"); 'n' -> listOf("ñ", "ń"); else -> emptyList()
    }
    private fun symbols() {
        val rows = if (page == 1) listOf("1234567890", "@#€%&-+()", "*\"':;!?/") else listOf("~`|•√π÷×¶∆", "£¥¢^°={}[]", "_\\<>$©®™")
        fun symbol(char: Char) = Key(char.toString(), alternates = when (char) {
            '"' -> listOf("„", "“", "”", "«", "»"); '\'' -> listOf("’", "‘"); '-' -> listOf("–", "—"); else -> emptyList()
        })
        row(rows[0].map(::symbol)); row(rows[1].map(::symbol), inset = 10)
        row(listOf(Key(if (page == 1) "=\\<" else "123", command = "more", weight = 1.4f)) + rows[2].map(::symbol) + Key("⌫", command = "delete", weight = 1.4f))
        row(listOf(Key("ABC", command = "alphabet", weight = 1.3f), panelKey(), Key(","), Key("space", " ", weight = 3.2f), Key("."), Key(profile.actionLabel, command = "enter", weight = 1.45f)))
    }
    private fun numberPad() {
        val phone = profile.kind == EditorKind.PHONE
        row(listOf(Key("1"), Key("2"), Key("3"), Key("⌫", command = "delete")))
        row(listOf(Key("4"), Key("5"), Key("6"), Key(if (phone) "+" else if (profile.signed) "−" else ",", if (phone) "+" else if (profile.signed) "-" else ",")))
        row(listOf(Key("7"), Key("8"), Key("9"), Key(if (phone) "#" else "/")))
        row(listOf(if (settings.showToolbar) Key("🌐", command = "switch") else Key("⋯", command = "tools"), Key("0", alternates = if (phone) listOf("+") else emptyList()),
            Key(if (phone) "*" else ".", alternates = if (!phone) listOf(",") else emptyList()), Key(profile.actionLabel, command = "enter")))
    }
    private fun emojiPanel() {
        val categories = listOf("Recent") + EmojiCatalog.categories.keys
        val tabs = LinearLayout(context)
        categories.forEach { category ->
            tabs.addView(TextView(context).apply {
                text = category; textSize = 13f; setTextColor(if (category == emojiCategory) accent else ink)
                setPadding(dp(12), dp(10), dp(12), dp(10)); contentDescription = "$category emoji"
                isSelected = category == emojiCategory
                background = if (isSelected) backgroundFor(modifier) else null
                setOnClickListener { emojiCategory = category; listener.onEmojiCategory(category); render() }
            }, LayoutParams(-2, dp(42)))
        }
        val tabScroller = HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled = false; addView(tabs) }
        addView(tabScroller)
        tabs.getChildAt(categories.indexOf(emojiCategory))?.let { selected -> tabScroller.post { tabScroller.smoothScrollTo(selected.left, 0) } }
        val emojis = if (emojiCategory == "Recent") recent else EmojiCatalog.categories[emojiCategory].orEmpty()
        val content = LinearLayout(context).apply { orientation = VERTICAL }
        if (emojis.isEmpty()) content.addView(panelMessage("Your recently used emoji appear here.\nChoose a category above to get started."))
        val columns = if (landscape) 12 else 8
        emojis.chunked(columns).forEach { group ->
            val line = LinearLayout(context)
            group.forEach { emoji ->
                line.addView(TextView(context).apply {
                    text = emoji; textSize = 27f; gravity = Gravity.CENTER; contentDescription = EmojiCatalog.description(emoji)
                    setOnClickListener { feedback(this); listener.onEmoji(emoji); if (profile.assistedTyping) recent = (listOf(emoji) + recent).distinct().take(24) }
                    val variants = EmojiCatalog.variants(emoji)
                    if (variants.isNotEmpty()) setOnLongClickListener { showAlternates(this, variants, true); true }
                }, LayoutParams(0, dp(48), 1f))
            }
            repeat(columns - group.size) { line.addView(View(context), LayoutParams(0, dp(48), 1f)) }
            content.addView(line)
        }
        addView(ScrollView(context).apply { addView(content); isFillViewport = true }, LayoutParams(-1, dp(if (landscape) 120 else 212)))
        panelFooter()
    }
    private fun panelKey() = if (settings.showToolbar) Key("☺", command = "emoji") else Key("⋯", command = "tools")
    private fun panelMessage(message: String) = TextView(context).apply {
        text = message; textSize = 14f; setTextColor(ink); gravity = Gravity.CENTER
        setPadding(dp(16), dp(16), dp(16), dp(16))
    }
    private fun panelFooter() = row(listOf(Key("ABC", command = "alphabet", weight = 1.4f), Key("⋯", command = "tools"),
        Key("space", " ", weight = 3f), Key("⌫", command = "delete"), Key(profile.actionLabel, command = "enter", weight = 1.4f)))
    fun showClipboard(entries: List<ClipboardEntry>, enabled: Boolean) {
        if (profile.sensitive) return
        clips = entries; historyEnabled = enabled; page = 4; render()
    }
    fun refreshClipboard(entries: List<ClipboardEntry>, enabled: Boolean) {
        clips = if (profile.sensitive) emptyList() else entries; historyEnabled = enabled
        if (page == 4) render()
    }
    fun clearTransientPanels() {
        clips = emptyList()
        if (page in 4..5) { page = 0; render() }
    }
    private fun clipboardPanel() {
        val content = LinearLayout(context).apply { orientation = VERTICAL }
        content.addView(panelMessage(if (historyEnabled) "Clipboard · unpinned clips expire after 1 hour" else "Clipboard · history is off\nOnly the current copied text is shown."))
        val actions = LinearLayout(context)
        actions.addView(panelButton(if (historyEnabled) "Turn history off" else "Turn history on") { listener.onCommand("clipboardHistory") }, LayoutParams(0, dp(44), 1f))
        if (historyEnabled && clips.isNotEmpty()) actions.addView(panelButton("Clear all") { listener.onClipboardAction("clear", "") }, LayoutParams(0, dp(44), 1f))
        content.addView(actions)
        if (clips.isEmpty()) content.addView(panelMessage("No copied text yet. Copy text in an app, then open Clipboard.\nPrivate or sensitive clips are never shown."))
        clips.forEach { clip ->
            val card = LinearLayout(context).apply { orientation = VERTICAL; background = backgroundFor(keyColor) }
            card.addView(TextView(context).apply {
                text = clip.text; setTextColor(ink); textSize = 16f; maxLines = 3; ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(dp(12), dp(12), dp(12), dp(12)); minHeight = dp(48)
                contentDescription = "Paste clip: ${clip.text}"; isClickable = true
                setOnClickListener { feedback(this); listener.onClipboardPaste(clip.text) }
            }, LayoutParams(-1, -2))
            if (historyEnabled) {
                val controls = LinearLayout(context)
                controls.addView(panelButton(if (clip.pinned) "Unpin" else "Pin") { listener.onClipboardAction("pin", clip.text) }, LayoutParams(0, dp(44), 1f))
                controls.addView(panelButton("Delete clip") { listener.onClipboardAction("delete", clip.text) }, LayoutParams(0, dp(44), 1f))
                card.addView(controls)
            }
            content.addView(card, LayoutParams(-1, -2).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) })
        }
        addView(ScrollView(context).apply { addView(content) }, LayoutParams(-1, dp(if (landscape) 120 else 212)))
        panelFooter()
    }
    private fun panelButton(label: String, action: () -> Unit) = TextView(context).apply {
        text = label; setTextColor(accent); textSize = 14f; gravity = Gravity.CENTER
        contentDescription = label; background = backgroundFor(modifier); isClickable = true; isFocusable = true
        setOnClickListener { feedback(this); action() }
    }
    private fun toolsPanel() {
        addView(panelMessage("Keyboard tools"))
        row(listOf(Key("Emoji", command = "emoji"), Key("Clipboard", command = "clipboard"), Key("Settings", command = "settings")))
        row(listOf(Key("Select all", command = "selectAll"), Key("Copy", command = "copy"), Key("Cut", command = "cut")))
        row(listOf(Key("‹", command = "left"), Key("›", command = "right"), Key("Language", command = "switch"), Key(if (settings.showToolbar) "Hide toolbar" else "Show toolbar", command = "toolbar")))
        panelFooter()
    }
    private fun row(keys: List<Key>, inset: Int = 0, compact: Boolean = false) {
        val line = LinearLayout(context).apply { setPadding(dp(inset), 0, dp(inset), 0); gravity = Gravity.CENTER; isBaselineAligned = false }
        val height = if (compact) if (landscape) 34 else 40 else if (landscape) 40 else settings.keyHeight
        keys.forEach { key ->
            val view = makeKey(key)
            val target = KeyTouchTarget(context, view)
            target.addView(view, FrameLayout.LayoutParams(-1, -1).apply {
                setMargins(dp(settings.keySpacing), dp(3), dp(settings.keySpacing), dp(3))
            })
            line.addView(target, LayoutParams(0, dp(height) + dp(3) * 2, key.weight))
        }
        addView(line, LayoutParams(-1, -2))
    }
    private fun backgroundFor(color: Int, keycap: Boolean = true): RippleDrawable {
        val alpha = if (photo != null) 215 else 255
        val shape = GradientDrawable().apply {
            setColor((color and 0x00FFFFFF) or (alpha shl 24))
            cornerRadius = dp(if (keycap) settings.keyCornerRadius else 8).toFloat()
            if (keycap && settings.keyBorders) setStroke(dp(1), (ink and 0x00FFFFFF) or (100 shl 24))
        }
        return RippleDrawable(ColorStateList.valueOf(Color.argb(50, 100, 150, 230)), shape, null)
    }
    private fun feedback(view: View) {
        if (settings.haptics) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        if (settings.sound) context.getSystemService(AudioManager::class.java).playSoundEffect(AudioManager.FX_KEY_CLICK)
    }
    private fun showAlternates(anchor: View, values: List<String>, emoji: Boolean = false) {
        popup?.dismiss()
        val line = LinearLayout(context).apply { setPadding(dp(4), dp(4), dp(4), dp(4)); setBackgroundColor(modifier) }
        values.forEach { value -> line.addView(TextView(context).apply {
            text = if (!emoji && shiftMode != ShiftMode.OFF) value.uppercase() else value
            gravity = Gravity.CENTER; textSize = if (emoji) 25f else 22f; setTextColor(ink)
            contentDescription = if (emoji) EmojiCatalog.description(value) else value
            setOnClickListener { feedback(this); if (emoji) listener.onEmoji(value) else listener.onText(value); popup?.dismiss() }
        }, LayoutParams(dp(44), dp(48))) }
        val scroller = HorizontalScrollView(context).apply { addView(line) }
        // The editor must keep input focus while alternatives are open. A focusable
        // popup restarts the input session and dismisses itself on recent Android.
        popup = PopupWindow(scroller, minOf(dp(44 * values.size + 8), resources.displayMetrics.widthPixels - dp(12)), dp(56), false).apply {
            inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
            setBackgroundDrawable(ColorDrawable(modifier)); isOutsideTouchable = true; elevation = dp(8).toFloat()
            showAsDropDown(anchor, -dp(12), -anchor.height - dp(56))
        }
    }
    @SuppressLint("ClickableViewAccessibility")
    private fun makeKey(key: Key, toolbar: Boolean = false): TextView {
        val command = key.command
        val secondary = key.alternates.firstOrNull()?.takeIf {
            settings.secondarySymbols && !toolbar && key.value.length == 1
        }
        val view: TextView = KeyLabelView(context, secondary).apply { clipboardIcon = toolbar && command == "clipboard" }
        view.apply {
            text = key.label; gravity = Gravity.CENTER
            textSize = if (toolbar) 23f else if (key.label.length > 2) 13f else 22f
            setTextColor(if (command == "enter") if (dark) surface else Color.WHITE else ink)
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            background = backgroundFor(when { command == "enter" -> accent; toolbar -> surface; command != null || key.value == " " -> modifier; else -> keyColor }, keycap = !toolbar)
            isFocusable = true; isClickable = true
            contentDescription = when (command) {
                "delete" -> "Delete, hold to repeat"; "enter" -> if (profile.actionLabel == "↵") "Enter" else profile.actionLabel
                "shift" -> "Shift, double tap for caps lock"; "switch" -> "Switch English and Romanian, hold to choose another keyboard"
                "hide" -> "Hide keyboard"; "left" -> "Move cursor left, hold to select"; "right" -> "Move cursor right, hold to select"
                "selectAll" -> "Select all"; "settings" -> "Open keyboard settings"; "symbols" -> "Numbers and symbols"
                "tools" -> "Keyboard tools"; "clipboard" -> "Clipboard"; "copy" -> "Copy selection"; "cut" -> "Cut selection"
                "toolbar" -> if (settings.showToolbar) "Hide toolbar" else "Show toolbar"
                "more" -> "More symbols"; "alphabet" -> "Letters"; "emoji" -> "Emoji"
                else -> if (key.value == " ") "Space, hold to switch language" else key.label + if (key.alternates.isNotEmpty()) ", hold for alternatives" else ""
            }
        }
        if (profile.sensitive && command in listOf("clipboard", "copy", "cut")) {
            view.isEnabled = false; view.alpha = .4f
            view.contentDescription = "${key.label} unavailable in private fields"
            return view
        }
        if (command == "shift") shiftKey = view
        if (command == null && key.value.length == 1 && key.value[0].isLetter()) letters.add(view to key.value)
        view.setOnClickListener {
            feedback(view)
            when (command) {
                "symbols" -> { page = 1; render() }; "more" -> { page = if (page == 1) 2 else 1; render() }
                "alphabet" -> { page = 0; render() }; "emoji" -> { page = 3; render() }
                "tools" -> { page = 5; render() }
                null -> listener.onText(key.value); else -> listener.onCommand(command)
            }
        }
        if (key.alternates.isNotEmpty() || command in listOf("shift", "left", "right", "switch", "emoji") || key.value == " ") {
            view.setOnLongClickListener {
                feedback(view)
                when { key.alternates.isNotEmpty() -> showAlternates(view, key.alternates)
                    command == "emoji" -> { page = 5; render() }
                    key.value == " " -> listener.onCommand("switch")
                    else -> listener.onCommand(command!!, true) }
                true
            }
        }
        if (key.value == " ") {
            if (Build.VERSION.SDK_INT >= 26) view.tooltipText = "Swipe Space to move the cursor; hold to switch language"
        }
        view.setOnTouchListener(KeyTouchHandler(view, handler, touches, settings.longPressDelay.toLong(),
            rollAllowed = command == null || command in listOf("shift", "delete"),
            repeatDelete = if (command == "delete") ({ listener.onCommand("delete") }) else null,
            cursorMove = if (key.value == " ") ({ steps -> listener.onCursorMove(steps) }) else null,
            holdFeedback = { feedback(view) }))
        return view
    }
}
