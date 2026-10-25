package com.ngocdevv.nativecook.blocks.cards.sneakerparallaxshop

import android.content.Context
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.graphics.Canvas as NativeCanvas
import android.graphics.Color as NativeColor
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private const val LOOP = 7.5
private val Names = arrayOf("Crimson Lift", "Dune Runner", "Prism Court")
private val Prices = arrayOf("₹8,895", "₹11,897", "₹12,995")

private data class SneakerParallaxShopFrame(val q: Double = 0.0, val e: Double = 0.0, val detail: Int = 2) {
    fun mixed(other: SneakerParallaxShopFrame, amount: Double) = SneakerParallaxShopFrame(
        q + (other.q - q) * amount,
        e + (other.e - e) * amount,
        other.detail,
    )
}

private object SneakerParallaxShopMath {
    fun clamp(x: Double, low: Double = 0.0, high: Double = 1.0) = x.coerceIn(low, high)
    fun smooth(x: Double): Double { val u = clamp(x); return u * u * (3 - 2 * u) }
    fun rounded(x: Double) = if (x >= 0) floor(x + 0.5) else ceil(x - 0.5)
    fun automatic(time: Double): SneakerParallaxShopFrame {
        val t = max(0.0, time) % LOOP
        val q = smooth((t - 0.67) / 0.48) + smooth((t - 1.82) / 0.53) - 2 * smooth((t - 5.271933) / 1.2)
        val e = clamp((t - 3) / 0.5) * (1 - clamp((t - 4.5) / 0.5))
        return SneakerParallaxShopFrame(q, e, 2)
    }
    fun spring(progress: Double): Double {
        if (progress >= 1) return 1.0
        val u = max(0.0, progress)
        val a = 5.15
        val b = 3.59
        val end = 1 - exp(-a) * (cos(b) + a / b * sin(b))
        return (1 - exp(-a * u) * (cos(b * u) + a / b * sin(b * u))) / end
    }
}

