package com.ngocdevv.nativecook.blocks.forms.priceglyphscatterslider

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Tram
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.cssBoxShadow
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private object PriceGlyphScatterSliderPalette {
    val page = Color(0xFFF3F4F9)
    val card = Color(0xFFFFFFFF)
    val purple = Color(0xFF5B4FD6)
    val trackRest = Color(0xFFE6E7F2)
    val ink = Color(0xFF1B1B2F)
    val body = Color(0xFF6E6E85)
    val mute = Color(0xFF9A9AB0)
    val iconDisc = Color(0xFFEEEDFB)
    val shadow = Color(0xFF3C3C78)
}

/** Sizes in dp, the same numbers as the SwiftUI reference in pt. */
internal object PriceGlyphScatterSliderMetrics {
    const val MARGIN = 20.0
    const val CARD_TOP = 212.0
    const val CARD_HEIGHT = 164.0
    const val CARD_RADIUS = 22.0
    const val INSET = 22.0
    const val TRACK_WIDTH = 218.0
    const val TRACK_HEIGHT = 6.0
    const val ROW_TOP = 52.0
    const val ROW_HEIGHT = 44.0
    const val PRICE_GAP = 16.0
    const val PRICE_WIDTH = 75.0
    const val THUMB = 24.0
    const val THUMB_GROWN = 28.0
    const val RING = 3.0
    const val PRICE_SIZE = 26.0
    const val DOLLAR_SIZE = 22.0
    const val MIN_VALUE = 40
    const val MAX_VALUE = 600
    const val STEP = 5
    const val NIGHTS = 3
    const val SCATTER_Y = 14.0
    const val SCATTER_X = 4.0
    const val SCATTER_ROTATION = 14.0
    /** Thumb speed (dp/s along the track) that scatters the glyphs fully. */
    const val FULL_SPEED = 220.0
    const val BUTTON_HEIGHT = 54.0
    const val TILE_TOP = 52.0
    const val TILE_HEIGHT = 84.0
    const val TILE_GAP = 12.0
}

internal object PriceGlyphScatterSliderMotion {
    const val LOOP = 8.0
    /** The loop's drags: start and end times. The first leaves the configured value, the last returns to it. */
    val segments = listOf(0.9 to 2.1, 3.3 to 4.5, 5.6 to 6.6)
    /** Where the drags stop in between (as 0…1 along the track): $520, then $95. */
    val stops = listOf(0.857, 0.098)
    /** The places the drags visit, in order: home, the stops, home again. */
    fun path(home: Double) = listOf(home) + stops + listOf(home)
    const val WOBBLE_FOR = 0.8
    const val SMOOTHING = 0.05
    const val RELEASE_RESPONSE = 0.42
    const val RELEASE_DAMPING = 0.55
    const val GROW = 0.2
    const val SHRINK = 0.25
    const val RESUME = 2.0
    const val RETURN_FOR = 0.6

    fun clamp(t: Double) = min(1.0, max(0.0, t))
    fun seg(t: Double, a: Double, b: Double) = clamp((t - a) / (b - a))
    fun easeInOutSine(x: Double) = (1 - cos(PI * x)) / 2
    fun easeInOutSineSlope(x: Double) = PI / 2 * sin(PI * x)
    fun easeOutCubic(x: Double) = 1 - (1 - x).pow(3)
    /** The glyphs' overshoot after a drag stops: starts at 0, swings the other way and dies out. */
    fun wobble(e: Double, sign: Double, peak: Double): Double {
        if (e < 0 || e >= WOBBLE_FOR) return 0.0
        return -0.18 * sign * min(1.0, peak) * sin(2 * PI * 2 * e) * exp(-6 * e)
    }
    /** The amplitude after a release: a damped spring from `a0` to 0. */
    fun released(a0: Double, e: Double): Double {
        val omega = 2 * PI / RELEASE_RESPONSE
        val zeta = RELEASE_DAMPING
        val wd = omega * sqrt(1 - zeta * zeta)
        return a0 * exp(-zeta * omega * e) * (cos(wd * e) + zeta * omega / wd * sin(wd * e))
    }
}

