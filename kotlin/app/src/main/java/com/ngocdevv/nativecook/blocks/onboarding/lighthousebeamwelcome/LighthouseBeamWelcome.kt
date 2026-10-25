package com.ngocdevv.nativecook.blocks.onboarding.lighthousebeamwelcome

import android.graphics.BitmapShader
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.cssBoxShadow
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.flow.first
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/*
 Lighthouse Beam Welcome — the onboarding of "Beacon", a navigation and safety app. Night sea at
 dusk, a lighthouse on its rocks, and onboarding text hidden in the fog that shows only where the
 turning beam falls (with a 0.8 s afterglow).

 Rendering: one Canvas. The whole screen is one AGSL shader (the text a child bitmap shader of the
 three pages; a drawn stand-in below Android 13), then the tower (a bitmap made once) and the lamp
 and paging dots (a few draws). The frame loop writes plain fields and bumps one counter that only
 the draw reads.

 Touch: press and drag anywhere above the controls and the beam turns to point at the finger
 (azimuth solved from the finger's position, followed by a spring, ζ 0.85); a quick horizontal
 swipe (> 650 dp/s) changes page (0.6 s). "Get started" goes to the next page. 2 s after the last
 touch the lantern settles back across the text and the page returns to the first (0.8 s), then
 the loop runs from t = 0. Reduced motion: no loop and no turning; the beam rests on the text and
 pages cross-fade.
 */

/** The lantern's spring under the finger. */
private const val AIM_K = 140f
private val AIM_C = 2 * 0.85f * sqrt(AIM_K)
private const val RETURN_S = 0.8f
private const val PIF = PI.toFloat()

private val Cream = Color(0xFFF2E6C8)
private fun nowSeconds() = System.nanoTime() / 1e9

/** Clocks and hand-driven values shared by the frame loop, the touches and the draw; plain fields. */
private class LightState(val g: LightGeo, private val reduced: Boolean) {
    var now = -1.0
    var start = -1.0
    var origin = 0.0
    var resumeAt = -1.0
    // Hand-driven state: the lantern's azimuth (unwrapped), its spring, the trail, the page.
    var phi = 0f
    var phiV = 0f
    var target = 0f
    val trail = FloatArray(5)
    val page = LightTween(0f)
    var touching = false
    /** φ now and the five trail angles, and the page, as drawn. */
    val phis = FloatArray(6)
    var shownPage = 0f
    val tick = mutableIntStateOf(0)
    val wake = mutableIntStateOf(0)

    private fun loopRunning() = start >= 0 && now >= start

    fun step(n: Double, dt: Float, auto: Boolean): Boolean {
        page.step(n)
        tick.intValue += 1
        // Under reduced motion only the page fade runs; the lantern rests, as in React Native.
        if (reduced) return page.active || touching
        val prev = now
        if (prev < 0) {
            origin = n
            if (auto) start = n
        }
        now = n
        val d = if (prev < 0) 0f else dt
        var moving = false
        if (!loopRunning()) {
            val a = AIM_K * (target - phi) - AIM_C * phiV
            phiV += a * d
            phi += phiV * d
            // The afterglow follows the beam through low-pass copies with the trail's delays.
            for (i in trail.indices) {
                trail[i] += (phi - trail[i]) * (1 - exp(-d / LIGHT_TRAIL[i]))
                if (abs(phi - trail[i]) > 1e-4f) moving = true
            }
            if (abs(phiV) > 1e-4f || abs(target - phi) > 1e-4f) moving = true
        }
        val r = resumeAt
        if (r >= 0 && n >= r) {
            resumeAt = -1.0
            target = (phi / (2 * PIF)).roundToInt() * 2 * PIF
            page.tween(n, 0f, RETURN_S)
            start = n + RETURN_S + 0.1
        }
        // Outside autoPlay a resting lantern lets the frame loop sleep (fog and sea pause).
        return auto || start >= 0 || resumeAt >= 0 || page.active || touching || moving
    }

    fun takeOver() {
        if (loopRunning()) {
            val t = ((now - start) % LOOP).toFloat()
            phi = lightPhiAt(t)
            phiV = 0f
            target = phi
            for (i in trail.indices) trail[i] = lightPhiBefore(t, LIGHT_TRAIL[i])
            page.set(lightPageAt(t))
        } else if (start >= 0) {
            page.set(page.value)
        }
        start = -1.0
        resumeAt = -1.0
    }

