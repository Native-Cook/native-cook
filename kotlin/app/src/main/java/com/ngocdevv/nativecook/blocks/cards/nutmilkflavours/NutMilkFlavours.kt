package com.ngocdevv.nativecook.blocks.cards.nutmilkflavours

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas as NativeCanvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

// Faithfully ports NutMilkFlavours.tsx, including its Instrument Serif font and six PNGs.
// Layout: 34 dp header; 70 dp letters around a 140 dp gap at H*0.4; 360 dp bottle at H*0.42;
// table H*0.66; 70 dp info, 14 dp gap, 58 dp button, safe bottom + 14 dp padding.
// LOOP = 7.8 s: three 2.6 s stops. Nuts fall with 0.1 s stagger, 0.45 s quadratic fall,
// 34 dp/0.27 s first bounce, 9 dp/0.18 s second bounce. Switch at local 1.9–2.6 s (cubic):
// letter lag clamp(|r|*1.25-n*0.04), translate ±110*lag; bottle translate 360r, turn -16r°,
// opacity 1-|r| around its bottom centre, plus 2sin(6πt/LOOP)° sway. Nuts drop 120r and fade.
// Swipe 300 dp/flavour, selection haptic at each rounded index; release snaps in 0.35 s,
// holds 2 s, then resumes at the chosen flavour's local 1.2 s. Reduced motion freezes the
// current frame (initial index=0/local=1/clock=0), disables swiping and stops the frame clock.

/** Seconds. */
const val LOOP = 7.8
private const val STOP = 2.6
private const val SWITCH = 1.9
private const val BOTTLE = 360f
private const val PER = 300.0
private const val HOLD = 2.0
private const val SNAP = 0.35
private const val ASSETS = "nutmilkflavours/nutmilkflavours_"
private val INK = Color(0xFF2A1D12)

private data class Flavour(
    val left: String, val right: String, val name: String, val line: String, val price: String,
    val tint: Int, val deep: Int,
)
private val FLAVOURS = listOf(
    Flavour("Alm", "ond", "Almond milk", "Toasted almonds, a pinch of sea salt. 1 L", "$4.20", 0xFFF4EBDD.toInt(), 0xFF6E4A26.toInt()),
    Flavour("Haze", "lnut", "Hazelnut milk", "Roasted hazelnuts, no added sugar. 1 L", "$4.60", 0xFFEAD9C6.toInt(), 0xFF5B3519.toInt()),
    Flavour("Wal", "nut", "Walnut milk", "Cold-pressed walnuts and a little date. 1 L", "$4.90", 0xFFE3E6D6.toInt(), 0xFF3F4A2A.toInt()),
)
private data class Nut(val dx: Double, val size: Double, val delay: Double, val turn: Double, val sink: Double)
// Double precision preserves the reference's seeded positions.
private val NUTS = List(6) { i ->
    fun h(n: Int): Double {
        val x = sin((i + 1) * 19.31 + n * 7.77) * 43758.5453
        return x - floor(x)
    }
    Nut((if (i % 2 == 0) -1 else 1) * (70 + 90 * h(1)), 40 + 22 * h(2), i * 0.1, h(3) * 360, h(4) * 18)
}
private fun clamp01(x: Double) = x.coerceIn(0.0, 1.0)
private fun ease(x: Double) = if (x < 0.5) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2
private fun smooth(x: Double) = clamp01(x).let { it * it * (3 - 2 * it) }
private fun wrap3(i: Double) = ((i % 3) + 3) % 3
private fun rounded(i: Double) = floor(i + 0.5) // JavaScript Math.round, including negative ties.
private fun offsetOf(j: Int, index: Double): Double {
    var r = j - wrap3(index)
    if (r > 1.5) r -= 3
    if (r <= -1.5) r += 3
    return r
}
private fun dropHeight(x: Double): Double = when {
    x <= 0 -> 260.0
    x >= 0.9 -> 0.0
    x < 0.45 -> 260 * (1 - (x / 0.45).pow(2))
    x < 0.72 -> 34 * sin(PI * (x - 0.45) / 0.27)
    else -> 9 * sin(PI * (x - 0.72) / 0.18)
}
private fun colorAt(index: Double, deep: Boolean): Color {
    val base = floor(index)
    val u = index - base
    val first = FLAVOURS[wrap3(base).toInt()]
    val next = FLAVOURS[wrap3(base + 1).toInt()]
    val a = if (deep) first.deep else first.tint
    val b = if (deep) next.deep else next.tint
    fun channel(shift: Int) = (((a ushr shift) and 255) * (1 - u) + ((b ushr shift) and 255) * u).roundToInt()
    return Color(channel(16), channel(8), channel(0))
}

