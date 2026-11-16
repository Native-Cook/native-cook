package com.ngocdevv.nativecook.blocks.modals.glasspourmenu

import com.ngocdevv.nativecook.blocks.modals.glasspourmenu.GlassPourMenuAnim.Step as AnimStep
import com.ngocdevv.nativecook.blocks.modals.glasspourmenu.GlassPourMenuMotion as M
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** Cues the host turns into haptics and (when `sound` is on) a glass sound. */
internal object GlassPourMenuBeat {
    const val GRAB_TAP = 0 // finger grabs the drop (soft) + tap
    const val GRAB = 1 // the satellite drop lands (soft)
    const val PINCH = 2 // the bead pinches off (medium) + plip
    const val MERGE = 3 // the bead merges back (light) + merge
    const val ROW = 4 // the bar crosses onto another row (selection)
    const val ROW_TAP = 5 // ... by a touch landing on it (selection) + tap
    const val PICK = 6 // a row is chosen (success)
    const val PICK_DANGER = 7 // Delete (warning)
}

/**
 * The frame callback, the finger and Reduce Motion of the RN block (`GlassPourMenu`), on the main thread. One progress clock p drives
 * everything; the loop plays `timeline`, a touch takes over, and two seconds after it leaves the menu settles closed and the loop restarts.
 */
