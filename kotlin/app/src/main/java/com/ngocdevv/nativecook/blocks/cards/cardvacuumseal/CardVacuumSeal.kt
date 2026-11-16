package com.ngocdevv.nativecook.blocks.cards.cardvacuumseal

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ngocdevv.nativecook.blocks.cards.cardvacuumseal.CardVacuumSealTheme as Theme
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion
import kotlin.math.max
import kotlin.math.min

/**
 * Card Vacuum Seal: a bank's Cards screen where "Protect Card" vacuum-seals the card in clear film. Hold the shield tile: a clear sheet
 * falls on the card and the air is drawn out until the film is a creased, glossy skin; slide off to hold it half-way, lift early to let the air
 * back in, tap to run the whole seal. A finger on the film squeegees the air out where it is; on the sealed card it dents the film; dragging in
 * from the sealed card's bottom or right edge peels it. One value `p` (0 no film, 0.3 the sheet on the card as an air pillow, 1 sealed) drives
 * the film, the shield disc, the label and the tiles' tug.
 *
 * LOOP = 6 s ([CardVacuumSealMotion.LOOP]): the sealed card is tapped open (0.12-0.4), the air comes back and the sheet lifts away (0.2-1.0), the
 * Protect tile is held (1.2-3.45: the sheet falls, air leaves, a one-second pause at 0.66, then the lock), the creases relax and a glint runs
 * over them, a slow highlight crosses the film (4-5), rest. A touch takes over; two seconds after the last one the film glides to rest and the
 * loop restarts. Reduce Motion: no film motion; the sealed film fades in and out and the label morphs.
 * The film is an AGSL shader (API 33+); below that the card is drawn with a strokes-and-gradients film.
 */
