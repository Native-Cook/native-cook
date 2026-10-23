package com.ngocdevv.nativecook.blocks.media.loupemagnifier

import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Size
import coil3.toBitmap
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private const val PHOTO = "photo-1529926691761-20fb82067c71"
private fun photoUrl(id: String) = "https://images.unsplash.com/$id?auto=format&fit=crop&w=1600&q=80"

private val Stops = listOf(
    LoupeStop(0.42f, 0.44f, 2.6f),
    LoupeStop(0.36f, 0.16f, 3.2f),
    LoupeStop(0.27f, 0.3f, 3f),
    LoupeStop(0.48f, 0.7f, 3.4f),
    LoupeStop(0.74f, 0.77f, 3f),
)

// Brushed metal around the rim, light to dark and back.
private val RIM_TONES = intArrayOf(0xFFF4F3EF.toInt(), 0xFF8C8B86.toInt(), 0xFFE2E0DA.toInt(), 0xFF5E5D59.toInt(), 0xFFD3D1CB.toInt(), 0xFF7D7C78.toInt(), 0xFFF4F3EF.toInt())

// The whole screen in one pass: the photo cover-fitted and dimmed, the loupe's soft shadow, and
// inside the glass the photo magnified with barrel distortion, colour fringes and a dark edge.
// Android 13+; below that the photo is drawn dimmed and magnified inside the glass without the
// lens's bulge and fringes.
private const val LENS_AGSL = """
uniform shader image;
uniform float2 imageSize;
uniform float2 viewSize;
uniform float2 center;
uniform float radius;
uniform float rim;
uniform float zoom;
uniform float dim;
uniform float2 shadowOffset;
uniform float shadowSoft;
uniform float barrel;
uniform float fringe;
uniform float shown;

float2 toImage(float2 p) {
  float s = max(viewSize.x / imageSize.x, viewSize.y / imageSize.y);
  float2 off = (viewSize - imageSize * s) * 0.5;
  return (p - off) / s;
}

half4 main(float2 p) {
  float2 d = p - center;
  float r = length(d) / radius;
  if (r < 1.0) {
    float k = 1.0 + barrel * r * r;
    float split = fringe * r * r * r;
    float2 base = d / zoom;
    half3 col;
    col.r = image.eval(toImage(center + base * (k + split))).r;
    col.g = image.eval(toImage(center + base * k)).g;
    col.b = image.eval(toImage(center + base * (k - split))).b;
    col *= 1.06 * (1.0 - 0.5 * smoothstep(0.74, 1.0, r));
    return half4(col * shown, 1.0);
  }
  half3 col = image.eval(toImage(p)).rgb * dim * shown;
  float2 uv = p / viewSize - 0.5;
  col *= 1.0 - 0.7 * dot(uv, uv);
  float sd = length(p - center - shadowOffset) - (radius + rim);
  col *= 1.0 - 0.5 * (1.0 - smoothstep(-shadowSoft * 0.4, shadowSoft, sd));
  return half4(col, 1.0);
}
"""

private fun argb(hex: Long, a: Float = 1f) = ((if (a.isNaN()) 0f else a.coerceIn(0f, 1f)) * 255f).roundToInt().shl(24) or (hex and 0xFFFFFF).toInt()

/** Every paint, shader and scratch object the picture needs, made once. */
private class LoupePaints {
    val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    val photo = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    val lens: Shader? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) RuntimeShader(LENS_AGSL) else null
    var lensImage: Any? = null
    val tab = text(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD), 11f)
    val title = text(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) Typeface.create(Typeface.SANS_SERIF, 700, false) else Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD), 22f)
    val meta = text(Typeface.MONOSPACE, 10f)
    val rect = RectF()
    val src = Rect()
    val bounds = Rect()
    val path = Path()

    private fun text(face: Typeface, size: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = face
        textSize = size
    }

    /** A string's width as Skia's `measureText` gives it: its ink bounds plus a point either side. */
    fun measure(paint: Paint, text: String): Float {
        paint.getTextBounds(text, 0, text.length, bounds)
        return if (bounds.isEmpty) 0f else bounds.width() + 2f
    }
}

