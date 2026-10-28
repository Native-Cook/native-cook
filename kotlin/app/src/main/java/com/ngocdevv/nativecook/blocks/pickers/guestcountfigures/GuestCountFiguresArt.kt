package com.ngocdevv.nativecook.blocks.pickers.guestcountfigures

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import kotlin.math.min
import kotlin.math.roundToInt

/*
 Guest Count Figures — the little people, drawn in code (a port of GuestCountFiguresArt.swift,
 same shapes and colours).

 Each figure stands with its feet on (0, 0), x centred, up is negative y, in dp. Every part is a
 rounded rectangle or an ellipse filled with a left-lit gradient (14 % lighter on the left, 22 %
 darker on the right), so the flat colours read like painted figurines. Adults are 115 dp tall,
 children 76 dp, each scaled a little by its own height.
*/

internal fun gcfHex(v: Long, a: Float = 1f): Color = Color(
    red = ((v shr 16) and 0xFF) / 255f,
    green = ((v shr 8) and 0xFF) / 255f,
    blue = (v and 0xFF) / 255f,
    alpha = a,
)

internal object GcfArt {
    class Body(
        val h: Float, val headW: Float, val headH: Float, val torsoTop: Float, val torsoBot: Float,
        val torsoW: Float, val armW: Float, val legW: Float, val shoeW: Float, val shoeH: Float,
        val neckW: Float, val headDrop: Float,
    )

    val ADULT = Body(118f, 17f, 19f, -92f, -56f, 24f, 7f, 10f, 13f, 5f, 6f, 3f)
    val CHILD = Body(76f, 15.5f, 16.5f, -58f, -33f, 19f, 6f, 8f, 11f, 4f, 5f, 0f)

    enum class Hair { BEANIE, SHORT, BUN, LONG, PUFFS }
    enum class Outer { NONE, VEST, COAT }
    enum class Extra { NONE, BAG, LANYARD, RING, DOTS }

    class Look(
        val kid: Boolean,
        val scale: Float,
        val skin: Long,
        val hair: Long,
        val hairKind: Hair,
        val hat: Long = 0,
        val top: Long,
        val sleeve: Long,
        val outer: Outer = Outer.NONE,
        val outerColor: Long = 0,
        val bottom: Long,
        val shorts: Boolean = false,
        val shoes: Long,
        val extra: Extra = Extra.NONE,
        val extraColor: Long = 0,
    )

    val LOOKS = listOf(
        Look(kid = false, scale = 1.0f, skin = 0xC68A62, hair = 0x3A2A22, hairKind = Hair.BEANIE, hat = 0x2FB5A8,
            top = 0xF1ECE2, sleeve = 0xC68A62, bottom = 0x8C8648, shoes = 0x5A3B28,
            extra = Extra.BAG, extraColor = 0x6B4A33),
        Look(kid = false, scale = 1.04f, skin = 0xE2B48E, hair = 0x6B4428, hairKind = Hair.SHORT,
            top = 0x9FC0DE, sleeve = 0x9FC0DE, outer = Outer.VEST, outerColor = 0x263447,
            bottom = 0xC49A5A, shoes = 0x2B2B2B, extra = Extra.LANYARD, extraColor = 0x3A6FD8),
        Look(kid = false, scale = 0.97f, skin = 0x8D5A3C, hair = 0x1F1A1A, hairKind = Hair.BUN,
            top = 0xEDE6DA, sleeve = 0xD9A33A, outer = Outer.COAT, outerColor = 0xD9A33A,
            bottom = 0x3C3F48, shoes = 0x1E1E1E),
        Look(kid = false, scale = 1.0f, skin = 0xF0C9A8, hair = 0xA2512E, hairKind = Hair.LONG,
            top = 0xB9A3D6, sleeve = 0xB9A3D6, bottom = 0x4B6A93, shoes = 0xECE8E1),
        Look(kid = true, scale = 1.0f, skin = 0x7A4B32, hair = 0x2A1E1A, hairKind = Hair.PUFFS,
            top = 0xF08CA6, sleeve = 0x7A4B32, bottom = 0xF08CA6, shorts = true, shoes = 0xF2C94C,
            extra = Extra.RING, extraColor = 0x6FD3C2),
        Look(kid = true, scale = 0.9f, skin = 0xD9A07A, hair = 0x3B2A20, hairKind = Hair.SHORT,
            top = 0xE8873A, sleeve = 0xE8873A, bottom = 0x5B4A8C, shoes = 0x2B2B2B,
            extra = Extra.DOTS, extraColor = 0xF7C58F),
        Look(kid = true, scale = 0.95f, skin = 0xF2CBAA, hair = 0xE3B04B, hairKind = Hair.SHORT,
            top = 0xD9534F, sleeve = 0xD9534F, bottom = 0x2E3A59, shorts = true, shoes = 0x3D7DD8),
    )

    // Colour.

