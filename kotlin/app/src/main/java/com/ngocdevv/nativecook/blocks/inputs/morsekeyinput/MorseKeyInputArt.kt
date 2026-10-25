package com.ngocdevv.nativecook.blocks.inputs.morsekeyinput

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import com.ngocdevv.nativecook.blocks.inputs.morsekeyinput.MorseMotion.clamp01
import com.ngocdevv.nativecook.blocks.inputs.morsekeyinput.MorseMotion.hash
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 Morse Key Input — the drawing, ported from morseKeyArt.ts. One canvas: a baked desk (green
 leather, the telegram card with its typed clue and answer boxes, the key's walnut base with its
 contact post, trunnion and binding posts, and the wires to the register), a baked register (the
 brass box the tape comes out of), and every frame: the tape and its ink, the torn strip falling
 away, the register's reel, the lever with its knob and spring, contact sparks, and the decoded
 letters. Everything is in dp.
*/

internal class MorseFonts {
    val type = MorseKeyInputGfx.font(Typeface.MONOSPACE, 400, 12.5f)
    val typeBold = MorseKeyInputGfx.font(Typeface.MONOSPACE, 700, 11f)
    val slot = MorseKeyInputGfx.font(Typeface.MONOSPACE, 700, 36f)
    val small = MorseKeyInputGfx.font(Typeface.MONOSPACE, 700, 8.5f)
    val chip = MorseKeyInputGfx.font(Typeface.DEFAULT, 600, 13f)
}

internal class MorseScene(val w: Float, val h: Float, top: Float, bottom: Float) {
    val cardX = 16f
    val cardY = top + 108
    val cardW = w - 32
    val cardH = 176f
    val slotW = 52f
    val slotH = 62f
    val slotY = cardY + cardH - slotH - 18
    val slotX = floatArrayOf(cardX + 20, cardX + 20 + slotW + 10)
    /** Tape: centre line, height; print head x (where the tape leaves the register). */
    val tapeY = cardY + cardH + 58
    val tapeH = 30f
    val regW = 78f
    val regX = w - regW - 14
    val regY = tapeY - 40
    val regH = 94f
    val headX = regX + 2
    /** Resting tape length (a blank leader out of the register). */
    val leader = (headX - 22).toDouble()
    val baseTop = h - bottom - 60 - 128
    val baseX = 22f
    val baseW = w - 44
    val leverY = baseTop - 56
    val pivotX = w - 112
    val knobX = 82f
    val contactX = (w * 0.46f).roundToInt().toFloat()
    val springX = contactX + 54
    /** Lever travel at full press, degrees. */
    val tilt = 3.3f
    /** Where the register image sits (it only covers what it draws). */
    val registerRect = RectF(regX - 50, regY - 30, regX - 50 + regW + 100, regY - 30 + regH + 90)
}

internal class MorseLayers(val desk: Bitmap, val register: Bitmap)

private val BRASS = intArrayOf(morseRgb(0x6E4F22), morseRgb(0xC9A35C), morseRgb(0xF0D9A0), morseRgb(0xB88E45), morseRgb(0x5E421B))
private val BRASS_POS = floatArrayOf(0f, 0.3f, 0.45f, 0.7f, 1f)

internal object MorseKeyInputArt {
    fun bakeLayers(s: MorseScene, fonts: MorseFonts, scale: Float): MorseLayers {
        val g = MorseKeyInputGfx().apply { liveBlur = true }
        val gp = Paint()
        val grains = arrayOf(
            MorseKeyInputGrain(128, 128, 1.43, 1.43, 31),
            MorseKeyInputGrain(128, 128, 0.71, 0.71, 7),
            MorseKeyInputGrain(500, 126, 83.0, 2.0, 41),
            MorseKeyInputGrain(500, 126, 83.0, 1.67, 43),
        )
        val desk = MorseKeyInputGfx.bake(s.w, s.h, scale) { c -> desk(c, s, fonts, g, gp, grains) }
        val r = s.registerRect
        val register = MorseKeyInputGfx.bake(r.width(), r.height(), scale, r.left, r.top) { c -> register(c, s, fonts, g) }
        return MorseLayers(desk, register)
    }

    private fun clipRound(c: Canvas, r: RectF, radius: Float) {
        c.clipPath(Path().apply { addRoundRect(r, radius, radius, Path.Direction.CW) })
    }

