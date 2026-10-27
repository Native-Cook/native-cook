package com.ngocdevv.nativecook.blocks.onboarding.tactilecalculatorwelcome

import android.app.Activity
import android.content.Context
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

/** `LOOP`: the length of one `autoPlay` loop, in seconds. */
const val TACTILE_CALCULATOR_WELCOME_LOOP = 6.1

/** How long after the last release the loop starts again. */
private const val RESUME_AFTER_TOUCH_MS = 2000L

/** `fontScale > 1.4`: above this system font scale the compact large-text layout takes over. */
private const val COMPACT_FONT_SCALE = 1.4f

/**
 * Tactile Calculator Welcome: a cream welcome for the invented Tally Calculator. Five oversized
 * isometric keys (minus, divide, plus, multiply, equals) march down the screen on a diagonal, drawn
 * like a cel-shaded illustration; under them "Welcome to" and a red "Tally Calculator" in Inter, and a
 * flat charcoal Continue pill. The top key slides under a frosted status-bar band. Port of
 * `TactileCalculatorWelcome.tsx`.
 *
 * Loop of [TACTILE_CALCULATOR_WELCOME_LOOP] s, every frame a function of one clock `t`: one key is
 * pressed about every second, divide at 0.17 s, plus at 1.17, multiply at 2.2, equals at 3.18, divide
 * at 4.18 and plus at 5.18. Each cap sinks 14 units in five frames of 60 fps, holds about 0.4 s and is
 * released on a damped oscillation. At rest between presses (and from 5.97 s on) every key is up, so
 * the loop starts and ends in the same frame.
 *
 * Touching a key presses it the same way with a rigid tap and rewrites the headline to the operation
 * ("8 + 4 = 12" and so on); Continue swaps it for "A little clarity, every day." and back. A touch holds
 * an [autoPlay] loop still; two seconds after the last release it starts again from the beginning.
 * [previewTime] freezes the clock at that loop time and turns every touch off, to look at one frame.
 * Under Reduce Motion (animations removed) the keys do not move by themselves nor under a finger, the
 * words cross-fade and Continue dims instead of shrinking.
 */
