package com.ngocdevv.nativecook.blocks.modals.frostwipealert

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.RuntimeShader
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/*
 Frost Wipe Alert — a weather app's frost advisory seen through a frozen window. Port of
 FrostWipeAlert.tsx. A night weather screen for "Hollow Creek" dimmed under an alert card, all under
 a frosted pane drawn by one per-frame AGSL shader over the real composables: the app behind is a
 blurred snapshot pulled toward a cold white, with ice feathers growing in from the edges (baked
 once into bitmaps). Wiped glass is transparent, so the real views show sharp.

 autoPlay (LOOP = 9 s): 0–1.4 rest (fully frosted), 1.4–4.0 a hand wipes a zig-zag down the alert,
 4.0–5.4 it stays clear, 4.2–6.0 six droplets run down, 5.4–8.4 the frost grows back, 8.4–9 rest.

 Touch: drag to wipe (a point every 18 dp; clear 1.2 s, then refreezes over 6 s). The buttons work
 only where the glass is clear: Remind me (light haptic) turns into "Reminder at 17:30", Dismiss
 lowers the alert. 2 s after the last touch the pane frosts over (0.9 s), the alert comes back and
 the loop restarts at 0. Reduced motion: no loop, the alert shown wiped clear.
 */

internal val FrostBackground = Color(0xFF0A1424)
/** A frozen frame of the loop shown under reduced motion: the alert wiped clear. */
private const val REDUCED_AT = 4.6
/** Pixels per dp of the ice bitmap (crisp needles), and of the haze and the blurred app. */
private const val ICE_SCALE = 2f
private const val SOFT_SCALE = 0.5f
/** Blur of the app under the glass (dp, as a Gaussian sigma). */
private const val CONTENT_BLUR = 9f
private val EaseOut = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

private val Hours = listOf("18:00" to "−1°", "21:00" to "−3°", "00:00" to "−4°", "03:00" to "−4°", "06:00" to "−2°")
private data class FrostDay(val name: String, val low: String, val high: String, val share: Float)
private val Days = listOf(FrostDay("Sat", "−6°", "2°", 0.1f), FrostDay("Sun", "−4°", "4°", 0.35f), FrostDay("Mon", "0°", "7°", 0.8f))
private val Bright = Color(0xFFE9F1F8)
private val PanelFill = Color(0x14D6E8FA)
private val PanelEdge = Color(0x29D6E8FA)

private class FrostHolder(auto: Boolean) {
    val f = FrostState(auto)
    val tick = mutableIntStateOf(0)
    val wake = mutableIntStateOf(0)
    /** The alert card (x, y, w, h) and its two buttons (x, y, w, h each), in dp from the block's corner. */
    val card = FloatArray(4)
    val buttons = FloatArray(8)
}

/** Grey textures and the blurred app, as shaders in dp. */
private class FrostTextures(val ice: BitmapShader, val haze: BitmapShader)

private fun shaderOf(bitmap: Bitmap, scale: Float) =
    BitmapShader(bitmap, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP).apply {
        setLocalMatrix(Matrix().apply { setScale(1f / scale, 1f / scale) })
    }

private fun solid(color: Int): BitmapShader = shaderOf(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }, 1f / 4096f)