/**
 * A large, detailed photo fills the screen, dimmed, with a round magnifying loupe over it. Inside
 * the glass the photo is enlarged 2.5 times through a curved lens: it bulges, its colours split
 * into fringes towards the thick metal rim, and a soft gloss slides across it as it moves. The
 * loupe trails the finger on a spring; a pinch changes the zoom, a tap on the glass looks closer,
 * and a tap anywhere else sends the loupe there on an arc.
 *
 * @param photo Unsplash id of a large, detailed photo; it is fetched 1600 px wide.
 * @param title Two lines at the bottom: a title and a line of small print.
 * @param autoPlay Wanders the loupe over the photo on a 13 s path, pausing on details.
 * @param stops Where the wander stops, as shares of the screen, and the zoom at each stop.
 */
@Composable
fun LoupeMagnifier(
    modifier: Modifier = Modifier,
    photo: String = PHOTO,
    title: String = "Harrow Strait Bridge",
    meta: String = "CABLE-STAYED · 471 M SPAN · F/8",
    autoPlay: Boolean = false,
    stops: List<LoupeStop> = Stops,
) {
    val reducedMotion = rememberReducedMotion()
    val auto = autoPlay && !reducedMotion
    val view = LocalView.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val engine = remember(stops) {
        LoupeEngine(
            stops,
            tick = { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) },
            land = { view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK) },
        )
    }
    val paints = remember { LoupePaints() }
    // Read by the picture, so it is drawn again every frame.
    var tick by remember { mutableFloatStateOf(0f) }

    val insets = WindowInsets.safeDrawing
    val safeTop = insets.getTop(density) / density.density
    val safeBottom = insets.getBottom(density) / density.density
    SideEffect {
        engine.auto = auto
        engine.reduced = reducedMotion
        engine.top = safeTop
        engine.bottom = safeBottom
    }

    LaunchedEffect(engine, auto) { engine.restartLoop() }

    LaunchedEffect(engine, photo, context) {
        val request = ImageRequest.Builder(context)
            .data(photoUrl(photo))
            .size(Size.ORIGINAL)
            // A software bitmap can carry mipmaps for the photo drawn smaller than it is.
            .allowHardware(false)
            .build()
        val result = SingletonImageLoader.get(context).execute(request) as? SuccessResult ?: return@LaunchedEffect
        val bitmap = result.image.toBitmap()
        bitmap.setHasMipMap(true)
        engine.photo = LoupePhoto(bitmap, engine.elapsed)
    }

    LaunchedEffect(engine) {
        var last = -1L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last < 0) 0.016f else min((now - last) / 1e9f, 0.064f)
                last = now
                if (dt > 0f) engine.advance(dt)
                tick = engine.elapsed
            }
        }
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .semantics {
                contentDescription = "$title. A magnifying loupe over the photo: drag to move it, pinch to zoom, tap elsewhere to send it there."
            }
            .pointerInput(engine) {
                val d = density.density
                awaitEachGesture {
                    val first = awaitFirstDown(requireUnconsumed = false)
                    first.consume()
                    engine.touchesBegan(listOf(first.id.value), listOf(floatArrayOf(first.position.x / d, first.position.y / d)))
                    try {
                        do {
                            val event = awaitPointerEvent()
                            val began = ArrayList<Long>()
                            val beganAt = ArrayList<FloatArray>()
                            val moved = ArrayList<Long>()
                            val movedAt = ArrayList<FloatArray>()
                            val ended = ArrayList<Long>()
                            val endedAt = ArrayList<FloatArray>()
                            for (change in event.changes) {
                                val at = floatArrayOf(change.position.x / d, change.position.y / d)
                                when {
                                    change.pressed && !change.previousPressed -> {
                                        began.add(change.id.value)
                                        beganAt.add(at)
                                    }
                                    !change.pressed && change.previousPressed -> {
                                        ended.add(change.id.value)
                                        endedAt.add(at)
                                    }
                                    change.pressed && change.position != change.previousPosition -> {
                                        moved.add(change.id.value)
                                        movedAt.add(at)
                                    }
                                }
                                change.consume()
                            }
                            if (began.isNotEmpty()) engine.touchesBegan(began, beganAt)
                            if (moved.isNotEmpty()) engine.touchesMoved(moved, movedAt)
                            if (ended.isNotEmpty()) engine.touchesEnded(ended, endedAt)
                        } while (event.changes.any { it.pressed })
                    } finally {
                        // Cancelled, or a finger's lift never reported: nothing stays held.
                        engine.touchesLost()
                    }
                }
            },
    ) {
        engine.width = maxWidth.value
        engine.height = maxHeight.value
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            @Suppress("UNUSED_VARIABLE")
            val frame = tick
            scale(density.density, density.density, pivot = Offset.Zero) {
                drawIntoCanvas { drawLoupe(it.nativeCanvas, engine, paints, title, meta) }
            }
        }
    }
}

