package com.ngocdevv.nativecook.blocks.forms.terminalreservationreview

import android.app.Activity
import android.graphics.Canvas as NativeCanvas
import android.graphics.Color as NativeColor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.content.res.AssetManager
import android.graphics.Paint
import android.graphics.Path as NativePath
import android.graphics.RectF
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private const val LOOP = 8f
private fun smooth(value: Float): Float { val p = value.coerceIn(0f, 1f); return p * p * (3 - 2 * p) }
private fun phase(t: Float, a: Float, b: Float) = smooth((t - a) / (b - a))
private fun out(t: Float, a: Float, b: Float): Float { val p = ((t - a) / (b - a)).coerceIn(0f, 1f); return 1 - (1 - p) * (1 - p) * (1 - p) }
private fun seconds() = SystemClock.elapsedRealtimeNanos() / 1_000_000_000.0

/** A local-only reservation review. No payment or network request is made. */
@Composable
fun TerminalReservationReview(autoPlay: Boolean = false, renderTime: Float? = null) {
    val reduced = rememberReducedMotion()
    val view = LocalView.current
    val assetManager = LocalContext.current.assets
    val artwork = remember(assetManager) { TerminalReservationReviewAssets(assetManager) }
    val m = remember { TerminalReservationReviewMotion() }
    val time = remember { mutableFloatStateOf(if (reduced) 6.5f else renderTime ?: 0f) }
    var serial by remember { mutableIntStateOf(0) }
    var insurance by remember { mutableStateOf(false) }
    val checkoutVisible by remember { derivedStateOf { out(time.floatValue, .70f, 1f) * (1 - phase(time.floatValue, 7.05f, 7.6f)) < .5f } }
    val confirmedVisible by remember { derivedStateOf { phase(time.floatValue, 5.25f, 5.60f) * (1 - phase(time.floatValue, 7.05f, 7.6f)) > .5f } }
    val density = LocalDensity.current.density
    DisposableEffect(view) {
        val activity = view.context as? Activity
        (activity as? androidx.activity.ComponentActivity)?.enableEdgeToEdge(statusBarStyle = SystemBarStyle.light(NativeColor.TRANSPARENT, NativeColor.TRANSPARENT), navigationBarStyle = SystemBarStyle.light(NativeColor.TRANSPARENT, NativeColor.TRANSPARENT))
        onDispose {
            (activity as? androidx.activity.ComponentActivity)?.enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(NativeColor.TRANSPARENT), navigationBarStyle = SystemBarStyle.dark(NativeColor.TRANSPARENT))
        }
    }
    fun pose(stamp: Double): Float {
        if (reduced) return if (m.touchedAt == null || m.replay) 6.5f else m.manual
        if (renderTime != null) return ((renderTime % LOOP) + LOOP) % LOOP
        val touch = m.touchedAt
        if (touch != null) {
            val idle = (stamp - touch).toFloat()
            if (m.dragging || idle < 2f) return if (m.replay) 1f + 5.5f * smooth(idle / 1.8f) else m.manual
            if (idle < 2.6f) return (if (m.replay) 6.5f else m.manual) * (1 - smooth((idle - 2) / .6f))
            return if (autoPlay) ((idle - 2.6f) % LOOP) else 0f
        }
        return if (autoPlay) ((stamp - m.epoch) % LOOP).toFloat() else 0f
    }
    fun touch(value: Float, action: Boolean = false) {
        m.manual = value.coerceIn(0f, 6.8f); m.replay = action; m.touchedAt = seconds(); serial++
        time.floatValue = if (reduced && action) 6.5f else value
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }
    LaunchedEffect(autoPlay, reduced, renderTime, serial) {
        if (reduced || renderTime != null) { time.floatValue = pose(seconds()); return@LaunchedEffect }
        if (!autoPlay && m.touchedAt == null && !m.dragging) { time.floatValue = 0f; return@LaunchedEffect }
        while (true) {
            withFrameNanos { time.floatValue = pose(seconds()) }
            val touch = m.touchedAt
            if (!m.dragging && touch != null && seconds() - touch >= 2.6) {
                m.epoch = touch + 2.6; m.touchedAt = null; m.replay = false
                if (!autoPlay) { time.floatValue = 0f; break }
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.White)) {
        val scale = min(maxWidth.value / 393f, maxHeight.value / 852f)
        val dx = (maxWidth.value - 393 * scale) / 2
        val dy = (maxHeight.value - 852 * scale) / 2
        Box(Modifier.offset(dx.dp, dy.dp).size(393.dp, 852.dp).graphicsLayer { scaleX = scale; scaleY = scale; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f); clip = true }
            .pointerInput(scale, reduced, renderTime) {
                val velocity = VelocityTracker()
                detectDragGestures(onDragStart = {
                    if (renderTime == null && !reduced) { velocity.resetTracking(); m.anchor = time.floatValue; m.translation = 0f; m.manual = m.anchor; m.replay = false; m.dragging = true; m.touchedAt = seconds(); serial++ }
                }, onDrag = { change, amount ->
                    if (renderTime == null && !reduced) { velocity.addPosition(change.uptimeMillis, change.position); change.consume(); m.translation += amount.x / density / scale; m.manual = (m.anchor + m.translation / 45).coerceIn(0f, 6.8f); m.touchedAt = seconds(); time.floatValue = m.manual }
                }, onDragEnd = { if (renderTime == null && !reduced && m.dragging) { m.manual = (m.manual + (velocity.calculateVelocity().x / density / 2500).coerceIn(-.4f, .4f)).coerceIn(0f, 6.8f); time.floatValue = m.manual; m.dragging = false; m.touchedAt = seconds(); serial++; view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) } }, onDragCancel = { if (!reduced && m.dragging) { m.dragging = false; m.touchedAt = seconds(); serial++ } })
            }) {
            Box(Modifier.size(393.dp, 852.dp).graphicsLayer { val t = time.floatValue; alpha = 1 - out(t, .70f, 1f) * (1 - phase(t, 7.05f, 7.6f)); translationX = -78.dp.toPx() * out(t, .70f, 1f) * (1 - phase(t, 7.05f, 7.6f)) }.background(Color.White)) {
                Copy("Confirm and pay", 50f, 87f, 293f, 17f, FontWeight.SemiBold, center = true)
                Copy("×", 354f, 87f, 24f, 21f)
                Copy("$1,530.00 now, $2,172.60 charged on May\n21. No extra fees. More info", 25f, 133f, 330f, 12f, color = Color.Gray)
                Copy("Payment method", 24f, 204f, 345f, 15f, FontWeight.SemiBold)
                Canvas(Modifier.offset(24.dp, 234.dp).size(345.dp, 50.dp)) { drawRoundRect(Color(0xFFE8E8E8), cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()), style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx())) }
                Copy("CARD  0636", 43f, 252f, 280f, 13f, FontWeight.SemiBold); Copy("›", 346f, 244f, 20f, 22f, color = Color.Gray)
                Copy("▰  ▰   Other ways to pay", 30f, 303f, 330f, 11f, color = Color.Gray)
                Box(Modifier.offset(24.dp, 332.dp).size(345.dp, 1.dp).background(Color(0xFFF0F0F0)))
                Copy("Add travel insurance?", 24f, 356f, 330f, 15f, FontWeight.SemiBold)
                Copy("Yes, add peace of mind for $112.42.\nUp to 100% back for covered reasons.", 39f, 400f, 280f, 13f)
                Box(Modifier.offset(323.dp, 395.dp).size(44.dp).semantics { contentDescription = "Add travel insurance" }.clickable { if (time.floatValue < 1.3f || time.floatValue > 7.3f) { insurance = !insurance; touch(0f) } }, contentAlignment = Alignment.Center) { BasicText(if (insurance) "☑" else "□", style = TextStyle(fontSize = 24.sp, color = Color(0xFFA6A6A6))) }
                Copy("Get reimbursed if you cancel due to illness, flight\ndelays, and more. Plus, get assistance services\nfor emergency help. Terms apply.", 39f, 451f, 310f, 11f, color = Color.Gray)
                Copy("Price details", 24f, 531f, 330f, 15f, FontWeight.SemiBold)
                listOf("6 nights" to "$3,060.00", "Taxes" to "$642.60", "Total USD" to "$3,702.60").forEachIndexed { i, row ->
                    Copy(row.first, 24f, 565f + i * 32, 200f, 14f, if (i == 2) FontWeight.SemiBold else FontWeight.Normal)
                    Copy(row.second, 264f, 565f + i * 32, 105f, 14f)
                }
                Copy("Price breakdown", 24f, 679f, 330f, 12f)
                Action("Confirm and pay", 745f, Color(0xFFE60055), checkoutVisible) { if (time.floatValue < 1.3f || time.floatValue > 7.3f) touch(1f, true) }
                Copy("By selecting the button, I agree to the booking terms.", 24f, 806f, 345f, 10f, color = Color.Gray, center = true)
                Box(Modifier.size(393.dp, 852.dp).graphicsLayer { alpha = out(time.floatValue, .70f, 1f) * .10f * (1 - phase(time.floatValue, 7.05f, 7.6f)) }.background(Color.Black))
            }
            Box(Modifier.size(393.dp, 852.dp).graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen; val t = time.floatValue; alpha = (1 - phase(t, 5.25f, 5.60f)) * (1 - phase(t, 7.05f, 7.6f)); translationX = (1 - out(t, .70f, 1f)) * 393.dp.toPx() }.background(Color.White)) {
                Canvas(Modifier.size(393.dp, 852.dp).clearAndSetSemantics { contentDescription = "Payment terminal with pink status ring, clock, card and flowers" }) {
                    drawIntoCanvas { canvas -> val c = canvas.nativeCanvas; c.save(); c.scale(size.width / 393, size.height / 852); TerminalReservationReviewDrawing.terminal(c, time.floatValue, artwork); c.restore() }
                }
                Box(Modifier.graphicsLayer { alpha = 1 - phase(time.floatValue, 4.30f, 4.55f) }) { Copy("Reviewing\npayment details", 60f, 535f, 273f, 26f, FontWeight.Medium, Color.Black, true) }
            }
            Box(Modifier.size(393.dp, 852.dp).graphicsLayer { compositingStrategy = CompositingStrategy.ModulateAlpha; val p = phase(time.floatValue, 5.25f, 5.60f); alpha = p * (1 - phase(time.floatValue, 7.05f, 7.6f)); translationY = (1 - p) * 18.dp.toPx() }.background(Color(0xFFF9F9F5))) {
                Copy("Your reservation is\nconfirmed!", 45f, 110f, 303f, 25f, FontWeight.SemiBold, Color.Black, true)
                Canvas(Modifier.size(393.dp, 710.dp).graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) { drawIntoCanvas { canvas -> val c = canvas.nativeCanvas; c.save(); c.scale(size.width / 393, size.height / 710); TerminalReservationReviewDrawing.reservation(c); c.restore() } }
                Canvas(Modifier.offset(61.dp, 237.dp).size(271.dp, 254.dp).graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) { drawIntoCanvas { canvas -> val c = canvas.nativeCanvas; c.save(); c.scale(size.width / 271, size.height / 254); TerminalReservationReviewDrawing.room(c, artwork); c.restore() } }
                Copy("Fern House Art Deco", 65f, 511f, 263f, 16f, FontWeight.Medium)
                Copy("May 5–11", 65f, 536f, 263f, 12f, color = Color.Gray)
                Copy("1 guest\n$3,702.60 total", 65f, 582f, 263f, 12f, color = Color.Gray)
                Action("Done", 768f, Color(0xFF141414), confirmedVisible) { if (time.floatValue in 5.425f..7.35f) touch(0f) }
            }
        }
    }
}

