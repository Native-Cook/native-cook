package com.ngocdevv.nativecook.blocks.cards.thermalrevealcard

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.RuntimeShader
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.tanh

/*
 Thermal Reveal Card — a travel club's membership pass printed in thermochromic ink. Port of
 ThermalRevealCard.tsx. One AGSL shader draws the card (dark ink with a guilloché print, a gloss
 band, a lit top edge); warmth turns it violet → magenta → orange → yellow and then clear over a
 map drawn once in code into a bitmap (ThermalRevealCardArt.kt).

 autoPlay (LOOP = 9 s): 0–1.5 rest (gloss drifting, a ±1.6° sway), 1.5–5.5 a thumb sweeps an
 S-curve down the card through the three coves, 4.6–5.5 the whole card warms, 5.5–6.4 peak,
 6.4–8.2 it cools from the edges in, 8.2–9 rest.

 Touch: drag on the card to warm it (a resting thumb keeps warming); a light tick when a cove is
 uncovered. Drag beside the card to tilt it (up to 8°, soft limit); it springs back on release.
 2 s after the last touch the warmth fades (0.8 s) and the loop restarts at 0. Reduced motion:
 the loop stops on the revealed map.
 */

internal val ThermalBackground = Color(0xFF0E1219)
private val Ink = Color(0xFFF2ECE2)
private val Mute = Ink.copy(alpha = 0.6f)
private val Faint = Ink.copy(alpha = 0.38f)
private val Amber = Color(0xFFF2B35A)
private const val STAGE_PAD = 28f
private const val CARD_RADIUS = 22f
private const val PERSPECTIVE = 900f
private val Stats = listOf("6" to "coves", "14.8 km" to "route", "06:12" to "low tide")

private class ThermalHolder(auto: Boolean, reduced: Boolean) {
    val s = ThermalState(auto, reduced)
    val tick = mutableIntStateOf(0)
    val wake = mutableIntStateOf(0)
}

