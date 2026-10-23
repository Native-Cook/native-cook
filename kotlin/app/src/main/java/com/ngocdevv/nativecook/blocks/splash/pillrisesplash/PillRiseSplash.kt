package com.ngocdevv.nativecook.blocks.splash.pillrisesplash

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan
import kotlinx.coroutines.launch

private fun unsplash(id: String, w: Int) = "https://images.unsplash.com/$id?auto=format&fit=crop&w=$w&q=80"

// The colour that fills the capsule as it rises: an iridescent swirl, drawn full screen.
private const val REVEAL_PHOTO = "photo-1620421680010-0766ff230392"
private const val REVEAL_WIDTH = 1200
private const val JAR_PHOTO_WIDTH = 360

private const val STAGE_REF_WIDTH = 402f
private const val STAGE_REF_HEIGHT = 470f

private val LIME = Color(0xFFA3EB6E)
private val INK = Color(0xFF173300)
private val NIGHT = Color(0xFF161A14)
private val HEADLINE = listOf("90 countries,", "30 currencies.", "One wallet")

// The capsule: PILL_W wide, its top a half circle. The splash's progress `s` runs 0 (hidden under
// the bottom edge) → PEEK (a dark capsule holding the button) → 0.48 (risen to 30% of the screen)
// → 0.68 (touching the top, a little wider) → 1 (the whole screen).
private const val PILL_W = 68f
private const val PEEK = 0.12f
private const val BUTTON_R = 24f
// How far above the home indicator the button sits while the capsule peeks.
private const val PEEK_LIFT = 64f
// Drag: a full screen of travel times this share moves `s` from 0 to 1. A release commits when
// it would carry `s` past COMMIT within THROW s.
private const val DRAG_SPAN = 0.85f
private const val COMMIT = 0.42f
private const val THROW = 0.22f
private const val DRAG_SLOP = 6f
private const val TAP_MS = 400L
private const val CTA_TAP_MS = 600L
// Splash timings, s.
private const val PEEK_S = 0.52f
private const val RISE_S = 1.5f
private const val BOARD_DELAY = 0.12f
private const val BOARD_S = 1.4f
private const val EXIT_S = 0.65f
private const val LOGO_S = 0.52f

// Jar drag: pulled further than RUBBER dp, a jar gives less and less. Released, it springs home
// with the finger's speed (stiffness JAR_K, damping JAR_C per unit mass: under-damped, it wobbles).
private const val RUBBER = 160f
private const val JAR_K = 220f
private const val JAR_C = 13f
private const val JAR_GRAB = 90f
// The line the coins appear on, below the progress bar.
private const val COIN_LINE = 34f
private val COIN_DX = floatArrayOf(-14f, 11f, -7f, 16f, -10f)

// autoPlay script (s). One loop starts and ends on the resting splash.
private const val LOOP = 11f
private const val PEEK_AT = 0.6f
private const val RISE_AT = 1.4f
private val COIN_AT = floatArrayOf(4.5f, 5.25f, 5.95f, 6.7f, 7.4f)
private val COIN_JAR = intArrayOf(0, 2, 1, 0, 2)
private const val PRESS_AT = 9.0f
private const val EXIT_AT = 9.15f
// A touch pauses the script for USER_HOLD s; it resumes at the matching point of the loop.
private const val USER_HOLD = 4f
private const val RESUME_SPLASH = PEEK_AT - 0.2f
private const val RESUME_BOARD = 8.2f

private const val HEADLINE_SIZE = 42f
private const val HEADLINE_LINE = 41f

// An invented mark: two slanted bars passing each other, like a transfer.
private const val MARK_SIZE = 64f
private const val MARK_SCALE = 1.35f
private const val MARK_SKEW = -0.22f

private const val CTA_HEIGHT = 56f
private const val CTA_LEFT = 20f

/** Where everything sits for a screen size, in dp: the board bottom up, the jars in what is left. */
private class PillRiseLayout(val w: Float, val h: Float, val top: Float, val bottom: Float, val lines: Int) {
    val ctaTop = h - max(bottom, 16f) - 8f - CTA_HEIGHT
    val ctaWidth = w - CTA_LEFT * 2f
    val linkTop = ctaTop - 52f
    val headlineTop = linkTop - 18f - HEADLINE_LINE * lines - 6f
    val barTop = top + 14f
    val stageTop = barTop + 24f
    val stageBottom = headlineTop - 16f
    val stageMid = (stageTop + stageBottom) / 2f
    val k = minOf(w / STAGE_REF_WIDTH, (stageBottom - stageTop) / STAGE_REF_HEIGHT, 1.25f)
    val peekTop = h - max(bottom, 12f) - PEEK_LIFT - BUTTON_R - 10f
    val jars = PillRiseJars.map { spec ->
        val x = w / 2f + spec.x * k
        val y = stageMid + spec.y * k
        PillRisePlacedJar(spec, k, x, y, y - (stageTop + COIN_LINE))
    }

