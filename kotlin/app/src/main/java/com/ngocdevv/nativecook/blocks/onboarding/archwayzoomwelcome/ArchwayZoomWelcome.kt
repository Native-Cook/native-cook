package com.ngocdevv.nativecook.blocks.onboarding.archwayzoomwelcome

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.ngocdevv.nativecook.shared.SoftRoundRect
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.surface
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max

private val Sheet = Color(0xFFFAFAFA)
private val Ink = Color(0xFF1C1C1E)
private val Mute = Color(0xFF8E8E93)
private val ButtonFill = Color(0xFF111111)

private const val SHEET_SIDE = 22f
private const val SHEET_HEIGHT = 315f
private const val SHEET_RADIUS = 20f
private const val BOTTOM = 27f
private const val INSET = 24f
private const val BUTTON = 49f
private const val PRESS_SCALE = 0.98f
private const val PRESS_MS = 120

// A shield in a 16 × 16 box and a thin arrow in an 18 × 12 box.
private val Shield = Path().apply {
    moveTo(2f, 2.6f); lineTo(8f, 1f); lineTo(14f, 2.6f); lineTo(14f, 8f)
    quadraticTo(14f, 12.6f, 8f, 15f); quadraticTo(2f, 12.6f, 2f, 8f); close()
}
private val Arrow = Path().apply {
    moveTo(1f, 6f); lineTo(17f, 6f)
    moveTo(12.5f, 1.5f); lineTo(17f, 6f); lineTo(12.5f, 10.5f)
}

// A drag of this many dp up moves the camera on by one round.
private const val DRAG = 500f

// The fastest a release may throw the camera, in rounds a second, and how fast a throw eases into
// the camera's own pace (per second).
private const val THROW_MAX = 1.5
private const val EASE = 3.0

// How long the frame clock runs on after a throw: the throw has eased out by then.
private const val SETTLE = 2.5

