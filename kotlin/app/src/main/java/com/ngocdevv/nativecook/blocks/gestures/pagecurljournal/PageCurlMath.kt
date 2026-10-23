package com.ngocdevv.nativecook.blocks.gestures.pagecurljournal

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

// The page turn's maths: where the grabbed point may go, and how far the sheet has turned.

/**
 * The sheet is bound at the spine (x = 0). A spine point must stay on the unfolded side of the fold, i.e. no
 * farther from the tip than from the grab point; otherwise the page would tear. Project the tip into both
 * discs. The result is written to [out] as (x, y).
 */
internal fun clampTip(gx: Float, gy: Float, tx: Float, ty: Float, height: Float, out: FloatArray) {
    var x = tx
    var y = ty
    for (pass in 0 until 3) {
        for (k in 0 until 2) {
            val sy = if (k == 0) 0f else height
            val r = hypot(gx, gy - sy)
            val d = hypot(x, y - sy)
            if (d > r && d > 0f) {
                x = x * r / d
                y = sy + (y - sy) * r / d
            }
        }
    }
    out[0] = x
    out[1] = y
}

/** 0 = flat, 1 = turned all the way over the spine. */
internal fun turnProgress(gx: Float, gy: Float, tx: Float, ty: Float): Float =
    min(1f, hypot(gx - tx, gy - ty) / max(1f, 2f * gx))

/** The roll is small while the sheet first lifts and while it lands flat: a sheet bends before it folds. */
internal fun rollRamp(d: Float, full: Float, rampIn: Float, rampOut: Float): Float =
    max(0f, min(1f, min(d / rampIn, (full - d) / rampOut)))

/** The scripted finger: a quadratic path, eased so a flick is still accelerating when it lifts. Written to [out]. */
internal fun strokePoint(stroke: ScriptStroke, u: Float, flick: Boolean, out: FloatArray) {
    val e = if (flick) u * u else u * u * (3f - 2f * u)
    val a = (1f - e) * (1f - e)
    val b = 2f * e * (1f - e)
    val c = e * e
    out[0] = a * stroke.from[0] + b * stroke.via[0] + c * stroke.to[0]
    out[1] = a * stroke.from[1] + b * stroke.via[1] + c * stroke.to[1]
}
