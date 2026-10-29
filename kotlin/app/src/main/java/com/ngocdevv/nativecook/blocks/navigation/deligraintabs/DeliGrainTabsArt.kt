package com.ngocdevv.nativecook.blocks.navigation.deligraintabs

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Look of Deli Grain Tabs: the palette, the twelve silhouettes (paths in a 100-unit box, bottom on
 * y = 96, non-zero fill with reversed holes), the layout in points of a 393-wide screen, the grains
 * each silhouette is sampled into, and the drawing.
 */
object DeliGrainTabsArt {
    const val CREAM = 0xFFF5F1E1.toInt()
    const val INK = 0xFF16130F.toInt()
    const val IDLE = 0xFFDCD5C3.toInt()
    const val MUTED = 0xFF8C8473.toInt()

    fun mix(a: Int, b: Int, t: Double): Int {
        fun ch(v: Int, s: Int) = (v shr s) and 0xFF
        fun lerp(s: Int) = (ch(a, s) + (ch(b, s) - ch(a, s)) * t).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (lerp(16) shl 16) or (lerp(8) shl 8) or lerp(0)
    }

    class Category(val name: String, val count: Int, val path: String)

    /** Top row, then bottom row. */
    val categories = listOf(
        Category("Balsamico", 24, "M43 4 L57 4 L57 9 L59 9 Q60 9 60 10 L60 12 Q60 13 59 13 L56 13 L56 27 L71 38 Q73 39.5 73 42 L73 94 Q73 96 71 96 L29 96 Q27 96 27 94 L27 42 Q27 39.5 29 38 L44 27 L44 13 L41 13 Q40 13 40 12 L40 10 Q40 9 41 9 L43 9 L43 4 Z"),
        Category("Wine & Spirits", 112, "M29.5 6 L70.5 6 C77.6 18.3 80.7 34.6 74.5 49 C69.4 60.2 58.2 63.3 54.1 67.4 L54.1 83.7 C54.1 86.8 62.3 87.8 68.4 89.9 Q72.5 91.9 70.5 95 Q69.4 96 66.4 96 L33.6 96 Q30.6 96 29.5 95 Q27.5 91.9 31.6 89.9 C37.7 87.8 45.9 86.8 45.9 83.7 L45.9 67.4 C41.8 63.3 30.6 60.2 25.5 49 C19.3 34.6 22.4 18.3 29.5 6 Z"),
        Category("Flour & Pizza", 36, "M12.4 30.7 C32.1 16.4 67.9 16.4 87.6 30.7 Q90.2 33.4 88.4 36.1 L53.6 93.3 Q50 98.7 46.4 93.3 L36.6 77.2 C41.1 73.6 39.3 67.4 33.9 67.4 L28.5 58.4 C33 55.8 32.1 50.4 26.8 51.3 L11.6 36.1 Q9.8 33.4 12.4 30.7 Z M48.4 46.8 C48.4 43.8 45.9 41.3 42.8 41.3 C39.8 41.3 37.3 43.8 37.3 46.8 C37.3 49.9 39.8 52.4 42.8 52.4 C45.9 52.4 48.4 49.9 48.4 46.8 Z M67.3 51.3 C67.3 48.6 65.2 46.5 62.5 46.5 C59.9 46.5 57.7 48.6 57.7 51.3 C57.7 54 59.9 56.1 62.5 56.1 C65.2 56.1 67.3 54 67.3 51.3 Z M55.2 68.3 C55.2 65.4 52.9 63.1 50 63.1 C47.1 63.1 44.8 65.4 44.8 68.3 C44.8 71.1 47.1 73.5 50 73.5 C52.9 73.5 55.2 71.1 55.2 68.3 Z M56.3 26.3 C54.5 26.3 54.5 29.8 56.3 30.3 C65.2 31.2 73.2 33.4 80 36.1 C81.3 36.5 82.2 35.2 81.3 34.3 C75 29.8 66.1 27.2 56.3 26.3 Z"),
        Category("Sweets & Coffee", 58, "M8 50 L76 50 Q78 50 78 52 C78 68 72 80 60 88 L58 90 L62 90 Q64 90 64 92 L64 94 Q64 96 62 96 L24 96 Q22 96 22 94 L22 92 Q22 90 24 90 L28 90 L26 88 C14 80 8 68 8 52 Q8 50 10 50 Z M92 62 C92 68.6 86.6 74 80 74 C73.4 74 68 68.6 68 62 C68 55.4 73.4 50 80 50 C86.6 50 92 55.4 92 62 Z M85.6 62 C85.6 58.9 83.1 56.4 80 56.4 C76.9 56.4 74.4 58.9 74.4 62 C74.4 65.1 76.9 67.6 80 67.6 C83.1 67.6 85.6 65.1 85.6 62 Z"),
        Category("Olive Oil", 41, "M35 6 L59 6 Q61 6 61 8 L61 30 C71 34 77 46 77 58 L77 93 Q77 96 74 96 L24 96 Q21 96 21 93 L21 58 C21 46 27 34 37 30 L37 18 L35 6 Z M59 16 L65 16 C75 16 79 22 79 32 C79 42 75 48 67 52 L63 46 C69 42 71 38 71 32 C71 26 69 23 65 23 L59 23 L59 16 Z"),
        Category("Sauces & Pesto", 67, "M35.7 14 L64.3 14 Q66 14 66 15.8 L66 18.5 Q66 20.2 64.3 20.2 L35.7 20.2 Q34 20.2 34 18.5 L34 15.8 Q34 14 35.7 14 Z M30.4 31.8 Q30.4 22.9 39.3 22.9 L60.7 22.9 Q69.6 22.9 69.6 31.8 L69.6 49.7 L30.4 49.7 L30.4 31.8 Z M30.4 53.2 L69.6 53.2 L69.6 69.3 L30.4 69.3 L30.4 53.2 Z M30.4 72.8 L69.6 72.8 L69.6 87.1 Q69.6 96 60.7 96 L39.3 96 Q30.4 96 30.4 87.1 L30.4 72.8 Z"),
        Category("Pasta & Risotto", 83, "M50 58.3 C58 48.3 72.1 38.2 88.2 40.3 C83.2 46.3 92.2 50.3 88.2 57.3 C84.2 63.4 94.2 68.4 89.2 75.4 C85.2 81.4 93.2 86.5 87.2 95.5 C72.1 98.5 58 88.5 50 78.4 C42 88.5 27.9 98.5 12.8 95.5 C6.8 86.5 14.8 81.4 10.8 75.4 C5.8 68.4 15.8 63.4 11.8 57.3 C7.8 50.3 16.8 46.3 11.8 40.3 C27.9 38.2 42 48.3 50 58.3 Z M49 54.3 L51 54.3 Q57 54.3 57 60.4 L57 76.4 Q57 82.5 51 82.5 L49 82.5 Q43 82.5 43 76.4 L43 60.4 Q43 54.3 49 54.3 Z"),
        Category("Specialty Cheeses", 49, "M10.4 69 L66.2 31.2 Q70.7 28.5 74.3 32.1 L88.7 51 Q89.6 52.8 89.6 55.5 L89.6 93.3 Q89.6 96 86.9 96 L13.1 96 Q10.4 96 10.4 93.3 L10.4 69 Z M17.6 70.8 L21.2 73.5 L86 61.8 L86 57.3 L17.6 70.8 Z"),
        Category("Salami & Ham", 31, "M45.8 29.3 L54.2 29.3 C55 40.9 58.3 49.3 63.3 59.3 C70 71 73.4 82.7 69.2 90.2 Q65 96 50 96 Q35 96 30.8 90.2 C26.6 82.7 30 71 36.7 59.3 C41.7 49.3 45 40.9 45.8 29.3 Z M55 25.9 C55 28.7 52.8 30.9 50 30.9 C47.2 30.9 45 28.7 45 25.9 C45 23.2 47.2 20.9 50 20.9 C52.8 20.9 55 23.2 55 25.9 Z M47.9 21.3 C47.9 23.2 46.4 24.7 44.6 24.7 C42.7 24.7 41.2 23.2 41.2 21.3 C41.2 19.5 42.7 18 44.6 18 C46.4 18 47.9 19.5 47.9 21.3 Z M58.8 21.3 C58.8 23.2 57.3 24.7 55.4 24.7 C53.6 24.7 52.1 23.2 52.1 21.3 C52.1 19.5 53.6 18 55.4 18 C57.3 18 58.8 19.5 58.8 21.3 Z"),
        Category("Gifts", 22, "M16 30.2 L84 30.2 Q86.1 30.2 86.1 32.3 L86.1 41.9 Q86.1 44 84 44 L16 44 Q13.9 44 13.9 41.9 L13.9 32.3 Q13.9 30.2 16 30.2 Z M21.3 47.2 L78.7 47.2 Q80.8 47.2 80.8 49.3 L80.8 93.9 Q80.8 96 78.7 96 L21.3 96 Q19.2 96 19.2 93.9 L19.2 49.3 Q19.2 47.2 21.3 47.2 Z M48.9 19.6 C48.9 24.8 43.7 29.1 37.3 29.1 C30.8 29.1 25.6 24.8 25.6 19.6 C25.6 14.3 30.8 10 37.3 10 C43.7 10 48.9 14.3 48.9 19.6 Z M74.4 19.6 C74.4 24.8 69.2 29.1 62.7 29.1 C56.3 29.1 51.1 24.8 51.1 19.6 C51.1 14.3 56.3 10 62.7 10 C69.2 10 74.4 14.3 74.4 19.6 Z M55.3 27 C55.3 29.9 52.9 32.3 50 32.3 C47.1 32.3 44.7 29.9 44.7 27 C44.7 24.1 47.1 21.7 50 21.7 C52.9 21.7 55.3 24.1 55.3 27 Z M42 19.6 C42 17.2 39.4 15.3 36.2 15.3 C33 15.3 30.4 17.2 30.4 19.6 C30.4 21.9 33 23.8 36.2 23.8 C39.4 23.8 42 21.9 42 19.6 Z M69.6 19.6 C69.6 17.2 67 15.3 63.8 15.3 C60.6 15.3 58 17.2 58 19.6 C58 21.9 60.6 23.8 63.8 23.8 C67 23.8 69.6 21.9 69.6 19.6 Z"),
        Category("Antipasti", 45, "M78 56 C78 78.1 65.5 96 50 96 C34.5 96 22 78.1 22 56 C22 33.9 34.5 16 50 16 C65.5 16 78 33.9 78 56 Z M61.5 40.9 L54.5 34 L61.5 27.1 L56.9 22.5 L50 29.5 L43.1 22.5 L38.5 27.1 L45.5 34 L38.5 40.9 L43.1 45.5 L50 38.5 L56.9 45.5 L61.5 40.9 Z"),
        Category("Bread & Spreads", 60, "M8 86 C4 66 22 52 50 52 C78 52 96 66 92 86 Q90 96 80 96 L20 96 Q10 96 8 86 Z M28.5 58 L32.5 75.5 L36.5 75 L32.5 57.5 L28.5 58 Z M44.5 55 L48.5 72.5 L52.5 72 L48.5 54.5 L44.5 55 Z M60.5 58 L64.5 75.5 L68.5 75 L64.5 57.5 L60.5 58 Z"),
    )

