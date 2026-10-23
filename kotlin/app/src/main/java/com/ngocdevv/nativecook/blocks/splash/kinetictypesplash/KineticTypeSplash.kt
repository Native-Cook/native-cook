package com.ngocdevv.nativecook.blocks.splash.kinetictypesplash

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
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
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

private val LINES = listOf("FIND", "YOUR")
private val WORDS = listOf("CROWD", "SCENE", "GLOW")
private const val NAME = "NIGHTJAR"
private const val TITLE = "The city after dark,\none tap away."
private const val CAPTION = "Shows, late kitchens and rooftops near you, picked fresh every night."
private const val CTA = "Get started"
private val BACKGROUND = Color(0xFF0A0A0B)
private val TYPE = Color(0xFFF3EDE3)
private val ACCENT = Color(0xFFFF3D7F)

private const val PHOTO = "https://images.unsplash.com/photo-1581300740943-cfa5f847db2c?auto=format&fit=crop&w=1200&q=80"

// Huge condensed capitals, sized so the widest word fills the width between MARGIN and the edge.
private const val MARGIN = 22f
private const val MAX_SIZE = 168f
// Each line is a window as tall as a capital plus PAD of its size above and below; letters rise
// into it from below the baseline. Lines sit LEADING of a capital apart.
private const val PAD = 0.12f
private const val LEADING = 1.2f
// The rolling word glows like a neon tube: a copy blurred by GLOW_BLUR dp under it.
private const val GLOW_BLUR = 9f
// Letters: each rises over LETTER_TIME s, LETTER_LAG s after the one before; lines LINE_LAG apart.
private const val LETTER_TIME = 0.55f
private const val LETTER_LAG = 0.045f
private const val LINE_LAG = 0.38f
private const val INTRO_END = 2 * LINE_LAG + 5 * LETTER_LAG + LETTER_TIME
private const val OUT_END = 4 * LETTER_LAG + LETTER_TIME
private val EaseOut = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
private val EaseInOut = CubicBezierEasing(0.76f, 0f, 0.24f, 1f)
private val Linear = Easing { it }
/** Reanimated's default timing curve, ease in and out (quadratic). */
private val Standard = Easing { p -> if (p < 0.5f) 2f * p * p else 1f - (-2f * p + 2f) * (-2f * p + 2f) / 2f }

// The rolling word's columns: the first follows the reel on a lively spring, each next one follows
// its neighbour on a stiffer one, so the letters land one after another like a slot machine.
private const val LEAD_K = 230f
private const val LEAD_C = 21f
private const val CHAIN_K = 420f
private const val CHAIN_C = 37f
private const val COLUMNS = 8
// The columns' springs are stepped at most this long (s) at a time: the stiff chain blows up when
// stepped in long frames.
private const val MAX_STEP = 0.025f
// A flick throws the reel this many seconds ahead before it snaps to a word.
private const val THROW = 0.12f
private const val DRAG_SLOP = 8f
private const val TAP_MS = 400L

// Reveal: the photo fills the last word, then the word grows from a point in its first stem
// until the photo is the whole screen.
private const val FILL_S = 0.5f
private const val ZOOM_S = 1.15f
private const val ZOOM_MAX = 70f
private const val FINAL_S = 0.85f
private const val EXIT_S = 0.65f

// autoPlay script (s).
private const val LOOP = 9.6f
private const val INTRO_AT = 0.25f
private val ROLL_AT = floatArrayOf(2.2f, 3.1f)
private const val REVEAL_AT = 3.8f
private const val PRESS_AT = 8.3f
private const val EXIT_AT = 8.45f
private const val USER_HOLD = 4f

private fun seg(v: Float, a: Float, b: Float) = ((v - a) / (b - a)).coerceIn(0f, 1f)

/**
 * A number driven like a Reanimated shared value by a (delayed) timing, stepped by the block's
 * clock; a finished timing hands back its callback, an interrupted one never calls it.
 */
private class TimedValue(initial: Float) {
    var value = initial
        private set
    private var running = false
    private var from = 0f
    private var fromSet = false
    private var to = 0f
    private var start = 0f
    private var duration = 0f
    private var curve: Easing = Linear
    private var done: (() -> Unit)? = null

