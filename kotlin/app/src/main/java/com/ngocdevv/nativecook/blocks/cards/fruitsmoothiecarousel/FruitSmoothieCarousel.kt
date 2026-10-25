package com.ngocdevv.nativecook.blocks.cards.fruitsmoothiecarousel

import android.content.Context
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as NativeCanvas
import android.graphics.Color as NativeColor
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private const val LOOP = 8.0
private val FruitSmoothieCarouselFlavors = arrayOf("Strawberry", "Blueberry", "Orange", "Kiwi", "Apricot")
private val FruitSmoothieCarouselTops = intArrayOf(0xEE91B0, 0xA8B8F1, 0xFFE690, 0xDAF78B, 0xF5D5BE)
private val FruitSmoothieCarouselBottoms = intArrayOf(0xD26388, 0x6F83D1, 0xF2B751, 0xB7DF60, 0xE2A18F)

private object FruitSmoothieCarouselMath {
    private val tabOrigins = doubleArrayOf(63.0, 196.0, 315.0, 420.0, 505.0)
    private val tabWidths = doubleArrayOf(106.0, 92.0, 78.0, 58.0, 70.0)
    fun clamp(x: Double, low: Double = 0.0, high: Double = 1.0) = x.coerceIn(low, high)
    fun smooth(x: Double): Double { val u = clamp(x); return u * u * (3 - 2 * u) }
    fun wrap(index: Int) = (index % 5 + 5) % 5
    fun rounded(x: Double) = if (x >= 0) floor(x + 0.5) else ceil(x - 0.5)
    fun automatic(seconds: Double): Double {
        val t = max(0.0, seconds) % LOOP
        val slot = floor(t / 1.6)
        return slot + smooth((t - slot * 1.6 - 1) / 0.6)
    }
    fun spring(progress: Double): Double {
        if (progress >= 1) return 1.0
        val u = max(0.0, progress); val a = 5.15; val b = 3.59
        val end = 1 - exp(-a) * (cos(b) + a / b * sin(b))
        return (1 - exp(-a * u) * (cos(b * u) + a / b * sin(b * u))) / end
    }
    fun tabX(index: Int): Double {
        val cycle = floor(index.toDouble() / 5)
        return tabOrigins[wrap(index)] + cycle * 575
    }
    fun tabShift(selection: Double): Double {
        val index = floor(selection).toInt(); val fraction = selection - floor(selection)
        return tabX(index) + (tabX(index + 1) - tabX(index)) * fraction - 63
    }
    fun tabWidth(index: Int) = tabWidths[wrap(index)]
    fun mixedColor(from: Int, to: Int, amount: Double): Int {
        val t = clamp(amount)
        fun channel(shift: Int): Int {
            val a = (from shr shift) and 255; val b = (to shr shift) and 255
            return (a + (b - a) * t).roundToInt()
        }
        return NativeColor.rgb(channel(16), channel(8), channel(0))
    }
}

