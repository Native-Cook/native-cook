package com.ngocdevv.nativecook.blocks.text.particlewordmorph

import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Region
import android.graphics.Typeface
import android.os.Build
import androidx.compose.animation.core.CubicBezierEasing
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

// Particles: how many, the grid step (dp) they are sampled from inside each word's glyphs, and the
// sprite each one is drawn with (a soft dot SPRITE wide, drawn at SPRITE_SCALE × its size).
internal const val COUNT = 2600
private const val SAMPLE_STEP = 2.1
internal const val SPRITE = 16f
private const val SPRITE_SCALE = 0.2f
// A word is set to fit WORD_MARGIN dp in from each side (so a scattered word still clears the
// edges), at most WORD_MAX dp tall, its middle at WORD_AT of the screen height.
internal const val WORD_MARGIN = 44f
private const val WORD_MAX = 128f
internal const val WORD_AT = 0.46f

// A morph: each particle travels for TRAVEL s, starting up to SPREAD s late (left to right, with a
// little noise), along an arc that bows SWIRL dp off the straight line in a direction set by a
// slowly turning field. Idle particles shimmer by SHIMMER dp.
private const val TRAVEL = 1.1f
private const val SPREAD = 0.5f
private const val SWIRL = 70f
private const val SHIMMER = 0.7f
private val EASE_IN_OUT = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)
// Colours shift to the next word's over COLOR_TIME s.
private const val COLOR_TIME = 1.2

// The word indicator's mark glides between the words on one critically damped spring (about 0.9 s,
// close to the particles' own flight); a tap within LABEL_GAP / 2 dp of a word picks it.
internal const val LABEL_GAP = 22f
private const val MARK_STIFFNESS = 60.0
private val MARK_DAMPING = 2.0 * sqrt(MARK_STIFFNESS)

// A finger pushes particles within REACH dp away, harder the closer they are (PUSH dp/s² at the
// finger); each one is held to its place by a spring (STIFFNESS, DAMPING) that brings it back with a
// small overshoot once the finger moves on.
private const val REACH = 92f
private const val PUSH = 5200f
private const val STIFFNESS = 46f
private const val DAMPING = 7.5f
// The finger's strength fades in and out over this long (s), so a touch never jolts the field.
private const val FINGER_EASE = 0.12f

// autoPlay (seconds): each word holds, then flows into the next every WORD_EVERY s, starting at
// FIRST_MORPH. While the third word holds, a ghost finger passes through it.
private const val LOOP = 16.0
private const val WORD_EVERY = 3.2
private const val FIRST_MORPH = 1.2
private const val GHOST_FROM = 6.1
private const val GHOST_TIME = 1.1
// After a touch the loop waits this long, then carries on from the word on screen.
private const val USER_HOLD = 4.0

/** A small deterministic random generator (the React Native version's), so every run samples the same particles. */
private class Random32(seed: Long) {
    private var s = seed and 0xFFFFFFFFL
    fun next(): Double {
        s = (s * 1664525L + 1013904223L) and 0xFFFFFFFFL
        return s / 4294967296.0
    }
}

internal fun clamp01(v: Float) = min(1f, max(0f, v))

/** One step of a unit-mass spring towards `to`: the new position and speed. */
private fun springStep(x: Double, v: Double, to: Double, stiffness: Double, damping: Double, dt: Double): Pair<Double, Double> {
    val speed = v + (stiffness * (to - x) - damping * v) * dt
    return (x + speed * dt) to speed
}

/** True when the loop clock went from `before` to `now` across `at`, wrapping at LOOP. */
private fun passed(before: Double, now: Double, at: Double) =
    (before < at && now >= at) || (now < before && (at > before || at <= now))

private fun heavyPaint(size: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    typeface = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        Typeface.create(Typeface.SANS_SERIF, 900, false)
    } else {
        Typeface.create("sans-serif-black", Typeface.NORMAL)
    }
    textSize = size
}

/**
 * The width of `word`'s ink as Skia measures it: each glyph's outline rounded out to whole dp plus
 * one on every side, joined across the word.
 */
private fun measure(word: String, paint: Paint): Float {
    val widths = FloatArray(word.length)
    paint.getTextWidths(word, widths)
    val path = Path()
    val ink = RectF()
    var pen = 0f
    var left = Float.POSITIVE_INFINITY
    var right = Float.NEGATIVE_INFINITY
    for ((i, ch) in word.withIndex()) {
        path.rewind()
        paint.getTextPath(ch.toString(), 0, 1, 0f, 0f, path)
        path.computeBounds(ink, true)
        if (!ink.isEmpty) {
            left = min(left, pen + floor(ink.left) - 1f)
            right = max(right, pen + ceil(ink.right) + 1f)
        }
        pen += widths[i]
    }
    return if (right > left) right - left else 0f
}

