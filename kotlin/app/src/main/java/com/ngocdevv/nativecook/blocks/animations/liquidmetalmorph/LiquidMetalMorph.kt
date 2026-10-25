package com.ngocdevv.nativecook.blocks.animations.liquidmetalmorph

import android.graphics.RuntimeShader
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/*
 Liquid Metal Morph — "Forge", a mode switch whose icon is a blob of liquid chrome. Port of
 LiquidMetalMorph.tsx: one AGSL shader draws the backdrop and the blob (a drawn stand-in below
 Android 13); it morphs drop → four-point star → ring → drop, throwing off satellite droplets.
 Under it the state rolls (Melting / Casting / Polishing) with three dots, and a segmented control
 (Drop, Star, Ring) whose pill slides with the morph.

 autoPlay (LOOP = 9 s): 0–1.6 rest, 1.6–3.2 drop → star, 4.6–6.2 star → ring, 7.0–8.4 ring → drop,
 a 0.6 s jiggle after each morph. Touch: drag from the blob pulls a droplet on a tether (light
 haptic at 120 dp), release flies it home with the finger's velocity; tap a segment to morph on a
 playful spring. 2 s after the last touch it eases back to the drop and the loop restarts at 0.
 Reduced motion: no loop, no wobble; the droplet and the segments still answer the finger.
 */

internal val LiquidMetalBackground = Color(0xFF16171B)
private val Ink = Color(0xFFE9EBF0)
private val Mute = Ink.copy(alpha = 0.56f)
private val Faint = Ink.copy(alpha = 0.32f)
private val SegIdle = Color(0xFF9095A1)
private val SegOn = Color(0xFF17181D)
private val Roll = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

private val States = listOf(
    "Melting" to "Drop · alloy at 356 °C",
    "Casting" to "Star · cooling to 210 °C",
    "Polishing" to "Ring · buffing to a mirror",
)
private val Segments = listOf("Drop", "Star", "Ring")
private val Readings = listOf("356 °C" to "alloy", "1.5 cP" to "viscosity", "0.8 s" to "per cast")
private const val SEGMENT_W = 96f
private const val LABEL_ROLL = 260

private class LiquidMetalHolder(auto: Boolean, reduced: Boolean) {
    val m = LiquidMetal(auto, reduced)
    val tick = mutableIntStateOf(0)
    /** Bumped by a touch to wake the frame loop after it went idle. */
    val wake = mutableIntStateOf(0)
}

private fun fade(w: Double) = min(max((w - 0.35) / 0.4, 0.0), 1.0).toFloat()

