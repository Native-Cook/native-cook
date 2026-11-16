package com.ngocdevv.nativecook.blocks.modals.glasspourmenu

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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ngocdevv.nativecook.blocks.modals.glasspourmenu.GlassPourMenuTheme as Theme
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion
import kotlin.math.max
import kotlin.math.min

/** One row of the menu: a key, a label, an icon (a name of the built-in glyphs, or an SVG path on a 24 x 24 grid), destructive / disabled flags. */
class GlassPourMenuItem(
    val key: String,
    val label: String,
    val icon: String? = null,
    val destructive: Boolean = false,
    val disabled: Boolean = false,
)

enum class GlassPourMenuPlacement { Below, Above, Auto }

/**
 * Glass Pour Menu: a drop of liquid glass that pours open into a frosted menu. Press the drop and pull it down: it stretches a neck,
 * pinches off a bead (the button's glyph turns into a cross) and the rest pours into a rounded panel with the rows; slide to a row and
 * a clear lozenge follows your finger, lift to choose it; a fast flick throws a satellite droplet. One progress value drives everything.
 *
 * LOOP = 7.2 s ([GlassPourMenuMotion.LOOP]): press .5, pull .55-1.45, hold at the neck to 2.45, release into the pour (pinch 2.51),
 * the lens bar tours the rows 3.15-4.6, Duplicate is chosen at 5.1, the menu pours back at 5.45, rest. A touch interrupts it; two seconds
 * after the last touch the menu settles closed and the loop restarts. Reduce Motion: no pour, the closed and open glass cross-fade.
 * `previewTime` pins the loop at that second (checks, previews).
 */
