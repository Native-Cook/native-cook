package com.ngocdevv.nativecook.blocks.navigation.greatwavemorning

import android.content.Context
import android.graphics.*
import android.os.Build
import kotlin.math.*
import com.ngocdevv.nativecook.blocks.navigation.greatwavemorning.GreatWaveMorningTheme.C
import com.ngocdevv.nativecook.blocks.navigation.greatwavemorning.GreatWaveMorningTheme.COPY
import com.ngocdevv.nativecook.blocks.navigation.greatwavemorning.GreatWaveMorningTheme.UI
import com.ngocdevv.nativecook.blocks.navigation.greatwavemorning.GreatWaveMorningTheme.S
import com.ngocdevv.nativecook.blocks.navigation.greatwavemorning.GreatWaveMorningTheme.DY
import com.ngocdevv.nativecook.blocks.navigation.greatwavemorning.GreatWaveMorningMotion.LOOP
import com.ngocdevv.nativecook.blocks.navigation.greatwavemorning.GreatWaveMorningMotion.PULL
import com.ngocdevv.nativecook.blocks.navigation.greatwavemorning.GreatWaveMorningMotion.mix
import com.ngocdevv.nativecook.blocks.navigation.greatwavemorning.GreatWaveMorningMotion.smooth
import com.ngocdevv.nativecook.blocks.navigation.greatwavemorning.GreatWaveMorningMotion.clamp01
import com.ngocdevv.nativecook.blocks.navigation.greatwavemorning.GreatWaveMorningMotion.easeOut

/** Native Canvas counterpart of gwm-draw.ts. Drawing order and paths stay in pin pixels. */
internal class GreatWaveMorningArt(context: Context) {
    private lateinit var canvas: Canvas
    private val medium = Typeface.createFromAsset(context.assets, "greatwavemorning/greatwavemorning_Manrope_500Medium.ttf")
    private val semi = Typeface.createFromAsset(context.assets, "greatwavemorning/greatwavemorning_Manrope_600SemiBold.ttf")
    // Keep all four reference faces bundled, even though the current scene uses medium and semi.
    private val paper = if (Build.VERSION.SDK_INT >= 33) RuntimeShader(GreatWaveMorningShaders.PAPER) else null
    private val blurNodes = mutableListOf<RenderNode>()
    private var blurIndex = 0
    private val softMasks = mutableListOf<Bitmap>()
    private var fallbackGrain: Bitmap? = null
    private var grainScale = 0f
    private val bases = doubleArrayOf(216.0, 246.0, 280.0, 318.0, 356.0, 398.0, 440.0, 478.0)
    private val amps = doubleArrayOf(10.0, 14.0, 16.0, 18.0, 20.0, 22.0, 20.0, 16.0)
    private val lams = doubleArrayOf(150.0, 120.0, 170.0, 140.0, 190.0, 150.0, 210.0, 170.0)
    private val tau = PI * 2

    private fun color(h: String, a: Double = 1.0) = (Color.parseColor(h) and 0xffffff) or ((a.coerceIn(0.0, 1.0) * 255).roundToInt() shl 24)

    private fun mixCol(a: String, b: String, k: Double): String {
        val ca = Color.parseColor(a)
        val cb = Color.parseColor(b)
        return "#%02X%02X%02X".format(mix(Color.red(ca), Color.red(cb), k).roundToInt().coerceIn(0, 255), mix(Color.green(ca), Color.green(cb), k).roundToInt().coerceIn(0, 255), mix(Color.blue(ca), Color.blue(cb), k).roundToInt().coerceIn(0, 255))
    }

