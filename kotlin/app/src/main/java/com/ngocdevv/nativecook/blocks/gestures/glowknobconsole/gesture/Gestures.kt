// The block is built for Android 12 (API 31) and newer; the demo checks before showing it.
@file:androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.S)

package com.ngocdevv.nativecook.blocks.gestures.glowknobconsole.gesture

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.VelocityTracker
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.max

/**
 * A port of the parts of react-native-gesture-handler 2.x (Android core:
 * GestureHandler, PanGestureHandler, TapGestureHandler, the orchestrator's
 * Race) that the console uses. Handlers consume raw MotionEvents like RNGH
 * does, with thresholds converted from dp to px exactly as RNGH's
 * `PixelUtil.toPixelFromDIP`, and report events in dp to the callbacks.
 */
class GestureEvent(
    val x: Float,
    val y: Float,
    val translationX: Float,
    val translationY: Float,
    val velocityX: Float,
    val velocityY: Float,
    val numberOfPointers: Int,
)

abstract class GestureHandler {
    enum class State { UNDETERMINED, BEGAN, ACTIVE, END, FAILED, CANCELLED }

    var enabled = true
    var state = State.UNDETERMINED
        private set
    internal var density = 1f
    internal var touchSlop = 8f
    internal var width = 0f
    internal var height = 0f
    internal var orchestrator: GestureDetectorState? = null
    protected var shouldCancelWhenOutside = false
    protected var lastEvent: GestureEvent = GestureEvent(0f, 0f, 0f, 0f, 0f, 0f, 0)

    var onBegin: ((GestureEvent) -> Unit)? = null
    var onStart: ((GestureEvent) -> Unit)? = null
    var onUpdate: ((GestureEvent) -> Unit)? = null
    var onEnd: ((GestureEvent, Boolean) -> Unit)? = null
    var onFinalize: ((GestureEvent, Boolean) -> Unit)? = null
    /** onTouchesDown(event, manager): numberOfTouches + a fail() hook. */
    var onTouchesDown: ((Int, () -> Unit) -> Unit)? = null

    val isActive get() = state == State.ACTIVE
    val isFinished get() = state == State.END || state == State.FAILED || state == State.CANCELLED

    fun handle(event: MotionEvent) {
        if (!enabled || isFinished) return
        val x = event.getX(event.actionIndex.coerceAtMost(event.pointerCount - 1))
        val y = event.getY(event.actionIndex.coerceAtMost(event.pointerCount - 1))
        val within = x in 0f..width && y in 0f..height
        if (shouldCancelWhenOutside && !within) {
            if (state == State.ACTIVE) cancel() else if (state == State.BEGAN) fail()
            return
        }
        val action = event.actionMasked
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            val touches = event.pointerCount
            onHandle(event)
            onTouchesDown?.invoke(touches) { fail() }
        } else {
            onHandle(event)
        }
    }

    protected abstract fun onHandle(event: MotionEvent)
    protected open fun onCancel() {}
    protected open fun onReset() {}
    open fun resetProgress() {}

    protected fun dp(px: Float) = px / density
    protected fun px(dp: Float) = dp * density

    private fun moveToState(next: State) {
        if (state == next) return
        val previous = state
        state = next
        orchestrator?.onStateChange(this, next, previous)
        when (next) {
            State.BEGAN -> onBegin?.invoke(lastEvent)
            State.ACTIVE -> onStart?.invoke(lastEvent)
            State.END, State.FAILED, State.CANCELLED -> {
                if (previous == State.ACTIVE) onEnd?.invoke(lastEvent, next == State.END)
                if (previous != State.UNDETERMINED) onFinalize?.invoke(lastEvent, next == State.END)
            }
            else -> Unit
        }
    }

    internal fun dispatchUpdate() {
        if (state == State.ACTIVE) onUpdate?.invoke(lastEvent)
    }

    fun begin() { if (state == State.UNDETERMINED) moveToState(State.BEGAN) }
    open fun activate() { if (state == State.UNDETERMINED || state == State.BEGAN) moveToState(State.ACTIVE) }
    fun end() { if (state == State.BEGAN || state == State.ACTIVE) moveToState(State.END) }
    fun fail() {
        if (state == State.ACTIVE || state == State.UNDETERMINED || state == State.BEGAN) {
            onCancel()
            moveToState(State.FAILED)
        }
    }
    fun cancel() {
        if (state == State.ACTIVE || state == State.UNDETERMINED || state == State.BEGAN) {
            onCancel()
            moveToState(State.CANCELLED)
        }
    }

    internal fun reset() {
        onReset()
        state = State.UNDETERMINED
    }
}

/** PanGestureHandler: activeOffsetX, failOffsetY, maxPointers, activateAfterLongPress. */
class PanGesture : GestureHandler() {
    var activeOffsetX: Pair<Float, Float>? = null
    var failOffsetY: Pair<Float, Float>? = null
    var maxPointers = 10
    var activateAfterLongPress = 0L

