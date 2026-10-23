package com.ngocdevv.nativecook.blocks.gestures.tiltswipeactions

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import kotlin.math.PI
import kotlin.math.cos

/**
 * use-swipe-demo.ts. Autoplay: a scripted finger drives the named row exactly as a real pan
 * does. Any real touch stops it; it starts again from rest RESUME_MS after the last one.
 */
internal class TiltDemoScript(
    private val screen: TiltScreen,
    private val autoPlay: Boolean,
) {
    /** Loop time in seconds. */
    private var t = 0f

    var paused = !autoPlay
        private set
    private var touching = false
    private var quiet = 0f
    private var tidy = -1f
    private var down = ""
    private var lastTx = 0f

    val ghostX = MotionValue(0f, screen.clock)
    val ghostY = MotionValue(0f, screen.clock)
    val ghostO = MotionValue(0f, screen.clock)
    val ghostPress = MotionValue(0f, screen.clock)

    /** The ambient clock: the loop clock while the demo runs, so the loop stays seamless. */
    var ambient by mutableFloatStateOf(0f)
        private set

    // Several writes can land in one frame and a row only sees the last one (as with the
    // reference's reaction), so the script keeps the latest and hands it over once per frame.
    private var pendingKey: String? = null
    private var pendingDown = false
    private var pendingTx = 0f
    private var pendingVx = 0f

    private fun send(key: String, down: Boolean, tx: Float, vx: Float) {
        pendingKey = key
        pendingDown = down
        pendingTx = tx
        pendingVx = vx
    }

    private fun flush() {
        val key = pendingKey ?: return
        pendingKey = null
        screen.rowState(key)?.scriptTouch(pendingDown, pendingTx, pendingVx)
    }

    private fun show(on: Boolean) {
        ghostO.timing(if (on) 1f else 0f, (TiltDemo.fingerFade * 1000f).toInt())
    }

    /** One drag on a row: begin at [downAt], follow [path] until [upAt], release with the path's velocity. */
    private fun drag(
        t0: Float,
        t1: Float,
        dt: Float,
        row: Int,
        downAt: Float,
        upAt: Float,
        x0: Float,
        path: (Float) -> Float,
    ) {
        val key = SPENDING[row].key
        val y = TiltFlat.rowsTop + TiltLayout.row * (row + 0.5f)
        if (t0 < downAt && t1 >= downAt) {
            down = key
            lastTx = 0f
            show(true)
            send(key, true, 0f, 0f)
        }
        if (down != key) return
        if (t1 >= downAt && t1 < upAt) {
            val tx = path(t1)
            send(key, true, tx, (tx - lastTx) / dt)
            lastTx = tx
            ghostX.set(x0 + tx)
            ghostY.set(y)
        }
        if (t0 < upAt && t1 >= upAt) {
            val tx = path(upAt)
            send(key, false, tx, (tx - lastTx) / dt)
            down = ""
            show(false)
        }
    }

    /** One frame; [dt] is the frame-clock delta in seconds, clamped to 0.1. */
    fun tick(dt: Float) {
        if (autoPlay) play(dt)
        ambient = if (autoPlay && !paused) t else ambient + dt
    }

    private fun play(dt: Float) {
        if (paused) {
            if (!touching) quiet += dt
            if (tidy < 0f && quiet >= RESUME_MS / 1000f) {
                tidy = 0f
                screen.setActiveKey("")
                screen.onReset()
            }
            if (tidy >= 0f) {
                tidy += dt
                if (tidy >= TiltDemo.tidy) {
                    tidy = -1f
                    t = 0f
                    paused = false
                }
            }
            return
        }
        val t0 = t
        val t1 = t0 + dt
        val o = TiltDemo.Open
        drag(t0, t1, dt, o.row, o.down, o.up, o.x) { time -> o.to * ease((time - o.down) / (o.moveEnd - o.down)) }
        val r = TiltDemo.Remove
        drag(t0, t1, dt, r.row, r.down, r.up, r.x) { time -> r.to * easeIn((time - r.down) / (r.moveEnd - r.down)) }
        val l = TiltDemo.Lean
        drag(t0, t1, dt, l.row, l.down, l.up, l.x) { time ->
            if (time < l.turn) {
                l.to * ease((time - l.down) / (l.turn - l.down))
            } else {
                l.to * (1f - ease((time - l.turn) / (l.moveEnd - l.turn)))
            }
        }
        val u = TiltDemo.Undo
        if (t0 < u.down && t1 >= u.down) {
            ghostX.set(UNDO_X)
            ghostY.set(UNDO_Y)
            show(true)
            ghostPress.timing(1f, ((u.up - u.down) * 1000f).toInt())
        }
        if (t0 < u.up && t1 >= u.up) {
            ghostPress.timing(0f, 120)
            show(false)
            screen.onUndo()
        }
        flush()
        t = if (t1 >= LOOP) t1 - LOOP else t1
    }

    /** A real touch: cancel the scripted one and pause. */
    fun touchStart() {
        touching = true
        quiet = 0f
        tidy = -1f
        if (paused) return
        paused = true
        show(false)
        if (down.isNotEmpty()) {
            screen.rowState(down)?.scriptTouch(false, 0f, 0f)
            down = ""
        }
        pendingKey = null
    }

    fun touchEnd() {
        touching = false
        quiet = 0f
    }

    private companion object {
        /** The Undo button in the flat app's coordinates (TiltSwipeActions.tsx undoPoint). */
        const val UNDO_X = FLAT_WIDTH - TiltLayout.gutter - TiltLayout.fab - 12f - 50f
        const val UNDO_Y = FLAT_HEIGHT - PILL_BOTTOM - TiltLayout.pillHeight / 2f

        fun ease(u: Float): Float {
            val k = u.coerceIn(0f, 1f)
            return (1f - cos(PI.toFloat() * k)) / 2f
        }

        fun easeIn(u: Float): Float {
            val k = u.coerceIn(0f, 1f)
            return k * k * k
        }
    }
}
