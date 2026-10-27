package com.ngocdevv.nativecook.blocks.progress.brightlessonrecap

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.database.ContentObserver
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ngocdevv.nativecook.blocks.progress.brightlessonrecap.BrightLessonRecapMotion.FADE_END
import com.ngocdevv.nativecook.blocks.progress.brightlessonrecap.BrightLessonRecapMotion.SOURCE_END
import com.ngocdevv.nativecook.blocks.progress.brightlessonrecap.BrightLessonRecapMotion.phase
import kotlinx.coroutines.delay

/** Two seconds after the last touch the paused story plays on (BrightLessonRecap.tsx:104). */
private const val RESUME_DELAY_MS = 2000L

/**
 * Bright Lesson Recap: a lesson-complete screen. An original otter bursts in over broad diagonal
 * green bands with "Brilliant work!" in heavy rounded Nunito, then settles into a compact recap
 * under four-point sparkles, with "Take a bow!", three outlined stat pills that grow coloured
 * caps (TOTAL XP, AMAZING, QUICK) and count up, a share button and a physical blue CLAIM XP key.
 *
 * Port of react-native/src/blocks/progress/bright-lesson-recap/BrightLessonRecap.tsx, value for
 * value. One clock drives everything (a frame is a function of loop time), so [previewTime]
 * freezes any frame of the [BRIGHT_LESSON_RECAP_LOOP]-second loop and turns touch off.
 *
 * Loop (autoPlay): the bands and the otter enter at 0.33 s; the otter drifts right and compacts
 * 1.2 to 1.45 s as the big title shrinks away and the small one rises; the pills form at 2.37 s,
 * 2.97 s and 3.65 s and count to 45, 100% and 3:40; the scene holds until 5.85 s, then fades and
 * resets. Touch (scrolling, holding the key) pauses the story until two seconds after the last
 * touch; tapping the otter replays; CLAIM XP freezes the complete recap and sends "+45 XP" up.
 * Reduced motion (animator duration scale 0): the complete recap, still.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BrightLessonRecap(
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false,
    previewTime: Double? = null,
    onClaim: ((points: Int) -> Unit)? = null,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val owner = LocalLifecycleOwner.current
    val density = LocalDensity.current

    val controller = remember(autoPlay, previewTime) {
        BrightLessonRecapController(autoPlay, previewTime, readReducedMotion(context))
    }
    SideEffect {
        controller.onClaim = onClaim
        controller.haptic = { haptic ->
            val constant = when (haptic) {
                BrightLessonRecapHaptic.Light -> HapticFeedbackConstants.CLOCK_TICK
                BrightLessonRecapHaptic.Success ->
                    if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.CONTEXT_CLICK
            }
            view.performHapticFeedback(constant)
        }
    }

    // Foreground state and the live Reduce Motion preference (animator duration scale).
    DisposableEffect(owner, context, controller) {
        fun refresh() {
            controller.foreground = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            controller.reduced = readReducedMotion(context)
        }
        refresh()
        val lifecycleObserver = LifecycleEventObserver { _, _ -> refresh() }
        owner.lifecycle.addObserver(lifecycleObserver)
        val settingsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                controller.reduced = readReducedMotion(context)
            }
        }
        context.contentResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            settingsObserver,
        )
        onDispose {
            owner.lifecycle.removeObserver(lifecycleObserver)
            context.contentResolver.unregisterContentObserver(settingsObserver)
        }
    }

    // Dark status bar icons on the white screen, like `<StatusBar style="dark" />`.
    DisposableEffect(view) {
        val window = context.findActivity()?.window
        val controllerCompat = window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controllerCompat?.isAppearanceLightStatusBars ?: false
        controllerCompat?.isAppearanceLightStatusBars = true
        onDispose { controllerCompat?.isAppearanceLightStatusBars = previous }
    }

    // The clock: one frame callback while the story is running (`useSceneClock`, motion.ts:21-42).
    val running = controller.running
    LaunchedEffect(controller, running, controller.foreground, controller.reduced) {
        controller.resetClock()
        if (controller.previewTime != null || controller.reduced || !running || !controller.foreground) {
            return@LaunchedEffect
        }
        while (true) {
            withFrameNanos { nanos -> controller.onFrame(nanos / 1_000_000.0) }
        }
    }

    // A paused story plays on two seconds after the last touch (BrightLessonRecap.tsx:104).
    LaunchedEffect(controller.claimed, controller.foreground, controller.interacting, controller.paused, controller.reduced) {
        if (!controller.paused || controller.interacting || !controller.foreground || controller.reduced || controller.claimed) {
            return@LaunchedEffect
        }
        delay(RESUME_DELAY_MS)
        controller.paused = false
    }

    // Claiming sends "+45 XP" up 56 dp and fades it in 700 ms (BrightLessonRecap.tsx:111-112).
    LaunchedEffect(controller.rewardRun) {
        if (controller.rewardRun == 0) return@LaunchedEffect
        controller.reward.snapTo(0f)
        controller.reward.animateTo(1f, tween(700, easing = LinearEasing))
    }

    val fontScale = density.fontScale
    val type = remember(context, fontScale) { BrightLessonRecapTypography(context, fontScale) }
    val mascot = remember(context) {
        context.assets.open("brightlessonrecap/brightlessonrecap_pip-celebrates.png").use { BitmapFactory.decodeStream(it) }.asImageBitmap()
    }
    val insets = WindowInsets.systemBarsIgnoringVisibility.union(WindowInsets.displayCutout)
    val insetTop = with(density) { insets.getTop(density).toDp().value }
    val insetBottom = with(density) { insets.getBottom(density).toDp().value }

    BoxWithConstraints(modifier.fillMaxSize().background(Color.White)) {
        val metrics = remember(maxWidth, maxHeight, fontScale, insetTop, insetBottom) {
            BrightLessonRecapMetrics(maxWidth.value, maxHeight.value, fontScale, insetTop, insetBottom)
        }
        val scroll = rememberScrollState()
        // The blur edges only show once the content is taller than the screen (`onlyWhenOverflow`).
        val overflowing by remember(scroll) {
            derivedStateOf { scroll.maxValue != Int.MAX_VALUE && scroll.maxValue > with(density) { 4.dp.toPx() } }
        }
        // Dragging the scroll view pauses the story, lifting the finger releases it.
        LaunchedEffect(scroll, controller) {
            scroll.interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is DragInteraction.Start -> controller.pause()
                    is DragInteraction.Stop, is DragInteraction.Cancel -> controller.release()
                }
            }
        }
        BrightLessonRecapBlurViewport(overflowing = overflowing, modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().verticalScroll(scroll)) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = metrics.height.dp)
                        .padding(
                            start = metrics.contentInset.dp,
                            end = metrics.contentInset.dp,
                            top = (insetTop + 18f).dp,
                            bottom = (insetBottom + 18f).dp,
                        ),
                ) {
                    Scene(controller, metrics, type, mascot)
                    if (metrics.flow) {
                        Spacer(Modifier.height(28.dp))
                        Spacer(Modifier.weight(1f))
                    } else {
                        Spacer(Modifier.height(metrics.actionGap.dp))
                    }
                    BrightLessonRecapActions(controller, metrics, type)
                }
            }
        }
    }
}

/** Hero and stats, faded out together at the end of the loop (`scene`, BrightLessonRecap.tsx:123 and 137). */
@Composable
private fun Scene(
    controller: BrightLessonRecapController,
    metrics: BrightLessonRecapMetrics,
    type: BrightLessonRecapTypography,
    mascot: ImageBitmap,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .brightLessonRecapGroupAlpha { (1 - phase(controller.time, SOURCE_END, FADE_END)).toFloat() },
    ) {
        BrightLessonRecapHero(controller, metrics, type, mascot)
        if (metrics.flow) {
            Box(Modifier.padding(bottom = 28.dp)) { BrightLessonRecapFlowCopy(controller, type) }
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                repeat(3) { index -> BrightLessonRecapStatCard(index, controller, metrics, type) }
            }
        } else {
            Row(
                Modifier
                    .width((metrics.width * 218f / 272f).dp)
                    .align(Alignment.CenterHorizontally),
                horizontalArrangement = Arrangement.spacedBy((metrics.width * 12.5f / 272f).dp),
            ) {
                repeat(3) { index -> BrightLessonRecapStatPill(index, controller, metrics, type, Modifier.weight(1f)) }
            }
        }
    }
}

/** Animator duration scale 0 is Android's "Remove animations": the same switch as Reduce Motion. */
private fun readReducedMotion(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
