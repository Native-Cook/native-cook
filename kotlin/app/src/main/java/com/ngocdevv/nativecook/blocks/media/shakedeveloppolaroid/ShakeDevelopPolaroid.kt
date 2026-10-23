package com.ngocdevv.nativecook.blocks.media.shakedeveloppolaroid

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/*
 Shake Develop Polaroid — a freshly ejected instant print that develops as you shake it. A port of the
 SwiftUI block (ShakeDevelopPolaroid.swift); the numbers are the same.

 Look: a dark aubergine viewer (#1F161C, a soft warm lift #3A2632 behind the print). Header: a back
 chevron and a "•••" in 36 dp translucent circles, "Today" over "10:10 AM" in the middle. The print: a
 warm white instant film (#F6F3ED → #ECE8E0, corner 4 dp) 80 % of the width (at most 330 dp), a square photo
 inset 16 dp with a 64 dp bottom margin, centred at 45 % of the height, with a deep shadow that slides
 against the shake. Under it a phone glyph and "Shake to develop" (or a check and "Developed"), and a
 140 dp bar for how far it has developed. A bottom bar: share, the roll (a 40 dp thumbnail of the
 developed photo, white 2 dp border), trash.

 Development and the autoPlay timeline (LOOP = 8 s): ShakeDevelopPolaroidModel.kt.

 Touch: drag the print back and forth: it follows the finger (90 % across, 25 % down, tilting 12° per
 314 dp) and every dp of sideways travel develops it (1/1600 per dp); on release it springs back
 (e^(−5 s), 2.2 Hz). Shaking the phone does the same through the linear-acceleration sensor: each jolt
 above 0.35 g kicks the print sideways and develops it. A tick every tenth, a confirm haptic when
 developed, then the glint. 2 s after the last touch the print finishes developing by itself (0.8 s) and
 the loop carries on from the drop into the roll.
 Reduced motion (animations off): no loop and no shake; the print shows developed, with "Developed".
 */

private const val PHOTO_ASSET = "shakedeveloppolaroid/shakedeveloppolaroid_lake.jpg"
private val MINT = Color(0xFF8CE6CC)

private fun nowSeconds() = System.nanoTime() / 1e9

private class ShakeDevelopPolaroidLayout(val width: Double, val height: Double, val top: Double, bottomInset: Double) {
    val cardW = min(width * 0.8, 330.0)
    val cardH = 16 + (cardW - 32) + 64
    val cardX = (width - cardW) / 2
    val cardY = height * 0.45 - cardH / 2
    val photo = cardW - 32
    val hintY = cardY + cardH + 36
    val dockY = height - max(bottomInset, 20.0) - 30
}

private class ShakeDevelopPolaroidController(val autoPlay: Boolean, val reduced: Boolean) {
    var frame by mutableDoubleStateOf(nowSeconds())
    var autoStart by mutableStateOf<Double?>(null)
    var dev by mutableDoubleStateOf(if (reduced) 1.0 else 0.0)
    /** The drag's translation in dp while a finger holds the print. */
    var drag by mutableStateOf<Offset?>(null)
    var lastX = 0.0
    var release by mutableStateOf<DoubleArray?>(null)        // [date, x, y, rotation]
    var kick by mutableStateOf<DoubleArray?>(null)           // [date, amplitude]
    var developedAt by mutableStateOf<Double?>(if (reduced) -1e9 else null)
    var finishing by mutableStateOf<DoubleArray?>(null)      // [date, from]
    var holding = false
    var active by mutableStateOf(false)
    var resume: Job? = null
    var lastKick = 0.0

    val running get() = (autoStart != null && !reduced) || active

