package com.ngocdevv.nativecook.blocks.navigation.editorialmenumorph

import android.graphics.Bitmap
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.ngocdevv.nativecook.shared.EaseOutStrong
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.launch

// The menu's timeline. Opening plays `open` from 0 to 1 over OPEN_TIME s, linearly; every part
// takes its own slice of it and eases within that slice, so closing (1 to 0 over CLOSE_TIME s)
// plays the same choreography backwards: the last letter drops first and the curtain leaves last.
private const val OPEN_TIME = 1.15f
private const val CLOSE_TIME = 0.9f
private const val REDUCED_TIME = 0.26f
private val PanelSlice = 0f to 0.42f
private val BurgerSlice = 0f to 0.36f
private const val WORDS_FROM = 0.3f
private const val WORD_STAGGER = 0.07f
private const val WORD_SPAN = 0.4f
private const val LETTER_STAGGER = 0.012f
private const val LETTER_SPAN = 0.3f
private val FooterSlice = 0.68f to 1f
private val EaseInOutPanel = CubicBezierEasing(0.77f, 0f, 0.175f, 1f)
private val EaseWipe = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)

/** Reanimated's default timing curve, `Easing.inOut(Easing.quad)`. */
private val EaseQuad = Easing { t -> if (t < 0.5f) 2f * t * t else 1f - (-2f * t + 2f).pow(2) / 2f }

// The curtain: its leading edge bows BULGE dp ahead of its sides at mid-travel (sin of its
// progress), flat when it starts and when it lands. The page under it sinks SINK dp, shrinks by
// SINK_SCALE and darkens to SINK_SHADE black.
private const val BULGE = 110f
private const val SINK = 34f
private const val SINK_SCALE = 0.04f
private const val SINK_SHADE = 0.28f

// The words: WORD_SIZE serif (smaller only if the widest word would not fit the screen), one per
// ROW dp, each in a box that clips at its own baseline so its letters rise out of nothing. The
// list starts LIST_TOP dp below the safe area.
private const val WORD_SIZE = 52f
private const val ROW = 80f
private const val CLIP = 66f
private const val LIST_TOP = 112f
private const val LIST_LEFT = 22f
private const val NUMBER_WIDTH = 30f
private const val FOCUS_SHIFT = 14f
private const val DIMMED = 0.28f
private const val RULE_Y = CLIP + 3f
private const val COUNT_ROOM = 6f + 34f

// The floating preview: a round photo PREVIEW_W pt across in a BORDER pt ring of paper, sitting
// PREVIEW_DX right of and PREVIEW_DY above the finger, chasing it at LAG (1/s) and leaning with
// its horizontal speed up to LEAN_MAX°. A new word wipes its photo up from the bottom over
// WIPE_TIME s while it settles from WIPE_ZOOM, with a band of the word's colour on the wipe's edge.
private const val PREVIEW_W = 168f
private const val PREVIEW_H = PREVIEW_W
private const val PREVIEW_DX = 100f
private const val PREVIEW_DY = -64f
private const val BORDER = 3f
private const val LAG = 7f
private const val LEAN_PER_SPEED = 0.012f
private const val LEAN_MAX = 9f
private const val WIPE_TIME = 0.46f
private const val WIPE_ZOOM = 1.14f
private const val BAND = 5f
private const val BG_TIME = 0.42f

// Item to page: the card flies to the hero's frame on a spring (stiffness FLIGHT_K, damping
// ratio FLIGHT_ZETA, mass 1), integrated per frame; its corners go from 3 dp to 0.
private const val FLIGHT_K = 180f
private const val FLIGHT_ZETA = 0.9f
private val FLIGHT_C = 2f * FLIGHT_ZETA * sqrt(FLIGHT_K)

// autoPlay (seconds): rest, tap the burger, glide down the words and back up, lift on
// Architecture, rest on its page, tap the burger, glide up to Exhibitions, lift, rest. The end
// state equals the start state, so one LOOP loops cleanly.
private const val LOOP = 16f
private val Taps = floatArrayOf(1.0f, 10.0f)
private val Glides = listOf(
    listOf(2.4f to 0f, 2.9f to 0f, 3.5f to 1f, 4.1f to 2f, 4.7f to 3f, 5.3f to 4f, 6.2f to 2f, 6.8f to 2f),
    listOf(11.4f to 2f, 11.9f to 2f, 12.5f to 1f, 13.1f to 0f, 13.6f to 0f),
)
private const val USER_HOLD = 4f
/** After a touch's pause the loop restarts this long before its own start, at rest. */
private const val RESUME_REST = 1f
private const val TAP_MS = 500L
private const val MIN_TOP = 24f

private fun clamp(x: Float, lo: Float = 0f, hi: Float = 1f) = min(hi, max(lo, x))

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

private fun part(t: Float, slice: Pair<Float, Float>) = clamp((t - slice.first) / (slice.second - slice.first))

private fun wordReveal(t: Float, index: Int) = EaseOutStrong.transform(clamp((t - WORDS_FROM - index * WORD_STAGGER) / WORD_SPAN))

