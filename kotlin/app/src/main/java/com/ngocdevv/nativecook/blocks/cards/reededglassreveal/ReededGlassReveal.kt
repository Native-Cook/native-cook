package com.ngocdevv.nativecook.blocks.cards.reededglassreveal

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ngocdevv.nativecook.blocks.cards.reededglassreveal.ReededGlassRevealTheme as Theme
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reeded Glass Reveal: a portrait behind thick reeded glass above a black account capsule and panel. A finger (or the swap button, or the autoplay)
 * combs the glass flat: a front travels across the 22 flutes, each flute lies down on its own spring, and the account number resolves exactly where
 * the glass has cleared, rolling glyph by glyph like an odometer. A long press opens a window through the glass that follows the finger.
 *
 * LOOP = 8 s ([ReededGlassRevealMotion.LOOP]): the swap button is pressed 0.7, the front combs the glass flat to mid-screen 0.8-1.6, parks for a
 * second (the gasp, breathing a third of a flute) to 2.6, goes on to the right edge to 3.4, the glass is clear and the number shown to 4.84, the
 * button again 4.76 and the front returns right to left to 6.44 (the flutes rise again), rest. A touch takes over; two seconds after the last one
 * the glass eases back and the loop restarts. Reduce Motion: no travelling front or magnification, the whole pane dissolves between deep and clear.
 * With a system font scale above 1.25 the capsule and panel become a scrolling column of real text.
 * The glass is an AGSL shader on API 33+, a clipped-flutes drawing below.
 */
