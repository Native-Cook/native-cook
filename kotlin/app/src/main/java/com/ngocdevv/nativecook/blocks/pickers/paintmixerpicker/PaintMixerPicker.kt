package com.ngocdevv.nativecook.blocks.pickers.paintmixerpicker

import android.graphics.Paint
import android.graphics.RectF
import android.graphics.RuntimeShader
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/*
 Paint Mixer Picker — "Hue Room": pick a wall colour by mixing paint. A glazed bowl seen from
 above, five tins around it; drops swirl into the paint and stirring blends them; the colour's
 name and code update and the room card behind takes the colour.

 Rendering: one Canvas. The table and the room are bitmaps made once; per frame the wall colour,
 the bowl (one AGSL shader over its square; a drawn stand-in below Android 13) and the tins,
 drops, stick and label (a few dozen draws). The frame loop writes plain fields and bumps one
 counter that only the draw reads; outside autoPlay it sleeps once the paint has settled.

 Touch: drag from a tin to lift a drop (it hangs above the fingertip) and let go over the paint to
 drop it in (light haptic on landing); let go elsewhere and it flies back. Drag inside the bowl to
 stir: the swirl follows the finger's angular speed and mixing follows how far it has turned.
 2 s after the last touch the bowl washes back to white (0.85 s) and the loop restarts. Reduced
 motion: no loop, no swirl; drops land at once and the colour fades in evenly.
 */

private const val WASH_HOLD = 0.9
/** The name/code roll: 260 ms per line, the new one 60 ms behind. */
private const val LABEL_ROLL = 0.32f

private fun nowSeconds() = System.nanoTime() / 1e9

/** Clocks, the sim and what the draw shows; plain fields shared by the frame loop and the touches. */
private class PaintState(val g: PaintGeo, reduced: Boolean) {
    var now = -1.0
    var start = -1.0
    var resumeAt = -1.0
    val sim = PaintSim(reduced)
    var frameMix: PaintMix? = null
    var shown = PAINT_BASE_RGB.copyOf()
    val label = PaintLabel()
    var labelAt = -10.0
    var touching = false
    val tick = mutableIntStateOf(0)
    val wake = mutableIntStateOf(0)
    private val tmp = FloatArray(3)

    private fun loopRunning() = start >= 0 && now >= start

    /** The script's label at t: the current name and, within the roll after a change, the one before. */
    private fun autoLabel(t: Float) {
        val cur = paintNearestName(paintShownColor(g, t, tmp))
        for (k in 1..7) {
            val back = (((t - k * 0.05f) % LOOP) + LOOP) % LOOP
            val rgb = paintShownColor(g, back)
            val idx = paintNearestName(rgb)
            if (idx != cur) {
                label.set(cur, idx, (k - 0.5f) * 0.05f, rgb)
                return
            }
        }
        label.set(cur, cur, 1f, PAINT_BASE_RGB)
    }

    fun step(n: Double, dt: Float, auto: Boolean, haptic: (Int) -> Unit): Boolean {
        val prev = now
        if (prev < 0 && auto) start = n
        now = n
        val d = if (prev < 0) 0f else dt
        if (loopRunning()) {
            val t = ((n - start) % LOOP).toFloat()
            frameMix = paintAutoMix(g, t)
            shown = paintShownColor(g, t)
            autoLabel(t)
            tick.intValue += 1
            return true
        }
        val s = sim
        s.step(n, d)
        if (s.events != 0) {
            haptic(s.events)
            s.events = 0
        }
        val labelMoving = n - labelAt < LABEL_ROLL + 0.05
        val mix = s.mix(g, n)
        val target = paintMixColor(mix, tmp)
        val cur = shown
        val settling = abs(target[0] - cur[0]) + abs(target[1] - cur[1]) + abs(target[2] - cur[2]) > 0.002f
        val busy = s.busy()
        if (busy || settling || labelMoving || frameMix == null) {
            frameMix = mix
            val k = 1 - exp(-d / 0.18f)
            val next = floatArrayOf(cur[0] + (target[0] - cur[0]) * k, cur[1] + (target[1] - cur[1]) * k, cur[2] + (target[2] - cur[2]) * k)
            shown = next
            val idx = paintNearestName(next)
            if (idx != label.cur) {
                labelAt = n
                label.set(idx, label.cur, 0f, cur)
            } else if (label.since < LABEL_ROLL) {
                label.since = (n - labelAt).toFloat()
            }
            tick.intValue += 1
        }
        val r = resumeAt
        if (r >= 0 && n >= r && !s.holding && !s.flying) {
            resumeAt = -1.0
            s.startWash(n)
            start = n + WASH_HOLD
        }
        return auto || start >= 0 || resumeAt >= 0 || busy || settling || labelMoving || touching
    }

