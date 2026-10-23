package com.ngocdevv.nativecook.blocks.forms.rideheightfilter

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathHitTester
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.sigmaToRadius
import com.ngocdevv.nativecook.shared.surface
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.sin

const val BOX_WIDTH = 496f
const val BOX_HEIGHT = 250f
private const val GROUND = 216f
private const val WHEEL_X = 422f
private const val WHEEL_Y = 155f
private const val TYRE = 61f
private const val RIM = 49f
private const val FOG_X = 289f
private const val FOG_Y = 151f
private const val FOG_RADIUS = 10f
private const val SPOKES = 5
private const val SPOKE_TURN = -95f
private const val LEAVES = 520
private const val RAGGED = 3f
private const val SHADOW_FADE = 0.45f
private val LeafTones = listOf(Color(0xFFC1BFC2), Color(0xFFD2D0D3), Color(0xFFE1DFE2), Color(0xFFF0EEF1))
private val Body = PathParser().parsePathString("M500 36C470 38 440 42 410 48C370 56 330 64 300 74C288 78 279 84 272 92C266 104 260 120 256 140C254 152 251 162 249 171L245.5 174.5L250 179.5C280 184 320 188 350 191.5L356.2 181.9A70 70 0 1 1 491.3 167.7L492 188L500 188Z").toPath()
private val ArchEdge = PathParser().parsePathString("M356.2 181.9A70 70 0 1 1 491.3 167.7").toPath()
private val Liner = PathParser().parsePathString("M356.2 181.9A70 70 0 1 1 491.3 167.7L492 188L354 188Z").toPath()
private val Shoulder = PathParser().parsePathString("M500 36C470 38 440 42 410 48C370 56 330 64 300 74C288 78 279 84 272 92C300 88 330 84 352 79C400 70 450 64 500 60Z").toPath()
private val Crease = PathParser().parsePathString("M352 79C400 70 450 64 500 60").toPath()
private val BumperLine = PathParser().parsePathString("M262 131C290 128 328 120 354 104").toPath()
private val Underside = PathParser().parsePathString("M246 176C280 184 320 188 352 192").toPath()
private val Splitter = PathParser().parsePathString("M249 171L245.5 174.5L250 179.5C280 184 320 188 350 191.5L351.5 186C320 182.5 285 178 257 172.5Z").toPath()
private val Nose = PathParser().parsePathString("M272 92C266 104 260 120 256 140C254 152 251 162 249 171L275 176C278 150 286 118 300 92Z").toPath()
private val Intake = PathParser().parsePathString("M265 142L309 132C312 131.4 314 132.6 314.8 135L321 159C321.6 161.4 321 163.6 319.4 165.4L313.6 172C312 173.8 309.8 174.4 307.6 173.6L270 161C267.6 160.2 266.2 158.4 266 156L264.4 144.6C264.2 143.2 264.2 142.2 265 142Z").toPath()
private val Lens = PathParser().parsePathString("M277 97C295 94 318 90 334 86C341 84 347 81 352.5 78.5L357.5 77.5C352 86 346 95 340 102.5C322 104 300 105 281 105.5C277 104 276 100 277 97Z").toPath()
private val LensBrow = PathParser().parsePathString("M277 97C295 94 318 90 334 86C341 84 347 81 352.5 78.5").toPath()
private val Headlight = PathParser().parsePathString("M331 104C338 96 346 86 352.5 78.5L357.5 77.5C352 86 346 95 340 102.5Z").toPath()
private val HeadlightLine = PathParser().parsePathString("M336 100C342 93 348 85 353.5 79").toPath()
private val Mound = PathParser().parsePathString("M-4 134C-6 112 2 92 20 84C28 68 52 60 66 70C82 62 104 70 108 88C122 94 128 114 124 134Z").toPath()

