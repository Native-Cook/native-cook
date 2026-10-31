package com.ngocdevv.nativecook.blocks.inputs.agentstatuspill

import android.graphics.Paint
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlin.math.PI
import kotlin.math.floor

// A direct port of AgentStatusPill.tsx. Four shader looks share one 8.8 s clock: each step
// lasts 2.2 s, with a smoothstep cross-fade during its final 0.4 s. Labels move down 6 dp
// as they fade. Ticks appear 0.2 s before each step ends and clear during 8.4–8.8 s.
// The pill is 300 × 84 dp, centred at 40% of the screen height. Tap advances to the current
// step's fade start and continues playback, exactly as in RN (there is no touch hold there).
// Reduced motion freezes the clock; a non-playing or initially reduced view holds t = 0.6 s.
// API 33+ uses the original shader in AGSL; older devices sample the same formula into a bitmap.

/** Seconds. */
private const val LOOP = 8.8
private const val STEP = 2.2
private const val FADE = 0.4
private val BACKGROUND = Color(0xFF15171A)
private val LABELS = listOf("Thinking…", "Searching…", "Planning…", "Writing…")
private val ROWS = listOf(
    "Reading your request", "Searching flights and stays",
    "Planning a three-day route", "Writing your itinerary",
)

private fun smooth(value: Double): Float {
    val u = value.coerceIn(0.0, 1.0).toFloat()
    return u * u * (3f - 2f * u)
}

internal fun agentStatusPillMode(time: Double, index: Int): Float {
    val step = floor(time / STEP).toInt()
    val fade = smooth((time - step * STEP - (STEP - FADE)) / FADE)
    return when (index) {
        step % 4 -> 1f - fade
        (step + 1) % 4 -> fade
        else -> 0f
    }
}

