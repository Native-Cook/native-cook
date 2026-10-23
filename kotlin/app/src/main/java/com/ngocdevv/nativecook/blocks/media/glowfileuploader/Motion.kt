package com.ngocdevv.nativecook.blocks.media.glowfileuploader

import androidx.compose.animation.core.CubicBezierEasing
import com.ngocdevv.nativecook.shared.EaseOutStrong
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** One loop of the autoPlay, in seconds. Every ambient period divides it, so the loop has no jump. */
internal const val LOOP = 12.0

// The autoPlay's timeline, in seconds from the start of a loop: Select files is pressed twice (file
// #0 uploads, file #1 fails at 64%), Retry is pressed on the failed row, then Continue once both are
// done, which shows "Uploaded" and sends the rows away. The loop ends at rest, as it started.
internal val AUTO_ADDS = doubleArrayOf(0.8, 1.75)
internal const val AUTO_RETRY = 4.7
internal const val AUTO_CONTINUE = 8.3

/** The row the autoPlay retries: the second file. */
internal const val AUTO_RETRY_ROW = 1

/** Seconds a press is held in the autoPlay. */
internal const val PRESS_FOR = 0.12

/** Seconds after the last touch (once nothing uploads or is held) before the autoPlay takes over again. */
internal const val USER_HOLD = 4.0

/** The list holds at most this many rows; Select files is off while it is full. */
internal const val MAX_ROWS = 3

internal class Spring(val mass: Double, val stiffness: Double, val damping: Double)

// Ambient, always running. The zone's glow breathes between two opacities, its hairlines drift one
// spacing to the right, and the badge's halo pulses in size and opacity.
internal const val BREATH_PERIOD = 4.0
internal const val BREATH_LOW = 0.82
internal const val BREATH_HIGH = 1.0
internal const val DRIFT_PERIOD = 6.0
internal const val HALO_PERIOD = 3.0
internal const val HALO_SCALE_LOW = 0.92
internal const val HALO_SCALE_HIGH = 1.08
internal const val HALO_ALPHA_LOW = 0.22
internal const val HALO_ALPHA_HIGH = 0.34

/** Pressing down: seconds to get there, and how far each button goes. */
internal const val PRESS_IN = 0.1
internal const val SELECT_PRESS = 0.95
internal const val RETRY_PRESS = 0.94
internal const val CONTINUE_PRESS = 0.97

/** A button coming back up after a press. */
internal val RELEASE = Spring(1.0, 380.0, 20.0)

// A file added: the zone flares (extra glow and a slight swell that decay together), the badge
// shakes, and a ring leaves the badge.
internal const val FLARE = 0.45
internal const val FLARE_SCALE = 0.012
internal const val FLARE_FOR = 0.7
internal const val SHAKE_ANGLE = -18.0
internal val SHAKE = Spring(1.0, 300.0, 9.0)
internal const val PULSE_FOR = 0.7
internal const val PULSE_TO = 2.6
internal const val PULSE_ALPHA = 0.5

// The new row: its slot grows on a spring while its card drops in from above, a little small.
internal val ARRIVE = Spring(1.0, 260.0, 24.0)
internal const val CARD_DELAY = 0.04
internal const val CARD_IN = 0.25
internal const val CARD_RISE = 14.0
internal const val CARD_SCALE = 0.94

/** An upload takes this long from 0 to 100%, on this curve; a failing one stops where it fails. */
internal const val UPLOAD = 1.6
internal val UploadCurve = CubicBezierEasing(0.45f, 0f, 0.2f, 1f)

// Done: the ring gives way to a green disc that pops in, the check draws itself, the fill fades.
internal val DISC = Spring(1.0, 320.0, 15.0)
internal const val DISC_FROM = 0.6
internal const val DISC_FADE = 0.1
internal const val RING_FADE = 0.1
internal const val CHECK_DELAY = 0.06
internal const val CHECK_DRAW = 0.28
internal const val FILL_FADE = 0.3

// Failed: the fill turns red and drains, the ring shrinks away, the row shakes (x = 7·e^(−7t)·sin(2π·6t)),
// the "Upload failed" pill opens from the left with its "!" popping, and "Retry" slides in.
internal const val TINT_FOR = 0.12
internal const val DRAIN = 0.35
internal const val RING_SHRINK = 0.15
internal const val ROW_SHAKE = 7.0
internal const val ROW_SHAKE_DECAY = 7.0
internal const val ROW_SHAKE_HZ = 6.0
internal const val ROW_SHAKE_FOR = 0.6
internal const val PILL_OPEN = 0.32
internal val BANG = Spring(1.0, 400.0, 14.0)
internal const val RETRY_DELAY = 0.1
internal const val RETRY_IN = 0.25
internal const val RETRY_SLIDE = 10.0

