package com.ngocdevv.nativecook.blocks.playground.pluckstringharp

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

// Thickness of the lowest and highest string (dp).
private const val GAUGE_LOW = 2.6f
private const val GAUGE_HIGH = 1.35f
// Points sampled along each string when it is drawn.
private const val STRING_SAMPLES = 30
// Glow around a sounding string: a wide haze and a tight core.
private const val HAZE_WIDTH = 16f
private const val HAZE_BLUR = 11f
private const val CORE_WIDTH = 5f
private const val CORE_BLUR = 3.5f
// Note names rise this far (dp) over their life.
private const val NOTE_RISE = 74.0
private const val NOTE_SIZE = 26f

/**
 * A harp of eight glowing strings on a carved wooden frame, in a dark room. Drag across the
 * strings to pluck them: each one is caught by the finger, pulled aside, and slips free into a
 * ringing standing wave whose glow brightens with its swing. Neighbours shimmer in sympathy, note
 * names float up and fade, and a quick strum leaves a trail of light.
 *
 * @param autoPlay Plays an arpeggio, a strum down and a strum up, on a loop. Off by default.
 * @param title The small line above the harp; [hint] the line below it.
 */
@Composable
fun PluckStringHarp(
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false,
    title: String = "Eight strings · C major",
    hint: String = "Drag across the strings",
) {
    val reducedMotion = rememberReducedMotion()
    val auto = autoPlay && !reducedMotion
    val view = LocalView.current
    val density = LocalDensity.current
    val insets = WindowInsets.safeDrawing
    val safeTop = insets.getTop(density) / density.density
    val safeBottom = insets.getBottom(density) / density.density
    val sim = remember { HarpSim(auto, reducedMotion) }
    // Bumped once a frame so the live canvas redraws.
    var frame by remember { mutableLongStateOf(0L) }

    LaunchedEffect(auto, reducedMotion) {
        sim.looping = auto
        sim.still = reducedMotion
        sim.clock = 0.0
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Color(Room[2]))
            .semantics { contentDescription = "A harp with eight strings. Drag across the strings to play." },
    ) {
        val w = maxWidth.value.toDouble()
        val h = maxHeight.value.toDouble()
        val layout = remember(w, h, safeTop, safeBottom) { layoutHarp(w, h, safeTop.toDouble(), safeBottom.toDouble()) }
        val paints = remember(density) { HarpPaints(density.density) }
        val live = remember(density) { HarpLive(density.density) }

        LaunchedEffect(layout) {
            var last = -1L
            // Plucks already answered with a haptic.
            var answered = sim.userPlucks
            while (true) {
                withFrameNanos { now ->
                    val dt = if (last < 0) 1.0 / 60 else (now - last) / 1e9
                    last = now
                    sim.step(layout.geometry, dt)
                    if (sim.userPlucks > answered) {
                        answered = sim.userPlucks
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    }
                    frame = now
                }
            }
        }

        // Everything that never moves, drawn once.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer()
                .drawBehind { inDp { drawHarpFrame(it, paints, layout) } },
        )
        // The strings, their glow, the notes and the finger's light.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer()
                .drawBehind {
                    if (frame < 0) return@drawBehind
                    inDp { live.draw(it, sim, layout, reducedMotion) }
                }
                .pointerInput(sim) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        sim.fingerDown(down.position.x / density.density.toDouble(), down.position.y / density.density.toDouble())
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            change.consume()
                            sim.fingerMove(change.position.x / density.density.toDouble(), change.position.y / density.density.toDouble())
                        }
                        sim.fingerUp()
                    }
                },
        )

        BasicText(
            title.uppercase(),
            Modifier.fillMaxWidth().offset(y = (safeTop + 22f).dp),
            style = TextStyle(
                color = Color(LABEL),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 2.4.sp,
                textAlign = TextAlign.Center,
                fontFamily = FontFamily.SansSerif,
            ),
        )
        BasicText(
            hint,
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = (safeBottom + 26f).dp),
            style = TextStyle(
                color = Color(LABEL).copy(alpha = 0.75f),
                fontSize = 15.sp,
                fontStyle = FontStyle.Italic,
                textAlign = TextAlign.Center,
                fontFamily = FontFamily.Serif,
            ),
        )
    }
}

/** Runs [block] on the native canvas scaled to dp, like the Skia canvas of the RN version. */
private inline fun DrawScope.inDp(block: (Canvas) -> Unit) {
    drawIntoCanvas { canvas ->
        val c = canvas.nativeCanvas
        c.save()
        c.scale(density, density)
        block(c)
        c.restore()
    }
}

