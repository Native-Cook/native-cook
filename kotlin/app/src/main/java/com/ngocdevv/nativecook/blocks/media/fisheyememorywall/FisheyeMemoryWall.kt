package com.ngocdevv.nativecook.blocks.media.fisheyememorywall

import android.app.Activity
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlin.math.hypot
import kotlin.math.max

/*
 Fisheye Memory Wall — the year view of an invented journal app ("Keepsake"): "2026 in moments"
 (a port of FisheyeMemoryWall.swift).

 Look: an endless dark wall of small rounded tiles, each a tiny memory drawn in code (a pan of
 shakshuka, a sunset over the sea, a ridge, a flat white, a birthday cake, a skyline at night, a
 tulip, a beach umbrella, a hot-air balloon, a lemon tart, a tent under the stars, gradients and date
 cards), seen through a strong fisheye lens that fills the screen: tiles in the middle are 1.6 times
 their size, tiles near the rim shrink and curve away, and the rim falls into a dark vignette.

 Hero: the bulge. One AGSL shader (API 33+) traces every point back to the wall through a barrel
 function and samples a pre-rendered atlas of one wall period (repeat tiling), so the wall never ends
 and the tiles stay crisp. Below API 33 the wall pans flat under the same vignette.

 autoPlay: LOOP = 9 s. Rest 0–1 s on the birthday cake with a focus ring. 1.0–7.6 s the wall pans one
 whole period sideways (soft start, cruise, soft stop) with an S-shaped bob (90 dp · sin 2πe); the
 bulge grows from k 1 to 1.42 (1.0–2.8 s), relaxes (5.6–7.6 s) a little past rest and settles by
 8.6 s; the ring fades out at 1.0–1.4 s and back at 7.8–8.4 s. One period on, the wall is where it
 began, so the last frame is the first.
 Touch: drag to wander (the centre tile follows the finger 1 : 1, a throw keeps momentum), pinch to
 strengthen or relax the bulge. A light tick when a new memory reaches the centre. 2 s after the last
 touch the wall eases to the nearest copy of rest (1.2 s) and the loop restarts.
 Reduced motion: the still rest frame (the lens stays); drag still pans, no loop.
*/

private const val BACKGROUND = 0x07090DL
private const val INK = 0xF5F3EEL
private const val ACCENT = 0xFF9A6BL

private fun hex(v: Long, a: Float = 1f) = Color(
    red = ((v shr 16) and 0xFF) / 255f,
    green = ((v shr 8) and 0xFF) / 255f,
    blue = (v and 0xFF) / 255f,
    alpha = a,
)

private fun Double.f() = toFloat()

/** Text measured once per string, size and weight (at 1 px per dp; drawn inside the density scale). */
private class FmwTexts(private val m: TextMeasurer) {
    private val unit = Density(1f, 1f)
    private val cache = HashMap<String, TextLayoutResult>()

    fun get(s: String, size: Float, weight: FontWeight, track: Float = 0f): TextLayoutResult =
        cache.getOrPut("$s|$size|${weight.weight}|$track") {
            m.measure(
                s,
                TextStyle(color = Color.White, fontSize = size.sp, fontWeight = weight, letterSpacing = track.sp),
                density = unit,
            )
        }
}

private class FmwHolder {
    var pose = FmwPose()
    var running = false
}