private fun drawLoupe(canvas: Canvas, e: LoupeEngine, p: LoupePaints, title: String, meta: String) {
    val width = e.width
    val height = e.height
    if (width <= 0f) return
    val l = e.lift
    val grow = 1f + JUMP_LIFT * l
    val r = RADIUS * grow
    val rim = RIM * grow
    val cx = e.x
    val cy = e.y
    val z = e.zoom
    val shadow = FloatArray(3) { SHADOW[it] + (SHADOW_LIFTED[it] - SHADOW[it]) * l }
    val fill = p.fill
    fill.shader = null
    fill.colorFilter = null
    val photo = e.photo
    val lens = p.lens
    if (photo == null) {
        fill.color = argb(0x0B0C10)
        canvas.drawRect(0f, 0f, width, height, fill)
    } else {
        val shown = clamp01((e.elapsed - photo.at) / IMAGE_FADE)
        val bitmap = photo.bitmap
        if (lens != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val shader = lens as RuntimeShader
            if (p.lensImage !== bitmap) {
                val image = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
                image.filterMode = BitmapShader.FILTER_MODE_LINEAR
                shader.setInputShader("image", image)
                p.lensImage = bitmap
            }
            shader.setFloatUniform("imageSize", bitmap.width.toFloat(), bitmap.height.toFloat())
            shader.setFloatUniform("viewSize", width, height)
            shader.setFloatUniform("center", cx, cy)
            shader.setFloatUniform("radius", r)
            shader.setFloatUniform("rim", rim)
            shader.setFloatUniform("zoom", z)
            shader.setFloatUniform("dim", DIM)
            shader.setFloatUniform("shadowOffset", shadow[0], shadow[1])
            shader.setFloatUniform("shadowSoft", shadow[2])
            shader.setFloatUniform("barrel", BARREL)
            shader.setFloatUniform("fringe", FRINGE)
            shader.setFloatUniform("shown", shown)
            fill.color = argb(0x000000)
            fill.shader = shader
            canvas.drawRect(0f, 0f, width, height, fill)
            fill.shader = null
        } else {
            drawPlainLens(canvas, p, bitmap, width, height, cx, cy, r, rim, z, shadow, shown)
        }
    }

    // The caption, over a dark fade at the bottom, under the loupe.
    canvas.save()
    p.path.reset()
    p.path.addCircle(cx, cy, r + rim, Path.Direction.CW)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) canvas.clipOutPath(p.path)
    fill.shader = LinearGradient(0f, height - 170f, 0f, height, argb(0x000000, 0f), argb(0x000000, 0.7f), Shader.TileMode.CLAMP)
    fill.color = argb(0x000000)
    canvas.drawRect(0f, height - 170f, width, height, fill)
    fill.shader = null
    val base = height - e.bottom - 18f
    p.title.color = argb(0xF3F1EA)
    canvas.drawText(title, 22f, base - 18f, p.title)
    p.meta.color = argb(0xF3F1EA, 0.6f)
    canvas.drawText(meta, 22f, base, p.meta)
    canvas.restore()

    drawGloss(canvas, p, cx, cy, r, e.tiltX, e.tiltY)
    drawRim(canvas, p, cx, cy, r, rim, e.tiltX * 90f, z)
}