    fun onCta(x: Float, y: Float) = x >= CTA_LEFT && x <= CTA_LEFT + ctaWidth && y >= ctaTop && y <= ctaTop + CTA_HEIGHT

    fun same(w: Float, h: Float, top: Float, bottom: Float, lines: Int) =
        this.w == w && this.h == h && this.top == top && this.bottom == bottom && this.lines == lines
}

/** Everything the frame loop, the touches and the picture share, kept off Compose state. */
private class PillRiseMotion {
    // Splash progress (see PEEK), board entrance, the way back, and the mark's own fade.
    val s = PillRiseValue(0f)
    val board = PillRiseValue(0f)
    val exit = PillRiseValue(0f)
    val logoIn = PillRiseValue(1f)
    val press = PillRiseValue(0f)
    // 0 splash, 1 opening or board, 2 on the way back.
    var phase = 0
    // Seconds since the block appeared (drives the floating and the coins), and the autoPlay clock.
    var time = 0f
    var clock = 0f
    var looping = false
    var hold = 0f
    val jarFrame = PillRiseJarFrame()
    var nextSlot = 0
    val speedY = FloatArray(3)
    // What the current pan moves: 0 nothing, 1 the capsule, 2 a jar.
    var panMode = 0
    var grabbed = -1
    var grabS = 0f
    var grabX = 0f
    var grabY = 0f
    var layout: PillRiseLayout? = null
    var reducedMotion = false
    val images = arrayOfNulls<Bitmap>(4)
}

/**
 * A lime splash with a mark in the middle. A dark capsule rises from the bottom edge holding an
 * arrow button, fills with colour and grows into the whole screen, pushing the mark away; then
 * the board settles in: three glazed jars floating in the dark, coins dropping into them, a big
 * headline and a lime button. Drag the capsule up (or tap) to open it, drag the jars, tap a jar
 * for a coin, and press the button to go back to the splash.
 *
 * @param autoPlay Plays the splash, the reveal, a few coins and the way back, on a loop.
 * @param headline The headline, one entry per line.
 * @param lime Splash and button colour; [ink] the mark on it; [night] the board's background.
 */