private fun letterReveal(t: Float, index: Int, letter: Int) =
    EaseOutStrong.transform(clamp((t - WORDS_FROM - index * WORD_STAGGER - letter * LETTER_STAGGER) / LETTER_SPAN))

private fun crossed(before: Float, now: Float, at: Float) =
    (before < at && now >= at) || (now < before && (at > before || at <= now))

/** 1 at [at], falling to 0 [width] s either side. */
private fun pulse(c: Float, at: Float, width: Float) = clamp(1f - abs(c - at) / width)

private fun smooth(u: Float): Float {
    val t = clamp(u)
    return t * t * (3f - 2f * t)
}

/** Two colours mixed like Reanimated's `interpolateColor`: in RGB raised to the power 2.2. */
private fun mix(a: Color, b: Color, t: Float): Color {
    fun ch(x: Float, y: Float): Float {
        val v = x.pow(2.2f) + (y.pow(2.2f) - x.pow(2.2f)) * t
        return max(0f, v).pow(1f / 2.2f)
    }
    return Color(ch(a.red, b.red), ch(a.green, b.green), ch(a.blue, b.blue))
}

/** A number timed from where it is to a target on a curve, like `withTiming` (with an optional delay). */
internal class Timed(initial: Float) {
    var value = initial
        private set
    private var from = 0f
    private var to = 0f
    private var time = 0f
    private var delay = 0f
    private var duration = 0f
    private var curve: Easing = EaseQuad
    private var moving = false

    fun set(next: Float) {
        value = next
        moving = false
    }

    fun animate(target: Float, seconds: Float, easing: Easing = EaseQuad, after: Float = 0f) {
        from = value
        to = target
        time = 0f
        delay = after
        duration = seconds
        curve = easing
        moving = true
    }

    fun step(dt: Float) {
        if (!moving) return
        time += dt
        val t = time - delay
        if (t < 0f) return
        val p = if (duration > 0f) min(1f, t / duration) else 1f
        value = from + (to - from) * curve.transform(p)
        if (p >= 1f) moving = false
    }
}

private enum class Touch { None, Burger, Menu, Ignored }

/** The menu's state, stepped once a frame like the RN frame callback, and the touch that drives it. */
internal class MenuEngine(val sections: List<MenuSection>) {
    val count = sections.size
    val tints = sections.map { Color(it.tint) }
    var width = 0f
    var height = 0f
    var top = 0f
    var bottom = 0f
    val listTop get() = top + LIST_TOP
    val open = Timed(0f)
    var isOpen = false
    var dir = 1f
    var hover = -1
    val focus = Array(count) { Timed(1f) }
    val shift = Array(count) { Timed(0f) }
    val italic = Array(count) { Timed(0f) }
    val rule = Array(count) { Timed(0f) }
    var current = 0
    var previous = -1
    val wipe = Timed(1f)
    val shown = Timed(0f)
    var fingerX = 0f
    var fingerY = 0f
    var px = 0f
    var py = 0f
    var lean = 0f
    var bgFrom = BaseTint
    var bgTo = BaseTint
    val bgT = Timed(1f)
    // The card as drawn: centre, size, turn (degrees), corner radius, opacity, and how far its
    // empty tone has gone from the menu's to the page's placeholder.
    var cardX = 0f
    var cardY = 0f
    var cardW = PREVIEW_W
    var cardH = PREVIEW_H
    var cardRot = 0f
    var cardR = PREVIEW_W / 2f
    var cardAlpha = 0f
    var cardTone = 0f
    // Item to page: true while the card flies to (or sits on) the hero.
    var flying = false
    var flight = 0f
    var flightV = 0f
    val flightAlpha = Timed(1f)
    var startX = 0f
    var startY = 0f
    var startW = PREVIEW_W
    var startH = PREVIEW_H
    var startRot = 0f
    val heroShown = Timed(1f)
    val pageIn = Timed(1f)
    // The page under the menu; Compose reads them.
    var section by mutableIntStateOf(0)
    var previousSection by mutableIntStateOf(0)
    // How much the widest word and title line must shrink to fit the screen (1 when they fit).
    var wordFit = 1f
    var titleFit = 1f
    var romanW = FloatArray(count)
    var italicW = FloatArray(count)
    // autoPlay
    var clock = 0f
    var looping = false
    var hold = 0f
    var userTouch = false
    var ghostX = 0f
    var ghostY = 0f
    var ghostAlpha = 0f
    var ghostPress = 0f
    var reduced = false
    private var touch = Touch.None
    // Bumped once a frame; the drawing reads it, so it is drawn again without composing.
    var tick by mutableLongStateOf(0L)

    var light: () -> Unit = {}
    var selection: () -> Unit = {}
    var selected: (Int) -> Unit = {}

    val background get() = mix(bgFrom, bgTo, bgT.value)

    /** The card has landed and the curtain is up: the page's hero takes over, its text writes in. */
    private fun handoff() {
        flying = false
        shown.set(0f)
        cardAlpha = 0f
        if (reduced) heroShown.animate(1f, 0.3f) else heroShown.set(1f)
        pageIn.set(0f)
        pageIn.animate(1f, PAGE_IN_TIME, LinearEasing)
        hoverOn(-1)
    }