@Composable
private fun Copy(text: String, x: Float, y: Float, width: Float, size: Float, weight: FontWeight = FontWeight.Normal, color: Color = Color(0xFF1F1F1F), center: Boolean = false) {
    BasicText(text, modifier = Modifier.offset(x.dp, y.dp).size(width.dp, (size * 1.25f * (text.count { it == '\n' } + 1) + 5).dp), style = TextStyle(color = color, fontSize = size.sp, fontWeight = weight, fontFamily = FontFamily.SansSerif, textAlign = if (center) TextAlign.Center else TextAlign.Left, lineHeight = (size * 1.19f).sp, platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)))
}
@Composable
private fun Action(text: String, y: Float, color: Color, active: Boolean, action: () -> Unit) {
    Box(Modifier.offset(24.dp, y.dp).size(345.dp, 48.dp).background(color, RoundedCornerShape(7.dp)).then(if (active) Modifier.clickable(onClick = action) else Modifier.clearAndSetSemantics { }), contentAlignment = Alignment.Center) {
        BasicText(text, style = TextStyle(color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
    }
}

private class TerminalReservationReviewMotion {
    var epoch = seconds()
    var touchedAt: Double? = null
    var manual = 0f
    var replay = false
    var dragging = false
    var anchor = 0f
    var translation = 0f
}

private class TerminalReservationReviewAssets(private val manager: AssetManager) {
    private fun load(name: String): Bitmap = manager.open("terminalreservationreview/terminalreservationreview_" + name).use { BitmapFactory.decodeStream(it) }
    val terminal = load("terminal.png")
    val vase = load("vase.png")
    val flowers = load("flowers.png")
    val card = load("card.png")
    val room = load("room.jpg")
}

private object TerminalReservationReviewCard {
    val feed = arrayOf(
        floatArrayOf(0.99f, -0.9303293f),
        floatArrayOf(1.071f, -0.545208f),
        floatArrayOf(1.143f, -0.2256194f),
        floatArrayOf(1.214f, 0.0167858f),
        floatArrayOf(1.286f, 0.3133213f),
        floatArrayOf(1.357f, 0.510619f),
        floatArrayOf(1.429f, 0.751616f),
        floatArrayOf(1.5f, 0.969007f),
        floatArrayOf(1.571f, 1f),
    )
    val eject = arrayOf(
        floatArrayOf(5.179f, 0f),
        floatArrayOf(5.214f, 0.1080205f),
        floatArrayOf(5.25f, 0.2542007f),
        floatArrayOf(5.286f, 0.6051038f),
        floatArrayOf(5.321f, 0.7773739f),
        floatArrayOf(5.42f, 1.5710126f),
    )
    fun sample(t: Float, keys: Array<FloatArray>): Float {
        if (t <= keys[0][0]) return keys[0][1]
        for (i in 1 until keys.size) {
            if (t > keys[i][0]) continue
            val a = keys[i - 1]; val b = keys[i]; val p = (t - a[0]) / (b[0] - a[0])
            return a[1] + (b[1] - a[1]) * p
        }
        return keys.last()[1]
    }
}

private object TerminalReservationReviewRing {
    // Keep source angles unwrapped while interpolating the counterclockwise arc.
    private val keys = arrayOf(
        floatArrayOf(1.5f, 259f, 190f),
        floatArrayOf(1.625f, 214f, 190f),
        floatArrayOf(1.678571f, 194.71f, 190f),
        floatArrayOf(1.714286f, 181.86f, 190f),
        floatArrayOf(1.75f, 169f, 194f),
        floatArrayOf(1.875f, 120f, 197f),
        floatArrayOf(2f, 78f, 197f),
        floatArrayOf(2.125f, 44f, 193f),
        floatArrayOf(2.25f, -11f, 200f),
        floatArrayOf(2.375f, -54f, 187f),
        floatArrayOf(2.5f, -97f, 183f),
        floatArrayOf(2.625f, -142f, 186f),
        floatArrayOf(2.75f, -188f, 196f),
        floatArrayOf(2.875f, -237f, 199f),
        floatArrayOf(3f, -292f, 211f),
        floatArrayOf(3.125f, -313f, 187f),
        floatArrayOf(3.25f, -368f, 200f),
        floatArrayOf(3.375f, -411f, 182f),
        floatArrayOf(3.5f, -457f, 183f),
        floatArrayOf(3.571429f, -481f, 187f),
        floatArrayOf(3.642857f, -504f, 194f),
        floatArrayOf(3.714286f, -529f, 189f),
        floatArrayOf(3.785714f, -563f, 193f),
        floatArrayOf(3.857143f, -629f, 210f),
        floatArrayOf(3.928571f, -664f, 212f),
        floatArrayOf(4f, -728f, 256f),
        floatArrayOf(4.071429f, -773f, 274f),
        floatArrayOf(4.142857f, -803f, 283f),
        floatArrayOf(4.214286f, -834f, 309f),
        floatArrayOf(4.285714f, -859f, 332f),
        floatArrayOf(4.357143f, -874f, 345f),
        floatArrayOf(4.392857f, -874f, 345f),
        floatArrayOf(4.428571f, -874f, 360f),
        floatArrayOf(4.464286f, -874f, 360f),
        floatArrayOf(4.5f, -874f, 360f),
    )
    fun pose(t: Float): Pair<Float, Float> {
        if (t <= keys[0][0]) return (keys[0][1] + (keys[0][0] - t) * 360) to keys[0][2]
        for (i in 1 until keys.size) {
            if (t > keys[i][0]) continue
            val a = keys[i - 1]; val b = keys[i]; val p = (t - a[0]) / (b[0] - a[0])
            return (a[1] + (b[1] - a[1]) * p) to (a[2] + (b[2] - a[2]) * p)
        }
        return keys.last()[1] to keys.last()[2]
    }
}

private object TerminalReservationReviewDrawing {
    private fun fill(hex: String) = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = NativeColor.parseColor(hex) }
    private fun image(c: NativeCanvas, image: Bitmap, rect: RectF, alpha: Float = 1f) {
        c.drawBitmap(image, null, rect, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { this.alpha = (alpha.coerceIn(0f, 1f) * 255).toInt() })
    }
    private fun pose(c: NativeCanvas, x: Float, y: Float, scale: Float, roll: Float, dx: Float = 0f, dy: Float = 0f) {
        c.translate(x + dx, y + dy); c.rotate(roll); c.scale(scale, scale); c.translate(-x, -y)
    }
    fun terminal(c: NativeCanvas, t: Float, art: TerminalReservationReviewAssets) {
        val reader = out(t, .87f, 1.08f); val vase = out(t, .94f, 1.13f)
        val age = max(0f, t - .97f)
        val sway = if (t < 1.80f) sin(age * 24) * kotlin.math.exp(-age * 6) * 18 else 0f
        c.drawOval(RectF(117f, 424f, 258f, 447f), fill("#000000").apply { alpha = (max(reader, vase) * 25).toInt(); maskFilter = android.graphics.BlurMaskFilter(7f, android.graphics.BlurMaskFilter.Blur.NORMAL) })
        c.save(); pose(c, 168f, 432f, .10f + .90f * vase, 0f, 0f, (1 - vase) * 8)
        c.save(); pose(c, 168f, 373f, 1f, sway)
        image(c, art.flowers, RectF(134f, 308f, 204f, 433f), vase); c.restore()
        image(c, art.vase, RectF(134f, 308f, 204f, 433f), vase); c.restore()

        val feed = TerminalReservationReviewCard.sample(t, TerminalReservationReviewCard.feed)
        val eject = TerminalReservationReviewCard.sample(t, TerminalReservationReviewCard.eject)
        // Translate inside the mouth-centered plane so its centerline stays on the slot.
        val travel = 95 * (1 - feed) + 110 * eject
        c.save(); pose(c, 192f, 471f, 1.22f - .22f * feed + .05f * eject, -18 * (1 - feed) - 12 * eject)
        c.translate(-travel, travel * 34 / 42)
        c.concat(android.graphics.Matrix().apply { setValues(floatArrayOf(42f / 300, 37f / 190, 148.3f, -34f / 300, 17f / 190, 482.9f, 0f, 0f, 1f)) })
        image(c, art.card, RectF(0f, 0f, 300f, 190f), phase(t, .98f, 1.04f) * (1 - phase(t, 5.34f, 5.48f))); c.restore()

        c.save(); pose(c, 213f, 443f, .08f + .92f * reader, -28 * (1 - reader), -8 * (1 - reader), 20 * (1 - reader))
        image(c, art.terminal, RectF(155f, 385f, 276f, 490f), reader)
        c.concat(android.graphics.Matrix().apply { setValues(floatArrayOf(.66f, -.58f, 204f, .30f, .47f, 397f, 0f, 0f, 1f)) })
        val (start, sweep) = TerminalReservationReviewRing.pose(t)
        val ring = NativePath().apply { addArc(RectF(13.5f, 8.5f, 70.5f, 65.5f), start, sweep) }
        val opacity = reader * phase(t, 1.25f, 1.75f) * (1 - phase(t, 5.214f, 5.232f) * (.96f + .04f * phase(t, 5.232f, 5.304f)))
        val pink = fill("#ED2868").apply { style = Paint.Style.STROKE; strokeWidth = 2.8f; strokeCap = Paint.Cap.ROUND; alpha = (255 * opacity).toInt() }
        c.drawPath(ring, Paint(pink).apply { strokeWidth = 5f; alpha = (66 * opacity).toInt(); maskFilter = android.graphics.BlurMaskFilter(2f, android.graphics.BlurMaskFilter.Blur.NORMAL) }); c.drawPath(ring, pink)
        // The source resolves to clock hands rather than a check mark.
        val length = out(t, 4.40f, 4.65f) * 28.85f
        val first = (length / 16.33f).coerceAtMost(1f); val second = ((length - 16.33f) / 12.52f).coerceIn(0f, 1f)
        val hands = NativePath().apply { moveTo(31.22f, 24.73f); lineTo(31.22f + 10.78f * first, 24.73f + 12.27f * first); if (length > 16.33f) lineTo(42 + 9.48f * second, 37 - 8.18f * second) }
        pink.strokeWidth = 2.6f; pink.strokeJoin = Paint.Join.ROUND; c.drawPath(hands, pink); c.restore()
    }
    fun reservation(c: NativeCanvas) {
        val shape = RectF(49f,225f,344f,636f)
        c.drawRoundRect(shape,17f,17f,fill("#FFFFFF").apply { setShadowLayer(20f,0f,18f,0x1A000000) })
        c.drawRoundRect(shape,17f,17f,fill("#FFFFFF").apply { setShadowLayer(2f,0f,2f,0x0F000000) })
    }
    fun room(c: NativeCanvas, art: TerminalReservationReviewAssets) {
        c.save(); c.clipPath(NativePath().apply { addRoundRect(RectF(0f,0f,271f,254f),8f,8f,NativePath.Direction.CW) })
        image(c, art.room, RectF(0f,-8.5f,271f,262.5f)); c.restore()
    }
}
