package com.ngocdevv.nativecook.blocks.cards.iceddrinkswitcher

import android.content.Context
import android.database.ContentObserver
import android.graphics.BitmapFactory
import android.graphics.Canvas as NativeCanvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.activity.compose.LocalActivity
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

// A faithful port of the React Native reference, including the six PNGs and both Archivo fonts.
// LOOP = 7.5 s: three 2.5 s stops, each holding for 1.8 s and switching for 0.7 s (cubic).
// Glass: center W/2,H*0.4; size 340 dp; move 360r, turn -14r°, scale 1-0.25|r|, fade 1-|r|.
// Ingredients: spread 1+1.2|r|, shift 200r, fade 1-1.5|r|, drift 8sin(4πt/LOOP+phase).
// Glass bob: 6sin(6πt/LOOP). Swipe: 300 dp/drink, 0.35 s snap, 2 s hold, resume that drink.
// Reduced motion stops the frame clock and disables the swipe; the initial matcha remains legible.
/** Seconds. */
const val LOOP = 7.5
private const val STOP = 2.5
private const val SWITCH = 1.8
private const val SNAP = 0.35
private const val HOLD = 2.0
private const val ASSETS = "iceddrinkswitcher/iceddrinkswitcher_"
private val INK = Color(0xFF1C1410)
private val MUTED = Color(0xFF7A6F68)

private data class Drink(val word: String, val name: String, val line: String, val price: String, val tint: Int, val deep: Int)
private val DRINKS = listOf(
    Drink("MATCHA", "Iced Matcha Latte", "Stone-ground matcha, oat milk, a little honey.", "$5.40", 0xFFDDEFD1.toInt(), 0xFF2F6B2A.toInt()),
    Drink("COFFEE", "Iced Cold Brew", "Steeped for 18 hours, finished with whole milk.", "$4.80", 0xFFF1E3D2.toInt(), 0xFF6A3B1D.toInt()),
    Drink("COCOA", "Iced Cocoa Cream", "Dark cocoa, cold milk and a cloud of cream.", "$5.10", 0xFFECD5C8.toInt(), 0xFF5B271A.toInt()),
)
private data class Bit(val dx: Double, val dy: Double, val size: Double, val depth: Double, val turn: Double, val phase: Double)
// Keep this hash in Double: Float rounding changes the seeded ingredient arrangement.
private val BITS = List(8) { i ->
    fun h(n: Int): Double {
        val x = sin((i + 1) * 31.17 + n * 11.73) * 43758.5453
        return x - floor(x)
    }
    val a = i / 8.0 * PI * 2 + (h(1) - 0.5) * 0.6
    val r = 120 + 60 * h(2)
    Bit(cos(a) * r, sin(a) * r * 1.15 - 10, 44 + 46 * h(3), h(4), h(5) * 360, h(6) * PI * 2)
}
private fun wrap3(i: Double) = ((i % 3) + 3) % 3
private fun rounded(i: Double) = floor(i + 0.5) // JavaScript Math.round, including negative ties.
private fun ease(x: Double) = if (x < 0.5) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2
private fun smooth(x: Double): Double = x.coerceIn(0.0, 1.0).let { it * it * (3 - 2 * it) }
private fun offsetOf(j: Int, i: Double): Double {
    var r = j - wrap3(i)
    if (r > 1.5) r -= 3
    if (r <= -1.5) r += 3
    return r
}
private fun indexAt(t: Double): Double {
    val k = floor(t / STOP)
    return k + ease(((t - k * STOP - SWITCH) / (STOP - SWITCH)).coerceIn(0.0, 1.0))
}
private fun colorAt(i: Double, deep: Boolean): Color {
    val base = floor(i)
    val u = i - base
    val first = DRINKS[wrap3(base).toInt()]
    val next = DRINKS[wrap3(base + 1).toInt()]
    val a = if (deep) first.deep else first.tint
    val b = if (deep) next.deep else next.tint
    fun channel(shift: Int) = (((a ushr shift) and 255) * (1 - u) + ((b ushr shift) and 255) * u).roundToInt()
    return Color(channel(16), channel(8), channel(0))
}
private class Driver {
    var index by mutableDoubleStateOf(0.0)
    var clock by mutableDoubleStateOf(0.0)
    var elapsed = 0.0
    var origin = 0.0
    var touching = false
    var manual = false
    var released = -1.0
    var grabbed = 0.0
    var from = 0.0
    var snapTo = 0.0
    var lastStep = 0.0
    fun frame(dt: Double, autoPlay: Boolean) {
        elapsed += dt
        clock = (elapsed - origin) % LOOP
        if (touching) return
        if (manual) {
            val since = elapsed - released
            if (since < SNAP) index = from + (snapTo - from) * ease(since / SNAP)
            else {
                index = snapTo
                if (since > SNAP + HOLD) {
                    manual = false
                    origin = elapsed - wrap3(snapTo) * STOP
                }
            }
            return
        }
        index = if (autoPlay) indexAt(clock) else 0.0
    }
    fun begin() {
        touching = true
        manual = true
        grabbed = index
        lastStep = rounded(index)
    }
    fun drag(translation: Float): Boolean {
        index = grabbed - translation / 300.0
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
private class Artwork(private val context: Context) {
    private fun bitmap(name: String) = context.assets.open("$ASSETS$name.png").use {
        requireNotNull(BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inScaled = false }))
    }
    private val glasses = listOf("drink-matcha", "drink-coffee", "drink-cocoa").map(::bitmap)
    private val sprites = listOf("leaf", "bean", "cocoa").map(::bitmap)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val rect = RectF()
    fun draw(c: NativeCanvas, i: Double, t: Double, cx: Float, cy: Float) {
        bits(c, false, i, t, cx, cy)
        val bob = (6 * sin(t / LOOP * PI * 6)).toFloat()
        glasses.forEachIndexed { j, image ->
            val r = offsetOf(j, i)
            val fade = 1 - abs(r)
            if (fade <= 0) return@forEachIndexed
            paint.alpha = (fade * 255).roundToInt().coerceIn(0, 255)
            val s = (1 - 0.25 * abs(r)).toFloat()
            c.save()
            c.translate(cx + (r * 360).toFloat(), cy + bob)
            c.rotate((-14 * r).toFloat())
            c.scale(s, s)
            rect.set(-170f, -170f, 170f, 170f)
            c.drawBitmap(image, null, rect, paint)
            c.restore()
        }
        bits(c, true, i, t, cx, cy)
    }
    private fun bits(c: NativeCanvas, front: Boolean, i: Double, t: Double, cx: Float, cy: Float) {
        sprites.forEachIndexed { j, image ->
            val r = offsetOf(j, i)
            val fade = 1 - 1.5 * abs(r)
            if (fade <= 0) return@forEachIndexed
            BITS.forEach { bit ->
                if ((bit.depth > 0.5) != front) return@forEach
                val spread = 1 + 1.2 * abs(r)
                val drift = sin(t / LOOP * PI * 4 + bit.phase)
                val x = cx + bit.dx * spread + r * 200
                val y = cy + bit.dy * spread + 8 * drift
                val size = (bit.size * if (front) 1.15 else 0.8).toFloat()
                paint.alpha = (fade * (if (front) 1.0 else 0.85) * 255).roundToInt().coerceIn(0, 255)
                c.save()
                c.translate(x.toFloat(), y.toFloat())
                c.rotate((bit.turn + 10 * drift).toFloat())
                rect.set(-size / 2, -size / 2, size / 2, size / 2)
                c.drawBitmap(image, null, rect, paint)
                c.restore()
            }
        }
    }
}