    fun toggle(user: Boolean) {
        if (flying) handoff()
        isOpen = !isOpen
        dir = if (isOpen) 1f else -1f
        hoverOn(-1)
        open.animate(if (isOpen) 1f else 0f, if (reduced) REDUCED_TIME else if (isOpen) OPEN_TIME else CLOSE_TIME, LinearEasing)
        if (user) light()
    }

    private fun moveTo(next: Int) {
        previousSection = section
        section = next
    }

    /** Lifting on word [h]: the card flies to the hero while the curtain lifts off the new page. */
    private fun commit(h: Int) {
        val s = 0.86f + 0.14f * shown.value
        startX = px
        startY = py
        startW = PREVIEW_W * s
        startH = PREVIEW_H * s
        startRot = lean
        flight = 0f
        flightV = 0f
        if (reduced) {
            flightAlpha.set(shown.value)
            flightAlpha.animate(0f, 0.2f)
        } else {
            flightAlpha.set(max(shown.value, 0.01f))
            flightAlpha.animate(1f, 0.16f)
        }
        if (current != h) {
            previous = -1
            current = h
            wipe.set(1f)
        }
        flying = true
        heroShown.set(0f)
        pageIn.set(0f)
        // The same choreography as a close, keeping the chosen word lit while it drops.
        isOpen = false
        dir = -1f
        open.animate(0f, if (reduced) REDUCED_TIME else CLOSE_TIME, LinearEasing)
        moveTo(h)
        if (userTouch) light()
        selected(h)
    }

    /** Shows section [i] without the menu: its hero fades in and its text writes in again. */
    private fun swapTo(i: Int) {
        if (flying) handoff()
        heroShown.set(0f)
        heroShown.animate(1f, 0.4f, after = 0.08f)
        pageIn.set(0f)
        pageIn.animate(1f, PAGE_IN_TIME, LinearEasing)
        moveTo(i)
        selected(i)
    }

    /** The word under a point, or -1. */
    private fun wordAt(x: Float, y: Float): Int {
        val k = floor((y - listTop) / ROW).toInt()
        if (k < 0 || k >= count || x > width - 8f) return -1
        return k
    }

    private fun release() {
        val h = hover
        if (h >= 0 && isOpen && open.value > 0.9f && !flying) commit(h) else hoverOn(-1)
    }

    /** A new word under the finger: the words dim, step and turn italic, the menu turns, the photo wipes in. */
    private fun hoverOn(now: Int) {
        val before = hover
        if (now == before) return
        hover = now
        for (i in 0 until count) {
            val on = now < 0 || now == i
            val held = now == i
            focus[i].animate(if (on) 1f else DIMMED, 0.22f)
            shift[i].animate(if (held && !reduced) FOCUS_SHIFT else 0f, 0.32f, EaseOutStrong)
            italic[i].animate(if (held) 1f else 0f, 0.24f)
            if (held) rule[i].animate(1f, 0.38f, EaseOutStrong) else rule[i].animate(0f, 0.2f)
        }
        bgFrom = background
        bgTo = if (now in 0 until count) tints[now] else BaseTint
        bgT.set(0f)
        bgT.animate(1f, BG_TIME)
        if (flying) return
        if (now < 0) {
            shown.animate(0f, 0.22f)
            return
        }
        if (before < 0 || shown.value < 0.05f) {
            // The first word under the finger: the card appears where the finger is, already filled.
            previous = -1
            current = now
            wipe.set(1f)
            px = fingerX + PREVIEW_DX
            py = fingerY + PREVIEW_DY
        } else {
            previous = current
            current = now
            wipe.set(0f)
            wipe.animate(1f, if (reduced) 0.001f else WIPE_TIME, EaseWipe)
        }
        shown.animate(1f, 0.26f, EaseOutStrong)
        if (userTouch) selection()
    }

    private fun touched() {
        hold = USER_HOLD
        userTouch = true
        ghostAlpha = 0f
    }

    fun frame(dt: Float) {
        open.step(dt)
        wipe.step(dt)
        shown.step(dt)
        bgT.step(dt)
        flightAlpha.step(dt)
        heroShown.step(dt)
        pageIn.step(dt)
        for (i in 0 until count) {
            focus[i].step(dt)
            shift[i].step(dt)
            italic[i].step(dt)
            rule[i].step(dt)
        }
        script(dt)
        tick += 1
    }