/** One selected colored surface becomes a curved header while its shoe stays in one layer. */
@Composable
fun SneakerParallaxShop(
    autoPlay: Boolean = false,
    previewTime: Double? = null,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val reduced = rememberSneakerParallaxShopReduced()
    val driver = remember { SneakerParallaxShopDriver() }
    val painter = remember(context.applicationContext) { SneakerParallaxShopPainter(context.applicationContext) }
    val clock = remember { mutableDoubleStateOf(sneakerParallaxShopNow()) }
    val semanticPhase = remember { mutableIntStateOf(0) }
    var revision by remember { mutableIntStateOf(0) }
    var active by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    val window = LocalActivity.current?.window
    val bars = remember(window, view) { window?.let { WindowCompat.getInsetsController(it, view) } }

    fun wake() { clock.doubleValue = sneakerParallaxShopNow(); revision++ }
    fun haptic() { if (!reduced) view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK) }
    fun leave() { if (onBack != null) onBack() else backDispatcher?.onBackPressed() }
    fun act(action: SneakerParallaxShopAction): Boolean {
        if (!active || previewTime != null) return false
        val result = driver.action(action, sneakerParallaxShopNow(), autoPlay, reduced)
        if (result.leave) leave()
        if (result.haptic) haptic()
        if (result.handled) wake()
        return result.handled
    }

    DisposableEffect(bars) {
        val oldStatus = bars?.isAppearanceLightStatusBars
        val oldNavigation = bars?.isAppearanceLightNavigationBars
        bars?.show(WindowInsetsCompat.Type.statusBars())
        onDispose {
            if (oldStatus != null) bars?.isAppearanceLightStatusBars = oldStatus
            if (oldNavigation != null) bars?.isAppearanceLightNavigationBars = oldNavigation
        }
    }
    DisposableEffect(lifecycle, driver) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                active = false
                driver.reset(sneakerParallaxShopNow())
                wake()
            } else if (event == Lifecycle.Event.ON_RESUME) {
                driver.reset(sneakerParallaxShopNow())
                active = true
                wake()
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); driver.reset(sneakerParallaxShopNow()) }
    }
    LaunchedEffect(autoPlay, reduced, previewTime) {
        driver.reset(sneakerParallaxShopNow())
        wake()
    }
    LaunchedEffect(autoPlay, reduced, previewTime, revision, active) {
        if (!active || reduced || previewTime != null) return@LaunchedEffect
        while (isActive && (autoPlay || driver.needsFrames(sneakerParallaxShopNow()))) {
            withFrameNanos {
                val now = sneakerParallaxShopNow()
                driver.complete(now, autoPlay)
                clock.doubleValue = now
            }
        }
    }

    Canvas(
        modifier.fillMaxSize().background(Color(0xFFF7F0EE))
            .semantics {
                contentDescription = "Sneaker shop. Swipe to browse shoes or open a product."
                customActions = buildList {
                    add(CustomAccessibilityAction("Next shoe") { act(SneakerParallaxShopAction.Next) })
                    add(CustomAccessibilityAction("Previous shoe") { act(SneakerParallaxShopAction.Previous) })
                    add(CustomAccessibilityAction("Open product details") { act(SneakerParallaxShopAction.Open) })
                    add(CustomAccessibilityAction("Back") { act(SneakerParallaxShopAction.Back) })
                    if (semanticPhase.intValue == 0) {
                        add(CustomAccessibilityAction("Browse shoes") { act(SneakerParallaxShopAction.Search) })
                        for ((i, name) in arrayOf("All", "Court", "Run", "Trail", "Daily").withIndex()) {
                            add(CustomAccessibilityAction("$name filter") { act(SneakerParallaxShopAction.Filter(i)) })
                        }
                    } else if (semanticPhase.intValue == 2) {
                        add(CustomAccessibilityAction("Favorite product") { act(SneakerParallaxShopAction.Favorite) })
                        add(CustomAccessibilityAction("More product details") { act(SneakerParallaxShopAction.More) })
                        for ((i, name) in arrayOf("Original", "Moss", "Night").withIndex()) {
                            add(CustomAccessibilityAction("$name colorway") { act(SneakerParallaxShopAction.Colorway(i)) })
                        }
                        for (i in 0..7) if (i != 5 && i != 7) {
                            add(CustomAccessibilityAction("Size UK ${i + 6}") { act(SneakerParallaxShopAction.Size(i)) })
                        }
                        add(CustomAccessibilityAction("Add to Bag") { act(SneakerParallaxShopAction.Bag) })
                    }
                }
            }
            .pointerInput(reduced, previewTime, active, autoPlay) {
                if (previewTime != null || !active) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val scale = size.width / 393.0
                    val top = max(0.0, (size.height - 852 * scale) / 2)
                    val origin = down.position
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, origin)
                    var last = origin
                    var normal = false
                    var pan = false
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (change.isConsumed) break
                            last = change.position
                            tracker.addPosition(change.uptimeMillis, change.position)
                            val delta = last - origin
                            if (!reduced && !pan && hypot(delta.x.toDouble(), delta.y.toDouble()) > 6 * scale) {
                                driver.beginPan(sneakerParallaxShopNow(), autoPlay)
                                pan = true
                                wake()
                            }
                            if (pan) {
                                driver.movePan(delta.x / scale, delta.y / scale, sneakerParallaxShopNow())
                                change.consume()
                            }
                            if (!change.pressed) { normal = true; break }
                        }
                    } finally {
                        if (pan) {
                            val velocity = tracker.calculateVelocity().x / scale
                            // UIKit's predicted remaining travel is approximated by a 325 ms decay horizon.
                            // The same final 18% projection and analytical snap as Swift then apply.
                            val projected = if (normal) velocity * 0.325 * 0.18 else 0.0
                            val vertical = if (normal) (last.y - origin.y) / scale else 0.0
                            if (driver.finishPan(projected, vertical, sneakerParallaxShopNow(), autoPlay, reduced) && normal) haptic()
                            wake()
                        } else if (normal && hypot((last.x - origin.x).toDouble(), (last.y - origin.y).toDouble()) < 6 * scale) {
                            val x = last.x / scale
                            val y = (last.y - top) / scale
                            val action = driver.hit(x, y, sneakerParallaxShopNow(), autoPlay, reduced)
                            if (action != null) act(action)
                        }
                    }
                }
            },
    ) {
        val frame = driver.frame(clock.doubleValue, autoPlay, reduced, previewTime)
        driver.rendered = frame
        val semanticTarget = if (frame.e < 0.05) 0 else if (frame.e > 0.96) 2 else 1
        // Semantics change only at the two visibility thresholds; no per-frame recomposition.
        if (semanticPhase.intValue != semanticTarget) semanticPhase.intValue = semanticTarget
        // Only the two threshold crossings change status-bar appearance, not every frame.
        val lightStatus = frame.e <= 0.5
        if (driver.lightStatus != lightStatus) {
            bars?.isAppearanceLightStatusBars = lightStatus
            bars?.isAppearanceLightNavigationBars = true
            driver.lightStatus = lightStatus
        }
        val canvas = drawContext.canvas.nativeCanvas
        val scale = size.width / 393f
        val top = max(0f, (size.height - 852f * scale) / 2f)
        canvas.save()
        canvas.translate(0f, top)
        canvas.scale(scale, scale)
        canvas.clipRect(0f, 0f, 393f, 852f)
        painter.draw(canvas, frame, driver)
        canvas.restore()
    }
}

