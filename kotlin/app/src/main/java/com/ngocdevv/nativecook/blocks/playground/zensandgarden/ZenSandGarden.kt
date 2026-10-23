package com.ngocdevv.nativecook.blocks.playground.zensandgarden

import android.graphics.BlurMaskFilter
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
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
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tan

// The rake follows the finger at FOLLOW per second (a fast first-order lag that keeps it off the
// stones), simulated in steps of SUBSTEP s. Its heading turns towards its motion at TURN per s.
private const val FOLLOW = 32f
private const val SUBSTEP = 1f / 240f
private const val TURN = 24f
// The rake keeps this share of its half width clear of a stone's outline, so the grooves bend
// round the stone instead of stopping at it.
private const val STONE_CLEARANCE = 0.85f
// The wooden rake drawn over the sand: a bar BAR_LENGTH × BAR_THICK dp and a handle trailing
// behind; it stands RAKE_LIFT dp above the sand for its shadow, and fades in or out over FADE s.
private const val BAR_LENGTH = (TINES - 1) * TINE_GAP + 18
private const val BAR_THICK = 8f
private const val HANDLE_LENGTH = 220f
private const val HANDLE_THICK = 6f
private const val RAKE_LIFT = 7f
private const val FADE = 0.18f
// A two-finger swipe longer than SMOOTH_SWIPE dp starts a sweep in its direction.
private const val SMOOTH_SWIPE = 40f
// The rake starts after PAN_SLOP dp; a touch lifted within TAP_MS that never got that far is a tap.
private const val PAN_SLOP = 4f
private const val TAP_MS = 500L
// A touch pauses autoPlay for this long after the finger lifts.
private const val USER_HOLD = 4f

// Light: a low warm sun whose bearing swings ±SUN_SWING° about SUN_BEARING° and whose height swings
// between SUN_LOW° and SUN_HIGH° once per LOOP, so the time of day drifts and still loops.
private const val SUN_BEARING = -138f
private const val SUN_SWING = 26f
private const val SUN_LOW = 17f
private const val SUN_HIGH = 25f
private val SunMorning = floatArrayOf(1.0f, 0.9f, 0.74f)
private val SunEvening = floatArrayOf(1.0f, 0.74f, 0.56f)
private val Sky = floatArrayOf(0.52f, 0.55f, 0.64f)

// autoPlay, one LOOP in seconds from smooth sand: a spiral, three rows of waves, the stones rung
// one after another, then the sweep, then rest.
private const val LOOP = 18f
private const val SPIRAL_FROM = 0.8f
private const val SPIRAL_TIME = 4.6f
private const val SPIRAL_CX = 0.63f
private const val SPIRAL_CY = 0.25f
private const val SPIRAL_TURNS = 2.4f
private const val SPIRAL_INNER = 16f
private const val SPIRAL_OUTER = 124f
private const val ROWS_FROM = 5.8f
private const val ROW_TIME = 1.3f
private const val ROW_PAUSE = 0.16f
private const val ROW_TOP = 0.66f
private const val ROW_GAP = RAKE_HALF * 2
private const val ROW_WAVE = 12f
private const val ROW_WAVELENGTH = 96f
private val RingAt = floatArrayOf(10.6f, 11.2f, 11.8f)
private const val SWEEP_AT = 13.2f

private val Hint = listOf("Drag to rake", "Tap a stone", "Two fingers to smooth")
private val Ink = Color(0xFF5A4A3A)
private val Ground = Color(0xFFD9CDB8)

private fun easeInOut(t: Float) = if (t < 0.5f) 4 * t * t * t else 1 - (-2 * t + 2).pow(3) / 2
private fun easeOut(t: Float) = 1 - (1 - t).pow(3)

/** The garden's state, stepped once a frame: the rake, the rings, the sweep and the autoPlay script. */
private class Garden {
    var map: ZenHeightMap? = null
    var w = 0f
    var h = 0f
    var elapsed = 0f
    var tick by mutableIntStateOf(0)