    fun set(next: Float) {
        value = next
        running = false
        done = null
    }

    fun timing(target: Float, seconds: Float, now: Float, delay: Float = 0f, curve: Easing = Linear, then: (() -> Unit)? = null) {
        from = value
        fromSet = delay <= 0f
        to = target
        start = now + delay
        duration = seconds
        this.curve = curve
        running = true
        done = then
    }

    fun step(now: Float): (() -> Unit)? {
        if (!running || now < start) return null
        if (!fromSet) {
            from = value
            fromSet = true
        }
        val p = if (duration <= 0f) 1f else ((now - start) / duration).coerceIn(0f, 1f)
        value = from + (to - from) * curve.transform(p)
        if (p < 1f) return null
        running = false
        val callback = done
        done = null
        return callback
    }
}

/** A word laid out letter by letter: each letter's outline on its own baseline at x 0, and its left edge. */
private class TypeWord(val letters: List<Path>, val xs: FloatArray, val path: Path)

/** The type fitted to the screen, and where its three lines sit. */
private class TypeSet(val w: Float, val h: Float, val top: Float, val bottom: Float, lines: List<String>, words: List<String>) {
    val key = listOf(w, h, top, bottom).joinToString() + lines + words
    val size: Float
    val cap: Float
    val lines: List<TypeWord>
    val words: List<TypeWord>
    val pad: Float
    val windowH: Float
    val baselines: FloatArray
    val ctaTop = h - max(bottom, 16f) - 8f - 56f

    init {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = HEADLINE_FACE }
        fun advances(text: String): FloatArray = FloatArray(text.length).also { paint.getTextWidths(text, it) }
        // Fitted at 100 dp, then set at the size that makes the widest word fill the width.
        paint.textSize = 100f
        val widest = (lines + words).maxOf { advances(it).sum() }
        size = min(MAX_SIZE, 100f * (w - MARGIN * 2f) / widest)
        paint.textSize = size
        val bounds = Rect()
        paint.getTextBounds("H", 0, 1, bounds)
        cap = bounds.height().toFloat()
        fun layout(text: String): TypeWord {
            val a = advances(text)
            var x = MARGIN
            val xs = FloatArray(text.length)
            val whole = Path()
            val letters = text.indices.map { i ->
                val letter = Path()
                paint.getTextPath(text, i, i + 1, 0f, 0f, letter)
                xs[i] = x
                whole.addPath(letter, x, 0f)
                x += a[i]
                letter
            }
            return TypeWord(letters, xs, whole)
        }
        this.lines = lines.map(::layout)
        this.words = words.map(::layout)
        pad = size * PAD
        windowH = cap + pad * 2f
        val pitch = cap * LEADING + pad
        // Baselines of the three lines, the block centred a little above the middle.
        val blockTop = h * 0.45f - (pitch * 2f + cap) / 2f
        baselines = FloatArray(3) { blockTop + cap + it * pitch }
    }
}

private val HEADLINE_FACE: Typeface = run {
    val condensed = Typeface.create("sans-serif-condensed", Typeface.NORMAL)
    if (Build.VERSION.SDK_INT >= 28) Typeface.create(condensed, 900, false) else Typeface.create(condensed, Typeface.BOLD)
}

/** Everything the frame loop, the touches and the picture share, kept off Compose state. */
private class KineticMotion {
    // Seconds into the lines' entrance and exit, the reel (in words), the reveal's steps, the way out.
    val intro = TimedValue(0f)
    val outro = TimedValue(0f)
    var reel = 0f
    val cols = FloatArray(COLUMNS)
    val colVel = FloatArray(COLUMNS)
    val fill = TimedValue(0f)
    val zoom = TimedValue(0f)
    val final = TimedValue(0f)
    val exit = TimedValue(0f)
    val press = TimedValue(0f)
    // 0 lines, 1 rolling, 2 revealing, 3 welcome, 4 leaving.
    var stage = 0
    var dragging = false
    var dragFrom = 0f
    var time = 0f
    var clock = 0f
    var looping = false
    var hold = 0f
    var lastWhole = 0
    var type: TypeSet? = null
    var reducedMotion = false
    var photo: Bitmap? = null
}

