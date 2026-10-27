package com.ngocdevv.nativecook.blocks.splash.daylighticonreveal

import android.app.Activity
import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.max
import kotlin.math.min

/** `LOOP`: the length of one `autoPlay` loop, in seconds. */
const val DAYLIGHT_ICON_REVEAL_LOOP = 8.8

private val ScreenColor = Color(0xFFF0EFF5)
private val TitleColor = Color(0xFF181820)
private val SubtitleColor = Color(0xFF8E8C96)
private val NoteColor = Color(0xFF9A98A2)

private const val CONTINUE_LABEL = "Continue"
private const val REPLAY_LABEL = "Replay welcome"
private const val TITLE = "Find your energy.\nFollow your rhythm."
private const val TITLE_BEGAN = "Make room for\nyour rhythm."
private const val SUBTITLE = "Meet Daylight, a gentle guide to the daily patterns that help you feel your best."
private const val SUBTITLE_BEGAN = "Daylight is ready. Begin with a quiet moment and notice how you feel."
private const val NOTE = "By continuing you agree to the Terms and Conditions and confirm that you have read and understood the Privacy Notice. ›"
private const val NOTE_BEGAN = "A fresh start, whenever you need one."

/**
 * Daylight Icon Reveal: a pale lavender welcome for the invented Daylight app. A glossy split
 * day-and-night icon rises oversized from below and settles in the upper third (a perspective
 * plane that follows measured tracks), then the headline, a line of copy, a Continue button and
 * small print come out of a soft blur on lift curves taken from the reference, one light sweep
 * crosses the icon and a faint warm and cool glow breathes behind it. At the end of the loop the
 * icon recedes and the copy leaves upward into a blur. Port of `DaylightIconReveal.tsx`.
 *
 * Loop of [DAYLIGHT_ICON_REVEAL_LOOP] s, every frame a function of one clock:
 *   0.35-1.30  the icon appears and shrinks into view (alpha 0 to 1)
 *   1.30-4.00  it settles at half the screen's width, about 3% undershoot
 *   1.40-2.60  the glow fades in; 1.75-3.25+0.75 the copy blocks reveal in turn
 *   2.50-3.40  the light sweep; 2.25-7.20 the button answers touch
 *   7.20-7.55  the copy leaves; 7.45-8.15 the icon recedes; 8.10 and the loop is back at rest
 *
 * Without [autoPlay] the screen is the settled welcome, and Continue swaps the words and Replay welcome
 * plays the entrance once. A touch on the button holds an `autoPlay` loop; two seconds after the last
 * release it settles in 240 ms and the loop starts again 650 ms after that began. [previewTime] freezes
 * the clock at that loop time and turns the button off, to look at any single frame.
 * Under Reduce Motion (animations removed) everything is in place and only fades.
 */
