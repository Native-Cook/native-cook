package com.ngocdevv.nativecook.blocks.playground.fireflyswarm

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

// Glow sprites: a soft halo and a hot core, tinted, drawn additively.
private const val SPRITE = 64
private val Halo = floatArrayOf(0.62f, 0.95f, 0.3f)
private val Core = floatArrayOf(1.0f, 0.98f, 0.74f)

/** One layer of grass: count, base as a share of the height, height range in dp, width range, colour. */
private class GrassLayer(
    val count: Int,
    val base: Float,
    val low: Float,
    val high: Float,
    val thin: Float,
    val thick: Float,
    val color: Int,
    val tip: Int,
    val give: Float,
)

private val Grass = listOf(
    GrassLayer(90, 0.74f, 22f, 60f, 2.5f, 4.5f, 0xFF0F1028.toInt(), 0xFF4B4579.toInt(), 0.5f),
    GrassLayer(70, 0.86f, 50f, 120f, 3f, 6f, 0xFF08091B.toInt(), 0xFF3A3664.toInt(), 0.8f),
    GrassLayer(48, 1.01f, 96f, 210f, 4f, 8.5f, 0xFF030409.toInt(), 0xFF2B2950.toInt(), 1f),
)
private const val STARS = 56
private val MeadowTop = 0xFF14142E.toInt()
private val Hint = listOf("Touch to gather", "Flick to scatter", "Hold to sync")
private val HintColor = Color(0xFFE8E2C8)

/** Blades: x as a share of the width, height and width in dp, a phase for the wind, a lean. */
private class Blades(layer: GrassLayer, seed: Int) {
    val x = FloatArray(layer.count)
    val h = FloatArray(layer.count)
    val w = FloatArray(layer.count)
    val p = FloatArray(layer.count)
    val lean = FloatArray(layer.count)

    init {
        val r = FireflyRandom(seed)
        for (i in 0 until layer.count) {
            x[i] = (i + r.next() * 0.9f) / layer.count * 1.08f - 0.04f
            h[i] = layer.low + (layer.high - layer.low) * r.next().pow(1.4f)
            w[i] = layer.thin + (layer.thick - layer.thin) * r.next()
            p[i] = r.next() * TAU
            lean[i] = (r.next() - 0.5f) * 0.35f
        }
    }
}

private val BladeLayers = Grass.mapIndexed { k, layer -> Blades(layer, 31 + k) }

private object StarField {
    val x = FloatArray(STARS)
    val y = FloatArray(STARS)
    val size = FloatArray(STARS)
    val k = FloatArray(STARS)
    val p = FloatArray(STARS)

    init {
        val r = FireflyRandom(5)
        for (i in 0 until STARS) {
            x[i] = r.next()
            y[i] = 0.02f + 0.42f * r.next().pow(1.3f)
            size[i] = 0.5f + 1.1f * r.next().pow(3)
            k[i] = 1 + floor(r.next() * 5)
            p[i] = r.next() * TAU
        }
    }
}

// The far tree line's crowns, drawn from a fixed seed.
private val TreeLine = FireflyRandom(77).let { r -> FloatArray(400) { r.next() } }

/** A tree line along `base`: rounded crowns with now and then a tall pointed conifer. */
private fun treeLine(w: Float, base: Float, scale: Float, seed: Int): Path {
    fun pick(k: Int) = TreeLine[(seed + k) % TreeLine.size]
    val line = Path()
    line.moveTo(-10f, base + 12)
    line.lineTo(-10f, base - 6 * scale)
    var x = -10f
    var n = 0
    while (x < w + 20) {
        if (pick(n) < 0.22f) {
            // A conifer: narrow, tall, pointed, its sides gently concave.
            val wide = (13 + pick(n + 1) * 10) * scale
            val tall = (24 + pick(n + 2) * 22) * scale
            line.quadTo(x + wide * 0.42f, base - tall * 0.45f, x + wide * 0.5f, base - tall)
            line.quadTo(x + wide * 0.58f, base - tall * 0.45f, x + wide, base - 6 * scale)
            x += wide
        } else {
            val crown = (12 + pick(n + 1) * 24) * scale
            val top = base - (12 + pick(n + 2) * 22) * scale
            line.cubicTo(x, top, x + crown, top, x + crown, base - (4 + pick(n + 3) * 6) * scale)
            x += crown * 0.8f
        }
        n += 4
    }
    line.lineTo(w + 20, base + 12)
    line.close()
    return line
}