/**
 * Kinetic type for a splash. Huge condensed capitals rise line by line out of their own
 * baselines, letter after letter; the last line rolls through its words like a slot machine; the
 * last word fills with a photo, which then grows out of the letters into the whole screen, with a
 * welcome and a button over it. Tap to skip ahead, drag the last line to roll it yourself.
 *
 * @param autoPlay Types the lines, rolls the last word, reveals the photo through it and starts over.
 * @param lines The two lines that stay; [words] the words the last line rolls through (the last one opens).
 */
@Composable
fun KineticTypeSplash(
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false,
    lines: List<String> = LINES,
    words: List<String> = WORDS,
    name: String = NAME,
    title: String = TITLE,
    caption: String = CAPTION,
    cta: String = CTA,
    background: Color = BACKGROUND,
    type: Color = TYPE,
    accent: Color = ACCENT,
    onGetStarted: (() -> Unit)? = null,
) {
    val reducedMotion = rememberReducedMotion()
    val auto = autoPlay && !reducedMotion
    val view = LocalView.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val started by rememberUpdatedState(onGetStarted)
    val motion = remember { KineticMotion() }
    motion.reducedMotion = reducedMotion
    val last = words.size - 1
    // Bumped once a frame so the canvas draws again.
    val tick = remember { mutableIntStateOf(0) }

    val insets = WindowInsets.safeDrawing
    val safeTop = insets.getTop(density) / density.density
    val safeBottom = insets.getBottom(density) / density.density

    fun haptic(kind: Int) {
        view.performHapticFeedback(kind)
    }

    fun startIntro(delay: Float, fast: Boolean) {
        val m = motion
        val left = max(0f, INTRO_END - m.intro.value)
        m.intro.timing(INTRO_END, if (fast) 0.26f else left, m.time, delay)
    }

    fun roll(to: Float) {
        val m = motion
        if (m.stage > 1) return
        m.stage = 1
        m.reel = to.coerceIn(0f, last.toFloat())
    }

    fun reveal(delay: Float) {
        val m = motion
        if (m.stage > 1) return
        m.stage = 2
        m.reel = last.toFloat()
        m.fill.timing(1f, FILL_S, m.time, delay)
        m.outro.timing(OUT_END, OUT_END, m.time, delay)
        m.zoom.timing(1f, if (reducedMotion) 0.5f else ZOOM_S, m.time, delay + FILL_S + 0.2f) {
            m.stage = 3
            m.final.timing(1f, FINAL_S, m.time)
        }
    }

    fun reset() {
        val m = motion
        m.intro.set(0f)
        m.outro.set(0f)
        m.reel = 0f
        m.cols.fill(0f)
        m.colVel.fill(0f)
        m.fill.set(0f)
        m.zoom.set(0f)
        m.final.set(0f)
        m.exit.set(0f)
        m.stage = 0
        if (!m.looping || m.hold > 0f) startIntro(0.4f, false)
    }

    fun leave(user: Boolean) {
        val m = motion
        if (m.stage != 3 || m.final.value < 0.9f) return
        m.stage = 4
        if (user) {
            haptic(HapticFeedbackConstants.CONTEXT_CLICK)
            started?.invoke()
        }
        m.exit.timing(1f, if (reducedMotion) 0.35f else EXIT_S, m.time, curve = EaseInOut, then = ::reset)
    }

    /** The button scales down and back, like `withSequence` of two timings. */
    fun pulse(down: Float, up: Float) {
        val m = motion
        m.press.timing(1f, down, m.time, curve = Standard) { m.press.timing(0f, up, m.time, curve = Standard) }
    }

    fun advance(dt: Float) {
        val m = motion
        m.time += dt
        val now = m.time
        listOfNotNull(
            m.intro.step(now), m.outro.step(now), m.fill.step(now), m.zoom.step(now), m.final.step(now),
            m.exit.step(now), m.press.step(now),
        ).forEach { it() }

        // The reel's columns: the first chases the reel (or sits under the finger), the rest chase it.
        if (!m.reel.isFinite()) m.reel = 0f
        val target = m.reel
        val steps = max(1, ceil(dt / MAX_STEP).toInt())
        val h = dt / steps
        for (j in 0 until COLUMNS) {
            if (reducedMotion) {
                m.cols[j] += (target - m.cols[j]) * min(1f, dt * 14f)
                m.colVel[j] = 0f
                continue
            }
            if (j == 0 && m.dragging) {
                m.colVel[0] = (target - m.cols[0]) / dt
                m.cols[0] = target
                continue
            }
            val lead = if (j == 0) target else m.cols[j - 1]
            val k = if (j == 0) LEAD_K else CHAIN_K
            val damp = if (j == 0) LEAD_C else CHAIN_C
            repeat(steps) {
                m.colVel[j] += (k * (lead - m.cols[j]) - damp * m.colVel[j]) * h
                m.cols[j] += m.colVel[j] * h
            }
            if (!m.cols[j].isFinite() || !m.colVel[j].isFinite()) {
                m.cols[j] = target
                m.colVel[j] = 0f
            }
        }
        // A tick each time a word reaches the window under the finger (roundToInt throws on NaN).
        val whole = m.cols[0].roundToInt()
        if (whole != m.lastWhole && m.dragging) haptic(HapticFeedbackConstants.CLOCK_TICK)
        m.lastWhole = whole

        if (m.hold > 0f) {
            m.hold -= dt
            if (m.hold <= 0f && m.looping) {
                m.clock = when {
                    m.stage <= 1 -> if (m.intro.value > 0f) INTRO_END + INTRO_AT - 0.2f else 0f
                    m.stage <= 3 -> PRESS_AT - 0.5f
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
        if (passed(INTRO_AT) && m.stage == 0 && m.intro.value == 0f) startIntro(0f, false)
        for (at in ROLL_AT) {
            if (passed(at) && m.stage <= 1 && m.reel < last) roll(m.reel.roundToInt() + 1f)
        }
        if (passed(REVEAL_AT)) reveal(0f)
        if (passed(PRESS_AT) && m.stage == 3) pulse(0.11f, 0.2f)
        if (passed(EXIT_AT)) leave(false)
    }

    LaunchedEffect(motion, auto) {
        motion.looping = auto
        motion.clock = 0f
        motion.hold = 0f
        if (!auto) startIntro(INTRO_AT, false)
    }

    LaunchedEffect(motion, reducedMotion) {
        var lastNanos = -1L
        while (true) {
            withFrameNanos { now ->
                val dt = if (lastNanos < 0) 1f / 60f else min((now - lastNanos) / 1e9f, 0.064f)
                lastNanos = now
                if (dt > 0f) advance(dt)
                tick.intValue += 1
            }
        }
    }

    LaunchedEffect(motion, context) {
        // A software bitmap, so it can be cropped and drawn through the letters' clip.
        val request = ImageRequest.Builder(context).data(PHOTO).allowHardware(false).build()
        val result = SingletonImageLoader.get(context).execute(request) as? SuccessResult ?: return@LaunchedEffect
        motion.photo = result.image.toBitmap()
    }

    fun panStart() {
        val m = motion
        m.hold = USER_HOLD
        if (m.stage > 1 || m.intro.value < INTRO_END - 0.3f || reducedMotion) return
        m.stage = 1
        m.dragFrom = m.cols[0]
        m.dragging = true
    }

    fun panUpdate(dy: Float) {
        val m = motion
        val windowH = m.type?.windowH ?: return
        if (!m.dragging) return
        val raw = m.dragFrom - dy / windowH
        // Past the first or last word the reel gives less and less.
        val over = if (raw < 0f) raw else if (raw > last) raw - last else 0f
        m.reel = raw - over + over / (1f + abs(over) * 2.5f)
    }

    fun panEnd(vy: Float) {
        val m = motion
        val windowH = m.type?.windowH ?: return
        if (!m.dragging) return
        m.dragging = false
        val speed = (-vy / windowH).takeIf { it.isFinite() } ?: 0f
        m.colVel[0] = speed
        val thrown = m.reel + speed * THROW
        val to = (if (thrown.isFinite()) thrown.roundToInt() else 0).coerceIn(0, last)
        m.reel = to.toFloat()
        if (to == last) reveal(0.7f)
    }

    fun tap(y: Float) {
        val m = motion
        val set = m.type ?: return
        m.hold = USER_HOLD
        when {
            m.stage <= 1 && m.intro.value < INTRO_END - 0.05f -> {
                startIntro(0f, true)
                haptic(HapticFeedbackConstants.CLOCK_TICK)
            }
            m.stage <= 1 && m.reel < last -> {
                roll(m.reel.roundToInt() + 1f)
                haptic(HapticFeedbackConstants.CLOCK_TICK)
            }
            m.stage <= 1 -> {
                reveal(0f)
                haptic(HapticFeedbackConstants.CLOCK_TICK)
            }
            m.stage == 3 && y > set.ctaTop - 8f && y < set.ctaTop + 64f -> {
                pulse(0.09f, 0.18f)
                leave(true)
            }
        }
    }

    val paints = remember { KineticPaints() }
    val fontScale = density.fontScale
    val colors = KineticColors(background.toArgb(), type.toArgb(), accent.toArgb())

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .semantics {
                role = Role.Button
                contentDescription = "${lines.joinToString(" ")} ${words.last()}. ${title.replace('\n', ' ')} $cta"
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
                    // One loop stands in for the race of a vertical pan (after 8 dp up or down) and a tap.
                    while (!released) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        tracker.addPointerInputChange(change)
                        val dy = (change.position.y - start.y) / dp
                        if (!change.pressed) {
                            if (panning) {
                                change.consume()
                                panEnd(tracker.calculateVelocity().y / dp)
                            } else if (change.uptimeMillis - down.uptimeMillis <= TAP_MS) {
                                tap(change.position.y / dp)
                            }
                            released = true
                            continue
                        }
                        if (!panning && abs(dy) >= DRAG_SLOP) {
                            panning = true
                            panStart()
                        }
                        if (panning) {
                            change.consume()
                            panUpdate(dy)
                        }
                    }
                    // A finger lost to the system still lets go.
                    if (panning && !released) panEnd(0f)
                }
            },
    ) {
        val w = maxWidth.value
        val h = maxHeight.value
        val key = listOf(w, h, safeTop, safeBottom).joinToString() + lines + words
        if (motion.type?.key != key) motion.type = TypeSet(w, h, safeTop, safeBottom, lines, words)

        Canvas(Modifier.fillMaxSize()) {
            tick.intValue
            scale(density.density, density.density, pivot = Offset.Zero) {
                drawIntoCanvas {
                    val set = motion.type ?: return@drawIntoCanvas
                    drawKinetic(it.nativeCanvas, motion, set, paints, colors, name, title, caption, cta, fontScale)
                }
            }
        }
    }
}

