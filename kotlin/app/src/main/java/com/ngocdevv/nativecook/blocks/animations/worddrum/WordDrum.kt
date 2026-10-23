package com.ngocdevv.nativecook.blocks.animations.worddrum

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private val Words = listOf(
    "Visibility",
    "Strategy",
    "Growth",
    "Solutions",
    "Progress",
    "Momentum",
    "Boost",
    "Scaling",
    "Innovation",
    "Clarity",
    "Reach",
    "Impact",
)

// The system sans, as the React Native version uses away from iOS.
private const val FONT_SIZE = 40f
// The word's baseline sits this share of the size below the drum's axis, so the letters straddle it.
private const val BASELINE = 0.34f

// The words are the spokes of a large wheel lying flat on the screen, its hub far off to the
// right. Each word sits on the rim, SPACING apart, and turns with its spoke, so the words above
// the front rise to the right and the words below fall away, each one a little further left.
// Words are drawn up to MAX_ROWS away from the front.
private const val SPACING = 60f
private const val RADIUS = 760f
private const val ANGLE = SPACING / RADIUS
private const val MAX_ROWS = 3.8f
// The word in front grows a little and steps right, away from the arrow.
private const val FRONT_GROW = 0.07f
private const val FRONT_SHIFT = 6f
// Left edge of the words and of the arrow, from the left of the screen.
private const val WORD_LEFT = 72f
private const val ARROW_LEFT = 28f
private const val ARROW_WIDTH = 26f
private const val ARROW_HEAD = 9f
private const val ARROW_STROKE = 3.2f
// Each word that reaches the front kicks the arrow, and the word with it, to the right like a
// ratchet's pawl: a push of KICK_SPEED dp/s into a critically damped spring that peaks about
// 13 dp over 80 ms and settles back over 300 ms. A fast spin kicks it once per word, up to KICK_MAX.
private const val KICK_SPEED = 430f
private const val KICK_STIFFNESS = 150f
private const val KICK_DAMPING = 24.5f
private const val KICK_MAX = 18f
// Height of the screen, as a share, where the drum's axis sits.
private const val AXIS_AT = 0.47f

// Blur grows with a word's distance from the front and with the drum's speed.
private const val BLUR_PER_ROW = 1.4f
private const val BLUR_PER_SPEED = 0.55f
private const val BLUR_MAX = 9f
private const val FADE_PER_ROW = 0.16f
private const val SPEED_RATE = 10f

// Finger travel that turns the wheel by one word: the words follow the finger.
private const val DRAG_STEP = SPACING
private const val DRAG_SLOP = 6f
// A tap is a touch lifted within this long without turning into a drag.
private const val TAP_MS = 400L
// The snap after a release: a spring on the drum's position, in words.
private const val MASS = 1f
private const val STIFFNESS = 300f
private const val DAMPING = 32f
// Seconds of speed a release throws the drum ahead by: a hard flick spins it like a reel.
private const val THROW_TIME = 0.3f

// Choosing a word: four stripes sweep across from the left, staggered, over WIPE_TIME s. The
// drum and the chosen word swap while the first stripe covers the screen.
private const val WIPE_TIME = 1.15f
private const val STRIPE_LAG = 0.08f
private const val STRIPE_SPAN = 0.56f
private const val SWAP_AT = STRIPE_SPAN / 2f
// Each stripe is this many screens wide, so neighbours always overlap and nothing shows between
// them while they move fastest.
private const val STRIPE_LENGTH = 2f
private val EaseInOut = CubicBezierEasing(0.77f, 0f, 0.175f, 1f)
private const val REVEAL_RISE = 26f
// Under reduced motion the drum and the word crossfade instead, on Reanimated's default curve.
private const val CROSSFADE_TIME = 0.22f
private fun crossfadeEase(t: Float) = if (t < 0.5f) 2f * t * t else 1f - (-2f * t + 2f).pow(2) / 2f

// autoPlay loop (seconds): five turns of one word, a word chosen, the drum again, a rest.
private const val LOOP = 10f
private val Turns = floatArrayOf(0.7f, 1.55f, 2.4f, 3.25f, 4.1f)
private const val CHOOSE_AT = 5.0f
private const val RETURN_AT = 7.6f
// After a touch the loop waits this long before it starts over; longer after a word is chosen,
// so it can be read.
private const val USER_HOLD = 3f
private const val CHOSEN_HOLD = 5f

