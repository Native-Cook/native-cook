package com.ngocdevv.nativecook.blocks.cards.sunuvbuddy

import android.content.Context
import android.graphics.*
import android.os.Build
import kotlin.math.*
import com.ngocdevv.nativecook.blocks.cards.sunuvbuddy.SunUvBuddyMotion.Live
import com.ngocdevv.nativecook.blocks.cards.sunuvbuddy.SunUvBuddyMotion.LOOP
import com.ngocdevv.nativecook.blocks.cards.sunuvbuddy.SunUvBuddyMotion.barValue
import com.ngocdevv.nativecook.blocks.cards.sunuvbuddy.SunUvBuddyMotion.mix
import com.ngocdevv.nativecook.blocks.cards.sunuvbuddy.SunUvBuddyMotion.smooth
import com.ngocdevv.nativecook.blocks.cards.sunuvbuddy.SunUvBuddyMotion.uvAt
import com.ngocdevv.nativecook.blocks.cards.sunuvbuddy.SunUvBuddyTheme.C
import com.ngocdevv.nativecook.blocks.cards.sunuvbuddy.SunUvBuddyTheme.Copy
import com.ngocdevv.nativecook.blocks.cards.sunuvbuddy.SunUvBuddyTheme.UI

/** Draw in the same 402 × 874 reference coordinates, hanging from the top. */
internal class SunUvBuddyArt(context: Context) {
    private val regular = Typeface.createFromAsset(context.assets, "sunuvbuddy/sunuvbuddy_Inter-Regular.ttf")
    private val medium = Typeface.createFromAsset(context.assets, "sunuvbuddy/sunuvbuddy_Inter-Medium.ttf")
    private val semi = Typeface.createFromAsset(context.assets, "sunuvbuddy/sunuvbuddy_Inter-SemiBold.ttf")
    private val round = Typeface.createFromAsset(context.assets, "sunuvbuddy/sunuvbuddy_Nunito-ExtraBold.ttf")
    private val fonts = mapOf(
        "title" to font(semi, 19.0), "place" to font(regular, 15.0), "chip" to font(medium, 15.0),
        "head" to font(round, 24.0), "badge" to font(semi, 14.0), "line" to font(regular, 16.5),
        "bar" to font(medium, 15.0), "hour" to font(regular, 13.5), "learn" to font(round, 22.0), "zed" to font(round, 13.0),
    )
    private lateinit var canvas: Canvas
    private val blurNode = if (Build.VERSION.SDK_INT >= 31) RenderNode("SunUvBuddy words") else null

    private fun color(rgb: Int, alpha: Double = 1.0) = ((alpha.coerceIn(0.0, 1.0) * 255).roundToInt() shl 24) or rgb

