package com.ngocdevv.nativecook.blocks.onboarding.ghostfloatwelcome

import android.content.Context
import android.graphics.*
import androidx.core.graphics.PathParser
import kotlin.math.*
import com.ngocdevv.nativecook.blocks.onboarding.ghostfloatwelcome.GhostFloatWelcomeTheme.C
import com.ngocdevv.nativecook.blocks.onboarding.ghostfloatwelcome.GhostFloatWelcomeTheme.UI
import com.ngocdevv.nativecook.blocks.onboarding.ghostfloatwelcome.GhostFloatWelcomeMotion.Frame

/** Canvas geometry stays in the reference's 402 × 874 space, including native glyph advances. */
internal class GhostFloatWelcomeArt(private val context: Context) {
    private val blur = GhostFloatWelcomeBlur()
    private val root = "ghostfloatwelcome/ghostfloatwelcome_"

    private fun face(name: String) = Typeface.createFromAsset(context.assets, root + name)
    private val regular = face("Manrope_400Regular.ttf")
    private val semi = face("Manrope_600SemiBold.ttf")
    private val bold = face("Manrope_700Bold.ttf")
    private val dino = context.assets.open(root + "avatar-dino.webp").use {
        requireNotNull(BitmapFactory.decodeStream(it))
    }
    private val penguin = context.assets.open(root + "avatar-penguin.webp").use {
        requireNotNull(BitmapFactory.decodeStream(it))
    }
    private val star = path("M0 -10 C1.2 -3.2 3.2 -1.2 10 0 C3.2 1.2 1.2 3.2 0 10 C-1.2 3.2 -3.2 1.2 -10 0 C-3.2 -1.2 -1.2 -3.2 0 -10 Z")
    private val glint = path("M0 -10 Q1.6 -1.6 10 0 Q1.6 1.6 0 10 Q-1.6 1.6 -10 0 Q-1.6 -1.6 0 -10 Z")
    private val check = path("M-5.2 0.2 L-1.6 3.8 L5.4 -3.6")
    private val lumen = Path().apply {
        addCircle(0f, 0f, 8.6f, Path.Direction.CW)
        op(Path().apply {
            addCircle(4.6f, -3.4f, 7.4f, Path.Direction.CW)
        }, Path.Op.DIFFERENCE)
    }
    private val ember = path("M0.5 -11 C5.5 -5.5 9 -1.5 8.2 3.8 C7.5 8.6 4 11 0 11 C-4.2 11 -7.8 8.4 -8 4 C-8.2 0.4 -5.8 -2 -4.6 -5 C-3.4 -2.6 -1.8 -1.2 -0.8 -1.4 C-1.2 -4.6 -1.4 -7.8 0.5 -11 Z M0.2 3.4 C-1.6 5.4 -2.6 6.6 -2.4 8 C-2.2 9.2 -1 9.8 0.2 9.8 C1.6 9.8 2.8 9 2.8 7.6 C2.8 6 1.6 4.8 0.2 3.4 Z").apply {
        fillType = Path.FillType.EVEN_ODD
    }
    private val fern = path("M-8.5 8.5 C-9.6 -2.8 -1.6 -9.4 9 -9 C9.6 1.6 2.6 9.6 -8.5 8.5 Z")
    private val fernRib = path("M-8.5 8.5 L3.6 -3.6 M-2.6 2.6 L-2.4 -3.2 M0.8 -0.8 L5.2 -0.6")
    private val halo = Path().apply {
        addCircle(-3.6f, 0f, 6f, Path.Direction.CW)
        addCircle(3.6f, 0f, 6f, Path.Direction.CW)
    }

    private fun path(svg: String): Path = requireNotNull(PathParser.createPathFromPathData(svg))

    private fun color(rgb: Int, alpha: Double = 1.0) = ((alpha.coerceIn(0.0, 1.0) * 255).roundToInt() shl 24) or rgb