/** Five unequal cards exchange their matching fruit layers outside the colored panels. */
@Composable
fun FruitSmoothieCarousel(
    autoPlay: Boolean = false,
    previewTime: Double? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val reduced = rememberFruitSmoothieCarouselReduced()
    val driver = remember { FruitSmoothieCarouselDriver() }
    val painter = remember(context.applicationContext) { FruitSmoothieCarouselPainter(context.applicationContext) }
    val clock = remember { mutableDoubleStateOf(fruitSmoothieCarouselNow()) }
    val semanticSelection = remember { mutableIntStateOf(0) }
    var revision by remember { mutableIntStateOf(0) }
    var chooser by remember { mutableStateOf(false) }
    var active by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    val preview = previewTime?.takeIf { it.isFinite() }?.coerceAtLeast(0.0)
    FruitSmoothieCarouselSystemBars()

    fun wake() { clock.doubleValue = fruitSmoothieCarouselNow(); revision++ }
    fun light() { if (!reduced) view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK) }
    fun choose(index: Int): Boolean {
        if (!active || preview != null) return false
        val changed = driver.choose(index, fruitSmoothieCarouselNow(), autoPlay, reduced)
        chooser = false
        if (changed) light()
        wake(); return true
    }
    fun adjacent(direction: Int): Boolean {
        val q = driver.selection(fruitSmoothieCarouselNow(), autoPlay, reduced, preview)
        return choose(FruitSmoothieCarouselMath.wrap(FruitSmoothieCarouselMath.rounded(q).toInt() + direction))
    }
    fun presentChooser(): Boolean {
        if (!active || preview != null) return false
        driver.presentChooser(fruitSmoothieCarouselNow(), autoPlay, reduced)
        chooser = true; wake(); return true
    }
    fun cancelChooser() {
        driver.dismissChooser(fruitSmoothieCarouselNow(), autoPlay, reduced)
        chooser = false; wake()
    }

    DisposableEffect(lifecycle, driver) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                active = false; driver.reset(fruitSmoothieCarouselNow()); chooser = false; wake()
            } else if (event == Lifecycle.Event.ON_RESUME) {
                driver.reset(fruitSmoothieCarouselNow()); chooser = false; active = true; wake()
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); driver.reset(fruitSmoothieCarouselNow()) }
    }
    LaunchedEffect(autoPlay, reduced, previewTime) {
        driver.reset(fruitSmoothieCarouselNow()); chooser = false; wake()
    }
    LaunchedEffect(autoPlay, reduced, preview, revision, active, chooser) {
        if (!active || preview != null || chooser || driver.held) return@LaunchedEffect
        if (reduced) return@LaunchedEffect
        while (isActive && !driver.held && !driver.choosing && (autoPlay || driver.eventMoving)) {
            withFrameNanos {
                val now = fruitSmoothieCarouselNow()
                driver.complete(now, autoPlay)
                clock.doubleValue = now
            }
        }
    }

    Canvas(modifier.fillMaxSize().background(Color(0xFFFCFAFC))
        .semantics {
            contentDescription = "${FruitSmoothieCarouselFlavors[semanticSelection.intValue]} smoothie"
            stateDescription = "${FruitSmoothieCarouselFlavors[semanticSelection.intValue]}, ${semanticSelection.intValue + 1} of 5"
            progressBarRangeInfo = ProgressBarRangeInfo(semanticSelection.intValue.toFloat(), 0f..4f, 3)
            setProgress { choose(it.roundToInt().coerceIn(0, 4)) }
            customActions = buildList {
                add(CustomAccessibilityAction("Previous flavor") { adjacent(-1) })
                add(CustomAccessibilityAction("Next flavor") { adjacent(1) })
                add(CustomAccessibilityAction("Choose a flavor") { presentChooser() })
                add(CustomAccessibilityAction("Find a flavor") { presentChooser() })
                for ((index, name) in FruitSmoothieCarouselFlavors.withIndex()) {
                    add(CustomAccessibilityAction("$name${if (semanticSelection.intValue == index) ", selected" else ""}") { choose(index) })
                }
            }
        }
        .pointerInput(autoPlay, reduced, preview, active, chooser) {
            if (!active || preview != null || chooser) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val scale = size.width / 393.0
                val top = max(0.0, (size.height - 852 * scale) / 2)
                val origin = down.position
                var last = origin
                var normal = false
                var pan = false
                val tracker = VelocityTracker()
                tracker.addPosition(down.uptimeMillis, origin)
                try {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.isConsumed) break
                        last = change.position
                        tracker.addPosition(change.uptimeMillis, last)
                        val delta = last - origin
                        if (!reduced && !pan && hypot(delta.x.toDouble(), delta.y.toDouble()) > 6 * scale) {
                            driver.beginPan(fruitSmoothieCarouselNow(), autoPlay, reduced); pan = true; wake()
                        }
                        if (pan) {
                            driver.movePan(delta.x / scale, fruitSmoothieCarouselNow())
                            clock.doubleValue = fruitSmoothieCarouselNow(); change.consume()
                        }
                        if (!change.pressed) { normal = true; break }
                    }
                } finally {
                    if (pan && !driver.choosing) {
                        val velocity = if (normal) tracker.calculateVelocity().x / scale else 0.0
                        if (driver.finishPan(velocity, fruitSmoothieCarouselNow(), autoPlay, reduced) && normal) light()
                        wake()
                    } else if (!pan && normal && hypot((last.x - origin.x).toDouble(), (last.y - origin.y).toDouble()) < 6 * scale) {
                        val x = last.x / scale; val y = (last.y - top) / scale
                        if ((x in 54.0..98.0 && y in 62.5..106.5) || (x in 290.0..334.0 && y in 64.5..108.5)) {
                            presentChooser()
                        } else if (y in 732.0..776.0) {
                            val q = driver.selection(fruitSmoothieCarouselNow(), autoPlay, reduced, preview)
                            val shift = FruitSmoothieCarouselMath.tabShift(q)
                            for (copy in -1..7) {
                                val left = FruitSmoothieCarouselMath.tabX(copy) - shift
                                if (x >= left && x <= left + FruitSmoothieCarouselMath.tabWidth(copy)) {
                                    choose(FruitSmoothieCarouselMath.wrap(copy)); break
                                }
                            }
                        }
                    }
                }
            }
        }) {
        // Snapshot state is consumed only by drawing; the screen does not recompose per frame.
        val q = driver.selection(clock.doubleValue, autoPlay, reduced, preview)
        val selected = FruitSmoothieCarouselMath.wrap(FruitSmoothieCarouselMath.rounded(q).toInt())
        if (semanticSelection.intValue != selected) semanticSelection.intValue = selected
        val canvas = drawContext.canvas.nativeCanvas
        val scale = size.width / 393f
        val top = max(0f, (size.height - 852 * scale) / 2)
        canvas.save(); canvas.translate(0f, top); canvas.scale(scale, scale)
        canvas.clipRect(0f, 0f, 393f, 852f)
        painter.draw(canvas, q)
        canvas.restore()
    }
    if (chooser) {
        AlertDialog(onDismissRequest = { cancelChooser() }, title = { Text("Choose a flavor") },
            text = { Column { FruitSmoothieCarouselFlavors.forEachIndexed { index, name ->
                TextButton(onClick = { choose(index) }) { Text(name) }
            } } }, confirmButton = { TextButton(onClick = { cancelChooser() }) { Text("Cancel") } })
    }
}