/** Containment is tested on the outline scaled up by this much, as a region is whole pixels. */
private const val FINE = 8f

/**
 * Points filling `word`'s glyphs, COUNT of them, centred at (cx, cy). They are sorted left to right,
 * so particle i lands at about the same place in every word's reading order and the words flow
 * sideways into each other rather than crossing chaotically.
 */
private fun sampleWord(word: String, size: Float, cx: Float, cy: Float, seed: Long): Pair<FloatArray, FloatArray> {
    // No word at all: every particle waits at the middle.
    if (word.isEmpty() || !size.isFinite() || size <= 0f || !cx.isFinite() || !cy.isFinite()) {
        return FloatArray(COUNT) { if (cx.isFinite()) cx else 0f } to FloatArray(COUNT) { if (cy.isFinite()) cy else 0f }
    }
    val path = Path()
    heavyPaint(size).getTextPath(word, 0, word.length, 0f, 0f, path)
    path.transform(Matrix().apply { setScale(FINE, FINE) })
    val region = Region()
    region.setPath(path, Region(-100000, -100000, 100000, 100000))
    // A word without ink (spaces only) has an empty box at the origin, like Skia's.
    val bounds = if (region.isEmpty) android.graphics.Rect() else region.bounds
    val bx = bounds.left / FINE.toDouble()
    val by = bounds.top / FINE.toDouble()
    val bw = bounds.width() / FINE.toDouble()
    val bh = bounds.height() / FINE.toDouble()
    val dx = cx - (bx + bw / 2)
    val dy = cy - (by + bh / 2)
    val rand = Random32(seed)
    val inside = ArrayList<DoubleArray>()
    var y = by
    while (y <= by + bh) {
        var x = bx
        while (x <= bx + bw) {
            val jx = x + (rand.next() - 0.5) * SAMPLE_STEP * 0.8
            val jy = y + (rand.next() - 0.5) * SAMPLE_STEP * 0.8
            if (region.contains(floor(jx * FINE).toInt(), floor(jy * FINE).toInt())) inside.add(doubleArrayOf(jx + dx, jy + dy))
            x += SAMPLE_STEP
        }
        y += SAMPLE_STEP
    }
    // Shuffle, then take COUNT (repeating with a nudge if a word is small).
    for (i in inside.size - 1 downTo 1) {
        val j = floor(rand.next() * (i + 1)).toInt()
        val t = inside[i]
        inside[i] = inside[j]
        inside[j] = t
    }
    // A word with nothing to sample (a missing glyph) collapses to its middle.
    if (inside.isEmpty()) inside.add(doubleArrayOf(cx.toDouble(), cy.toDouble()))
    val picked = ArrayList<DoubleArray>(COUNT)
    for (i in 0 until COUNT) {
        val p = inside[i % inside.size]
        if (i >= inside.size) {
            val nx = (rand.next() - 0.5) * 1.6
            val ny = (rand.next() - 0.5) * 1.6
            picked.add(doubleArrayOf(p[0] + nx, p[1] + ny))
        } else {
            picked.add(p)
        }
    }
    // A stable sort, like the engine the React Native version runs on.
    val sorted = picked.sortedWith(compareBy { it[0] + it[1] * 0.18 })
    return FloatArray(sorted.size) { sorted[it][0].toFloat() } to FloatArray(sorted.size) { sorted[it][1].toFloat() }
}

