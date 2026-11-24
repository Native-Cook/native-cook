package com.ngocdevv.nativecook.blocks.text.floatinglettersstir

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.cssBoxShadow
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.rgba
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/*
 Floating Letters Stir — the opening screen of a meditation app. "let it settle" floats on a
 pale pool as eleven separate letters. A finger stirs the water: letters near its path are
 carried along, turn and drift, rings run out from the stroke and lift each letter as they
 pass, and then every letter eases home on its own spring until the phrase reads again.

 One Canvas, drawn in dp: the water (an AGSL pass: depth gradient, caustics, rings, grain; three
 gradients and stroked rings below API 33), then the letters, each placed, turned and scaled
 about its own pivot from advances measured at runtime: a soft shadow on the pool floor, a
 contact shadow, the bright rim where water clings to a floating thing, and the letter. The
 three soft layers are blurred once per glyph into alpha bitmaps (a blur is the same whichever
 way the glyph is turned), so a frame is a few dozen tinted quads and eleven glyphs. The script
 and the live simulation are in FloatingLettersMotion.kt, the water in FloatingLettersWater.kt.
 A port of the React Native block, number for number.
*/

private val DEFAULT_LINES = listOf("let it", "settle")
private const val CAPTION = "Stir the water, then let it settle."
private const val INK = 0xFF173F47.toInt()
private const val MAX_SIZE = 96f
private const val SIDE = 34f
private const val LINE_GAP = 1.04f
private const val TRACKING = 0.02f
/** A glyph turns about the middle of its lowercase body: this far above the baseline, in em. */
private const val PIVOT_UP = 0.3f

private const val IDLE = 2f
private const val SPAWN_EVERY = 56f
private const val TICK_SPEED = 900f

/** Shadow on the pool floor: where it falls, and how far a ring's slope shifts it (refraction). */
private const val SHADOW_DX = 4f
private const val SHADOW_DY = 11f
private const val SHADOW_REFRACT = 5f
private const val CONTACT_DY = 2.5f

private const val FLOOR_SIGMA = 7f
private const val CONTACT_SIGMA = 2.5f
private const val RIM_SIGMA = 1.2f
private const val RIM_WIDTH = 3.2f

private const val TEXT_FLAGS = Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG or Paint.LINEAR_TEXT_FLAG

private val ROUNDED_BOLD: Typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)

/** The phrase laid out once, in dp: glyphs, their advances, their resting pivots and what the script does to each. */
private class StirLayout(
    val size: Float,
    val glyphs: Array<String>,
    val advances: FloatArray,
    val homeX: FloatArray,
    val homeY: FloatArray,
    val pushes: Array<Push>,
    val geo: Geo,
    /** Bottom of the phrase (0.3 em under the last baseline). */
    val bottom: Float,
)

private fun lineWidth(paint: Paint, line: String, size: Float): Float {
    val widths = FloatArray(line.length)
    paint.getTextWidths(line, widths)
    return widths.sum() + size * TRACKING * (line.length - 1)
}