private class FruitSmoothieCarouselDriver {
    private var epoch = fruitSmoothieCarouselNow()
    private var manualFrom: Double? = null
    private var manualTarget = 0.0
    private var eventDate = epoch
    var eventDuration = 0.0
        private set
    private var eventSpring = false
    var eventMoving = false
        private set
    private var lastTouch = epoch
    var held = false
        private set
    var choosing = false
        private set
    private var panOrigin = 0.0
    private var reducedSelection = 0
    val hasManual get() = manualFrom != null

    fun selection(now: Double, autoPlay: Boolean, reduced: Boolean, preview: Double?): Double {
        if (reduced) return reducedSelection.toDouble()
        manualFrom?.let { from ->
            if (held) return manualTarget
            val raw = if (eventDuration == 0.0) 1.0 else (now - eventDate) / eventDuration
            val progress = if (eventSpring) FruitSmoothieCarouselMath.spring(raw) else FruitSmoothieCarouselMath.smooth(raw)
            val current = from + (manualTarget - from) * progress
            val recovery = if (autoPlay) FruitSmoothieCarouselMath.smooth((now - lastTouch - 2) / 0.6) else 0.0
            val rest = FruitSmoothieCarouselMath.rounded(manualTarget / 5) * 5
            return current + (rest - current) * recovery
        }
        return FruitSmoothieCarouselMath.automatic(preview ?: if (autoPlay) now - epoch else 0.0)
    }
    fun reset(now: Double) {
        held = false; choosing = false; manualFrom = null; eventMoving = false; reducedSelection = 0; epoch = now
    }
    private fun interact(target: Double, now: Double, autoPlay: Boolean, reduced: Boolean, duration: Double = 0.6, spring: Boolean = false) {
        val current = selection(now, autoPlay, reduced, null)
        val wrapped = current >= 5 - 0.00000001
        val from = if (wrapped) current - 5 else current
        val destination = if (wrapped && target >= 5) target - 5 else target
        if (reduced) reducedSelection = FruitSmoothieCarouselMath.wrap(FruitSmoothieCarouselMath.rounded(destination).toInt())
        held = false; choosing = false; manualFrom = from; manualTarget = destination; eventDate = now
        eventDuration = duration; eventSpring = spring; eventMoving = duration > 0; lastTouch = now
    }
    fun choose(index: Int, now: Double, autoPlay: Boolean, reduced: Boolean): Boolean {
        val q = selection(now, autoPlay, reduced, null); val item = FruitSmoothieCarouselMath.wrap(index)
        val target = if (item == 0 && q > 2.5) 5.0 else item.toDouble()
        val changed = FruitSmoothieCarouselMath.wrap(FruitSmoothieCarouselMath.rounded(q).toInt()) != item
        interact(target, now, autoPlay, reduced)
        return changed && !reduced
    }
    fun presentChooser(now: Double, autoPlay: Boolean, reduced: Boolean) {
        val q = selection(now, autoPlay, reduced, null)
        manualFrom = q; manualTarget = q; held = true; choosing = true; eventMoving = false
    }
    fun dismissChooser(now: Double, autoPlay: Boolean, reduced: Boolean) {
        if (held) interact(selection(now, autoPlay, reduced, null), now, autoPlay, reduced, duration = 0.0)
        choosing = false
    }
    fun beginPan(now: Double, autoPlay: Boolean, reduced: Boolean) {
        val current = selection(now, autoPlay, reduced, null)
        val q = if (current >= 5 - 0.00000001) current - 5 else current
        panOrigin = q; manualFrom = q; manualTarget = q; held = true; eventMoving = false
    }
    fun movePan(dx: Double, now: Double) {
        if (!held) return
        val proposed = panOrigin - dx / 304
        val edge = FruitSmoothieCarouselMath.clamp(proposed, 0.0, 5.0)
        val over = (proposed - edge) * 304
        manualTarget = edge + over / (1 + abs(over) / 120) / 304
        eventDuration = 0.0; lastTouch = now
    }
    fun finishPan(velocity: Double, now: Double, autoPlay: Boolean, reduced: Boolean): Boolean {
        if (!held) return false
        val projected = manualTarget - FruitSmoothieCarouselMath.clamp(velocity, -1200.0, 1200.0) * 0.12 / 304
        val target = FruitSmoothieCarouselMath.clamp(FruitSmoothieCarouselMath.rounded(projected), 0.0, 5.0)
        val changed = FruitSmoothieCarouselMath.wrap(target.toInt()) != FruitSmoothieCarouselMath.wrap(FruitSmoothieCarouselMath.rounded(panOrigin).toInt())
        interact(target, now, autoPlay, reduced, duration = 0.42, spring = true)
        return changed && !reduced
    }
    fun complete(now: Double, autoPlay: Boolean) {
        if (held || choosing || manualFrom == null) return
        if (autoPlay && now - lastTouch >= 2.6) reset(now)
        else if (!autoPlay && now - eventDate >= max(0.02, eventDuration + 0.02)) eventMoving = false
    }
}