/** The still part of the dusk — sky, moon, the tree lines and the meadow floor — drawn once. */
private fun makeBackdrop(w: Float, h: Float, density: Float): Bitmap {
    val bitmap = Bitmap.createBitmap(ceil(w * density).toInt().coerceAtLeast(1), ceil(h * density).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    val c = Canvas(bitmap)
    c.scale(density, density)
    val paint = Paint().apply { isAntiAlias = true }
    paint.shader = LinearGradient(
        0f, 0f, 0f, h * 0.6f,
        intArrayOf(0xFF070A1D.toInt(), 0xFF121741.toInt(), 0xFF2B2A5E.toInt(), 0xFF6B4A76.toInt(), 0xFFC97A5C.toInt()),
        floatArrayOf(0f, 0.32f, 0.6f, 0.82f, 1f), Shader.TileMode.CLAMP,
    )
    c.drawRect(0f, 0f, w, h, paint)
    paint.shader = null
    // The moon: a thin crescent with a soft halo (a blurred disc).
    val mx = w * 0.78f
    val my = h * 0.13f
    val glow = Paint().apply {
        isAntiAlias = true
        color = android.graphics.Color.argb(33, 214, 208, 255)
        maskFilter = BlurMaskFilter(sigmaToRadius(34f), BlurMaskFilter.Blur.NORMAL)
    }
    c.drawCircle(mx, my, 48f, glow)
    val disc = Path().apply { addCircle(mx, my, 15f, Path.Direction.CW) }
    val bite = Path().apply { addCircle(mx + 6.5f, my - 4f, 13.5f, Path.Direction.CW) }
    disc.op(bite, Path.Op.DIFFERENCE)
    paint.color = 0xFFF2EAD6.toInt()
    c.drawPath(disc, paint)
    // Two tree lines, the far one paler in the haze, mist lying between them, then the meadow.
    val base = h * 0.6f
    paint.color = 0xFF3A2F5E.toInt()
    c.drawPath(treeLine(w, base - 12, 0.7f, 0), paint)
    paint.shader = LinearGradient(
        0f, base - 40, 0f, base + 10,
        intArrayOf(android.graphics.Color.argb(0, 196, 128, 140), android.graphics.Color.argb(89, 176, 118, 146), android.graphics.Color.argb(26, 120, 90, 140)),
        floatArrayOf(0f, 0.7f, 1f), Shader.TileMode.CLAMP,
    )
    c.drawRect(0f, base - 40, w, base + 10, paint)
    paint.shader = null
    paint.color = MeadowTop
    c.drawPath(treeLine(w, base, 1f, 97), paint)
    paint.shader = LinearGradient(
        0f, base + 6, 0f, h,
        intArrayOf(MeadowTop, 0xFF0C0E22.toInt(), 0xFF04050B.toInt()),
        floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP,
    )
    c.drawRect(0f, base + 6, w, h, paint)
    return bitmap
}

/** A glow sprite: a radial falloff in one colour on clear. */
private fun sprite(color: FloatArray, alphas: FloatArray, stops: FloatArray): Bitmap {
    val px = SPRITE * 2
    val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    val colors = IntArray(alphas.size) {
        android.graphics.Color.argb((alphas[it] * 255).toInt(), (color[0] * 255).toInt(), (color[1] * 255).toInt(), (color[2] * 255).toInt())
    }
    val paint = Paint().apply { shader = RadialGradient(px / 2f, px / 2f, px / 2f, colors, stops, Shader.TileMode.CLAMP) }
    Canvas(bitmap).drawRect(0f, 0f, px.toFloat(), px.toFloat(), paint)
    return bitmap
}

/** Paints, the backdrop and the sprites, made once. */
private class SwarmArt {
    var backdrop: Bitmap? = null
    private var forW = 0f
    private var forH = 0f
    val halo = sprite(Halo, floatArrayOf(1f, 0.62f, 0.3f, 0.1f, 0f), floatArrayOf(0f, 0.12f, 0.3f, 0.55f, 1f))
    val core = sprite(Core, floatArrayOf(1f, 0.9f, 0.35f, 0f), floatArrayOf(0f, 0.22f, 0.5f, 1f))
    val plain = Paint().apply { isFilterBitmap = true }
    val star = Paint().apply { isAntiAlias = true }
    val ring = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 2f
        maskFilter = BlurMaskFilter(sigmaToRadius(4f), BlurMaskFilter.Blur.NORMAL)
    }
    val glow = Paint().apply {
        isFilterBitmap = true
        xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
    }
    val grass = Grass.map { Paint().apply { isAntiAlias = true } }
    val grassPath = Path()
    val rect = RectF()
    private var shadersFor = -1f

    fun prepare(w: Float, h: Float, density: Float) {
        // Android may drop a cached bitmap's pixels; a recycled one is drawn again.
        if (backdrop == null || backdrop?.isRecycled == true || w != forW || h != forH) {
            backdrop = makeBackdrop(w, h, density)
            forW = w
            forH = h
        }
        if (shadersFor != h) {
            shadersFor = h
            Grass.forEachIndexed { k, layer ->
                val y0 = layer.base * h
                grass[k].shader = LinearGradient(0f, y0 - layer.high, 0f, y0, intArrayOf(layer.tip, layer.color), floatArrayOf(0f, 0.75f), Shader.TileMode.CLAMP)
            }
        }
    }
}