internal class GlassPourMenuEngine(
    val items: List<GlassPourMenuItem>,
    val g: GlassPourMenuTheme.Geometry,
    val ox: Double,
    val oy: Double,
    val direction: Int,
    val autoPlay: Boolean,
    val reduce: Boolean,
    var controlledOpen: Boolean?,
    val dragToSelect: Boolean,
    initialOpen: Boolean,
) {
    private class Touch(
        val active: Boolean = false,
        val mode: Int = 0,
        val x: Double = 0.0,
        val y: Double = 0.0,
        val p: Double = 0.0,
        val moved: Boolean = false,
        val row: Int = -1,
        val latched: Boolean = false,
        val off: Boolean = false,
    )

    var cue: (Int) -> Unit = {}
    var onSelect: (String) -> Unit = {}
    var onOpenChange: (Boolean) -> Unit = {}
    var onComplete: (Boolean) -> Unit = {}
    var onMenuOpen: (Boolean) -> Unit = {}

    val p = GlassPourMenuAnim(if (initialOpen) 1.0 else 0.0)
    val press = GlassPourMenuAnim(0.0)
    val lensY = GlassPourMenuAnim(g.rows[0])
    val lensA = GlassPourMenuAnim(0.0)
    val dwell = GlassPourMenuAnim(0.0)
    val flash = GlassPourMenuAnim(0.0)
    val fade = GlassPourMenuAnim(if (initialOpen) 1.0 else 0.0)
    val satelliteProgress = GlassPourMenuAnim(0.0)

    var clock = 0.0
        private set
    private var loopT = 0.0
    private var playing = autoPlay && !reduce && controlledOpen == null
    private var pinned: Double? = null
    private var touchedAt = -1e9
    private var settling = false
    private var touch = Touch()
    private var prevP = if (initialOpen) 1.0 else 0.0
    private var pinchAt = -100.0
    private var satelliteAt = -100.0
    private var dipAt = -100.0
    private var dipStrength = 0.0
    private var twist = 0.0
    private var twistKickAt = -100.0
    private var activeRowAt = 0.0
    private var settled = if (initialOpen) 1 else 0
    private var announced = initialOpen

    private val auto get() = playing || pinned != null
    private val time get() = if (auto) loopT else clock

    /** Frame time pinned at t seconds of the loop (previews and checks). */
    fun pin(t: Double?) {
        pinned = t
    }

    /** Whether any animated value is still moving (Reduce Motion redraws only then). */
    val animating: Boolean
        get() = p.running || press.running || lensY.running || lensA.running || dwell.running || flash.running || fade.running || satelliteProgress.running

    private fun stepAnims(dt: Double) {
        p.step(dt)
        press.step(dt)
        lensY.step(dt)
        lensA.step(dt)
        dwell.step(dt)
        flash.step(dt)
        fade.step(dt)
        satelliteProgress.step(dt)
    }

    fun frame(rawMs: Double): Boolean {
        val dt = min(rawMs, 50.0) / 1000
        val now = clock + dt
        clock = now
        stepAnims(dt)
        if (!reduce) {
            val pin = pinned
            if (pin != null || playing) {
                val rawT = pin ?: (loopT + dt)
                val t = ((rawT % M.LOOP) + M.LOOP) % M.LOOP
                loopT = t
                val s = M.timeline(t)
                p.set(s.p)
                press.set(s.press)
                lensY.set(s.lensY)
                lensA.set(s.lensA)
                flash.set(s.flash)
                dwell.set(0.0)
            }
            val pv = p.value
            val before = prevP
            if ((before < M.P_PINCH && pv >= M.P_PINCH) || (before >= M.P_PINCH && pv < M.P_PINCH)) {
                pinchAt = now
                twistKickAt = now
                if (!playing && pinned == null && (touch.active || now - touchedAt < 1.2)) {
                    cue(if (pv >= M.P_PINCH) GlassPourMenuBeat.PINCH else GlassPourMenuBeat.MERGE)
                }
            }
            prevP = pv
            val satelliteAge = now - satelliteAt
            if (satelliteAge >= 0.22 && satelliteAge - dt < 0.22 && !playing && pinned == null) cue(GlassPourMenuBeat.GRAB)
            twist = M.mix(twist, ((8 * PI) / 180) * M.easeInOut(M.clamp01(pv)), 1 - exp(-dt / 0.07))
            if (touch.row >= 0 && now - activeRowAt >= 0.25 && dwell.value == 0.0) dwell.run(AnimStep.Spring(1.0, M.LENS))
            if (autoPlay && controlledOpen == null && !playing && pinned == null && !touch.active && now - touchedAt > M.RESUME_AFTER) {
                if (!settling) {
                    settling = true
                    p.run(AnimStep.Spring(0.0, M.GLIDE))
                    lensA.run(AnimStep.Timing(0.0, 0.16))
                } else if (abs(pv) < 0.001) {
                    settling = false
                    loopT = 0.0
                    playing = true
                }
            }
        }
        // announce only after all labels are revealed; completion once the value settles at an end
        val value = if (reduce) fade.value else p.value
        val next = if (announced) value > 0.9 else value >= 0.999
        if (next != announced) {
            announced = next
            onMenuOpen(next)
        }
        val end = if (abs(value) < 0.001) 0 else if (abs(value - 1) < 0.001) 1 else -1
        if (end >= 0 && end != settled) {
            settled = end
            if (!playing) onComplete(end == 1)
        } else if (value > 0.02 && value < 0.98) {
            settled = -1
        }
        return true
    }

    // ---- what the drawing reads ----

    fun look(): GlassPourMenuGlass {
        if (reduce) return M.glassAt(if (fade.value > 0.5) 1.0 else 0.0, 0.0, g)
        val t = time
        var life = M.lifeAt(t, p.value)
        if (auto && t > 6.55) life = GlassPourMenuLife(sag = life.sag * (1 - M.smooth(6.55, 6.9, t)), breath = life.breath, bob = life.bob)
        val event = if (auto) (if (t > 6.55) -1.0 else t - (if (t >= M.T_CLOSE) M.T_MERGE else M.T_PINCH)) else clock - pinchAt
        return M.glassAt(
            p.value, press.value, g,
            GlassPourMenuLife(
                sag = life.sag, breath = life.breath, bob = life.bob,
                recoil = 5 * M.impulseAt(event, M.RECOIL),
                dimple = -3 * M.impulseAt(event, M.DIMPLE),
                dip = if (auto) 0.0 else 9 * dipStrength * max(0.0, M.impulseAt(clock - dipAt, M.OPEN)),
            ),
        )
    }

    /** The loop or live time the specular sweep and the flower's twist read. */
    fun specTime(): Double = time

    /** [x, y, r] of the satellite droplet a fast flick throws, in dp from O (zeros when none). */
    fun satellite(gl: GlassPourMenuGlass): DoubleArray {
        val sat = clock - satelliteAt
        return if (pinned == null && !playing && sat >= 0 && sat < 0.22) {
            doubleArrayOf(0.0, M.mix(gl.neckB, gl.boxY, M.clamp01(satelliteProgress.value)), 5 * (1 - M.smooth(0.12, 0.22, sat)))
        } else doubleArrayOf(0.0, 0.0, 0.0)
    }

    /** The flower's turn (rad): 8 deg with the pour plus a small kick when the bead pinches or merges. */
    fun twistAngle(): Double {
        if (reduce) return 0.0
        val tm = loopT
        val kick = if (auto) {
            if (tm > 6.55) 0.0 else M.impulseAt(tm - (if (tm >= M.T_CLOSE) M.T_MERGE else M.T_PINCH), M.TWIST)
        } else M.impulseAt(clock - twistKickAt, M.TWIST)
        val turn = if (auto) ((8 * PI) / 180) * M.easeInOut(M.clamp01(M.timeline(max(0.0, tm - 0.07)).p)) else twist
        return turn + ((1.2 * PI) / 180) * kick
    }

    // ---- the finger ----

    private fun takeOver() {
        playing = false
        pinned = null
        settling = false
        p.cancel()
        fade.cancel()
        lensY.cancel()
        lensA.cancel()
        flash.cancel()
        flash.set(0.0)
        touchedAt = clock
    }

    private fun request(next: Boolean, velocity: Double = 0.0) {
        onOpenChange(next)
        val co = controlledOpen
        if (co != null) {
            if (!reduce) p.run(AnimStep.Spring(if (co) 1.0 else 0.0, M.GLIDE))
            return
        }
        if (reduce) {
            if ((fade.value > 0.5) != next) cue(if (next) GlassPourMenuBeat.PINCH else GlassPourMenuBeat.MERGE)
            fade.run(AnimStep.Timing(if (next) 1.0 else 0.0, 0.18))
        } else {
            p.run(
                AnimStep.Spring(
                    if (next) 1.0 else 0.0,
                    if (next) GlassPourMenuSpring(M.mix(22.0, 14.0, M.clamp01(velocity / 6)), M.OPEN.stiffness, M.OPEN.mass) else M.CLOSE,
                    velocity,
                ),
            )
        }
        if (!next) lensA.run(AnimStep.Timing(0.0, 0.16))
    }

    private fun selectRow(i: Int) {
        val item = items.getOrNull(i) ?: return
        if (item.disabled || (p.value < 0.985 && !reduce)) return
        onSelect(item.key)
        cue(if (item.destructive) GlassPourMenuBeat.PICK_DANGER else GlassPourMenuBeat.PICK)
        flash.run(AnimStep.Timing(1.0, 0.12), AnimStep.Timing(0.0, 0.23))
        request(false)
        if (controlledOpen == null) {
            if (reduce) fade.run(AnimStep.Delay(0.3), AnimStep.Timing(0.0, 0.18))
            else p.run(AnimStep.Delay(0.3), AnimStep.Spring(0.0, M.CLOSE))
            lensA.run(AnimStep.Delay(0.3), AnimStep.Timing(0.0, 0.16))
        }
    }

    private fun rowAt(y: Double): Int {
        val i = floor((y - g.rows[0]) / g.rowPitch + 0.5).toInt()
        return if (i >= 0 && i < items.size && !items[i].disabled) i else -1
    }

    private fun focusRow(i: Int, onset: Boolean = false) {
        if (i >= 0) {
            if (reduce) lensY.set(g.rows[i]) else lensY.run(AnimStep.Spring(g.rows[i], M.LENS))
            lensA.run(AnimStep.Timing(1.0, 0.14))
            cue(if (onset) GlassPourMenuBeat.ROW_TAP else GlassPourMenuBeat.ROW)
        } else {
            lensA.run(AnimStep.Timing(0.0, 0.14))
        }
        dwell.set(0.0)
        activeRowAt = clock
    }

    fun panBegin(x: Double, y: Double) {
        takeOver()
        val qx = x - ox
        val qy = (y - oy) * direction
        val pv = if (reduce) (if (fade.value > 0.5) 1.0 else 0.0) else p.value
        val gl = M.glassAt(pv, 0.0, g)
        var mode = 4
        if (hypot(qx, qy - gl.beadY) < max(gl.beadR + 10, 22.0)) mode = if (pv > 0.5) 3 else 1
        else if (abs(qx) < gl.boxW && abs(qy - gl.boxY) < gl.boxH) mode = if (pv > 0.985) 2 else 1
        val row = if (mode == 2) rowAt(qy) else -1
        touch = Touch(active = true, mode = mode, x = x, y = y, p = p.value, row = row)
        if (mode == 1 || mode == 3) {
            press.run(AnimStep.Spring(1.0, M.SNAP))
            cue(GlassPourMenuBeat.GRAB_TAP)
        }
        if (row >= 0) focusRow(row, true)
    }

    fun panUpdate(x: Double, y: Double) {
        val t = touch
        if (!t.active) return
        touchedAt = clock
        val dy = (y - t.y) * direction
        val qy = (y - oy) * direction
        val moved = t.moved || hypot(x - t.x, y - t.y) > M.TAP_SLOP
        val off = abs(x - ox) > g.panelHalf + 40 || qy < g.panelTop || qy > g.panelBottom
        var latched = t.latched
        var row = t.row
        if (!reduce && (t.mode == 1 || t.mode == 3)) {
            val raw = t.p + (if (t.mode == 3) -dy else dy) / M.PT_PER_P
            val rubber = if (raw < 0) raw / (1 - raw * 6) else if (raw > 1) 1 + (raw - 1) / (1 + (raw - 1) * 6) else raw
            p.set(rubber)
            latched = t.mode == 1 && raw >= 1 && dragToSelect
        }
        if (t.mode == 2 || latched) {
            val next = if (!off) rowAt(qy) else -1
            if (next != row) {
                row = next
                focusRow(row)
            }
        }
        touch = Touch(true, t.mode, t.x, t.y, t.p, moved, row, latched, off)
    }

    /** The finger lifted; `vy` in dp per second. */
    fun panEnd(vy: Double) {
        val t = touch
        if (t.mode == 1 || t.mode == 3) {
            if (!t.moved) {
                request(t.mode == 1)
            } else if (t.latched && t.row >= 0) {
                selectRow(t.row)
            } else {
                val velocity = (vy * direction * (if (t.mode == 3) -1 else 1)) / M.PT_PER_P
                request(p.value + velocity * M.PROJECTION > 0.5, velocity)
                dipAt = clock
                dipStrength = M.clamp01(velocity / 6)
                if (!reduce && abs(vy) > 900 && p.value > 0.6) {
                    satelliteAt = clock
                    satelliteProgress.set(0.0)
                    satelliteProgress.run(AnimStep.Spring(1.0, M.SATELLITE))
                }
            }
        } else if (t.mode == 2 && t.row >= 0) {
            selectRow(t.row)
        } else if (t.mode == 4 || t.off) {
            request(false)
        }
    }

    fun panFinalize(success: Boolean) {
        touch = Touch()
        touchedAt = clock
        press.run(AnimStep.Spring(0.0, M.SNAP))
        dwell.run(AnimStep.Spring(0.0, M.LENS))
        if (!success) request(p.value > 0.5)
    }

    /** A screen-reader action: -1 open, -2 close, 0.. choose that row. */
    fun activate(action: Int) {
        takeOver()
        if (action < 0) request(action == -1) else selectRow(action)
    }

    /** The host changed the controlled `open`: follow it from where the glass is. */
    fun onControlledChange() {
        val open = controlledOpen ?: return
        playing = false
        if (reduce) fade.run(AnimStep.Timing(if (open) 1.0 else 0.0, 0.18))
        else p.run(AnimStep.Spring(if (open) 1.0 else 0.0, if (open) M.OPEN else M.CLOSE))
    }
}