    // Paths

    /** Reads the absolute M L Q C Z paths above (in units). */
    fun parse(d: String): Path {
        val tokens = Regex("[A-Za-z]|-?\\d*\\.?\\d+").findAll(d).map { it.value }.toList()
        val path = Path()
        var i = 0
        var command = "M"
        fun n(): Float = tokens[i++].toFloat()
        while (i < tokens.size) {
            val t = tokens[i]
            if (t[0].isLetter()) {
                command = t
                i++
                if (command == "Z") path.close()
                continue
            }
            when (command) {
                "M" -> { path.moveTo(n(), n()); command = "L" }
                "L" -> path.lineTo(n(), n())
                "Q" -> path.quadTo(n(), n(), n(), n())
                "C" -> path.cubicTo(n(), n(), n(), n(), n(), n())
                else -> i++
            }
        }
        return path
    }

    val paths: List<Path> by lazy { categories.map { parse(it.path) } }

    // Layout (points of a 393-wide screen)

    const val HERO_SCALE = 2.2 // points per unit
    const val ICON_SCALE = 0.34
    const val COLUMN_GAP = 58.0

    class Layout(val height: Double, val top: Double, val bottom: Double) {
        val topRow = top + 42
        val bottomRow = height - bottom - 46
        /** The hero silhouettes stand on this line (unit y = 96). */
        val baseline = (topRow + bottomRow) / 2 - 12 + 92
        val nameY = baseline + 46
        val countY = baseline + 68
        /** The grains' swirl turns about this point. */
        val swirlX = 196.5
        val swirlY = baseline - 48 * HERO_SCALE + 30