    private fun script(dt: Float) {
        // The card chases the finger; it leans into its own sideways speed.
        val w = width
        val tx = clamp(fingerX + PREVIEW_DX, PREVIEW_W / 2f + 10f, max(PREVIEW_W / 2f + 10f, w - PREVIEW_W / 2f - 10f))
        val ty = max(fingerY + PREVIEW_DY, top + 60f + PREVIEW_H / 2f)
        val k = if (reduced) 1f else 1f - exp(-dt * LAG)
        val nx = px + (tx - px) * k
        val vx = (nx - px) / dt
        px = nx
        py += (ty - py) * k
        val leanTarget = if (reduced) 0f else clamp(vx * LEAN_PER_SPEED, -LEAN_MAX, LEAN_MAX)
        lean += (leanTarget - lean) * (1f - exp(-dt * 10f))

        if (flying) {
            // The flight: a damped spring from 0 to 1, in small steps so a long frame stays stable.
            var f = flight
            var v = flightV
            if (reduced) {
                f = 1f
                v = 0f
            } else {
                val h = dt / 4f
                repeat(4) {
                    v += (-FLIGHT_K * (f - 1f) - FLIGHT_C * v) * h
                    f += v * h
                }
                if (abs(1f - f) < 0.0015f && abs(v) < 0.02f) {
                    f = 1f
                    v = 0f
                }
            }
            flight = f
            flightV = v
            // The hero's frame as it is drawn right now, inside the sinking page.
            val l = PageLayout(w, height, top, bottom)
            val p = if (reduced) 0f else EaseInOutPanel.transform(part(open.value, PanelSlice))
            val s = 1f - SINK_SCALE * p
            val hx = w / 2f + (l.heroX + l.heroW / 2f - w / 2f) * s
            val hy = height / 2f + (l.heroY + l.heroH / 2f - height / 2f) * s + SINK * p
            val ff = if (reduced) 0f else clamp(f)
            cardX = lerp(startX, hx, ff)
            cardY = lerp(startY, hy, ff)
            cardW = lerp(startW, l.heroW * s, ff)
            cardH = lerp(startH, l.heroH * s, ff)
            cardRot = lerp(startRot, 0f, ff)
            // A circle opening into the hero's rectangle: the corner keeps to half the shorter side,
            // shrinking to nothing a little ahead of the size so the last of the round goes calmly.
            cardR = min(cardW, cardH) / 2f * (1f - ff).pow(1.4f)
            cardTone = ff
            cardAlpha = flightAlpha.value
            if (f >= 1f && open.value <= 0.0005f) handoff()
        } else {
            val s = 0.86f + 0.14f * shown.value
            cardX = px
            cardY = py
            cardW = PREVIEW_W * s
            cardH = PREVIEW_H * s
            cardRot = lean
            cardR = PREVIEW_W * s / 2f
            cardTone = 0f
            cardAlpha = shown.value
        }

        if (hold > 0f) {
            hold -= dt
            if (hold <= 0f && looping) {
                // The pause is over: close the menu, go back to the first page, start the loop again.
                clock = -RESUME_REST
                userTouch = false
                if (isOpen) toggle(false)
                if (section != 0) swapTo(0)
            }
            return
        }
        if (!looping || w <= 0f) return
        val before = clock
        var c = before + dt
        if (c >= LOOP) c -= LOOP
        clock = c

        val burgerX = w - 36f
        val burgerY = top + 30f
        var ghostOn = 0f
        var press = 0f
        for (at in Taps) {
            if (crossed(before, c, at - 0.3f)) {
                ghostX = burgerX
                ghostY = burgerY
            }
            if (crossed(before, c, at) && !isOpen) toggle(false)
            ghostOn = max(ghostOn, min(1f, 2f * pulse(c, at, 0.3f)))
            press = max(press, pulse(c, at, 0.12f))
        }
        // The glides: from word to word, easing between stops, drifting a little to the right;
        // the last stop is the lift.
        for (glide in Glides) {
            val first = glide.first().first
            val lift = glide.last().first
            if (c >= first && c < lift && isOpen) {
                var row = glide[0].second
                for (i in 1 until glide.size) {
                    val (t0, r0) = glide[i - 1]
                    val (t1, r1) = glide[i]
                    if (c >= t0) row = lerp(r0, r1, smooth((c - t0) / (t1 - t0)))
                }
                val x = LIST_LEFT + 90f + 50f * sin((c - first) * 1.3f)
                val y = listTop + (row + 0.5f) * ROW
                fingerX = x
                fingerY = y
                ghostX = x
                ghostY = y
                hoverOn(wordAt(x, y))
            }
            if (crossed(before, c, lift)) release()
            ghostOn = max(ghostOn, clamp(min((c - first + 0.25f) / 0.2f, (lift + 0.2f - c) / 0.2f)))
        }
        ghostAlpha = ghostOn
        ghostPress = press
    }

    // One touch for the whole screen: a tap on the burger (or its label) toggles the menu; over
    // the open menu the finger is a pointer from touch down, and lifting on a word opens its page.

    private fun onBurger(x: Float, y: Float) = x >= width - 36f - 22f - 58f && x <= width && abs(y - (top + 30f)) <= 32f

    fun down(x: Float, y: Float) {
        touched()
        touch = when {
            onBurger(x, y) -> Touch.Burger
            isOpen -> Touch.Menu
            else -> Touch.Ignored
        }
        if (touch == Touch.Menu) move(x, y)
    }

