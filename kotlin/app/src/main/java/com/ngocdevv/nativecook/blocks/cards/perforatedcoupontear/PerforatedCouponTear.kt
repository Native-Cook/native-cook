package com.ngocdevv.nativecook.blocks.cards.perforatedcoupontear

import android.graphics.Paint
import android.graphics.Path
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.blocks.cards.perforatedcoupontear.CouponMotion.CARD_H
import com.ngocdevv.nativecook.shared.cssBoxShadow
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/*
 Perforated Coupon Tear — a café loyalty screen ("Bean Counter — Your free coffee"): an espresso
 coupon whose caramel stub tears off along its perforation hole by hole, swings from the finger and
 flies into a green leather wallet while a REDEEMED stamp slams onto the coupon; then a fresh
 coupon slides in. A port of PerforatedCouponTear.tsx (the reference) and its SwiftUI twin.

 autoPlay (LOOP = 9 s, CouponMotion): rest 0–1.4; grab 1.4–1.6 (lift, 2° anticipation); tear
 1.6–3.6 (0.22u + 0.78u³, the front hole by hole, the stub turning up to 12.6° about it); swing
 3.6–4.6 (damped pendulum while the finger drifts); fly 4.6–5.4 into the wallet (quadratic arc,
 to 7° and 0.6×); stamp 5.0–5.38; hold; reset 6.8–8.2 (filed stub sinks, coupon leaves left, a
 fresh one slides in with a 4 % overshoot); rest from 8.2.

 Touch: pull the stub down and away (0.85·dy + 0.55·dx over 210 dp rips it all, pushing back
 rubber-bands), a tick every two holes and a light impact when it comes free; let go early and it
 springs back carrying the finger's velocity; once free it hangs from the finger as a spring
 pendulum a sideways flick swings, and letting go plays the flight, stamp and reset. The loop
 restarts 2 s after the last touch. Reduced motion: no loop, the coupon rests whole; the finger
 still drives the tear.

 Drawing: one Canvas. Static art is baked once off the main thread (PerforatedCouponTearArt); per
 frame the canvas draws the baked layers, fills the body's and stub's paths (rebuilt from the tear
 front, ~70 points each) with their baked faces as bitmap shaders, strokes the fibres and holes,
 and places the stub and stamp bitmaps. Nothing recomposes while it plays: the frame loop bumps
 one long state that only the draw lambda reads.
 */

/** Seconds after the last touch before the loop starts again. */
private const val IDLE = 2.0
/** Finger travel (dp, down and away) that rips the whole perforation. */
private const val TEAR_TRAVEL = 210.0

private class CouponController(val looping: Boolean) {
    var geom: CouponMotion.Geom? = null
    var clock = 0.0
    var running = looping
    var user = false
    var idle = 0.0
    var userRun = false
    var fly: CouponMotion.Fly? = null
    var uTear = 0.0
    var uFree = false
    var uX = 0.0
    var uY = 0.0
    var uPhi = 0.0
    var uPhiV = 0.0
    var uVx = 0.0
    var grabbing = false
    var startTear = 0.0
    var offX = 0.0
    var offY = 0.0
    var lastHole = 0
    // The spring back after an early release.
    var springing = false
    var springX0 = 0.0
    var springV0 = 0.0
    var springT = 0.0

    /** Bumped every frame; only the draw lambda reads it. */
    val tick = mutableLongStateOf(0L)
    /** Whether the frame loop should run. */
    var awake by mutableStateOf(looping)

    fun pose(g: CouponMotion.Geom): CouponMotion.Pose =
        if (user) CouponMotion.userPose(g, uTear, uFree, uX, uY, uPhi) else CouponMotion.scriptPose(g, clock, fly)