    /** Desk, card, key base and its fixed parts, wires. */
    private fun desk(c: Canvas, s: MorseScene, fonts: MorseFonts, g: MorseKeyInputGfx, gp: Paint, grains: Array<MorseKeyInputGrain>) {
        val w = s.w
        val h = s.h
        val full = RectF(0f, 0f, w, h)
        // Green leather desk with a lamp's pool of light and a vignette.
        c.drawRect(full, g.shaded(g.linear(0f, 0f, 0f, h, intArrayOf(morseRgb(0x1A2A25), morseRgb(0x14221E), morseRgb(0x101B18), morseRgb(0x0C1513)), floatArrayOf(0f, 0.4f, 0.75f, 1f))))
        c.drawRect(full, g.shaded(g.radial(w * 0.42f, s.baseTop - 60, h * 0.55f, intArrayOf(morseRgba(214, 190, 130, 0.16), morseRgba(214, 190, 130, 0.05), 0), floatArrayOf(0f, 0.5f, 1f))))
        grains[0].draw(c, full, 0.14, gp)
        c.drawRect(full, g.shaded(g.radial(w * 0.5f, h * 0.5f, h * 0.72f, intArrayOf(0, morseRgba(2, 6, 5, 0.25), morseRgba(2, 6, 5, 0.62)), floatArrayOf(0.4f, 0.75f, 1f))))

        // The telegram card: cream paper, two shadows, a red-brown printed header and typed clue.
        val x = s.cardX
        val y = s.cardY
        val cw = s.cardW
        val ch = s.cardH
        g.rr(c, x, y + 14, cw, ch, 14f, g.blurred(0xFF000000.toInt(), 0.45, 16f))
        g.rr(c, x, y + 1.5f, cw, ch, 14f, g.blurred(0xFF000000.toInt(), 0.5, 1.5f))
        val card = RectF(x, y, x + cw, y + ch)
        c.drawRoundRect(card, 14f, 14f, g.shaded(g.linear(x, y, x + cw * 0.3f, y + ch, intArrayOf(morseRgb(0xF3EAD6), morseRgb(0xEADFC6), morseRgb(0xE0D2B5)))))
        c.save()
        clipRound(c, card, 14f)
        grains[1].draw(c, card, 0.16, gp)
        c.restore()
        val red = morseRgb(0x9A3B2A)
        fonts.typeBold.color = red
        c.drawText("TELEGRAM", x + 20, y + 28, fonts.typeBold)
        val no = "PUZZLE No. 27"
        c.drawText(no, x + cw - 20 - fonts.typeBold.measureText(no), y + 28, fonts.typeBold)
        c.drawLine(x + 20, y + 38, x + cw - 20, y + 38, g.paint(red, 0.7).apply { strokeWidth = 1.2f })
        c.drawLine(x + 20, y + 41, x + cw - 20, y + 41, g.paint(red, 0.7).apply { strokeWidth = 0.6f })
        fonts.type.color = morseRgba(0x2D, 0x2A, 0x26, 0.88)
        c.drawText("THE OPERATOR IS WAITING STOP", x + 20, y + 62, fonts.type)
        c.drawText("SAY HELLO IN TWO LETTERS STOP", x + 20, y + 80, fonts.type)
        // Answer boxes: typed outlines with a baseline.
        for (sx in s.slotX) {
            g.rr(c, sx, s.slotY, s.slotW, s.slotH, 8f, g.paint(morseRgb(0xD9CBAE), 0.55))
            g.rr(c, sx, s.slotY, s.slotW, s.slotH, 8f, g.stroke(g.paint(morseRgb(0x8C7A5C), 0.6), 1f))
            c.drawLine(sx + 12, s.slotY + s.slotH - 12, sx + s.slotW - 12, s.slotY + s.slotH - 12, g.paint(morseRgb(0x8C7A5C), 0.8).apply { strokeWidth = 1.5f })
        }

        // Wires from the binding posts up into the register: cloth-covered, one red, one green.
        val postA = s.baseX + s.baseW - 44
        val postB = s.baseX + s.baseW - 24
        val regBottom = s.regY + s.regH
        for (k in 0..1) {
            val px = if (k == 0) postA else postB
            val col = if (k == 0) morseRgb(0x7A2E24) else morseRgb(0x2F5A43)
            val dx = if (k == 0) -10f else 8f
            val path = Path()
            path.moveTo(px, s.baseTop - 20)
            path.cubicTo(px - 30 + dx, s.baseTop - 120, s.regX + s.regW / 2 + dx - 20, regBottom + 90, s.regX + s.regW / 2 + dx, regBottom - 4)
            c.save()
            c.translate(3f, 5f)
            c.drawPath(path, g.stroke(g.blurred(0xFF000000.toInt(), 0.45, 3f), 4f))
            c.restore()
            c.drawPath(path, g.stroke(g.paint(col), 3.4f, round = true))
            c.save()
            c.translate(-0.8f, -0.8f)
            c.drawPath(path, g.stroke(g.paint(0xFFFFFFFF.toInt(), 0.18), 1f))
            c.restore()
        }

        // The key's base: a walnut block seen from the front, its top face catching the lamp.
        val bx = s.baseX
        val bw = s.baseW
        val top = s.baseTop
        g.rr(c, bx + 4, top + 30, bw - 8, 44f, 10f, g.blurred(0xFF000000.toInt(), 0.6, 14f))
        g.rr(c, bx, top + 2, bw, 58f, 6f, g.blurred(0xFF000000.toInt(), 0.6, 1.5f))
        val face = RectF(bx, top + 12, bx + bw, top + 58)
        c.drawRoundRect(face, 6f, 6f, g.shaded(g.linear(0f, top + 12, 0f, top + 58, intArrayOf(morseRgb(0x5A3720), morseRgb(0x45291A), morseRgb(0x2F1C12)))))
        c.save()
        clipRound(c, face, 6f)
        grains[2].draw(c, face, 0.4, gp)
        c.restore()
        val topFace = RectF(bx + 3, top, bx + bw - 3, top + 14)
        c.drawRoundRect(topFace, 5f, 5f, g.shaded(g.linear(0f, top, 0f, top + 14, intArrayOf(morseRgb(0x8A5C3A), morseRgb(0x734A2D), morseRgb(0x5E3B23)))))
        c.save()
        clipRound(c, topFace, 5f)
        grains[3].draw(c, RectF(bx, top, bx + bw, top + 14), 0.35, gp)
        c.restore()
        c.drawLine(bx + 8, top + 0.6f, bx + bw - 8, top + 0.6f, g.paint(morseRgb(0xFFD9AE), 0.35))
        // A small engraved brass plate on the front.
        val plateW = 92f
        val px0 = bx + bw / 2 - plateW / 2
        g.rr(c, px0, top + 26, plateW, 18f, 3f, g.shaded(g.linear(px0, 0f, px0 + plateW, 0f, BRASS, BRASS_POS)))
        val plate = "DOT DASH  No. 3"
        fonts.small.color = morseRgba(0x3B, 0x2A, 0x12, 0.85)
        c.drawText(plate, px0 + plateW / 2 - fonts.small.measureText(plate) / 2, top + 38.5f, fonts.small)
        // Lower contact: a silver post with a domed tip.
        val cx = s.contactX
        val tipY = s.leverY + 16
        c.drawRect(cx - 5, tipY + 3, cx + 5, tipY + 3 + (top - tipY), g.shaded(g.linear(cx - 5, 0f, cx + 5, 0f, intArrayOf(morseRgb(0x6F7378), morseRgb(0xE4E7EA), morseRgb(0x9EA3A8), morseRgb(0x55595E)), floatArrayOf(0f, 0.35f, 0.65f, 1f))))
        c.drawOval(RectF(cx - 7, top - 4, cx + 7, top + 3), g.shaded(g.linear(cx - 7, 0f, cx + 7, 0f, BRASS, BRASS_POS)))
        c.drawOval(RectF(cx - 4, tipY, cx + 4, tipY + 6), g.shaded(g.radial(cx - 1, tipY + 2, 5f, intArrayOf(morseRgb(0xFFFFFF), morseRgb(0xB9BEC4), morseRgb(0x6B7075)))))
        // Trunnion: a brass upright with the axle's screw head.
        val tx = s.pivotX
        g.rr(c, tx - 9, s.leverY - 12, 18f, top - s.leverY + 14, 4f, g.shaded(g.linear(tx - 9, 0f, tx + 9, 0f, BRASS, BRASS_POS)))
        // Binding posts: knurled brass with a dark collar.
        for (px in floatArrayOf(postA, postB)) {
            c.drawRect(px - 6, top - 22, px + 6, top + 2, g.shaded(g.linear(px - 6, 0f, px + 6, 0f, BRASS, BRASS_POS)))
            val knurl = g.paint(morseRgb(0x4A3412), 0.5).apply { strokeWidth = 0.8f }
            var k = -4
            while (k <= 4) {
                c.drawLine(px + k, top - 20, px + k, top - 12, knurl)
                k += 2
            }
            c.drawRect(px - 7, top - 6, px + 7, top - 1, g.paint(morseRgb(0x2A1E12)))
        }
    }