    // The rake: the finger (target), the head that follows it, its heading and the last section.
    var fingerDown = false
    var targetX = 0f
    var targetY = 0f
    var headX = 0f
    var headY = 0f
    var dirX = 1f
    var dirY = 0f
    var rakeAlpha = 0f
    var hasSection = false
    var lastX = 0f
    var lastY = 0f
    var lastNx = 0f
    var lastNy = 0f
    var lastStampX = 0f
    var lastStampY = 0f
    var newStroke = true

    // Ripples (start time per stone, < 0 when idle) and the sweep (direction 0 when idle).
    val ripples = floatArrayOf(-1f, -1f, -1f)
    var sweepDir = 0
    var sweepFrom = 0f

    // autoPlay's clock, how long a touch still holds it, and whether its finger is down.
    var clock = 0f
    var hold = 0f
    var autoFinger = false

    /** Puts the head outside every stone's clearance ring, sliding it round the outline. */
    fun keepOffStones() {
        var x = headX
        var y = headY
        for (s in Stones) {
            val qx = x - s.cx(w)
            val qy = y - s.cy(h)
            val l = hypot(qx, qy)
            val keep = s.radius(atan2(qy, qx)) + RAKE_HALF * STONE_CLEARANCE
            if (l < keep) {
                if (l < 0.001f) {
                    x = s.cx(w) + keep
                    y = s.cy(h)
                } else {
                    x = s.cx(w) + qx * keep / l
                    y = s.cy(h) + qy * keep / l
                }
            }
        }
        headX = x
        headY = y
    }

    fun press(x: Float, y: Float) {
        fingerDown = true
        targetX = x
        targetY = y
        headX = x
        headY = y
        keepOffStones()
        newStroke = true
        lastStampX = headX
        lastStampY = headY
    }

    fun ring(k: Int) {
        ripples[k] = elapsed
    }

    fun sweep(dir: Int) {
        sweepDir = dir
        sweepFrom = elapsed
    }

    fun touched() {
        hold = USER_HOLD
        if (autoFinger) {
            autoFinger = false
            fingerDown = false
        }
    }

    fun step(dt: Float, auto: Boolean, reducedMotion: Boolean) {
        val map = map ?: return
        val now = elapsed + dt
        elapsed = now

        // autoPlay drives the same finger, ripples and sweep a touch would.
        if (hold > 0f) {
            if (!fingerDown || autoFinger) hold -= dt
            if (hold <= 0f) clock = 0f
        } else if (auto) {
            val before = clock
            val t = (before + dt) % LOOP
            clock = t
            fun passed(at: Float) = (before < at && t >= at) || (t < before && (at > before || at <= t))
            val finger = scriptFinger(t)
            if (finger != null && !autoFinger) {
                autoFinger = true
                press(finger.x, finger.y)
            } else if (finger != null) {
                targetX = finger.x
                targetY = finger.y
            } else if (autoFinger) {
                autoFinger = false
                fingerDown = false
            }
            for (k in RingAt.indices) if (passed(RingAt[k])) ring(k)
            if (passed(SWEEP_AT)) sweep(1)
        }

        map.begin()
        // The head chases the finger in small steps, never entering a stone.
        val down = fingerDown
        if (down) {
            val steps = max(1, ceil(dt / SUBSTEP).toInt())
            val k = min(1f, FOLLOW * (dt / steps))
            repeat(steps) {
                headX += (targetX - headX) * k
                headY += (targetY - headY) * k
                keepOffStones()
            }
            val mx = headX - lastStampX
            val my = headY - lastStampY
            val moved = hypot(mx, my)
            if (moved >= STAMP_STEP) {
                val ux = mx / moved
                val uy = my / moved
                var dx = dirX
                var dy = dirY
                val turned = newStroke || dx * ux + dy * uy < -0.2f
                if (turned) {
                    dx = ux
                    dy = uy
                } else {
                    val turn = min(1f, TURN * dt)
                    dx += (ux - dx) * turn
                    dy += (uy - dy) * turn
                    val l = hypot(dx, dy).let { if (it == 0f) 1f else it }
                    dx /= l
                    dy /= l
                }
                dirX = dx
                dirY = dy
                if (!turned && hasSection) map.stamp(lastX, lastY, lastNx, lastNy, headX, headY, -dy, dx)
                hasSection = true
                lastX = headX
                lastY = headY
                lastNx = -dy
                lastNy = dx
                lastStampX = headX
                lastStampY = headY
                newStroke = false
            }
        }
        val fade = min(1f, dt / FADE)
        rakeAlpha += ((if (down) 1f else 0f) - rakeAlpha) * fade

        // Rings travelling out from struck stones.
        for (k in Stones.indices) {
            if (ripples[k] < 0f) continue
            val p = ((now - ripples[k]) / (if (reducedMotion) 0.01f else RIPPLE_TIME)).coerceIn(0f, 1f)
            val s = Stones[k]
            val inner = s.reach + 3
            map.ripple(s, inner + (RINGS * TINE_GAP + TINE_GAP * 0.6f) * easeOut(p))
            if (p >= 1f) ripples[k] = -1f
        }

        // The sweep: across the whole garden, then done.
        if (sweepDir != 0) {
            val p = ((now - sweepFrom) / (if (reducedMotion) 0.01f else SWEEP_TIME)).coerceIn(0f, 1f)
            val extent = if (sweepDir <= 2) h else w
            map.sweep(sweepDir, -PILE + (extent + PILE * 2 + 4) * easeInOut(p))
            if (p >= 1f) sweepDir = 0
        }
        map.end()
        tick++
    }