private sealed interface SneakerParallaxShopAction {
    data object Next : SneakerParallaxShopAction
    data object Previous : SneakerParallaxShopAction
    data object Open : SneakerParallaxShopAction
    data object Back : SneakerParallaxShopAction
    data object Favorite : SneakerParallaxShopAction
    data object Search : SneakerParallaxShopAction
    data object More : SneakerParallaxShopAction
    data object Bag : SneakerParallaxShopAction
    data class Product(val index: Int) : SneakerParallaxShopAction
    data class Filter(val index: Int) : SneakerParallaxShopAction
    data class Colorway(val index: Int) : SneakerParallaxShopAction
    data class Size(val index: Int) : SneakerParallaxShopAction
}

private data class SneakerParallaxShopResult(val handled: Boolean = true, val haptic: Boolean = false, val leave: Boolean = false)

private class SneakerParallaxShopDriver {
    private var epoch = sneakerParallaxShopNow()
    private var manualFrom: SneakerParallaxShopFrame? = null
    private var manualTarget = SneakerParallaxShopFrame()
    private var eventDate = epoch
    private var eventDuration = 0.42
    private var eventMoving = false
    private var lastTouch = epoch
    private var held = false
    private var panOrigin = SneakerParallaxShopFrame()
    var favorite = false
        private set
    var sizeIndex = 3
        private set
    var filter = 0
        private set
    var added = false
        private set
    var more = false
        private set
    var colorway = 0
        private set
    private var reducedDetail = 2
    private var reducedExpanded = true
    var rendered = SneakerParallaxShopFrame()
    var lightStatus: Boolean? = null

    fun frame(now: Double, autoPlay: Boolean, reduced: Boolean, previewTime: Double?): SneakerParallaxShopFrame {
        if (reduced) return SneakerParallaxShopFrame(reducedDetail.toDouble(), if (reducedExpanded) 1.0 else 0.0, reducedDetail)
        val from = manualFrom
        if (from != null) {
            val u = if (eventDuration == 0.0) 1.0 else SneakerParallaxShopMath.spring((now - eventDate) / eventDuration)
            val current = from.mixed(manualTarget, u)
            val recovery = if (held || !autoPlay) 0.0 else SneakerParallaxShopMath.smooth((now - lastTouch - 2) / 0.55)
            return current.copy(q = current.q * (1 - recovery), e = current.e * (1 - recovery))
        }
        return SneakerParallaxShopMath.automatic(previewTime ?: if (autoPlay) now - epoch else 0.0)
    }

    fun needsFrames(now: Double) = held || (eventMoving && now - eventDate < eventDuration + 0.02)

    fun reset(now: Double) {
        held = false
        eventMoving = false
        manualFrom = null
        epoch = now
        favorite = false
        sizeIndex = 3
        filter = 0
        added = false
        more = false
        colorway = 0
        reducedDetail = 2
        reducedExpanded = true
    }

    fun complete(now: Double, autoPlay: Boolean) {
        if (manualFrom == null || held) return
        if (autoPlay && now - lastTouch > 2.55) reset(now)
        else if (!autoPlay && now - eventDate > eventDuration + 0.02) eventMoving = false
    }

    private fun interact(target: SneakerParallaxShopFrame, now: Double, autoPlay: Boolean, reduced: Boolean, duration: Double = 0.42) {
        val current = frame(now, autoPlay, reduced, null)
        if (reduced) {
            reducedDetail = (if (target.e > 0.5) target.detail else SneakerParallaxShopMath.rounded(target.q).toInt()).coerceIn(0, 2)
            reducedExpanded = target.e > 0.5
        }
        held = false
        manualFrom = current
        manualTarget = target
        eventDate = now
        eventDuration = duration
        eventMoving = duration > 0
        lastTouch = now
    }

    private fun open(index: Int, now: Double, autoPlay: Boolean, reduced: Boolean) {
        val i = index.coerceIn(0, 2)
        if (i != frame(now, autoPlay, reduced, null).detail) {
            colorway = 0; favorite = false; more = false; added = false
        }
        reducedDetail = i
        interact(SneakerParallaxShopFrame(i.toDouble(), 1.0, i), now, autoPlay, reduced, 0.55)
    }