    fun scene(at: Double): ShakeDevelopPolaroidScene {
        val M = ShakeDevelopPolaroidMotion
        val start = autoStart
        if (start != null && at >= start) return M.scene((at - start) % M.LOOP)
        val s = ShakeDevelopPolaroidScene()
        s.develop = dev
        val f = finishing
        val d = developedAt
        if (f != null) {
            val u = at - f[0]
            s.develop = f[1] + (1 - f[1]) * M.inOut(u / 0.8)
            if (f[1] < 1) s.glint = if (u < 0.75) u / 0.75 else -1.0
            s.label = if (f[1] >= 1) 1.0 else M.smoothstep(0.5, 0.8, u)
        } else if (d != null) {
            val u = at - d
            s.label = M.smoothstep(0.0, 0.3, u)
            if (u >= 0 && u < 0.7) s.glint = u / 0.7
        }
        val dr = drag
        val r = release
        if (dr != null) {
            s.x = dr.x * 0.9
            s.y = dr.y * 0.25
            s.rotation = s.x / 314 * 12
        } else if (r != null && !reduced) {
            val tau = at - r[0]
            val k = exp(-5 * tau) * cos(2 * PI * 2.2 * tau)
            s.x = r[1] * k; s.y = r[2] * k; s.rotation = r[3] * k
        }
        val kk = kick
        if (kk != null && !reduced) {
            val tau = at - kk[0]
            val k = exp(-6 * tau) * sin(2 * PI * 3 * tau)
            s.x += kk[1] * k
            s.rotation += kk[1] / 314 * 40 * k
        }
        return s
    }

    /** Stops the loop and keeps what it showed, unless the print is on its way to the roll. */
    fun takeOver(now: Double): Boolean {
        resume?.cancel()
        active = true
        val start = autoStart
        if (start != null && !reduced) {
            val t = (now - start) % ShakeDevelopPolaroidMotion.LOOP
            if (t >= 5.9 && t < 7.5) return false
            val s = ShakeDevelopPolaroidMotion.scene(t)
            dev = s.develop
            developedAt = if (s.develop >= 1) now - 1 else null
            release = doubleArrayOf(now, s.x, s.y, s.rotation)
            autoStart = null
        }
        if (finishing != null) {
            dev = scene(now).develop
            if (dev >= 1) developedAt = now - 1
            finishing = null
        }
        return true
    }
}

