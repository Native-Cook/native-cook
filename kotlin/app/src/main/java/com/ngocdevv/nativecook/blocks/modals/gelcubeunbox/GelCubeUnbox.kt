package com.ngocdevv.nativecook.blocks.modals.gelcubeunbox

import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ngocdevv.nativecook.blocks.modals.gelcubeunbox.GelCubeUnboxTheme as Theme
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion
import kotlin.math.max
import kotlin.math.min

/** Which copy the sheet shows when the action is pressed. */
enum class GelCubeUnboxState { Sealed, Open }

/**
 * Gel Cube Unbox: a gel cube with a glowing core that you open by dragging up. The lid lifts and swings back, a fan of light falls across the floor,
 * the headline, the action and the sheet light up and the texts change from "Unbox" to "See deals". Drag up past 45 % (or flick) to open, drag
 * down to close, tap "Unbox" or the cube to run it. Everything is one analytic material pass (an AGSL shader on API 33+, a gradient version below)
 * driven by one value `p` (0 sealed .. 1 open).
 *
 * LOOP = 7 s ([GelCubeUnboxMotion.LOOP]): the cube gathers 0.8-1.1, the lid lifts a little to 1.6 (a gasp), swings open 1.6-2.7 with a gel tail,
 * rests open to 5.2, closes to 6.3 (a two-frame flash as it lands), rest. A touch takes over; two seconds after the last one the lid closes in
 * 0.6 s and the loop restarts. Reduce Motion: no drag, no lid; the copy cross-fades and a tap toggles it. With a system font scale above 1.1 the
 * texts become a scrolling column of real text over the picture.
 * Not ported from the RN props: `progress` (an external shared value), `children` (a custom sheet) and `tokens`.
 */
