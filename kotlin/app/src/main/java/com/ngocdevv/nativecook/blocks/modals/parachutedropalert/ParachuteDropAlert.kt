package com.ngocdevv.nativecook.blocks.modals.parachutedropalert

import android.os.Build
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 Parachute Drop Alert — "Parcelpoint", a delivery app whose "your parcel is 4 minutes away" alert
 arrives by parachute. A port of ParachuteDropAlert.tsx (the reference); the numbers are the same.

 The card drops in from above the screen under a coral-and-cream canopy, swinging like a pendulum
 and drifting, lands on the screen with a squash; the canopy deflates, drapes over the card's top
 edge and the wind pulls it away. "Track" and "Later" both send it off: a fresh canopy blooms out
 of its top and lifts it away (ParachuteDropAlertMotion.kt has the timeline). Drag the card and it
 swings from its lines like a pendulum (g 2000 dp/s², 200 dp rope) under the finger; let go and it
 springs back to its place (8 rad/s, damping 0.6) while the swing dies out. 2 s after the last
 touch it lifts away and the loop starts again.
 Reduced motion: no loop; the alert sits in place, Track / Later fade it out (0.3 s), and it fades
 back 2 s later.
*/

private const val BACK_FOR = 0.9
private const val ROPE = 200.0
private const val GRAVITY = 2000.0
private const val FADE_IN = 0.25
private val INK = Color(0xFF1D2B3D)
private val MUTE = Color(0xFF6A7482)
private val CORAL = Color(0xFFE2553C)

private fun nowSeconds() = System.nanoTime() / 1e9

private class ParachuteSim {
    var x = 0.0
    var y = 0.0
    var vx = 0.0
    var vy = 0.0
    var theta = 0.0
    var omega = 0.0
    var grab = false
    var offX = 0.0
    var offY = 0.0
    var lastTouch = 0.0
    var chute = false
    var collapseAt = -1.0
    var backAt = -1.0
    var downAt = 0.0
    var downX = 0.0
    var downY = 0.0
    var moved = 0.0
    var pressed = -1
}

private class ParachuteArtSet(val layout: ParachuteLayout, val layers: ParachuteLayers)

private class ParachuteController(val auto: Boolean, val reduced: Boolean) {
    enum class Mode { Auto, Touch, Back }

    var now by mutableDoubleStateOf(0.0)
    var awake by mutableStateOf(true)
    /** The baked app's fade-in, 0..1 (a state of its own so the app layer stops redrawing once it is in). */
    var artFade by mutableFloatStateOf(0f)
    var mode = Mode.Auto
    var loopStart = -1.0
    var artAt = -1.0
    var lastFrame = -1.0
    var sim = ParachuteSim()
    var landed = false
    /** Reduced motion: shown, and since when. */
    var quietShown = true
    var quietAt = -10.0
    private val pose = ParachutePose()
    private var poseAt = Double.NaN

    fun loopTime(at: Double) = if (!auto || loopStart < 0) 5.5 else (((at - loopStart) % LOOP) + LOOP) % LOOP

    fun pose(g: ParachuteGeo, at: Double = now): ParachutePose {
        if (at == poseAt) return pose
        poseAt = at
        if (reduced) {
            ParachuteMotion.loopPose(5.5, g, pose)
            val k = ParachuteMotion.clamp01((at - quietAt) / 0.3)
            pose.fade = if (quietShown) k else 1 - k
            return pose
        }
        if (mode == Mode.Auto) return ParachuteMotion.loopPose(loopTime(at), g, pose)
        val s = sim
        val breath = sin(2 * PI * 2.1 * at)
        if (mode == Mode.Back) {
            ParachuteMotion.loopPose(PARACHUTE_LIFT_AT + (8.0 - PARACHUTE_LIFT_AT) * min(1.0, (at - s.backAt) / BACK_FOR), g, pose)
            if (s.collapseAt >= 0 && at - s.collapseAt < 1.2) ParachuteMotion.collapsing(g, breath, g.landX, g.landY, at - s.collapseAt, pose.add())
            return pose
        }
        pose.canopyCount = 0
        if (s.chute) {
            if (s.collapseAt < 0) ParachuteMotion.hangingCanopy(g, s.x, s.y, s.theta, breath, 1.0, pose.add())
            else if (at - s.collapseAt < 1.2) ParachuteMotion.collapsing(g, breath, s.x, s.y, at - s.collapseAt, pose.add())
        }
        pose.cardX = s.x
        pose.cardY = s.y
        pose.cardRot = s.theta * 0.6
        pose.sx = 1.0
        pose.sy = 1.0
        pose.shown = true
        pose.scrim = 0.36
        pose.fade = 1.0
        return pose
    }

