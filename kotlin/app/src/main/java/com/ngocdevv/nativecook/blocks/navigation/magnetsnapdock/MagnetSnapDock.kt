package com.ngocdevv.nativecook.blocks.navigation.magnetsnapdock

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.flow.first
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/*
 Magnet Snap Dock — "Studio", a drawing app whose tool dock is magnetic. Bring a finger near it
 and the six enamel tool tiles lean toward it, the nearest growing to 1.35× while its neighbours
 make room; dashed field lines flow from the tiles to the fingertip. Let go over a tool and it
 clicks down onto the dock's plate, the plate slides under it and the panel above swaps to that
 tool's settings. Move away and the tiles spring home.

 Two canvases: the backdrop (a bitmap rendered once) under the panel, and a live one over it
 (the dock bitmap, plate, tile shadows, up to 18 dashed curves, the six face bitmaps and the
 fingertip), invalidated by one frame counter. autoPlay: LOOP = 8 s, analytic
 (MagnetSnapDockMotion.kt). Compose only recomposes when the selected tool changes.

 Modes: 0 loop · 1 finger down · 2 released (tiles spring home) · 3 back to Pen and the loop
 (2 s after the last touch, 0.9 s).
 */

private val Background = Color(0xFF16181C)
private val Ink = Color(0xFFF1EDE6)
private val InkSoft = Color(0xFF9A9CA3)
private const val RETURN_AFTER = 2.0
private const val RETURN_FOR = 0.9
private const val SPRING_W = 16.0
private const val SPRING_Z = 0.72

private fun nowSeconds() = System.nanoTime() / 1e9

/** Everything the frame loop, the gestures and the drawing share. Plain fields; `frame` invalidates. */
internal class MagnetState(auto: Boolean) {
    val frame = mutableIntStateOf(0)
    val wake = mutableIntStateOf(0)
    /** The selected tool, as state for accessibility only (changes at a snap). */
    val selected = mutableIntStateOf(MAGNET_REST_TOOL)
    var now = -1.0
    var loopStart = 0.0
    var mode = if (auto) 0 else 2
    /** Finger: x, y, strength, target strength. */
    val finger = DoubleArray(4)
    var tilesLive = false
    val tx = DoubleArray(MAGNET_TOOLS)
    val ty = DoubleArray(MAGNET_TOOLS)
    val ts = DoubleArray(MAGNET_TOOLS)
    val vx = DoubleArray(MAGNET_TOOLS)
    val vy = DoubleArray(MAGNET_TOOLS)
    val vs = DoubleArray(MAGNET_TOOLS)
    // What is drawn.
    val x = DoubleArray(MAGNET_TOOLS)
    val y = DoubleArray(MAGNET_TOOLS)
    val sc = DoubleArray(MAGNET_TOOLS)
    val f = DoubleArray(MAGNET_TOOLS)
    var fx = 0.0
    var fy = 0.0
    var ring = 0.0
    var phase = 0.0
    var ready = false
    // Selection: from, to, snap time, and the clock it is read on.
    var selFrom = MAGNET_REST_TOOL
    var selTo = MAGNET_REST_TOOL
    var selAt = -10.0
    var clock = 0.0
    var lastTouch = 0.0
    var returnAt = 0.0
    var nearest = -1
    val scratch = MagnetScratch()
    val fp = DoubleArray(4)
    val sel = DoubleArray(3)
    val targetX = DoubleArray(MAGNET_TOOLS)
    val targetY = DoubleArray(MAGNET_TOOLS)
    val targetS = DoubleArray(MAGNET_TOOLS)

    fun setSelection(from: Int, to: Int, at: Double) {
        selFrom = from
        selTo = to
        selAt = at
        if (selected.intValue != to) selected.intValue = to
    }

    /** True while anything on screen can still move. */
    fun moving(auto: Boolean): Boolean {
        if (mode != 2) return true
        if (auto) return true
        if (finger[2] > 0.002) return true
        if (clock - selAt < 1.2) return true
        for (i in 0 until MAGNET_TOOLS) {
            if (abs(vx[i]) + abs(vy[i]) + abs(vs[i]) > 0.01) return true
            if (abs(tx[i] - targetX[i]) + abs(ty[i] - targetY[i]) > 0.02 || abs(ts[i] - targetS[i]) > 0.0005) return true
        }
        return false
    }
}

