package app.aster.aster_keyboard.ime

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/** Native, accessible keys; no Flutter engine is created by the IME. */
class AsterKeyboardView(context: Context, private val listener: Listener) : LinearLayout(context) {
    interface Listener {
        fun onText(text: String)
        fun onCommand(command: String, longPress: Boolean = false)
    }
    private data class Key(val label: String, val value: String = label,
        val command: String? = null, val weight: Float = 1f, val alternate: String? = null)
    private val handler = Handler(Looper.getMainLooper())
    private val letters = mutableListOf<Pair<TextView, String>>()
    private var shiftKey: TextView? = null
    private var profile = EditorProfile()
    private var shiftMode = ShiftMode.OFF
    private var page = 0 // 0: alphabet, 1: symbols, 2: extra symbols
    private val ink = Color.rgb(26, 39, 59)
    private val surface = Color.rgb(233, 239, 246)
    private val modifier = Color.rgb(213, 224, 239)
    private val blue = Color.rgb(37, 86, 157)
    private val landscape get() = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    init {
        orientation = VERTICAL
        layoutDirection = View.LAYOUT_DIRECTION_LTR
        setBackgroundColor(surface)
        setPadding(dp(4), dp(3), dp(4), dp(6))
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }
    fun configure(editor: EditorProfile, shift: ShiftMode) {
        cancelPendingTouches()
        profile = editor
        shiftMode = shift
        page = 0
        render()
    }
    fun updateShift(mode: ShiftMode) {
        shiftMode = mode
        letters.forEach { (view, value) -> view.text = if (mode == ShiftMode.OFF) value else value.uppercase() }
        shiftKey?.apply {
            text = if (mode == ShiftMode.LOCKED) "⇪" else "⇧"
            contentDescription = when (mode) { ShiftMode.OFF -> "Shift"; ShiftMode.ONCE -> "Shift on"; ShiftMode.LOCKED -> "Caps lock on" }
            background = backgroundFor(if (mode == ShiftMode.OFF) modifier else blue)
            setTextColor(if (mode == ShiftMode.OFF) ink else Color.WHITE)
        }
    }
    fun cancelPendingTouches() { handler.removeCallbacksAndMessages(null) }
    override fun onDetachedFromWindow() { cancelPendingTouches(); super.onDetachedFromWindow() }