@Composable
fun GelCubeUnbox(
    autoPlay: Boolean = false,
    tint: Int = Theme.tint,
    open: Boolean? = null,
    defaultOpen: Boolean = false,
    onOpenChange: ((Boolean) -> Unit)? = null,
    onProgress: ((Double) -> Unit)? = null,
    title: String = Theme.TITLE,
    subtitle: String = Theme.SUBTITLE,
    sealed: GelCubeUnboxCopy = Theme.SEALED,
    opened: GelCubeUnboxCopy = Theme.OPENED,
    onAction: ((GelCubeUnboxState) -> Unit)? = null,
    onClose: (() -> Unit)? = null,
    cubeSize: Double = Theme.CUBE_SIZE,
    lidWeight: Double = GelCubeUnboxMotion.SPRING_LID.mass,
    sound: Boolean = false,
) {
    val context = LocalContext.current
    val densityOwner = LocalDensity.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val reduced = rememberLiveReducedMotion()
    val vibe = remember(context, sound) { GelCubeUnboxHaptics.create(context, sound) }
    DisposableEffect(vibe) { onDispose { vibe.release() } }
    val tick = remember { mutableIntStateOf(0) }
    var active by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var semanticOpen by remember { mutableStateOf(open ?: defaultOpen) }
    BlockSystemBars(darkIcons = false)

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            active = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color(tint))) {
        val w = maxWidth.value.toDouble()
        val h = maxHeight.value.toDouble()
        val insetTop = with(densityOwner) { WindowInsets.statusBars.getTop(densityOwner).toDp().value }.toDouble()
        val insetBottom = with(densityOwner) { WindowInsets.navigationBars.getBottom(densityOwner).toDp().value }.toDouble()
        val layout = remember(w, h, insetTop, insetBottom) { Theme.Layout(w, h, insetTop, insetBottom) }
        val s = layout.s
        val dens = densityOwner.density.toDouble()
        val largeType = context.resources.configuration.fontScale > Theme.LARGE_TYPE_THRESHOLD
        val pal = remember(tint) { Theme.paletteOf(tint) }
        val renderer = remember { GelCubeUnboxRenderer(context.assets) }
        val showClose = onClose != null
        val params = remember(sealed, opened, title, subtitle, showClose, cubeSize, largeType) {
            GelCubeUnboxParams(sealed, opened, title, subtitle, showClose, max(0.4, cubeSize / Theme.CUBE_SIZE), Theme.RADIUS_SHEET, !largeType)
        }
        val engine = remember(autoPlay, reduced, open != null, lidWeight, s) {
            GelCubeUnboxEngine(autoPlay, reduced, open != null, open ?: defaultOpen, lidWeight, s)
        }
        val currentOpen by rememberUpdatedState(semanticOpen)
        val currentOnAction by rememberUpdatedState(onAction)
        val currentOnClose by rememberUpdatedState(onClose)
        SideEffect {
            engine.cue = { beat -> vibe.emit(beat, true) }
            engine.onCommit = { next -> onOpenChange?.invoke(next) }
            engine.onProgress = onProgress?.let { cb -> { value: Double -> cb(value) } }
            engine.onSemanticOpen = { next -> semanticOpen = next }
        }
        LaunchedEffect(engine, open) { if (open != null) engine.onControlledChange(open) }

        LaunchedEffect(engine, active) {
            if (!active) return@LaunchedEffect
            engine.resumeClock()
            var previous = 0L
            while (true) {
                withFrameNanos { nanos ->
                    val dt = if (previous == 0L) 0.016 else min((nanos - previous) / 1e9, 0.05)
                    previous = nanos
                    // Read only inside the Canvas below: invalidates drawing, not the screen's composition.
                    if (engine.frame(nanos / 1e6, dt)) tick.intValue++
                }
            }
        }

        Canvas(Modifier.fillMaxSize()) {
            tick.intValue
            val c = drawContext.canvas.nativeCanvas
            val scale = drawContext.density.density
            c.save()
            c.scale(scale, scale)
            renderer.draw(c, size.width / scale, size.height / scale, layout, engine, pal, params)
            c.restore()
        }

        fun spot(x: Double, y: Double, bw: Double, bh: Double) = Modifier.offset(x.dp, y.dp).size(bw.dp, bh.dp)
        val sheetLabel = if (semanticOpen) opened else sealed

        if (!largeType) {
            // the texts are drawn on the canvas: static nodes carry them for the screen reader
            val tb = Theme.TITLE_BOX
            Box(
                spot(layout.x(tb.x), layout.y(tb.y), tb.w * s, tb.h * s).semantics(mergeDescendants = true) {
                    heading()
                    contentDescription = title.replace('\n', ' ')
                },
            )
            val sb = Theme.SUBTITLE_BOX
            Box(spot(layout.x(sb.x), layout.y(sb.y), sb.w * s, sb.h * s).semantics(mergeDescendants = true) { contentDescription = subtitle })
            val sh = Theme.SHEET
            Box(
                spot(layout.x(sh.x), layout.y(sh.y), sh.w * s, sh.h * s).semantics(mergeDescendants = true) {
                    contentDescription = listOfNotNull(sheetLabel.chip, sheetLabel.sheetTitle, sheetLabel.sheetBody).joinToString(". ")
                },
            )
            // the action
            val pl = Theme.PILL
            Box(
                spot(layout.x(pl.x), layout.y(pl.y + pl.h / 2) - max(Theme.MIN_HIT, pl.h * s) / 2, pl.w * s, max(Theme.MIN_HIT, pl.h * s))
                    .pointerInput(engine) {
                        detectTapGestures(
                            onPress = {
                                engine.pressIn()
                                tick.intValue++
                                val released = tryAwaitRelease()
                                engine.pressOut()
                                if (released) {
                                    val wasOpen = currentOpen
                                    engine.act(wasOpen)
                                    currentOnAction?.invoke(if (wasOpen) GelCubeUnboxState.Open else GelCubeUnboxState.Sealed)
                                }
                                tick.intValue++
                            },
                        )
                    }
                    .semantics(mergeDescendants = true) {
                        role = Role.Button
                        contentDescription = sheetLabel.action
                        onClick {
                            val wasOpen = currentOpen
                            engine.act(wasOpen)
                            currentOnAction?.invoke(if (wasOpen) GelCubeUnboxState.Open else GelCubeUnboxState.Sealed)
                            tick.intValue++
                            true
                        }
                    },
            )
        }

        // the cube: a drag that lifts the lid (a tap with Reduce Motion)
        val tc = Theme.TOUCH
        Box(
            spot(layout.x(tc.x), layout.y(tc.y), tc.w * s, tc.h * s)
                .pointerInput(engine, dens, reduced) {
                    if (reduced) {
                        detectTapGestures(onTap = {
                            engine.toggle()
                            tick.intValue++
                        })
                    } else {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            down.consume()
                            val tracker = VelocityTracker()
                            tracker.addPosition(down.uptimeMillis, down.position)
                            engine.panBegin()
                            tick.intValue++
                            var finished = false
                            try {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    tracker.addPosition(change.uptimeMillis, change.position)
                                    change.consume()
                                    val translation = (change.position.y - down.position.y) / dens
                                    engine.panUpdate(translation)
                                    if (!change.pressed) {
                                        engine.panEnd(tracker.calculateVelocity().y / dens)
                                        engine.panFinalize(true)
                                        finished = true
                                        break
                                    }
                                    tick.intValue++
                                }
                            } finally {
                                if (!finished) engine.panFinalize(false)
                                tick.intValue++
                            }
                        }
                    }
                }
                .semantics(mergeDescendants = true) {
                    role = Role.Button
                    contentDescription = if (semanticOpen) Theme.A11Y_CLOSE_BOX else Theme.A11Y_OPEN
                    stateDescription = if (semanticOpen) "Expanded" else "Collapsed"
                    onClick(label = Theme.A11Y_HINT) {
                        engine.toggle()
                        tick.intValue++
                        true
                    }
                },
        )

        if (onClose != null) {
            val cl = Theme.CLOSE
            Box(
                spot(layout.x(cl.x), layout.y(cl.y), max(Theme.MIN_HIT, cl.w * s), max(Theme.MIN_HIT, cl.h * s))
                    .pointerInput(Unit) { detectTapGestures(onTap = { currentOnClose?.invoke() }) }
                    .semantics(mergeDescendants = true) {
                        role = Role.Button
                        contentDescription = Theme.A11Y_CLOSE_REWARD
                        onClick {
                            currentOnClose?.invoke()
                            true
                        }
                    },
            )
        }

        if (largeType) {
            GelCubeUnboxFlow(
                layout = layout,
                insetTop = insetTop,
                insetBottom = insetBottom,
                pal = pal,
                title = title,
                subtitle = subtitle,
                label = sheetLabel,
                onAction = {
                    engine.pressIn()
                },
                onRelease = { released ->
                    engine.pressOut()
                    if (released) {
                        val wasOpen = currentOpen
                        engine.act(wasOpen)
                        currentOnAction?.invoke(if (wasOpen) GelCubeUnboxState.Open else GelCubeUnboxState.Sealed)
                    }
                    tick.intValue++
                },
            )
        }
    }
}