@Composable
fun ReededGlassReveal(
    autoPlay: Boolean = false,
    autoMaskMs: Double = 8000.0,
    onReveal: (() -> Unit)? = null,
    onMask: (() -> Unit)? = null,
    onAddPlatform: (() -> Unit)? = null,
    glints: Double = 1.0,
    haptics: Boolean = true,
) {
    val context = LocalContext.current
    val densityOwner = LocalDensity.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val reduced = rememberLiveReducedMotion()
    val vibe = remember(context) { ReededGlassRevealHaptics.create(context) }
    val tick = remember { mutableIntStateOf(0) }
    var active by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var revealed by remember { mutableStateOf(false) }
    var targetRevealed by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf(false) }
    var showDialog by remember { mutableStateOf(false) }
    BlockSystemBars(darkIcons = true)

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            active = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    // the portrait, and its two defocused copies (the glass refracts the soft one, the capsule's ends the hazed one), made once off the main thread
    val images by produceState<ReededGlassRevealImages?>(null) {
        value = withContext(Dispatchers.Default) {
            val full = context.assets.open("reededglassreveal/reededglassreveal_rgr-portrait.jpg").use { BitmapFactory.decodeStream(it) }
            val half = Bitmap.createScaledBitmap(full, full.width / 2, full.height / 2, true)
            // RN: sigma 2.6 x 3.2 dp for the glass copy, 7 dp for the capsule's; one dp of the 402 dp image is width / 402 px
            val pxPerDpX = half.width / Theme.REF_W
            val pxPerDpY = half.height / Theme.REF_H
            val soft = ReededGlassRevealBlur.blur(half, Theme.GLASS_SMEAR * pxPerDpX, Theme.GLASS_SMEAR_Y * pxPerDpY)
            val haze = ReededGlassRevealBlur.blur(half, Theme.CAPSULE_HAZE * pxPerDpX, Theme.CAPSULE_HAZE * pxPerDpY)
            half.recycle()
            ReededGlassRevealImages(full, soft, haze)
        }
    }
    val contentAlpha = remember { Animatable(0f) }
    LaunchedEffect(ready) { if (ready) contentAlpha.animateTo(1f, tween(220)) }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color(Theme.wall))) {
        val w = maxWidth.value.toDouble()
        val h = maxHeight.value.toDouble()
        val insetBottom = with(densityOwner) { WindowInsets.navigationBars.getBottom(densityOwner).toDp().value }.toDouble()
        val layout = remember(w, h, insetBottom) { Theme.Layout(w, h, insetBottom) }
        val s = layout.s
        val dens = densityOwner.density.toDouble()
        val largeType = context.resources.configuration.fontScale > 1.25
        val renderer = remember { ReededGlassRevealRenderer(context.assets) }
        val engine = remember(autoPlay, reduced, layout, autoMaskMs, haptics) {
            ReededGlassRevealEngine(autoPlay, reduced, layout, autoMaskMs, haptics)
        }
        val currentOnAdd by rememberUpdatedState(onAddPlatform)
        val addPlatform: () -> Unit = { currentOnAdd?.invoke() ?: run { showDialog = true } }
        SideEffect {
            engine.cue = { beat -> vibe.emit(beat) }
            engine.onRevealedChange = { r -> if (r) onReveal?.invoke() else onMask?.invoke() }
            engine.onRevealedState = { r -> revealed = r }
            engine.onTargetState = { r -> targetRevealed = r }
            engine.onReady = { ready = true }
        }
        LaunchedEffect(engine) { ready = false }
        LaunchedEffect(engine, images) { if (images != null) engine.armGate() }

        LaunchedEffect(engine, active) {
            if (!active) return@LaunchedEffect
            var previous = 0L
            while (true) {
                withFrameNanos { nanos ->
                    val ms = if (previous == 0L) 16.7 else (nanos - previous) / 1e6
                    previous = nanos
                    // Read only inside the Canvas below: invalidates drawing, not the screen's composition.
                    if (engine.frame(ms)) tick.intValue++
                }
            }
        }

        val capY = layout.yb(Theme.CAPSULE.y)
        val shown = ready && images != null
        val swapX = layout.x(Theme.SWAP_CX)
        val swapY = layout.yb(Theme.SWAP_CY)
        val plusX = layout.x(Theme.PLUS_CX)
        val plusY = layout.yb(Theme.PLUS_CY)
        val half = Theme.SWAP_D * s / 2

        Canvas(
            Modifier.fillMaxSize().pointerInput(engine, dens, shown, largeType) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val x = down.position.x / dens
                    val y = down.position.y / dens
                    val onSwap = shown && !largeType && abs(x - swapX) <= half && abs(y - swapY) <= half
                    val onPlus = shown && !largeType && abs(x - plusX) <= half && abs(y - plusY) <= half
                    val onGlass = y < capY && images != null
                    if (!onSwap && !onPlus && !onGlass) return@awaitEachGesture
                    down.consume()
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    if (onSwap) engine.swapDown() else if (onPlus) engine.plusDown() else engine.touchBegin(x)
                    tick.intValue++
                    var inside = true
                    var finished = false
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            tracker.addPosition(change.uptimeMillis, change.position)
                            change.consume()
                            val px = change.position.x / dens
                            val py = change.position.y / dens
                            if (onGlass) {
                                engine.touchMove(px, (change.position.x - down.position.x) / dens, tracker.calculateVelocity().x / dens)
                            } else {
                                val cx = if (onSwap) swapX else plusX
                                val cy = if (onSwap) swapY else plusY
                                inside = abs(px - cx) <= half + 20 && abs(py - cy) <= half + 20
                            }
                            if (!change.pressed) {
                                if (onGlass) engine.touchEnd(tracker.calculateVelocity().x / dens, true)
                                else if (onSwap) engine.swapUp(inside) else engine.plusUp()
                                finished = true
                                if (onPlus && inside) addPlatform()
                                break
                            }
                            tick.intValue++
                        }
                    } finally {
                        if (!finished) {
                            if (onGlass) engine.touchEnd(0.0, false) else if (onSwap) engine.swapUp(false) else engine.plusUp()
                        }
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
            renderer.draw(c, size.width / scale, size.height / scale, layout, engine, images, shown && !largeType, contentAlpha.value, glints)
            c.restore()
        }

        // static nodes over the drawing, so the controls are focusable and labelled
        fun spot(x: Double, y: Double, bw: Double, bh: Double) = Modifier.offset(x.dp, y.dp).size(bw.dp, bh.dp)
        if (images != null) {
            Box(
                spot(0.0, 0.0, w, capY).semantics(mergeDescendants = true) {
                    role = Role.Image
                    contentDescription = Theme.A11Y_GLASS
                    onClick { engine.toggle(); tick.intValue++; true }
                },
            )
        }
        if (shown && !largeType) {
            Box(
                spot(layout.x(Theme.CAPSULE.x), capY, Theme.CAPSULE.w * s, Theme.CAPSULE.h * s).semantics(mergeDescendants = true) {
                    liveRegion = LiveRegionMode.Polite
                    contentDescription = if (revealed) Theme.A11Y_SHOWN else Theme.A11Y_HIDDEN
                },
            )
            Box(
                spot(swapX - half, swapY - half, half * 2, half * 2).semantics(mergeDescendants = true) {
                    role = Role.Button
                    contentDescription = if (targetRevealed) Theme.A11Y_HIDE else Theme.A11Y_REVEAL
                    onClick { engine.toggle(); tick.intValue++; true }
                },
            )
            Box(
                spot(layout.x(Theme.TITLE2_X), layout.yb(Theme.TITLE2_BASE) - Theme.TITLE_SIZE * s, 250 * s, Theme.TITLE_SIZE * s * 1.4).semantics(mergeDescendants = true) {
                    heading()
                    contentDescription = Theme.TITLE2
                },
            )
            Box(
                spot(plusX - half, plusY - half, half * 2, half * 2).semantics(mergeDescendants = true) {
                    role = Role.Button
                    contentDescription = Theme.A11Y_ADD
                    onClick { addPlatform(); true }
                },
            )
            for ((i, row) in Theme.ROWS.withIndex()) {
                val g = Theme.ROWS_GEO[i]
                Box(
                    spot(0.0, layout.yb(g.cy) - Theme.ROW_WELL_D * s / 2, w, Theme.ROW_WELL_D * s).semantics(mergeDescendants = true) {
                        contentDescription = "${row.name}, ${row.followers.replace("  ", " ")}, ${row.value}"
                    },
                )
            }
        }
        if (shown && largeType) {
            ReededGlassRevealDetails(layout, insetBottom, revealed, targetRevealed, { engine.toggle(); tick.intValue++ }, addPlatform, { engine.hold() }, { engine.release() })
        }
        if (showDialog) {
            AlertDialog(
                onDismissRequest = { showDialog = false },
                confirmButton = { TextButton(onClick = { showDialog = false }) { Text("OK") } },
                title = { Text("Linked platforms") },
                text = { Text("Vidly and Loop are linked in this preview.") },
            )
        }
    }
}

