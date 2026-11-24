// Ink Wash Page Turn — the three pages, each drawn once into a bitmap (iwp-pages.ts).
//
// A page is warm paper, a date, a title, a short poem in the system font and a small ink-wash drawing
// under it. Nothing here runs per frame: the bitmaps are baked when the screen's size is known and the
// bleed only samples them. All lengths are dp; the canvas is scaled to the bitmap.
package com.ngocdevv.nativecook.blocks.navigation.inkwashpageturn

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

internal const val PAPER_ARGB = 0xFFF3ECDD.toInt()
private const val PAPER_EDGE = 0xFFE9DFCB.toInt()
private const val TEXT = 0xFF26231E.toInt()
private const val MUTE = 0xFF8C8576.toInt()
private const val WASH = 0xFF2A2B36.toInt()
private const val SEAL = 0xFFC2553C.toInt()
private const val OCHRE = 0xFFD9A441.toInt()

private class Poem(val date: String, val title: String, val body: String)

private val POEMS = listOf(
    Poem(
        "Saturday, 14 March",
        "Low Tide",
        "The harbour pulls its blanket back\nand shows us what it kept:\na rope, a shoe, a ring of keys,\nthe print where a heron stepped.\n\nWe walk out further than we should\non ground that was the sea.\nNothing out here is in a hurry.\nFor an hour, neither are we.",
    ),
    Poem(
        "Sunday, 15 March",
        "Kettle",
        "Before the house is properly awake\nthe kettle starts on its complaint.\nSteam finds the glass, writes a line,\nand rubs it out again.\n\nI hold the cup with both my hands\nthe way you hold good news,\nnot drinking yet, just letting it\nbe warm, be mine, be morning.",
    ),
    Poem(
        "Monday, 16 March",
        "Last Bus",
        "The last bus hums along the coast\nwith four of us inside,\neach window carrying a moon\nit did not ask to hold.\n\nNobody speaks. The driver knows\nthe road the way you know a song.\nWe are delivered, one by one,\nto porches with the lights left on.",
    ),
)

private fun face(weight: Int): Typeface = when {
    Build.VERSION.SDK_INT >= 28 -> Typeface.create(Typeface.DEFAULT, weight, false)
    weight >= 600 -> Typeface.DEFAULT_BOLD
    weight >= 500 -> Typeface.create("sans-serif-medium", Typeface.NORMAL)
    else -> Typeface.DEFAULT
}

/** a block of text wrapped to a width, with a fixed line height and the leading split around each line */
private class Paragraph(text: String, size: Float, private val line: Float, weight: Int, color: Int, width: Float, spacing: Float) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG or Paint.LINEAR_TEXT_FLAG).apply {
        this.color = color
        textSize = size
        typeface = face(weight)
        letterSpacing = spacing / size
    }
    private val lines = ArrayList<String>()
    val height: Float get() = lines.size * line

    init {
        for (row in text.split('\n')) {
            var current = ""
            for (word in row.split(' ')) {
                val next = if (current.isEmpty()) word else "$current $word"
                if (current.isNotEmpty() && paint.measureText(next) > width) {
                    lines.add(current)
                    current = word
                } else {
                    current = next
                }
            }
            lines.add(current)
        }
    }

    fun draw(canvas: Canvas, x: Float, y: Float) {
        val metrics = paint.fontMetrics
        val base = (line - (metrics.descent - metrics.ascent)) / 2f - metrics.ascent
        lines.forEachIndexed { i, row -> if (row.isNotEmpty()) canvas.drawText(row, x, y + i * line + base, paint) }
    }
}

/** Skia's blur takes a sigma, Android's a radius: sigma = 0.57735 r + 0.5 */
private fun wash(color: Int, alpha: Float, sigma: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    this.color = Color.argb((alpha * 255f + 0.5f).toInt(), Color.red(color), Color.green(color), Color.blue(color))
    if (sigma > 0f) maskFilter = BlurMaskFilter(max(0.1f, (sigma - 0.5f) / 0.57735f), BlurMaskFilter.Blur.NORMAL)
}