@Composable
fun ShakeDevelopPolaroid(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val context = LocalContext.current
    val view = LocalView.current
    val reduced = remember { reducedMotion(context) }
    val photo = remember { context.assets.open(PHOTO_ASSET).use { BitmapFactory.decodeStream(it) } }
    val thumb = remember(photo) { photo.asImageBitmap() }
    val c = remember(reduced) { ShakeDevelopPolaroidController(autoPlay, reduced) }
    val painter = remember(photo) { ShakeDevelopPolaroidPainter(photo) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(c) { if (autoPlay && !reduced) c.autoStart = nowSeconds() }
    LaunchedEffect(c, c.running) {
        while (c.running) withFrameNanos { c.frame = it / 1e9 }
    }

    fun develop(amount: Double, now: Double) {
        if (c.dev >= 1) return
        val before = c.dev
        c.dev = min(1.0, c.dev + amount)
        if ((c.dev * 10).toInt() > (before * 10).toInt() && c.dev < 1) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        if (c.dev >= 1) {
            c.developedAt = now
            view.performHapticFeedback(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS)
        }
    }

    fun scheduleResume() {
        c.resume?.cancel()
        c.resume = scope.launch {
            delay(2000)
            if (reduced) {
                c.drag = null
                c.active = false
                return@launch
            }
            // The print finishes developing by itself (0.8 s), then the loop carries on from the drop into the roll.
            c.finishing = doubleArrayOf(nowSeconds(), c.dev)
            c.release = null
            c.kick = null
            delay(800)
            c.finishing = null
            if (autoPlay) {
                c.dev = 0.0
                c.developedAt = null
                c.autoStart = nowSeconds() - 5.9
            } else {
                c.dev = 1.0
                c.developedAt = -1e9
                c.active = false
            }
        }
    }

    // Shaking the phone: each jolt above 0.35 g kicks the print and develops it.
    DisposableEffect(c) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val ax = event.values[0] / 9.81
                val ay = event.values[1] / 9.81
                val az = event.values[2] / 9.81
                val g = sqrt(ax * ax + ay * ay + az * az)
                val now = nowSeconds()
                if (g <= 0.35 || now - c.lastKick <= 0.12 || c.holding) return
                if (!c.takeOver(now)) return
                c.lastKick = now
                c.kick = doubleArrayOf(now, max(-24.0, min(24.0, ax * 60)))
                develop(min(0.06, (g - 0.35) * 0.05 + 0.015), now)
                scheduleResume()
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        if (!reduced && sensor != null) manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose {
            manager?.unregisterListener(listener)
            c.resume?.cancel()
        }
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val density = LocalDensity.current.density
        val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding().value.toDouble()
        val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().value.toDouble()
        val l = remember(maxWidth, maxHeight, top, bottomInset) {
            ShakeDevelopPolaroidLayout(maxWidth.value.toDouble(), maxHeight.value.toDouble(), top, bottomInset)
        }

        // Backdrop.
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Color(0xFF1F161C))
            val lift = Color(0xFF3A2632)
            drawRect(Brush.radialGradient(listOf(lift, lift.copy(alpha = 0f)),
                center = Offset(((l.cardX + l.cardW / 2) * density).toFloat(), ((l.cardY + l.cardH / 2) * density).toFloat()),
                radius = size.width * 0.85f))
        }

        // Header.
        Row(
            Modifier.fillMaxWidth().height(44.dp).offset(y = (l.top + 6).dp).padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            CircleButton { SdpGlyph(SdpGlyphKind.BACK, Color.White.copy(alpha = 0.85f), 1.8f, Modifier.size(18.dp)) }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(1.dp)) {
                BasicText("Today", style = TextStyle(color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
                BasicText("10:10 AM", style = TextStyle(color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp))
            }
            CircleButton { SdpGlyph(SdpGlyphKind.MORE, Color.White.copy(alpha = 0.85f), 0f, Modifier.size(18.dp)) }
        }

        // Hint and bar.
        Box(Modifier.fillMaxWidth().offset(y = (l.hintY - 10).dp), contentAlignment = Alignment.TopCenter) {
            Row(
                Modifier.graphicsLayer { alpha = (1 - c.scene(c.frame).label).toFloat() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SdpGlyph(
                    SdpGlyphKind.PHONE, Color.White.copy(alpha = 0.75f), 1.6f,
                    Modifier.size(20.dp).graphicsLayer {
                        val s = c.scene(c.frame)
                        rotationZ = if (reduced || s.label >= 1) 0f else (s.x / 17 * 14).toFloat()
                    },
                )
                BasicText("Shake to develop", style = TextStyle(color = Color.White.copy(alpha = 0.75f), fontSize = 14.sp, fontWeight = FontWeight.Medium))
            }
            Row(
                Modifier.graphicsLayer { alpha = c.scene(c.frame).label.toFloat() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SdpGlyph(SdpGlyphKind.CHECK, MINT, 1.8f, Modifier.size(20.dp))
                BasicText("Developed", style = TextStyle(color = Color.White.copy(alpha = 0.9f), fontSize = 14.sp, fontWeight = FontWeight.Medium))
            }
            Canvas(
                Modifier
                    .offset(y = 28.dp)
                    .size(140.dp, 3.dp)
                    .graphicsLayer { alpha = c.scene(c.frame).bar.toFloat().coerceIn(0f, 1f) },
            ) {
                val r = androidx.compose.ui.geometry.CornerRadius(size.height / 2)
                drawRoundRect(Color.White.copy(alpha = 0.12f), cornerRadius = r)
                val w = max(3.0, 140 * c.scene(c.frame).develop).toFloat() * density
                drawRoundRect(MINT, size = androidx.compose.ui.geometry.Size(w, size.height), cornerRadius = r)
            }
        }

        // Dock.
        Row(
            Modifier.fillMaxWidth().height(44.dp).offset(y = (l.dockY - 22).dp).padding(horizontal = 32.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            SdpGlyph(SdpGlyphKind.SHARE, Color.White.copy(alpha = 0.75f), 1.6f, Modifier.size(22.dp))
            Box(
                Modifier
                    .graphicsLayer {
                        val b = (1 + c.scene(c.frame).bump).toFloat()
                        scaleX = b; scaleY = b
                    }
                    .background(Color(0xFFF5F2EC), RoundedCornerShape(8.dp))
                    .padding(2.dp),
            ) {
                Image(thumb, null, Modifier.size(36.dp).clip(RoundedCornerShape(6.dp)), contentScale = ContentScale.Crop)
            }
            SdpGlyph(SdpGlyphKind.TRASH, Color.White.copy(alpha = 0.75f), 1.6f, Modifier.size(22.dp))
        }

        // The print, and the touch.
        Canvas(
            Modifier
                .fillMaxSize()
                .semantics { contentDescription = "Instant photo of a turquoise mountain lake at sunrise" }
                .pointerInput(c, l) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val p0 = down.position / density
                        val now0 = nowSeconds()
                        val s0 = c.scene(now0)
                        val cx = l.cardX + l.cardW / 2 + s0.x
                        val cy = l.cardY + l.cardH / 2 + s0.y
                        val inside = s0.toRoll == 0.0 && abs(p0.x - cx) <= l.cardW / 2 && abs(p0.y - cy) <= l.cardH / 2
                        if (!inside || !c.takeOver(now0)) return@awaitEachGesture
                        c.holding = true
                        c.lastX = 0.0
                        c.release = null
                        c.drag = Offset.Zero
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            change.consume()
                            val d = change.position / density - p0
                            if (reduced) {
                                c.drag = Offset(d.x * 0.25f, d.y * 0.25f)
                                continue
                            }
                            c.drag = d
                            val dx = abs(d.x - c.lastX)
                            c.lastX = d.x.toDouble()
                            develop(dx / 1600, nowSeconds())
                        }
                        // Released (or cancelled): spring back.
                        c.holding = false
                        val now = nowSeconds()
                        val s = c.scene(now)
                        c.drag = null
                        c.release = if (reduced) null else doubleArrayOf(now, s.x, s.y, s.rotation)
                        scheduleResume()
                    }
                },
        ) {
            val s = c.scene(c.frame)
            painter.draw(drawContext.canvas.nativeCanvas, s, l, density)
        }
    }
}