private fun makeLayout(lines: List<String>, w: Float, h: Float, insetTop: Float): StirLayout {
    val paint = Paint(TEXT_FLAGS).apply { typeface = ROUNDED_BOLD }
    var size = min(MAX_SIZE, w * 0.245f)
    paint.textSize = size
    var widest = lines.maxOf { lineWidth(paint, it, size) }
    if (widest > w - SIDE * 2) {
        size = floor((size * (w - SIDE * 2)) / widest)
        paint.textSize = size
        widest = lines.maxOf { lineWidth(paint, it, size) }
    }
    val gap = size * LINE_GAP
    val block = gap * (lines.size - 1)
    val cy = max(insetTop + 96 + block / 2 + size * 0.5f, h * 0.4f)
    val first = cy - block / 2 + size * PIVOT_UP
    val glyphs = ArrayList<String>()
    val advances = ArrayList<Float>()
    val homeX = ArrayList<Float>()
    val homeY = ArrayList<Float>()
    for ((l, line) in lines.withIndex()) {
        val widths = FloatArray(line.length)
        paint.getTextWidths(line, widths)
        var x = (w - (widths.sum() + size * TRACKING * (line.length - 1))) / 2
        val baseline = first + gap * l
        for (k in line.indices) {
            if (line[k] != ' ') {
                glyphs += line[k].toString()
                advances += widths[k]
                homeX += x + widths[k] / 2
                homeY += baseline - size * PIVOT_UP
            }
            x += widths[k] + size * TRACKING
        }
    }
    val geo = Geo(cx = w / 2, cy = cy, hw = min(w / 2 - 30, widest / 2 + 10), hh = block / 2 + size * 0.46f, w = w, h = h)
    val hx = homeX.toFloatArray()
    val hy = homeY.toFloatArray()
    return StirLayout(size, glyphs.toTypedArray(), advances.toFloatArray(), hx, hy, makePushes(hx, hy, geo), geo, first + block + size * 0.3f)
}

/** One soft layer of one glyph, blurred once: an alpha bitmap and where it sits around the glyph's pivot (dp). */
private class Soft(val bitmap: Bitmap, val box: RectF)

private class GlyphArt(val floor: Soft, val contact: Soft, val rim: Soft)

private fun softGlyph(glyph: String, advance: Float, size: Float, density: Float, sigma: Float, strokeWidth: Float): Soft {
    val paint = Paint(TEXT_FLAGS).apply {
        typeface = ROUNDED_BOLD
        textSize = size * density
        color = android.graphics.Color.BLACK
        maskFilter = BlurMaskFilter(sigmaToRadius(sigma * density), BlurMaskFilter.Blur.NORMAL)
        if (strokeWidth > 0f) {
            style = Paint.Style.STROKE
            this.strokeWidth = strokeWidth * density
            strokeJoin = Paint.Join.ROUND
        }
    }
    val metrics = paint.fontMetrics
    val pad = ceil((sigma * 3 + strokeWidth + 2) * density)
    val widthPx = ceil(advance * density + pad * 2).toInt().coerceAtLeast(1)
    val heightPx = ceil(metrics.bottom - metrics.top + pad * 2).toInt().coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ALPHA_8)
    android.graphics.Canvas(bitmap).drawText(glyph, pad, pad - metrics.top, paint)
    // The pen sits half an advance left of the pivot and PIVOT_UP em under it.
    val left = -advance / 2 - pad / density
    val top = size * PIVOT_UP + (metrics.top - pad) / density
    return Soft(bitmap, RectF(left, top, left + widthPx / density, top + heightPx / density))
}

private fun makeArt(layout: StirLayout, density: Float) = Array(layout.glyphs.size) { i ->
    GlyphArt(
        floor = softGlyph(layout.glyphs[i], layout.advances[i], layout.size, density, FLOOR_SIGMA, 0f),
        contact = softGlyph(layout.glyphs[i], layout.advances[i], layout.size, density, CONTACT_SIGMA, 0f),
        rim = softGlyph(layout.glyphs[i], layout.advances[i], layout.size, density, RIM_SIGMA, RIM_WIDTH),
    )
}

/** The clocks, the finger and the live water. Plain fields, stepped once a frame and read while drawing. */
private class Stir(val layout: StirLayout) {
    /** Bumped once a frame (and by touches): the Canvas reads it, so only the Canvas redraws. */
    val frame = mutableLongStateOf(0L)
    /** autoPlay and not reduced motion. State, so a change redraws even when no frame is running. */
    var auto by mutableStateOf(true)
    var reduced by mutableStateOf(false)

    /** Subscribes the caller (a draw) to the frame clock. */
    fun observe(): Long = frame.longValue