/**
 * The large-type layout (system font scale above 1.1): the headline, the subtitle, the action and the sheet's copy as real text in a scrolling
 * column over the picture, in a rounded backdrop of the page colour at 96 %.
 */
@Composable
private fun GelCubeUnboxFlow(
    layout: Theme.Layout,
    insetTop: Double,
    insetBottom: Double,
    pal: Theme.Palette,
    title: String,
    subtitle: String,
    label: GelCubeUnboxCopy,
    onAction: () -> Unit,
    onRelease: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val face = remember { FontFamily(Typeface.createFromAsset(context.assets, "gelcubeunbox/gelcubeunbox_Inter-Regular.ttf")) }
    val s = layout.s
    val track = (Theme.DISPLAY_TRACK * s).sp
    fun style(size: Double, line: Double, color: Color) = TextStyle(
        fontFamily = face, color = color, fontSize = size.sp, lineHeight = line.sp, textAlign = TextAlign.Center, letterSpacing = track,
    )
    val ink = Color(Theme.ink)
    val mute = Color(Theme.mute)
    val backdrop = Color(
        (pal.page[0] * 255).toInt().coerceIn(0, 255), (pal.page[1] * 255).toInt().coerceIn(0, 255), (pal.page[2] * 255).toInt().coerceIn(0, 255),
        (Theme.FLOW_ALPHA * 255).toInt(),
    )
    val top = max(insetTop, layout.y(Theme.TOUCH.y + Theme.TOUCH.h) + 24.0)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(PaddingValues(start = 24.dp, end = 24.dp, top = top.dp, bottom = (insetBottom + 24.0).dp)),
    ) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(Theme.RADIUS_SHEET.dp)).background(backdrop).padding(Theme.FLOW_PADDING.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Theme.FLOW_GAP.dp),
        ) {
            Text(title, Modifier.fillMaxWidth().semantics { heading() }, style = style(Theme.FLOW_TITLE, Theme.FLOW_TITLE_LINE, ink))
            Text(subtitle, Modifier.fillMaxWidth(), style = style(Theme.FLOW_BODY, Theme.FLOW_BODY_LINE, mute))
            Box(
                Modifier.fillMaxWidth().padding(vertical = 8.dp).heightIn(min = Theme.FLOW_ACTION_HEIGHT.dp)
                    .clip(RoundedCornerShape(Theme.FLOW_ACTION_RADIUS.dp)).background(Color(Theme.flowGlass))
                    .pointerInput(Unit) {
                        detectTapGestures(onPress = {
                            onAction()
                            val released = tryAwaitRelease()
                            onRelease(released)
                        })
                    }
                    .semantics(mergeDescendants = true) {
                        role = Role.Button
                        contentDescription = label.action
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(label.action, Modifier.padding(12.dp), style = style(Theme.FLOW_ACTION, Theme.FLOW_ACTION * 1.2, ink))
            }
            Column(
                Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
                    contentDescription = listOfNotNull(label.chip, label.sheetTitle, label.sheetBody).joinToString(". ")
                },
            ) {
                label.chip?.let { Text(it, Modifier.fillMaxWidth().padding(bottom = 12.dp), style = style(Theme.FLOW_CHIP, Theme.FLOW_CHIP_LINE, ink)) }
                label.sheetTitle?.let { Text(it, Modifier.fillMaxWidth(), style = style(Theme.FLOW_TITLE, Theme.FLOW_TITLE_LINE, ink)) }
                label.sheetBody?.let { Text(it, Modifier.fillMaxWidth(), style = style(Theme.FLOW_BODY, Theme.FLOW_BODY_LINE, mute)) }
            }
        }
    }
}
