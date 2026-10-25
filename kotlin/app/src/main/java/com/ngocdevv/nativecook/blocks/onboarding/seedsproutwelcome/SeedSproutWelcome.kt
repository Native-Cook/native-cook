package com.ngocdevv.nativecook.blocks.onboarding.seedsproutwelcome

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotateRad
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.blocks.onboarding.seedsproutwelcome.SeedSproutWelcomeMotion.LOOP
import com.ngocdevv.nativecook.blocks.onboarding.seedsproutwelcome.SeedSproutWelcomeMotion.PARK_X
import com.ngocdevv.nativecook.blocks.onboarding.seedsproutwelcome.SeedSproutWelcomeMotion.PARK_Y
import com.ngocdevv.nativecook.blocks.onboarding.seedsproutwelcome.SeedSproutWelcomeMotion.TILT
import com.ngocdevv.nativecook.blocks.onboarding.seedsproutwelcome.SeedSproutWelcomeMotion.clamp01
import com.ngocdevv.nativecook.blocks.onboarding.seedsproutwelcome.SeedSproutWelcomeMotion.easeOut
import com.ngocdevv.nativecook.blocks.onboarding.seedsproutwelcome.SeedSproutWelcomeMotion.hash
import com.ngocdevv.nativecook.shared.cssBoxShadow
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** The insets animate for a moment while the demo hides the status bar: bakes keyed on them wait this long to settle. */
private const val SETTLE_MS = 120L

/*
 Seed Sprout Welcome — a plant-care app's welcome (Greenroom): a seed in a terracotta pot on a
 sunny sill. The watering can tips and showers it, the seed splits and a stem grows, unfurling
 leaves, a bud opens into a coral flower, and the onboarding pages roll with it; at the end the
 petals fall and it starts over. A port of SeedSproutWelcome.tsx (and its SwiftUI twin); the
 numbers are the same, the clock is SeedSproutWelcomeMotion.kt.

 autoPlay (LOOP = 10 s): rest 0–1.4; the can glides over the pot 1.4–2.0, tips 1.8–2.1, pours
 until 2.8 and returns 3.0–3.55; soil darkens 2.05–2.7, dries 4.2–6.4; sprout 2.6–4.6, bloom
 5.0–6.8; hold; petals fall 8.0–9.4 while the plant shrinks back 8.2–9.6.
 Touch: drag the can; over the pot it tips on a spring (140, 16) and pours, which grows the plant
 at 0.32 a second; let go and it springs home. 2 s after the last touch the plant shrinks back in
 0.9 s and the loop starts again. Reduced motion: no loop, the pot rests with its seed; the can
 still works under a finger.
 */

private val Ink = Color(0xFF23331F)
private val Mute = Color(35 / 255f, 51 / 255f, 31 / 255f, 0.62f)
private val Green = Color(0xFF3E6B35)

private val seedPages = listOf(
    "Plant something small" to "Greenroom keeps every seed, pot and sill in one calm place.",
    "Water on its own rhythm" to "Gentle nudges tuned to each plant's thirst and your light.",
    "Watch it bloom" to "Log new leaves and buds, and see your garden come alive.",
)

/** Seconds after the last touch before the loop starts again. */
private const val IDLE = 2.0
/** Growth per second of full pouring under the finger. */
private const val GROW_RATE = 0.32
private val returnEase = CubicBezierEasing(0.77f, 0f, 0.175f, 1f)

/** The finger's state and the scene of the current frame; plain fields, read by the draw lambdas after a tick. */
private class SeedController {
    var clock = 0.0
    var wt = 0.0
    var user = false
    var grabbing = false
    var returning = false
    var idle = 0.0
    var retFrom = 0.0
    var retT = 0.0
    var uG = 0.0
    var uX = PARK_X
    var uY = PARK_Y
    var uVx = 0.0
    var uVy = 0.0
    var uTilt = 0.0
    var uTiltV = 0.0
    var uWet = 0.0
    var offX = 0.0
    var offY = 0.0
    var lastPage = -1
    var scene = SeedSproutWelcomeMotion.sceneAt(0.0)

    /** Frames run while the finger owns the can; set again by a grab. */
    var awake by mutableStateOf(false)
    var tick by mutableIntStateOf(0)