    /** Where autoPlay's finger is at `t` s into the loop, or null while it is up. */
    fun scriptFinger(t: Float): Offset? {
        val spiral = (t - SPIRAL_FROM) / SPIRAL_TIME
        if (spiral in 0f..1f) {
            val e = easeInOut(spiral) * 0.85f + spiral * 0.15f
            val r = SPIRAL_INNER + (SPIRAL_OUTER - SPIRAL_INNER) * e
            val a = -PI.toFloat() / 2 + e * SPIRAL_TURNS * PI.toFloat() * 2
            return Offset(SPIRAL_CX * w + cos(a) * r, SPIRAL_CY * h + sin(a) * r)
        }
        for (row in 0 until 3) {
            val start = ROWS_FROM + row * (ROW_TIME + ROW_PAUSE)
            val p = (t - start) / ROW_TIME
            if (p in 0f..1f) {
                val e = p * 0.7f + easeInOut(p) * 0.3f
                val x = if (row % 2 == 0) -50 + (w + 100) * e else w + 50 - (w + 100) * e
                val y = ROW_TOP * h + row * ROW_GAP + ROW_WAVE * sin(2 * PI.toFloat() * x / ROW_WAVELENGTH)
                return Offset(x, y)
            }
        }
        return null
    }
}

/**
 * A raked sand garden seen from above under a low warm sun. Dragging pulls a wooden rake through
 * the sand, leaving five grooves that bend round three mossy stones; tapping a stone sends rings
 * of grooves out round it; a two-finger swipe sweeps the sand level again. The sand's shape is a
 * height map drawn into a strip at a time and lit every frame by an AGSL shader (Android 13+);
 * below that the grooves are shaded straight from the height map.
 *
 * @param autoPlay Rakes a spiral and a field of waves, rings the three stones, then smooths the sand, on a loop.
 * @param hint The line of hints along the bottom edge.
 */