    fun takeOver() {
        if (loopRunning()) sim.fromAuto(g, ((now - start) % LOOP).toFloat(), now)
        start = -1.0
        resumeAt = -1.0
    }
}

/**
 * A wall-colour picker where the colour is mixed: drag drops of paint from five tins into a glazed
 * bowl and stir; the name, code and the room card behind follow the mix.
 *
 * @param autoPlay drops yellow and teal in, stirs them to "Morning Fern" and washes the bowl back (LOOP 9 s).
 */
@Composable
fun PaintMixerPicker(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val reduced = rememberReducedMotion()
    val auto = autoPlay && !reduced
    val view = LocalView.current
    val screen = LocalDensity.current
    val density = screen.density
    val insets = WindowInsets.safeDrawing
    val top = insets.getTop(screen) / density
    val bottom = insets.getBottom(screen) / density
    // A shader the driver cannot compile throws: fall back to the drawn bowl instead of crashing.
    val shader = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) runCatching { RuntimeShader(PAINT_AGSL) }.getOrNull() else null
    }
    val brush = remember(shader) { shader?.let { ShaderBrush(it) } }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val w = maxWidth.value
        val h = maxHeight.value
        val state = remember(w, h, top, bottom, reduced) { PaintState(PaintGeo(w, h, top, bottom), reduced) }
        val g = state.g
        val fonts = remember { PaintFonts() }
        val table = remember(g, density) { paintTableBitmap(g, density) }
        val room = remember(g, density) { paintRoomBitmap(g, fonts, density) }
        val screenDst = remember(g, density) { RectF(0f, 0f, g.w * density, g.h * density) }
        val dynamicArt = remember(g) { PaintDynamicArt(g, fonts) }
        val fallback = remember(g) { PaintFallback(g) }
        val card = remember(g) { paintCardRect(g) }
        val wall = remember { Paint(Paint.ANTI_ALIAS_FLAG) }
        val haptic: (Int) -> Unit = { events ->
            if (events and PAINT_EVENT_LAND != 0) {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            } else if (events and PAINT_EVENT_GRAB != 0) {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            }
        }

        LaunchedEffect(state, auto) {
            var last = -1L
            while (true) {
                val keep = withFrameNanos { nanos ->
                    val dt = if (last < 0) 0f else min((nanos - last) / 1e9f, 1f / 30f)
                    last = nanos
                    state.step(nanos / 1e9, dt, auto, haptic)
                }
                if (!keep) {
                    val seen = state.wake.intValue
                    snapshotFlow { state.wake.intValue }.first { it != seen }
                    last = -1L
                }
            }
        }

        Canvas(
            Modifier
                .fillMaxSize()
                .semantics { contentDescription = "Paint mixing bowl. Drag a tin's drop into the bowl, drag in the bowl to stir." }
                .pointerInput(state, auto) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val id = down.id
                        state.takeOver()
                        state.touching = true
                        state.sim.down(g, down.position.x / density, down.position.y / density)
                        state.wake.intValue += 1
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == id } ?: break
                                if (!change.pressed) break
                                tracker.addPosition(change.uptimeMillis, change.position)
                                val v = tracker.calculateVelocity()
                                state.sim.move(g, change.position.x / density, change.position.y / density, v.x / density, v.y / density)
                                change.consume()
                            }
                        } finally {
                            val n = max(state.now, nowSeconds())
                            state.sim.up(g, n)
                            state.touching = false
                            if (auto) state.resumeAt = n + 2
                            state.wake.intValue += 1
                        }
                    }
                },
        ) {
            state.tick.intValue
            val m = state.frameMix ?: return@Canvas
            drawIntoCanvas { canvas ->
                val c = canvas.nativeCanvas
                c.drawBitmap(table, null, screenDst, null)
                c.save()
                c.scale(density, density)
                wall.color = paintRgbInt(state.shown)
                c.drawRoundRect(card, PAINT_CARD_R, PAINT_CARD_R, wall)
                c.restore()
                c.drawBitmap(room, null, screenDst, null)
            }
            scale(density, density, pivot = Offset.Zero) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && shader != null && brush != null) {
                    shader.setFloatUniform("center", g.cx, g.cy)
                    shader.setFloatUniform("rim", g.rim)
                    shader.setFloatUniform("paintR", g.paint)
                    shader.setFloatUniform("unit", g.u)
                    shader.setFloatUniform("baseA", m.baseA[0], m.baseA[1], m.baseA[2], m.baseW)
                    for (i in 0 until 4) {
                        val d = m.drops.getOrNull(i)
                        if (d != null) {
                            shader.setFloatUniform("dA$i", d.a[0], d.a[1], d.a[2], PAINT_K * d.amt * m.fade)
                            shader.setFloatUniform("dP$i", d.x, d.y, d.m, d.tw)
                        } else {
                            shader.setFloatUniform("dA$i", 0f, 0f, 0f, 0f)
                            shader.setFloatUniform("dP$i", 0f, 0f, 0f, 0f)
                        }
                    }
                    for (i in 0 until 3) {
                        val r = m.ripples.getOrNull(i)
                        if (r != null) shader.setFloatUniform("rip$i", r.x, r.y, r.age, r.str) else shader.setFloatUniform("rip$i", 0f, 0f, 0f, 0f)
                    }
                    shader.setFloatUniform("wash", m.washFront, m.washTwist)
                    drawRect(brush, Offset(g.cx - g.rim - 40, g.cy - g.rim - 30), Size(2 * g.rim + 80, 2 * g.rim + 80))
                } else {
                    drawIntoCanvas { fallback.draw(it.nativeCanvas, m) }
                }
                drawIntoCanvas { dynamicArt.draw(it.nativeCanvas, m, state.shown, state.label) }
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = (top + 8).dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            BasicText("HUE ROOM", style = TextStyle(color = Color(0xFF8A7F72), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.4.sp))
            BasicText("Mix your wall colour", style = TextStyle(color = Color(0xFF2B2620), fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp))
            BasicText("Drop paint into the bowl, then stir.", style = TextStyle(color = Color(0xFF7A7064), fontSize = 15.sp, fontWeight = FontWeight.Normal))
        }

        ApplyButton(
            Modifier
                .offset(0.dp, g.buttonY.dp)
                .padding(horizontal = 24.dp)
                .fillMaxWidth()
                .height(52.dp),
        ) {
            view.performHapticFeedback(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.CONTEXT_CLICK,
            )
        }
    }
}

/** "Apply to wall": a dark pill that dips to 97 % under the finger and springs back. */
@Composable
private fun ApplyButton(modifier: Modifier, onPress: () -> Unit) {
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
            .cssBoxShadow(Color(0x4D2B2620), 1.dp, 2.dp, 26.dp)
            .cssBoxShadow(Color(0x2E2B2620), 10.dp, 24.dp, 26.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(Color(0xFF2B2620))
            .drawWithContent {
                drawContent()
                // The inset top highlight.
                drawLine(Color(0x1FFFFFFF), Offset(26.dp.toPx(), 0.5.dp.toPx()), Offset(size.width - 26.dp.toPx(), 0.5.dp.toPx()), 1.dp.toPx())
            }
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onPress),
        contentAlignment = Alignment.Center,
    ) {
        BasicText("Apply to wall", style = TextStyle(color = Color(0xFFF5F0E8), fontSize = 17.sp, fontWeight = FontWeight.SemiBold))
    }
}