private val Slats = Path().apply {
    var y = 99.0
    while (y <= 160.0) {
        val x = 268.0 - (y - 99.0) * 0.26
        moveTo((x + 1.5).toFloat(), y.toFloat()); lineTo((x + 8.5).toFloat(), (y - 0.6).toFloat())
        y += 3.4
    }
}
private val Mesh = Path().apply {
    var x = 220.0
    while (x < 330.0) {
        moveTo(x.toFloat(), 180f); lineTo((x + 52.0).toFloat(), 128f)
        moveTo((x + 52.0).toFloat(), 180f); lineTo(x.toFloat(), 128f)
        x += 3.6
    }
}
private val SpokePath = Path().apply {
    for (i in 0 until SPOKES) {
        val at = (SPOKE_TURN + i * 360.0 / SPOKES) * Math.PI / 180.0
        fun point(radius: Double, offset: Double, twist: Double): Offset {
            val angle = at + twist
            return Offset((cos(angle) * radius - sin(angle) * offset).toFloat(), (sin(angle) * radius + cos(angle) * offset).toFloat())
        }
        val a = point(8.0, -6.5, 0.0); val b = point(46.0, -11.5, 0.1)
        val c = point(46.0, 11.5, 0.1); val d = point(8.0, 6.5, 0.0)
        moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y); lineTo(d.x, d.y); close()
    }
}
private val SpokeEdges = Path().apply {
    for (i in 0 until SPOKES) {
        val at = (SPOKE_TURN + i * 360.0 / SPOKES) * Math.PI / 180.0
        val twisted = at + 0.1
        moveTo((cos(at) * 10.0 - sin(at) * 6.5).toFloat(), (sin(at) * 10.0 + cos(at) * 6.5).toFloat())
        lineTo((cos(twisted) * 45.0 - sin(twisted) * 11.5).toFloat(), (sin(twisted) * 45.0 + cos(twisted) * 11.5).toFloat())
    }
}

/** The same fixed integer sequence and fractional mound test build all leaf paths once. */
private val LeafPaths = run {
    val builders = List(LeafTones.size) { Path() }
    val hit = PathHitTester().apply { updatePath(Mound, 0.001f) }
    var seed = 7L
    fun next(): Double { seed = seed * 16807L % 2147483647L; return seed.toDouble() / 2147483647.0 }
    repeat(LEAVES) {
        val x = -4.0 + next() * 130.0; val y = 62.0 + next() * 74.0
        val turn = next() * Math.PI
        val long = 3.2 + next() * 3.2; val wide = long * (0.36 + next() * 0.14)
        val shade = next()
        if (!hit.contains(Offset(x.toFloat(), y.toFloat())) && !hit.contains(Offset(x.toFloat(), (y + RAGGED).toFloat()))) return@repeat
        val light = 0.62 - (x - 60.0) / 260.0 - (y - 98.0) / 110.0 + (shade - 0.5) * 0.9
        val tone = floor(light * LeafTones.size).toInt().coerceIn(0, LeafTones.lastIndex)
        val c = cos(turn); val s = sin(turn)
        builders[tone].apply {
            moveTo((x - c * long).toFloat(), (y - s * long).toFloat())
            quadraticTo((x - s * wide * 2.0).toFloat(), (y + c * wide * 2.0).toFloat(), (x + c * long).toFloat(), (y + s * long).toFloat())
            quadraticTo((x + s * wide * 2.0).toFloat(), (y - c * wide * 2.0).toFloat(), (x - c * long).toFloat(), (y - s * long).toFloat()); close()
        }
    }
    builders
}
private val Kerb = Path().apply { addRoundRect(RoundRect(-12f, 174f, 219f, 218f, CornerRadius(9f))) }
private val PlanterSide = PathParser().parsePathString("M132 132L139 130L139 178L132 182Z").toPath()
private val TyreDash = PathEffect.dashPathEffect(floatArrayOf(1.6f, 4.54f))

