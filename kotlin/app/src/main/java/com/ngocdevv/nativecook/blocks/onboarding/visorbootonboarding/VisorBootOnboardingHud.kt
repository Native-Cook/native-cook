package com.ngocdevv.nativecook.blocks.onboarding.visorbootonboarding

import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

/**
 * The three 2D modules that live on the helmet (VisorHud.tsx), drawn over the 3D view with the stage camera's own
 * projection: the visor HUD (a perspective-correct quad on the visor plate, drawn in canonical units through the
 * homography that `Matrix.setPolyToPoly` builds from the four projected corners), the radar round the ear sensor,
 * and the voice module (rings at the mouthpiece, a waveform under the helmet and the transcript under it, which
 * React Native draws as VisorVoiceCaption). Everything is in dp; the caller scales the canvas by the density.
 */
internal class VisorBootHudPainter(context: Context) {
    private val mono: Typeface = try {
        Typeface.createFromAsset(context.assets, "$VISOR_BOOT_ASSET_DIR/${VISOR_BOOT_ASSET_DIR}_IBMPlexMono-Medium.ttf")
    } catch (_: RuntimeException) {
        Typeface.MONOSPACE
    }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val f8 = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = mono; textSize = 8.5f }
    private val f11 = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = mono; textSize = 13f }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = mono; textSize = 11f }
    private val caption = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = if (Build.VERSION.SDK_INT >= 28) Typeface.create(Typeface.DEFAULT, 500, false) else Typeface.DEFAULT
        textSize = 17f
        color = VisorBootMotion.INK.toInt()
        textAlign = Paint.Align.CENTER
    }
    private val path = Path()
    private val matrix = Matrix()
    private val src = floatArrayOf(0f, 0f, HUD_W, 0f, HUD_W, HUD_H, 0f, HUD_H)
    private val dst = FloatArray(8)
    private val o = DoubleArray(3)
    private val o2 = DoubleArray(3)
    private val oc = DoubleArray(3)

    private fun amber(p: Paint, a: Double) {
        p.shader = null
        p.color = android.graphics.Color.argb((VisorBootMotion.clamp01(a) * 255).toInt(), 255, 194, 122)
    }

    fun draw(canvas: Canvas, w: Double, h: Double, s: VisorBootState, limitY: Double) {
        val m = s.m
        val c = VisorBootMotion.cameraAt(s)
        val b = VisorBootMotion.basis(c, w)
        val lift = s.lift
        val hudVis = VisorBootMotion.blend(VisorBootMotion.filmHud(s.t), VisorBootMotion.pageWeight(s.page, 1), m) *
            VisorBootMotion.hudFacing(c, VisorBootMotion.HELMET_Y + lift)
        val boot = VisorBootMotion.blend(VisorBootMotion.filmBoot(s.t), 1.0, m)
        val radarVis = VisorBootMotion.blend(VisorBootMotion.filmRadar(s.t), VisorBootMotion.pageWeight(s.page, 2), m)
        val voiceVis = VisorBootMotion.blend(VisorBootMotion.filmVoice(s.t), VisorBootMotion.pageWeight(s.page, 3), m)
        val run = s.run
        paint.style = Paint.Style.FILL

        // ---- HUD on the visor
        if (hudVis > 0.004) {
            var ok = true
            for (k in 0 until 4) {
                if (!VisorBootMotion.project(c, b, w, h, VisorBootMotion.helmetPoint(VisorBootMotion.hudQuad[k], lift), o)) ok = false
                dst[k * 2] = o[0].toFloat()
                dst[k * 2 + 1] = o[1].toFloat()
            }
            if (ok) {
                // a soft amber bloom on the visor plate under the glass
                val gx = (dst[0] + dst[2] + dst[4] + dst[6]) / 4
                val gy = (dst[1] + dst[3] + dst[5] + dst[7]) / 4
                val gr = max(1f, hypot(dst[2] - dst[0], dst[3] - dst[1]) * 0.7f)
                paint.color = android.graphics.Color.WHITE
                paint.shader = RadialGradient(
                    gx, gy, gr,
                    android.graphics.Color.argb((0.16 * hudVis * 255).toInt(), 255, 194, 122),
                    android.graphics.Color.argb(0, 255, 194, 122),
                    Shader.TileMode.CLAMP,
                )
                canvas.drawCircle(gx, gy, gr, paint)
                paint.shader = null
                if (matrix.setPolyToPoly(src, 0, dst, 0, 4)) {
                    canvas.save()
                    canvas.concat(matrix)
                    drawHud(canvas, hudVis, boot, run)
                    canvas.restore()
                }
            }
        }

        // ---- radar round the ear sensor
        if (radarVis > 0.004) {
            val grow = 0.72 + 0.28 * VisorBootMotion.easeOutCubic(radarVis)
            val cw = VisorBootMotion.helmetPoint(VisorBootMotion.portCenter, lift)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1f
            for (ring in 1..3) {
                val r = VisorBootMotion.RADAR_R * ring * grow / 3
                path.reset()
                var first = true
                for (k in 0..64) {
                    if (!VisorBootMotion.project(c, b, w, h, radarPoint(cw, k / 64.0 * PI * 2, r), o)) continue
                    if (first) path.moveTo(o[0].toFloat(), o[1].toFloat()) else path.lineTo(o[0].toFloat(), o[1].toFloat())
                    first = false
                }
                amber(paint, (if (ring == 3) 0.55 else 0.28) * radarVis)
                canvas.drawPath(path, paint)
            }
            amber(paint, 0.5 * radarVis)
            val big = VisorBootMotion.RADAR_R * grow
            for (k in 0 until 12) {
                val ang = k / 12.0 * PI * 2
                if (VisorBootMotion.project(c, b, w, h, radarPoint(cw, ang, big), o) &&
                    VisorBootMotion.project(c, b, w, h, radarPoint(cw, ang, big * 1.07), o2)
                ) {
                    canvas.drawLine(o[0].toFloat(), o[1].toFloat(), o2[0].toFloat(), o2[1].toFloat(), paint)
                }
            }
            // the sweep: a fading wedge behind a bright leading edge
            val a = VisorBootMotion.radarAngle(run)
            paint.style = Paint.Style.FILL
            val okC = VisorBootMotion.project(c, b, w, h, cw, oc)
            val sl = 18
            for (k in 0 until sl) {
                val a0 = a - 1.1 * (1 - k.toDouble() / sl)
                val a1 = a - 1.1 * (1 - (k + 1).toDouble() / sl)
                if (!okC || !VisorBootMotion.project(c, b, w, h, radarPoint(cw, a0, big), o) ||
                    !VisorBootMotion.project(c, b, w, h, radarPoint(cw, a1, big), o2)
                ) continue
                path.reset()
                path.moveTo(oc[0].toFloat(), oc[1].toFloat())
                path.lineTo(o[0].toFloat(), o[1].toFloat())
                path.lineTo(o2[0].toFloat(), o2[1].toFloat())
                path.close()
                amber(paint, 0.22 * ((k + 1).toDouble() / sl).pow(2) * radarVis)
                canvas.drawPath(path, paint)
            }
            if (okC && VisorBootMotion.project(c, b, w, h, radarPoint(cw, a, big), o)) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1.4f
                amber(paint, 0.9 * radarVis)
                canvas.drawLine(oc[0].toFloat(), oc[1].toFloat(), o[0].toFloat(), o[1].toFloat(), paint)
            }
            // blips flare as the sweep passes them, then fade
            paint.style = Paint.Style.FILL
            for ((ang, rs) in BLIPS) {
                var since = (a - ang) % (PI * 2)
                if (since < 0) since += PI * 2
                val age = since / (PI * 2) * 2.1
                val glow = exp(-age * 2.2)
                if (!VisorBootMotion.project(c, b, w, h, radarPoint(cw, ang, big * rs), o)) continue
                val rad = max(1.5, 0.035 * b.focal / o[2]).toFloat()
                amber(paint, (0.25 + 0.75 * glow) * radarVis)
                canvas.drawCircle(o[0].toFloat(), o[1].toFloat(), rad, paint)
                amber(paint, 0.25 * glow * radarVis)
                canvas.drawCircle(o[0].toFloat(), o[1].toFloat(), rad * 3.2f, paint)
            }
            if (VisorBootMotion.project(c, b, w, h, radarPoint(cw, PI * 0.25, big * 1.12), o)) {
                amber(label, 0.85 * radarVis)
                canvas.drawText("3 nearby", (o[0] + 6).toFloat(), (o[1] - 4).toFloat(), label)
                amber(label, 0.6 * radarVis)
                canvas.drawText("closest 11.6 m", (o[0] + 6).toFloat(), (o[1] + 10).toFloat(), label)
            }
        }

        // ---- voice module at the mouthpiece
        if (voiceVis > 0.004) {
            val mw = VisorBootMotion.helmetPoint(VisorBootMotion.mouth, lift)
            val okM = VisorBootMotion.project(c, b, w, h, mw, oc)
            val ax = oc[0]
            val ay = oc[1]
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.2f
            val ma = VisorBootMotion.mouthAxisA
            val mb = VisorBootMotion.mouthAxisB
            for (k in 0 until 3) {
                val ph = ((run / 1.6 + k / 3.0) % 1.0 + 1.0) % 1.0
                val r = 0.1 + 0.55 * ph
                path.reset()
                var first = true
                for (sIdx in 0..40) {
                    val ang = sIdx / 40.0 * PI * 2
                    val ca = cos(ang) * r
                    val sa = sin(ang) * r
                    val pt = VisorBootV3(
                        mw.x + ca * ma.x + sa * mb.x,
                        mw.y + ca * ma.y + sa * mb.y,
                        mw.z + ca * ma.z + sa * mb.z,
                    )
                    if (!VisorBootMotion.project(c, b, w, h, pt, o)) continue
                    if (first) path.moveTo(o[0].toFloat(), o[1].toFloat()) else path.lineTo(o[0].toFloat(), o[1].toFloat())
                    first = false
                }
                amber(paint, 0.6 * (1 - ph) * voiceVis)
                canvas.drawPath(path, paint)
            }
            if (okM) {
                val wy = VisorBootMotion.voiceWaveY(ay, h, limitY)
                val bw = 3.0
                val gap = 3.2
                val total = BARS * bw + (BARS - 1) * gap
                val x0 = w / 2 - total / 2
                val rise = VisorBootMotion.easeOutCubic(voiceVis)
                paint.strokeWidth = 1f
                amber(paint, 0.45 * voiceVis)
                canvas.drawLine(ax.toFloat(), (ay + 6).toFloat(), ax.toFloat(), (ay + 6 + (wy - 26 - ay - 6) * rise).toFloat(), paint)
                paint.style = Paint.Style.FILL
                amber(paint, 0.9 * voiceVis)
                canvas.drawCircle(ax.toFloat(), ay.toFloat(), 2.6f, paint)
                for (i in 0 until BARS) {
                    val v = VisorBootMotion.voiceBar(i, BARS, run)
                    val bh = 3 + 30 * v * rise
                    val x = x0 + i * (bw + gap)
                    amber(paint, (0.45 + 0.5 * v) * voiceVis)
                    canvas.drawRoundRect(x.toFloat(), (wy - bh / 2).toFloat(), (x + bw).toFloat(), (wy + bh / 2).toFloat(), 1.5f, 1.5f, paint)
                }
                // the transcript (VisorVoiceCaption): a 17 pt line whose top sits 24 pt under the waveform's centre
                val typed = VisorBootMotion.blend(VisorBootMotion.filmTranscript(s.t), 1.0, m)
                val n = ceil(VisorBootMotion.clamp01(typed) * TRANSCRIPT.length).toInt()
                if (n > 0) {
                    caption.alpha = (VisorBootMotion.clamp01(voiceVis) * 255).toInt()
                    val fm = caption.fontMetrics
                    // React Native's line box: the line's height is the font's, the text centred in it
                    val top = wy + 24
                    val baseline = top - fm.ascent
                    val part = TRANSCRIPT.substring(0, n)
                    canvas.drawText(part, (w / 2).toFloat(), baseline.toFloat(), caption)
                }
            }
        }
    }

    /** A point of the radar's plane: angle ang (rad), radius r, round the world-space centre cw. */
    private fun radarPoint(cw: VisorBootV3, ang: Double, r: Double): VisorBootV3 {
        val ca = cos(ang) * r
        val sa = sin(ang) * r
        val a = VisorBootMotion.portAxisA
        val b = VisorBootMotion.portAxisB
        return VisorBootV3(cw.x + ca * a.x + sa * b.x, cw.y + ca * a.y + sa * b.y, cw.z + ca * a.z + sa * b.z)
    }

    /** The HUD in its own canonical units (HUD_W x HUD_H), drawn under the visor homography. */
    private fun drawHud(canvas: Canvas, vis: Double, boot: Double, run: Double) {
        val p = paint
        p.style = Paint.Style.FILL
        amber(p, 0.045 * vis)
        canvas.drawRect(0f, 0f, HUD_W, HUD_H, p)
        // scanlines: strong while it boots, then a whisper
        p.style = Paint.Style.STROKE
        p.strokeWidth = 0.6f
        amber(p, (0.05 + 0.08 * (1 - VisorBootMotion.seg(boot, 0.25, 0.45))) * vis)
        var y = 2f
        while (y < HUD_H) {
            canvas.drawLine(0f, y, HUD_W, y, p)
            y += 3f
        }
        // the boot sweep: a bright band running down the glass
        val sweep = VisorBootMotion.seg(boot, 0.0, 0.3)
        if (sweep > 0 && sweep < 1) {
            val sy = (-12 + sweep * (HUD_H + 24)).toFloat()
            p.style = Paint.Style.FILL
            for (k in 0 until 6) {
                amber(p, (0.08 + 0.1 * k) * vis)
                canvas.drawRect(0f, sy - 12 + k * 2, HUD_W, sy - 12 + k * 2 + 2, p)
            }
            amber(p, 0.9 * vis)
            canvas.drawRect(0f, sy, HUD_W, sy + 1.2f, p)
        }
        // corner brackets drawing in
        val br = (14 * VisorBootMotion.easeOutCubic(VisorBootMotion.seg(boot, 0.1, 0.35))).toFloat()
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.4f
        p.strokeCap = Paint.Cap.SQUARE
        amber(p, 0.85 * vis)
        if (br > 0.5f) {
            val i = 4f
            canvas.drawLine(i, i, i + br, i, p)
            canvas.drawLine(i, i, i, i + br * 0.7f, p)
            canvas.drawLine(HUD_W - i, i, HUD_W - i - br, i, p)
            canvas.drawLine(HUD_W - i, i, HUD_W - i, i + br * 0.7f, p)
            canvas.drawLine(i, HUD_H - i, i + br, HUD_H - i, p)
            canvas.drawLine(i, HUD_H - i, i, HUD_H - i - br * 0.7f, p)
            canvas.drawLine(HUD_W - i, HUD_H - i, HUD_W - i - br, HUD_H - i, p)
            canvas.drawLine(HUD_W - i, HUD_H - i, HUD_W - i, HUD_H - i - br * 0.7f, p)
        }
        p.strokeCap = Paint.Cap.BUTT
        // compass ribbon, opening from the centre
        val reveal = VisorBootMotion.easeOutCubic(VisorBootMotion.seg(boot, 0.3, 0.55))
        val cx = HUD_W / 2
        if (reveal > 0.01) {
            val half = (96 * reveal).toFloat()
            canvas.save()
            canvas.clipRect(cx - half, 6f, cx + half, 32f)
            val hd = VisorBootMotion.heading(run)
            val ppd = 2.6
            p.strokeWidth = 1f
            var deg = floor((hd - 42) / 5).toInt() * 5
            while (deg <= hd + 42) {
                val x = (cx + (deg - hd) * ppd).toFloat()
                val major = ((deg % 15) + 15) % 15 == 0
                val edge = 1 - abs(x - cx) / 100.0
                amber(p, (if (major) 0.9 else 0.5) * vis * VisorBootMotion.clamp01(edge * 1.6))
                canvas.drawLine(x, 12f, x, 12f + if (major) 6f else 3f, p)
                val d360 = ((deg % 360) + 360) % 360
                if (d360 % 30 == 0) {
                    val text = when (d360) {
                        0 -> "N"; 90 -> "E"; 180 -> "S"; 270 -> "W"
                        else -> (if (d360 < 100) "0" else "") + d360
                    }
                    f8.color = p.color
                    canvas.drawText(text, x - f8.measureText(text) / 2, 28f, f8)
                }
                deg += 5
            }
            canvas.restore()
            // caret
            p.style = Paint.Style.FILL
            amber(p, 0.95 * vis * reveal)
            path.reset()
            path.moveTo(cx, 10f)
            path.lineTo(cx - 3.5f, 5f)
            path.lineTo(cx + 3.5f, 5f)
            path.close()
            canvas.drawPath(path, p)
        }
        // reticle
        val ret = VisorBootMotion.easeOutCubic(VisorBootMotion.seg(boot, 0.25, 0.5))
        if (ret > 0.01) {
            p.style = Paint.Style.STROKE
            p.strokeWidth = 1f
            amber(p, 0.7 * vis * ret)
            val ry = 60f
            val rr = (7 + 4 * (1 - ret)).toFloat()
            canvas.drawCircle(cx, ry, rr, p)
            canvas.drawLine(cx - rr - 9, ry, cx - rr - 3, ry, p)
            canvas.drawLine(cx + rr + 3, ry, cx + rr + 9, ry, p)
            canvas.drawLine(cx, ry + rr + 3, cx, ry + rr + 7, p)
            amber(p, 0.35 * vis * ret)
            canvas.drawLine(cx - 70, ry, cx - 36, ry, p)
            canvas.drawLine(cx + 36, ry, cx + 70, ry, p)
        }
        // two readouts typing in
        p.style = Paint.Style.FILL
        val kl = VisorBootMotion.seg(boot, 0.55, 0.78)
        val kr = VisorBootMotion.seg(boot, 0.72, 0.95)
        if (kl > 0) {
            val n = ceil(kl * READOUT_L.length).toInt()
            amber(p, 0.95 * vis)
            f11.color = p.color
            val s = READOUT_L.substring(0, n)
            canvas.drawText(s, 12f, 100f, f11)
            if (kl < 1) canvas.drawRect(13 + f11.measureText(s), 90f, 19 + f11.measureText(s), 101f, p)
        }
        if (kr > 0) {
            val x1 = HUD_W - 12
            val n = ceil(kr * READOUT_R.length).toInt()
            val s = READOUT_R.substring(0, n)
            val full = f11.measureText(READOUT_R)
            amber(p, 0.95 * vis)
            f11.color = p.color
            canvas.drawText(s, x1 - full, 100f, f11)
            if (kr < 1) {
                val x = x1 - full + f11.measureText(s) + 1
                canvas.drawRect(x, 90f, x + 6, 101f, p)
            }
        }
    }

    private companion object {
        val HUD_W = VisorBootMotion.HUD_W.toFloat()
        val HUD_H = VisorBootMotion.HUD_H.toFloat()
        const val READOUT_L = "38.4 m"
        const val READOUT_R = "640 lx"
        const val BARS = 30
        const val TRANSCRIPT = "“Mark this trail for later.”"
        val BLIPS = listOf(0.9 to 0.62, 2.7 to 0.38, 4.5 to 0.78)
    }
}
