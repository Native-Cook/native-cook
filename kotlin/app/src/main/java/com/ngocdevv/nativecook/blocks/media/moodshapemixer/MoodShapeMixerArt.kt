package com.ngocdevv.nativecook.blocks.media.moodshapemixer

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Picture
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import com.ngocdevv.nativecook.blocks.media.moodshapemixer.MoodShapeMixerModel as M
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

private const val PAPER = 0xFFECE7DB.toInt()
private const val INK = 0xFF211F1D.toInt()
private const val LINE = 0xFF8F8A7F.toInt()

/** Static source linework is recorded once; only the chooser and compact panel redraw. */
internal class MoodShapeMixerArt {
    private val painter = MoodShapeMixerPainter()
    private val room = Picture().apply {
        painter.canvas = beginRecording(393, 852)
        painter.room()
        endRecording()
    }
    fun draw(canvas: Canvas, shown: M.Shown, still: Boolean) {
        canvas.drawPicture(room)
        painter.canvas = canvas
        painter.stage(shown, still)
    }
}

private object MoodShapeMixerPaths {
    val roomLines = Path().apply {
        // Window frame and sill.
        addRect(-4f, -20f, 265f, 195f, Path.Direction.CW)
        addRect(-4f, 195f, 278f, 205f, Path.Direction.CW)
        moveTo(105f, -20f)
        lineTo(105f, 195f)
        moveTo(113f, -20f)
        lineTo(113f, 195f)
        moveTo(256f, -20f)
        lineTo(256f, 195f)
        // Sleeping cat and the curved tail behind the clock.
        moveTo(42f, 324f)
        cubicTo(33f, 295f, 49f, 261f, 81f, 243f)
        lineTo(78f, 261f)
        lineTo(107f, 253f)
        lineTo(131f, 258f)
        cubicTo(143f, 283f, 148f, 303f, 139f, 323f)
        moveTo(132f, 261f)
        cubicTo(178f, 230f, 224f, 230f, 263f, 266f)
        cubicTo(291f, 279f, 294f, 315f, 286f, 342f)
        moveTo(230f, 276f)
        cubicTo(201f, 261f, 179f, 223f, 198f, 233f)
        cubicTo(215f, 220f, 218f, 257f, 241f, 268f)
        cubicTo(273f, 283f, 292f, 265f, 273f, 263f)
        cubicTo(254f, 246f, 240f, 250f, 234f, 255f)
        moveTo(67f, 308f)
        quadTo(77f, 301f, 87f, 308f)
        moveTo(111f, 307f)
        quadTo(121f, 301f, 130f, 308f)
        moveTo(98f, 312f)
        lineTo(104f, 318f)
        lineTo(110f, 312f)
        close()
        moveTo(61f, 316f)
        lineTo(87f, 319f)
        moveTo(121f, 318f)
        lineTo(145f, 314f)
        moveTo(0f, 342f)
        quadTo(22f, 315f, 73f, 324f)
        lineTo(259f, 324f)
        quadTo(285f, 336f, 297f, 357f)
        // A plain framed print on the right wall.
        addRect(324f, 82f, 491f, 403f, Path.Direction.CW)
        addRect(333f, 92f, 484f, 394f, Path.Direction.CW)
        moveTo(382f, 161f)
        cubicTo(374f, 225f, 357f, 264f, 405f, 283f)
        quadTo(447f, 280f, 440f, 164f)
        // Cube on the desk: all visible interior grid lines.
        moveTo(128f, 585f)
        lineTo(186f, 581f)
        lineTo(221f, 605f)
        lineTo(166f, 611f)
        close()
        moveTo(128f, 585f)
        lineTo(128f, 644f)
        lineTo(166f, 668f)
        lineTo(221f, 668f)
        lineTo(221f, 605f)
        moveTo(166f, 611f)
        lineTo(166f, 668f)
        moveTo(147f, 598f)
        lineTo(203f, 593f)
        moveTo(149f, 584f)
        lineTo(183f, 609f)
        lineTo(183f, 668f)
        moveTo(128f, 614f)
        lineTo(166f, 639f)
        lineTo(221f, 639f)
        moveTo(147f, 598f)
        lineTo(147f, 656f)
        // Sketchbook and pencil.
        moveTo(-20f, 708f)
        lineTo(79f, 687f)
        lineTo(141f, 766f)
        lineTo(0f, 806f)
        moveTo(124f, 713f)
        lineTo(259f, 773f)
        lineTo(246f, 780f)
        close()
        moveTo(125f, 714f)
        lineTo(133f, 729f)
        lineTo(140f, 721f)
        moveTo(10f, 736f)
        lineTo(17f, 730f)
        lineTo(24f, 733f)
        lineTo(31f, 725f)
        lineTo(39f, 727f)
        lineTo(49f, 722f)
        lineTo(72f, 716f)
        // Glass and ice.
        addOval(300f, 521f, 454f, 586f, Path.Direction.CW)
        moveTo(300f, 552f)
        lineTo(321f, 740f)
        moveTo(454f, 552f)
        lineTo(438f, 740f)
        addOval(321f, 714f, 438f, 761f, Path.Direction.CW)
        addOval(309f, 589f, 448f, 642f, Path.Direction.CW)
        moveTo(335f, 603f)
        lineTo(360f, 583f)
        lineTo(393f, 600f)
        lineTo(369f, 620f)
        close()
        addOval(375f, 657f, 380f, 665f, Path.Direction.CW)
        addOval(369f, 682f, 373f, 688f, Path.Direction.CW)
    }
    val clock = Path().apply { addRoundRect(15f, 355f, 303f, 562f, 34f, 34f, Path.Direction.CW) }
    val digitOne = Path().apply {
        moveTo(-10f, -25f)
        quadTo(2f, -45f, 1f, -34f)
        lineTo(-2f, 34f)
    }
    val digitThree = Path().apply {
        moveTo(-13f, -28f)
        cubicTo(12f, -48f, 18f, -13f, 5f, -1f)
        cubicTo(28f, 14f, 5f, 48f, -13f, 31f)
    }
    val digitFour = Path().apply {
        moveTo(1f, -35f)
        lineTo(-12f, 9f)
        lineTo(15f, 9f)
        moveTo(9f, -21f)
        lineTo(7f, 34f)
    }
    val crease = Path().apply { moveTo(-23.5f, 0f); lineTo(23.5f, 0f) }
    val handle = Path().apply {
        moveTo(91f, 330f)
        lineTo(99f, 317f)
        lineTo(177f, 317f)
        lineTo(189f, 331f)
        close()
    }
    val power = Path().apply {
        addArc(32f, 779f, 52f, 799f, -55f, 290f)
        moveTo(42f, 777f)
        lineTo(42f, 787f)
    }
    val sliders = Path().apply {
        repeat(3) { index ->
            val y = 781f + index * 8
            moveTo(341f, y)
            lineTo(366f, y)
            val x = 346f + ((index + 1) % 2) * 9
            addOval(x, y - 2.5f, x + 5, y + 2.5f, Path.Direction.CW)
        }
    }
    val close = Path().apply {
        moveTo(189.5f, 777f)
        lineTo(203.5f, 791f)
        moveTo(203.5f, 777f)
        lineTo(189.5f, 791f)
    }
    val icons = List(5) { index ->
        Path().apply {
            when (index) {
                0 -> {
                    addOval(-11f, -11f, 11f, 11f, Path.Direction.CW)
                    addOval(-7f, -7f, 7f, 7f, Path.Direction.CW)
                    addOval(-3f, -3f, 3f, 3f, Path.Direction.CW)
                }
                1 -> {
                    moveTo(-12f, -10f)
                    lineTo(0f, -7f)
                    lineTo(12f, -10f)
                    lineTo(12f, 10f)
                    lineTo(0f, 12f)
                    lineTo(-12f, 10f)
                    close()
                    moveTo(0f, -7f)
                    lineTo(0f, 12f)
                }
                2 -> {
                    addOval(-11f, -11f, 11f, 11f, Path.Direction.CW)
                    for (offset in floatArrayOf(-5f, 4f)) {
                        moveTo(-10f, offset)
                        quadTo(0f, offset - 5, 10f, offset + 2)
                    }
                }
                3 -> repeat(4) { index ->
                    val a = index * PI / 4
                    moveTo((-13 * cos(a)).toFloat(), (-13 * sin(a)).toFloat())
                    lineTo((13 * cos(a)).toFloat(), (13 * sin(a)).toFloat())
                }
                else -> {
                    addArc(-11f, -11f, 11f, 11f, -50f, 280f)
                    quadTo(1f, 4f, 7f, -8f)
                    close()
                    addOval(4f, -7f, 6f, -5f, Path.Direction.CW)
                }
            }
        }
    }
    val wash = LinearGradient(
        0f, 0f, 0f, 852f,
        intArrayOf(0x73ECE7DB, PAPER, PAPER), floatArrayOf(0f, 0.18f, 1f), Shader.TileMode.CLAMP,
    )
}

