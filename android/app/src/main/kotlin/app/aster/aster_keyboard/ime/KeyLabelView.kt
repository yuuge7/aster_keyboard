package app.aster.aster_keyboard.ime

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.TypedValue
import android.widget.TextView

/** Secondary legends are decorative; the key keeps one accessible click target. */
class KeyLabelView @JvmOverloads constructor(context: Context, private val secondary: String? = null) : TextView(context) {
    var clipboardIcon = false
    var uppercaseHint = false
        set(value) { field = value; invalidate() }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(canvas: Canvas) {
        if (clipboardIcon) {
            val d = resources.displayMetrics.density
            val x = width / 2f; val y = height / 2f
            hintPaint.apply { color = currentTextColor; alpha = 255; style = Paint.Style.STROKE; strokeWidth = 1.7f * d }
            canvas.drawRoundRect(x - 8 * d, y - 8 * d, x + 8 * d, y + 10 * d, 2 * d, 2 * d, hintPaint)
            hintPaint.style = Paint.Style.FILL
            canvas.drawRoundRect(x - 4 * d, y - 11 * d, x + 4 * d, y - 6 * d, d, d, hintPaint)
            canvas.drawRect(x - 4 * d, y - 2 * d, x + 4 * d, y - d, hintPaint)
            canvas.drawRect(x - 4 * d, y + 3 * d, x + 3 * d, y + 4 * d, hintPaint)
            return
        }
        super.onDraw(canvas)
        val label = secondary?.takeIf { it.isNotEmpty() } ?: return
        val density = resources.displayMetrics.density
        if (width < 24 * density || height < 34 * density) return
        hintPaint.apply {
            color = currentTextColor; alpha = 170
            textSize = minOf(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 10f,
                resources.displayMetrics), height * .22f)
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText(if (uppercaseHint) label.uppercase() else label,
            width - 4 * density, 2 * density - hintPaint.fontMetrics.ascent, hintPaint)
    }
}