private const val LENS_AGSL = """
uniform shader atlas;
uniform float4 lens;
uniform float4 view;
uniform float4 wall;
uniform float4 focus;
uniform float2 ring;
uniform half4 background;

float lensF(float d, float k) {
    float d2 = d * d;
    return 1.0 / (1.0 + 0.6 * k) + k * (0.217 * d2 / 3.0 + 2.284 * d2 * d2 * d2 / 7.0);
}

float lensRadial(float d, float k) {
    float d2 = d * d;
    return 1.0 / (1.0 + 0.6 * k) + k * (0.217 * d2 + 2.284 * d2 * d2 * d2);
}

half3 tap(float2 p) {
    float2 q = (p - lens.xy) / lens.zw;
    float d = min(length(q), 1.0);
    float2 w = view.xy + (p - lens.xy) * lensF(d, view.z);
    w = w - floor(w / wall.xy) * wall.xy;
    return atlas.eval(w * wall.w).rgb;
}

half4 main(float2 position) {
    float2 q = (position - lens.xy) / lens.zw;
    float d = length(q);
    float minify = lensRadial(min(d, 1.0), view.z) * view.w;
    half3 color;
    if (minify > 1.4) {
        float o = wall.z * 0.25;
        color = (tap(position + float2(-o, -o)) + tap(position + float2(o, -o))
               + tap(position + float2(-o, o)) + tap(position + float2(o, o))) * 0.25;
    } else {
        color = tap(position);
    }
    if (ring.y > 0.001) {
        float dc = min(d, 1.0);
        float2 w = view.xy + (position - lens.xy) * lensF(dc, view.z);
        w = w - floor(w / wall.xy) * wall.xy;
        float2 halfSize = focus.zw * 0.5;
        float2 qb = abs(w - (focus.xy + halfSize)) - halfSize + ring.x;
        float sd = length(max(qb, 0.0)) + min(max(qb.x, qb.y), 0.0) - ring.x;
        float px = wall.z * lensRadial(dc, view.z);
        float line = 1.0 - smoothstep(0.9, 0.9 + px, abs(sd - 2.2));
        float glow = exp(-max(sd - 2.2, 0.0) * 0.35) * step(0.0, sd) * 0.18;
        color = mix(color, half3(1.0), ring.y * min(1.0, line * 0.92 + glow));
    }
    float vignette = 1.0 - 0.82 * smoothstep(0.7, 1.0, d);
    color *= vignette;
    float2 sheen = (position - float2(lens.x - 0.36 * lens.z, lens.y - 0.42 * lens.w)) / (0.9 * lens.z);
    color += half3(0.07 * exp(-dot(sheen, sheen) * 2.2) * (1.0 - smoothstep(0.8, 1.0, d)));
    float outside = smoothstep(0.985, 1.02, d);
    color = mix(color, background.rgb, outside);
    return half4(color, 1.0);
}
"""

/** The lens as an AGSL shader over the repeating atlas (API 33+). */
@RequiresApi(33)
private class FmwLens {
    private val shader = RuntimeShader(LENS_AGSL)
    val paint = Paint()