private class KineticColors(val background: Int, val type: Int, val accent: Int)

/** Reused paints for the type, the photo and the welcome. */
private class KineticPaints {
    val fill = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { maskFilter = BlurMaskFilter(sigmaToRadius(GLOW_BLUR), BlurMaskFilter.Blur.NORMAL) }
    val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply { maskFilter = BlurMaskFilter(sigmaToRadius(GLOW_BLUR * 1.4f), BlurMaskFilter.Blur.NORMAL) }
    val text = Paint(Paint.ANTI_ALIAS_FLAG)
    val reel = Path()
    val placed = Path()
    val matrix = Matrix()
    val rect = RectF()
    var shade: Shader? = null
    var shadeH = 0f
    var fallback: Shader? = null
    var fallbackKey = 0f
    val condensed: Typeface = run {
        val base = Typeface.create("sans-serif-condensed", Typeface.NORMAL)
        if (Build.VERSION.SDK_INT >= 28) Typeface.create(base, 800, false) else Typeface.create(base, Typeface.BOLD)
    }
    val medium: Typeface =
        if (Build.VERSION.SDK_INT >= 28) Typeface.create(Typeface.SANS_SERIF, 500, false) else Typeface.DEFAULT
    val bold: Typeface =
        if (Build.VERSION.SDK_INT >= 28) Typeface.create(Typeface.SANS_SERIF, 700, false) else Typeface.DEFAULT_BOLD
}

