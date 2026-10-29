package app.aster.aster_keyboard.ime

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs

/** Finish an earlier rolling tap before a later finger can commit its key. */
internal class KeyTouchCoordinator {
    private val active = mutableSetOf<KeyTouchHandler>()
    val hasTouches get() = active.isNotEmpty()
    fun register(press: KeyTouchHandler) { active.add(press) }
    fun remove(press: KeyTouchHandler) { active.remove(press) }
    fun finishRolling() { active.toList().filter { it.rollAllowed }.forEach { it.finish() } }
    fun cancelAll() { active.toList().forEach { it.cancel() } }
}

/** The visible gap belongs to a key's touch target instead of discarding taps. */
@SuppressLint("ViewConstructor") // Constructed with its existing accessible key, never inflated from XML.
internal class KeyTouchTarget(context: Context, private val key: View) : FrameLayout(context) {
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (!key.isEnabled) return false
        val local = MotionEvent.obtain(event)
        local.offsetLocation(-key.left.toFloat(), -key.top.toFloat())
        return try { key.dispatchTouchEvent(local) } finally { local.recycle() }
    }
}

/** Pointer IDs, drift tolerance, holds, and spacebar dragging share one gesture lifecycle. */
internal class KeyTouchHandler(
    private val view: View,
    private val handler: Handler,
    private val coordinator: KeyTouchCoordinator,
    private val holdDelay: Long,
    val rollAllowed: Boolean,
    private val repeatDelete: (() -> Unit)? = null,
    private val cursorMove: ((Int) -> Unit)? = null,
    private val holdFeedback: () -> Unit,
) : View.OnTouchListener {
    private val slop = ViewConfiguration.get(view.context).scaledTouchSlop.toFloat()
    private val cursorStep = 12 * view.resources.displayMetrics.density
    private var pointer = -1
    private var held = false
    private var dragging = false
    private var startX = 0f
    private var startY = 0f
    private var cursorSteps = 0
    private val repeat = object : Runnable {
        override fun run() {
            if (pointer < 0 || !view.isAttachedToWindow) return
            repeatDelete?.invoke(); handler.postDelayed(this, 55)
        }
    }
    private val hold = Runnable {
        if (pointer >= 0 && !dragging && view.isAttachedToWindow) {
            if (repeatDelete != null) { held = true; holdFeedback(); repeat.run() }
            else held = view.performLongClick()
            view.isPressed = false
        }
    }
    fun cancel() {
        handler.removeCallbacks(hold); handler.removeCallbacks(repeat)
        pointer = -1; view.isPressed = false; coordinator.remove(this)
    }
    fun finish() {
        val tap = pointer >= 0 && !held && !dragging
        cancel()
        if (tap && view.isAttachedToWindow) view.performClick()
    }
    @SuppressLint("ClickableViewAccessibility") // finish() routes every accepted tap through View.performClick().
    override fun onTouch(target: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                coordinator.finishRolling(); cancel()
                if (!view.isAttachedToWindow) return true
                val index = event.actionIndex
                pointer = event.getPointerId(index); held = false; dragging = false; cursorSteps = 0
                startX = event.getX(index); startY = event.getY(index)
                view.isPressed = true; view.drawableHotspotChanged(startX, startY); coordinator.register(this)
                if (repeatDelete != null || view.isLongClickable) handler.postDelayed(hold, holdDelay)
            }
            MotionEvent.ACTION_MOVE -> {
                val index = event.findPointerIndex(pointer)
                if (index < 0) return true
                val x = event.getX(index); val y = event.getY(index)
                val dx = x - startX; val dy = y - startY
                if (cursorMove != null && !held && !dragging && abs(dx) >= maxOf(slop, cursorStep) && abs(dx) > abs(dy)) {
                    dragging = true; handler.removeCallbacks(hold); holdFeedback()
                }
                if (dragging) {
                    val steps = ((dx / cursorStep).toInt() - cursorSteps).coerceIn(-8, 8)
                    if (steps != 0) { cursorSteps += steps; cursorMove?.invoke(steps) }
                } else if (x < -slop || x > view.width + slop || y < -slop || y > view.height + slop) cancel()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                if (event.getPointerId(event.actionIndex) == pointer) finish()
            }
            MotionEvent.ACTION_CANCEL -> cancel()
        }
        return true
    }
}
