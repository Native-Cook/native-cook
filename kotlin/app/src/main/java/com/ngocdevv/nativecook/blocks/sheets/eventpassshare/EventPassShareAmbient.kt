package com.ngocdevv.nativecook.blocks.sheets.eventpassshare

import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/** The OKLCH aurora constants from `event-pass-share/ambient.ts`. */
internal object EventPassShareAurora {
    data class Light(
        val name: String,
        val x: Float,
        val y: Float,
        val radius: Float,
        val intensity: Float,
        val orbit: Float,
        val phase: Float,
        val l: Float,
        val c: Float,
        val h: Float,
    )

    val base = floatArrayOf(0.19f, 0.035f, 232f)
    val lights = arrayOf(
        Light("emerald", 112f, 214f, 150f, 0.95f, 26f, 0f, 0.63f, 0.15f, 162f),
        Light("teal", 338f, 352f, 128f, 0.85f, 22f, 2.1f, 0.66f, 0.115f, 203f),
        Light("gold", 372f, 226f, 60f, 0.62f, 14f, 4f, 0.79f, 0.13f, 66f),
        Light("violet", 52f, 572f, 150f, 0.85f, 24f, 1f, 0.5f, 0.17f, 290f),
        Light("rose", 356f, 652f, 104f, 0.6f, 18f, 3.3f, 0.58f, 0.16f, 345f),
    )
}

/** Exact ambient tuning values from the React Native source. */
internal object EventPassShareAmbient {
    const val period = LOOP
    const val warp = 16f
    const val warpScale = 0.0075f
    const val warpCircle = 0.85f
    const val parallax = 2.2f
    const val hueY = 0.02f
    const val hueX = 0.015f
    const val omega = 7f
    const val sheetSeeThrough = 0.2f
    const val sheetTopBleed = 0.22f
    const val sheetBleedDepth = 38f
    const val stageGainTop = 1.25f
    const val stageGainMid = 0.7f
    const val stageGainBottom = 0.38f
    const val stageFadeFrom = 236f
    const val stageFadeMid = 330f
    const val stageFadeTo = 690f
    const val floorReflection = 0.3f
    const val floorDepth = 70f
    const val spillRest = 0.12f
    const val spillReach = 20f
    const val spillStrength = 0.55f
    const val spillShift = 1.6f
}

/** Reusable uniform values consumed by the ambient shaders. */
internal class EventPassShareAmbientFrame(
    var angle: Float = 0f,
    var tiltX: Float = 0f,
    var tiltY: Float = 0f,
    var glow: Float = 0f,
) {
    fun set(angle: Float, tiltX: Float, tiltY: Float, glow: Float): EventPassShareAmbientFrame {
        this.angle = angle
        this.tiltX = tiltX
        this.tiltY = tiltY
        this.glow = glow
        return this
    }
}

/** Mutable, allocation-free critically damped follower state. */
internal class EventPassShareAmbientFollower {
    var x = 0f
    var vx = 0f
    var y = 0f
    var vy = 0f
    var glow = 0f
    var vGlow = 0f

    fun reset() {
        x = 0f
        vx = 0f
        y = 0f
        vy = 0f
        glow = 0f
        vGlow = 0f
    }

    /** Writes a shader frame without allocating; [output] may be held by the caller. */
    fun writeFrame(angle: Float, output: EventPassShareAmbientFrame): EventPassShareAmbientFrame =
        output.set(angle, x, y, glow)

    fun frame(angle: Float): EventPassShareAmbientFrame = EventPassShareAmbientFrame(angle, x, y, glow)

    fun stepInPlace(targetX: Float, targetY: Float, targetGlow: Float, dt: Float) {
        stepEventPassShareAmbientFollower(this, targetX, targetY, targetGlow, dt)
    }
}

/** Values matching `spillUniform`; [output] is reusable to avoid per-frame garbage. */
internal fun eventPassShareSpillUniform(
    tiltX: Double,
    tiltY: Double,
    flipAngle: Double,
    output: FloatArray = FloatArray(3),
): FloatArray {
    require(output.size >= 3)
    val turn = min(90.0, maxOf(0.0, if (flipAngle <= 90.0) flipAngle else 180.0 - flipAngle))
    val shadeY = tiltY + turn * 0.2
    val tilt = min(1.0, hypot(tiltX / 6.0, tiltY / 7.5))
    val lit = min(1.0, tilt + sin(turn * PI / 180.0) * 0.8)
    output[0] = tiltX.toFloat()
    output[1] = shadeY.toFloat()
    output[2] = (lit * lit * (3.0 - 2.0 * lit)).toFloat()
    return output
}

/** One deterministic revolution per [EventPassShareAmbient.period], including for negative times. */
internal fun ambientAngle(timeSeconds: Double): Float {
    val unit = (timeSeconds % EventPassShareAmbient.period) / EventPassShareAmbient.period
    return ((if (unit < 0.0) unit + 1.0 else unit) * PI * 2.0).toFloat()
}

/**
 * In-place port of `stepAmbientFollower`: substeps cap integration at 1/240 s and elapsed time at
 * 0.1 s, preserving the React Native critically damped response at variable frame rates.
 */
internal fun stepEventPassShareAmbientFollower(
    state: EventPassShareAmbientFollower,
    targetX: Float,
    targetY: Float,
    targetGlow: Float,
    dt: Float,
) {
    val omega = EventPassShareAmbient.omega
    var left = dt.coerceIn(0f, 0.1f)
    while (left > 0f) {
        val h = min(left, 1f / 240f)
        left -= h
        state.vx += (omega * omega * (targetX - state.x) - 2f * omega * state.vx) * h
        state.x += state.vx * h
        state.vy += (omega * omega * (targetY - state.y) - 2f * omega * state.vy) * h
        state.y += state.vy * h
        state.vGlow += (omega * omega * (targetGlow - state.glow) - 2f * omega * state.vGlow) * h
        state.glow += state.vGlow * h
    }
}
