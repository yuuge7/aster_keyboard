package app.aster.aster_keyboard.ime

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.TypedValue
import android.widget.TextView

/** Secondary legends are decorative; the key keeps one accessible click target. */
class KeyLabelView @JvmOverloads constructor(context: Context, private val secondary: String? = null) : TextView(context) {
    var toolbarIcon: String? = null
    var uppercaseHint = false
        set(value) { field = value; invalidate() }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val iconPath = Path()
    override fun onDraw(canvas: Canvas) {
        toolbarIcon?.let { icon ->
            val d = resources.displayMetrics.density
            val save = canvas.save()
            canvas.translate(width / 2f - 12 * d, height / 2f - 12 * d)
            canvas.scale(d, d)
            hintPaint.apply {
                color = currentTextColor; alpha = 255; style = Paint.Style.STROKE
                strokeWidth = 1.7f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
            }
            when (icon) {
                "emojiSearch" -> {
                    canvas.drawCircle(10.5f, 10.5f, 6.5f, hintPaint)
                    canvas.drawLine(15.5f, 15.5f, 21f, 21f, hintPaint)
                }
                "tools" -> for (x in listOf(4f, 14f)) for (y in listOf(4f, 14f))
                    canvas.drawRoundRect(x, y, x + 6, y + 6, 1.5f, 1.5f, hintPaint)
                "clipboard" -> {
                    iconPath.reset(); iconPath.moveTo(8f, 5f); iconPath.lineTo(5f, 5f)
                    iconPath.lineTo(5f, 21f); iconPath.lineTo(19f, 21f); iconPath.lineTo(19f, 5f); iconPath.lineTo(16f, 5f)
                    canvas.drawPath(iconPath, hintPaint)
                    canvas.drawRoundRect(8f, 3f, 16f, 7f, 1f, 1f, hintPaint)
                    canvas.drawLine(9f, 12f, 15f, 12f, hintPaint); canvas.drawLine(9f, 16f, 14f, 16f, hintPaint)
                }
                "settings" -> {
                    // The same rounded stroke weight as the other toolbar icons.
                    iconPath.reset()
                    repeat(32) { index ->
                        val angle = Math.PI * index / 16
                        val radius = if (index % 4 in 1..2) 10 else 8
                        val x = 12 + kotlin.math.cos(angle).toFloat() * radius
                        val y = 12 + kotlin.math.sin(angle).toFloat() * radius
                        if (index == 0) iconPath.moveTo(x, y) else iconPath.lineTo(x, y)
                    }
                    iconPath.close(); canvas.drawPath(iconPath, hintPaint)
                    canvas.drawCircle(12f, 12f, 3f, hintPaint)
                }
                "hide" -> {
                    iconPath.reset(); iconPath.moveTo(6f, 9f); iconPath.lineTo(12f, 15f); iconPath.lineTo(18f, 9f)
                    canvas.drawPath(iconPath, hintPaint)
                }
                "keyboardPicker" -> {
                    canvas.drawRoundRect(2f, 5f, 22f, 19f, 2f, 2f, hintPaint)
                    for (x in listOf(6f, 10f, 14f, 18f)) for (y in listOf(9f, 12f))
                        canvas.drawPoint(x, y, hintPaint)
                    canvas.drawLine(7f, 16f, 17f, 16f, hintPaint)
                }
            }
            canvas.restoreToCount(save)
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