/** The print, through android.graphics: shadow, paper, photo with its development, veil, glint. */
private class ShakeDevelopPolaroidPainter(val photo: Bitmap) {
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paper = Paint(Paint.ANTI_ALIAS_FLAG)
    private val image = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val veil = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.5f
        color = android.graphics.Color.argb(38, 0, 0, 0)
    }
    private var paperFor = -1.0

    fun draw(canvas: android.graphics.Canvas, s: ShakeDevelopPolaroidScene, l: ShakeDevelopPolaroidLayout, density: Float) {
        if (s.alpha <= 0) return
        val w = l.cardW.toFloat()
        val h = l.cardH.toFloat()
        val p = l.photo.toFloat()
        if (paperFor != l.cardW) {
            paperFor = l.cardW
            paper.shader = LinearGradient(0f, 0f, 0f, h, 0xFFF6F3ED.toInt(), 0xFFECE8E0.toInt(), Shader.TileMode.CLAMP)
            shadow.color = 0xFFECE8E0.toInt()
        }
        val rollX = l.width / 2
        val rollY = l.dockY
        val midX = l.cardX + l.cardW / 2
        val midY = l.cardY + l.cardH / 2
        val cx = midX + (rollX - midX) * s.toRoll + s.x
        val cy = midY + (rollY - midY) * s.toRoll + s.y
        val rotation = s.rotation * (1 - s.toRoll)

        val save = canvas.saveLayerAlpha(null, (s.alpha * 255).toInt())
        canvas.scale(density, density)
        canvas.translate(cx.toFloat(), cy.toFloat())
        canvas.rotate(rotation.toFloat())
        canvas.scale(s.scale.toFloat(), s.scale.toFloat())
        canvas.translate(-w / 2, -h / 2)

        // The shadow's offset is on screen, so it is turned back into the print's own frame.
        val ox = -s.x * 0.35
        val oy = 16 + s.y * 0.3
        val a = -rotation * PI / 180
        val lx = (ox * cos(a) - oy * sin(a)) / s.scale
        val ly = (ox * sin(a) + oy * cos(a)) / s.scale
        shadow.setShadowLayer((24 / s.scale).toFloat(), lx.toFloat(), ly.toFloat(), android.graphics.Color.argb(140, 0, 0, 0))
        val card = RectF(0f, 0f, w, h)
        canvas.drawRoundRect(card, 4f, 4f, shadow)
        canvas.drawRoundRect(card, 4f, 4f, paper)

        val rect = RectF(16f, 16f, 16f + p, 16f + p)
        val m = ShakeDevelopPolaroidMotion.matrix(s.develop)
        val values = FloatArray(20) { i -> (if (i % 5 == 4) m[i] * 255 else m[i]).toFloat() }
        image.colorFilter = ColorMatrixColorFilter(ColorMatrix(values))
        canvas.drawBitmap(photo, null, rect, image)
        // The veil, as nine stops from the top (v = 1) to the bottom (v = 0).
        val colors = IntArray(9) { i ->
            val v = 1 - i / 8.0
            android.graphics.Color.argb((ShakeDevelopPolaroidMotion.haze(s.develop, v) * 255).toInt(), 215, 221, 218)
        }
        val stops = FloatArray(9) { it / 8f }
        veil.shader = LinearGradient(0f, rect.top, 0f, rect.bottom, colors, stops, Shader.TileMode.CLAMP)
        canvas.drawRect(rect, veil)
        if (s.glint >= 0) {
            val g = -0.2 + 1.4 * s.glint
            val ga = (0.35 * sin(PI * s.glint) * 255).toInt()
            glint.shader = LinearGradient(
                rect.left, rect.top, rect.right, rect.bottom,
                intArrayOf(android.graphics.Color.argb(0, 255, 255, 255), android.graphics.Color.argb(ga, 255, 255, 255), android.graphics.Color.argb(0, 255, 255, 255)),
                floatArrayOf(max(0.0, g - 0.15).toFloat(), min(1.0, max(0.0, g)).toFloat(), min(1.0, g + 0.15).toFloat()),
                Shader.TileMode.CLAMP,
            )
            canvas.drawRect(rect, glint)
        }
        canvas.drawRect(RectF(rect.left + 0.25f, rect.top + 0.25f, rect.right - 0.25f, rect.bottom - 0.25f), edge)
        canvas.restoreToCount(save)
    }
}

