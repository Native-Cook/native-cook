package com.ngocdevv.nativecook.blocks.lists.pickleflavourwipe

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.tan

class PickleFlavourWipeResources(
    val foods: List<Bitmap?>,
    val jars: List<Bitmap>,
    val pattern: Bitmap,
    val black: Typeface,
    val bold: Typeface,
    val semibold: Typeface,
    val medium: Typeface,
)

/** Pages, layout and drawing of Pickle Flavour Wipe, ported from PickleFlavourWipeArt.swift. */
object PickleFlavourWipeArt {
    private val m = PickleFlavourWipeMotion

    const val WIDTH = 393.0
    const val CREAM = 0xFFF4E0
    const val GOLD = 0xFFC93C
    const val LEAF_LIGHT = 0x6BBF4A
    const val LEAF_DARK = 0x2E7A2C
    const val WHITE = 0xFFFFFF
    const val BLACK = 0x000000

    fun argb(hex: Int, alpha: Double = 1.0): Int =
        ((alpha.coerceIn(0.0, 1.0) * 255 + 0.5).toInt() shl 24) or (hex and 0xFFFFFF)

    // Pages

    const val CARROT = 0
    const val LIME = 1
    const val GARLIC = 2

    /** An ingredient around the jar: offset from the jar's centre (pt), drawn width, turn (degrees). */
    class Item(val food: Int, val x: Double, val y: Double, val w: Double, val turn: Double, val flip: Boolean = false)

    /** A small leaf drawn in code, placed like an item. */
    class Leaf(val x: Double, val y: Double, val len: Double, val turn: Double)

    class Page(
        val title: String, val subtitle: String, val line: String, val price: String, val weight: String,
        val cta: String, val top: Int, val mid: Int, val low: Int, val cut: Int,
        val jar: Int?, // index into the jar images; null for the line-up page
        val pickle: String, // the label's last line
        val items: List<Item>, val leaves: List<Leaf>,
    )

    val pages = listOf(
        Page(
            "Mango", "Mixed Pickle", "Green mango, carrot & lime in mustard oil", "$6.90", "350 g jar", "Add to basket",
            0x8A3BC4, 0x6E22A8, 0x4E1588, 0xD2A6F5, 0, "MIXED PICKLE",
            listOf(Item(CARROT, -118.0, 40.0, 172.0, -18.0), Item(LIME, 124.0, 72.0, 146.0, 8.0)),
            listOf(Leaf(-150.0, -46.0, 34.0, -38.0), Leaf(146.0, -30.0, 30.0, 42.0), Leaf(-46.0, 150.0, 28.0, 196.0)),
        ),
        Page(
            "Lime", "Chilli Pickle", "Sun-cured lime & green chilli, nice and sharp", "$6.50", "350 g jar", "Add to basket",
            0xEA3B4C, 0xD21F35, 0xA3112B, 0xFFB0B8, 1, "CHILLI PICKLE",
            listOf(Item(LIME, -122.0, 66.0, 154.0, -10.0), Item(CARROT, 120.0, 38.0, 164.0, 18.0, flip = true)),
            listOf(Leaf(-146.0, -40.0, 32.0, -30.0), Leaf(150.0, -44.0, 34.0, 50.0), Leaf(58.0, 152.0, 26.0, 160.0)),
        ),
        Page(
            "Garlic", "Whole Clove Pickle", "Slow-pickled cloves with red chilli & fennel", "$7.20", "350 g jar", "Add to basket",
            0x5DAAE8, 0x3D8FD6, 0x2869B3, 0xBFE2FF, 2, "GARLIC PICKLE",
            listOf(Item(GARLIC, -120.0, 68.0, 156.0, -8.0), Item(GARLIC, 128.0, 88.0, 108.0, 20.0, flip = true)),
            listOf(Leaf(-148.0, -36.0, 30.0, -42.0), Leaf(144.0, -26.0, 32.0, 36.0), Leaf(-60.0, 152.0, 26.0, 200.0)),
        ),
        Page(
            "Trio", "Tasting Box", "Order your favourites now!", "$18.50", "3 × 350 g", "Add the box",
            0x35A36B, 0x1F8050, 0x125F3A, 0xA5EBC4, null, "",
            listOf(Item(CARROT, -112.0, 156.0, 150.0, -22.0), Item(LIME, 18.0, 178.0, 100.0, 12.0), Item(GARLIC, 132.0, 156.0, 100.0, -10.0)),
            listOf(Leaf(-156.0, -120.0, 30.0, -30.0), Leaf(158.0, -108.0, 28.0, 60.0), Leaf(-40.0, 96.0, 24.0, 170.0)),
        ),
    )