private class MoodShapeMixerPainter {
    lateinit var canvas: Canvas
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private var opacity = 1.0
    private val shape = Path()
    private val face = Path()
    private val surface = Path()
    private val chevron = Path()
    private val regular = Typeface.create("sans-serif", Typeface.NORMAL)
    private val medium = if (Build.VERSION.SDK_INT >= 28) Typeface.create(regular, 500, false) else Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private val semibold = if (Build.VERSION.SDK_INT >= 28) Typeface.create(regular, 600, false) else Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private val bold = Typeface.create("sans-serif", Typeface.BOLD)
    private val names = listOf("FOCUS", "STUDYING", "RELAX", "WORK", "SLEEP")
    private val colors = intArrayOf(0xFF7EC9D2.toInt(), 0xFFEACD73.toInt(), 0xFFDB92B5.toInt(), 0xFFA5A0E0.toInt(), 0xFFB59ACF.toInt())

    private inline fun saved(alpha: Double = 1.0, block: () -> Unit) {
        val previous = opacity
        canvas.save()
        opacity *= alpha
        try { block() } finally {
            opacity = previous
            canvas.restore()
        }
    }
    private fun prepare(color: Int) {
        paint.shader = null
        paint.color = color
        paint.alpha = (Color.alpha(color) * opacity).roundToInt().coerceIn(0, 255)
        paint.style = Paint.Style.FILL
    }
    private fun fill(path: Path, color: Int) {
        prepare(color)
        canvas.drawPath(path, paint)
    }
    private fun stroke(path: Path, color: Int = LINE, width: Double = 1.0) {
        prepare(color)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = width.toFloat()
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        canvas.drawPath(path, paint)
    }
    private fun rect(x: Double, y: Double, w: Double, h: Double, color: Int, radius: Double = 0.0) {
        prepare(color)
        canvas.drawRoundRect(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat(), radius.toFloat(), radius.toFloat(), paint)
    }
    private fun ellipse(x: Double, y: Double, w: Double, h: Double, color: Int) {
        prepare(color)
        canvas.drawOval(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat(), paint)
    }
    private fun label(text: String, x: Double, y: Double, size: Double, weight: Int = 400, color: Int = INK, tracking: Double = 0.0) {
        if (text.isEmpty()) return
        prepare(color)
        paint.textSize = size.toFloat()
        paint.typeface = when (weight) { 500 -> medium; 600 -> semibold; 700 -> bold; else -> regular }
        val baseline = (y - (paint.ascent() + paint.descent()) / 2).toFloat()
        if (tracking == 0.0) {
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText(text, x.toFloat(), baseline, paint)
        } else {
            paint.textAlign = Paint.Align.LEFT
            val widths = FloatArray(text.length)
            paint.getTextWidths(text, widths)
            var left = x - (widths.sum() + tracking * (text.length - 1)) / 2
            text.forEachIndexed { index, char ->
                canvas.drawText(char.toString(), left.toFloat(), baseline, paint)
                left += widths[index] + tracking
            }
        }
    }

