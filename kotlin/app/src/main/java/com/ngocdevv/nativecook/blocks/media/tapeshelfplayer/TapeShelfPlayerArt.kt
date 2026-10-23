package com.ngocdevv.nativecook.blocks.media.tapeshelfplayer

import android.content.res.AssetManager
import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** The tapes, the cassette, the player and the home video on its screen, drawn in code. Ported from TapeShelfPlayerArt.swift. */
internal val TapePage = Color(0xFFF4F2EE)
internal val TapeInk = Color(0xFF141414)
internal val TapeMagenta = Color(0xFFC4125A)

internal class Tape(val label: String, val brand: String, val color: Long, val brandInk: Long, val scene: LongArray)

internal val tapes = listOf(
    Tape("Summer '98", "KORAL", 0xFFE5352F, 0xFFFFFFFF, longArrayOf(0xFFFF8A5B, 0xFFFFD08A, 0xFFFFF3C4, 0xFF2C6E8F)),
    Tape("Lake house", "STRATA", 0xFFF2EBD3, 0xFF1C1C1C, longArrayOf(0xFF7FB7D9, 0xFFD9EEF7, 0xFFFFFFFF, 0xFF2F5D50)),
    Tape("First snow", "NOVAVIEW", 0xFF1F2F6E, 0xFFFFFFFF, longArrayOf(0xFFB8C4D6, 0xFFE9EEF5, 0xFFFFF7E0, 0xFFC9D2DF)),
    Tape("Road trip", "SOLACE", 0xFF9B87C8, 0xFFFFFFFF, longArrayOf(0xFFF2A65A, 0xFFF7D9A8, 0xFFFFF1C9, 0xFF7A5A3A)),
    Tape("Sunset pier", "DRIFT", 0xFF1A1A1C, 0xFFFFFFFF, longArrayOf(0xFF5B3A8C, 0xFFF28C8C, 0xFFFFD58A, 0xFF2A2350)),
    Tape("Nana's 70th", "STRATA HQ", 0xFFE6EFC4, 0xFF1E5E3A, longArrayOf(0xFFE9C46A, 0xFFF4E3B5, 0xFFFFFFFF, 0xFFA0522D)),
    Tape("Beach day", "KORAL", 0xFFF08A24, 0xFFFFFFFF, longArrayOf(0xFF4FC3F7, 0xFFB3E5FC, 0xFFFFF59D, 0xFF0277BD)),
    Tape("New puppy", "NOVAVIEW", 0xFF2F7F86, 0xFFFFFFFF, longArrayOf(0xFFA5D6A7, 0xFFE8F5E9, 0xFFFFFFFF, 0xFF558B2F)),
    Tape("Night drive", "SOLACE", 0xFFC9A27A, 0xFF2A1A0E, longArrayOf(0xFF0B1026, 0xFF2B2F77, 0xFFF5F3CE, 0xFF05060F)),
)
private val rainbow = longArrayOf(0xFFE53935, 0xFFFB8C00, 0xFFFDD835, 0xFF43A047, 0xFF1E88E5)

/** The screen's geometry in dp, from its size and safe area. */
internal class TapeGeo(val w: Float, val h: Float, val top: Float, val bottom: Float) {
    val shelfTop = top + 44
    val shelfBottom = shelfTop + 226
    val body = Rect((w - 0.52f * w) / 2, top + 58, (w - 0.52f * w) / 2 + 0.52f * w, top + 58 + 188)
    val screen = Rect(body.left + 14, body.top + 14, body.right - 14, body.top + 14 + 112)
    val slot = run {
        val sw = 0.62f * body.width
        Rect(w / 2 - sw / 2, screen.bottom + 20, w / 2 + sw / 2, screen.bottom + 29)
    }
    val gridTop = shelfBottom + 58
    val cellW = (w - 48 - 28) / 3
    val cellH = cellW * 1.3f
    fun cell(i: Int): Rect {
        val x = 24 + (i % 3) * (cellW + 14)
        val y = gridTop + (i / 3) * (cellH + 14)
        return Rect(x, y, x + cellW, y + cellH)
    }
    /** The cassette: its length, and its thickness seen from above (a VHS is 1.83 : 1). */
    val cassetteL = cellH * 0.88f
    val cassetteT = cassetteL / 1.83f
    val pillY = h - max(bottom, 16f) - 12 - 44
    val transportY = h - max(bottom, 16f) - 8 - 60
}

private fun c(hex: Long, alpha: Float = 1f) = Color(hex).copy(alpha = alpha)