    private fun paint(rgb: Int, alpha: Double = 1.0) = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = color(rgb, alpha) }

    private fun font(face: Typeface, size: Double) = paint(C.ink).apply {
        typeface = face
        textSize = size.toFloat()
        isSubpixelText = true
    }

    private fun stroke(rgb: Int, width: Double, alpha: Double = 1.0, rounded: Boolean = false) = paint(rgb, alpha).apply {
        style = Paint.Style.STROKE
        strokeWidth = width.toFloat()
        if (rounded) strokeCap = Paint.Cap.ROUND
    }

    private fun rect(x: Double, y: Double, w: Double, h: Double) = RectF(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat())

    private fun rr(x: Double, y: Double, w: Double, h: Double, radius: Double, p: Paint) = canvas.drawRoundRect(rect(x, y, w, h), radius.toFloat(), radius.toFloat(), p)

    private fun circle(x: Double, y: Double, radius: Double, p: Paint) = canvas.drawCircle(x.toFloat(), y.toFloat(), radius.toFloat(), p)

    private fun line(x1: Double, y1: Double, x2: Double, y2: Double, p: Paint) = canvas.drawLine(x1.toFloat(), y1.toFloat(), x2.toFloat(), y2.toFloat(), p)

    private fun translate(x: Double, y: Double) = canvas.translate(x.toFloat(), y.toFloat())

    private fun scale(x: Double, y: Double = x) = canvas.scale(x.toFloat(), y.toFloat())

    private inline fun saved(block: () -> Unit) {
        canvas.save()
        try { block() } finally { canvas.restore() }
    }

    private fun text(s: String, x: Double, baseline: Double, key: String, rgb: Int, alpha: Double = 1.0) {
        val p = requireNotNull(fonts[key])
        p.color = color(rgb, alpha)
        canvas.drawText(s, x.toFloat(), baseline.toFloat(), p)
    }

    private fun textC(s: String, x: Double, cy: Double, key: String, rgb: Int, alpha: Double = 1.0) {
        val p = requireNotNull(fonts[key])
        text(s, x - p.measureText(s) / 2, cy + p.textSize * 0.36, key, rgb, alpha)
    }

    private fun mixColor(a: Int, b: Int, k: Double): Int {
        fun channel(shift: Int) = mix(((a shr shift) and 255).toDouble(), ((b shr shift) and 255).toDouble(), k).roundToInt().coerceIn(0, 255)
        return (channel(24) shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    private fun barX(i: Int) = UI.barsX + i * (UI.barsW + UI.barsGap)

    private fun heightFor(v: Double): Double {
        val i = floor(v).toInt().coerceIn(0, UI.barsH.size - 2)
        return mix(UI.barsH[i], UI.barsH[i + 1], (v - i).coerceIn(0.0, 1.0))
    }

    private fun barColor(v: Double): Int = when {
        v < 2.5 -> color(C.barLow)
        v < 3 -> mixColor(color(C.barLow), color(C.barMid), (v - 2.5) * 2)
        v < 5.5 -> color(C.barMid)
        else -> mixColor(color(C.barMid), color(C.barHigh), min(1.0, (v - 5.5) * 2))
    }

    private fun drawSun(live: Live, uv: Double) {
        val radius = UI.sunR * live.size
        val squash = 0.06 * sin(PI * min(1.0, live.squish / 0.5)) * exp(-live.squish * 3) * (if (live.squish >= 0) 1 else 0)
        val breathe = 0.012 * sin(2 * PI * 2 * live.amb / LOOP)
        val rays = smooth(uv - 3.4)
        if (rays > 0.01) {
            val p = stroke(C.sun, 7.0, 0.55 * rays, true)
            val spin = 360 * live.amb / LOOP / 3
            for (k in 0 until 12) {
                val angle = (k * 30 + spin) * PI / 180
                val r0 = radius * 1.12
                val r1 = radius * (1.3 + 0.06 * sin(2 * PI * 2 * live.amb / LOOP + k))
                line(UI.sunX + cos(angle) * r0, UI.sunY + sin(angle) * r0, UI.sunX + cos(angle) * r1, UI.sunY + sin(angle) * r1, p)
            }
        }
        val glow = paint(0xFFE97A).apply {
            shader = RadialGradient(
                UI.sunX.toFloat(), UI.sunY.toFloat(), (radius * 1.6).toFloat(),
                intArrayOf(
                    color(0xFFE97A, 0.22 * smooth(uv / 3) + 0.4 * smooth(uv - 4)),
                    color(0xFFE97A, 0.0),
                ),
                null, Shader.TileMode.CLAMP,
            )
        }
        circle(UI.sunX, UI.sunY, radius * 1.6, glow)
        saved {
            translate(UI.sunX, UI.sunY + radius * 0.9)
            scale(1 + squash + breathe, 1 - squash - breathe * 0.6)
            translate(0.0, -radius * 0.9)
            val body = Path()
            val phase = 2 * PI * live.amb / LOOP
            for (k in 0..120) {
                val angle = k / 120.0 * 2 * PI
                val r = radius * (1 + 0.05 * cos(14 * angle + phase) + 0.01 * sin(3 * angle - phase * 2))
                val x = (cos(angle) * r).toFloat()
                val y = (sin(angle) * r).toFloat()
                if (k == 0) body.moveTo(x, y) else body.lineTo(x, y)
            }
            body.close()
            canvas.drawPath(body, paint(C.sun))
            rr(radius * 0.42, radius * 0.62, radius * 0.78, radius * 0.19, 3.0, paint(C.sun))
            rr(radius * 1.06, radius * 0.24, radius * 0.15, radius * 0.55, 3.0, paint(C.sun))
            val fx = radius * 0.38
            val fy = -radius * 0.16
            val sleepy = 1 - smooth(uv / 0.8)
            val shades = smooth(uv - 4.2)
            val eyeH = mix(7.5, 1.6, sleepy)
            for (e in 0 until 2) {
                val look = ((barX(0) + UI.barsW / 2 + live.hour * (UI.barsW + UI.barsGap) - UI.sunX) / 160).coerceIn(-1.0, 1.0)
                val ex = fx + (if (e == 0) -6.5 else 6.5) + 2.6 * look
                rr(ex - 2.4, fy - eyeH / 2, 4.8, eyeH, 1.6, paint(C.ink))
            }
            val open = smooth((uv - 1) / 2)
            val mouth = Path().apply {
                moveTo((fx - 4).toFloat(), (fy + 9).toFloat())
                quadTo(fx.toFloat(), (fy + 13 + 3 * open).toFloat(), (fx + 4).toFloat(), (fy + 9).toFloat())
                close()
            }
            canvas.drawPath(mouth, paint(C.ink))
            if (shades > 0.01) saved {
                translate(0.0, (1 - shades) * -26)
                val p = paint(0x17181C, shades)
                rr(fx - 15, fy - 6, 13.0, 10.0, 4.0, p)
                rr(fx + 2, fy - 6, 13.0, 10.0, 4.0, p)
                val bridge = stroke(0x17181C, 2.0, shades)
                line(fx - 2, fy - 3, fx + 2, fy - 3, bridge)
                line(fx - 15, fy - 3, fx - 24, fy - 6, bridge)
                rr(fx - 12, fy - 4.5, 4.0, 2.2, 1.0, paint(0xFFFFFF, 0.35 * shades))
            }
            if (sleepy > 0.3) text("z", fx + 18, fy - 14 - 4 * sin(2 * PI * 3 * live.amb / LOOP), "zed", C.ink, (sleepy - 0.3) * 0.8)
        }
    }

    private fun drawWords(live: Live) {
        val near = floor(live.hour + 0.5).toInt().coerceIn(0, 10)
        val uv = floor(uvAt(near.toDouble(), live.from, live.to, live.tau) + 0.5).toInt()
        val soft = smooth(abs(live.hour - near) * 2.2)
        val alpha = 1 - 0.75 * soft
        val words = SunUvBuddyTheme.words(uv)
        val head = "${if (near == 3) "From" else "At"} ${SunUvBuddyTheme.hours[near]}:00 it will be ${words.level}"
        fun content() {
            val width = requireNotNull(fonts["head"]).measureText(head).toDouble()
            val k = min(1.0, 366 / width)
            saved {
                translate(201.0, UI.headlineY)
                scale(k)
                textC(head, 0.0, 0.0, "head", C.ink, alpha)
            }
            circle(UI.badgeX, UI.badgeY, UI.badgeR, paint(barColor(uv.toDouble()) and 0xFFFFFF, alpha))
            textC(uv.toString(), UI.badgeX, UI.badgeY, "badge", C.ink, alpha)
            for (i in 0 until 2) textC(words.lines[i], 201.0, UI.lineY[i], "line", C.ink, alpha)
        }
        if (soft > 0.02) blurredWords(3.2 * soft) { content() } else content()
    }

    private inline fun blurredWords(sigma: Double, block: () -> Unit) {
        val previous = canvas
        if (Build.VERSION.SDK_INT >= 31 && previous.isHardwareAccelerated) {
            val node = requireNotNull(blurNode)
            // Bounds cover the advice glyphs plus Gaussian support. They are allocation bounds,
            // not a clip; RN similarly supplies a saveLayer bounds hint.
            node.setPosition(0, 420, 402, 575)
            node.setClipToBounds(false)
            node.setRenderEffect(RenderEffect.createBlurEffect(sigma.toFloat(), sigma.toFloat(), Shader.TileMode.DECAL))
            canvas = node.beginRecording()
            canvas.translate(0f, -420f)
            try { block() } finally {
                node.endRecording()
                canvas = previous
            }
            previous.drawRenderNode(node)
        } else {
            // Older hardware cannot blur a layer. Rasterize the same group and blur premultiplied
            // channels with a separable Gaussian rather than dropping the effect.
            val bitmap = Bitmap.createBitmap(422, 175, Bitmap.Config.ARGB_8888)
            canvas = Canvas(bitmap)
            canvas.translate(10f, -410f)
            try { block() } finally { canvas = previous }
            val output = SunUvBuddyBlur.apply(bitmap, sigma)
            previous.drawBitmap(output, -10f, 410f, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            bitmap.recycle()
            output.recycle()
        }
    }

    fun drawScene(target: Canvas, width: Float, live: Live) {
        canvas = target
        canvas.drawColor(color(C.page))
        saved {
            scale(width / SunUvBuddyTheme.REF_W)
            val uv = uvAt(live.hour, live.from, live.to, live.tau)
            canvas.drawRect(rect(0.0, -20.0, 402.0, UI.sheetY + 40), paint(C.map))
            val road = stroke(0xFFFFFF, 1.2, 0.6)
            line(40.0, -10.0, 120.0, 70.0, road)
            line(230.0, -10.0, 210.0, 70.0, road)
            line(300.0, 20.0, 402.0, 0.0, road)
            val warm = smooth(uv / 3)
            val hot = smooth(uv - 4)
            val top = mixColor(mixColor(color(0xEEF2FA), color(C.cardTop), warm), color(0xFFF1C2), hot)
            val bottom = mixColor(mixColor(color(0xDCE5F4), color(C.cardBottom), warm), color(0xFFD27A), hot)
            val sheet = paint(C.cardTop).apply {
                shader = LinearGradient(
                    0f, UI.sheetY.toFloat(), 0f, UI.sheetBottom.toFloat(),
                    top, bottom, Shader.TileMode.CLAMP,
                )
            }
            rr(0.0, UI.sheetY, 402.0, UI.sheetBottom - UI.sheetY, UI.sheetR, sheet)
            textC(Copy.title, 201.0, UI.titleY, "title", C.ink)
            textC(Copy.place, 201.0, UI.placeY, "place", C.grey)
            circle(UI.closeX, UI.closeY, UI.closeR, paint(C.ink, 0.06))
            val cross = stroke(C.ink, 1.9, rounded = true)
            line(UI.closeX - 6, UI.closeY - 6, UI.closeX + 6, UI.closeY + 6, cross)
            line(UI.closeX + 6, UI.closeY - 6, UI.closeX - 6, UI.closeY + 6, cross)
            for (i in 0 until 5) saved {
                val on = i == live.to
                val k = if (on) 1 - 0.04 * live.dayPress else 1.0
                translate(UI.chipsX[i] + UI.chipsW[i] / 2, UI.chipsY)
                scale(k)
                rr(-UI.chipsW[i] / 2, -UI.chipsH / 2, UI.chipsW[i], UI.chipsH, UI.chipsH / 2, paint(if (on) C.chipOn else C.chip))
                val textWidth = requireNotNull(fonts["chip"]).measureText(Copy.days[i]).toDouble()
                val x0 = -(textWidth + 12) / 2
                circle(x0 + 3.5, 0.0, 3.5, paint(if (i == 4) C.green else C.yellow))
                text(Copy.days[i], x0 + 12, 15 * 0.36, "chip", if (on) 0xFFFFFF else C.ink)
            }
            drawSun(live, uv)
            drawWords(live)
            val props = smooth((uv - 1.6) / 1.2)
            if (props > 0.01) {
                val p = paint(C.ink, props)
                saved {
                    translate(182.0, UI.iconsY)
                    scale(1.25 * (0.7 + 0.3 * props))
                    rr(-6.0, -10.0, 12.0, 20.0, 2.5, p)
                    canvas.drawRect(rect(-4.0, -12.5, 8.0, 3.0), p)
                    circle(0.0, -1.0, 2.6, paint(C.yellow, props))
                }
                saved {
                    translate(222.0, UI.iconsY + 1)
                    scale(1.25 * (0.7 + 0.3 * props))
                    val cap = Path().apply {
                        moveTo(-12f, 4f)
                        cubicTo(-12f, -8f, 6f, -10f, 8f, 2f)
                        lineTo(14f, 4f)
                        lineTo(12f, 7f)
                        lineTo(-12f, 7f)
                        close()
                    }
                    canvas.drawPath(cap, p)
                }
            }
            val hx = barX(0) + UI.barsW / 2 + live.hour * (UI.barsW + UI.barsGap)
            val value = barValue(floor(live.hour + 0.5).toInt().coerceIn(0, 10), live.from, live.to, live.tau)
            val by = UI.barsBottom - heightFor(value)
            val bend = (-live.hourV * 9).coerceIn(-26.0, 26.0)
            val stem = Path().apply {
                moveTo(hx.toFloat(), (UI.sheetBottom - 4).toFloat())
                quadTo((hx + bend).toFloat(), ((UI.sheetBottom + by) / 2).toFloat(), hx.toFloat(), by.toFloat())
            }
            canvas.drawPath(stem, stroke(C.sunDeep, 2.0))
            circle(hx, UI.sheetBottom - 2, 5.0, paint(C.cardBottom))
            for (i in 0 until 11) {
                val v = barValue(i, live.from, live.to, live.tau)
                val near = exp(-(i - live.hour).pow(2) / 0.35)
                val h = heightFor(v) + 7 * near * min(1.0, abs(live.hourV) / 3)
                val x = barX(i)
                rr(x, UI.barsBottom - h, UI.barsW, h + 6, 6.0, paint(barColor(v) and 0xFFFFFF))
                val label = floor(v + 0.5).toInt()
                textC(label.toString(), x + UI.barsW / 2, UI.barsBottom - 17, "bar", if (label == 0) 0x9A9CA4 else C.ink)
                textC(SunUvBuddyTheme.hours[i], x + UI.barsW / 2, UI.barsLabelY, "hour", C.grey)
            }
            saved {
                canvas.clipRect(rect(0.0, UI.barsBottom, 402.0, 8.0))
                canvas.drawRect(rect(0.0, UI.barsBottom, 402.0, 8.0), paint(C.page))
            }
            rr(0.0, 820.0, 402.0, 200.0, 26.0, paint(0xFFFFFF))
            text(Copy.learn, 22.0, UI.learnY + 8, "learn", C.ink)
        }
    }
}