    private val count = layout.glyphs.size
    /** Loop time of the scripted stir. */
    private var clock = 0f
    /** Ambient time (swell, caustics), period LOOP; keeps running under touch. */
    var amb = 0f
        private set
    /** Seconds since mount, for the age of live rings. */
    private var live = 0f
    var touching = false
        private set
    private var idleFor = 0f
    private val sim = Sim(count)
    var fingerX = 0f
        private set
    var fingerY = 0f
        private set
    private var fingerVx = 0f
    private var fingerVy = 0f
    private var fingerOn = false
    private var fingerAt = 0f
    var press = 0f
        private set
    /** Live rings: RIPPLES × [x, y, born (live time), strength]. */
    private val liveRips = FloatArray(RIPPLES * 4)
    private var ripIndex = 0
    private var spawnedAt = 0f
    private var spawnX = 0f
    private var spawnY = 0f
    private var ticked = false

    /** The rings on the water now: RIPPLES × [x, y, age, strength]. */
    val ripples = FloatArray(RIPPLES * 4)
    /** Every glyph's pose, 6 each: dx, dy, rotation, scale, shadow shift x, y. */
    val poses = FloatArray(count * 6)
    /** The ghost finger of the script: x, y, presence. */
    val ghost = FloatArray(3)

    private val o4 = FloatArray(4)
    private val p4 = FloatArray(4)
    private val v2 = FloatArray(2)
    private val v3 = FloatArray(3)

    fun step(dt: Float) {
        val now = live + dt
        live = now
        if (!reduced) amb = (amb + dt) % LOOP
        // A finger that rests sends no updates: its last velocity must not keep pushing.
        if (fingerOn && now - fingerAt > 0.08f) {
            fingerVx = 0f
            fingerVy = 0f
        }
        press += ((if (fingerOn) 1f else 0f) - press) * min(1f, dt * 14)
        if (!touching) {
            if (auto) clock = (clock + dt) % LOOP
            return
        }
        if (!fingerOn) idleFor += dt
        val settle = idleFor > IDLE
        val steps = if (dt > 1f / 90) 2 else 1
        repeat(steps) {
            stepSim(sim, layout.homeX, layout.homeY, fingerX, fingerY, fingerVx, fingerVy, fingerOn, layout.geo, dt / steps, settle)
        }
        // Back to the script once the letters are home and the last ring has died: t = 0 is this rest.
        if (settle && simEnergy(sim) < 0.3f && now - spawnedAt > RIPPLE_LIFE) {
            clock = 0f
            touching = false
        }
    }

    /** Rings, poses and the ghost for the state as it is now. Called from the draw. */
    fun pose() {
        if (!touching) {
            autoRipples(clock, layout.geo, auto, ripples, v2)
        } else {
            for (k in 0 until RIPPLES) {
                val o = k * 4
                val age = live - liveRips[o + 2]
                if (liveRips[o + 3] > 0f && age < RIPPLE_LIFE) {
                    ripples[o] = liveRips[o]; ripples[o + 1] = liveRips[o + 1]; ripples[o + 2] = age; ripples[o + 3] = liveRips[o + 3]
                } else {
                    ripples[o] = 0f; ripples[o + 1] = 0f; ripples[o + 2] = 0f; ripples[o + 3] = 0f
                }
            }
        }
        for (i in 0 until count) {
            var dx = 0f
            var dy = 0f
            var rot = 0f
            var sc = 1f
            if (!touching) {
                if (auto) {
                    autoOffset(clock, i, layout.pushes[i], o4)
                    dx = o4[0]; dy = o4[1]; rot = o4[2]; sc = o4[3]
                }
            } else {
                dx = sim.x[i]; dy = sim.y[i]; rot = sim.r[i]
            }
            swellAt(amb, i, v2)
            ripplesAt(layout.homeX[i] + dx, layout.homeY[i] + dy, ripples, v3)
            val o = i * 6
            poses[o] = dx
            poses[o + 1] = dy + v2[0] - BOB_LIFT * v3[0]
            poses[o + 2] = rot + v2[1] + BOB_TILT * v3[1]
            poses[o + 3] = sc
            poses[o + 4] = SHADOW_REFRACT * v3[1]
            poses[o + 5] = SHADOW_REFRACT * v3[2]
        }
        if (touching || !auto) {
            ghost[0] = 0f; ghost[1] = 0f; ghost[2] = 0f
        } else {
            stirPos(clock, layout.geo, v2)
            ghost[0] = v2[0]; ghost[1] = v2[1]; ghost[2] = ghostAt(clock)
        }
    }

