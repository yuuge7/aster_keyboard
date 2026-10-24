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
    }
    private data class Key(val label: String, val value: String = label, val command: String? = null,
        val weight: Float = 1f, val alternates: List<String> = emptyList())
    private val handler = Handler(Looper.getMainLooper())
    private val letters = mutableListOf<Pair<TextView, String>>()
    private var shiftKey: TextView? = null
    private var suggestions: LinearLayout? = null
    private var popup: PopupWindow? = null
    private var profile = EditorProfile()
    private var settings = KeyboardSettings()
    private var shiftMode = ShiftMode.OFF
    private var page = 0
    private var emojiCategory = "Faces"
    private var recent = emptyList<String>()
    private var photo: Bitmap? = null
    private var photoStamp = ""
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var dark = false
    private var ink = 0; private var surface = 0; private var modifier = 0; private var accent = 0; private var keyColor = 0
    private val landscape get() = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    init {
        orientation = VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_LTR; setWillNotDraw(false)
        setPadding(dp(4), dp(3), dp(4), dp(6))
        setOnApplyWindowInsetsListener { _, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(WindowInsets.Type.navigationBars() or WindowInsets.Type.displayCutout())
                setPadding(dp(4) + bars.left, dp(3), dp(4) + bars.right, dp(6) + bars.bottom)
            } else {
                @Suppress("DEPRECATION")
                setPadding(dp(4) + insets.systemWindowInsetLeft, dp(3), dp(4) + insets.systemWindowInsetRight, dp(6) + insets.systemWindowInsetBottom)
            }
            insets
        }
    }
    fun configure(editor: EditorProfile, shift: ShiftMode, preferences: KeyboardSettings, wallpaper: File?, recents: List<String>) {
        cancelPendingTouches(); profile = editor; shiftMode = shift; settings = preferences; recent = recents; page = 0
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
            canvas.drawBitmap(bitmap, null, RectF((width - w) / 2, (height - h) / 2, (width + w) / 2, (height + h) / 2), paint)
            canvas.drawColor(Color.argb((settings.wallpaperDim * 255).toInt(), 0, 0, 0))
        }
    }
    fun updateShift(mode: ShiftMode) {
        shiftMode = mode
        letters.forEach { (view, value) -> view.text = if (mode == ShiftMode.OFF) value else value.uppercase() }
        shiftKey?.apply {
            text = if (mode == ShiftMode.LOCKED) "⇪" else "⇧"
            contentDescription = when (mode) { ShiftMode.OFF -> "Shift"; ShiftMode.ONCE -> "Shift on"; ShiftMode.LOCKED -> "Caps lock on" }
            background = backgroundFor(if (mode == ShiftMode.OFF) modifier else accent)
            setTextColor(if (mode == ShiftMode.OFF) ink else if (dark) surface else Color.WHITE)
        }
    }
    fun cancelPendingTouches() { handler.removeCallbacksAndMessages(null); popup?.dismiss(); popup = null }
    override fun onDetachedFromWindow() { cancelPendingTouches(); super.onDetachedFromWindow() }
    private fun render() {
        cancelPendingTouches(); removeAllViews(); letters.clear(); shiftKey = null; suggestions = null
        toolbar()
        if (page == 3) { emojiPanel(); return }
        if (profile.assistedTyping && settings.suggestions && profile.kind == EditorKind.TEXT) {
            suggestions = LinearLayout(context).apply { gravity = Gravity.CENTER; isBaselineAligned = false }
            addView(suggestions, LayoutParams(-1, dp(if (landscape) 32 else 40)))
        }
        if (profile.kind in listOf(EditorKind.NUMBER, EditorKind.PHONE)) numberPad()
        else if (page == 0) alphabet() else symbols()
        updateShift(shiftMode)
    }
    private fun toolbar() {
        val row = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL; isBaselineAligned = false }
        row.addView(makeKey(Key(if (profile.sensitive) "Private field" else if (settings.language == "ro") "Română ▾" else "English ▾", command = "switch"), toolbar = true).apply { textSize = 13f }, LayoutParams(0, dp(42), 1f))
        listOf(Key("‹", command = "left"), Key("›", command = "right"), Key("☷", command = "selectAll"),
            Key("⚙", command = "settings"), Key("⌄", command = "hide")).forEach {
            row.addView(makeKey(it, toolbar = true), LayoutParams(dp(38), dp(42)))
        }
        addView(row)
    }
    fun showSuggestions(words: List<String>) {
        val row = suggestions ?: return
        row.removeAllViews()
        words.distinct().take(3).forEach { word ->
            val view = TextView(context).apply {
                text = word; textSize = 15f; setTextColor(ink); gravity = Gravity.CENTER
                maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(dp(6), 0, dp(6), 0); background = backgroundFor(modifier)
                contentDescription = "Insert suggestion $word"; isClickable = true
                setOnClickListener { feedback(this); listener.onSuggestion(word) }
            }
            row.addView(view, LayoutParams(0, -1, 1f).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) })
        }
    }
    private fun alphabet() {
        if (settings.numberRow) row("1234567890".map { Key(it.toString()) }, compact = true)
        if (settings.language == "ro") row("ăâîșț".map { Key(it.toString()) }, compact = true)
        row("qwertyuiop".mapIndexed { index, c -> Key(c.toString(), alternates = listOf(if (index == 9) "0" else "${index + 1}") + accents(c)) })
        row("asdfghjkl".map { Key(it.toString(), alternates = accents(it)) }, inset = 12)
        row(listOf(Key("⇧", command = "shift", weight = 1.35f)) + "zxcvbnm".map { Key(it.toString(), alternates = accents(it)) } + Key("⌫", command = "delete", weight = 1.35f))
        val contextKey = when (profile.kind) { EditorKind.EMAIL -> "@"; EditorKind.URL -> "/"; else -> "," }
        row(listOf(Key("?123", command = "symbols", weight = 1.3f), Key("☺", command = "emoji"),
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
        row(listOf(Key("ABC", command = "alphabet", weight = 1.3f), Key("☺", command = "emoji"), Key(","), Key("space", " ", weight = 3.2f), Key("."), Key(profile.actionLabel, command = "enter", weight = 1.45f)))
    }
    private fun numberPad() {
        val phone = profile.kind == EditorKind.PHONE
        row(listOf(Key("1"), Key("2"), Key("3"), Key("⌫", command = "delete")))
        row(listOf(Key("4"), Key("5"), Key("6"), Key(if (phone) "+" else if (profile.signed) "−" else ",", if (phone) "+" else if (profile.signed) "-" else ",")))
        row(listOf(Key("7"), Key("8"), Key("9"), Key(if (phone) "#" else "/")))
        row(listOf(Key("🌐", command = "switch"), Key("0", alternates = if (phone) listOf("+") else emptyList()),
            Key(if (phone) "*" else if (settings.language == "ro") "," else ".", alternates = if (!phone) listOf(".", ",") else emptyList()), Key(profile.actionLabel, command = "enter")))
    }
    private fun emojiPanel() {
        val categories = listOf("Recent") + EmojiCatalog.categories.keys
        val tabs = LinearLayout(context)
        categories.forEach { category ->
            tabs.addView(TextView(context).apply {
                text = category; textSize = 13f; setTextColor(if (category == emojiCategory) accent else ink)
                setPadding(dp(12), dp(10), dp(12), dp(10)); contentDescription = "$category emoji"
                setOnClickListener { emojiCategory = category; render() }
            }, LayoutParams(-2, dp(42)))
        }
        addView(HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled = false; addView(tabs) })
        val emojis = if (emojiCategory == "Recent") recent else EmojiCatalog.categories[emojiCategory].orEmpty()
        val content = LinearLayout(context).apply { orientation = VERTICAL }
        if (emojis.isEmpty()) content.addView(TextView(context).apply { text = "Your recently used emoji appear here."; setTextColor(ink); gravity = Gravity.CENTER; setPadding(dp(16), dp(32), dp(16), dp(32)) })
        val columns = if (landscape) 12 else 8
        emojis.chunked(columns).forEach { group ->
            val line = LinearLayout(context)
            group.forEach { emoji ->
                line.addView(TextView(context).apply {
                    text = emoji; textSize = 27f; gravity = Gravity.CENTER; contentDescription = EmojiCatalog.description(emoji)
                    setOnClickListener { feedback(this); listener.onEmoji(emoji); recent = (listOf(emoji) + recent).distinct().take(24) }
                    val variants = EmojiCatalog.variants(emoji)
                    if (variants.isNotEmpty()) setOnLongClickListener { showAlternates(this, variants, true); true }
                }, LayoutParams(0, dp(48), 1f))
            }
            repeat(columns - group.size) { line.addView(View(context), LayoutParams(0, dp(48), 1f)) }
            content.addView(line)
        }
        addView(ScrollView(context).apply { addView(content); isFillViewport = true }, LayoutParams(-1, dp(if (landscape) 120 else 212)))
        row(listOf(Key("ABC", command = "alphabet", weight = 1.4f), Key("🌐", command = "switch"), Key("space", " ", weight = 3f), Key("⌫", command = "delete"), Key(profile.actionLabel, command = "enter", weight = 1.4f)))
    }
    private fun row(keys: List<Key>, inset: Int = 0, compact: Boolean = false) {
        val line = LinearLayout(context).apply { setPadding(dp(inset), 0, dp(inset), 0); gravity = Gravity.CENTER; isBaselineAligned = false }
        val height = if (compact) if (landscape) 34 else 40 else if (landscape) 40 else settings.keyHeight
        keys.forEach { line.addView(makeKey(it), LayoutParams(0, dp(height), it.weight).apply { setMargins(dp(2), dp(3), dp(2), dp(3)) }) }
        addView(line, LayoutParams(-1, -2))
    }
    private fun backgroundFor(color: Int): RippleDrawable {
        val alpha = if (photo != null) 215 else 255
        val shape = GradientDrawable().apply { setColor((color and 0x00FFFFFF) or (alpha shl 24)); cornerRadius = dp(8).toFloat() }
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
        popup = PopupWindow(scroller, minOf(dp(44 * values.size + 8), resources.displayMetrics.widthPixels - dp(12)), dp(56), true).apply {
            setBackgroundDrawable(ColorDrawable(modifier)); isOutsideTouchable = true; elevation = dp(8).toFloat()
            showAsDropDown(anchor, -dp(12), -anchor.height - dp(56))
        }
    }
    @SuppressLint("ClickableViewAccessibility")
    private fun makeKey(key: Key, toolbar: Boolean = false): TextView {
        val command = key.command
        val view = TextView(context).apply {
            text = key.label; gravity = Gravity.CENTER
            textSize = if (toolbar) 23f else if (key.label.length > 2) 13f else 22f
            setTextColor(if (command == "enter") if (dark) surface else Color.WHITE else ink)
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            background = backgroundFor(when { command == "enter" -> accent; toolbar -> surface; command != null || key.value == " " -> modifier; else -> keyColor })
            isFocusable = true; isClickable = true
            contentDescription = when (command) {
                "delete" -> "Delete, hold to repeat"; "enter" -> if (profile.actionLabel == "↵") "Enter" else profile.actionLabel
                "shift" -> "Shift, double tap for caps lock"; "switch" -> "Switch English and Romanian, hold to choose another keyboard"
                "hide" -> "Hide keyboard"; "left" -> "Move cursor left, hold to select"; "right" -> "Move cursor right, hold to select"
                "selectAll" -> "Select all"; "settings" -> "Open keyboard settings"; "symbols" -> "Numbers and symbols"
                "more" -> "More symbols"; "alphabet" -> "Letters"; "emoji" -> "Emoji"
                else -> if (key.value == " ") "Space, hold to switch language" else key.label + if (key.alternates.isNotEmpty()) ", hold for alternatives" else ""
            }
        }
        if (command == "shift") shiftKey = view
        if (command == null && key.value.length == 1 && key.value[0].isLetter()) letters.add(view to key.value)
        view.setOnClickListener {
            feedback(view)
            when (command) {
                "symbols" -> { page = 1; render() }; "more" -> { page = if (page == 1) 2 else 1; render() }
                "alphabet" -> { page = 0; render() }; "emoji" -> { page = 3; render() }
                null -> listener.onText(key.value); else -> listener.onCommand(command)
            }
        }
        if (command == "delete") {
            val repeat = object : Runnable { override fun run() { listener.onCommand("delete"); handler.postDelayed(this, 55) } }
            var repeated = false
            val start = Runnable { repeated = true; feedback(view); repeat.run() }
            view.setOnTouchListener { _, event -> when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { repeated = false; view.isPressed = true; handler.postDelayed(start, 380); true }
                MotionEvent.ACTION_UP -> { handler.removeCallbacks(start); handler.removeCallbacks(repeat); view.isPressed = false; if (!repeated) view.performClick(); true }
                MotionEvent.ACTION_CANCEL -> { handler.removeCallbacks(start); handler.removeCallbacks(repeat); view.isPressed = false; true }
                MotionEvent.ACTION_MOVE -> { if (event.x !in 0f..view.width.toFloat() || event.y !in 0f..view.height.toFloat()) { handler.removeCallbacks(start); handler.removeCallbacks(repeat); view.isPressed = false; repeated = true }; true }
                else -> false
            } }
        } else if (key.alternates.isNotEmpty() || command in listOf("shift", "left", "right", "switch") || key.value == " ") {
            view.setOnLongClickListener {
                feedback(view)
                when { key.alternates.isNotEmpty() -> showAlternates(view, key.alternates)
                    key.value == " " -> listener.onCommand("switch")
                    else -> listener.onCommand(command!!, true) }
                true
            }
        }
        return view
    }
}
