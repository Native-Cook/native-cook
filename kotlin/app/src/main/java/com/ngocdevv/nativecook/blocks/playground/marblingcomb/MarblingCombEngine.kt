package com.ngocdevv.nativecook.blocks.playground.marblingcomb

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

// The tick every step of the paint runs on, s, and at most this many per frame (a 1/30 s cap).
internal const val TICK = 1.0 / 60
private const val MAX_TICKS = 2
// A tool moves through the paint in steps no longer than this, dp.
private const val SUBSTEP = 6.0

// A tapped drop spreads to DROP_RADIUS dp over DROP_TIME s (DROP_TIME_REDUCED with reduced motion).
private const val DROP_RADIUS = 46.0
private const val DROP_TIME = 0.9
private const val DROP_TIME_REDUCED = 0.22
// Holding a finger still drips a drop every DRIP_EVERY s, cycling the paints, for rings.
private const val DRIP_EVERY = 0.34
private const val DRIP_RADIUS = 30.0

// The comb: tines COMB_SPACING dp apart across the whole tray.
private const val COMB_SPACING = 30.0
private const val COMB_SHARPNESS = 6.5
private const val COMB_GAIN = 0.78
private const val COMB_WAKE = 44.0

// The print: the sheet comes down over LAY s, soaks for SOAK s, then peels from its lower right
// corner over PEEL s, pausing about two thirds of the way to show the print on its back.
internal const val LAY = 0.8
internal const val SOAK = 0.5
internal const val PEEL = 2.7
internal const val PRINT_TIME = LAY + SOAK + PEEL
// Paint seen through the wet sheet before it is lifted.
internal const val SOAK_GHOST = 0.2
// Paint left on the size after a print, fading over RESIDUE_FADE s.
internal const val RESIDUE = 0.14
private const val RESIDUE_FADE = 0.7

// A touch pauses autoPlay this long, s.
private const val USER_HOLD = 4.0

// What happened during a frame, for haptics and the controls.
internal const val EVENT_DROPPED = 1
internal const val EVENT_STROKED = 2
internal const val EVENT_PRESSED = 4
internal const val EVENT_PRINT_START = 8
internal const val EVENT_PRINT_DONE = 16

/**
 * The frame loop of the React Native block (its `useFrameCallback`, script and gestures), step for
 * step: the paint, the tool, the drip, the print and the autoPlay clock on one fixed tick. No
 * Android here: it compiles on its own.
 */
internal class MarbleEngine(auto: Boolean, val reduced: Boolean, private val paints: Int) {
    var marble: Marble? = null
        private set
    var elapsed = 0.0
        private set
    private var acc = 0.0

    /** The tool picked in the switch (0 stylus, 1 comb) and the paint picked in the row. */
    var selectedTool = 0
        set(value) {
            field = value
            toolKind = value
        }
    var ink = 2

    // The tool in the paint: where it was last applied, where the finger (or the script) has it.
    var toolKind = 0
        private set
    var toolOn = false
        private set
    var toolX = 0.0
        private set
    var toolY = 0.0
        private set
    private var appliedX = 0.0
    private var appliedY = 0.0
    var toolAlpha = 0.0
        private set
    // The comb's axis and its tines: set at the start of each stroke.
    var combAX = 1.0
        private set
    var combAY = 0.0
        private set
    var combSpacing = COMB_SPACING
        private set
    var combSide = 8.0
        private set
    private var scripted = false
    private val stylus = MarbleTool(0.0, 0.0, 1.0, 0.0, 10.0, 0.85, 60.0)
    private val comb = MarbleTool(COMB_SPACING, 8.0, 1.0, 0.0, COMB_SHARPNESS, COMB_GAIN, COMB_WAKE)

    private var dripOn = false
    private var dripX = 0.0
    private var dripY = 0.0
    private var dripTime = 0.0
    private var dripColor = 2

    /** The print: < 0 when no sheet is down, else seconds since it started. */
    var printT = -1.0
        private set
    var residue = 0.0
        private set

    private var clock = 0.0
    private var hold = 0.0
    private var looping = auto

    /** What happened since the events were last read (EVENT_… bits). */
    var events = 0
    /** Called the moment the sheet lies flat, before the tray is cleared: take the paint up now. */
    var onTakePrint: (() -> Unit)? = null

    /** Restarts the autoPlay clock (autoPlay switched on or off). */
    fun setAuto(auto: Boolean) {
        looping = auto
        clock = 0.0
        hold = 0.0
    }

    /** Lays the sheet down: the print runs from here on the shared tick. */
    private fun startPrint() {
        if (printT >= 0) return
        printT = 0.0
        toolOn = false
        dripOn = false
        events = events or EVENT_PRINT_START
    }

    /** Moves the tool to (x, y) through the paint in short steps. */
    private fun applyTool(m: Marble, x: Double, y: Double) {
        val fx = appliedX
        val fy = appliedY
        val len = hypot(x - fx, y - fy)
        if (!(len >= 0.05) || len.isInfinite()) return
        val steps = min(400, max(1, ceil(len / SUBSTEP).toInt()))
        val t = if (toolKind == 1) {
            comb.spacing = combSpacing
            comb.side = combSide
            comb.axisX = combAX
            comb.axisY = combAY
            comb
        } else {
            stylus
        }
        var px = fx
        var py = fy
        for (i in 1..steps) {
            val nx = fx + (x - fx) * i / steps
            val ny = fy + (y - fy) * i / steps
            m.moveTool(t, px, py, nx, ny)
            px = nx
            py = ny
        }
        appliedX = x
        appliedY = y
    }

