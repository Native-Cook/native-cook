package com.ngocdevv.nativecook.blocks.media.scattergridalbum

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.os.Build
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

// Colours of Scatter Grid Album.

internal val ScreenColor = Color.White

// The gradient: a near-white base with a blue light in the middle and a mint one low on the right;
// folded into the card they make its blue-grey middle, as in the design.
internal val BaseColor = Color(0xFFF4F7FA)
internal val BlueColor = Color(0xFFB2C6DE)
internal val MintColor = Color(0xFFC3E0CF)

/** A photo still loading. */
internal val PlaceholderColor = Color(0xFFE6E9EE)
internal val IconColor = Color(0xFF9AA0A8)
internal val HeartColor = Color(0xFFFF4D6A)
internal val YearColor = Color.White.copy(alpha = 0.85f)

/** The heading's very soft shadow (Swift draws it on the name and again on the heading: twice on the name). */
internal val HeadingShadow = Color.Black.copy(alpha = 0.06f)
internal const val HEADING_SHADOW_RADIUS = 12f

// A photo's shadow; a photo in its cell keeps CELL_SHADOW of it.
private val PhotoShadow = Color.Black.copy(alpha = 0.18f)
private const val PHOTO_SHADOW_SIGMA = 18f
private const val PHOTO_SHADOW_Y = 8f
private const val CELL_SHADOW = 0.3f

internal val CardShadow = Color.Black.copy(alpha = 0.16f)
internal const val CARD_SHADOW_SIGMA = 30f
internal const val CARD_SHADOW_Y = 16f

// The soft shadow under each icon, as in the design.
private val IconShadow = Color.Black.copy(alpha = 0.14f)
private const val ICON_SHADOW_SIGMA = 3f
private const val ICON_SHADOW_Y = 4f

/**
 * One light of the gradient, in its frame's coordinates (so it squashes with the frame into the card):
 * centre and radii as shares of the frame's width and height. It is full colour out to [GLOW_CORE] of
 * its radius, then fades out.
 */
private class Glow(val color: Color, val x: Float, val y: Float, val rx: Float, val ry: Float)

private val Glows = listOf(
    Glow(BlueColor, 0.3f, 0.45f, 0.85f, 0.34f),
    Glow(MintColor, 0.72f, 0.64f, 0.6f, 0.28f),
)
private const val GLOW_CORE = 0.15f

// The gradient brushes are drawn in a unit circle and scaled to each light's radii.
private val GlowBrushes = Glows.map {
    Brush.radialGradient(
        0f to it.color,
        GLOW_CORE to it.color,
        1f to it.color.copy(alpha = 0f),
        center = Offset.Zero,
        radius = 1f,
    )
}

private fun roundRect(r: Area, radius: Double) = RoundRect(
    r.x.toFloat(), r.y.toFloat(), r.maxX.toFloat(), r.maxY.toFloat(), CornerRadius(radius.toFloat()),
)

/** The gradient in its moving frame, clipped to it (in dp, inside a density scale). */
internal fun DrawScope.drawCardGradient(pose: CardPose, paints: ScatterGridAlbumPaints) {
    val rect = pose.rect
    val shape = paints.clip.apply {
        reset()
        addRoundRect(roundRect(rect, pose.radius))
    }
    clipPath(shape) {
        drawRect(BaseColor, Offset(rect.x.toFloat(), rect.y.toFloat()), Size(rect.width.toFloat(), rect.height.toFloat()))
        Glows.forEachIndexed { i, glow ->
            // An ellipse: a circle of radius rx·width, squashed to ry·height.
            val rx = glow.rx * rect.width.toFloat()
            val ry = glow.ry * rect.height.toFloat()
            translate(rect.x.toFloat() + glow.x * rect.width.toFloat(), rect.y.toFloat() + glow.y * rect.height.toFloat()) {
                scale(rx, ry, pivot = Offset.Zero) {
                    drawCircle(GlowBrushes[i], radius = 1f, center = Offset.Zero)
                }
            }
        }
    }
}

/**
 * Soft shadows of rounded rectangles whose blur and strength change every frame. Blur filters are
 * kept by sigma, rounded to a quarter dp, so a moving photo does not build one per frame.
 */
internal class ScatterGridAlbumPaints {
    /** A path reused for every clip, so a frame allocates none. */
    val clip = Path()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val filters = HashMap<Int, BlurMaskFilter>()