    fun released(auto: Boolean) {
        if (auto) resumeAt = max(now, nowSeconds()) + 2
    }

    /** Aims the lantern at a finger at (x, y) dp. */
    fun aim(x: Float, y: Float, near: Float) {
        target = lightAimAt(x - g.lampX, y - g.lampY, near)
        if (reduced) {
            phi = target
            trail.fill(phi)
            tick.intValue += 1
        }
    }

    /** Fills phis and shownPage for the last frame. */
    fun scene() {
        if (loopRunning()) {
            val t = ((now - start) % LOOP).toFloat()
            phis[0] = lightPhiAt(t)
            for (i in 0 until 5) phis[i + 1] = lightPhiBefore(t, LIGHT_TRAIL[i])
            shownPage = lightPageAt(t)
            return
        }
        phis[0] = phi
        for (i in 0 until 5) phis[i + 1] = trail[i]
        shownPage = page.value
    }

    fun ambient() = if (now < 0) 0f else ((((now - origin) % LOOP) + LOOP) % LOOP).toFloat()
}

/**
 * The onboarding of a night-navigation app: a lighthouse beam turns over a dusk sea and the text
 * exists only where it falls. Drag to aim the beam, swipe to change page.
 *
 * @param autoPlay turns the lantern twice, changing page while the beam is behind the tower (LOOP 10 s).
 */
@Composable
fun LighthouseBeamWelcome(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val reduced = rememberReducedMotion()
    val auto = autoPlay && !reduced
    val view = LocalView.current
    val screen = LocalDensity.current
    val density = screen.density
    val ratio = min(3f, density)
    val insets = WindowInsets.safeDrawing
    val top = insets.getTop(screen) / density
    val bottom = insets.getBottom(screen) / density
    // A shader the driver cannot compile throws: fall back to the drawn scene instead of crashing.
    val shader = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) runCatching { RuntimeShader(LIGHTHOUSE_AGSL) }.getOrNull() else null
    }
    val brush = remember(shader) { shader?.let { ShaderBrush(it) } }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val w = maxWidth.value
        val h = maxHeight.value
        val state = remember(w, h, top, bottom, reduced) { LightState(LightGeo(w, h, top, bottom), reduced) }
        val g = state.g
        val content = remember(g, ratio) { lighthouseContent(g, ratio) }
        val tower = remember(g, density) { lighthouseTower(g, density) }
        val towerDst = remember(g, density) { RectF(0f, 0f, g.w * density, g.h * density) }
        val live = remember(g, density) { LighthouseLive(g, density) }
        val fallback = remember(g, content, ratio) { LighthouseFallback(g, content, ratio) }
        // The pages as the shader's child: decal, so the shader reads no ink outside the bitmap.
        remember(shader, content) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && shader != null) {
                runCatching { shader.setInputShader("content", BitmapShader(content, Shader.TileMode.DECAL, Shader.TileMode.DECAL)) }
            }
            content
        }

        LaunchedEffect(state, auto) {
            var last = -1L
            while (true) {
                val keep = withFrameNanos { nanos ->
                    val dt = if (last < 0) 0f else min((nanos - last) / 1e9f, 1f / 30f)
                    last = nanos
                    state.step(nanos / 1e9, dt, auto)
                }
                if (!keep) {
                    val seen = state.wake.intValue
                    snapshotFlow { state.wake.intValue }.first { it != seen }
                    last = -1L
                }
            }
        }

        fun goTo(next: Int) {
            val p = max(0, min(LIGHT_PAGES - 1, next))
            if (p == state.page.value.roundToInt()) return
            state.page.tween(nowSeconds(), p.toFloat(), if (reduced) 0.3f else 0.6f)
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            state.wake.intValue += 1
        }

        Canvas(Modifier.fillMaxSize()) {
            state.tick.intValue
            state.scene()
            val phis = state.phis
            val clock = state.ambient()
            scale(density, density, pivot = Offset.Zero) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && shader != null && brush != null) {
                    shader.setFloatUniform("size", max(1f, g.w), max(1f, g.h))
                    shader.setFloatUniform("unit", g.u)
                    shader.setFloatUniform("lamp", g.lampX, g.lampY)
                    shader.setFloatUniform("horizon", g.horizon)
                    shader.setFloatUniform("phiA", phis[0], phis[1], phis[2], phis[3])
                    shader.setFloatUniform("phiB", phis[4], phis[5])
                    shader.setFloatUniform("clk", clock, LOOP)
                    shader.setFloatUniform("text", state.shownPage * g.w, g.contentY, ratio, 0f)
                    drawRect(brush, Offset.Zero, Size(g.w, g.h))
                } else {
                    drawIntoCanvas { fallback.draw(it.nativeCanvas, phis, state.shownPage) }
                }
            }
            drawIntoCanvas { canvas ->
                val c = canvas.nativeCanvas
                c.drawBitmap(tower, null, towerDst, null)
                c.save()
                c.scale(density, density)
                live.drawLamp(c, lightFacing(phis[0]))
                live.drawDots(c, g.w / 2, g.dotsY, state.shownPage)
                c.restore()
            }
        }

        // Touch: everything above the dots aims the beam; a quick swipe changes page.
        Box(
            Modifier
                .fillMaxWidth()
                .height(max(1f, g.dotsY - 16).dp)
                .semantics { contentDescription = "A lighthouse beam reveals the app's features. Drag to aim the beam, swipe to change page." }
                .pointerInput(state, auto, reduced) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val id = down.id
                        state.takeOver()
                        state.touching = true
                        state.aim(down.position.x / density, down.position.y / density, state.phi)
                        state.wake.intValue += 1
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        var ended = false
                        var lastX = down.position.x
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == id } ?: break
                                tracker.addPosition(change.uptimeMillis, change.position)
                                if (!change.pressed) {
                                    ended = true
                                    lastX = change.position.x
                                    break
                                }
                                change.consume()
                                lastX = change.position.x
                                state.aim(change.position.x / density, change.position.y / density, state.target)
                            }
                        } finally {
                            if (ended) {
                                val vx = tracker.calculateVelocity().x / density
                                val tx = (lastX - down.position.x) / density
                                if (abs(vx) > 650 && abs(tx) > 40) goTo(state.page.value.roundToInt() + (if (vx < 0) 1 else -1))
                            }
                            state.touching = false
                            state.released(auto)
                            state.wake.intValue += 1
                        }
                    }
                },
        )

        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = (top + 12).dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText("BEACON", style = TextStyle(color = Cream, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.2.sp))
            BasicText(
                "Skip",
                modifier = Modifier.semantics { contentDescription = "Skip" },
                style = TextStyle(color = Cream.copy(alpha = 0.62f), fontSize = 15.sp, fontWeight = FontWeight.Medium),
            )
        }

        Box(Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp, bottom = (bottom + 16).dp), contentAlignment = Alignment.BottomCenter) {
            GetStartedButton(Modifier.fillMaxWidth().height(56.dp)) {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                state.takeOver()
                goTo(state.page.value.roundToInt() + 1)
                state.released(auto)
                state.wake.intValue += 1
            }
        }
    }
}