private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun sheetText(size: Float, lineHeight: Float, weight: FontWeight, color: Color, spacing: Float = 0f) = TextStyle(
    color = color,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    letterSpacing = spacing.sp,
    textAlign = TextAlign.Center,
    lineHeightStyle = Centered,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private val BrandStyle = sheetText(14f, 18f, FontWeight.Bold, Ink, 2.5f)
private val TitleStyle = sheetText(21f, 28f, FontWeight.SemiBold, Ink)
private val ButtonStyle = sheetText(13f, 18f, FontWeight.SemiBold, Color.White)
private val LinkStyle = sheetText(12.5f, 18f, FontWeight.SemiBold, Ink)
private val LegalStyle = sheetText(8f, 12f, FontWeight.Normal, Mute)
private val LegalLinkStyle = LegalStyle.copy(textDecoration = TextDecoration.Underline)

/**
 * Where the camera was last let go.
 *
 * @param rounds How many rounds in it was.
 * @param at When, on the clock of `System.nanoTime()`.
 * @param speed How fast it was thrown, in rounds a second.
 */
private class Throw(val rounds: Double, val at: Long, val speed: Double)

private fun Context.activity(): Activity? = when (this) { is Activity -> this; is ContextWrapper -> baseContext.activity(); else -> null }

/**
 * A sign-in sheet under a pale scene the camera travels through without end: into a stone archway,
 * up to the bar chart behind it until two green bars tower over the sheet, and on through them to
 * the next archway. A drag up or down over the scene moves the camera by hand.
 *
 * @param title Two lines, split with a line break.
 * @param legal The small print before the two links.
 * @param legalJoin The word between the two links.
 * @param autoPlay Travels through the scene by itself, one round every [LOOP] seconds. Off, the
 *   scene stays where a drag leaves it.
 */
@Composable
fun ArchwayZoomWelcome(
    brand: String = "VERRA",
    title: String = "Smarter financial moves\nawait",
    continueLabel: String = "Continue with Passkey",
    signInLabel: String = "Sign in with your Verra Account",
    legal: String = "by proceeding, you agree to our ",
    termsLabel: String = "terms of use",
    legalJoin: String = " & ",
    privacyLabel: String = "privacy policy",
    autoPlay: Boolean = false,
    onContinue: (() -> Unit)? = null,
    onSignIn: (() -> Unit)? = null,
    onTerms: (() -> Unit)? = null,
    onPrivacy: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val still = rememberReducedMotion()
    // The camera's own pace, in rounds a second, as asked for and as it is travelling.
    val rate = if (autoPlay && !still) 1.0 / LOOP else 0.0
    var pace by remember { mutableDoubleStateOf(rate) }
    var from by remember { mutableStateOf(Throw(0.0, System.nanoTime(), rate)) }
    // The rounds while a finger holds the scene.
    var hold by remember { mutableStateOf<Double?>(null) }
    var throwsMade by remember { mutableIntStateOf(0) }
    // Seconds into the round. Read while drawing only.
    val tau = remember { mutableFloatStateOf(0f) }
    // The gesture outlives a recomposition: it reads this through a state.
    val enabled by rememberUpdatedState(!still)

    /** The rounds travelled: a throw eases into the camera's pace, so the speed never jumps. */
    fun roundsAt(now: Long): Double {
        hold?.let { return it }
        val t = max(0.0, (now - from.at) / 1e9)
        return from.rounds + pace * t + (from.speed - pace) / EASE * (1.0 - exp(-EASE * t))
    }

    LaunchedEffect(rate) {
        if (rate == pace) return@LaunchedEffect
        // A change of pace starts from where the camera is.
        val now = System.nanoTime()
        from = Throw(roundsAt(now), now, pace)
        pace = rate
    }

    LaunchedEffect(still, pace == 0.0, throwsMade, hold != null) {
        if (still) return@LaunchedEffect
        val until = System.nanoTime() + (SETTLE * 1e9).toLong()
        while (true) {
            val now = withFrameNanos { it }
            val travelled = roundsAt(now)
            tau.floatValue = ((travelled - floor(travelled)) * LOOP).toFloat()
            // With no pace of its own the camera stops once a throw has eased out.
            if (pace == 0.0 && hold == null && now > until) break
        }
    }

    // The gallery's activity has light icons on both bars; this screen is pale.
    DisposableEffect(view) {
        val controller = view.context.activity()?.window?.let { WindowCompat.getInsetsController(it, view) }
        val status = controller?.isAppearanceLightStatusBars
        val navigation = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = true
        controller?.isAppearanceLightNavigationBars = true
        onDispose {
            if (status != null) controller.isAppearanceLightStatusBars = status
            if (navigation != null) controller.isAppearanceLightNavigationBars = navigation
        }
    }

    val shadow = remember { SoftRoundRect(Color.Black.copy(alpha = 0.1f), sigma = 15f) }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        val bottom = max(insets.calculateBottomPadding().value, 12f) + BOTTOM
        val sheetTop = maxHeight.value - bottom - SHEET_HEIGHT

        Canvas(
            Modifier
                .matchParentSize()
                .clearAndSetSemantics {}
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (!enabled) return@awaitEachGesture
                        val drag = DRAG.dp.toPx()
                        val grabbed = roundsAt(System.nanoTime())
                        val tracker = VelocityTracker().apply { addPosition(down.uptimeMillis, down.position) }
                        var held = grabbed
                        hold = held
                        try {
                            while (true) {
                                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                tracker.addPosition(change.uptimeMillis, change.position)
                                // Up is forwards.
                                held = grabbed - (change.position.y - down.position.y) / drag
                                hold = held
                                if (!change.pressed) break
                                change.consume()
                            }
                        } finally {
                            val speed = (-tracker.calculateVelocity().y / drag).toDouble().coerceIn(-THROW_MAX, THROW_MAX)
                            from = Throw(held, System.nanoTime(), speed)
                            hold = null
                            throwsMade++
                        }
                    }
                },
        ) {
            surface(0.dp) { _, _, w, h -> paintScene(w, h, sheetTop - HORIZON, tau.floatValue) }
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(start = SHEET_SIDE.dp, end = SHEET_SIDE.dp, bottom = bottom.dp)
                .fillMaxWidth()
                .height(SHEET_HEIGHT.dp)
                .drawBehind {
                    surface(0.dp) { _, _, w, h ->
                        drawIntoCanvas { shadow.draw(it.nativeCanvas, 0f, 10f, w, h, SHEET_RADIUS) }
                        drawRoundRect(Sheet, size = Size(w, h), cornerRadius = CornerRadius(SHEET_RADIUS))
                    }
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(35.dp))
            // The spacing after the last letter would push the word off centre.
            BasicText(brand, style = BrandStyle, maxLines = 1, modifier = Modifier.padding(start = 2.5.dp))
            Spacer(Modifier.height(21.dp))
            BasicText(title, style = TitleStyle, maxLines = 2)
            Spacer(Modifier.height(25.dp))

            val interaction = remember { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            val press by animateFloatAsState(if (pressed) PRESS_SCALE else 1f, tween(PRESS_MS), label = "press")
            Row(
                Modifier
                    .padding(horizontal = INSET.dp)
                    .fillMaxWidth()
                    .height(BUTTON.dp)
                    .graphicsLayer {
                        scaleX = press
                        scaleY = press
                    }
                    .background(ButtonFill, RoundedCornerShape(2.dp))
                    .clickable(interaction, indication = null, role = Role.Button) {
                        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                        onContinue?.invoke()
                    }
                    .semantics(mergeDescendants = true) { contentDescription = continueLabel },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Canvas(Modifier.size(16.dp)) {
                    surface(0.dp) { _, _, _, _ -> drawPath(Shield, Color.White, style = Stroke(1.3f, join = StrokeJoin.Round)) }
                }
                Spacer(Modifier.width(10.dp))
                BasicText(continueLabel, style = ButtonStyle, maxLines = 1, modifier = Modifier.clearAndSetSemantics {})
                Spacer(Modifier.width(10.dp))
                Canvas(Modifier.size(18.dp, 12.dp)) {
                    surface(0.dp) { _, _, _, _ -> drawPath(Arrow, Color.White, style = Stroke(1f, cap = StrokeCap.Round, join = StrokeJoin.Round)) }
                }
            }

            Spacer(Modifier.height(24.dp))
            BasicText(
                signInLabel,
                style = LinkStyle,
                maxLines = 1,
                modifier = Modifier.clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) { onSignIn?.invoke() },
            )
            Spacer(Modifier.height(30.dp))
            Row {
                BasicText(legal, style = LegalStyle, maxLines = 1)
                BasicText(
                    termsLabel,
                    style = LegalLinkStyle,
                    maxLines = 1,
                    modifier = Modifier.clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) { onTerms?.invoke() },
                )
                BasicText(legalJoin, style = LegalStyle, maxLines = 1)
                BasicText(
                    privacyLabel,
                    style = LegalLinkStyle,
                    maxLines = 1,
                    modifier = Modifier.clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) { onPrivacy?.invoke() },
                )
            }
        }
    }
}
