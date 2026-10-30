package com.ngocdevv.nativecook.blocks.sheets.goldticketreward

import android.content.Context
import android.graphics.*
import android.os.Build
import androidx.core.graphics.PathParser
import kotlin.math.*
import com.ngocdevv.nativecook.blocks.sheets.goldticketreward.GoldTicketRewardMotion.Frame
import com.ngocdevv.nativecook.blocks.sheets.goldticketreward.GoldTicketRewardMotion.T
import com.ngocdevv.nativecook.blocks.sheets.goldticketreward.GoldTicketRewardMotion.mix
import com.ngocdevv.nativecook.blocks.sheets.goldticketreward.GoldTicketRewardTheme.A
import com.ngocdevv.nativecook.blocks.sheets.goldticketreward.GoldTicketRewardTheme.B
import com.ngocdevv.nativecook.blocks.sheets.goldticketreward.GoldTicketRewardTheme.Box
import com.ngocdevv.nativecook.blocks.sheets.goldticketreward.GoldTicketRewardTheme.x
import com.ngocdevv.nativecook.blocks.sheets.goldticketreward.GoldTicketRewardTheme.yb
import com.ngocdevv.nativecook.blocks.sheets.goldticketreward.GoldTicketRewardTheme.yt


/** Images, labels, car-alpha particles and the 3× blurred page are constructed only once. */
internal class GoldTicketRewardArt(context: Context) {
    private val cars = context.assets.open("goldticketreward/goldticketreward_cars.webp").use {
        requireNotNull(BitmapFactory.decodeStream(it))
    }
    private val coin = context.assets.open("goldticketreward/goldticketreward_coin-atlas.webp").use {
        requireNotNull(BitmapFactory.decodeStream(it))
    }
    private val faces = listOf("Regular", "Medium", "SemiBold", "Bold").associateWith {
        Typeface.createFromAsset(context.assets, "goldticketreward/goldticketreward_Poppins-$it.ttf")
    }
    private val labels = mutableMapOf<String, Paint>()
    private val advances = mutableMapOf<String, FloatArray>()
    private val starPath = requireNotNull(PathParser.createPathFromPathData("M0 -10 Q1.6 -1.6 10 0 Q1.6 1.6 0 10 Q-1.6 1.6 -10 0 Q-1.6 -1.6 0 -10 Z"))
    private val runtime = if (Build.VERSION.SDK_INT >= 33) GoldTicketRewardRuntime(cars) else null
    private lateinit var c: Canvas
    private val carBox = Box(A.cars.x, A.cars.y + A.cars.h - A.cars.w * 280 / 900, A.cars.w, A.cars.w * 280 / 900)
    private val dust = GoldTicketRewardDust.make(cars, carBox)
    private val page: Bitmap

    private fun color(hex: Int, a: Double = 1.0) = (a.coerceIn(0.0, 1.0).times(255).roundToInt() shl 24) or hex