/** How far a glyph leaves its place at full amplitude: y, x and rotation factors. */
internal data class PriceGlyphScatterSliderGlyphFactor(val y: Double, val x: Double, val rotation: Double) {
    companion object {
        /** Counted from the right: the last digit, the tens, the hundreds. */
        val digits = listOf(
            PriceGlyphScatterSliderGlyphFactor(-1.0, 0.6, 1.0),
            PriceGlyphScatterSliderGlyphFactor(0.7, -0.4, -0.8),
            PriceGlyphScatterSliderGlyphFactor(-0.55, 0.5, 0.6),
        )
        val dollar = PriceGlyphScatterSliderGlyphFactor(0.35, -0.2, -0.4)
    }
}

/** Everything one frame draws: the thumb's place (0…1), the scatter amplitude (−1…1) and the thumb's growth (0…1). */
internal data class PriceGlyphScatterSliderFrame(val p: Double, val a: Double, val grow: Double) {
    companion object {
        /** The still screen: the thumb at `home`, the glyphs in place. */
        fun rest(home: Double) = PriceGlyphScatterSliderFrame(home, 0.0, 0.0)
    }
}

internal object PriceGlyphScatterSliderScene {
    private val S = PriceGlyphScatterSliderMetrics
    private val M = PriceGlyphScatterSliderMotion

    /** One eased drag from `from` to `to` over `start…end`: the place and the amplitude at `t`. */
    fun drag(t: Double, start: Double, end: Double, from: Double, to: Double): Pair<Double, Double> {
        val u = M.seg(t, start, end)
        val p = from + (to - from) * M.easeInOutSine(u)
        val speed = (to - from) * M.easeInOutSineSlope(u) / (end - start) * S.TRACK_WIDTH
        return p to max(-1.0, min(1.0, speed / S.FULL_SPEED))
    }

    /** The self-playing loop at `t`, leaving from and coming back to `home` (the configured value's place). */
    fun loopFrame(t: Double, home: Double): PriceGlyphScatterSliderFrame {
        val path = M.path(home)
        var p = home
        var a = 0.0
        M.segments.forEachIndexed { i, (start, end) ->
            val from = path[i]
            val to = path[i + 1]
            if (t >= start && t < end) {
                val d = drag(t, start, end, from, to)
                p = d.first; a = d.second
            } else if (t >= end) {
                p = to
                val peak = abs(to - from) * PI / 2 / (end - start) * S.TRACK_WIDTH / S.FULL_SPEED
                a += M.wobble(t - end, if (to > from) 1.0 else -1.0, peak)
            }
        }
        return PriceGlyphScatterSliderFrame(p, a, 0.0)
    }

    fun place(value: Int) = M.clamp((value - S.MIN_VALUE).toDouble() / (S.MAX_VALUE - S.MIN_VALUE))
    fun amount(p: Double): Int {
        val raw = S.MIN_VALUE + (S.MAX_VALUE - S.MIN_VALUE) * M.clamp(p)
        return (raw / S.STEP).roundToInt() * S.STEP
    }
}

private sealed interface PriceGlyphScatterSliderMode {
    /** Resting at the rest place, or playing the loop from it. */
    data object Loop : PriceGlyphScatterSliderMode
    data object Touching : PriceGlyphScatterSliderMode
    /** The thumb holds at `p`; the scatter springs down from `a0` and the thumb shrinks from `grow0`, since `at`. */
    data class Released(val p: Double, val a0: Double, val grow0: Double, val at: Long) : PriceGlyphScatterSliderMode
    /** The thumb glides from `from` back to the rest place, since `at`. */
    data class Returning(val from: Double, val at: Long) : PriceGlyphScatterSliderMode
}