private class FruitSmoothieCarouselPainter(context: Context) {
    private val art: List<Bitmap?> = arrayOf("strawberry", "blueberry", "orange", "kiwi", "apricot").map { name ->
        context.assets.open("fruitsmoothiecarousel/fruitsmoothiecarousel_$name.png").use { stream ->
            BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply { inScaled = false; inPreferredConfig = Bitmap.Config.ARGB_8888 })
        }
    }
    private fun font(weight: Int): Typeface = if (Build.VERSION.SDK_INT >= 28) Typeface.create(Typeface.create("sans-serif", Typeface.NORMAL), weight, false)
        else Typeface.create("sans-serif", if (weight >= 600) Typeface.BOLD else Typeface.NORMAL)
    private val fonts = mapOf(400 to font(400), 600 to font(600), 700 to font(700))
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 0.7f; color = NativeColor.argb(26,255,255,255) }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG)
    private val image = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val icon = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 2f; strokeCap = Paint.Cap.ROUND; style = Paint.Style.STROKE }
    private val bounds = RectF(0f,0f,393f,852f)
    private val cardRect = RectF()
    private val artRect = RectF()
    private val fontMetrics = Paint.FontMetrics()
    private val gradientMatrix = Matrix()
    private val gradients = Array(5) { index ->
        LinearGradient(0f,0f,0f,1f,FruitSmoothieCarouselTops[index] or -0x1000000,FruitSmoothieCarouselBottoms[index] or -0x1000000,Shader.TileMode.CLAMP)
    }

    fun draw(c: NativeCanvas, q: Double) {
        c.drawColor(0xFFFCFAFC.toInt())
        label(c, "Seasonal", 63.0, 174.25, 22.0, 600, 0xFF242426.toInt())
        label(c, "Smoothie", 63.0, 200.75, 22.0, 700, 0xFF242426.toInt())
        // Swift's zIndex draws equally-small neighbors first, then the expanding matching layers.
        val copies = (-1..6).sortedBy { 1 - min(1.0, abs(it - q)) }
        for (copy in copies) {
            if (copy == -1) continue
            val alpha = if (copy == 4) 1 - FruitSmoothieCarouselMath.smooth((q - 4.82) / 0.18) else 1.0
            if (alpha <= 0) continue
            val distance = copy - q; val a = min(1.0, abs(distance)); val index = FruitSmoothieCarouselMath.wrap(copy)
            val x = 51 + (if (distance >= 0) 304 else 120) * distance
            val width = 287 - 197 * a; val height = 405 - 63 * a; val y = 294 + 32 * a; val radius = 40 - 6 * a
            val restore = if (alpha < 0.999999) c.saveLayerAlpha(bounds, (alpha * 255).roundToInt()) else c.save()
            gradientMatrix.setScale(1f,height.toFloat()); gradientMatrix.postTranslate(0f,y.toFloat())
            gradients[index].setLocalMatrix(gradientMatrix)
            fill.shader = gradients[index]; fill.alpha = 255; fill.style = Paint.Style.FILL
            cardRect.set(x.toFloat(),y.toFloat(),(x+width).toFloat(),(y+height).toFloat())
            c.drawRoundRect(cardRect,radius.toFloat(),radius.toFloat(),fill)
            c.drawRoundRect(cardRect,radius.toFloat(),radius.toFloat(),edge)
            art[index]?.let { bitmap ->
                val scale = 1 - 0.69 * a; val w = 336 * scale; val h = 504 * scale
                val centerX = x + width / 2; val centerY = 438 + 50 * a
                image.alpha = 255
                artRect.set((centerX-w/2).toFloat(),(centerY-h/2).toFloat(),(centerX+w/2).toFloat(),(centerY+h/2).toFloat())
                c.drawBitmap(bitmap,null,artRect,image)
            }
            c.restoreToCount(restore)
        }
        val shift = FruitSmoothieCarouselMath.tabShift(q)
        for (copy in -1..7) {
            val index = FruitSmoothieCarouselMath.wrap(copy)
            val selected = max(0.0, 1 - abs(copy - q))
            label(c,FruitSmoothieCarouselFlavors[index],FruitSmoothieCarouselMath.tabX(copy)-shift,754.0,18.0,400,FruitSmoothieCarouselMath.mixedColor(0xB5B2B5,0x2D2D30,selected))
        }
        val current = floor(q).toInt(); val fraction = q - floor(q)
        val underlineWidth = FruitSmoothieCarouselMath.tabWidth(current) * (1-fraction) + FruitSmoothieCarouselMath.tabWidth(current+1) * fraction
        fill.shader = null; fill.color = 0xFFC66A8F.toInt(); fill.alpha = 255
        val left = 63 + underlineWidth / 2 - 25
        c.drawRoundRect(RectF(left.toFloat(),776f,(left+50).toFloat(),779f),1.5f,1.5f,fill)
        navigation(c)
    }
    private fun label(c: NativeCanvas, value: String, x: Double, centerY: Double, size: Double, weight: Int, color: Int) {
        text.shader = null; text.color = color; text.alpha = 255; text.textSize = size.toFloat(); text.typeface = fonts[weight]
        text.style = Paint.Style.FILL
        text.getFontMetrics(fontMetrics)
        c.drawText(value,x.toFloat(),(centerY-(fontMetrics.ascent+fontMetrics.descent)/2).toFloat(),text)
    }
    private fun navigation(c: NativeCanvas) {
        icon.color = 0xFFD5A6B4.toInt(); icon.strokeWidth = 2f
        for (y in floatArrayOf(73f,84.5f,96f)) c.drawLine(63f,y,89f,y,icon)
        fill.shader = null; fill.alpha = 255; fill.color = 0xFFF0EDF0.toInt()
        c.drawRoundRect(RectF(290f,65f,334f,108f),11f,11f,fill)
        icon.color = 0xFFAEAAAF.toInt(); icon.strokeWidth = 1.65f
        c.drawCircle(310.5f,84.5f,6.4f,icon); c.drawLine(315.3f,89.3f,320.5f,94.5f,icon)
    }
}

