package com.ngocdevv.nativecook.blocks.gestures.trafficjamshake

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotateRad
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion
import kotlinx.coroutines.delay as wait
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

// A commute card for an invented app, Laneway: "Heavy traffic on Ring Rd". Its hero is a flat pile
// of chunky extruded cars that wobble, lean, stretch and bump each other, each on its own spring
// (see TrafficJamShakeSim). Drag anywhere to shake the jam; tap a car to make it hop and honk.
// A port of TrafficJamShake.swift, number for number.
//
// autoPlay timeline (LOOP = 8 s), the simulation stepped from rest at t = 0:
//   0.00-0.75  rest (a 0.35 pt engine-idle tremble)
//   0.75-0.95  a ghost finger lands on the red front car
//   0.95       tap: the red car hops 28 pt and honks; it lands at ~1.25 s and bumps its neighbours
//   1.70-1.95  the ghost finger moves to the pile's centre
//   1.95-5.00  it shakes the ground left-right (0.66 s swings, up to 10 pt, smoothstep envelope
//              in over 1.95-2.6 s and out over 4.45-5.0 s); the finger fades out over 5.0-5.25 s
//   2.80-4.80  peak: cars lag, lean, stretch into limos, bump, honk and hop
//   5.00-6.40  the pile settles on its springs
//   6.40-7.20  what is left fades out (smoothstep), so the frame is exactly the rest frame
//   7.20-8.00  rest, identical to t = 0

private const val MARGIN = 20f
/** Bottom card: 18 + row 36 + 14 + bar 10 + 8 + labels 16 + 18 + button 52 + 18. */
private const val CARD_HEIGHT = 190f
/** Top copy below the safe area: 8 + header 32 + 18 + label 16 + 6 + title 76 + 8 + subtitle 20. */
private const val TOP_HEIGHT = 184f

/**
 * @param autoPlay Shakes the jam by itself, every [TrafficJamShakeMotion.LOOP] seconds.
 */
