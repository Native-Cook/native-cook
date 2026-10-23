package com.ngocdevv.nativecook.blocks.pickers.morphbubblecolumn

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Shader
import androidx.compose.animation.core.CubicBezierEasing
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

// Colours: a white screen with a faint dot grid, near-black ink; graphite shows in the rail's notch.
internal const val PAGE = 0xFF1D1D1B.toInt()
internal const val CARD = 0xFFF4F4F0.toInt()
internal const val GRID_DOT = 0xFFC9C9C2.toInt()
internal const val INK = 0xFF141414.toInt()
internal const val MUTED = 0xFF8B8B84.toInt()
internal const val ON_PAGE = 0xFFECECE6.toInt()
internal const val GRID_STEP = 22f

// The card is the whole screen, edge to edge (the display's own corners round it). A notch cut into
// its right edge holds the tool rail (dp).
internal const val CARD_INSET = 0f
internal const val CARD_RADIUS = 0f
internal const val NOTCH_AT = 0.56f
internal const val NOTCH_HEIGHT = 128f
internal const val NOTCH_DEPTH = 34f
internal const val NOTCH_RADIUS = 13f

// The column: its middle sits this share of the card from the left; a bubble's unit size is this
// share of the card's width, scaled by the zoom slider between ZOOM_MIN and ZOOM_MAX.
private const val COLUMN_X = 0.57f
private const val UNIT = 0.21f
private const val GAP = 14f
private const val ZOOM_MIN = 0.92f
private const val ZOOM_MAX = 1.08f
// Each bubble sits a little off the column's axis, as in a hand-set column; the selected one centres.
private val NUDGE = floatArrayOf(0.08f, -0.06f, 0.07f, -0.08f, 0.05f)
// The selected bubble: a rounded square this many units wide and tall, with corners of this share of
// half its side and a squircle shoulder of this exponent (2 is a circle's arc).
private const val BIG_W = 1.5f
private const val BIG_H = 1.42f
private const val BIG_R = 0.4f
private const val BIG_N = 3.6f

// Selection: a number from 0 (top site) to the last site, on a spring. Under a finger it moves one
// site per FOCUS_DRAG dp and rubber-bands past the ends; on release the finger's speed carries it
// FOCUS_THROW s further before it settles on the nearest site.
private const val FOCUS_DRAG = 92f
private const val FOCUS_RUBBER = 0.3f
private const val FOCUS_THROW = 0.16f
private const val HAND_STIFFNESS = 210f
private val HAND_DAMPING = 2f * 0.82f * sqrt(HAND_STIFFNESS)
// When the loop or a tap moves it: a calmer, critically damped spring (about 0.8 s).
private const val CALM_STIFFNESS = 70f
private val CALM_DAMPING = 2f * sqrt(CALM_STIFFNESS)

// First appearance: every bubble blooms from a dot, top to bottom.
internal const val DOT = 0.1f
internal const val ENTER_DELAY = 0.2f
internal const val ENTER_STAGGER = 0.11f
private const val BLOOM_OMEGA = 9f
private const val BLOOM_ZETA = 0.8f

// Opening: a spring on 0 (in the column) … 1 (the whole card). A drag down by DISMISS_SHARE of the
// screen closes it fully; a release faster than DISMISS_SPEED dp/s, or below CLOSE_BELOW, closes it.
private const val OPEN_STIFFNESS = 95f
private const val OPEN_DAMPING = 17.5f
private const val DISMISS_SHARE = 0.6f
private const val DISMISS_SPEED = 650f
private const val CLOSE_BELOW = 0.8f
private const val RUBBER = 0.22f

// The zoom slider: rest value, and the spring that glides it when not held.
private const val SLIDER_REST = 0.5f
private const val SLIDER_STIFFNESS = 26f
private const val SLIDER_DAMPING = 10.2f
private const val SLIDER_STEPS = 8f

// autoPlay (s): the selection walks down two sites, the third opens and is read while the slider
// zooms in and out, then it closes and the selection walks back to the top, where the loop rests.
private const val LOOP = 18f
private val STEPS = listOf(1.2f to 1, 2.7f to 2, 14.2f to 1, 15.6f to 0)
private const val OPEN_AT = 4.3f
private const val ZOOM_IN_AT = 8.6f
private const val ZOOM_IN_TO = 0.82f
private const val ZOOM_OUT_AT = 10.8f
private const val CLOSE_AT = 12.4f
internal const val AUTO_SITE = 2
// After a touch the loop waits this long; longer when the touch opened a photo, so it can be read.
private const val USER_HOLD = 4f
private const val OPEN_HOLD = 8f

internal val EASE_OUT = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