/** Fixed mask filters are cached; the continuously changing ground blur uses one mutable paint. */
private class CarPaint {
    private data class Key(val color: Int, val sigma: Float, val width: Float)
    private val paints = mutableMapOf<Key, Paint>()
    private val ground = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.Black.toArgb() }
    private var groundSigma = -1f
    private fun paint(color: Color, sigma: Float, width: Float) = paints.getOrPut(Key(color.toArgb(), sigma, width)) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color.copy(alpha = 1f).toArgb(); style = if (width == 0f) Paint.Style.FILL else Paint.Style.STROKE
            strokeWidth = width; strokeJoin = Paint.Join.MITER; strokeCap = Paint.Cap.BUTT
            if (sigma > 0f && Build.VERSION.SDK_INT >= 28) maskFilter = BlurMaskFilter(sigmaToRadius(sigma), BlurMaskFilter.Blur.NORMAL)
        }
    }
    fun path(canvas: android.graphics.Canvas, path: Path, color: Color, sigma: Float, width: Float = 0f, round: Boolean = false) {
        val p = paint(color, sigma, width)
        p.strokeCap = if (round) Paint.Cap.ROUND else Paint.Cap.BUTT
        if (Build.VERSION.SDK_INT >= 28 || sigma == 0f) {
            p.alpha = (color.alpha * 255f).toInt(); canvas.drawPath(path.asAndroidPath(), p)
        } else {
            if (width == 0f) { p.style = Paint.Style.FILL; p.alpha = (color.alpha * 0.8f * 255f).toInt(); canvas.drawPath(path.asAndroidPath(), p) }
            p.style = Paint.Style.STROKE
            for (layer in 6 downTo 1) {
                val outer = layer * 0.5f; val inner = (layer - 1) * 0.5f
                val weight = (if (width == 0f) 0.2f else 1f) * (exp(-inner * inner / 2f) - exp(-outer * outer / 2f))
                p.strokeWidth = width + 2f * outer * sigma; p.alpha = (color.alpha * weight * 255f).toInt()
                canvas.drawPath(path.asAndroidPath(), p)
            }
        }
    }
    fun oval(canvas: android.graphics.Canvas, x: Float, y: Float, w: Float, h: Float, color: Color, sigma: Float, alpha: Float = 1f, dynamic: Boolean = false) {
        val p = if (dynamic) ground else paint(color, sigma, 0f)
        if (dynamic && Build.VERSION.SDK_INT >= 28 && sigma != groundSigma) { groundSigma = sigma; p.maskFilter = BlurMaskFilter(sigmaToRadius(sigma), BlurMaskFilter.Blur.NORMAL) }
        if (Build.VERSION.SDK_INT >= 28 || sigma == 0f) {
            p.alpha = (color.alpha * alpha * 255f).toInt(); canvas.drawOval(x, y, x + w, y + h, p)
        } else for (layer in 6 downTo 1) {
            val outer = layer * 0.5f; val inner = (layer - 1) * 0.5f
            val weight = 0.5f * (exp(-inner * inner / 2f) - exp(-outer * outer / 2f))
            val spread = outer * sigma
            p.alpha = (color.alpha * alpha * weight * 255f).toInt(); canvas.drawOval(x - spread, y - spread, x + w + spread, y + h + spread, p)
        }
    }
    fun rect(canvas: android.graphics.Canvas, x: Float, y: Float, w: Float, h: Float, color: Color, sigma: Float) {
        val p = paint(color, sigma, 0f)
        if (Build.VERSION.SDK_INT >= 28) { p.alpha = (color.alpha * 255f).toInt(); canvas.drawRect(x, y, x + w, y + h, p) }
        else for (layer in 6 downTo 1) {
            val outer = layer * 0.5f; val inner = (layer - 1) * 0.5f
            val spread = outer * sigma; val weight = 0.5f * (exp(-inner * inner / 2f) - exp(-outer * outer / 2f))
            p.alpha = (color.alpha * weight * 255f).toInt(); canvas.drawRect(x - spread, y - spread, x + w + spread, y + h + spread, p)
        }
    }
    fun circle(canvas: android.graphics.Canvas, x: Float, y: Float, r: Float, color: Color, sigma: Float, width: Float = 0f) {
        val p = paint(color, sigma, width)
        if (Build.VERSION.SDK_INT >= 28 || sigma == 0f) { p.alpha = (color.alpha * 255f).toInt(); canvas.drawCircle(x, y, r, p) }
        else for (layer in 6 downTo 1) {
            val outer = layer * 0.5f; val inner = (layer - 1) * 0.5f
            val weight = 0.5f * (exp(-inner * inner / 2f) - exp(-outer * outer / 2f))
            p.alpha = (color.alpha * weight * 255f).toInt()
            if (width > 0f) p.strokeWidth = width + outer * sigma * 2f
            canvas.drawCircle(x, y, r + if (width == 0f) outer * sigma else 0f, p)
        }
    }
}