// Retry pressed: the arrows spin a turn, the pill closes, "Retry" fades, the upload starts again
// a moment later (and succeeds) with the ring growing back.
internal const val SPIN = 0.55
internal const val PILL_CLOSE = 0.2
internal const val RETRY_OUT = 0.2
internal const val RESTART = 0.15
internal const val RING_BACK = 0.15

// Continue: its colours cross-fade when it turns on, and one sheen sweeps across it.
internal const val ENABLE_FADE = 0.3
internal const val SHEEN_DELAY = 0.1
internal const val SHEEN_FOR = 0.8
internal const val SHEEN_WIDTH = 60.0

/** Cancel and Select files dim to this when off, over [FADE]. */
internal const val OFF_ALPHA = 0.4

/** Continue pressed: its label cross-fades to "Uploaded", which stays this long before the rows leave. */
internal const val LABEL_FADE = 0.2
internal const val UPLOADED_FOR = 0.9

// Rows leaving, the bottom one first: each card fades, rises and shrinks while its slot collapses.
internal const val LEAVE_STAGGER = 0.07
internal const val LEAVE_FOR = 0.22
internal const val LEAVE_RISE = 10.0
internal const val LEAVE_SCALE = 0.96
internal const val COLLAPSE = 0.3

/** Reduced motion: every change is a fade (or a plain resize) of this length, in place. */
internal const val FADE = 0.2

/** The ease-in-out used for slots collapsing and the sheen. */
internal val InOut = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)

/** Long ago (an event that is over) and not yet (one that has not happened). */
internal const val NEVER = -1e6
internal const val LATER = 1e9

private const val EPS = 1e-6

internal fun clamp01(value: Double) = value.coerceIn(0.0, 1.0)

private fun ease(curve: CubicBezierEasing, t: Double) = curve.transform(clamp01(t).toFloat()).toDouble()

internal fun easeOut(t: Double) = ease(EaseOutStrong, t)

internal fun easeInOut(t: Double) = ease(InOut, t)

internal fun lerp(a: Double, b: Double, t: Double) = a + (b - a) * t

/** A spring let go from 0 towards 1 at rest, [t] seconds later (the closed form, so every stack agrees). */
internal fun spring(t: Double, s: Spring): Double {
    if (t <= 0) return 0.0
    val w0 = sqrt(s.stiffness / s.mass)
    val zeta = s.damping / (2 * sqrt(s.stiffness * s.mass))
    if (zeta >= 1) return 1 - exp(-w0 * t) * (1 + w0 * t)
    val wd = w0 * sqrt(1 - zeta * zeta)
    return 1 - exp(-zeta * w0 * t) * (cos(wd * t) + (zeta * w0 / wd) * sin(wd * t))
}

/** Seconds an upload takes to reach [share] on [UploadCurve]. */
internal fun reachAfter(share: Double): Double {
    var lo = 0.0
    var hi = 1.0
    repeat(30) {
        val mid = (lo + hi) / 2
        if (ease(UploadCurve, mid) < share) lo = mid else hi = mid
    }
    return (lo + hi) / 2 * UPLOAD
}

/**
 * One file in the list, as the times of what happened to it. [failPoint] is where its first upload
 * stops (null when it succeeds); a retry always succeeds.
 */
internal data class Upload(
    val id: Int,
    val name: String,
    val failPoint: Double?,
    val addedAt: Double,
    val retryDownAt: Double = NEVER,
    val retryLiftAt: Double = NEVER,
    val retryAt: Double = LATER,
    val dismissAt: Double = LATER,
    val leaveAt: Double = LATER,
) {
    val failAt: Double = failPoint?.let { addedAt + reachAfter(it) } ?: LATER
    val retried get() = retryAt < LATER
    val restartAt get() = if (retried) retryAt + RESTART else LATER
    val doneAt get() = when {
        failPoint == null -> addedAt + UPLOAD
        retried -> restartAt + UPLOAD
        else -> LATER
    }

    fun failed(now: Double) = now >= failAt && !retried
    fun uploading(now: Double) = now >= addedAt && now < doneAt && now < dismissAt && !failed(now)
}