/** State is read only in draw/graphicsLayer; animation never recomposes the screen per frame. */
private class Driver {
    var index by mutableDoubleStateOf(0.0)
    var local by mutableDoubleStateOf(1.0)
    var clock by mutableDoubleStateOf(0.0)
    private var elapsed = 0.0
    private var origin = 0.0
    private var touching = false
    private var manual = false
    private var released = -1.0
    private var grabbed = 0.0
    private var from = 0.0
    private var snapTo = 0.0
    private var lastStep = 0.0

    fun frame(dt: Double, autoPlay: Boolean) {
        elapsed += dt
        clock = (elapsed - origin) % LOOP
        if (touching) return
        if (manual) {
            val since = elapsed - released
            if (since < SNAP) index = from + (snapTo - from) * ease(since / SNAP)
            else {
                index = snapTo
                local = minOf(1.2, since - SNAP)
                if (since > SNAP + HOLD) {
                    manual = false
                    origin = elapsed - wrap3(snapTo) * STOP - 1.2
                }
            }
            return
        }
        if (!autoPlay) {
            index = 0.0
            local = 1.2
            return
        }
        val k = floor(clock / STOP)
        local = clock - k * STOP
        index = k + ease(clamp01((local - SWITCH) / (STOP - SWITCH)))
    }

    fun begin() {
        touching = true
        manual = true
        grabbed = index
        lastStep = rounded(index)
    }
    fun drag(translation: Float): Boolean {
        index = grabbed - translation / PER
        val step = rounded(index)
        if (step == lastStep) return false
        lastStep = step
        return true
    }
    fun release() {
        touching = false
        from = index
        snapTo = rounded(index)
        released = elapsed
    }
}

/** Decode all artwork and measure individual letter advances once, before the frame clock. */
private class Artwork(private val context: Context) {
    val serif = Typeface.createFromAsset(context.assets, "${ASSETS}InstrumentSerif-Regular.ttf")
    private fun bitmap(name: String) = context.assets.open("$ASSETS$name.png").use {
        requireNotNull(BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inScaled = false }))
            .apply { prepareToDraw() }
    }
    private val bottles = listOf("bottle-almond", "bottle-hazelnut", "bottle-walnut").map(::bitmap)
    private val nuts = listOf("almond", "hazelnut", "walnut").map(::bitmap)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val rect = RectF()
    private val letterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = serif
        textSize = 70f
        isSubpixelText = true
    }
    private class Half(val text: String, val advances: FloatArray) { val width = advances.sum() }
    private val halves = FLAVOURS.map { f ->
        listOf(f.left, f.right).map { word ->
            Half(word, FloatArray(word.length) { n -> letterPaint.measureText(word, n, n + 1) - 2f })
        }
    }

    fun draw(c: NativeCanvas, index: Double, local: Double, t: Double, width: Float, height: Float) {
        val cx = width / 2
        val bottleY = height * 0.42f
        val tableY = height * 0.66f
        val wordY = height * 0.4f - 55f
        val fm = letterPaint.fontMetrics
        val baseline = wordY + (110f - fm.descent - fm.ascent) / 2
        FLAVOURS.forEachIndexed { k, f ->
            letterPaint.color = f.deep
            halves[k].forEachIndexed { side, half ->
                var x = if (side == 0) cx - 70 - half.width else cx + 70
                c.save()
                c.clipRect(x, wordY, x + half.width, wordY + 110)
                val r = offsetOf(k, index)
                half.text.forEachIndexed { n, _ ->
                    val lag = clamp01(abs(r) * 1.25 - (n + if (side == 0) 0 else 4) * 0.04)
                    val y = (if (r <= 0) -1 else 1) * lag * 110
                    letterPaint.alpha = ((1 - lag) * 255).roundToInt()
                    c.drawText(half.text, n, n + 1, x, baseline + y.toFloat(), letterPaint)
                    x += half.advances[n]
                }
                c.restore()
            }
        }
        paint.color = 0x2E2A1D12
        c.drawRect(24f, tableY, width - 24, tableY + 1, paint)

        val switching = index - floor(index)
        val img = nuts[wrap3(floor(index)).toInt()]
        NUTS.forEach { nut ->
            val h = dropHeight(local - nut.delay)
            val y = tableY - nut.size / 2 + nut.sink - h + switching * 120
            paint.alpha = ((1 - switching) * 255).roundToInt()
            val half = (nut.size / 2).toFloat()
            rect.set(-half, -half, half, half)
            c.save()
            c.translate(cx + nut.dx.toFloat(), y.toFloat())
            c.rotate((nut.turn + h * 0.6).toFloat())
            c.drawBitmap(img, null, rect, paint)
            c.restore()
        }
        val sway = 2 * sin(t / LOOP * PI * 6)
        bottles.forEachIndexed { j, b ->
            val r = offsetOf(j, index)
            val fade = 1 - abs(r)
            if (fade <= 0) return@forEachIndexed
            paint.alpha = (fade * 255).roundToInt()
            c.save()
            c.translate(cx + (r * 360).toFloat(), bottleY + BOTTLE / 2)
            c.rotate((-16 * r + sway * fade).toFloat())
            c.translate(0f, -BOTTLE / 2)
            rect.set(-BOTTLE / 2, -BOTTLE / 2, BOTTLE / 2, BOTTLE / 2)
            c.drawBitmap(b, null, rect, paint)
            c.restore()
        }
    }
}