@Composable
fun DaylightIconReveal(
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false,
    previewTime: Double? = null,
    onContinue: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val owner = LocalLifecycleOwner.current
    val fontScale = density.fontScale

    var foreground by remember { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var reduced by remember { mutableStateOf(animationsRemoved(context)) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, _ ->
            foreground = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            reduced = animationsRemoved(context)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    // Dark status bar icons on the pale screen (`StatusBar style="dark"`).
    DisposableEffect(view) {
        val window = (context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars ?: false
        controller?.isAppearanceLightStatusBars = true
        onDispose { controller?.isAppearanceLightStatusBars = previous }
    }

    var began by remember { mutableStateOf(false) }
    var replayEpoch by remember { mutableIntStateOf(0) }
    val clock = remember {
        val start = when {
            previewTime != null -> DaylightIconRevealClock.previewPhase(previewTime)
            reduced || !autoPlay -> 4.0
            else -> 0.0
        }
        DaylightIconRevealClock(start)
    }
    // The flags the clock reads when a timer fires, kept current after every composition.
    SideEffect {
        clock.autoPlay = autoPlay
        clock.reduced = reduced
        clock.foreground = foreground
        clock.began = began
        clock.previewTime = previewTime
        clock.onLanding = {
            if (!clock.landed) {
                clock.landed = true
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
    }
    DisposableEffect(autoPlay, began, foreground, previewTime, reduced, replayEpoch) {
        clock.onEffect(replayEpoch)
        onDispose { clock.onEffectCleanup() }
    }
    LaunchedEffect(clock) {
        while (true) {
            withFrameNanos { nanos -> clock.advance(nanos) }
        }
    }
    // The button answers from 2.25 s to 7.2 s of the loop (`ready`).
    val ready by remember(clock) { derivedStateOf { reduced || (clock.time >= 2.25 && clock.time < 7.2) } }
    val time: () -> Double = remember(clock) { { clock.time } }
    val bitmap = remember(context) { loadDaylightIconRevealBitmap(context) }
    val scroll = rememberScrollState()

    fun continueAction() {
        clock.clearTimers()
        clock.cancel()
        haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
        if (began) {
            clock.landed = false
            began = false
            replayEpoch += 1
            if (reduced) clock.set(4.0)
        } else {
            began = true
            clock.set(4.0)
            onContinue?.invoke()
        }
    }

    BoxWithConstraints(modifier.fillMaxSize().background(ScreenColor)) {
        val width = maxWidth.value
        val height = maxHeight.value
        // `useSafeAreaInsets`: the system bars and the display cutout, in dp.
        val insets = WindowInsets.systemBars.union(WindowInsets.displayCutout)
        val safeTop = insets.getTop(density) / density.density
        val safeBottom = insets.getBottom(density) / density.density

        // Large text switches to a compact layout: a smaller hero, the copy straight under it.
        val compact = fontScale > 1.4f
        val heroHeight = max(if (compact) 240f else 320f, height * if (compact) 0.38f else 0.57f)
        val artWidth = width * if (compact) 0.8f else 1f
        val artHeight = height * if (compact) 0.8f else 1f
        // Where the settled icon's centre lands (top 0.242 + half its 0.232 height).
        val iconCenterY = if (compact) heroHeight / 2f else height * 0.358f
        val iconLeft = (width - artWidth) / 2f
        val iconTop = if (compact) (heroHeight + safeTop) / 2f - artHeight * 0.35836f else 0f
        val bottomPadding = max(safeBottom, 18f)
        val copyPadding = if (width > 420f) 40f else 25f
        val buttonWidth = min(0.66f * (width - 2f * copyPadding), 300f)
        // The copy fills what is left of the screen (`flex: 1`), unless the layout is compact.
        val copyMinHeight = if (compact) 0f else max(0f, height - bottomPadding - heroHeight)

        DaylightIconRevealBlurViewport(
            enabled = compact,
            overflowing = { scroll.maxValue > 4f * density.density },
            backgroundColor = ScreenColor,
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
                Column(Modifier.fillMaxWidth().heightIn(min = height.dp)) {
                    // Hero: the glow and the icon, hidden from accessibility.
                    Box(Modifier.fillMaxWidth().height(heroHeight.dp).clearAndSetSemantics { }) {
                        DaylightIconRevealGlow(time, reduced, width, heroHeight, iconCenterY)
                        DaylightIconRevealIcon(bitmap, time, reduced, iconLeft, iconTop, artWidth, artHeight, width.dp, height.dp)
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (compact) {
                                    Modifier
                                } else {
                                    Modifier.graphicsLayer { translationY = height * 0.032f * this.density }
                                },
                            )
                            .heightIn(min = copyMinHeight.dp)
                            .padding(start = copyPadding.dp, end = copyPadding.dp, top = if (compact) 16.dp else 26.dp, bottom = 12.dp),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Box(Modifier.fillMaxWidth().daylightIconRevealStage(DaylightIconRevealStage.Title, reduced, (height * 0.32f).dp, time)) {
                            DaylightIconRevealSwapText(
                                text = if (began) TITLE_BEGAN else TITLE,
                                style = TextStyle(
                                    color = TitleColor,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    letterSpacing = (-0.65f / 31f).em,
                                    lineHeightStyle = DaylightIconRevealLineHeightStyle,
                                ),
                                fontSize = 31f,
                                lineHeight = 36f,
                                reduced = reduced,
                                modifier = Modifier.fillMaxWidth(),
                                maxFontScale = 1.6f,
                                isHeading = true,
                            )
                        }
                        Box(Modifier.fillMaxWidth().daylightIconRevealStage(DaylightIconRevealStage.Subtitle, reduced, (height * 0.29f).dp, time)) {
                            DaylightIconRevealSwapText(
                                text = if (began) SUBTITLE_BEGAN else SUBTITLE,
                                style = TextStyle(
                                    color = SubtitleColor,
                                    textAlign = TextAlign.Center,
                                    lineHeightStyle = DaylightIconRevealLineHeightStyle,
                                ),
                                fontSize = 15f,
                                lineHeight = 20f,
                                reduced = reduced,
                                modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp).widthIn(max = 300.dp),
                                delayMs = 50,
                            )
                        }
                        Box(Modifier.fillMaxWidth().daylightIconRevealStage(DaylightIconRevealStage.Button, reduced, (height * 0.25f).dp, time)) {
                            DaylightIconRevealButton(
                                label = if (began) REPLAY_LABEL else CONTINUE_LABEL,
                                width = buttonWidth.dp,
                                enabled = ready && previewTime == null,
                                reduced = reduced,
                                onPressIn = clock::press,
                                onPressOut = clock::release,
                                onPress = ::continueAction,
                                modifier = Modifier.align(Alignment.TopCenter).padding(top = 23.dp),
                            )
                        }
                        Box(Modifier.fillMaxWidth().daylightIconRevealStage(DaylightIconRevealStage.Note, reduced, 8.dp, time)) {
                            DaylightIconRevealSwapText(
                                text = if (began) NOTE_BEGAN else NOTE,
                                style = TextStyle(
                                    color = NoteColor,
                                    textAlign = TextAlign.Center,
                                    lineHeightStyle = DaylightIconRevealLineHeightStyle,
                                ),
                                fontSize = 9.5f,
                                lineHeight = 12.5f,
                                reduced = reduced,
                                modifier = Modifier.padding(start = 56.dp, top = 14.dp, end = 56.dp),
                                delayMs = 100,
                            )
                        }
                    }
                    Spacer(Modifier.height(bottomPadding.dp))
                }
            }
        }
    }
}

/** True when the user turned animations off in system settings ("Remove animations"). */
private fun animationsRemoved(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