@Composable
fun TactileCalculatorWelcome(
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false,
    previewTime: Double? = null,
    onContinue: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val fontScale = density.fontScale

    // `AppState`: the loop plays only while the screen is in the foreground.
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
    // Dark status bar icons on the cream screen (`StatusBar style="dark"`).
    DisposableEffect(view) {
        val window = (context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars ?: false
        controller?.isAppearanceLightStatusBars = true
        onDispose { controller?.isAppearanceLightStatusBars = previous }
    }

    val fonts = remember(context) { TactileCalculatorWelcomeFonts.load(context) }
    // `clock`: loop time in seconds. Read only while drawing, so a frame redraws and nothing recomposes.
    val clock = remember {
        mutableDoubleStateOf(if (previewTime != null) previewPhase(previewTime) else 0.0)
    }
    val time: () -> Double = remember(clock) { { clock.doubleValue } }
    val touch = remember { TactileCalculatorWelcomeTouch() }
    var operation by remember { mutableStateOf<TactileCalculatorWelcomeOperation?>(null) }
    var started by remember { mutableStateOf(false) }
    // Bumped by the two-second timer to run `play` again.
    var resumes by remember { mutableIntStateOf(0) }
    val interactive = previewTime == null
    val scroll = rememberScrollState()

    // `play` and its `useEffect`: a preview freezes the clock; otherwise, when the screen may play by
    // itself, the clock restarts at 0 and runs the loop linearly. Any change of what `play` depends on
    // stops the clock where it is and tries again.
    LaunchedEffect(autoPlay, previewTime, reduced, foreground, started, resumes) {
        val preview = previewTime
        if (preview != null) {
            clock.doubleValue = previewPhase(preview)
            return@LaunchedEffect
        }
        if (!autoPlay || reduced || !foreground || touch.held || started) return@LaunchedEffect
        clock.doubleValue = 0.0
        var origin = Long.MIN_VALUE
        while (true) {
            withFrameNanos { nanos ->
                if (touch.held) return@withFrameNanos
                if (origin == Long.MIN_VALUE) origin = nanos
                clock.doubleValue = ((nanos - origin) / 1e9) % TACTILE_CALCULATOR_WELCOME_LOOP
            }
            if (touch.held) break
        }
    }
    // The cleanup of that effect also drops the pending restart.
    DisposableEffect(autoPlay, previewTime, reduced, foreground, started) {
        onDispose { touch.cancelTimer() }
    }

    /** `begin`: a finger came down on a key or on Continue, so the loop holds still at rest. */
    fun begin() {
        touch.held = true
        touch.cancelTimer()
        clock.doubleValue = 0.0
    }

    /** `end`: the finger left. Two seconds later `play` runs again. */
    fun end() {
        touch.held = false
        touch.cancelTimer()
        val preview = previewTime
        if (preview != null) {
            clock.doubleValue = previewPhase(preview)
            return
        }
        touch.timer = scope.launch {
            delay(RESUME_AFTER_TOUCH_MS)
            resumes += 1
        }
    }

    /** `onPress` of Continue: swap the copy, tell the host the first time, and tick. */
    fun continueAction() {
        val wasStarted = started
        started = !started
        if (!wasStarted) onContinue?.invoke()
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    val tabular = operation != null && !started
    val welcomeRole = remember(fonts, tabular) {
        val role = TactileCalculatorWelcomeTextRole.welcome(fonts)
        if (tabular) role.withTabularFigures() else role
    }
    val titleRole = remember(fonts) { TactileCalculatorWelcomeTextRole.title(fonts) }
    val ctaRole = remember(fonts) { TactileCalculatorWelcomeTextRole.cta(fonts) }

    BoxWithConstraints(modifier.fillMaxSize().background(TactileCalculatorWelcomePalette.Cream)) {
        // `useWindowDimensions` and `useSafeAreaInsets`: the window in dp, and the bottom inset of
        // the system bars and the display cutout.
        val width = maxWidth.value
        val height = maxHeight.value
        val insets = WindowInsets.systemBars.union(WindowInsets.displayCutout)
        val safeBottom = insets.getBottom(density) / density.density

        // Large text switches to a compact layout: smaller keys, the copy straight under them.
        val compact = fontScale > COMPACT_FONT_SCALE
        val compactScale = if (compact) 0.7f else 1f
        val heroHeight = height * 0.625f * compactScale
        val keyScale = min(min(width * 0.6125f / 200f, height * 0.1628f / 116f), 1.6f) * compactScale
        val bottomPadding = max(safeBottom, height * 0.0614f)
        val copyTop = if (compact) 28f else height * 0.068f

        TactileCalculatorWelcomeBlurViewport(
            edgeDp = if (compact) 28f else 56f,
            topEdge = true,
            bottomEdge = compact,
            onlyWhenOverflow = compact,
            overflowing = { scroll.maxValue > 4f * density.density },
            backgroundColor = TactileCalculatorWelcomePalette.Cream,
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
                TactileCalculatorWelcomeFlow(
                    minHeight = height.dp,
                    heroHeight = heroHeight.dp,
                    bottomPadding = bottomPadding.dp,
                    growCopy = !compact,
                    modifier = Modifier.fillMaxWidth(),
                    hero = {
                        Box(Modifier.fillMaxSize().clipToBounds()) {
                            for (spec in TACTILE_CALCULATOR_WELCOME_KEYS) {
                                TactileCalculatorWelcomeKey(
                                    spec = spec,
                                    time = time,
                                    reduced = reduced,
                                    interactive = interactive,
                                    sceneWidth = width,
                                    sceneHeight = height * compactScale,
                                    scale = keyScale,
                                    onBegin = { begin() },
                                    onEnd = { end() },
                                    onSelect = { operation = it },
                                    onPressHaptic = { view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY) },
                                )
                            }
                        }
                    },
                    copy = {
                        Column(
                            modifier = Modifier
                                .semantics { liveRegion = LiveRegionMode.Polite }
                                .padding(start = 24.dp, end = 24.dp, top = copyTop.dp, bottom = 28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            TactileCalculatorWelcomeSwapText(
                                text = if (started) STARTED_HEADLINE else expression(operation),
                                role = welcomeRole,
                                reduced = reduced,
                            )
                            TactileCalculatorWelcomeSwapText(
                                text = if (started) STARTED_TITLE else TITLE,
                                role = titleRole,
                                reduced = reduced,
                                modifier = Modifier.padding(top = 11.dp),
                                delayMs = 40,
                            )
                        }
                    },
                    cta = {
                        TactileCalculatorWelcomeButton(
                            label = if (started) "Start again" else "Continue",
                            textRole = ctaRole,
                            enabled = interactive,
                            reduced = reduced,
                            onPressIn = { begin() },
                            onPressOut = { end() },
                            onPress = { continueAction() },
                        )
                    },
                )
            }
        }
    }
}

/** The pieces of the React Native refs the touch handlers share: `held` and `timer`. */
private class TactileCalculatorWelcomeTouch {
    /** A finger is down on a key or on Continue. Read by the loop, so it is not state. */
    var held = false

    /** The pending restart, two seconds after the last release. */
    var timer: Job? = null

    fun cancelTimer() {
        timer?.cancel()
        timer = null
    }
}

private const val TITLE = "Tally Calculator"
private const val STARTED_HEADLINE = "A little clarity, every day."
private const val STARTED_TITLE = "It all adds up."

/** `expression`: the headline for the key last touched, "Welcome to" before any. */
private fun expression(operation: TactileCalculatorWelcomeOperation?): String = when (operation) {
    TactileCalculatorWelcomeOperation.Add -> "8 + 4 = 12"
    TactileCalculatorWelcomeOperation.Subtract -> "8 − 4 = 4"
    TactileCalculatorWelcomeOperation.Multiply -> "8 × 4 = 32"
    TactileCalculatorWelcomeOperation.Divide -> "8 ÷ 4 = 2"
    TactileCalculatorWelcomeOperation.Equals -> "8 + 4 = 12"
    null -> "Welcome to"
}

/** `previewPhase`: [value] folded into 0 until the loop's length. */
private fun previewPhase(value: Double): Double {
    if (!value.isFinite()) return 0.0
    val phase = value % TACTILE_CALCULATOR_WELCOME_LOOP
    return if (phase < 0) phase + TACTILE_CALCULATOR_WELCOME_LOOP else phase
}

/** True when the user turned animations off in system settings ("Remove animations"). */
private fun animationsRemoved(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