    // ---- Touch: the finger is the stir -------------------------------------------------------

    private fun spawn(x: Float, y: Float, strength: Float) {
        val o = ripIndex * 4
        liveRips[o] = x
        liveRips[o + 1] = y
        liveRips[o + 2] = live
        liveRips[o + 3] = strength
        ripIndex = (ripIndex + 1) % RIPPLES
        spawnedAt = live
        spawnX = x
        spawnY = y
    }

    private fun takeOver() {
        if (!touching) {
            sim.reset()
            val t = clock
            if (auto) {
                // Start from where the script has the letters, moving as they were.
                val back = 1f / 60
                for (i in 0 until count) {
                    autoOffset(t, i, layout.pushes[i], o4)
                    autoOffset(max(0f, t - back), i, layout.pushes[i], p4)
                    sim.x[i] = o4[0]
                    sim.y[i] = o4[1]
                    sim.r[i] = o4[2]
                    sim.vx[i] = (o4[0] - p4[0]) / back
                    sim.vy[i] = (o4[1] - p4[1]) / back
                    sim.vr[i] = (o4[2] - p4[2]) / back
                }
            }
            // The script's rings stay on the water: re-date them on the live clock.
            autoRipples(t, layout.geo, auto, ripples, v2)
            for (k in 0 until RIPPLES) {
                val o = k * 4
                liveRips[o] = ripples[o]
                liveRips[o + 1] = ripples[o + 1]
                liveRips[o + 2] = live - ripples[o + 2]
                liveRips[o + 3] = ripples[o + 3]
            }
            ripIndex = 0
            touching = true
        }
        idleFor = 0f
    }

    fun down(x: Float, y: Float) {
        takeOver()
        fingerX = x; fingerY = y; fingerVx = 0f; fingerVy = 0f; fingerOn = true
        fingerAt = live
        spawn(x, y, 0.7f)
        ticked = false
        frame.longValue++
    }

    /** True when this move is the stroke's first above the haptic speed. */
    fun move(x: Float, y: Float, vx: Float, vy: Float): Boolean {
        fingerX = x; fingerY = y; fingerVx = vx; fingerVy = vy; fingerOn = true
        fingerAt = live
        idleFor = 0f
        val speed = hypot(vx, vy)
        if (hypot(x - spawnX, y - spawnY) >= SPAWN_EVERY) spawn(x, y, 0.45f + 0.55f * seg(speed, 100f, 1100f))
        if (!ticked && speed > TICK_SPEED) {
            ticked = true
            return true
        }
        return false
    }

    fun up() {
        fingerVx = 0f; fingerVy = 0f; fingerOn = false
        idleFor = 0f
    }
}

/**
 * @param autoPlay Plays the stir by itself every [LOOP] seconds; a touch takes over.
 * @param lines The phrase, one string per line.
 */