    fun action(action: SneakerParallaxShopAction, now: Double, autoPlay: Boolean, reduced: Boolean): SneakerParallaxShopResult {
        val f = frame(now, autoPlay, reduced, null)
        fun touchControl() = interact(frame(now, autoPlay, reduced, null), now, autoPlay, reduced, 0.0)
        when (action) {
            SneakerParallaxShopAction.Next, SneakerParallaxShopAction.Previous -> {
                val direction = if (action == SneakerParallaxShopAction.Next) 1 else -1
                val index = (SneakerParallaxShopMath.rounded(f.q).toInt() + direction + 3) % 3
                if (reduced) reducedDetail = index
                interact(SneakerParallaxShopFrame(index.toDouble(), f.e, index), now, autoPlay, reduced)
            }
            SneakerParallaxShopAction.Open -> { open(SneakerParallaxShopMath.rounded(f.q).toInt(), now, autoPlay, reduced); return SneakerParallaxShopResult(haptic = true) }
            SneakerParallaxShopAction.Back -> {
                if (f.e > 0.5) interact(SneakerParallaxShopFrame(f.detail.toDouble(), 0.0, f.detail), now, autoPlay, reduced)
                else return SneakerParallaxShopResult(leave = true)
            }
            SneakerParallaxShopAction.Favorite -> {
                if (f.e <= 0.5) return SneakerParallaxShopResult(handled = false)
                favorite = !favorite; touchControl()
            }
            SneakerParallaxShopAction.Search -> {
                filter = (filter + 1) % 5
                interact(SneakerParallaxShopFrame((filter % 3).toDouble()), now, autoPlay, reduced)
            }
            SneakerParallaxShopAction.More -> {
                if (f.e <= 0.96) return SneakerParallaxShopResult(handled = false)
                more = !more; touchControl()
            }
            SneakerParallaxShopAction.Bag -> {
                if (f.e <= 0.96) return SneakerParallaxShopResult(handled = false)
                added = true; touchControl(); return SneakerParallaxShopResult(haptic = true)
            }
            is SneakerParallaxShopAction.Product -> {
                if (f.e >= 0.05) return SneakerParallaxShopResult(handled = false)
                if (abs(f.q - action.index) < 0.05) {
                    open(action.index, now, autoPlay, reduced)
                    return SneakerParallaxShopResult(haptic = true)
                }
                interact(SneakerParallaxShopFrame(action.index.toDouble()), now, autoPlay, reduced)
            }
            is SneakerParallaxShopAction.Filter -> {
                if (f.e >= 0.05) return SneakerParallaxShopResult(handled = false)
                filter = action.index
                interact(SneakerParallaxShopFrame((filter % 3).toDouble()), now, autoPlay, reduced)
            }
            is SneakerParallaxShopAction.Colorway -> {
                if (f.e <= 0.96) return SneakerParallaxShopResult(handled = false)
                colorway = action.index.coerceIn(0, 2); touchControl()
            }
            is SneakerParallaxShopAction.Size -> {
                if (f.e <= 0.96 || action.index == 5 || action.index == 7) return SneakerParallaxShopResult(handled = false)
                sizeIndex = action.index.coerceIn(0, 7); touchControl()
            }
        }
        return SneakerParallaxShopResult()
    }

    fun beginPan(now: Double, autoPlay: Boolean) {
        val current = frame(now, autoPlay, false, null)
        panOrigin = current
        manualFrom = current
        manualTarget = current
        held = true
    }

    fun movePan(dx: Double, dy: Double, now: Double) {
        var f = panOrigin
        if (panOrigin.e > 0.5 && abs(dy) > abs(dx)) {
            f = f.copy(e = SneakerParallaxShopMath.clamp(panOrigin.e - dy / 280))
        } else if (panOrigin.e < 0.5) {
            val proposed = panOrigin.q - dx / 349
            val edge = SneakerParallaxShopMath.clamp(proposed, 0.0, 2.0)
            val over = (proposed - edge) * 349
            f = f.copy(q = edge + (over / (1 + abs(over) / 120)) / 349)
        }
        manualTarget = f
        eventDuration = 0.0
        eventDate = now
        lastTouch = now
    }