/** Everything that happened, as times on the clock in seconds. */
internal data class Events(
    val rows: List<Upload> = emptyList(),
    val nextId: Int = 0,
    val nextFile: Int = 0,
    val addAt: Double = NEVER,
    val selectDownAt: Double = NEVER,
    val selectLiftAt: Double = NEVER,
    val continueDownAt: Double = NEVER,
    val continueLiftAt: Double = NEVER,
    val continueAt: Double = NEVER,
) {
    /** The rows Continue and Cancel act on at [t]: added and not yet sent away. */
    fun batch(t: Double) = rows.filter { it.addedAt <= t && t < it.dismissAt }

    fun uploaded(t: Double) = t >= continueAt && t < continueAt + UPLOADED_FOR
    fun selectOn(t: Double) = batch(t).size < MAX_ROWS
    fun cancelOn(t: Double) = batch(t).isNotEmpty()
    fun continueOn(t: Double) = batch(t).let { b -> b.isNotEmpty() && b.all { it.doneAt <= t } }
    fun continueTappable(t: Double) = continueOn(t) && !uploaded(t)

    /** Something is uploading, held down or showing "Uploaded": the autoPlay waits for it. */
    fun busy(t: Double) = uploaded(t) || selectDownAt > selectLiftAt || continueDownAt > continueLiftAt ||
        rows.any { it.uploading(t) || it.retryDownAt > it.retryLiftAt }

    /** Every time at which something can turn on or off. */
    private fun times(): List<Double> = buildList {
        for (r in rows) {
            add(r.addedAt)
            add(r.doneAt)
            add(r.dismissAt)
        }
        add(continueAt)
        add(continueAt + UPLOADED_FOR)
    }.filter { it > NEVER / 2 && it < LATER / 2 }

    /** The last time before [now] at which [on] flipped, or null if it never did. */
    fun flippedAt(now: Double, on: Events.(Double) -> Boolean): Double? =
        times().filter { it <= now && on(it - EPS) != on(it) }.maxOrNull()

    /** [on] as a level from 0 to 1 that fades over [fade] seconds whenever it flips. */
    fun level(now: Double, fade: Double, on: Events.(Double) -> Boolean): Double {
        val state = on(now)
        val flip = flippedAt(now, on) ?: return if (state) 1.0 else 0.0
        val k = clamp01((now - flip) / fade)
        return if (state) k else 1 - k
    }

    /** Drops the rows that have finished leaving by [now]. */
    fun visibleAt(now: Double, still: Boolean) = copy(rows = rows.filter { now < it.leaveAt + if (still) FADE else COLLAPSE })

    fun add(at: Double, files: List<UploadFile>): Events {
        val file = files[nextFile % files.size]
        val row = Upload(nextId, file.name, file.failsAt?.toDouble(), at)
        return copy(rows = rows + row, nextId = nextId + 1, nextFile = nextFile + 1, addAt = at)
    }

    private fun row(id: Int, change: (Upload) -> Upload) = copy(rows = rows.map { if (it.id == id) change(it) else it })

    fun retryDown(id: Int, at: Double) = row(id) { it.copy(retryDownAt = at) }
    fun retryLift(id: Int, at: Double) = row(id) { it.copy(retryLiftAt = at) }
    fun retry(id: Int, at: Double) = row(id) { it.copy(retryAt = at) }

    /** Sends the rows that are not already gone by [at] away then, the bottom one first. */
    fun dismiss(at: Double): Events {
        val going = rows.filter { it.dismissAt > at }
        val n = going.size
        return copy(
            rows = rows.map { r ->
                val i = going.indexOf(r)
                if (i < 0) r else r.copy(dismissAt = at, leaveAt = at + (n - 1 - i) * LEAVE_STAGGER)
            },
        )
    }

    /** Continue pressed at [at]: "Uploaded" shows, then the rows leave. */
    fun continued(at: Double) = copy(continueAt = at).dismiss(at + UPLOADED_FOR)
}

/** The events of the autoPlay up to [now], for a loop that started at [start]. */
internal fun autoEvents(now: Double, start: Double, files: List<UploadFile>): Events {
    val base = start + floor((now - start) / LOOP) * LOOP
    fun at(offset: Double) = (base + offset).takeIf { it <= now }
    var e = Events()
    for (down in AUTO_ADDS) {
        val d = at(down) ?: break
        e = e.copy(selectDownAt = d)
        val up = at(down + PRESS_FOR) ?: break
        e = e.copy(selectLiftAt = up).add(up, files)
    }
    at(AUTO_RETRY)?.let { e = e.retryDown(AUTO_RETRY_ROW, it) }
    at(AUTO_RETRY + PRESS_FOR)?.let { e = e.retryLift(AUTO_RETRY_ROW, it).retry(AUTO_RETRY_ROW, it) }
    at(AUTO_CONTINUE)?.let { e = e.copy(continueDownAt = it) }
    at(AUTO_CONTINUE + PRESS_FOR)?.let { e = e.copy(continueLiftAt = it).continued(it) }
    return e
}