/** One frame: the loop's analytic layout, or the touch springs. */
private fun magnetStep(s: MagnetState, g: MagnetGeo, t0: Double, dt: Double, auto: Boolean, reduced: Boolean) {
    if (s.mode == 0) {
        val t = (t0 - s.loopStart).mod(LOOP)
        magnetLoopLayout(g, t, s.x, s.y, s.sc, s.f, s.fp, s.scratch)
        magnetLoopSelection(t, s.sel)
        s.setSelection(s.sel[0].toInt(), s.sel[1].toInt(), s.sel[2])
        s.clock = t
        s.fx = s.fp[0]; s.fy = s.fp[1]; s.ring = s.fp[3]
        s.phase = -((t * 36) % 12)
        s.ready = true
        return
    }
    // Touch: the finger's presence eases in and out; the tiles ride springs to the magnet layout.
    val fg = s.finger
    fg[2] += (fg[3] - fg[2]) * (1 - exp(-dt / 0.08))
    magnetLayout(g, fg[0], fg[1], if (reduced) 0.0 else fg[2], s.targetX, s.targetY, s.targetS, s.f)
    if (!s.tilesLive) {
        for (i in 0 until MAGNET_TOOLS) { s.tx[i] = g.xs[i]; s.ty[i] = g.cy; s.ts[i] = 1.0 }
        s.vx.fill(0.0); s.vy.fill(0.0); s.vs.fill(0.0)
        s.tilesLive = true
    }
    val h = dt / 2
    repeat(2) {
        for (i in 0 until MAGNET_TOOLS) {
            s.vx[i] += (-SPRING_W * SPRING_W * (s.tx[i] - s.targetX[i]) - 2 * SPRING_Z * SPRING_W * s.vx[i]) * h
            s.tx[i] += s.vx[i] * h
            s.vy[i] += (-SPRING_W * SPRING_W * (s.ty[i] - s.targetY[i]) - 2 * SPRING_Z * SPRING_W * s.vy[i]) * h
            s.ty[i] += s.vy[i] * h
            s.vs[i] += (-SPRING_W * SPRING_W * (s.ts[i] - s.targetS[i]) - 2 * SPRING_Z * SPRING_W * s.vs[i]) * h
            s.ts[i] += s.vs[i] * h
        }
    }
    for (i in 0 until MAGNET_TOOLS) { s.x[i] = s.tx[i]; s.y[i] = s.ty[i]; s.sc[i] = s.ts[i] }
    if (s.mode == 2 && auto && t0 - s.lastTouch > RETURN_AFTER) {
        s.mode = 3
        s.returnAt = t0
        if (s.selTo != MAGNET_REST_TOOL) s.setSelection(s.selTo, MAGNET_REST_TOOL, t0)
    } else if (s.mode == 3 && t0 - s.returnAt > RETURN_FOR) {
        s.mode = 0
        s.loopStart = t0
        s.tilesLive = false
    }
    s.clock = t0
    s.fx = fg[0]; s.fy = fg[1]; s.ring = fg[2]
    s.phase = -((t0 * 36) % 12)
    s.ready = true
}

/** Freezes the loop's tiles, finger and selection into the touch timeline. */
private fun magnetTakeOver(s: MagnetState, t0: Double) {
    if (s.mode != 0) return
    for (i in 0 until MAGNET_TOOLS) {
        s.tx[i] = s.x[i]; s.ty[i] = s.y[i]; s.ts[i] = s.sc[i]
    }
    s.vx.fill(0.0); s.vy.fill(0.0); s.vs.fill(0.0)
    s.tilesLive = true
    val t = (t0 - s.loopStart).mod(LOOP)
    magnetLoopSelection(t, s.sel)
    s.setSelection(s.sel[0].toInt(), s.sel[1].toInt(), t0 - (t - s.sel[2]))
    s.finger[0] = s.fx; s.finger[1] = s.fy; s.finger[2] = s.ring; s.finger[3] = 0.0
}

