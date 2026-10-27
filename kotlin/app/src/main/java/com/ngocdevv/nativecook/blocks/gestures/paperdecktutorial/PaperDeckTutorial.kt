package com.ngocdevv.nativecook.blocks.gestures.paperdecktutorial

import android.app.Activity
import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.max
import kotlin.math.min

private val ScreenColor = Color(0xFF050505)

/**
 * Paper Deck Tutorial: a six-card conversation tutorial on ink black. A "Tutorial" header with its count
 * sits between a gear and a lock disc while the illustrated cover deck shows, and a white close disc while
 * the tutorial runs. The cover (a puzzled character on violet, two covers peeking above) turns edge-on to
 * reveal the first white card; each card carries one serif instruction, an emoji and its number, and flies
 * off in the direction it asks for. The tutorial ends on a "This or that" deck with a burger-or-apple card.
 * Port of `PaperDeckTutorial.tsx`.
 *
 * Loop of [PAPER_DECK_TUTORIAL_LOOP] s, every frame a function of one clock (`PaperDeckTutorialScript`):
 *   0.20-0.70  the cover lifts from below; 0.60-1.12 it turns about its vertical axis (edge-on at 0.84 s)
 *   1.10-1.60  the rear sheets appear and settle
 *   3.40-4.00  card 1 is thrown right; 5.15-5.75 card 2 left; then short right throws at 9.12, 11.27, 12.55
 *   14.33-14.50  the last card is thrown up and away; 14.90-15.20 the "This or that" deck rises
 *   17.50-17.90  the choice card fades into the thinking cover; the rest to 18.5 s is the resting cover
 *
 * A finger on the screen holds the loop where it is, as a manual deck; the card follows the finger
 * (bending, tilting about where it was grabbed, its shadow widening), and a release past 26% of the
 * card's width or faster than 650 dp per second throws it on a spring (400 ms, damping ratio 0.8) that keeps
 * the finger's speed. Two seconds after the last touch the deck eases home and the loop resumes at the
 * step's anchor. [previewTime] freezes the clock at that loop time and turns every touch off, to look at any
 * single frame. Under Reduce Motion (animations removed) the deck is still, the cards change at once, and
 * [onComplete] is called when the last card is gone, as when the tutorial finishes any other way.
 *
 * At large text sizes (font scale over 1.4) the page scrolls and its top and bottom edges blur progressively.
 */
