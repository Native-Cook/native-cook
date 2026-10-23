package com.ngocdevv.nativecook.blocks.lists.foryouinsights

import android.app.Activity
import android.view.HapticFeedbackConstants
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tanh

private fun forYouInsightsNow(): Double = System.nanoTime() / 1_000_000_000.0

private fun forYouInsightsY(reference: Double, safeTop: Double, scale: Double): Double {
    return safeTop + (reference - ForYouInsightsTheme.REF_SAFE_TOP) * scale
}

@Composable
fun ForYouInsights(
    autoPlay: Boolean = false,
    modifier: Modifier = Modifier,
) {
    CompositionLocalProvider(
        LocalTextStyle provides TextStyle(
            platformStyle = PlatformTextStyle(includeFontPadding = false),
        ),
    ) {
        ForYouInsightsScreen(autoPlay = autoPlay, modifier = modifier)
    }
}

@Composable
private fun ForYouInsightsScreen(
    autoPlay: Boolean,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val activity = LocalActivity.current
    val density = LocalDensity.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val reducedMotion = rememberReducedMotion()
    val controller = remember { ForYouInsightsController() }
    val assets = remember(context) { loadForYouInsightsAssets(context.applicationContext) }
    val frame = remember { mutableStateOf(ForYouInsightsFrame()) }
    val feedScroll = rememberScrollState()
    val readerLiveState = remember { mutableStateOf(false) }
    val scrollTokenState = remember { mutableIntStateOf(0) }
    val wakeGenerationState = remember { mutableIntStateOf(0) }
    var section by remember { mutableStateOf(ForYouInsightsSection.INSIGHTS) }
    var activeArticle by remember { mutableIntStateOf(0) }
    var foreground by remember {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }

    fun publish(time: Double) {
        val next = controller.tick(time)
        frame.value = next
        readerLiveState.value = next.readerLive
        scrollTokenState.intValue = next.scrollToken
    }

    fun wake() {
        wakeGenerationState.intValue += 1
        publish(forYouInsightsNow())
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> foreground = true
                Lifecycle.Event.ON_PAUSE,
                Lifecycle.Event.ON_STOP,
                -> foreground = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(activity, view) {
        val window = (activity as? Activity)?.window
        val bars = window?.let { value -> WindowCompat.getInsetsController(value, view) }
        val previous = bars?.isAppearanceLightStatusBars
        bars?.isAppearanceLightStatusBars = true
        onDispose {
            if (previous != null) {
                bars?.isAppearanceLightStatusBars = previous
            }
        }
    }

    LaunchedEffect(autoPlay, reducedMotion) {
        controller.configure(
            autoPlay = autoPlay,
            quiet = reducedMotion,
            time = forYouInsightsNow(),
        )
        publish(forYouInsightsNow())
        wakeGenerationState.intValue += 1
    }

    LaunchedEffect(section, feedScroll) {
        feedScroll.scrollTo(0)
        controller.setScrollY(0.0)
    }

    LaunchedEffect(feedScroll, density.density) {
        snapshotFlow { feedScroll.value }
            .distinctUntilChanged()
            .collect { pixels ->
                controller.setScrollY(pixels / density.density.toDouble())
            }
    }

    LaunchedEffect(scrollTokenState.intValue) {
        if (scrollTokenState.intValue > 0) {
            feedScroll.animateScrollTo(0)
        }
    }

    LaunchedEffect(
        controller,
        foreground,
        wakeGenerationState.intValue,
        autoPlay,
        reducedMotion,
    ) {
        if (!foreground) {
            return@LaunchedEffect
        }
        while (isActive && controller.wantsFrames(forYouInsightsNow())) {
            withFrameNanos { nanos ->
                publish(nanos / 1_000_000_000.0)
            }
        }
    }

    val safeTop = with(density) { WindowInsets.safeDrawing.getTop(density).toDp().value.toDouble() }
    val bottomInset = with(density) { WindowInsets.safeDrawing.getBottom(density).toDp().value.toDouble() }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(ForYouInsightsTheme.Colors.page),
    ) {
        val width = maxWidth.value.toDouble()
        val height = maxHeight.value.toDouble()
        val scale = width / ForYouInsightsTheme.REF_WIDTH
        val heroX = ForYouInsightsTheme.Layout.HERO_INSET * scale
        val heroY = forYouInsightsY(ForYouInsightsTheme.Layout.HERO_TOP, safeTop, scale)
        val heroWidth = width - heroX * 2.0
        val heroHeight = ForYouInsightsTheme.Layout.HERO_HEIGHT * scale
        val readerLive = readerLiveState.value
        val actorProvider = {
            forYouInsightsActorFrame(
                frame = frame.value,
                controller = controller,
                quiet = reducedMotion,
                scale = scale,
                safeTop = safeTop,
                heroX = heroX,
                heroY = heroY,
                heroWidth = heroWidth,
                heroHeight = heroHeight,
            )
        }

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(controller) {
                    awaitEachGesture {
                        val down = awaitFirstDown(
                            requireUnconsumed = false,
                            pass = PointerEventPass.Initial,
                        )
                        controller.touchBegan(forYouInsightsNow())
                        wake()
                        while (true) {
                            val change = awaitPointerEvent(PointerEventPass.Final)
                                .changes
                                .firstOrNull { item -> item.id == down.id }
                            if (change == null || !change.pressed) {
                                break
                            }
                        }
                        controller.touchEnded(forYouInsightsNow())
                        wake()
                    }
                },
        ) {
            ForYouInsightsFeed(
                width = width,
                scale = scale,
                safeTop = safeTop,
                bottomInset = bottomInset,
                section = section,
                reducedMotion = reducedMotion,
                readerLive = readerLive,
                activeArticle = activeArticle,
                frame = frame,
                assets = assets,
                feedScroll = feedScroll,
                onMore = {
                    if (!reducedMotion) {
                        controller.regenerate(forYouInsightsNow())
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        wake()
                    }
                },
                onPressRow = { index, down ->
                    val accepted = controller.pressRow(index, down, forYouInsightsNow())
                    if (down && accepted) {
                        activeArticle = index
                    }
                    wake()
                },
                onOpenRow = { index, base ->
                    val opened = controller.open(index, base, forYouInsightsNow())
                    if (opened) {
                        activeArticle = index
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    }
                    wake()
                },
            )

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(safeTop.dp)
                    .background(ForYouInsightsTheme.Colors.page),
            )

            Box(
                Modifier
                    .fillMaxSize()
                    .background(ForYouInsightsTheme.Colors.page)
                    .graphicsLayer {
                        val progress = frame.value.progress
                        alpha = if (reducedMotion) {
                            ForYouInsightsMotion.clamp01(progress).toFloat()
                        } else {
                            ForYouInsightsMotion.smooth(
                                ForYouInsightsMotion.seg(
                                    progress,
                                    ForYouInsightsMotion.Reader.BACKDROP.first,
                                    ForYouInsightsMotion.Reader.BACKDROP.second,
                                ),
                            ).toFloat()
                        }
                    },
            )

            ForYouInsightsActor(
                assets = assets,
                actorIndex = { frame.value.actorIndex },
                actorFrame = actorProvider,
                modifier = Modifier.fillMaxSize(),
            )

            ForYouInsightsReader(
                articleIndex = activeArticle,
                frame = frame,
                scale = scale,
                top = heroY + heroHeight + ForYouInsightsTheme.Layout.READER_TITLE_GAP * scale,
                viewportHeight = (
                    height - heroY - heroHeight -
                        ForYouInsightsTheme.Layout.READER_TITLE_GAP * scale
                    ).coerceAtLeast(0.0),
                bottomInset = bottomInset,
                live = readerLive,
                reducedMotion = reducedMotion,
            )

            ForYouInsightsHeroDragTarget(
                articleIndex = activeArticle,
                live = readerLive,
                scale = scale,
                controller = controller,
                onDismiss = {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                },
                onUpdate = ::wake,
                modifier = Modifier
                    .offset(x = heroX.dp, y = heroY.dp)
                    .size(heroWidth.dp, heroHeight.dp),
            )

            ForYouInsightsCloseButton(
                title = ForYouInsightsContent.articles[activeArticle].title,
                frame = frame,
                reducedMotion = reducedMotion,
                scale = scale,
                left = heroX + ForYouInsightsTheme.Layout.CLOSE_INSET * scale - 4.0 * scale,
                top = heroY + ForYouInsightsTheme.Layout.CLOSE_INSET * scale - 4.0 * scale,
                enabled = readerLive,
                onClose = {
                    controller.close(forYouInsightsNow())
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    wake()
                },
            )

            ForYouInsightsNavigation(
                section = section,
                frame = frame,
                reducedMotion = reducedMotion,
                scale = scale,
                width = width,
                top = forYouInsightsY(ForYouInsightsTheme.Layout.TAB_BAR_TOP, safeTop, scale),
                enabled = !readerLive,
                onSelect = { next ->
                    if (next != section && !readerLive) {
                        section = next
                        val time = forYouInsightsNow()
                        controller.selectSection(next == ForYouInsightsSection.INSIGHTS, time)
                        controller.setLens(next.ordinal, animated = true, time = time)
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        wake()
                    }
                },
            )

            ForYouInsightsWarmLayers(
                assets = assets,
                modifier = Modifier
                    .offset(x = (-8).dp, y = (-8).dp)
                    .size(2.dp),
            )
        }
    }
}