internal class TapePainter(assets: AssetManager) {
    private val hand: Typeface = Typeface.createFromAsset(assets, "tapeshelfplayer/tapeshelfplayer_GochiHand-Regular.ttf")
    private val condensed: Typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
    private val bold: Typeface = Typeface.DEFAULT_BOLD
    private val mono: Typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val blurPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val blurs = HashMap<Int, BlurMaskFilter>()

    /** Text placed like SwiftUI's anchors: alignX 0 left, 1 centre, 2 right; anchorY 0 centre, 1 top, 2 bottom. */
    /** A line wider than [maxWidth] is drawn at a smaller size so that it fits. */
    private fun DrawScope.text(
        s: String, x: Float, y: Float, size: Float, color: Color, face: Typeface, alignX: Int, anchorY: Int,
        maxWidth: Float = Float.POSITIVE_INFINITY,
    ) {
        val p = textPaint
        p.typeface = face
        p.textSize = size
        val width = p.measureText(s)
        if (width > maxWidth && width > 0f) p.textSize = size * maxWidth / width
        p.color = color.toArgb()
        p.textAlign = when (alignX) {
            0 -> Paint.Align.LEFT
            1 -> Paint.Align.CENTER
            else -> Paint.Align.RIGHT
        }
        val fm = p.fontMetrics
        val baseline = when (anchorY) {
            0 -> y - (fm.ascent + fm.descent) / 2
            1 -> y - fm.ascent
            else -> y - fm.descent
        }
        drawContext.canvas.nativeCanvas.drawText(s, x, baseline, p)
    }