/** A bubble this frame: its box, corners (share of half its shorter side), exponent, bloom scale and how selected it is. */
internal class Box(var x: Float, var y: Float, var w: Float, var h: Float, var r: Float, var n: Float, var scale: Float, var big: Float)

internal class Loaded(val bitmap: Bitmap, val at: Float) {
    // Drawn through a shader, so the bubble's curved edge stays anti-aliased (a clip path is not).
    val shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
}

internal fun clamp01(v: Float) = min(1f, max(0f, v))
internal fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t

/** JavaScript's Math.round: halves round up. */
internal fun jsRound(v: Float) = floor(v + 0.5f)

/** A whole site index from a selection value, clamped to the column (NaN-safe). */
internal fun siteOf(v: Float, count: Int): Int {
    if (!v.isFinite() || count <= 0) return 0
    return jsRound(v).coerceIn(0f, (count - 1).toFloat()).toInt()
}

/** True when the loop clock went from `before` to `now` across `at`, wrapping at LOOP. */
private fun passed(before: Float, now: Float, at: Float) =
    (before < at && now >= at) || (now < before && (at > before || at <= now))

/** The size multiplier of a bubble `t` s after it starts to bloom from a dot. */
private fun bloomScale(t: Float): Float {
    if (t <= 0f) return DOT
    val wd = BLOOM_OMEGA * sqrt(1f - BLOOM_ZETA * BLOOM_ZETA)
    val decay = exp(-BLOOM_ZETA * BLOOM_OMEGA * t)
    return 1f - (1f - DOT) * decay * (cos(wd * t) + (BLOOM_ZETA * BLOOM_OMEGA / wd) * sin(wd * t))
}

internal fun barTop(height: Float, bottom: Float) = height - max(bottom + 4f, 14f) - 56f

/** The slider track's ends and its touch band on screen. */
internal class Track(val x0: Float, val x1: Float, val y: Float, val top: Float, val bottom: Float)

internal fun track(width: Float, height: Float, bottom: Float): Track {
    val top = barTop(height, bottom)
    return Track(CARD_INSET + 10f + 112f, width - CARD_INSET - 10f - 22f, top + 24f, top, top + 56f)
}

/** The column's state, stepped once a frame like the RN frame callback, and the gestures that move it. */
internal class MorphBubbleColumnMotion(val count: Int) {
    var width = 0f
    var height = 0f
    var top = 0f
    var bottom = 0f

    var boxes: List<Box> = emptyList()
        private set
    // The selection: 0 (top site) … count − 1, its velocity and target; true while a finger set it.
    private var focus = 0f
    private var focusVelocity = 0f
    private var focusTarget = 0f
    private var focusFrom = 0f
    private var byHand = false
    private var lastFocusStep = 0
    // 0 in the column … 1 the whole card, its velocity and target; the site that opens.
    var open = 0f
        private set
    private var openVelocity = 0f
    private var openTarget = 0f
    var openSite = -1
        private set
    private var openFrom = 0f
    // Seconds since the open photo was asked to open.
    var shownFor = 0f
        private set
    // What a drag moves: 0 nothing, 1 the open photo, 2 the slider, 3 the selection.
    private var dragMode = 0
    var slider = SLIDER_REST
        private set
    private var sliderVelocity = 0f
    private var sliderTarget = SLIDER_REST
    private var lastStep = (SLIDER_REST * SLIDER_STEPS).roundToInt()
    private var clock = 0f
    private var looping = false
    private var hold = 0f
    var elapsed = 0f
        private set
    var reducedMotion = false

    val thumbs = arrayOfNulls<Loaded>(count)
    val sharps = arrayOfNulls<Loaded>(count)
    var face: Loaded? = null

    /** Called when a site opens (its index, and whether a finger opened it). */
    var onOpened: (Int, Boolean) -> Unit = { _, _ -> }
    var onCloseByHand: () -> Unit = {}
    /** A selection tick: the slider passed a step, a dragged selection passed a site, or a tap moved it. */
    var onTick: () -> Unit = {}

    fun resetLoop(auto: Boolean) {
        looping = auto
        clock = 0f
        hold = 0f
    }

    /** Sends the selection to `index` on the calm spring. */
    private fun select(index: Int) {
        focusTarget = index.coerceIn(0, max(0, count - 1)).toFloat()
        byHand = false
        if (reducedMotion) {
            focus = focusTarget
            focusVelocity = 0f
        }
    }

    private fun openFocused(user: Boolean) {
        if (count <= 0) return
        val index = siteOf(focusTarget, count)
        openSite = index
        openTarget = 1f
        shownFor = 0f
        onOpened(index, user)
    }

