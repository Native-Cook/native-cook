package com.ngocdevv.nativecook.blocks.pickers.neoncontourmood

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ngocdevv.nativecook.blocks.pickers.neoncontourmood.NeonContourMoodMotion as M
import com.ngocdevv.nativecook.blocks.pickers.neoncontourmood.NeonContourMoodTheme as Theme
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Neon Contour Mood: a market-mood check-in. One shader field of seven nested neon contours that reshape into each mood's word
 * (PANIC .. MANIA) and its tag; drag the slider (or the light itself) and the outer rings keep the moods of a moment ago; hold and they
 * nest. A tap on the light sends a pulse outward.
 *
 * LOOP = 8 s ([NeonContourMoodMotion.LOOP]): the finger drags to between two moods, parks, flicks to the last, lets go and returns.
 * A touch interrupts it; two seconds after the last touch it settles and the loop restarts. Autoplay is silent.
 */
@Suppress("DEPRECATION")
@Composable
fun NeonContourMood(autoPlay: Boolean = false, haptics: Boolean = true, sound: Boolean = false, previewTime: Double? = null) {
    val context = LocalContext.current
    val densityOwner = LocalDensity.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val view = LocalView.current
    val reduced = rememberLiveReducedMotion()
    val vibe = remember(context) { NeonContourMoodHaptics.create(context) }
    val audio = remember(context, sound) { if (sound) NeonContourMoodAudio(context) else null }
    DisposableEffect(audio) { onDispose { audio?.release() } }
    val tick = remember { mutableIntStateOf(0) }
    var active by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var selected by remember { mutableIntStateOf(Math.round(Theme.MOODS.size.let { M.PATH_REST * (it - 1) / 6.0 }).toInt()) }
    BlockSystemBars(darkIcons = false)

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            active = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    val moods = Theme.MOODS
    val n = moods.size
    val last = n - 1
    val pack = remember { NeonContourMoodPack.of(moods) }
    val fontScale = densityOwner.fontScale.toDouble()
    val restMood = M.PATH_REST * last / 6.0
    val table = remember { M.buildCrossings(n) }
    val lens = remember { IntArray(n) { moods[it].word.length } }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color(Theme.PAGE))) {
        val w = maxWidth.value.toDouble()
        val h = maxHeight.value.toDouble()
        val insetTop = with(densityOwner) { WindowInsets.statusBars.getTop(densityOwner).toDp().value }.toDouble()
        val insetBottom = with(densityOwner) { WindowInsets.navigationBars.getBottom(densityOwner).toDp().value }.toDouble()
        val layout = remember(w, h, insetTop, insetBottom, fontScale) { Theme.makeLayout(w, h, insetTop, insetBottom, fontScale) }
        val renderer = remember(layout, context) { NeonContourMoodRenderer(context.assets, moods, pack, layout) }
        val cfg = remember(layout, reduced, renderer) {
            NeonContourMoodCfg(
                n, M.ECHO_MS, 7.0, 26.0, true, reduced, w / 2, layout.heroY, layout.heroS,
                layout.tagY + (Theme.HERO_CUTOFF - Theme.TAG_CENTER_Y) * layout.s, Theme.HERO_FADE * layout.s,
                floatArrayOf(((Theme.PAGE shr 16) and 255) / 255f, ((Theme.PAGE shr 8) and 255) / 255f, (Theme.PAGE and 255) / 255f),
                floatArrayOf(((Theme.CORE shr 16) and 255) / 255f, ((Theme.CORE shr 8) and 255) / 255f, (Theme.CORE and 255) / 255f),
                pack, renderer.tagWidths, lens, table,
            )
        }
        val engine = remember(cfg, autoPlay) {
            NeonContourMoodEngine(autoPlay, reduced, cfg, restMood, restMood, layout.s, layout)
        }
        SideEffect {
            engine.onBuzz = { cue, weight -> if (haptics) vibe.emit(cue, weight) }
            engine.onSelect = { index, live ->
                selected = index
                if (live) {
                    if (haptics) vibe.emit(NeonContourMoodEngine.CUE_DETENT, M.hapticWeight(index, n))
                    if (sound) audio?.strike(index, n)
                }
            }
        }
        val dens = densityOwner.density

        LaunchedEffect(engine, active, previewTime) {
            if (!active) return@LaunchedEffect
            var previous = 0L
            if (previewTime != null) engine.pin(previewTime)
            while (true) {
                withFrameNanos { nanos ->
                    val dt = if (previous == 0L) 1.0 / 60 else min((nanos - previous) / 1e9, 0.05)
                    previous = nanos
                    engine.frame(nanos / 1e9, dt)
                    // Read only inside the Canvas below: invalidates drawing, not the screen's composition.
                    tick.intValue++
                }
            }
        }

        val scroll = rememberScrollState()
        androidx.compose.foundation.layout.Column(Modifier.fillMaxSize().verticalScroll(scroll, enabled = layout.h > h + 1)) {
            Box(Modifier.fillMaxWidth().height(layout.h.dp)) {
                Canvas(Modifier.fillMaxSize()) {
                    tick.intValue
                    val c = drawContext.canvas.nativeCanvas
                    val scale = drawContext.density.density
                    c.save()
                    c.scale(scale, scale)
                    renderer.draw(c, engine, scale)
                    c.restore()
                }

                // one pointer handler: the slider, the light, the close mark and the button
                Box(
                    Modifier.fillMaxSize().pointerInput(engine, renderer, dens) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val x0 = (down.position.x / dens).toDouble()
                            val y0 = (down.position.y / dens).toDouble()
                            val hit = renderer.hit(x0, y0)
                            if (hit == NeonContourMoodHit.NONE) return@awaitEachGesture
                            down.consume()
                            when (hit) {
                                NeonContourMoodHit.NEXT, NeonContourMoodHit.CLOSE -> {
                                    val isNext = hit == NeonContourMoodHit.NEXT
                                    if (isNext) engine.buttonDown() else engine.closeDown()
                                    var inside = true
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                        val px = change.position.x / dens
                                        val py = change.position.y / dens
                                        inside = renderer.hit(px.toDouble(), py.toDouble()) == hit
                                        change.consume()
                                        if (!change.pressed) break
                                    }
                                    if (isNext) engine.buttonUp() else engine.closeUp()
                                    if (inside) {
                                        if (isNext) {
                                            val index = engine.next()
                                            view.announceForAccessibility("${moods[index].word.lowercase()} selected")
                                        } else engine.close()
                                    }
                                }
                                NeonContourMoodHit.SLIDER -> {
                                    val tracker = VelocityTracker()
                                    tracker.addPosition(down.uptimeMillis, down.position)
                                    engine.sliderBegin(x0, y0)
                                    try {
                                        var lastX = x0
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                            tracker.addPosition(change.uptimeMillis, change.position)
                                            lastX = (change.position.x / dens).toDouble()
                                            if (!change.pressed) {
                                                change.consume()
                                                engine.sliderEnd(lastX, (tracker.calculateVelocity().x / dens).toDouble())
                                                break
                                            }
                                            engine.sliderUpdate(lastX)
                                            change.consume()
                                        }
                                    } finally {
                                        engine.sliderFinalize()
                                    }
                                }
                                else -> {
                                    // the light: a pan (after 6 dp) scrubs the mood and squeezes the rings, a short tap sends a pulse
                                    val tracker = VelocityTracker()
                                    tracker.addPosition(down.uptimeMillis, down.position)
                                    engine.heroBegin(x0, y0)
                                    engine.heroTapBegin()
                                    var panning = false
                                    var tapOk = true
                                    try {
                                        var lastX = x0
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                            tracker.addPosition(change.uptimeMillis, change.position)
                                            lastX = (change.position.x / dens).toDouble()
                                            val ly = (change.position.y / dens).toDouble()
                                            val dist = max(abs(lastX - x0), abs(ly - y0))
                                            if (dist > 10) tapOk = false
                                            if (!panning && dist > 6) panning = true
                                            if (!change.pressed) {
                                                change.consume()
                                                if (panning) engine.heroEnd(lastX, (tracker.calculateVelocity().x / dens).toDouble())
                                                else if (tapOk) engine.heroTapEnd()
                                                break
                                            }
                                            if (panning) engine.heroUpdate(lastX, ly)
                                            change.consume()
                                        }
                                    } finally {
                                        engine.heroFinalize()
                                    }
                                }
                            }
                        }
                    },
                )

                // accessibility: the title, the close button, the slider as one adjustable control, the button
                Box(
                    Modifier
                        .offset((48 * layout.s).dp, (layout.titleY - 24 * layout.s).dp)
                        .size((layout.w - 108 * layout.s).dp, (28 * layout.s).dp)
                        .semantics { heading(); contentDescription = Theme.TITLE },
                )
                Box(
                    Modifier
                        .offset((layout.closeX - 2 * layout.s).dp, (layout.closeY - 2 * layout.s).dp)
                        .size(max(48.0, 48 * layout.s).dp, max(48.0, 48 * layout.s).dp)
                        .semantics {
                            role = Role.Button
                            contentDescription = "Close ${Theme.TITLE.lowercase()}"
                            onClick { engine.close(); true }
                        },
                )
                Box(
                    Modifier
                        .offset(layout.sliderX.dp, (layout.sliderY - 4).dp)
                        .size(layout.sliderW.dp, (layout.sliderH + 8).dp)
                        .semantics {
                            contentDescription = Theme.QUESTION
                            stateDescription = "${moods[selected].word.lowercase()}, ${selected + 1} of $n"
                            customActions = listOf(
                                CustomAccessibilityAction("Increase") { engine.selectStop(min(last, selected + 1)); true },
                                CustomAccessibilityAction("Decrease") { engine.selectStop(max(0, selected - 1)); true },
                            )
                        },
                )
                Box(
                    Modifier
                        .offset((Theme.BUTTON_X * layout.s).dp, layout.buttonY.dp)
                        .size((Theme.BUTTON_W * layout.s).dp, max(48.0, layout.buttonH).dp)
                        .semantics {
                            role = Role.Button
                            contentDescription = "${Theme.NEXT}. Use the selected market mood"
                            onClick {
                                val index = engine.next()
                                view.announceForAccessibility("${moods[index].word.lowercase()} selected")
                                true
                            }
                        },
                )
            }
        }
    }
}