@Composable
private fun CircleButton(content: @Composable () -> Unit) {
    Box(Modifier.size(36.dp).background(Color.White.copy(alpha = 0.08f), CircleShape), contentAlignment = Alignment.Center) { content() }
}

private enum class SdpGlyphKind { BACK, MORE, PHONE, CHECK, SHARE, TRASH }

/** Line glyphs on a 24 × 24 grid, scaled to the size. */
@Composable
private fun SdpGlyph(kind: SdpGlyphKind, color: Color, width: Float, modifier: Modifier) {
    Canvas(modifier) {
        val k = size.minDimension / 24
        val path = androidx.compose.ui.graphics.Path()
        fun m(x: Float, y: Float) = path.moveTo(x * k, y * k)
        fun ln(x: Float, y: Float) = path.lineTo(x * k, y * k)
        when (kind) {
            SdpGlyphKind.BACK -> { m(14.5f, 5f); ln(7.5f, 12f); ln(14.5f, 19f) }
            SdpGlyphKind.MORE -> {
                for (x in floatArrayOf(5f, 12f, 19f)) drawCircle(color, 1.8f * k, Offset(x * k, 12f * k))
                return@Canvas
            }
            SdpGlyphKind.PHONE -> {
                path.addRoundRect(androidx.compose.ui.geometry.RoundRect(7f * k, 3f * k, 17f * k, 21f * k, 2.5f * k, 2.5f * k))
                m(10.5f, 18f); ln(13.5f, 18f)
                m(3.5f, 8f); path.quadraticTo(1.5f * k, 12f * k, 3.5f * k, 16f * k)
                m(20.5f, 8f); path.quadraticTo(22.5f * k, 12f * k, 20.5f * k, 16f * k)
            }
            SdpGlyphKind.CHECK -> { m(5f, 12.5f); ln(10f, 17.5f); ln(19f, 7f) }
            SdpGlyphKind.SHARE -> {
                m(12f, 3.5f); ln(12f, 15f)
                m(7.5f, 8f); ln(12f, 3.5f); ln(16.5f, 8f)
                m(8f, 11f); ln(5f, 11f); ln(5f, 21f); ln(19f, 21f); ln(19f, 11f); ln(16f, 11f)
            }
            SdpGlyphKind.TRASH -> {
                m(4f, 6.5f); ln(20f, 6.5f)
                m(9.5f, 6.5f); ln(9.5f, 4f); ln(14.5f, 4f); ln(14.5f, 6.5f)
                m(6f, 6.5f); ln(7f, 20f); ln(17f, 20f); ln(18f, 6.5f)
            }
        }
        drawPath(path, color, style = Stroke(width * density, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

private fun reducedMotion(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