    init {
        val atlas = BitmapShader(FmwAtlas.bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        atlas.setFilterMode(BitmapShader.FILTER_MODE_LINEAR)
        shader.setInputShader("atlas", atlas)
        paint.shader = shader
    }

    fun set(w: Double, h: Double, pose: FmwPose, density: Float, focus: FmwTile, ring: Double) {
        shader.setFloatUniform("lens", (w / 2).f(), (h / 2).f(), (w * 0.53).f(), (h * 0.5).f())
        shader.setFloatUniform(
            "view",
            FmwModel.wrap(FmwModel.REST_X + pose.x, FmwModel.PERIOD_W).f(),
            FmwModel.wrap(FmwModel.REST_Y + pose.y, FmwModel.PERIOD_H).f(),
            pose.k.f(),
            (FmwModel.ATLAS_SCALE / density).f(),
        )
        shader.setFloatUniform("wall", FmwModel.PERIOD_W.f(), FmwModel.PERIOD_H.f(), 1f / density, FmwModel.ATLAS_SCALE.f())
        shader.setFloatUniform("focus", focus.x.f(), focus.y.f(), focus.width.f(), focus.height.f())
        shader.setFloatUniform("ring", focus.radius.f(), ring.f())
        shader.setFloatUniform("background", 7 / 255f, 9 / 255f, 13 / 255f, 1f)
    }
}

/** Below API 33: the wall pans flat at the centre's zoom under the same vignette. */
private class FmwFlat {
    private val shader = BitmapShader(FmwAtlas.bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    private val matrix = Matrix()
    val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply { shader = this@FmwFlat.shader }
    val vignette = Paint()

    fun set(w: Double, h: Double, pose: FmwPose) {
        val a = FmwModel.centre(pose.k)
        val px = FmwModel.wrap(FmwModel.REST_X + pose.x, FmwModel.PERIOD_W)
        val py = FmwModel.wrap(FmwModel.REST_Y + pose.y, FmwModel.PERIOD_H)
        matrix.reset()
        matrix.setTranslate((-px * FmwModel.ATLAS_SCALE).f(), (-py * FmwModel.ATLAS_SCALE).f())
        matrix.postScale((1 / (FmwModel.ATLAS_SCALE * a)).f(), (1 / (FmwModel.ATLAS_SCALE * a)).f())
        matrix.postTranslate((w / 2).f(), (h / 2).f())
        shader.setLocalMatrix(matrix)
        val m = Matrix()
        m.setScale(1f, (h / (w * 1.06)).f(), (w / 2).f(), (h / 2).f())
        val g = RadialGradient(
            (w / 2).f(), (h / 2).f(), (w * 0.53).f(),
            intArrayOf(0x00000000, 0x00000000, 0xD007090D.toInt(), 0xFF07090D.toInt()),
            floatArrayOf(0f, 0.7f, 0.97f, 1f), Shader.TileMode.CLAMP,
        )
        g.setLocalMatrix(m)
        vignette.shader = g
    }
}

/**
 * The year view of a journal app: an endless wall of small memories seen through a fisheye lens.
 *
 * @param autoPlay plays the 9 s pan-and-bulge loop until touched.
 */
@Composable
fun FisheyeMemoryWall(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val reduced = rememberReducedMotion()
    val auto = autoPlay && !reduced
    val view = LocalView.current
    val context = LocalContext.current
    val screen = LocalDensity.current
    val safeTop = WindowInsets.safeDrawing.getTop(screen) / screen.density
    val safeBottom = WindowInsets.safeDrawing.getBottom(screen) / screen.density
    DisposableEffect(view) {
        val window = (context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars ?: false
        controller?.isAppearanceLightStatusBars = false
        onDispose { controller?.isAppearanceLightStatusBars = previous }
    }
    val driver = remember(auto) { FmwDriver() }
    val holder = remember { FmwHolder() }
    val tick = remember { mutableIntStateOf(0) }
    val wake = remember { mutableIntStateOf(0) }
    val measurer = rememberTextMeasurer()
    val texts = remember(measurer) { FmwTexts(measurer) }
    val lens = remember { if (Build.VERSION.SDK_INT >= 33) FmwLens() else null }
    val flat = remember { if (Build.VERSION.SDK_INT < 33) FmwFlat() else null }

    LaunchedEffect(driver, auto, wake.intValue) {
        holder.pose = driver.pose(System.nanoTime() / 1e9, auto)
        tick.intValue += 1
        holder.running = true
        try {
            while (true) {
                val idle = withFrameNanos { n ->
                    holder.pose = driver.pose(n / 1e9, auto)
                    tick.intValue += 1
                    !auto && !driver.live()
                }
                if (idle) break
            }
        } finally {
            holder.running = false
        }
    }

    BoxWithConstraints(modifier.fillMaxSize().background(hex(BACKGROUND)).clipToBounds()) {
        val w = maxWidth.value.toDouble()
        val h = maxHeight.value.toDouble()

        fun now() = System.nanoTime() / 1e9
        fun haptic() = view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        fun wakeUp() {
            if (!holder.running) wake.intValue += 1
        }

        Canvas(
            Modifier
                .fillMaxSize()
                .semantics {
                    contentDescription = "2026 in moments. A wall of memories under a fisheye lens. Drag to wander, pinch to change the bulge."
                }
                .pointerInput(driver, auto) {
                    awaitEachGesture {
                        val d = density
                        val down = awaitFirstDown(requireUnconsumed = false)
                        driver.beginDrag(now(), auto)
                        wakeUp()
                        var anchor = down.position
                        var anchorId: PointerId = down.id
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        var pinching = false
                        var startDist = 1f
                        var reanchor = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.isEmpty()) break
                            if (pressed.size >= 2) {
                                val dist = (pressed[0].position - pressed[1].position).getDistance()
                                if (!pinching) {
                                    pinching = true
                                    startDist = max(dist, 1f)
                                    driver.beginPinch(now(), auto)
                                }
                                driver.pinch((dist / startDist).toDouble())
                                reanchor = true
                            } else {
                                if (pinching) {
                                    pinching = false
                                    driver.endPinch(now())
                                }
                                val c = pressed[0]
                                if (reanchor || c.id != anchorId) {
                                    driver.beginDrag(now(), auto)
                                    anchor = c.position
                                    anchorId = c.id
                                    tracker.resetTracking()
                                    reanchor = false
                                }
                                tracker.addPosition(c.uptimeMillis, c.position)
                                val dx = ((c.position.x - anchor.x) / d).toDouble()
                                val dy = ((c.position.y - anchor.y) / d).toDouble()
                                if (driver.drag(dx, dy)) haptic()
                            }
                            event.changes.forEach { it.consume() }
                        }
                        if (pinching) driver.endPinch(now())
                        val v = tracker.calculateVelocity()
                        driver.endDrag((v.x / d).toDouble(), (v.y / d).toDouble(), now())
                        wakeUp()
                    }
                },
        ) {
            tick.intValue
            val pose = holder.pose
            scale(density, density, pivot = Offset.Zero) {
                fmwDraw(w, h, safeTop.toDouble(), safeBottom.toDouble(), pose, texts, lens, flat, density)
            }
        }
    }
}

// Drawing (in dp).

private val shadowPaint = Paint()

private fun DrawScope.fmwDraw(
    w: Double, h: Double, top: Double, bottom: Double, pose: FmwPose, tx: FmwTexts,
    lens: Any?, flat: FmwFlat?, density: Float,
) {
    // The memory under the centre gets a focus ring; it dims while the centre crosses a gutter.
    val (index, depth) = FmwModel.memory(FmwModel.REST_X + pose.x, FmwModel.REST_Y + pose.y)
    val tile = FmwModel.TILES[index]
    val ring = pose.ring * FmwModel.clamp((depth + 2.5) / 8)
    if (Build.VERSION.SDK_INT >= 33 && lens != null) {
        drawLens(lens, w, h, pose, density, tile, ring)
    } else if (flat != null) {
        flat.set(w, h, pose)
        drawRect(hex(BACKGROUND), Offset.Zero, Size(w.f(), h.f()))
        drawIntoCanvas {
            it.nativeCanvas.drawRect(0f, 0f, w.f(), h.f(), flat.paint)
            it.nativeCanvas.drawRect(0f, 0f, w.f(), h.f(), flat.vignette)
        }
    }

    // Scrims keep the header and the caption readable over the rim.
    val bg = hex(BACKGROUND)
    drawRect(
        Brush.verticalGradient(
            0f to bg.copy(alpha = 0.94f), 0.45f to bg.copy(alpha = 0.7f), 1f to bg.copy(alpha = 0f),
            startY = 0f, endY = (top + 170).f(),
        ),
        Offset.Zero, Size(w.f(), (top + 170).f()),
    )
    val sy = h - bottom - 210
    drawRect(
        Brush.verticalGradient(
            0f to bg.copy(alpha = 0f), 0.5f to bg.copy(alpha = 0.75f), 1f to bg.copy(alpha = 0.94f),
            startY = sy.f(), endY = h.f(),
        ),
        Offset(0f, sy.f()), Size(w.f(), (h - sy).f()),
    )
    header(top, tx)
    caption(w, h, bottom, tile, depth, tx)
}

@RequiresApi(33)
private fun DrawScope.drawLens(lens: Any, w: Double, h: Double, pose: FmwPose, density: Float, tile: FmwTile, ring: Double) {
    val l = lens as FmwLens
    l.set(w, h, pose, density, tile, ring)
    drawIntoCanvas { it.nativeCanvas.drawRect(0f, 0f, w.f(), h.f(), l.paint) }
}

/** Draws a line of text vertically centred on y: starting at x (anchor −1) or centred on it (0). */
private fun DrawScope.text(l: TextLayoutResult, x: Double, y: Double, color: Color, anchor: Int = -1) {
    val tw = l.size.width.toFloat()
    val th = l.size.height.toFloat()
    val left = if (anchor < 0) x.f() else x.f() - tw / 2
    drawText(l, color = color, topLeft = Offset(left, y.f() - th / 2))
}

private fun DrawScope.header(top: Double, tx: FmwTexts) {
    val y = top + 12
    text(tx.get("KEEPSAKE", 11f, FontWeight.SemiBold, 2.2f), 24.0, y + 8, hex(INK, 0.55f))
    text(tx.get("2026 in moments", 30f, FontWeight.Bold, -0.6f), 24.0, y + 38, hex(INK))
    text(tx.get("1,284 memories · 212 days", 13f, FontWeight.Medium), 24.0, y + 66, hex(INK, 0.6f))
}

private fun DrawScope.caption(w: Double, h: Double, bottom: Double, tile: FmwTile, depth: Double, tx: FmwTexts) {
    val x = 20.0
    val y = h - max(bottom, 16.0) - 46 - 64
    val cw = w - 40
    val ch = 64.0
    shadowPaint.reset()
    shadowPaint.isAntiAlias = true
    shadowPaint.color = Color.Black.copy(alpha = 0.35f).toArgb()
    shadowPaint.maskFilter = BlurMaskFilter(24f, BlurMaskFilter.Blur.NORMAL)
    drawIntoCanvas { it.nativeCanvas.drawRoundRect(x.f(), (y + 8).f(), (x + cw).f(), (y + 8 + ch).f(), 22f, 22f, shadowPaint) }
    drawRoundRect(hex(0x0F131AL, 0.82f), Offset(x.f(), y.f()), Size(cw.f(), ch.f()), CornerRadius(22f))
    drawRoundRect(
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.04f)), startY = y.f(), endY = (y + ch).f()),
        Offset((x + 0.5).f(), (y + 0.5).f()), Size((cw - 1).f(), (ch - 1).f()), CornerRadius(21.5f), style = Stroke(1f),
    )
    // The memory under the centre; its text dims while the centre crosses a gutter.
    val a = (0.3 + 0.7 * FmwModel.clamp((depth + 2.5) / 8)).toFloat()
    val (title, place) = FmwModel.CAPTIONS[tile.design]
    text(tx.get(FmwModel.MONTHS[tile.month], 10f, FontWeight.Bold, 1.4f), x + 36, y + 21, hex(ACCENT, a), 0)
    text(tx.get("${tile.day}", 22f, FontWeight.Bold), x + 36, y + 41, hex(INK, a), 0)
    drawRect(Color.White.copy(alpha = 0.12f), Offset((x + 70).f(), (y + 14).f()), Size(1f, 36f))
    text(tx.get(title, 16f, FontWeight.SemiBold), x + 86, y + 24, hex(INK, a))
    text(tx.get(place, 12f, FontWeight.Medium), x + 86, y + 44, hex(INK, 0.55f * a))
    // An open chevron.
    val chevron = Path().apply {
        moveTo((x + cw - 26).f(), (y + ch / 2 - 6).f())
        lineTo((x + cw - 20).f(), (y + ch / 2).f())
        lineTo((x + cw - 26).f(), (y + ch / 2 + 6).f())
    }
    drawPath(chevron, hex(INK, 0.45f), style = Stroke(1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    text(tx.get("Drag to wander · pinch to bulge", 11f, FontWeight.Medium), w / 2, y + ch + 18, hex(INK, 0.4f), 0)
}