@Composable
fun AgentStatusPill(autoPlay: Boolean = false, modifier: Modifier = Modifier) {
    val density = LocalDensity.current.density
    val reduced = rememberReducedMotion()
    val auto by rememberUpdatedState(autoPlay)
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptics = LocalHapticFeedback.current
    val clock = remember { mutableDoubleStateOf(0.6) }
    val elapsed = remember { mutableDoubleStateOf(0.0) }
    val origin = remember { mutableDoubleStateOf(0.0) }
    val painter = remember {
        if (Build.VERSION.SDK_INT >= 33) AgentStatusPillShader() else AgentStatusPillBitmap()
    }

    // Frame state is read only by draw/layer blocks. Inactive time never enters the clock.
    LaunchedEffect(lifecycleOwner, reduced) {
        if (reduced) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var previous = 0L
            while (true) withFrameNanos { now ->
                if (previous != 0L) elapsed.doubleValue += (now - previous) / 1e9
                previous = now
                if (auto) {
                    val t = elapsed.doubleValue - origin.doubleValue
                    clock.doubleValue = ((t % LOOP) + LOOP) % LOOP
                }
            }
        }
    }

    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val textStyle = TextStyle(
        color = Color.White, fontFamily = FontFamily.SansSerif,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
    )
    BoxWithConstraints(modifier.fillMaxSize().background(BACKGROUND)) {
        val pillX = (maxWidth - 300.dp) / 2
        val pillY = maxHeight * 0.4f - 42.dp
        Row(
            Modifier.offset(x = 20.dp, y = insets.calculateTopPadding() + 8.dp)
                .width(maxWidth - 40.dp).height(34.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Atlas", style = textStyle, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            Text("Trip assistant", style = textStyle, fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.5f))
        }
        Box(
            Modifier.align(Alignment.TopEnd)
                .offset(x = (-20).dp, y = insets.calculateTopPadding() + 64.dp)
                .width(290.dp)
                .background(Color(0xFF2A2D33), RoundedCornerShape(22.dp, 22.dp, 6.dp, 22.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text("Plan me three slow days in Lisbon in May — good coffee, no rush.",
                style = textStyle.copy(lineHeightStyle = LineHeightStyle(
                    LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None,
                )), fontSize = 16.sp, lineHeight = 22.sp)
        }
        Box(
            Modifier.offset(pillX, pillY).size(300.dp, 84.dp)
                .semantics { contentDescription = "Agent status. Tap to skip ahead." }
                .clickable(
                    enabled = autoPlay && !reduced,
                    interactionSource = remember { MutableInteractionSource() }, indication = null,
                    role = Role.Button,
                ) {
                    val t = clock.doubleValue
                    val next = (floor(t / STEP) + 1) * STEP - FADE
                    if (next > t) origin.doubleValue -= next - t
                    haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawIntoCanvas { target ->
                    painter.draw(target.nativeCanvas, size.width, size.height, clock.doubleValue)
                }
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.85f), Color.White.copy(alpha = 0.15f),
                            Color.White.copy(alpha = 0.6f)), startY = 0f, endY = size.height,
                    ),
                    topLeft = Offset(density, density),
                    size = Size(size.width - density * 2, size.height - density * 2),
                    cornerRadius = CornerRadius(41 * density), style = Stroke(2 * density),
                )
            }
            LABELS.forEachIndexed { index, label ->
                Text(label, modifier = Modifier.graphicsLayer {
                    val weight = agentStatusPillMode(clock.doubleValue, index)
                    alpha = weight
                    translationY = 6 * density * (1 - weight)
                }, style = textStyle.copy(shadow = Shadow(Color.Black.copy(alpha = 0.35f), Offset.Zero, 6 * density)),
                    fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Column(
            Modifier.offset(x = 40.dp, y = pillY + 118.dp).width(maxWidth - 80.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ROWS.forEachIndexed { index, row ->
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.fillMaxSize()) {
                            val t = clock.doubleValue
                            val active = if (t >= index * STEP && t < (index + 1) * STEP) 1f else 0.4f
                            rotate((t / LOOP * 360 * 8).toFloat()) {
                                drawCircle(Color.White.copy(alpha = 0.2f * active),
                                    radius = 8 * density, style = Stroke(2 * density))
                                drawArc(Color.White.copy(alpha = active), -135f, 90f, false,
                                    topLeft = Offset(3 * density, 3 * density),
                                    size = Size(16 * density, 16 * density), style = Stroke(2 * density))
                            }
                        }
                        Box(Modifier.fillMaxSize().graphicsLayer {
                            val t = clock.doubleValue
                            alpha = if (t > (index + 1) * STEP - FADE / 2)
                                1f - smooth((t - (LOOP - FADE)) / FADE) else 0f
                        }.background(Color(0xFF3CCB7F), CircleShape), contentAlignment = Alignment.Center) {
                            Text("✓", style = textStyle, fontSize = 13.sp,
                                fontWeight = FontWeight.Black, color = BACKGROUND)
                        }
                    }
                    Text(row, modifier = Modifier.graphicsLayer {
                        alpha = if (clock.doubleValue >= index * STEP) 1f else 0.4f
                    }, style = textStyle, fontSize = 16.sp)
                }
            }
        }
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = insets.calculateBottomPadding() + 12.dp)) {
            Row(Modifier.fillMaxWidth().height(54.dp)
                .background(Color(0xFF22252A), CircleShape).padding(start = 20.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("Ask a follow-up…", Modifier.weight(1f), style = textStyle,
                    color = Color.White.copy(alpha = 0.45f), fontSize = 16.sp)
                Box(Modifier.size(42.dp).background(Color.White, CircleShape), contentAlignment = Alignment.Center) {
                    // Android's fallback ↑ ignores the requested weight. Match the reference's
                    // 13 × 15 dp ink bounds rather than using that narrow fallback glyph.
                    Canvas(Modifier.fillMaxSize().semantics { contentDescription = "↑" }) {
                        val arrow = Path().apply {
                            moveTo(21 * density, 27.25f * density)
                            lineTo(21 * density, 14.75f * density)
                            moveTo(15.75f * density, 20 * density)
                            lineTo(21 * density, 14.75f * density)
                            lineTo(26.25f * density, 20 * density)
                        }
                        drawPath(arrow, BACKGROUND, style = Stroke(
                            2.5f * density, cap = StrokeCap.Round, join = StrokeJoin.Round,
                        ))
                    }
                }
            }
        }
    }
}

internal interface AgentStatusPillPainter {
    fun draw(canvas: android.graphics.Canvas, width: Float, height: Float, time: Double)
}

@RequiresApi(33)
private class AgentStatusPillShader : AgentStatusPillPainter {
    private val shader = RuntimeShader(AGENT_STATUS_PILL_AGSL)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = this@AgentStatusPillShader.shader }
    override fun draw(canvas: android.graphics.Canvas, width: Float, height: Float, time: Double) {
        shader.setFloatUniform("uBox", 0f, 0f, width, height)
        shader.setFloatUniform("uMode", agentStatusPillMode(time, 0), agentStatusPillMode(time, 1),
            agentStatusPillMode(time, 2), agentStatusPillMode(time, 3))
        shader.setFloatUniform("uPhase", (time / LOOP * 2 * PI).toFloat())
        canvas.drawRoundRect(0f, 0f, width, height, height / 2, height / 2, paint)
    }
}