/** `x` moved by whole turns of `n` into [-n/2, n/2). */
private fun centred(x: Float, n: Int) = x - n * floor(x / n + 0.5f)

private fun snapTarget(scroll: Float, velocity: Float) = (scroll + velocity * THROW_TIME).roundToInt().toFloat()

/** Where a stripe's left edge is, in screens: -STRIPE_LENGTH (all to the left) … 1 (all to the right). */
private fun stripeAt(wipe: Float, k: Int): Float {
    val p = ((wipe - k * STRIPE_LAG) / STRIPE_SPAN).coerceIn(0f, 1f)
    return -STRIPE_LENGTH + (STRIPE_LENGTH + 1f) * EaseInOut.transform(p)
}

/** Colour between two, mixed with gamma 2.2 as Reanimated's `interpolateColor` mixes them. */
private fun mixColor(from: Color, to: Color, t: Float): Int {
    fun channel(a: Float, b: Float): Float {
        val linear = a.pow(2.2f) + (b.pow(2.2f) - a.pow(2.2f)) * t
        return (linear.pow(1f / 2.2f) * 255f).roundToInt() / 255f
    }
    return Color(channel(from.red, to.red), channel(from.green, to.green), channel(from.blue, to.blue)).toArgb()
}

/** The drum's motion, shared by the frame loop, the gestures and the drawing. */
private class DrumMotion(val count: Int) {
    var scroll by mutableFloatStateOf(0f)
    var seen = 0f
    var velocity = 0f
    var target = 0f
    // |velocity| smoothed, in words per second.
    var speed by mutableFloatStateOf(0f)
    // The arrow's kick to the right, in dp, and its speed.
    var kick by mutableFloatStateOf(0f)
    var kickVelocity = 0f
    var dragging = false
    var dragFrom = 0f
    // 0…1 through a sweep of the stripes; `showing` flips while they cover the screen.
    var wipe by mutableFloatStateOf(0f)
    var showing by mutableFloatStateOf(0f)
    var wiping = false
    var byUser = false
    var clock = 0f
    var looping = false
    var hold = 0f
    // The sweep or crossfade under way: how far through it, and where `showing` fades from and to.
    var wipeRunning = false
    var fadeRunning = false
    var fadeT = 0f
    var fadeFrom = 0f
    var fadeTo = 0f
    var covered = false
    var lastWhole = 0
}

/**
 * A wheel of words turning in the dark. The word in front is bright, with an arrow pointing at
 * it; the others fan away above and below like spokes of a wheel whose hub is off the right edge,
 * dimmer and more blurred the further they are, and the whole wheel blurs while it spins. Swiping
 * turns it a word at a time; tapping the word in front chooses it, and a run of blue stripes
 * sweeps across to show it on its own.
 *
 * @param words The words on the drum, in order. Twelve or more keep a word from showing twice.
 * @param lead The line above the drum.
 * @param promise What follows the chosen word once it is chosen.
 * @param autoPlay Turns the drum a word at a time, chooses one, goes back and starts over.
 * @param activeColor The word in front; [idleColor] the rest of the drum.
 * @param stripes The stripes that sweep across when a word is chosen, darkest first.
 * @param onChoose Called with a word when it is chosen.
 */
