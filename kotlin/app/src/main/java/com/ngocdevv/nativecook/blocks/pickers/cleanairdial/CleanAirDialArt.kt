package com.ngocdevv.nativecook.blocks.pickers.cleanairdial

import android.content.Context
import android.graphics.*

import kotlin.math.*
import com.ngocdevv.nativecook.blocks.pickers.cleanairdial.CleanAirDialTheme.C
import com.ngocdevv.nativecook.blocks.pickers.cleanairdial.CleanAirDialTheme.COPY
import com.ngocdevv.nativecook.blocks.pickers.cleanairdial.CleanAirDialTheme.UI
import com.ngocdevv.nativecook.blocks.pickers.cleanairdial.CleanAirDialTheme as Theme
import com.ngocdevv.nativecook.blocks.pickers.cleanairdial.CleanAirDialMotion as Motion
import com.ngocdevv.nativecook.blocks.pickers.cleanairdial.CleanAirDialMotion.Frame

/** One native Canvas pass in the pin's pixels; the soft dust sprite and typefaces are cached. */
internal class CleanAirDialArt(context: Context) {
    private val faces = arrayOf("400Regular", "500Medium", "600SemiBold", "700Bold").map {
        Typeface.createFromAsset(context.assets, "cleanairdial/cleanairdial_Manrope_$it.ttf")
    }
    private val fonts = mapOf(
        "title" to font(3, 34),
        "room" to font(1, 25),
        "pm" to font(1, 30),
        "value" to font(3, 70),
        "stat" to font(2, 34),
        "unit" to font(0, 34),
        "cap" to font(2, 27),
        "label" to font(3, 25),
        "sub" to font(1, 24),
        "badge" to font(3, 20),
    )