        fun heroX(u: Double) = 196.5 + (u - 50) * HERO_SCALE
        fun heroY(v: Double) = baseline + (v - 96) * HERO_SCALE
        fun tabX(i: Int) = 196.5 + ((i % 6) - 2.5) * COLUMN_GAP
        fun tabY(i: Int) = if (i < 6) topRow else bottomRow
    }

    // Grains

    const val GRAIN_COUNT = 1400
    const val GRID = 110

    fun hash(x: Double): Double {
        val s = sin(x * 12.9898 + 78.233) * 43758.5453
        return s - floor(s)
    }

    /** One silhouette as grains, in units: positions and each grain's height share (0 top, 1 bottom). */
    class Grains(val x: DoubleArray, val y: DoubleArray, val r: DoubleArray, val top: Double, val bottom: Double)

    private val cache = HashMap<Int, Grains>()

    /**
     * Samples shape [index] once: a jittered 110 × 110 grid kept where the path fills (row by row),
     * then 1,400 picks spread evenly through that list. Seeded, so every run gets the same grains.
     */
    fun grains(index: Int): Grains {
        cache[index]?.let { return it }
        val side = 440
        val bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ALPHA_8)
        val canvas = Canvas(bitmap)
        canvas.scale(side / 100f, side / 100f)
        canvas.drawPath(paths[index], Paint(Paint.ANTI_ALIAS_FLAG))
        val buffer = ByteBuffer.allocate(bitmap.rowBytes * side)
        bitmap.copyPixelsToBuffer(buffer)
        val row = bitmap.rowBytes
        bitmap.recycle()
        val bytes = buffer.array()
        val ix = ArrayList<Double>()
        val iy = ArrayList<Double>()
        val cell = 100.0 / GRID
        for (r in 0 until GRID) for (c in 0 until GRID) {
            val n = (r * GRID + c).toDouble() + index * 7919.0
            val px = (c + 0.15 + 0.7 * hash(n * 0.731)) * cell
            val py = (r + 0.15 + 0.7 * hash(n * 0.731 + 0.5)) * cell
            val mx = min(side - 1, (px / 100 * side).toInt())
            val my = min(side - 1, (py / 100 * side).toInt())
            if ((bytes[my * row + mx].toInt() and 0xFF) > 127) {
                ix.add(px)
                iy.add(py)
            }
        }
        val m = max(ix.size, 1)
        val xs = DoubleArray(GRAIN_COUNT)
        val ys = DoubleArray(GRAIN_COUNT)
        var top = 100.0
        var bottom = 0.0
        for (i in 0 until GRAIN_COUNT) {
            val k = min(m - 1, ((i + hash(i * 0.37 + index * 3.1)) * m / GRAIN_COUNT).toInt())
            xs[i] = if (ix.isEmpty()) 50.0 else ix[k]
            ys[i] = if (iy.isEmpty()) 50.0 else iy[k]
            top = min(top, ys[i])
            bottom = max(bottom, ys[i])
        }
        val span = max(bottom - top, 1.0)
        val g = Grains(xs, ys, DoubleArray(GRAIN_COUNT) { (ys[it] - top) / span }, top, bottom)
        cache[index] = g
        return g
    }

    // Drawing (canvas already scaled to 393-wide points)

    class Paints {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val name = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 17f
            textAlign = Paint.Align.CENTER
            typeface = if (Build.VERSION.SDK_INT >= 28) Typeface.create(Typeface.DEFAULT, 600, false) else Typeface.DEFAULT_BOLD
            letterSpacing = -0.2f / 17f
        }
        val count = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 13f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT
        }
        val xs = DoubleArray(GRAIN_COUNT)
        val ys = DoubleArray(GRAIN_COUNT)
        val fly = DoubleArray(GRAIN_COUNT)
        val rect = RectF()
    }

    /** Static paper speckle over the cream, seeded. */
    fun drawPaper(canvas: Canvas, width: Float, height: Float, paint: Paint) {
        for (i in 0 until 900) {
            val x = (hash(i * 1.37 + 11) * width).toFloat()
            val y = (hash(i * 2.11 + 23) * height).toFloat()
            val r = (0.3 + 0.4 * hash(i * 3.7 + 5)).toFloat()
            paint.color = if (i % 3 == 0) 0x80FFFFFF.toInt() else ((0x0F shl 24) or (INK and 0xFFFFFF))
            canvas.drawCircle(x, y, r, paint)
        }
    }

    private fun withAlpha(color: Int, alpha: Double): Int =
        ((alpha.coerceIn(0.0, 1.0) * 255).toInt() shl 24) or (color and 0xFFFFFF)

    fun draw(canvas: Canvas, l: Layout, f: DeliGrainTabsMotion.Frame, still: Boolean, p: Paints) {
        drawTabs(canvas, l, f, still, p)
        if (still) {
            val q = DeliGrainTabsMotion.easeOutCubic(DeliGrainTabsMotion.seg(f.p, 0.0, 0.3))
            if (q < 1) drawRest(canvas, l, f.fromTab, 1 - q, p)
            drawRest(canvas, l, f.to, q, p)
        } else {
            drawHero(canvas, l, f, p)
        }
        drawLabels(canvas, l, f, still, p)
    }

    private inline fun inHero(canvas: Canvas, l: Layout, block: () -> Unit) {
        canvas.save()
        canvas.translate((196.5 - 50 * HERO_SCALE).toFloat(), (l.baseline - 96 * HERO_SCALE).toFloat())
        canvas.scale(HERO_SCALE.toFloat(), HERO_SCALE.toFloat())
        block()
        canvas.restore()
    }

    private fun drawTabs(canvas: Canvas, l: Layout, f: DeliGrainTabsMotion.Frame, still: Boolean, p: Paints) {
        val m = DeliGrainTabsMotion
        val change = f.fromTab != f.to
        val swap = if (still) m.seg(f.p, 0.0, 0.3) else m.easeOutCubic(m.seg(f.p, 0.0, 0.25))
        for (i in categories.indices) {
            var amount = 0.0
            if (i == f.to) amount = if (change) swap else 1.0
            if (change && i == f.fromTab) amount = 1 - swap
            val pop = if (!still && change && i == f.to) 1 + 0.14 * sin(PI * m.seg(f.p, 0.0, 0.35)) else 1.0
            val s = (ICON_SCALE * pop).toFloat()
            canvas.save()
            canvas.translate(l.tabX(i).toFloat(), l.tabY(i).toFloat())
            canvas.scale(s, s)
            canvas.translate(-50f, -50f)
            p.fill.color = mix(IDLE, INK, amount)
            canvas.drawPath(paths[i], p.fill)
            canvas.restore()
        }
        // The indicator: slides within a row, hands over between rows.
        p.fill.color = INK
        fun dot(x: Double, y: Double, scale: Double) {
            if (scale <= 0.01) return
            canvas.drawCircle(x.toFloat(), (y + 25).toFloat(), (2.0 * scale).toFloat(), p.fill)
        }
        val ax = l.tabX(f.fromTab); val ay = l.tabY(f.fromTab)
        val bx = l.tabX(f.to); val by = l.tabY(f.to)
        when {
            !change -> dot(bx, by, 1.0)
            still -> { dot(ax, ay, 1 - swap); dot(bx, by, swap) }
            ay == by -> {
                val e = m.easeInOutCubic(m.seg(f.p, 0.0, 0.45))
                dot(ax + (bx - ax) * e, by, 1.0)
            }
            else -> {
                dot(ax, ay, 1 - m.easeInCubic(m.seg(f.p, 0.0, 0.22)))
                dot(bx, by, m.easeOutCubic(m.seg(f.p, 0.22, 0.45)))
            }
        }
    }

    /** A silhouette at rest (reduced motion): its fill and its grains. */
    private fun drawRest(canvas: Canvas, l: Layout, shape: Int, alpha: Double, p: Paints) {
        if (alpha <= 0.001) return
        p.fill.color = withAlpha(INK, alpha)
        inHero(canvas, l) { canvas.drawPath(paths[shape], p.fill) }
        val g = grains(shape)
        for (i in 0 until GRAIN_COUNT) {
            canvas.drawCircle(l.heroX(g.x[i]).toFloat(), l.heroY(g.y[i]).toFloat(), 1.9f, p.fill)
        }
    }

    private fun drawHero(canvas: Canvas, l: Layout, f: DeliGrainTabsMotion.Frame, p: Paints) {
        val m = DeliGrainTabsMotion
        val calm = 1 - m.stirEnergy(f.stirs, f.clock)
        // The solid fill: drains bottom-up from the old shape, fills bottom-up into the new one.
        if (calm > 0.01) {
            p.fill.color = withAlpha(INK, calm)
            if (f.resting) {
                inHero(canvas, l) { canvas.drawPath(paths[f.to], p.fill) }
            } else {
                if (f.from >= 0) {
                    val g = grains(f.from)
                    val r = 1 - (f.p - 0.05) / 0.36 - 0.03
                    if (r > 0) {
                        val y = l.heroY(g.top + r * (g.bottom - g.top)).toFloat()
                        canvas.save()
                        canvas.clipRect(0f, -2000f, 393f, y)
                        inHero(canvas, l) { canvas.drawPath(paths[f.from], p.fill) }
                        canvas.restore()
                    }
                }
                val g = grains(f.to)
                val r = 1 - (f.p - 1.04) / 0.40 + 0.08
                if (r < 1) {
                    val y = l.heroY(g.top + max(r, -1.0) * (g.bottom - g.top)).toFloat()
                    canvas.save()
                    canvas.clipRect(0f, y, 393f, y + 4000f)
                    inHero(canvas, l) { canvas.drawPath(paths[f.to], p.fill) }
                    canvas.restore()
                }
            }
        }
        // The grains.
        m.positions(f, l, p.xs, p.ys, p.fly)
        p.fill.color = INK
        for (i in 0 until GRAIN_COUNT) {
            val r = (1.9 * (1 - 0.3 * p.fly[i])).toFloat()
            canvas.drawCircle(p.xs[i].toFloat(), p.ys[i].toFloat(), r, p.fill)
        }
    }

    private fun drawLabels(canvas: Canvas, l: Layout, f: DeliGrainTabsMotion.Frame, still: Boolean, p: Paints) {
        val m = DeliGrainTabsMotion
        fun label(i: Int, alpha: Double, dy: Double) {
            if (alpha <= 0.001) return
            val cat = categories[i]
            // Text is placed by its visual centre, as SwiftUI's centred anchor does.
            p.name.color = withAlpha(INK, alpha)
            val nameBase = (l.nameY + dy - (p.name.ascent() + p.name.descent()) / 2).toFloat()
            canvas.drawText(cat.name, 196.5f, nameBase, p.name)
            p.count.color = withAlpha(MUTED, alpha)
            val countBase = (l.countY + dy - (p.count.ascent() + p.count.descent()) / 2).toFloat()
            canvas.drawText("${cat.count} products", 196.5f, countBase, p.count)
        }
        when {
            f.fromTab == f.to -> label(f.to, 1.0, 0.0)
            still -> {
                val q = m.seg(f.p, 0.0, 0.3)
                label(f.fromTab, 1 - q, 0.0)
                label(f.to, q, 0.0)
            }
            else -> {
                val out = m.easeInCubic(m.seg(f.p, 0.0, 0.3))
                val into = m.easeOutCubic(m.seg(f.p, 1.05, 1.4))
                label(f.fromTab, 1 - out, 6 * out)
                label(f.to, into, 6 * (1 - into))
            }
        }
    }
}