    private fun render() {
        cancelPendingTouches()
        removeAllViews()
        letters.clear()
        shiftKey = null
        toolbar()
        if (profile.kind == EditorKind.NUMBER || profile.kind == EditorKind.PHONE) numberPad()
        else if (page == 0) alphabet() else symbols()
        updateShift(shiftMode)
    }
    private fun toolbar() {
        val row = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        val brand = TextView(context).apply {
            text = if (profile.sensitive) "ASTER  ·  Private field" else "ASTER  ·  English"
            textSize = 11f; setTextColor(blue); setTypeface(null, Typeface.BOLD)
            setPadding(dp(10), 0, 0, 0)
        }
        row.addView(brand, LayoutParams(0, dp(42), 1f))
        listOf(Key("‹", command="left"), Key("›", command="right"),
            Key("☷", command="selectAll"), Key("⚙", command="settings"), Key("⌄", command="hide")).forEach {
            row.addView(makeKey(it, toolbar=true), LayoutParams(dp(40), dp(42)))
        }
        addView(row)
    }
    private fun alphabet() {
        row("qwertyuiop".mapIndexed { index, c -> Key(c.toString(), alternate=(index + 1).let { if (it == 10) "0" else "$it" }) })
        row("asdfghjkl".map { Key(it.toString()) }, inset=14)
        row(listOf(Key("⇧", command="shift", weight=1.35f)) + "zxcvbnm".map { Key(it.toString()) } + Key("⌫", command="delete", weight=1.35f))
        val contextKey = when (profile.kind) { EditorKind.EMAIL -> "@"; EditorKind.URL -> "/"; else -> "," }
        row(listOf(Key("?123", command="symbols", weight=1.4f), Key("◎", command="switch"),
            Key(contextKey, alternate=";"), Key("space", " ", weight=3.8f),
            Key(".", alternate="?"), Key(profile.actionLabel, command="enter", weight=1.7f)))
    }
    private fun symbols() {
        val rows = if (page == 1) listOf("1234567890", "@#€%&-+()", "*\"':;!?/")
            else listOf("~`|•√π÷×¶∆", "£¥¢^°={}[]", "_\\<>$©®™")
        row(rows[0].map { Key(it.toString()) })
        row(rows[1].map { Key(it.toString()) }, inset=10)
        row(listOf(Key(if (page == 1) "=\\<" else "123", command="more", weight=1.4f)) +
            rows[2].map { Key(it.toString()) } + Key("⌫", command="delete", weight=1.4f))
        row(listOf(Key("ABC", command="alphabet", weight=1.4f), Key("◎", command="switch"),
            Key(","), Key("space", " ", weight=3.8f), Key("."), Key(profile.actionLabel, command="enter", weight=1.7f)))
    }
    private fun numberPad() {
        val phone = profile.kind == EditorKind.PHONE
        row(listOf(Key("1"), Key("2"), Key("3"), Key("⌫", command="delete")))
        row(listOf(Key("4"), Key("5"), Key("6"), Key(if (phone) "+" else if (profile.signed) "−" else ",", if (phone) "+" else if (profile.signed) "-" else ",")))
        row(listOf(Key("7"), Key("8"), Key("9"), Key(if (phone) "#" else "/")))
        row(listOf(Key("◎", command="switch"), Key("0", alternate=if (phone) "+" else null),
            Key(if (phone) "*" else "."), Key(profile.actionLabel, command="enter")))
    }
    private fun row(keys: List<Key>, inset: Int = 0) {
        val row = LinearLayout(context).apply {
            setPadding(dp(inset), 0, dp(inset), 0)
            gravity = Gravity.CENTER
            isBaselineAligned = false
        }
        keys.forEach { key ->
            row.addView(makeKey(key), LayoutParams(0, dp(if (landscape) 42 else 52), key.weight).apply {
                setMargins(dp(2), dp(3), dp(2), dp(3))
            })
        }
        addView(row, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }
    private fun backgroundFor(color: Int): RippleDrawable {
        val shape = GradientDrawable().apply { setColor(color); cornerRadius = dp(9).toFloat() }
        return RippleDrawable(ColorStateList.valueOf(Color.argb(45, 37, 86, 157)), shape, null)
    }
    @SuppressLint("ClickableViewAccessibility")
    private fun makeKey(key: Key, toolbar: Boolean = false): TextView {
        val command = key.command
        val view = TextView(context).apply {
            text = key.label
            gravity = Gravity.CENTER
            textSize = if (toolbar) 23f else if (key.label.length > 2) 14f else 22f
            setTextColor(if (command == "enter") Color.WHITE else ink)
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            background = backgroundFor(when { command == "enter" -> blue; toolbar -> surface; command != null || key.value == " " -> modifier; else -> Color.WHITE })
            isFocusable = true
            isClickable = true
            contentDescription = when(command) {
                "delete" -> "Delete, hold to repeat"; "enter" -> if (profile.actionLabel == "↵") "Enter" else profile.actionLabel
                "shift" -> "Shift, double tap for caps lock"; "switch" -> "Choose keyboard"
                "hide" -> "Hide keyboard"; "left" -> "Move cursor left, hold to select"
                "right" -> "Move cursor right, hold to select"; "selectAll" -> "Select all"
                "settings" -> "Open Aster setup"; "symbols" -> "Numbers and symbols"
                "more" -> "More symbols"; "alphabet" -> "Letters"
                else -> if (key.value == " ") "Space" else key.label + (key.alternate?.let { ", hold for $it" } ?: "")
            }
        }
        if (command == "shift") shiftKey = view
        if (command == null && key.value.length == 1 && key.value[0] in 'a'..'z') letters.add(view to key.value)
        val click = {
            when(command) {
                "symbols" -> { page = 1; render() }
                "more" -> { page = if (page == 1) 2 else 1; render() }
                "alphabet" -> { page = 0; render() }
                null -> listener.onText(key.value)
                else -> listener.onCommand(command)
            }
        }
        view.setOnClickListener { click() }
        if (command == "delete") {
            val repeat = object : Runnable {
                override fun run() { listener.onCommand("delete"); handler.postDelayed(this, 55) }
            }
            var repeated = false
            val startRepeat = Runnable { repeated = true; repeat.run() }
            view.setOnTouchListener { _, event ->
                when(event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> { repeated = false; view.isPressed = true; handler.postDelayed(startRepeat, 380); true }
                    MotionEvent.ACTION_UP -> { handler.removeCallbacks(startRepeat); handler.removeCallbacks(repeat); view.isPressed = false; if (!repeated) view.performClick(); true }
                    MotionEvent.ACTION_CANCEL -> { handler.removeCallbacks(startRepeat); handler.removeCallbacks(repeat); view.isPressed = false; true }
                    MotionEvent.ACTION_MOVE -> {
                        if (event.x < 0 || event.x > view.width || event.y < 0 || event.y > view.height) {
                            handler.removeCallbacks(startRepeat); handler.removeCallbacks(repeat); view.isPressed = false; repeated = true
                        }; true
                    }
                    else -> false
                }
            }
        } else if (key.alternate != null || command in listOf("shift", "left", "right")) {
            view.setOnLongClickListener {
                if (key.alternate != null) listener.onText(key.alternate) else listener.onCommand(command!!, true)
                true
            }
        }
        return view
    }
}