    private fun font(face: Int, size: Int) = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        typeface = faces[face]
        textSize = size.toFloat()
    }
    private val dot = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).also { bitmap ->
        val p = paint(0xffffff)
        p.shader = RadialGradient(16f, 16f, 16f, intArrayOf(-1, 0xccffffff.toInt(), 0x00ffffff), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        Canvas(bitmap).drawCircle(16f, 16f, 16f, p)
    }
    private val dustPaint = paint(C.dust).apply {
        colorFilter = PorterDuffColorFilter(color(C.dust), PorterDuff.Mode.SRC_IN)
        isFilterBitmap = true
    }
    private val source = Rect(0, 0, 32, 32)
    private val destination = RectF()

    private fun hash(i: Int, k: Int): Double {
        val h = sin((i + 1) * 12.9898 + k * 78.233) * 43758.5453
        return h - floor(h)
    }
    private val radius = DoubleArray(300) {
        sqrt(hash(it, 1)) * (UI.disk - 14)
    }
    private val theta = DoubleArray(300) {
        hash(it, 2) * Motion.TAU
    }
    private val turns = DoubleArray(300) {
        if (radius[it] < UI.disk * 0.55) 2.0 else 1.0
    }
    private val wobbleA = DoubleArray(300) {
        4 + 10 * hash(it, 3)
    }
    private val wobbleF = DoubleArray(300) {
        1 + floor(hash(it, 4) * 3)
    }
    private val wobbleP = DoubleArray(300) {
        hash(it, 5) * Motion.TAU
    }
    private val sizes = DoubleArray(300) {
        4 + 6 * hash(it, 6).pow(2)
    }
    private val alpha = DoubleArray(300) {
        0.3 + 0.55 * hash(it, 7)
    }
    private lateinit var canvas: Canvas

    private fun color(rgb: Int, alpha: Double = 1.0) = (Motion.clamp01(alpha).times(255).roundToInt() shl 24) or rgb

    private fun paint(rgb: Int, alpha: Double = 1.0) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = color(rgb, alpha)
    }

    private fun stroke(rgb: Int, width: Double, alpha: Double = 1.0) = paint(rgb, alpha).apply {
        style = Paint.Style.STROKE
        strokeWidth = width.toFloat()
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private fun mixColor(a: Int, b: Int, k: Double): Int {
        fun channel(shift: Int) = Motion.mix(((a shr shift) and 255).toDouble(), ((b shr shift) and 255).toDouble(), k).roundToInt().coerceIn(0, 255)
        return (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    private fun circle(x: Double, y: Double, r: Double, p: Paint) = canvas.drawCircle(x.toFloat(), y.toFloat(), r.toFloat(), p)

    private fun line(x0: Double, y0: Double, x1: Double, y1: Double, p: Paint) = canvas.drawLine(x0.toFloat(), y0.toFloat(), x1.toFloat(), y1.toFloat(), p)

    private fun rect(x: Double, y: Double, w: Double, h: Double) = RectF(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat())

    private fun clipCircle(x: Double, y: Double, r: Double) {
        canvas.clipPath(Path().apply {
            addCircle(x.toFloat(), y.toFloat(), r.toFloat(), Path.Direction.CW)
        })
    }

    private fun text(value: String, x: Double, cy: Double, font: String, rgb: Int, ax: Double = 0.0, alpha: Double = 1.0): Double {
        val p = requireNotNull(fonts[font])
        p.color = color(rgb, alpha)
        val width = p.measureText(value).toDouble()
        canvas.drawText(value, (x - width * ax).toFloat(), (cy + p.textSize * 0.36).toFloat(), p)
        return width
    }

    private fun width(value: String, font: String) = requireNotNull(fonts[font]).measureText(value).toDouble()
    private fun easeOutC(x: Double) = 1 - (1 - Motion.clamp01(x)).pow(3)
    private inline fun saved(block: () -> Unit) {
        canvas.save()
        try {
            block()
        } finally {
            canvas.restore()
        }
    }

    private fun path(vararg points: Double): Path = Path().apply {
        moveTo(points[0].toFloat(), points[1].toFloat())
        for (i in 2 until points.size step 2) lineTo(points[i].toFloat(), points[i + 1].toFloat())
    }

    private fun fan(x: Double, y: Double, s: Double, angle: Double, rgb: Int) = saved {
        canvas.translate(x.toFloat(), y.toFloat())
        canvas.rotate(angle.toFloat())
        for (i in 0..3) saved {
            canvas.rotate(i * 90f)
            val p = Path().apply {
                moveTo(0f, 0f)
                cubicTo((-3*s).toFloat(), (-6*s).toFloat(), (-2*s).toFloat(), (-15*s).toFloat(), (5*s).toFloat(), (-16*s).toFloat())
                cubicTo((10*s).toFloat(), (-16*s).toFloat(), (9*s).toFloat(), (-8*s).toFloat(), (2*s).toFloat(), (-2*s).toFloat())
                close()
            }
            canvas.drawPath(p, paint(rgb))
        }
        circle(0.0, 0.0, 3 * s, paint(rgb))
    }

    private fun stopwatch(x: Double, y: Double, hand: Double, rgb: Int) {
        circle(x, y + 2, 15.0, stroke(rgb, 3.4))
        line(x - 5, y - 19, x + 5, y - 19, stroke(rgb, 3.4))
        line(x, y - 19, x, y - 13, stroke(rgb, 3.0))
        val a = hand * PI / 180 - PI / 2
        line(x, y + 2, x + cos(a) * 9, y + 2 + sin(a) * 9, stroke(rgb, 3.2))
    }

    private fun moon(x: Double, y: Double, wob: Double, rgb: Int, bg: Int) = saved {
        canvas.translate(x.toFloat(), y.toFloat())
        canvas.rotate(wob.toFloat())
        circle(0.0, 0.0, 15.0, paint(rgb))
        circle(8.0, -7.0, 13.0, paint(bg))
        circle(-6.0, 11.0, 6.0, paint(rgb))
        circle(1.0, 13.0, 5.0, paint(rgb))
        circle(-12.0, 14.0, 4.5, paint(rgb))
    }

    private fun snowflake(x: Double, y: Double) {
        val p = stroke(C.white, 3.0)
        for (i in 0..2) {
            val a = i * PI / 3
            val dx = cos(a) * 15
            val dy = sin(a) * 15
            line(x - dx, y - dy, x + dx, y + dy, p)
            for (sign in intArrayOf(-1, 1)) {
                val ex = x + sign * dx * 0.62
                val ey = y + sign * dy * 0.62
                val b1 = a + (if (sign > 0) PI else 0.0) + 0.6
                val b2 = b1 - 1.2
                line(ex, ey, ex + cos(b1) * 6, ey + sin(b1) * 6, p)
                line(ex, ey, ex + cos(b2) * 6, ey + sin(b2) * 6, p)
            }
        }
    }

    private fun drop(x: Double, y: Double) {
        val p = Path().apply {
            moveTo(x.toFloat(), (y-15).toFloat())
            cubicTo((x+4).toFloat(), (y-8).toFloat(), (x+12).toFloat(), (y-2).toFloat(), (x+12).toFloat(), (y+5).toFloat())
            cubicTo((x+12).toFloat(), (y+12).toFloat(), (x+6).toFloat(), (y+16).toFloat(), x.toFloat(), (y+16).toFloat())
            cubicTo((x-6).toFloat(), (y+16).toFloat(), (x-12).toFloat(), (y+12).toFloat(), (x-12).toFloat(), (y+5).toFloat())
            cubicTo((x-12).toFloat(), (y-2).toFloat(), (x-4).toFloat(), (y-8).toFloat(), x.toFloat(), (y-15).toFloat())
            close()
        }
        canvas.drawPath(p, stroke(C.white, 3.0))
        saved {
            canvas.clipPath(p)
            canvas.drawRect(rect(x - 14, y + 4, 28.0, 14.0), paint(C.white))
        }
    }

    private fun dots(x: Double, y: Double) {
        for (r in -2..2) for (q in -2..2) {
            if (abs(r) + abs(q) > 2 && !(abs(r) == 1 && abs(q) == 1)) continue
            if ((r + q) % 2 != 0) continue
            circle(x + q * 6.5, y + r * 6.5, 3.6, paint(C.white))
        }
    }

    private fun power(rgb: Int) {
        val p = Path().apply {
            addArc(rect(-15.0, -13.0, 30.0, 30.0), -50f, 280f)
        }
        canvas.drawPath(p, stroke(rgb, 3.6))
        line(0.0, -18.0, 0.0, -2.0, stroke(rgb, 3.6))
    }

    private fun gear() {
        val p = Path()
        for (i in 0..15) {
            val a0 = i / 16.0 * Motion.TAU
            val rr = if (i % 2 == 0) 18.0 else 13.5
            val a1 = (i + 1) / 16.0 * Motion.TAU
            if (i == 0) p.moveTo((cos(a0)*rr).toFloat(), (sin(a0)*rr).toFloat())
            p.lineTo((cos(a0+0.12)*rr).toFloat(), (sin(a0+0.12)*rr).toFloat())
            p.lineTo((cos(a1-0.12)*rr).toFloat(), (sin(a1-0.12)*rr).toFloat())
        }
        p.close()
        canvas.drawPath(p, paint(C.white))
        circle(0.0, 0.0, 6.0, paint(C.chip))
    }

    private fun autoBadge(rgb: Int, ink: Int) {
        val p = Path()
        for (i in 0..24) {
            val a = i / 24.0 * Motion.TAU
            val rr = 17 + if (i % 2 == 0) 1.6 else -1.2
            if (i == 0) p.moveTo((cos(a)*rr).toFloat(), (sin(a)*rr).toFloat()) else p.lineTo((cos(a)*rr).toFloat(), (sin(a)*rr).toFloat())
        }
        p.close()
        canvas.drawPath(p, paint(rgb))
        text("A", 0.0, 0.0, "badge", ink, 0.5)
    }

    private fun drawTicks(f: Frame) {
        for (k in 0 until UI.tickCount) {
            val a = k / UI.tickCount.toDouble() * Motion.TAU - PI / 2
            val on = Motion.clamp01(f.g - k + 0.5)
            val near = if (f.drag >= 0) max(0.0, 1 - abs(k - f.drag) / 3.5) else 0.0
            val lead = if (on > 0 && on < 1) 1.0 else max(0.0, 1 - abs(k - (f.g - 0.5)))
            val r0 = UI.r0 - 3 * near
            val r1 = UI.r1 + 12 * near + 3 * lead * (1 - f.hush)
            if (lead > 0.3 && on > 0) line(UI.dialX + cos(a)*r0, UI.dialY + sin(a)*r0, UI.dialX + cos(a)*r1, UI.dialY + sin(a)*r1, stroke(C.green, UI.tickW*3, 0.18*lead*(1-f.hush)))
            line(UI.dialX + cos(a)*r0, UI.dialY + sin(a)*r0, UI.dialX + cos(a)*r1, UI.dialY + sin(a)*r1, stroke(mixColor(C.tick, C.green, on*(1-f.hush)), UI.tickW, 1-0.45*f.hush))
        }
    }

    private fun drawDust(f: Frame) {
        val reach = f.f * Theme.BASE_DUST * 1.15
        val band = Theme.BASE_DUST * 0.15
        val wob = Motion.TAU * f.amb / Motion.LOOP
        for (i in 0 until Theme.DUST) {
            var pull: Double
            if (i < Theme.BASE_DUST) pull = Motion.clamp01((reach - i) / band)
            else {
                val v = Motion.clamp01((-f.f * 80 - (i - Theme.BASE_DUST)) / 10)
                if (v <= 0) continue
                pull = 1-v
            }
            val extra = i >= Theme.BASE_DUST
            val r0 = radius[i]
            var rr = r0
            var th = theta[i] + turns[i] * f.phi
            var a = alpha[i]
            var size = sizes[i]
            if (pull > 0) {
                if (f.ret == 1 || extra) {
                    rr = Motion.mix(r0, UI.disk+10, pull)
                    a *= 1-pull
                } else {
                    rr = r0*(1-pull).pow(1.5)
                    th += pull*pull*Motion.TAU*1.2
                    a *= 1-Motion.smooth((pull-0.65)/0.35)
                    size *= 1-0.5*pull
                }
            }
            if (a < 0.01) continue
            val wx = wobbleA[i] * sin(wobbleF[i]*wob+wobbleP[i])
            val wy = wobbleA[i]*cos(wobbleF[i]*wob+wobbleP[i]*1.3)
            val x = UI.dialX+cos(th)*rr+wx*(1-pull)
            val y = UI.dialY+sin(th)*rr+wy*(1-pull)
            destination.set((x-size/2).toFloat(), (y-size/2).toFloat(), (x+size/2).toFloat(), (y+size/2).toFloat())
            dustPaint.alpha = (a*(1-0.6*f.hush)*255).roundToInt().coerceIn(0, 255)
            canvas.drawBitmap(dot, source, destination, dustPaint)
        }
    }

    private fun drawSwirl(f: Frame) {
        val speed = Motion.clamp01((f.g-12)/18)
        val a0 = 0.05+0.18*speed
        if (a0*(1-f.hush) < 0.02) return
        for (k in 0..3) {
            val base = f.phi*2+k*Motion.TAU/4
            val p = Path()
            for (s in 0..24) {
                val u = s/24.0
                val rr = Motion.mix(UI.disk*0.9, UI.disk*0.25, u)
                val th = base+u*2.2
                val x = (UI.dialX+cos(th)*rr).toFloat()
                val y = (UI.dialY+sin(th)*rr).toFloat()
                if (s == 0) p.moveTo(x, y) else p.lineTo(x, y)
            }
            val sp = stroke(0xffffff, 3.0)
            sp.shader = SweepGradient(UI.dialX.toFloat(), UI.dialY.toFloat(), intArrayOf(color(0xffffff, 0.0), color(0x9FE8C0, a0*(1-f.hush)), color(0xffffff, 0.0)), floatArrayOf(0f, 0.08f, 0.25f)).apply {
                setLocalMatrix(Matrix().apply {
                    setRotate((base*180/PI).toFloat(), UI.dialX.toFloat(), UI.dialY.toFloat())
                })
            }
            canvas.drawPath(p, sp)
        }
    }

    private fun drawDial(f: Frame) {
        drawTicks(f)
        val dp = paint(C.diskTop).apply {
            shader = LinearGradient(0f, (UI.dialY-UI.disk).toFloat(), 0f, (UI.dialY+UI.disk).toFloat(), color(C.diskTop), color(C.diskBot), Shader.TileMode.CLAMP)
        }
        circle(UI.dialX, UI.dialY, UI.disk, dp)
        circle(UI.dialX, UI.dialY, UI.disk, stroke(C.rim, 3.0))
        val pm = Motion.pmOf(f.f)
        val q = Theme.qualityMax.indexOfFirst {
            pm < it
        }.let {
            if (it < 0) 2 else it
        }
        val lo = if (q > 0) Theme.qualityMax[q-1] else -10.0
        val hi = Theme.qualityMax[q]
        val kLo = if (q > 0) Motion.smooth((pm-lo)/0.8+0.5) else 1.0
        val kHi = if (q < 2) Motion.smooth((hi-pm)/0.8+0.5) else 1.0
        val capColor = if (kLo < 1) mixColor(Theme.qualityColor[q-1], Theme.qualityColor[q], kLo) else if (kHi < 1) mixColor(Theme.qualityColor[q+1], Theme.qualityColor[q], kHi) else Theme.qualityColor[q]
        saved {
            clipCircle(UI.dialX, UI.dialY, UI.disk-2)
            drawSwirl(f)
            drawDust(f)
            // the moment the air turns Good, a ring of fresh air sweeps out across the disk
            val goodF = (12.0 - 6.5) / 13.0
            val wave = if (f.ret == 0) Motion.clamp01((f.f - goodF) / 0.14) else 0.0
            if (wave > 0 && wave < 1) {
                val rr = Motion.mix(30.0, UI.disk, easeOutC(wave))
                val wp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = Motion.mix(26.0, 6.0, wave).toFloat()
                    shader = RadialGradient(
                        UI.dialX.toFloat(), UI.dialY.toFloat(), (rr + 16).toFloat(),
                        intArrayOf(
                            color(Theme.qualityColor[0], 0.0),
                            color(Theme.qualityColor[0], 0.35 * (1 - wave)),
                            color(Theme.qualityColor[0], 0.0),
                        ),
                        floatArrayOf(0.6f, 0.9f, 1.0f),
                        Shader.TileMode.CLAMP,
                    )
                }
                circle(UI.dialX, UI.dialY, rr, wp)
            }
            circle(UI.dialX, UI.capCY, UI.capR, paint(capColor, 1-0.55*f.hush))
            saved {
                clipCircle(UI.dialX, UI.capCY, UI.capR)
                if (kLo < 1) {
                    text(Theme.qualityWord[q-1], UI.dialX, UI.capText-26*kLo,"cap", C.capText, 0.5, 1-kLo)
                    text(Theme.qualityWord[q], UI.dialX, UI.capText+26*(1-kLo),"cap", C.capText, 0.5, kLo)
                } else if (kHi < 1) {
                    text(Theme.qualityWord[q+1], UI.dialX, UI.capText+26*kHi,"cap", C.capText, 0.5, 1-kHi)
                    text(Theme.qualityWord[q], UI.dialX, UI.capText-26*(1-kHi),"cap", C.capText, 0.5, kHi)
                } else text(Theme.qualityWord[q], UI.dialX, UI.capText,"cap", C.capText, 0.5)
            }
        }
        val pulling = if (f.ret == 0 && f.f > 0.01 && f.f < Motion.F_HIGH+0.3) Motion.clamp01(f.f/0.15)*Motion.clamp01((Motion.F_HIGH+0.25-f.f)/0.25) else 0.0
        if (pulling > 0.01) {
            val gp = paint(0x9FE8C0).apply {
                shader = RadialGradient(UI.dialX.toFloat(), UI.dialY.toFloat(), 60f, intArrayOf(color(0x9FE8C0, 0.12*pulling), color(0x9FE8C0, 0.0)), null, Shader.TileMode.CLAMP)
            }
            circle(UI.dialX, UI.dialY, 60.0, gp)
        }
        line(UI.dialX-UI.disk+2, UI.divider, UI.dialX+UI.disk-2, UI.divider, stroke(C.divider, 2.0))
        text(COPY.pm, UI.dialX, UI.pmY,"pm", C.label)
        val n0 = floor(pm).toInt()
        val roll = Motion.smooth(pm-n0)
        val s0 = n0.toString().padStart(3, '0')
        val s1 = (n0+1).toString().padStart(3, '0')
        var x = UI.dialX-width(s0,"value")/2
        saved {
            canvas.clipRect(rect(UI.dialX-160, UI.pmValue-40, 320.0, 80.0))
            for (i in 0..2) {
                val a = s0[i].toString()
                val b = s1[i].toString()
                if (a == b || roll < 0.001) text(a, x, UI.pmValue,"value", C.white, alpha=1-0.5*f.hush)
                else {
                    text(a, x, UI.pmValue+roll*74,"value", C.white, alpha=1-0.5*f.hush)
                    text(b, x, UI.pmValue-(1-roll)*74,"value", C.white, alpha=1-0.5*f.hush)
                }
                x += width(a,"value")
            }
        }
        for (i in 0..2) {
            val sx = UI.statX[i]
            val vw = width(COPY.statV[i],"stat")
            val uw = width(COPY.statU[i],"unit")
            text(COPY.statV[i], sx-(vw+uw)/2, UI.statV,"stat", C.white)
            text(COPY.statU[i], sx-(vw+uw)/2+vw, UI.statV,"unit", C.unit)
            when (i) {
                0 -> snowflake(sx, UI.statIcon)
                1 -> drop(sx, UI.statIcon)
                else -> dots(sx, UI.statIcon)
            }
        }
        for (i in 0..1) line(UI.statDiv[i], UI.statDivY[0], UI.statDiv[i], UI.statDivY[1], stroke(C.divider, 2.0))
    }

    private fun pill(rgb: Int) = canvas.drawRoundRect(rect(-UI.chipW/2, -UI.chipH/2, UI.chipW, UI.chipH), (UI.chipH/2).toFloat(), (UI.chipH/2).toFloat(), paint(rgb))

    private fun drawChips(f: Frame) {
        for (i in 0..2) {
            val x = UI.chipX[i]
            val press = if (f.press == i) f.pressK else 0.0
            saved {
                canvas.translate(x.toFloat(), UI.chipY.toFloat())
                canvas.scale((1-0.04*press).toFloat(), (1-0.04*press).toFloat())
                val bg = when (i) {
                    0 -> C.blue
                    2 -> C.purple
                    else -> C.chip
                }
                val on = when (i) {
                    0 -> f.power
                    1 -> f.timer
                    else -> f.scenes
                }
                val fill = if (i == 1) mixColor(C.chip, 0x2A2A2A, on) else mixColor(C.chip, bg, on)
                pill(fill)
                when (i) {
                    0 -> {
                        fan(-32.0, 0.0, 1.25, f.phi*4*180/PI, C.white)
                        canvas.drawPath(path(28.0, -9.0, 36.0, -17.0, 44.0, -9.0).apply {
                            close()
                        }, paint(0xffffff, 0.55))
                        canvas.drawPath(path(28.0, 9.0, 36.0, 17.0, 44.0, 9.0).apply {
                            close()
                        }, paint(0xffffff, 0.55))
                    }
                    1 -> stopwatch(0.0, 0.0, if (f.timer > 0) min(360.0, f.timerAt*240)*f.timer else 0.0, C.white)
                    else -> {
                        moon(-25.0, 0.0, 6*sin(Motion.TAU*2*f.amb/Motion.LOOP), C.white, mixColor(C.chip, C.purple, on))
                        canvas.drawPath(path(38.0, -8.0, 45.0, 0.0, 38.0, 8.0), stroke(0xffffff, 3.2))
                    }
                }
            }
            text(COPY.chips[i], x, UI.chipLabel,"label", C.white, 0.5)
        }
        var shown = false
        for (l in 0..3) {
            val d = (f.g-Theme.ticks[l])/max(1.0, if (l < 3) Theme.ticks[l+1]-Theme.ticks[l] else 9.0)
            val a = Motion.clamp01(1-abs(f.g-Theme.ticks[l])/4.5)
            if (a < 0.02) continue
            shown = true
            text(if (f.power < 0.5) "Off" else Theme.levelNames[l], UI.chipX[0], UI.chipSub-d*30,"sub", C.sub, 0.5, a)
        }
        if (!shown) text(Theme.levelNames[Motion.levelAt(max(0.0, f.g))], UI.chipX[0], UI.chipSub,"sub", C.sub, 0.5)
        text(if (f.timer > 0.5) COPY.timerOn else COPY.timerOff, UI.chipX[1], UI.chipSub,"sub", C.sub, 0.5)
        text(if (f.scenes > 0.5) "On" else "Off", UI.chipX[2], UI.chipSub,"sub", C.sub, 0.5)
    }

    private fun drawBottom(f: Frame) {
        for (i in 0..2) saved {
            val press = if (f.press == 3+i) f.pressK else 0.0
            canvas.translate(UI.chipX[i].toFloat(), UI.bottomY.toFloat())
            canvas.scale((1-0.04*press).toFloat(), (1-0.04*press).toFloat())
            when (i) {
                1 -> {
                    pill(mixColor(C.chip, C.power, f.power))
                    power(if (f.power > 0.5) 0x151515 else C.white)
                }
                0 -> {
                    pill(mixColor(C.chip, 0xE9E9E9, f.auto))
                    autoBadge(if (f.auto > 0.5) 0x151515 else C.white, if (f.auto > 0.5) 0xE9E9E9 else C.chip)
                }
                else -> {
                    pill(C.chip)
                    gear()
                }
            }
        }
    }

    fun drawScene(target: Canvas, width: Float, frame: Frame) {
        canvas = target
        canvas.drawColor(color(C.bg))
        saved {
            val scale = width/Theme.REF_W
            canvas.scale(scale.toFloat(), scale.toFloat())
            canvas.translate(0f, Theme.Y0.toFloat())
            canvas.scale(Theme.S.toFloat(), Theme.S.toFloat())
            canvas.translate(-Theme.X0.toFloat(), 0f)
            canvas.drawPath(path(Theme.X0+52, UI.headerY-16, Theme.X0+36, UI.headerY, Theme.X0+52, UI.headerY+16), stroke(C.white, 4.0))
            text(COPY.title, 600.0, UI.headerY-8,"title", C.white, 0.5)
            text(COPY.room, 600.0, UI.headerSub+2,"room", C.label, 0.5)
            for (i in 0..2) circle(Theme.X0+670-64+i*12, UI.headerY, 3.6, paint(C.white))
            drawDial(frame)
            drawChips(frame)
            drawBottom(frame)
        }
    }
}