/**
 * A dusk meadow drawn in layered grass silhouettes under a crescent moon, with about 150 fireflies
 * flocking over it as boids and blinking with an additive glow. Touching gathers the swarm round
 * the finger, where it orbits; a fast flick scatters it; holding still makes the blinking roll out
 * from the finger in a wave. The grass sways and bends where the swarm passes low.
 *
 * @param autoPlay Gathers the swarm on a figure-eight, flicks it apart, syncs its blinking, lets it settle, on a loop.
 * @param hint The line of hints along the bottom edge.
 */
@Composable
fun FireflySwarm(
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false,
    hint: List<String> = Hint,
) {
    val reducedMotion = rememberReducedMotion()
    val auto = autoPlay && !reducedMotion
    val view = LocalView.current
    val density = LocalDensity.current
    val swarm = remember { Swarm().apply { home = if (auto) 1f else 0f } }
    val art = remember { SwarmArt() }
    var tick by remember { mutableIntStateOf(0) }
    // The touch under way: when it began (frame clock, ns), where, and whether it may still sync.
    val touch = remember { LongArray(1) { -1L } }
    val press = remember { FloatArray(2) }
    val pressState = remember { BooleanArray(2) } // [failed, fired]
    val bottom = WindowInsets.safeDrawing.getBottom(density) / density.density

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Color(0xFF070A1D)),
    ) {
        val w = maxWidth.value
        val h = maxHeight.value

        LaunchedEffect(swarm, auto, reducedMotion) {
            var last = -1L
            while (true) {
                withFrameNanos { now ->
                    val dt = if (last < 0) 1f / 60f else min((now - last) / 1e9f, 0.05f)
                    last = now
                    // Nothing to fly in before the first real layout (the backdrop would be 1 px).
                    if (dt <= 0f || w <= 0f || h <= 0f) return@withFrameNanos
                    if (!swarm.ready || swarm.w != w || swarm.h != h) {
                        swarm.reset(w, h)
                        return@withFrameNanos
                    }
                    // A finger held still long enough syncs the blinking.
                    val start = touch[0]
                    if (start >= 0 && !pressState[0] && !pressState[1]) {
                        if (touch[0] == 0L) touch[0] = now
                        else if ((now - touch[0]) / 1e9f >= HOLD_TIME) {
                            pressState[1] = true
                            swarm.startSync(press[0], press[1])
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        }
                    }
                    swarm.step(dt, auto, reducedMotion)
                    tick++
                }
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .semantics { contentDescription = "Firefly swarm" }
                .pointerInput(swarm, reducedMotion) {
                    val tracker = VelocityTracker()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val px = density.density
                        val sx = down.position.x / px
                        val sy = down.position.y / px
                        tracker.resetTracking()
                        tracker.addPointerInputChange(down)
                        swarm.touched()
                        swarm.fingerDown = true
                        swarm.fingerX = sx
                        swarm.fingerY = sy
                        press[0] = sx
                        press[1] = sy
                        pressState[0] = false
                        pressState[1] = false
                        touch[0] = 0L // stamped by the next frame
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        var panning = false
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                tracker.addPointerInputChange(change)
                                val x = change.position.x / px
                                val y = change.position.y / px
                                if (!change.pressed) {
                                    val v = tracker.calculateVelocity()
                                    val vx = v.x / px
                                    val vy = v.y / px
                                    if (panning && vx.isFinite() && vy.isFinite() && hypot(vx, vy) > FLICK && !swarm.holding) {
                                        swarm.scatter(x, y, vx, vy, reducedMotion)
                                        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                                    }
                                    break
                                }
                                val moved = hypot(x - sx, y - sy)
                                if (moved >= 2f) panning = true
                                if (moved > HOLD_SLOP) pressState[0] = true
                                swarm.fingerX = x
                                swarm.fingerY = y
                                press[0] = x
                                press[1] = y
                                change.consume()
                            }
                        } finally {
                            // Lifted, cancelled, or the pointer input restarted mid-touch: the finger
                            // is gone, so the swarm is not held round it and autoPlay can come back.
                            swarm.fingerDown = false
                            swarm.holding = false
                            touch[0] = -1L
                        }
                    }
                }
                .drawBehind {
                    tick
                    if (!swarm.ready) return@drawBehind
                    art.prepare(w, h, density.density)
                    drawIntoCanvas { c ->
                        val canvas = c.nativeCanvas
                        val back = art.backdrop ?: return@drawIntoCanvas
                        canvas.drawBitmap(back, null, android.graphics.Rect(0, 0, size.width.toInt(), size.height.toInt()), art.plain)
                        canvas.save()
                        canvas.scale(density.density, density.density)
                        drawSwarm(canvas, swarm, art, w, h)
                        canvas.restore()
                    }
                },
        )

        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = (bottom + 14).dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            hint.forEachIndexed { i, line ->
                if (i > 0) Box(Modifier.size(3.dp).alpha(0.3f).background(HintColor, CircleShape))
                BasicText(
                    line,
                    modifier = Modifier.alpha(0.5f),
                    maxLines = 1,
                    style = TextStyle(color = HintColor, fontSize = 12.sp, letterSpacing = 0.2.sp, fontWeight = FontWeight.Medium),
                )
            }
        }
    }
}

