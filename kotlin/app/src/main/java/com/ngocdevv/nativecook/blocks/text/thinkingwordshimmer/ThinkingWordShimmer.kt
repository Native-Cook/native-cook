package com.ngocdevv.nativecook.blocks.text.thinkingwordshimmer

import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.LongState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

// An assistant at work: the request on top, and in a black card one status word at a time
// ("Thinking...", "Processing...", "Analyzing...", "Computing...", "Almost..."). Each word rises
// in sharpening from a blur, a bright band sweeps through its letters while its three dots pulse
// one after the other, then it lifts away blurring as the next word rises in under it. A port of
// the SwiftUI block, number for number.
//
// Every word gets a 2 s slot; times below are from the start of the slot, when the word has
// just landed:
//   0.00-0.15   at rest, dots pulsing (each dot 1 s around, 0.9 rad behind the one before)
//   0.15-1.15   a white band 0.6 of the word wide crosses it, left to right (ease-in-out);
//               outside the band the word is at 50% white
//   1.45-1.80   the word lifts 14 dp, blurs to 6 dp and fades (ease-in)
//   1.60-2.00   the next word rises 14 dp into place, sharpening and fading in (ease-out)
// autoPlay loop: the five words, 10 s, back on "Thinking..." exactly where it started. The
// ambient glows drift on the same 10 s period.

/** Seconds. */
private const val SLOT = 2f
private const val SWEEP_AT = 0.15f
private const val SWEEP_FOR = 1f
/** From the start of a slot to the start of the change to the next word. */
private const val SWITCH_AT = 1.45f
private const val EXIT_FOR = 0.35f
private const val ENTER_DELAY = 0.15f
private const val ENTER_FOR = 0.4f
/** From the start of a change to the moment the new word has landed (its slot's start). */
private const val LANDED = ENTER_DELAY + ENTER_FOR
private const val TRAVEL = 14f
private const val BLUR = 6f
/** The word's brightness outside the band, which is white; the band is 0.6 of the word wide. */
private const val BASE = 0.5f
private const val DOT_PERIOD = 1f
private const val DOT_LAG = 0.9f
private const val USER_HOLD = 2f
private const val TEXT_SIZE = 24f

private val BACKGROUND = Color(0xFF04080A)
private val TEAL = Color(0xFF12505F)
private val DEEP_TEAL = Color(0xFF0B3A47)
private val WARM = Color(0xFF8C7A4E)
private val TEXT = Color(0xFFE6ECEE)
private val LABEL = Color(0xFF7D8A90)
private val STEP = Color(0xFF263034)

private fun clamp01(x: Float) = x.coerceIn(0f, 1f)
private fun easeInOut(t: Float) = t * t * (3 - 2 * t)
private fun easeIn(t: Float) = t * t
private fun easeOut(t: Float) = 1 - (1 - t) * (1 - t)

/** Where the bright band's centre is, in units of the word's width, [s] seconds into its slot. */
private fun band(s: Float): Float {
    val t = s % SLOT
    return -0.35f + 1.7f * easeInOut(clamp01((t - SWEEP_AT) / SWEEP_FOR))
}

/** A dot's opacity, [s] seconds into the word's slot. */
private fun dot(k: Int, s: Float) = 0.3f + 0.7f * (0.5f + 0.5f * sin(2 * PI.toFloat() * s / DOT_PERIOD - k * DOT_LAG))

/**
 * @param autoPlay Goes through the words by itself, one every 2 s.
 */