    /** The register the tape comes out of. */
    private fun register(c: Canvas, s: MorseScene, fonts: MorseFonts, g: MorseKeyInputGfx) {
        val x = s.regX
        val y = s.regY
        val w = s.regW
        val h = s.regH
        g.rr(c, x - 2, y + 16, w + 4, h, 10f, g.blurred(0xFF000000.toInt(), 0.55, 14f))
        g.rr(c, x, y + 1.5f, w, h, 10f, g.blurred(0xFF000000.toInt(), 0.6, 1.5f))
        g.rr(c, x, y, w, h, 10f, g.shaded(g.linear(0f, y, 0f, y + h, intArrayOf(morseRgb(0x2B3330), morseRgb(0x1C2321), morseRgb(0x141A18)))))
        g.rr(c, x + 1, y + 1, w - 2, h - 2, 9f, g.stroke(g.shaded(g.linear(x, 0f, x + w, 0f, BRASS, BRASS_POS)), 2f))
        // The window over the paper reel (the reel itself is drawn each frame).
        val wy = y + 10
        c.drawCircle(x + w / 2, wy + 24, 25f, g.paint(morseRgb(0x0A0E0D)))
        c.drawCircle(x + w / 2, wy + 24, 26f, g.stroke(g.shaded(g.linear(x, 0f, x + w, 0f, BRASS, BRASS_POS)), 3f))
        // The slot the tape comes out of, at the tape's height on the left edge.
        c.drawRect(x - 1, s.tapeY - s.tapeH / 2 - 3, x + 7, s.tapeY + s.tapeH / 2 + 3, g.paint(morseRgb(0x0A0E0D)))
        val label = "REGISTER"
        fonts.small.color = morseRgba(0xD8, 0xBC, 0x80, 0.75)
        c.drawText(label, x + w / 2 - fonts.small.measureText(label) / 2, y + h - 12, fonts.small)
    }
}