    fun step(dt: Double, looping: Boolean, onPage: () -> Unit) {
        wt += dt
        var pour = 0.0
        if (user) {
            // Over the pot the can tips and pours; the water grows the plant.
            val tx = SeedSproutWelcomeMotion.spoutX(uX, 0.0)
            val ty = SeedSproutWelcomeMotion.spoutY(uY, 0.0)
            val over = grabbing && abs(tx + 10) < 80 && ty < -40 && ty > -330
            val target = if (over) TILT else 0.0
            val a = -140 * (uTilt - target) - 16 * uTiltV
            uTiltV += a * dt
            uTilt += uTiltV * dt
            pour = clamp01((uTilt / TILT - 0.6) / 0.4)
            if (!returning) uG = min(1.0, uG + pour * GROW_RATE * dt)
            uWet = max(pour, uWet - dt * 0.25)
            if (!grabbing) {
                // Home on a spring (damping ratio 0.8, ~0.5 s), carrying the finger's velocity.
                val w = 11.0
                val z = 0.8
                uVx += (-w * w * (uX - PARK_X) - 2 * z * w * uVx) * dt
                uVy += (-w * w * (uY - PARK_Y) - 2 * z * w * uVy) * dt
                uX += uVx * dt
                uY += uVy * dt
            }
            if (!grabbing && !returning) {
                idle += dt
                if (idle > IDLE) {
                    returning = true
                    retFrom = uG
                    retT = 0.0
                }
            }
            if (returning) {
                retT += dt
                val u = min(1.0, retT / 0.9)
                uG = retFrom * (1 - returnEase.transform(u.toFloat()))
                if (u >= 1) {
                    clock = 0.0
                    user = false
                    returning = false
                }
            }
        } else if (looping) {
            clock = (clock + dt) % LOOP
        }
        scene = if (!user) {
            SeedSproutWelcomeMotion.sceneAt(clock)
        } else {
            SeedSproutWelcomeMotion.Scene(uG, uX, uY, uTilt, pour, uWet, 0.0, SeedSproutWelcomeMotion.pageOf(uG), wt)
        }
        if (user) {
            val p = scene.page.roundToInt()
            if (lastPage >= 0 && p > lastPage) onPage()
            lastPage = p
        } else {
            lastPage = -1
        }
    }
}

/** The screen's geometry in dp. */
private class SeedGeo(val w: Float, val h: Float, val top: Float, val bottom: Float) {
    val shelfY = h - bottom - 236
    val s = max(0.6f, min(1.1f, min((shelfY - top - 64) / 400, (w - 32) / 330)))
    val ox = w / 2 - 22 * s
    val oy = shelfY - 112 * s
}