@Composable
fun ThinkingWordShimmer(
    modifier: Modifier = Modifier,
    words: List<String> = listOf("Thinking", "Processing", "Analyzing", "Computing", "Almost"),
    label: String = "Assistant",
    request: String = "Summarise this week's customer calls and flag anything urgent.",
    hint: String = "Tap to skip ahead",
    autoPlay: Boolean = false,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val view = LocalView.current
    val reducedMotion = rememberReducedMotion()
    val currentWords by rememberUpdatedState(words)

    val typeface = remember(context) {
        runCatching { Typeface.createFromAsset(context.assets, "thinkingwordshimmer/thinkingwordshimmer_Inter-SemiBold.ttf") }
            .getOrDefault(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD))
    }
    val family = remember(typeface) { FontFamily(androidx.compose.ui.text.font.Typeface(typeface)) }
    val paint = remember(typeface, density) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            textSize = TEXT_SIZE * density.density
        }
    }

    // The frame clock in nanoseconds; read in draw and layer blocks only.
    val clock = remember { mutableLongStateOf(System.nanoTime()) }
    var current by remember { mutableIntStateOf(0) }
    var previous by remember { mutableStateOf<Int?>(null) }
    // When the change to `current` began; `current` landed LANDED later.
    var changedAt by remember { mutableLongStateOf(System.nanoTime() - (LANDED * 1e9f).toLong()) }
    // Where the ambient glows' period starts.
    var origin by remember { mutableLongStateOf(System.nanoTime()) }
    var touches by remember { mutableIntStateOf(0) }
    val loop = SLOT * words.size

    fun change(to: Int) {
        previous = current
        current = to
        changedAt = System.nanoTime()
        clock.longValue = changedAt
    }

    LaunchedEffect(reducedMotion) {
        while (true) withFrameNanos { clock.longValue = System.nanoTime() }
    }

    LaunchedEffect(autoPlay && !reducedMotion, touches) {
        if (!autoPlay || reducedMotion) return@LaunchedEffect
        var landed: Long
        if (touches > 0) {
            delay((USER_HOLD * 1000).toLong())
            // Back to the first word, unless it is already up and not yet leaving.
            val s = (System.nanoTime() - changedAt) / 1e9f - LANDED
            landed = if (current == 0 && s >= 0f && s < SWITCH_AT) {
                System.nanoTime() - (s * 1e9f).toLong()
            } else {
                change(0)
                System.nanoTime() + (LANDED * 1e9f).toLong()
            }
            // The glows' period starts over with the loop.
            origin = landed
        } else {
            landed = System.nanoTime() - (max(0f, (System.nanoTime() - changedAt) / 1e9f - LANDED) * 1e9f).toLong()
        }
        // `landed` is when the word on screen landed; each slot changes word at 1.45 s.
        while (true) {
            delay(max(0L, (landed + (SWITCH_AT * 1e9f).toLong() - System.nanoTime()) / 1_000_000))
            change((current + 1) % currentWords.size)
            landed += (SLOT * 1e9f).toLong()
        }
    }

    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val noRipple = remember { MutableInteractionSource() }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .drawBehind {
                // Near-black with a teal glow up high, a deeper teal low on the left and a warm one
                // low in the middle, each drifting a little once per loop.
                val phase = if (reducedMotion) 0f else (2 * PI * (((clock.longValue - origin) / 1e9) % loop) / loop).toFloat()
                val w = size.width
                val h = size.height
                drawRect(BACKGROUND)
                glow(TEAL, 0.75f, Offset(w * (0.7f + 0.05f * sin(phase)), h * (0.3f + 0.03f * cos(phase))), w * 0.75f)
                glow(DEEP_TEAL, 0.85f, Offset(w * (0.2f + 0.04f * cos(phase)), h * 0.7f), w * 0.65f)
                glow(WARM, 0.6f, Offset(w * (0.56f + 0.06f * sin(phase + 1.3f)), h * (0.68f + 0.02f * sin(phase))), w * 0.6f)
            }
            .clickable(interactionSource = noRipple, indication = null) {
                touches++
                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                change((current + 1) % currentWords.size)
            },
    ) {
        val cardW = maxWidth - 40.dp
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = insets.calculateTopPadding() + 20.dp)
                .padding(horizontal = 20.dp),
        ) {
            Text(label.uppercase(), style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.6.sp, color = LABEL))
            Text(
                request,
                modifier = Modifier.padding(top = 10.dp),
                style = TextStyle(fontSize = 22.sp, fontFamily = family, color = TEXT.copy(alpha = 0.92f), lineHeight = (22 * 1.21f + 4).sp),
            )

            Spacer(Modifier.weight(1f))

            // The card.
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(cardW, (cardW.value * 0.62f).roundToInt().dp)
                    .dropShadow(RoundedCornerShape(28.dp), Shadow(radius = 30.dp, color = Color.Black, offset = DpOffset(0.dp, 18.dp), alpha = 0.6f))
                    .background(Color.Black, RoundedCornerShape(28.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(28.dp))
                    .semantics { contentDescription = currentWords[current] + "…" },
            ) {
                val prev = previous
                if (prev != null) {
                    ThinkingWordShimmerStatus(words[prev], paint, clock, reducedMotion) { now ->
                        val since = (now - changedAt) / 1e9f
                        if (reducedMotion) Pose(0f, 0f, 1 - clamp01(since / 0.25f), 0f)
                        else if (since >= EXIT_FOR) Pose(0f, 0f, 0f, 0f)
                        else {
                            val q = easeIn(clamp01(since / EXIT_FOR))
                            Pose(-TRAVEL * q, BLUR * q, 1 - q, SWITCH_AT + since)
                        }
                    }
                }
                ThinkingWordShimmerStatus(words[current], paint, clock, reducedMotion) { now ->
                    val since = (now - changedAt) / 1e9f
                    if (reducedMotion) Pose(0f, 0f, clamp01(since / 0.25f), 0f)
                    else {
                        val q = easeOut(clamp01((since - ENTER_DELAY) / ENTER_FOR))
                        Pose(TRAVEL * (1 - q), BLUR * (1 - q), q, max(0f, since - LANDED))
                    }
                }
            }

            // One step per word.
            Row(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                words.indices.forEach { i ->
                    val on = i == current
                    val width by animateDpAsState(if (on) 28.dp else 14.dp, if (reducedMotion) snap() else spring(dampingRatio = 0.85f, stiffness = 247f), label = "step")
                    val color by animateColorAsState(if (on) TEXT else STEP, if (reducedMotion) snap() else spring(dampingRatio = 0.85f, stiffness = 247f), label = "step")
                    Box(Modifier.width(width).height(3.dp).background(color, CircleShape))
                }
            }

            Spacer(Modifier.weight(1f))

            Text(
                hint,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = insets.calculateBottomPadding() + 28.dp),
                style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = LABEL.copy(alpha = 0.8f), textAlign = TextAlign.Center),
            )
        }
    }
}

