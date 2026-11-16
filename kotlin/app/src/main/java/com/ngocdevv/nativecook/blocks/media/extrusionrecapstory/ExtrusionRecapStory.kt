package com.ngocdevv.nativecook.blocks.media.extrusionrecapstory

import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ngocdevv.nativecook.blocks.media.extrusionrecapstory.ExtrusionRecapStoryMotion as M
import com.ngocdevv.nativecook.blocks.media.extrusionrecapstory.ExtrusionRecapStoryTheme as Theme
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Extrusion Recap Story: a full-screen story pager. A projected solid lofts from one silhouette to the next (a music recap of three
 * cards: a favourite song, 264 discoveries, a closing memory), its distance-field normal lighting the walls, the counter rolling up to 264
 * as its card lands. Tap either side to navigate, drag horizontally (300 design points per page), flick to commit, hold and drag to
 * aim the light.
 *
 * LOOP = 8 s ([ExtrusionRecapStoryMotion.LOOP]): the note rests, extrudes into the heart, rests, extrudes into the bolt, rests, back to the
 * note. A touch interrupts it; two seconds after the last touch it springs home and the film restarts. Autoplay is silent.
 */
@Composable
fun ExtrusionRecapStory(autoPlay: Boolean = false, onClose: (() -> Unit)? = null, previewTime: Double? = null) {
    val context = LocalContext.current
    val densityOwner = LocalDensity.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val reduced = rememberLiveReducedMotion()
    val vibe = remember(context) { ExtrusionRecapStoryHaptics.create(context) }
    val tick = remember { mutableIntStateOf(0) }
    var foreground by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var screenReader by remember { mutableStateOf(false) }
    var activePage by remember { mutableIntStateOf(0) }
    var paused by remember { mutableStateOf(false) }
    BlockSystemBars(darkIcons = false)

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            foreground = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    DisposableEffect(context) {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        screenReader = am?.isTouchExplorationEnabled == true
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { screenReader = it }
        am?.addTouchExplorationStateChangeListener(listener)
        onDispose { am?.removeTouchExplorationStateChangeListener(listener) }
    }

    val pages = Theme.RECAP
    // the glyph distance fields decode off the main thread; nothing draws until they are in
    val atlas by produceState<android.graphics.Bitmap?>(null, pages) {
        value = withContext(Dispatchers.Default) { ExtrusionRecapStorySdf.createAtlas(pages) }
    }
    val fontScale = densityOwner.fontScale.toDouble()
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val close = onClose ?: { backDispatcher?.onBackPressed() }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color(Theme.PAGE))) {
        val w = maxWidth.value.toDouble()
        val h = maxHeight.value.toDouble()
        val insetTop = with(densityOwner) { WindowInsets.statusBars.getTop(densityOwner).toDp().value }.toDouble()
        val insetBottom = with(densityOwner) { WindowInsets.navigationBars.getBottom(densityOwner).toDp().value }.toDouble()
        val layout = remember(w, h, insetTop, insetBottom) { Theme.makeLayout(w, h, insetTop, insetBottom) }
        val engine = remember(layout, autoPlay, reduced) { ExtrusionRecapStoryEngine(pages, autoPlay, reduced, layout.s, w) }
        val renderer = remember(layout, engine, atlas, fontScale) {
            ExtrusionRecapStoryRenderer(context.assets, pages, atlas, layout, w, h, fontScale, hasClose = true, hasPlay = autoPlay)
        }
        SideEffect {
            engine.onCue = { cue -> vibe.emit(cue) }
            engine.onIndex = { activePage = it }
            engine.foreground = foreground
            engine.screenReader = screenReader
            engine.paused = paused
        }
        val dens = densityOwner.density

        LaunchedEffect(engine, atlas, previewTime) {
            if (atlas == null) return@LaunchedEffect
            var previous = 0L
            if (previewTime != null) engine.pin(previewTime)
            while (true) {
                withFrameNanos { nanos ->
                    val dtMs = if (previous == 0L) 16.67 else min((nanos - previous) / 1e6, 50.0)
                    previous = nanos
                    engine.frame(dtMs)
                    // Read only inside the Canvas below: invalidates drawing, not the screen's composition.
                    tick.intValue++
                }
            }
        }

        Canvas(Modifier.fillMaxSize()) {
            tick.intValue
            val c = drawContext.canvas.nativeCanvas
            val scale = drawContext.density.density
            c.save()
            c.scale(scale, scale)
            renderer.draw(c, engine, scale)
            c.restore()
        }

        // one pointer handler: the header's buttons and the peek card are presses, everything else is the pan
        Box(
            Modifier.fillMaxSize().pointerInput(engine, renderer, dens, w) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val x0 = (down.position.x / dens).toDouble()
                    val y0 = (down.position.y / dens).toDouble()
                    val hit = renderer.hit(x0, y0, engine)
                    down.consume()
                    if (hit != ExtrusionRecapStoryHit.NONE) {
                        val rect = when (hit) {
                            ExtrusionRecapStoryHit.CLOSE -> renderer.closeRect
                            ExtrusionRecapStoryHit.PLAY -> renderer.playRect
                            else -> renderer.peekRect
                        }
                        var inside = true
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            inside = rect.contains(change.position.x / dens, change.position.y / dens)
                            change.consume()
                            if (!change.pressed) break
                        }
                        if (inside) when (hit) {
                            ExtrusionRecapStoryHit.CLOSE -> close()
                            ExtrusionRecapStoryHit.PLAY -> if (!reduced && !screenReader) paused = !paused
                            else -> engine.advance()
                        }
                    } else {
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        engine.panBegin()
                        var x = x0
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                tracker.addPosition(change.uptimeMillis, change.position)
                                x = (change.position.x / dens).toDouble()
                                val y = (change.position.y / dens).toDouble()
                                val v = tracker.calculateVelocity()
                                if (!change.pressed) {
                                    change.consume()
                                    engine.panEnd(x - x0, (v.x / dens).toDouble(), x)
                                    break
                                }
                                engine.panUpdate(x - x0, y - y0, (v.x / dens).toDouble())
                                change.consume()
                            }
                        } finally {
                            engine.panFinalize()
                        }
                    }
                }
            },
        )

        // accessibility: the story as one adjustable control, the peek card, the close and play buttons
        val page = pages[activePage.coerceIn(0, pages.size - 1)]
        Box(
            Modifier
                .fillMaxSize()
                .semantics {
                    contentDescription = listOfNotNull(page.stat?.toString(), page.headline.replace("\n", " "), page.caption).joinToString(". ") +
                        ". Swipe left for the next page. Hold and drag to aim the light."
                    stateDescription = "Page ${activePage + 1} of ${pages.size}"
                    customActions = listOf(
                        CustomAccessibilityAction(Theme.NEXT) { engine.changePage(1); true },
                        CustomAccessibilityAction(Theme.PREVIOUS) { engine.changePage(-1); true },
                    )
                },
        )
        Box(
            Modifier
                .offset(0.dp, (layout.cardBottom + Theme.GAP * layout.s).dp)
                .size(w.dp, (h - layout.cardBottom - Theme.GAP * layout.s).dp)
                .semantics {
                    role = Role.Button
                    contentDescription = "${if (activePage == pages.size - 1) Theme.REPLAY else Theme.UP_NEXT}: " +
                        pages[(activePage + 1) % pages.size].let { (it.stat?.let { v -> "$v " } ?: "") + it.headline.replace("\n", " ") }
                    onClick { engine.advance(); true }
                },
        )
        Box(
            Modifier
                .offset((Theme.EDGE * layout.s - 12 * layout.s).dp, layout.headerY.dp)
                .size(44.dp, 44.dp)
                .semantics {
                    role = Role.Button
                    contentDescription = Theme.CLOSE
                    onClick { close(); true }
                },
        )
        if (autoPlay) {
            Box(
                Modifier
                    .offset((w - Theme.EDGE * layout.s - 44).dp, (layout.headerY + 44).dp)
                    .size(44.dp, 44.dp)
                    .semantics {
                        role = Role.Button
                        contentDescription = if (reduced || screenReader) "Animated playback disabled by accessibility settings" else if (paused) "Play recap" else "Pause recap"
                        onClick { if (!reduced && !screenReader) paused = !paused; true }
                    },
            )
        }
    }
}