    /** One frame; returns true when the stamp lands in a run the finger started (for the haptic). */
    fun step(dt: Double): Boolean {
        if (user) {
            if (springing) {
                springT += dt
                uTear = CouponMotion.springBack(springX0, springV0, springT)
                if (CouponMotion.springSettled(springX0, springV0, springT)) {
                    uTear = 0.0
                    springing = false
                }
            }
            if (uFree) {
                // The free stub hangs from the finger like a pendulum; a sideways flick swings it.
                val target = CouponMotion.HANG - (uVx * 0.0006).coerceIn(-0.5, 0.5)
                val a = -70 * (uPhi - target) - 6 * uPhiV
                uPhiV += a * dt
                uPhi += uPhiV * dt
                uVx *= 0.9
            }
            if (!grabbing) {
                idle += dt
                if (idle > IDLE && abs(uTear) < 0.002) {
                    user = false
                    springing = false
                    clock = 0.0
                    running = looping
                    if (!running) awake = false
                }
            }
            return false
        }
        if (!running) {
            awake = false
            return false
        }
        val before = clock
        var t = clock + dt
        if (t >= CouponMotion.LOOP) {
            t -= CouponMotion.LOOP
            fly = null
            userRun = false
            if (!looping) {
                t = 0.0
                running = false
            }
        }
        clock = t
        return userRun && before < CouponMotion.STAMP_AT && t >= CouponMotion.STAMP_AT
    }
}