    // Layout

    class Layout(val height: Double, val top: Double, val bottom: Double) {
        val barY = top + 26
        val titleY = top + 150
        val jarY = top + 366
        val ctaTop = height - bottom - 16 - 56
        val lineY = ctaTop - 64
        val dotsY = ctaTop - 30
    }

    const val JAR_H = 272.0
    const val TRIO_H = 140.0
    const val TRIO_GAP = 118.0
    private const val PATTERN_W = 440.0
    private const val PATTERN_H = 1000.0

    // Artwork

    private fun loadBitmap(context: Context, name: String): Bitmap? = runCatching {
        context.assets.open("pickleflavourwipe/pickleflavourwipe_$name.webp").use { stream ->
            BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply {
                inScaled = false
                inPreferredConfig = Bitmap.Config.ARGB_8888
            })?.also { it.prepareToDraw() }
        }
    }.getOrNull()

    private fun weight(w: Int, fallbackBold: Boolean): Typeface =
        if (Build.VERSION.SDK_INT >= 28) Typeface.create(Typeface.DEFAULT, w, false)
        else if (fallbackBold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT

    /** Where each jar's blank label band sits, as a share of the jar's height (centre). */
    private val labelCentre = doubleArrayOf(0.597, 0.558, 0.625)

    fun load(context: Context): PickleFlavourWipeResources {
        val black = weight(900, true)
        val bold = weight(700, true)
        val names = listOf("jar-mango", "jar-lime", "jar-garlic")
        val jars = names.mapIndexed { i, name -> labelledJar(loadBitmap(context, name), i, black, bold) }
        return PickleFlavourWipeResources(
            foods = listOf(loadBitmap(context, "carrot"), loadBitmap(context, "lime"), loadBitmap(context, "garlic")),
            jars = jars,
            pattern = pattern(),
            black = black,
            bold = bold,
            semibold = weight(600, true),
            medium = weight(500, false),
        )
    }

    /** Each jar with its label printed once (3×), so a frame draws one bitmap. */
    private fun labelledJar(jar: Bitmap?, index: Int, black: Typeface, bold: Typeface): Bitmap {
        val h = JAR_H
        val w = if (jar != null) h * jar.width / jar.height.toDouble() else h * 0.92
        val out = Bitmap.createBitmap((w * 3).toInt(), (h * 3).toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.scale(3f, 3f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        if (jar != null) c.drawBitmap(jar, null, RectF(0f, 0f, w.toFloat(), h.toFloat()), paint)
        val page = pages[index]
        val cx = w / 2
        val cy = h * labelCentre[index]
        drawLine(c, "jarful", cx, cy - 23, 13.0, black, argb(CREAM), 0.0, Paint.Align.CENTER)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 0.8f
        paint.color = argb(GOLD, 0.9)
        c.drawLine((cx - 16).toFloat(), (cy - 12).toFloat(), (cx + 16).toFloat(), (cy - 12).toFloat(), paint)
        drawLine(c, page.title, cx, cy + 5, 23.0, Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC), argb(CREAM), 0.0, Paint.Align.CENTER)
        drawLine(c, page.pickle, cx, cy + 25, 6.5, bold, argb(GOLD), 1.6, Paint.Align.CENTER)
        out.prepareToDraw()
        return out
    }

    /** The backdrop's tonal marks (leaves and chilli curves on a jittered grid), 440 × 1000 pt, 2×. */
    private fun pattern(): Bitmap {
        val out = Bitmap.createBitmap((PATTERN_W * 2).toInt(), (PATTERN_H * 2).toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.scale(2f, 2f)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 8f
            strokeCap = Paint.Cap.ROUND
        }
        val leaf = Path().apply {
            moveTo(-15f, 0f)
            quadTo(0f, -13f, 15f, 0f)
            quadTo(0f, 13f, -15f, 0f)
            close()
        }
        val arc = RectF(-14f, -14f, 14f, 14f)
        val step = 58.0
        for (row in 0 until 18) {
            for (col in 0 until 8) {
                val seed = (row * 31 + col * 7) + 0.5
                val x = col * step + (if (row % 2 == 0) 0.0 else step / 2) + (m.hash(seed) - 0.5) * 22
                val y = row * step + (m.hash(seed + 1.3) - 0.5) * 22
                val turn = m.hash(seed + 2.1) * 360
                val light = m.hash(seed + 3.7) > 0.62
                val ink = if (light) argb(WHITE, 0.07) else argb(BLACK, 0.085)
                c.save()
                c.translate(x.toFloat(), y.toFloat())
                c.rotate(turn.toFloat())
                if (m.hash(seed + 5.9) > 0.5) {
                    fill.color = ink
                    c.drawPath(leaf, fill)
                } else {
                    // A chilli curve: a thick arc with round ends (−2.2 → −0.5 rad).
                    stroke.color = ink
                    c.drawArc(arc, (-2.2 * 180 / PI).toFloat(), ((1.7) * 180 / PI).toFloat(), false, stroke)
                }
                c.restore()
            }
        }
        out.prepareToDraw()
        return out
    }

    // Paints (reused; made opaque before each shader)

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val rect = RectF()

    /** One line of text whose line box is centred on [cy]; [tracking] in pt. */
    fun drawLine(
        canvas: Canvas, text: String, x: Double, cy: Double, size: Double, typeface: Typeface,
        color: Int, tracking: Double = 0.0, align: Paint.Align = Paint.Align.LEFT,
    ) {
        val p = textPaint
        p.typeface = typeface
        p.textSize = size.toFloat()
        p.letterSpacing = (tracking / size).toFloat()
        p.color = color
        p.textAlign = align
        val fm = p.fontMetrics
        val baseline = cy - (fm.ascent + fm.descent) / 2
        canvas.drawText(text, x.toFloat(), baseline.toFloat(), p)
    }

    private fun measure(text: String, size: Double, typeface: Typeface): Double {
        textPaint.typeface = typeface
        textPaint.textSize = size.toFloat()
        textPaint.letterSpacing = 0f
        return textPaint.measureText(text).toDouble()
    }

    private fun shaderPaint(shader: Shader, alpha: Double = 1.0): Paint {
        paint.reset()
        paint.isAntiAlias = true
        paint.isFilterBitmap = true
        paint.style = Paint.Style.FILL
        paint.color = 0xFF000000.toInt()
        paint.alpha = (alpha.coerceIn(0.0, 1.0) * 255).toInt()
        paint.shader = shader
        return paint
    }

    private fun colorPaint(color: Int): Paint {
        paint.reset()
        paint.isAntiAlias = true
        paint.isFilterBitmap = true
        paint.style = Paint.Style.FILL
        paint.color = color
        return paint
    }

    // Scene

    /** The page [from], then page [to] revealed by the slash edge (or cross-faded under reduced motion). */
    fun drawScene(c: Canvas, l: Layout, res: PickleFlavourWipeResources, scene: PickleFlavourWipeMotion.Scene, a: Double) {
        val r = scene.reveal
        if (scene.fade >= 0) {
            drawPage(c, l, res, pages[scene.from], scene.fromAge, 0.0, 0.0, a, 1.0)
            drawPage(c, l, res, pages[scene.to], scene.toAge, 0.0, 0.0, a, scene.fade)
            return
        }
        if (scene.from == scene.to || r <= 0) {
            drawPage(c, l, res, pages[scene.from], scene.fromAge, 0.0, 0.0, a, 1.0)
            return
        }
        val dir = scene.dir
        val cr = min(max(r, 0.0), 1.2)
        drawPage(c, l, res, pages[scene.from], scene.fromAge, -36 * dir * cr, -14 * dir * cr, a, 1.0)

        val edge = Edge(l, r, dir)
        // The new page casts a soft shadow onto the old one.
        val nx = edge.nx * -dir
        val ny = edge.ny * -dir
        val shadow = Path().apply {
            moveTo(edge.topX.toFloat(), edge.topY.toFloat())
            lineTo(edge.bottomX.toFloat(), edge.bottomY.toFloat())
            lineTo((edge.bottomX + nx * 30).toFloat(), (edge.bottomY + ny * 30).toFloat())
            lineTo((edge.topX + nx * 30).toFloat(), (edge.topY + ny * 30).toFloat())
            close()
        }
        val midX = edge.midX
        val midY = edge.midY
        c.drawPath(shadow, shaderPaint(LinearGradient(
            midX.toFloat(), midY.toFloat(), (midX + nx * 30).toFloat(), (midY + ny * 30).toFloat(),
            argb(BLACK, 0.26), argb(BLACK, 0.0), Shader.TileMode.CLAMP,
        )))

        c.save()
        c.clipPath(edge.region)
        val rest = 1 - min(max(r, 0.0), 1.0)
        drawPage(c, l, res, pages[scene.to], scene.toAge, 24 * dir * rest, 14 * dir * rest, a, 1.0)
        // A sheen just inside the new page.
        c.drawPath(edge.region, shaderPaint(LinearGradient(
            midX.toFloat(), midY.toFloat(), (midX - nx * 46).toFloat(), (midY - ny * 46).toFloat(),
            argb(WHITE, 0.16), argb(WHITE, 0.0), Shader.TileMode.CLAMP,
        )))
        c.restore()

        // The cut line itself.
        val line = colorPaint(argb(pages[scene.to].cut))
        line.style = Paint.Style.STROKE
        line.strokeWidth = 3.2f
        c.drawLine(edge.topX.toFloat(), edge.topY.toFloat(), edge.bottomX.toFloat(), edge.bottomY.toFloat(), line)
        line.color = argb(WHITE, 0.75)
        line.strokeWidth = 1f
        c.drawLine(edge.topX.toFloat(), edge.topY.toFloat(), edge.bottomX.toFloat(), edge.bottomY.toFloat(), line)
    }

    /** The slash: a straight "/" edge at 64° that sweeps across the screen. */
    class Edge(l: Layout, r: Double, dir: Double) {
        val topX: Double
        val topY = -2.0
        val bottomX: Double
        val bottomY: Double
        val nx: Double // unit normal pointing right-down
        val ny: Double
        val region = Path()
        val midX get() = (topX + bottomX) / 2
        val midY get() = (topY + bottomY) / 2

        init {
            val w = WIDTH
            val h = l.height
            val s = h / 2 / tan(64 * PI / 180)
            val travel = w + 2 * s + 48
            val ex = if (dir > 0) (w + s + 24) - r * travel else (-s - 24) + r * travel
            topX = ex + s
            bottomX = ex - s
            bottomY = h + 2
            val len = hypot(2 * s, h)
            nx = h / len
            ny = 2 * s / len
            if (dir > 0) {
                val right = (max(w, topX) + 60).toFloat()
                region.moveTo(topX.toFloat(), topY.toFloat())
                region.lineTo(right, topY.toFloat())
                region.lineTo(right, bottomY.toFloat())
                region.lineTo(bottomX.toFloat(), bottomY.toFloat())
            } else {
                val left = (min(0.0, bottomX) - 60).toFloat()
                region.moveTo(left, topY.toFloat())
                region.lineTo(topX.toFloat(), topY.toFloat())
                region.lineTo(bottomX.toFloat(), bottomY.toFloat())
                region.lineTo(left, bottomY.toFloat())
            }
            region.close()
        }
    }

    // Page

    private fun drawPage(
        c: Canvas, l: Layout, res: PickleFlavourWipeResources, page: Page, age: Double,
        push: Double, bgPush: Double, a: Double, alpha: Double,
    ) {
        if (alpha <= 0.001) return
        val layered = alpha < 0.999
        if (layered) c.saveLayerAlpha(null, (alpha * 255).toInt())
        c.drawRect(-2f, -2f, (WIDTH + 2).toFloat(), (l.height + 2).toFloat(), shaderPaint(LinearGradient(
            0f, 0f, 0f, l.height.toFloat(),
            intArrayOf(argb(page.top), argb(page.mid), argb(page.low)), floatArrayOf(0f, 0.46f, 1f), Shader.TileMode.CLAMP,
        )))
        rect.set((-24 + bgPush).toFloat(), 0f, (-24 + bgPush + PATTERN_W).toFloat(), PATTERN_H.toFloat())
        c.drawBitmap(res.pattern, null, rect, colorPaint(argb(BLACK)))
        // A soft light behind the hero.
        val gx = (196 + push).toFloat()
        val gy = (l.jarY - 10).toFloat()
        c.drawCircle(gx, gy, 230f, shaderPaint(RadialGradient(gx, gy, 230f, argb(WHITE, 0.17), argb(WHITE, 0.0), Shader.TileMode.CLAMP)))

        c.save()
        c.translate(push.toFloat(), 0f)
        val jx = 196.0
        val jy = l.jarY
        val jar = page.jar
        if (jar != null) {
            // Items behind the jar, the jar's shadow, the jar, then the leaves in front.
            page.items.forEachIndexed { i, item -> drawItem(c, res, item, i, jx, jy, age, a) }
            val v = m.jarIn(age)
            val dy = 300 * (1 - v) + 3 * m.wave(a, 5.0, 0.4)
            val turn = -6 + 20 * (1 - v) + 0.8 * m.wave(a, 4.0, 1.1)
            val scale = 0.9 + 0.1 * v
            val image = res.jars[jar]
            val w = JAR_H * image.width / image.height
            drawShadow(c, jx + 10, jy + JAR_H * 0.43 + dy, 230.0, 66.0, 0.3 * m.clamp(v))
            c.save()
            c.translate(jx.toFloat(), (jy + dy).toFloat())
            c.rotate(turn.toFloat())
            c.scale(scale.toFloat(), scale.toFloat())
            rect.set((-w / 2).toFloat(), (-JAR_H / 2).toFloat(), (w / 2).toFloat(), (JAR_H / 2).toFloat())
            c.drawBitmap(image, null, rect, colorPaint(argb(BLACK)))
            c.restore()
        } else {
            page.items.forEachIndexed { i, item -> drawItem(c, res, item, i, jx, jy, age, a) }
            // The line-up: left, right, then the centre jar in front.
            for (j in intArrayOf(0, 2, 1)) {
                val v = m.trioJarIn(age, j)
                val dx = (j - 1) * TRIO_GAP
                val dy = 260 * (1 - v) + 2.5 * m.wave(a, 5.0, j * 0.9)
                val turn = (j - 1) * 4 + 16 * (1 - v)
                val image = res.jars[intArrayOf(2, 1, 0)[j]]
                val w = TRIO_H * image.width / image.height
                drawShadow(c, jx + dx + 6, jy - 14 + TRIO_H * 0.44 + dy, 140.0, 40.0, 0.28 * m.clamp(v))
                c.save()
                c.translate((jx + dx).toFloat(), (jy - 14 + dy).toFloat())
                c.rotate(turn.toFloat())
                rect.set((-w / 2).toFloat(), (-TRIO_H / 2).toFloat(), (w / 2).toFloat(), (TRIO_H / 2).toFloat())
                c.drawBitmap(image, null, rect, colorPaint(argb(BLACK)))
                c.restore()
            }
        }
        page.leaves.forEachIndexed { i, leaf -> drawLeaf(c, leaf, i, jx, jy, age, a) }

        drawTitle(c, l, res, page, age)
        drawCopy(c, l, res, page, age)
        c.restore()
        if (layered) c.restore()
    }

    private fun drawShadow(c: Canvas, x: Double, y: Double, w: Double, h: Double, alpha: Double) {
        if (alpha <= 0.005) return
        c.save()
        c.translate(x.toFloat(), y.toFloat())
        c.scale(1f, (h / w).toFloat())
        val r = (w / 2).toFloat()
        c.drawCircle(0f, 0f, r, shaderPaint(RadialGradient(0f, 0f, r, argb(BLACK, alpha), argb(BLACK, 0.0), Shader.TileMode.CLAMP)))
        c.restore()
    }

    private fun drawItem(c: Canvas, res: PickleFlavourWipeResources, item: Item, i: Int, jx: Double, jy: Double, age: Double, a: Double) {
        val v = m.ingredientIn(age, i)
        if (v <= 0.001) return
        val image = res.foods[item.food] ?: return
        val h = item.w * image.height / image.width
        val x = jx + item.x * v
        val y = jy + item.y * v + 5 * m.wave(a, 5.0, 1.3 + i * 2.1)
        val turn = item.turn - 30 * (1 - v) + 3 * m.wave(a, 4.0, i * 1.7)
        val scale = 0.2 + 0.8 * v
        c.save()
        c.translate(x.toFloat(), y.toFloat())
        c.rotate(turn.toFloat())
        c.scale((if (item.flip) -scale else scale).toFloat(), scale.toFloat())
        rect.set((-item.w / 2).toFloat(), (-h / 2).toFloat(), (item.w / 2).toFloat(), (h / 2).toFloat())
        val p = colorPaint(argb(BLACK))
        p.alpha = (m.clamp(v * 3) * 255).toInt()
        c.drawBitmap(image, null, rect, p)
        c.restore()
    }

    private val leafPath = Path()
    private val ribPath = Path()

    private fun drawLeaf(c: Canvas, leaf: Leaf, i: Int, jx: Double, jy: Double, age: Double, a: Double) {
        val v = m.ingredientIn(age, i + 2)
        if (v <= 0.001) return
        val alpha = m.clamp(v * 3)
        c.save()
        c.translate((jx + leaf.x * v).toFloat(), (jy + leaf.y * v + 6 * m.wave(a, 5.0, 2.4 + i * 1.9)).toFloat())
        c.rotate((leaf.turn + 40 * (1 - v) + 8 * m.wave(a, 3.0, i * 2.3)).toFloat())
        val s = (leaf.len * (0.3 + 0.7 * v)).toFloat()
        leafPath.reset()
        leafPath.moveTo(-s / 2, 0f)
        leafPath.quadTo(-s * 0.05f, -s * 0.46f, s / 2, 0f)
        leafPath.quadTo(s * 0.05f, s * 0.4f, -s / 2, 0f)
        leafPath.close()
        c.drawPath(leafPath, shaderPaint(LinearGradient(0f, -s * 0.25f, 0f, s * 0.2f, argb(LEAF_LIGHT), argb(LEAF_DARK), Shader.TileMode.CLAMP), alpha))
        ribPath.reset()
        ribPath.moveTo(-s * 0.45f, 0f)
        ribPath.quadTo(0f, -s * 0.08f, s * 0.42f, -s * 0.02f)
        val rib = colorPaint(argb(0x1F5A1E, 0.55 * alpha))
        rib.style = Paint.Style.STROKE
        rib.strokeWidth = 0.9f
        c.drawPath(ribPath, rib)
        c.restore()
    }

    // Type

    private const val TITLE_SIZE = 92.0

    private fun drawTitle(c: Canvas, l: Layout, res: PickleFlavourWipeResources, page: Page, age: Double) {
        val v = m.titleIn(age)
        val width = measure(page.title, TITLE_SIZE, res.black)
        val fit = min(1.0, 330 / max(width, 1.0))
        val speed = m.titleSpeed(age) * 300 // pt/s
        c.save()
        c.translate((196 - 300 * (1 - v)).toFloat(), l.titleY.toFloat())
        c.rotate(-7f)
        c.save()
        c.scale(fit.toFloat(), fit.toFloat())
        // A soft drop shadow, then the smear ghosts trailing behind, then the title.
        drawLine(c, page.title, 0.0, 4.0, TITLE_SIZE, res.black, argb(BLACK, 0.16 * m.clamp(v * 2)), 0.0, Paint.Align.CENTER)
        if (speed > 40) {
            val ghosts = doubleArrayOf(0.32, 0.2, 0.12, 0.06)
            for (k in 1..4) {
                drawLine(c, page.title, -speed * 0.011 * k / fit, 0.0, TITLE_SIZE, res.black,
                    argb(WHITE, ghosts[k - 1] * m.clamp(speed / 600)), 0.0, Paint.Align.CENTER)
            }
        }
        drawLine(c, page.title, 0.0, 0.0, TITLE_SIZE, res.black, argb(WHITE), 0.0, Paint.Align.CENTER)
        c.restore()

        val copy = m.copyIn(age)
        if (copy > 0.001) {
            drawLine(c, page.subtitle, width * fit / 2 - 4, 60 * fit + 8 * (1 - copy), 15.0, res.semibold,
                argb(CREAM, 0.92 * copy), 0.0, Paint.Align.RIGHT)
        }
        c.restore()
    }

    private fun drawCopy(c: Canvas, l: Layout, res: PickleFlavourWipeResources, page: Page, age: Double) {
        val copy = m.copyIn(age)
        if (copy > 0.001) {
            val y = l.lineY + 10 * (1 - copy)
            if (page.jar == null) {
                val parts = listOf("Order your " to argb(WHITE, copy), "favourites" to argb(GOLD, copy), " now!" to argb(WHITE, copy))
                val total = parts.sumOf { measure(it.first, 21.0, res.bold) }
                var x = 196 - total / 2
                for ((text, color) in parts) {
                    drawLine(c, text, x, y, 21.0, res.bold, color)
                    x += measure(text, 21.0, res.bold)
                }
            } else {
                drawLine(c, page.line, 196.0, y, 15.0, res.medium, argb(CREAM, 0.88 * copy), 0.0, Paint.Align.CENTER)
            }
        }
        drawLine(c, page.price, 24.0, l.ctaTop + 19, 26.0, res.bold, argb(WHITE))
        drawLine(c, page.weight, 24.0, l.ctaTop + 43, 12.0, res.medium, argb(CREAM, 0.72))
        rect.set(179f, (l.ctaTop + 3).toFloat(), 369f, (l.ctaTop + 59).toFloat())
        c.drawRoundRect(rect, 28f, 28f, colorPaint(argb(BLACK, 0.14)))
        rect.set(179f, l.ctaTop.toFloat(), 369f, (l.ctaTop + 56).toFloat())
        c.drawRoundRect(rect, 28f, 28f, colorPaint(argb(CREAM)))
        drawLine(c, page.cta, 274.0, l.ctaTop + 28, 16.0, res.semibold, argb(page.low), 0.0, Paint.Align.CENTER)
    }

    // Chrome (not wiped)

    private val bagPath = Path()

    fun drawChrome(c: Canvas, l: Layout, res: PickleFlavourWipeResources, scene: PickleFlavourWipeMotion.Scene) {
        val y = l.barY
        for (i in 0 until 3) {
            val w = if (i == 1) 12f else 18f
            val top = (y - 6.5 + i * 5.5).toFloat()
            rect.set(24f, top, 24f + w, top + 2.2f)
            c.drawRoundRect(rect, 1.1f, 1.1f, colorPaint(argb(WHITE)))
        }
        val markW = measure("jarful", 24.0, res.black)
        drawLine(c, "jarful", 196.0 - 5, y, 24.0, res.black, argb(WHITE), 0.0, Paint.Align.CENTER)
        c.drawCircle((196 - 5 + markW / 2 + 3 + 3.5).toFloat(), (y + 6.5).toFloat(), 3.5f, colorPaint(argb(GOLD)))

        val bx = 349f
        val by = y.toFloat()
        c.drawCircle(bx, by, 20f, colorPaint(argb(WHITE, 0.16)))
        bagPath.reset()
        rect.set(bx - 7.5f, by - 4f, bx + 7.5f, by + 8.5f)
        bagPath.addRoundRect(rect, 2.5f, 2.5f, Path.Direction.CW)
        bagPath.moveTo(bx - 4f, by - 4f)
        bagPath.cubicTo(bx - 4f, by - 10.5f, bx + 4f, by - 10.5f, bx + 4f, by - 4f)
        val bag = colorPaint(argb(WHITE))
        bag.style = Paint.Style.STROKE
        bag.strokeWidth = 1.7f
        bag.strokeCap = Paint.Cap.ROUND
        bag.strokeJoin = Paint.Join.ROUND
        c.drawPath(bagPath, bag)

        // Page dots: the active capsule slides with the edge.
        val r = if (scene.fade >= 0) scene.fade else m.clamp(scene.reveal)
        val gap = 16.0
        val wide = 22.0
        val total = (m.PAGES - 1) * gap + wide
        var x = 196 - total / 2
        for (i in 0 until m.PAGES) {
            val share = (if (i == scene.from) 1 - r else 0.0) + (if (i == scene.to) r else 0.0)
            val w = 8 + (wide - 8) * share
            rect.set(x.toFloat(), (l.dotsY - 4).toFloat(), (x + w).toFloat(), (l.dotsY + 4).toFloat())
            c.drawRoundRect(rect, 4f, 4f, colorPaint(argb(WHITE, 0.4 + 0.55 * share)))
            x += w + gap - 8
        }
    }
}
