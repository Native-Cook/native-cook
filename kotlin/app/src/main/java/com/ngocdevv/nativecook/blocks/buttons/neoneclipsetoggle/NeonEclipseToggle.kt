package com.ngocdevv.nativecook.blocks.buttons.neoneclipsetoggle

import android.graphics.Paint
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt

/** Seconds. */
const val LOOP = 8f
private const val TW = 236f
private const val TH = 108f
private const val TRAVEL = TW - TH
private const val HOLD = 2f

// RN reference timeline, in seconds:
// 0–1.2 off; 1.2–1.7 ease-out-back knob; 1.2–1.6 cubic darkness;
// 1.4–2.4 cubic plasma flood; 2.4–6.2 flowing neon; 6.2–6.8 cubic drain;
// 6.4–6.9 cubic knob return; 6.5–6.9 white pearl return; 6.9–8 off.
// The shader phase is 2πt/8. Tap toggles; drag follows the finger then snaps.
// Two seconds after release the loop resumes at t=3 when on, or t=0 when off.
private fun seg(t: Float, a: Float, b: Float) = ((t - a) / (b - a)).coerceIn(0f, 1f)
private fun easeOutBack(x: Float) = 1f + 2.4f * (x - 1f).pow(3) + 1.4f * (x - 1f).pow(2)
private fun easeOutCubic(x: Float) = 1f - (1f - x).pow(3)
private fun easeInOutCubic(x: Float) = if (x < 0.5f) 4f * x * x * x else 1f - (-2f * x + 2f).pow(3) / 2f

/** Updated by the frame clock; reads occur only in drawing/layer lambdas. */
private class NeonEclipseToggleMotion {
    var elapsed = 0.0
    var origin = 0.0
    var phase = 0f
    var pos = 0f
    var fill = 0f
    var dark = 0f
    var touching = false
    var manual = false
    var released = -1.0
    var target = 0f
    var selected by mutableStateOf(false)

    fun choose(on: Float) {
        touching = false
        manual = true
        target = on
        selected = on > 0.5f
        released = elapsed
    }

    fun step(dt: Float, autoPlay: Boolean) {
        elapsed += dt
        val t = ((elapsed - origin) % LOOP).toFloat()
        phase = t / LOOP * (2.0 * PI).toFloat()
        if (touching || manual) {
            if (!touching) pos += (target - pos) * (1f - exp(-10f * dt))
            dark += (target - dark) * (1f - exp(-8f * dt))
            fill += (target - fill) * (1f - exp(-(if (target > 0.5f) 4f else 7f) * dt))
            if (!touching && elapsed - released > HOLD) {
                manual = false
                origin = if (target > 0.5f) elapsed - 3.0 else elapsed
            }
            return
        }
        if (!autoPlay) return
        pos = easeOutBack(seg(t, 1.2f, 1.7f)) - easeInOutCubic(seg(t, 6.4f, 6.9f))
        fill = easeOutCubic(seg(t, 1.4f, 2.4f)) * (1f - seg(t, 6.2f, 6.8f).pow(3))
        dark = easeInOutCubic(seg(t, 1.2f, 1.6f)) * (1f - easeInOutCubic(seg(t, 6.5f, 6.9f)))
        selected = pos > 0.5f
    }
}