    fun move(x: Float, y: Float) {
        if (touch != Touch.Menu) return
        fingerX = x
        fingerY = y
        if (isOpen) hoverOn(wordAt(x, y))
    }

    fun up(x: Float, y: Float, quick: Boolean) {
        when (touch) {
            Touch.Burger -> if (quick && onBurger(x, y)) toggle(true)
            Touch.Menu -> release()
            else -> Unit
        }
        touch = Touch.None
    }
}

/**
 * A museum's site with a hamburger button. Tapping it morphs the two lines into a cross and rolls
 * "Menu" to "Close" while a curtain with a soft liquid edge comes down over the sinking page and
 * the menu's serif words rise letter by letter from their clipped baselines. Running a finger
 * down the words turns the one under it italic, rules it and floats a lagging, leaning photo
 * card that wipes to each word's photo while the menu takes that word's colour. Lifting on a
 * word flies the card into its page's hero as the curtain lifts, then the page writes itself in.
 *
 * @param autoPlay Opens the menu, glides down the words, opens a page from it and comes back, on a loop.
 * @param onSelect Called with a section's word when it is chosen from the menu.
 */
@Composable
fun EditorialMenuMorph(
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false,
    onSelect: ((String) -> Unit)? = null,
) {
    val sections = MenuSections
    val reduced = rememberReducedMotion()
    val auto = autoPlay && !reduced
    val view = LocalView.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val select by rememberUpdatedState(onSelect)
    val engine = remember { MenuEngine(sections) }
    val photos = remember { arrayOfNulls<Bitmap>(sections.size) }
    val paints = remember { CoverPaints() }
    val curtain = remember { Path() }

    val insets = WindowInsets.safeDrawing
    // With the status bar hidden Android reports no top inset; keep the header off the glass edge.
    engine.top = max(insets.getTop(density) / density.density, MIN_TOP)
    engine.bottom = insets.getBottom(density) / density.density
    engine.reduced = reduced
    engine.light = { view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK) }
    engine.selection = { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
    engine.selected = { i -> sections.getOrNull(i)?.let { select?.invoke(it.word) } }

    LaunchedEffect(engine, auto) {
        engine.looping = auto
        engine.clock = 0f
        engine.hold = 0f
    }
    LaunchedEffect(engine) {
        var last = -1L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last < 0) 1f / 60f else min((now - last) / 1e9f, 0.064f)
                last = now
                if (dt > 0f) engine.frame(dt)
            }
        }
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Paper)
            .semantics {
                contentDescription = "Orsel, Museum of Form. A menu button at the top right opens the sections: ${sections.joinToString(", ") { it.word }}."
            }
            .pointerInput(engine) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    engine.down(down.position.x / density.density, down.position.y / density.density)
                    var last = down.position
                    var lastTime = down.uptimeMillis
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        last = change.position
                        lastTime = change.uptimeMillis
                        if (!change.pressed) break
                        engine.move(change.position.x / density.density, change.position.y / density.density)
                        change.consume()
                    }
                    engine.up(last.x / density.density, last.y / density.density, lastTime - down.uptimeMillis <= TAP_MS)
                }
            },
    ) {
        val width = maxWidth.value
        val height = maxHeight.value
        engine.width = width
        engine.height = height
        val top = engine.top
        val bottom = engine.bottom
        val layout = PageLayout(width, height, top, bottom)

        // One photo per section at the hero's size: the menu's card and the page's hero draw the same bitmap.
        LaunchedEffect(context, layout.heroW.roundToInt(), layout.heroH.roundToInt()) {
            val loader = SingletonImageLoader.get(context)
            sections.forEachIndexed { i, s ->
                launch {
                    val request = ImageRequest.Builder(context).data(photoUrl(s.photo, layout.heroW, layout.heroH)).allowHardware(false).build()
                    val result = loader.execute(request) as? SuccessResult ?: return@launch
                    photos[i] = result.image.toBitmap()
                }
            }
        }

        // The widths the menu needs: each word set roman letter by letter and italic, and the fits.
        val wordSp = with(density) { WORD_SIZE.dp.toSp() }
        val romanStyle = TextStyle(fontFamily = Display, fontWeight = DisplayWeight, fontSize = wordSp, letterSpacing = with(density) { (-0.6f).dp.toSp() })
        val italicStyle = romanStyle.copy(fontStyle = FontStyle.Italic)
        val natural = remember(width, density) {
            sections.map { s ->
                val roman = s.word.sumOf { measurer.measure(it.toString(), romanStyle).size.width.toDouble() }.toFloat() / density.density
                val italic = measurer.measure(s.word, italicStyle).size.width / density.density
                roman to italic
            }
        }
        val titleNatural = remember(width, density) {
            val style = TextStyle(fontFamily = Display, fontWeight = DisplayWeight, fontSize = with(density) { TITLE_SIZE.dp.toSp() }, letterSpacing = with(density) { (-0.4f).dp.toSp() })
            sections.maxOf { s -> s.title.maxOf { measurer.measure(it, style).size.width / density.density } }
        }
        val room = width - LIST_LEFT - NUMBER_WIDTH - COUNT_ROOM - FOCUS_SHIFT - 6f
        engine.wordFit = clamp(room / max(1f, natural.maxOf { it.first }), 0.5f, 1f)
        engine.titleFit = clamp((width - 40f) / max(1f, titleNatural), 0.5f, 1f)
        for (i in natural.indices) {
            engine.romanW[i] = natural[i].first * engine.wordFit
            engine.italicW[i] = natural[i].second * engine.wordFit
        }

        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    engine.tick
                    val p = if (reduced) 0f else EaseInOutPanel.transform(part(engine.open.value, PanelSlice))
                    val s = 1f - SINK_SCALE * p
                    scaleX = s
                    scaleY = s
                    translationY = (SINK * p).dp.toPx()
                },
        ) {
            MuseumPage(engine, sections, photos, width, height, top, bottom, reduced, paints)
        }

        // The shade over the sinking page, then the curtain.
        Canvas(Modifier.fillMaxSize()) {
            engine.tick
            val p = EaseInOutPanel.transform(part(engine.open.value, PanelSlice))
            if (p > 0f) drawRect(Color.Black.copy(alpha = SINK_SHADE * p))
            val color = engine.background
            if (reduced) {
                if (p > 0f) drawRect(color.copy(alpha = p))
                return@Canvas
            }
            val edge = p * size.height
            if (edge <= 0.5f || size.width <= 0f) return@Canvas
            val w = size.width
            val bow = edge + 4f / 3f * engine.dir * BULGE.dp.toPx() * sin(PI.toFloat() * p)
            curtain.reset()
            curtain.moveTo(-2f, -2f)
            curtain.lineTo(w + 2f, -2f)
            curtain.lineTo(w + 2f, edge)
            curtain.cubicTo(w * 0.72f, bow, w * 0.28f, bow, -2f, edge)
            curtain.close()
            drawPath(curtain, color)
        }

        Menu(engine, sections, bottom, reduced)

        Canvas(Modifier.fillMaxSize()) {
            engine.tick
            drawIntoCanvas { drawCard(it.nativeCanvas, engine, photos, paints, density.density) }
        }

        Header(engine, top, reduced)

        Box(
            Modifier
                .size(44.dp)
                .graphicsLayer {
                    engine.tick
                    alpha = engine.ghostAlpha * 0.85f
                    translationX = (engine.ghostX - 22f).dp.toPx()
                    translationY = (engine.ghostY - 22f).dp.toPx()
                    val s = 1f - 0.2f * engine.ghostPress
                    scaleX = s
                    scaleY = s
                }
                .background(Color(120 / 255f, 110 / 255f, 98 / 255f, 0.35f), CircleShape)
                .border(1.5.dp, Color.White.copy(alpha = 0.8f), CircleShape),
        )
    }
}