    private var startX = 0f
    private var startY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var offsetX = 0f
    private var offsetY = 0f
    private var velocityX = 0f
    private var velocityY = 0f
    private var tracker: VelocityTracker? = null
    private val handler = Handler(Looper.getMainLooper())
    private val activateDelayed = Runnable { activate() }

    // RNGH: custom activation criteria replace the default minDist (touch slop).
    private val minDist get() = if (activeOffsetX != null || failOffsetY != null) Float.MAX_VALUE else touchSlop

    private fun shouldActivate(): Boolean {
        val dx = lastX - startX + offsetX
        val dy = lastY - startY + offsetY
        activeOffsetX?.let { (s, e) ->
            if (dx < px(s)) return true
            if (dx > px(e)) return true
        }
        val distSq = dx * dx + dy * dy
        return minDist != Float.MAX_VALUE && distSq >= minDist * minDist
    }

    private fun shouldFail(): Boolean {
        val dx = lastX - startX + offsetX
        val dy = lastY - startY + offsetY
        if (activateAfterLongPress > 0 && dx * dx + dy * dy > touchSlop * touchSlop) {
            handler.removeCallbacksAndMessages(null)
            return true
        }
        failOffsetY?.let { (s, e) ->
            if (dy < px(s)) return true
            if (dy > px(e)) return true
        }
        return false
    }

    private fun lastPointerX(e: MotionEvent): Float {
        val skip = if (e.actionMasked == MotionEvent.ACTION_POINTER_UP) e.actionIndex else -1
        // RNGH (averageTouches = false): the last pointer that is not lifting.
        for (i in e.pointerCount - 1 downTo 0) if (i != skip) return e.getX(i)
        return e.x
    }

    private fun lastPointerY(e: MotionEvent): Float {
        val skip = if (e.actionMasked == MotionEvent.ACTION_POINTER_UP) e.actionIndex else -1
        for (i in e.pointerCount - 1 downTo 0) if (i != skip) return e.getY(i)
        return e.y
    }

    private fun addVelocity(e: MotionEvent) {
        val ox = e.rawX - e.x
        val oy = e.rawY - e.y
        e.offsetLocation(ox, oy)
        tracker?.addMovement(e)
        e.offsetLocation(-ox, -oy)
    }

    private fun snapshot(e: MotionEvent) {
        lastEvent = GestureEvent(
            x = dp(e.x), y = dp(e.y),
            translationX = dp(lastX - startX + offsetX),
            translationY = dp(lastY - startY + offsetY),
            velocityX = dp(velocityX), velocityY = dp(velocityY),
            numberOfPointers = e.pointerCount,
        )
    }

    override fun onHandle(event: MotionEvent) {
        val action = event.actionMasked
        if (action == MotionEvent.ACTION_POINTER_UP || action == MotionEvent.ACTION_POINTER_DOWN) {
            offsetX += lastX - startX
            offsetY += lastY - startY
            lastX = lastPointerX(event); lastY = lastPointerY(event)
            startX = lastX; startY = lastY
        } else {
            lastX = lastPointerX(event); lastY = lastPointerY(event)
        }
        if (state == State.UNDETERMINED) {
            startX = lastX; startY = lastY
            offsetX = 0f; offsetY = 0f
            velocityX = 0f; velocityY = 0f
            tracker = VelocityTracker.obtain()
            addVelocity(event)
            snapshot(event)
            begin()
            if (activateAfterLongPress > 0) handler.postDelayed(activateDelayed, activateAfterLongPress)
        } else tracker?.let {
            addVelocity(event)
            it.computeCurrentVelocity(1000)
            velocityX = it.xVelocity
            velocityY = it.yVelocity
        }
        snapshot(event)
        when {
            action == MotionEvent.ACTION_UP -> if (state == State.ACTIVE) end() else fail()
            action == MotionEvent.ACTION_POINTER_DOWN && event.pointerCount > maxPointers ->
                if (state == State.ACTIVE) cancel() else fail()
            state == State.BEGAN -> if (shouldFail()) fail() else if (shouldActivate()) activate()
        }
    }

    override fun activate() {
        // Reset the starting point if the handler has not yet activated.
        if (state != State.ACTIVE) resetProgress()
        snapshotTranslationOnly()
        super.activate()
    }

    private fun snapshotTranslationOnly() {
        lastEvent = GestureEvent(
            lastEvent.x, lastEvent.y,
            dp(lastX - startX + offsetX), dp(lastY - startY + offsetY),
            lastEvent.velocityX, lastEvent.velocityY, lastEvent.numberOfPointers,
        )
    }

    override fun resetProgress() {
        startX = lastX
        startY = lastY
    }

    override fun onCancel() {
        handler.removeCallbacksAndMessages(null)
    }

    override fun onReset() {
        handler.removeCallbacksAndMessages(null)
        tracker?.recycle()
        tracker = null
    }
}

/** TapGestureHandler (1 tap): maxDist, maxDuration 500, cancels when outside. */
class TapGesture : GestureHandler() {
    var maxDist: Float? = null
    private val maxDurationMs = 500L
    private var startX = 0f
    private var startY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private val handler = Handler(Looper.getMainLooper())
    private val failDelayed = Runnable { fail() }