    fun finishPan(projected: Double, vertical: Double, now: Double, autoPlay: Boolean, reduced: Boolean): Boolean {
        if (!held) return false
        var f = manualTarget
        f = if (panOrigin.e > 0.5) f.copy(e = if (vertical > 80) 0.0 else 1.0, q = f.detail.toDouble())
        else f.copy(q = SneakerParallaxShopMath.clamp(SneakerParallaxShopMath.rounded(f.q - projected / 349), 0.0, 2.0))
        val changed = SneakerParallaxShopMath.rounded(f.q).toInt() != SneakerParallaxShopMath.rounded(panOrigin.q).toInt() || (f.e > 0.5) != (panOrigin.e > 0.5)
        interact(f, now, autoPlay, reduced)
        return changed
    }

    fun hit(x: Double, y: Double, now: Double, autoPlay: Boolean, reduced: Boolean): SneakerParallaxShopAction? {
        val f = frame(now, autoPlay, reduced, null)
        fun inside(left: Double, top: Double, width: Double, height: Double) = x >= left && x <= left + width && y >= top && y <= top + height
        if (inside(4.0, 54.0, 44.0, 44.0)) return SneakerParallaxShopAction.Back
        if (inside(338.0, 54.0, 44.0, 44.0)) return if (f.e > 0.5) SneakerParallaxShopAction.Favorite else SneakerParallaxShopAction.Search
        if (f.e > 0.96) {
            if (inside(321.0, 409.0, 50.0, 44.0)) return SneakerParallaxShopAction.More
            for (i in 0..2) if (inside(22.0 + 99 * i, 469.0, 88.0, 77.0)) return SneakerParallaxShopAction.Colorway(i)
            for (i in 0..7) if (i != 5 && i != 7 && inside(27.0 + 90.5 * (i % 4), 627.0 + 62 * (i / 4), 73.0, 44.0)) return SneakerParallaxShopAction.Size(i)
            if (inside(27.0, 780.0, 344.0, 49.0)) return SneakerParallaxShopAction.Bag
        }
        if (f.e < 0.05) {
            var left = 15.0
            for ((i, width) in doubleArrayOf(61.0, 82.0, 70.0, 78.0, 74.0).withIndex()) {
                if (inside(left, 140.0, width, 44.0)) return SneakerParallaxShopAction.Filter(i)
                left += width + 9
            }
            val order = (0..2).filter { it != f.detail } + f.detail
            for (i in order.reversed()) if (inside(18 + 349 * (i - f.q), 205.0, 282.0, 325.0)) return SneakerParallaxShopAction.Product(i)
        }
        return null
    }
}

private fun sneakerParallaxShopNow() = System.nanoTime() / 1_000_000_000.0

private class SneakerParallaxShopPainter(context: Context) {
    private val images = arrayOf("red", "yellow", "gray", "blue", "olive", "black").map { name ->
        context.assets.open("sneakerparallaxshop/sneakerparallaxshop_sneaker-$name.png").use { BitmapFactory.decodeStream(it) }
    }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val fonts = (400..700 step 100).associateWith { weight ->
        if (Build.VERSION.SDK_INT >= 28) Typeface.create(Typeface.create("sans-serif", Typeface.NORMAL), weight, false)
        else Typeface.create(if (weight == 500) "sans-serif-medium" else "sans-serif", if (weight >= 600) Typeface.BOLD else Typeface.NORMAL)
    }
    private val path = Path()
    private val shadow = Bitmap.createBitmap(260, 90, Bitmap.Config.ARGB_8888).also { bitmap ->
        val c = NativeCanvas(bitmap)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = argb(0x3C3330, 0.15)
        p.maskFilter = BlurMaskFilter(12f, BlurMaskFilter.Blur.NORMAL)
        c.drawOval(RectF(36f, 33f, 224f, 57f), p)
        p.color = argb(0x3C3330, 0.16)
        p.maskFilter = BlurMaskFilter(3f, BlurMaskFilter.Blur.NORMAL)
        c.drawOval(RectF(56f, 41f, 204f, 49f), p)
    }
    private val colors = intArrayOf(0xE24C4C, 0xFAB960, 0x4D80F4)
    private val catalogSizes = doubleArrayOf(254.06, 256.54, 293.08)
    private val catalogX = doubleArrayOf(223.8, 218.4, 197.8)
    private val catalogY = doubleArrayOf(403.0, 410.1, 391.8)
    private val catalogAngles = doubleArrayOf(8.4, 3.5, -31.0)
    private val detailX = doubleArrayOf(187.66, 185.09, 175.2)
    private val detailY = doubleArrayOf(179.67, 169.01, 172.3)
    private val detailAngles = doubleArrayOf(29.8, 24.9, -9.6)

    companion object {
        private fun argb(hex: Int, alpha: Double = 1.0) = NativeColor.argb((alpha.coerceIn(0.0, 1.0) * 255).toInt(), hex shr 16 and 255, hex shr 8 and 255, hex and 255)
    }