@Composable
fun NeonEclipseToggle(
    autoPlay: Boolean = false,
    brand: String = "Lumo Frame",
    title: String = "Make the\nwall glow",
    modifier: Modifier = Modifier,
) {
    val reduced = rememberReducedMotion()
    val owner = LocalLifecycleOwner.current
    var active by remember(owner) { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, _ ->
            active = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    // Reduced motion has the reference's initial, legible off state and no gestures/clock.
    val motion = remember(reduced) { NeonEclipseToggleMotion() }
    val tick = remember { mutableLongStateOf(0L) }
    LaunchedEffect(active, reduced, autoPlay, motion) {
        if (!active || reduced) return@LaunchedEffect
        var previous = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (previous == 0L) 0f else (now - previous) / 1e9f
                previous = now
                motion.step(dt, autoPlay)
                tick.longValue = now
            }
        }
    }
    val density = LocalDensity.current
    val d = density.density
    val safeTop = WindowInsets.safeDrawing.getTop(density) / d
    val safeBottom = WindowInsets.safeDrawing.getBottom(density) / d
    val view = LocalView.current
    val shader = remember { if (Build.VERSION.SDK_INT >= 33) NeonEclipseToggleShader() else null }
    val fallback = remember { if (Build.VERSION.SDK_INT < 33) NeonEclipseToggleFallback() else null }
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }

    BoxWithConstraints(
        modifier.fillMaxSize().drawBehind {
            tick.longValue
            val f = motion.fill
            drawRect(Color((16f - 9f * f).roundToInt(), (16f - 6f * f).roundToInt(), (17f + 10f * f).roundToInt()))
        },
    ) {
        val width = maxWidth.value
        val height = maxHeight.value
        val x0 = (width - TW) / 2f
        val y0 = height * 0.46f - TH / 2f
        Column(Modifier.offset(24.dp, (safeTop + 8f).dp).size((width - 48f).dp, 160.dp)) {
            Row(Modifier.fillMaxWidth().height(34.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(brand, style = textStyle(17f, FontWeight.ExtraBold, tracking = 0.2f))
                Spacer(Modifier.weight(1f))
                Text("Living room", style = textStyle(14f, FontWeight.SemiBold, 0.55f))
            }
            Text(title, modifier = Modifier.padding(top = 16.dp), style = textStyle(44f, FontWeight.Black, tracking = -1.5f, lineHeight = 48f))
        }

        Canvas(Modifier.fillMaxSize()) {
            tick.longValue
            val canvas = drawContext.canvas.nativeCanvas
            if (Build.VERSION.SDK_INT >= 33 && shader != null) {
                shader.update(d, x0, y0, motion.pos, motion.fill, motion.dark, motion.phase)
                paint.color = android.graphics.Color.WHITE
                paint.alpha = 255
                paint.shader = shader.shader
                canvas.drawRect(0f, (y0 - 160f) * d, size.width, (y0 + TH + 200f) * d, paint)
            } else {
                canvas.save()
                canvas.scale(d, d)
                fallback?.draw(canvas, width, x0, y0, motion.pos, motion.fill, motion.dark, motion.phase)
                canvas.restore()
            }
        }

        Box(
            Modifier.offset((x0 - 12f).dp, (y0 - 16f).dp).size((TW + 24f).dp, (TH + 32f).dp)
                .semantics {
                    contentDescription = "Art mode"
                    role = Role.Switch
                    toggleableState = if (motion.selected) ToggleableState.On else ToggleableState.Off
                    if (!reduced) onClick {
                        motion.choose(if (motion.pos > 0.5f) 0f else 1f)
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        true
                    }
                }
                .pointerInput(reduced, motion) {
                    if (reduced) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val grab = motion.pos
                        var dragging = false
                        var completed = false
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    completed = dragging || change.uptimeMillis - down.uptimeMillis <= 500L
                                    break
                                }
                                val delta = change.position - down.position
                                if (!dragging && delta.getDistance() >= 6f * d) {
                                    dragging = true
                                    motion.touching = true
                                    motion.manual = true
                                }
                                if (dragging) {
                                    motion.pos = (grab + delta.x / d / TRAVEL).coerceIn(0f, 1f)
                                    motion.target = if (motion.pos > 0.5f) 1f else 0f
                                    change.consume()
                                }
                            }
                        } finally {
                            if (completed || dragging) {
                                motion.choose(if (dragging) {
                                    if (motion.pos > 0.5f) 1f else 0f
                                } else {
                                    if (motion.pos > 0.5f) 0f else 1f
                                })
                                if (completed) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            }
                        }
                    }
                },
        )

        Box(Modifier.offset(24.dp, (y0 - 46f).dp).size((width - 48f).dp, 24.dp), contentAlignment = Alignment.TopCenter) {
            Text("ART MODE", style = textStyle(14f, FontWeight.ExtraBold, tracking = 3f))
        }
        Box(Modifier.offset(24.dp, (y0 + TH + 92f).dp).size((width - 48f).dp, 28.dp), contentAlignment = Alignment.TopCenter) {
            Text("Off · the frame sleeps", style = textStyle(16f, FontWeight.SemiBold, 0.75f), modifier = Modifier.graphicsLayer {
                tick.longValue
                alpha = 1f - (motion.fill * 2f - 0.4f).coerceIn(0f, 1f)
            })
            Text("On · showing “Solar Bloom”", style = textStyle(16f, FontWeight.SemiBold, 0.75f), modifier = Modifier.graphicsLayer {
                tick.longValue
                alpha = (motion.fill * 2f - 0.4f).coerceIn(0f, 1f)
            })
        }
        Column(
            Modifier.align(Alignment.BottomCenter).padding(start = 20.dp, end = 20.dp, bottom = (safeBottom + 16f).dp)
                .fillMaxWidth().background(Color.White.copy(alpha = 0.07f), RoundedCornerShape(22.dp))
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(22.dp)).padding(1.dp).padding(horizontal = 18.dp),
        ) {
            CardRow("Glow schedule", "18:00 – 23:30")
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.1f)))
            CardRow("Brightness", "70 %")
        }
    }
}

private fun textStyle(size: Float, weight: FontWeight, alpha: Float = 1f, tracking: Float = 0f, lineHeight: Float? = null) = TextStyle(
    color = Color.White.copy(alpha = alpha),
    fontSize = size.sp,
    fontWeight = weight,
    letterSpacing = tracking.sp,
    lineHeight = (lineHeight ?: (size * 1.2f)).sp,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
)

@Composable
private fun CardRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = textStyle(16f, FontWeight.SemiBold))
        Spacer(Modifier.weight(1f))
        Text(value, style = textStyle(16f, FontWeight.Normal, 0.6f))
    }
}