    private fun paint(h: String, a: Double = 1.0) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = color(h, a)
    }

    private fun stroke(h: String, w: Number, a: Double = 1.0) = paint(h, a).apply {
        style = Paint.Style.STROKE
        strokeWidth = w.toFloat()
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private fun rect(x: Number, y: Number, w: Number, h: Number) = RectF(x.toFloat(), y.toFloat(), (x.toDouble() + w.toDouble()).toFloat(), (y.toDouble() + h.toDouble()).toFloat())

    private fun rr(x: Number, y: Number, w: Number, h: Number, r: Number, p: Paint) {
        canvas.drawRoundRect(rect(x, y, w, h), r.toFloat(), r.toFloat(), p)
    }

    private fun line(x: Number, y: Number, x2: Number, y2: Number, p: Paint) {
        canvas.drawLine(x.toFloat(), y.toFloat(), x2.toFloat(), y2.toFloat(), p)
    }

    private fun circle(x: Number, y: Number, r: Number, p: Paint) {
        if (r.toDouble()>0) canvas.drawCircle(x.toFloat(), y.toFloat(), r.toFloat(), p)
    }

    private fun translate(x: Number, y: Number) = canvas.translate(x.toFloat(), y.toFloat())

    private fun scale(x: Number, y: Number = x) = canvas.scale(x.toFloat(), y.toFloat())

    private fun linear(x: Number, y: Number, x2: Number, y2: Number, colors: Array<String>, a: Double = 1.0, stops: FloatArray? = null) = paint("#FFFFFF").apply {
        shader = LinearGradient(x.toFloat(), y.toFloat(), x2.toFloat(), y2.toFloat(), colors.map {
            color(it, a)
        }.toIntArray(), stops, Shader.TileMode.CLAMP)
    }

    private fun text(s: String, x: Number, cy: Number, size: Number, p: Paint, ax: Double = 0.0, bold: Boolean = false): Double {
        p.typeface = if (bold) semi else medium
        p.textSize = size.toFloat()
        val width = p.measureText(s).toDouble()
        canvas.drawText(s, (x.toDouble() - width * ax).toFloat(), (cy.toDouble() + size.toDouble() * 0.36).toFloat(), p)
        return width
    }

    private fun width(s: String, size: Number, bold: Boolean = false) = paint(C.navy).apply {
        textSize = size.toFloat()
        typeface = if (bold) semi else medium
    }.measureText(s).toDouble()

    private fun path(block: GreatWaveMorningPath.() -> Unit) = GreatWaveMorningPath().apply(block).value

    private fun clipRound(x: Number, y: Number, w: Number, h: Number, r: Number) {
        canvas.clipPath(Path().apply {
            addRoundRect(rect(x, y, w, h), r.toFloat(), r.toFloat(), Path.Direction.CW)
        })
    }
    // One blur pass per silhouette. Hardware Canvas ignores BlurMaskFilter, so use a RenderNode
    // on API 31+, and rasterize that single path on earlier Android versions.
    @Suppress("DEPRECATION")

    private fun shadow(p: Path, a: Double, blur: Double, strokeWidth: Float? = null) {
        val ink = paint("#06142B", a).apply {
            if (strokeWidth != null) {
                style = Paint.Style.STROKE
                this.strokeWidth = strokeWidth
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
        }
        val bounds = RectF()
        p.computeBounds(bounds, true)
        val pad = ceil(blur * 3 + (strokeWidth ?: 0f)).toInt()
        val left = floor(bounds.left).toInt() - pad
        val top = floor(bounds.top).toInt() - pad
        val w = (ceil(bounds.right).toInt() + pad - left).coerceAtLeast(1)
        val h = (ceil(bounds.bottom).toInt() + pad - top).coerceAtLeast(1)
        if (Build.VERSION.SDK_INT >= 31 && canvas.isHardwareAccelerated) {
            val index = blurIndex++
            val node = if (index<blurNodes.size) blurNodes[index] else RenderNode("GreatWaveMorning paper shadow").also {
                blurNodes.add(it)
            }
            node.setPosition(left, top, left + w, top + h)
            node.setClipToBounds(false)
            node.setRenderEffect(RenderEffect.createBlurEffect(blur.toFloat(), blur.toFloat(), Shader.TileMode.DECAL))
            val nc = node.beginRecording()
            nc.translate(-left.toFloat(), -top.toFloat())
            nc.drawPath(p, ink)
            node.endRecording()
            canvas.drawRenderNode(node)
        } else if (canvas.isHardwareAccelerated) {
            val index = blurIndex++
            val existing = softMasks.getOrNull(index)
            val bitmap = if (existing != null && existing.width >= w && existing.height >= h) existing else {
                Bitmap.createBitmap(max(w, existing?.width ?: 0), max(h, existing?.height ?: 0), Bitmap.Config.ARGB_8888).also {
                    if (index < softMasks.size) softMasks[index] = it else softMasks.add(it)
                }
            }
            bitmap.eraseColor(Color.TRANSPARENT)
            val nc = Canvas(bitmap)
            nc.translate(-left.toFloat(), -top.toFloat())
            ink.maskFilter = BlurMaskFilter(((blur - 0.5) / 0.57735).toFloat().coerceAtLeast(0.001f), BlurMaskFilter.Blur.NORMAL)
            nc.drawPath(p, ink)
            canvas.drawBitmap(bitmap, left.toFloat(), top.toFloat(), null)
        } else {
            ink.maskFilter = BlurMaskFilter(((blur - 0.5) / 0.57735).toFloat().coerceAtLeast(0.001f), BlurMaskFilter.Blur.NORMAL)
            canvas.drawPath(p, ink)
        }
    }

    private fun seaOf(scroll: Double): GreatWaveMorningSea {
        val pull = max(0.0, -scroll)
        val fold = smooth(max(0.0, scroll) / 340)
        return GreatWaveMorningSea(pull, pull / PULL, fold, mix(UI.header.bottom, UI.header.collapsed, fold) + pull)
    }

    private fun riverAt(y: Double, sea: GreatWaveMorningSea): Pair<Double, Double> {
        val top = 252 + sea.pull * 0.35
        val u = clamp01((y - top) / (UI.header.bottom + sea.pull - top))
        return Pair(360 + 46 * sin(u * 6.4 + 0.3) * (0.5 + 0.5 * u), mix(3, 70, u.pow(1.6)))
    }

    private fun bandPath(i: Int, sea: GreatWaveMorningSea, amb: Double): Path {
        val k = i / 7.0
        val ph = tau * amb / LOOP
        val rest = bases[i]+sea.pull * (0.25 + 0.75 * k)
        val target = sea.bottom - 22 + (i - 7) * 1.5
        val base = mix(rest, target, sea.fold)
        val amp = amps[i]*(1 + 0.7 * sea.e * (0.4 + k)) * (1 - 0.82 * sea.fold)
        val lam = lams[i]
        return path {
            for (x in -24..760 step 12) {
                val hill = abs(sin(PI * x / lam + i * 1.7 + ph * (if (i%2 == 0) 1 else -1))).pow(0.8)
                val roll = sin(tau * x / (lam * 2.3) + i + ph * 2)
                val y = base - amp * (0.75 * hill + 0.35 * roll)
                if (x == -24) moveTo(x, y) else lineTo(x, y)
            }
            lineTo(760, sea.bottom + 60)
            lineTo(-24, sea.bottom + 60)
            close()
        }
    }

    private fun drawClouds(sea: GreatWaveMorningSea, amb: Double) {
        val a = 1 - smooth(sea.fold * 1.6)
        if (a<0.01) return
        val drift = 6 * sin(tau * amb / LOOP)
        val sink = sea.fold * 60 + sea.pull * 0.15
        fun puff(x: Double, y: Double, r: Int) {
            circle(x, y + 6, r, paint(C.cloudShade, a))
            circle(x, y, r, paint(C.cloud, a))
        }
        puff(drift + 20, 236 + sink, 34)
        puff(drift + 70, 222 + sink, 30)
        puff(drift + 118, 238 + sink, 24)
        puff(drift + 150, 254 + sink, 16)
        puff(-drift + 520, 228 + sink, 22)
        puff(-drift + 570, 206 + sink, 32)
        puff(-drift + 630, 186 + sink, 38)
        puff(-drift + 700, 196 + sink, 40)
        puff(-drift + 740, 224 + sink, 30)
    }

    private fun drawRiver(sea: GreatWaveMorningSea, amb: Double) {
        val a = 1 - smooth(sea.fold * 1.4)
        if (a<0.01) return
        val top = 252 + sea.pull * 0.35
        val right = mutableListOf<Pair<Double, Double>>()
        val p = path {
            for (i in 0..28) {
                val y = mix(top, sea.bottom + 10, i / 28.0)
                val (x, w) = riverAt(y, sea)
                if (i == 0) moveTo(x - w, y) else lineTo(x - w, y)
                right.add(Pair(x + w, y))
            }
            for ((x, y) in right.asReversed()) lineTo(x, y)
            close()
        }
        shadow(p, 0.5 * a, 6.0)
        canvas.drawPath(p, linear(0, top, 0, sea.bottom, arrayOf(C.river, C.riverLow), a))
        canvas.drawPath(p, stroke("#B8D2EE", 2.5, 0.5 * a))
        for (k in 0..6) {
            val u = (k / 7.0 + amb / LOOP)%1
            val y = mix(top + 20, sea.bottom - 10, u)
            val (x, w) = riverAt(y, sea)
            line(x - w * 0.45, y, x + w * 0.2, y + 1.5, stroke("#8DB3DB", 2, 0.55 * a * sin(PI * u)))
        }
    }

    private fun drawBoat(sea: GreatWaveMorningSea, amb: Double) {
        val ph = tau * 3 * amb / LOOP
        val restY = UI.boat.y + sea.pull * 0.6 + 4 * sin(ph)
        val y = mix(restY, sea.bottom - 34, sea.fold)
        val x = mix(riverAt(restY, sea).first, 600, sea.fold)
        val sc = mix(1, 0.62, sea.fold) * (1 + 0.08 * sea.e)
        val tilt = 5 * sin(ph + 1) + 8 * sea.e * sin(ph * 2)
        canvas.save()
        translate(x, y)
        canvas.rotate(tilt.toFloat())
        scale(sc)
        canvas.drawOval(rect(-40, 14, 80, 10), paint("#173763", 0.35))
        // its wake: two soft lines opening out behind it, with ripples running down them
        val wa = 1 - sea.fold
        if (wa > 0.02) {
            for (side in doubleArrayOf(-1.0, 1.0)) {
                line(side * 26, 16, side * 70, 64, stroke("#CFE0F4", 3, 0.45 * wa))
                for (k in 0..2) {
                    val u = ((amb / LOOP) * 6 + k / 3.0) % 1
                    val px = mix(side * 26, side * 70, u)
                    val py = mix(16, 64, u)
                    line(px - side * 6, py - 2, px + side * 2, py + 4, stroke("#FFFFFF", 2.4, 0.55 * wa * sin(PI * u)))
                }
            }
        }
        canvas.drawPath(path {
            moveTo(-38, 0)
            lineTo(38, 0)
            lineTo(26, 16)
            lineTo(-28, 16)
            close()
        }, paint(C.boat))
        line(-30, 6, 32, 6, stroke(C.sailShade, 2))
        line(2, 0, 2, -62, stroke("#2B3B55", 2.4))
        canvas.drawPath(path {
            moveTo(4, -58)
            lineTo(4, -6)
            lineTo(30, -6)
            close()
        }, paint(C.sail))
        canvas.drawPath(path {
            moveTo(0, -50)
            lineTo(0, -6)
            lineTo(-22, -6)
            close()
        }, paint(C.sailShade))
        canvas.drawPath(path {
            moveTo(2, -62)
            lineTo(14, -58 + 2 * sin(ph * 3))
            lineTo(2, -54)
            close()
        }, paint("#21406E"))
        canvas.restore()
    }

    private fun drawCurl(cx: Double, cy: Double, r: Double, dir: Int, roll: Double, a: Double) {
        if (a<0.01) return
        canvas.save()
        translate(cx, cy)
        scale(dir, 1)
        val body = path {
            moveTo(-2.6 * r, 1.25 * r)
            cubicTo(-1.7 * r, r, -1.2 * r, -0.1 * r, -0.35 * r, -0.85 * r)
            cubicTo(0.25 * r, -1.25 * r, 1.15 * r, -1.05 * r, 1.2 * r, -0.25 * r)
            cubicTo(1.22 * r, 0.25 * r, 0.8 * r, 0.5 * r, 0.45 * r, 0.35 * r)
            cubicTo(0.9 * r, 0.75 * r, 1.3 * r, r, 1.8 * r, 1.25 * r)
            close()
        }
        shadow(body, 0.4 * a, 8.0)
        canvas.drawPath(body, linear(-r, -r, r, r, arrayOf("#5C8CC8", "#2C5590", "#1C3F72"), a, floatArrayOf(0f, 0.55f, 1f)))
        for (k in 0..2) {
            val o = 0.22 * r * (k + 1)
            canvas.drawPath(path {
                moveTo(-2.2 * r + o, 1.2 * r)
                cubicTo(-1.4 * r + o, 0.9 * r, -0.9 * r + o * 0.6, 0.1 * r + o * 0.4, -0.25 * r + o * 0.5, -0.5 * r + o * 0.6)
            }, stroke("#7FA7DA", 3, 0.35 * a))
        }
        val ox = 0.42 * r
        val oy = -0.2 * r
        val inner = mutableListOf<Pair<Double, Double>>()
        val outer = path {
            for (i in 0..36) {
                val u = i / 36.0
                val ang = PI * 1.02 + u * PI * 1.65 + roll * 0.25
                val ro = r * (1 - 0.62 * u)
                val wv = r * (0.4 * (1 - u).pow(1.1) + 0.04)
                val x = ox + cos(ang) * ro * 0.95
                val y = oy + sin(ang) * ro * 0.82
                if (i == 0) moveTo(x - 0.12 * r, y + 0.25 * r)
                lineTo(x, y)
                inner.add(Pair(ox + cos(ang) * (ro - wv) * 0.95, oy + sin(ang) * (ro - wv) * 0.82))
            }
            for ((x, y) in inner.asReversed()) lineTo(x, y)
            close()
        }
        shadow(outer, 0.3 * a, 5.0)
        canvas.drawPath(outer, linear(0, -r, 0, r * 0.4, arrayOf("#FBF8F2", C.crest, C.crestShade), a, floatArrayOf(0f, 0.6f, 1f)))
        for (k in 0..2) {
            val u = 0.02 + k * 0.07
            val ang = PI * 1.02 + u * PI * 1.65 + roll * 0.25
            val ro = r * (1 - 0.62 * u)
            circle(ox + cos(ang) * ro * 0.98, oy + sin(ang) * ro * 0.86, r * (0.13 - k * 0.025), paint(C.crest, a))
        }
        canvas.restore()
    }

    private fun drawSea(frame: GreatWaveMorningFrame) {
        val sea = seaOf(frame.scroll)
        val amb = frame.amb
        fun edgeY(x: Int) = sea.bottom + 4 * sin(x * 0.07 + 1.3) + 2 * sin(x * 0.23)
        val above = path {
            moveTo(-24, -DY / S - 20)
            lineTo(760, -DY / S - 20)
            for (x in 760 downTo -24 step 16) lineTo(x, edgeY(x))
            close()
        }
        canvas.save()
        canvas.clipPath(above)
        canvas.drawRect(rect(-30, -DY / S - 10, 796, sea.bottom + DY / S + 70), linear(0, -DY / S, 0, sea.bottom, arrayOf(C.sky, C.skyLow)))
        // tilt: far layers barely move, near ones move most, so the sea has depth under the hand
        canvas.save()
        translate(frame.tiltX * 4, frame.tiltY * 2)
        drawClouds(sea, amb)
        canvas.restore()
        for (i in 0..7) {
            val p = bandPath(i, sea, amb)
            val depth = (i + 1) / 8.0
            canvas.save()
            translate(frame.tiltX * 18 * depth, frame.tiltY * 8 * depth)
            canvas.save()
            translate(0, -5)
            shadow(p, 0.45, 6.0)
            canvas.restore()
            canvas.drawPath(p, paint(C.bands[i]))
            canvas.save()
            canvas.clipPath(p)
            translate(0, 3)
            canvas.drawPath(p, stroke("#FFFFFF", 3, 0.08))
            canvas.restore()
            canvas.restore()
        }
        canvas.save()
        translate(frame.tiltX * 12, frame.tiltY * 5)
        drawRiver(sea, amb)
        drawBoat(sea, amb)
        val ph = tau * amb / LOOP
        val roll = 0.18 * sin(ph * 2) + 0.5 * sea.e
        val ca = 1 - smooth(sea.fold * 1.5)
        val crests = listOf(
            GreatWaveMorningCrest(120 - 10 * sea.e, 380 + sea.pull * 0.75, 78 * (1 + 0.25 * sea.e), 1),
            GreatWaveMorningCrest(600 + 10 * sea.e, 330 + sea.pull * 0.55, 64 * (1 + 0.25 * sea.e), -1),
            GreatWaveMorningCrest(650 + 8 * sea.e, 462 + sea.pull * 0.9, 52 * (1 + 0.3 * sea.e), -1))
        for ((k, cr) in crests.withIndex()) drawCurl(cr.x, cr.y - 20 * sea.fold, cr.r * (1 - 0.6 * sea.fold), cr.d, roll + k * 0.3, ca)
        canvas.restore()
        canvas.restore()
        val edge = path {
            for (x in -24..760 step 16) {
                if (x == -24) moveTo(x, edgeY(x) + 3) else lineTo(x, edgeY(x) + 3)
            }
        }
        shadow(edge, 0.18, 5.0, 6f)
        if (frame.foam<0) return
        val t = frame.foam
        for ((k, cr) in crests.withIndex()) for (i in 0..15) {
            val h1 = sin((i + 1) * 12.9898 + k * 7.1) * 43758.5453
            val r1 = h1 - floor(h1)
            val h2 = sin((i + 1) * 39.35 + k * 3.3) * 24634.63
            val r2 = h2 - floor(h2)
            val ang = -PI / 2 + (r1 - 0.5) * 1.6 + cr.d * 0.4
            val v = (180 + 220 * r2) * frame.foamPow
            val x = cr.x - cr.d * cr.r * 0.2 + cos(ang) * v * t
            val y = cr.y - cr.r + sin(ang) * v * t + 420 * t * t
            val a = (1 - smooth(t / 1.2)) * 0.9
            if (a >= 0.02) circle(x, y, 3 + 4 * r1 * (1 - t), paint(C.crest, a))
        }
    }

    private fun drawHeaderText(frame: GreatWaveMorningFrame) {
        val sea = seaOf(frame.scroll)
        val fold = sea.fold
        val sc = mix(1, 0.86, fold)
        canvas.save()
        translate(UI.hello.x, UI.hello.y + sea.pull * 0.15 + 4 * fold)
        scale(sc)
        text(COPY.hello, 0, 0, 40, paint("#FFFFFF"), bold = true)
        canvas.restore()
        val pa = 1 - smooth(fold * 2.2)
        if (pa>0.01) {
            val p = UI.pill
            val pw = COPY.sprint.sumOf {
                width(it, 21)
            }+24 + 36
            val py = p.y + sea.pull * 0.15 - 12 * fold
            rr(p.x, py, pw, p.h, p.h / 2, paint(C.pill, pa))
            var x = p.x + 18
            x += text(COPY.sprint[0], x, py + p.h / 2, 21, paint("#FFFFFF", pa)) + 12
            x += text(COPY.sprint[1], x, py + p.h / 2, 21, paint("#FFFFFF", 0.7 * pa)) + 12
            text(COPY.sprint[2], x, py + p.h / 2, 21, paint("#FFFFFF", pa))
        }
        val a = UI.avatar
        canvas.save()
        translate(a.x, a.y + sea.pull * 0.15)
        scale(mix(1, 0.78, fold))
        circle(0, 0, a.r + 4, paint(C.avatarRing))
        circle(0, 0, a.r, paint(C.avatar))
        text(COPY.initials, 0, 0, 30, paint(C.navy), 0.5, true)
        canvas.restore()
    }

    private fun drawContent(frame: GreatWaveMorningFrame) {
        canvas.save()
        translate(0, -frame.scroll)
        val cards = UI.cards
        for (i in 0..2) {
            val st = COPY.stats[i]
            val x = cards.x[i]
            rr(x + 2, cards.y + 6, cards.w, cards.h, cards.r, paint(C.cardEdge))
            rr(x, cards.y, cards.w, cards.h, cards.r, paint(C.card))
            canvas.save()
            clipRound(x, cards.y, cards.w, cards.h, cards.r)
            val ph = tau * frame.amb / LOOP + i
            val wave = path {
                moveTo(x + cards.w * 0.42, cards.y + cards.h)
                cubicTo(x + cards.w * 0.6, cards.y + cards.h - 18 - 3 * sin(ph), x + cards.w * 0.72, cards.y + cards.h - 46 + 4 * sin(ph), x + cards.w, cards.y + cards.h - 58)
                lineTo(x + cards.w, cards.y + cards.h)
                close()
            }
            canvas.drawPath(wave, paint(st.wave))
            canvas.restore()
            text(st.label, x + 26, cards.y + 38, 23, paint(C.navy))
            text(st.value, x + 24, cards.y + 96, 70, paint(st.color), bold = true)
            val sw = if (st.sub[0].isNotEmpty()) text(st.sub[0], x + 26, cards.y + 142, 20, paint(C.tan)) else 0.0
            text(st.sub[1], x + 26 + sw, cards.y + 142, 20, paint(C.navy))
        }
        for (i in 0..2) circle(UI.dots.x[i], UI.dots.y, 5.5, paint(if (i == 0) C.blue else "#D6C9AE"))
        text(COPY.focus, 42, UI.focus.y, 27, paint(C.navy), bold = true)
        line(220, UI.focus.lineY, 648, UI.focus.lineY, stroke(C.line, 1.5))
        canvas.drawPath(path {
            moveTo(652, UI.focus.lineY)
            cubicTo(660, UI.focus.lineY - 12, 676, UI.focus.lineY - 12, 680, UI.focus.lineY - 4)
            cubicTo(672, UI.focus.lineY - 8, 668, UI.focus.lineY - 2, 674, UI.focus.lineY)
            lineTo(704, UI.focus.lineY)
        }, stroke(C.line, 1.5))
        val rows = UI.rows
        for (i in COPY.rows.indices) {
            val row = COPY.rows[i]
            val y = rows.y0 + i * rows.pitch
            val dn = frame.done[i]
            val press = if (frame.pressRow == i) frame.pressK else 0.0
            canvas.save()
            rr(rows.x + 2, y + 5, rows.w, rows.h, 6, paint(C.cardEdge, 0.9))
            rr(rows.x, y, rows.w, rows.h, 6, paint(C.row))
            if (press > 0.01) rr(rows.x, y, rows.w, rows.h, 6, paint("#E9DFC9", 0.7 * press))
            rr(rows.x, y, 14, rows.h, 4, paint(mixCol(row.bar, COPY.done.bar, dn)))
            val tw = text(row.title, 74, y + rows.h / 2, 20.5, paint(mixCol(C.navy, C.grey, dn)))
            if (dn>0.01) line(74, y + rows.h / 2 + 1, 74 + tw * easeOut(dn), y + rows.h / 2 + 1, stroke(C.grey, 1.6))
            text(row.time, 538, y + rows.h / 2, 22, paint(C.time), 1.0)
            val label = if (dn>0.5) COPY.done.status else row.status
            val pw = max(98.0, width(label, 19) + 30)
            val flip = abs(cos(PI * dn))
            canvas.save()
            translate(632, y + rows.h / 2)
            scale(1, max(0.05, flip))
            rr(-pw / 2, -21, pw, 42, 21, paint(if (dn>0.5) COPY.done.pill else row.pill))
            text(label, 0, 0, 19, paint(if (dn>0.5) COPY.done.ink else row.ink), 0.5)
            canvas.restore()
            canvas.restore()
        }
        canvas.restore()
    }

    private fun tabIcon(i: Int, x: Double, y: Double, color: String) {
        val p = stroke(color, 3.4)
        val fp = paint(color)
        when (i) {
            0 -> canvas.drawPath(path {
                moveTo(x - 18, y)
                lineTo(x, y - 17)
                lineTo(x + 18, y)
                lineTo(x + 13, y)
                lineTo(x + 13, y + 16)
                lineTo(x + 4, y + 16)
                lineTo(x + 4, y + 6)
                lineTo(x - 4, y + 6)
                lineTo(x - 4, y + 16)
                lineTo(x - 13, y + 16)
                lineTo(x - 13, y)
                close()
            }, fp)
            1 -> for (k in 0..2) {
                circle(x - 15, y - 12 + k * 12, 3, fp)
                line(x - 6, y - 12 + k * 12, x + 18, y - 12 + k * 12, p)
            }
            2 -> {
                rr(x - 20, y - 10, 40, 28, 4, fp)
                rr(x - 7, y - 17, 14, 9, 2, stroke(color, 3))
                line(x - 20, y + 2, x + 20, y + 2, stroke(C.tabBg, 2))
            }
            3 -> {
                circle(x - 8, y - 8, 7, fp)
                circle(x + 10, y - 6, 6, fp)
                rr(x - 20, y + 2, 24, 14, 7, fp)
                rr(x + 2, y + 3, 20, 13, 6, fp)
            }
            else -> {
                canvas.drawRect(rect(x - 16, y + 2, 7, 14), fp)
                canvas.drawRect(rect(x - 4, y - 6, 7, 22), fp)
                canvas.drawRect(rect(x + 8, y - 16, 7, 32), fp)
                line(x - 20, y + 17, x + 20, y + 17, stroke(color, 2.5))
            }
        }
    }

    private fun drawTabs(frame: GreatWaveMorningFrame, screenH: Double) {
        val tab = UI.tab
        canvas.drawRect(rect(-10, tab.y, 756, screenH), paint(C.tabBg))
        line(0, tab.y, 736, tab.y, stroke(C.line, 1.5))
        val pos = frame.tab
        val i0 = floor(pos).toInt().coerceIn(0, 4)
        val i1 = min(4, i0 + 1)
        val tx = mix(tab.x[i0], tab.x[i1], pos - i0)
        rr(tx - tab.tile / 2, tab.y + 12, tab.tile, 64, 14, paint(C.blue))
        for (i in 0..4) {
            val on = max(0.0, 1 - abs(pos - i))
            tabIcon(i, tab.x[i], tab.y + 44, mixCol(C.tabIcon, "#FFFFFF", on))
            text(COPY.tabs[i], tab.x[i], tab.y + 94, 19, paint(mixCol(C.tabIcon, C.navy, on)), 0.5)
        }
    }

    fun drawScene(output: Canvas, w: Float, h: Float, density: Float, frame: GreatWaveMorningFrame) {
        canvas = output
        blurIndex = 0
        canvas.drawColor(color(C.page))
        canvas.save()
        val screenScale = w / 402.0
        scale(screenScale)
        translate(0, DY)
        scale(S)
        val screenH = (h / screenScale - DY) / S
        drawContent(frame)
        drawSea(frame)
        drawHeaderText(frame)
        drawTabs(frame, screenH)
        canvas.restore()
        // Grain lives in logical device points, above every layer, exactly like Skia's overlay.
        if (Build.VERSION.SDK_INT >= 33 && paper != null) {
            paper.setFloatUniform("scale", density)
            canvas.drawRect(0f, 0f, w, h, paint("#000000").apply {
                shader = paper
            })
        } else {
            if (fallbackGrain == null || grainScale != density || fallbackGrain?.width != ceil(w / density).toInt() || fallbackGrain?.height != ceil(h / density).toInt()) {
                val bw = ceil(w / density).toInt().coerceAtLeast(1)
                val bh = ceil(h / density).toInt().coerceAtLeast(1)
                val pixels = IntArray(bw * bh)
                fun hash(x: Double, y: Double): Double {
                    val value = sin(x * 12.9898 + y * 78.233) * 43758.5453
                    return value - floor(value)
                }
                for (y in 0 until bh) for (x in 0 until bw) {
                    val grain = hash(floor(x * 0.75), floor(y * 0.75))
                    val fiber = hash(floor(x * 0.08), floor(y * 1.4))
                    pixels[y * bw + x] = (((grain * 0.55 + fiber * 0.45) * 0.07 * 255).roundToInt() shl 24)
                }
                fallbackGrain = Bitmap.createBitmap(pixels, bw, bh, Bitmap.Config.ARGB_8888)
                grainScale = density
            }
            fallbackGrain?.let {
                canvas.drawBitmap(it, null, RectF(0f, 0f, w, h), null)
            }
        }
    }
}