/** The string's colour between idle and hot, mixed per channel and rounded like the RN `mixColor`. */
private fun mix(a: Int, b: Int, t: Double): Int {
    fun channel(shift: Int): Int {
        val x = (a shr shift) and 0xff
        val y = (b shr shift) and 0xff
        return (x + (y - x) * t).roundToInt()
    }
    return (0xFF shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
}

private fun easeOut(t: Double) = 1 - (1 - t) * (1 - t) * (1 - t)

/** Draws every string, its glow and its vibration, the floating notes and the finger's light. */
private class HarpLive(density: Float) {
    private val p = HarpPaints(density)
    private val path = Path()
    private val band = Path()
    private val envelope = DoubleArray(STRING_SAMPLES + 1)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = NOTE_SIZE
        typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
    }
    private val noteWidths = FloatArray(NoteNames.size) { text.measureText(NoteNames[it]) }
    private var warm: RadialGradient? = null
    private var warmAt = Float.NaN

    fun draw(c: Canvas, sim: HarpSim, l: HarpLayout, still: Boolean) {
        val g = l.geometry
        // The room warms with the music.
        var energy = 0.0
        for (i in 0 until STRINGS) energy += sim.heat[i]
        energy = min(1.0, energy / 3)
        if (energy > 0.005) {
            val cx = l.centreX.toFloat()
            val cy = l.centreY.toFloat()
            val shader = warm?.takeIf { warmAt == cx + cy } ?: RadialGradient(
                cx, cy, 300f,
                intArrayOf(0x29FFAA50, 0x00FFAA50),
                null,
                Shader.TileMode.CLAMP,
            ).also {
                warm = it
                warmAt = cx + cy
            }
            c.drawCircle(cx, cy, 300f, p.shaded(shader, energy.toFloat()))
        }

        for (i in 0 until STRINGS) {
            val x = g.x[i]
            val y0 = g.top[i]
            val length = g.bottom[i] - y0
            val heat = sim.heat[i]
            path.rewind()
            var widest = 0.0
            for (k in 0..STRING_SAMPLES) {
                val s = k.toDouble() / STRING_SAMPLES
                val px = (x + sim.displacement(i, s)).toFloat()
                val py = (y0 + length * s).toFloat()
                if (k == 0) path.moveTo(px, py) else path.lineTo(px, py)
                envelope[k] = sim.envelope(i, s)
                widest = max(widest, envelope[k])
            }

            // The blur of a vibrating string: the whole width it sweeps, faintly lit.
            if (widest > 0.5) {
                band.rewind()
                for (k in 0..STRING_SAMPLES) {
                    val s = k.toDouble() / STRING_SAMPLES
                    val bx = (x + envelope[k]).toFloat()
                    val by = (y0 + length * s).toFloat()
                    if (k == 0) band.moveTo(bx, by) else band.lineTo(bx, by)
                }
                for (k in STRING_SAMPLES downTo 0) {
                    val s = k.toDouble() / STRING_SAMPLES
                    band.lineTo((x - envelope[k]).toFloat(), (y0 + length * s).toFloat())
                }
                band.close()
                c.drawPath(band, p.fill(GLOW, (0.2 * min(1.0, widest / 5)).toFloat(), 2.5f))
            }
            if (heat > 0.01) {
                c.drawPath(path, p.stroke(GLOW, HAZE_WIDTH, (0.3 * heat).toFloat(), HAZE_BLUR))
                c.drawPath(path, p.stroke(GLOW_CORE, CORE_WIDTH, (0.8 * heat).toFloat(), CORE_BLUR))
            }
            val gauge = GAUGE_LOW + ((GAUGE_HIGH - GAUGE_LOW) * i) / (STRINGS - 1)
            c.drawPath(path, p.stroke(mix(STRING_IDLE, STRING_HOT, min(1.0, heat * 1.15)), gauge))
            // A hairline of light along the string's lit side.
            c.save()
            c.translate(-gauge * 0.22f, 0f)
            c.drawPath(path, p.stroke(0xFFFFF6E6.toInt(), 0.7f, (0.18 + 0.55 * heat).toFloat()))
            c.restore()
        }

        // Note names drifting up from where each string was plucked.
        for (n in 0 until NOTE_POOL) {
            val age = sim.noteAge[n]
            if (age < 0) continue
            val t = age / NOTE_LIFE
            val alpha = min(1.0, age / 0.12) * max(0.0, 1 - t).pow(1.5)
            val string = sim.noteString[n]
            val rise = if (still) 0.0 else NOTE_RISE * easeOut(t)
            val nx = sim.noteX[n] + 16 + (if (still) 0.0 else sin(t * 3 + string) * 5)
            val ny = (sim.noteY[n] - 10 - rise).toFloat()
            val left = (nx - noteWidths[string] / 2).toFloat()
            text.color = GLOW
            text.alpha = (alpha * 0.7 * 255).roundToInt().coerceIn(0, 255)
            text.maskFilter = p.blur(6f)
            c.drawText(NoteNames[string], left, ny, text)
            text.color = NOTE_INK
            text.alpha = (alpha * 255).roundToInt().coerceIn(0, 255)
            text.maskFilter = null
            c.drawText(NoteNames[string], left, ny, text)
        }

        // The finger's light, and a short trail behind it.
        for (k in 0 until TRAIL) {
            val age = sim.time - sim.trailT[k]
            if (age > TRAIL_LIFE || age < 0) continue
            val t = age / TRAIL_LIFE
            c.drawCircle(
                sim.trailX[k].toFloat(),
                sim.trailY[k].toFloat(),
                (10 * (1 - t * 0.5)).toFloat(),
                p.fill(GLOW_CORE, (0.32 * (1 - t) * (1 - t)).toFloat(), 7f),
            )
        }
        if (sim.down) {
            c.drawCircle(sim.fx.toFloat(), sim.fy.toFloat(), 20f, p.fill(GLOW, 0.4f, 14f))
            c.drawCircle(sim.fx.toFloat(), sim.fy.toFloat(), 5f, p.fill(0xFFFFF4E2.toInt(), 0.85f, 2f))
        }
    }
}