    private fun basic(color: Int, alpha: Double = 1.0, stroke: Double? = null) {
        paint.reset()
        paint.isAntiAlias = true
        paint.isFilterBitmap = true
        paint.color = argb(color, alpha)
        paint.style = if (stroke == null) Paint.Style.FILL else Paint.Style.STROKE
        paint.strokeWidth = (stroke ?: 0.0).toFloat()
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
    }

    private fun text(c: NativeCanvas, value: String, size: Double, weight: Int, hex: Int, x: Double, centerY: Double, alpha: Double = 1.0, align: Paint.Align = Paint.Align.LEFT, tracking: Double = 0.0) {
        basic(hex, alpha)
        paint.textSize = size.toFloat()
        paint.typeface = fonts[weight]
        paint.textAlign = align
        val baseline = (centerY - (paint.fontMetrics.ascent + paint.fontMetrics.descent) / 2).toFloat()
        if (tracking == 0.0) c.drawText(value, x.toFloat(), baseline, paint)
        else {
            val advances = FloatArray(value.length)
            paint.getTextWidths(value, advances)
            val width = advances.sum() + tracking * max(0, value.length - 1)
            var cursor = x - when (align) { Paint.Align.CENTER -> width / 2; Paint.Align.RIGHT -> width; else -> 0.0 }
            paint.textAlign = Paint.Align.LEFT
            for (i in value.indices) { c.drawText(value, i, i + 1, cursor.toFloat(), baseline, paint); cursor += advances[i] + tracking }
        }
    }

    private fun multiline(c: NativeCanvas, value: String, size: Double, weight: Int, hex: Int, x: Double, top: Double, spacing: Double, alpha: Double) {
        basic(hex, alpha)
        paint.textSize = size.toFloat()
        paint.typeface = fonts[weight]
        // The detail price immediately before this block is right-aligned.
        // Set multiline alignment explicitly instead of inheriting a reused Paint's text state.
        paint.textAlign = Paint.Align.LEFT
        val metrics = paint.fontMetrics
        var baseline = top - metrics.ascent
        val lineHeight = metrics.descent - metrics.ascent + spacing
        for (line in value.split('\n')) {
            c.drawText(line, x.toFloat(), baseline.toFloat(), paint)
            baseline += lineHeight
        }
    }

    private fun roundRect(c: NativeCanvas, x: Double, y: Double, w: Double, h: Double, r: Double, color: Int, alpha: Double = 1.0, stroke: Double? = null) {
        basic(color, alpha, stroke)
        c.drawRoundRect(RectF(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat()), r.toFloat(), r.toFloat(), paint)
    }

    private fun image(c: NativeCanvas, index: Int, centerX: Double, centerY: Double, width: Double, height: Double, angle: Double = 0.0, alpha: Double = 1.0) {
        if (alpha <= 0) return
        val bitmap = images.getOrNull(index) ?: return
        val scale = min(width / bitmap.width, height / bitmap.height)
        val w = bitmap.width * scale
        val h = bitmap.height * scale
        basic(0xFFFFFF, alpha)
        val save = c.save()
        c.rotate(angle.toFloat(), centerX.toFloat(), centerY.toFloat())
        c.drawBitmap(bitmap, null, RectF((centerX - w / 2).toFloat(), (centerY - h / 2).toFloat(), (centerX + w / 2).toFloat(), (centerY + h / 2).toFloat()), paint)
        c.restoreToCount(save)
    }

    fun draw(c: NativeCanvas, f: SneakerParallaxShopFrame, state: SneakerParallaxShopDriver) {
        c.drawColor(argb(0xF7F0EE))
        val chrome = 1 - f.e
        if (chrome > 0) catalog(c, state, chrome)
        val order = (0..2).filter { it != f.detail } + f.detail
        for (i in order) hero(c, i, f, state)
        val details = SneakerParallaxShopMath.smooth((f.e - 0.35) / 0.65)
        if (details > 0) {
            c.save()
            c.translate(0f, (12 * (1 - details)).toFloat())
            detail(c, f.detail.coerceIn(0, 2), state, details)
            c.restore()
        }
        navigation(c, f.e, state.favorite)
    }

