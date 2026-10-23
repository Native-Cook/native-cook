package com.ngocdevv.nativecook.blocks.onboarding.breathingtidewelcome

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.surface
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.sqrt

private val Paper = Color(0xFFFEFEFE)
private val Dot = Color(0xFFFD6703)
private val Ink = Color(0xFF232323)
private val Words = Color(0xFF313131)
private val Soft = Color(0xFF585858)
private val Blue = Color(0xFF136EFB)
private val Blush = Color(0xFFFDF6F6)

/** The splash's logo, as a fraction of the screen's height. */
private const val LOGO_AT = 0.47f
private const val LOGO_DOT = 58f
private const val LOGO_GAP = 14f

// Where things are, in dp under the top safe area…
private const val WORD_Y = 17f
private const val PICTURE_Y = 131f
private const val CREST_Y = 289f

/** The white area's edge is this much lower at the screen's edges than at its crest. */
private const val CREST_DROP = 32f
private const val TITLE_BELOW = 72f
private const val SUBTITLE_BELOW = 114f

// …and above the bottom safe area.
private const val SECONDARY_UP = 43f
private const val PRIMARY_UP = 110f
private const val CONSENT_UP = 182f
private const val BUTTON = 50f
private const val BUTTON_MARGIN = 26.5f
private const val CONSENT_MARGIN = 48f
private const val BOX = 18f

/** A tick in an 18 × 18 box. */
private val Tick = Path().apply {
    moveTo(4.5f, 9.3f); lineTo(7.8f, 12.5f); lineTo(13.6f, 5.9f)
}

private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun tideText(size: Float, lineHeight: Float, color: Color, weight: FontWeight = FontWeight.Normal, align: TextAlign = TextAlign.Center) = TextStyle(
    color = color,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    textAlign = align,
    lineHeightStyle = Centered,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private val WordmarkStyle = tideText(34f, 42f, Ink, FontWeight.Medium)
private val WordStyle = tideText(26f, 32f, Words, FontWeight.SemiBold)
private val TitleStyle = tideText(26f, 32f, Words, FontWeight.SemiBold)
private val SubtitleStyle = tideText(16f, 22f, Soft)
private val ConsentStyle = tideText(11f, 16f, Words, align = TextAlign.Start)
private val ButtonStyle = tideText(15f, 20f, Words, FontWeight.SemiBold)

private enum class Holder { Hand, Auto }

/**
 * The scene on its way from one time to another at an even pace, to rest on [landing]. [late] is
 * how long ago it was due, so that a timer that fires late does not shift it.
 */
private class Run(val from: Float, val to: Float, val duration: Float, val landing: Float, val late: Float = 0f)

private tailrec fun Context.tideActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.tideActivity()
    else -> null
}

/**
 * A first launch that starts with a breath. The splash gives way to an orange tide with a
 * sleeping face on it; holding the screen makes it rise ("Breathe in"), and at the top it sinks
 * again by itself, slower ("Breathe out"). After that one breath the welcome fades in: a picture
 * on a yellow panel, a consent box and two buttons.
 *
 * @param wordmark The name next to the dot on the splash.
 * @param consent The line next to the checkbox, in turns plain and as a link.
 * @param tideColor The tide.
 * @param panelColor The yellow panel.
 * @param autoPlay Takes one breath, shows the welcome and comes back to the splash by itself, every [LOOP] seconds.
 * @param onBreath Called when the breath is complete and the welcome shows.
 */
