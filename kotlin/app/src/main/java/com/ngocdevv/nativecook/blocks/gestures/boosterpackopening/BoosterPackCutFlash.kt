package com.ngocdevv.nativecook.blocks.gestures.boosterpackopening

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.SweepGradient

// The reference's ring is a single-frame event at f124. The horizontal cut stays
// visible afterward, narrowing from 22 px at f125 to a fine line at f133.
private val BEAM_TIMES = doubleArrayOf(0.0, 16.667, 33.333, 50.0, 66.667, 100.0, 150.0, 220.0)
private val BEAM_WIDTHS = doubleArrayOf(0.083, 0.056, 0.04, 0.038, 0.029, 0.012, 0.0045, 0.0045)
private val SPECTRUM =
    intArrayOf(
        0xFFA9FFFD.toInt(),
        0xFFEFC8FF.toInt(),
        0xFFF9DCFF.toInt(),
        0xFF94FFFF.toInt(),
        0xFFB7FFFF.toInt(),
        0xFFF8BCFF.toInt(),
        0xFFFF87ED.toInt(),
        0xFFA9FFFD.toInt(),
    )
private val SPECTRUM_POSITIONS = floatArrayOf(0f, 0.12f, 0.25f, 0.4f, 0.5f, 0.69f, 0.84f, 1f)

private val RING_OPACITY_TIMES = doubleArrayOf(0.0, 8.333, 16.667)
private val RING_OPACITY = doubleArrayOf(1.0, 0.38, 0.0)
private val CORE_BLUR_TIMES = doubleArrayOf(0.0, 16.667, 66.667)
private val CORE_BLUR = doubleArrayOf(0.012, 0.002, 0.0008)
private val EDGE_BLUR_TIMES = doubleArrayOf(0.0, 16.667)
private val EDGE_BLUR = doubleArrayOf(0.016, 0.002)
private val BEAM_OPACITY_TIMES = doubleArrayOf(0.0, 150.0, 183.333, 220.0)
private val BEAM_OPACITY = doubleArrayOf(1.0, 1.0, 0.55, 0.0)
private val GLOW_OPACITY_TIMES = doubleArrayOf(0.0, 16.667, 66.667, 150.0)
private val GLOW_OPACITY = doubleArrayOf(1.0, 0.4, 0.18, 0.0)
private val EDGE_OPACITY_TIMES = doubleArrayOf(0.0, 66.667, 150.0)
private val EDGE_OPACITY = doubleArrayOf(1.0, 0.55, 0.0)
private val WARM_OPACITY_TIMES = doubleArrayOf(0.0, 16.667, 50.0, 66.667)
private val WARM_OPACITY = doubleArrayOf(1.0, 0.8, 0.15, 0.0)

private val BEAM_EDGE_COLORS =
    intArrayOf(0xFF67FFF5.toInt(), 0xFFD5FFF2.toInt(), 0xFFFFFFFF.toInt(), 0xFFDCFFFF.toInt(), 0xFF87F9FF.toInt())
private val BEAM_WARM_COLORS =
    intArrayOf(0xFF67FFF5.toInt(), 0xFFD5FFF2.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFFFB3.toInt(), 0xFFFFE66D.toInt())
private val BEAM_EDGE_POSITIONS = floatArrayOf(0f, 0.18f, 0.5f, 0.8f, 1f)

private val FLARE_COLORS =
    intArrayOf(
        rgba(255, 89, 191, 0.0),
        rgba(255, 148, 106, 0.32),
        rgba(255, 220, 133, 0.92),
        0xFFFFFFFF.toInt(),
        0xFFFFFFFF.toInt(),
        rgba(255, 255, 212, 0.98),
        rgba(255, 251, 99, 0.93),
        rgba(116, 255, 167, 0.82),
        rgba(97, 223, 255, 0.74),
        rgba(112, 135, 255, 0.0),
    )
private val FLARE_POSITIONS = floatArrayOf(0f, 0.12f, 0.25f, 0.36f, 0.7f, 0.76f, 0.82f, 0.88f, 0.95f, 1f)

