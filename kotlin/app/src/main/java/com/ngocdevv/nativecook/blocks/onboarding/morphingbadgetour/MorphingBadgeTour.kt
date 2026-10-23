package com.ngocdevv.nativecook.blocks.onboarding.morphingbadgetour

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.surface
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

private val Night = Color(0xFF0E0D10)
private val Mute = Color(0xFF99999C)
private val Link = Color(0xFF3773F5)

/** The stage's centre, as a fraction of the screen's height. */
private const val STAGE_AT = 0.387f

// The lower third, as centres above the bottom safe area.
private const val SECONDARY_UP = 9f
private const val PRIMARY_UP = 83f
private const val LEGAL_UP = 159f
private const val CAPTION_UP = 251f

/** The artwork is cut off this far above the caption's first line. */
private const val STAGE_GAP = 26f
private const val BUTTON = 56f
private const val BUTTON_MARGIN = 26.5f

private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun tourText(size: Float, lineHeight: Float, color: Color, weight: FontWeight = FontWeight.Normal, spacing: Float = 0f) = TextStyle(
    color = color,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    letterSpacing = spacing.sp,
    textAlign = TextAlign.Center,
    lineHeightStyle = Centered,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private val CaptionStyle = tourText(22f, 30f, Color.White, spacing = -0.4f)
private val LegalStyle = tourText(11f, 16f, Mute)
private val PrimaryStyle = tourText(14f, 18f, Night, FontWeight.SemiBold)
private val SecondaryStyle = tourText(14f, 18f, Color.White, FontWeight.SemiBold)

/**
 * The pieces on their way from where they were ([from], PIECES × FIELDS numbers) to the scene
 * [to]; with [away], everything leaves the stage first. The caption of [leaving] goes. [late] is
 * how long ago the run was due, so that a timer that fires late does not shift it.
 */
private class Run(val from: FloatArray, val to: Int, val away: Boolean, val leaving: Int, val late: Float = 0f)

/**
 * A wallet's intro: a fixed lower third (caption, small print, two buttons) under a stage where
 * one cast of gradient shapes keeps re-forming. A hero badge on a capsule with badges floating
 * round it becomes a grid, the grid a constellation, the constellation a striped wallet a diamond
 * drops into. Nothing cuts: each shape travels and turns into what it is in the next scene.
 * Tap the stage or swipe left for the next scene, swipe right for the one before.
 *
 * @param captions One caption a scene: the welcome, the grid, the constellation and the wallet.
 * @param legal The small print, in turns plain and highlighted.
 * @param autoPlay Goes through the four scenes by itself, every [LOOP] seconds.
 * @param onSceneChange Called with the scene's index when the artwork starts to turn into it.
 */
@Composable
fun MorphingBadgeTour(
    captions: List<String> = listOf("Welcome to\nPrism Wallet", "Your home for\neverything onchain", "Trade 2+ million coins", "Or just save\n(that's cool too)"),
    legal: List<String> = listOf("By using Prism Wallet, you agree to the ", "terms", " and\n", "privacy policy"),
    primaryLabel: String = "Create new wallet",
    secondaryLabel: String = "I already have a wallet",
    autoPlay: Boolean = false,
    onSceneChange: ((index: Int) -> Unit)? = null,
    onPrimary: (() -> Unit)? = null,
    onSecondary: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    // The gestures and the loop outlive a recomposition: what they read is state.
    val calm by rememberUpdatedState(rememberReducedMotion())
    val changed by rememberUpdatedState(onSceneChange)

    // The block opens on the first scene at rest.
    var run by remember { mutableStateOf(Run(FloatArray(PIECES * FIELDS) { Scenes[0][it / FIELDS][it % FIELDS] }, to = 0, away = false, leaving = 0)) }
    // Seconds into the run. Read while drawing only.
    val r = remember { mutableFloatStateOf(MOVE) }
    // The run's clock is going.
    var active by remember { mutableStateOf(false) }
    // Seconds since the block appeared, for the bobbing, the breathing and the sparkles.
    val clock = remember { mutableFloatStateOf(0f) }
    // Changes on every touch, which restarts the autoPlay loop behind a pause.
    var touches by remember { mutableIntStateOf(0) }

    fun tap() = view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)

    /** Starts the change to scene [to] from wherever the pieces are. */
    fun goTo(to: Int, hand: Boolean, late: Float = 0f) {
        val now = run
        val seconds = r.floatValue
        val from = FloatArray(PIECES * FIELDS) { pieceValue(now.from, now.to, now.away, seconds, it / FIELDS, it % FIELDS) }
        // After the last scene everything leaves the stage, and the first one comes in from outside.
        run = Run(from, to, away = now.to == COUNT - 1 && to == 0, leaving = now.to, late = late)
        r.floatValue = 0f
        active = true
        if (to != now.to) {
            if (hand) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            changed?.invoke(to)
        }
    }

    fun step(by: Int) = goTo((run.to + by + COUNT) % COUNT, hand = true)

    LaunchedEffect(run) {
        if (!active) return@LaunchedEffect
        val now = run
        val length = runLength(now.away)
        val origin = withFrameNanos { it }
        while (true) {
            val passed = (withFrameNanos { it } - origin) / 1e9f + now.late
            if (passed >= length) break
            r.floatValue = passed
        }
        r.floatValue = length
        active = false
    }

    LaunchedEffect(calm) {
        if (calm) return@LaunchedEffect
        val origin = withFrameNanos { it }
        while (true) clock.floatValue = (withFrameNanos { it } - origin) / 1e9f
    }

    LaunchedEffect(autoPlay, touches) {
        if (!autoPlay) return@LaunchedEffect
        var origin = System.nanoTime()
        if (touches > 0) {
            // Three seconds of rest, then back to the first scene if need be, and the loop starts
            // over once it has formed.
            var quiet = 0f
            while (quiet < USER_HOLD) {
                delay(100)
                quiet = if (active) 0f else quiet + 0.1f
            }
            origin = System.nanoTime()
            if (run.to != 0) {
                origin += (runLength(run.to == COUNT - 1) * 1e9f).toLong()
                goTo(0, hand = false)
            }
        }

        /** Waits for the moment [seconds] into the loop, and says how late it woke up. */
        suspend fun until(seconds: Float): Float {
            val due = origin + (seconds * 1e9f).toLong()
            delay(max(0L, (due - System.nanoTime()) / 1_000_000))
            return (System.nanoTime() - due) / 1e9f
        }
        while (true) {
            for (at in NEXT_AT) {
                val late = until(at)
                goTo((run.to + 1) % COUNT, hand = false, late = late)
            }
            origin += (LOOP * 1e9f).toLong()
        }
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Night)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    touches++
                }
            },
    ) {
        val bottom = max(WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding().value, 12f)
        val width = maxWidth.value
        val height = maxHeight.value
        val cx = width / 2f
        val cy = (height * STAGE_AT).roundToInt().toFloat()
        val captionY = height - bottom - CAPTION_UP
        val stageBottom = captionY - STAGE_GAP
        val path = remember { Path() }

        Canvas(Modifier.matchParentSize().clearAndSetSemantics {}) {
            surface(0.dp) { _, _, w, _ ->
                clipRect(0f, 0f, w, stageBottom) {
                    val now = run
                    val seconds = r.floatValue
                    if (calm) {
                        // Nothing travels: the scene the pieces come from fades into the one they go to.
                        val u = ease(seconds / BLEND)
                        paintStage(path, { k, i -> now.from[k * FIELDS + i] }, 1f - u, null, cx, cy)
                        paintStage(path, { k, i -> Scenes[now.to][k][i] }, u, null, cx, cy)
                    } else {
                        paintStage(path, { k, i -> pieceValue(now.from, now.to, now.away, seconds, k, i) }, 1f, clock.floatValue, cx, cy)
                    }
                }
            }
        }

        // The stage takes the taps and the swipes; the artwork is drawn under it.
        Box(
            Modifier
                .fillMaxWidth()
                .height(stageBottom.dp)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val up = waitForUpOrCancellation() ?: return@awaitEachGesture
                        val across = (up.position.x - down.position.x) / density
                        val along = (up.position.y - down.position.y) / density
                        if (abs(across) > SWIPE) step(if (across < 0f) 1 else -1) else if (abs(across) < 12f && abs(along) < 12f) step(1)
                    }
                }
                .semantics {
                    role = Role.Button
                    contentDescription = captions[run.to.coerceAtMost(captions.size - 1)].replace('\n', ' ')
                    onClick("Shows the next scene") {
                        step(1)
                        true
                    }
                },
        )

        captions.take(COUNT).forEachIndexed { index, text ->
            if (index == run.to || index == run.leaving) {
                BasicText(
                    text,
                    Modifier
                        .fillMaxWidth()
                        .offset(y = (captionY - 15f).dp)
                        .graphicsLayer {
                            val shift = if (calm) 0f else CAPTION_SHIFT.dp.toPx()
                            if (run.to == run.leaving) {
                                alpha = 1f
                                translationY = 0f
                            } else if (index == run.leaving) {
                                alpha = 1f - captionGone(r.floatValue)
                                translationY = shift * captionGone(r.floatValue)
                            } else {
                                alpha = captionCome(r.floatValue)
                                translationY = shift * (1f - captionCome(r.floatValue))
                            }
                        }
                        .then(if (index == run.to) Modifier else Modifier.clearAndSetSemantics {}),
                    style = CaptionStyle,
                )
            }
        }

        BasicText(
            buildAnnotatedString {
                legal.forEachIndexed { index, part -> if (index % 2 == 1) withStyle(SpanStyle(color = Link)) { append(part) } else append(part) }
            },
            Modifier.fillMaxWidth().offset(y = (height - bottom - LEGAL_UP - 8f).dp),
            style = LegalStyle,
        )

        val primary = remember { MutableInteractionSource() }
        val primaryPressed by primary.collectIsPressedAsState()
        val primaryDim by animateFloatAsState(if (primaryPressed) 0.8f else 1f, tween(120), label = "primary")
        Box(
            Modifier
                .offset(y = (height - bottom - PRIMARY_UP - BUTTON / 2f).dp)
                .padding(horizontal = BUTTON_MARGIN.dp)
                .fillMaxWidth()
                .height(BUTTON.dp)
                .graphicsLayer { alpha = primaryDim }
                .background(Color.White, CircleShape)
                .clickable(primary, indication = null, role = Role.Button) {
                    tap()
                    onPrimary?.invoke()
                },
            contentAlignment = Alignment.Center,
        ) {
            BasicText(primaryLabel, style = PrimaryStyle, maxLines = 1)
        }

        val secondary = remember { MutableInteractionSource() }
        val secondaryPressed by secondary.collectIsPressedAsState()
        val secondaryDim by animateFloatAsState(if (secondaryPressed) 0.6f else 1f, tween(120), label = "secondary")
        Box(
            Modifier
                .offset(y = (height - bottom - SECONDARY_UP - 22f).dp)
                .padding(horizontal = BUTTON_MARGIN.dp)
                .fillMaxWidth()
                .height(44.dp)
                .graphicsLayer { alpha = secondaryDim }
                .clickable(secondary, indication = null, role = Role.Button) {
                    tap()
                    onSecondary?.invoke()
                },
            contentAlignment = Alignment.Center,
        ) {
            BasicText(secondaryLabel, style = SecondaryStyle, maxLines = 1)
        }
    }
}
