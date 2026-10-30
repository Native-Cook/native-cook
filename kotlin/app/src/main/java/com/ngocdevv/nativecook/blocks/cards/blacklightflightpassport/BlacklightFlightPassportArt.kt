package com.ngocdevv.nativecook.blocks.cards.blacklightflightpassport

import android.content.Context
import android.graphics.*
import android.os.Build
import androidx.core.graphics.PathParser
import kotlin.math.*
import com.ngocdevv.nativecook.blocks.cards.blacklightflightpassport.BlacklightFlightPassportMotion.Frame
import com.ngocdevv.nativecook.blocks.cards.blacklightflightpassport.BlacklightFlightPassportMotion.LOOP
import com.ngocdevv.nativecook.blocks.cards.blacklightflightpassport.BlacklightFlightPassportMotion.smooth
import com.ngocdevv.nativecook.blocks.cards.blacklightflightpassport.BlacklightFlightPassportTheme.UI
import com.ngocdevv.nativecook.blocks.cards.blacklightflightpassport.BlacklightFlightPassportTheme.Page
import com.ngocdevv.nativecook.blocks.cards.blacklightflightpassport.BlacklightFlightPassportTheme.Copy
import com.ngocdevv.nativecook.blocks.cards.blacklightflightpassport.BlacklightFlightPassportTheme.Palette