private fun brush(color: Int, alpha: Float, sigma: Float, width: Float) = wash(color, alpha, sigma).apply {
    style = Paint.Style.STROKE
    strokeWidth = width
    strokeCap = Paint.Cap.ROUND
}

/** Low Tide: a small red sun over four tide lines that thin out towards the shore */
private fun drawTide(c: Canvas, x: Float, y: Float, w: Float, h: Float) {
    c.drawCircle(x + w * 0.74f, y + h * 0.22f, h * 0.13f, wash(SEAL, 0.78f, 1.2f))
    val rows = arrayOf(
        floatArrayOf(0.02f, 0.98f, 0.5f, 11f, 0.5f),
        floatArrayOf(0.14f, 0.9f, 0.66f, 8f, 0.36f),
        floatArrayOf(0.3f, 0.78f, 0.8f, 6f, 0.26f),
        floatArrayOf(0.44f, 0.66f, 0.92f, 4f, 0.18f),
    )
    for ((x0, x1, at, width, alpha) in rows) {
        val path = Path()
        path.moveTo(x + w * x0, y + h * at)
        path.cubicTo(x + w * (x0 + (x1 - x0) * 0.3f), y + h * (at - 0.035f), x + w * (x0 + (x1 - x0) * 0.68f), y + h * (at + 0.035f), x + w * x1, y + h * at)
        c.drawPath(path, brush(WASH, alpha, 2.4f, width))
    }
}

/** Kettle: a cup seen from the side and three threads of steam */
private fun drawSteam(c: Canvas, x: Float, y: Float, w: Float, h: Float) {
    val cx = x + w * 0.5f
    val rim = y + h * 0.66f
    val cup = Path()
    cup.moveTo(cx - h * 0.2f, rim)
    cup.cubicTo(cx - h * 0.19f, rim + h * 0.26f, cx - h * 0.1f, rim + h * 0.3f, cx, rim + h * 0.3f)
    cup.cubicTo(cx + h * 0.1f, rim + h * 0.3f, cx + h * 0.19f, rim + h * 0.26f, cx + h * 0.2f, rim)
    cup.close()
    c.drawPath(cup, wash(WASH, 0.5f, 2.2f))
    c.drawOval(RectF(cx - h * 0.2f, rim - h * 0.028f, cx + h * 0.2f, rim + h * 0.028f), wash(WASH, 0.62f, 1f))
    val handle = Path()
    handle.moveTo(cx + h * 0.19f, rim + h * 0.06f)
    handle.cubicTo(cx + h * 0.3f, rim + h * 0.04f, cx + h * 0.3f, rim + h * 0.2f, cx + h * 0.16f, rim + h * 0.2f)
    c.drawPath(handle, brush(WASH, 0.46f, 1.6f, 5f))
    val threads = arrayOf(floatArrayOf(-0.09f, 0.3f, 5f), floatArrayOf(0f, 0.42f, 7f), floatArrayOf(0.09f, 0.26f, 4f))
    for ((dx, alpha, width) in threads) {
        val path = Path()
        val sx = cx + h * dx
        path.moveTo(sx, rim - h * 0.08f)
        path.cubicTo(sx - h * 0.09f, rim - h * 0.22f, sx + h * 0.1f, rim - h * 0.32f, sx, rim - h * 0.46f)
        path.cubicTo(sx - h * 0.07f, rim - h * 0.54f, sx + h * 0.05f, rim - h * 0.6f, sx + h * 0.01f, rim - h * 0.66f)
        c.drawPath(path, brush(WASH, alpha, 3.2f, width))
    }
    c.drawCircle(cx - h * 0.34f, rim + h * 0.24f, h * 0.035f, wash(SEAL, 0.78f, 0.8f))
}

