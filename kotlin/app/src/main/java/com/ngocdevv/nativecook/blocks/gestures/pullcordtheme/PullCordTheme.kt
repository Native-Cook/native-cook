package com.ngocdevv.nativecook.blocks.gestures.pullcordtheme

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.ngocdevv.nativecook.blocks.gestures.pullcordtheme.PullCordThemeTheme.Copy
import com.ngocdevv.nativecook.blocks.gestures.pullcordtheme.PullCordThemeTheme.Cord
import com.ngocdevv.nativecook.blocks.gestures.pullcordtheme.PullCordThemeTheme.Type
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Pull Cord Theme: a hanging Edison bulb with a ceiling pull cord. Pulling the brass pull fills the glass with
 * warm light like a liquid; at the click a warm room grows out of the bulb as a circle and the text recolours as
 * the front passes it. Pulling again drains the light back and the blue filament re-ignites.
 * The design is from a Pinterest pin; the motion is ours. Full screen: it draws its own background.
 *
 * With [autoPlay] a 12 s script pulls the cord by itself and ends exactly at rest; a touch interrupts it and it
 * resumes 3.5 s after the last touch.
 */
@Composable
fun PullCordTheme(autoPlay: Boolean = false, modifier: Modifier = Modifier) {
    val reduced = rememberReducedMotion()
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current
    val currentAutoPlay by rememberUpdatedState(autoPlay)
    val textMeasurer = rememberTextMeasurer()
    val tilt = rememberPullCordThemeTilt(enabled = !reduced)
    var light by remember { mutableStateOf(false) }
    // Bumped once per frame: the draw lambda reads it, so a new frame redraws without composing again.
    val frameTick = remember { mutableLongStateOf(0L) }
    val bottomInset = WindowInsets.safeDrawing.getBottom(density) / density.density

    // The status bar's icons follow the committed theme: light icons on the dark room, dark icons on the light one.
    val window = LocalActivity.current?.window
    DisposableEffect(window, view, light) {
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = light
        onDispose { if (previous != null) controller.isAppearanceLightStatusBars = previous }
    }

    BoxWithConstraints(modifier.fillMaxSize().background(PullCordThemeTheme.DARK.wallTop)) {
        val g = remember(maxWidth, maxHeight) { PullCordThemeGeometry(maxWidth.value.toDouble(), maxHeight.value.toDouble()) }
        // The cord settles under gravity in the sim's constructor: once, not on every frame.
        val sim = remember(g) { PullCordThemeSim(g, reduced, autoPlay) }
        val frame = remember(g) { PullCordThemeFrame(g) }
        val scene = remember(g) { PullCordThemeScene(g) }
        val copy = remember(g, density, bottomInset) {
            PullCordThemeText(textMeasurer, g, density.density, bottomInset.toDouble())
        }
        val assets by produceState<PullCordThemeAssets?>(null, g, density.density) {
            value = withContext(Dispatchers.Default) { PullCordThemeAssets.load(context, g, density.density) }
        }

        LaunchedEffect(sim) {
            var previousNanos = 0L
            while (true) {
                withFrameNanos { now ->
                    // The first frame only sets the clock: Reanimated's frame callback skips it as well.
                    if (previousNanos != 0L) {
                        val elapsed = (now - previousNanos) / 1_000_000_000.0
                        sim.reduced = reduced
                        sim.step(elapsed * PullCordThemeMotion.QA_SPEED, currentAutoPlay)
                        if (frame.publish(sim, tilt.roll)) light = frame.themeNow == 1
                        for (kind in sim.events) view.performPullCordThemeHaptic(kind)
                        sim.events.clear()
                        frameTick.longValue++
                    }
                    previousNanos = now
                }
            }
        }

        // The whole screen takes the finger: the bead is pulled, the bulb is thrown, anywhere else does nothing.
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(sim, density.density) {
                    val dp = density.density.toDouble()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val velocity = VelocityTracker()
                        velocity.addPosition(down.uptimeMillis, down.position)
                        sim.down(down.position.x / dp, down.position.y / dp)
                        var finished = false
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                velocity.addPosition(change.uptimeMillis, change.position)
                                if (!change.pressed) {
                                    val v = velocity.calculateVelocity()
                                    sim.up(v.x / dp, v.y / dp)
                                    finished = true
                                    change.consume()
                                    break
                                }
                                sim.move(change.position.x / dp, change.position.y / dp)
                                change.consume()
                            }
                        } finally {
                            if (!finished) sim.up(0.0, 0.0, cancelled = true)
                        }
                    }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                frameTick.longValue
                drawIntoCanvas { canvas ->
                    val native = canvas.nativeCanvas
                    native.save()
                    native.scale(density.density, density.density)
                    scene.draw(native, frame, assets)
                    native.restore()
                }
                copy.draw(this, frame)
            }

            // The title as one heading for a screen reader: "Dark theme" or "Light theme".
            val titleTop = (g.h - bottomInset - Type.bottom - Type.hintLine - Type.gap - Type.titleLine).toFloat()
            Box(
                Modifier
                    .offset(Type.margin.dp, titleTop.dp)
                    .width((g.w - Type.margin * 2).dp)
                    .height(Type.titleLine.dp)
                    .semantics(mergeDescendants = true) {
                        heading()
                        contentDescription = "${if (light) Copy.light else Copy.dark} theme"
                    },
            )

            // The cord's touch target, as a switch with an activate action.
            Box(
                Modifier
                    .offset((g.anchorX - Cord.hit * g.s / 2).dp, (g.restY - 8 * g.s).dp)
                    .width((Cord.hit * g.s).dp)
                    .height(((Cord.beadHeight + 16) * g.s).dp)
                    .semantics {
                        role = Role.Switch
                        contentDescription = Copy.a11yLabel
                        stateDescription = Copy.a11yHint
                        toggleableState = if (light) ToggleableState.On else ToggleableState.Off
                        onClick("activate") {
                            sim.requestToggle()
                            true
                        }
                    },
            )
        }
    }
}