    private fun lighten(v: Long, k: Float): Color {
        val c = gcfHex(v)
        return Color(c.red + (1 - c.red) * k, c.green + (1 - c.green) * k, c.blue + (1 - c.blue) * k)
    }

    private fun darken(v: Long, k: Float): Color {
        val c = gcfHex(v)
        return Color(c.red * (1 - k), c.green * (1 - k), c.blue * (1 - k))
    }

    fun darkenHex(v: Long, k: Float): Long {
        val c = gcfHex(v)
        val r = (c.red * (1 - k) * 255).roundToInt().toLong()
        val g = (c.green * (1 - k) * 255).roundToInt().toLong()
        val b = (c.blue * (1 - k) * 255).roundToInt().toLong()
        return (r shl 16) or (g shl 8) or b
    }

    // Primitives.

    private fun shade(v: Long, x: Float, w: Float): Brush = Brush.horizontalGradient(
        0f to lighten(v, 0.14f), 0.45f to gcfHex(v), 1f to darken(v, 0.22f),
        startX = x, endX = x + w,
    )

    private fun DrawScope.rr(x: Float, y: Float, w: Float, h: Float, r: Float, v: Long) {
        val rad = min(r, min(w / 2, h / 2))
        drawRoundRect(shade(v, x, w), Offset(x, y), Size(w, h), CornerRadius(rad, rad))
    }

    private fun DrawScope.el(x: Float, y: Float, w: Float, h: Float, v: Long) {
        drawOval(shade(v, x, w), Offset(x, y), Size(w, h))
    }

    private fun DrawScope.line(x0: Float, y0: Float, x1: Float, y1: Float, w: Float, v: Long) {
        drawLine(gcfHex(v), Offset(x0, y0), Offset(x1, y1), w, StrokeCap.Round)
    }

    // Shadows.

    /** The tight contact shadow and the long soft one to the right, under a figure at (0, 0). */
    fun DrawScope.drawShadow(slot: Float, k: Float, alpha: Float) {
        fun blob(cx: Float, w: Float, h: Float, a: Float) {
            if (w <= 0f) return
            translate(cx, 0f) {
                scale(1f, h / w, pivot = Offset.Zero) {
                    drawCircle(
                        Brush.radialGradient(
                            0f to gcfHex(0x2A2420, a), 0.5f to gcfHex(0x2A2420, a * 0.45f), 1f to gcfHex(0x2A2420, 0f),
                            center = Offset.Zero, radius = w / 2,
                        ),
                        radius = w / 2,
                        center = Offset.Zero,
                    )
                }
            }
        }
        blob(slot * 0.55f, slot * 1.7f * k, 10f, 0.07f * alpha)
        blob(0f, slot * 0.9f * k, 7f, 0.22f * alpha)
    }

    // Figure.