@Composable
fun ZenSandGarden(
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false,
    hint: List<String> = Hint,
) {
    val reducedMotion = rememberReducedMotion()
    val auto = autoPlay && !reducedMotion
    val view = LocalView.current
    val density = LocalDensity.current
    val garden = remember { Garden() }
    val paints = remember { GardenPaints() }
    val bottom = WindowInsets.safeDrawing.getBottom(density) / density.density

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Ground)
            .semantics { contentDescription = "Zen sand garden" },
    ) {
        val w = maxWidth.value
        val h = maxHeight.value
        LaunchedEffect(w, h) {
            // An unbounded or empty box (Infinity / 0) would ask for an impossible bitmap.
            val usable = w.isFinite() && h.isFinite() && w > 0f && h > 0f
            if (usable && (garden.map?.w != w || garden.map?.h != h)) {
                garden.w = w
                garden.h = h
                garden.map = ZenHeightMap(w, h)
            }
        }
        LaunchedEffect(garden, auto, reducedMotion) {
            var last = -1L
            while (true) {
                withFrameNanos { now ->
                    val dt = if (last < 0) 1f / 60f else min((now - last) / 1e9f, 0.05f)
                    last = now
                    if (dt > 0f) garden.step(dt, auto, reducedMotion)
                }
            }
        }

        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(garden) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val px = density.density
                        val sx = down.position.x / px
                        val sy = down.position.y / px
                        var panning = false
                        var multi = false
                        var swept = false
                        var twoFrom: Offset? = null
                        while (true) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.isEmpty()) {
                                val up = event.changes.firstOrNull { it.id == down.id }
                                if (!multi && !panning && up != null && up.uptimeMillis - down.uptimeMillis <= TAP_MS) {
                                    val x = up.position.x / px
                                    val y = up.position.y / px
                                    val k = Stones.indexOfFirst { it.distance(x, y, garden.w, garden.h) < 1.2f }
                                    if (k >= 0) {
                                        garden.touched()
                                        garden.ring(k)
                                        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                                    }
                                }
                                if (panning) garden.fingerDown = false
                                break
                            }
                            if (pressed.size >= 2) {
                                // Two fingers: the rake lets go, and a swipe sweeps the sand.
                                val c = Offset((pressed[0].position.x + pressed[1].position.x) / 2 / px, (pressed[0].position.y + pressed[1].position.y) / 2 / px)
                                if (!multi) {
                                    multi = true
                                    twoFrom = c
                                    garden.touched()
                                    if (panning) garden.fingerDown = false
                                    panning = false
                                }
                                val from = twoFrom ?: c
                                val tx = c.x - from.x
                                val ty = c.y - from.y
                                if (!swept && hypot(tx, ty) >= SMOOTH_SWIPE) {
                                    swept = true
                                    garden.sweep(if (abs(ty) >= abs(tx)) (if (ty > 0) 1 else 2) else if (tx > 0) 3 else 4)
                                    confirm(view)
                                }
                                event.changes.forEach { it.consume() }
                                continue
                            }
                            if (multi) continue
                            val change = event.changes.firstOrNull { it.id == down.id && it.pressed } ?: pressed[0]
                            val x = change.position.x / px
                            val y = change.position.y / px
                            if (!panning) {
                                if (hypot(x - sx, y - sy) >= PAN_SLOP) {
                                    panning = true
                                    garden.touched()
                                    garden.press(sx, sy)
                                    garden.targetX = x
                                    garden.targetY = y
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                }
                            } else {
                                garden.targetX = x
                                garden.targetY = y
                            }
                            change.consume()
                        }
                    }
                },
        ) {
            garden.tick
            val map = garden.map ?: return@Canvas
            scale(density.density, density.density, pivot = Offset.Zero) {
                drawGarden(garden, map, paints)
            }
        }

        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = (bottom + 14).dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            hint.forEachIndexed { i, line ->
                if (i > 0) Box(Modifier.size(3.dp).alpha(0.35f).background(Ink, CircleShape))
                BasicText(
                    line,
                    modifier = Modifier.alpha(0.62f),
                    maxLines = 1,
                    style = TextStyle(color = Ink, fontSize = 12.sp, letterSpacing = 0.2.sp, fontWeight = FontWeight.Medium),
                )
            }
        }
    }
}

private fun confirm(view: View) {
    view.performHapticFeedback(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.CONTEXT_CLICK,
    )
}

/** Paints and the shader, made once. */
private class GardenPaints {
    val shader: RuntimeShader? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) RuntimeShader(SAND_AGSL) else null
    val sand = Paint()
    val rake = Paint().apply { isAntiAlias = true }
    val handleShadow = Paint().apply {
        isAntiAlias = true
        maskFilter = BlurMaskFilter(sigmaToRadius(3.5f), BlurMaskFilter.Blur.NORMAL)
    }
    val barShadow = Paint().apply {
        isAntiAlias = true
        maskFilter = BlurMaskFilter(sigmaToRadius(3f), BlurMaskFilter.Blur.NORMAL)
    }
    // Below Android 13: the height map itself, lit flat — grooves darker, ridges lighter — then the
    // stones as shaded discs with their shadows.
    val shaded = Paint().apply {
        isFilterBitmap = true
        colorFilter = ColorMatrixColorFilter(
            ColorMatrix(
                floatArrayOf(
                    0.45f, 0f, 0f, 0f, 173f,
                    0.45f, 0f, 0f, 0f, 158f,
                    0.42f, 0f, 0f, 0f, 138f,
                    0f, 0f, 0f, 0f, 255f,
                ),
            ),
        )
    }
    val stone = Paint().apply { isAntiAlias = true }
    val stonePaths = HashMap<Int, Path>()
    // The garden size the cached stone outlines were made for.
    var stonePathsW = 0f
    var stonePathsH = 0f
}