    fun frame(g: ParachuteGeo, at: Double) {
        if (loopStart < 0) loopStart = at
        if (artAt < 0) artAt = at
        val dt = if (lastFrame < 0) 1.0 / 60 else min(at - lastFrame, 1.0 / 20)
        lastFrame = at
        landed = false
        if (artFade < 1f) artFade = ParachuteMotion.clamp01((at - artAt) / FADE_IN).toFloat()
        if (reduced && !quietShown && at - quietAt > 2.3) {
            quietShown = true
            quietAt = at
        }
        if (mode == Mode.Touch) {
            val s = sim
            val n = 4
            val h = dt / n
            for (i in 0 until n) {
                var ax = 0.0
                if (!s.grab) {
                    // Back to its place on a spring; the swing keeps going and dies out.
                    val w = 8.0
                    val z = 0.6
                    ax = -w * w * (s.x - g.landX) - 2 * z * w * s.vx
                    val ay = -w * w * (s.y - g.landY) - 2 * z * w * s.vy
                    s.vx += ax * h
                    s.vy += ay * h
                    s.x += s.vx * h
                    s.y += s.vy * h
                }
                // A pendulum hanging from the card's lines, pushed by the card's sideways acceleration.
                val alpha = -(GRAVITY / ROPE) * sin(s.theta) - 1.1 * s.omega - (if (s.grab) 0.0 else (ax / ROPE) * cos(s.theta))
                s.omega += alpha * h
                s.theta += s.omega * h
            }
            s.theta = max(-0.7, min(0.7, s.theta))
            // A chute still open lands with the card, then deflates.
            if (!s.grab && s.chute && s.collapseAt < 0 && hypot(s.x - g.landX, s.y - g.landY) < 4 && hypot(s.vx, s.vy) < 80) {
                s.collapseAt = at
                landed = true
            }
            if (!s.grab && at - s.lastTouch > 2) {
                s.backAt = at
                mode = Mode.Back
            }
        } else if (mode == Mode.Back) {
            if (at - sim.backAt >= BACK_FOR) {
                sim = ParachuteSim()
                mode = Mode.Auto
                loopStart = at
            }
        }
        now = at
    }

    fun idle(at: Double): Boolean {
        if (at - artAt < FADE_IN) return false
        if (reduced) return quietShown && at - quietAt > 0.35
        return !auto && mode == Mode.Auto
    }

    /** Takes the card from wherever the loop has it. */
    fun takeOver(at: Double, p: ParachutePose) {
        val s = ParachuteSim()
        s.x = p.cardX
        s.y = p.cardY
        s.theta = p.cardRot / 0.6
        s.lastTouch = at
        val t = if (auto) loopTime(at) else 5.0
        if (t < PARACHUTE_LAND_AT || t >= PARACHUTE_LIFT_AT) {
            s.chute = true
        } else if (t < PARACHUTE_LAND_AT + 1.2) {
            s.chute = true
            s.collapseAt = at - (t - PARACHUTE_LAND_AT)
        }
        sim = s
        mode = Mode.Touch
        poseAt = Double.NaN
    }

    fun dismiss(g: ParachuteGeo, at: Double) {
        if (reduced) {
            quietShown = false
            quietAt = at
            return
        }
        val s = sim
        s.backAt = at
        if (mode == Mode.Auto) {
            s.collapseAt = -1.0
            s.x = g.landX
        }
        mode = Mode.Back
        poseAt = Double.NaN
    }

    fun invalidate() {
        poseAt = Double.NaN
    }
}

/** Which button (0 Later, 1 Track) a point on the landed card is in, or -1. */
private fun parachuteButtonAt(g: ParachuteLayout, x: Double, y: Double): Int {
    val lx = x - (g.landX - g.cardW / 2)
    val ly = y - (g.landY - g.cardH / 2)
    val rects = arrayOf(g.later, g.track)
    for (i in 0 until 2) {
        val r = rects[i]
        if (lx >= r[0] - 4 && lx <= r[0] + r[2] + 4 && ly >= r[1] - 6 && ly <= r[1] + r[3] + 6) return i
    }
    return -1
}

/**
 * "Parcelpoint": a delivery app whose "4 min away" alert arrives by parachute. Drag the card to
 * swing it; Track or Later sends it off under a fresh canopy.
 *
 * @param autoPlay plays the 9 s drop-land-lift loop until touched.
 */