@Composable
private fun Menu(engine: MenuEngine, sections: List<MenuSection>, bottom: Float, reduced: Boolean) {
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = LIST_LEFT.dp, top = engine.listTop.dp)) {
            for ((i, s) in sections.withIndex()) MenuWord(engine, i, s, reduced)
        }
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = LIST_LEFT.dp, end = 22.dp, bottom = (bottom + 22f).dp)
                .graphicsLayer {
                    engine.tick
                    val f = EaseOutStrong.transform(part(engine.open.value, FooterSlice))
                    alpha = f
                    translationY = if (reduced) 0f else ((1f - f) * 16f).dp.toPx()
                }
                .drawBehind { drawRect(Paper.copy(alpha = 0.35f), size = Size(size.width, 1f)) }
                .padding(top = 16.dp),
        ) {
            val footer = TextStyle(fontFamily = Sans, fontSize = dpSp(12f), fontWeight = FontWeight.Medium, color = Paper.copy(alpha = 0.7f))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    BasicText(
                        "Open today, 10:00–18:00",
                        style = TextStyle(fontFamily = Display, fontWeight = DisplayWeight, fontStyle = FontStyle.Italic, fontSize = dpSp(16f), color = Paper),
                    )
                    BasicText("Kestrel Quay 4, Lisbourne", style = footer)
                }
                Spacer(Modifier.weight(1f))
                BasicText("Tickets · Members · Shop", style = footer)
            }
        }
    }
}

/**
 * One menu word. Its letters rise one by one; under the finger it crossfades to its italic,
 * steps right and draws a rule under itself while the other words dim.
 */