    private fun catalog(c: NativeCanvas, state: SneakerParallaxShopDriver, alpha: Double) {
        text(c, "Shoes", 27.0, 600, 0x202A33, 14.0, 113.0, alpha)
        val labels = arrayOf("All", "Court", "Run", "Trail", "Daily")
        var left = 15.0
        for ((i, width) in doubleArrayOf(61.0, 82.0, 70.0, 78.0, 74.0).withIndex()) {
            if (state.filter == i) roundRect(c, left, 145.0, width, 34.0, 17.0, 0x202A33, alpha)
            else roundRect(c, left, 145.0, width, 34.0, 17.0, 0xE5DFDC, alpha, 1.0)
            text(c, labels[i], 14.0, 500, if (state.filter == i) 0xFFFFFF else 0xA5A09D, left + width / 2, 162.0, alpha, Paint.Align.CENTER)
            left += width + 9
        }
        text(c, "243 OPTIONS", 12.0, 400, 0xA5A09D, 78.0, 590.0, alpha, Paint.Align.CENTER, 1.0)
        image(c, 2, 85.5, 665.0, 131.0, 90.0, alpha = alpha)
        image(c, 3, 85.5, 780.0, 131.0, 90.0, alpha = alpha)
        multiline(c, "Cinder Pace\nLow Runner", 17.0, 500, 0x202A33, 182.0, 636.0, 1.0, alpha)
        text(c, "₹12,797", 14.0, 400, 0xA5A09D, 182.0, 692.5, alpha)
        text(c, "Lowtide Mesh", 17.0, 500, 0x202A33, 182.0, 763.0, alpha)
        text(c, "₹9,995", 14.0, 400, 0xA5A09D, 182.0, 787.5, alpha)
    }

    private fun surface(c: NativeCanvas, x: Double, e: Double, color: Int, alpha: Double) {
        val fast = SneakerParallaxShopMath.clamp(2.29 * e)
        val px = x * (1 - fast)
        val y = 205 - 473.9 * e
        val w = 282 + 111 * fast
        val r = 19 * (1 - fast)
        val right = 530 * (1 - e) + 240 * e
        val left = 530 * (1 - e) + 125 * e
        path.rewind()
        path.moveTo((px + r).toFloat(), y.toFloat())
        path.lineTo((px + w - r).toFloat(), y.toFloat())
        path.quadTo((px + w).toFloat(), y.toFloat(), (px + w).toFloat(), (y + r).toFloat())
        path.lineTo((px + w).toFloat(), (right - r).toFloat())
        path.quadTo((px + w).toFloat(), right.toFloat(), (px + w - r).toFloat(), right.toFloat())
        path.cubicTo(((px + w * 0.72) * (1 - e) + 289 * e).toFloat(), (530 * (1 - e) + 300 * e).toFloat(),
            ((px + w * 0.25) * (1 - e) + 103 * e).toFloat(), (530 * (1 - e) + 287 * e).toFloat(), (px + r).toFloat(), left.toFloat())
        path.quadTo(px.toFloat(), left.toFloat(), px.toFloat(), (left - r).toFloat())
        path.lineTo(px.toFloat(), (y + r).toFloat())
        path.quadTo(px.toFloat(), y.toFloat(), (px + r).toFloat(), y.toFloat())
        path.close()
        basic(color, alpha)
        c.drawPath(path, paint)
    }

    private fun hero(c: NativeCanvas, index: Int, f: SneakerParallaxShopFrame, state: SneakerParallaxShopDriver) {
        val expanded = if (index == f.detail) f.e else 0.0
        val alpha = if (index == f.detail) 1.0 else 1 - f.e
        if (alpha <= 0) return
        val panelX = 18 + 349 * (index - f.q)
        surface(c, panelX, expanded, colors[index], alpha)
        val labelAlpha = (1 - expanded) * alpha
        val labelX = 45 + 349 * (index - f.q)
        text(c, Names[index], 25.0, 600, 0xFFFFFF, labelX, 248.5 - 191 * expanded, labelAlpha)
        text(c, Prices[index], 19.0, 400, 0xFFFFFF, labelX, 281.5 - 191 * expanded, labelAlpha * 0.64)
        basic(0xFFFFFF, labelAlpha * 0.22)
        c.drawRect(labelX.toFloat(), (301 - 191 * expanded).toFloat(), (labelX + 0.7).toFloat(), (480 - 191 * expanded).toFloat(), paint)
        val shoeX = 72 + 349 * (index - f.q) * 0.84
        val centerX = (catalogX[index] + 349 * (index - f.q) * 0.84) * (1 - expanded) + detailX[index] * expanded
        val centerY = catalogY[index] * (1 - expanded) + detailY[index] * expanded
        val imageSize = catalogSizes[index] + (294.57 - catalogSizes[index]) * expanded
        val shadowX = (shoeX + 163) * (1 - expanded) + 183 * expanded
        val shadowY = 504 * (1 - expanded) + 246 * expanded
        basic(0xFFFFFF, alpha)
        c.drawBitmap(shadow, (shadowX - 130).toFloat(), (shadowY - 45).toFloat(), paint)
        val angle = (catalogAngles[index] + 11 * sin(PI * f.q)) * (1 - expanded) + detailAngles[index] * expanded
        val changing = index == f.detail && state.colorway > 0
        image(c, index, centerX, centerY, imageSize, imageSize, angle, alpha * if (changing) 1 - expanded else 1.0)
        if (changing) image(c, state.colorway + 3, centerX, centerY, imageSize, imageSize, angle, alpha * expanded)
    }

