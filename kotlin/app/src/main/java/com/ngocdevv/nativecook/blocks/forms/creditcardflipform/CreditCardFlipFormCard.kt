package com.ngocdevv.nativecook.blocks.forms.creditcardflipform

import android.graphics.BlurMaskFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.ui.draw.drawBehind
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

// The card's fixed geometry, in dp from its top left.
internal const val CARD_RADIUS = 18f
internal const val CARD_PAD = 22f
internal const val NUMBER_AT = 0.52f
internal const val GROUP_GAP = 14f
internal const val NUMBER_LINE = 28f
internal const val BOTTOM_ROW = 56f
internal const val SMALL_LINE = 19f
internal const val CHIP_X = 22f
internal const val CHIP_Y = 62f
internal const val CHIP_W = 42f
internal const val CHIP_H = 31f
internal const val STRIPE_Y = 24f
internal const val STRIPE_H = 42f
internal const val STRIP_X = 22f
internal const val STRIP_Y = 92f
internal const val STRIP_H = 36f
internal const val CVV_W = 62f
internal const val CVV_H = 28f
// The new gradient spreads from the network's mark as a disc REVEAL_SOFT dp soft at the rim.
private const val REVEAL_SOFT = 26f
private const val GUILLOCHE_LINES = 15
// Brushed metal: BRUSH_LINES hairlines across the card at seeded heights, half lighter and half
// darker than the base, so the surface has a grain the light can catch.
private const val BRUSH_LINES = 120
private const val BRUSH_SEED = 7L
// The light that crosses the card: a band SHEEN_WIDTH dp wide, leaning SHEEN_TILT°.
private const val SHEEN_WIDTH = 90f
private const val SHEEN_TILT = -22f
// The soft specular light over the card, SPECULAR × the card's width in radius. It rests above the
// top left and slides against the card's tilt (SPECULAR_TRAVEL of the card per 10° of tilt).
private const val SPECULAR = 0.95f
private const val SPECULAR_TRAVEL = 0.5f
// The focus outline stands OUTLINE_PAD_X / OUTLINE_PAD_Y dp clear of what it frames, with
// OUTLINE_RADIUS corners; a comet of light runs round it, a bright head COMET_HEAD of the perimeter
// long over a dimmer tail COMET_TAIL long, once round every ORBIT s.
internal const val OUTLINE_PAD_X = 8f
internal const val OUTLINE_PAD_Y = 7f
private const val OUTLINE_RADIUS = 10f
private const val COMET_HEAD = 0.12f
private const val COMET_TAIL = 0.34f
internal const val ORBIT = 2.6

/** The focus outline drawn on one face: its frame (dp), how much of it shows, and where the comet is (0…1). */
internal class OutlineState(val rect: androidx.compose.ui.geometry.Rect, val on: Float, val orbit: Float, val dark: Boolean = false)

/** Blurred discs in dp units, with a radial falloff where mask filters are not drawn (API < 28). */
private class SoftDisc {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val blurs = HashMap<Float, BlurMaskFilter>()

    fun draw(scope: DrawScope, color: Color, alpha: Float, x: Float, y: Float, r: Float, blur: Float) {
        scope.drawIntoCanvas { canvas ->
            paint.shader = null
            paint.color = color.copy(alpha = alpha).toArgb()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                paint.maskFilter = blurs.getOrPut(blur) { BlurMaskFilter(sigmaToRadius(blur), BlurMaskFilter.Blur.NORMAL) }
                canvas.nativeCanvas.drawCircle(x, y, r, paint)
            } else {
                paint.maskFilter = null
                val c = color.copy(alpha = alpha).toArgb()
                paint.shader = RadialGradient(x, y, r + blur * 2, intArrayOf(c, c, 0), floatArrayOf(0f, (r - blur) / (r + blur * 2), 1f), Shader.TileMode.CLAMP)
                canvas.nativeCanvas.drawCircle(x, y, r + blur * 2, paint)
            }
        }
    }
}