/** A full-screen drink card with offline artwork and a swipe-driven carousel. */
@Composable
fun IcedDrinkSwitcher(autoPlay: Boolean = false, brand: String = "chill.", action: String = "Add to order", modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val view = LocalView.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val driver = remember { Driver() }
    val art = remember(context) { Artwork(context) }
    val black = remember(context) { FontFamily(androidx.compose.ui.text.font.Typeface(Typeface.createFromAsset(context.assets, "${ASSETS}ArchivoExpanded-Black.ttf"))) }
    val semi = remember(context) { FontFamily(androidx.compose.ui.text.font.Typeface(Typeface.createFromAsset(context.assets, "${ASSETS}Archivo-SemiBold.ttf"))) }
    val reduced = rememberIcedDrinkSwitcherReducedMotion()
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
    IcedDrinkSwitcherSystemBars()
    val safeTop = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding()
    val safeBottom = WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding()
    val noPadding = PlatformTextStyle(includeFontPadding = false)
    BoxWithConstraints(modifier.fillMaxSize().drawBehind { drawRect(colorAt(driver.index, false)) }) {
        val height = maxHeight
        val cardTop = height * 0.64f
        Row(Modifier.offset(y = safeTop + 8.dp).padding(horizontal = 24.dp).fillMaxWidth().height(34.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(brand, style = TextStyle(fontFamily = black, fontSize = 22.sp, letterSpacing = (-0.5).sp, color = INK, platformStyle = noPadding))
            Box(Modifier.size(34.dp).background(INK, CircleShape), contentAlignment = Alignment.Center) {
                Text("2", style = TextStyle(fontFamily = black, fontSize = 13.sp, color = Color.White, platformStyle = noPadding))
            }
        }
        Box(Modifier.offset(y = height * 0.27f - 40.dp).fillMaxWidth().height(80.dp), contentAlignment = Alignment.TopCenter) {
            DRINKS.forEachIndexed { k, drink ->
                Text(drink.word, modifier = Modifier.graphicsLayer {
                    val r = offsetOf(k, driver.index)
                    alpha = (1 - smooth(abs(r) / 0.6)).toFloat()
                    translationX = (r * 120 * density).toFloat()
                }, style = TextStyle(fontFamily = black, fontSize = 64.sp, lineHeight = 80.sp, letterSpacing = (-2).sp, color = Color(drink.deep), platformStyle = noPadding))
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            val c = drawContext.canvas.nativeCanvas
            c.save()
            c.scale(density, density)
            art.draw(c, driver.index, driver.clock, size.width / density / 2, size.height / density * 0.4f)
            c.restore()
        }
        Box(Modifier.fillMaxWidth().height(cardTop).semantics { contentDescription = "Drinks. Swipe to change." }.pointerInput(reduced, density) {
            if (reduced) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown()
                driver.begin()
                var activated = false
                try {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        val translation = (change.position.x - down.position.x) / density
                        if ((change.position - down.position).getDistance() >= 4 * density) activated = true
                        if (activated) {
                            change.consume()
                            if (driver.drag(translation)) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        }
                    }
                } finally { driver.release() }
            }
        })
        Column(Modifier.offset(y = cardTop).fillMaxWidth().height(height - cardTop).background(Color.White, RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)).padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = safeBottom + 14.dp)) {
            Box(Modifier.fillMaxWidth().height(76.dp)) {
                DRINKS.forEachIndexed { k, drink ->
                    Column(Modifier.fillMaxWidth().graphicsLayer {
                        val r = abs(offsetOf(k, driver.index))
                        alpha = (1 - smooth(r / 0.45)).toFloat()
                        translationY = (r * 14 * density).toFloat()
                    }) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(drink.name, Modifier.alignByBaseline(), style = TextStyle(fontFamily = semi, fontSize = 24.sp, letterSpacing = (-0.4).sp, color = INK, platformStyle = noPadding))
                            Text(drink.price, Modifier.alignByBaseline(), style = TextStyle(fontFamily = black, fontSize = 20.sp, color = Color(drink.deep), platformStyle = noPadding))
                        }
                        Text(drink.line, Modifier.padding(top = 6.dp), style = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, color = MUTED, platformStyle = noPadding))
                    }
                }
            }
            Row(Modifier.padding(top = 8.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("S", "M", "L").forEach { s ->
                    val selected = s == "M"
                    Box(Modifier.size(44.dp).background(if (selected) INK else Color.Transparent, CircleShape).border(1.5.dp, if (selected) INK else Color(0xFFE4DDD7), CircleShape), contentAlignment = Alignment.Center) {
                        Text(s, style = TextStyle(fontFamily = semi, fontSize = 15.sp, color = if (selected) Color.White else INK, platformStyle = noPadding))
                    }
                }
                Text("16 oz · less ice", Modifier.padding(start = 6.dp), style = TextStyle(fontSize = 14.sp, color = MUTED, platformStyle = noPadding))
            }
            Spacer(Modifier.height(18.dp))
            Box(Modifier.fillMaxWidth().height(56.dp).clip(CircleShape).drawBehind { drawRect(colorAt(driver.index, true)) }.semantics { role = Role.Button; contentDescription = action }, contentAlignment = Alignment.Center) {
                Text(action, style = TextStyle(fontFamily = semi, fontSize = 17.sp, color = Color.White, platformStyle = noPadding))
            }
        }
    }
}

@Composable
private fun rememberIcedDrinkSwitcherReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    fun read() = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    var reduced by remember(resolver) { mutableStateOf(read()) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { reduced = read() }
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return reduced
}

@Composable
private fun IcedDrinkSwitcherSystemBars() {
    val window = LocalActivity.current?.window
    val view = LocalView.current
    DisposableEffect(window, view) {
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val oldStatus = controller?.isAppearanceLightStatusBars
        val oldNavigation = controller?.isAppearanceLightNavigationBars
        controller?.show(WindowInsetsCompat.Type.statusBars())
        controller?.isAppearanceLightStatusBars = true
        controller?.isAppearanceLightNavigationBars = true
        onDispose {
            if (oldStatus != null) controller?.isAppearanceLightStatusBars = oldStatus
            if (oldNavigation != null) controller?.isAppearanceLightNavigationBars = oldNavigation
        }
    }
}