@Composable
fun PerforatedCouponTear(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val view = LocalView.current
    val reduced = rememberReducedMotion()
    val looping = autoPlay && !reduced
    val c = remember(looping) { CouponController(looping) }
    val density = LocalDensity.current.density

    fun feel(kind: Int) {
        view.performHapticFeedback(if (kind == 0) HapticFeedbackConstants.CLOCK_TICK else HapticFeedbackConstants.KEYBOARD_TAP)
    }

    BoxWithConstraints(modifier.fillMaxSize().background(Color(CouponPalette.PAPER[1]))) {
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        val top = insets.calculateTopPadding().value
        val bottom = insets.calculateBottomPadding().value
        val l = remember(maxWidth, maxHeight, top, bottom) { CouponLayout(maxWidth.value, maxHeight.value, top, bottom) }
        c.geom = l.geom
        // The demo hides the status bar, so the insets animate for a moment: the bake waits for them to
        // settle (a cancellable delay), at most 2 px per dp, and stops when its layout is replaced.
        val art by produceState<CouponArt?>(null, l, density) {
            delay(120)
            value = withContext(Dispatchers.Default) { bakeCouponArt(l, min(2f, density), coroutineContext.job) }
        }

        // The clock starts at t = 0 on the first frame that has the art; it sleeps when nothing moves.
        LaunchedEffect(c, art != null) {
            if (art == null) return@LaunchedEffect
            c.clock = 0.0
            var last = -1L
            while (true) {
                snapshotFlow { c.awake }.first { it }
                withFrameNanos { now ->
                    val dt = if (last < 0) 0.0 else min((now - last) / 1e9, 1.0 / 30)
                    last = now
                    if (c.step(dt)) feel(1)
                    c.tick.longValue = now
                }
                if (!c.awake) last = -1L
            }
        }

        val paths = remember { CouponPaths() }

        Canvas(
            Modifier
                .fillMaxSize()
                .semantics { contentDescription = "Free coffee coupon. Drag the stub down to tear it off and file it in your wallet." }
                .pointerInput(c, l) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val vt = VelocityTracker()
                        vt.addPosition(down.uptimeMillis, down.position)
                        val sx = down.position.x / density
                        val sy = down.position.y / density
                        var started = false
                        val g = l.geom
                        while (true) {
                            val event = awaitPointerEvent()
                            val ch = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!ch.pressed) break
                            vt.addPosition(ch.uptimeMillis, ch.position)
                            val x = (ch.position.x / density).toDouble()
                            val y = (ch.position.y / density).toDouble()
                            val tx = x - sx
                            val ty = y - sy
                            if (!started) {
                                if (hypot(tx, ty) < 2) continue
                                started = true
                                // Grab the stub if the finger is on it.
                                val p = c.pose(g)
                                val lx = x - l.cardX
                                val ly = y - l.cardY
                                val ok = p.slide == 0.0 && !p.filed && p.scale >= 1 && if (p.tear >= 1) {
                                    hypot(lx - p.sx, ly - p.sy) < 120
                                } else {
                                    lx > g.px - 8 && lx < g.cw + 12 && ly > -12 && ly < CARD_H + 12
                                }
                                if (!ok) return@awaitEachGesture
                                c.grabbing = true
                                c.idle = 0.0
                                c.user = true
                                c.running = false
                                c.springing = false
                                c.awake = true
                                if (p.tear >= 1) {
                                    c.uFree = true
                                    c.uX = p.sx; c.uY = p.sy; c.uPhi = p.phi; c.uPhiV = 0.0
                                    c.offX = lx - p.sx; c.offY = ly - p.sy
                                } else {
                                    c.uFree = false
                                    c.uTear = p.tear
                                    c.startTear = p.tear
                                    c.lastHole = floor(p.tear * CouponMotion.holeCount).toInt()
                                }
                                ch.consume()
                                continue
                            }
                            ch.consume()
                            c.idle = 0.0
                            val lx = x - l.cardX
                            val ly = y - l.cardY
                            if (c.uFree) {
                                c.uX = lx - c.offX
                                c.uY = ly - c.offY
                                c.uVx = (vt.calculateVelocity().x / density).toDouble()
                                continue
                            }
                            val raw = c.startTear + (ty * 0.85 + tx * 0.55) / TEAR_TRAVEL
                            // Pushing the stub back in only gives a little (rubber band).
                            val tear = if (raw < 0) -(-raw / (1 + -raw * 8)) else raw
                            if (tear >= 1) {
                                val a = CouponMotion.attachedPose(g, 1.0, 0.0)
                                c.uFree = true
                                c.uTear = 1.0
                                c.uX = a[1]; c.uY = a[2]; c.uPhi = a[3]; c.uPhiV = 0.0
                                c.offX = lx - a[1]; c.offY = ly - a[2]
                                feel(1)
                                continue
                            }
                            c.uTear = tear
                            val hole = floor(max(0.0, tear) * CouponMotion.holeCount).toInt()
                            if (abs(hole - c.lastHole) >= 2) {
                                c.lastHole = hole
                                feel(0)
                            }
                        }
                        if (!started || !c.grabbing) return@awaitEachGesture
                        c.grabbing = false
                        if (c.uFree) {
                            // Let go of a free stub: it flies into the wallet and the script finishes the story.
                            c.fly = CouponMotion.Fly(c.uX, c.uY, c.uPhi, true)
                            c.uFree = false
                            c.uTear = 0.0
                            c.user = false
                            c.userRun = true
                            c.clock = CouponMotion.FLY_START
                            c.running = true
                            return@awaitEachGesture
                        }
                        val v = vt.calculateVelocity()
                        c.springX0 = c.uTear
                        c.springV0 = ((v.y * 0.85 + v.x * 0.55) / density) / TEAR_TRAVEL
                        c.springT = 0.0
                        c.springing = true
                    }
                },
        ) {
            c.tick.longValue
            val a = art ?: return@Canvas
            val g = c.geom ?: return@Canvas
            val p = c.pose(g)
            drawIntoCanvas { canvas -> drawCoupon(canvas.nativeCanvas, density, l, a, p, paths) }
        }

        Overlays(l)
    }
}