/** Paints and paths for one face, made once (or once per size). */
private class ArtPaints(width: Float, height: Float) {
    val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    val comet = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    val chip = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            CHIP_X, CHIP_Y, CHIP_X + CHIP_W, CHIP_Y + CHIP_H,
            intArrayOf(0xFFF4E3B0.toInt(), 0xFFB8904A.toInt(), 0xFFEFD9A0.toInt(), 0xFF9C7634.toInt()), null, Shader.TileMode.CLAMP,
        )
    }
    val sheen = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(0f, 0f, SHEEN_WIDTH, 0f, intArrayOf(0x00FFFFFF, 0x38FFFFFF, 0x00FFFFFF), null, Shader.TileMode.CLAMP)
    }
    val shade = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(0f, height * 0.55f, 0f, height, 0x00000000, 0x38000000, Shader.TileMode.CLAMP)
    }
    val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        shader = LinearGradient(
            0f, 0f, 0f, height, intArrayOf(0x61FFFFFF, 0x0FFFFFFF, 0x40000000), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP,
        )
    }
    // The specular light, made once about the origin and moved with a local matrix each frame.
    val lightShader = RadialGradient(
        0f, 0f, width * SPECULAR, intArrayOf(0x33FFFFFF, 0x0FFFFFFF, 0x00FFFFFF), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP,
    )
    val lightMatrix = android.graphics.Matrix()
    val light = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = lightShader }
    // Each palette's diagonal gradient, made the first time it is drawn.
    val gradients = HashMap<List<Long>, LinearGradient>()
    val outline = android.graphics.Path()
    val piece = android.graphics.Path()
    val measure = android.graphics.PathMeasure()
    val bounds = android.graphics.RectF()
    val grain = brush(width, height)
}

/**
 * Everything drawn on one face of the card: its colour (spreading from the mark when the network
 * changes), brushed grain and guilloche, the light over it, the chip or the stripes, the light
 * crossing it, the focus outline and the edge.
 */
@Composable
internal fun CardArt(
    width: Float,
    height: Float,
    back: Boolean,
    from: () -> Network,
    to: () -> Network,
    mix: () -> Float,
    shine: () -> Float,
    tilt: () -> Pair<Float, Float>,
    outline: () -> OutlineState,
    origin: Offset,
    modifier: Modifier = Modifier,
) {
    val guilloche = remember(width, height) { guilloche(width, height) }
    val chipLines = remember { chipLines() }
    val disc = remember { SoftDisc() }
    val p = remember(width, height) { ArtPaints(width, height) }
    val mask = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) maskFilter = BlurMaskFilter(sigmaToRadius(REVEAL_SOFT / 2), BlurMaskFilter.Blur.NORMAL)
        }
    }
    val card = remember(width, height) {
        Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, 0f, width, height, CARD_RADIUS, CARD_RADIUS)) }
    }
    val far = hypot(max(origin.x, width - origin.x), max(origin.y, height - origin.y)) + REVEAL_SOFT * 2
    Canvas(modifier.size(width.dp, height.dp)) {
        scale(density, density, pivot = Offset.Zero) {
            drawIntoCanvas { it.save() }
            drawIntoCanvas { it.clipPath(card) }
            layer(this, from().palette, width, height, disc, p)
            val radius = mix() * far
            if (radius > 0.5f) {
                drawIntoCanvas { canvas ->
                    val native = canvas.nativeCanvas
                    val saved = native.saveLayer(0f, 0f, width, height, null)
                    layer(this, to().palette, width, height, disc, p)
                    native.drawCircle(origin.x, origin.y, radius, mask)
                    native.restoreToCount(saved)
                }
            }
            // Brushed grain, then the printed guilloche over it.
            drawPath(p.grain.first, Color.White.copy(alpha = 0.05f), style = Stroke(0.6f))
            drawPath(p.grain.second, Color.Black.copy(alpha = 0.09f), style = Stroke(0.6f))
            drawPath(guilloche, Color.White.copy(alpha = 0.07f), style = Stroke(0.7f))
            drawIntoCanvas { canvas ->
                val native = canvas.nativeCanvas
                // The light over the card (mirrored on the back, which is the front turned over),
                // and shade towards the bottom edge.
                val (tx, ty) = tilt()
                val side = if (back) -1f else 1f
                val lx = width * (if (back) 0.78f else 0.22f) - side * ty * width * SPECULAR_TRAVEL / 10f
                val ly = -height * 0.18f + tx * height * SPECULAR_TRAVEL / 10f
                p.lightMatrix.setTranslate(lx, ly)
                p.lightShader.setLocalMatrix(p.lightMatrix)
                native.drawRect(0f, 0f, width, height, p.light)
                native.drawRect(0f, height * 0.55f, width, height, p.shade)
            }
            if (back) {
                drawRect(Color(10, 10, 14).copy(alpha = 0.9f), Offset(0f, STRIPE_Y), Size(width, STRIPE_H))
                drawRoundRect(
                    Color(242, 238, 228).copy(alpha = 0.94f), Offset(STRIP_X, STRIP_Y), Size(width - STRIP_X * 2, STRIP_H),
                    androidx.compose.ui.geometry.CornerRadius(5f, 5f),
                )
            } else {
                drawIntoCanvas { it.nativeCanvas.drawRoundRect(CHIP_X, CHIP_Y, CHIP_X + CHIP_W, CHIP_Y + CHIP_H, 6f, 6f, p.chip) }
                drawPath(chipLines, Color(90, 60, 18).copy(alpha = 0.55f), style = Stroke(1f))
                drawRoundRect(
                    Color(255, 246, 214).copy(alpha = 0.5f), Offset(CHIP_X + 0.5f, CHIP_Y + 0.5f), Size(CHIP_W - 1, CHIP_H - 1),
                    androidx.compose.ui.geometry.CornerRadius(5.5f, 5.5f), style = Stroke(1f),
                )
            }
            drawIntoCanvas { canvas ->
                val native = canvas.nativeCanvas
                // The light: a band leaning SHEEN_TILT°, swept across from out of sight to out of sight.
                native.save()
                native.translate(-SHEEN_WIDTH * 2 + (width + SHEEN_WIDTH * 4) * shine(), height / 2)
                native.rotate(SHEEN_TILT)
                native.translate(0f, -height / 2)
                native.drawRect(0f, -height, SHEEN_WIDTH, height * 2, p.sheen)
                native.restore()
                drawOutline(native, outline(), p)
                // The card's edge: a hairline of light along the top, darker along the bottom.
                native.drawRoundRect(0.5f, 0.5f, width - 0.5f, height - 0.5f, CARD_RADIUS - 0.5f, CARD_RADIUS - 0.5f, p.edge)
            }
            drawIntoCanvas { it.restore() }
        }
    }
}