    private fun close(velocity: Float) {
        openTarget = 0f
        openVelocity = velocity
    }

    private fun spring(value: Float, velocity: Float, target: Float, dt: Float, k: Float, c: Float): Pair<Float, Float> {
        val v = velocity + (k * (target - value) - c * velocity) * dt
        return (value + v * dt) to v
    }

    fun advance(dt: Float) {
        if (count <= 0) return
        elapsed += dt
        val isOpen = openSite >= 0

        if (hold > 0f) {
            hold -= dt
            if (hold <= 0f && looping) {
                // The loop takes over again from its resting state: closed, the top site selected.
                clock = 0f
                if (isOpen) close(0f)
                sliderTarget = SLIDER_REST
                select(0)
            }
        } else if (looping) {
            val before = clock
            val t = (before + dt) % LOOP
            clock = t
            for ((at, index) in STEPS) if (passed(before, t, at)) select(index)
            if (passed(before, t, OPEN_AT) && !isOpen && count > AUTO_SITE) {
                select(AUTO_SITE)
                openFocused(false)
            }
            if (passed(before, t, ZOOM_IN_AT)) sliderTarget = ZOOM_IN_TO
            if (passed(before, t, ZOOM_OUT_AT)) sliderTarget = SLIDER_REST
            if (passed(before, t, CLOSE_AT) && isOpen) close(0f)
        }

        if (dragMode != 3) {
            val k = if (byHand) HAND_STIFFNESS else CALM_STIFFNESS
            val c = if (byHand) HAND_DAMPING else CALM_DAMPING
            val (f, v) = spring(focus, focusVelocity, focusTarget, dt, k, c)
            focus = if (f.isFinite()) f else focusTarget
            focusVelocity = if (v.isFinite()) v else 0f
        }

        if (dragMode != 1) {
            if (reducedMotion) {
                // Without spatial motion the photo only fades in and out.
                val gap = openTarget - open
                open += Math.signum(gap) * min(abs(gap), dt / 0.25f)
                openVelocity = 0f
            } else {
                val (o, v) = spring(open, openVelocity, openTarget, dt, OPEN_STIFFNESS, OPEN_DAMPING)
                open = o
                openVelocity = v
            }
            if (openTarget == 0f && open < 0.002f && abs(openVelocity) < 0.05f) {
                open = 0f
                openVelocity = 0f
                openSite = -1
            }
        }
        if (openSite >= 0) shownFor += dt

        if (dragMode != 2) {
            val (s, v) = spring(slider, sliderVelocity, sliderTarget, dt, SLIDER_STIFFNESS, SLIDER_DAMPING)
            slider = s
            sliderVelocity = v
        }
        // A tick each time a dragged selection passes a site, or the slider passes a step by hand.
        val step = (slider * SLIDER_STEPS).roundToInt()
        if (step != lastStep && dragMode == 2) onTick()
        lastStep = step
        val site = siteOf(focus, count)
        if (site != lastFocusStep && dragMode == 3) onTick()
        lastFocusStep = site

        boxes = columnBoxes()
    }

    /**
     * Where each bubble sits this frame. A bubble is as much "selected" as the selection number is
     * close to it, and grows from a circle into the big rounded square by that much. The column is
     * laid out from those sizes, each bubble GAP below the one above, so a growing bubble pushes its
     * neighbours instead of overlapping them; the whole column stays centred between the clock and
     * the bar, and shrinks to fit if it would ever outgrow that room.
     */
    private fun columnBoxes(): List<Box> {
        val cardX = CARD_INSET
        val cardW = width - 2f * CARD_INSET
        val unit = cardW * UNIT * mix(ZOOM_MIN, ZOOM_MAX, slider)
        val list = ArrayList<Box>(count)
        var total = 0f
        for (s in 0 until count) {
            val big = clamp01(1f - abs(s - focus))
            // Corners ease more than size, so a half-selected bubble still reads as round.
            val soft = big * big * (3f - 2f * big)
            val scale = if (reducedMotion) 1f else bloomScale(elapsed - ENTER_DELAY - s * ENTER_STAGGER)
            list.add(Box(0f, 0f, mix(1f, BIG_W, big) * unit * scale, mix(1f, BIG_H, big) * unit * scale, mix(1f, BIG_R, soft), mix(2f, BIG_N, soft), scale, big))
            total += mix(1f, BIG_H, big) * unit + if (s > 0) GAP else 0f
        }
        val top = max(this.top, 50f) + 92f
        val bottom = barTop(height, this.bottom) - 18f
        val fit = min(1f, (bottom - top) / max(1f, total))
        var y = (top + bottom) / 2f - total * fit / 2f
        for (s in list.indices) {
            val box = list[s]
            val slotH = mix(1f, BIG_H, box.big) * unit * fit
            box.w *= fit
            box.h *= fit
            val cx = cardX + cardW * COLUMN_X + NUDGE[s % NUDGE.size] * unit * (1f - box.big)
            box.x = cx - box.w / 2f
            // A blooming bubble grows from the middle of its slot.
            box.y = y + (slotH - box.h) / 2f
            y += slotH + GAP * fit
        }
        return list
    }