    /** A soft black rounded rect (Gaussian of [sigma] dp); a plain translucent one below API 28. */
    private fun DrawScope.softRect(r: Rect, radius: Float, sigma: Float, alpha: Float) {
        val p = blurPaint
        p.color = android.graphics.Color.BLACK
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val key = (sigma * 4).roundToInt()
            p.maskFilter = blurs.getOrPut(key) { BlurMaskFilter(sigmaToRadius(key / 4f), BlurMaskFilter.Blur.NORMAL) }
            p.alpha = (255 * alpha).roundToInt().coerceIn(0, 255)
        } else {
            p.maskFilter = null
            p.alpha = (255 * alpha * 0.5f).roundToInt().coerceIn(0, 255)
        }
        drawContext.canvas.nativeCanvas.drawRoundRect(r.left, r.top, r.right, r.bottom, radius, radius, p)
    }

    // Tapes on the shelf.

    fun DrawScope.sleeve(i: Int, r: Rect, lift: Float) {
        val t = tapes[i]
        val s = 1 + 0.05f * lift
        scale(s, s, r.center) {
            softRect(r.translate(0f, 2 + 8 * lift), 4f, 3 + 8 * lift, 0.16f + 0.12f * lift)
            val box = Path().apply { addRoundRect(RoundRect(r, CornerRadius(4f))) }
            drawPath(box, c(t.color))
            val w = r.width
            val h = r.height
            clipPath(box) {
                // Spine.
                drawRect(Color.Black.copy(alpha = 0.18f), r.topLeft, Size(w * 0.11f, h))
                // Brand and length.
                val ink = c(t.brandInk)
                text(t.brand, r.left + w * 0.17f, r.top + h * 0.05f, w * 0.16f, ink, condensed, 0, 1)
                text("T-120", r.left + w * 0.17f, r.top + h * 0.05f + w * 0.17f, w * 0.075f, ink.copy(alpha = 0.75f), bold, 0, 1)
                // Rainbow band.
                rainbow.forEachIndexed { k, hex ->
                    drawRect(c(hex), Offset(r.left + w * 0.11f, r.top + h * (0.66f + k * 0.026f)), Size(w * 0.89f, h * 0.026f))
                }
                // The sticker with the handwritten title.
                translate(r.center.x + w * 0.04f, r.top + h * 0.44f) {
                    rotate(-4f, Offset.Zero) {
                        val sticker = Rect(-w * 0.37f, -h * 0.12f, w * 0.37f, h * 0.12f)
                        drawRoundRect(Color.Black.copy(alpha = 0.12f), sticker.topLeft + Offset(0.5f, 1f), sticker.size, CornerRadius(3f))
                        drawRoundRect(c(0xFFFBFAF6), sticker.topLeft, sticker.size, CornerRadius(3f))
                        drawRect(c(0xFF8FB5D9), Offset(sticker.left + 5, sticker.bottom - h * 0.055f), Size(sticker.width - 10, 0.6f))
                        text(t.label, 0f, -h * 0.005f, w * 0.15f, c(0xFF1D2A6B), hand, 1, 0, sticker.width - 8)
                    }
                }
                // A small format badge.
                val badge = Rect(r.right - w * 0.27f, r.bottom - h * 0.11f, r.right - w * 0.07f, r.bottom - h * 0.04f)
                drawRoundRect(ink.copy(alpha = 0.7f), badge.topLeft, badge.size, CornerRadius(2f), style = Stroke(0.8f))
                text("VHS", badge.center.x, badge.center.y, w * 0.06f, ink.copy(alpha = 0.8f), bold, 1, 0)
                // A faint sheen.
                drawRect(
                    Brush.linearGradient(listOf(Color.White.copy(alpha = 0.14f), Color.White.copy(alpha = 0f)), r.topLeft, Offset(r.left + w * 0.6f, r.top + h * 0.5f)),
                    r.topLeft, r.size,
                )
            }
        }
    }

    // The cassette.

    /** A VHS cassette seen from above, centred at the origin, horizontal, length l and thickness t. */
    fun DrawScope.cassette(l: Float, t: Float, label: String, reels: Float) {
        val body = Rect(-l / 2, -t / 2, l / 2, t / 2)
        drawRoundRect(Brush.verticalGradient(listOf(c(0xFF2A2A2E), c(0xFF141416)), startY = -t / 2, endY = t / 2), body.topLeft, body.size, CornerRadius(l * 0.05f))
        drawRoundRect(Color.White.copy(alpha = 0.12f), body.topLeft + Offset(0.5f, 0.5f), Size(l - 1, t - 1), CornerRadius(l * 0.05f), style = Stroke(1f))
        // Label.
        val lab = Rect(-l * 0.36f, -t * 0.4f, l * 0.36f, -t * 0.08f)
        drawRoundRect(c(0xFFF7F5EE), lab.topLeft, lab.size, CornerRadius(2f))
        text(label, lab.center.x, lab.center.y, t * 0.2f, c(0xFF1D2A6B), hand, 1, 0, lab.width - 6)
        // Window with the two reels.
        val win = Rect(-l * 0.27f, 0f, l * 0.27f, t * 0.34f)
        val winPath = Path().apply { addRoundRect(RoundRect(win, CornerRadius(3f))) }
        drawPath(winPath, c(0xFF0A0A0B))
        clipPath(winPath) {
            floatArrayOf(-1f, 1f).forEachIndexed { k, side ->
                val ctr = Offset(side * l * 0.15f, win.center.y)
                val tapeR = t * (if (k == 0) 0.2f else 0.13f)
                drawCircle(c(0xFF4A2E1A), tapeR, ctr)
                val hub = t * 0.07f
                drawCircle(c(0xFFEDEDED), hub, ctr)
                for (s in 0 until 6) {
                    val a = s * PI / 3 + reels
                    drawLine(c(0xFF555555), ctr, Offset(ctr.x + (cos(a) * hub * 0.9).toFloat(), ctr.y + (sin(a) * hub * 0.9).toFloat()), 1f)
                }
            }
        }
        for (x in floatArrayOf(-0.44f, 0.44f)) drawCircle(Color.White.copy(alpha = 0.25f), 1.5f, Offset(l * x, t * 0.3f))
    }

    /** The cassette on its way: beside its sleeve, in flight to the slot, or squashing into it. */
    fun DrawScope.flyingCassette(p: TapeMotion.Pose, g: TapeGeo) {
        if (p.cassetteAlpha <= 0.001) return
        val box = g.cell(p.tape)
        val l = g.cassetteL
        val t = g.cassetteT
        val k = g.slot.width / l
        val p0 = Offset(box.center.x + p.slide.toFloat() * g.cellW * 0.62f, box.center.y)
        val p2 = Offset(g.w / 2, g.slot.center.y - t * k / 2 - 4)
        val p1 = Offset((p0.x + p2.x) / 2 + 40, min(p0.y, p2.y) - 70)
        val f = p.flight.toFloat()
        val x = (1 - f) * (1 - f) * p0.x + 2 * (1 - f) * f * p1.x + f * f * p2.x
        var y = (1 - f) * (1 - f) * p0.y + 2 * (1 - f) * f * p1.y + f * f * p2.y
        val lift = sin(PI * f).toFloat()
        val rot = 90 * (1 - f) + 14 * lift
        val sc = 1 + (k - 1) * f + 0.12f * lift
        y += (g.slot.center.y - y) * p.squash.toFloat()
        val sy = sc * (1 - 0.9f * p.squash.toFloat())
        val nc = drawContext.canvas.nativeCanvas
        val alpha = p.cassetteAlpha.toFloat()
        if (alpha < 0.999f) nc.saveLayerAlpha(0f, 0f, g.w, g.h, (255 * alpha).roundToInt())
        // Its shadow, further below while it flies.
        translate(x, y + 4 + 14 * lift) {
            rotate(rot, Offset.Zero) {
                scale(sc, sy, Offset.Zero) { softRect(Rect(-l / 2, -t / 2, l / 2, t / 2), l * 0.05f, 4 + 8 * lift, 0.3f) }
            }
        }
        translate(x, y) {
            rotate(rot, Offset.Zero) {
                scale(sc, sy, Offset.Zero) { cassette(l, t, tapes[p.tape].label, f * 2 * PI.toFloat()) }
            }
        }
        if (alpha < 0.999f) nc.restore()
    }

    // The player.

    fun DrawScope.player(g: TapeGeo, p: TapeMotion.Pose, clock: Double, still: Boolean) {
        translate(0f, p.bounce.toFloat()) {
            val body = g.body
            // Shadow on the shelf, then the body.
            softRect(Rect(body.left + 8, body.top + 14, body.right - 8, body.bottom + 6), 26f, 14f, 0.35f)
            drawRoundRect(Brush.verticalGradient(listOf(c(0xFF2B2B2E), c(0xFF19191B)), startY = body.top, endY = body.bottom), body.topLeft, body.size, CornerRadius(26f))
            drawRoundRect(
                Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.04f)), startY = body.top, endY = body.bottom),
                body.topLeft + Offset(0.5f, 0.5f), Size(body.width - 1, body.height - 1), CornerRadius(25.5f), style = Stroke(1f),
            )
            screen(g.screen, p, clock, still)
            // Slot, LED, buttons.
            val slot = g.slot
            drawRoundRect(c(0xFF111113), Offset(slot.left - 4, slot.top - 4), Size(slot.width + 8, slot.height + 8), CornerRadius(8f))
            drawRoundRect(Color.Black, slot.topLeft, slot.size, CornerRadius(4f))
            drawRect(Color.White.copy(alpha = 0.15f), Offset(slot.left + 4, slot.bottom - 1.5f), Size(slot.width - 8, 1f))
            val led = Offset(body.right - 22, body.bottom - 20)
            val ledColor = if (p.led > 0.5) c(0xFF3BE37A) else c(0xFFFF3B5C)
            drawCircle(Brush.radialGradient(listOf(ledColor.copy(alpha = 0.7f), ledColor.copy(alpha = 0f)), center = led, radius = 10f), 10f, led)
            drawCircle(ledColor, 3.5f, led)
            for (k in 0 until 3) {
                val b = Offset(body.left + 22 + k * 16, body.bottom - 20)
                drawCircle(c(0xFF0F0F11), 5f, b)
                drawCircle(Color.White.copy(alpha = 0.12f), 4.6f, b, style = Stroke(0.8f))
            }
        }
    }

    /** The CRT: idle text, the power line, the home video, scanlines and glass. */
    private fun DrawScope.screen(r: Rect, p: TapeMotion.Pose, clock: Double, still: Boolean) {
        val glass = Path().apply { addRoundRect(RoundRect(r, CornerRadius(16f))) }
        clipPath(glass) {
            drawPath(glass, c(0xFF0E0F11))
            if (p.screen <= 0.001) {
                // Idle: a blinking prompt, once a second.
                val on = still || clock % 1 < 0.6
                if (on) text("INSERT TAPE", r.center.x, r.center.y, 9f, Color.White.copy(alpha = 0.45f), mono, 1, 0)
            } else {
                val u = p.screen
                val band = when {
                    still -> r.height
                    p.powering > 0 -> r.height * TapeMotion.easeOut((u - 0.15) / 0.85).toFloat()
                    else -> r.height * TapeMotion.smoothstep(0.35, 1.0, u).toFloat()
                }
                if (band > 0.5f) {
                    val nc = drawContext.canvas.nativeCanvas
                    val layered = still && u < 0.999
                    if (layered) nc.saveLayerAlpha(r.left, r.top, r.right, r.bottom, (255 * u).roundToInt())
                    clipRect(r.left, r.center.y - band / 2, r.right, r.center.y + band / 2) { video(r, p.tape, p.video) }
                    if (layered) nc.restore()
                }
                // The power line: a bright horizontal streak growing (on) or shrinking to a dot (off).
                if (!still) {
                    if (p.powering > 0 && u < 0.4) {
                        val lw = r.width * min(1.0, u / 0.15).toFloat()
                        drawRect(Color.White.copy(alpha = (1 - u / 0.4).toFloat()), Offset(r.center.x - lw / 2, r.center.y - 1), Size(lw, 2f))
                    } else if (p.powering < 0 && u < 0.35) {
                        val lw = max(3f, r.width * (u / 0.35).toFloat())
                        drawRoundRect(Color.White.copy(alpha = min(1.0, u / 0.05).toFloat()), Offset(r.center.x - lw / 2, r.center.y - 1.5f), Size(lw, 3f), CornerRadius(1.5f))
                    }
                }
            }
            // Scanlines, vignette, glass highlight.
            var y = r.top
            while (y < r.bottom) {
                drawRect(Color.Black.copy(alpha = 0.18f), Offset(r.left, y), Size(r.width, 1f))
                y += 3
            }
            drawPath(
                glass,
                Brush.radialGradient(
                    0f to Color.Transparent, 0.4167f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.45f),
                    center = r.center, radius = r.width * 0.72f,
                ),
            )
            drawPath(glass, Brush.linearGradient(listOf(Color.White.copy(alpha = 0.14f), Color.White.copy(alpha = 0f)), r.topLeft, Offset(r.left + r.width * 0.45f, r.center.y)))
        }
        drawPath(glass, Color.Black.copy(alpha = 0.8f), style = Stroke(2f))
    }

    /** The home video: a sky, a sun, the water with moving glints, the camcorder's OSD and a rolling tracking band. */
    private fun DrawScope.video(r: Rect, tape: Int, time: Double) {
        val sc = tapes[tape].scene
        val horizon = r.top + r.height * 0.62f
        drawRect(Brush.verticalGradient(listOf(c(sc[0]), c(sc[1])), startY = r.top, endY = horizon), r.topLeft, r.size)
        // The sun, cut by bands, slowly sinking.
        val sunR = r.height * 0.2f
        val sun = Offset(r.left + r.width * 0.66f, (horizon - sunR * 0.55f + 3 * sin(time * 0.6)).toFloat())
        clipRect(r.left, r.top, r.right, horizon) {
            drawCircle(c(sc[2]), sunR, sun)
            for (k in 0 until 3) drawRect(c(sc[1]), Offset(sun.x - sunR, sun.y + sunR * (0.15f + k * 0.28f)), Size(2 * sunR, 1.2f + k))
        }
        // The water, with glints drifting.
        drawRect(c(sc[3]), Offset(r.left, horizon), Size(r.width, r.bottom - horizon))
        for (k in 0 until 9) {
            val row = k % 3
            val y = horizon + 3 + row * (r.bottom - horizon - 6) / 3 + 2
            val span = r.width * 0.9
            val x = r.left + ((k * 37 + time * (14 + row * 6)) % span) + r.width * 0.05
            val len = 6 + 10 * TapeMotion.clamp01(1 - abs(x - sun.x) / (r.width * 0.5))
            drawRect(c(sc[2]).copy(alpha = 0.75f), Offset((x - len / 2).toFloat(), y), Size(len.toFloat(), 1.2f))
        }
        // A dark pier reaching into the water.
        drawRect(Color.Black.copy(alpha = 0.55f), Offset(r.left, horizon - 3), Size(r.width * 0.34f, 3f))
        for (k in 0 until 4) drawRect(Color.Black.copy(alpha = 0.5f), Offset(r.left + k * r.width * 0.1f + 4, horizon), Size(2f, 7f + k))
        // The rolling tracking band.
        val bandY = r.top + ((time * 0.35) % 1.3).toFloat() * r.height - r.height * 0.15f
        val frame = (time * 12).toInt()
        for (k in 0 until 7) {
            val lx = r.left + (TapeMotion.hash(k, frame) * r.width).toFloat()
            drawRect(Color.White.copy(alpha = 0.35f), Offset(lx, bandY + k * 2.2f), Size((10 + 30 * TapeMotion.hash(k, frame + 7)).toFloat(), 1f))
        }
        // OSD.
        val play = if (time < 2.5 && time % 0.8 < 0.5) "PLAY ▶" else "PLAY"
        text(play, r.left + 10, r.top + 8, 8f, c(0xFF9CFFB0), mono, 0, 1)
        text("SP", r.right - 10, r.top + 8, 8f, Color.White.copy(alpha = 0.85f), mono, 2, 1)
        text("JUL 14 1998", r.right - 10, r.bottom - 8, 8f, c(0xFFFFB347), mono, 2, 2)
    }
}