@Composable
fun WordDrum(
    modifier: Modifier = Modifier,
    words: List<String> = Words,
    lead: String = "What we bring",
    promise: String = "built into everything we ship.",
    autoPlay: Boolean = false,
    background: Color = Color.Black,
    activeColor: Color = Color(0xFF8CC4FF),
    idleColor: Color = Color(0xFF3F6FAE),
    textColor: Color = Color(0xFFE9F2FF),
    stripes: List<Color> = listOf(Color(0xFF1F63D8), Color(0xFF3F86EE), Color(0xFF7CB4F7), Color(0xFFC3DEFC)),
    onChoose: ((String) -> Unit)? = null,
) {
    val reducedMotion = rememberReducedMotion()
    val auto = autoPlay && !reducedMotion
    val count = words.size
    val view = LocalView.current
    val density = LocalDensity.current
    val choosing by rememberUpdatedState(onChoose)
    var chosen by remember { mutableIntStateOf(0) }
    val motion = remember(count) { DrumMotion(count) }
    val insets = WindowInsets.safeDrawing
    val safeTop = insets.getTop(density) / density.density
    val safeBottom = insets.getBottom(density) / density.density

    LaunchedEffect(motion, auto) {
        motion.looping = auto
        motion.clock = 0f
        motion.hold = 0f
    }

    fun chose(index: Int, user: Boolean) {
        chosen = index
        if (!user) return
        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        choosing?.invoke(words[index])
    }

    /** Sweeps the stripes across; while they cover the screen the drum and the word swap. */
    fun sweep() {
        val m = motion
        if (m.wiping) return
        m.wiping = true
        m.wipe = 0f
        if (reducedMotion) {
            m.fadeRunning = true
            m.fadeT = 0f
            m.fadeFrom = m.showing
            m.fadeTo = if (m.showing > 0.5f) 0f else 1f
            return
        }
        m.wipeRunning = true
    }

    fun choose(user: Boolean) {
        val front = motion.scroll.roundToInt()
        motion.target = front.toFloat()
        chose(front.mod(count), user)
        sweep()
    }

    fun touched() {
        motion.hold = USER_HOLD
        motion.byUser = true
    }

    LaunchedEffect(motion, reducedMotion) {
        var last = -1L
        val m = motion
        while (true) {
            withFrameNanos { now ->
                val dt = if (last < 0) 1f / 60f else min((now - last) / 1e9f, 0.064f)
                last = now
                if (dt <= 0f) return@withFrameNanos
                // The sweep runs on a straight clock; the crossfade on an ease-in-out.
                if (m.wipeRunning) {
                    m.wipe = min(1f, m.wipe + dt / WIPE_TIME)
                    if (m.wipe >= 1f) {
                        m.wipeRunning = false
                        m.wiping = false
                    }
                }
                if (m.fadeRunning) {
                    m.fadeT = min(1f, m.fadeT + dt / CROSSFADE_TIME)
                    m.showing = m.fadeFrom + (m.fadeTo - m.fadeFrom) * crossfadeEase(m.fadeT)
                    if (m.fadeT >= 1f) {
                        m.fadeRunning = false
                        m.wiping = false
                    }
                }
                // The swap happens under the first stripe, so neither side is seen changing.
                val covered = m.wipe >= SWAP_AT
                if (covered && !m.covered) m.showing = if (m.showing > 0.5f) 0f else 1f
                m.covered = covered

                if (m.dragging) {
                    m.velocity += ((m.scroll - m.seen) / dt - m.velocity) * 0.5f
                } else {
                    if (m.hold > 0f) {
                        m.hold -= dt
                        if (m.hold <= 0f) {
                            m.clock = 0f
                            // A word left chosen goes back to the wheel before the loop starts over.
                            if (m.looping && m.showing > 0.5f) sweep()
                        }
                    } else if (m.looping) {
                        val before = m.clock
                        val t = (before + dt) % LOOP
                        m.clock = t
                        fun passed(at: Float) = (before < at && t >= at) || (t < before && (at > before || at <= t))
                        for (turn in Turns) {
                            if (passed(turn)) {
                                m.byUser = false
                                m.target = m.target.roundToInt() + 1f
                            }
                        }
                        if (passed(CHOOSE_AT) && m.showing < 0.5f) choose(false)
                        if (passed(RETURN_AT) && m.showing > 0.5f) sweep()
                    }
                    if (reducedMotion) {
                        m.scroll = m.target
                        m.velocity = 0f
                    } else {
                        val v = m.velocity + ((STIFFNESS * (m.target - m.scroll) - DAMPING * m.velocity) / MASS) * dt
                        m.velocity = v
                        m.scroll += v * dt
                    }
                    // Whole turns of the drum change nothing on screen, so the numbers are kept small.
                    val turns = floor(m.target / count) * count
                    if (turns != 0f) {
                        m.scroll -= turns
                        m.target -= turns
                    }
                }
                m.seen = m.scroll
                val fast = if (reducedMotion) 0f else abs(m.velocity)
                m.speed += (fast - m.speed) * (1f - exp(-dt * SPEED_RATE))

                // Each word reaching the front kicks the arrow; under the user's hand it also ticks.
                val whole = m.scroll.roundToInt()
                if (whole != m.lastWhole) {
                    if (!reducedMotion) m.kickVelocity = min(KICK_SPEED, m.kickVelocity + KICK_SPEED)
                    if (m.byUser) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                }
                m.lastWhole = whole

                val pull = -KICK_STIFFNESS * m.kick - KICK_DAMPING * m.kickVelocity
                val kv = m.kickVelocity + pull * dt
                val next = m.kick + kv * dt
                // Held between rest and KICK_MAX; pressed against either end, it stops there.
                when {
                    next >= KICK_MAX -> {
                        m.kick = KICK_MAX
                        m.kickVelocity = min(kv, 0f)
                    }
                    next <= 0f -> {
                        m.kick = 0f
                        m.kickVelocity = max(kv, 0f)
                    }
                    else -> {
                        m.kick = next
                        m.kickVelocity = kv
                    }
                }
            }
        }
    }

    val paints = remember { DrumPaints() }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(background)
            .semantics { contentDescription = "$lead: ${words.joinToString()}" },
    ) {
        val height = maxHeight.value
        val axisY = height * AXIS_AT

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(motion, axisY, reducedMotion) {
                    val tracker = VelocityTracker()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val start = down.position
                        tracker.resetTracking()
                        tracker.addPointerInputChange(down)
                        var panning = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            tracker.addPointerInputChange(change)
                            val dx = (change.position.x - start.x) / density.density
                            val dy = (change.position.y - start.y) / density.density
                            if (!change.pressed) {
                                if (panning) {
                                    change.consume()
                                    if (motion.dragging) {
                                        motion.dragging = false
                                        // The finger's own speed at release, in words per second, carries into the spring.
                                        motion.velocity = -tracker.calculateVelocity().y / density.density / DRAG_STEP
                                        motion.target = snapTarget(motion.scroll, motion.velocity)
                                    }
                                } else if (change.uptimeMillis - down.uptimeMillis <= TAP_MS && !motion.wiping) {
                                    touched()
                                    if (motion.showing > 0.5f) {
                                        sweep()
                                    } else {
                                        // The word under the finger: rows sit RADIUS·sin(d·ANGLE) from the axis.
                                        val y = change.position.y / density.density
                                        var hit = 0
                                        var best = Float.MAX_VALUE
                                        for (d in -3..3) {
                                            val off = abs(y - (axisY + RADIUS * sin(d * ANGLE)))
                                            if (off < best) {
                                                best = off
                                                hit = d
                                            }
                                        }
                                        if (hit == 0) {
                                            motion.hold = CHOSEN_HOLD
                                            choose(true)
                                        } else {
                                            motion.target = (motion.scroll.roundToInt() + hit).toFloat()
                                        }
                                    }
                                }
                                break
                            }
                            if (!panning && sqrt(dx * dx + dy * dy) >= DRAG_SLOP) {
                                panning = true
                                if (motion.showing <= 0.5f && !motion.wiping) {
                                    touched()
                                    motion.dragFrom = motion.scroll
                                    motion.seen = motion.scroll
                                    motion.dragging = true
                                }
                            }
                            if (panning) {
                                change.consume()
                                if (motion.dragging) motion.scroll = motion.dragFrom - dy / DRAG_STEP
                            }
                        }
                        // A finger lost to the system still lets go of the drum.
                        if (motion.dragging) {
                            motion.dragging = false
                            motion.target = snapTarget(motion.scroll, motion.velocity)
                        }
                    }
                },
        ) {
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = 1f - motion.showing }) {
                BasicText(
                    lead,
                    Modifier.offset(ARROW_LEFT.dp, (safeTop + 32f).dp),
                    style = TextStyle(color = idleColor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp, fontFamily = FontFamily.SansSerif),
                )
                Canvas(Modifier.fillMaxSize()) {
                    scale(density.density, density.density, pivot = Offset.Zero) {
                        drawIntoCanvas { canvas ->
                            val nc = canvas.nativeCanvas
                            for (index in words.indices) {
                                drawRow(nc, words[index], centred(index - motion.scroll, count), motion, axisY, activeColor, idleColor, paints, density.density)
                            }
                        }
                        // The arrow stays on the front row; each word arriving there kicks it to the right.
                        translate(left = motion.kick) {
                            drawPath(paints.arrow(axisY), activeColor, style = Stroke(ARROW_STROKE, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }
                    }
                }
                BasicText(
                    "Swipe to turn · Tap to choose",
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = (safeBottom + 28f).dp),
                    style = TextStyle(color = idleColor, fontSize = 13.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, fontFamily = FontFamily.SansSerif),
                )
            }

            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = ARROW_LEFT.dp)
                    .graphicsLayer {
                        alpha = motion.showing
                        translationY = if (reducedMotion) 0f else (REVEAL_RISE * (1f - ((motion.wipe - 0.45f) / 0.55f).coerceIn(0f, 1f))).dp.toPx()
                    },
                verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
            ) {
                BasicText(
                    words[chosen],
                    style = TextStyle(color = activeColor, fontSize = 56.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp, fontFamily = FontFamily.SansSerif),
                )
                BasicText(
                    promise,
                    Modifier.widthIn(max = 260.dp),
                    style = TextStyle(color = textColor, fontSize = 24.sp, fontWeight = FontWeight.Medium, lineHeight = 31.sp, fontFamily = FontFamily.SansSerif),
                )
                Spacer(Modifier.height(18.dp))
                BasicText(
                    "Tap to turn again",
                    style = TextStyle(color = idleColor, fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFamily = FontFamily.SansSerif),
                )
            }

            // Drawn rather than laid out: a Box can't be wider than the screen it sits in, which
            // would cut each stripe to one screen and leave gaps between them.
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width
                stripes.forEachIndexed { k, color ->
                    val left = stripeAt(motion.wipe, k) * w
                    if (left < w && left + STRIPE_LENGTH * w > 0f) {
                        drawRect(color, topLeft = Offset(left, 0f), size = Size(STRIPE_LENGTH * w, size.height))
                    }
                }
            }
        }
    }
}