private fun withAlpha(color: Int, alpha: Float): Int =
    (color and 0x00FFFFFF) or ((((color ushr 24) and 0xFF) * alpha.coerceIn(0f, 1f)).roundToInt() shl 24)

/** The copy broken into lines that fit [width], word by word. */
private fun wrap(text: String, paint: Paint, width: Float): List<String> {
    val lines = ArrayList<String>()
    var line = ""
    for (word in text.split(" ")) {
        val candidate = if (line.isEmpty()) word else "$line $word"
        if (line.isNotEmpty() && paint.measureText(candidate) > width) {
            lines.add(line)
            line = word
        } else {
            line = candidate
        }
    }
    if (line.isNotEmpty()) lines.add(line)
    return lines
}

/**
 * The photo, screen-sized and cropped to cover, drifting in a little as the welcome settles; a
 * gradient in the accent stands in until it has loaded.
 */
private fun drawPhoto(canvas: Canvas, m: KineticMotion, w: Float, h: Float, p: KineticPaints, accent: Int, alpha: Float) {
    val photo = m.photo
    p.fill.alpha = (alpha * 255).roundToInt()
    if (photo == null) {
        if (p.fallback == null || p.fallbackKey != w + h) {
            p.fallback = LinearGradient(0f, 0f, w, h, accent, 0xFF5B2BFF.toInt(), Shader.TileMode.CLAMP)
            p.fallbackKey = w + h
        }
        p.fill.shader = p.fallback
        canvas.drawRect(0f, 0f, w, h, p.fill)
        p.fill.shader = null
        return
    }
    val drift = 1.08f - 0.08f * EaseOut.transform(seg(m.zoom.value, 0.3f, 1f) * 0.6f + m.final.value * 0.4f)
    val s = max(w / photo.width, h / photo.height)
    val save = canvas.save()
    canvas.clipRect(0f, 0f, w, h)
    canvas.scale(drift, drift, w / 2f, h / 2f)
    canvas.translate(w / 2f - photo.width * s / 2f, h / 2f - photo.height * s / 2f)
    canvas.scale(s, s)
    canvas.drawBitmap(photo, 0f, 0f, p.fill)
    canvas.restoreToCount(save)
    p.fill.alpha = 255
}