/**
 * The lens without a runtime shader: the photo cover-fitted and dimmed with the same vignette and
 * shadow, and inside the glass the photo magnified about its middle with a darkened edge.
 */
private fun drawPlainLens(canvas: Canvas, p: LoupePaints, bitmap: android.graphics.Bitmap, width: Float, height: Float, cx: Float, cy: Float, r: Float, rim: Float, z: Float, shadow: FloatArray, shown: Float) {
    val s = max(width / bitmap.width, height / bitmap.height)
    val w = bitmap.width * s
    val h = bitmap.height * s
    val ox = (width - w) / 2f
    val oy = (height - h) / 2f
    val fill = p.fill
    val dim = DIM * shown
    p.photo.colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setScale(dim, dim, dim, 1f) })
    p.rect.set(ox, oy, ox + w, oy + h)
    canvas.drawBitmap(bitmap, null, p.rect, p.photo)
    fill.color = argb(0x000000)
    fill.shader = RadialGradient(width / 2f, height / 2f, max(width, height) * 0.71f, intArrayOf(argb(0x000000, 0f), argb(0x000000, 0.35f)), null, Shader.TileMode.CLAMP)
    canvas.drawRect(0f, 0f, width, height, fill)
    fill.shader = RadialGradient(cx + shadow[0], cy + shadow[1], r + rim + shadow[2], intArrayOf(argb(0x000000, 0.5f), argb(0x000000, 0.5f), argb(0x000000, 0f)), floatArrayOf(0f, (r + rim - shadow[2] * 0.4f) / (r + rim + shadow[2]), 1f), Shader.TileMode.CLAMP)
    canvas.drawCircle(cx + shadow[0], cy + shadow[1], r + rim + shadow[2], fill)
    fill.shader = null
    canvas.save()
    p.path.reset()
    p.path.addCircle(cx, cy, r, Path.Direction.CW)
    canvas.clipPath(p.path)
    fill.color = argb(0x000000)
    canvas.drawCircle(cx, cy, r, fill)
    val bright = 1.06f * shown
    p.photo.colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setScale(bright, bright, bright, 1f) })
    canvas.save()
    canvas.scale(z, z, cx, cy)
    canvas.drawBitmap(bitmap, null, p.rect, p.photo)
    canvas.restore()
    fill.shader = RadialGradient(cx, cy, r, intArrayOf(argb(0x000000, 0f), argb(0x000000, 0f), argb(0x000000, 0.5f)), floatArrayOf(0f, 0.74f, 1f), Shader.TileMode.CLAMP)
    canvas.drawCircle(cx, cy, r, fill)
    fill.shader = null
    canvas.restore()
    p.photo.colorFilter = null
}