@Composable
fun BreathingTideWelcome(
    wordmark: String = "stillwater",
    inhaleLabel: String = "Breathe in",
    exhaleLabel: String = "Breathe out",
    title: String = "Welcome to Stillwater",
    subtitle: String = "Support for all of life’s moments",
    consent: List<String> = listOf("I agree to Stillwater’s ", "Terms & Conditions", " and acknowledge the ", "Privacy Policy", "."),
    primaryLabel: String = "Create an account",
    secondaryLabel: String = "Log in",
    tideColor: Color = Tide,
    panelColor: Color = Panel,
    autoPlay: Boolean = false,
    onBreath: (() -> Unit)? = null,
    onAgree: ((agreed: Boolean) -> Unit)? = null,
    onPrimary: (() -> Unit)? = null,
    onSecondary: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    // The gestures and the loop outlive a recomposition: what they read is state.
    val calm by rememberUpdatedState(rememberReducedMotion())
    val breathed by rememberUpdatedState(onBreath)
    val agreedTo by rememberUpdatedState(onAgree)

    // The scene time. Read while drawing only, so a moving scene redraws and does not recompose.
    val scene = remember { mutableFloatStateOf(0f) }
    var run by remember { mutableStateOf<Run?>(null) }
    // The breath in progress: whose it is, for the haptic at its top and for a touch that interrupts.
    var holder by remember { mutableStateOf<Holder?>(null) }
    var agreed by remember { mutableStateOf(false) }
    // Seconds since the block appeared, for the ornaments.
    val clock = remember { mutableFloatStateOf(0f) }
    // Changes on every touch, which restarts the autoPlay loop behind a pause.
    var touches by remember { mutableIntStateOf(0) }
    val atWelcome by remember { derivedStateOf { run == null && scene.floatValue == AT_C } }
    // The welcome is composed only while some of it shows.
    val welcomeShown by remember { derivedStateOf { welcome(scene.floatValue) > 0f } }

    fun tap() = view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)

    /** The splash has rested: the logo fades and the tide comes. */
    fun enter(late: Float = 0f) {
        if (run != null || scene.floatValue != 0f) return
        run = Run(0f, AT_LOW, AT_LOW, landing = AT_LOW, late = late)
    }

    /** A finger goes down: the breath runs to its end and on to the welcome, unless it is let go before the top. */
    fun press(by: Holder, late: Float = 0f) {
        val s = scene.floatValue
        if (s <= 0f || s > AT_TOP) return
        holder = by
        run = Run(s, AT_C, AT_C - s, landing = AT_C, late = late)
    }

    /** Let go before the top: the tide sinks back, at the pace of an exhale, and waits. */
    fun release() {
        holder = null
        val s = scene.floatValue
        if (run == null || s <= 0f || s >= AT_TOP) return
        run = Run(s, AT_LOW, if (s < AT_LOW) AT_LOW - s else (s - AT_LOW) * EXHALE / INHALE, landing = AT_LOW)
    }

    /** From the welcome, or from the waiting tide, back to the splash. */
    fun goBack(late: Float = 0f) {
        if (run != null) return
        if (scene.floatValue == AT_LOW) run = Run(AT_LOW, 0f, AT_LOW, landing = 0f)
        if (scene.floatValue == AT_C) run = Run(AT_C, END, BACK, landing = 0f, late = late)
    }

    fun agree(by: Holder) {
        if (by == Holder.Hand) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        agreed = !agreed
        agreedTo?.invoke(agreed)
    }

    /** A touch takes over from the autoPlay loop, which starts again from the splash a while later. */
    fun touched() {
        if (holder == Holder.Auto) release()
        touches++
    }

    LaunchedEffect(run) {
        val now = run ?: return@LaunchedEffect
        val origin = withFrameNanos { it }
        while (true) {
            val passed = (withFrameNanos { it } - origin) / 1e9f + now.late
            if (passed >= now.duration) break
            val before = scene.floatValue
            scene.floatValue = now.from + (now.to - now.from) * passed / now.duration
            if (before < AT_TOP && scene.floatValue >= AT_TOP) {
                if (holder == Holder.Hand) view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                holder = null
            }
        }
        scene.floatValue = now.landing
        run = null
        if (now.landing == AT_C) breathed?.invoke()
        // The box is empty again by the time the splash is back.
        if (now.landing == 0f) agreed = false
    }

    // The ornaments float while the welcome is there to be seen, on a clock that started with the block.
    val appeared = remember { longArrayOf(-1L) }
    LaunchedEffect(Unit) { appeared[0] = withFrameNanos { it } }
    LaunchedEffect(calm, welcomeShown) {
        if (calm || !welcomeShown) return@LaunchedEffect
        while (true) clock.floatValue = (withFrameNanos { it } - appeared[0]) / 1e9f
    }

    LaunchedEffect(autoPlay, touches) {
        if (!autoPlay) {
            // By hand the splash gives way by itself, and the tide waits for a finger.
            delay((SPLASH * 1000).toLong())
            enter()
            return@LaunchedEffect
        }
        if (touches > 0) {
            // Three seconds of rest, then back to the splash, which the loop starts from.
            var quiet = 0f
            while (quiet < USER_HOLD) {
                delay(100)
                quiet = if (run == null) quiet + 0.1f else 0f
            }
            while (run != null || scene.floatValue != 0f) {
                goBack()
                delay(100)
            }
        }
        var origin = System.nanoTime()

        /** Waits for the moment [seconds] into the loop, and says how late it woke up. */
        suspend fun until(seconds: Float): Float {
            val due = origin + (seconds * 1e9f).toLong()
            delay(max(0L, (due - System.nanoTime()) / 1_000_000))
            return (System.nanoTime() - due) / 1e9f
        }
        while (true) {
            enter(late = until(ENTER_AT))
            press(Holder.Auto, late = until(PRESS_AT))
            until(TICK_AT)
            if (!agreed) agree(Holder.Auto)
            goBack(late = until(BACK_AT))
            origin += (LOOP * 1e9f).toLong()
        }
    }

    // The gallery's bars have light icons; this is a light screen and needs dark ones.
    DisposableEffect(view) {
        val controller = view.context.tideActivity()?.window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = true
        onDispose { if (previous != null) controller.isAppearanceLightStatusBars = previous }
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Paper)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    touched()
                }
            },
    ) {
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        val top = insets.calculateTopPadding().value
        val bottom = max(insets.calculateBottomPadding().value, 16f)
        val width = maxWidth.value
        val height = maxHeight.value
        val cx = width / 2f
        val crestY = top + CREST_Y
        val path = remember { Path() }

        Row(
            Modifier
                .align(Alignment.TopCenter)
                .offset(y = (height * LOGO_AT - LOGO_DOT / 2f).dp)
                .height(LOGO_DOT.dp)
                .graphicsLayer { alpha = logoAlpha(scene.floatValue) }
                .clearAndSetSemantics {},
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(LOGO_DOT.dp).background(Dot, CircleShape))
            Spacer(Modifier.width(LOGO_GAP.dp))
            BasicText(wordmark, style = WordmarkStyle, maxLines = 1)
        }

        Canvas(Modifier.matchParentSize().clearAndSetSemantics {}) {
            surface(0.dp) { _, _, w, h -> paintTide(path, scene.floatValue, calm, w, h, tideColor) }
        }

        BasicText(
            inhaleLabel,
            Modifier.fillMaxWidth().offset(y = (top + WORD_Y - 16f).dp).graphicsLayer { alpha = inAlpha(scene.floatValue) }.clearAndSetSemantics {},
            style = WordStyle,
            maxLines = 1,
        )
        BasicText(
            exhaleLabel,
            Modifier.fillMaxWidth().offset(y = (top + WORD_Y - 16f).dp).graphicsLayer { alpha = outAlpha(scene.floatValue) }.clearAndSetSemantics {},
            style = WordStyle,
            maxLines = 1,
        )

        // Anywhere on the screen takes the breath. A layer that is not shown still takes touches,
        // so only one of this and the welcome is there to touch.
        if (!atWelcome) {
            Box(
                Modifier
                    .matchParentSize()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown()
                            press(Holder.Hand)
                            waitForUpOrCancellation()
                            release()
                        }
                    }
                    .semantics {
                        role = Role.Button
                        contentDescription = "$inhaleLabel. Press and hold"
                        onClick {
                            press(Holder.Hand)
                            true
                        }
                    },
            )
        }

        if (welcomeShown) {
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = welcome(scene.floatValue) }
                    .then(if (atWelcome) Modifier else Modifier.clearAndSetSemantics {}),
            ) {
                // The yellow panel comes down as it fades in.
                fun slide(density: Float): Float {
                    val s = scene.floatValue
                    return if (calm || s > AT_C) 0f else -PANEL_SLIDE * density * (1f - welcome(s))
                }
                Box(
                    Modifier
                        .offset(y = (-PANEL_SLIDE).dp)
                        .fillMaxWidth()
                        .height((crestY + CREST_DROP + PANEL_SLIDE * 2f).dp)
                        .graphicsLayer { translationY = slide(density) }
                        .background(panelColor),
                )
                // The white area under it has an arched top edge, like the tide.
                Canvas(Modifier.matchParentSize()) {
                    surface(0.dp) { _, _, w, h ->
                        path.rewind()
                        path.moveTo(0f, crestY + CREST_DROP)
                        path.quadraticTo(w / 2f, crestY - CREST_DROP, w, crestY + CREST_DROP)
                        path.lineTo(w, h)
                        path.lineTo(0f, h)
                        path.close()
                        drawPath(path, Paper)
                    }
                }
                Canvas(Modifier.matchParentSize().graphicsLayer { translationY = slide(density) }) {
                    surface(0.dp) { _, _, _, _ -> paintPicture(path, if (calm) null else clock.floatValue, cx, top + PICTURE_Y) }
                }

                BasicText(title, Modifier.fillMaxWidth().offset(y = (crestY + TITLE_BELOW - 16f).dp), style = TitleStyle, maxLines = 1)
                BasicText(subtitle, Modifier.fillMaxWidth().offset(y = (crestY + SUBTITLE_BELOW - 11f).dp), style = SubtitleStyle, maxLines = 1)

                // The box fills and the tick lands on a spring.
                val landing = spring<Float>(dampingRatio = TICK_DAMPING / (2f * sqrt(TICK_STIFFNESS)), stiffness = TICK_STIFFNESS)
                val tick by animateFloatAsState(if (agreed) 1f else 0f, landing, label = "tick")
                Row(
                    Modifier
                        .offset(y = (height - bottom - CONSENT_UP - 22f).dp)
                        .padding(horizontal = CONSENT_MARGIN.dp)
                        .fillMaxWidth()
                        .height(44.dp)
                        .clickable(remember { MutableInteractionSource() }, indication = null, enabled = atWelcome, role = Role.Checkbox) { agree(Holder.Hand) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Canvas(Modifier.size(BOX.dp).border(2.dp, Words, RoundedCornerShape(4.dp))) {
                        surface(0.dp) { _, _, w, h ->
                            drawRoundRect(Words, size = Size(w, h), cornerRadius = CornerRadius(4f, 4f), alpha = tick.coerceIn(0f, 1f))
                            if (tick > 0f) {
                                scale(tick, Offset(w / 2f, h / 2f)) {
                                    drawPath(Tick, Color.White, style = Stroke(2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                                }
                            }
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    BasicText(
                        buildAnnotatedString {
                            consent.forEachIndexed { index, part ->
                                if (index % 2 == 1) withStyle(SpanStyle(color = Blue, textDecoration = TextDecoration.Underline)) { append(part) } else append(part)
                            }
                        },
                        style = ConsentStyle,
                    )
                }

                val primary = remember { MutableInteractionSource() }
                val primaryPressed by primary.collectIsPressedAsState()
                val primaryDim by animateFloatAsState(if (!agreed) 0.4f else if (primaryPressed) 0.8f else 1f, tween(200), label = "primary")
                Box(
                    Modifier
                        .offset(y = (height - bottom - PRIMARY_UP - BUTTON / 2f).dp)
                        .padding(horizontal = BUTTON_MARGIN.dp)
                        .fillMaxWidth()
                        .height(BUTTON.dp)
                        .graphicsLayer { alpha = primaryDim }
                        .background(Blue, CircleShape)
                        .clickable(primary, indication = null, enabled = atWelcome && agreed, role = Role.Button) {
                            tap()
                            onPrimary?.invoke()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(primaryLabel, style = ButtonStyle.copy(color = Color.White), maxLines = 1)
                }

                val secondary = remember { MutableInteractionSource() }
                val secondaryPressed by secondary.collectIsPressedAsState()
                val secondaryDim by animateFloatAsState(if (secondaryPressed) 0.7f else 1f, tween(120), label = "secondary")
                Box(
                    Modifier
                        .offset(y = (height - bottom - SECONDARY_UP - BUTTON / 2f).dp)
                        .padding(horizontal = BUTTON_MARGIN.dp)
                        .fillMaxWidth()
                        .height(BUTTON.dp)
                        .graphicsLayer { alpha = secondaryDim }
                        .shadow(6.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.3f), spotColor = Color.Black.copy(alpha = 0.3f))
                        .background(Blush, CircleShape)
                        .clickable(secondary, indication = null, enabled = atWelcome, role = Role.Button) {
                            tap()
                            onSecondary?.invoke()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(secondaryLabel, style = ButtonStyle, maxLines = 1)
                }
            }
        }
    }
}