private fun drawSwarm(canvas: Canvas, s: Swarm, art: SwarmArt, w: Float, h: Float) {
    val now = s.elapsed
    // Stars, each twinkling a whole number of times per LOOP.
    for (i in 0 until STARS) {
        val a = 0.35f + 0.65f * (0.5f + 0.5f * sin(StarField.k[i] * W0 * now + StarField.p[i]))
        art.star.color = android.graphics.Color.argb((a * 0.8f * 255).toInt(), 235, 230, 255)
        canvas.drawCircle(StarField.x[i] * w, StarField.y[i] * h, StarField.size[i], art.star)
    }
    // Faint rings show the sync wave rolling out, one per pulse.
    val sync = s.sync
    if (sync > 0.01f) {
        val phase = (SYNC_RATE * now) % 1f
        for (k in 0 until 3) {
            val r = (phase + k) * WAVE_SPEED
            val alpha = 0.1f * sync * (1 - (phase + k) / 3)
            art.ring.color = android.graphics.Color.argb((alpha * 255).toInt(), 217, 255, 140)
            canvas.drawCircle(s.syncX, s.syncY, r, art.ring)
        }
    }
    drawGrass(canvas, art, 0, s, w, h, now)
    drawFlies(canvas, art, s, now, false)
    drawGrass(canvas, art, 1, s, w, h, now)
    drawGrass(canvas, art, 2, s, w, h, now)
    drawFlies(canvas, art, s, now, true)
}