    fun room() {
        rect(0.0, 0.0, 393.0, 852.0, PAPER)
        rect(0.0, -20.0, 103.0, 215.0, 0xFF9ABBF5.toInt())
        rect(114.0, -20.0, 142.0, 215.0, 0xFF9ABBF5.toInt())
        ellipse(40.0, 69.0, 42.0, 42.0, 0xFFF1F8FA.toInt())
        ellipse(167.0, 113.0, 14.0, 14.0, 0xFFFFF24A.toInt())
        stroke(MoodShapeMixerPaths.roomLines)
        fill(MoodShapeMixerPaths.clock, 0xFFE9E5DB.toInt())
        stroke(MoodShapeMixerPaths.clock)
        rect(28.0, 371.0, 260.0, 180.0, 0xFF242323.toInt(), 24.0)
        ellipse(47.0, 389.0, 9.0, 9.0, 0xFFF3EED9.toInt())
        ellipse(50.0, 387.0, 8.0, 8.0, 0xFF242323.toInt())
        label("PM", 68.0, 394.0, 12.0, 500, 0xFFF3EED9.toInt())
        label("Tuesday", 242.0, 394.0, 12.0, 500, 0xFFF3EED9.toInt())
        val xs = doubleArrayOf(43.0, 96.0, 165.0, 218.0)
        val angles = floatArrayOf(-3f, 1f, -4f, 2f)
        val digits = listOf(MoodShapeMixerPaths.digitOne, MoodShapeMixerPaths.digitThree, MoodShapeMixerPaths.digitOne, MoodShapeMixerPaths.digitFour)
        repeat(4) { index ->
            saved {
                canvas.translate((xs[index] + 23.5).toFloat(), 468f)
                canvas.rotate(angles[index])
                rect(-23.5, -50.0, 47.0, 100.0, 0xFFE6E6D8.toInt(), 2.0)
                stroke(digits[index], 0xFF242323.toInt(), 6.0)
                stroke(MoodShapeMixerPaths.crease, 0xFF242323.toInt())
            }
        }
        fill(MoodShapeMixerPaths.handle, PAPER)
        stroke(MoodShapeMixerPaths.handle)
        stroke(MoodShapeMixerPaths.power, INK, 2.0)
        stroke(MoodShapeMixerPaths.sliders, INK, 1.5)
    }