/** The focus outline: a fine frame of light with a comet running round it at an even speed. */
private fun drawOutline(canvas: android.graphics.Canvas, o: OutlineState, p: ArtPaints) {
    if (o.on <= 0.002f) return
    val r = o.rect
    p.bounds.set(r.left, r.top, r.left + max(1f, r.width), r.top + max(1f, r.height))
    p.outline.rewind()
    p.outline.addRoundRect(p.bounds, OUTLINE_RADIUS, OUTLINE_RADIUS, android.graphics.Path.Direction.CW)
    p.fill.shader = null
    // Light on the card's colour; dark ink on the cream signature strip (the CVV).
    val ink = if (o.dark) Color(0xFF1D2340) else Color.White
    p.fill.color = ink.copy(alpha = 0.06f * o.on).toArgb()
    canvas.drawPath(p.outline, p.fill)
    p.stroke.strokeWidth = 1f
    p.stroke.color = ink.copy(alpha = (if (o.dark) 0.3f else 0.34f) * o.on).toArgb()
    canvas.drawPath(p.outline, p.stroke)
    p.measure.setPath(p.outline, true)
    val length = p.measure.length
    fun comet(part: Float, width: Float, alpha: Float) {
        val start = o.orbit - part
        p.comet.strokeWidth = width
        p.comet.color = ink.copy(alpha = alpha * o.on).toArgb()
        val pieces = if (start >= 0) listOf(start to o.orbit) else listOf(start + 1 to 1f, 0f to o.orbit)
        for ((a, b) in pieces) {
            if (b <= a) continue
            p.piece.rewind()
            p.measure.getSegment(a * length, b * length, p.piece, true)
            canvas.drawPath(p.piece, p.comet)
        }
    }
    comet(COMET_TAIL, 1.5f, 0.4f)
    comet(COMET_HEAD, 1.75f, 1f)
}