// ---- Ambient ----

/** The zone glow's opacity: 0.82 at the start of a loop, 1.0 two seconds later. */
internal fun breath(now: Double, still: Boolean) =
    if (still) (BREATH_LOW + BREATH_HIGH) / 2 else lerp(BREATH_LOW, BREATH_HIGH, (1 - cos(2 * PI * now / BREATH_PERIOD)) / 2)

/** How far the hairlines have drifted, as a share of their spacing. */
internal fun drift(now: Double, still: Boolean) = if (still) 0.0 else (now / DRIFT_PERIOD).mod(1.0)

/** The halo's pulse from 0 (small, faint) to 1 (big, bright). */
internal fun haloWave(now: Double, still: Boolean) = if (still) 0.5 else (1 - cos(2 * PI * now / HALO_PERIOD)) / 2

// ---- Presses and the zone ----

/** The size of a button: down while pressed, springing back up after. */
internal fun pressScale(downAt: Double, liftAt: Double, now: Double, depth: Double): Double {
    val press = if (downAt > liftAt) {
        easeOut((now - downAt) / PRESS_IN)
    } else {
        easeOut((liftAt - downAt) / PRESS_IN) * (1 - spring(now - liftAt, RELEASE))
    }
    return 1 - (1 - depth) * press
}

/** The flare after a file is added: 1 at once, decaying to 0. */
internal fun flare(e: Events, now: Double, still: Boolean) =
    if (still || now < e.addAt) 0.0 else 1 - easeOut((now - e.addAt) / FLARE_FOR)

/** The badge's turn in degrees: −18 when a file is added, wobbling back to 0. */
internal fun badgeTurn(e: Events, now: Double, still: Boolean) =
    if (still || now < e.addAt) 0.0 else SHAKE_ANGLE * (1 - spring(now - e.addAt, SHAKE))

/** How far the ring pulse has gone (0 to 1), or null when there is none. */
internal fun ringPulse(e: Events, now: Double, still: Boolean): Double? {
    val t = (now - e.addAt) / PULSE_FOR
    return if (still || t < 0 || t >= 1) null else t
}

// ---- A row ----

/** The share of its slot (row and gap) a row takes: grows when added, collapses when it leaves. */
internal fun Upload.slot(now: Double, still: Boolean): Double {
    val grow = if (still) easeInOut((now - addedAt) / FADE) else spring(now - addedAt, ARRIVE)
    val collapse = 1 - easeInOut((now - leaveAt) / if (still) FADE else COLLAPSE)
    return grow * collapse
}

internal class CardLook(val alpha: Float, val dy: Float, val scale: Float)

/** The card's opacity, rise and size as it arrives and leaves. */
internal fun Upload.card(now: Double, still: Boolean): CardLook {
    if (still) {
        val a = clamp01((now - addedAt) / FADE) * (1 - clamp01((now - leaveAt) / FADE))
        return CardLook(a.toFloat(), 0f, 1f)
    }
    val p = easeOut((now - addedAt - CARD_DELAY) / CARD_IN)
    val q = easeInOut((now - leaveAt) / LEAVE_FOR)
    val scale = lerp(CARD_SCALE, 1.0, p) * lerp(1.0, LEAVE_SCALE, q)
    return CardLook((p * (1 - q)).toFloat(), (-CARD_RISE * (1 - p) - LEAVE_RISE * q).toFloat(), scale.toFloat())
}

/** The row's sideways shake after its upload fails, in dp. */
internal fun Upload.shake(now: Double, still: Boolean): Double {
    val t = now - failAt
    if (still || t < 0 || t > ROW_SHAKE_FOR) return 0.0
    return ROW_SHAKE * exp(-t * ROW_SHAKE_DECAY) * sin(2 * PI * t * ROW_SHAKE_HZ)
}

/** The current attempt's progress, 0 to 1. */
internal fun Upload.progress(now: Double): Double {
    if (retried) return if (now >= restartAt) ease(UploadCurve, (now - restartAt) / UPLOAD) else failPoint ?: 0.0
    val p = ease(UploadCurve, (now - addedAt) / UPLOAD)
    return failPoint?.let { min(p, it) } ?: p
}

/** The row's background fill: how far it reaches, how red, how opaque, and how bright its leading edge. */
internal class FillLook(val width: Float, val tint: Float, val alpha: Float, val edge: Float)