    private fun filter(sigma: Float): BlurMaskFilter {
        val key = (sigma * 4f).roundToInt().coerceAtLeast(1)
        return filters.getOrPut(key) { BlurMaskFilter(sigmaToRadius(key / 4f), BlurMaskFilter.Blur.NORMAL) }
    }

    /** Hardware canvases only honour mask filters from API 28. */
    private val supported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

    fun roundRect(scope: DrawScope, color: Color, sigma: Float, x: Float, y: Float, w: Float, h: Float, r: Float, alpha: Float) {
        if (!supported || alpha <= 0f) return
        paint.style = Paint.Style.FILL
        paint.maskFilter = filter(sigma)
        paint.color = color.copy(alpha = 1f).toArgb()
        paint.alpha = (color.alpha * alpha * 255f).roundToInt().coerceIn(0, 255)
        scope.drawIntoCanvas { it.nativeCanvas.drawRoundRect(x, y, x + w, y + h, r, r, paint) }
    }

    /** An icon's shadow: its path, stroked (or filled), blurred and dropped. */
    fun icon(scope: DrawScope, path: Path, stroke: Float?) {
        if (!supported) return
        paint.style = if (stroke != null) Paint.Style.STROKE else Paint.Style.FILL
        paint.strokeWidth = stroke ?: 0f
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.maskFilter = filter(ICON_SHADOW_SIGMA)
        paint.color = IconShadow.toArgb()
        scope.drawIntoCanvas {
            it.nativeCanvas.save()
            it.nativeCanvas.translate(0f, ICON_SHADOW_Y)
            it.nativeCanvas.drawPath(path.asAndroidPath(), paint)
            it.nativeCanvas.restore()
        }
    }
}

/** One photo: aspect-filled in its frame, rounded, with its shadow; grey while it loads (in dp). */
internal fun DrawScope.drawPhoto(image: ImageBitmap?, pose: PhotoPose, shadows: ScatterGridAlbumPaints) {
    val w = pose.width.toFloat()
    val h = pose.height.toFloat()
    val r = pose.radius.toFloat()
    val lift = CELL_SHADOW + (1 - CELL_SHADOW) * pose.lift.toFloat()
    translate(pose.cx.toFloat() - w / 2f, pose.cy.toFloat() - h / 2f) {
        rotate(pose.tilt.toFloat(), pivot = Offset(w / 2f, h / 2f)) {
            shadows.roundRect(this, PhotoShadow, PHOTO_SHADOW_SIGMA * lift, 0f, PHOTO_SHADOW_Y * lift, w, h, r, lift)
            val shape = shadows.clip.apply {
                reset()
                addRoundRect(RoundRect(0f, 0f, w, h, CornerRadius(r)))
            }
            clipPath(shape) {
                drawRect(PlaceholderColor, Offset.Zero, Size(w, h))
                if (image != null) {
                    // Aspect fill: the largest centred piece of the image with the frame's shape.
                    val k = max(w / image.width, h / image.height)
                    val sw = (w / k).roundToInt().coerceIn(1, image.width)
                    val sh = (h / k).roundToInt().coerceIn(1, image.height)
                    val src = IntSize(sw, sh)
                    scale(w / sw, h / sh, pivot = Offset.Zero) {
                        drawImage(image, IntOffset((image.width - sw) / 2, (image.height - sh) / 2), src, IntOffset.Zero, src)
                    }
                }
            }
        }
    }
}

/** The six dots that burst out of the heart as it fills, around [center] (in dp). */
internal fun DrawScope.drawBurst(progress: Double, center: Offset) {
    val e = easeOut(progress)
    val radius = (BURST_FROM + (BURST_TO - BURST_FROM) * e).toFloat()
    val dot = (BURST_DOT * (1 - 0.4 * progress)).toFloat()
    val alpha = (1 - progress).toFloat().coerceIn(0f, 1f)
    for (k in 0 until BURST_DOTS) {
        val a = k.toDouble() / BURST_DOTS * 2 * Math.PI - Math.PI / 2
        drawCircle(HeartColor, dot / 2f, Offset(center.x + cos(a).toFloat() * radius, center.y + sin(a).toFloat() * radius), alpha)
    }
}

