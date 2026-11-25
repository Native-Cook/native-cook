package com.ngocdevv.nativecook.blocks.gestures.yoyopullrefresh

import android.database.ContentObserver
import android.graphics.BitmapFactory
import android.graphics.Canvas as NativeCanvas
import android.graphics.Color as NativeColor
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.*

private data class YoyoPullRefreshCard(val color: Int, val title: String, val meta: String)
private val yoyoCards = listOf(
    YoyoPullRefreshCard(0xFF2B6BFF.toInt(), "Weekend plans", "Ines · 2 min"),
    YoyoPullRefreshCard(0xFFFF5C8A.toInt(), "Your order shipped", "Parcel 7 · 1 h"),
    YoyoPullRefreshCard(0xFF36D39A.toInt(), "New release notes", "Studio · 3 h"),
    YoyoPullRefreshCard(0xFFFFB21E.toInt(), "Lunch?", "Kai · 5 h"),
    YoyoPullRefreshCard(0xFF9B6BFF.toInt(), "Flight check-in", "Wren Air · 6 h"),
    YoyoPullRefreshCard(0xFFFF7A3D.toInt(), "Rent reminder", "Home · 1 d"),
    YoyoPullRefreshCard(0xFF3DC8FF.toInt(), "Photos from Sunday", "Mira · 2 d"),
)

/** Native canvas rendering under Compose's semantic inbox. No network, vibration permission or package. */
@Composable
fun YoyoPullRefresh(autoPlay: Boolean = false) {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current.density
    val resolver = context.contentResolver
    fun readReduced() = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    var reduced by remember { mutableStateOf(readReduced()) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { reduced = readReduced() }
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    var clock by remember { mutableDoubleStateOf(0.0) }
    val touch = remember(reduced, autoPlay) { YoyoPullRefreshTouch().apply { manual = !autoPlay; loopStart = clock } }
    val art = remember { YoyoPullRefreshArt(context.assets) }
    val owner = LocalLifecycleOwner.current
    var active by remember { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, _ -> active = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(touch, reduced, active) {
        if (!active) return@LaunchedEffect
        var last = 0L; var origin = 0L; val clockBase = clock; var lastBeat = -1.0
        while (true) withFrameNanos { stamp ->
            if (last == 0L) origin = stamp
            val dt = if (last == 0L) 0.0 else ((stamp - last) / 1e9).coerceIn(0.0, .1)
            last = stamp; clock = clockBase + (stamp - origin) / 1e9
            if (reduced) {
                if (touch.rmAt >= 0 && clock - touch.rmAt > 3.9) { touch.rmAt = -1.0; touch.realRefresh = false }
            } else touch.step(clock, dt, autoPlay)
            val beat = if (reduced) { if (touch.rmAt >= 0) 1.7 + clock - touch.rmAt else -1.0 }
                       else if (touch.manual) touch.beat(clock) else -1.0
            if (touch.realRefresh && lastBeat >= 0 && beat >= 0) {
                if (lastBeat < 2.05 && beat >= 2.05) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                if (lastBeat < 3.7 && beat >= 3.7) yoyoPullRefreshAnnounce(view)
            }
            lastBeat = beat
        }
    }
    fun refresh(): Boolean {
        if (reduced) {
            if (touch.rmAt >= 0) return false
            touch.rmAt = clock; touch.realRefresh = true
        } else {
            touch.takeOver(clock)
            if (!touch.canGrab(clock)) return false
            touch.refresh(clock, true)
        }
        return true
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black).pointerInput(touch, reduced) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val pressY = down.position.y
            var endY = pressY
            down.consume()
            if (!reduced) { touch.takeOver(clock); if (touch.canGrab(clock)) touch.grab(clock) }
            try {
                do {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    endY = change.position.y
                    if (!reduced && touch.touching) { touch.drag = ((endY - pressY) / density).toDouble(); touch.lastTouch = clock }
                    change.consume()
                } while (event.changes.any { it.id == down.id && it.pressed })
            } finally {
                if (reduced) { if (YoyoPullRefreshMotion.dragD(((endY - pressY) / density).toDouble(), 0.0) > 120) refresh() }
                else if (touch.touching) touch.release(clock) else touch.lastTouch = clock
            }
        }
    }) {
        val w = maxWidth.value.toDouble(); val h = maxHeight.value.toDouble()
        val ox = (w - 393) / 2; val fy = h / 852
        touch.overflow = max(0.0, 770 * fy + 112 - h)
        val frame = touch.frame(clock, reduced)
        Canvas(Modifier.fillMaxSize()) {
            val c = drawContext.canvas.nativeCanvas
            c.save(); c.scale(density, density)
            art.draw(c, w.toFloat(), h.toFloat(), frame)
            c.restore()
        }
        Box(Modifier.offset((ox + 28).dp, (64 * fy).dp).size(120.dp, 44.dp).semantics {
            heading(); contentDescription = "Inbox"
        })
        yoyoCards.forEachIndexed { i, card ->
            Box(Modifier.offset((ox + 20).dp, ((170 + 100 * i) * fy + frame.d).dp).size(353.dp, 88.dp).semantics {
                contentDescription = "${card.title}, ${card.meta}"
                customActions = listOf(CustomAccessibilityAction("Refresh") { refresh() })
            })
        }
    }
}