@Composable
fun ParachuteDropAlert(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val view = LocalView.current
    val reduced = rememberReducedMotion()
    val auto = autoPlay && !reduced
    val c = remember(auto, reduced) { ParachuteController(auto, reduced) }

    BoxWithConstraints(modifier.fillMaxSize().background(Color(0xFFF3EEE7)).clipToBounds()) {
        val screen = LocalDensity.current
        val density = screen.density
        val insets = WindowInsets.safeDrawing
        val top = insets.getTop(screen) / density
        val bottom = insets.getBottom(screen) / density
        val w = maxWidth.value
        val h = maxHeight.value
        val g = remember(w, h, top, bottom) { ParachuteLayout(w, h, top, bottom) }
        val painter = remember { ParachutePainter() }
        val placeholder = remember { ParachuteGfx(live = true) }
        var art by remember { mutableStateOf<ParachuteArtSet?>(null) }

        LaunchedEffect(w, h, top, bottom, density) {
            // Wait for the insets to settle (the demo hides the status bar) before baking.
            delay(120)
            val layout = ParachuteLayout(w, h, top, bottom)
            val layers = withContext(Dispatchers.Default) { ParachuteDropAlertArt.bakeLayers(layout, ParachuteFonts(), min(2f, density)) }
            // A rebake (the insets settled late) swaps the art in place; only the first one fades in and starts the loop.
            val first = art == null
            art = ParachuteArtSet(layout, layers)
            if (first) {
                c.artAt = -1.0
                c.artFade = 0f
                c.loopStart = -1.0
            }
            c.awake = true
        }

        val current = art
        LaunchedEffect(current, c, c.awake) {
            if (current == null || !c.awake) return@LaunchedEffect
            while (true) {
                withFrameNanos { n -> c.frame(current.layout.geo, n / 1e9) }
                if (c.landed) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                if (c.idle(c.now)) {
                    c.awake = false
                    break
                }
            }
        }

        // The app under the alert: baked once; flat shapes until the bake lands, then a fade.
        Canvas(Modifier.fillMaxSize()) {
            val a = current
            scale(density, density, Offset.Zero) {
                val canvas = drawContext.canvas.nativeCanvas
                val lay = a?.layout ?: g
                canvas.drawRect(0f, 0f, lay.w, lay.skyH + 10, placeholder.paint("#B4CFE7"))
                canvas.drawRect(0f, lay.sheetY, lay.w, lay.h, placeholder.paint("#F3EEE7"))
                canvas.drawRoundRect(16f, lay.card1Y, lay.w - 16, lay.card1Y + lay.card1H, 22f, 22f, placeholder.paint("#FFFDFA"))
                if (a == null) return@scale
                val p = placeholder.paint("#000000")
                p.alpha = (255 * c.artFade).toInt()
                a.layers.app.draw(canvas, p)
            }
        }

        Column(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = (top + 14).dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            BasicText("PARCELPOINT", style = TextStyle(color = CORAL, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp))
            BasicText("Package arriving", style = TextStyle(color = INK, fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp))
            BasicText("Today · 2 deliveries on the way", style = TextStyle(color = Color(0xFF3E4B5C), fontSize = 15.sp, fontWeight = FontWeight.Medium))
        }

        // Card 1's text: the head row, the stops of the route and the courier line.
        Row(
            Modifier.offset(x = 16.dp, y = g.card1Y.dp).width((w - 32).dp).padding(start = 72.dp, end = 16.dp, top = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f)) {
                BasicText("Ceramic mug set", style = ROW_TITLE, maxLines = 1, overflow = TextOverflow.Ellipsis)
                BasicText("from Kiln & Clay · 2 items", Modifier.padding(top = 2.dp), style = ROW_SUB, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End) {
                BasicText("ETA", style = TextStyle(color = MUTE, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp))
                BasicText("6:42 pm", style = TextStyle(color = INK, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"))
            }
        }
        Box(Modifier.offset(x = 16.dp, y = g.card1Y.dp).width((w - 32).dp).height(g.card1H.dp)) {
            val stops = arrayOf("Packed", "Shipped", "Out for delivery", "Arriving")
            for (i in stops.indices) {
                val x = 30 + (w - 32 - 60) * i / 3
                val left = when (i) {
                    0 -> x - 10
                    3 -> x - 74
                    else -> x - 42
                }
                val align = when (i) {
                    0 -> TextAlign.Left
                    3 -> TextAlign.Right
                    else -> TextAlign.Center
                }
                BasicText(
                    stops[i],
                    Modifier.offset(x = left.dp, y = 116.dp).width(84.dp),
                    style = TextStyle(color = if (i == 3) INK else MUTE, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, textAlign = align),
                    maxLines = 1,
                )
            }
            BasicText("Courier Leo · Van 12 · 0.6 mi away", Modifier.offset(x = 16.dp, y = 148.dp), style = TextStyle(color = INK, fontSize = 13.sp, fontWeight = FontWeight.Medium))
        }

        BasicText("Also coming", Modifier.offset(x = 24.dp, y = g.sectionY.dp), style = TextStyle(color = INK, fontSize = 17.sp, fontWeight = FontWeight.Bold))
        val rows = arrayOf(
            arrayOf("Linen napkins, set of 4", "Parcel locker 14 · Thu", "2 items"),
            arrayOf("Warm desk lamp bulb", "Front door · Fri", "1 item"),
            arrayOf("Oat milk frother", "Front door · Sat", "1 item"),
        )
        for (i in 0 until g.rows) {
            Row(
                Modifier.offset(x = 16.dp, y = (g.rowsY + i * 78).dp).width((w - 32).dp).height(68.dp).padding(start = 72.dp, end = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    BasicText(rows[i][0], style = ROW_TITLE, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    BasicText(rows[i][1], Modifier.padding(top = 2.dp), style = ROW_SUB, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                BasicText(rows[i][2], style = TextStyle(color = MUTE, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"))
            }
        }

        Row(Modifier.offset(y = (g.tabY + 32).dp).fillMaxWidth()) {
            val tabs = arrayOf("Home", "Track", "Send", "Profile")
            for (i in tabs.indices) {
                BasicText(
                    tabs[i],
                    Modifier.weight(1f),
                    style = TextStyle(color = if (i == 1) CORAL else Color(0xFF8A93A0), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center),
                )
            }
        }

        // The alert layer: scrim, lines, canopies and the card, redrawn every frame.
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = if (art == null) 0f else 1f }
                .semantics {
                    liveRegion = LiveRegionMode.Polite
                    contentDescription = "Your parcel is 4 min away. Leo is on Linden Street now. Door code 2417 is ready. Track or Later."
                }
                .pointerInput(current, c) {
                    val a = current ?: return@pointerInput
                    val lay = a.layout
                    val geo = lay.geo
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val ex = down.position.x / density.toDouble()
                        val ey = down.position.y / density.toDouble()
                        var at = nowSeconds()
                        c.invalidate()
                        val p = c.pose(geo, at)
                        if (!p.shown || p.fade < 0.5 || c.mode == ParachuteController.Mode.Back) return@awaitEachGesture
                        val inside = abs(ex - p.cardX) < geo.cardW / 2 + 10 && abs(ey - p.cardY) < geo.cardH / 2 + 10
                        if (!inside) return@awaitEachGesture
                        c.awake = true
                        val cardRot = p.cardRot
                        val cardX = p.cardX
                        val cardY = p.cardY
                        if (!reduced && c.mode == ParachuteController.Mode.Auto) c.takeOver(at, p)
                        val s = c.sim
                        s.downAt = at
                        s.downX = ex
                        s.downY = ey
                        s.moved = 0.0
                        s.pressed = if (abs(cardRot) < 0.05 && hypot(cardX - geo.landX, cardY - geo.landY) < 6) parachuteButtonAt(lay, ex, ey) else -1
                        if (!reduced) {
                            s.grab = true
                            s.offX = s.x - ex
                            s.offY = s.y - ey
                            s.vx = 0.0
                            s.vy = 0.0
                            s.lastTouch = at
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        }
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val ch = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!ch.pressed) break
                                ch.consume()
                                if (reduced) continue
                                tracker.addPosition(ch.uptimeMillis, ch.position)
                                at = nowSeconds()
                                val x = ch.position.x / density.toDouble()
                                val y = ch.position.y / density.toDouble()
                                s.moved = max(s.moved, hypot(x - s.downX, y - s.downY))
                                if (s.moved > 10) s.pressed = -1
                                val v = tracker.calculateVelocity()
                                val vx = v.x / density.toDouble()
                                val dt = max(1.0 / 240, at - s.lastTouch)
                                val ax = (vx - s.vx) / dt
                                // The finger's sideways acceleration swings the card.
                                s.omega += (-(ax / ROPE) * cos(s.theta)) * min(dt, 1.0 / 30)
                                s.x = x + s.offX
                                s.y = y + s.offY
                                s.vx = vx
                                s.vy = v.y / density.toDouble()
                                s.lastTouch = at
                            }
                        } finally {
                            at = nowSeconds()
                            val tap = s.moved < 10 && at - s.downAt < 0.35 && s.pressed >= 0
                            s.grab = false
                            s.lastTouch = at
                            s.vx = max(-2500.0, min(2500.0, s.vx))
                            s.vy = max(-2500.0, min(2500.0, s.vy))
                            if (tap) {
                                val kind = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS
                                view.performHapticFeedback(kind)
                                c.dismiss(geo, at)
                            }
                            s.pressed = -1
                        }
                    }
                },
        ) {
            val a = current ?: return@Canvas
            scale(density, density, Offset.Zero) {
                val pose = c.pose(a.layout.geo)
                val pressed = if (c.mode == ParachuteController.Mode.Touch) c.sim.pressed else -1
                painter.alert(drawContext.canvas.nativeCanvas, a.layout, a.layers, pose, pressed)
            }
        }
    }
}

private val ROW_TITLE = TextStyle(color = INK, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
private val ROW_SUB = TextStyle(color = MUTE, fontSize = 13.sp, fontWeight = FontWeight.Medium)