/** The card's colour: a diagonal gradient and two soft pools of the palette's light colour. */
private fun layer(scope: DrawScope, palette: List<Long>, w: Float, h: Float, disc: SoftDisc, p: ArtPaints) {
    scope.drawIntoCanvas { canvas ->
        val paint = p.fill
        paint.shader = p.gradients.getOrPut(palette) { LinearGradient(0f, 0f, w, h, palette[0].toInt(), palette[1].toInt(), Shader.TileMode.CLAMP) }
        paint.color = 0xFF000000.toInt()
        canvas.nativeCanvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null
    }
    val glow = Color(palette[2])
    disc.draw(scope, glow, 0.26f, w * 0.92f, -h * 0.12f, h * 0.62f, 42f)
    disc.draw(scope, glow, 0.18f, w * 0.04f, h * 1.08f, h * 0.5f, 36f)
}

/** The brushed grain: hairlines at heights from a small linear congruential generator, the same on every platform. */
private fun brush(w: Float, h: Float): Pair<Path, Path> {
    var seed = BRUSH_SEED
    fun next(): Float {
        seed = (seed * 1_664_525L + 1_013_904_223L) and 0xFFFFFFFFL
        return (seed.toDouble() / 4_294_967_296.0).toFloat()
    }
    val light = Path()
    val dark = Path()
    repeat(BRUSH_LINES) {
        val y = next() * h
        val target = if (next() < 0.5f) light else dark
        target.moveTo(0f, y)
        target.lineTo(w, y)
    }
    return light to dark
}

/** The fine wavy lines printed across the card, like the guilloche on a banknote. */
private fun guilloche(w: Float, h: Float): Path {
    val path = Path()
    for (k in 0 until GUILLOCHE_LINES) {
        val base = h * 0.3f + k * 6.5f
        var x = -4f
        while (x <= w + 4) {
            val y = base + 10 * sin(x / 26 + k * 0.42f) + 4 * sin(x / 9.5f - k * 0.8f)
            if (x == -4f) path.moveTo(x, y) else path.lineTo(x, y)
            x += 4
        }
    }
    return path
}

private fun chipLines(): Path = Path().apply {
    val x = CHIP_X
    val y = CHIP_Y
    val w = CHIP_W
    val h = CHIP_H
    moveTo(x, y + h / 3); lineTo(x + w * 0.34f, y + h / 3)
    moveTo(x, y + 2 * h / 3); lineTo(x + w * 0.34f, y + 2 * h / 3)
    moveTo(x + w, y + h / 3); lineTo(x + w * 0.66f, y + h / 3)
    moveTo(x + w, y + 2 * h / 3); lineTo(x + w * 0.66f, y + 2 * h / 3)
    addRoundRect(androidx.compose.ui.geometry.RoundRect(x + w * 0.34f, y + h * 0.18f, x + w * 0.66f, y + h * 0.82f, 3f, 3f))
}

// MARK: - Reel

/** One character of the number, the expiry or the CVV: the window onto its reel. */
@Composable
internal fun ReelView(
    roll: () -> Double,
    speed: () -> Double,
    placeholder: String,
    width: Float,
    line: Float,
    style: TextStyle,
    shadow: Boolean = false,
) {
    val measurer = rememberTextMeasurer(cacheSize = 24)
    val glyphs = remember(placeholder, style) {
        List(21) { j -> measurer.measure(if (j == 0) placeholder else ((j - 1) % 10).toString(), style.copy(textAlign = TextAlign.Center)) }
    }
    Canvas(
        Modifier
            .size(width.dp, line.dp)
            .graphicsLayer {
                clip = true
                alpha = 1 - (1 - Reel.MOVING_ALPHA) * speed().toFloat()
            },
    ) {
        val r = roll()
        val index = floor(r).toInt()
        val lineHeight = line.dp.toPx()
        for (j in listOf(index, index + 1)) {
            if (j !in 0..20) continue
            val glyph = glyphs[j]
            val x = (size.width - glyph.size.width) / 2
            val y = ((j - r) * lineHeight + lineHeight / 2).toFloat() - glyph.size.height / 2
            if (shadow) drawText(glyph, color = Color.Black.copy(alpha = 0.25f), topLeft = Offset(x, y + 1.dp.toPx()), alpha = 0.6f)
            drawText(glyph, topLeft = Offset(x, y))
        }
    }
}

// MARK: - Network mark

private val MarkInk = Color.White.copy(alpha = 0.95f)