/** Reanimated's interpolateColor: channels mixed in a 2.2 gamma's linear space, then rounded. */
internal fun mixColor(a: Int, b: Int, t: Float): Int {
    fun channel(shift: Int): Int {
        val ca = (((a shr shift) and 0xff) / 255.0).pow(2.2)
        val cb = (((b shr shift) and 0xff) / 255.0).pow(2.2)
        return ((ca + (cb - ca) * t).pow(1 / 2.2) * 255).roundToInt()
    }
    return (0xff shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
}

/** The particles, stepped once a frame like the RN frame callback, and the finger that pushes them. */
internal class ParticleWordMorphMotion(private val words: List<String>, auto: Boolean) {
    // Per-particle constants: its start delay, size, shimmer phase and swirl direction.
    val delay = FloatArray(COUNT)
    val scale = FloatArray(COUNT)
    private val phase = FloatArray(COUNT)
    private val turn = FloatArray(COUNT)

    init {
        val rand = Random32(7)
        for (i in 0 until COUNT) {
            delay[i] = clamp01((i.toDouble() / COUNT + (rand.next() - 0.5) * 0.18).toFloat()) * SPREAD
            scale[i] = SPRITE_SCALE * (0.55f + rand.next().toFloat() * 0.75f)
            phase[i] = (rand.next() * PI * 2).toFloat()
            turn[i] = if (rand.next() < 0.5) -1f else 1f
        }
    }

    private var targets: List<Pair<FloatArray, FloatArray>> = emptyList()
    private var targetsFor = Pair(-1f, -1f)
    // Where each particle travels from, and when its trip began; the finger's push; where it is drawn.
    private val fromX = FloatArray(COUNT)
    private val fromY = FloatArray(COUNT)
    private var word = 0
    private var start = -10.0
    private val ox = FloatArray(COUNT)
    private val oy = FloatArray(COUNT)
    private val vx = FloatArray(COUNT)
    private val vy = FloatArray(COUNT)
    val px = FloatArray(COUNT)
    val py = FloatArray(COUNT)
    var ready = false
        private set

    var width = 0f
    var height = 0f
    var reducedMotion = false
    private var elapsed = 0.0
    private var clock = 0.0
    private var looping = auto
    private var hold = 0.0
    // The finger (or the ghost): where, and how strongly it pushes (0…1, eased).
    var fingerX = 0f
    var fingerY = 0f
    private var fingerOn = 0f
    var fingerTarget = 0f
    // Colours: the palette being left, the one arriving, and when the change began.
    var colorFrom = 0
        private set
    var colorTo = 0
        private set
    private var colorStart = -10.0
    var shown = 0
        private set
    // The indicator's mark, in words (0 … count − 1), and its speed; the labels' middle on screen and
    // each label's left edge and width (dp), set by the view as it lays them out, for taps.
    var mark = 0.0
        private set
    private var markSpeed = 0.0
    var rowY = 0f
    var labelX = FloatArray(0)
    var labelW = FloatArray(0)

    val shift: Float get() = min(1.0, max(0.0, (elapsed - colorStart) / COLOR_TIME)).toFloat()

    fun resetLoop(auto: Boolean) {
        looping = auto
        clock = 0.0
        hold = 0.0
    }

    /**
     * Every word's particle targets, sampled once per screen size; a new size puts every particle
     * straight onto the first word.
     */
    fun prepare() {
        if (width <= 0f || height <= 0f || !width.isFinite() || !height.isFinite() || targetsFor == Pair(width, height)) return
        targetsFor = Pair(width, height)
        val probe = heavyPaint(100f)
        var widest = 1f
        for (w in words) widest = max(widest, measure(w, probe))
        val fontSize = max(12f, min(WORD_MAX, 100f * max(40f, width - WORD_MARGIN * 2f) / widest))
        targets = words.mapIndexed { k, w -> sampleWord(w, fontSize, width / 2f, height * WORD_AT, 17L + k * 101L) }
        ready = targets.isNotEmpty() && targets.all { it.first.size == COUNT && it.second.size == COUNT }
        if (!ready) return
        word = 0
        targets[0].first.copyInto(fromX)
        targets[0].second.copyInto(fromY)
        fromX.copyInto(px)
        fromY.copyInto(py)
        start = -10.0
        ox.fill(0f)
        oy.fill(0f)
        vx.fill(0f)
        vy.fill(0f)
        shown = 0
        colorFrom = 0
        colorTo = 0
        mark = 0.0
        markSpeed = 0.0
    }

    /** Starts every particle towards word `next` from wherever it is now. */
    private fun morphTo(next: Int) {
        if (!ready || next < 0 || next >= targets.size) return
        for (i in 0 until COUNT) {
            // The trip starts from the particle's place on its path, without the finger's push.
            fromX[i] = px[i] - ox[i]
            fromY[i] = py[i] - oy[i]
        }
        word = next
        start = elapsed
        colorFrom = colorTo
        colorTo = next
        colorStart = elapsed
        shown = next
    }

    /**
     * A tap at (x, y) dp: the next word starts to form, or the word whose label was tapped.
     * Returns its index, or null when nothing changes.
     */
    fun tap(x: Float, y: Float): Int? {
        if (!ready) return null
        hold = USER_HOLD
        var next = (shown + 1) % targets.size
        // A tap on the indicator picks that word.
        if (x.isFinite() && y.isFinite() && abs(y - rowY) < 22f) {
            val xs = labelX
            val ws = labelW
            for (k in 0 until minOf(targets.size, xs.size, ws.size)) {
                if (ws[k] > 0f && x > xs[k] - LABEL_GAP / 2f && x < xs[k] + ws[k] + LABEL_GAP / 2f) {
                    if (k == shown) return null
                    next = k
                    break
                }
            }
        }
        morphTo(next)
        return next
    }

    fun touched() {
        hold = USER_HOLD
    }

    fun step(dt: Float) {
        if (!ready) return
        val now = elapsed + dt
        elapsed = now
        val count = targets.size

        var ghost = false
        if (hold > 0) {
            hold -= dt
            // The loop picks up from the word on screen, so it never jumps.
            if (hold <= 0) clock = shown * WORD_EVERY
        } else if (looping) {
            val before = clock
            val t = (before + dt) % LOOP
            clock = t
            for (k in 0 until count) if (passed(before, t, FIRST_MORPH + k * WORD_EVERY)) morphTo((k + 1) % count)
            if (t >= GHOST_FROM && t <= GHOST_FROM + GHOST_TIME) {
                // The ghost finger's path across the third word: left to right, a little uphill, eased.
                val p = EASE_IN_OUT.transform(clamp01(((t - GHOST_FROM) / GHOST_TIME).toFloat()))
                fingerX = width * (0.1f + 0.8f * p)
                fingerY = height * (WORD_AT + 0.05f - 0.08f * p + 0.03f * sin(p * PI.toFloat() * 2f))
                ghost = true
            }
        }
        // The indicator's mark follows the word on screen.
        val goal = shown.toDouble()
        if (reducedMotion) {
            mark = goal
            markSpeed = 0.0
        } else {
            val a = springStep(mark, markSpeed, goal, MARK_STIFFNESS, MARK_DAMPING, dt.toDouble())
            mark = if (a.first.isFinite()) a.first else goal
            markSpeed = if (a.second.isFinite()) a.second else 0.0
        }

        val want = if (ghost) 1f else fingerTarget
        val on = fingerOn + (want - fingerOn) * clamp01(dt / FINGER_EASE)
        fingerOn = if (reducedMotion) 0f else on

        val fx = fingerX
        val fy = fingerY
        val push = fingerOn
        val (toX, toY) = targets.getOrNull(word) ?: targets[0]
        val since = (now - start).toFloat()
        val t = now.toFloat()
        for (i in 0 until COUNT) {
            val tx = toX[i]
            val ty = toY[i]
            var bx = tx
            var by = ty
            val p = clamp01((since - delay[i]) / TRAVEL)
            if (p < 1f) {
                val sx = fromX[i]
                val sy = fromY[i]
                if (reducedMotion) {
                    bx = if (p < 0.5f) sx else tx
                    by = if (p < 0.5f) sy else ty
                } else {
                    val e = EASE_IN_OUT.transform(p)
                    bx = sx + (tx - sx) * e
                    by = sy + (ty - sy) * e
                    // Bow off the straight line along a direction from a slowly turning field.
                    val angle = sin(bx * 0.009f + t * 0.5f) * 2.2f + cos(by * 0.011f - t * 0.4f) * 2.2f + phase[i] * 0.35f
                    val bow = sin(PI.toFloat() * e) * SWIRL * turn[i] * (0.6f + 0.4f * sin(phase[i] * 3f))
                    bx += cos(angle) * bow
                    by += sin(angle) * bow
                }
            }
            if (!reducedMotion) {
                bx += sin(t * 1.7f + phase[i]) * SHIMMER
                by += cos(t * 1.3f + phase[i] * 1.3f) * SHIMMER
            }

            // The finger's push, and the spring that returns each particle.
            var ax = -STIFFNESS * ox[i] - DAMPING * vx[i]
            var ay = -STIFFNESS * oy[i] - DAMPING * vy[i]
            if (push > 0.01f) {
                val ddx = bx + ox[i] - fx
                val ddy = by + oy[i] - fy
                val d = sqrt(ddx * ddx + ddy * ddy) + 0.001f
                if (d < REACH) {
                    val k = (1f - d / REACH) * (1f - d / REACH) * PUSH * push
                    ax += ddx / d * k
                    ay += ddy / d * k
                }
            }
            vx[i] += ax * dt
            vy[i] += ay * dt
            ox[i] += vx[i] * dt
            oy[i] += vy[i] * dt
            px[i] = bx + ox[i]
            py[i] = by + oy[i]
        }
    }
}