/** A budget slider whose price readout breaks into glyphs that scatter with the thumb's speed and settle when it stops. */
@Composable
fun PriceGlyphScatterSlider(
    value: Int = 180,
    autoPlay: Boolean = false,
    eyebrow: String = "Weekender",
    title: String = "Daily budget",
    subtitle: String = "How much would you like to spend a day in Lisbon? Food, transport and tickets.",
    cardLabel: String = "Per day",
    nights: Int = PriceGlyphScatterSliderMetrics.NIGHTS,
    hint: String = "Drag the dot — the price settles when you let go.",
    buttonLabel: String = "Set budget",
    onChange: ((Int) -> Unit)? = null,
    onSubmit: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val S = PriceGlyphScatterSliderMetrics
    val M = PriceGlyphScatterSliderMotion
    val P = PriceGlyphScatterSliderPalette
    // Live: follows the Remove animations setting while the block is on screen.
    val reduceMotion = rememberLiveReducedMotion()
    val reduced by rememberUpdatedState(reduceMotion)
    val playing by rememberUpdatedState(autoPlay)
    val changed by rememberUpdatedState(onChange)
    val view = LocalView.current
    val density = LocalDensity.current.density
    BlockSystemBars(darkIcons = true)

    var mode by remember { mutableStateOf<PriceGlyphScatterSliderMode>(PriceGlyphScatterSliderMode.Loop) }
    var origin by remember { mutableLongStateOf(System.nanoTime()) }
    /** Where the thumb rests: the configured value, or the value the user chose when the block does not play by itself. */
    var restP by remember { mutableStateOf(PriceGlyphScatterSliderScene.place(value)) }
    var touchP by remember { mutableStateOf(restP) }
    var lastP by remember { mutableStateOf(restP) }
    var lastNanos by remember { mutableLongStateOf(0L) }
    var smoothedSpeed by remember { mutableStateOf(0.0) }
    var touchStart by remember { mutableLongStateOf(0L) }
    var lastTick by remember { mutableIntStateOf(PriceGlyphScatterSliderScene.amount(restP) / 50) }
    var touches by remember { mutableIntStateOf(0) }
    /** A finger on the button: the block holds where it is until it lifts. */
    var pressing by remember { mutableStateOf(false) }
    // Read only inside graphicsLayer blocks and the derived amount: the screen must not recompose per frame.
    var frame by remember { mutableStateOf(PriceGlyphScatterSliderFrame.rest(restP)) }
    // The price in $5 steps; it notifies its few readers only when it changes.
    val amountState = remember { derivedStateOf { PriceGlyphScatterSliderScene.amount(frame.p) } }
    // Whether anything moves: a touch and its settle always, the loop only while it plays.
    val animating by remember { derivedStateOf { mode !is PriceGlyphScatterSliderMode.Loop || (playing && !reduced) } }

    // A controlled parent moves the rest explicitly; a touch in progress keeps going.
    LaunchedEffect(value) { restP = PriceGlyphScatterSliderScene.place(value) }

    fun frameAt(now: Long): PriceGlyphScatterSliderFrame = when (val m = mode) {
        PriceGlyphScatterSliderMode.Loop -> {
            if (!playing || reduced) PriceGlyphScatterSliderFrame.rest(restP)
            else PriceGlyphScatterSliderScene.loopFrame(((now - origin) / 1e9).coerceAtLeast(0.0) % M.LOOP, restP)
        }
        PriceGlyphScatterSliderMode.Touching -> {
            val grow = if (reduced) 1.0 else M.easeOutCubic(M.seg((now - touchStart) / 1e9, 0.0, M.GROW))
            val a = if (reduced) 0.0 else max(-1.0, min(1.0, smoothedSpeed / S.FULL_SPEED))
            PriceGlyphScatterSliderFrame(touchP, a, grow)
        }
        is PriceGlyphScatterSliderMode.Released -> {
            val e = (now - m.at) / 1e9
            PriceGlyphScatterSliderFrame(m.p, if (reduced) 0.0 else M.released(m.a0, e), if (reduced) 0.0 else m.grow0 * (1 - M.seg(e, 0.0, M.SHRINK)))
        }
        is PriceGlyphScatterSliderMode.Returning -> {
            val d = PriceGlyphScatterSliderScene.drag((now - m.at) / 1e9, 0.0, M.RETURN_FOR, m.from, restP)
            PriceGlyphScatterSliderFrame(d.first, if (reduced) 0.0 else d.second, 0.0)
        }
    }
    val latestFrameAt by rememberUpdatedState(::frameAt)

    // Per frame while something moves: the finger's speed, smoothed, drives the scatter and the value ticks by $50.
    // At rest (no autoPlay, or reduced motion) the frame clock stops and the still frame follows the rest place.
    LaunchedEffect(animating) {
        if (!animating) {
            lastNanos = 0L
            snapshotFlow { restP }.collect { frame = PriceGlyphScatterSliderFrame.rest(it) }
            return@LaunchedEffect
        }
        while (true) {
            val now = withFrameNanos { it }
            if (mode == PriceGlyphScatterSliderMode.Touching) {
                val dt = if (lastNanos == 0L) 1.0 / 60 else ((now - lastNanos) / 1e9).coerceIn(0.001, 0.064)
                lastNanos = now
                val speed = (touchP - lastP) * S.TRACK_WIDTH / dt
                lastP = touchP
                smoothedSpeed += (speed - smoothedSpeed) * (1 - exp(-dt / M.SMOOTHING))
                val tick = PriceGlyphScatterSliderScene.amount(touchP) / 50
                if (tick != lastTick) {
                    lastTick = tick
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                }
            } else {
                lastNanos = 0L
            }
            frame = latestFrameAt(now)
        }
    }

    fun release() {
        if (mode != PriceGlyphScatterSliderMode.Touching) return
        val a0 = if (reduced) 0.0 else max(-1.0, min(1.0, smoothedSpeed / S.FULL_SPEED))
        mode = PriceGlyphScatterSliderMode.Released(touchP, a0, 1.0, System.nanoTime())
        touches++
    }

    /**
     * Any touch outside the slider (the button) holds the block where it is, so it rests and resumes like a drag:
     * the playing loop, or a glide home that a resume started. The frame is captured as it is, so nothing jumps.
     */
    fun pauseLoop() {
        when (mode) {
            is PriceGlyphScatterSliderMode.Loop -> if (!playing || reduced) return
            is PriceGlyphScatterSliderMode.Returning -> Unit
            else -> return
        }
        val now = System.nanoTime()
        val current = latestFrameAt(now)
        mode = PriceGlyphScatterSliderMode.Released(current.p, current.a, current.grow, now)
    }

    // 2 s after the last touch: a playing block glides home and its loop starts again from rest; a still one keeps the chosen value.
    // A finger on the button keeps the hold; the release counts as the last touch. An effect another touch superseded
    // does nothing, even when its delay ends before recomposition has cancelled it.
    LaunchedEffect(touches) {
        if (touches == 0) { origin = System.nanoTime(); return@LaunchedEffect }
        val generation = touches
        delay((M.RESUME * 1000).toLong())
        if (touches != generation || pressing) return@LaunchedEffect
        val m = mode as? PriceGlyphScatterSliderMode.Released ?: return@LaunchedEffect
        if (playing) {
            if (abs(m.p - restP) > 0.001) {
                mode = PriceGlyphScatterSliderMode.Returning(m.p, System.nanoTime())
                delay(((M.RETURN_FOR + 0.05) * 1000).toLong())
                if (touches != generation || mode !is PriceGlyphScatterSliderMode.Returning) return@LaunchedEffect
            }
        } else {
            restP = m.p
        }
        origin = System.nanoTime()
        mode = PriceGlyphScatterSliderMode.Loop
    }

    BoxWithConstraints(modifier.fillMaxSize().background(P.page)) {
        val width = maxWidth.value.toDouble()
        val height = maxHeight.value.toDouble()
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        val top = insets.calculateTopPadding().value.toDouble()
        val bottom = max(insets.calculateBottomPadding().value.toDouble(), 16.0)
        val cardWidth = width - S.MARGIN * 2

        // Header.
        Box(Modifier.offset(S.MARGIN.dp, (top + 12).dp).size(36.dp).background(P.card, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.ChevronLeft, contentDescription = null, tint = P.ink, modifier = Modifier.size(20.dp))
        }
        BasicText(eyebrow.uppercase(), style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp, color = P.purple), modifier = Modifier.offset((S.MARGIN + 4).dp, (top + 72).dp))
        BasicText(title, style = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp, color = P.ink), maxLines = 1, modifier = Modifier.offset((S.MARGIN + 4).dp, (top + 92).dp))
        BasicText(subtitle, style = TextStyle(fontSize = 15.sp, lineHeight = 21.sp, color = P.body), maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.offset((S.MARGIN + 4).dp, (top + 134).dp).width((cardWidth - 8).dp))

        // The card. Its accessibility value reads the amount inside, so the card's children do not recompose with it.
        BudgetCard(
            amount = amountState,
            title = title,
            onSetProgress = { target ->
                val next = PriceGlyphScatterSliderScene.place(target.roundToInt())
                mode = PriceGlyphScatterSliderMode.Released(next, 0.0, 0.0, System.nanoTime())
                changed?.invoke(PriceGlyphScatterSliderScene.amount(next))
                touches++
            },
            modifier = Modifier.offset(S.MARGIN.dp, (top + S.CARD_TOP).dp).width(cardWidth.dp).height(S.CARD_HEIGHT.dp)
                .cssBoxShadow(P.shadow.copy(alpha = 0.12f), offsetY = 10.dp, blur = 30.dp, radius = S.CARD_RADIUS.dp)
                .cssBoxShadow(P.shadow.copy(alpha = 0.06f), offsetY = 1.dp, blur = 2.dp, radius = S.CARD_RADIUS.dp)
                .background(P.card, RoundedCornerShape(S.CARD_RADIUS.dp)),
        ) {
            BasicText(cardLabel.uppercase(), style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, color = P.mute), modifier = Modifier.offset(S.INSET.dp, S.INSET.dp))
            // The slider row: the track, the thumb and the touch target.
            Box(
                Modifier.offset(0.dp, S.ROW_TOP.dp).width((S.INSET + S.TRACK_WIDTH + S.PRICE_GAP + S.PRICE_WIDTH).dp).height(S.ROW_HEIGHT.dp)
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            // Any touch on the row takes over, a tap included; it keeps its grip on the thumb or the thumb jumps under it.
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val current = latestFrameAt(System.nanoTime()).p
                            val at = (down.position.x / density - S.INSET) / S.TRACK_WIDTH
                            val grip = if (abs(at - current) * S.TRACK_WIDTH <= S.THUMB) at - current else 0.0
                            touchP = current; lastP = current; smoothedSpeed = 0.0; lastNanos = 0L
                            touchStart = System.nanoTime()
                            lastTick = PriceGlyphScatterSliderScene.amount(current) / 50
                            mode = PriceGlyphScatterSliderMode.Touching
                            touches++
                            down.consume()
                            try {
                                // Returns on the finger lifting or on the system taking the pointer; either way the thumb releases.
                                drag(down.id) { change ->
                                    val next = M.clamp((change.position.x / density - S.INSET) / S.TRACK_WIDTH - grip)
                                    if (PriceGlyphScatterSliderScene.amount(next) != PriceGlyphScatterSliderScene.amount(touchP)) changed?.invoke(PriceGlyphScatterSliderScene.amount(next))
                                    touchP = next
                                    change.consume()
                                }
                            } finally {
                                release()
                            }
                        }
                    },
            ) {
                val cy = S.ROW_HEIGHT / 2
                Box(Modifier.offset(S.INSET.dp, (cy - S.TRACK_HEIGHT / 2).dp).width(S.TRACK_WIDTH.dp).height(S.TRACK_HEIGHT.dp).background(P.trackRest, CircleShape))
                Box(
                    Modifier.offset(S.INSET.dp, (cy - S.TRACK_HEIGHT / 2).dp).width(S.TRACK_WIDTH.dp).height(S.TRACK_HEIGHT.dp)
                        .graphicsLayer { transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f); scaleX = max(S.TRACK_HEIGHT / S.TRACK_WIDTH, frame.p).toFloat() }
                        .background(P.purple, CircleShape),
                )
                Box(
                    Modifier.offset(0.dp, 0.dp).size(S.THUMB_GROWN.dp)
                        .graphicsLayer {
                            val size = S.THUMB + (S.THUMB_GROWN - S.THUMB) * frame.grow
                            val k = (size / S.THUMB_GROWN).toFloat()
                            scaleX = k; scaleY = k
                            translationX = ((S.INSET + frame.p * S.TRACK_WIDTH - S.THUMB_GROWN / 2) * density).toFloat()
                            translationY = ((cy - S.THUMB_GROWN / 2) * density).toFloat()
                        }
                        .cssBoxShadow(P.purple.copy(alpha = 0.35f), offsetY = 3.dp, blur = 8.dp, radius = (S.THUMB_GROWN / 2).dp)
                        .background(P.card, CircleShape)
                        .padding(S.RING.dp)
                        .background(P.purple, CircleShape),
                )
                Readout(amountState, { frame.a }, Modifier.offset((S.INSET + S.TRACK_WIDTH + S.PRICE_GAP).dp, 0.dp).width(S.PRICE_WIDTH.dp).height(S.ROW_HEIGHT.dp))
            }
            BasicText("$${S.MIN_VALUE}", style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = P.mute), modifier = Modifier.offset(S.INSET.dp, 108.dp))
            BasicText("$${S.MAX_VALUE}", style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = P.mute, textAlign = TextAlign.End), modifier = Modifier.offset(S.INSET.dp, 108.dp).width(S.TRACK_WIDTH.dp))
            TotalLine(amountState, nights, Modifier.offset(S.INSET.dp, 128.dp))
        }

        BasicText(hint, style = TextStyle(fontSize = 13.sp, color = P.mute, textAlign = TextAlign.Center), maxLines = 2,
            modifier = Modifier.offset(S.MARGIN.dp, (top + S.CARD_TOP + S.CARD_HEIGHT + 18).dp).width(cardWidth.dp))

        // Where a day's money goes, following the slider.
        val tileWidth = (cardWidth - S.TILE_GAP * 2) / 3
        Row(Modifier.offset(S.MARGIN.dp, (top + S.CARD_TOP + S.CARD_HEIGHT + S.TILE_TOP).dp).width(cardWidth.dp), horizontalArrangement = Arrangement.spacedBy(S.TILE_GAP.dp)) {
            val tiles = listOf(Triple("Food", Icons.Filled.Restaurant, 0.4), Triple("Transport", Icons.Filled.Tram, 0.2), Triple("Tickets", Icons.Filled.ConfirmationNumber, 0.4))
            for ((label, icon, share) in tiles) {
                Column(
                    Modifier.width(tileWidth.dp).height(S.TILE_HEIGHT.dp)
                        .cssBoxShadow(P.shadow.copy(alpha = 0.08f), offsetY = 4.dp, blur = 16.dp, radius = 16.dp)
                        .background(P.card, RoundedCornerShape(16.dp)).padding(14.dp),
                ) {
                    Box(Modifier.size(28.dp).background(P.iconDisc, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, tint = P.purple, modifier = Modifier.size(14.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    BasicText(label, style = TextStyle(fontSize = 12.sp, color = P.mute), maxLines = 1)
                    TileAmount(amountState, share, Modifier.padding(top = 2.dp))
                }
            }
        }

        // The button. The press holds the block from the touch down to the lift or cancel, which arms the 2 s resume;
        // only a completed click submits. An activation without a press (TalkBack) pauses and arms from the click itself.
        val interaction = remember { MutableInteractionSource() }
        LaunchedEffect(interaction) {
            interaction.interactions.collect { event ->
                when (event) {
                    is PressInteraction.Press -> { pressing = true; pauseLoop() }
                    is PressInteraction.Release, is PressInteraction.Cancel -> { pressing = false; touches++ }
                }
            }
        }
        Box(
            Modifier.offset(S.MARGIN.dp, (height - bottom - 20 - S.BUTTON_HEIGHT).dp).width(cardWidth.dp).height(S.BUTTON_HEIGHT.dp)
                .background(P.purple, RoundedCornerShape(18.dp))
                .clickable(interactionSource = interaction, indication = null) {
                    pauseLoop()
                    touches++
                    onSubmit?.invoke(PriceGlyphScatterSliderScene.amount(frame.p))
                },
            contentAlignment = Alignment.Center,
        ) {
            BasicText(buttonLabel, style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White))
        }
    }
}