    init {
        shouldCancelWhenOutside = true
    }

    private fun shouldFail(): Boolean {
        val dx = lastX - startX
        val dy = lastY - startY
        val m = maxDist ?: return false
        return dx * dx + dy * dy > px(m) * px(m)
    }

    override fun onHandle(event: MotionEvent) {
        val action = event.actionMasked
        if (state == State.UNDETERMINED) {
            startX = event.x; startY = event.y
        }
        lastX = event.x; lastY = event.y
        lastEvent = GestureEvent(dp(event.x), dp(event.y), 0f, 0f, 0f, 0f, event.pointerCount)
        if (shouldFail()) {
            fail()
        } else if (state == State.UNDETERMINED) {
            if (action == MotionEvent.ACTION_DOWN) begin()
            handler.removeCallbacksAndMessages(null)
            handler.postDelayed(failDelayed, maxDurationMs)
        } else if (state == State.BEGAN) {
            if (action == MotionEvent.ACTION_UP) {
                handler.removeCallbacksAndMessages(null)
                activate()
            }
        }
    }

    override fun activate() {
        super.activate()
        end()
    }

    override fun onCancel() = handler.removeCallbacksAndMessages(null)
    override fun onReset() = handler.removeCallbacksAndMessages(null)
}

/** One GestureDetector: its handlers race (the first to activate cancels the rest). */
class GestureDetectorState(val handlers: List<GestureHandler>) {
    init {
        handlers.forEach { it.orchestrator = this }
    }

    internal fun onStateChange(handler: GestureHandler, next: GestureHandler.State, previous: GestureHandler.State) {
        if (next == GestureHandler.State.ACTIVE) {
            for (other in handlers) if (other !== handler && !other.isFinished) other.cancel()
        }
    }

    fun onTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        if (action == MotionEvent.ACTION_DOWN) handlers.forEach { it.reset() }
        if (action == MotionEvent.ACTION_CANCEL) {
            handlers.forEach { it.cancel() }
        } else {
            for (h in handlers) {
                h.handle(event)
                h.dispatchUpdate()
            }
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            // Anything still pending (e.g. a tap waiting) ends with the stream.
            handlers.forEach { if (!it.isFinished) it.cancel() }
        }
        return true
    }
}

/** GestureDetector: attaches the handlers to this composable's bounds. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun Modifier.gestureDetector(vararg handlers: GestureHandler): Modifier {
    val density = LocalDensity.current.density
    val slop = android.view.ViewConfiguration.get(LocalContext.current).scaledTouchSlop.toFloat()
    val detector = remember(*handlers) { GestureDetectorState(handlers.toList()) }
    handlers.forEach { it.density = density; it.touchSlop = slop }
    return this
        .onSizeChanged { size -> handlers.forEach { it.width = size.width.toFloat(); it.height = size.height.toFloat() } }
        .pointerInteropFilter { detector.onTouchEvent(it) }
}

/**
 * RN `Pressable` (Pressability) for the selector buttons: press-in on touch
 * down, press-out on release or when the finger leaves the retention area
 * (hitSlop + pressRetentionOffset 20/20/20/30), held for at least 130 ms
 * (DEFAULT_MIN_PRESS_DURATION); onPress on release inside.
 */
class Pressability(
    private val onPressIn: () -> Unit,
    private val onPressOut: () -> Unit,
    private val onPress: () -> Unit,
) {
    var enabled = true
    internal var density = 1f
    internal var width = 0f
    internal var height = 0f
    /** Extra touch area around the view, dp (the hit area itself is padded by it). */
    var hitSlop = 0f
    private var pressed = false
    private var activatedAt = 0L
    private val handler = Handler(Looper.getMainLooper())

    private fun retained(x: Float, y: Float): Boolean {
        val l = -20f * density
        val t = -20f * density
        val r = width + 20f * density
        val b = height + 30f * density
        return x in l..r && y in t..b
    }

    private fun deactivate() {
        val held = SystemClock.uptimeMillis() - activatedAt
        val delay = max(130L - held, 0L)
        if (delay > 0) handler.postDelayed({ onPressOut() }, delay) else onPressOut()
    }

    fun onTouchEvent(e: MotionEvent): Boolean {
        if (!enabled) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressed = true
                activatedAt = SystemClock.uptimeMillis()
                onPressIn()
            }
            MotionEvent.ACTION_MOVE -> if (pressed && !retained(e.x, e.y)) {
                pressed = false
                deactivate()
            }
            MotionEvent.ACTION_UP -> if (pressed) {
                pressed = false
                deactivate()
                onPress()
            }
            MotionEvent.ACTION_CANCEL -> if (pressed) {
                pressed = false
                deactivate()
            }
        }
        return true
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun Modifier.pressable(p: Pressability): Modifier {
    p.density = LocalDensity.current.density
    return this
        .onSizeChanged { p.width = it.width.toFloat(); p.height = it.height.toFloat() }
        .pointerInteropFilter { p.onTouchEvent(it) }
}