    // Measured source face proportions, interpolated with the same slider curve.
    private val faces = arrayOf(
        doubleArrayOf(10.4, -1.5, 7.2, 10.9, 12.0, -11.0, 3.2, 0.0),
        doubleArrayOf(9.3, -3.0, 6.2, 10.0, 10.0, -4.0, 2.3, 1.0),
        doubleArrayOf(9.0, -5.0, 6.2, 6.8, 4.8, -1.3, 2.5, 0.0),
        doubleArrayOf(9.0, -5.3, 6.2, 7.0, 4.5, 0.0, 2.5, 0.0),
        doubleArrayOf(8.9, -3.0, 6.2, 6.5, 6.8, 3.0, 2.5, 0.0),
        doubleArrayOf(8.2, -5.4, 5.7, 7.8, 5.5, 8.2, 2.5, 0.0),
        doubleArrayOf(7.8, -4.5, 5.6, 8.6, 6.3, 8.5, 2.4, 0.0),
    )
    private fun faceMetric(m: Double, feature: Int): Double {
        val value = M.clamp(m)
        val index = kotlin.math.floor(value).toInt()
        return M.mix(faces[index][feature], faces[minOf(index + 1, 6)][feature], M.ease(value - index))
    }

    private fun blob(x: Double, y: Double, r: Double, m: Double) {
        ellipse(x - r, y - r, r * 2, r * 2, M.color(m))
        val radii = M.outline(m)
        val scale = r / 72
        shape.rewind()
        radii.forEachIndexed { index, radius ->
            val angle = index.toDouble() / radii.size * 2 * PI
            val px = (x + cos(angle) * radius * 44 * scale).toFloat()
            val py = (y + sin(angle) * radius * 44 * scale).toFloat()
            if (index == 0) shape.moveTo(px, py) else shape.lineTo(px, py)
        }
        shape.close()
        fill(shape, Color.WHITE)
        val tired = faceMetric(m, 7)
        val eyeHalf = faceMetric(m, 0)
        val eyeY = faceMetric(m, 1)
        val eyeSize = faceMetric(m, 2)
        for (side in doubleArrayOf(-1.0, 1.0)) {
            ellipse(x + (side * eyeHalf - eyeSize / 2) * scale, y + (eyeY - eyeSize / 2) * scale, eyeSize * scale, eyeSize * scale, INK)
            if (tired > 0.01) {
                face.rewind()
                face.moveTo((x + (side * eyeHalf - 4) * scale).toFloat(), (y + 3 * scale).toFloat())
                face.quadTo((x + side * eyeHalf * scale).toFloat(), (y + 1.5 * scale).toFloat(), (x + (side * eyeHalf + 4) * scale).toFloat(), (y + 3 * scale).toFloat())
                saved(alpha = tired * 0.4) { stroke(face, INK, 1.5 * scale) }
            }
        }
        val half = faceMetric(m, 3)
        val mouthY = faceMetric(m, 4)
        val depth = faceMetric(m, 5)
        face.rewind()
        face.moveTo((x - half * scale).toFloat(), (y + mouthY * scale).toFloat())
        face.quadTo(x.toFloat(), (y + (mouthY + depth) * scale).toFloat(), (x + half * scale).toFloat(), (y + mouthY * scale).toFloat())
        saved(alpha = 1 - tired * 0.3) { stroke(face, INK, faceMetric(m, 6) * scale) }
    }