internal fun Upload.fill(now: Double): FillLook {
    if (failPoint == null || (retried && now >= restartAt) || now < failAt) {
        val a = (1 - clamp01((now - doneAt) / FILL_FADE)).toFloat()
        return FillLook(progress(now).toFloat(), 0f, a, a)
    }
    val t = now - failAt
    val red = clamp01(t / TINT_FOR).toFloat()
    return FillLook((failPoint * (1 - easeOut(t / DRAIN))).toFloat(), red, 1f, 1 - red)
}

/** The progress ring: its arc, size and opacity. */
internal class RingLook(val progress: Float, val scale: Float, val alpha: Float)

internal fun Upload.ring(now: Double, still: Boolean): RingLook {
    val fade = if (still) FADE else RING_FADE
    val alpha = 1 - clamp01((now - doneAt) / fade)
    val scale = when {
        retried && now >= restartAt -> if (still) 1.0 else easeOut((now - restartAt) / RING_BACK)
        now >= failAt -> if (still) 1.0 else 1 - clamp01((now - failAt) / RING_SHRINK)
        else -> 1.0
    }
    // Under reduced motion the ring fades out instead of shrinking.
    val gone = if (still && now >= failAt && !(retried && now >= restartAt)) clamp01((now - failAt) / FADE) else 0.0
    return RingLook(progress(now).toFloat(), scale.toFloat(), (alpha * (1 - gone)).toFloat())
}

/** The green disc and its check: size, opacity, and how much of the check is drawn. */
internal class DoneLook(val scale: Float, val alpha: Float, val trim: Float)

internal fun Upload.done(now: Double, still: Boolean): DoneLook {
    val t = now - doneAt
    if (t < 0) return DoneLook(0f, 0f, 0f)
    if (still) return DoneLook(1f, clamp01(t / FADE).toFloat(), 1f)
    return DoneLook(
        lerp(DISC_FROM, 1.0, spring(t, DISC)).toFloat(),
        clamp01(t / DISC_FADE).toFloat(),
        easeOut((t - CHECK_DELAY) / CHECK_DRAW).toFloat(),
    )
}

/** The "Upload failed" pill: how much of it is uncovered, its opacity and the size of its "!". */
internal class PillLook(val open: Float, val alpha: Float, val bang: Float)

internal fun Upload.pill(now: Double, still: Boolean): PillLook {
    val t = now - failAt
    if (t < 0) return PillLook(0f, 0f, 0f)
    val close = clamp01((now - retryAt) / PILL_CLOSE)
    if (still) return PillLook(1f, (clamp01(t / FADE) * (1 - close)).toFloat(), 1f)
    return PillLook((easeOut(t / PILL_OPEN) * (1 - close)).toFloat(), (1 - close).toFloat(), spring(t, BANG).toFloat())
}

/** "Retry": opacity, slide in dp, size while pressed, and the arrows' turn in degrees. */
internal class RetryLook(val alpha: Float, val dx: Float, val scale: Float, val spin: Float)

internal fun Upload.retryLook(now: Double, still: Boolean): RetryLook {
    val t = now - failAt
    if (t < 0) return RetryLook(0f, 0f, 1f, 0f)
    val shown = if (still) clamp01(t / FADE) else easeOut((t - RETRY_DELAY) / RETRY_IN)
    val out = clamp01((now - retryAt) / RETRY_OUT)
    return RetryLook(
        (shown * (1 - out)).toFloat(),
        if (still) 0f else (RETRY_SLIDE * (1 - shown)).toFloat(),
        if (still) 1f else pressScale(retryDownAt, retryLiftAt, now, RETRY_PRESS).toFloat(),
        if (still) 0f else (360 * easeOut((now - retryAt) / SPIN)).toFloat(),
    )
}

// ---- Continue ----

/** Where the sheen is across Continue (0 to 1), or null when it is not crossing. */
internal fun sheen(e: Events, now: Double, still: Boolean): Double? {
    if (still || !e.continueOn(now)) return null
    val on = e.flippedAt(now) { continueOn(it) } ?: return null
    val t = (now - on - SHEEN_DELAY) / SHEEN_FOR
    return if (t < 0 || t > 1) null else easeInOut(t)
}

/** How much of Continue's label is "Uploaded" (0 to 1). */
internal fun uploadedMix(e: Events, now: Double) =
    clamp01((now - e.continueAt) / LABEL_FADE) * (1 - clamp01((now - e.continueAt - UPLOADED_FOR) / LABEL_FADE))