/** The card's shell: it alone reads the amount for accessibility, so its children stay as they are while the price changes. */
@Composable
private fun BudgetCard(amount: State<Int>, title: String, onSetProgress: (Float) -> Unit, modifier: Modifier, content: @Composable () -> Unit) {
    val S = PriceGlyphScatterSliderMetrics
    val value by amount
    Box(
        modifier.semantics {
            contentDescription = "$title, $$value per day"
            progressBarRangeInfo = ProgressBarRangeInfo(value.toFloat(), S.MIN_VALUE.toFloat()..S.MAX_VALUE.toFloat())
            setProgress { target -> onSetProgress(target); true }
        },
    ) { content() }
}

/** "≈ $540 for 3 nights", following the amount on its own. */
@Composable
private fun TotalLine(amount: State<Int>, nights: Int, modifier: Modifier) {
    val P = PriceGlyphScatterSliderPalette
    val value by amount
    BasicText(
        buildAnnotatedString {
            withStyle(SpanStyle(color = P.body)) { append("≈ ") }
            withStyle(SpanStyle(color = P.ink, fontWeight = FontWeight.SemiBold)) { append("$" + String.format(Locale.US, "%,d", value * nights)) }
            withStyle(SpanStyle(color = P.body)) { append(" for $nights nights") }
        },
        style = TextStyle(fontSize = 13.sp), modifier = modifier,
    )
}