@Composable
fun CardVacuumSeal(autoPlay: Boolean = false) {
    val context = LocalContext.current
    val densityOwner = LocalDensity.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val reduced = rememberLiveReducedMotion()
    val vibe = remember(context, reduced) { CardVacuumSealHaptics.create(context, !reduced) }
    DisposableEffect(vibe) { onDispose { vibe.release() } }
    val tick = remember { mutableIntStateOf(0) }
    var active by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var sealed by remember { mutableStateOf(true) }
    BlockSystemBars(darkIcons = false)

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            active = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    // the screen fades in over 420 ms once it is drawn (with Reduce Motion it is simply there)
    val shown = remember { Animatable(0f) }
    LaunchedEffect(reduced) {
        if (reduced) shown.snapTo(1f) else shown.animateTo(1f, tween(420, easing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)))
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color(Theme.page))) {
        val w = maxWidth.value.toDouble()
        val h = maxHeight.value.toDouble()
        val insetTop = with(densityOwner) { WindowInsets.statusBars.getTop(densityOwner).toDp().value }.toDouble()
        val insetBottom = with(densityOwner) { WindowInsets.navigationBars.getBottom(densityOwner).toDp().value }.toDouble()
        val layout = remember(w, h, insetTop, insetBottom) { Theme.Layout(w, h, max(insetTop, 20.0), insetBottom) }
        val s = layout.s
        val dens = densityOwner.density.toDouble()
        // type may grow with the system font size up to 1.3, never clip (RN's maxFontSizeMultiplier)
        val fs = min(context.resources.configuration.fontScale.toDouble(), 1.3)
        val renderer = remember { CardVacuumSealRenderer(context.assets) }
        val engine = remember(autoPlay, reduced, s) {
            CardVacuumSealEngine(autoPlay, reduced, Theme.TILES_W * s, Theme.TILES_H * s).also {
                it.geom = CardVacuumSealEngine.Geom(s, Theme.CARD_W / 2, Theme.CARD_H / 2)
            }
        }
        SideEffect {
            engine.cue = { beat -> vibe.emit(beat, true) }
            engine.air = { rate -> vibe.air(rate) }
            engine.onSealedChange = { next -> sealed = next }
        }

        LaunchedEffect(engine, active) {
            if (!active) return@LaunchedEffect
            var previous = 0L
            while (true) {
                withFrameNanos { nanos ->
                    val dt = if (previous == 0L) 0.016 else min((nanos - previous) / 1e9, 0.05)
                    previous = nanos
                    // Read only inside the Canvas below: invalidates drawing, not the screen's composition.
                    if (engine.frame(dt)) tick.intValue++
                }
            }
        }

        val contentH = layout.contentH
        val tileX = (Theme.CARD_X + (Theme.TILES_W + Theme.TILES_GAP)) * s
        val tileY = layout.yt(Theme.TILES_Y)
        val tileSize = Theme.TILES_W * s
        val cardX = Theme.CARD_X * s
        val cardY = layout.yt(Theme.CARD_Y)
        val cardW = Theme.CARD_W * s
        val cardH = Theme.CARD_H * s

        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState(), enabled = contentH > h)) {
            Canvas(
                Modifier.fillMaxWidth().height(contentH.dp)
                    .pointerInput(engine, dens, layout) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val x = down.position.x / dens
                            val y = down.position.y / dens
                            val onTile = x >= tileX && x <= tileX + tileSize && y >= tileY && y <= tileY + tileSize
                            val onCard = x >= cardX && x <= cardX + cardW && y >= cardY && y <= cardY + cardH
                            // anything else is left to the page (it scrolls when it is taller than the screen)
                            if (!onTile && !onCard) return@awaitEachGesture
                            down.consume()
                            if (onTile) engine.startHold() else engine.squeegeeBegin(x - cardX, y - cardY)
                            tick.intValue++
                            try {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    change.consume()
                                    if (!change.pressed) break
                                    val px = change.position.x / dens
                                    val py = change.position.y / dens
                                    if (onTile) engine.updateHold(px - tileX, py - tileY) else engine.squeegeeMove(px - cardX, py - cardY)
                                    tick.intValue++
                                }
                            } finally {
                                if (onTile) engine.finishHold() else engine.squeegeeEnd()
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
                renderer.draw(c, size.width / scale, size.height / scale, scale, layout, engine, shown.value, fs)
                c.restore()
            }

            // the controls: real nodes over the drawing, so they are focusable and labelled (positions are static)
            fun spot(x: Double, y: Double, bw: Double, bh: Double) = Modifier.offset(x.dp, y.dp).size(bw.dp, bh.dp)
            val hy = layout.yt(Theme.HEADER_CY)
            Box(
                spot((Theme.HEADER_ARROW_X - 11) * s, hy - 22, 44.0, 44.0).semantics(mergeDescendants = true) {
                    role = Role.Button
                    contentDescription = Theme.A11Y_BACK
                },
            )
            Box(
                spot(0.0, hy - Theme.TITLE_SIZE * s * 0.62, w, Theme.TITLE_SIZE * s * 1.3).semantics(mergeDescendants = true) {
                    heading()
                    contentDescription = Theme.TITLE
                },
            )
            Box(
                spot((Theme.HEADER_CLOSE_X + Theme.HEADER_CLOSE_D / 2) * s - 22, hy - 22, 44.0, 44.0).semantics(mergeDescendants = true) {
                    role = Role.Button
                    contentDescription = Theme.A11Y_CLOSE
                },
            )
            Box(
                spot(cardX, layout.yt(Theme.TILES_Y), tileSize, tileSize).semantics(mergeDescendants = true) {
                    role = Role.Button
                    contentDescription = Theme.DETAILS
                },
            )
            Box(
                spot(tileX, tileY, tileSize, tileSize).semantics(mergeDescendants = true) {
                    role = Role.Switch
                    contentDescription = Theme.A11Y_PROTECT
                    toggleableState = ToggleableState(sealed)
                    liveRegion = LiveRegionMode.Polite
                    onClick(label = Theme.A11Y_PROTECT_HINT) { engine.toggle(!sealed); true }
                },
            )
            Box(
                spot((Theme.CARD_X + 2 * (Theme.TILES_W + Theme.TILES_GAP)) * s, tileY, tileSize, tileSize).semantics(mergeDescendants = true) {
                    role = Role.Button
                    contentDescription = Theme.SETTINGS
                },
            )
            Box(
                spot(cardX, layout.yt(Theme.REV_Y), cardW, Theme.REV_H * s).semantics(mergeDescendants = true) {
                    contentDescription = "${Theme.REVENUE} ${Theme.REVENUE_VALUE}"
                },
            )
            for ((i, row) in Theme.ROWS.withIndex()) {
                Box(
                    spot(cardX, layout.yt(Theme.LIST_Y) + (Theme.LIST_ROW_Y + i * Theme.LIST_ROW_H) * s, cardW, Theme.LIST_AVATAR * s).semantics(mergeDescendants = true) {
                        contentDescription = "${row.name}, ${row.note}, ${row.amount}, ${row.status}"
                    },
                )
            }
            Box(
                spot(cardX, cardY, cardW, cardH).semantics(mergeDescendants = true) {
                    role = Role.Switch
                    contentDescription = Theme.a11yCard(sealed)
                    toggleableState = ToggleableState(sealed)
                    onClick { engine.toggle(!sealed); true }
                },
            )
        }
    }
}