// Icons (20 × 20 boxes, stroked at 1.6), built at [size] dp.

private class PathScale(val s: Float) {
    val path = Path()
    fun m(x: Float, y: Float) = path.moveTo(x * s, y * s)
    fun l(x: Float, y: Float) = path.lineTo(x * s, y * s)

    /** A quadratic curve to (x, y) through the control point (cx, cy), in Swift's argument order. */
    fun q(x: Float, y: Float, cx: Float, cy: Float) = path.quadraticTo(cx * s, cy * s, x * s, y * s)
    fun c(x: Float, y: Float, c1x: Float, c1y: Float, c2x: Float, c2y: Float) = path.cubicTo(c1x * s, c1y * s, c2x * s, c2y * s, x * s, y * s)
    fun oval(x: Float, y: Float, w: Float, h: Float) = path.addOval(Rect(x * s, y * s, (x + w) * s, (y + h) * s))
}

/** Share: an open box with an arrow up out of it. */
internal fun sharePath(size: Float) = PathScale(size / 20f).run {
    m(7.2f, 7.6f); l(5.8f, 7.6f); q(4.4f, 9f, 4.4f, 7.6f); l(4.4f, 16.6f); q(5.8f, 18f, 4.4f, 18f)
    l(14.2f, 18f); q(15.6f, 16.6f, 15.6f, 18f); l(15.6f, 9f); q(14.2f, 7.6f, 15.6f, 7.6f); l(12.8f, 7.6f)
    m(10f, 12.4f); l(10f, 2.2f)
    m(7.1f, 5f); l(10f, 2.1f); l(12.9f, 5f)
    path
}

/** Heart, also filled when liked. */
internal fun heartPath(size: Float) = PathScale(size / 20f).run {
    m(10f, 17f)
    c(2.6f, 7.7f, 6.4f, 14.6f, 2.6f, 11.4f)
    c(6.6f, 3.4f, 2.6f, 5.2f, 4.4f, 3.4f)
    c(10f, 5.4f, 8.1f, 3.4f, 9.3f, 4.2f)
    c(13.4f, 3.4f, 10.7f, 4.2f, 11.9f, 3.4f)
    c(17.4f, 7.7f, 15.6f, 3.4f, 17.4f, 5.2f)
    c(10f, 17f, 17.4f, 11.4f, 13.6f, 14.6f)
    path.close()
    path
}

/** Info: a circle with an "i" (its dot is [infoDotPath]). */
internal fun infoPath(size: Float) = PathScale(size / 20f).run {
    oval(2.2f, 2.2f, 15.6f, 15.6f)
    m(8.7f, 9f); l(10f, 9f); l(10f, 14f)
    m(8.6f, 14f); l(11.4f, 14f)
    path
}

internal fun infoDotPath(size: Float) = PathScale(size / 20f).run {
    oval(9.05f, 5.55f, 1.9f, 1.9f)
    path
}

/** Trash: a lid with a handle, a tapered can with two ribs. */
internal fun trashPath(size: Float) = PathScale(size / 20f).run {
    m(3.2f, 5f); l(16.8f, 5f)
    m(7.6f, 5f); l(7.6f, 3.6f); q(8.6f, 2.6f, 7.6f, 2.6f); l(11.4f, 2.6f); q(12.4f, 3.6f, 12.4f, 2.6f); l(12.4f, 5f)
    m(4.9f, 5f); l(5.8f, 16.6f); q(7.2f, 18f, 5.9f, 18f); l(12.8f, 18f); q(14.2f, 16.6f, 14.1f, 18f); l(15.1f, 5f)
    m(8.4f, 8.2f); l(8.4f, 14.8f)
    m(11.6f, 8.2f); l(11.6f, 14.8f)
    path
}

internal val IconStroke = Stroke(width = ICON_STROKE.toFloat(), cap = StrokeCap.Round, join = StrokeJoin.Round)

// Photos

/** Downloads a photo once (Coil keeps it in its memory and disk caches) and hands it over ready to draw. */
internal suspend fun loadScatterGridAlbumPhoto(context: Context, address: String): ImageBitmap? {
    val request = ImageRequest.Builder(context).data(address).build()
    val result = SingletonImageLoader.get(context).execute(request) as? SuccessResult ?: return null
    return result.image.toBitmap().asImageBitmap()
}