@Composable
private fun rememberFruitSmoothieCarouselReduced(): Boolean {
    val resolver = LocalContext.current.contentResolver
    fun read() = Settings.Global.getFloat(resolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f) == 0f
    var reduced by remember(resolver) { mutableStateOf(read()) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) { override fun onChange(selfChange: Boolean) { reduced = read() } }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),false,observer)
        reduced = read()
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return reduced
}

@Composable
private fun FruitSmoothieCarouselSystemBars() {
    val window = LocalActivity.current?.window; val view = LocalView.current
    val controller = remember(window,view) { window?.let { WindowCompat.getInsetsController(it,view) } }
    DisposableEffect(controller) {
        val oldStatus = controller?.isAppearanceLightStatusBars
        val oldNavigation = controller?.isAppearanceLightNavigationBars
        controller?.show(WindowInsetsCompat.Type.statusBars())
        controller?.isAppearanceLightStatusBars = true; controller?.isAppearanceLightNavigationBars = true
        onDispose {
            if (oldStatus != null) controller?.isAppearanceLightStatusBars = oldStatus
            if (oldNavigation != null) controller?.isAppearanceLightNavigationBars = oldNavigation
        }
    }
}

private fun fruitSmoothieCarouselNow() = System.nanoTime() / 1_000_000_000.0
