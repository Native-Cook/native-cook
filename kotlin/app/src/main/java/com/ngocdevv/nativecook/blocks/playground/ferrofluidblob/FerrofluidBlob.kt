package com.ngocdevv.nativecook.blocks.playground.ferrofluidblob

import android.graphics.RuntimeShader
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// The camera looks down at the table from CAMERA_ELEVATION degrees above the horizon, so the round
// dish reads as an ellipse and the spikes stand up off the screen plane.
private const val CAMERA_ELEVATION = 38.0
internal val SinE = sin(CAMERA_ELEVATION * PI / 180)
internal val CosE = cos(CAMERA_ELEVATION * PI / 180)
// The dish: its radius on the table (a share of the width, at most DISH_MAX dp), and where its
// centre sits on the screen (a share of the height).
private const val DISH_SHARE = 0.47
private const val DISH_MAX = 215.0
private const val DISH_AT = 0.56
// The fluid's resting level in dp, used to land a finger on its surface.
internal const val LEVEL = 6.0
// After a touch the loop waits this long, then starts over from rest.
private const val USER_HOLD = 3.5

private val Ink = Color(0xFFE9E4DA)
private val InkSoft = Ink.copy(alpha = 0.5f)
internal val FerroBackground = Color(0xFF0B0B0C)

/** The simulation, the autoPlay clock and the hold after a touch, shared by the frame loop and the gestures. */
private class FerroState(reduced: Boolean) {
    val sim = FerroSim(reduced)
    var clock = 0.0
    var hold = 0.0
    val frame = mutableIntStateOf(0)
}

/**
 * A shallow porcelain dish of black ferrofluid under studio light, drawn by one AGSL shader (a
 * drawn stand-in below Android 13). A finger is a magnet: the fluid heaps up under it and a crown
 * of hexagonal spikes rises, leaning toward it and trailing a drag; a second finger raises a second
 * crown, a tap makes the spikes flinch up, and lifting lets them melt back with a wobble.
 *
 * @param autoPlay sweeps a magnet over the dish, pulses it, brings in a second one, and lets the fluid settle.
 */
@Composable
fun FerrofluidBlob(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val reducedMotion = rememberReducedMotion()
    val auto = autoPlay && !reducedMotion
    val view = LocalView.current
    val screen = LocalDensity.current
    val state = remember(reducedMotion) { FerroState(reducedMotion) }
    val insets = WindowInsets.safeDrawing
    val safeTop = insets.getTop(screen) / screen.density
    val safeBottom = insets.getBottom(screen) / screen.density
    // A shader the driver cannot compile throws: fall back to the drawn scene instead of crashing.
    val shader = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) runCatching { RuntimeShader(FERRO_AGSL) }.getOrNull() else null
    }
    val brush = remember(shader) { shader?.let { ShaderBrush(it) } }
    val fallback = remember { FerroFallback() }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .semantics { contentDescription = "A dish of ferrofluid. Touch to raise spikes, use two fingers for two crowns, tap to pulse." },
    ) {
        val width = maxWidth.value.toDouble()
        val height = maxHeight.value.toDouble()
        val cx = width / 2
        val cy = height * DISH_AT
        val dishR = min(width * DISH_SHARE, DISH_MAX)

        LaunchedEffect(state, auto) {
            state.clock = 0.0
            state.hold = 0.0
        }

        LaunchedEffect(state, auto, dishR) {
            var last = -1L
            while (true) {
                withFrameNanos { now ->
                    // The script and the simulation share one clock, capped so a slow frame cannot skip ahead.
                    val raw = if (last < 0) 1.0 / 60 else (now - last) / 1e9
                    last = now
                    val dt = min(raw, MAX_FRAME)
                    val s = state.sim
                    if (dishR > 0 && dt > 0) {
                        // A finger resting on the dish keeps the script away.
                        if (s.userHolding) state.hold = USER_HOLD
                        if (state.hold > 0) {
                            state.hold -= dt
                            if (state.hold <= 0) state.clock = 0.0
                        } else if (auto) {
                            val before = state.clock
                            val t = (before + dt) % LOOP
                            state.clock = t
                            s.script(before, t, dishR)
                        }
                        s.step(dt)
                        if (s.events != 0) {
                            if (s.eventsByUser) {
                                if (s.events and EVENT_PULSE != 0) {
                                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                                } else if (s.events and EVENT_RISE != 0) {
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                }
                            }
                            s.events = 0
                            s.eventsByUser = false
                        }
                    }
                    state.frame.intValue += 1
                }
            }
        }

        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(state, cx, cy) {
                    /** A screen point, landed on the fluid's surface, in ground coordinates (dish centre = 0). */
                    fun groundX(x: Float) = x / screen.density - cx
                    fun groundY(y: Float) = (y / screen.density - cy + LEVEL * CosE) / SinE
                    // One gesture sees every finger from the moment it lands, so the field rises at
                    // once and a second finger raises a second crown.
                    awaitEachGesture {
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val s = state.sim
                                for (change in event.changes) {
                                    val id = change.id.value
                                    val p = change.position
                                    when {
                                        change.changedToDown() -> {
                                            state.hold = USER_HOLD
                                            s.cancelScripted()
                                            s.touchDown(id, groundX(p.x), groundY(p.y), false, 1.0)
                                        }
                                        change.changedToUp() -> s.touchUp(id)
                                        change.pressed && change.position != change.previousPosition -> {
                                            state.hold = USER_HOLD
                                            s.touchMove(id, groundX(p.x), groundY(p.y), 1.0)
                                        }
                                    }
                                }
                                if (event.changes.none { it.pressed }) break
                            }
                        } finally {
                            // However the gesture ends (lifted, cancelled, or the input restarted),
                            // no magnet stays held by a pointer that is gone.
                            state.sim.releaseUsers()
                        }
                    }
                },
        ) {
            state.frame.intValue
            val d = screen.density
            val s = state.sim
            val a = s.m[0]
            val b = s.m[1]
            scale(d, d, pivot = Offset.Zero) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && shader != null && brush != null) {
                    shader.setFloatUniform("center", cx.toFloat(), cy.toFloat())
                    shader.setFloatUniform("size", maxOf(1.0, width).toFloat(), maxOf(1.0, height).toFloat())
                    shader.setFloatUniform("dishR", dishR.toFloat())
                    shader.setFloatUniform("cam", SinE.toFloat(), CosE.toFloat())
                    shader.setFloatUniform("magA", a.x.toFloat(), a.y.toFloat(), a.field.toFloat(), a.spike.toFloat())
                    shader.setFloatUniform("magB", b.x.toFloat(), b.y.toFloat(), b.field.toFloat(), b.spike.toFloat())
                    shader.setFloatUniform("lean", a.leanX.toFloat(), a.leanY.toFloat(), b.leanX.toFloat(), b.leanY.toFloat())
                    drawRect(brush, Offset.Zero, Size(width.toFloat(), height.toFloat()))
                } else {
                    with(fallback) { draw(width, height, cx, cy, dishR, s) }
                }
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = (safeTop + 22).dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            BasicText(
                "FERROFLUID",
                style = TextStyle(color = Ink, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp),
            )
            BasicText(
                "Magnetic study in a porcelain dish",
                style = TextStyle(color = InkSoft, fontSize = 13.sp, fontWeight = FontWeight.Medium),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .padding(start = 24.dp, end = 24.dp, bottom = (safeBottom + 26).dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            BasicText(
                "Hold to raise · Two fingers, two crowns · Tap to pulse",
                style = TextStyle(
                    color = InkSoft,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.3.sp,
                    textAlign = TextAlign.Center,
                ),
            )
        }
    }
}