/** Reused per-frame paths and paints. */
private class CouponPaths {
    val body = Path()
    val stub = Path()
    val bodyFibres = Path()
    val stubFibres = Path()
    val freshBody = Path()
    val freshStub = Path()
    var freshPx = Float.NaN
    val bitmap = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    val face = Paint(Paint.ANTI_ALIAS_FLAG)
    val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        color = CouponPalette.rgba(255, 240, 215, 0.35f)
    }
    val stubFibre = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.9f
        strokeCap = Paint.Cap.ROUND
        color = CouponPalette.rgba(255, 246, 230, 0.85f)
    }
    val bodyFibre = Paint(stubFibre).apply { color = CouponPalette.rgba(255, 240, 218, 0.8f) }
    val hole = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = CouponPalette.PAPER[1] }
    var bodyShader: android.graphics.Shader? = null
    var stubShader: android.graphics.Shader? = null
    var shaderArt: CouponArt? = null
}

private fun drawCoupon(nc: android.graphics.Canvas, d: Float, l: CouponLayout, a: CouponArt, p: CouponMotion.Pose, q: CouponPaths) {
    if (q.shaderArt !== a) {
        q.shaderArt = a
        q.bodyShader = a.bodyFace.shader()
        q.stubShader = a.stubFace.shader()
        q.freshPx = Float.NaN
    }
    val px = l.px
    val cw = l.cw
    if (q.freshPx != px) {
        q.freshPx = px
        couponBodyPath(q.freshBody, px, 0f)
        couponStubPath(q.freshStub, px, cw, 0f)
    }
    val bm = q.bitmap
    bm.alpha = 255

    nc.save()
    nc.scale(d, d)
    a.back.draw(nc, bm)

    nc.save()
    nc.translate(l.cardX, l.cardY)
    fun stubTransform() {
        nc.translate(p.sx.toFloat(), p.sy.toFloat())
        nc.rotate(Math.toDegrees(p.phi).toFloat())
        nc.scale(p.scale.toFloat(), p.scale.toFloat())
        nc.translate(-l.gx, -l.gy)
    }
    // A filed stub lies behind the pocket's front, clipped to the wallet's bottom.
    if (p.filed) {
        nc.save()
        nc.clipRect(-l.cardX, -l.cardY, l.w - l.cardX, l.pocketBottom - 2 - l.cardY)
        stubTransform()
        a.filedStub.draw(nc, bm)
        nc.restore()
    }
    nc.restore()
    a.front.draw(nc, bm)
    nc.translate(l.cardX, l.cardY)

    fun stubFace(path: Path, fibres: Path?) {
        q.face.shader = q.stubShader
        nc.drawPath(path, q.face)
        nc.drawPath(path, q.edge)
        if (fibres != null) nc.drawPath(fibres, q.stubFibre)
        nc.drawPath(a.holes, q.hole)
    }
    fun body(path: Path, fibres: Path?) {
        bm.alpha = 255
        a.bodyShadow.draw(nc, bm)
        q.face.shader = q.bodyShader
        nc.drawPath(path, q.face)
        if (fibres != null) nc.drawPath(fibres, q.bodyFibre)
        nc.drawPath(a.holes, q.hole)
    }

    // The next coupon, sliding in while the redeemed one is still on its way out.
    if (p.freshOn) {
        nc.save()
        nc.translate(p.fresh.toFloat(), 0f)
        stubFace(q.freshStub, null)
        body(q.freshBody, null)
        nc.restore()
    }

    nc.save()
    nc.translate(p.slide.toFloat(), 0f)
    val front = p.front.toFloat()
    couponBodyPath(q.body, px, front)
    couponFibrePath(q.bodyFibres, px, front, 1)
    if (!p.filed) {
        couponStubPath(q.stub, px, cw, front)
        couponFibrePath(q.stubFibres, px, front, -1)
        // The live stub sits under the body, so its still-joined part tucks beneath it as it turns.
        nc.save()
        stubTransform()
        val lift = p.lift.toFloat()
        val op = 0.18f + 0.16f * lift
        nc.save()
        nc.translate(1 + 5 * lift, 2 + 12 * lift)
        if (lift < 1) {
            bm.alpha = (255 * op * (1 - lift)).toInt()
            a.stubShadowLo.draw(nc, bm)
        }
        if (lift > 0) {
            bm.alpha = (255 * op * lift).toInt()
            a.stubShadowHi.draw(nc, bm)
        }
        nc.restore()
        stubFace(q.stub, q.stubFibres)
        nc.restore()
    }
    body(q.body, q.bodyFibres)

    // The REDEEMED stamp.
    val s = p.stamp
    if (s > 0) {
        nc.save()
        nc.translate(px * 0.56f, 104f)
        nc.rotate(Math.toDegrees(-0.21).toFloat())
        nc.scale(s.toFloat(), s.toFloat())
        bm.alpha = (255 * min(1.0, max(0.15, (1.36 - s) / 0.2))).toInt()
        a.stamp.draw(nc, bm)
        nc.restore()
    }
    nc.restore()
    nc.restore()
    bm.alpha = 255
}