private fun makeRing(
    centerX: Double,
    centerY: Double,
    radiusX: Double,
    topRadius: Double,
    bottomRadius: Double,
): Path {
    val path = PathBuilder()
    val arc = 0.55228475
    path.moveTo(centerX - radiusX, centerY)
    path.cubicTo(
        centerX - radiusX, centerY - topRadius * arc,
        centerX - radiusX * arc, centerY - topRadius,
        centerX, centerY - topRadius,
    )
    path.cubicTo(
        centerX + radiusX * arc, centerY - topRadius,
        centerX + radiusX, centerY - topRadius * arc,
        centerX + radiusX, centerY,
    )
    path.cubicTo(
        centerX + radiusX, centerY + bottomRadius * arc,
        centerX + radiusX * arc, centerY + bottomRadius,
        centerX, centerY + bottomRadius,
    )
    path.cubicTo(
        centerX - radiusX * arc, centerY + bottomRadius,
        centerX - radiusX, centerY + bottomRadius * arc,
        centerX - radiusX, centerY,
    )
    path.close()
    return path.build()
}

private fun makeRingRibbon(width: Double, height: Double, cutY: Double): Path {
    // At f124 the far rim is only a thin arc above the logo; the near and side
    // rims spread into a much wider flare. A constant-width ellipse hides that
    // depth cue and washes out the printed cap.
    val path = PathBuilder()
    path.moveTo(-width * 0.028, cutY)
    path.cubicTo(
        -width * 0.026, cutY - height * 0.064,
        width * 0.19, cutY - height * 0.108, width * 0.498, cutY - height * 0.108,
    )
    path.cubicTo(
        width * 0.81, cutY - height * 0.108,
        width * 1.026, cutY - height * 0.06, width * 1.028, cutY + height * 0.002,
    )
    path.cubicTo(
        width * 1.026, cutY + height * 0.054,
        width * 0.756, cutY + height * 0.076, width * 0.508, cutY + height * 0.07,
    )
    path.cubicTo(
        width * 0.254, cutY + height * 0.07,
        -width * 0.026, cutY + height * 0.051, -width * 0.028, cutY,
    )
    path.close()
    path.moveTo(width * 0.069, cutY)
    path.cubicTo(
        width * 0.073, cutY + height * 0.037,
        width * 0.282, cutY + height * 0.054, width * 0.506, cutY + height * 0.053,
    )
    path.cubicTo(
        width * 0.738, cutY + height * 0.058,
        width * 0.93, cutY + height * 0.039, width * 0.931, cutY + height * 0.001,
    )
    path.cubicTo(
        width * 0.929, cutY - height * 0.052,
        width * 0.793, cutY - height * 0.102, width * 0.499, cutY - height * 0.102,
    )
    path.cubicTo(
        width * 0.216, cutY - height * 0.102,
        width * 0.074, cutY - height * 0.057, width * 0.069, cutY,
    )
    path.close()
    return path.build()
}

private fun makeRefractedFlare(width: Double, height: Double, cutY: Double): Path {
    // Light refracts through the torn packet in an uneven ribbon. Its lower
    // edge carries the warm-to-cyan diffraction visible around the character.
    val path = PathBuilder()
    path.moveTo(width * 0.124, cutY - height * 0.012)
    path.cubicTo(
        width * 0.24, cutY - height * 0.057,
        width * 0.43, cutY - height * 0.059,
        width * 0.54, cutY - height * 0.052,
    )
    path.cubicTo(
        width * 0.72, cutY - height * 0.055,
        width * 0.8, cutY - height * 0.027,
        width * 0.87, cutY - height * 0.012,
    )
    path.lineTo(width * 0.862, cutY + height * 0.043)
    path.lineTo(width * 0.834, cutY + height * 0.07)
    path.lineTo(width * 0.816, cutY + height * 0.032)
    path.lineTo(width * 0.795, cutY + height * 0.043)
    path.lineTo(width * 0.774, cutY + height * 0.073)
    path.lineTo(width * 0.742, cutY + height * 0.08)
    path.lineTo(width * 0.725, cutY + height * 0.065)
    path.lineTo(width * 0.695, cutY + height * 0.052)
    path.lineTo(width * 0.653, cutY + height * 0.029)
    path.cubicTo(
        width * 0.625, cutY + height * 0.053,
        width * 0.606, cutY + height * 0.068,
        width * 0.576, cutY + height * 0.072,
    )
    path.lineTo(width * 0.554, cutY + height * 0.089)
    path.cubicTo(
        width * 0.509, cutY + height * 0.108,
        width * 0.459, cutY + height * 0.108,
        width * 0.44, cutY + height * 0.089,
    )
    path.lineTo(width * 0.431, cutY + height * 0.076)
    path.lineTo(width * 0.397, cutY + height * 0.083)
    path.lineTo(width * 0.352, cutY + height * 0.075)
    path.lineTo(width * 0.31, cutY + height * 0.072)
    path.lineTo(width * 0.286, cutY + height * 0.077)
    path.lineTo(width * 0.236, cutY + height * 0.064)
    path.lineTo(width * 0.218, cutY + height * 0.059)
    path.lineTo(width * 0.168, cutY + height * 0.057)
    path.lineTo(width * 0.137, cutY + height * 0.041)
    path.close()
    return path.build()
}