// Explicit completion feedback also supports the block's API 26 minimum.
@Suppress("DEPRECATION")
private fun yoyoPullRefreshAnnounce(view: View) { view.announceForAccessibility("Up to date") }

private class YoyoPullRefreshArt(assets: android.content.res.AssetManager) {
    private val image = assets.open("yoyopullrefresh/yoyopullrefresh_yoyo.webp").use { BitmapFactory.decodeStream(it) }
    private val title = Typeface.createFromAsset(assets, "yoyopullrefresh/yoyopullrefresh_InterDisplay-Bold.ttf")
    private val medium = Typeface.createFromAsset(assets, "yoyopullrefresh/yoyopullrefresh_Inter-Medium.ttf")
    private val semibold = Typeface.createFromAsset(assets, "yoyopullrefresh/yoyopullrefresh_Inter-SemiBold.ttf")
    private val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private fun text(c: NativeCanvas, text: String, face: Typeface, size: Float, x: Float, y: Float, color: Int, alpha: Double = 1.0, tracking: Float = 0f) {
        p.shader = null; p.color = color; p.alpha = (alpha * 255).roundToInt(); p.typeface = face; p.textSize = size
        p.letterSpacing = tracking / size
        c.drawText(text, x, y, p); p.letterSpacing = 0f
    }
    fun draw(c: NativeCanvas, w: Float, h: Float, f: YoyoPullRefreshMotion.Frame) {
        val ox = (w - 393) / 2; val fy = h / 852; val d = f.d.toFloat()
        c.drawColor(NativeColor.BLACK)
        text(c, "Inbox", title, 40f, ox + 28, 98 * fy, NativeColor.WHITE, tracking = -1f)
        text(c, YoyoPullRefreshMotion.labels[f.status.shown], medium, 15f, ox + 30, 124 * fy, 0xFF7B7F92.toInt(), f.status.alpha)
        yoyoCards.forEachIndexed { i, card ->
            val x = ox + 20; val top = (170 + i * 100) * fy + d
            if (top <= h && top + 88 >= 0) {
                p.color = 0xFF121318.toInt(); p.alpha = 255; p.shader = null
                c.drawRoundRect(x, top, x + 353, top + 88, 24f, 24f, p)
                p.color = card.color; c.drawRoundRect(x + 18, top + 18, x + 70, top + 70, 16f, 16f, p)
                text(c, card.title, semibold, 17f, x + 86, top + 40, 0xFFF1F2F7.toInt())
                text(c, card.meta, medium, 14f, x + 86, top + 64, 0xFF7B7F92.toInt())
            }
        }
        val x = ox + 260; val y = f.y.toFloat() * fy
        if (y > -60 * fy && f.alpha > 0) {
            c.saveLayerAlpha(null, (f.alpha * 255).roundToInt())
            val pulse = f.spin / 5 * (.75 + .25 * sin(f.pulse * 4 * PI))
            p.alpha = 255; p.shader = RadialGradient(x, y, 120f, intArrayOf(NativeColor.argb((.38 * pulse * 255).roundToInt(), 46, 107, 255), 0x002E6BFF), floatArrayOf(10f / 120, 1f), Shader.TileMode.CLAMP)
            c.drawRect(x - 125, y - 125, x + 125, y + 125, p)
            p.shader = null; p.color = NativeColor.argb(217, 242, 242, 242); p.strokeWidth = 1.5f
            c.drawLine(x, -2f, x, y, p)
            for (k in 0..6) {
                val angle = f.turns * 360 + f.spin * 360 / 30 * .8 * (k / 6.0 - .5)
                c.save(); c.translate(x, y); c.rotate(angle.toFloat()); p.alpha = (255.0 / (k + 1)).roundToInt()
                c.drawBitmap(image, null, RectF(-70f, -70f, 70f, 70f), p); c.restore()
            }
            c.save(); val clip = Path().apply { addCircle(x, y, 69f, Path.Direction.CW) }; c.clipPath(clip)
            p.alpha = 255; p.shader = RadialGradient(x - 26, y - 28, 52f, intArrayOf(0x47FFFFFF, 0x00FFFFFF), floatArrayOf(2f / 52, 1f), Shader.TileMode.CLAMP)
            c.drawCircle(x, y, 69f, p); p.shader = null; c.restore(); c.restore()
        }
        if (f.finger > 0) {
            p.shader = null; p.color = NativeColor.argb((.3 * f.finger * 255).roundToInt(), 255, 255, 255)
            c.drawCircle(ox + 260, 320 * fy + d, 24f, p)
        }
    }
}