/** One frame: the lines, the reel, the photo through the last word, the welcome and the way out. */
private fun drawKinetic(
    canvas: Canvas,
    m: KineticMotion,
    set: TypeSet,
    p: KineticPaints,
    c: KineticColors,
    name: String,
    title: String,
    caption: String,
    cta: String,
    fontScale: Float,
) {
    val w = set.w
    val h = set.h
    val reduced = m.reducedMotion
    val cap = set.cap
    val pad = set.pad
    val windowH = set.windowH
    val baselines = set.baselines
    canvas.drawColor(c.background)
    p.fill.shader = null

    // The lines that stay: each letter rises out of its baseline, and leaves upwards for the reveal.
    set.lines.forEachIndexed { line, word ->
        val save = canvas.save()
        canvas.clipRect(0f, baselines[line] - cap - pad, w, baselines[line] - cap - pad + windowH)
        word.letters.forEachIndexed { index, letter ->
            val from = line * LINE_LAG + index * LETTER_LAG
            val enter = seg(m.intro.value, from, from + LETTER_TIME)
            val away = seg(m.outro.value, index * LETTER_LAG, index * LETTER_LAG + LETTER_TIME)
            val dy = if (reduced) 0f else (1f - EaseOut.transform(enter)) * windowH - EaseInOut.transform(away) * windowH
            val alpha = if (reduced) enter * (1f - away) else 1f
            if (alpha <= 0f) return@forEachIndexed
            p.fill.color = withAlpha(c.type, alpha)
            val at = canvas.save()
            canvas.translate(word.xs[index], baselines[line] + dy)
            canvas.drawPath(letter, p.fill)
            canvas.restoreToCount(at)
        }
        canvas.restoreToCount(save)
    }

    // The reel: every word's letters on their own columns, so a word rolls letter after letter.
    val reelAlpha = 1f - m.fill.value
    if (reelAlpha > 0f) {
        val save = canvas.save()
        val top = baselines[2] - cap - pad
        canvas.clipRect(0f, top, w, top + windowH)
        if (reelAlpha < 1f) canvas.saveLayerAlpha(0f, top, w, top + windowH, (reelAlpha * 255).roundToInt())
        p.reel.reset()
        set.words.forEachIndexed { wi, word ->
            word.letters.forEachIndexed { index, letter ->
                val from = 2 * LINE_LAG + index * LETTER_LAG
                val offset = wi - m.cols[min(index, COLUMNS - 1)]
                if (reduced) {
                    // Letters fade in place and crossfade between words.
                    val alpha = max(0f, 1f - abs(offset)) * seg(m.intro.value, from, from + LETTER_TIME)
                    if (alpha <= 0f) return@forEachIndexed
                    p.placed.reset()
                    p.placed.addPath(letter, word.xs[index], baselines[2])
                    p.glow.color = withAlpha(c.accent, 0.45f * alpha)
                    canvas.drawPath(p.placed, p.glow)
                    p.fill.color = withAlpha(c.accent, alpha)
                    canvas.drawPath(p.placed, p.fill)
                    return@forEachIndexed
                }
                if (abs(offset) > 1.05f) return@forEachIndexed
                val enter = EaseOut.transform(seg(m.intro.value, from, from + LETTER_TIME))
                p.reel.addPath(letter, word.xs[index], baselines[2] + offset * windowH + (1f - enter) * windowH)
            }
        }
        if (!reduced) {
            // A neon halo under the letters.
            p.glow.color = withAlpha(c.accent, 0.45f)
            canvas.drawPath(p.reel, p.glow)
            p.fill.color = c.accent
            canvas.drawPath(p.reel, p.fill)
        }
        canvas.restoreToCount(save)
    }

    val lastWord = set.words.last()
    // The halo stays under the photo-filled word until it starts to grow.
    val halo = 0.7f * m.fill.value * (1f - seg(m.zoom.value, 0f, 0.25f))
    if (halo > 0f) {
        p.halo.color = withAlpha(c.accent, halo)
        val save = canvas.save()
        canvas.translate(0f, baselines[2])
        canvas.drawPath(lastWord.path, p.halo)
        canvas.restoreToCount(save)
    }

    // The last word as a window onto the photo, growing about its first stem until it is the screen.
    val photoInType = m.fill.value * (if (reduced) 1f - m.zoom.value else 1f)
    if (photoInType > 0f) {
        val z = if (reduced) 1f else ZOOM_MAX.pow(EaseInOut.transform(m.zoom.value))
        val fx = MARGIN + cap * 0.12f
        val fy = baselines[2] - cap * 0.45f
        val save = canvas.save()
        canvas.scale(z, z, fx, fy)
        canvas.translate(0f, baselines[2])
        canvas.clipPath(lastWord.path)
        canvas.translate(0f, -baselines[2])
        canvas.scale(1f / z, 1f / z, fx, fy)
        drawPhoto(canvas, m, w, h, p, c.accent, photoInType)
        canvas.restoreToCount(save)
    }
    val photoFull = if (reduced) m.zoom.value else seg(m.zoom.value, 0.9f, 1f)
    if (photoFull > 0f) drawPhoto(canvas, m, w, h, p, c.accent, photoFull)

    val final = m.final.value
    if (final > 0f) {
        if (p.shade == null || p.shadeH != h) {
            p.shade = LinearGradient(
                0f, h * 0.4f, 0f, h,
                intArrayOf(0x0008060A, 0xB808060A.toInt(), 0xF208060A.toInt()), floatArrayOf(0f, 0.42f, 1f), Shader.TileMode.CLAMP,
            )
            p.shadeH = h
        }
        p.fill.shader = p.shade
        p.fill.alpha = (final * 255).roundToInt()
        canvas.drawRect(0f, 0f, w, h, p.fill)
        p.fill.shader = null
        p.fill.alpha = 255
        drawWelcome(canvas, m, set, p, c, name, title, caption, cta, fontScale)
    }

    // The way out: a panel of the background rises over everything (fades in, under reduced motion).
    val e = m.exit.value
    if (e > 0f) {
        if (reduced) {
            p.fill.color = withAlpha(c.background, e)
            canvas.drawRect(0f, 0f, w, h, p.fill)
        } else {
            p.fill.color = c.background
            canvas.drawRect(0f, h * (1f - EaseInOut.transform(e)), w, h * 2f, p.fill)
        }
    }
}

