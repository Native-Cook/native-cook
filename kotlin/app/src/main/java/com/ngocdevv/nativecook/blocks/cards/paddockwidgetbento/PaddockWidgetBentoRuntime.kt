package com.ngocdevv.nativecook.blocks.cards.paddockwidgetbento

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull

/** Event state is separate from the frame state read by placement and graphics layers. */
internal class PaddockWidgetBentoRuntime(autoPlay: Boolean, quiet: Boolean) {
    var controller = PaddockWidgetBentoController()
        private set
    val frame = mutableStateOf(PaddockWidgetBentoMotion.restFrame(PaddockWidgetBentoMotion.defaultOrder))
    var order by mutableStateOf(PaddockWidgetBentoMotion.defaultOrder)
        private set
    var isEditing by mutableStateOf(false)
        private set
    var foreground = true
        private set
    var activeOwner: Long? = null
        private set
    private var activeCard: PaddockWidgetBentoCardID? = null

    /** Seconds into the pit stop (a pull, or the demo's loop); -1 when none runs. */
    val pit = mutableStateOf(-1.0)

    /** The sticker: the finger, a released finger falling back, or the demo (always with the resting curl). */
    val peel = mutableStateOf(PaddockWidgetBentoMotion.withHint(PaddockWidgetBentoMotion.PeelState(0.0, PaddockWidgetBentoMotion.Peel.angle, -1.0)))

    /** The clock the jiggle reads while the demo does not run. */
    val clock = mutableStateOf(0.0)
    var reducedMotion = false
    private var livePitStart: Double? = null
    var peelHeld = false
        private set
    private var peelDistance = 0.0
    private var peelAngle = PaddockWidgetBentoMotion.Peel.angle
    private var peelReleaseStart: Double? = null
    private var peelReleaseTotal = 0.0
    private val events = Channel<Unit>(Channel.CONFLATED)

    init {
        controller.appear(autoPlay, quiet, paddockNowSeconds())
        publish()
    }

    fun publish(now: Double = paddockNowSeconds()) {
        publishFrame(now)
        order = controller.order.toList()
        isEditing = controller.isEditing
        events.trySend(Unit)
    }

    private fun cleanupExtras(now: Double) {
        livePitStart?.let { if (now >= it + PaddockWidgetBentoMotion.Pit.duration) livePitStart = null }
        peelReleaseStart?.let {
            if (now >= it + peelReleaseTotal) {
                peelReleaseStart = null
                peelDistance = 0.0
            }
        }
    }

    private fun publishFrame(now: Double) {
        cleanupExtras(now)
        val current = controller.frame(now)
        frame.value = current
        clock.value = now
        val live = livePitStart
        pit.value = when {
            live != null && now - live < PaddockWidgetBentoMotion.Pit.duration -> now - live
            current.loopTime >= 0.0 -> current.loopTime - PaddockWidgetBentoMotion.Pit.loopStart
            else -> -1.0
        }
        peel.value = peelAt(current, now)
    }

    private fun peelAt(current: PaddockWidgetBentoFrame, now: Double): PaddockWidgetBentoMotion.PeelState {
        val m = PaddockWidgetBentoMotion
        if (peelHeld) return m.withHint(PaddockWidgetBentoMotion.PeelState(peelDistance, peelAngle, -1.0))
        val start = peelReleaseStart
        if (start != null) {
            val since = now - start
            if (since < peelReleaseTotal) {
                if (reducedMotion) {
                    return m.withHint(PaddockWidgetBentoMotion.PeelState(peelDistance * (1.0 - m.clamp(since / 0.2)), peelAngle, -1.0))
                }
                return m.withHint(m.peelRelease(peelDistance, peelAngle, since))
            }
        } else if (peelDistance > 0.5) {
            return m.withHint(PaddockWidgetBentoMotion.PeelState(peelDistance, peelAngle, -1.0))
        }
        return m.withHint(
            if (current.loopTime >= 0.0) m.peelDemo(current.loopTime) else PaddockWidgetBentoMotion.PeelState(0.0, PaddockWidgetBentoMotion.Peel.angle, -1.0),
        )
    }

    // Touch-driven effects.

    fun peelBegan() {
        val now = paddockNowSeconds()
        peelReleaseStart = null
        peelHeld = true
        controller.holdDemo(now + 30.0)
        publish(now)
    }

    fun peelMoved(dx: Double, dy: Double) {
        peelDistance = min(PaddockWidgetBentoMotion.Peel.touchMaxDistance, hypot(dx, dy))
        peelAngle = atan2(dy, dx) * 180.0 / PI
        publish()
    }