private fun DrawScope.glow(color: Color, alpha: Float, centre: Offset, radius: Float) {
    drawRect(Brush.radialGradient(listOf(color.copy(alpha = alpha), color.copy(alpha = 0f)), center = centre, radius = radius))
}

/** How a status word sits: lift (dp), blur (dp), opacity, and seconds into its slot. */
private class Pose(val dy: Float, val blur: Float, val opacity: Float, val s: Float)

/**
 * A status word and its three dots, centred in the card: the word is lit by the moving band, the
 * dots pulse. Everything that moves is read from [clock] in the layer and draw blocks.
 */
@Composable
private fun ThinkingWordShimmerStatus(word: String, paint: Paint, clock: LongState, reduced: Boolean, pose: (Long) -> Pose) {
    val dotWidth = remember(paint) { paint.measureText(".") }
    val wordWidth = remember(paint, word) { paint.measureText(word) }
    val dotPaint = remember(paint) { Paint(paint) }
    val wordPaint = remember(paint) { Paint(paint) }
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                val p = pose(clock.longValue)
                alpha = p.opacity
                translationY = p.dy * density
                val b = p.blur * density
                renderEffect = if (b > 0.05f && Build.VERSION.SDK_INT >= 31) BlurEffect(sigmaToRadius(b), sigmaToRadius(b), TileMode.Decal) else null
            }
            .drawBehind {
                val p = pose(clock.longValue)
                val total = wordWidth + 3 * dotWidth
                val x0 = (size.width - total) / 2
                val fm = paint.fontMetrics
                val baseline = size.height / 2 - (fm.ascent + fm.descent) / 2
                val c = if (reduced) 2f else band(p.s)
                val base = Color.White.copy(alpha = if (reduced) 0.9f else BASE).toArgb()
                wordPaint.shader = LinearGradient(
                    x0 + (c - 0.5f) * wordWidth, 0f, x0 + (c + 0.5f) * wordWidth, 0f,
                    intArrayOf(base, base, Color.White.toArgb(), base, base),
                    floatArrayOf(0f, 0.2f, 0.5f, 0.8f, 1f),
                    Shader.TileMode.CLAMP,
                )
                val canvas = drawContext.canvas.nativeCanvas
                canvas.drawText(word, x0, baseline, wordPaint)
                for (k in 0 until 3) {
                    dotPaint.color = Color.White.copy(alpha = if (reduced) 0.9f else dot(k, p.s)).toArgb()
                    canvas.drawText(".", x0 + wordWidth + k * dotWidth, baseline, dotPaint)
                }
            },
    )
}