@Composable
fun ThermalRevealCard(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val reduced = rememberReducedMotion()
    val auto = autoPlay && !reduced
    val view = LocalView.current
    val screen = LocalDensity.current
    val insets = WindowInsets.safeDrawing
    val safeTop = insets.getTop(screen) / screen.density
    val safeBottom = insets.getBottom(screen) / screen.density
    val holder = remember(auto, reduced) { ThermalHolder(auto, reduced) }
    val shader = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) runCatching { RuntimeShader(THERMAL_AGSL) }.getOrNull() else null
    }
    val brush = remember(shader) { shader?.let { ShaderBrush(it) } }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(ThermalBackground)
            .background(Brush.verticalGradient(0f to Color(0xFF18202E), 0.5f to Color(0xFF0F141D), 1f to Color(0xFF0A0D13))),
    ) {
        val width = maxWidth.value
        val height = maxHeight.value
        val cardW = min(width - 48, 345f)
        val cardTop = safeTop + 168
        val cardH = max(0f, min(cardW * 1.3f, height - cardTop - safeBottom - 196).roundToInt().toFloat())
        val cardX = (width - cardW) / 2
        val mapScale = min(screen.density, 3f)

        // The map, drawn once per card size off the main thread.
        val map by produceState<Bitmap?>(null, cardW, cardH, mapScale) {
            value = if (cardW > 0 && cardH > 0) withContext(Dispatchers.Default) { thermalMapBitmap(cardW, cardH, mapScale) } else null
        }
        val mapImage: ImageBitmap? = remember(map) { map?.asImageBitmap() }
        remember(shader, map) {
            val bmp = map
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && shader != null && bmp != null) {
                val bs = BitmapShader(bmp, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP)
                bs.setLocalMatrix(Matrix().apply { setScale(1f / mapScale, 1f / mapScale) })
                shader.setInputShader("map", bs)
            }
            Unit
        }

        LaunchedEffect(holder, cardW, cardH, holder.wake.intValue) {
            if (cardH <= 0) return@LaunchedEffect
            val s = holder.s
            val w = cardW.toDouble()
            val h = cardH.toDouble()
            var last = -1L
            while (true) {
                val idle = withFrameNanos { now ->
                    val dt = if (last < 0) 1.0 / 60 else min((now - last) / 1e9, 1.0 / 20)
                    last = now
                    s.now += dt
                    when (s.mode) {
                        THERMAL_AUTO -> if (auto) {
                            s.t = (s.t + dt) % LOOP
                            s.scriptFrame(s.t, w, h)
                        }
                        THERMAL_LIVE -> {
                            s.liveFrame(dt)
                            for (i in 0 until 3) {
                                val heat = s.heatAt(ThermalCoves[i][0] * w, ThermalCoves[i][1] * h, w, h)
                                if (!s.found[i] && heat > 0.85 && s.warming) {
                                    s.found[i] = true
                                    s.buzz = true
                                } else if (s.found[i] && heat < 0.4) {
                                    s.found[i] = false
                                }
                            }
                            val idle = !s.warming && !s.tilting && s.now - s.lastTouch > THERMAL_IDLE_BEFORE_BACK
                            if (auto && idle) s.beginBack()
                        }
                        else -> if (s.backFrame(dt)) {
                            s.mode = THERMAL_AUTO
                            s.t = 0.0
                        }
                    }
                    if (s.buzz) {
                        s.buzz = false
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    }
                    holder.tick.intValue += 1
                    !s.moving()
                }
                if (idle) break
            }
        }

        ThermalNav(safeTop)

        Column(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = (safeTop + 62).dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            BasicText("COVE SOCIETY · SEASON 4", style = TextStyle(color = Amber, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp))
            BasicText("Hidden Coves of Aruna", style = TextStyle(color = Ink, fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp))
            BasicText("Warm the ink with your thumb to read the map", style = TextStyle(color = Mute, fontSize = 15.sp, fontWeight = FontWeight.Medium))
        }

        if (cardH > 0) {
            // The stage: the card plus STAGE_PAD above and below, the whole width, for tilting beside it.
            Box(
                Modifier
                    .offset(y = (cardTop - STAGE_PAD).dp)
                    .fillMaxWidth()
                    .height((cardH + STAGE_PAD * 2).dp)
                    .semantics {
                        contentDescription = "Membership card in thermochromic ink. Drag on it to warm the ink and reveal the map; drag beside it to tilt it."
                    }
                    .pointerInput(holder, cardX, cardW, cardH) {
                        fun soft(x: Double) = THERMAL_TILT_MAX * tanh(x / THERMAL_TILT_MAX)
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val d = screen.density
                            val s = holder.s
                            s.lastTouch = s.now
                            if (s.mode != THERMAL_LIVE) {
                                s.mode = THERMAL_LIVE
                                s.fade = 1.0
                                s.vrx = 0.0
                                s.vry = 0.0
                            }
                            holder.wake.intValue += 1
                            val x0 = down.position.x / d - cardX
                            val y0 = down.position.y / d - STAGE_PAD
                            if (x0 in 0f..cardW && y0 in 0f..cardH) {
                                s.warming = true
                                s.fx = x0.toDouble(); s.fy = y0.toDouble()
                                s.emit(s.fx, s.fy)
                            } else {
                                s.tilting = true
                                s.tiltX = s.rx
                                s.tiltY = s.ry
                            }
                            val tracker = VelocityTracker()
                            tracker.addPosition(down.uptimeMillis, down.position)
                            var released = false
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                s.lastTouch = s.now
                                tracker.addPosition(change.uptimeMillis, change.position)
                                if (!change.pressed) {
                                    released = true
                                    break
                                }
                                if (change.positionChange() == Offset.Zero) continue
                                change.consume()
                                if (s.warming) {
                                    val x = (change.position.x / d - cardX).coerceIn(0f, cardW).toDouble()
                                    val y = (change.position.y / d - STAGE_PAD).coerceIn(0f, cardH).toDouble()
                                    s.fx = x
                                    s.fy = y
                                    if (hypot(x - s.ex, y - s.ey) > 12) s.emit(x, y)
                                } else if (s.tilting) {
                                    val tx = (change.position.x - down.position.x) / d
                                    val ty = (change.position.y - down.position.y) / d
                                    s.tiltY = soft(tx * THERMAL_TILT_PER_PT)
                                    s.tiltX = soft(-ty * THERMAL_TILT_PER_PT)
                                }
                            }
                            if (released && s.tilting) {
                                val v = tracker.calculateVelocity()
                                s.vry = v.x / d * THERMAL_TILT_PER_PT * 0.5
                                s.vrx = -v.y / d * THERMAL_TILT_PER_PT * 0.5
                            }
                            s.warming = false
                            s.tilting = false
                            s.lastTouch = s.now
                        }
                    },
            ) {
                // Two shadows under the card (ambient, then contact); the card only tilts a few
                // degrees, so they stay put.
                Box(
                    Modifier
                        .offset(x = cardX.dp, y = (STAGE_PAD + 8).dp)
                        .size(cardW.dp, (cardH - 8).dp)
                        .shadow(28.dp, RoundedCornerShape(CARD_RADIUS.dp), ambientColor = Color.Black.copy(alpha = 0.42f), spotColor = Color.Black.copy(alpha = 0.42f)),
                )
                Box(
                    Modifier
                        .offset(x = cardX.dp, y = STAGE_PAD.dp)
                        .size(cardW.dp, cardH.dp)
                        .shadow(3.dp, RoundedCornerShape(CARD_RADIUS.dp), ambientColor = Color.Black.copy(alpha = 0.35f), spotColor = Color.Black.copy(alpha = 0.35f)),
                )
                Box(
                    Modifier
                        .offset(x = cardX.dp, y = STAGE_PAD.dp)
                        .size(cardW.dp, cardH.dp)
                        .graphicsLayer {
                            holder.tick.intValue
                            rotationX = holder.s.rx.toFloat()
                            rotationY = holder.s.ry.toFloat()
                            cameraDistance = PERSPECTIVE * density / 72f
                        },
                ) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && shader != null && brush != null && map != null) {
                        Canvas(Modifier.fillMaxSize()) {
                            holder.tick.intValue
                            val s = holder.s
                            val heat = FloatArray(THERMAL_HEAT_SLOTS * 4)
                            for (j in 0 until THERMAL_HEAT_SLOTS) {
                                heat[j * 4] = s.hx[j].toFloat()
                                heat[j * 4 + 1] = s.hy[j].toFloat()
                                heat[j * 4 + 2] = (thermalHeatStrength(s.now - s.ht[j]) * s.fade).toFloat()
                                heat[j * 4 + 3] = THERMAL_HEAT_RADIUS
                            }
                            shader.setFloatUniform("size", max(cardW, 1f), max(cardH, 1f))
                            shader.setFloatUniform("heat", heat)
                            shader.setFloatUniform("glob", s.glob.toFloat())
                            shader.setFloatUniform("gloss", s.gloss.toFloat(), 1f)
                            shader.setFloatUniform("radius", CARD_RADIUS)
                            val d = density
                            scale(d, d, pivot = Offset.Zero) {
                                drawRect(brush, Offset.Zero, Size(cardW, cardH))
                            }
                        }
                    } else {
                        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(CARD_RADIUS.dp)).background(Color(0xFF1B1F2B))) {
                            val image = mapImage
                            if (image != null) {
                                Canvas(Modifier.fillMaxSize()) {
                                    drawImage(image, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()))
                                }
                            }
                            Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
                                holder.tick.intValue
                                thermalFallback(holder.s, cardW, cardH)
                            }
                        }
                    }
                    ThermalCardText()
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp).offset(y = (cardTop + cardH + 22).dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Stats.forEach { (value, label) ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        BasicText(value, style = TextStyle(color = Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"))
                        BasicText(label, style = TextStyle(color = Faint, fontSize = 12.sp, fontWeight = FontWeight.Medium))
                    }
                }
            }
        }

        // The call to action.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 24.dp, end = 24.dp, bottom = (safeBottom + 20).dp)
                .fillMaxWidth()
                .height(52.dp)
                .shadow(12.dp, RoundedCornerShape(26.dp), ambientColor = Color(0x47E9853A), spotColor = Color(0x47E9853A))
                .background(
                    Brush.linearGradient(0f to Color(0xFFF7CF7A), 0.55f to Color(0xFFF2A94E), 1f to Color(0xFFE9853A)),
                    RoundedCornerShape(26.dp),
                )
                .drawBehind {
                    val r = 26.dp.toPx()
                    drawLine(Color.White.copy(alpha = 0.45f), Offset(r, 0.5.dp.toPx()), Offset(size.width - r, 0.5.dp.toPx()), 1.dp.toPx())
                },
            contentAlignment = Alignment.Center,
        ) {
            BasicText("Start the route", style = TextStyle(color = Color(0xFF2A1A0C), fontSize = 17.sp, fontWeight = FontWeight.Bold))
        }
    }
}