    fun stage(s: M.Shown, still: Boolean) {
        saved(alpha = (1 - s.chooser) * if (s.origin > 0.5 && s.panel > 0) 0.0 else 1.0) {
            rect(127.5, 765.0, 138.0, 50.0, Color.WHITE, 25.0)
            blob(171.0, 790.0, 13.0, s.m)
            label(M.moods[M.nearest(s.m)].name, 220.0, 790.0, 10.0, 600)
        }
        saved(alpha = s.chooser) {
            prepare(Color.WHITE)
            paint.shader = MoodShapeMixerPaths.wash
            canvas.drawRect(0f, 0f, 393f, 852f, paint)
            paint.shader = null
            saved(alpha = 0.58 * (1 - s.panel * 0.4)) {
                val firstY = M.menuTrack(s.panel, 158.7, 186.3, 215.4)
                val spacing = M.menuTrack(s.panel, 89.0, 67.15, 36.2)
                val localScale = M.menuTrack(s.panel, 1.0, 0.96, 0.55).toFloat()
                val widths = doubleArrayOf(183.4, 209.6, 181.9, 186.3, 177.6)
                repeat(5) { index ->
                    saved {
                        canvas.translate(196.5f, (firstY + index * spacing).toFloat())
                        canvas.scale(localScale, localScale)
                        rect(-widths[index] / 2, -33.5, widths[index], 67.0, 0xA6FFFFFF.toInt(), 33.5)
                        saved {
                            canvas.translate(-45.5f, 0f)
                            stroke(MoodShapeMixerPaths.icons[index], colors[index], 1.5)
                        }
                        label(names[index], 21.5, 0.0, 12.0, 600, 0xFFAAA69C.toInt(), 0.2)
                    }
                }
            }
            saved(alpha = 1 - 0.9 * M.ease(M.segment(s.panel, 0.355, 0.8))) {
                label("HOW ARE YOU FEELING?", 196.5, M.menuTrack(s.panel, 599.0, 507.0, 374.0), 14.0, 700)
                label("Automatically match sounds based on mood", 196.5, M.menuTrack(s.panel, 624.0, 531.0, 396.0), 11.0, color = 0xFF99958A.toInt())
            }
            ellipse(172.5, 760.0, 48.0, 48.0, 0x99E7E1D5.toInt())
            stroke(MoodShapeMixerPaths.close, INK, 1.5)
        }
        if (s.closing) {
            val body = M.ease(M.segment(s.panel, 0.15, 1.0))
            saved(alpha = s.chooser * (1 - body)) {
                val itemWidth = M.itemWidth(s.m)
                rect(196.5 - itemWidth / 2, 652.0, itemWidth, 68.0, Color.WHITE, 34.0)
                inline(s.m, 0.0)
            }
            saved(alpha = s.panel) { picker(s, 1.0, still, if (s.panel > 0.001) body / s.panel else 0.0) }
        } else {
            saved(alpha = if (s.origin > 0.5 && s.panel > 0) 1.0 else s.chooser) { picker(s, s.panel, still) }
        }
    }