private class DrumPaints {
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = FONT_SIZE
        typeface = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Typeface.create(Typeface.SANS_SERIF, 700, false)
        } else {
            Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
    }
    private var arrowAt = Float.NaN
    private var arrowPath = androidx.compose.ui.graphics.Path()

    fun arrow(axisY: Float): androidx.compose.ui.graphics.Path {
        if (arrowAt != axisY) {
            arrowPath = androidx.compose.ui.graphics.Path().apply {
                moveTo(ARROW_LEFT, axisY)
                lineTo(ARROW_LEFT + ARROW_WIDTH, axisY)
                moveTo(ARROW_LEFT + ARROW_WIDTH - ARROW_HEAD, axisY - ARROW_HEAD)
                lineTo(ARROW_LEFT + ARROW_WIDTH, axisY)
                lineTo(ARROW_LEFT + ARROW_WIDTH - ARROW_HEAD, axisY + ARROW_HEAD)
            }
            arrowAt = axisY
        }
        return arrowPath
    }
}

/** One word on the wheel's rim, turned with its spoke and blurred by its distance from the front. */
private fun drawRow(
    canvas: android.graphics.Canvas,
    word: String,
    d: Float,
    m: DrumMotion,
    axisY: Float,
    activeColor: Color,
    idleColor: Color,
    paints: DrumPaints,
    density: Float,
) {
    val ad = abs(d)
    val opacity = if (ad > MAX_ROWS) 0f else max(0f, 1f - ad * FADE_PER_ROW) * min(1f, (MAX_ROWS - ad) * 1.5f)
    if (opacity <= 0f) return
    val angle = d * ANGLE
    // 1 for the word in front, 0 from one row away.
    val front = max(0f, 1f - ad)
    val paint = paints.text
    paint.color = mixColor(activeColor, idleColor, min(1f, ad))
    paint.alpha = (opacity * 255f).roundToInt()
    // The blur is measured in screen pixels whatever the word's scale, as in the React Native
    // version; mask filters on a hardware canvas need API 28.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val sigma = (ad * BLUR_PER_ROW + m.speed * BLUR_PER_SPEED).coerceIn(0.01f, BLUR_MAX)
        val scale = 1f + front * FRONT_GROW
        paint.maskFilter = BlurMaskFilter(sigmaToRadius(sigma / (density * scale)), BlurMaskFilter.Blur.NORMAL)
    }
    canvas.save()
    canvas.translate(WORD_LEFT - RADIUS * (1f - cos(angle)) + front * (FRONT_SHIFT + m.kick), axisY + RADIUS * sin(angle))
    canvas.rotate(Math.toDegrees(angle.toDouble()).toFloat())
    canvas.scale(1f + front * FRONT_GROW, 1f + front * FRONT_GROW)
    canvas.drawText(word, 0f, FONT_SIZE * BASELINE, paint)
    canvas.restore()
}