    private fun paint(rgb: Int, alpha: Double = 1.0) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = color(rgb, alpha)
    }

    private fun stroke(rgb: Int, width: Double, alpha: Double = 1.0) = paint(rgb, alpha).apply {
        style = Paint.Style.STROKE
        strokeWidth = width.toFloat()
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private fun blend(a: Int, b: Int, p: Double): Int {
        val channels = intArrayOf(16, 8, 0).map { shift -> (((a shr shift) and 255) + (((b shr shift) and 255) - ((a shr shift) and 255)) * p).roundToInt().coerceIn(0, 255)
        }
        return (channels[0] shl 16) or (channels[1] shl 8) or channels[2]
    }

    private fun circle(c: Canvas, x: Double, y: Double, r: Double, p: Paint) = c.drawCircle(x.toFloat(), y.toFloat(), r.toFloat(), p)

    private fun oval(c: Canvas, x: Double, y: Double, w: Double, h: Double, p: Paint) = c.drawOval(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat(), p)

    private fun transform(c: Canvas, x: Double, y: Double, rotation: Double = 0.0, sx: Double = 1.0, sy: Double = sx, draw: () -> Unit) {
        c.save()
        c.translate(x.toFloat(), y.toFloat())
        c.rotate(rotation.toFloat())
        c.scale(sx.toFloat(), sy.toFloat())
        draw()
        c.restore()
    }
    private data class Label(val advances: FloatArray, val width: Double)
    private val labels = mutableMapOf<String, Label>()

    // drawGlyphs in RN positions individual glyphs by their advances, without pair kerning.
    private fun label(c: Canvas, text: String, size: Double, face: Typeface, x: Double, cy: Double, anchor: Double, rgb: Int): Double {
        val p = paint(rgb).apply {
            textSize = size.toFloat()
            typeface = face
            isSubpixelText = true
        }
        val label = labels.getOrPut(text) {
            val advances = FloatArray(text.length)
            val single = FloatArray(1)
            text.indices.forEach { index ->
                // Shape each glyph alone: a whole run can apply pair kerning unlike SkFont.
                p.getTextWidths(text, index, index + 1, single)
                advances[index] = single[0]
            }
            Label(advances, advances.fold(0.0) { total, advance -> total + advance })
        }
        var cursor = x - label.width * anchor
        text.forEachIndexed { i, ch ->
            c.drawText(ch.toString(), cursor.toFloat(), (cy + size * 0.36).toFloat(), p)
            cursor += label.advances[i]
        }
        return label.width
    }

    fun drawScene(c: Canvas, width: Float, height: Float, f: Frame, d: GhostFloatWelcomeDriver, extra: GhostFloatWelcomeDriver.Extra, tiltX: Double, tiltY: Double) {
        c.drawColor(color(C.bg))
        val scale = width / 402f
        val dy = (height - 874 * scale) / 2
        c.save()
        c.translate(0f, dy)
        c.scale(scale, scale)
        GhostFloatWelcomeTheme.stars.forEachIndexed { i, s ->
            val pose = GhostFloatWelcomeMotion.starPose(i, f.t, d.amb)
            val j = 7 + i
            transform(c, pose.x + d.ox[j] - tiltX * 10 * s.depth, pose.y + d.oy[j] - tiltY * 8 * s.depth, pose.rot + d.or[j], s.s / 10) {
                c.drawPath(star, paint(C.star))
            }
        }
        val gp = GhostFloatWelcomeMotion.bodyPose(0, f.t, d.amb)
        val gx = gp.x + d.ox[0] - tiltX * 12
        val gy = gp.y + d.oy[0] - tiltY * 9
        ring(c, gx, gy, f.ring, 0)
        ring(c, gx, gy, extra.ring, 1)
        for (i in 1 until 7) {
            val b = GhostFloatWelcomeTheme.bodies[i]
            val p = GhostFloatWelcomeMotion.bodyPose(i, f.t, d.amb)
            val x = p.x + d.ox[i] - tiltX * 12 * b.depth
            val y = p.y + d.oy[i] - tiltY * 9 * b.depth
            val speed = hypot(d.vx[i], d.vy[i])
            if (speed > 260) {
                val amount = min(1.0, (speed - 260) / 900)
                val ex = x - d.vx[i] * 0.07
                val ey = y - d.vy[i] * 0.07
                val streak = stroke(0xd9d1ff, b.r * 1.1).apply {
                    shader = LinearGradient(x.toFloat(), y.toFloat(), ex.toFloat(), ey.toFloat(), intArrayOf(color(0xd9d1ff, 0.22 * amount), color(0xd9d1ff, 0.0)), null, Shader.TileMode.CLAMP)
                }
                c.drawLine(x.toFloat(), y.toFloat(), ex.toFloat(), ey.toFloat(), streak)
            }
            token(c, i, x, y, p.rot + d.or[i])
        }
        GhostFloatWelcomeTheme.glints.forEachIndexed { k, gl ->
            val b = GhostFloatWelcomeTheme.bodies[gl.body]
            val p = GhostFloatWelcomeMotion.bodyPose(gl.body, f.t, d.amb)
            val tw = 0.5 + 0.5 * sin(GhostFloatWelcomeMotion.TAU * ((2 + k % 2) * d.amb / 9 + k * 0.27))
            transform(c, p.x + d.ox[gl.body] - tiltX * 12 * b.depth + gl.dx, p.y + d.oy[gl.body] - tiltY * 9 * b.depth + gl.dy,
            12 * sin(GhostFloatWelcomeMotion.TAU * (d.amb / 9 + k * 0.2)), gl.s * (0.7 + 0.3 * tw) / 10) {
                c.drawPath(glint, paint(C.white, 0.75 + 0.25 * tw))
            }
        }
        ghost(c, f, d, extra, gx, gy, gp.rot + d.or[0])
        controls(c, f, d, extra)
        c.restore()
    }

    private fun token(c: Canvas, i: Int, x: Double, y: Double, rot: Double) {
        val b = GhostFloatWelcomeTheme.bodies[i]
        transform(c, x, y) {
            if (b.kind == "dino" || b.kind == "penguin") {
                val image = if (b.kind == "dino") dino else penguin
                val clip = Path().apply {
                    addCircle(0f, 0f, b.r.toFloat(), Path.Direction.CW)
                }
                c.save()
                c.clipPath(clip)
                c.rotate((rot * 0.5).toFloat())
                val radius = (b.r * 1.04).toFloat()
                c.drawBitmap(image, null, RectF(-radius, -radius, radius, radius), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
                c.restore()
            } else {
                val tk = GhostFloatWelcomeTheme.tokens.getValue(b.kind)
                circle(c, 0.0, 0.0, b.r, paint(tk.first))
                val highlight = paint(C.white).apply {
                    shader = RadialGradient((-b.r * 0.4).toFloat(), (-b.r * 0.5).toFloat(), (b.r * 1.3).toFloat(), intArrayOf(color(C.white, 0.16), color(C.white, 0.0)), null, Shader.TileMode.CLAMP)
                }
                circle(c, 0.0, 0.0, b.r, highlight)
                transform(c, 0.0, 0.0, rot, b.r / 24 * 1.55) {
                    when (b.kind) {
                        "lumen" -> c.drawPath(lumen, paint(tk.second))
                        "ember" -> c.drawPath(ember, paint(tk.second))
                        "fern" -> {
                            c.drawPath(fern, paint(tk.second))
                            c.drawPath(fernRib, stroke(tk.first, 1.3))
                        }
                        "halo" -> c.drawPath(halo, stroke(tk.second, 2.4).apply {
                            strokeCap = Paint.Cap.BUTT
                        })
                    }
                }
            }
        }
    }

    private fun ghost(c: Canvas, f: Frame, d: GhostFloatWelcomeDriver, e: GhostFloatWelcomeDriver.Extra, x: Double, y: Double, rot: Double) {
        val ph = GhostFloatWelcomeMotion.TAU * 4 * d.amb / 9
        val puff = f.puff + e.puff
        val sx = 1 + 0.07 * f.inhale - 0.1 * puff
        val sy = 1 - 0.1 * f.inhale + 0.16 * puff
        val hop = f.hop + e.hop
        val alpha = 0.025 + 0.02 * sin(GhostFloatWelcomeMotion.TAU * 2 * d.amb / 9) + 0.12 * max(0.0, puff)
        blur.glow(c, x, y - hop, alpha)
        c.save()
        c.translate(x.toFloat(), (y - hop + 30).toFloat())
        c.scale(sx.toFloat(), sy.toFloat())
        c.translate(0f, -30f)
        c.rotate((rot - 8 + f.roll + e.roll).toFloat())
        val w0 = 3.2 * sin(ph)
        val w1 = 3.2 * sin(ph + 2.1)
        val w2 = 3.2 * sin(ph + 4.2)
        val g = Path().apply {
            moveTo(-38f, 14f)
            cubicTo(-40f, -14f, -20f, -34f, 2f, -34f)
            cubicTo(26f, -34f, 40f, -14f, 38f, 10f)
            lineTo(38f, 20f)
            quadTo(28f, (36 + w0).toFloat(), 14f, 24f)
            quadTo(1f, (36 + w1).toFloat(), -12f, 23f)
            quadTo(-28f, (33 + w2).toFloat(), -38f, 14f)
            close()
        }
        c.drawPath(g, paint(C.ghost))
        val ink = paint(C.eye)
        val lx = d.lookX * 3.2
        val ly = d.lookY * 2.6
        val wide = max(f.wide, e.wide)
        val happy = f.happy
        repeat(2) { eye ->
            val ex = (if (eye == 0) 7 else 20) + lx
            val ey = -9 + ly
            if (happy > 0.5) {
                val arc = Path().apply {
                    moveTo((ex - 4.6).toFloat(), (ey + 2).toFloat())
                    quadTo(ex.toFloat(), (ey - 5.5).toFloat(), (ex + 4.6).toFloat(), (ey + 2).toFloat())
                }
                c.drawPath(arc, stroke(C.eye, 3.0, (happy - 0.5) * 2))
            } else {
                val k = 1 + 0.32 * wide
                val open = max(0.08, 1 - f.blink) * (1 - 0.35 * f.inhale) * (if (1 - happy * 2 > 0) 1 - happy * 2 else 0.05)
                oval(c, ex - 4.3 * k, ey - 6.3 * k * open, 8.6 * k, 12.6 * k * open, ink)
            }
        }
        val mouth = max(wide, max(0.0, puff)) * (1 - happy)
        if (mouth > 0.05) oval(c, 13.5 + lx - 3.4 * mouth, 4 + ly - 4 * mouth, 6.8 * mouth, 8 * mouth, ink)
        c.restore()
    }

    private fun ring(c: Canvas, x: Double, y: Double, p: Double, slot: Int) {
        if (p < 0) return
        val q = 1 - (1 - p).pow(3)
        blur.ring(c, x, y, GhostFloatWelcomeMotion.mix(30.0, 270.0, q),
            GhostFloatWelcomeMotion.mix(14.0, 1.5, q), 0.3 * (1 - p), slot)
    }

    private fun controls(c: Canvas, f: Frame, d: GhostFloatWelcomeDriver, e: GhostFloatWelcomeDriver.Extra) {
        label(c, "Welcome to Wisp", 29.0, bold, 201.0, UI.titleY, 0.5, C.white)
        label(c, "To get started, create a new wallet or", 17.6, regular, 201.0, UI.sub1Y, 0.5, C.sub)
        label(c, "import an existing one.", 17.6, regular, 201.0, UI.sub2Y, 0.5, C.sub)
        val total = UI.pillW + (UI.dotN - 1) * UI.dotGap
        var x = 201 - total / 2
        c.drawRoundRect(x.toFloat(), (UI.dotsY - UI.dotH / 2).toFloat(), (x + UI.pillW).toFloat(), (UI.dotsY + UI.dotH / 2).toFloat(), (UI.dotH / 2).toFloat(), (UI.dotH / 2).toFloat(), paint(C.pill))
        x += UI.pillW + UI.dotGap - UI.dotH / 2
        for (i in 1 until UI.dotN) {
            circle(c, x, UI.dotsY, UI.dotH / 2, paint(C.dot))
            x += UI.dotGap
        }
        val agree = if (d.agree >= 0) d.agree else f.tick
        transform(c, UI.checkX, UI.checkY, sx = 1 - 0.12 * f.press) {
            circle(c, 0.0, 0.0, UI.checkR - 0.85, stroke(blend(C.ring, C.lavender, agree), 1.7))
            if (agree > 0.001) {
                circle(c, 0.0, 0.0, (UI.checkR - 0.2) * (0.6 + 0.4 * min(1.0, agree * 1.3)), paint(C.lavender, min(1.0, agree * 1.4)))
                val measure = PathMeasure(check, false)
                val trimmed = Path()
                measure.getSegment(0f, (measure.length * ((agree - 0.25) / 0.75).coerceIn(0.0, 1.0)).toFloat(), trimmed, true)
                c.drawPath(trimmed, stroke(C.bg, 2.4))
            }
        }
        if (e.noP >= 0) circle(c, UI.checkX, UI.checkY, UI.checkR + 10 * e.noP, stroke(C.lavender, 2.0, 0.7 * (1 - e.noP)))
        val agreeWidth = label(c, "I agree to the ", 17.4, regular, UI.agreeX, UI.checkY, 0.0, C.grey)
        label(c, "Terms of Service", 17.4, semi, UI.agreeX + agreeWidth, UI.checkY, 0.0, C.lavender)
        val button = if (d.agree >= 0) d.agree else f.button
        val bs = 1 - 0.04 * d.btnPress
        transform(c, UI.buttonX + UI.buttonW / 2 + 7 * e.no, UI.buttonY + UI.buttonH / 2, sx = bs) {
            val rect = RectF((-UI.buttonW / 2).toFloat(), (-UI.buttonH / 2).toFloat(), (UI.buttonW / 2).toFloat(), (UI.buttonH / 2).toFloat())
            val radius = (UI.buttonH / 2).toFloat()
            c.drawRoundRect(rect, radius, radius, paint(blend(C.btnOff, C.btnOn, button)))
            if (f.sheen >= 0 && d.agree < 0) {
                val sx = GhostFloatWelcomeMotion.mix(-UI.buttonW / 2 - 60, UI.buttonW / 2 + 60, f.sheen).toFloat()
                val sheen = paint(C.white).apply {
                    shader = LinearGradient(sx - 50, 0f, sx + 50, 10f, intArrayOf(color(C.white, 0.0), color(C.white, 0.35), color(C.white, 0.0)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
                    xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
                }
                c.save()
                c.clipPath(Path().apply {
                    addRoundRect(rect, radius, radius, Path.Direction.CW)
                })
                c.drawRect(rect, sheen)
                c.restore()
            }
            label(c, "Create a new wallet", 17.4, bold, 0.0, 0.0, 0.5, blend(C.btnOffInk, C.btnOnInk, button))
        }
        label(c, "I already have a wallet", 17.2, semi, 201.0, UI.haveY, 0.5, C.have)
        circle(c, UI.helpX, UI.helpY, UI.helpR, stroke(C.have, 1.5))
        label(c, "?", 12.5, semi, UI.helpX, UI.helpY, 0.5, C.have)
    }
}