/** Reflow only at larger text sizes; the pin's measured composition stays exact at its default size. */
@Composable
private fun ReededGlassRevealDetails(
    layout: Theme.Layout,
    insetBottom: Double,
    revealed: Boolean,
    targetRevealed: Boolean,
    onToggle: () -> Unit,
    onAddPlatform: () -> Unit,
    onHold: () -> Unit,
    onRelease: () -> Unit,
) {
    val context = LocalContext.current
    val light = remember { FontFamily(Typeface.createFromAsset(context.assets, "reededglassreveal/reededglassreveal_Urbanist-Light.ttf")) }
    val regular = remember { FontFamily(Typeface.createFromAsset(context.assets, "reededglassreveal/reededglassreveal_Urbanist-Regular.ttf")) }
    val ink = Color(Theme.ink)
    val mute = Color(Theme.mute)
    fun style(family: FontFamily, size: Double, line: Double, color: Color) = TextStyle(fontFamily = family, fontSize = size.sp, lineHeight = line.sp, color = color)
    val top = min(layout.yb(Theme.CAPSULE.y), layout.h * 0.4)
    Column(
        Modifier.offset(16.dp, top.dp).size((layout.w - 32).dp, (layout.h - top).dp)
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(Color(Theme.panel)).verticalScroll(rememberScrollState())
            .padding(start = 22.dp, end = 22.dp, top = 22.dp, bottom = max(insetBottom, 24.0).dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(Theme.TITLE1, Modifier.weight(1f).semantics { heading() }, style = style(regular, 17.0, 23.0, ink))
            Box(
                Modifier.size(52.dp).clip(CircleShape).background(Color(Theme.swap))
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            onHold()
                            val up = waitForUpOrCancellation()
                            onRelease()
                            if (up != null) onToggle()
                        }
                    }
                    .semantics(mergeDescendants = true) {
                        role = Role.Button
                        contentDescription = if (targetRevealed) Theme.A11Y_HIDE else Theme.A11Y_REVEAL
                        onClick { onToggle(); true }
                    },
                contentAlignment = Alignment.Center,
            ) { Text("⇄", style = style(regular, 20.0, 24.0, Color(Theme.swapGlyph))) }
        }
        Text(
            if (revealed) Theme.FULL.joinToString(" ") else "${Theme.STARS} ${Theme.LAST4}",
            Modifier.padding(top = 12.dp).semantics { contentDescription = if (revealed) Theme.A11Y_SHOWN else Theme.A11Y_HIDDEN },
            style = style(light, 17.0, 24.0, mute),
        )
        Row(Modifier.fillMaxWidth().padding(top = 28.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(Theme.TITLE2, Modifier.weight(1f).semantics { heading() }, style = style(regular, 17.0, 23.0, ink))
            Box(
                Modifier.size(52.dp).clip(CircleShape).background(Color(Theme.plusWell))
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            onHold()
                            val up = waitForUpOrCancellation()
                            onRelease()
                            if (up != null) onAddPlatform()
                        }
                    }
                    .semantics(mergeDescendants = true) {
                        role = Role.Button
                        contentDescription = Theme.A11Y_ADD
                        onClick { onAddPlatform(); true }
                    },
                contentAlignment = Alignment.Center,
            ) { Text("+", style = style(regular, 20.0, 24.0, ink)) }
        }
        for (row in Theme.ROWS) {
            Row(
                Modifier.fillMaxWidth().padding(top = 24.dp).semantics(mergeDescendants = true) { contentDescription = "${row.name}, ${row.followers.replace("  ", " ")}, ${row.value}" },
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(Modifier.size(52.dp).clip(CircleShape).background(Color(Theme.well)))
                Column(Modifier.weight(1f)) {
                    Text(row.name, style = style(light, 14.5, 20.0, ink))
                    Text(row.followers.replace("  ", " "), style = style(light, 14.5, 20.0, mute))
                    Text(row.value, style = style(regular, 14.5, 20.0, ink))
                }
            }
        }
    }
}
