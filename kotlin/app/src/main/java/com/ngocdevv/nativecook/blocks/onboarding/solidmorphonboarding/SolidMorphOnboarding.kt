package com.ngocdevv.nativecook.blocks.onboarding.solidmorphonboarding

import android.graphics.Bitmap
import android.os.Build
import android.util.Log
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ngocdevv.nativecook.blocks.onboarding.solidmorphonboarding.SolidMorphOnboardingMotion as M
import com.ngocdevv.nativecook.blocks.onboarding.solidmorphonboarding.SolidMorphOnboardingTheme as Theme
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A button of the footer: its label and what it does. */
class SolidMorphOnboardingAction(val label: String, val onPress: () -> Unit)

/**
 * Solid Morph Onboarding: a three-page introduction to a money app where one ceramic token turns into the next promise. Swipe (or tap a side) and the
 * solid turns on its lathe, its outline morphs (a disc with a dish, the same disc split by a wedge, a rounded heptagon), the engraved mark changes (infinity,
 * tick, clock), the next page's ground opens as an iris out of the solid and the copy leaves upward and arrives from below. Press the solid and its cushion
 * compresses to a 4 pt stop; drag it and it tilts and springs back.
 *
 * LOOP = 9 s ([SolidMorphOnboardingMotion.LOOP]): page 1, the solid turns to page 2 between 0.6 and 2.3 (held half-way 1.0-1.8), page 3 from 2.8 to 5.3,
 * back to page 1 6.0-8.55, rest. A touch takes over; two seconds after the last one the demo blends back to the first page. Reduce Motion (and large text)
 * swap the lathe for a fade between pages. The solid is an AGSL ray marcher on API 33+, a flat drawing of a plate and a dish below.
 * Not ported from the RN props: `index` / `progress` (controlled), `renderFooter`, `tokens`, custom glyph sources, the rotation-sensor tilt, `SolidMorph`.
 */