/** One layer of grass: each blade a tapered curve bent by the wind and the swarm. */
private fun drawGrass(canvas: Canvas, art: SwarmArt, index: Int, s: Swarm, w: Float, h: Float, t: Float) {
    val layer = Grass[index]
    val blades = BladeLayers[index]
    val bend = s.bend
    val b = art.grassPath
    b.rewind()
    val y0 = layer.base * h
    for (i in 0 until layer.count) {
        val x0 = blades.x[i] * w
        val height = blades.h[i]
        val half = blades.w[i] / 2
        // Wind: two gusts that repeat per LOOP, travelling across the meadow.
        val wind = 0.07f * sin(3 * W0 * t - x0 * 0.011f + blades.p[i] * 0.3f) + 0.035f * sin(7 * W0 * t + blades.p[i])
        val k = min(bend.size - 1, max(0, floor(x0 / BUCKET).toInt()))
        val angle = blades.lean[i] + wind + (if (bend.isEmpty()) 0f else bend[k]) * layer.give
        val tipX = x0 + sin(angle) * height
        val tipY = y0 - cos(angle) * height
        val midX = x0 + sin(angle * 0.45f) * height * 0.55f
        val midY = y0 - cos(angle * 0.45f) * height * 0.55f
        b.moveTo(x0 - half, y0)
        b.quadTo(midX - half * 0.7f, midY, tipX, tipY)
        b.quadTo(midX + half * 0.7f, midY, x0 + half, y0)
        b.close()
    }
    // The bank the blades stand on, then the blades lit faintly from the sky at their tips.
    b.addRect(-10f, y0 - 1, w + 10, h + 11, Path.Direction.CW)
    canvas.drawPath(b, art.grass[index])
}

/** Glows: halos and cores of one depth layer, added onto the scene. */
private fun drawFlies(canvas: Canvas, art: SwarmArt, s: Swarm, now: Float, near: Boolean) {
    val t = Traits
    val paint = art.glow
    for (i in 0 until COUNT) {
        val depth = t.depth[i]
        if ((depth >= 0.85f) != near) continue
        val own = t.blinkP[i] + (t.blinkK[i] / LOOP) * now
        val glow = flash(own + s.drift[i])
        val halo = (14 + 62 * glow) * depth
        val core = (4 + 6 * glow) * depth
        val fade = 0.55f + 0.45f * clamp01((depth - 0.55f) / 0.6f)
        val hot = min(1f, (0.3f + glow) * fade)
        val warm = min(1f, glow * 0.85f * fade)
        // The sprites are already tinted; their brightness is the paint's alpha, which the additive
        // blend adds exactly as the colour-modulated atlas does.
        paint.alpha = (warm * 255).toInt()
        art.rect.set(s.x[i] - halo / 2, s.y[i] - halo / 2, s.x[i] + halo / 2, s.y[i] + halo / 2)
        canvas.drawBitmap(art.halo, null, art.rect, paint)
        paint.alpha = (hot * 255).toInt()
        art.rect.set(s.x[i] - core / 2, s.y[i] - core / 2, s.x[i] + core / 2, s.y[i] + core / 2)
        canvas.drawBitmap(art.core, null, art.rect, paint)
    }
}