@Composable
fun PillRiseSplash(
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false,
    headline: List<String> = HEADLINE,
    link: String = "See our rates",
    cta: String = "Get started",
    lime: Color = LIME,
    ink: Color = INK,
    night: Color = NIGHT,
    onGetStarted: (() -> Unit)? = null,
) {
    val reducedMotion = rememberReducedMotion()
    val auto = autoPlay && !reducedMotion
    val view = LocalView.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val started by rememberUpdatedState(onGetStarted)
    val motion = remember { PillRiseMotion() }
    motion.reducedMotion = reducedMotion
    // Bumped once a frame so the canvas draws again.
    val tick = remember { mutableIntStateOf(0) }

    // Read during composition, so the block is laid out again once the insets arrive.
    val insets = WindowInsets.safeDrawing
    val safeTop = insets.getTop(density) / density.density
    val safeBottom = insets.getBottom(density) / density.density

    fun thud() {
        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    }

    fun peek(delay: Float) {
        if (motion.phase != 0) return
        motion.s.timing(PEEK, if (reducedMotion) 0f else PEEK_S, motion.time, PillEaseOut, delay)
    }

    fun startBoard() {
        motion.board.timing(1f, if (reducedMotion) 0.5f else BOARD_S, motion.time, PillLinear, BOARD_DELAY)
    }

    /** Opens the capsule into the board. [velocity] is the finger's, in units of `s` per second. */
    fun commit(user: Boolean, velocity: Float) {
        if (motion.phase != 0) return
        motion.phase = 1
        if (user) thud()
        when {
            reducedMotion -> motion.s.timing(1f, 0.42f, motion.time, PillStandard, then = ::startBoard)
            user -> motion.s.spring(1f, velocity, 1.3f, 1f, clampOvershoot = true, then = ::startBoard)
            else -> motion.s.timing(1f, RISE_S, motion.time, PillRiseEase, then = ::startBoard)
        }
    }

    /** Back to the resting splash: everything resets under the full-screen lime. */
    fun reset() {
        val m = motion
        m.s.set(0f)
        m.board.set(0f)
        m.exit.set(0f)
        m.phase = 0
        m.jarFrame.coins = Array(COIN_SLOTS) { PillNoCoin }
        m.jarFrame.offsetX.fill(0f)
        m.jarFrame.offsetY.fill(0f)
        m.jarFrame.speedX.fill(0f)
        m.speedY.fill(0f)
        m.logoIn.set(0f)
        m.logoIn.timing(1f, LOGO_S, m.time, PillEaseOut)
        if (!m.looping || m.hold > 0f) peek(LOGO_S + 0.4f)
    }

    /** The button grows into a full-screen lime and the splash comes back. */
    fun leave(user: Boolean) {
        if (motion.phase != 1 || motion.board.value < 0.9f) return
        motion.phase = 2
        if (user) {
            thud()
            started?.invoke()
        }
        motion.exit.timing(1f, if (reducedMotion) 0.3f else EXIT_S, motion.time, if (reducedMotion) PillStandard else PillExitEase, then = ::reset)
    }

    /** Drops a coin into jar [jar]. */
    fun drop(jar: Int, user: Boolean) {
        val slot = motion.nextSlot
        motion.nextSlot = (slot + 1) % COIN_SLOTS
        motion.jarFrame.coins[slot] = PillRiseCoin(motion.time, jar, COIN_DX[slot], user)
    }

    fun pressTo(target: Float, seconds: Float) {
        motion.press.timing(target, seconds, motion.time, PillStandard)
    }

    fun advance(dt: Float) {
        val m = motion
        val layout = m.layout ?: return
        val before = m.time
        val now = before + dt
        m.time = now
        listOfNotNull(
            m.s.step(dt, now), m.board.step(dt, now), m.exit.step(dt, now), m.logoIn.step(dt, now), m.press.step(dt, now),
        ).forEach { it() }

        // A coin passing through a rim rings its jar; one a finger dropped gets a haptic too.
        val f = m.jarFrame
        for (c in f.coins) {
            if (c.t0 < 0f) continue
            val at = c.t0 + pillLandingTime(layout.jars[c.jar].drop)
            if (before < at && now >= at) {
                f.landed[c.jar] = at
                if (c.user) thud()
            }
        }

        // Jars not under a finger spring home, carrying the speed they were let go with.
        for (i in 0 until 3) {
            if (i == m.grabbed) continue
            if (abs(f.offsetX[i]) + abs(f.offsetY[i]) + abs(f.speedX[i]) + abs(m.speedY[i]) < 0.05f) continue
            f.speedX[i] += (-JAR_K * f.offsetX[i] - JAR_C * f.speedX[i]) * dt
            m.speedY[i] += (-JAR_K * f.offsetY[i] - JAR_C * m.speedY[i]) * dt
            f.offsetX[i] += f.speedX[i] * dt
            f.offsetY[i] += m.speedY[i] * dt
        }

        // The script.
        if (m.hold > 0f) {
            m.hold -= dt
            if (m.hold <= 0f && m.looping) {
                m.clock = when (m.phase) {
                    0 -> RESUME_SPLASH
                    1 -> RESUME_BOARD
                    else -> EXIT_AT + 0.01f
                }
            }
            return
        }
        if (!m.looping) return
        val t0 = m.clock
        val t = (t0 + dt) % LOOP
        m.clock = t
        fun passed(at: Float) = (t0 < at && t >= at) || (t < t0 && (at > t0 || at <= t))
        if (passed(PEEK_AT)) peek(0f)
        if (passed(RISE_AT)) commit(false, 0f)
        for (i in COIN_AT.indices) {
            if (passed(COIN_AT[i]) && m.phase == 1 && m.board.value > 0.9f) drop(COIN_JAR[i], false)
        }
        if (passed(PRESS_AT) && m.phase == 1) {
            m.press.timing(1f, 0.11f, now, PillStandard) { m.press.timing(0f, 0.2f, m.time, PillStandard) }
        }
        if (passed(EXIT_AT)) leave(false)
    }

    LaunchedEffect(motion, auto) {
        motion.looping = auto
        motion.clock = 0f
        motion.hold = 0f
        if (!auto) peek(0.7f)
    }

    LaunchedEffect(motion, reducedMotion) {
        var last = -1L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last < 0) 1f / 60f else min((now - last) / 1e9f, 0.064f)
                last = now
                if (dt > 0f) advance(dt)
                tick.intValue += 1
            }
        }
    }

    LaunchedEffect(motion, context) {
        val loader = SingletonImageLoader.get(context)
        val sources = listOf(unsplash(REVEAL_PHOTO, REVEAL_WIDTH)) + PillRiseJars.map { unsplash(it.photo, JAR_PHOTO_WIDTH) }
        sources.forEachIndexed { i, address ->
            launch {
                // A software bitmap, so it can be painted through a shader.
                val request = ImageRequest.Builder(context).data(address).allowHardware(false).build()
                val result = loader.execute(request) as? SuccessResult ?: return@launch
                motion.images[i] = result.image.toBitmap()
            }
        }
    }

    /** The jar nearest a point on screen, or -1 when none is within reach. */
    fun jarAt(x: Float, y: Float, reach: Float): Int {
        val layout = motion.layout ?: return -1
        val f = motion.jarFrame
        var best = -1
        var bestD = reach
        layout.jars.forEachIndexed { i, jar ->
            val d = hypot(x - (jar.x + f.offsetX[i]), y - (jar.mid + f.offsetY[i]))
            if (d < bestD) {
                bestD = d
                best = i
            }
        }
        return best
    }

    fun panStart(x: Float, y: Float) {
        val m = motion
        m.panMode = 0
        // A jar left grabbed by a gesture the system cut short springs home again.
        m.grabbed = -1
        m.hold = USER_HOLD
        if (m.phase == 0 && !reducedMotion) {
            m.s.stop()
            m.grabS = m.s.value
            m.panMode = 1
        } else if (m.phase == 1 && m.board.value > 0.9f) {
            val j = jarAt(x, y, JAR_GRAB * (m.layout?.k ?: 1f) + 20f)
            if (j < 0) return
            m.grabbed = j
            m.grabX = m.jarFrame.offsetX[j]
            m.grabY = m.jarFrame.offsetY[j]
            m.panMode = 2
        }
    }

    fun panUpdate(dx: Float, dy: Float, velocityX: Float) {
        val m = motion
        val layout = m.layout ?: return
        if (m.panMode == 1) {
            val raw = m.grabS - dy / (layout.h * DRAG_SPAN)
            // Below the resting peek the capsule resists; it never tears off the bottom edge.
            m.s.set(if (raw < PEEK) PEEK - pillRubber(PEEK - raw, 0f) * 0.35f else min(raw, 0.97f))
        } else if (m.panMode == 2 && m.grabbed in 0 until 3) {
            val j = m.grabbed
            m.jarFrame.offsetX[j] = pillRubber(m.grabX + dx, RUBBER)
            m.jarFrame.offsetY[j] = pillRubber(m.grabY + dy, RUBBER)
            // The finger's speed tilts the jar while it is carried.
            m.jarFrame.speedX[j] = velocityX
        }
    }

    fun panEnd(velocityX: Float, velocityY: Float) {
        val m = motion
        val layout = m.layout ?: return
        if (m.panMode == 1) {
            val velocity = -velocityY / (layout.h * DRAG_SPAN)
            if (m.s.value + velocity * THROW > COMMIT) commit(true, velocity)
            else m.s.spring(PEEK, velocity, 0.45f, 0.8f)
        } else if (m.panMode == 2 && m.grabbed in 0 until 3) {
            m.jarFrame.speedX[m.grabbed] = velocityX
            m.speedY[m.grabbed] = velocityY
        }
        m.panMode = 0
        m.grabbed = -1
    }

    fun tap(x: Float, y: Float) {
        val m = motion
        val layout = m.layout ?: return
        m.hold = USER_HOLD
        if (m.phase == 0) {
            commit(true, 0f)
        } else if (m.phase == 1 && m.board.value > 0.9f && y > layout.stageTop && y < layout.stageBottom) {
            val j = jarAt(x, y, 1000f)
            if (j >= 0) drop(j, true)
        }
    }

    val paints = remember { PillRisePaints() }
    val fontScale = density.fontScale

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .semantics {
                role = Role.Button
                contentDescription = "${headline.joinToString(" ")}. $cta"
            }
            .pointerInput(motion, reducedMotion) {
                val tracker = VelocityTracker()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val start = down.position
                    val dp = density.density
                    tracker.resetTracking()
                    tracker.addPointerInputChange(down)
                    var panning = false
                    var released = false
                    // One loop stands in for the race of a pan (after 6 dp of travel) and a tap,
                    // plus the button's own tap.
                    var onCta = motion.phase == 1 && motion.layout?.onCta(start.x / dp, start.y / dp) == true
                    if (onCta) pressTo(1f, 0.11f)
                    while (!released) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        tracker.addPointerInputChange(change)
                        val dx = (change.position.x - start.x) / dp
                        val dy = (change.position.y - start.y) / dp
                        if (!change.pressed) {
                            val held = change.uptimeMillis - down.uptimeMillis
                            if (panning) {
                                change.consume()
                                val v = tracker.calculateVelocity()
                                panEnd(v.x / dp, v.y / dp)
                            } else if (onCta) {
                                if (held <= CTA_TAP_MS) {
                                    motion.hold = USER_HOLD
                                    leave(true)
                                }
                            } else if (held <= TAP_MS) {
                                tap(change.position.x / dp, change.position.y / dp)
                            }
                            if (onCta) pressTo(0f, 0.18f)
                            released = true
                            continue
                        }
                        if (!panning && sqrt(dx * dx + dy * dy) >= DRAG_SLOP) {
                            panning = true
                            // The button's tap fails once the finger travels.
                            if (onCta) {
                                onCta = false
                                pressTo(0f, 0.18f)
                            }
                            panStart(change.position.x / dp, change.position.y / dp)
                        }
                        if (panning) {
                            change.consume()
                            panUpdate(dx, dy, tracker.calculateVelocity().x / dp)
                        }
                    }
                    // A finger lost to the system still lets go.
                    if (panning && !released) panEnd(0f, 0f)
                    if (onCta && !released) pressTo(0f, 0.18f)
                }
            },
    ) {
        val w = maxWidth.value
        val h = maxHeight.value
        val current = motion.layout
        if (current == null || !current.same(w, h, safeTop, safeBottom, headline.size)) {
            motion.layout = PillRiseLayout(w, h, safeTop, safeBottom, headline.size)
        }

        Canvas(Modifier.fillMaxSize()) {
            tick.intValue
            scale(density.density, density.density, pivot = Offset.Zero) {
                drawIntoCanvas {
                    val layout = motion.layout ?: return@drawIntoCanvas
                    drawPillRise(
                        it.nativeCanvas, motion, layout, paints, headline, link, cta,
                        lime.toArgb(), ink.toArgb(), night.toArgb(), fontScale, density.density,
                    )
                }
            }
        }
    }
}