@Composable
private fun MenuWord(engine: MenuEngine, index: Int, section: MenuSection, reduced: Boolean) {
    val size = dpSp(WORD_SIZE * engine.wordFit)
    val wordStyle = TextStyle(
        fontFamily = Display,
        fontWeight = DisplayWeight,
        fontSize = size,
        lineHeight = dpSp(CLIP),
        letterSpacing = dpSp(-0.6f),
        color = Paper,
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    )
    val italicStyle = wordStyle.copy(fontStyle = FontStyle.Italic)
    Row(
        Modifier
            .height(ROW.dp)
            .graphicsLayer {
                engine.tick
                alpha = engine.focus[index].value
                translationX = engine.shift[index].value.dp.toPx()
            },
    ) {
        BasicText(
            "%02d".format(index + 1),
            Modifier
                .width(NUMBER_WIDTH.dp)
                .padding(top = 12.dp)
                .graphicsLayer {
                    engine.tick
                    alpha = wordReveal(engine.open.value, index)
                },
            style = TextStyle(fontFamily = Display, fontWeight = DisplayWeight, fontStyle = FontStyle.Italic, fontSize = dpSp(15f), color = Paper.copy(alpha = 0.72f), fontFeatureSettings = "tnum"),
        )
        Box(
            Modifier.drawBehind {
                engine.tick
                val r = engine.rule[index].value
                if (r <= 0.001f) return@drawBehind
                val w = r * lerp(engine.romanW[index], engine.italicW[index], engine.italic[index].value)
                val a = wordReveal(engine.open.value, index)
                drawRect(Paper.copy(alpha = 0.85f * a), topLeft = Offset(0f, RULE_Y.dp.toPx()), size = Size(w.dp.toPx(), 1.dp.toPx()))
            },
        ) {
            Box(Modifier.height(CLIP.dp).clipToBounds()) {
                Row {
                    for ((j, ch) in section.word.withIndex()) {
                        BasicText(
                            ch.toString(),
                            Modifier.graphicsLayer {
                                engine.tick
                                val it = engine.italic[index].value
                                if (reduced) {
                                    alpha = wordReveal(engine.open.value, index) * (1f - it)
                                } else {
                                    val r = letterReveal(engine.open.value, index, j)
                                    alpha = 1f - it
                                    transformOrigin = TransformOrigin.Center
                                    translationY = ((1f - r) * CLIP).dp.toPx()
                                    rotationZ = (1f - r) * 4f
                                }
                            },
                            style = wordStyle,
                            softWrap = false,
                        )
                    }
                }
                BasicText(
                    section.word,
                    Modifier.graphicsLayer {
                        engine.tick
                        val r = wordReveal(engine.open.value, index)
                        val it = engine.italic[index].value
                        if (reduced) {
                            alpha = it * r
                        } else {
                            alpha = it
                            translationY = ((1f - r) * CLIP).dp.toPx()
                        }
                    },
                    style = italicStyle,
                    softWrap = false,
                )
            }
        }
        BasicText(
            section.count,
            Modifier
                .padding(start = 6.dp, top = 11.dp)
                .graphicsLayer {
                    engine.tick
                    alpha = wordReveal(engine.open.value, index)
                    translationX = (-(engine.romanW[index] - engine.italicW[index]) * engine.italic[index].value).dp.toPx()
                },
            style = TextStyle(fontFamily = Sans, fontSize = dpSp(11f), fontWeight = FontWeight.SemiBold, letterSpacing = dpSp(0.4f), color = Paper.copy(alpha = 0.62f), fontFeatureSettings = "tnum"),
            softWrap = false,
        )
    }
}

@Composable
private fun Header(engine: MenuEngine, top: Float, reduced: Boolean) {
    val ink = ColorProducer {
        engine.tick
        mix(Ink, Paper, EaseInOutPanel.transform(part(engine.open.value, BurgerSlice)))
    }
    val label = TextStyle(fontFamily = Sans, fontSize = dpSp(11f), lineHeight = dpSp(14f), fontWeight = FontWeight.SemiBold, letterSpacing = dpSp(1.6f))
    Row(
        Modifier
            .padding(start = 20.dp, end = 20.dp, top = (top + 6f).dp)
            .fillMaxWidth()
            .height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            BasicText("ORSEL", style = TextStyle(fontFamily = Display, fontSize = dpSp(24f), fontWeight = FontWeight.Bold, letterSpacing = dpSp(5f)), color = ink)
            BasicText(
                "MUSEUM OF FORM",
                Modifier.offset(y = (-1).dp),
                style = TextStyle(fontFamily = Sans, fontSize = dpSp(8.5f), fontWeight = FontWeight.SemiBold, letterSpacing = dpSp(2.6f)),
                color = ink,
            )
        }
        Spacer(Modifier.weight(1f))
        // "Menu" rolls up and out as "Close" rolls in, with the burger's morph.
        Box(Modifier.offset(x = 6.dp).widthIn(min = 44.dp).height(14.dp).clipToBounds(), contentAlignment = Alignment.CenterEnd) {
            BasicText(
                "MENU",
                Modifier.graphicsLayer {
                    engine.tick
                    val b = EaseInOutPanel.transform(part(engine.open.value, BurgerSlice))
                    if (reduced) alpha = 1f - b else translationY = (-b * 14f).dp.toPx()
                },
                style = label,
                color = ink,
            )
            BasicText(
                "CLOSE",
                Modifier.graphicsLayer {
                    engine.tick
                    val b = EaseInOutPanel.transform(part(engine.open.value, BurgerSlice))
                    if (reduced) alpha = b else translationY = ((1f - b) * 14f).dp.toPx()
                },
                style = label,
                color = ink,
            )
        }
        Box(Modifier.offset(x = 6.dp).size(44.dp)) {
            BurgerLine(engine, ink, upper = true)
            BurgerLine(engine, ink, upper = false)
        }
    }
}