    /** Returns true when the release will slap (a haptic is due after `Peel.release`). */
    fun peelEnded(): Boolean {
        if (!peelHeld) return false
        val now = paddockNowSeconds()
        peelHeld = false
        peelReleaseStart = now
        val slaps = !reducedMotion
        peelReleaseTotal = if (slaps) PaddockWidgetBentoMotion.Peel.release + PaddockWidgetBentoMotion.Peel.slapEnd else 0.2
        controller.holdDemo(now + peelReleaseTotal)
        publish(now)
        return slaps
    }

    fun startPit(): Boolean {
        val now = paddockNowSeconds()
        if (reducedMotion || livePitStart != null) return false
        livePitStart = now
        controller.holdDemo(now + PaddockWidgetBentoMotion.Pit.duration)
        publish(now)
        return true
    }

    private val continuous: Boolean
        get() = peelHeld || (controller.isEditing && !reducedMotion)

    suspend fun runClock() {
        while (currentCoroutineContext().isActive) {
            val now = paddockNowSeconds()
            publishFrame(now)
            val interval = controller.cadence(now)
            val extra = continuous || livePitStart != null || peelReleaseStart != null
            when {
                extra || (interval != null && interval <= 1.0 / 55) -> withFrameNanos { }
                interval == null -> events.receive()
                interval <= 1.0 / 55 -> {
                    // Match TimelineView's 60 Hz minimum interval on faster displays.
                    do {
                        withFrameNanos { }
                    } while (paddockNowSeconds() - now < interval - 0.001)
                }
                else -> withTimeoutOrNull((interval * 1_000).toLong().coerceAtLeast(1)) {
                    events.receive()
                }
            }
        }
    }

    fun setActive(active: Boolean) {
        foreground = active
        controller.setActive(active, paddockNowSeconds())
        if (!active) clearTouch()
        publish()
    }

    fun setVisible(visible: Boolean) {
        controller.setVisible(visible, paddockNowSeconds())
        if (!visible) clearTouch()
        publish()
    }

    fun down(owner: Long, referenceY: Double, timestamp: Double): Boolean {
        val current = controller.frame(paddockNowSeconds())
        val slot0 = PaddockWidgetBentoMotion.slotY[0]
        val id = PaddockWidgetBentoCardID.entries.minBy {
            kotlin.math.abs((current.cardY[it] ?: slot0) - referenceY)
        }
        if (kotlin.math.abs((current.cardY[id] ?: slot0) - referenceY) > 38) return false
        if (activeOwner != null && activeOwner != owner) {
            controller.beginPress(id, referenceY, owner, paddockNowSeconds())
            clearTouch()
            publish()
            return false
        }
        activeOwner = owner
        activeCard = id
        controller.beginPress(id, referenceY, owner, timestamp)
        publish()
        // A visible card can still open while scrolling has suspended the demo controller.
        return true
    }

    fun hold(owner: Long, referenceY: Double): Boolean {
        if (activeOwner != owner) return false
        val id = activeCard ?: return false
        controller.beginDrag(id, referenceY, owner, paddockNowSeconds())
        publish()
        return controller.isDragging
    }

    fun move(owner: Long, referenceY: Double) {
        if (activeOwner != owner || !controller.isDragging) return
        controller.updateDrag(referenceY, owner, paddockNowSeconds())
        publish()
    }

    fun up(owner: Long, onTap: (PaddockWidgetBentoCardID) -> Unit): Boolean {
        if (activeOwner != owner) return false
        val id = activeCard ?: return false
        val wasDragging = controller.isDragging
        val changed = controller.endDrag(owner, paddockNowSeconds())
        clearTouch()
        publish()
        if (!wasDragging && !controller.isEditing) onTap(id)
        return changed
    }

    fun cancel(owner: Long? = activeOwner) {
        if (owner != null && activeOwner != owner) return
        controller.cancelDrag(paddockNowSeconds())
        clearTouch()
        publish()
    }

    fun done() {
        controller.done(paddockNowSeconds())
        clearTouch()
        publish()
    }

    fun restore(autoPlay: Boolean, quiet: Boolean, visible: Boolean) {
        val now = paddockNowSeconds()
        controller.done(now)
        controller = PaddockWidgetBentoController()
        controller.appear(autoPlay, quiet, now)
        controller.setVisible(visible, now)
        controller.setActive(foreground, now)
        clearTouch()
        publish(now)
    }

    private fun clearTouch() {
        activeOwner = null
        activeCard = null
    }
}

// PointerEvent.uptimeMillis uses this clock, including a delayed initial down delivery.
internal fun paddockNowSeconds(): Double = SystemClock.uptimeMillis() / 1_000.0