/** Draws the moving parts each frame; holds its reusable paints and paths. */
internal class MorsePainter(private val fonts: MorseFonts) {
    private val g = MorseKeyInputGfx()
    private val imagePaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val src = Rect()
    private val dst = RectF()
    private val strip = Path()
    private val ink = Path()
    private val spring = Path()
    private val lines = Path()
    private val rect = RectF()
    private val slotPaint = Paint(fonts.slot)
    private val chipPaint = Paint(fonts.chip)

    fun frame(c: Canvas, s: MorseScene, layers: MorseLayers, pose: MorsePose) {
        src.set(0, 0, layers.desk.width, layers.desk.height)
        dst.set(0f, 0f, s.w, s.h)
        c.drawBitmap(layers.desk, src, dst, imagePaint)
        answer(c, s, pose)
        strip(c, s, pose.tape, true)
        if (pose.fall > 0 && pose.fall < 1) {
            // Torn off at the head: it drops, swings away from the tear and fades.
            val k = pose.fall.toFloat()
            val alpha = 1 - clamp01((k - 0.45) / 0.55)
            c.saveLayerAlpha(null, (255 * alpha).roundToInt())
            c.translate(-30 * k, 300 * k * k)
            c.rotate(-10 * k, s.headX, s.tapeY)
            strip(c, s, pose.torn, true)
            c.restore()
        }
        src.set(0, 0, layers.register.width, layers.register.height)
        c.drawBitmap(layers.register, src, s.registerRect, imagePaint)
        reel(c, s, pose.tape.run)
        // The pen: an inked wheel in the slot, down on the tape while the contact is closed.
        val penY = (s.tapeY - 2 + 2 * pose.pen).toFloat()
        c.drawCircle(s.headX + 4, penY, 4f, g.shaded(g.radial(s.headX + 3, penY - 1, 5f, intArrayOf(morseRgb(0x8A8F96), morseRgb(0x2A2E36)))))
        spring(c, s, pose.depth)
        lever(c, s, pose.depth)
        spark(c, s, pose)
    }