/** The glass's gloss: a soft crescent of light and a pin-point glint, sliding against the speed. */
private fun drawGloss(canvas: Canvas, p: LoupePaints, cx: Float, cy: Float, r: Float, tx: Float, ty: Float) {
    canvas.save()
    p.path.reset()
    p.path.addCircle(cx, cy, r, Path.Direction.CW)
    canvas.clipPath(p.path)
    val hx = cx - r * 0.34f + tx * r
    val hy = cy - r * 0.42f + ty * r
    val fill = p.fill
    fill.color = argb(0x000000)
    fill.shader = RadialGradient(hx, hy, r * 0.62f, intArrayOf(argb(0xFFFFFF, 0.34f), argb(0xFFFFFF, 0.08f), argb(0xFFFFFF, 0f)), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
    canvas.save()
    canvas.rotate(-32f + tx * 40f, hx, hy)
    p.rect.set(hx - r * 0.6f, hy - r * 0.3f, hx + r * 0.6f, hy + r * 0.3f)
    canvas.drawOval(p.rect, fill)
    canvas.restore()
    fill.shader = null
    // A thin bright arc along the lower right of the glass: light leaving through the far side.
    p.stroke.strokeWidth = 3f
    p.stroke.color = argb(0xFFFFFF, 0.16f)
    p.rect.set(cx - r + 5f, cy - r + 5f, cx + r - 5f, cy + r - 5f)
    canvas.drawArc(p.rect, 20f - tx * 30f, 70f, false, p.stroke)
    fill.color = argb(0xFFFFFF, 0.85f)
    canvas.drawCircle(cx - r * 0.5f + tx * r * 1.2f, cy - r * 0.52f + ty * r * 1.2f, r * 0.035f, fill)
    canvas.restore()
}

/** The metal rim: a brushed ring with bevels and a knurled outer band, and the zoom on a tab. */
private fun drawRim(canvas: Canvas, p: LoupePaints, cx: Float, cy: Float, r: Float, rim: Float, spin: Float, zoom: Float) {
    val ring = p.stroke
    ring.strokeWidth = rim
    ring.color = argb(0x000000)
    ring.shader = SweepGradient(cx, cy, RIM_TONES, null)
    // The brushed light turns a little as the loupe swings.
    canvas.save()
    canvas.rotate(spin, cx, cy)
    canvas.drawCircle(cx, cy, r + rim / 2f, ring)
    canvas.restore()
    ring.shader = null
    // Knurling on the outer half of the band.
    ring.strokeWidth = 0.9f
    ring.color = argb(0x000000, 0.2f)
    for (k in 0 until 90) {
        val a = k / 90f * PI.toFloat() * 2f
        val c = cos(a)
        val s = sin(a)
        canvas.drawLine(cx + c * (r + rim * 0.58f), cy + s * (r + rim * 0.58f), cx + c * (r + rim * 0.94f), cy + s * (r + rim * 0.94f), ring)
    }
    // Bevels: dark at the glass and at the outer edge, a bright line just inside the outer edge.
    ring.strokeWidth = 1.4f
    ring.color = argb(0x000000, 0.6f)
    canvas.drawCircle(cx, cy, r, ring)
    ring.strokeWidth = 1f
    ring.color = argb(0x000000, 0.45f)
    canvas.drawCircle(cx, cy, r + rim, ring)
    ring.color = argb(0xFFFFFF, 0.5f)
    canvas.drawCircle(cx, cy, r + 2f, ring)
    ring.color = argb(0xFFFFFF, 0.25f)
    canvas.drawCircle(cx, cy, r + rim - 1.5f, ring)

    // The zoom on a small dark tab fixed to the rim, up and to the right.
    val label = String.format(Locale.US, "%.1f×", zoom)
    val w = p.measure(p.tab, label) + 14f
    val tx = cx + cos(-PI.toFloat() / 4f) * (r + rim) - 4f
    val ty = cy + sin(-PI.toFloat() / 4f) * (r + rim) - 6f
    p.rect.set(tx, ty - 11f, tx + w, ty + 11f)
    p.fill.shader = null
    p.fill.color = argb(0x0E0E10, 0.86f)
    canvas.drawRoundRect(p.rect, 11f, 11f, p.fill)
    ring.color = argb(0xFFFFFF, 0.22f)
    canvas.drawRoundRect(p.rect, 11f, 11f, ring)
    p.tab.color = argb(0xF3F1EA)
    canvas.drawText(label, tx + 7f, ty + 4f, p.tab)
}