private fun DrawScope.drawGarden(garden: Garden, map: ZenHeightMap, paints: GardenPaints) {
    val w = garden.w
    val h = garden.h
    // The light of the moment.
    val phase = garden.elapsed / LOOP * PI.toFloat() * 2
    val day = sin(phase)
    val bearing = (SUN_BEARING + SUN_SWING * day) * PI.toFloat() / 180
    val rise = (SUN_LOW + (SUN_HIGH - SUN_LOW) * (0.5f + 0.5f * cos(phase))) * PI.toFloat() / 180
    val sx = cos(rise) * cos(bearing)
    val sy = cos(rise) * sin(bearing)
    val sz = sin(rise)
    val warm = 0.5f + 0.5f * day

    drawIntoCanvas { c ->
        val canvas = c.nativeCanvas
        val shader = paints.shader
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && shader != null) {
            shader.setInputShader("heightMap", map.shader)
            shader.setFloatUniform("size", w, h)
            shader.setFloatUniform("scale", HEIGHT_SCALE)
            shader.setFloatUniform("sun", sx, sy, sz)
            shader.setFloatUniform(
                "sunColor",
                SunMorning[0] + (SunEvening[0] - SunMorning[0]) * warm,
                SunMorning[1] + (SunEvening[1] - SunMorning[1]) * warm,
                SunMorning[2] + (SunEvening[2] - SunMorning[2]) * warm,
            )
            shader.setFloatUniform("ambient", Sky[0], Sky[1], Sky[2])
            val names = arrayOf("a", "b", "c")
            for (k in Stones.indices) {
                val s = Stones[k]
                val n = names[k]
                shader.setFloatUniform("${n}Pos", s.cx(w), s.cy(h), s.r, s.h)
                shader.setFloatUniform("${n}Shape", s.a2, s.p2, s.a3, s.p3)
                shader.setFloatUniform("${n}Shape2", s.a5, s.p5)
                shader.setFloatUniform("${n}Tint", s.tint[0], s.tint[1], s.tint[2], s.moss)
            }
            shader.setFloatUniform("time", garden.elapsed)
            paints.sand.shader = shader
            canvas.drawRect(0f, 0f, w, h, paints.sand)
        } else {
            drawFallback(canvas, garden, map, paints, sx, sy)
        }

        // The rake, standing a little above the sand, its shadow cast away from the sun.
        val alpha = garden.rakeAlpha
        if (alpha > 0.01f) {
            val angle = Math.toDegrees(atan2(garden.dirY, garden.dirX).toDouble()).toFloat()
            val reach = RAKE_LIFT / max(0.2f, tan(rise))
            val ox = cos(bearing) * reach
            val oy = sin(bearing) * reach
            drawRake(canvas, paints, garden.headX - ox, garden.headY - oy, angle, alpha, true)
            drawRake(canvas, paints, garden.headX, garden.headY, angle, alpha, false)
        }
    }
}