@Composable
fun FrostWipeAlert(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val reduced = rememberReducedMotion()
    val auto = autoPlay && !reduced
    val view = LocalView.current
    val screen = LocalDensity.current
    val graphics = LocalGraphicsContext.current
    val insets = WindowInsets.safeDrawing
    val safeTop = insets.getTop(screen) / screen.density
    val safeBottom = insets.getBottom(screen) / screen.density
    val holder = remember(autoPlay, reduced) { FrostHolder(autoPlay) }
    val agsl = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    val pane = remember { if (agsl) runCatching { RuntimeShader(FROST_AGSL) }.getOrNull() else null }
    val paneBrush = remember(pane) { pane?.let { ShaderBrush(it) } }
    var reminded by remember { mutableStateOf(false) }
    var dismissed by remember { mutableStateOf(false) }
    var textures by remember { mutableStateOf<FrostTextures?>(null) }
    var blurred by remember { mutableStateOf<BitmapShader?>(null) }
    val shown = remember { Animatable(1f) }
    val contentLayer = rememberGraphicsLayer()
    val root = remember { arrayOfNulls<LayoutCoordinates>(1) }

    LaunchedEffect(dismissed) {
        shown.animateTo(if (dismissed) 0f else 1f, tween(if (dismissed) 260 else 380, easing = EaseOut))
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(FrostBackground)
            .onGloballyPositioned { root[0] = it },
    ) {
        val width = maxWidth.value
        val height = maxHeight.value
        val cardTop = max(safeTop + 150, height * 0.5f - 150)

        // The ice and the haze: baked once per size on the GPU, so frame one is already frosted.
        LaunchedEffect(width, height) {
            if (!agsl || width <= 0 || height <= 0) return@LaunchedEffect
            textures = runCatching {
                suspend fun bake(mode: Float, scale: Float): Bitmap {
                    val tex = RuntimeShader(FROST_TEX_AGSL)
                    tex.setFloatUniform("size", width, height)
                    tex.setFloatUniform("mode", mode)
                    val layer = graphics.createGraphicsLayer()
                    try {
                        val px = IntSize(ceil(width * scale).toInt(), ceil(height * scale).toInt())
                        layer.record(screen, LayoutDirection.Ltr, px) {
                            scale(scale, scale, pivot = Offset.Zero) { drawRect(ShaderBrush(tex), Offset.Zero, Size(width, height)) }
                        }
                        return layer.toImageBitmap().asAndroidBitmap()
                    } finally {
                        graphics.releaseGraphicsLayer(layer)
                    }
                }
                FrostTextures(shaderOf(bake(0f, ICE_SCALE), ICE_SCALE), shaderOf(bake(1f, SOFT_SCALE), SOFT_SCALE))
            }.getOrNull()
        }

        // A fresh blurred snapshot of the app under the glass whenever what it shows changes.
        LaunchedEffect(width, height, reminded, dismissed) {
            if (!agsl || width <= 0 || height <= 0) return@LaunchedEffect
            delay(420)
            blurred = runCatching {
                val snapshot = contentLayer.toImageBitmap()
                val inner = graphics.createGraphicsLayer()
                val outer = graphics.createGraphicsLayer()
                try {
                    val px = IntSize(ceil(width * SOFT_SCALE).toInt(), ceil(height * SOFT_SCALE).toInt())
                    val radius = max(0.01f, (CONTENT_BLUR * SOFT_SCALE - 0.5f) / 0.57735f)
                    inner.renderEffect = BlurEffect(radius, radius, TileMode.Clamp)
                    inner.record(screen, LayoutDirection.Ltr, px) {
                        drawImage(snapshot, dstSize = px)
                    }
                    outer.record(screen, LayoutDirection.Ltr, px) { drawLayer(inner) }
                    shaderOf(outer.toImageBitmap().asAndroidBitmap(), SOFT_SCALE)
                } finally {
                    graphics.releaseGraphicsLayer(inner)
                    graphics.releaseGraphicsLayer(outer)
                }
            }.getOrNull()
        }

        LaunchedEffect(holder, holder.wake.intValue) {
            val f = holder.f
            var last = -1L
            while (true) {
                val idle = withFrameNanos { now ->
                    val dt = if (last < 0) 1.0 / 60 else min((now - last) / 1e9, 1.0 / 20)
                    last = now
                    if (holder.card[2] <= 0) return@withFrameNanos false
                    f.now += dt
                    when (f.mode) {
                        FROST_AUTO -> {
                            if (auto) f.t = (f.t + dt) % LOOP
                            f.scriptFrame(if (reduced) REDUCED_AT else f.t, holder.card)
                            if (reduced) f.db.fill(-100.0)
                        }
                        FROST_LIVE -> if (auto && !f.wiping && f.now - f.lastTouch > FROST_IDLE_BEFORE_BACK) {
                            f.beginBack()
                            reminded = false
                            dismissed = false
                        }
                        else -> if (f.backFrame()) {
                            f.mode = FROST_AUTO
                            f.t = 0.0
                        }
                    }
                    holder.tick.intValue += 1
                    // Idle: the held reduced-motion frame, or a finger-driven pane fully refrozen.
                    (f.mode == FROST_AUTO && !auto) || !f.moving()
                }
                if (idle) break
            }
        }

        // Without the loop to bring it back, a dismissed alert returns on its own after a moment.
        LaunchedEffect(dismissed, auto) {
            if (dismissed && !auto) {
                delay(3000)
                dismissed = false
            }
        }

        // The app under the glass, recorded into a layer so it can be snapshotted.
        Box(
            Modifier
                .fillMaxSize()
                .drawWithContent {
                    contentLayer.record { this@drawWithContent.drawContent() }
                    drawLayer(contentLayer)
                },
        ) {
            FrostWeather(safeTop, safeBottom)
            Box(Modifier.fillMaxSize().background(Color(0x80040912)))
            FrostAlertCard(
                cardTop = cardTop,
                reminded = reminded,
                shown = { shown.value },
                onCard = { c ->
                    val r = root[0]
                    if (r != null && r.isAttached && c.isAttached) {
                        val o = r.localPositionOf(c, Offset.Zero)
                        val d = screen.density
                        holder.card[0] = o.x / d; holder.card[1] = o.y / d
                        holder.card[2] = c.size.width / d; holder.card[3] = c.size.height / d
                    }
                },
                onButton = { i, c ->
                    val r = root[0]
                    if (r != null && r.isAttached && c.isAttached) {
                        val o = r.localPositionOf(c, Offset.Zero)
                        val d = screen.density
                        holder.buttons[i * 4] = o.x / d; holder.buttons[i * 4 + 1] = o.y / d
                        holder.buttons[i * 4 + 2] = c.size.width / d; holder.buttons[i * 4 + 3] = c.size.height / d
                    }
                },
            )
        }

        // The pane: one canvas over everything, taking every touch.
        Canvas(
            Modifier
                .fillMaxSize()
                .semantics { contentDescription = "A frosted window over a frost advisory. Drag to wipe the glass; the buttons work where it is clear." }
                .pointerInput(holder) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val d = screen.density
                        val f = holder.f
                        f.lastTouch = f.now
                        if (f.mode != FROST_LIVE) f.mode = FROST_LIVE
                        f.wiping = true
                        f.pen = false
                        f.lay((down.position.x / d).toDouble(), (down.position.y / d).toDouble())
                        holder.wake.intValue += 1
                        var last = down.position
                        var released = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            f.lastTouch = f.now
                            last = change.position
                            if (!change.pressed) {
                                released = true
                                break
                            }
                            change.consume()
                            val x = (change.position.x / d).toDouble()
                            val y = (change.position.y / d).toDouble()
                            val n = f.count - 1
                            if (n < 0 || hypot(x - f.px[n], y - f.py[n]) >= FROST_STEP) f.lay(x, y)
                        }
                        // A short tap that barely moved presses a button, if the glass over it is clear.
                        if (released && hypot((last.x - down.position.x) / d, (last.y - down.position.y) / d) <= 10 && shown.value >= 0.5f) {
                            val x = last.x / d
                            val y = last.y / d
                            val b = holder.buttons
                            for (i in 0 until 2) {
                                val inside = x >= b[i * 4] && x <= b[i * 4] + b[i * 4 + 2] && y >= b[i * 4 + 1] && y <= b[i * 4 + 1] + b[i * 4 + 3]
                                if (inside && f.clearAt(x.toDouble(), y.toDouble()) > 0.5) {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    if (i == 0) reminded = !reminded else dismissed = true
                                }
                            }
                        }
                        f.wiping = false
                        f.lastTouch = f.now
                    }
                },
        ) {
            holder.tick.intValue
            val f = holder.f
            val d = density
            val floor = if (f.mode == FROST_BACK) f.backK * 1.25 else -1.0
            val tex = textures
            if (agsl && pane != null && paneBrush != null) {
                val wipe = FloatArray(FROST_TRAIL * 4)
                for (i in 0 until FROST_TRAIL) {
                    if (i < f.count) {
                        wipe[i * 4] = f.px[i].toFloat(); wipe[i * 4 + 1] = f.py[i].toFloat()
                        wipe[i * 4 + 2] = f.pr[i].toFloat(); wipe[i * 4 + 3] = f.regrow(i, floor).toFloat()
                    } else {
                        wipe[i * 4 + 3] = 2f
                    }
                }
                val drops = FloatArray(FROST_DROPS * 4)
                for (i in 0 until FROST_DROPS) f.dropAt(i, if (f.mode == FROST_BACK) 1 - f.backK else 1.0, drops, i * 4)
                val content = blurred
                pane.setInputShader("iceTex", tex?.ice ?: FrostBlack)
                pane.setInputShader("hazeTex", tex?.haze ?: FrostGrey)
                pane.setInputShader("content", content ?: FrostPlaceholder)
                pane.setFloatUniform("size", max(width, 1f), max(height, 1f))
                pane.setFloatUniform("ready", if (content != null) 1f else 0f)
                pane.setFloatUniform("card", holder.card[0], holder.card[1], holder.card[2], holder.card[3])
                pane.setFloatUniform("wipe", wipe)
                pane.setFloatUniform("drops", drops)
                scale(d, d, pivot = Offset.Zero) { drawRect(paneBrush, Offset.Zero, Size(width, height)) }
            } else {
                drawContext.canvas.saveLayer(androidx.compose.ui.geometry.Rect(Offset.Zero, size), androidx.compose.ui.graphics.Paint())
                scale(d, d, pivot = Offset.Zero) { frostFallback(f, floor, width, height) }
                drawContext.canvas.restore()
            }
        }
    }
}