@Composable
fun SeedSproutWelcome(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val view = LocalView.current
    val reduced = rememberReducedMotion()
    val looping = autoPlay && !reduced
    val c = remember { SeedController() }

    BoxWithConstraints(modifier.fillMaxSize().background(Color(0xFFE0E8D8))) {
        val density = LocalDensity.current.density
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        val top = insets.calculateTopPadding().value
        val bottom = insets.calculateBottomPadding().value
        val g = remember(maxWidth, maxHeight, top, bottom) { SeedGeo(maxWidth.value, maxHeight.value, top, bottom) }

        var art by remember { mutableStateOf<SeedArt?>(null) }
        LaunchedEffect(g, density) {
            delay(SETTLE_MS)
            art = withContext(Dispatchers.Default) { bakeSeedArt(g.w, g.h, density, g.shelfY, g.ox, g.oy, g.s) }
        }
        val ready = art != null

        // One frame loop; the loop starts at t = 0 on the first frame that has the art.
        LaunchedEffect(c, looping, ready, c.awake) {
            if (!ready) return@LaunchedEffect
            var last = -1L
            if (looping && !c.user) c.clock = 0.0
            while (looping || c.user) {
                withFrameNanos { now ->
                    val dt = if (last < 0) 0.0 else min((now - last) / 1e9, 1.0 / 30)
                    last = now
                    c.step(dt, looping) { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
                    c.tick++
                }
            }
            c.awake = false
            c.tick++
        }

        val crumbs = remember { seedSoilCrumbs() }
        val seedTop = remember { seedHalf(-1f) }
        val seedBottom = remember { seedHalf(1f) }
        val bud = remember { seedBud() }
        val stamens = remember { seedStamens() }
        val stem = remember { Path() }
        val drops = remember { Path() }

        Canvas(
            Modifier
                .fillMaxSize()
                .semantics { contentDescription = "A seed in a pot. Drag the watering can over it to make it grow." }
                .pointerInput(c, g, looping) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val sc = c.scene
                        fun lx(x: Float) = ((x / density - g.ox) / g.s).toDouble()
                        fun ly(y: Float) = ((y / density - g.oy) / g.s).toDouble()
                        val x0 = lx(down.position.x)
                        val y0 = ly(down.position.y)
                        if (c.returning || hypot(x0 - sc.cx + 20, y0 - sc.cy) > 90) return@awaitEachGesture
                        down.consume()
                        if (!c.user) {
                            c.uG = sc.g
                            c.uWet = sc.wet
                            c.uTilt = sc.tilt
                            c.uTiltV = 0.0
                        }
                        c.uX = sc.cx
                        c.uY = sc.cy
                        c.uVx = 0.0
                        c.uVy = 0.0
                        c.offX = x0 - sc.cx
                        c.offY = y0 - sc.cy
                        c.lastPage = sc.page.roundToInt()
                        c.user = true
                        c.grabbing = true
                        c.idle = 0.0
                        c.awake = true
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        while (true) {
                            val event = awaitPointerEvent()
                            val ch = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!ch.pressed) break
                            ch.consume()
                            tracker.addPosition(ch.uptimeMillis, ch.position)
                            c.idle = 0.0
                            c.uX = lx(ch.position.x) - c.offX
                            c.uY = min(-90.0, ly(ch.position.y) - c.offY)
                        }
                        val v = tracker.calculateVelocity()
                        c.uVx = (v.x / density / g.s).toDouble()
                        c.uVy = (v.y / density / g.s).toDouble()
                        c.grabbing = false
                    }
                },
        ) {
            c.tick
            val a = art ?: return@Canvas
            val sc = c.scene
            drawImage(a.back, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()))
            scale(density, density, Offset.Zero) {
                translate(g.ox, g.oy) {
                    scale(g.s, g.s, Offset.Zero) { drawHero(a, sc, crumbs, seedTop, seedBottom, bud, stamens, stem, drops, c.user) }
                }
            }
        }

        // Header.
        Row(
            Modifier.offset(x = 24.dp, y = (top + 12).dp).width((g.w - 48).dp).height(32.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(16.dp)
                    .graphicsLayer { rotationZ = -45f }
                    .background(Green, RoundedCornerShape(topStart = 16.dp, bottomEnd = 16.dp)),
            )
            Spacer(Modifier.width(8.dp))
            BasicText("Greenroom", style = TextStyle(color = Ink, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.2).sp))
            Spacer(Modifier.weight(1f))
            BasicText("Skip", style = TextStyle(color = Mute, fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
        }

        // The rolling copy.
        Box(Modifier.offset(x = 24.dp, y = (g.shelfY + 36).dp).width((g.w - 48).dp).height(96.dp)) {
            for (i in seedPages.indices) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            c.tick
                            val d = (i - c.scene.page).toFloat()
                            alpha = max(0f, 1 - abs(d) * 1.6f)
                            translationY = d * 28 * density
                        },
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    BasicText(seedPages[i].first, style = TextStyle(color = Ink, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp))
                    BasicText(seedPages[i].second, style = TextStyle(color = Mute, fontSize = 16.sp, fontWeight = FontWeight.Medium, lineHeight = 22.sp))
                }
            }
        }

        // Dots and the button.
        Column(
            Modifier.offset(x = 24.dp, y = (g.h - bottom - 16 - 80).dp).width((g.w - 48).dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Box(Modifier.height(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(3) { Box(Modifier.size(8.dp).background(Color(35 / 255f, 51 / 255f, 31 / 255f, 0.18f), CircleShape)) }
                }
                Box(
                    Modifier
                        .graphicsLayer {
                            c.tick
                            translationX = (c.scene.page * 16 * density).toFloat()
                        }
                        .size(8.dp)
                        .background(Green, CircleShape),
                )
            }
            SeedButton { c.scene.page to c.tick }
        }
    }
}