    private fun inline(mood: Double, origin: Double) {
        val itemWidth = M.itemWidth(mood)
        blob(M.mix(196.5 - itemWidth / 2 + 50.5, 171.0, origin), M.mix(686.0, 790.0, origin), M.mix(17.0, 13.0, origin), mood)
        label(M.moods[M.nearest(mood)].name, M.mix(216.0, 220.0, origin), M.mix(686.0, 790.0, origin), M.mix(13.0, 10.0, origin), 600, if (origin > 0.5) INK else 0xFF77746E.toInt())
        saved(alpha = 1 - origin) {
            val center = (196.5 + itemWidth / 2 - 28.5).toFloat()
            chevron.rewind()
            chevron.moveTo(center - 5, 682f)
            chevron.lineTo(center, 687f)
            chevron.lineTo(center + 5, 682f)
            stroke(chevron, 0xFFAAA69D.toInt(), 1.5)
        }
    }

    private fun picker(s: M.Shown, p: Double, still: Boolean, bodyAlpha: Double = 1.0) {
        val origin = if (s.closing) 0.0 else s.origin
        val itemWidth = M.itemWidth(s.m)
        val x = M.mix(M.mix(196.5 - itemWidth / 2, 127.5, origin), 10.2, p)
        val y = M.mix(M.mix(652.0, 765.0, origin), 418.0, p)
        val w = M.mix(M.mix(itemWidth, 138.0, origin), 372.6, p)
        val h = M.mix(M.mix(68.0, 50.0, origin), 306.0, p)
        val radius = minOf(h / 2, 56.0).toFloat()
        surface.rewind()
        surface.addRoundRect(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat(), radius, radius, Path.Direction.CW)
        val content = M.ease(M.segment(p, 0.35, 0.96))
        val q = 1 - (1 - p).pow(3)
        saved {
            canvas.clipPath(surface)
            saved(alpha = bodyAlpha) { fill(surface, Color.WHITE) }
            saved(alpha = 1 - content) { inline(s.m, origin) }
            saved(alpha = content) {
                saved(alpha = bodyAlpha) { rect(x, y + h - 68, w, 68.0, 0xFFFAF7F5.toInt()) }
                blob(M.mix(M.mix(196.5 - itemWidth / 2 + 50.5, 171.0, origin), 196.5, q), M.mix(M.mix(686.0, 790.0, origin), 563.0, q), M.mix(M.mix(17.0, 13.0, origin), 72.0, q), s.m)
                ellipse(54 + s.m * 47.8 - 14 * content, y + h - 35 - 14 * content, 28 * content, 28 * content, Color.WHITE)
            }
        }
        // The source title stays above the growing card; only face and footer are clipped.
        saved(alpha = content) {
            val step = floor(s.clock * 24).toInt()
            saved(alpha = 0.18) { label(M.title(s.m, step, ghost = true, still = still), 198.5, 464.0, 19.0, 700, tracking = 0.2) }
            label(M.title(s.m, step, still = still), 196.5, 459.0, 19.0, 700, tracking = 0.2)
        }

    }
}