/** Back, the screen's title and More, 44 dp tall. */
@Composable
private fun ThermalNav(safeTop: Float) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = (safeTop + 6).dp).height(44.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        ThermalNavButton("Back") {
            val path = Path().apply {
                moveTo(9f, 2f); lineTo(4f, 7f); lineTo(9f, 12f)
            }
            val k = size.width / 14f
            scale(k, k, pivot = Offset.Zero) {
                drawPath(path, Ink, style = Stroke(2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
        BasicText("Members", style = TextStyle(color = Mute, fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
        ThermalNavButton("More") {
            val k = size.width / 14f
            scale(k, k, pivot = Offset.Zero) {
                for (x in listOf(1f, 7f, 13f)) drawCircle(Ink, 1.6f, Offset(x, 7f))
            }
        }
    }
}

@Composable
private fun ThermalNavButton(label: String, icon: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .background(Color.White.copy(alpha = 0.07f), CircleShape)
            .border(0.5.dp, Color.White.copy(alpha = 0.12f), CircleShape)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(14.dp), onDraw = icon)
    }
}

/** The printed type on the card: brand and number at the top, holder and the reveal note at the bottom. */
@Composable
private fun ThermalCardText() {
    val d = LocalDensity.current.density
    fun shade(blur: Float, a: Float = 0.45f) = Shadow(Color.Black.copy(alpha = a), Offset.Zero, blur * d)
    Box(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                BasicText("ARUNA", style = TextStyle(color = Ink, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 5.sp, shadow = shade(4f)))
                BasicText(
                    "COVE SOCIETY",
                    Modifier.padding(top = 2.dp),
                    style = TextStyle(color = Mute, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.6.sp, shadow = shade(3f)),
                )
            }
            BasicText("No. 0418", style = TextStyle(color = Ink, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontFeatureSettings = "tnum", shadow = shade(3f)))
        }
        Row(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Column {
                BasicText("MARA ELLIS", style = TextStyle(color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp, shadow = shade(3f, 0.5f)))
                BasicText(
                    "Explorer · since 2023",
                    Modifier.padding(top = 2.dp),
                    style = TextStyle(color = Mute, fontSize = 11.sp, fontWeight = FontWeight.Medium, shadow = shade(3f, 0.5f)),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Canvas(Modifier.size(8.dp, 16.dp)) {
                    val k = size.width / 8f
                    scale(k, k, pivot = Offset.Zero) {
                        // The thermometer: a rounded stem over a bulb.
                        val stem = Path().apply {
                            moveTo(4f, 1.2f)
                            cubicTo(5f, 1.2f, 5.8f, 2f, 5.8f, 3f)
                            lineTo(5.8f, 9.6f)
                            cubicTo(7.4f, 10.6f, 7.6f, 13.2f, 6.1f, 14.6f)
                            cubicTo(4.9f, 15.6f, 3.1f, 15.6f, 1.9f, 14.6f)
                            cubicTo(0.4f, 13.2f, 0.6f, 10.6f, 2.2f, 9.6f)
                            lineTo(2.2f, 3f)
                            cubicTo(2.2f, 2f, 3f, 1.2f, 4f, 1.2f)
                            close()
                        }
                        drawPath(stem, Ink, style = Stroke(1f))
                        drawCircle(Amber, 1.6f, Offset(4f, 12.2f))
                    }
                }
                BasicText("Reveals at 31 °C", style = TextStyle(color = Mute, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, shadow = shade(3f, 0.5f)))
            }
        }
    }
}

/**
 * Below Android 13 (no AGSL): over the map (drawn beneath), the dark ink in an offscreen layer with a warm
 * orange-magenta fringe around each heat point and a hole cut where it is warm, faded as a whole
 * by the card's warmth. Drawn in px; positions are card dp.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.thermalFallback(s: ThermalState, cardW: Float, cardH: Float) {
    val d = density
    val glob = s.glob.toFloat().coerceIn(0f, 1f)
    val inkAlpha = 1f - smooth(0.35f, 0.95f, glob)
    drawRect(Color(0xFF1B1F2B).copy(alpha = inkAlpha))
    val r = THERMAL_HEAT_RADIUS * d
    for (j in 0 until THERMAL_HEAT_SLOTS) {
        val st = (thermalHeatStrength(s.now - s.ht[j]) * s.fade).toFloat()
        if (st <= 0f) continue
        val c = Offset(s.hx[j].toFloat() * d, s.hy[j].toFloat() * d)
        val k = (st / 0.42f).coerceIn(0f, 1f)
        drawCircle(
            Brush.radialGradient(
                0f to Color(0xFFFFD04A).copy(alpha = 0.9f * k),
                0.5f to Color(0xFFF06A2A).copy(alpha = 0.8f * k),
                0.8f to Color(0xFFB8287A).copy(alpha = 0.6f * k),
                1f to Color.Transparent,
                center = c,
                radius = r * 2.2f,
            ),
            r * 2.2f,
            c,
            blendMode = BlendMode.SrcAtop,
        )
        drawCircle(
            Brush.radialGradient(0f to Color.Black.copy(alpha = k), 0.6f to Color.Black.copy(alpha = 0.7f * k), 1f to Color.Transparent, center = c, radius = r * 1.6f),
            r * 1.6f,
            c,
            blendMode = BlendMode.DstOut,
        )
    }
    // Gloss band.
    val g = s.gloss.toFloat()
    drawRect(
        Brush.linearGradient(
            0f to Color.Transparent,
            (g - 0.06f).coerceIn(0f, 1f) to Color.Transparent,
            g.coerceIn(0f, 1f) to Color.White.copy(alpha = 0.14f),
            (g + 0.06f).coerceIn(0f, 1f) to Color.Transparent,
            1f to Color.Transparent,
            start = Offset.Zero,
            end = Offset(cardW * d, cardH * d * 0.5f),
        ),
    )
}

private fun smooth(a: Float, b: Float, x: Float): Float {
    val t = ((x - a) / (b - a)).coerceIn(0f, 1f)
    return t * t * (3 - 2 * t)
}