/** Below Android 13: the height map shaded flat, and the stones as lit discs with soft shadows. */
private fun drawFallback(canvas: android.graphics.Canvas, garden: Garden, map: ZenHeightMap, paints: GardenPaints, sx: Float, sy: Float) {
    canvas.save()
    canvas.scale(1 / HEIGHT_SCALE, 1 / HEIGHT_SCALE)
    canvas.drawBitmap(map.bitmap, 0f, 0f, paints.shaded)
    canvas.restore()
    val l = hypot(sx, sy).let { if (it == 0f) 1f else it }
    if (paints.stonePathsW != garden.w || paints.stonePathsH != garden.h) {
        paints.stonePaths.clear()
        paints.stonePathsW = garden.w
        paints.stonePathsH = garden.h
    }
    for (k in Stones.indices) {
        val s = Stones[k]
        val path = paints.stonePaths.getOrPut(k) {
            Path().apply {
                for (i in 0..72) {
                    val th = i / 72f * 2 * PI.toFloat()
                    val r = s.radius(th)
                    val x = s.cx(garden.w) + cos(th) * r
                    val y = s.cy(garden.h) + sin(th) * r
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
                close()
            }
        }
        // The long shadow away from the sun.
        canvas.save()
        canvas.translate(-sx / l * s.h * 0.5f, -sy / l * s.h * 0.5f)
        paints.stone.shader = null
        paints.stone.color = android.graphics.Color.argb(56, 60, 44, 28)
        canvas.drawPath(path, paints.stone)
        canvas.restore()
        val cx = s.cx(garden.w)
        val cy = s.cy(garden.h)
        val (r, g, b) = Triple(s.tint[0], s.tint[1], s.tint[2])
        fun c(f: Float) = android.graphics.Color.rgb((r * f * 255).coerceAtMost(255f).toInt(), (g * f * 255).coerceAtMost(255f).toInt(), (b * f * 255).coerceAtMost(255f).toInt())
        paints.stone.color = android.graphics.Color.BLACK
        paints.stone.shader = RadialGradient(
            cx + sx / l * s.r * 0.35f, cy + sy / l * s.r * 0.35f, s.r * 1.4f,
            intArrayOf(c(1.9f), c(1.3f), c(0.7f)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP,
        )
        canvas.drawPath(path, paints.stone)
        paints.stone.shader = null
    }
}

/** The wooden rake at (x, y) heading `angle`°, or its blurred shadow. */
private fun drawRake(canvas: android.graphics.Canvas, paints: GardenPaints, x: Float, y: Float, angle: Float, alpha: Float, shadow: Boolean) {
    canvas.save()
    canvas.translate(x, y)
    canvas.rotate(angle)
    fun argb(r: Int, g: Int, b: Int, a: Float) = android.graphics.Color.argb((a.coerceIn(0f, 1f) * 255).toInt(), r, g, b)
    if (shadow) {
        paints.handleShadow.color = argb(40, 26, 12, 0.3f * alpha)
        paints.barShadow.color = argb(40, 26, 12, 0.34f * alpha)
        canvas.drawRoundRect(-HANDLE_LENGTH, -HANDLE_THICK / 2, -2f, HANDLE_THICK / 2, 3f, 3f, paints.handleShadow)
        canvas.drawRoundRect(-BAR_THICK / 2, -BAR_LENGTH / 2, BAR_THICK / 2, BAR_LENGTH / 2, 3f, 3f, paints.barShadow)
    } else {
        val p = paints.rake
        p.shader = LinearGradient(
            0f, -HANDLE_THICK / 2, 0f, HANDLE_THICK / 2,
            intArrayOf(argb(196, 160, 112, alpha), argb(150, 108, 66, alpha), argb(104, 70, 40, alpha)),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP,
        )
        canvas.drawRoundRect(-HANDLE_LENGTH, -HANDLE_THICK / 2, -2f, HANDLE_THICK / 2, 3f, 3f, p)
        p.shader = LinearGradient(
            -BAR_THICK / 2, 0f, BAR_THICK / 2, 0f,
            intArrayOf(argb(112, 76, 44, alpha), argb(206, 168, 118, alpha), argb(150, 106, 62, alpha), argb(92, 60, 34, alpha)),
            floatArrayOf(0f, 0.3f, 0.7f, 1f), Shader.TileMode.CLAMP,
        )
        canvas.drawRoundRect(-BAR_THICK / 2, -BAR_LENGTH / 2, BAR_THICK / 2, BAR_LENGTH / 2, 3f, 3f, p)
        p.shader = null
        p.color = argb(70, 46, 26, 0.8f * alpha)
        canvas.drawCircle(0f, 0f, 1.6f, p)
    }
    canvas.restore()
}