/** What sits over the photo once it fills the screen. */
private fun drawWelcome(
    canvas: Canvas,
    m: KineticMotion,
    set: TypeSet,
    p: KineticPaints,
    c: KineticColors,
    name: String,
    title: String,
    caption: String,
    cta: String,
    fontScale: Float,
) {
    val w = set.w
    val final = m.final.value
    val exit = m.exit.value
    val reduced = m.reducedMotion
    // A welcome element fades and rises in from `from`, and fades out in the first 40% of the way out.
    fun alphaOf(from: Float) = seg(final, from, from + 0.4f) * (1f - seg(exit, 0f, 0.4f))
    fun riseOf(from: Float, distance: Float) = if (reduced) 0f else (1f - EaseOut.transform(seg(final, from, from + 0.6f))) * distance
    val t = p.text
    t.textAlign = Paint.Align.LEFT

    val nameAlpha = alphaOf(0f)
    if (nameAlpha > 0f) {
        t.typeface = p.condensed
        t.textSize = 17f * fontScale
        t.letterSpacing = 3f / 17f
        t.color = withAlpha(c.type, nameAlpha)
        canvas.drawText(name, MARGIN, set.top + 16f + riseOf(0f, 10f) - t.fontMetrics.top, t)
    }

    // The title and the caption sit together 30 dp above the button.
    t.typeface = p.medium
    t.textSize = 16f * fontScale
    t.letterSpacing = 0f
    val captionLines = wrap(caption, t, min(320f, w - MARGIN * 2f))
    val titleLines = title.split("\n")
    val captionTop = set.ctaTop - 30f - 22f * captionLines.size
    val titleTop = captionTop - 12f - 40f * titleLines.size

    val titleAlpha = alphaOf(0.1f)
    if (titleAlpha > 0f) {
        t.typeface = p.condensed
        t.textSize = 38f * fontScale
        t.letterSpacing = -0.3f / 38f
        t.color = withAlpha(c.type, titleAlpha)
        val fm = t.fontMetrics
        val rise = riseOf(0.1f, 30f)
        // Each line sits in a 40 dp box, its glyphs centred in it.
        titleLines.forEachIndexed { i, line ->
            canvas.drawText(line, MARGIN, titleTop + i * 40f + (40f - (fm.descent - fm.ascent)) / 2f - fm.ascent + rise, t)
        }
    }
    val captionAlpha = alphaOf(0.22f)
    if (captionAlpha > 0f) {
        t.typeface = p.medium
        t.textSize = 16f * fontScale
        t.letterSpacing = 0f
        t.color = withAlpha(withAlpha(c.type, 0xC0 / 255f), captionAlpha)
        val fm = t.fontMetrics
        val rise = riseOf(0.22f, 24f)
        captionLines.forEachIndexed { i, line ->
            canvas.drawText(line, MARGIN, captionTop + i * 22f + (22f - (fm.descent - fm.ascent)) / 2f - fm.ascent + rise, t)
        }
    }

    val ctaAlpha = alphaOf(0.32f)
    if (ctaAlpha > 0f) {
        val save = canvas.save()
        canvas.translate(w / 2f, set.ctaTop + 28f + riseOf(0.32f, 50f))
        val scale = 1f - m.press.value * 0.03f
        canvas.scale(scale, scale)
        val half = w / 2f - MARGIN
        canvas.saveLayerAlpha(-half, -28f, half, 28f, (ctaAlpha * 255).roundToInt())
        p.fill.color = c.type
        p.rect.set(-half, -28f, half, 28f)
        canvas.drawRoundRect(p.rect, 28f, 28f, p.fill)
        t.textAlign = Paint.Align.CENTER
        t.typeface = p.bold
        t.textSize = 17f * fontScale
        t.letterSpacing = 0f
        t.color = 0xFF0A0A0B.toInt()
        val fm = t.fontMetrics
        canvas.drawText(cta, 0f, -(fm.ascent + fm.descent) / 2f, t)
        canvas.restoreToCount(save)
    }
}
