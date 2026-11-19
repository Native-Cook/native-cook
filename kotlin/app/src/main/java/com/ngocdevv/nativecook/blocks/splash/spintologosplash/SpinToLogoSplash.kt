package com.ngocdevv.nativecook.blocks.splash.spintologosplash

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion
import kotlin.math.abs
import kotlin.math.roundToInt

// Spin to Logo Splash: a 14-second film of an onboarding splash for an invented money app, Arca. Its 3D mark (three
// nested arches and a terracotta pearl, the React Native block's own arca.glb rendered by Filament) stands on warm
// paper; a ghost finger gives it a full turn while a track on the floor fills; the arches fold flat into the brand
// mark, the flat 2D logo takes over, rises, and the welcome screen fades up. Drag sideways to turn it yourself; Skip
// folds it at once. A port of SpinToLogoSplash.tsx: every frame is a function of the loop time (SpinToLogoSplashMotion).

private const val GUTTER = 24
private const val WORD = "arca"

@Composable
fun SpinToLogoSplash(autoPlay: Boolean = true) {
    val reduced = rememberLiveReducedMotion()
    BlockSystemBars(darkIcons = true)
    // a change of the preference restarts the block in its new state
    androidx.compose.runtime.key(reduced, autoPlay) {
        SpinScreen(film = !reduced && autoPlay, reduced = reduced)
    }
}

@Composable
private fun SpinScreen(film: Boolean, reduced: Boolean) {
    val view = LocalView.current
    val density = LocalDensity.current
    val controller = remember { SpinController(film, reduced) }
    val host = remember { F3Host<SpinWorld>() }
    val tState = remember { mutableFloatStateOf(controller.t) }
    val yawState = remember { mutableFloatStateOf(0f) }
    val liveState = remember { mutableFloatStateOf(controller.live) }

    controller.onHaptic = { detent ->
        if (detent) {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        } else {
            view.performHapticFeedback(
                if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS,
            )
        }
    }

    LaunchedEffect(controller) {
        while (true) {
            withFrameNanos { now ->
                controller.frame(now, host.ready)
                val t = controller.t
                val yaw = controller.yaw
                tState.floatValue = t
                yawState.floatValue = yaw
                liveState.floatValue = controller.live
                val vis = clamp01(stageVisible(t))
                host.view?.alpha = vis
                if (vis > 0.001f) host.frame(now) { it.pose(t, yaw) }
            }
        }
    }

    val topInset = with(density) { WindowInsets.statusBars.getTop(this).toDp().value }
    val bottomInset = with(density) { WindowInsets.navigationBars.getBottom(this).toDp().value }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color(PAPER_BOTTOM))) {
        val w = maxWidth.value
        val h = maxHeight.value
        val compact = h < 760f
        val finalBase = topInset + if (compact) 96f else 132f
        val floor = remember(w, h) { SpinFloorPainter(w, h) }
        val mark = remember(w, h, finalBase) { SpinMarkPainter(w, h, finalBase) }
        val trackFront = remember(w, h) {
            val c = FloatArray(6)
            cameraAt(0f, c)
            val q = FloatArray(3)
            project(0f, 0f, TRACK_R, c, w, h, q)
            q[1]
        }

        Canvas(Modifier.fillMaxSize()) {
            drawIntoCanvas {
                val nc = it.nativeCanvas
                nc.save()
                nc.scale(density.density, density.density)
                floor.draw(nc, tState.floatValue, yawState.floatValue)
                nc.restore()
            }
        }
        F3Stage(host, ::loadSpinAssets, ::SpinWorld, Modifier.fillMaxSize())
        Canvas(Modifier.fillMaxSize()) {
            drawIntoCanvas {
                val nc = it.nativeCanvas
                nc.save()
                nc.scale(density.density, density.density)
                mark.draw(nc, tState.floatValue, liveState.floatValue)
                nc.restore()
            }
        }
        // the pan: anywhere on the screen (the overlay's buttons sit above it)
        Box(
            Modifier.fillMaxSize().pointerInput(controller) {
                val slop = 4.dp.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    controller.touch()
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    var total = 0f
                    var started = false
                    var startX = down.position.x
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        tracker.addPosition(change.uptimeMillis, change.position)
                        if (!change.pressed) break
                        total += abs(change.positionChange().x) + abs(change.positionChange().y)
                        if (!started && total > slop) {
                            started = true
                            startX = change.position.x
                            controller.dragStart()
                        }
                        if (started) {
                            controller.dragUpdate((change.position.x - startX) / density.density)
                            change.consume()
                        }
                    }
                    if (started) controller.dragEnd(tracker.calculateVelocity().x / density.density)
                }
            },
        )
        SpinOverlay(
            tState = tState, yawState = yawState, top = topInset, bottom = bottomInset, compact = compact,
            reduced = reduced, finalBase = finalBase, trackFront = trackFront,
            onSkip = { controller.skip() },
            onStart = { view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY) },
            onSignIn = { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) },
        )
    }
}