@Composable
fun MagnetSnapDock(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val reduced = rememberReducedMotion()
    val auto = autoPlay && !reduced
    val state = remember(auto) { MagnetState(auto) }
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current.density
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val insetTop = insets.calculateTopPadding().value
    val insetBottom = insets.calculateBottomPadding().value

    BoxWithConstraints(modifier.fillMaxSize().background(Background).clipToBounds()) {
        val w = maxWidth.value
        val h = maxHeight.value
        val g = remember(w, h, insetTop, insetBottom) { MagnetGeo(w, h, insetTop, insetBottom) }
        val backdrop = remember(g, density) { magnetRenderBackdrop(g, density).asImageBitmap() }
        val dock = remember(g, density) { magnetRenderDock(g, density).asImageBitmap() }
        val faces = remember(density) { magnetRenderFaces(density) }
        val painter = remember { MagnetPainter() }

        LaunchedEffect(state, g, auto, reduced) {
            while (true) {
                withFrameNanos { nanos ->
                    val t0 = nanos / 1e9
                    val dt = if (state.now < 0) 1.0 / 60 else min(t0 - state.now, 1.0 / 30)
                    if (state.now < 0 && state.mode == 0) state.loopStart = t0
                    state.now = t0
                    magnetStep(state, g, t0, dt, auto, reduced)
                    state.frame.intValue++
                }
                if (!state.moving(auto)) {
                    // At rest: sleep until a touch wakes the loop.
                    val seen = state.wake.intValue
                    snapshotFlow { state.wake.intValue }.first { it != seen }
                    state.now = -1.0
                }
            }
        }

        Canvas(Modifier.fillMaxSize()) { drawImage(backdrop) }

        // Header: the app, the document, and a share button.
        Row(
            Modifier
                .padding(start = 20.dp, end = 20.dp, top = (insetTop + 12f).dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("STUDIO", color = Color(0xFFC4532F), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.4.sp)
                Text("Harbor study", color = Ink, fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp)
                Text("Autosaved 2 min ago · 6 layers", color = InkSoft, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
            val source = remember { MutableInteractionSource() }
            val pressed by source.collectIsPressedAsState()
            val press by animateFloatAsState(if (pressed) 1f else 0f, tween(130), label = "share")
            Box(
                Modifier
                    .padding(top = 14.dp)
                    .size(40.dp)
                    .graphicsLayer {
                        val k = 1f - 0.03f * press
                        scaleX = k
                        scaleY = k
                        alpha = 1f - 0.15f * press
                    }
                    .background(Color(0x14FFFFFF), CircleShape)
                    .drawBehind { drawArc(Color(0x14FFFFFF), 200f, 140f, false, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx())) }
                    .clickable(source, indication = null, role = Role.Button) {}
                    .semantics { contentDescription = "Share" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.IosShare, null, tint = Ink, modifier = Modifier.size(18.dp))
            }
        }

        MagnetSnapDockPanel(state, g.panelTop, g.panelH)

        // The live canvas over the panel; touches land on the area from the artboard down.
        Canvas(Modifier.fillMaxSize()) {
            state.frame.intValue
            if (!state.ready) return@Canvas
            drawImage(dock)
            drawIntoCanvas { canvas ->
                val c = canvas.nativeCanvas
                c.save()
                c.scale(density, density)
                val s = state
                val px = if (reduced) g.xs[s.selTo] else magnetPlateX(g, s.selFrom, s.selTo, s.selAt, s.clock)
                painter.plate(c, g, px.toFloat(), MagnetToolColors[s.selTo])
                val clickY = if (reduced) 0.0 else magnetClickY(s.clock - s.selAt)
                val clickS = if (reduced) 1.0 else magnetClickScale(s.clock - s.selAt)
                for (i in 0 until MAGNET_TOOLS) {
                    val yi = s.y[i] + if (i == s.selTo) clickY else 0.0
                    val si = s.sc[i] * if (i == s.selTo) clickS else 1.0
                    painter.tileShadow(c, s.x[i].toFloat(), yi.toFloat(), si.toFloat(), (max(0.0, g.cy - yi) + (si - 1) * 24).toFloat())
                }
                painter.field(c, s.x, s.y, s.sc, s.f, s.fx.toFloat(), s.fy.toFloat(), s.phase.toFloat())
                for (i in 0 until MAGNET_TOOLS) {
                    val yi = s.y[i] + if (i == s.selTo) clickY else 0.0
                    val si = s.sc[i] * if (i == s.selTo) clickS else 1.0
                    painter.face(c, faces[i], s.x[i].toFloat(), yi.toFloat(), si.toFloat(), density)
                }
                painter.finger(c, s.fx.toFloat(), s.fy.toFloat(), s.ring.toFloat())
                c.restore()
            }
        }

        val names = MagnetToolNames
        Box(
            Modifier
                .offset(y = g.boardTop.dp)
                .fillMaxSize()
                .semantics { contentDescription = "Tool dock, ${names[state.selected.intValue]} selected. Drag near the dock and let go on a tool." }
                .pointerInput(state, g) {
                    fun nearestTile(x: Double): Int {
                        var best = 0
                        for (i in 1 until MAGNET_TOOLS) if (abs(g.xs[i] - x) < abs(g.xs[best] - x)) best = i
                        return best
                    }
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val t0 = nowSeconds()
                        magnetTakeOver(state, t0)
                        state.mode = 1
                        var ex = down.position.x / density.toDouble()
                        var ey = down.position.y / density.toDouble() + g.boardTop
                        state.finger[0] = ex; state.finger[1] = ey; state.finger[3] = 1.0
                        state.nearest = nearestTile(ex)
                        state.lastTouch = t0
                        state.wake.intValue++
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            ex = change.position.x / density.toDouble()
                            ey = change.position.y / density.toDouble() + g.boardTop
                            if (!change.pressed) break
                            change.consume()
                            state.finger[0] = ex; state.finger[1] = ey; state.finger[3] = 1.0
                            val n = nearestTile(ex)
                            if (n != state.nearest && ey > g.cy - 170) {
                                state.nearest = n
                                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            }
                            state.lastTouch = nowSeconds()
                        }
                        if (state.mode == 1) {
                            val n = nearestTile(ex)
                            val cx = if (state.tilesLive) state.tx[n] else g.xs[n]
                            val cy = if (state.tilesLive) state.ty[n] else g.cy
                            val reach = 30 * (if (state.tilesLive) state.ts[n] else 1.0)
                            val now = nowSeconds()
                            if (abs(ex - cx) < reach && abs(ey - cy) < reach + 8 && n != state.selTo) {
                                state.setSelection(state.selTo, n, now)
                                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                            }
                            state.finger[3] = 0.0
                            state.mode = 2
                            state.lastTouch = now
                            state.wake.intValue++
                        }
                    }
                },
        )
    }
}