    fun DrawScope.drawFigure(look: Look) {
        val b = if (look.kid) CHILD else ADULT
        val headTop = -b.h + b.headDrop
        val headCY = headTop + b.headH / 2

        // Hair behind the head.
        when (look.hairKind) {
            Hair.LONG -> rr(-b.headW / 2 - 1.5f, headTop - 1, b.headW + 3, b.headH + 12, 9f, look.hair)
            Hair.BUN -> el(-5.5f, headTop - 7.5f, 11f, 10f, look.hair)
            Hair.PUFFS -> {
                el(-b.headW / 2 - 5, headTop + 1, 10f, 10f, look.hair)
                el(b.headW / 2 - 5, headTop + 1, 10f, 10f, look.hair)
            }
            else -> Unit
        }

        // Legs and shoes.
        val legTop = b.torsoBot - 4
        for (s in floatArrayOf(-1f, 1f)) {
            val lx = s * (b.legW / 2 + 0.5f)
            if (look.shorts) {
                val lw = b.legW * 0.78f
                rr(lx - lw / 2, legTop, lw, -legTop - b.shoeH + 1.5f, lw / 2, look.skin)
                rr(lx - b.legW / 2 - 0.5f, legTop, b.legW + 1, -legTop * 0.36f, 3f, look.bottom)
            } else {
                rr(lx - b.legW / 2, legTop, b.legW, -legTop - b.shoeH + 1.5f, b.legW * 0.4f, look.bottom)
            }
            rr(lx - b.shoeW / 2 + s * 1, -b.shoeH, b.shoeW, b.shoeH, b.shoeH / 2, look.shoes)
        }
        // Hips.
        rr(-b.torsoW / 2 + 1, b.torsoBot - 6, b.torsoW - 2, 10f, 4f, look.bottom)

        // Arms, then hands.
        val sleeve = if (look.outer == Outer.COAT) look.outerColor else look.sleeve
        for (s in floatArrayOf(-1f, 1f)) {
            val ax = s * (b.torsoW / 2 + b.armW / 2 - 1.5f)
            rr(ax - b.armW / 2, b.torsoTop + 1, b.armW, b.torsoBot - b.torsoTop + 1, b.armW / 2, sleeve)
            val hr = b.armW * 0.55f
            el(ax - hr, b.torsoBot + 3 - hr, 2 * hr, 2 * hr, look.skin)
        }

        // Neck and torso.
        val neckTop = headCY + b.headH * 0.3f
        rr(-b.neckW / 2, neckTop, b.neckW, b.torsoTop + 3 - neckTop, 1f, look.skin)
        rr(-b.torsoW / 2, b.torsoTop, b.torsoW, b.torsoBot - b.torsoTop + 2, b.torsoW * 0.3f, look.top)

        when (look.outer) {
            Outer.COAT -> {
                rr(-b.torsoW / 2 - 1, b.torsoTop, b.torsoW + 2, b.torsoBot - b.torsoTop + 22, 7f, look.outerColor)
                line(0.5f, b.torsoTop + 5, 0.5f, b.torsoBot + 20, 0.9f, 0xA8761F)
                rr(-3.5f, b.torsoTop - 0.5f, 7f, 5f, 2.5f, look.top)
            }
            Outer.VEST -> {
                val hw = b.torsoW / 2 - 2
                rr(-b.torsoW / 2 - 0.5f, b.torsoTop + 1, hw, b.torsoBot - b.torsoTop, 5f, look.outerColor)
                rr(b.torsoW / 2 + 0.5f - hw, b.torsoTop + 1, hw, b.torsoBot - b.torsoTop, 5f, look.outerColor)
                for (y in floatArrayOf(b.torsoTop + 13, b.torsoTop + 23)) {
                    line(-b.torsoW / 2 + 1, y, -2.5f, y, 0.8f, 0x18212E)
                    line(2.5f, y, b.torsoW / 2 - 1, y, 0.8f, 0x18212E)
                }
            }
            Outer.NONE -> Unit
        }

        when (look.extra) {
            Extra.BAG -> {
                line(-b.torsoW / 2 + 2, b.torsoTop + 2, b.torsoW / 2 + 2, b.torsoBot - 6, 2f, look.extraColor)
                rr(b.torsoW / 2 - 2, b.torsoBot - 9, 9f, 8f, 2f, look.extraColor)
            }
            Extra.LANYARD -> {
                line(-3f, b.torsoTop + 1, 0f, b.torsoTop + 13, 0.9f, look.extraColor)
                line(3f, b.torsoTop + 1, 0f, b.torsoTop + 13, 0.9f, look.extraColor)
                rr(-3f, b.torsoTop + 13, 6f, 8f, 1f, 0xF7F5F0)
            }
            Extra.DOTS -> {
                val dots = floatArrayOf(-5f, 6f, 3.5f, 10f, -2f, 16f, 5f, 19f, -6f, 21f)
                for (i in 0 until 5) el(dots[2 * i] - 1.3f, b.torsoTop + dots[2 * i + 1] - 1.3f, 2.6f, 2.6f, look.extraColor)
            }
            Extra.RING -> {
                val y = b.torsoBot - 6
                el(-17f, y - 7, 34f, 14f, look.extraColor)
                el(-10.5f, y - 4, 21f, 6f, 0x3FA595)
                el(-17f, y - 1, 34f, 8f, look.extraColor)
                for (dx in floatArrayOf(-11f, 0f, 11f)) rr(dx - 1.3f, y, 2.6f, 6f, 1.3f, 0xF7FFFD)
                el(12.5f, y - 13, 9f, 10f, look.extraColor)
                el(17.8f, y - 10, 1.8f, 1.8f, 0x1E3B36)
            }
            Extra.NONE -> Unit
        }

        // Head, face, hair in front.
        el(-b.headW / 2, headTop, b.headW, b.headH, look.skin)
        val eyeY = headCY + 1.5f
        drawOval(gcfHex(0x2A1E1A, 0.75f), Offset(-3.6f - 0.8f, eyeY - 0.8f), Size(1.6f, 1.6f))
        drawOval(gcfHex(0x2A1E1A, 0.75f), Offset(3.0f - 0.8f, eyeY - 0.8f), Size(1.6f, 1.6f))

        when (look.hairKind) {
            Hair.BEANIE -> {
                rr(-b.headW / 2 - 0.5f, headTop + 5, 3.5f, 8f, 1.5f, look.hair)
                rr(b.headW / 2 - 3, headTop + 5, 3.5f, 8f, 1.5f, look.hair)
                rr(-b.headW / 2 - 1, headTop - 3, b.headW + 2, 11f, 7f, look.hat)
                rr(-b.headW / 2 - 1.5f, headTop + 5, b.headW + 3, 4f, 2f, darkenHex(look.hat, 0.18f))
            }
            else -> {
                el(-b.headW / 2 - 0.5f, headTop - 1.5f, b.headW + 1, b.headH * 0.46f, look.hair)
                rr(-b.headW / 2 - 0.5f, headTop + 2, 3f, b.headH * 0.38f, 1.5f, look.hair)
                rr(b.headW / 2 - 2.5f, headTop + 2, 3f, b.headH * 0.38f, 1.5f, look.hair)
            }
        }
    }
}