    private fun detail(c: NativeCanvas, item: Int, state: SneakerParallaxShopDriver, alpha: Double) {
        text(c, Names[item], 24.0, 700, 0x202A33, 27.0, 340.5, alpha)
        text(c, Prices[item], 18.0, 600, 0x202A33, 371.0, 341.5, alpha, Paint.Align.RIGHT)
        multiline(c, "Built for the moments that move you most.\nLight on your feet, ready for every turn.\nSoft landings. A little more possibility.", 16.5, 400, 0xA5A09D, 27.0, 372.0, 3.5, alpha)
        text(c, if (state.more) "LESS" else "MORE", 11.0, 600, 0x202A33, 346.0, 431.0, alpha, Paint.Align.CENTER)
        if (state.more) text(c, "Choose your pace. Make it yours.", 13.0, 400, 0xA5A09D, 160.0, 452.0, alpha, Paint.Align.CENTER)
        for (slot in 0..2) {
            val artwork = if (slot == 0) item else slot + 3
            image(c, artwork, 66.0 + slot * 99, 507.5, 88.0, 77.0, if (slot == 0) detailAngles[item] else 0.0, alpha)
            if (state.colorway == slot) roundRect(c, 22.0 + slot * 99, 469.0, 88.0, 77.0, 18.0, 0x202A33, alpha, 1.6)
        }
        text(c, "Select Size", 16.0, 600, 0x202A33, 27.0, 602.0, alpha)
        for (index in 0..7) {
            val x = 27.0 + index % 4 * 90.5
            val y = 628.0 + index / 4 * 62
            val disabled = index == 5 || index == 7
            roundRect(c, x, y, 73.0, 42.0, 9.0, 0xFFFCFA, alpha)
            if (state.sizeIndex == index) roundRect(c, x, y, 73.0, 42.0, 9.0, 0x202A33, alpha, 1.5)
            text(c, "UK ${index + 6}", 14.0, 400, 0x202A33, x + 36.5, y + 21, alpha * if (disabled) 0.3 else 1.0, Paint.Align.CENTER)
        }
        roundRect(c, 27.0, 780.0, 344.0, 49.0, 13.0, 0x202A33, alpha)
        text(c, if (state.added) "Added to Bag" else "Add to Bag", 16.0, 500, 0xFFFFFF, 199.0, 804.5, alpha, Paint.Align.CENTER)
    }

    private fun navigation(c: NativeCanvas, expansion: Double, favorite: Boolean) {
        val tint = if (expansion > 0.5) 0xFFFFFF else 0x202A33
        basic(tint, stroke = 1.25)
        path.rewind(); path.moveTo(35f, 76f); path.lineTo(17f, 76f); path.moveTo(25f, 68f); path.lineTo(17f, 76f); path.lineTo(25f, 84f)
        c.drawPath(path, paint)
        if (expansion > 0.5) {
            path.rewind()
            path.moveTo(360f, 84f)
            path.cubicTo(357f, 81f, 350f, 77f, 350f, 72f)
            path.cubicTo(350f, 66f, 357f, 65f, 360f, 70f)
            path.cubicTo(363f, 65f, 370f, 66f, 370f, 72f)
            path.cubicTo(370f, 77f, 363f, 81f, 360f, 84f)
            path.close()
            basic(tint, stroke = if (favorite) null else 1.2)
            c.drawPath(path, paint)
        } else {
            c.drawCircle(358f, 74f, 7f, paint)
            c.drawLine(363f, 79f, 369f, 85f, paint)
        }
    }
}

@Composable
private fun rememberSneakerParallaxShopReduced(): Boolean {
    val resolver = LocalContext.current.contentResolver
    fun read() = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    var reduced by remember(resolver) { mutableStateOf(read()) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { reduced = read() }
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        reduced = read()
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return reduced
}