/** Reused paints and paths for the splash and the board. */
private class PillRisePaints {
    val fill = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    val headline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        val condensed = Typeface.create("sans-serif-condensed", Typeface.NORMAL)
        typeface = if (Build.VERSION.SDK_INT >= 28) Typeface.create(condensed, 900, false) else Typeface.create(condensed, Typeface.BOLD)
        color = 0xFFF2F5EC.toInt()
    }
    val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = if (Build.VERSION.SDK_INT >= 28) Typeface.create(Typeface.DEFAULT, 600, false) else Typeface.DEFAULT_BOLD
    }
    val jarPaints = PillRiseJarPaints()
    val rect = RectF()
    val path = Path()
    val matrix = Matrix()
    val pillGradient = HashMap<Long, LinearGradient>()
    val mark = Path().apply {
        moveTo(14f, 16f); lineTo(44f, 16f); lineTo(54f, 24.5f); lineTo(44f, 33f); lineTo(14f, 33f); close()
        moveTo(50f, 37f); lineTo(20f, 37f); lineTo(10f, 45.5f); lineTo(20f, 54f); lineTo(50f, 54f); close()
    }
    val arrow = Path().apply {
        moveTo(0f, 9f); lineTo(0f, -9f)
        moveTo(-7f, -2f); lineTo(0f, -9f); lineTo(7f, -2f)
    }
}

