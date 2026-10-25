package com.ngocdevv.nativecook.blocks.gestures.abacusexpensecounter

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.flow.first
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/*
 Abacus Expense Counter — "Tally", a weekly budget logged on a wooden abacus. Each rod is a
 category, each lacquered bead one purchase at that category's price; flick a bead to the right
 to log it. Beads slide with momentum and friction, knock into their neighbours and bounce off
 the frame with a light click; the rod's amount, the week's total, what is left and the daily
 pace roll to the new numbers, and each category's meter springs.

 One Canvas (AbacusExpenseCounterArt.kt: the board and one bead per colour rendered once into
 bitmaps; 100 bitmap draws per frame). Beads: AbacusExpenseCounterMotion.kt, analytic in autoPlay
 (LOOP = 8 s), a small physics step under the finger. Amounts are digit wheels driven from
 snapshotFlows (AbacusExpenseCounterRolling.kt), so nothing recomposes while beads move.

 Modes: 0 loop · 1 touch physics · 2 gliding back to rest (0.9 s ease-in-out: "Undo today", or
 2 s after the last touch once the beads have settled), then the loop restarts.
 */

private val Background = Color(0xFF16110D)
private val Ink = Color(0xFFF4ECE0)
private val InkSoft = Color(0xFFAD9E8E)
private val Accent = Color(0xFFD9A441)
private const val RETURN_AFTER = 2.0
private const val RETURN_FOR = 0.9
private const val HEADER_H = 128f
private const val TILES_H = 70f
/** Room kept under the abacus: the two tiles, the week strip and the hint. */
private const val FOOTER_H = 20f + TILES_H + 12f + ABACUS_WEEK_H + 44f
private val RestCents = IntArray(ABACUS_RODS) { AbacusRestCounts[it] * AbacusPrices[it] }
private val RestTotal = RestCents.sum()

private fun nowSeconds() = System.nanoTime() / 1e9

private class AbacusState(auto: Boolean) {
    val frame = mutableIntStateOf(0)
    val wake = mutableIntStateOf(0)
    /** Amounts, written only when a count changes: they drive the wheels and meters. */
    val rodCents = Array(ABACUS_RODS) { mutableIntStateOf(RestCents[it]) }
    val total = mutableIntStateOf(RestTotal)
    var now = -1.0
    var loopStart = 0.0
    var mode = if (auto) 0 else 1
    var ready = false
    val x = DoubleArray(ABACUS_RODS * ABACUS_BEADS)
    val v = DoubleArray(ABACUS_RODS * ABACUS_BEADS)
    val rest = DoubleArray(ABACUS_RODS * ABACUS_BEADS)
    var heldRod = -1
    var heldBead = 0
    var heldX = 0.0
    var grabOffset = 0.0
    val finger = DoubleArray(3)
    var undoPress = 0.0
    var lastTouch = 0.0
    var lastClick = 0.0
    var glideStart = 0.0
    val glideFrom = DoubleArray(ABACUS_RODS * ABACUS_BEADS)

    fun startGlide(t: Double) {
        System.arraycopy(x, 0, glideFrom, 0, x.size)
        glideStart = t
        heldRod = -1
        mode = 2
    }
}

/** One frame; returns true when a bead knocked hard enough for a click. */
private fun abacusStep(s: AbacusState, g: AbacusGeo, t0: Double, dt: Double, auto: Boolean, reduced: Boolean): Boolean {
    var click = false
    if (!s.ready) {
        abacusRestBeads(g, s.x)
        abacusRestBeads(g, s.rest)
        s.ready = true
    }
    when (s.mode) {
        0 -> {
            val t = (t0 - s.loopStart).mod(LOOP)
            abacusLoopBeads(g, t, s.x)
            abacusLoopFinger(g, t, s.finger)
            s.undoPress = abacusLoopUndoPress(t)
        }
        1 -> {
            val hit = abacusStepBeads(g, s.x, s.v, s.heldRod, s.heldBead, s.heldX, dt)
            if (hit > 0 && t0 - s.lastClick > 0.1) {
                s.lastClick = t0
                click = true
            }
            s.finger.fill(0.0)
            s.undoPress = 0.0
            if (auto && s.heldRod < 0 && t0 - s.lastTouch > RETURN_AFTER && abacusSettled(g, s.x, s.v)) s.startGlide(t0)
        }
        else -> {
            s.finger.fill(0.0)
            s.undoPress = 0.0
            val k = abacusEaseInOut(abacusSeg(t0 - s.glideStart, 0.0, if (reduced) 0.3 else RETURN_FOR))
            for (i in s.x.indices) s.x[i] = s.glideFrom[i] + (s.rest[i] - s.glideFrom[i]) * k
            if (k >= 1) {
                s.v.fill(0.0)
                if (auto && t0 - s.lastTouch > RETURN_AFTER) {
                    s.mode = 0
                    s.loopStart = t0
                } else {
                    s.mode = 1
                }
            }
        }
    }
    // Amounts: only written when a count changes, so the wheels roll once per bead.
    var sum = 0
    for (r in 0 until ABACUS_RODS) {
        val c = abacusCount(g, s.x, r) * AbacusPrices[r]
        sum += c
        if (s.rodCents[r].intValue != c) s.rodCents[r].intValue = c
    }
    if (s.total.intValue != sum) s.total.intValue = sum
    return click
}