    // Touch

    private fun onSlider(x: Float, y: Float): Boolean {
        val t = track(width, height, bottom)
        return x > t.x0 - 18f && x < t.x1 + 18f && y > t.top && y < t.bottom
    }

    private fun setSliderFrom(x: Float) {
        val t = track(width, height, bottom)
        // The track is never narrower than a point, so a value never comes out NaN.
        val value = clamp01((x - t.x0) / max(1f, t.x1 - t.x0))
        slider = value
        sliderTarget = value
        sliderVelocity = 0f
    }

    fun panStart(x: Float, y: Float) {
        hold = if (openSite >= 0) OPEN_HOLD else USER_HOLD
        if (onSlider(x, y)) {
            dragMode = 2
            setSliderFrom(x)
            return
        }
        if (openSite >= 0) {
            if (openTarget == 1f) {
                dragMode = 1
                openFrom = open
            } else {
                dragMode = 0
            }
            return
        }
        // Anywhere on the column screen, a drag moves the selection with the finger.
        dragMode = 3
        focusFrom = focus
        focusVelocity = 0f
    }

    /** `translationY` is the finger's travel since the pan began (dp). */
    fun panUpdate(x: Float, translationY: Float) {
        when (dragMode) {
            2 -> setSliderFrom(x)
            1 -> {
                val distance = max(1f, height * DISMISS_SHARE)
                val p = openFrom - translationY / distance
                open = if (p > 1f) 1f + (p - 1f) * RUBBER else max(0f, p)
            }
            3 -> {
                val raw = focusFrom + translationY / FOCUS_DRAG
                val last = max(0, count - 1).toFloat()
                // Past either end the selection gives way less and less.
                val over = if (raw < 0f) raw else if (raw > last) raw - last else 0f
                val give = FOCUS_RUBBER * over / (FOCUS_RUBBER + abs(over))
                focus = if (raw < 0f) give else if (raw > last) last + give else raw
            }
        }
    }

    /** The finger lifted at `velocityY` dp/s (down is positive). */
    fun panEnd(velocityY: Float) {
        val mode = dragMode
        dragMode = 0
        if (mode == 1) {
            val distance = max(1f, height * DISMISS_SHARE)
            // The finger's speed carries into the spring, in opens per second.
            val velocity = -velocityY / distance
            if (velocityY > DISMISS_SPEED || (open < CLOSE_BELOW && velocityY > -DISMISS_SPEED)) {
                close(velocity)
                onCloseByHand()
            } else {
                openTarget = 1f
                openVelocity = velocity
            }
        } else if (mode == 3) {
            // The finger's speed, in sites per second, is handed to the spring; a flick carries on.
            val velocity = if (velocityY.isFinite()) velocityY / FOCUS_DRAG else 0f
            focusTarget = siteOf(focus + velocity * FOCUS_THROW, count).toFloat()
            focusVelocity = if (reducedMotion) 0f else velocity
            byHand = true
            if (reducedMotion) focus = focusTarget
        }
    }

    /** The system took the finger away: whatever the drag held is let go where it is. */
    fun panCancel() {
        if (dragMode == 3) {
            focusTarget = siteOf(focus, count).toFloat()
            byHand = true
        }
        dragMode = 0
    }

    fun tap(x: Float, y: Float) {
        if (onSlider(x, y)) {
            hold = if (openSite >= 0) OPEN_HOLD else USER_HOLD
            val t = track(width, height, bottom)
            sliderTarget = clamp01((x - t.x0) / max(1f, t.x1 - t.x0))
            onTick()
            return
        }
        if (openSite >= 0) return
        for ((s, b) in boxes.withIndex()) {
            // A little slop around the small bubbles keeps them easy to hit.
            val pad = max(0f, (44f - min(b.w, b.h)) / 2f)
            if (x > b.x - pad && x < b.x + b.w + pad && y > b.y - pad && y < b.y + b.h + pad) {
                if (s == siteOf(focusTarget, count) && abs(focus - s) < 0.35f) {
                    hold = OPEN_HOLD
                    openFocused(true)
                } else {
                    hold = USER_HOLD
                    select(s)
                    onTick()
                }
                return
            }
        }
    }
}