@Composable
fun FloatingLettersStir(
    modifier: Modifier = Modifier,
    autoPlay: Boolean = true,
    lines: List<String> = DEFAULT_LINES,
) {
    BlockSystemBars(darkIcons = true)
    val density = LocalDensity.current.density
    val view = LocalView.current
    val reducedMotion = rememberReducedMotion()
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val insetTop = insets.calculateTopPadding()
    val insetBottom = insets.calculateBottomPadding()
    val phrase = lines.joinToString(" ")

    BoxWithConstraints(modifier.fillMaxSize().background(Color(0xFFC5E3E1))) {
        val w = maxWidth.value
        val h = maxHeight.value
        val layout = remember(lines, w, h, insetTop) { makeLayout(lines, w, h, insetTop.value) }
        val art = remember(layout, density) { makeArt(layout, density) }
        val stir = remember(layout) {
            Stir(layout).apply {
                auto = autoPlay && !reducedMotion
                reduced = reducedMotion
            }
        }
        SideEffect {
            stir.auto = autoPlay && !reducedMotion
            stir.reduced = reducedMotion
        }
        val water = remember { if (Build.VERSION.SDK_INT >= 33) ShaderWater() else GradientWater() }
        val paints = remember { StirPaints() }
        val captionX = remember(w) { (w - paints.caption.measureText(CAPTION)) / 2 }

        // Phrase at rest, water still, no loop, no gesture under reduced motion.
        LaunchedEffect(stir, reducedMotion) {
            if (reducedMotion) return@LaunchedEffect
            var last = 0L
            while (true) {
                withFrameNanos { now ->
                    val dt = if (last == 0L) 0.016f else min((now - last) / 1e9f, 0.05f)
                    last = now
                    stir.step(dt)
                    stir.frame.longValue = now
                }
            }
        }

        Canvas(
            Modifier
                .fillMaxSize()
                .semantics {
                    heading()
                    contentDescription = "$phrase. Stir the water with a finger and the letters drift, then settle."
                }
                .pointerInput(stir, reducedMotion) {
                    if (reducedMotion) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        stir.down(down.position.x / density, down.position.y / density)
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            tracker.addPosition(change.uptimeMillis, change.position)
                            val v = tracker.calculateVelocity()
                            change.consume()
                            if (stir.move(change.position.x / density, change.position.y / density, v.x / density, v.y / density)) {
                                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                            }
                        }
                        stir.up()
                    }
                },
        ) {
            stir.observe()
            // Read so a change of autoPlay or reduced motion redraws without a frame.
            val auto = stir.auto
            stir.pose()
            drawIntoCanvas { c ->
                val canvas = c.nativeCanvas
                canvas.save()
                canvas.scale(density, density)

                val phase = (2 * PI * stir.amb / LOOP).toFloat()
                if (stir.touching) {
                    water.draw(canvas, layout.geo, phase, stir.ripples, stir.fingerX, stir.fingerY, stir.press, density)
                } else {
                    water.draw(canvas, layout.geo, phase, stir.ripples, stir.ghost[0], stir.ghost[1], if (auto) stir.ghost[2] else 0f, density)
                }

                // The line under the phrase is drawn on the water, so drifting letters pass over it, not under.
                canvas.drawText(CAPTION, captionX, layout.bottom + layout.size * 0.34f + 17, paints.caption)

                val poses = stir.poses
                val n = layout.glyphs.size
                // Floor shadows, shifted by the slope of the ring above them.
                for (i in 0 until n) {
                    val o = i * 6
                    canvas.save()
                    canvas.translate(layout.homeX[i] + poses[o] + SHADOW_DX + poses[o + 4], layout.homeY[i] + poses[o + 1] + SHADOW_DY + poses[o + 5])
                    canvas.rotate(poses[o + 2] * DEGREES)
                    canvas.scale(poses[o + 3], poses[o + 3])
                    canvas.drawBitmap(art[i].floor.bitmap, null, art[i].floor.box, paints.floor)
                    canvas.restore()
                }
                for (i in 0 until n) {
                    val o = i * 6
                    canvas.save()
                    canvas.translate(layout.homeX[i] + poses[o], layout.homeY[i] + poses[o + 1] + CONTACT_DY)
                    canvas.rotate(poses[o + 2] * DEGREES)
                    canvas.scale(poses[o + 3], poses[o + 3])
                    canvas.drawBitmap(art[i].contact.bitmap, null, art[i].contact.box, paints.contact)
                    canvas.restore()
                }
                for (pass in 0..1) {
                    for (i in 0 until n) {
                        val o = i * 6
                        canvas.save()
                        canvas.translate(layout.homeX[i] + poses[o], layout.homeY[i] + poses[o + 1])
                        canvas.rotate(poses[o + 2] * DEGREES)
                        canvas.scale(poses[o + 3], poses[o + 3])
                        if (pass == 0) {
                            canvas.drawBitmap(art[i].rim.bitmap, null, art[i].rim.box, paints.rim)
                        } else {
                            paints.letter.textSize = layout.size
                            canvas.drawText(layout.glyphs[i], -layout.advances[i] / 2, layout.size * PIVOT_UP, paints.letter)
                        }
                        canvas.restore()
                    }
                }

                val presence = stir.ghost[2]
                if (presence > 0.001f) {
                    val radius = 17 * (1.25f - 0.25f * presence)
                    paints.ghostFill.alpha = (0.42f * presence * 255).toInt()
                    canvas.drawCircle(stir.ghost[0], stir.ghost[1], radius, paints.ghostFill)
                    paints.ghostRim.alpha = (0.85f * presence * 255).toInt()
                    canvas.drawCircle(stir.ghost[0], stir.ghost[1], radius, paints.ghostRim)
                }
                canvas.restore()
            }
        }

        // The wordmark.
        Row(
            Modifier.fillMaxWidth().padding(top = insetTop + 18.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(18.dp).border(1.5.dp, rgba(23, 63, 71, 0.78f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(6.dp).background(rgba(23, 63, 71, 0.78f), CircleShape))
            }
            Text("Stillwater", style = TextStyle(color = rgba(23, 63, 71, 0.82f), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp))
        }

        // The session and its button.
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = insetBottom + 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "Evening unwind · 6 min",
                style = TextStyle(color = rgba(23, 63, 71, 0.6f), fontSize = 14.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum"),
            )
            Box(
                Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .height(54.dp)
                    .cssBoxShadow(rgba(13, 50, 58, 0.22f), offsetY = 14.dp, blur = 30.dp, radius = 27.dp)
                    .cssBoxShadow(rgba(13, 50, 58, 0.32f), offsetY = 1.dp, blur = 2.dp, radius = 27.dp)
                    .clip(CircleShape)
                    .background(Brush.verticalGradient(0f to Color(0xFF2C6C77), 0.55f to Color(0xFF1F535D), 1f to Color(0xFF173F47)))
                    .drawWithContent {
                        drawContent()
                        // inset 0 1px 0: the sliver of the pill that the same pill, one dp lower, does not cover.
                        val pill = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(size.height / 2))) }
                        val lower = Path().apply { addPath(pill, Offset(0f, 1.dp.toPx())) }
                        drawPath(Path.combine(PathOperation.Difference, pill, lower), Color.White.copy(alpha = 0.22f))
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text("Begin", style = TextStyle(color = Color(0xFFF2F8F6), fontSize = 17.sp, fontWeight = FontWeight.SemiBold))
            }
        }
    }
}

private const val DEGREES = (180 / PI).toFloat()

/** The Canvas's paints. Each keeps one colour, so none inherits another's alpha. */
private class StirPaints {
    val caption = Paint(TEXT_FLAGS).apply {
        typeface = Typeface.SANS_SERIF
        textSize = 17f
        color = android.graphics.Color.argb(158, 23, 63, 71)
    }
    val floor = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { color = android.graphics.Color.argb(51, 20, 74, 86) }
    val contact = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { color = android.graphics.Color.argb(77, 12, 52, 62) }
    val rim = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { color = android.graphics.Color.argb(128, 255, 255, 255) }
    val letter = Paint(TEXT_FLAGS).apply {
        typeface = ROUNDED_BOLD
        color = INK
    }
    val ghostFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        maskFilter = BlurMaskFilter(sigmaToRadius(1.5f), BlurMaskFilter.Blur.NORMAL)
    }
    val ghostRim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 1.2f
    }
}