@Composable
fun SolidMorphOnboarding(
    autoPlay: Boolean = false,
    pages: List<SolidMorphOnboardingPage>? = null,
    onIndexChange: ((Int) -> Unit)? = null,
    onSkip: (() -> Unit)? = null,
    onComplete: (() -> Unit)? = null,
    primary: SolidMorphOnboardingAction? = null,
    secondary: SolidMorphOnboardingAction? = null,
    hover: Boolean = true,
    spin: Double = M.SPIN,
    conserveVolume: Boolean = true,
) {
    val context = LocalContext.current
    val densityOwner = LocalDensity.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val liveReduced = rememberLiveReducedMotion()
    val fontScale = context.resources.configuration.fontScale.toDouble()
    val reduced = liveReduced || fontScale > Theme.LARGE_TYPE
    val vibe = remember(context) { SolidMorphOnboardingHaptics.create(context) }
    val tick = remember { mutableIntStateOf(0) }
    var active by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var selected by remember { mutableIntStateOf(0) }
    val safePages = remember(pages) { if (pages != null && pages.isNotEmpty()) pages else Theme.DEFAULT_PAGES }
    val current = safePages[selected.coerceIn(0, safePages.size - 1)]
    BlockSystemBars(darkIcons = !current.dark)

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            active = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color(Theme.ink))) {
        val w = maxWidth.value.toDouble()
        val h = maxHeight.value.toDouble()
        val insetTop = with(densityOwner) { WindowInsets.statusBars.getTop(densityOwner).toDp().value }.toDouble()
        val insetBottom = with(densityOwner) { WindowInsets.navigationBars.getBottom(densityOwner).toDp().value }.toDouble()
        val layout = remember(w, h, insetTop, insetBottom, fontScale) { Theme.Layout(w, h, insetTop, insetBottom, fontScale) }
        val dens = densityOwner.density
        val primaryLabel = primary?.label ?: Theme.PRIMARY
        val secondaryLabel: String? = if (primary != null || secondary != null) secondary?.label else Theme.SECONDARY
        val atlasMaker = remember { SolidMorphOnboardingAtlas(context.assets) }
        val atlas by produceState<Bitmap?>(null, layout, safePages, primaryLabel, secondaryLabel) {
            value = withContext(Dispatchers.Default) { atlasMaker.pageAtlas(safePages, layout, dens, primaryLabel, secondaryLabel) }
        }
        val glyphs by produceState<Bitmap?>(null, safePages) {
            value = withContext(Dispatchers.Default) { atlasMaker.glyphAtlas(safePages) }
        }
        val engine = remember(safePages, autoPlay, reduced, layout, hover) { SolidMorphOnboardingEngine(safePages, autoPlay, reduced, layout, hover) }
        val ready = atlas != null && glyphs != null
        val cbIndex by rememberUpdatedState(onIndexChange)
        SideEffect {
            engine.ready = ready
            engine.cue = { beat -> vibe.emit(beat) }
            engine.onIndexChange = { page, _ ->
                selected = page
                cbIndex?.invoke(page)
            }
        }
        LaunchedEffect(engine, active) {
            engine.onForeground(active)
            if (!active) return@LaunchedEffect
            var previous = 0L
            while (true) {
                withFrameNanos { nanos ->
                    val dt = if (previous == 0L) 0.016 else min((nanos - previous) / 1e9, 0.05)
                    previous = nanos
                    engine.frame(nanos / 1e6, dt)
                    tick.intValue++
                }
            }
        }
        val pass = remember(atlas, glyphs, layout, dens) {
            val a = atlas
            val g = glyphs
            if (Build.VERSION.SDK_INT >= 33 && a != null && g != null) {
                try {
                    SolidMorphOnboardingShaderPass(a, g, layout, dens)
                } catch (error: IllegalArgumentException) {
                    Log.w("SolidMorphOnboarding", "Runtime shader unavailable; using the Canvas fallback", error)
                    null
                }
            } else {
                null
            }
        }
        val fallback = remember { SolidMorphOnboardingFallback() }

        val s = layout.s
        val btnW = if (secondaryLabel == null || layout.largeText) w - 2 * Theme.MARGIN * s else (w - 2 * Theme.MARGIN * s - Theme.BUTTON_GAP * s) / 2
        val secondX = if (layout.largeText) Theme.MARGIN * s else Theme.MARGIN * s + btnW + Theme.BUTTON_GAP * s
        val secondY = if (layout.largeText) layout.footerY + layout.buttonHeight + Theme.BUTTON_GAP * s else layout.footerY
        val skipH = max(48.0, 32 * s * fontScale)
        val skipL = w - 8 * s - layout.skipWidth
        val skipT = layout.progressY - 18 * s
        val panBottom = layout.footerY - 12
        val end = safePages.size - 1
        val onPrimary = {
            engine.touched()
            if (primary != null) primary.onPress() else if (onComplete != null) onComplete() else engine.go(min(end, selected + 1))
        }
        val onSecondary = {
            engine.touched()
            if (secondary != null) secondary.onPress() else engine.go(0)
        }
        val onSkipPress = {
            engine.go(end)
            onSkip?.invoke()
        }

        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState(), enabled = layout.h > h)) {
            Canvas(
                Modifier.fillMaxWidth().height(layout.h.dp).pointerInput(engine, dens, layout, ready) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val x = down.position.x / dens.toDouble()
                        val y = down.position.y / dens.toDouble()
                        fun inside(l: Double, t: Double, bw: Double, bh: Double) = x >= l && x <= l + bw && y >= t && y <= t + bh
                        // 0 none, 1 pan on the stage, 2 primary, 3 secondary, 4 skip
                        val mode = when {
                            !ready -> 0
                            inside(skipL, skipT, layout.skipWidth, skipH) -> 4
                            inside(Theme.MARGIN * s, layout.footerY, btnW, layout.buttonHeight) -> 2
                            secondaryLabel != null && inside(secondX, secondY, btnW, layout.buttonHeight) -> 3
                            y < panBottom && x >= M.EDGE_GUARD -> 1
                            else -> 0
                        }
                        if (mode == 0) return@awaitEachGesture
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        if (mode == 1) engine.panBegin(x, y)
                        if (mode == 2) engine.primaryDown()
                        if (mode == 3) engine.secondaryDown()
                        var consume = mode != 1 || !layout.largeText
                        if (consume) down.consume()
                        var finished = false
                        var abandoned = false
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                tracker.addPosition(change.uptimeMillis, change.position)
                                val px = change.position.x / dens.toDouble()
                                val py = change.position.y / dens.toDouble()
                                val tx = (change.position.x - down.position.x) / dens.toDouble()
                                val ty = (change.position.y - down.position.y) / dens.toDouble()
                                if (mode == 1 && layout.largeText && !consume) {
                                    // the scrolling stage: a horizontal drag is the pager's, a vertical one is the page's
                                    if (abs(ty) > 12 && abs(tx) < M.PRESS_SLOP) {
                                        engine.panFinalize(false)
                                        abandoned = true
                                        break
                                    }
                                    if (abs(tx) >= M.PRESS_SLOP) consume = true
                                }
                                if (consume) change.consume()
                                when (mode) {
                                    1 -> {
                                        engine.panUpdate(px, py, tx, ty)
                                        if (!change.pressed) {
                                            val v = tracker.calculateVelocity()
                                            engine.panEnd(v.x / dens.toDouble(), v.y / dens.toDouble(), tx)
                                            engine.panFinalize(true)
                                            if (hypot(tx, ty) <= 8 && change.uptimeMillis - down.uptimeMillis < 500) engine.tap(px, w)
                                            finished = true
                                        }
                                    }
                                    2, 3 -> if (!change.pressed) {
                                        val bx = if (mode == 2) Theme.MARGIN * s else secondX
                                        val by = if (mode == 2) layout.footerY else secondY
                                        val ok = px >= bx && px <= bx + btnW && py >= by && py <= by + layout.buttonHeight
                                        if (mode == 2) engine.primaryUp() else engine.secondaryUp()
                                        if (ok) (if (mode == 2) onPrimary() else onSecondary())
                                        finished = true
                                    }
                                    4 -> if (!change.pressed) {
                                        val ok = px >= skipL && px <= skipL + layout.skipWidth && py >= skipT && py <= skipT + skipH
                                        if (ok) onSkipPress()
                                        finished = true
                                    }
                                }
                                if (finished) break
                                tick.intValue++
                            }
                        } finally {
                            if (!finished && !abandoned) {
                                when (mode) {
                                    1 -> engine.panFinalize(false)
                                    2 -> engine.primaryUp()
                                    3 -> engine.secondaryUp()
                                }
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
                val cw = size.width / scale
                val ch = size.height / scale
                if (ready) {
                    val vis = engine.visibility.value.coerceIn(0.0, 1.0)
                    val layer = if (vis < 0.999) c.saveLayerAlpha(0f, 0f, cw, ch, (255 * vis).toInt()) else -1
                    val sp = pass
                    if (sp != null && Build.VERSION.SDK_INT >= 33) {
                        sp.draw(c, engine, safePages, spin, conserveVolume, primary != null && secondary == null)
                    } else {
                        fallback.draw(c, atlas, engine, safePages, layout, spin)
                    }
                    if (layer >= 0) c.restoreToCount(layer)
                }
                c.restore()
            }

            // static nodes over the drawing, so the controls are focusable and labelled
            fun spot(x: Double, y: Double, bw: Double, bh: Double) = Modifier.offset(x.dp, y.dp).size(bw.dp, bh.dp)
            val count = safePages.size
            Box(
                spot(0.0, 0.0, w, max(0.0, panBottom)).semantics(mergeDescendants = true) {
                    contentDescription = "${current.title.replace('\n', ' ')}. ${current.body ?: ""}"
                    stateDescription = "Page ${selected + 1} of $count"
                    progressBarRangeInfo = ProgressBarRangeInfo((selected + 1).toFloat(), 1f..count.toFloat(), count - 2)
                    setProgress { value ->
                        engine.go(Math.round(value).toInt() - 1)
                        true
                    }
                },
            )
            Box(
                spot(skipL, skipT, layout.skipWidth, skipH).semantics(mergeDescendants = true) {
                    role = Role.Button
                    contentDescription = "Skip introduction"
                    onClick { onSkipPress(); true }
                },
            )
            Box(
                spot(Theme.MARGIN * s, layout.footerY, btnW, layout.buttonHeight).semantics(mergeDescendants = true) {
                    role = Role.Button
                    contentDescription = primaryLabel
                    onClick { onPrimary(); true }
                },
            )
            if (secondaryLabel != null) {
                Box(
                    spot(secondX, secondY, btnW, layout.buttonHeight).semantics(mergeDescendants = true) {
                        role = Role.Button
                        contentDescription = secondaryLabel
                        onClick { onSecondary(); true }
                    },
                )
            }
        }
    }
}