/** A full-screen, offline shop page; swipe sideways to change the flavour. */
@Composable
fun NutMilkFlavours(
    autoPlay: Boolean = false,
    brand: String = "Oatlier",
    action: String = "Add to basket",
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val d = LocalDensity.current.density
    val view = LocalView.current
    val driver = remember { Driver() }
    val artwork = remember(context) { Artwork(context) }
    val serif = remember(artwork) { FontFamily(androidx.compose.ui.text.font.Typeface(artwork.serif)) }
    val reduced = rememberLiveReducedMotion()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var active by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ -> active = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(active, reduced, autoPlay) {
        if (!active || reduced) return@LaunchedEffect
        var previous = 0L
        while (true) withFrameNanos { now ->
            driver.frame(if (previous == 0L) 0.0 else (now - previous) / 1e9, autoPlay)
            previous = now
        }
    }
    val configuration = LocalConfiguration.current
    // Android reapplies its bar defaults after a window resize; keep the reference's dark icons.
    key(configuration.screenWidthDp, configuration.screenHeightDp) {
        BlockSystemBars(darkIcons = true)
    }
    val safe = WindowInsets.safeDrawing.asPaddingValues()
    val safeTop = safe.calculateTopPadding()
    val safeBottom = safe.calculateBottomPadding()
    val noPadding = PlatformTextStyle(includeFontPadding = false)
    BoxWithConstraints(modifier.fillMaxSize().drawBehind { drawRect(colorAt(driver.index, deep = false)) }) {
        val footerTop = maxHeight - safeBottom - 156.dp
        Canvas(Modifier.fillMaxSize().semantics { contentDescription = "Nut milk bottle. Swipe to change flavour." }.pointerInput(d, reduced, footerTop) {
            if (reduced) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown()
                if (down.position.y >= footerTop.toPx()) return@awaitEachGesture
                driver.begin()
                var panning = false
                try {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        val translation = (change.position - down.position) / d
                        if (!panning && translation.getDistance() >= 4f) panning = true
                        if (panning) {
                            change.consume()
                            if (driver.drag(translation.x)) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        }
                    }
                } finally { driver.release() }
            }
        }) {
            val native = drawContext.canvas.nativeCanvas
            native.save()
            native.scale(d, d)
            artwork.draw(native, driver.index, driver.local, driver.clock, size.width / d, size.height / d)
            native.restore()
        }
        Row(
            Modifier.offset(y = safeTop + 8.dp).padding(horizontal = 24.dp).fillMaxWidth().height(34.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(brand, style = TextStyle(fontFamily = serif, fontSize = 28.sp, color = INK, platformStyle = noPadding))
            Text("Shop · Story · Basket (1)", style = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = INK.copy(alpha = 0.6f), platformStyle = noPadding))
        }
        Column(Modifier.align(Alignment.BottomCenter).padding(start = 24.dp, end = 24.dp, bottom = safeBottom + 14.dp)) {
            Box(Modifier.fillMaxWidth().height(70.dp)) {
                FLAVOURS.forEachIndexed { k, f ->
                    Column(Modifier.fillMaxWidth().graphicsLayer { alpha = (1 - smooth(abs(offsetOf(k, driver.index)) / 0.45)).toFloat() }) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(f.name, Modifier.alignByBaseline(), style = TextStyle(fontFamily = serif, fontSize = 34.sp, color = INK, platformStyle = noPadding))
                            Text(f.price, Modifier.alignByBaseline(), style = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(f.deep), platformStyle = noPadding))
                        }
                        Text(f.line, Modifier.padding(top = 2.dp), style = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 15.sp, lineHeight = 20.sp, color = INK.copy(alpha = 0.62f), platformStyle = noPadding))
                    }
                }
            }
            Box(
                Modifier.padding(top = 14.dp).fillMaxWidth().height(58.dp).clip(CircleShape)
                    .drawBehind { drawRect(colorAt(driver.index, deep = true)) }
                    .semantics { role = Role.Button; contentDescription = action },
                contentAlignment = Alignment.Center,
            ) {
                Text(action, style = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White, platformStyle = noPadding))
            }
        }
    }
}