    private fun paint(hex: Int = 0xffffff,
        a: Double = 1.0) = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        color = color(hex, a)
    }

    private fun stroke(hex: Int, w: Double, a: Double = 1.0) = paint(hex, a).apply {
        style = Paint.Style.STROKE
        strokeWidth = w.toFloat()
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private fun rect(x: Double,
        y: Double,
        w: Double,
        h: Double) = RectF(x.toFloat(),
        y.toFloat(),
        (x + w).toFloat(),
        (y + h).toFloat())

    private fun rr(b: Box,
        r: Double,
        p: Paint) = c.drawRoundRect(rect(b.x,
        b.y,
        b.w,
        b.h),
        r.toFloat(),
        r.toFloat(),
        p)

    private fun circle(x: Double,
        y: Double,
        r: Double,
        p: Paint) = c.drawCircle(x.toFloat(),
        y.toFloat(),
        max(0.0,
        r).toFloat(),
        p)

    private fun line(x: Double,
        y: Double,
        x2: Double,
        y2: Double,
        p: Paint) = c.drawLine(x.toFloat(),
        y.toFloat(),
        x2.toFloat(),
        y2.toFloat(),
        p)

    private fun translate(x: Double, y: Double) = c.translate(x.toFloat(), y.toFloat())

    private fun scale(x: Double, y: Double = x) = c.scale(x.toFloat(), y.toFloat())

    private inline fun saved(block: () -> Unit) {
        c.save()
        try {
            block()
        } finally {
            c.restore()
        }
    }

    private inline fun layer(alpha: Double = 1.0, block: () -> Unit) {
        c.saveLayer(null, paint(0xffffff, alpha))
        try {
            block()
        } finally {
            c.restore()
        }
    }

    private fun linear(x1: Double,
        y1: Double,
        x2: Double,
        y2: Double,
        colors: IntArray,
        stops: FloatArray? = null) = paint().apply {
        shader = LinearGradient(x1.toFloat(),
            y1.toFloat(),
            x2.toFloat(),
            y2.toFloat(),
            colors,
            stops,
            Shader.TileMode.CLAMP)
    }

    private fun radial(x: Double, y: Double, r: Double, colors: IntArray, stops: FloatArray? = null) = paint().apply {
        shader = RadialGradient(x.toFloat(),
            y.toFloat(),
            r.toFloat().coerceAtLeast(0.01f),
            colors,
            stops,
            Shader.TileMode.CLAMP)
    }

    private fun path(vararg xy: Double) = Path().apply {
        moveTo(xy[0].toFloat(), xy[1].toFloat())
        for (i in 2 until xy.size step 2) lineTo(xy[i].toFloat(), xy[i + 1].toFloat())
    }

    private fun label(key: String,
        text: String,
        target: Double,
        face: String = "SemiBold",
        track: Double = 0.0): Paint {
        val p = paint(0x1e1d2b).apply {
            typeface = faces.getValue(face)
            textSize = 100f
            flags = flags or Paint.SUBPIXEL_TEXT_FLAG or Paint.LINEAR_TEXT_FLAG
            fontFeatureSettings = "'kern' 0, 'liga' 0"
        }
        val probeWidths = FloatArray(text.length)
        p.getTextWidths(text, probeWidths)
        p.textSize = (target / (probeWidths.sum() / 100 + track * (text.length - 1))).toFloat()
        labels[key] = p
        return p
    }
    private val strings = mutableMapOf<String, String>()
    private val tracks = mutableMapOf<String, Double>()

    private fun add(key: String, text: String, target: Double, face: String = "SemiBold", track: Double = 0.0) {
        strings[key] = text
        tracks[key] = track
        label(key, text, target, face, track)
    }

    private fun text(key: String,
        x: Double,
        y: Double,
        ax: Double = 0.5,
        p: Paint = paint(0x1e1d2b),
        k: Double = 1.0) {
        val font = labels.getValue(key)
        val str = strings.getValue(key)
        val track = tracks.getValue(key) * font.textSize
        val widths = advances.getValue(key)
        val width = widths.sum() + track * (str.length - 1)
        saved {
            translate(x, y)
            scale(k)
            val q = Paint(p).apply {
                typeface = font.typeface
                textSize = font.textSize
                flags = flags or Paint.SUBPIXEL_TEXT_FLAG or Paint.LINEAR_TEXT_FLAG
                fontFeatureSettings = font.fontFeatureSettings
            }
            var cursor = - width * ax
            for (index in str.indices) {
                c.drawText(str, index, index + 1, cursor.toFloat(), font.textSize * 0.35f, q)
                cursor += widths[index] + track
            }
        }
    }

    init {
        add("title", "Inspection details", x(100.0))
        add("help", "Get Help", x(43.0), "Medium")
        add("status", "Expert Assigned", x(91.0))
        add("expertRef", "Rajesh Kumar assigned", x(110.0))
        add("expert", "Arjun Mehta assigned", x(110.0))
        labels.getValue("expert").textSize = labels.getValue("expertRef").textSize
        add("verified", "Verified inspector", x(67.0), "Regular")
        add("progress", "Inspection in Progress", x(91.0), "Medium")
        add("section", "YOU CAN MAKE A DIFFERENCE", x(98.0), track = 0.06)
        add("pros", "Our professionals work hard", x(134.0))
        add("prosSub", "Small gestures keep them going all day", x(122.0), "Regular")
        add("water", "Offer a glass of water", x(77.0), "Medium")
        add("thanks", "THANKS FOR YOUR INTEREST!", x(132.0), "Bold", 0.11)
        add("callback", "Our Car loan expert team will call you shortly", x(228.0), "Regular")
        add("winA", "Book Test Drive to Win", x(130.0))
        add("prizeA", "1 Gm Gold Coin", x(131.0), "Bold")
        add("winB", "Book Test Drive to Win", x(109.0), "Medium")
        add("prizeB", "1 Gm Gold Coin", x(129.0), "Bold")
        listOf("Benefits upto 10 Lakhs", "200+ Car Models", "Compare on the spot").forEachIndexed {
            i, s -> add("benefit$i", s, x(83.0))
        }
        for (i in 1..2) labels.getValue("benefit$i").textSize = labels.getValue("benefit0").textSize
        add("cta", "Check Now", x(65.0), "Medium")
        labels.forEach {
            (key, font) ->
            val text = strings.getValue(key)
            advances[key] = FloatArray(text.length).also {
                font.getTextWidths(text, it)
            }
        }
        val source = Bitmap.createBitmap(1206, 2622, Bitmap.Config.ARGB_8888)
        c = Canvas(source)
        c.scale(3f, 3f)
        drawPage()
        page = GoldTicketRewardPageBlur.blur(source, 4.8)
        if (page !== source) source.recycle()
    }

    private fun drawPage() {
        c.drawColor(color(0xf3f3f6))
        val ink = paint(0x1e1d2b)
        val mute = paint(0x77768a)
        c.drawPath(path(x(20.0), yt(54.0) - 6, x(20.0) - 7, yt(54.0), x(20.0), yt(54.0) + 6), stroke(0x1e1d2b, 1.8))
        line(x(20.0) - 7, yt(54.0), x(20.0) + 8, yt(54.0), stroke(0x1e1d2b, 1.8))
        text("title", x(42.0), yt(54.0), 0.0)
        text("help", x(253.0), yt(54.0), 1.0)
        val hi = x(204.0)
        val hy = yt(54.0)
        c.drawArc(rect(hi - 6.5, hy - 7, 13.0, 13.0), 180f, 180f, false, stroke(0x1e1d2b, 1.6))
        line(hi - 6.5, hy, hi - 6.5, hy + 3.5, stroke(0x1e1d2b, 1.6))
        line(hi + 6.5, hy, hi + 6.5, hy + 3.5, stroke(0x1e1d2b, 1.6))
        circle(x(136.0), yt(116.0), x(19.5), stroke(0x3f3ab9, x(2.3)))
        c.drawPath(path(x(136.0) - 8,
            yt(116.0) + 0.5,
            x(136.0) - 2.5,
            yt(116.0) + 6,
            x(136.0) + 8.5,
            yt(116.0) - 6),
            stroke(0x3f3ab9,
            x(2.3)))
        text("status", x(136.5), yt(155.0))
        val card = GoldTicketRewardTheme.box(9.0, 178.0, 265.0, 262.0, false)
        rr(card, 12.0, paint())
        rr(card, 12.0, stroke(0xe4e4ec, 1.0))
        circle(x(30.0), yt(204.0), x(8.0) + 3, paint(0xececf3))
        circle(x(30.0), yt(204.0) - 2.6, 3.4, paint(0x9a99ab))
        c.drawArc(rect(x(30.0) - 6, yt(204.0) + 1.5, 12.0, 10.0), 180f, 180f, true, paint(0x9a99ab))
        text("expert", x(48.0), yt(195.0), 0.0, ink)
        rr(GoldTicketRewardTheme.box(54.0, 207.5, 66.0, 214.5, false), 3.0, paint(0xf2c94c))
        text("verified", x(70.0), yt(211.5), 0.0, mute)
        saved {
            translate(x(242.0), yt(205.0))
            c.drawPath(requireNotNull(PathParser.createPathFromPathData("M-5.5 -7 Q-7 -7 -7 -5 Q-6 3 3 7 Q5 7.5 6 6 L7 4 Q7.2 3 6 2.4 L3.6 1.4 Q2.6 1 2 1.9 L1.3 2.8 Q-2.8 1 -4.4 -2.8 L-3.4 -3.5 Q-2.4 -4.1 -2.8 -5.2 L-3.8 -7 Q-4.3 -7.6 -5.5 -7 Z")),
                paint(0x3f3ab9))
        }
        line(card.x + 12, yt(232.0), card.x + card.w - 12, yt(232.0), stroke(0xe4e4ec, 1.0))
        circle(x(25.0), yt(248.0), 5.5, stroke(0x77768a, 1.4))
        line(x(25.0), yt(248.0) - 2.8, x(25.0), yt(248.0), stroke(0x77768a, 1.4))
        line(x(25.0), yt(248.0), x(25.0) + 2.2, yt(248.0) + 1.4, stroke(0x77768a, 1.4))
        text("progress", x(42.0), yt(248.0), 0.0)
        c.drawPath(path(x(247.0) - 2.5,
            yt(248.0) - 5,
            x(247.0) + 2.5,
            yt(248.0),
            x(247.0) - 2.5,
            yt(248.0) + 5),
            stroke(0x77768a,
            1.6))
        text("section", x(10.0), yt(289.0), 0.0, mute)
        val card2 = GoldTicketRewardTheme.box(9.0, 300.0, 265.0, 362.0, false)
        rr(card2, 12.0, paint())
        rr(card2, 12.0, stroke(0xe4e4ec, 1.0))
        text("pros", x(18.0), yt(312.0), 0.0)
        text("prosSub", x(18.0), yt(323.0), 0.0, mute)
        c.drawPath(path(x(22.0) - 4,
            yt(347.0) - 5,
            x(22.0) - 3,
            yt(347.0) + 5,
            x(22.0) + 3,
            yt(347.0) + 5,
            x(22.0) + 4,
            yt(347.0) - 5),
            stroke(0x3f3ab9,
            1.4))
        text("water", x(30.0) + 6, yt(347.0), 0.0, paint(0x3f3ab9))
    }

    fun drawScene(canvas: Canvas,
        width: Float,
        height: Float,
        f: Frame,
        tiltX: Double = 0.0,
        tiltY: Double = 0.0,
        cta: Double = 0.0) {
        c = canvas
        val s = width / 402.0
        val dy = height - 874 * s
        c.drawBitmap(page, null, rect(0.0, 0.0, 402 * s, 874 * s), paint())
        c.drawRect(0f,
            0f,
            width,
            height,
            paint((mix(31.0,
            24.0,
            f.dim).roundToInt() shl 16) or (mix(29.0,
            23.0,
            f.dim).roundToInt() shl 8) or mix(46.0,
            30.0,
            f.dim).roundToInt(),
            mix(0.75,
            0.85,
            f.dim)))
        saved {
            translate(0.0, dy)
            scale(s)
            if (f.glow > 0.003) {
                val p = linear(0.0,
                    A.glowTop,
                    0.0,
                    A.stageEdge,
                    intArrayOf(color(0xd8b464,
                    0.0),
                    color(0xd8b464,
                    0.12),
                    color(0xd8b464,
                    0.29),
                    color(0xd8b464,
                    0.48),
                    color(0xd8b464,
                    0.6),
                    color(0xf1e1ac,
                    0.9),
                    color(0xf1e1ac)),
                    floatArrayOf(0f,
                    0.41f,
                    0.57f,
                    0.75f,
                    0.83f,
                    0.93f,
                    1f)).apply {
                    alpha = (f.glow * 255).roundToInt()
                }
                c.drawRect(rect(0.0, A.glowTop, 402.0, A.stageEdge - A.glowTop), p)
                val q = radial(carBox.x + carBox.w / 2,
                    A.stageTop - 6,
                    carBox.w * 0.62,
                    intArrayOf(color(0xf0cf7a,
                    0.32),
                    0)).apply {
                    alpha = (f.glow * 255).roundToInt()
                }
                c.drawRect(rect(0.0, A.glowTop, 402.0, A.stageEdge - A.glowTop), q)
            }
            drawSheet(f)
            if (f.starsA * f.glow > 0.01) A.sparkles.forEach {
                sp -> val tw = 0.75 + 0.25 * (0.5 + 0.5 * sin(2 * PI * (sp.k * f.t / 9 + sp.ph)))
                drawStar(sp.x, sp.y, sp.s * tw, 0.0, f.starsA * f.glow, false)
            }
            if (f.cars < 0.999) {
                if (Build.VERSION.SDK_INT >= 33 && runtime != null) {
                    c.drawRect(rect(carBox.x, carBox.y - 4, carBox.w, carBox.h + 8), paint().apply {
                        shader = runtime.dissolve(carBox, f.cars, f.carsGlint)
                    }
                    )
                } else c.drawBitmap(cars, null, rect(carBox.x, carBox.y, carBox.w, carBox.h), paint(0xffffff, 1 - f.cars))
            }
            if (f.thanks.a > 0.01) text("thanks", A.thanks.x, A.thanks.y + f.thanks.dy, p = paint(0x1e1d2b, f.thanks.a))
            if (f.callback.a > 0.01) text("callback",
                A.callback.x,
                A.callback.y + f.callback.dy,
                p = paint(0x55545f,
                f.callback.a))
            if (f.divider > 0.01) c.drawRect(rect(x(23.0),
                yb(347.0) - 0.5,
                x(227.0),
                1.0),
                linear(x(23.0),
                0.0,
                x(250.0),
                0.0,
                intArrayOf(0,
                color(0x968c6e,
                0.35),
                0)).apply {
                alpha = (255 * f.divider).roundToInt()
            }
            )
            drawAura(f)
            drawTicket(f, tiltX, tiltY)
            drawDust(f.t)
            drawSparks(f.t)
            f.stars.forEachIndexed {
                i,
                sp -> if (sp.a > 0.01) drawStar(sp.x - tiltX * 7 * f.bAmount,
                    sp.y - tiltY * 5 * f.bAmount,
                    sp.s,
                    sp.rot,
                    sp.a,
                    B.sparkles[i].gold)
            }
            drawBenefits(f)
            drawButton(f, cta)
            if (f.close.a > 0.01) saved {
                translate(B.close.x, B.close.y)
                c.rotate((f.close.rot * 180 / PI).toFloat())
                scale(f.close.s)
                val h = x(9.0) / 2
                line(-h, - h, h, h, stroke(0xffffff, 2.0, f.close.a))
                line(-h, h, h, - h, stroke(0xffffff, 2.0, f.close.a))
            }
        }
    }

    private fun drawSheet(f: Frame) {
        val inkR = 640 + f.ink * 1060

        fun ink() = radial(A.ticket.x + A.ticket.w / 2,
            1574.0,
            inkR,
            intArrayOf(color(0x000000),
            0),
            floatArrayOf(((inkR - 150) / inkR).toFloat(),
            1f))
        if (f.ink < 0.999) {
            val p = ((f.ink - 0.08) / 0.5).coerceIn(0.0, 1.0)
            layer(1 - p * p * (3 - 2 * p)) {
                val sink = f.ink * f.ink * 56
                translate(0.0, sink)
                val stage = path(0.0,
                    A.stageEdge,
                    A.stageInset,
                    A.stageTop,
                    402 - A.stageInset,
                    A.stageTop,
                    402.0,
                    A.stageEdge,
                    402.0,
                    874.0,
                    0.0,
                    874.0).apply {
                    close()
                }
                c.drawPath(stage,
                    linear(0.0,
                    A.stageTop,
                    0.0,
                    A.band.y,
                    intArrayOf(color(0xf8efd0),
                    color(0xfdf8ea),
                    color(0xffffff)),
                    floatArrayOf(0f,
                    0.42f,
                    1f)))
                if (f.benefits < 0.999) c.drawRect(rect(A.band.x, A.band.y, A.band.w, A.band.h), paint(0xfbf6e8, 1 - f.benefits))
                if (f.ink > 0.001) {
                    translate(0.0, - sink)
                    c.drawRect(rect(0.0, A.glowTop, 402.0, 874 - A.glowTop), ink().apply {
                        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
                    }
                    )
                }
            }
        }
        if (f.ink > 0.001) {
            layer {
                c.drawRect(rect(0.0,
                    B.fadeTop,
                    402.0,
                    874 - B.fadeTop),
                    linear(0.0,
                    B.fadeTop,
                    0.0,
                    874.0,
                    intArrayOf(color(0x5233ae,
                    0.0),
                    color(0x5233ae,
                    0.3),
                    color(0x5233ae,
                    0.66),
                    color(0x5233ae),
                    color(0x2c0e8d),
                    color(0x211060)),
                    floatArrayOf(0f,
                    48f / 229,
                    88f / 229,
                    ((B.full - B.fadeTop) / (874 - B.fadeTop)).toFloat(),
                    188f / 229,
                    1f)))
                c.drawRect(rect(0.0,
                    B.fadeTop,
                    402.0,
                    874 - B.fadeTop),
                    radial(201.0,
                    B.benefits[0].y - 4,
                    402 * 0.55,
                    intArrayOf(color(0x6a4fd6,
                    0.32),
                    0)))
                if (f.ink < 0.999) c.drawRect(rect(0.0, 0.0, 402.0, 874.0), ink().apply {
                    xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
                }
                )
            }
            val rim = sin(PI * min(1.0, f.ink * 1.15))
            if (rim > 0.01 && f.ink < 0.999) c.drawRect(rect(0.0,
                0.0,
                402.0,
                874.0),
                radial(A.ticket.x + A.ticket.w / 2,
                1574.0,
                inkR,
                intArrayOf(0,
                color(0x9678ff,
                0.55 * rim),
                0),
                floatArrayOf(((inkR - 195) / inkR).toFloat(),
                ((inkR - 75) / inkR).toFloat(),
                1f)).apply {
                xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
            }
            )
        }
    }

    private fun drawAura(f: Frame) {
        if (f.aura <= 0.005) return
        val bx = f.ticket
        val cx = bx.x + bx.w / 2
        val cy = bx.y + bx.h / 2
        val p = radial(cx,
            cy,
            bx.w * 0.85,
            intArrayOf(color(0xffb048,
            min(0.75,
            f.aura)),
            0),
            floatArrayOf(0.25f,
            1f)).apply {
            shader.setLocalMatrix(Matrix().apply {
                setScale(1f, 0.62f, cx.toFloat(), cy.toFloat())
            }
            )
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
        }
        c.drawRect(rect(cx - bx.w, cy - bx.w * 0.7, bx.w * 2, bx.w * 1.4), p)
    }

    private fun shadow(b: Box, r: Double, hex: Int, a: Double, sigma: Double, oval: Boolean = false) {
        if (a <= 0.01) return
        // A rasterized mask works on all hardware Canvas API levels
        // Its shape is reused.
        val key = "${b.w.roundToInt()}:${b.h.roundToInt()}:$r:$sigma:$oval"
        val mask = shadowMasks.getOrPut(key) {
            val pad = ceil(sigma * 3).toInt()
            val bmp = Bitmap.createBitmap(ceil(b.w).toInt() + pad * 2, ceil(b.h).toInt() + pad * 2, Bitmap.Config.ALPHA_8)
            val maskCanvas = Canvas(bmp)
            val maskPaint = paint().apply {
                maskFilter = BlurMaskFilter(((sigma - 0.5) / 0.57735).toFloat(), BlurMaskFilter.Blur.NORMAL)
            }
            if (oval) maskCanvas.drawOval(rect(pad.toDouble(), pad.toDouble(), b.w, b.h), maskPaint)
            else maskCanvas.drawRoundRect(rect(pad.toDouble(), pad.toDouble(), b.w, b.h), r.toFloat(), r.toFloat(), maskPaint)
            bmp
        }
        c.drawBitmap(mask, (b.x - ceil(sigma * 3)).toFloat(), (b.y - ceil(sigma * 3)).toFloat(), paint(hex, a))
    }
    private val shadowMasks = object: LinkedHashMap<String, Bitmap>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap> ?) = size > 96
    }

    private fun drawTicket(f: Frame, tx: Double, ty: Double) {
        val bx = f.ticket
        val mp = (bx.w - A.ticket.w) / (B.ticket.w - A.ticket.w)
        val cx = bx.x + bx.w / 2
        val cy = bx.y + bx.h / 2
        saved {
            translate(cx, cy + f.tDy)
            if (tx != 0.0 || ty != 0.0) {
                // Flatten P(520) · Rx(-tiltY × .2) · Ry(tiltX × .24) exactly as RN.
                val rx = -ty * 0.2
                val ry = tx * 0.24
                val transform = Matrix().apply {
                    setValues(floatArrayOf(
                        cos(ry).toFloat(), 0f, 0f,
                        (sin(rx) * sin(ry)).toFloat(), cos(rx).toFloat(), 0f,
                        (cos(rx) * sin(ry) / 520).toFloat(), (-sin(rx) / 520).toFloat(), 1f,
                    ))
                }
                c.concat(transform)
            }
            scale(f.tScaleX, f.tScaleY)
            translate(-cx, - cy)
            shadow(Box(bx.x, bx.y + 3, bx.w, bx.h), A.radius, 0x5a461e, 0.16 * (1 - f.lift), 7.0)
            if (f.halo > 0.01 && f.lift < 0.99) shadow(Box(A.coin.x - A.halo * f.halo,
                A.coin.y + 2 - A.halo * f.halo,
                A.halo * f.halo * 2,
                A.halo * f.halo * 2),
                A.halo * f.halo,
                0x5a461e,
                0.16 * (1 - f.lift),
                7.0)
            shadow(Box(bx.x + 6, bx.y + 12, bx.w - 12, bx.h), A.radius, 0x1a0656, 0.55 * f.lift, 13.0)
            shadow(Box(bx.x - 4, bx.y - 2, bx.w + 8, bx.h + 4), A.radius + 4, 0xffb347, 0.2 * f.lift, 13.0)
            saved {
                val notches = Path().apply {
                    addCircle(bx.x.toFloat(), cy.toFloat(), f.notch.toFloat(), Path.Direction.CW)
                    addCircle((bx.x + bx.w).toFloat(), cy.toFloat(), f.notch.toFloat(), Path.Direction.CW)
                }
                c.clipOutPath(notches)
                if (f.halo > 0.01) circle(A.coin.x, A.coin.y, A.halo * f.halo, paint())
                rr(bx, A.radius, paint())
                val round = Path().apply {
                    addRoundRect(rect(bx.x, bx.y, bx.w, bx.h), A.radius.toFloat(), A.radius.toFloat(), Path.Direction.CW)
                }
                val goldOn = f.gold > 0.001
                val edge = mix(-0.4, 1.4, f.gold)

                fun at(v: Double) = bx.x + bx.w * (edge - (v - 0.5) * 0.35)
                val goldSide = path(bx.x - 400,
                    bx.y - 200,
                    at(-200 / bx.h),
                    bx.y - 200,
                    at(1 + 200 / bx.h),
                    bx.y + bx.h + 200,
                    bx.x - 400,
                    bx.y + bx.h + 200).apply {
                    close()
                }
                if (goldOn) saved {
                    c.clipPath(round)
                    if (Build.VERSION.SDK_INT >= 33 && runtime != null) c.drawRect(rect(bx.x, bx.y, bx.w, bx.h), paint().apply {
                        shader = runtime.foil(bx, f.gold, f.sweep, tx, ty)
                    }
                    )
                    else {
                        c.clipPath(goldSide)
                        c.drawRect(rect(bx.x,
                            bx.y,
                            bx.w,
                            bx.h),
                            linear(bx.x,
                            bx.y,
                            bx.x + bx.w,
                            bx.y + bx.h,
                            intArrayOf(color(0xf5d677),
                            color(0xe3a23f),
                            color(0x7a4a18))))
                    }
                }
                val winX = mix(A.win.x, B.win.x, mp)
                val winY = mix(A.win.y, B.win.y, mp)
                val prizeX = mix(A.prize.x, B.prize.x, mp)
                val prizeY = mix(A.prize.y, B.prize.y, mp)
                val winW = x(mix(130.0, 109.0, mp))
                val prizeW = x(mix(131.0, 129.0, mp))
                if (f.gold < 0.999) saved {
                    if (goldOn) c.clipOutPath(goldSide)
                    text("winA", winX, winY, p = paint(0x1c1b22), k = winW / x(130.0))
                    text("prizeA",
                        prizeX,
                        prizeY,
                        p = linear(-x(131.0) / 2,
                        0.0,
                        x(131.0) / 2,
                        0.0,
                        intArrayOf(color(0x9c6a18),
                        color(0xe3c16e),
                        color(0xe6c98a))),
                        k = prizeW / x(131.0))
                }
                if (goldOn) saved {
                    c.clipPath(goldSide)
                    text("prizeB", prizeX, prizeY + 1.2, p = paint(0x7a3f05, 0.35), k = prizeW / x(129.0))
                    text("winB", winX, winY, p = paint(0xfff1d6, 0.95), k = winW / x(109.0))
                    text("prizeB", prizeX, prizeY, p = paint(), k = prizeW / x(129.0))
                }
            }
            drawCoin(f, bx, tx, ty)
        }
    }

    private fun drawCoin(f: Frame, bx: Box, tx: Double, ty: Double) {
        val k = f.coin
        val cx = k.x + tx * 3 * f.bAmount
        val cy = k.y + ty * 2 * f.bAmount
        val sw = k.r * (0.9 + k.shadow * 0.7)
        val sy = max(bx.y + 6, cy + k.r * 0.55 + k.shadow * 30)
        shadow(Box(cx - sw,
            sy - k.r * 0.28,
            sw * 2,
            k.r * 0.56),
            k.r * 0.28,
            if (f.lift > 0.5) 0x5b2a00 else 0x6b5a33,
            0.34 * (1 - k.shadow * 0.75),
            5.0,
            oval = true)
        val i = ((floor(k.frame + 0.5).toInt()%24) + 24)%24
        val half = k.r * 1.1
        c.drawBitmap(coin,
            Rect(i%6 * 256,
            i / 6 * 256,
            i%6 * 256 + 256,
            i / 6 * 256 + 256),
            rect(cx - half,
            cy - half,
            half * 2,
            half * 2),
            paint())
        if (f.glint > 0 && f.glint < 1) saved {
            c.clipPath(Path().apply {
                addCircle(cx.toFloat(), cy.toFloat(), (k.r * 0.97).toFloat(), Path.Direction.CW)
            }
            )
            val gx = cx + mix(-1.6, 1.6, f.glint) * k.r
            c.drawRect(rect(cx - k.r,
                cy - k.r,
                k.r * 2,
                k.r * 2),
                linear(gx - k.r * 0.5,
                cy - k.r,
                gx + k.r * 0.5,
                cy + k.r,
                intArrayOf(0,
                color(0xfff8dc,
                0.85),
                0)).apply {
                xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
            }
            )
        }
        if (f.ringA > 0.01) circle(B.coin.x, B.coin.y, f.ringR, stroke(0xffe7a8, mix(0.8, 2.6, f.ringA / 0.85), f.ringA))
        if (f.flash > 0.01) circle(B.coin.x,
            B.coin.y,
            B.coin.r * 3.4,
            radial(B.coin.x,
            B.coin.y,
            B.coin.r * 3.4,
            intArrayOf(color(0xffe2a0,
            0.75 * f.flash),
            0)).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
        }
        )
    }

    private fun drawStar(x: Double, y: Double, half: Double, rot: Double, a: Double, gold: Boolean) {
        saved {
            translate(x, y)
            c.rotate((rot * 180 / PI).toFloat())
            scale(half / 10)
            val glow = starGlows.getValue(gold)
            c.drawBitmap(glow, - 25f, - 25f, paint(0xffffff, a * 0.55))
            c.drawPath(starPath, paint(if (gold) 0xe8b24a else 0xffffff, a))
            if (gold) {
                scale(0.45)
                c.drawPath(starPath, paint(0xfff4cf, a))
            }
        }
    }
    private val starGlows = listOf(false, true).associateWith {
        gold -> Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888).also {
            bitmap -> Canvas(bitmap).apply {
                translate(25f, 25f)
                drawPath(starPath, paint(if (gold) 0xffcf6a else 0xffffff).apply {
                    maskFilter = BlurMaskFilter(((3 - 0.5) / 0.57735).toFloat(), BlurMaskFilter.Blur.NORMAL)
                }
                )
            }
        }
    }

    private fun drawBenefits(f: Frame) {

        fun col(p: Double) = color((mix(42.0,
            255.0,
            p).roundToInt() shl 16) or (mix(41.0,
            255.0,
            p).roundToInt() shl 8) or mix(51.0,
            255.0,
            p).roundToInt())
        for (i in 0..1) {
            val m = if (i == 0) f.benefits else f.benefits2
            text("benefit$i",
                mix(A.benefits[i].x,
                B.benefits[i].x,
                m),
                mix(A.benefits[i].y,
                B.benefits[i].y,
                m),
                p = paint().apply {
                color = col(m)
                alpha = (255 * mix(1.0, 0.92, m)).roundToInt()
            }
                , k = mix(1.0, 110.0 / 83, m))
        }
        val m = f.benefits
        val sh = mix(x(10.0), x(13.0), m)
        val sx = mix(x(102.0), x(148.0), m)
        val sy = mix(A.benefits[0].y, B.benefits[0].y, m)
        c.drawRect(rect(sx - 0.6, sy - sh / 2, 1.2, sh), paint().apply {
            color = col(m)
            alpha = (255 * mix(0.45, 0.6, m)).roundToInt()
        }
        )
        if (f.third > 0.01) {
            text("benefit2", A.benefits[2].x, A.benefits[2].y, p = paint(0x2a2933, f.third))
            c.drawRect(rect(x(178.0) - 0.6, A.benefits[0].y - x(5.0), 1.2, x(10.0)), paint(0x2a2933, 0.45 * f.third))
        }
    }

    private fun drawButton(f: Frame, cta: Double) {
        val m = f.button
        val w = mix(A.button.w, B.button.w, m)
        val h = mix(A.button.h, B.button.h, m)
        saved {
            translate(mix(A.button.x, B.button.x, m) + w / 2, mix(A.button.y, B.button.y, m) + h / 2)
            scale(1 - 0.04 * cta)
            if (f.ctaGlow > 0.01) {
                val grow = 3 + 5 * f.ctaGlow
                shadow(Box(-w / 2 - grow,
                    - h / 2 - grow,
                    w + grow * 2,
                    h + grow * 2),
                    A.radius + 2 + grow,
                    0xffffff,
                    0.42 * f.ctaGlow,
                    6 + 8 * f.ctaGlow)
            }
            val box = Box(-w / 2, - h / 2, w, h)
            val round = Path().apply {
                addRoundRect(rect(box.x, box.y, w, h), (A.radius + 2).toFloat(), (A.radius + 2).toFloat(), Path.Direction.CW)
            }
            val edge = - w / 2 - 30 + (w + 60) * m
            val white = path(-w, - h, edge + 12, - h, edge - 12, h, - w, h).apply {
                close()
            }
            c.clipPath(round)
            if (m < 0.999) {
                rr(box, A.radius + 2, paint(0x5835d1))
                saved {
                    if (m > 0.001)c.clipOutPath(white)
                    text("cta", 0.0, 0.0, p = paint(0xf6f1ff), k = mix(1.0, 62.0 / 65, m))
                }
            }
            if (m > 0.001) saved {
                if (m < 0.999)c.clipPath(white)
                rr(box, A.radius + 2, paint())
                text("cta", 0.0, 0.0, p = paint(0x4b33b8), k = mix(1.0, 62.0 / 65, m))
            }
        }
    }

    private fun drawDust(t: Double) {
        val out = t >= T.carsOut[0] && t < T.gold[1] + 0.2
        val home = t >= T.goldOut[0] && t < T.carsIn[1]
        if (!out && !home) return

        fun bez(a: Double, b: Double, c: Double, e: Double, q: Double): Double {
            val u = 1 - q
            return u * u * u * a + 3 * u * u * q * b + 3 * u * q * q * c + q * q * q * e
        }
        for (m in dust) {
            val k: Double
            if (out) {
                if (t < m.rel || t > m.arr + 0.06) continue
                k = min(1.0, (t - m.rel) / (m.arr - m.rel))
            } else {
                if (t < m.back0 || t > m.back0 + 0.5) continue
                k = 1 - (t - m.back0) / 0.5
            }
            val q = k * k * (3 - 2 * k)
            val a = min(1.0, (if (out)k else 1 - k) / 0.12) * (if (k > 0.86 && out) 1 - (k - 0.86) / 0.14 else 1.0) * 0.95
            for (j in 0..1) {
                val qq = if (j == 0)q else max(0.0, q - (if (out)0.05 else - 0.05))
                circle(bez(m.x0,
                    m.c1x,
                    m.c2x,
                    m.tx,
                    qq),
                    bez(m.y0,
                    m.c1y,
                    m.c2y,
                    m.ty,
                    qq),
                    m.size * (1 - 0.35 * q) * (if (j == 0)1.0 else 0.75),
                    paint(if (m.warm > 0.5)0xffd774 else 0xfff0b8,
                    a * (if (j == 0)1.0 else 0.4)).apply {
                    xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
                }
                )
            }
        }
    }

    private fun drawSparks(t: Double) {
        val tau = t - T.land
        if (tau <= 0 || tau > 0.75)return
        for (i in 0 until 18) {
            val h1 = sin((i + 1) * 78.233) * 43758.5453
            val r1 = h1 - floor(h1)
            val h2 = sin((i + 1) * 39.425) * 24634.6345
            val r2 = h2 - floor(h2)
            val side = if (i%2 == 0) - 1 else 1
            val ang = - PI / 2 + side * (0.35 + r1 * 1.05)
            val sp = 150 + r2 * 190
            val life = 0.38 + r1 * 0.3
            if (tau > life)continue
            val vx = cos(ang) * sp
            val vy = sin(ang) * sp
            val x = B.coin.x + side * B.coin.r * (0.45 + 0.4 * r2) + vx * tau
            val y = B.ticket.y + 1 + vy * tau + 450 * tau * tau
            val fade = 1 - tau / life
            line(x, y, x - vx * 0.02, y - (vy + 900 * tau) * 0.02, stroke(0xffe7a0, 0.7 + 1.3 * fade, fade).apply {
                xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
            }
            )
        }
    }
}