/** One tile's share of the day, following the amount on its own. */
@Composable
private fun TileAmount(amount: State<Int>, share: Double, modifier: Modifier) {
    val P = PriceGlyphScatterSliderPalette
    val value by amount
    BasicText("$" + (value * share).roundToInt(), style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = P.ink, fontFeatureSettings = "tnum"), maxLines = 1, modifier = modifier)
}

/** The price as separate glyphs, laid out right to left; each leaves its place by the amplitude. */
@Composable
private fun Readout(amount: State<Int>, amplitude: () -> Double, modifier: Modifier) {
    val S = PriceGlyphScatterSliderMetrics
    val P = PriceGlyphScatterSliderPalette
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val digitStyle = TextStyle(fontSize = S.PRICE_SIZE.sp, fontWeight = FontWeight.SemiBold, color = P.ink, fontFeatureSettings = "tnum", textAlign = TextAlign.Center)
    val dollarStyle = TextStyle(fontSize = S.DOLLAR_SIZE.sp, fontWeight = FontWeight.SemiBold, color = P.mute, textAlign = TextAlign.Center)
    val digitWidth = remember(density) { with(density) { measurer.measure(AnnotatedString("0"), digitStyle).size.width.toDp() } }
    val dollarWidth = remember(density) { with(density) { measurer.measure(AnnotatedString("$"), dollarStyle).size.width.toDp() } }
    val digits = amount.value.toString()
    Row(modifier, horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
        Glyph("$", dollarStyle, dollarWidth, PriceGlyphScatterSliderGlyphFactor.dollar, amplitude)
        digits.forEachIndexed { index, ch ->
            val fromRight = digits.length - 1 - index
            val factor = PriceGlyphScatterSliderGlyphFactor.digits[min(fromRight, PriceGlyphScatterSliderGlyphFactor.digits.size - 1)]
            Glyph(ch.toString(), digitStyle, digitWidth, factor, amplitude)
        }
    }
}

@Composable
private fun Glyph(text: String, style: TextStyle, width: androidx.compose.ui.unit.Dp, factor: PriceGlyphScatterSliderGlyphFactor, amplitude: () -> Double) {
    val S = PriceGlyphScatterSliderMetrics
    Box(
        Modifier.width(width).height(S.ROW_HEIGHT.dp).graphicsLayer {
            val a = amplitude()
            val m = abs(a)
            rotationZ = (S.SCATTER_ROTATION * a * factor.rotation).toFloat()
            translationX = (-S.SCATTER_X * a * factor.x * density).toFloat()
            translationY = (S.SCATTER_Y * m * factor.y * density).toFloat()
            alpha = (1 - 0.15 * m).toFloat()
        },
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text, style = style, maxLines = 1)
    }
}