private val FrostBlack by lazy { solid(android.graphics.Color.BLACK) }
private val FrostGrey by lazy { solid(android.graphics.Color.rgb(140, 140, 140)) }
private val FrostPlaceholder by lazy { solid(android.graphics.Color.rgb(26, 42, 58)) }

/** The weather screen under the alert: place and temperature, an hourly strip, the next days. */
@Composable
private fun FrostWeather(safeTop: Float, safeBottom: Float) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(0f to Color(0xFF0A1424), 0.55f to Color(0xFF16304B), 1f to Color(0xFF2C4B68))),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = (safeTop + 28).dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BasicText("Hollow Creek", style = TextStyle(color = Bright, fontSize = 20.sp, fontWeight = FontWeight.SemiBold))
            BasicText(
                "−4°",
                Modifier.offset(y = (-2).dp),
                style = TextStyle(color = Color(0xFFF2F7FB), fontSize = 92.sp, fontWeight = FontWeight.ExtraLight, fontFeatureSettings = "tnum"),
            )
            BasicText("Clear · frost likely after 22:00", style = TextStyle(color = Bright.copy(alpha = 0.75f), fontSize = 15.sp, fontWeight = FontWeight.Medium))
            BasicText(
                "H −1°   L −6°",
                Modifier.padding(top = 4.dp),
                style = TextStyle(color = Bright.copy(alpha = 0.55f), fontSize = 14.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum"),
            )
        }
        Row(
            Modifier
                .padding(start = 20.dp, end = 20.dp, top = (safeTop + 236).dp)
                .fillMaxWidth()
                .height(96.dp)
                .background(PanelFill, RoundedCornerShape(22.dp))
                .border(0.5.dp, PanelEdge, RoundedCornerShape(22.dp)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            val moon = remember { PathParser().parsePathString("M12.5 3.5 a6 6 0 1 0 2 9.5 a5 5 0 0 1 -2 -9.5 Z").toPath() }
            Hours.forEach { (time, temp) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BasicText(time, style = TextStyle(color = Bright.copy(alpha = 0.6f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"))
                    Canvas(Modifier.size(18.dp)) {
                        val k = size.width / 18f
                        scale(k, k, pivot = Offset.Zero) { drawPath(moon, Color(0xFFC9D8EA)) }
                    }
                    BasicText(temp, style = TextStyle(color = Bright, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"))
                }
            }
        }
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 20.dp, end = 20.dp, bottom = (safeBottom + 24).dp)
                .fillMaxWidth()
                .background(PanelFill, RoundedCornerShape(22.dp))
                .border(0.5.dp, PanelEdge, RoundedCornerShape(22.dp))
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BasicText("NEXT 3 DAYS", style = TextStyle(color = Bright.copy(alpha = 0.55f), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp))
            Days.forEach { day ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BasicText(day.name, Modifier.width(40.dp), style = TextStyle(color = Bright, fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
                    BasicText(
                        day.low,
                        Modifier.width(36.dp),
                        style = TextStyle(color = Bright.copy(alpha = 0.6f), fontSize = 15.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.End, fontFeatureSettings = "tnum"),
                    )
                    Box(
                        Modifier
                            .weight(1f)
                            .height(4.dp)
                            .background(Color(0x1FD6E8FA), RoundedCornerShape(2.dp))
                            .drawBehind {
                                val l = size.width * day.share * 0.4f
                                val r = size.width * (1 - (1 - day.share) * 0.3f)
                                drawRoundRect(
                                    Brush.horizontalGradient(0f to Color(0xFF7FB4E8), 0.6f to Color(0xFFB9D6F0), 1f to Color(0xFFF2D39A), startX = l, endX = r),
                                    topLeft = Offset(l, 0f),
                                    size = Size(r - l, size.height),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()),
                                )
                            },
                    )
                    BasicText(day.high, Modifier.width(32.dp), style = TextStyle(color = Bright, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"))
                }
            }
        }
    }
}

/** The alert card; it reports its layout box (before its show/hide transform) and its buttons'. */
@Composable
private fun FrostAlertCard(
    cardTop: Float,
    reminded: Boolean,
    shown: () -> Float,
    onCard: (LayoutCoordinates) -> Unit,
    onButton: (Int, LayoutCoordinates) -> Unit,
) {
    val flake = remember {
        PathParser().parsePathString("M12 2.5 V21.5 M12 6 L9.5 3.8 M12 6 L14.5 3.8 M12 18 L9.5 20.2 M12 18 L14.5 20.2").toPath()
    }
    val tick = remember { PathParser().parsePathString("M2.5 7.5 L5.6 10.4 L11.5 3.8").toPath() }
    val blue = Color(0xFF2A5D8F)
    Column(
        Modifier
            .padding(start = 24.dp, end = 24.dp)
            .offset(y = cardTop.dp)
            .fillMaxWidth()
            .onGloballyPositioned(onCard)
            .graphicsLayer {
                val v = shown()
                alpha = v
                translationY = (1 - v) * 36 * density
                scaleX = 0.96f + 0.04f * v
                scaleY = scaleX
            }
            .shadow(24.dp, RoundedCornerShape(28.dp), ambientColor = Color(0x73020812), spotColor = Color(0x73020812))
            .background(Color(0xFFEEF3F7), RoundedCornerShape(28.dp))
            .drawBehind {
                val r = 28.dp.toPx()
                drawLine(Color.White.copy(alpha = 0.9f), Offset(r, 0.5.dp.toPx()), Offset(size.width - r, 0.5.dp.toPx()), 1.dp.toPx())
            }
            .padding(24.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Box(Modifier.size(44.dp).background(Color(0xFFD8E6F2), CircleShape), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(24.dp)) {
                    val k = size.width / 24f
                    scale(k, k, pivot = Offset.Zero) {
                        for (a in listOf(0f, 60f, 120f)) {
                            rotate(a, pivot = Offset(12f, 12f)) {
                                drawPath(flake, blue, style = Stroke(1.7f, cap = StrokeCap.Round))
                            }
                        }
                        drawCircle(blue, 1.6f, Offset(12f, 12f))
                    }
                }
            }
            Box(Modifier.height(24.dp).background(Color(0xFFDBE7F2), RoundedCornerShape(12.dp)).padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                BasicText("ADVISORY", style = TextStyle(color = blue, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp))
            }
        }
        BasicText("Frost advisory", style = TextStyle(color = Color(0xFF0F1C2B), fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp))
        BasicText(
            "−4 °C tonight in Hollow Creek. Cover your plants by 18:00.",
            Modifier.padding(top = 8.dp),
            style = TextStyle(color = Color(0xFF3A4A5B), fontSize = 16.sp, lineHeight = 22.sp),
        )
        BasicText(
            "Issued 15:20 · until Sun 09:00",
            Modifier.padding(top = 12.dp),
            style = TextStyle(color = Color(0xFF6A7989), fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum"),
        )
        Row(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                Modifier
                    .weight(1f)
                    .height(48.dp)
                    .onGloballyPositioned { onButton(0, it) }
                    .shadow(1.dp, RoundedCornerShape(24.dp), ambientColor = Color(0x4D0A1E3C), spotColor = Color(0x4D0A1E3C))
                    .background(Color(0xFF2F6FB3), RoundedCornerShape(24.dp))
                    .drawBehind {
                        val r = 24.dp.toPx()
                        drawLine(Color.White.copy(alpha = 0.25f), Offset(r, 0.5.dp.toPx()), Offset(size.width - r, 0.5.dp.toPx()), 1.dp.toPx())
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            ) {
                if (reminded) {
                    Canvas(Modifier.size(14.dp)) {
                        val k = size.width / 14f
                        scale(k, k, pivot = Offset.Zero) {
                            drawPath(tick, Color(0xFFF4F8FC), style = Stroke(2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }
                    }
                }
                BasicText(if (reminded) "Reminder at 17:30" else "Remind me", style = TextStyle(color = Color(0xFFF4F8FC), fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
            }
            Box(
                Modifier
                    .weight(1f)
                    .height(48.dp)
                    .onGloballyPositioned { onButton(1, it) }
                    .background(Color(0xFFDDE6EE), RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center,
            ) {
                BasicText("Dismiss", style = TextStyle(color = Color(0xFF21384F), fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
            }
        }
    }
}

/**
 * Below Android 13 (no AGSL): a cold translucent pane in an offscreen layer, whiter toward the
 * edges, with each wipe capsule cut out (fading as it refreezes) and the droplets as small bright
 * lenses. No blur of the app, no ice feathers. Drawn in dp.
 */
private fun DrawScope.frostFallback(f: FrostState, floor: Double, width: Float, height: Float) {
    drawRect(Color(0xFFDBE8F5).copy(alpha = 0.82f), size = Size(width, height))
    drawRect(
        Brush.radialGradient(
            0f to Color.Transparent,
            0.7f to Color.Transparent,
            1f to Color.White.copy(alpha = 0.5f),
            center = Offset(width / 2, height / 2),
            radius = hypot(width, height) / 2,
        ),
        size = Size(width, height),
    )
    for (i in 0 until f.count) {
        val r = abs(f.pr[i]).toFloat()
        if (r < 0.5f) continue
        val q = f.regrow(i, floor).toFloat()
        val clear = (1f - (q + 0.1f) / 1.0f).coerceIn(0f, 1f)
        if (clear <= 0f) continue
        val c = Offset(f.px[i].toFloat(), f.py[i].toFloat())
        val cut = Color.Black.copy(alpha = clear)
        drawCircle(cut, r * 0.8f, c, blendMode = BlendMode.DstOut)
        if (i > 0 && f.pr[i] > 0) {
            drawLine(cut, Offset(f.px[i - 1].toFloat(), f.py[i - 1].toFloat()), c, r * 1.6f, cap = StrokeCap.Round, blendMode = BlendMode.DstOut)
        }
    }
    val drop = FloatArray(4)
    for (i in 0 until FROST_DROPS) {
        f.dropAt(i, if (f.mode == FROST_BACK) 1 - f.backK else 1.0, drop, 0)
        if (drop[3] <= 0.3f) continue
        val head = Offset(drop[0], drop[1])
        drawLine(Color.Black.copy(alpha = 0.4f), Offset(head.x, head.y - drop[2]), head, drop[3] * 0.7f, cap = StrokeCap.Round, blendMode = BlendMode.DstOut)
        drawCircle(Color.Black, drop[3], head, blendMode = BlendMode.DstOut)
        drawCircle(Color.White.copy(alpha = 0.85f), drop[3] * 0.3f, Offset(head.x - drop[3] * 0.3f, head.y - drop[3] * 0.45f))
    }
}