    /** One strip of tape whose free end is `len` to the left of the head, with its ink. */
    private fun strip(c: Canvas, s: MorseScene, st: MorseStrip, torn: Boolean) {
        if (st.len < 0.5) return
        val x1 = s.headX + 4
        val x0 = (s.headX - st.len).toFloat()
        val top = s.tapeY - s.tapeH / 2
        val bottom = s.tapeY + s.tapeH / 2
        // The free end is torn: a jagged edge, seeded so it never flickers.
        strip.rewind()
        strip.moveTo(x1, top)
        strip.lineTo(x0 + 2, top)
        for (i in 0..8) {
            val y = top + s.tapeH * i / 8
            strip.lineTo(x0 + ((hash(i + 3.3) - 0.5) * 4 + if (torn) (hash(i * 7.1) - 0.5) * 3 else 0.0).toFloat(), y)
        }
        strip.lineTo(x1, bottom)
        strip.close()
        c.save()
        c.translate(0f, 5f)
        c.drawPath(strip, g.blurred(0xFF000000.toInt(), 0.45, 4f))
        c.restore()
        c.drawPath(strip, g.shaded(g.linear(0f, top, 0f, bottom, intArrayOf(morseRgb(0xF4ECD9), morseRgb(0xEDE3CC), morseRgb(0xE2D5B8)))))
        val edge = g.paint(morseRgb(0xB9A984), 0.6).apply { strokeWidth = 0.8f }
        c.drawLine(max(x0, -10f), top + 0.5f, x1, top + 0.5f, edge)
        c.drawLine(max(x0, -10f), bottom - 0.5f, x1, bottom - 0.5f, edge)
        // Ink: each mark sits where its run has carried it, a little bleed around the stroke.
        ink.rewind()
        val m = st.marks
        var i = 0
        while (i + 1 < st.count) {
            val a = s.headX - (st.run - m[i])
            val e = s.headX - (st.run - m[i + 1])
            val mid = (a + e) / 2
            val half = max(3.8, abs(e - a) / 2)
            i += 2
            if (mid + half < -10 || mid - half > s.headX) continue
            rect.set((mid - half).toFloat(), s.tapeY - 3.8f, (mid + half).toFloat(), s.tapeY + 3.8f)
            ink.addRoundRect(rect, 3.8f, 3.8f, Path.Direction.CW)
        }
        c.drawPath(ink, g.blurred(morseRgb(0x1D2230), 0.3, 1.6f))
        c.drawPath(ink, g.paint(morseRgb(0x1D2230), 0.92))
    }

    /** The lever group, rotated about the trunnion: bar, knob, upper contact screw. */
    private fun lever(c: Canvas, s: MorseScene, depth: Double) {
        val angle = (s.tilt * depth).toFloat()
        val y = s.leverY
        // Its shadow on the base's top face moves with it.
        g.rr(c, s.knobX - 20, s.baseTop - 3, s.pivotX - s.knobX + 40, 8f, 4f, g.blurred(0xFF000000.toInt(), 0.4, 6f))
        c.save()
        c.rotate(-angle, s.pivotX, y)
        // Bar: brass with a bright upper edge.
        g.rr(c, s.knobX - 18, y - 7, s.pivotX - s.knobX + 46, 14f, 4f, g.shaded(g.linear(0f, y - 7, 0f, y + 7, intArrayOf(morseRgb(0xF2DDA6), morseRgb(0xC9A15A), morseRgb(0x94702F), morseRgb(0x5F451B)), floatArrayOf(0f, 0.35f, 0.7f, 1f))))
        c.drawLine(s.knobX - 14, y - 6.4f, s.pivotX + 24, y - 6.4f, g.paint(morseRgb(0xFFF3D0), 0.5))
        // Axle boss at the pivot.
        c.drawCircle(s.pivotX, y, 8f, g.shaded(g.radial(s.pivotX - 2, y - 2, 9f, intArrayOf(morseRgb(0xF5E2AE), morseRgb(0xB08A44), morseRgb(0x5E451B)))))
        c.drawCircle(s.pivotX, y, 2.2f, g.paint(morseRgb(0x3A2A10)))
        // Upper contact screw under the bar.
        c.drawRect(s.contactX - 3.5f, y + 7, s.contactX + 3.5f, y + 16, g.shaded(g.linear(s.contactX - 3.5f, 0f, s.contactX + 3.5f, 0f, intArrayOf(morseRgb(0x7A7F84), morseRgb(0xE8EBEE), morseRgb(0x62666B)))))
        rect.set(s.contactX - 4, y + 13, s.contactX + 4, y + 17)
        c.drawOval(rect, g.paint(morseRgb(0xC8CDD2)))
        // Knob: a shaft and a black bakelite mushroom with a soft top highlight.
        val kx = s.knobX
        c.drawRect(kx - 5, y - 24, kx + 5, y - 4, g.shaded(g.linear(kx - 5, 0f, kx + 5, 0f, BRASS, BRASS_POS)))
        g.rr(c, kx - 29, y - 52, 58f, 30f, 13f, g.shaded(g.linear(kx - 29, 0f, kx + 29, 0f, intArrayOf(morseRgb(0x0F0D0C), morseRgb(0x2C2724), morseRgb(0x3A3430), morseRgb(0x1A1715), morseRgb(0x0B0A09)), floatArrayOf(0f, 0.3f, 0.42f, 0.7f, 1f))))
        rect.set(kx - 33, y - 64, kx + 33, y - 42)
        c.drawOval(rect, g.shaded(g.radial(kx - 10, y - 59, 36f, intArrayOf(morseRgb(0x4A433E), morseRgb(0x24201D), morseRgb(0x121010)))))
        rect.set(kx - 20, y - 62, kx + 6, y - 55)
        c.drawOval(rect, g.paint(0xFFFFFFFF.toInt(), 0.16))
        c.restore()
    }