private val roast = Color(CouponPalette.ROAST)
private val cream = Color(CouponPalette.CREAM)
private fun ink(a: Float) = Color(58 / 255f, 36 / 255f, 24 / 255f, a)

/** Header, titles, the wallet's heading and the button (RN's absolutely placed Views). */
@Composable
private fun Overlays(l: CouponLayout) {
    Row(
        Modifier.offset(x = 20.dp, y = (l.top + 12).dp).padding(end = 40.dp).fillMaxWidth().height(40.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier
                    .size(32.dp)
                    .cssBoxShadow(Color(46 / 255f, 26 / 255f, 14 / 255f, 0.3f), 1.dp, 2.dp, 16.dp)
                    .background(roast, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                BasicText("BC", style = TextStyle(color = cream, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.5.sp))
            }
            BasicText("Bean Counter", style = TextStyle(color = roast, fontSize = 16.sp, fontWeight = FontWeight.Bold))
        }
        Box(
            Modifier.height(28.dp).background(ink(0.08f), RoundedCornerShape(14.dp)).padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            BasicText("Larch St.", style = TextStyle(color = ink(0.75f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
        }
    }
    Column(
        Modifier.offset(x = 20.dp, y = (l.top + 66).dp).padding(end = 40.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        BasicText("Your free coffee", style = TextStyle(color = roast, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.6).sp), maxLines = 1)
        BasicText("10 of 10 stamps · tear the stub at the counter", style = TextStyle(color = ink(0.62f), fontSize = 15.sp, fontWeight = FontWeight.Medium))
    }
    Column(
        Modifier.offset(x = 20.dp, y = (l.cardY + CARD_H + 30).dp).padding(end = 40.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        BasicText("Wallet", style = TextStyle(color = roast, fontSize = 17.sp, fontWeight = FontWeight.Bold))
        BasicText(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = ink(0.8f), fontFeatureSettings = "tnum")) { append("2") }
                append(" rewards redeemed this month")
            },
            style = TextStyle(color = ink(0.6f), fontSize = 13.sp, fontWeight = FontWeight.Medium),
        )
    }
    Box(Modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, bottom = (l.bottom + 24).dp), contentAlignment = Alignment.BottomCenter) {
        val source = remember { MutableInteractionSource() }
        val pressed by source.collectIsPressedAsState()
        val shadowColor = Color(46 / 255f, 26 / 255f, 14 / 255f, 1f)
        Box(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .graphicsLayer {
                    val s = if (pressed) 0.97f else 1f
                    scaleX = s
                    scaleY = s
                }
                .cssBoxShadow(shadowColor.copy(alpha = 0.35f), 1.dp, 2.dp, 26.dp)
                .cssBoxShadow(shadowColor.copy(alpha = 0.22f), 10.dp, 24.dp, 26.dp)
                .background(roast, RoundedCornerShape(26.dp))
                .clickable(interactionSource = source, indication = null, role = Role.Button) {},
            contentAlignment = Alignment.Center,
        ) {
            BasicText("Show code at counter", style = TextStyle(color = cream, fontSize = 17.sp, fontWeight = FontWeight.Bold))
        }
    }
}