/** One of the burger's two lines: they slide together, the short one lengthening, then cross. */
@Composable
private fun BurgerLine(engine: MenuEngine, ink: ColorProducer, upper: Boolean) {
    Box(
        Modifier
            .offset(9.dp, 21.2.dp)
            .size(26.dp, 1.6.dp)
            .graphicsLayer {
                engine.tick
                val b = EaseInOutPanel.transform(part(engine.open.value, BurgerSlice))
                val meet = clamp(b * 2f)
                val turn = clamp(b * 2f - 1f)
                if (upper) {
                    translationY = (-5f * (1f - meet)).dp.toPx()
                    rotationZ = 45f * turn
                    scaleX = 1f - 0.08f * meet
                } else {
                    val short = 17f / 26f
                    translationX = ((1f - meet) * ((26f - 17f) / 2f)).dp.toPx()
                    translationY = (5f * (1f - meet)).dp.toPx()
                    rotationZ = -45f * turn
                    scaleX = short + (0.92f - short) * meet
                }
            }
            .drawBehind {
                val r = 1.dp.toPx()
                drawRoundRect(ink(), cornerRadius = CornerRadius(r, r))
            },
    )
}

/**
 * The floating preview and, after a lift, the hero in flight: the photos split by the wipe, drawn
 * "cover" into the card's animating rect, and the band on the wipe's edge.
 */
private fun drawCard(canvas: android.graphics.Canvas, m: MenuEngine, photos: Array<Bitmap?>, p: CoverPaints, density: Float) {
    val alpha = m.cardAlpha
    val w = m.cardW
    val h = m.cardH
    if (alpha <= 0.002f || w <= 0.5f || h <= 0.5f || !m.cardX.isFinite() || !m.cardY.isFinite()) return
    canvas.save()
    canvas.scale(density, density)
    canvas.translate(m.cardX, m.cardY)
    canvas.rotate(m.cardRot)
    canvas.translate(-w / 2f, -h / 2f)
    canvas.saveLayerAlpha(0f, 0f, w, h, (clamp(alpha) * 255f).roundToInt())
    p.paint.color = mix(CardTone, HeroPlaceholder, m.cardTone).toArgb()
    canvas.drawRect(0f, 0f, w, h, p.paint)
    val wipe = m.wipe.value
    val edge = h * (1f - wipe)
    if (m.previous >= 0) {
        photos.getOrNull(m.previous)?.let {
            canvas.save()
            canvas.clipRect(0f, 0f, w, edge)
            drawCover(canvas, it, w, h, 1f, p)
            canvas.restore()
        }
    }
    if (m.current >= 0) {
        photos.getOrNull(m.current)?.let {
            canvas.save()
            canvas.clipRect(0f, edge, w, h)
            // The incoming photo settles from slightly zoomed in as it is uncovered.
            drawCover(canvas, it, w, h, WIPE_ZOOM - (WIPE_ZOOM - 1f) * EaseOutStrong.transform(wipe), p)
            canvas.restore()
        }
    }
    if (wipe > 0.001f && wipe < 0.999f) {
        p.paint.color = (m.tints.getOrNull(m.current) ?: BaseTint).toArgb()
        canvas.drawRect(0f, edge - BAND, w, edge, p.paint)
    }
    // The round shape: everything outside the rounded rect is cleared with an anti-aliased edge
    // (a hardware clipPath would leave the circle jagged).
    val r = max(0f, m.cardR)
    p.path.reset()
    p.path.addRoundRect(0f, 0f, w, h, r, r, android.graphics.Path.Direction.CW)
    p.path.fillType = android.graphics.Path.FillType.INVERSE_WINDING
    p.paint.color = android.graphics.Color.BLACK
    p.paint.xfermode = ClearOutside
    canvas.drawPath(p.path, p.paint)
    p.paint.xfermode = null
    p.path.fillType = android.graphics.Path.FillType.WINDING
    // The paper ring, just inside the edge; it thins and fades as the photo flies into the hero.
    val ringAlpha = clamp(1f - m.cardTone * 1.6f)
    if (ringAlpha > 0.002f && w > BORDER * 2f && h > BORDER * 2f) {
        val inset = BORDER / 2f
        val rr = max(0f, r - inset)
        p.path.reset()
        p.path.addRoundRect(inset, inset, w - inset, h - inset, rr, rr, android.graphics.Path.Direction.CW)
        p.paint.style = android.graphics.Paint.Style.STROKE
        p.paint.strokeWidth = max(0.5f, BORDER * (1f - m.cardTone))
        p.paint.color = Paper.copy(alpha = ringAlpha).toArgb()
        canvas.drawPath(p.path, p.paint)
        p.paint.style = android.graphics.Paint.Style.FILL
    }
    canvas.restore()
    canvas.restore()
}

/** Clears what lies under it: used to cut the card's round shape out of its layer. */
private val ClearOutside = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR)