    private fun spring(c: Canvas, s: MorseScene, depth: Double) {
        val drop = ((s.pivotX - s.springX) * sin(s.tilt * depth * PI / 180)).toFloat()
        val top = s.leverY + 6 + drop
        val bottom = s.baseTop + 1
        val turns = 9
        spring.rewind()
        spring.moveTo(s.springX, top)
        for (i in 0..turns * 2) {
            val yy = top + (bottom - top) * i / (turns * 2)
            spring.lineTo(s.springX + if (i % 2 == 0) -6 else 6, yy)
        }
        c.drawPath(spring, g.stroke(g.paint(morseRgb(0xB8934E)), 1.6f))
        c.drawPath(spring, g.stroke(g.paint(morseRgb(0xFFF0C8), 0.45), 0.6f))
    }

    private fun spark(c: Canvas, s: MorseScene, pose: MorsePose) {
        val x = s.contactX
        val y = s.leverY + 17
        val age = pose.sparkAge
        if (age >= 0 && age < 0.25) {
            val flash = exp(-age * 24)
            val glow = g.shaded(g.radial(x, y, 34f, intArrayOf(morseRgba(200, 225, 255, 0.95), morseRgba(120, 170, 255, 0.35), morseRgba(80, 120, 255, 0.0)), floatArrayOf(0f, 0.35f, 1f)), flash)
            glow.xfermode = MorseKeyInputGfx.plus
            c.drawCircle(x, y, 34f, glow)
            lines.rewind()
            for (i in 0 until 9) {
                val ang = (-170 + 160 * hash(i * 2.3)) * (PI / 180)
                val sp = 70 + 160 * hash(i * 5.1)
                val life = 0.1 + 0.12 * hash(i * 9.7)
                if (age > life) continue
                val a0 = max(0.0, age - 0.025)
                lines.moveTo((x + cos(ang) * sp * a0).toFloat(), (y + sin(ang) * sp * a0 + 300 * a0 * a0).toFloat())
                lines.lineTo((x + cos(ang) * sp * age).toFloat(), (y + sin(ang) * sp * age + 300 * age * age).toFloat())
            }
            val sp = g.stroke(g.paint(morseRgb(0xE6F0FF)), 1.3f, round = true)
            sp.xfermode = MorseKeyInputGfx.plus
            c.drawPath(lines, sp)
        }
        if (pose.hum > 0) {
            val hum = g.shaded(g.radial(x, y, 12f, intArrayOf(morseRgba(170, 200, 255, 0.55), morseRgba(170, 200, 255, 0.0))))
            hum.xfermode = MorseKeyInputGfx.plus
            c.drawCircle(x, y, 12f, hum)
        }
    }