@Composable
fun TrafficJamShake(
    modifier: Modifier = Modifier,
    app: String = "Laneway",
    route: String = "Home → Studio",
    label: String = "MORNING COMMUTE",
    title: String = "Heavy traffic\non Ring Rd",
    subtitle: String = "Leave at 8:40 to make your 9:15.",
    hint: String = "Shake the jam · tap a car to honk",
    duration: String = "34 min",
    delay: String = "+12 min",
    arrival: String = "Arrive 9:14",
    action: String = "Remind me at 8:35",
    autoPlay: Boolean = false,
) {
    val m = TrafficJamShakeMotion
    val p = TrafficJamShakePalette
    BlockSystemBars(darkIcons = true)
    val view = LocalView.current
    val density = LocalDensity.current
    val reducedMotion = rememberLiveReducedMotion()
    val reduced by rememberUpdatedState(reducedMotion)
    val auto by rememberUpdatedState(autoPlay)

    val sim = remember { TrafficJamShakeSim() }
    val painter = remember { TrafficJamShakePainter() }
    // The frame clock, in nanoseconds; read only in the draw block.
    val clock = remember { mutableLongStateOf(System.nanoTime()) }
    var origin by remember { mutableLongStateOf(System.nanoTime()) }
    // True from a touch until the pile has settled and the loop has started over.
    var manual by remember { mutableStateOf(false) }
    // When manual mode started (ns), and the simulation time then.
    var manualStart by remember { mutableLongStateOf(0L) }
    var manualBase by remember { mutableStateOf(0.0) }
    // When the residual of a touch started to fade out (0: not fading).
    var settleStart by remember { mutableLongStateOf(0L) }
    var dragging by remember { mutableStateOf(false) }
    // Honks under reduced motion (no simulation): car index and when (ns).
    var stillHonkCar by remember { mutableIntStateOf(-1) }
    var stillHonkAt by remember { mutableLongStateOf(0L) }
    // Changes on every touch, which restarts the loop behind a pause.
    var touches by remember { mutableIntStateOf(0) }
    val haptics = remember { object { var lastBumps = 0; var lastHaptic = 0L } }

    val animating = (!reducedMotion && autoPlay) || manual || stillHonkCar >= 0
    LaunchedEffect(animating) {
        if (!animating) return@LaunchedEffect
        while (true) withFrameNanos { clock.longValue = it.coerceAtLeast(clock.longValue) }
    }

    // 2 s after the last touch the pile's residual fades out and the loop starts over from rest.
    LaunchedEffect(touches) {
        if (touches == 0) return@LaunchedEffect
        wait((m.USER_HOLD * 1000).toLong())
        if (dragging) return@LaunchedEffect
        stillHonkCar = -1
        if (!manual) return@LaunchedEffect
        settleStart = System.nanoTime()
        wait((m.SETTLE * 1000).toLong())
        if (dragging) return@LaunchedEffect
        settleStart = 0L
        sim.reset()
        origin = System.nanoTime()
        manual = false
    }

    /** Advances the simulation to [now]: the factor its deviations are drawn with, and the loop time (-1 out of the loop). */
    fun advance(now: Long): Pair<Double, Double> {
        if (manual) {
            sim.run(manualBase + (now - manualStart) / 1e9, 0.25)
            if (settleStart != 0L) return (1 - m.smooth((now - settleStart) / 1e9, 0.0, m.SETTLE)) to -1.0
            return 1.0 to -1.0
        }
        if (!auto || reduced) return 0.0 to -1.0
        val t = (((now - origin) / 1e9) % m.LOOP).coerceAtLeast(0.0)
        if (!sim.scripted || t + m.DT < sim.time) sim.reset()
        sim.run(t)
        return m.window(t) to t
    }

    /** Hands the pile to the finger: from here the simulation runs on real time. */
    fun takeOver(now: Long) {
        if (!manual) {
            val (k, _) = advance(now)
            sim.scale(k)
            sim.scripted = false
            sim.tx = 0.0
            sim.ty = 0.0
            manualBase = sim.time
            manualStart = now
            haptics.lastBumps = sim.bumps
            manual = true
        }
        settleStart = 0L
    }

    val insets = WindowInsets.safeDrawing.asPaddingValues()

    BoxWithConstraints(modifier.fillMaxSize().background(p.background)) {
        val d = density.density
        val widthDp = constraints.maxWidth / d
        val heightDp = constraints.maxHeight / d
        val safeTop = insets.calculateTopPadding().value
        val safeBottom = insets.calculateBottomPadding().value
        val cardTop = heightDp - safeBottom - 16 - CARD_HEIGHT
        val heroTop = safeTop + TOP_HEIGHT + 12
        val heroBottom = cardTop - 14 - 16 - 12
        val b = m.bounds
        val bw = b[2] - b[0]
        val bh = b[3] - b[1]
        // Where the pile sits: design point → dp. Centred, bottom-aligned (room above for hops).
        val s = minOf(1.1, (widthDp - 24) / bw, (heroBottom - heroTop - 30) / bh)
        val ox = widthDp / 2 - (b[0] + b[2]) / 2 * s
        val oy = heroBottom - b[3] * s

        /** The car under a point in dp, front to back. */
        fun carIndex(x0: Double, y0: Double): Int {
            val x = (x0 - ox) / s
            val y = (y0 - oy) / s
            for (i in m.cars.indices.reversed()) {
                val c = m.cars[i]
                val ax = c.x + sim.px[i]
                val ay = c.y + sim.py[i] - sim.z[i]
                val top = -(c.hb + c.hc) + 0.82 * c.l * m.dirY
                if (x >= ax - c.w / 2 && x <= ax + c.w / 2 + c.l * m.dirX && y >= ay + top && y <= ay + 4) {
                    // Inside the box's sloping outline: below the roof line that rises to the right.
                    val rel = (x - (ax + c.w / 2)) / (c.l * m.dirX)
                    val roof = ay - c.hb - c.hc + max(0.0, rel) * c.l * m.dirY * 0.82
                    val floor = ay + max(0.0, rel) * c.l * m.dirY + 4
                    if (y >= roof && y <= floor) return i
                }
            }
            return -1
        }

        Column(Modifier.fillMaxSize().padding(horizontal = MARGIN.dp)) {
            Row(
                Modifier.padding(top = (safeTop + 8).dp).fillMaxWidth().height(32.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(app, style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = p.ink))
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.height(28.dp).background(p.card, CircleShape).padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(route, style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = p.ink))
                }
            }
            Text(
                label,
                modifier = Modifier.padding(top = 18.dp).height(16.dp),
                style = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp, color = p.ink.copy(alpha = 0.5f)),
            )
            Text(
                title,
                modifier = Modifier.padding(top = 6.dp).height(76.dp),
                style = TextStyle(fontSize = 34.sp, lineHeight = 38.sp, fontWeight = FontWeight.ExtraBold, color = p.ink),
            )
            Text(
                subtitle,
                modifier = Modifier.padding(top = 8.dp).height(20.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(fontSize = 16.sp, lineHeight = 20.sp, color = p.ink.copy(alpha = 0.62f)),
            )

            Spacer(Modifier.weight(1f))

            Text(
                hint,
                modifier = Modifier.fillMaxWidth().height(16.dp),
                textAlign = TextAlign.Center,
                style = TextStyle(fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, color = p.ink.copy(alpha = 0.45f)),
            )
            Column(
                Modifier
                    .padding(top = 14.dp, bottom = (safeBottom + 16).dp)
                    .padding(horizontal = (16 - MARGIN).coerceAtLeast(0f).dp)
                    .fillMaxWidth()
                    .height(CARD_HEIGHT.dp)
                    .background(p.card, RoundedCornerShape(26.dp))
                    .padding(18.dp),
            ) {
                Row(Modifier.fillMaxWidth().height(36.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(duration, style = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = p.ink))
                    Box(
                        Modifier.padding(start = 10.dp).height(24.dp).background(p.redSoft, CircleShape).padding(horizontal = 9.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(delay, style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = p.red))
                    }
                    Spacer(Modifier.weight(1f))
                    Text(arrival, style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = p.ink.copy(alpha = 0.55f)))
                }
                BoxWithConstraints(Modifier.padding(top = 14.dp).fillMaxWidth().height(10.dp)) {
                    val segments = listOf(0.22f to p.green, 0.14f to p.orange, 0.30f to p.jam, 0.12f to p.orange, 0.22f to p.green)
                    val free = maxWidth - (2 * (segments.size - 1)).dp
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        for ((f, color) in segments) {
                            Box(Modifier.width(free * f).height(10.dp).background(color, CircleShape))
                        }
                    }
                }
                Box(Modifier.padding(top = 8.dp).fillMaxWidth().height(16.dp)) {
                    val small = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, color = p.ink.copy(alpha = 0.5f))
                    Text("Elm Bridge", modifier = Modifier.align(Alignment.CenterStart), style = small)
                    Text("Ring Rd · 2.4 km jam", modifier = Modifier.align(Alignment.Center), style = small)
                    Text("Studio", modifier = Modifier.align(Alignment.CenterEnd), style = small)
                }
                Box(
                    Modifier
                        .padding(top = 18.dp)
                        .fillMaxWidth()
                        .height(52.dp)
                        .background(p.ink, CircleShape)
                        .semantics { role = Role.Button },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(action, style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White))
                }
            }
        }

        Canvas(
            Modifier
                .fillMaxSize()
                .semantics { contentDescription = "A jam of eight cartoon cars" }
                .pointerInput(reducedMotion, s, ox, oy) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val start = System.nanoTime()
                        dragging = true
                        touches++
                        var moved = false
                        // Rubber-banded: the ground follows the finger less and less.
                        fun band(v: Double) = 0.6 * v / (1 + abs(v) / 60)
                        var last = down.position
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            last = change.position
                            val tx = (change.position.x - down.position.x) / d
                            val ty = (change.position.y - down.position.y) / d
                            if (!moved && hypot(tx, ty) > 8) {
                                moved = true
                                if (!reduced) {
                                    takeOver(System.nanoTime())
                                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                                }
                            }
                            if (moved && !reduced) {
                                change.consume()
                                sim.tx = band(tx.toDouble()) / s
                                sim.ty = band(ty.toDouble()) / s
                            }
                        }
                        val now = System.nanoTime()
                        dragging = false
                        if (!moved && (now - start) < 350_000_000L) {
                            val i = carIndex((last.x / d).toDouble(), (last.y / d).toDouble())
                            if (i >= 0) {
                                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                                if (reduced) {
                                    stillHonkCar = i
                                    stillHonkAt = now
                                } else {
                                    takeOver(now)
                                    sim.run(manualBase + (now - manualStart) / 1e9, 0.25)
                                    sim.tap(i)
                                }
                            }
                        }
                        sim.tx = 0.0
                        sim.ty = 0.0
                        touches++
                    }
                },
        ) {
            val now = clock.longValue
            val (k, loopTime) = advance(now)
            with(painter) {
                scale(d, d, pivot = Offset.Zero) {
                    translate(ox.toFloat(), oy.toFloat()) {
                        scale(s.toFloat(), s.toFloat(), pivot = Offset.Zero) {
                            for ((i, c) in m.cars.withIndex()) {
                                val tremble = if (loopTime >= 0) m.tremble(i, loopTime) else 0.0
                                translate((c.x + sim.px[i] * k).toFloat(), (c.y + sim.py[i] * k + tremble).toFloat()) {
                                    rotateRad((c.angle + sim.a[i] * k).toFloat(), pivot = Offset.Zero) {
                                        car(c, sim.e[i] * k, sim.z[i] * k)
                                    }
                                }
                            }
                            for ((i, c) in m.cars.withIndex()) {
                                translate((c.x + sim.px[i] * k).toFloat(), (c.y + sim.py[i] * k).toFloat()) {
                                    rotateRad((c.angle + sim.a[i] * k).toFloat(), pivot = Offset.Zero) {
                                        if (stillHonkCar == i) {
                                            honk(c, 0.0, 0.0, (now - stillHonkAt) / 1e9, 1.0)
                                        } else if (manual || loopTime >= 0) {
                                            honk(c, sim.e[i] * k, sim.z[i] * k, sim.honk[i], k)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // The ghost finger of the loop.
                    if (loopTime >= 0) {
                        val f = m.finger(loopTime)
                        if (f.opacity > 0.001) {
                            val cx: Double
                            val cy: Double
                            if (f.onTap) {
                                val c = m.cars[m.TAP_CAR]
                                cx = ox + (c.x + 0.1 * c.w) * s
                                cy = oy + (c.y - c.hb * 0.6) * s
                            } else {
                                cx = ox + ((b[0] + b[2]) / 2 + 3 * m.targetX(loopTime)) * s
                                cy = oy + ((b[1] + b[3]) / 2 + 3 * m.targetY(loopTime)) * s
                            }
                            val r = (21 * f.scale).toFloat()
                            val centre = Offset(cx.toFloat(), cy.toFloat())
                            drawCircle(p.ink.copy(alpha = (0.1 * f.opacity).toFloat()), r, centre)
                            drawCircle(p.ink.copy(alpha = (0.5 * f.opacity).toFloat()), r, centre, style = Stroke(1.5f))
                        }
                    }
                }
            }

            if (manual && sim.bumps != haptics.lastBumps) {
                haptics.lastBumps = sim.bumps
                if (now - haptics.lastHaptic > 250_000_000L) {
                    haptics.lastHaptic = now
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                }
            }
        }
    }
}