/**
 * The optical layer of the cut: an asymmetric ring for one frame, a soft burst refracted through
 * the packet, and the narrow beam along the tear that outlives both. All of it is a function of
 * the time since the cut.
 */
internal class CutFlash(
    private val width: Double,
    private val height: Double,
    seamY: Double,
) {
    private val cutY = seamY - height * 0.003
    private val outerRing = makeRing(width * 0.5, cutY, width * 0.528, height * 0.105, height * 0.088)
    private val mainRing = makeRingRibbon(width, height, cutY)
    private val refractedFlare = makeRefractedFlare(width, height, cutY)
    private val spectrum = SweepGradient((width * 0.5).toFloat(), cutY.toFloat(), SPECTRUM, SPECTRUM_POSITIONS)
    private val flareGradient =
        linearGradient(0.0, cutY - height * 0.061, 0.0, cutY + height * 0.108, FLARE_COLORS, FLARE_POSITIONS)
    private val beam = Path()
    private val oval = RectF()

    fun draw(
        canvas: Canvas,
        brush: Brush,
        time: Double,
        committed: Double,
        packetMask: Path,
        reducedMotion: Boolean,
    ) {
        val live = clamp(committed, 0.0, 1.0)
        if (live <= 0 || time >= 220) return

        val ringOpacity =
            live * (if (reducedMotion) 0.18 else 1.0) * interpolateKeyframes(time, RING_OPACITY_TIMES, RING_OPACITY)
        val beamWidth = width * (if (reducedMotion) 0.35 else 1.0) * interpolateKeyframes(time, BEAM_TIMES, BEAM_WIDTHS)
        val beamEdgeWidth = beamWidth + width * 0.008
        val beamGlowWidth = beamWidth + width * 0.022
        val beamBloomWidth = beamWidth + width * 0.062
        val beamCoreBlur = width * interpolateKeyframes(time, CORE_BLUR_TIMES, CORE_BLUR)
        val beamEdgeBlur = width * interpolateKeyframes(time, EDGE_BLUR_TIMES, EDGE_BLUR)
        val beamBloomOpacity = if (reducedMotion) 0.0 else ringOpacity * 0.92
        val beamOpacity =
            live * (if (reducedMotion) 0.45 else 1.0) * interpolateKeyframes(time, BEAM_OPACITY_TIMES, BEAM_OPACITY)
        val glowOpacity =
            live * (if (reducedMotion) 0.08 else 0.75) * interpolateKeyframes(time, GLOW_OPACITY_TIMES, GLOW_OPACITY)
        val beamEdgeOpacity = beamOpacity * interpolateKeyframes(time, EDGE_OPACITY_TIMES, EDGE_OPACITY)
        val warmEdgeOpacity = beamEdgeOpacity * interpolateKeyframes(time, WARM_OPACITY_TIMES, WARM_OPACITY)

        val inset = if (reducedMotion) 0.0 else width * 0.11 * (1 - clamp(time / 16.667, 0.0, 1.0))
        beam.rewind()
        beam.moveTo(inset.toFloat(), cutY.toFloat())
        beam.lineTo((width - inset).toFloat(), cutY.toFloat())

        canvas.save()
        canvas.clipRect(0f, (cutY - height * 0.14).toFloat(), width.toFloat(), (cutY + height * 0.14).toFloat())

        if (ringOpacity > 0.001) {
            val travel = if (reducedMotion) 0.0 else clamp(time / 16.667, 0.0, 1.0)
            canvas.save()
            canvas.translate((width * 0.5).toFloat(), cutY.toFloat())
            canvas.translate(0f, (-height * 0.002 * travel).toFloat())
            canvas.scale((1 + travel * 0.06).toFloat(), (1 + travel * 0.12).toFloat())
            canvas.translate((-width * 0.5).toFloat(), (-cutY).toFloat())
            if (reducedMotion) {
                oval.set(
                    (width * 0.06).toFloat(),
                    (cutY - height * 0.078).toFloat(),
                    (width * 0.94).toFloat(),
                    (cutY + height * 0.078).toFloat(),
                )
                canvas.drawOval(
                    oval,
                    brush.stroke(width * 0.012, 0xFFB6FFFA.toInt(), ringOpacity, blur = width * 0.009),
                )
            } else {
                // The packet occludes the rear arc; refracted light below is a
                // separate bloom, so the front never becomes a white hoop.
                canvas.save()
                canvas.clipOutPath(packetMask)
                canvas.drawPath(
                    outerRing,
                    brush.stroke(width * 0.028, opacity = ringOpacity, blur = width * 0.012, shader = spectrum),
                )
                canvas.drawPath(
                    outerRing,
                    brush.stroke(width * 0.011, opacity = ringOpacity, blur = width * 0.006, shader = spectrum),
                )
                canvas.drawPath(mainRing, brush.fill(0xFFE9FFF8.toInt(), ringOpacity, blur = width * 0.009))
                canvas.drawPath(mainRing, brush.fill(0xFFFFFFFF.toInt(), ringOpacity, blur = width * 0.004))
                canvas.restore()
                oval.set(
                    (width * 0.23).toFloat(),
                    (cutY - height * 0.09).toFloat(),
                    (width * 0.77).toFloat(),
                    (cutY - height * 0.021).toFloat(),
                )
                canvas.drawOval(
                    oval,
                    brush.fill(0xFFFFFFBC.toInt(), ringOpacity * 0.87, blur = width * 0.04, blend = BLEND_SCREEN),
                )
                canvas.save()
                canvas.translate(0f, (-height * 0.008).toFloat())
                canvas.drawPath(refractedFlare, brush.fill(0xFFFFFFF8.toInt(), ringOpacity * 0.9, blur = width * 0.014))
                canvas.restore()
                canvas.drawPath(
                    refractedFlare,
                    brush.fill(opacity = ringOpacity, blur = width * 0.012, shader = flareGradient),
                )
            }
            canvas.restore()
        }

        canvas.drawPath(beam, brush.stroke(beamGlowWidth, 0xFF83FFFA.toInt(), glowOpacity, blur = width * 0.016))
        canvas.drawPath(
            beam,
            brush.stroke(beamBloomWidth, 0xFFFFFFED.toInt(), beamBloomOpacity, blur = width * 0.027, cap = Paint.Cap.ROUND),
        )
        if (beamEdgeOpacity > 0.001) {
            val top = cutY - beamEdgeWidth * 0.5
            val bottom = cutY + beamEdgeWidth * 0.5
            canvas.drawPath(
                beam,
                brush.stroke(
                    beamEdgeWidth,
                    opacity = beamEdgeOpacity,
                    blur = beamEdgeBlur,
                    shader = linearGradient(0.0, top, 0.0, bottom, BEAM_EDGE_COLORS, BEAM_EDGE_POSITIONS),
                    cap = Paint.Cap.ROUND,
                ),
            )
            canvas.drawPath(
                beam,
                brush.stroke(
                    beamEdgeWidth,
                    opacity = warmEdgeOpacity,
                    blur = beamEdgeBlur,
                    shader = linearGradient(0.0, top, 0.0, bottom, BEAM_WARM_COLORS, BEAM_EDGE_POSITIONS),
                    cap = Paint.Cap.ROUND,
                ),
            )
        }
        canvas.drawPath(
            beam,
            brush.stroke(beamWidth, 0xFFFFFFFF.toInt(), beamOpacity, blur = beamCoreBlur, cap = Paint.Cap.ROUND),
        )
        canvas.restore()
    }
}