    private fun reel(c: Canvas, s: MorseScene, run: Double) {
        val cx = s.regX + s.regW / 2
        val cy = s.regY + 34
        c.drawCircle(cx, cy, 21f, g.shaded(g.radial(cx - 4, cy - 4, 24f, intArrayOf(morseRgb(0xF2EAD8), morseRgb(0xE2D5BA), morseRgb(0xBFAF8E)))))
        // Paper layers and three spokes turning as the tape pays out.
        val rings = g.stroke(g.paint(morseRgb(0xA8987A), 0.45), 0.5f)
        var r = 9f
        while (r < 21) {
            c.drawCircle(cx, cy, r, rings)
            r += 3
        }
        c.save()
        c.rotate((-run / 21 * (180 / PI)).toFloat(), cx, cy)
        val spoke = g.paint(morseRgb(0x6E5C3E)).apply { strokeWidth = 2f }
        for (i in 0 until 3) {
            val a = i * 2 * PI / 3
            c.drawLine(cx, cy, cx + (cos(a) * 8).toFloat(), cy + (sin(a) * 8).toFloat(), spoke)
        }
        c.restore()
        c.drawCircle(cx, cy, 3f, g.shaded(g.radial(cx - 1, cy - 1, 4f, intArrayOf(morseRgb(0xF5E2AE), morseRgb(0x8A6A2E)))))
        // Glass reflection over the window.
        c.drawCircle(cx, cy, 24f, g.shaded(g.linear(cx - 24, cy - 24, cx + 10, cy + 10, intArrayOf(morseRgba(255, 255, 255, 0.22), morseRgba(255, 255, 255, 0.04), morseRgba(255, 255, 255, 0.0)), floatArrayOf(0f, 0.45f, 1f))))
    }

    private fun answer(c: Canvas, s: MorseScene, pose: MorsePose) {
        // Decoded letters roll up into their boxes; clearing, they roll on up and out.
        for (i in 0 until 2) {
            val sl = pose.slots[i]
            if (sl.ch.isEmpty() || sl.into <= 0.001) continue
            val tw = slotPaint.measureText(sl.ch)
            val x = s.slotX[i] + s.slotW / 2 - tw / 2
            val base = s.slotY + s.slotH - 17
            val dy = ((1 - sl.into) * 18 - sl.out * 20).toFloat()
            val a = clamp01(sl.into * 1.4) * (1 - sl.out)
            if (a <= 0.001) continue
            c.save()
            c.clipRect(s.slotX[i] + 2, s.slotY + 2, s.slotX[i] + s.slotW - 2, s.slotY + s.slotH - 2)
            val stamp = (1 + 0.12 * (1 - sl.into)).toFloat()
            c.translate(x + tw / 2, base + dy)
            c.scale(stamp, stamp)
            slotPaint.color = morseRgba(0x23, 0x20, 0x1C, a)
            c.drawText(sl.ch, -tw / 2, 0f, slotPaint)
            c.restore()
        }
        // The letter in progress: its dots and dashes next to the boxes.
        if (pose.bufferAlpha > 0 && pose.buffer.isNotEmpty()) {
            val y = s.slotY + s.slotH / 2
            var x = s.slotX[1] + s.slotW + 18
            val p = g.paint(morseRgb(0x9A3B2A), 0.9)
            for (ch in pose.buffer) {
                if (ch == '.') {
                    c.drawCircle(x + 3, y, 3.4f, p)
                    x += 14
                } else {
                    g.rr(c, x, y - 3, 18f, 6f, 3f, p)
                    x += 26
                }
            }
        }
        // Result chip on the right of the row.
        if (pose.chip > 0.005) {
            val label = if (pose.correct) "Correct · +40" else "Not quite"
            val tw = chipPaint.measureText(label)
            val w = tw + 28
            val x = s.cardX + s.cardW - 20 - w
            val y = (s.slotY + s.slotH / 2 - 15 + (1 - pose.chip) * 6).toFloat()
            val col = if (pose.correct) morseRgb(0x2F6B4A) else morseRgb(0x9A3B2A)
            g.rr(c, x, y, w, 30f, 15f, g.paint(col, 0.92 * pose.chip))
            chipPaint.color = morseRgba(0xF6, 0xEE, 0xDC, pose.chip)
            c.drawText(label, x + 14, y + 19.5f, chipPaint)
        }
    }
}