private fun colorOf(hex: Int) = Color(0xFF000000.toInt() or hex)

@Composable
fun AbacusExpenseCounter(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val reduced = rememberReducedMotion()
    val auto = autoPlay && !reduced
    val state = remember(auto) { AbacusState(auto) }
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current.density
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val top = insets.calculateTopPadding().value
    val bottom = max(insets.calculateBottomPadding().value, 12f)
    val boardY = top + 12f + HEADER_H

    BoxWithConstraints(modifier.fillMaxSize().background(Background).clipToBounds()) {
        val w = maxWidth.value
        val h = maxHeight.value
        val g = remember(w, h, boardY, bottom) { AbacusGeo(w, h, boardY, FOOTER_H + bottom) }
        val board = remember(g, density) { abacusRenderBoard(g, density).asImageBitmap() }
        val beads = remember(g, density) { AbacusBeadColors.map { abacusRenderBead(g, it, density) } }
        val shadow = remember(g, density) { abacusRenderBeadShadow(g, density) }
        val painter = remember(g) { AbacusPainter(g) }

        LaunchedEffect(state, g, auto, reduced) {
            state.ready = false
            while (true) {
                withFrameNanos { nanos ->
                    val t0 = nanos / 1e9
                    val dt = if (state.now < 0) 1.0 / 60 else min(t0 - state.now, 1.0 / 30)
                    if (state.now < 0 && state.mode == 0) state.loopStart = t0
                    state.now = t0
                    if (abacusStep(state, g, t0, dt, auto, reduced)) haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                    state.frame.intValue++
                }
                if (state.mode == 1 && !auto && state.heldRod < 0 && abacusSettled(g, state.x, state.v)) {
                    // At rest: sleep until a touch wakes the loop.
                    val seen = state.wake.intValue
                    snapshotFlow { state.wake.intValue }.first { it != seen }
                    state.now = -1.0
                }
            }
        }

        Canvas(Modifier.fillMaxSize()) {
            state.frame.intValue
            drawImage(board)
            if (!state.ready) return@Canvas
            drawIntoCanvas { canvas ->
                val c = canvas.nativeCanvas
                c.save()
                c.scale(density, density)
                painter.draw(c, beads, shadow, state.x, state.finger[0].toFloat(), state.finger[1].toFloat(), state.finger[2].toFloat(), density)
                c.restore()
            }
        }

        // Header: the week, "Undo today", the rolling total and the budget meter.
        Column(
            Modifier
                .offset(y = (top + 12f).dp)
                .padding(horizontal = 20.dp)
                .fillMaxWidth()
                .height(HEADER_H.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(Modifier.fillMaxWidth().height(30.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("TALLY · WEEK 43", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp)
                val source = remember { MutableInteractionSource() }
                val pressed by source.collectIsPressedAsState()
                Box(
                    Modifier
                        .height(30.dp)
                        .graphicsLayer {
                            state.frame.intValue
                            val k = 1f - 0.05f * state.undoPress.toFloat()
                            scaleX = k
                            scaleY = k
                            alpha = if (pressed) 0.85f else 1f
                        }
                        .drawBehind {
                            state.frame.intValue
                            drawRoundRect(Accent.copy(alpha = 0.14f + 0.16f * state.undoPress.toFloat()), cornerRadius = CornerRadius(size.height / 2))
                        }
                        .border(0.5.dp, Accent.copy(alpha = 0.45f), RoundedCornerShape(15.dp))
                        .clickable(source, indication = null, role = Role.Button) {
                            state.lastTouch = nowSeconds()
                            state.startGlide(nowSeconds())
                            state.wake.intValue++
                            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                        }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Undo today", color = Color(0xFFF1D79E), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Text("Weekly spending", color = InkSoft, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 2.dp))
            AbacusRollingAmount({ state.total.intValue }, RestTotal, 44f, Ink, reduced = reduced)
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AbacusMeter({ state.total.intValue / ABACUS_WEEK_BUDGET.toFloat() }, Accent, 6f, reduced, Modifier.weight(1f))
                Text("of $520 · Oct 20 – 26", color = InkSoft, fontSize = 13.sp, fontWeight = FontWeight.Medium, style = TextStyle(fontFeatureSettings = "tnum"))
            }
        }

        // The board's touch area.
        Box(
            Modifier
                .offset(x = g.boardX.dp, y = g.boardY.dp)
                .size(g.boardW.dp, g.boardH.dp)
                .semantics { contentDescription = "Abacus. Flick a bead right to log a purchase, push it back to remove it." }
                .pointerInput(state, g, reduced) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (state.mode != 1) {
                            state.mode = 1
                            state.v.fill(0.0)
                        }
                        state.lastTouch = nowSeconds()
                        state.wake.intValue++
                        val x = down.position.x / density + g.boardX
                        val y = down.position.y / density + g.boardY
                        var r = 0
                        for (k in 1 until ABACUS_RODS) if (abs(g.rodY[k] - y) < abs(g.rodY[r] - y)) r = k
                        var best = -1
                        if (abs(g.rodY[r] - y) <= g.band * 0.55f) {
                            var bestD = g.bw * 0.85
                            for (j in 0 until ABACUS_BEADS) {
                                val d = abs(state.x[r * ABACUS_BEADS + j] - x)
                                if (d < bestD) { bestD = d; best = j }
                            }
                        }
                        if (best >= 0) {
                            state.grabOffset = x - state.x[r * ABACUS_BEADS + best]
                            state.heldRod = r
                            state.heldBead = best
                            state.heldX = state.x[r * ABACUS_BEADS + best]
                        }
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            tracker.addPosition(change.uptimeMillis, change.position)
                            if (!change.pressed) break
                            change.consume()
                            state.lastTouch = nowSeconds()
                            if (state.heldRod >= 0) state.heldX = change.position.x / density + g.boardX - state.grabOffset
                        }
                        state.lastTouch = nowSeconds()
                        if (state.heldRod >= 0) {
                            // The flick: the bead leaves with the finger's speed (the push chain follows by contact).
                            val i = state.heldRod * ABACUS_BEADS + state.heldBead
                            state.heldRod = -1
                            state.v[i] = if (reduced) 0.0 else (tracker.calculateVelocity().x / density).toDouble().coerceIn(-2200.0, 2200.0)
                            state.wake.intValue++
                        }
                    }
                },
        )

        // Each rod's name, meter and amount, along the top of its band.
        for (r in 0 until ABACUS_RODS) {
            val color = colorOf(AbacusBeadColors[r])
            Row(
                Modifier
                    .offset(x = g.innerL.dp, y = (g.boardY + g.frame + r * g.band + 4f).dp)
                    .width((g.innerR - g.innerL).dp)
                    .height(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(Modifier.size(7.dp).background(color, CircleShape))
                Text(AbacusCategories[r], color = Color(0xFFE9DCCB), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.width(58.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    AbacusMeter({ state.rodCents[r].intValue / AbacusBudgets[r].toFloat() }, color, 3f, reduced, Modifier.widthIn(max = 120.dp).fillMaxWidth())
                }
                AbacusRollingAmount({ state.rodCents[r].intValue }, RestCents[r], 13f, Ink, FontWeight.SemiBold, reduced = reduced)
            }
        }

        // Two tiles: what is left this week, and the daily pace.
        val footerTop = g.boardY + g.boardH + 20f
        Row(
            Modifier
                .offset(y = footerTop.dp)
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .height(TILES_H.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Tile("Left this week", Modifier.weight(1f)) {
                AbacusRollingAmount({ max(0, ABACUS_WEEK_BUDGET - state.total.intValue) }, ABACUS_WEEK_BUDGET - RestTotal, 20f, Ink, reduced = reduced)
            }
            Tile("Daily pace", Modifier.weight(1f)) {
                AbacusRollingAmount({ (state.total.intValue / 7.0).roundToInt() }, (RestTotal / 7.0).roundToInt(), 20f, Ink, intDigits = 2, reduced = reduced)
            }
        }

        val weekTop = footerTop + TILES_H + 12f
        if (weekTop + ABACUS_WEEK_H <= h - bottom - 34f) {
            Box(Modifier.offset(y = weekTop.dp).padding(horizontal = 16.dp).fillMaxWidth()) {
                AbacusWeek({ state.total.intValue }, RestTotal, Accent, reduced)
            }
        }

        Text(
            "Flick a bead right to log it · push it back to remove it",
            color = Ink.copy(alpha = 0.45f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 24.dp, end = 24.dp, bottom = (bottom + 10f).dp)
                .fillMaxWidth(),
        )
    }
}

@Composable
private fun Tile(label: String, modifier: Modifier, amount: @Composable () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier
            .fillMaxSize()
            .background(Color(0x0DF4ECE0), shape)
            .border(0.5.dp, Color(0x1AF4ECE0), shape)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, color = InkSoft, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        amount()
    }
}