@Composable
fun PaperDeckTutorial(
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false,
    previewTime: Double? = null,
    onComplete: (() -> Unit)? = null,
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
    // Light status bar icons on the black (`StatusBar style="light"`).
    DisposableEffect(view) {
        val window = (context as? Activity)?.window
        val insetsController = window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = insetsController?.isAppearanceLightStatusBars ?: false
        insetsController?.isAppearanceLightStatusBars = false
        onDispose { insetsController?.isAppearanceLightStatusBars = previous }
    }

    val assets = remember(context) { PaperDeckTutorialAssets(context) }
    val controller = remember {
        PaperDeckTutorialController(
            initialClock = when {
                previewTime != null -> PaperDeckTutorialScript.previewPhase(previewTime)
                !autoPlay || reduced -> 1.1
                else -> 0.0
            },
            initialManual = reduced || (previewTime == null && !autoPlay),
        )
    }
    val touchEnabled = previewTime == null

    BoxWithConstraints(modifier.fillMaxSize().background(ScreenColor).clipToBounds()) {
        val screenWidth = maxWidth.value.toDouble()
        val screenHeight = maxHeight.value.toDouble()
        // `useSafeAreaInsets`: the system bars and the display cutout, in dp.
        val insets = WindowInsets.systemBars.union(WindowInsets.displayCutout)
        val safeTop = insets.getTop(density) / density.density
        val safeBottom = insets.getBottom(density) / density.density

        // The card: 0.9 of the window's width up to 410, 0.6 of its height between 400 (520 at large text) and 660.
        val cardWidth = min(screenWidth * 0.9, 410.0)
        val cardHeight = max(if (fontScale > 1.4f) 520.0 else 400.0, min(660.0, screenHeight * 0.6))
        val geometry = remember(cardWidth, cardHeight, screenHeight) { PaperDeckTutorialGeometry(cardWidth, cardHeight, screenHeight) }

        // The flags the controller reads when a touch or a timer fires, kept current after every composition.
        SideEffect {
            controller.autoPlay = autoPlay
            controller.reduced = reduced
            controller.foreground = foreground
            controller.previewTime = previewTime
            controller.screenWidth = screenWidth
            controller.cardWidth = cardWidth
            controller.cardHeight = cardHeight
            controller.onComplete = onComplete
            controller.onHaptic = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
        }
        // Declared in the order of the React Native effects: Reduce Motion first, then `play`.
        DisposableEffect(controller, reduced) {
            controller.onReducedEffect()
            onDispose { }
        }
        DisposableEffect(controller, autoPlay, foreground, previewTime, reduced) {
            controller.play()
            onDispose { controller.stopMotion() }
        }
        LaunchedEffect(controller) {
            while (true) {
                withFrameNanos { nanos -> controller.advance(nanos) }
            }
        }

        // Large text lets the page scroll; a scroll counts as a touch (`onScrollBeginDrag` to `onMomentumScrollEnd`).
        val scrollable = fontScale > 1.4f
        val scroll = rememberScrollState()
        LaunchedEffect(scroll, scrollable, touchEnabled) {
            if (!scrollable || !touchEnabled) return@LaunchedEffect
            var moving = false
            snapshotFlow { scroll.isScrollInProgress }.collect { now ->
                if (now && !moving) controller.buttonBegin()
                if (!now && moving) controller.buttonEnd()
                moving = now
            }
        }

        // Layout, from the `contentContainerStyle` and styles of the React Native file.
        val headerHeight = 62.0 * max(1.0, fontScale * 0.8)
        val topPadding = safeTop.toDouble() + 12.0
        val bottomPadding = max(safeBottom.toDouble(), 16.0)
        val stageMinHeight = max(cardHeight + 90.0, screenHeight - topPadding - bottomPadding - headerHeight)
        val stageTopPadding = if (scrollable) 8.0 else max(8.0, screenHeight * 0.224 - safeTop - 12.0 - 62.0)

        PaperDeckTutorialBlurViewport(
            overflowing = { scrollable && scroll.maxValue > 4f * density.density },
            fade = ScreenColor,
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .paperDeckTutorialTouchHold(touchEnabled, controller)
                    .then(if (scrollable) Modifier.verticalScroll(scroll) else Modifier),
            ) {
                Box(Modifier.fillMaxWidth().heightIn(min = screenHeight.dp)) {
                    // The buttons sit 29 dp above the header's top edge, so they are laid over its row, inside the
                    // page. They come first, so the deck (a later sibling in the React Native tree too) draws over them.
                    PaperDeckTutorialHeaderButtons(
                        controller = controller,
                        assets = assets,
                        touchEnabled = touchEnabled,
                        modifier = Modifier.offset(y = (topPadding - 29.0).dp),
                    )
                    Column(Modifier.fillMaxWidth().padding(top = topPadding.dp, bottom = bottomPadding.dp)) {
                        PaperDeckTutorialHeader(controller, assets, reduced, headerHeight.dp)
                        // `stage`: the deck, at the top of what is left (centred at large text).
                        Box(
                            Modifier.fillMaxWidth().heightIn(min = stageMinHeight.dp).padding(top = stageTopPadding.dp),
                            contentAlignment = if (scrollable) Alignment.Center else Alignment.TopCenter,
                        ) {
                            PaperDeckTutorialDeck(controller, assets, geometry, reduced, touchEnabled)
                        }
                    }
                }
            }
        }
    }
}

/** True when the user turned animations off in system settings ("Remove animations"). */
private fun animationsRemoved(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