    /** One step of the autoPlay script, from `before` to `t` seconds into the loop. */
    private fun script(m: Marble, before: Double, t: Double) {
        val w = m.width
        val h = m.height
        for (d in ScriptDrops) {
            if (marbleCrossed(before, t, d.at)) m.drop(d.x * w, d.y * h, d.r * w, d.color, DROP_TIME)
        }
        var active = -1
        ScriptPasses.forEachIndexed { k, p -> if (t >= p.from && t <= p.to) active = k }
        if (active >= 0) {
            val p = ScriptPasses[active]
            val e = marbleEaseInOut((t - p.from) / (p.to - p.from))
            val x = (p.x0 + (p.x1 - p.x0) * e) * w
            val y = (p.y0 + (p.y1 - p.y0) * e) * h
            if (!toolOn || !scripted) {
                scripted = true
                toolOn = true
                toolKind = 1
                combAX = if (p.horizontal) 1.0 else 0.0
                combAY = if (p.horizontal) 0.0 else 1.0
                combSpacing = p.spacing * w
                combSide = ceil((if (p.horizontal) w else h) / (p.spacing * w)) + 1
                comb.sharpness = p.sharpness
                comb.gain = p.gain
                comb.wake = p.wake
                appliedX = x
                appliedY = y
            }
            toolX = x
            toolY = y
        } else if (scripted) {
            scripted = false
            toolOn = false
            toolKind = selectedTool
        }
        if (marbleCrossed(before, t, PRINT_AT)) startPrint()
    }

    /** One frame `frameDt` s after the last one, on a tray `width` × `height` dp. */
    fun frame(frameDt: Double, width: Double, height: Double) {
        if (width == 0.0) return
        val m = marble ?: Marble(width, height, 0).also { marble = it }
        // The tray follows the layout (a rotation or a resize keeps the paint where it is).
        m.width = width
        m.height = height
        val dt = min(frameDt, MAX_TICKS * TICK)
        var pending = acc + dt
        var ticks = 0
        while (pending >= TICK && ticks < MAX_TICKS) {
            pending -= TICK
            ticks += 1
            elapsed += TICK

            if (hold > 0) {
                hold -= TICK
                if (hold <= 0) clock = 0.0
            } else if (looping) {
                val before = clock
                val t = (before + TICK) % LOOP
                clock = t
                script(m, before, t)
            }

            if (toolOn) applyTool(m, toolX, toolY)

            if (dripOn) {
                dripTime += TICK
                if (dripTime >= DRIP_EVERY) {
                    dripTime -= DRIP_EVERY
                    val c = dripColor
                    m.drop(dripX, dripY, DRIP_RADIUS, c, if (reduced) DROP_TIME_REDUCED else DROP_TIME * 0.8)
                    dripColor = (c % max(1, paints - 1)) + 1
                    events = events or EVENT_DROPPED
                }
            }

            m.step(TICK)

            // The print: the sheet takes up the paint the moment it lies flat.
            val p = printT
            if (p >= 0) {
                val next = p + TICK
                if (p < LAY && next >= LAY) {
                    onTakePrint?.invoke()
                    m.clear(0)
                    residue = 1.0
                    events = events or EVENT_PRESSED
                }
                if (next >= PRINT_TIME) {
                    printT = -1.0
                    events = events or EVENT_PRINT_DONE
                } else {
                    printT = next
                }
            }
            if (residue > 0 && p < 0) residue = max(0.0, residue - TICK / RESIDUE_FADE)
        }
        acc = min(pending, TICK)
        toolAlpha += ((if (toolOn) 1.0 else 0.0) - toolAlpha) * min(1.0, dt * 14)
    }

    // ─── Gestures ────────────────────────────────────────────────────────────────────────────────

    private fun touched() {
        hold = USER_HOLD
        if (scripted) {
            scripted = false
            toolOn = false
        }
    }

    /** A drag began at (x0, y0) and is now at (x, y). */
    fun panStart(x0: Double, y0: Double, x: Double, y: Double) {
        val m = marble ?: return
        if (printT >= 0) return
        touched()
        toolKind = selectedTool
        // The comb lies across the stroke: drawn sideways, its axis runs up and down.
        val sideways = abs(x - x0) > abs(y - y0)
        combAX = if (sideways) 0.0 else 1.0
        combAY = if (sideways) 1.0 else 0.0
        combSpacing = COMB_SPACING
        combSide = ceil(max(m.width, m.height) / COMB_SPACING) + 1
        comb.sharpness = COMB_SHARPNESS
        comb.gain = COMB_GAIN
        comb.wake = COMB_WAKE
        appliedX = x0
        appliedY = y0
        toolX = x
        toolY = y
        toolOn = true
        events = events or EVENT_STROKED
    }

    fun panMove(x: Double, y: Double) {
        if (!toolOn || scripted) return
        toolX = x
        toolY = y
    }

    fun panEnd() {
        if (!scripted) toolOn = false
    }

    fun tap(x: Double, y: Double) {
        val m = marble ?: return
        if (printT >= 0) return
        touched()
        m.drop(x, y, DROP_RADIUS, ink, if (reduced) DROP_TIME_REDUCED else DROP_TIME)
        events = events or EVENT_DROPPED
    }

    fun dripStart(x: Double, y: Double) {
        if (printT >= 0) return
        touched()
        dripX = x
        dripY = y
        dripColor = ink
        // The first drop lands now; the rest follow every DRIP_EVERY s.
        dripTime = DRIP_EVERY
        dripOn = true
    }

    fun dripEnd() {
        dripOn = false
    }

    /** The Print button. */
    fun printButton() {
        hold = USER_HOLD + PRINT_TIME
        scripted = false
        startPrint()
    }
}