/** Last Bus: a pale moon, two headlands and four lit windows on the road between them */
private fun drawCoast(c: Canvas, x: Float, y: Float, w: Float, h: Float) {
    c.drawCircle(x + w * 0.3f, y + h * 0.3f, h * 0.2f, wash(OCHRE, 0.5f, 3f))
    val far = Path()
    far.moveTo(x, y + h * 0.78f)
    far.cubicTo(x + w * 0.2f, y + h * 0.5f, x + w * 0.42f, y + h * 0.56f, x + w * 0.62f, y + h * 0.72f)
    far.cubicTo(x + w * 0.76f, y + h * 0.82f, x + w * 0.9f, y + h * 0.7f, x + w, y + h * 0.74f)
    far.lineTo(x + w, y + h)
    far.lineTo(x, y + h)
    far.close()
    c.drawPath(far, wash(WASH, 0.26f, 4f))
    val near = Path()
    near.moveTo(x, y + h * 0.94f)
    near.cubicTo(x + w * 0.3f, y + h * 0.8f, x + w * 0.56f, y + h * 0.9f, x + w * 0.74f, y + h * 0.86f)
    near.cubicTo(x + w * 0.86f, y + h * 0.83f, x + w * 0.94f, y + h * 0.9f, x + w, y + h * 0.9f)
    near.lineTo(x + w, y + h)
    near.lineTo(x, y + h)
    near.close()
    c.drawPath(near, wash(WASH, 0.52f, 2.4f))
    for (i in 0 until 4) c.drawCircle(x + w * (0.6f + i * 0.03f), y + h * (0.846f - i * 0.003f), 2.2f, wash(SEAL, 0.9f, 0.5f))
}

private fun drawPage(c: Canvas, index: Int, width: Float, height: Float, top: Float, bottom: Float) {
    val compact = height < 740f
    val margin = 32f
    // warm paper, a little darker towards the corners
    val paper = Paint().apply {
        shader = RadialGradient(
            width / 2f, height * 0.42f, hypot(width, height) * 0.62f,
            intArrayOf(PAPER_ARGB, PAPER_ARGB, PAPER_EDGE), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP,
        )
    }
    c.drawRect(0f, 0f, width, height, paper)

    val poem = POEMS[index]
    val column = width - margin * 2f
    var y = top + if (compact) 58f else 84f
    Paragraph(poem.date, 13f, 18f, 500, MUTE, column, 0f).draw(c, margin, y)
    y += 18f + if (compact) 4f else 8f
    val title = Paragraph(poem.title, if (compact) 28f else 34f, if (compact) 34f else 40f, 700, TEXT, column, -0.6f)
    title.draw(c, margin, y)
    y += title.height + if (compact) 12f else 20f
    val body = Paragraph(poem.body, if (compact) 16f else 17f, if (compact) 24f else 28f, 400, TEXT, column, -0.1f)
    body.draw(c, margin, y)
    y += body.height

    // the drawing fills what is left above the page dots
    val floor = height - max(bottom, 12f) - if (compact) 56f else 84f
    val room = max(0f, floor - y - if (compact) 14f else 28f)
    val h = min(room, if (compact) 150f else 210f)
    if (h < 70f) return
    val at = floor - h
    when (index) {
        0 -> drawTide(c, margin, at, column, h)
        1 -> drawSteam(c, margin, at, column, h)
        else -> drawCoast(c, margin, at, column, h)
    }
}

/** the pixels per dp the pages are baked at */
internal fun pageScale(density: Float) = min(3f, density)

/** the three pages as bitmaps, [scale] pixels per dp */
internal fun bakePages(width: Float, height: Float, top: Float, bottom: Float, scale: Float): List<Bitmap> =
    POEMS.indices.map { i ->
        val bitmap = Bitmap.createBitmap(ceil(width * scale).toInt().coerceAtLeast(1), ceil(height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.scale(scale, scale)
        drawPage(canvas, i, width, height, top, bottom)
        bitmap
    }
