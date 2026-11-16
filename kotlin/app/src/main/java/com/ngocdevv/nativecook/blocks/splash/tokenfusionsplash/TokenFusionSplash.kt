package com.ngocdevv.nativecook.blocks.splash.tokenfusionsplash

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ngocdevv.nativecook.blocks.splash.tokenfusionsplash.TokenFusionSplashTheme as Theme
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Token Fusion Splash: a live signed-distance splash. Four tokens orbit a violet core, are drawn in one by one through necks that
 * stretch, thin and snap, and their dyes stir into the gel; the body floods the screen, the mark is cut out of the flood, and the
 * wordmark lands. Drag a token toward the core (it is pulled in and swallowed, with a ring through the body), drag the core itself,
 * throw a token and it is caught or flies back.
 *
 * LOOP = 7.5 s ([TokenFusionSplashMotion.LOOP]): gather .4-1, captures 2 / 2.55 / 2.95 / 3.28, flood 3.75-4.6, mark 4.18-4.92, wordmark
 * 4.96-5.2, brand to 5.7, recede to 7, seam to 7.5. With `autoPlay` in `interactive` mode a touch takes over and two seconds after it
 * leaves the scene eases back to the first frame and the loop restarts; in `splash` mode the story plays once and stops on the brand.
 * `idleMs`: when set (and not on autoPlay) the flood starts by itself after that long without a touch. Reduce Motion: no physics, the
 * canvas fades through the state change. `previewTime` pins the loop at that second (checks, previews).
 */
@Composable
fun TokenFusionSplash(
    autoPlay: Boolean = false,
    splash: Boolean = false,
    idleMs: Double? = 5000.0,
    touchCore: Boolean = true,
    previewTime: Double? = null,
    onMerge: ((String, List<String>) -> Unit)? = null,
    onRelease: ((String) -> Unit)? = null,
    onComplete: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val densityOwner = LocalDensity.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val reduced = rememberLiveReducedMotion()
    val vibe = remember(context) { TokenFusionSplashHaptics.create(context) }
    val tick = remember { mutableIntStateOf(0) }
    var active by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    BlockSystemBars(darkIcons = false)

    // the glyph atlas is rasterised and distance-transformed off the main thread; nothing draws until it is in
    val renderer by produceState<TokenFusionSplashRenderer?>(null, context.assets) {
        value = withContext(Dispatchers.Default) { TokenFusionSplashRenderer(context.assets) }
    }

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            active = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    val engine = remember(autoPlay, splash, reduced, idleMs, touchCore) {
        TokenFusionSplashEngine(autoPlay, splash, reduced, idleMs, touchCore)
    }
    SideEffect {
        engine.cue = { cue -> vibe.emit(cue) }
        engine.onMerge = onMerge
        engine.onRelease = onRelease
        engine.onComplete = onComplete
    }
    LaunchedEffect(engine) {
        if (reduced && splash) {
            delay(240)
            engine.reducedSplash()
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color(Theme.pageInt))) {
        val w = maxWidth.value.toDouble()
        val h = maxHeight.value.toDouble()
        val insetTop = with(densityOwner) { WindowInsets.statusBars.getTop(densityOwner).toDp().value }.toDouble()
        val insetBottom = with(densityOwner) { WindowInsets.navigationBars.getBottom(densityOwner).toDp().value }.toDouble()
        val layout = remember(w, h, insetTop, insetBottom) { Theme.Layout(w, h, insetTop, insetBottom) }
        SideEffect { engine.applyLayout(layout, insetTop, insetBottom, w, h) }
        val dens = densityOwner.density.toDouble()

        LaunchedEffect(engine, active, previewTime) {
            if (!active) return@LaunchedEffect
            var previous = 0L
            while (true) {
                withFrameNanos { nanos ->
                    val dt = if (previous == 0L) 0.016 else min((nanos - previous) / 1e9, 0.05)
                    previous = nanos
                    if (previewTime != null) {
                        engine.frameAt(previewTime)
                        tick.intValue++
                    } else if (engine.frame(dt)) {
                        // Read only inside the Canvas below: invalidates drawing, not the screen's composition.
                        tick.intValue++
                    }
                }
            }
        }

        Canvas(
            Modifier.fillMaxSize()
                .semantics {
                    contentDescription = Theme.CORE_LABEL
                    customActions = Theme.TOKENS.mapIndexed { i, token ->
                        CustomAccessibilityAction("Add ${token.label}") {
                            engine.merge(i)
                            true
                        }
                    } + CustomAccessibilityAction(Theme.SKIP) {
                        engine.skip()
                        true
                    }
                }
                .pointerInput(engine, dens) {
                    awaitEachGesture {
                        val tracking = HashSet<Long>()
                        val first = awaitFirstDown(requireUnconsumed = false)
                        if (!engine.inGestureStrip(first.position.x / dens, first.position.y / dens)) {
                            tracking.add(first.id.value)
                            engine.touchDown(first.id.value.toInt(), first.position.x / dens, first.position.y / dens)
                            first.consume()
                            tick.intValue++
                        }
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                for (ch in event.changes) {
                                    val id = ch.id.value
                                    val x = ch.position.x / dens
                                    val y = ch.position.y / dens
                                    if (ch.changedToDown() && id != first.id.value) {
                                        if (!engine.inGestureStrip(x, y)) {
                                            tracking.add(id)
                                            engine.touchDown(id.toInt(), x, y)
                                            ch.consume()
                                        }
                                    } else if (ch.changedToUp()) {
                                        if (tracking.remove(id)) engine.touchUp(id.toInt(), x, y)
                                        ch.consume()
                                    } else if (ch.pressed && id in tracking) {
                                        engine.touchMove(id.toInt(), x, y)
                                        ch.consume()
                                    }
                                }
                                tick.intValue++
                                if (event.changes.none { it.pressed }) break
                            }
                        } finally {
                            for (id in tracking) engine.touchCancel(id.toInt())
                        }
                    }
                },
        ) {
            tick.intValue
            val r = renderer ?: return@Canvas
            val c = drawContext.canvas.nativeCanvas
            val scale = drawContext.density.density
            c.save()
            c.scale(scale, scale)
            r.draw(c, size.width / scale, size.height / scale, engine.scene, engine.lagTwo, engine.layout, engine.opacity.value.toFloat())
            c.restore()
        }
    }
}