@Composable
fun LiquidMetalMorph(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val reduced = rememberReducedMotion()
    val auto = autoPlay && !reduced
    val view = LocalView.current
    val screen = LocalDensity.current
    val insets = WindowInsets.safeDrawing
    val safeTop = insets.getTop(screen) / screen.density
    val safeBottom = insets.getBottom(screen) / screen.density
    val holder = remember(auto, reduced) { LiquidMetalHolder(auto, reduced) }
    val shader = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) runCatching { RuntimeShader(LIQUID_METAL_AGSL) }.getOrNull() else null
    }
    val brush = remember(shader) { shader?.let { ShaderBrush(it) } }

    BoxWithConstraints(modifier.fillMaxSize().background(LiquidMetalBackground)) {
        val width = maxWidth.value
        val height = maxHeight.value
        val cx = width / 2
        val cy = height * 0.41f
        val r = minOf(width * 0.23f, 96f, height * 0.11f)
        val statusTop = cy + r * 2.05f
        val footerTop = height - safeBottom - 24 - 84
        val showReadings = footerTop - (statusTop + 80) > 76

        LaunchedEffect(holder, r, holder.wake.intValue) {
            if (r <= 0) return@LaunchedEffect
            val m = holder.m
            var last = -1L
            while (true) {
                val idle = withFrameNanos { now ->
                    val dt = if (last < 0) 1.0 / 60 else min((now - last) / 1e9, 1.0 / 20)
                    last = now
                    m.now += dt
                    when (m.mode) {
                        MODE_AUTO -> {
                            if (auto) m.t = (m.t + dt) % LOOP
                            m.scriptFrame(m.t, r.toDouble())
                        }
                        MODE_LIVE -> {
                            m.liveFrame(dt)
                            if (auto && !m.held && m.now - m.lastTouch > LIQUID_METAL_IDLE_BEFORE_BACK) m.beginBack()
                        }
                        else -> if (m.backFrame(dt)) {
                            m.mode = MODE_AUTO
                            m.t = 0.0
                        }
                    }
                    if (m.buzz) {
                        m.buzz = false
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    }
                    holder.tick.intValue += 1
                    !m.moving()
                }
                if (idle) break
            }
        }

        fun touched() {
            val m = holder.m
            m.lastTouch = m.now
            if (m.mode != MODE_LIVE) {
                m.mode = MODE_LIVE
                m.v.fill(0.0)
                var best = 0
                for (i in 1 until 3) if (m.w[i] > m.w[best]) best = i
                m.target = best
            }
            holder.wake.intValue += 1
        }

        Canvas(
            Modifier
                .fillMaxSize()
                .semantics { contentDescription = "A blob of liquid metal. Drag from it to pull out a droplet." }
                .pointerInput(holder, cx, cy, r) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val m = holder.m
                        touched()
                        val d = screen.density
                        val x0 = down.position.x / d
                        val y0 = down.position.y / d
                        val inside = hypot(x0 - cx, y0 - cy) < r * 1.25f
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        if (inside) {
                            m.held = true
                            m.shown = true
                            m.detached = false
                            m.hx = x0.toDouble(); m.hy = y0.toDouble()
                            m.fx = m.hx; m.fy = m.hy
                            m.dx = m.hx; m.dy = m.hy
                            m.dvx = 0.0; m.dvy = 0.0
                        }
                        var released = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            m.lastTouch = m.now
                            tracker.addPosition(change.uptimeMillis, change.position)
                            if (!change.pressed) {
                                released = true
                                break
                            }
                            if (change.positionChange() != Offset.Zero && m.held) {
                                change.consume()
                                m.fx = (change.position.x / d).toDouble()
                                m.fy = (change.position.y / d).toDouble()
                                val dist = hypot(m.fx - m.hx, m.fy - m.hy)
                                if (!m.detached && dist > LIQUID_METAL_DETACH_AT) {
                                    m.detached = true
                                    m.buzz = true
                                } else if (m.detached && dist < LIQUID_METAL_DETACH_AT * 0.7) {
                                    m.detached = false
                                }
                            }
                        }
                        if (released && m.held) {
                            val v = tracker.calculateVelocity()
                            m.dvx = (v.x / d).toDouble()
                            m.dvy = (v.y / d).toDouble()
                        }
                        m.held = false
                        m.lastTouch = m.now
                    }
                },
        ) {
            holder.tick.intValue
            val m = holder.m
            val d = density
            scale(d, d, pivot = Offset.Zero) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && shader != null && brush != null) {
                    val dropR = if (m.shown) m.dropletRadius(r.toDouble()).toFloat() else 0f
                    val s = m.sats
                    shader.setFloatUniform("size", max(1f, width), max(1f, height))
                    shader.setFloatUniform("center", cx, cy)
                    shader.setFloatUniform("R", max(r, 1f))
                    shader.setFloatUniform("w", m.w[0].toFloat(), m.w[1].toFloat(), m.w[2].toFloat())
                    shader.setFloatUniform("wob", m.p1.toFloat(), m.p2.toFloat(), m.amp.toFloat(), m.jiggle.toFloat())
                    shader.setFloatUniform("sat0", s[0].toFloat(), s[1].toFloat(), s[2].toFloat(), s[3].toFloat())
                    shader.setFloatUniform("sat1", s[4].toFloat(), s[5].toFloat(), s[6].toFloat(), s[7].toFloat())
                    shader.setFloatUniform("sat2", s[8].toFloat(), s[9].toFloat(), s[10].toFloat(), s[11].toFloat())
                    shader.setFloatUniform("sat3", s[12].toFloat(), s[13].toFloat(), s[14].toFloat(), s[15].toFloat())
                    shader.setFloatUniform("finger", m.dx.toFloat(), m.dy.toFloat(), dropR, LIQUID_METAL_TETHER_K.toFloat())
                    drawRect(brush, Offset.Zero, Size(width, height))
                } else {
                    liquidMetalFallback(width, height, cx, cy, r, m)
                }
            }
        }

        // Header.
        Column(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = (safeTop + 20).dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            BasicText("FORGE", style = TextStyle(color = Faint, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp))
            BasicText("Casting your workspace", style = TextStyle(color = Ink, fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp))
            BasicText("Liquid alloy preset · 3 modes", style = TextStyle(color = Mute, fontSize = 15.sp, fontWeight = FontWeight.Medium))
        }

        // The dominant shape, for the label's roll; recomputed per frame, changes three times a loop.
        val active by remember(holder) {
            derivedStateOf {
                holder.tick.intValue
                val w = holder.m.w
                var best = 0
                for (i in 1 until 3) if (w[i] > w[best]) best = i
                best
            }
        }
        // 0 = waiting below, 1 = shown, 2 = gone above.
        val shown = remember(holder) { List(3) { Animatable(if (it == 0) 1f else 0f) } }
        val previous = remember(holder) { intArrayOf(0) }
        LaunchedEffect(active) {
            val before = previous[0]
            previous[0] = active
            if (before == active) return@LaunchedEffect
            for (i in 0 until 3) if (i != active && i != before) shown[i].snapTo(0f)
            shown[active].snapTo(0f)
            kotlinx.coroutines.coroutineScope {
                launch { shown[active].animateTo(1f, tween(LABEL_ROLL, easing = Roll)) }
                launch { shown[before].animateTo(2f, tween(LABEL_ROLL, easing = Roll)) }
            }
        }

        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).offset(y = statusTop.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(Modifier.fillMaxWidth().height(56.dp)) {
                States.forEachIndexed { i, (title, line) ->
                    Column(
                        Modifier.fillMaxWidth().graphicsLayer {
                            val v = shown[i].value
                            alpha = if (v <= 1f) v else 2f - v
                            translationY = (1f - v) * 14f * density
                        },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        BasicText(title, style = TextStyle(color = Ink, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp))
                        BasicText(line, style = TextStyle(color = Mute, fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum"))
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (i in 0 until 3) {
                    Box(
                        Modifier
                            .size(6.dp)
                            .graphicsLayer {
                                holder.tick.intValue
                                val f = fade(holder.m.w[i])
                                alpha = 0.3f + 0.7f * f
                                scaleX = 1f + 1.6f * f
                            }
                            .background(Ink, CircleShape),
                    )
                }
            }
        }

        if (showReadings) {
            Row(
                Modifier
                    .padding(horizontal = 24.dp)
                    .offset(y = (footerTop - 24 - 56).dp)
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(18.dp))
                    .border(0.5.dp, Color.White.copy(alpha = 0.09f), RoundedCornerShape(18.dp)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Readings.forEach { (value, label) ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        BasicText(value, style = TextStyle(color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"))
                        BasicText(label, style = TextStyle(color = Faint, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp))
                    }
                }
            }
        }

        // Footer: the segmented control and the hint.
        Column(
            Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp, bottom = (safeBottom + 24).dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.Bottom),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .shadow(16.dp, RoundedCornerShape(24.dp), ambientColor = Color.Black.copy(alpha = 0.28f), spotColor = Color.Black.copy(alpha = 0.3f))
                    .background(Color(0xEB282A31), RoundedCornerShape(24.dp))
                    .border(0.5.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(24.dp))
                    .padding(4.dp),
            ) {
                // The pill, sliding with the weights; its small shadow is drawn, not elevated.
                Box(
                    Modifier
                        .width(SEGMENT_W.dp)
                        .height(40.dp)
                        .graphicsLayer {
                            holder.tick.intValue
                            val w = holder.m.w
                            translationX = ((w[1] + 2 * w[2]) * SEGMENT_W).toFloat() * density
                        }
                        .drawBehind {
                            val cr = CornerRadius(20.dp.toPx())
                            drawRoundRect(Color.Black.copy(alpha = 0.22f), Offset(0f, 1.dp.toPx()), size, cr)
                            drawRoundRect(Color(0xFFD9DDE5), cornerRadius = cr)
                            drawRoundRect(
                                Color.White.copy(alpha = 0.7f),
                                topLeft = Offset(cr.x * 0.5f, 0f),
                                size = Size(size.width - cr.x, 1.dp.toPx()),
                            )
                        },
                )
                Row {
                    Segments.forEachIndexed { i, label ->
                        Box(
                            Modifier
                                .width(SEGMENT_W.dp)
                                .height(40.dp)
                                .semantics { contentDescription = "$label shape" }
                                .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) {
                                    touched()
                                    holder.m.target = i
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            BasicText(
                                label,
                                style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center),
                                color = ColorProducer {
                                    holder.tick.intValue
                                    lerp(SegIdle, SegOn, fade(holder.m.w[i]))
                                },
                            )
                        }
                    }
                }
            }
            BasicText("Pull a droplet out of the metal", style = TextStyle(color = Faint, fontSize = 13.sp, fontWeight = FontWeight.Medium))
        }
    }
}