/**
 * The network's mark: a small drawn emblem and its name, white on the card; before a digit is
 * typed, an empty dashed slot where the mark will go.
 */
@Composable
internal fun NetworkMark(network: Network, modifier: Modifier = Modifier) {
    if (network == Network.None) {
        Canvas(modifier.size(64.dp, 22.dp)) {
            val stroke = 0.7.dp.toPx()
            drawRoundRect(
                Color.White.copy(alpha = 0.22f),
                topLeft = Offset(stroke / 2, stroke / 2),
                size = Size(size.width - stroke, size.height - stroke),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2),
                style = Stroke(stroke, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 2.dp.toPx()))),
            )
        }
        return
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(26.dp, 24.dp)) {
            scale(density, density, pivot = Offset.Zero) { drawMark(network) }
        }
        Box(Modifier.size(6.dp))
        Text(
            network.title.uppercase(),
            color = MarkInk,
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold,
            fontStyle = FontStyle.Italic,
            letterSpacing = 1.6.sp,
        )
    }
}

private fun DrawScope.drawMark(network: Network) {
    when (network) {
        Network.Vela -> drawPath(
            Path().apply {
                moveTo(3f, 21f); lineTo(13f, 2f); lineTo(13f, 21f); close()
                moveTo(15.5f, 21f); lineTo(15.5f, 8f); lineTo(24f, 21f); close()
            },
            MarkInk,
        )
        Network.Kestrel -> drawPath(
            Path().apply {
                moveTo(2f, 7f); lineTo(13f, 17f); lineTo(24f, 7f)
                moveTo(6f, 3f); lineTo(13f, 10f); lineTo(20f, 3f)
            },
            MarkInk,
            style = Stroke(2.6f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        Network.Orrery -> {
            drawCircle(MarkInk, 8.5f, Offset(12f, 12f), style = Stroke(2.2f))
            drawCircle(MarkInk, 3f, Offset(12f, 12f))
            drawCircle(MarkInk, 2.6f, Offset(19.6f, 6.2f))
        }
        Network.Nimbus -> {
            val p = Path()
            p.moveTo(3f, 18f)
            arc(p, Offset(3f, 18f), Offset(8f, 11f), 5f)
            arc(p, Offset(8f, 11f), Offset(20f, 10f), 6.5f)
            arc(p, Offset(20f, 10f), Offset(23f, 18f), 4.5f)
            p.close()
            drawPath(p, MarkInk, style = Stroke(2.2f, join = StrokeJoin.Round))
        }
        Network.None -> Unit
    }
}

/** An SVG arc (small, clockwise on screen) from [a] to [b], added as line segments. */
private fun arc(path: Path, a: Offset, b: Offset, radius: Float) {
    val mx = (a.x + b.x) / 2
    val my = (a.y + b.y) / 2
    val dx = b.x - a.x
    val dy = b.y - a.y
    val chord = hypot(dx, dy)
    val r = max(radius, chord / 2)
    val h = sqrt(max(0f, r * r - chord * chord / 4))
    // For a small arc swept clockwise on screen (y down) the centre lies to the right of a → b.
    val cx = mx - dy / chord * h
    val cy = my + dx / chord * h
    val start = atan2(a.y - cy, a.x - cx)
    var end = atan2(b.y - cy, b.x - cx)
    while (end < start) end += (2 * PI).toFloat()
    for (i in 1..16) {
        val t = start + (end - start) * i / 16
        path.lineTo(cx + r * cos(t), cy + r * sin(t))
    }
}

/** The contactless mark next to the chip: three arcs, like the "wave.3.right" symbol. */
@Composable
internal fun Contactless(modifier: Modifier = Modifier) {
    Canvas(modifier.size(17.dp)) {
        scale(density, density, pivot = Offset.Zero) {
            val ink = Color.White.copy(alpha = 0.75f)
            val stroke = Stroke(1.5f, cap = StrokeCap.Round)
            for ((i, r) in listOf(3.2f, 6.4f, 9.6f).withIndex()) {
                val sweep = 70f + i * 8f
                drawArc(ink, -sweep / 2, sweep, false, Offset(2f - r + 2f, 8.5f - r), Size(r * 2, r * 2), style = stroke)
            }
        }
    }
}