private fun forYouInsightsActorFrame(
    frame: ForYouInsightsFrame,
    controller: ForYouInsightsController,
    quiet: Boolean,
    scale: Double,
    safeTop: Double,
    heroX: Double,
    heroY: Double,
    heroWidth: Double,
    heroHeight: Double,
): ForYouInsightsActorFrame {
    val progress = frame.progress
    val sourceX = ForYouInsightsTheme.Layout.ROW_IMAGE_X * scale
    val sourceY = safeTop +
        (controller.rowBase + ForYouInsightsTheme.Layout.ROW_PITCH * frame.actorIndex -
            ForYouInsightsTheme.REF_SAFE_TOP) * scale -
        controller.scrollY
    val sourceSize = ForYouInsightsTheme.Layout.ROW_IMAGE_SIZE * scale
    val movement = if (quiet) 1.0 else progress
    val tilt = ForYouInsightsMotion.tiltOf(progress, frame.driftTime, frame.drift)
    val touchX = frame.touchX / (ForYouInsightsMotion.Depth.TOUCH_TILT_RANGE * scale)
    val touchY = frame.touchY / (ForYouInsightsMotion.Depth.TOUCH_TILT_RANGE * scale)
    val press = 1.0 - (1.0 - ForYouInsightsMotion.PRESS_SCALE) * frame.press *
        (1.0 - ForYouInsightsMotion.smooth(ForYouInsightsMotion.seg(progress, 0.0, 0.5)))
    val flight = 1.0 + ForYouInsightsMotion.Depth.LIFT_SCALE *
        sin(PI * ForYouInsightsMotion.clamp01(progress))
    val pull = 1.0 - ForYouInsightsMotion.Dismiss.SCALE_LOSS *
        ForYouInsightsMotion.clamp01(
            frame.dragY / (ForYouInsightsMotion.Dismiss.DISTANCE * scale * 2.0),
        )
    return ForYouInsightsActorFrame(
        visible = if (quiet) {
            ForYouInsightsMotion.clamp01(progress)
        } else if (progress > 0.0005) {
            1.0
        } else {
            0.0
        },
        x = ForYouInsightsMotion.lerp(sourceX, heroX, movement),
        y = ForYouInsightsMotion.lerp(sourceY, heroY, movement) + frame.dragY,
        width = ForYouInsightsMotion.lerp(sourceSize, heroWidth, movement),
        height = ForYouInsightsMotion.lerp(sourceSize, heroHeight, movement),
        radius = ForYouInsightsMotion.lerp(
            ForYouInsightsTheme.Radius.IMAGE,
            ForYouInsightsTheme.Radius.HERO,
            ForYouInsightsMotion.clamp01(movement),
        ) * scale,
        scale = if (quiet) 1.0 else press * flight * pull,
        tiltX = if (quiet) {
            0.0
        } else {
            tilt.first - tanh(touchY) * ForYouInsightsMotion.Depth.TOUCH_TILT_X
        },
        tiltY = if (quiet) {
            0.0
        } else {
            tilt.second + tanh(touchX) * ForYouInsightsMotion.Depth.TOUCH_TILT_Y
        },
        lift = if (quiet) 0.0 else ForYouInsightsMotion.liftOf(progress),
        separation = if (quiet) 0.0 else ForYouInsightsMotion.separationOf(progress),
    )
}