/** The body rides on its suspension; the bush, kerb and wheel keep their source coordinates. */
@Composable
fun CarPicture(width: Float, lift: State<Float>, pitch: State<Float>, maxLift: Float, modifier: Modifier = Modifier) {
    val scale = width / BOX_WIDTH
    val paint = remember { CarPaint() }
    Canvas(modifier.size(width.dp, (BOX_HEIGHT * scale).dp).clipToBounds().clearAndSetSemantics {}) {
        surface(0.dp) { _, _, _, _ -> scale(scale, scale, pivot = Offset.Zero) {
            drawPath(Mound, Color(0xFFAEACAF))
            for ((index, path) in LeafPaths.withIndex()) drawPath(path, LeafTones[index])
            drawRect(Brush.verticalGradient(listOf(Color(0xFFD9D7DA), Color(0xFFCFCDD0)), startY = 132f, endY = 182f), Offset(-2f, 132f), Size(134f, 50f))
            drawPath(PlanterSide, Color(0xFFC1BFC2)); drawRect(Color(0xFFECEBEE), Offset(-2f, 131f), Size(134f, 2.2f))
            drawIntoCanvas { paint.rect(it.nativeCanvas, -4f, 176f, 146f, 7f, Color.Black.copy(alpha = 0.16f), 3f); paint.rect(it.nativeCanvas, -10f, GROUND, 232f, 3f, Color.Black.copy(alpha = 0.3f), 2.5f) }
            drawPath(Kerb, Brush.verticalGradient(0f to Color(0xFFEAE8EB), 0.45f to Color(0xFFDDDBDE), 1f to Color(0xFFC8C6C9), startY = 174f, endY = 218f))
            clipPath(Kerb) {
                drawRect(Color.Black.copy(alpha = 0.045f), Offset(196f, 174f), Size(24f, 44f))
                drawRect(Color.Black.copy(alpha = 0.13f), Offset(195.4f, 174f), Size(1.1f, 44f))
                drawRect(Color.White.copy(alpha = 0.7f), Offset(-12f, 174.6f), Size(231f, 1.4f))
            }
            val height = (lift.value / maxLift).coerceIn(0f, 1f)
            drawIntoCanvas { paint.oval(it.nativeCanvas, 262f, 208f, 250f, 13f, Color.Black.copy(alpha = 0.36f), 5f + 4f * height, 1f - SHADOW_FADE * height, dynamic = true); paint.oval(it.nativeCanvas, 384f, 211f, 82f, 8f, Color.Black.copy(alpha = 0.3f), 3f) }
            withTransform({ translate(0f, -lift.value); rotate(pitch.value * 180f / Math.PI.toFloat(), Offset(WHEEL_X, WHEEL_Y)) }) {
                drawPath(Liner, Brush.verticalGradient(0f to Color(0xFF313133), 0.55f to Color(0xFF48484A), 1f to Color(0xFF757376), startY = 88f, endY = 190f))
                drawPath(Body, Brush.verticalGradient(0f to Color(0xFFE9E7EA), 0.3f to Color(0xFFE3E1E4), 0.6f to Color(0xFFDEDCDF), 1f to Color(0xFFD1CFD2), startY = 36f, endY = 192f))
                clipPath(Body) {
                    drawIntoCanvas { paint.path(it.nativeCanvas, Shoulder, Color(0xFFF4F2F5), 2.5f) }
                    withTransform({ translate(0f, 4f) }) { drawIntoCanvas { paint.path(it.nativeCanvas, Crease, Color.Black.copy(alpha = 0.07f), 3f, 5f) } }
                    drawIntoCanvas { paint.path(it.nativeCanvas, Nose, Color.Black.copy(alpha = 0.05f), 7f) }
                    withTransform({ translate(0f, 3.5f) }) { drawIntoCanvas { paint.path(it.nativeCanvas, BumperLine, Color.Black.copy(alpha = 0.085f), 3f, 5f) } }
                    drawIntoCanvas {
                        paint.path(it.nativeCanvas, BumperLine, Color.White.copy(alpha = 0.75f), 0.7f, 1.4f, true)
                        paint.path(it.nativeCanvas, Underside, Color.Black.copy(alpha = 0.1f), 5f, 12f)
                        paint.path(it.nativeCanvas, ArchEdge, Color.Black.copy(alpha = 0.11f), 3f, 9f)
                        paint.path(it.nativeCanvas, ArchEdge, Color.White.copy(alpha = 0.55f), 0.6f, 2.2f)
                    }
                }
                drawPath(Slats, Color.Black.copy(alpha = 0.2f), style = Stroke(1.3f, cap = StrokeCap.Round))
                drawPath(Intake, Brush.verticalGradient(listOf(Color(0xFFA3A1A4), Color(0xFFB9B7BA)), startY = 132f, endY = 174f))
                clipPath(Intake) { drawPath(Mesh, Color.White.copy(alpha = 0.42f), style = Stroke(0.8f)); drawIntoCanvas { paint.path(it.nativeCanvas, Intake, Color.Black.copy(alpha = 0.2f), 2.5f, 5f) } }
                drawPath(Intake, Color.White.copy(alpha = 0.7f), style = Stroke(1.2f))
                drawIntoCanvas { paint.circle(it.nativeCanvas, FOG_X, FOG_Y + 1.2f, FOG_RADIUS + 1f, Color.Black.copy(alpha = 0.22f), 2f) }
                drawCircle(Brush.radialGradient(listOf(Color(0xFFFBF9FC), Color(0xFFDEDCDF)), center = Offset(FOG_X - 3f, FOG_Y - 4f), radius = FOG_RADIUS * 1.6f), FOG_RADIUS, Offset(FOG_X, FOG_Y))
                drawCircle(Color.Black.copy(alpha = 0.1f), FOG_RADIUS - 3.2f, Offset(FOG_X, FOG_Y), style = Stroke(1f))
                drawPath(Lens, Color(0xFFECEBEE))
                clipPath(Lens) { drawIntoCanvas { paint.path(it.nativeCanvas, LensBrow, Color.Black.copy(alpha = 0.16f), 2.5f, 7f) } }
                drawPath(Lens, Color.Black.copy(alpha = 0.1f), style = Stroke(0.8f, join = StrokeJoin.Round))
                drawPath(Headlight, Color(0xFFF9F7FA)); drawPath(Headlight, Color.Black.copy(alpha = 0.16f), style = Stroke(1f, join = StrokeJoin.Round))
                drawPath(HeadlightLine, Color.Black.copy(alpha = 0.14f), style = Stroke(0.9f))
                drawPath(Splitter, Color(0xFFEFEDF0)); drawPath(Splitter, Color.Black.copy(alpha = 0.12f), style = Stroke(0.8f, join = StrokeJoin.Round))
            }
            withTransform({ translate(WHEEL_X, WHEEL_Y) }) {
                drawCircle(Brush.radialGradient(0f to Color(0xFFF1EFF2), 0.6f to Color(0xFFE2E0E3), 1f to Color(0xFFC9C7CA), center = Offset(-16f, -26f), radius = TYRE * 1.5f), TYRE, Offset.Zero)
                drawCircle(Color.Black.copy(alpha = 0.075f), TYRE - 2.4f, Offset.Zero, style = Stroke(3.6f, pathEffect = TyreDash))
                drawIntoCanvas { paint.circle(it.nativeCanvas, 0f, 0f, RIM + 1f, Color.Black.copy(alpha = 0.14f), 1f, 1.6f) }
                drawCircle(Brush.radialGradient(0f to Color(0xFFB4B2B5), 0.58f to Color(0xFFABA9AC), 0.62f to Color(0xFF8B898C), 1f to Color(0xFF7E7C7F), center = Offset.Zero, radius = RIM - 2f), RIM - 2f, Offset.Zero)
                drawPath(SpokePath, Brush.radialGradient(listOf(Color(0xFFF4F2F5), Color(0xFFE1DFE2)), center = Offset(-10f, -16f), radius = RIM * 1.5f))
                drawIntoCanvas { paint.path(it.nativeCanvas, SpokeEdges, Color.Black.copy(alpha = 0.14f), 0.8f, 1.6f) }
                drawCircle(Brush.radialGradient(listOf(Color(0xFFF5F3F6), Color(0xFFDCDADD)), center = Offset(-14f, -22f), radius = RIM * 1.6f), RIM - 2.5f, Offset.Zero, style = Stroke(5f))
                drawCircle(Brush.radialGradient(listOf(Color(0xFFF3F1F4), Color(0xFFD9D7DA)), center = Offset(-3f, -5f), radius = 16f), 12.5f, Offset.Zero)
                for (index in 0 until SPOKES) {
                    val at = (SPOKE_TURN + 36.0 + index * 360.0 / SPOKES) * Math.PI / 180.0
                    drawCircle(Color(0xFFB3B1B4), 1.7f, Offset((cos(at) * 8.0).toFloat(), (sin(at) * 8.0).toFloat()))
                }
                drawCircle(Color(0xFFCFCDD0), 3.6f, Offset.Zero)
            }
        } }
    }
}