private val titleStyle = TextStyle(
    color = Color(INK), fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp,
)
private val bodyStyle = TextStyle(color = Color(MUTE), fontSize = 17.sp, lineHeight = 23.sp)

@Composable
private fun SpinOverlay(
    tState: androidx.compose.runtime.FloatState,
    yawState: androidx.compose.runtime.FloatState,
    top: Float,
    bottom: Float,
    compact: Boolean,
    reduced: Boolean,
    finalBase: Float,
    trackFront: Float,
    onSkip: () -> Unit,
    onStart: () -> Unit,
    onSignIn: () -> Unit,
) {
    val title = if (compact) titleStyle.copy(fontSize = 30.sp, lineHeight = 35.sp) else titleStyle
    val posterOn by remember { derivedStateOf { posterUi(tState.floatValue) > 0.5f } }
    val welcomeOn by remember { derivedStateOf { welcomeItem(tState.floatValue, 2) > 0.5f } }
    val degrees by remember { derivedStateOf { (trackFill(tState.floatValue, yawState.floatValue) * 360).roundToInt() } }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .padding(start = GUTTER.dp, end = GUTTER.dp)
                .offset(y = (top + if (compact) 20 else 36).dp)
                .graphicsLayer {
                    val k = clamp01(posterUi(tState.floatValue))
                    alpha = k
                    translationY = (1 - k) * -8 * density
                },
        ) {
            Text("Give it a full turn.", style = title)
            Text(
                "Drag the sculpture sideways. Arca opens when the circle closes.",
                style = bodyStyle, modifier = Modifier.padding(top = 10.dp).widthIn(max = 340.dp),
            )
        }
        Text(
            "$degrees°",
            style = TextStyle(
                color = Color(INK), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                fontFeatureSettings = "tnum",
            ),
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = (trackFront + if (compact) 14 else 22).dp)
                .graphicsLayer { alpha = clamp01(posterUi(tState.floatValue)) },
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = (bottom + 12).dp)
                .graphicsLayer { alpha = clamp01(posterUi(tState.floatValue)) }
                .let { if (posterOn) it.clickable(interactionSource = null, indication = null, onClick = onSkip) else it },
        ) {
            Text(
                "Skip", color = Color(MUTE), fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }

        // the welcome screen
        Row(
            Modifier.fillMaxWidth().offset(y = (finalBase + 10).dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            WORD.forEachIndexed { i, ch ->
                Text(
                    ch.toString(),
                    style = TextStyle(
                        color = Color(INK), fontSize = 40.sp, lineHeight = 46.sp, fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-1).sp,
                    ),
                    modifier = Modifier.graphicsLayer {
                        val k = wordLetter(tState.floatValue, i)
                        alpha = k
                        translationY = (1 - k) * 14 * density
                    },
                )
            }
        }
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = GUTTER.dp, end = GUTTER.dp, bottom = (bottom + 12).dp),
        ) {
            WelcomeItem(0, tState) { Text("Your money,\nin order.", style = title) }
            WelcomeItem(1, tState, Modifier.padding(top = 2.dp)) {
                Text(
                    "Accounts, bills and savings goals in one calm place. Set up takes two minutes.",
                    style = bodyStyle, modifier = Modifier.padding(top = 10.dp).widthIn(max = 340.dp),
                )
            }
            WelcomeItem(2, tState, Modifier.padding(top = if (compact) 22.dp else 32.dp)) {
                PressButton(
                    enabled = welcomeOn, reduced = reduced, scale = 0.96f, onClick = onStart,
                    modifier = Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(28.dp)).background(Color(INK)),
                ) {
                    Text("Get started", color = Color(PAPER_TOP), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            WelcomeItem(3, tState) {
                PressButton(
                    enabled = welcomeOn, reduced = reduced, scale = 0.98f, onClick = onSignIn,
                    modifier = Modifier.padding(top = 8.dp).fillMaxWidth().height(48.dp),
                ) {
                    Text("I already have an account", color = Color(INK), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun WelcomeItem(
    j: Int,
    tState: androidx.compose.runtime.FloatState,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier.graphicsLayer {
            val k = welcomeItem(tState.floatValue, j)
            alpha = k
            translationY = (1 - k) * 18 * density
        },
    ) { content() }
}

/** PressScale: 0.96 in 150 ms on a strong ease-out; under reduced motion the press dims instead. */
@Composable
private fun PressButton(
    enabled: Boolean,
    reduced: Boolean,
    scale: Float,
    onClick: () -> Unit,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val k by animateFloatAsState(if (pressed && enabled) 1f else 0f, tween(150, easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)), label = "press")
    Box(
        Modifier
            .graphicsLayer {
                if (reduced) alpha = 1f - 0.28f * k else {
                    scaleX = 1f - (1f - scale) * k; scaleY = scaleX
                }
            }
            .then(modifier)
            .clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}