@Composable
private fun SeedButton(page: () -> Pair<Double, Int>) {
    var pressed by remember { mutableStateOf(false) }
    val density = LocalDensity.current.density
    Box(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .graphicsLayer {
                val s = if (pressed) 0.97f else 1f
                scaleX = s
                scaleY = s
            }
            .cssBoxShadow(Color(20 / 255f, 30 / 255f, 15 / 255f, 0.2f), 10.dp, 22.dp, 26.dp)
            .cssBoxShadow(Color(20 / 255f, 30 / 255f, 15 / 255f, 0.35f), 1.dp, 2.dp, 26.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(Ink)
            .semantics { role = Role.Button }
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    pressed = true
                    tryAwaitRelease()
                    pressed = false
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        for (k in 0..1) {
            BasicText(
                if (k == 0) "Continue" else "Get started",
                Modifier.graphicsLayer {
                    val r = clamp01(page().first - 1)
                    val d = (k - r).toFloat()
                    alpha = max(0f, 1 - abs(d) * 1.8f)
                    translationY = d * 20 * density
                },
                style = TextStyle(color = Color(0xFFF1F5EA), fontSize = 17.sp, fontWeight = FontWeight.Bold),
            )
        }
    }
}

/** Places a sprite in the current frame, scaled from its bitmap to its size in dp. */
private fun DrawScope.drawSprite(sp: SeedSprite, alpha: Float = 1f) {
    withTransform({
        translate(sp.left, sp.top)
        scale(sp.width / sp.image.width, sp.height / sp.image.height, Offset.Zero)
    }) {
        drawImage(sp.image, alpha = alpha.coerceIn(0f, 1f))
    }
}

private val soilA = intArrayOf(0x6A4630, 0x4E321F, 0x3A2415)
private val soilB = intArrayOf(0x432817, 0x2F1C0E, 0x22140A)

/** Everything that moves, in pot coordinates. */
private fun DrawScope.drawHero(
    a: SeedArt,
    sc: SeedSproutWelcomeMotion.Scene,
    crumbs: Path,
    seedTop: Path,
    seedBottom: Path,
    bud: Path,
    stamens: Path,
    stem: Path,
    drops: Path,
    user: Boolean,
) {
    val m = SeedSproutWelcomeMotion
    val pt = DoubleArray(4)

    // Soil: darkens while wet.
    val soil = Brush.linearGradient(
        List(3) { Color(m.mixRgb(soilA[it], soilB[it], sc.wet)) },
        start = Offset(0f, -9f), end = Offset(0f, 9f),
    )
    drawOval(soil, topLeft = Offset(-68f, -9f), size = androidx.compose.ui.geometry.Size(136f, 18f))
    clipRect(-68f, -9f, 68f, 9f) {
        drawPath(crumbs, Color(0xFF7B5638))
        if (sc.wet > 0.001) drawSprite(a.wet, (sc.wet * 0.55).toFloat())
    }

    // The marker, then the seed splitting as the stem starts.
    withTransform({
        translate(-44f, -4f)
        rotateRad(-0.12f, Offset.Zero)
    }) { drawSprite(a.marker) }

    val stemUp = m.stemOf(sc.g)
    val bloom = m.bloomOf(sc.g)
    val sway = m.sway(sc.t, sc.g)
    val open = clamp01(stemUp * 8).toFloat()
    val seedA = (1 - clamp01((stemUp - 0.1) * 6)).toFloat()
    if (seedA > 0) {
        scale(1.6f, 1.6f, Offset.Zero) {
            withTransform({
                translate(-2 - 5 * open, -3f)
                rotateRad(-0.25f - 0.7f * open, Offset.Zero)
            }) { drawPath(seedTop, Color(0xFF7A5530), alpha = seedA) }
            withTransform({
                translate(-2 + 5 * open, -3f)
                rotateRad(-0.25f + 0.5f * open, Offset.Zero)
            }) { drawPath(seedBottom, Color(0xFF5E3E20), alpha = seedA) }
        }
    }

    // The stem, sampled up to its grown length so it really grows.
    if (stemUp > 0.004) {
        stem.rewind()
        stem.moveTo(0f, 0f)
        val up = max(0.001, stemUp)
        for (k in 1..26) {
            m.stemPoint(up * k / 26, sway, pt)
            stem.lineTo(pt[0].toFloat(), pt[1].toFloat())
        }
        drawPath(stem, Color(0xFF4E7F3A), style = Stroke(6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(stem, Color(190 / 255f, 230 / 255f, 150 / 255f, 0.55f), style = Stroke(1.6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }

    // Leaves: grow out once the stem has passed them, unfurling with an overshoot.
    for (k in 0 until 4) {
        val len0 = m.leafLength(stemUp, k)
        if (len0 <= 0) continue
        val wide = m.leafWide(stemUp, k)
        m.stemPoint(m.leafU[k], sway * m.leafU[k], pt)
        val droop = 0.06 * sin(2 * PI * 2 * sc.t / LOOP + k)
        val angle = pt[2] + m.leafSide[k] * (0.25 + 0.95 * wide) + droop
        val len = m.leafLen[k] / 50 * max(0.001, len0)
        val px = pt[0].toFloat()
        val py = pt[1].toFloat()
        withTransform({
            translate(px, py)
            rotateRad(angle.toFloat(), Offset.Zero)
            scale(len.toFloat(), max(0.001, len * (0.3 + 0.7 * wide)).toFloat(), Offset.Zero)
        }) { drawSprite(a.leaf) }
    }

    // The flower at the stem's tip: a bud that opens into seven petals, which fall at the end.
    m.stemPoint(1.0, sway, pt)
    val hx = pt[0].toFloat()
    val hy = pt[1].toFloat()
    val grow = easeOut(clamp01((stemUp - 0.82) / 0.18))
    val budS = (grow * (1 - 0.2 * bloom)).toFloat()
    val budA = (1 - clamp01((bloom - 0.25) / 0.3)).toFloat()
    if (budS > 0.002 && budA > 0) {
        translate(hx, hy) {
            scale(budS, budS, Offset.Zero) {
                drawPath(
                    bud,
                    Brush.linearGradient(listOf(Color(0xFFF08A72), Color(0xFFB9785A), Color(0xFF5C8A44)), Offset(0f, -26f), Offset(0f, 2f)),
                    alpha = budA,
                )
            }
        }
    }
    // Falling petals keep falling from where the flower was, not from the shrinking stem.
    var fx = hx
    var fy = hy
    if (sc.fall > 0) {
        m.stemPoint(1.0, 0.0, pt)
        fx = pt[0].toFloat()
        fy = pt[1].toFloat()
    }
    val b = easeOut(bloom)
    for (i in 0 until m.PETALS) {
        val closed = -PI / 2 + (i - 3) * 0.13
        val opened = -PI / 2 + i * 2 * PI / m.PETALS
        val angle = closed + (opened - closed) * b + PI / 2
        val s = (0.45 + 0.55 * b) * clamp01(bloom * 5) * 1.25
        if (s < 0.002) continue
        val tau = sc.fall * 1.4 - i * 0.12
        val alpha = if (tau <= 0) 1f else (1 - clamp01(tau / 1.05)).toFloat()
        if (alpha <= 0) continue
        withTransform({
            translate(fx, fy)
            if (tau <= 0) {
                rotateRad(angle.toFloat(), Offset.Zero)
            } else {
                val dx = 16 * sin(tau * 5 + i) + tau * (i - 3) * 14
                val dy = 0.5 * 240 * tau * tau
                translate(dx.toFloat(), dy.toFloat())
                rotateRad((angle + tau * (hash(i.toDouble()) - 0.5) * 6).toFloat(), Offset.Zero)
            }
            scale(s.toFloat(), s.toFloat(), Offset.Zero)
        }) { drawSprite(a.petal, alpha) }
    }
    val discA = (1 - clamp01(sc.fall * 3)).toFloat()
    val discS = easeOut(bloom).toFloat()
    if (discA > 0 && discS > 0.002) {
        translate(hx, hy) {
            scale(discS, discS, Offset.Zero) {
                drawCircle(
                    Brush.radialGradient(listOf(Color(0xFFFFE08A), Color(0xFFF2B33D), Color(0xFFC9801C)), Offset(-2f, -3f), 14f),
                    radius = 12f, center = Offset.Zero, alpha = discA,
                )
                drawPath(stamens, Color(0xFF8A4F12), alpha = discA)
            }
        }
    }

    // The shower: in the loop a drop shows only if the can was pouring when it left the rose.
    drops.rewind()
    if (sc.pour > 0 || !user) {
        val tx = m.spoutX(sc.cx, sc.tilt)
        val ty = m.spoutY(sc.cy, sc.tilt)
        for (i in 0 until m.DROPS) {
            if (!m.dropAt(i, sc.t, tx, ty, sc.tilt, pt)) continue
            val on = if (!user) m.pourAt(max(0.0, sc.t - pt[3])) else sc.pour
            if (on <= 0.05) continue
            val r = (pt[2] * min(1.0, on + 0.3)).toFloat()
            val x = pt[0].toFloat()
            val y = pt[1].toFloat()
            drops.addOval(androidx.compose.ui.geometry.Rect(x - r, y - r, x + r, y + r))
        }
        if (!drops.isEmpty) {
            translate(0f, 0.5f) { drawPath(drops, Color(40 / 255f, 90 / 255f, 120 / 255f, 0.35f)) }
            drawPath(drops, Color(150 / 255f, 205 / 255f, 236 / 255f, 0.95f))
        }
    }

    // The can.
    withTransform({
        translate(sc.cx.toFloat(), sc.cy.toFloat())
        rotateRad(sc.tilt.toFloat(), Offset.Zero)
    }) { drawSprite(a.can) }
}