@Composable
fun GlassPourMenu(
    autoPlay: Boolean = false,
    items: List<GlassPourMenuItem> = Theme.DEFAULT_ITEMS,
    onSelect: (String) -> Unit = {},
    open: Boolean? = null,
    defaultOpen: Boolean = false,
    onOpenChange: ((Boolean) -> Unit)? = null,
    onComplete: ((Boolean) -> Unit)? = null,
    anchorX: Double? = null,
    anchorY: Double? = null,
    placement: GlassPourMenuPlacement = GlassPourMenuPlacement.Auto,
    dropSize: Double? = null,
    menuWidth: Double? = null,
    rowHeight: Double? = null,
    tint: Int = Theme.dropTint,
    refraction: Double = 1.0,
    dispersion: Double = 1.0,
    frost: Double = 1.0,
    hot: Double = 1.0,
    dragToSelect: Boolean = true,
    haptics: Boolean = true,
    sound: Boolean = false,
    previewTime: Double? = null,
    accessibilityLabel: String = Theme.BUTTON,
) {
    require(items.size in 2..7) { "GlassPourMenu requires 2-7 items" }
    val context = LocalContext.current
    val densityOwner = LocalDensity.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val reduced = rememberLiveReducedMotion()
    val vibe = remember(context, sound) { GlassPourMenuHaptics.create(context, sound) }
    DisposableEffect(vibe) { onDispose { vibe.release() } }
    val tick = remember { mutableIntStateOf(0) }
    var active by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    val initialOpen = open ?: defaultOpen
    var menuOpen by remember { mutableStateOf(initialOpen) }
    BlockSystemBars(darkIcons = true)

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            active = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color(Theme.cream))) {
        val w = maxWidth.value.toDouble()
        val h = maxHeight.value.toDouble()
        val insetTop = with(densityOwner) { WindowInsets.statusBars.getTop(densityOwner).toDp().value }.toDouble()
        val insetBottom = with(densityOwner) { WindowInsets.navigationBars.getBottom(densityOwner).toDp().value }.toDouble()
        val labelSize = Theme.ROW_SIZE * context.resources.configuration.fontScale
        val g = remember(items.size, dropSize, menuWidth, rowHeight, w, labelSize) {
            Theme.menuGeometry(items.size, dropSize, min(menuWidth ?: 250.0, w - 32), rowHeight, labelSize)
        }
        val layout = remember(w, h, insetTop, insetBottom) { Theme.Layout(w, h, insetTop, insetBottom) }
        val above = placement == GlassPourMenuPlacement.Above ||
            (placement == GlassPourMenuPlacement.Auto && anchorY != null && h - anchorY - insetBottom < g.panelBottom && anchorY - insetTop > g.panelBottom)
        val direction = if (above) -1 else 1
        val low = if (direction == 1) -g.beadY + g.beadR else g.panelBottom
        val high = if (direction == 1) g.panelBottom else -g.beadY + g.beadR
        val ox = max(g.panelHalf + 8, min(w - g.panelHalf - 8, anchorX ?: layout.ox))
        val oy = max(insetTop + low + 8, min(h - insetBottom - high - 8, anchorY ?: layout.oy))
        val optics = remember(refraction, dispersion, frost, hot) {
            doubleArrayOf(refraction, dispersion, frost, hot).map { max(0.0, min(it, 2.0)).toFloat() }.toFloatArray()
        }
        val tintRgb = remember(tint) { floatArrayOf(android.graphics.Color.red(tint) / 255f, android.graphics.Color.green(tint) / 255f, android.graphics.Color.blue(tint) / 255f) }
        val dens = densityOwner.density.toDouble()
        val renderer = remember { GlassPourMenuRenderer(context.assets) }

        val engine = remember(items, g, ox, oy, direction, autoPlay, reduced, dragToSelect) {
            GlassPourMenuEngine(items, g, ox, oy, direction, autoPlay, reduced, open, dragToSelect, initialOpen)
        }
        SideEffect {
            engine.controlledOpen = open
            engine.cue = { beat -> vibe.emit(beat, haptics, sound) }
            engine.onSelect = onSelect
            engine.onOpenChange = { next -> onOpenChange?.invoke(next) }
            engine.onComplete = { next -> onComplete?.invoke(next) }
            engine.onMenuOpen = { next -> menuOpen = next }
        }
        LaunchedEffect(engine, open) { engine.onControlledChange() }
        LaunchedEffect(engine, previewTime) { engine.pin(previewTime) }

        LaunchedEffect(engine, active) {
            if (!active) return@LaunchedEffect
            var previous = 0L
            while (true) {
                withFrameNanos { nanos ->
                    val dtMs = if (previous == 0L) 16.0 else min((nanos - previous) / 1e6, 50.0)
                    previous = nanos
                    engine.frame(dtMs)
                    // Read only inside the Canvas below: invalidates drawing, not the screen's composition.
                    if (!reduced || engine.animating) tick.intValue++
                }
            }
        }

        Canvas(
            Modifier.fillMaxSize()
                .semantics { dismiss { engine.activate(-2); true } }
                .pointerInput(engine, dens) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        down.consume()
                        engine.panBegin(down.position.x / dens, down.position.y / dens)
                        tick.intValue++
                        var finished = false
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                tracker.addPosition(change.uptimeMillis, change.position)
                                if (!change.pressed) {
                                    change.consume()
                                    engine.panUpdate(change.position.x / dens, change.position.y / dens)
                                    engine.panEnd(tracker.calculateVelocity().y / dens)
                                    engine.panFinalize(true)
                                    finished = true
                                    break
                                }
                                engine.panUpdate(change.position.x / dens, change.position.y / dens)
                                change.consume()
                                tick.intValue++
                            }
                        } finally {
                            if (!finished) engine.panFinalize(false)
                            tick.intValue++
                        }
                    }
                },
        ) {
            tick.intValue
            val c = drawContext.canvas.nativeCanvas
            val scale = drawContext.density.density
            c.save()
            c.scale(scale, scale)
            renderer.draw(c, size.width / scale, size.height / scale, engine, labelSize, optics, tintRgb, tint)
            c.restore()
        }

        // the controls: real nodes over the drawing, so they are focusable and labelled (positions are static)
        fun hit(cx: Double, cy: Double, bw: Double, bh: Double) = Modifier
            .offset((cx - max(bw, 44.0) / 2).dp, (cy - max(bh, 44.0) / 2).dp)
            .size(max(bw, 44.0).dp, max(bh, 44.0).dp)
        if (!menuOpen) {
            Box(
                hit(ox, oy, g.buttonR * 2, g.buttonR * 2).semantics(mergeDescendants = true) {
                    role = Role.Button
                    contentDescription = accessibilityLabel
                    stateDescription = "Collapsed"
                    onClick { engine.activate(-1); true }
                },
            )
        } else {
            Box(
                hit(ox, oy + direction * g.beadY, g.beadR * 2, g.beadR * 2).semantics(mergeDescendants = true) {
                    role = Role.Button
                    contentDescription = Theme.CLOSE
                    onClick { engine.activate(-2); true }
                },
            )
            for ((i, item) in items.withIndex()) {
                Box(
                    hit(ox, oy + direction * g.rows[i], g.panelHalf * 2, g.rowPitch).semantics(mergeDescendants = true) {
                        role = Role.Button
                        contentDescription = item.label
                        if (item.disabled) stateDescription = "Disabled"
                        onClick { engine.activate(i); true }
                    },
                )
            }
        }
    }
}