private fun withAlpha(color: Int, alpha: Float): Int =
    (color and 0x00FFFFFF) or ((((color ushr 24) and 0xFF) * alpha.coerceIn(0f, 1f)).toInt() shl 24)

/** Draws the photo cropped to cover [dst]. */
private fun drawCover(canvas: Canvas, image: Bitmap, dst: RectF, paint: Paint) {
    val scale = max(dst.width() / image.width, dst.height() / image.height)
    val w = image.width * scale
    val h = image.height * scale
    val save = canvas.save()
    canvas.clipRect(dst)
    canvas.translate(dst.centerX() - w / 2f, dst.centerY() - h / 2f)
    canvas.scale(scale, scale)
    canvas.drawBitmap(image, 0f, 0f, paint)
    canvas.restoreToCount(save)
}

/** One frame: the splash (mark, capsule, arrow button), the board over it and the way back. */
private fun drawPillRise(
    canvas: Canvas,
    m: PillRiseMotion,
    l: PillRiseLayout,
    p: PillRisePaints,
    headline: List<String>,
    link: String,
    cta: String,
    lime: Int,
    ink: Int,
    night: Int,
    fontScale: Float,
    density: Float,
) {
    val w = l.w
    val h = l.h
    val reduced = m.reducedMotion
    val s = m.s.value
    canvas.drawColor(lime)

    // ——— The splash ———
    // Without big motion the capsule stays at its peek and the colour fades in instead.
    val shape = if (reduced) min(s, PEEK) else s
    val pillTop = pillLerp(shape, floatArrayOf(0f, PEEK, 0.48f, 0.68f, 1f), floatArrayOf(h + 2f, l.peekTop, h * 0.3f, 0f, 0f))

    // The capsule pushes the mark up as its top passes it; the mark fades on the way.
    val mid = h / 2f
    val push = max(0f, mid + MARK_SIZE / 2f + 24f - pillTop)
    val markAlpha = m.logoIn.value * (1f - pillSeg(push, 20f, 150f)) * (1f - pillSeg(s, PEEK, 0.6f) * (if (reduced) 1f else 0f))
    if (markAlpha > 0f) {
        val save = canvas.save()
        canvas.translate(w / 2f, mid - push * (if (reduced) 0f else 0.85f))
        val grow = (0.92f + 0.08f * PillEaseOut.transform(m.logoIn.value)) * MARK_SCALE
        canvas.scale(grow, grow)
        // Skia's skewX leans the mark's rows: the right side rises.
        p.matrix.reset()
        p.matrix.setSkew(0f, tan(MARK_SKEW))
        canvas.concat(p.matrix)
        canvas.translate(-MARK_SIZE / 2f, -MARK_SIZE / 2f)
        p.fill.shader = null
        p.fill.color = withAlpha(ink, markAlpha)
        canvas.drawPath(p.mark, p.fill)
        canvas.restoreToCount(save)
    }

    val reveal = m.images[0]
    if (s > 0.001f) {
        val g = shape
        val pw = pillLerp(g, floatArrayOf(0f, 0.48f, 0.68f, 1f), floatArrayOf(PILL_W, PILL_W, PILL_W * 1.8f, w))
        val r = min(pw / 2f, pillLerp(g, floatArrayOf(0f, 0.48f, 0.68f, 0.9f, 1f), floatArrayOf(PILL_W / 2f, PILL_W / 2f, PILL_W * 0.9f, 46f, 0f)))
        val save = canvas.save()
        p.path.reset()
        p.rect.set((w - pw) / 2f, pillTop, (w + pw) / 2f, h + 80f)
        p.path.addRoundRect(p.rect, r, r, Path.Direction.CW)
        canvas.clipPath(p.path)
        val key = (w.toBits().toLong() shl 32) or h.toBits().toLong()
        p.fill.shader = p.pillGradient.getOrPut(key) {
            LinearGradient(0f, 0f, w, h, intArrayOf(0xFFF25FB4.toInt(), 0xFF8B5CF6.toInt(), 0xFF38BDF8.toInt()), null, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, w, h, p.fill)
        p.fill.shader = null
        if (reveal != null) {
            // The colour inside grows still as the capsule opens.
            val grow = pillLerp(g, floatArrayOf(PEEK, 1f), floatArrayOf(1.2f, 1f))
            val inner = canvas.save()
            canvas.clipRect(0f, 0f, w, h)
            canvas.scale(grow, grow, w / 2f, h / 2f)
            p.fill.color = 0xFFFFFFFF.toInt()
            p.rect.set(0f, 0f, w, h)
            drawCover(canvas, reveal, p.rect, p.fill)
            canvas.restoreToCount(inner)
        }
        // A dark cap at the top of the capsule, gone by the time it is a third of the way up.
        val cap = 1f - pillSeg(g, PEEK + 0.04f, 0.46f)
        if (cap > 0f) {
            p.fill.shader = LinearGradient(0f, pillTop + 40f, 0f, pillTop + 150f, withAlpha(night, cap), withAlpha(night, 0f), Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, w, h + 80f, p.fill)
            p.fill.shader = null
        }
        canvas.restoreToCount(save)
    }
    if (reduced && reveal != null) {
        val fade = pillSeg(s, PEEK, 1f)
        if (fade > 0f) {
            p.fill.color = withAlpha(0xFFFFFFFF.toInt(), fade)
            p.rect.set(0f, 0f, w, h)
            drawCover(canvas, reveal, p.rect, p.fill)
            p.fill.alpha = 255
        }
    }

    // The arrow button rides the top of the capsule, then flies off the top and fades.
    val buttonAlpha = pillSeg(shape, 0.03f, PEEK * 0.9f) * (1f - pillSeg(shape, 0.52f, 0.68f))
    if (buttonAlpha > 0f) {
        val lift = pillLerp(shape, floatArrayOf(0.48f, 0.7f), floatArrayOf(0f, -110f))
        val grow = pillLerp(shape, floatArrayOf(0.02f, PEEK), floatArrayOf(0.55f, 1f)) * pillLerp(shape, floatArrayOf(0.5f, 0.7f), floatArrayOf(1f, 0.85f))
        val save = canvas.save()
        canvas.translate(w / 2f, pillTop + PILL_W / 2f + lift)
        canvas.scale(grow, grow)
        canvas.saveLayerAlpha(-BUTTON_R - 8f, -BUTTON_R - 8f, BUTTON_R + 8f, BUTTON_R + 8f, (buttonAlpha * 255).toInt())
        p.stroke.strokeWidth = 1.5f
        p.stroke.color = withAlpha(lime, 0.45f)
        canvas.drawCircle(0f, 0f, BUTTON_R + 4f, p.stroke)
        p.fill.color = lime
        canvas.drawCircle(0f, 0f, BUTTON_R, p.fill)
        // A soft nudge upwards while the capsule waits at its peek.
        val at = 1f - pillSeg(abs(shape - PEEK), 0f, 0.04f)
        val nudge = if (reduced) 0f else -3f * at * (0.5f - 0.5f * cos((PI * 2 * m.time / 1.5).toFloat()))
        canvas.translate(0f, nudge)
        p.stroke.strokeWidth = 2.4f
        p.stroke.color = ink
        canvas.drawPath(p.arrow, p.stroke)
        canvas.restoreToCount(save)
    }

    // ——— The board ———
    val board = m.board.value
    if (board > 0f) drawBoard(canvas, m, l, p, headline, link, cta, lime, ink, night, fontScale, density)

    // ——— The way back: the button opens into the full screen, from its own frame. ———
    val e = m.exit.value
    if (e > 0f) {
        if (reduced) {
            p.fill.color = withAlpha(lime, e)
            canvas.drawRect(0f, 0f, w, h, p.fill)
        } else {
            val left = CTA_LEFT * (1f - e)
            val top = l.ctaTop * (1f - e)
            val rw = l.ctaWidth + (w - l.ctaWidth) * e
            val rh = CTA_HEIGHT + (h - CTA_HEIGHT) * e
            val radius = min((CTA_HEIGHT / 2f) * (1f - e) + 46f * sin(PI.toFloat() * min(e, 0.5f)) * (1f - e), min(rw, rh) / 2f)
            p.fill.color = lime
            p.rect.set(left, top, left + rw, top + rh)
            canvas.drawRoundRect(p.rect, radius, radius, p.fill)
        }
    }
}

private fun drawBoard(
    canvas: Canvas,
    m: PillRiseMotion,
    l: PillRiseLayout,
    p: PillRisePaints,
    headline: List<String>,
    link: String,
    cta: String,
    lime: Int,
    ink: Int,
    night: Int,
    fontScale: Float,
    density: Float,
) {
    val w = l.w
    val board = m.board.value
    val reduced = m.reducedMotion

    p.fill.shader = null
    p.fill.color = withAlpha(night, pillSeg(board, 0f, 0.5f))
    canvas.drawRect(0f, 0f, w, l.h, p.fill)

    // Three steps of progress; the first fills.
    val barAlpha = pillSeg(board, 0.35f, 0.6f)
    if (barAlpha > 0f) {
        val track = (w - 48f - 12f) / 3f
        for (step in 0 until 3) {
            val x = 24f + step * (track + 6f)
            p.fill.color = withAlpha(0x24FFFFFF, barAlpha)
            p.rect.set(x, l.barTop, x + track, l.barTop + 3f)
            canvas.drawRoundRect(p.rect, 1.5f, 1.5f, p.fill)
            if (step == 0) {
                val fill = PillEaseOut.transform(pillSeg(board, 0.5f, 1f))
                if (fill > 0f) {
                    val save = canvas.save()
                    p.path.reset()
                    p.path.addRoundRect(p.rect, 1.5f, 1.5f, Path.Direction.CW)
                    canvas.clipPath(p.path)
                    p.fill.color = withAlpha(lime, barAlpha)
                    canvas.drawRect(x, l.barTop, x + track * fill, l.barTop + 3f, p.fill)
                    canvas.restoreToCount(save)
                }
            }
        }
    }

    val f = m.jarFrame
    f.board = board
    f.time = m.time
    f.reducedMotion = reduced
    f.scale = density
    l.jars.forEachIndexed { i, jar -> drawPillRiseJar(canvas, jar, i, m.images[i + 1], f, p.jarPaints) }

    // The headline, each line rising out of its own baseline.
    val hp = p.headline
    hp.textSize = HEADLINE_SIZE * fontScale
    hp.letterSpacing = 0.3f / HEADLINE_SIZE
    val fm = hp.fontMetrics
    headline.forEachIndexed { index, line ->
        val from = 0.1f + index * 0.08f
        val pr = PillEaseOut.transform(pillSeg(board, from, from + 0.38f))
        val alpha = if (reduced) pr else pillSeg(board, from, from + 0.2f)
        if (alpha <= 0f) return@forEachIndexed
        val top = l.headlineTop + index * HEADLINE_LINE
        val rise = if (reduced) 0f else (1f - pr) * HEADLINE_LINE * 1.1f
        // The line box is 45 dp tall and hangs from the bottom of its 41 dp slot.
        val box = top - 4f
        val baseline = box + (HEADLINE_LINE + 4f - (fm.descent - fm.ascent)) / 2f - fm.ascent
        val save = canvas.save()
        canvas.clipRect(24f, top, w - 24f, top + HEADLINE_LINE)
        hp.alpha = (alpha * 255).toInt()
        canvas.drawText(line.uppercase(), w / 2f, baseline + rise, hp)
        canvas.restoreToCount(save)
    }

    val label = p.label
    val linkAlpha = pillSeg(board, 0.55f, 0.85f)
    if (linkAlpha > 0f) {
        label.textSize = 15f * fontScale
        label.letterSpacing = 0f
        label.isUnderlineText = true
        label.color = withAlpha(lime, linkAlpha)
        canvas.drawText(link, w / 2f, l.linkTop - label.fontMetrics.top, label)
        label.isUnderlineText = false
    }

    val ctaAlpha = pillSeg(board, 0.42f, 0.6f)
    if (ctaAlpha > 0f) {
        val pr = PillEaseOut.transform(pillSeg(board, 0.42f, 0.85f))
        val save = canvas.save()
        canvas.translate(w / 2f, l.ctaTop + CTA_HEIGHT / 2f + (if (reduced) 0f else (1f - pr) * 90f))
        val scale = 1f - m.press.value * 0.03f
        canvas.scale(scale, scale)
        // The button fades as one piece, label and all.
        if (ctaAlpha < 1f) canvas.saveLayerAlpha(-l.ctaWidth / 2f, -CTA_HEIGHT / 2f, l.ctaWidth / 2f, CTA_HEIGHT / 2f, (ctaAlpha * 255).toInt())
        p.fill.color = lime
        p.rect.set(-l.ctaWidth / 2f, -CTA_HEIGHT / 2f, l.ctaWidth / 2f, CTA_HEIGHT / 2f)
        canvas.drawRoundRect(p.rect, CTA_HEIGHT / 2f, CTA_HEIGHT / 2f, p.fill)
        label.textSize = 17f * fontScale
        label.letterSpacing = -0.2f / 17f
        label.color = ink
        val lm = label.fontMetrics
        canvas.drawText(cta, 0f, -(lm.ascent + lm.descent) / 2f, label)
        canvas.restoreToCount(save)
    }
}