private val ButtonFace = Brush.verticalGradient(0f to Color(0xFFFBE2A8), 0.55f to Color(0xFFF3CF86), 1f to Color(0xFFE5B566))

/** "Get started": a warm lamp-lit pill that dips to 97 % under the finger and springs back. */
@Composable
private fun GetStartedButton(modifier: Modifier, onPress: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed = remember { Animatable(0f) }
    LaunchedEffect(source) {
        source.interactions.collect { i ->
            when (i) {
                is PressInteraction.Press -> pressed.animateTo(1f, tween(110))
                is PressInteraction.Release, is PressInteraction.Cancel -> pressed.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 320f))
            }
        }
    }
    Box(
        modifier
            .graphicsLayer {
                val s = 1 - 0.03f * pressed.value
                scaleX = s
                scaleY = s
            }
            .cssBoxShadow(Color(0x59050819), 1.dp, 2.dp, 28.dp)
            .cssBoxShadow(Color(0x38F3C878), 12.dp, 30.dp, 28.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(ButtonFace)
            .drawWithContent {
                drawContent()
                // The inset top highlight.
                drawLine(Color(0x8CFFFFFF), Offset(28.dp.toPx(), 0.5.dp.toPx()), Offset(size.width - 28.dp.toPx(), 0.5.dp.toPx()), 1.dp.toPx())
            }
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onPress)
            .semantics { contentDescription = "Get started" },
        contentAlignment = Alignment.Center,
    ) {
        BasicText("Get started", style = TextStyle(color = Color(0xFF1B1E3D), fontSize = 17.sp, fontWeight = FontWeight.SemiBold))
    }
}