/** Cached geometry and bundled typefaces; Android Canvas uses plain sRGB throughout. */
internal class BlacklightFlightPassportArt(context: Context) {
    private val theme = BlacklightFlightPassportTheme
    private val map = context.assets.open("blacklightflightpassport/blacklightflightpassport_map-dots.webp").use {
        requireNotNull(BitmapFactory.decodeStream(it))
    }
    private val fonts = listOf("Inter-Regular", "Inter-Medium", "Inter-SemiBold", "Inter-Bold", "JetBrainsMono-Regular").map {
        Typeface.createFromAsset(context.assets, "blacklightflightpassport/blacklightflightpassport_$it.ttf")
    }
    private val fontSpecs = mapOf(
        "title" to (1 to 17.0), "sub" to (0 to 14.0), "brand" to (2 to 14.5), "kinds" to (2 to 6.8),
        "big" to (2 to Page.BIG_SIZE), "word" to (0 to Page.WORD_SIZE), "row" to (1 to Page.ROW_SIZE),
        "rowB" to (0 to Page.ROW_SIZE), "statL" to (0 to 10.5), "statV" to (1 to 18.0),
        "mrz" to (4 to Page.MRZ_SIZE), "button" to (1 to 15.0), "share" to (0 to 15.0),
        "stamp" to (3 to 11.0), "tiny" to (2 to 7.0),
    )
    private val textPaints = fontSpecs.mapValues { (_, spec) ->
        paint().apply { typeface = fonts[spec.first]; textSize = spec.second.toFloat() }
    }
    private val plane = requireNotNull(PathParser.createPathFromPathData("M0 -9 C0.9 -9 1.4 -7.8 1.4 -6.4 L1.4 -2.2 L8.6 2.2 L8.6 4 L1.4 1.9 L1.4 6 L3.6 7.8 L3.6 9 L0 8 L-3.6 9 L-3.6 7.8 L-1.4 6 L-1.4 1.9 L-8.6 4 L-8.6 2.2 L-1.4 -2.2 L-1.4 -6.4 C-1.4 -7.8 -0.9 -9 0 -9 Z"))
    private data class Arc(val path: Path, val measure: PathMeasure, val a: PointF, val b: PointF, val control: PointF)
    private fun project(code: String): PointF {
        val (lon, lat) = theme.AIRPORTS.getValue(code)
        return PointF(((lon + 170) / 360 * UI.CARD_W).toFloat(), (Page.MAP_Y + (78 - lat) * 1.42).toFloat())
    }
    private val arcs = theme.ROUTES.map { (from, to, bow) ->
        val a = project(from); val b = project(to)
        val length = hypot((b.x - a.x).toDouble(), (b.y - a.y).toDouble())
        val control = PointF((a.x + b.x) / 2, ((a.y + b.y) / 2 - length * bow).toFloat())
        val path = Path().apply { moveTo(a.x, a.y); quadTo(control.x, control.y, b.x, b.y) }
        Arc(path, PathMeasure(path, false), a, b, control)
    }
    private val guilloche = Path().apply {
        repeat(22) { i ->
            val y0 = 20 + i * 19
            moveTo(0f, y0.toFloat())
            for (x in 0..341 step 4) lineTo(x.toFloat(), (y0 + 5 * sin(x / 17.0 + i * 0.7) + 3 * sin(x / 7.3 - i)).toFloat())
        }
    }
    private val rosette = Path().apply {
        for (k in 0..720) {
            val theta = k / 720.0 * PI * 2 * 13
            val x = (21 * cos(theta) + 22 * cos(21.0 / 13 * theta)).toFloat()
            val y = (21 * sin(theta) - 22 * sin(21.0 / 13 * theta)).toFloat()
            if (k == 0) moveTo(x, y) else lineTo(x, y)
        }
    }
    private lateinit var canvas: Canvas
    private var blurIndex = 0
    private val blurNodes = mutableListOf<RenderNode>()
    private val softMasks = LinkedHashMap<String, Bitmap>(32, 0.75f, true)
    private fun cacheSoftMask(key: String, bitmap: Bitmap) {
        val limit = 16L * 1024 * 1024
        if (bitmap.allocationByteCount > limit) return
        softMasks[key] = bitmap
        while (softMasks.values.sumOf { it.allocationByteCount.toLong() } > limit) {
            softMasks.remove(softMasks.keys.first())
        }
    }
    private fun color(rgb: Int, alpha: Double = 1.0) = (alpha.coerceIn(0.0, 1.0).times(255).roundToInt() shl 24) or rgb
    private fun paint(rgb: Int = 0xffffff, alpha: Double = 1.0) = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { color = color(rgb, alpha) }
    private fun stroke(rgb: Int, alpha: Double = 1.0, width: Double = 1.0) = paint(rgb, alpha).apply {
        style = Paint.Style.STROKE; strokeWidth = width.toFloat(); strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private fun rect(x: Number, y: Number, w: Number, h: Number) = RectF(x.toFloat(), y.toFloat(), (x.toDouble() + w.toDouble()).toFloat(), (y.toDouble() + h.toDouble()).toFloat())
    private fun rr(x: Number, y: Number, w: Number, h: Number, radius: Number, p: Paint) = canvas.drawRoundRect(rect(x, y, w, h), radius.toFloat(), radius.toFloat(), p)
    private fun circle(x: Number, y: Number, radius: Number, p: Paint) = canvas.drawCircle(x.toFloat(), y.toFloat(), radius.toFloat().coerceAtLeast(0f), p)
    private fun line(x1: Number, y1: Number, x2: Number, y2: Number, p: Paint) = canvas.drawLine(x1.toFloat(), y1.toFloat(), x2.toFloat(), y2.toFloat(), p)
    private fun translate(x: Number, y: Number) = canvas.translate(x.toFloat(), y.toFloat())
    private fun scale(x: Number, y: Number = x) = canvas.scale(x.toFloat(), y.toFloat())
    private inline fun saved(block: () -> Unit) { canvas.save(); try { block() } finally { canvas.restore() } }
    private inline fun layer(p: Paint? = null, block: () -> Unit) { canvas.saveLayer(null, p); try { block() } finally { canvas.restore() } }
    private fun radial(x: Double, y: Double, radius: Double, colors: IntArray, stops: FloatArray? = null) = paint().apply {
        shader = RadialGradient(x.toFloat(), y.toFloat(), max(0.01, radius).toFloat(), colors, stops, Shader.TileMode.CLAMP)
    }
    private fun linear(x1: Number, y1: Number, x2: Number, y2: Number, colors: IntArray, stops: FloatArray? = null) = paint().apply {
        shader = LinearGradient(x1.toFloat(), y1.toFloat(), x2.toFloat(), y2.toFloat(), colors, stops, Shader.TileMode.CLAMP)
    }
    /** RenderEffect preserves Gaussian sigma on HW Canvas; old devices use a software alpha mask. */
    private fun blurred(sigma: Float, bounds: RectF, key: String, p: Paint, draw: (Paint) -> Unit) {
        if (Build.VERSION.SDK_INT >= 31 && canvas.isHardwareAccelerated) {
            val index = blurIndex++
            val node = if (index < blurNodes.size) blurNodes[index] else RenderNode("Passport blur").also { blurNodes.add(it) }
            val pad = ceil(sigma * 3).toInt()
            val left = floor(bounds.left).toInt() - pad; val top = floor(bounds.top).toInt() - pad
            node.setPosition(left, top, ceil(bounds.right).toInt() + pad, ceil(bounds.bottom).toInt() + pad)
            node.setClipToBounds(false)
            node.setRenderEffect(RenderEffect.createBlurEffect(sigma, sigma, Shader.TileMode.DECAL))
            val parent = canvas
            canvas = node.beginRecording()
            canvas.translate(-left.toFloat(), -top.toFloat())
            try { draw(p) } finally { node.endRecording(); canvas = parent }
            canvas.drawRenderNode(node)
        } else {
            val pad = ceil(sigma * 3).toInt()
            val width = ceil(bounds.width()).toInt() + pad * 2; val height = ceil(bounds.height()).toInt() + pad * 2
            val cacheKey = "$key:$width:$height:$sigma:${p.color and 0xffffff}:${p.style}:${p.strokeWidth}"
            val bitmap = softMasks[cacheKey] ?: Bitmap.createBitmap(max(1, width), max(1, height), Bitmap.Config.ARGB_8888).also {
                val parent = canvas
                canvas = Canvas(it)
                canvas.translate(pad - bounds.left, pad - bounds.top)
                val blurredPaint = Paint(p).apply {
                    alpha = 255
                    maskFilter = BlurMaskFilter(max(0.001f, (sigma - 0.5f) / 0.57735f), BlurMaskFilter.Blur.NORMAL)
                }
                try { draw(blurredPaint) } finally { canvas = parent }
                // The leading ring changes radius every frame; retaining it wastes old-device RAM.
                if (!key.startsWith("edge:")) cacheSoftMask(cacheKey, it)
            }
            canvas.drawBitmap(bitmap, bounds.left - pad, bounds.top - pad, paint(0xffffff, p.alpha / 255.0))
        }
    }
    private fun text(value: String, x: Number, cy: Number, font: String, rgb: Int, alpha: Double = 1.0, anchor: Double = 0.0) {
        val p = textPaints.getValue(font)
        p.color = color(rgb, alpha)
        val width = if (anchor == 0.0) 0f else p.measureText(value)
        canvas.drawText(value, (x.toDouble() - width * anchor).toFloat(), (cy.toDouble() + p.textSize * 0.36).toFloat(), p)
    }
    private fun drawFlag(x: Double, y: Double, radius: Double, index: Int) {
        val (kind, colors) = theme.FLAGS[index]
        saved {
            canvas.clipPath(Path().apply { addCircle(x.toFloat(), y.toFloat(), radius.toFloat(), Path.Direction.CW) })
            if (kind == 'd') {
                circle(x, y, radius, paint(colors[0])); circle(x, y, radius * 0.45, paint(colors[1]))
            } else {
                colors.forEachIndexed { i, rgb ->
                    if (kind == 'h') canvas.drawRect(rect(x - radius, y - radius + 2 * radius * i / colors.size, 2 * radius, 2 * radius / colors.size + 0.2), paint(rgb))
                    else canvas.drawRect(rect(x - radius + 2 * radius * i / colors.size, y - radius, 2 * radius / colors.size + 0.2, 2 * radius), paint(rgb))
                }
            }
        }
        circle(x, y, radius, stroke(0x000000, 0.15, 0.6))
    }
    private fun pop(u: Double) = if (u <= 0) 0.0 else if (u >= 1) 1.0 else 1 + 0.6 * sin(PI * u) * (1 - u)

    private fun drawPage(f: Frame, pal: Palette, uv: Boolean, driver: BlacklightFlightPassportDriver) {
        val width = UI.CARD_W
        val tiny = paint(pal.ink, 0.28)
        repeat(11) { i ->
            val x = 14 + i * 31
            if (i == 2 || i == 9) text("SFO", x - 6, Page.PLANES_Y, "tiny", pal.ink, 0.28)
            else saved { translate(x, Page.PLANES_Y); canvas.rotate(90f); scale(0.42); canvas.drawPath(plane, tiny) }
        }
        val mapPaint = paint().apply { colorFilter = PorterDuffColorFilter(color(pal.map), PorterDuff.Mode.SRC_IN) }
        canvas.drawBitmap(map, null, rect(0, Page.MAP_Y, width, Page.MAP_H), mapPaint)
        if (uv) {
            val security = stroke(theme.GLOW_LINE, 0.42 * (0.4 + 0.6 * f.glow), 0.7)
            canvas.drawPath(guilloche, security)
            saved {
                translate(width - 64, 318); canvas.rotate((360 * driver.amb / LOOP / 6).toFloat())
                security.color = color(theme.STAMP, 0.55 * f.glow)
                canvas.drawPath(rosette, security)
            }
            saved {
                translate(width * 0.74, 296); canvas.rotate(-24f); scale(4.6)
                blurred(0.6f, rect(-9, -9, 18, 18), "watermark", paint(theme.GLOW_LINE, 0.34 * f.glow)) { canvas.drawPath(plane, it) }
            }
        }
        val routePaint = stroke(pal.arc, f.fade, if (uv) 1.5 else 1.25)
        for (i in arcs.indices) {
            val progress = f.routes[i]
            if (progress <= 0.001) continue
            val arc = arcs[i]
            if (progress < 0.999) {
                val trimmed = Path()
                arc.measure.getSegment(0f, (arc.measure.length * progress).toFloat(), trimmed, true)
                canvas.drawPath(trimmed, routePaint)
                // RN uses quadratic parameter p for the tip, and length fraction p for path trim.
                val q = 1 - progress
                val x = q * q * arc.a.x + 2 * q * progress * arc.control.x + progress * progress * arc.b.x
                val y = q * q * arc.a.y + 2 * q * progress * arc.control.y + progress * progress * arc.b.y
                val dx = 2 * q * (arc.control.x - arc.a.x) + 2 * progress * (arc.b.x - arc.control.x)
                val dy = 2 * q * (arc.control.y - arc.a.y) + 2 * progress * (arc.b.y - arc.control.y)
                saved {
                    translate(x, y); canvas.rotate((atan2(dy, dx) * 180 / PI + 90).toFloat()); scale(0.62)
                    canvas.drawPath(plane, paint(if (uv) 0xffffff else pal.ink))
                }
            } else canvas.drawPath(arc.path, routePaint)
        }
        val airportPaint = paint(pal.airport, f.fade)
        for (i in arcs.indices) {
            val progress = f.routes[i]
            if (progress <= 0.001) continue
            val arc = arcs[i]
            circle(arc.a.x, arc.a.y, 2.1 * pop(min(1.0, progress * 6)), airportPaint)
            if (progress > 0.92) circle(arc.b.x, arc.b.y, 2.1 * pop((progress - 0.92) / 0.08), airportPaint)
        }
        theme.FLAGS.indices.forEach { drawFlag(width / 2 - 6 * 11.6 + it * 11.6, Page.FLAGS_Y, 5.4, it) }
        line(0, Page.RULE_Y, width, Page.RULE_Y, stroke(pal.ink, 0.15, 0.8))
        text(Copy.BRAND, Page.BRAND_X, Page.BRAND_Y, "brand", pal.ink)
        canvas.drawRect(rect(Page.KINDS_X - 12, Page.KINDS_Y - 3, 9, 6), paint(pal.ink, 0.75))
        text(Copy.KINDS, Page.KINDS_X, Page.KINDS_Y, "kinds", pal.ink)
        rr(Page.LOGO_X, Page.LOGO_Y - Page.LOGO_SIZE / 2, Page.LOGO_SIZE, Page.LOGO_SIZE, 5, paint(0xffffff, if (uv) 0.5 else 1.0))
        saved {
            translate(Page.LOGO_X + Page.LOGO_SIZE / 2, Page.LOGO_Y); canvas.rotate(45f); scale(0.8)
            canvas.drawPath(plane, paint(if (uv) pal.ink else 0xc9a8f2))
        }
        val count = f.count
        text((27 * count).roundToInt().toString(), Page.BIG_X, Page.BIG_Y, "big", pal.text)
        text(Copy.FLIGHTS, Page.WORD_X, Page.WORD_Y, "word", pal.inkSoft)
        Copy.ROWS.forEachIndexed { i, (label, value) ->
            text(label, Page.ROW_X, Page.ROW_Y[i], "row", pal.inkSoft)
            val labelWidth = textPaints.getValue("row").measureText("$label ")
            text(value, Page.ROW_X + labelWidth, Page.ROW_Y[i], "rowB", pal.grey)
        }
        val distance = (128714 * count).roundToInt().toString().reversed().chunked(3).joinToString(",").reversed()
        val values = listOf("$distance km", "${(7 * count).roundToInt()}d ${count.roundToInt()}h", (21 * count).roundToInt().toString(), (14 * count).roundToInt().toString())
        repeat(4) { i ->
            text(Copy.STATS[i], Page.STAT_X[i], Page.STAT_LABEL_Y, "statL", pal.grey)
            text(values[i], Page.STAT_X[i], Page.STAT_VALUE_Y, "statV", pal.text)
        }
        repeat(2) { text(Copy.MRZ[it], Page.MRZ_X, Page.MRZ_Y[it], "mrz", pal.ink, 0.85) }
        if (uv) saved {
            translate(width - 104, 402); canvas.rotate(-8f)
            rr(-70, -13, 140, 26, 6, stroke(theme.STAMP, 0.75 * f.glow, 1.6))
            text(Copy.STAMP, 0, 0, "stamp", theme.STAMP, 0.85 * f.glow, 0.5)
        }
    }

    private fun drawWorld(f: Frame, pal: Palette, uv: Boolean, driver: BlacklightFlightPassportDriver, dy: Double, scale: Double) {
        val backdrop = rect(0, -dy / scale - 40, theme.REF_W, 1000)
        if (uv) {
            canvas.drawRect(backdrop, linear(0, 0, 0, 874, intArrayOf(color(0x141052), color(pal.bg), color(0x05061a)), floatArrayOf(0f, 0.45f, 1f)))
            circle(201, -20, 260, radial(201.0, -20.0, 260.0, intArrayOf(color(theme.BG_GLOW, 0.85), color(theme.BG_GLOW, 0.0))))
        } else canvas.drawRect(backdrop, paint(pal.bg))
        for (x in doubleArrayOf(UI.CLOSE_X, UI.LAYOUT_X)) {
            if (!uv) blurred(6f, rect(x - 23, 68, 46, 46), "chrome", paint(0x000000, 0.07)) { circle(x, 91, 23, it) }
            circle(x, UI.CHROME_Y, UI.CHROME_R, paint(pal.chrome, if (uv) 0.12 else 1.0))
        }
        val icon = stroke(pal.chromeInk, width = 2.0)
        line(33, 82, 47, 96, icon); line(47, 82, 33, 96, icon)
        icon.strokeWidth = 1.6f
        rr(352, 81, 20, 16, 3, icon); line(352, 86, 372, 86, icon); line(364, 86, 364, 97, icon)
        text(Copy.TITLE, 201, UI.TITLE_Y, "title", pal.header, anchor = 0.5)
        text(Copy.SUB, 201, UI.SUB_Y, "sub", pal.headerSub, anchor = 0.5)
        if (!uv) {
            blurred(16f, rect(UI.CARD_X + 6, UI.CARD_Y + 14, UI.CARD_W - 12, UI.CARD_H), "cardShadow", paint(0x0d1433, 0.16)) {
                rr(UI.CARD_X + 6, UI.CARD_Y + 14, UI.CARD_W - 12, UI.CARD_H, UI.CARD_R, it)
            }
        } else blurred(22f, rect(UI.CARD_X, UI.CARD_Y, UI.CARD_W, UI.CARD_H), "cardGlow", paint(0x7b6cff, 0.35 * (0.5 + 0.5 * f.glow))) {
            rr(UI.CARD_X, UI.CARD_Y, UI.CARD_W, UI.CARD_H, UI.CARD_R, it)
        }
        rr(UI.CARD_X, UI.CARD_Y, UI.CARD_W, UI.CARD_H, UI.CARD_R, paint(pal.cover))
        val pageRect = rect(UI.CARD_X, UI.CARD_Y + UI.BAND, UI.CARD_W, UI.CARD_H - UI.BAND - UI.BAND_BOTTOM)
        val pagePaint = linear(UI.CARD_X, UI.CARD_Y, UI.CARD_X + UI.CARD_W, UI.CARD_Y + UI.CARD_H, intArrayOf(color(pal.page), color(pal.page2)))
        canvas.drawRoundRect(pageRect, 4f, 4f, pagePaint)
        saved {
            canvas.clipPath(Path().apply { addRoundRect(pageRect, 4f, 4f, Path.Direction.CW) })
            translate(UI.CARD_X, UI.CARD_Y)
            drawPage(f, pal, uv, driver)
        }
        saved {
            translate(UI.BUTTON_X + UI.BUTTON_W / 2, UI.BUTTON_Y + UI.BUTTON_H / 2)
            val pressScale = 1 - 0.04 * max(f.press, driver.pressP)
            scale(pressScale)
            if (uv) blurred(10f, rect(-51, -15.5, 102, 31), "buttonGlow", paint(0xb38cff, 0.55 + 0.25 * sin(2 * PI * 2 * driver.amb / LOOP))) {
                rr(-51, -15.5, 102, 31, 15.5, it)
            }
            rr(-51, -15.5, 102, 31, 15.5, paint(pal.button))
            rr(-51, -15.5, 102, 31, 15.5, if (uv) stroke(0xd9c2ff, width = 1.6) else stroke(0x000000, 0.1))
            rr(-35, -6.5, 5, 13, 1.5, paint(pal.buttonInk))
            rr(-36.4, -8, 7.8, 4, 1, paint(pal.buttonInk))
            text(Copy.BUTTON, -21, 0, "button", pal.buttonInk)
        }
        repeat(3) { i ->
            val x = UI.SHARE_X[i]; val y = UI.SHARE_Y
            val rgb = if (uv) 0xffffff else 0x111111
            val innerRgb = if (uv) 0x141052 else 0xffffff
            val inner = paint(innerRgb)
            rr(x - 13, y - 13, 26, 26, 7, paint(rgb))
            when (i) {
                0 -> { canvas.drawOval(rect(x - 8, y - 6.5, 16, 12), inner); circle(x - 5, y + 6, 2, inner) }
                1 -> repeat(8) { k ->
                    val angle = k / 8.0 * PI * 2
                    circle(x + cos(angle) * 3.6, y + sin(angle) * 3.6, 3.6, stroke(innerRgb, width = 1.1))
                }
                else -> for (k in -1..1) circle(x + k * 5.5, y, 1.9, inner)
            }
            text(Copy.SHARE[i], x, UI.SHARE_LABEL_Y, "share", rgb, anchor = 0.5)
        }
    }

    private fun scene(f: Frame, driver: BlacklightFlightPassportDriver, dy: Double, scale: Double) {
        drawWorld(f, theme.LIGHT, false, driver, dy, scale)
        val sheenA = 1 - BlacklightFlightPassportMotion.smooth(f.uv * 3)
        if (sheenA > 0.01) saved {
            val u = 0.5 + 0.42 * driver.tiltX + 0.18 * sin(2 * PI * driver.amb / BlacklightFlightPassportMotion.LOOP) + 0.1 * driver.tiltY
            val cx = UI.CARD_X + UI.CARD_W * u
            val card = rect(UI.CARD_X, UI.CARD_Y, UI.CARD_W, UI.CARD_H)
            canvas.clipPath(Path().apply { addRoundRect(card, UI.CARD_R.toFloat(), UI.CARD_R.toFloat(), Path.Direction.CW) })
            val sheen = linear(cx - 90, UI.CARD_Y, cx + 90, UI.CARD_Y + UI.CARD_H * 0.5,
                intArrayOf(color(0xFFFFFF, 0.0), color(0xC9F2FF, 0.1 * sheenA), color(0xFFE7FB, 0.16 * sheenA), color(0xE9FFD9, 0.1 * sheenA), color(0xFFFFFF, 0.0)),
                floatArrayOf(0f, 0.35f, 0.5f, 0.65f, 1f)).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD) }
            canvas.drawRect(card, sheen)
        }
        val reach = f.uv; val torch = driver.torch
        if (reach <= 0.001 && torch <= 0.01) return
        val bx = UI.BUTTON_X + UI.BUTTON_W / 2; val by = UI.BUTTON_Y + UI.BUTTON_H / 2
        layer {
            drawWorld(f.copy(glow = max(f.glow, torch)), theme.UV, true, driver, dy, scale)
            // Composite both circles into one mask before DstIn, preserving their union.
            layer(paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }) {
                if (reach > 0.001) {
                    val radius = 20 + reach * 1080
                    circle(bx, by, radius, radial(bx, by, radius, intArrayOf(color(0), color(0), color(0, 0.0)), floatArrayOf(0f, max(0.0, (radius - 70) / radius).toFloat(), 1f)))
                }
                if (torch > 0.01) {
                    val radius = 84 * smooth(torch)
                    circle(driver.tx, driver.ty, radius, radial(driver.tx, driver.ty, radius, intArrayOf(color(0), color(0, 0.9), color(0, 0.0)), floatArrayOf(0f, 0.55f, 1f)))
                }
            }
        }
        if (reach > 0.001 && reach < 0.999) {
            val radius = 20 + reach * 1080
            val edge = stroke(0x8e6bff, 0.35 * sin(PI * reach), 26.0)
            val edgeRadius = radius - 40
            if (edgeRadius > 0) blurred(18f, rect(bx - edgeRadius - 13, by - edgeRadius - 13, 2 * edgeRadius + 26, 2 * edgeRadius + 26), "edge:$edgeRadius", edge) {
                circle(bx, by, edgeRadius, it)
            }
        }
    }

    fun drawScene(target: Canvas, width: Float, height: Float, f: Frame, driver: BlacklightFlightPassportDriver, reducedFade: Double? = null) {
        canvas = target
        blurIndex = 0
        val scale = width / theme.REF_W
        val dy = (height - theme.REF_H * scale) / 2
        saved {
            translate(0, dy); scale(scale)
            if (reducedFade == null) scene(f, driver, dy, scale)
            else {
                val still = BlacklightFlightPassportDriver()
                scene(BlacklightFlightPassportMotion.frameAt(0.0), still, dy, scale)
                if (reducedFade > 0.001) layer(paint(0xffffff, reducedFade)) {
                    scene(BlacklightFlightPassportMotion.frameAt(BlacklightFlightPassportMotion.UV_PEAK), still, dy, scale)
                }
            }
        }
    }
}
