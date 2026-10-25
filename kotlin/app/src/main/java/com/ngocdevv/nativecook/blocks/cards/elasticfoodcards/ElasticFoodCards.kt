package com.ngocdevv.nativecook.blocks.cards.elasticfoodcards

import android.content.Context
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlendMode
import android.graphics.BlurMaskFilter
import android.graphics.Canvas as NativeCanvas
import android.graphics.Color as NativeColor
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalDensity
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

private val Names = arrayOf("Harbor Ramen", "Sunrise Rolls", "Salmon Bites", "Garden Miso")
private val Prices = arrayOf("$ 15.00", "$ 9.50", "$ 7.00", "$ 4.50")
private val Ratings = arrayOf("4.5", "4.8", "5.0", "4.3")

/** A food screen whose refresh hand gathers a continuous matte header surface. */
@Composable
fun ElasticFoodCards(autoPlay: Boolean = false, previewTime: Double? = null, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val density = LocalDensity.current
    val reduced = rememberElasticFoodCardsReduced()
    val driver = remember(context.applicationContext) { ElasticFoodCardsDriver(ElasticFoodCardsMotion(context.applicationContext)) }
    val painter = remember(context.applicationContext) { ElasticFoodCardsPainter(context.applicationContext) }
    DisposableEffect(painter) { onDispose { painter.dispose() } }
    val clock = remember { mutableDoubleStateOf(elasticFoodCardsNow()) }
    var revision by remember { mutableIntStateOf(0) }
    var active by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var searchPresented by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val extra = if (density.fontScale >= 1.5f) 44.0 else 0.0
    val window = LocalActivity.current?.window
    val viewLocation = remember(view) { IntArray(2) }
    val windowLocation = remember(window) { IntArray(2) }
    ElasticFoodCardsSystemBars()
    fun wake() { clock.doubleValue = elasticFoodCardsNow(); revision++ }
    fun light() { view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK) }
    fun action(action: ElasticFoodCardsAction): Boolean {
        if (previewTime != null || !active) return false
        val now = elasticFoodCardsNow()
        driver.registerAction(now, autoPlay, reduced, searchPresented)
        when (action) {
            ElasticFoodCardsAction.Search -> { query = driver.searchQuery; driver.presentSearch(now, autoPlay, reduced); searchPresented = true }
            ElasticFoodCardsAction.Refresh -> driver.refresh(now, reduced)
            ElasticFoodCardsAction.All -> { driver.selectedCategory = 0; driver.searchQuery = ""; query = ""; driver.resetScroll() }
            is ElasticFoodCardsAction.Category -> driver.selectedCategory = action.index
            is ElasticFoodCardsAction.Save -> { driver.toggleSaved(action.index, now, reduced); light() }
            is ElasticFoodCardsAction.Scroll -> driver.scrollBy(action.amount)
        }
        wake()
        return true
    }
    DisposableEffect(lifecycle, driver) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                active = false; driver.cancelLifecycle(); wake()
            } else if (event == Lifecycle.Event.ON_RESUME) {
                driver.reset(elasticFoodCardsNow()); searchPresented = false; query = ""; active = true; wake()
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); driver.cancelLifecycle() }
    }
    LaunchedEffect(autoPlay, reduced, previewTime) {
        driver.reset(elasticFoodCardsNow()); searchPresented = false; query = ""; wake()
    }
    LaunchedEffect(autoPlay, reduced, previewTime, revision, active, searchPresented) {
        if (!active || previewTime != null || searchPresented || driver.heldSettled(elasticFoodCardsNow())) return@LaunchedEffect
        if (reduced) {
            val wait = driver.resetDelay(elasticFoodCardsNow()) ?: return@LaunchedEffect
            delay(max(1L, (wait * 1000).toLong() + 1))
            driver.complete(elasticFoodCardsNow(), searchPresented)
            clock.doubleValue = elasticFoodCardsNow()
            return@LaunchedEffect
        }
        while (isActive && !driver.heldSettled(elasticFoodCardsNow()) && (autoPlay || driver.hasManual || driver.scrollMoving(elasticFoodCardsNow()))) {
            withFrameNanos {
                val now = elasticFoodCardsNow()
                driver.complete(now, searchPresented)
                clock.doubleValue = now
            }
        }
    }

    Canvas(modifier.fillMaxSize().background(Color(0xFFDFDFDD))
        .semantics {
            contentDescription = "Food delivery, Fable Bay. Pull down to refresh food."
            customActions = buildList {
                add(CustomAccessibilityAction("Search food") { action(ElasticFoodCardsAction.Search) })
                add(CustomAccessibilityAction("Refresh food") { action(ElasticFoodCardsAction.Refresh) })
                add(CustomAccessibilityAction("View all food") { action(ElasticFoodCardsAction.All) })
                for ((index, name) in arrayOf("Ramen", "Sushi", "Rolls", "Soup").withIndex()) {
                    add(CustomAccessibilityAction("$name category") { action(ElasticFoodCardsAction.Category(index)) })
                }
                for (index in 0..3) {
                    add(CustomAccessibilityAction("${if (driver.isSaved(index)) "Unsave" else "Save"} ${Names[index]}, ${Prices[index]}, rated ${Ratings[index]} out of 5") {
                        action(ElasticFoodCardsAction.Save(index))
                    })
                }
                add(CustomAccessibilityAction("Scroll food down") { action(ElasticFoodCardsAction.Scroll(60.0)) })
                add(CustomAccessibilityAction("Scroll food up") { action(ElasticFoodCardsAction.Scroll(-60.0)) })
            }
        }
        .pointerInput(reduced, previewTime, active, searchPresented, autoPlay, extra) {
            if (previewTime != null || !active || searchPresented) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val scale = size.width / 393.0
                val top = max(0.0, (size.height - 852 * scale) * 0.5)
                val initial = down.position
                val startY = (initial.y - top) / scale
                val tracker = VelocityTracker()
                tracker.addPosition(down.uptimeMillis, initial)
                val scrollStart = driver.scrollOffset(elasticFoodCardsNow())
                val scrollTop = elasticFoodCardsClipTop(driver.frame(elasticFoodCardsNow(), autoPlay, reduced, false, null))
                if (!reduced) { driver.beginPan(startY, elasticFoodCardsNow(), autoPlay, false); wake() }
                var last = initial
                var normal = false
                var traveled = 0.0
                try {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.isConsumed) break
                        last = change.position
                        tracker.addPosition(change.uptimeMillis, change.position)
                        val dx = (last.x - initial.x) / scale
                        val dy = (last.y - initial.y) / scale
                        traveled = hypot(dx, dy)
                        val wasSettled = driver.heldSettled(elasticFoodCardsNow())
                        if (!reduced && driver.movePan(dx, dy, elasticFoodCardsNow())) light()
                        if (!reduced && wasSettled) wake()
                        if (!reduced) clock.doubleValue = elasticFoodCardsNow()
                        if ((reduced || !driver.heroPan) && startY >= scrollTop && startY <= 852 && abs(dy) > 3 && driver.canScroll(elasticFoodCardsNow(), autoPlay, reduced)) {
                            driver.setScroll(scrollStart - dy)
                            if (reduced) clock.doubleValue = elasticFoodCardsNow()
                            change.consume()
                        } else if (!reduced && driver.heroPan && traveled > 3) change.consume()
                        if (!change.pressed) { normal = true; break }
                    }
                } finally {
                    val now = elasticFoodCardsNow()
                    val velocity = if (normal) tracker.calculateVelocity().y / scale else 0.0
                    val wasHero = driver.heroPan
                    if (!reduced) { driver.finishPan(velocity, now); wake() }
                    if (normal && traveled < 6) {
                        val f = driver.frame(now, autoPlay, reduced, false, null)
                        driver.hit(last.x / scale, (last.y - top) / scale, f, now, extra)?.let { action(it) }
                    } else if (normal && !reduced && !wasHero && startY >= scrollTop && startY <= 852) {
                        driver.flingScroll(-velocity, now)
                        wake()
                    }
                }
            }
        }) {
        val now = clock.doubleValue
        val current = driver.frame(now, autoPlay, reduced, searchPresented, previewTime)
        val scale = size.width / 393f
        val baseY = max(0f, (size.height - 852f * scale) * 0.5f)
        val insets = window?.decorView?.rootWindowInsets?.let { WindowInsetsCompat.toWindowInsetsCompat(it) }
        val bottomInset = insets?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom?.div(scale) ?: 0f
        val cutout = insets?.displayCutout
        val topCutout = cutout?.takeIf { it.safeInsetTop > 0 }?.boundingRects?.firstOrNull {
            !it.isEmpty && it.top < cutout.safeInsetTop && it.bottom <= cutout.safeInsetTop
        }
        // Window cutout coordinates become screen coordinates before mapping to this Canvas.
        view.getLocationOnScreen(viewLocation)
        window?.decorView?.getLocationOnScreen(windowLocation)
        val inletX = ElasticFoodCardsArmMath.cutoutInletX(topCutout?.exactCenterX()?.toDouble(),
            windowLocation[0].toDouble(), viewLocation[0].toDouble(), scale.toDouble())
        val inletY = ElasticFoodCardsArmMath.cutoutInletY(topCutout?.bottom?.toDouble(),
            windowLocation[1].toDouble(), viewLocation[1].toDouble(), baseY.toDouble(), scale.toDouble())
        driver.maximumScroll = max(0.0, extra * 2 + max(0.0, 148 * current.q[5]) + bottomInset)
        val canvas = drawContext.canvas.nativeCanvas
        canvas.save()
        canvas.translate(0f, baseY)
        canvas.scale(scale, scale)
        canvas.clipRect(0f, 0f, 393f, 852f)
        painter.draw(canvas, current, driver, now, reduced, extra, inletX, inletY)
        canvas.restore()
    }

    if (searchPresented) {
        fun close(search: Boolean) {
            if (search) driver.searchQuery = query
            driver.registerAction(elasticFoodCardsNow(), autoPlay, reduced, true)
            if (search) driver.selectedCategory = 0
            searchPresented = false
            wake()
        }
        AlertDialog(onDismissRequest = { close(false) }, title = { Text("Search food") }, text = {
            OutlinedTextField(value = query, onValueChange = { query = it },
                label = { Text("Food or ingredients") }, singleLine = true)
        }, confirmButton = { TextButton(onClick = { close(true) }) { Text("Search") } },
            dismissButton = { TextButton(onClick = { close(false) }) { Text("Cancel") } })
    }
}

internal sealed interface ElasticFoodCardsAction {
    data object Search : ElasticFoodCardsAction
    data object Refresh : ElasticFoodCardsAction
    data object All : ElasticFoodCardsAction
    data class Category(val index: Int) : ElasticFoodCardsAction
    data class Save(val index: Int) : ElasticFoodCardsAction
    data class Scroll(val amount: Double) : ElasticFoodCardsAction
}

internal fun elasticFoodCardsNow() = System.nanoTime() / 1_000_000_000.0

private class ElasticFoodCardsPainter(context: Context) {
    private val art = arrayOf("ramen", "rolls", "nigiri", "miso", "hand", "hand-half", "hand-fist", "promo", "avatar").associateWith { name ->
        context.assets.open("elasticfoodcards/elasticfoodcards_$name.png").use { BitmapFactory.decodeStream(it)?.apply { prepareToDraw() } }
    }
    private val surface = ElasticFoodCardsSurfacePainter()
    private val handNames = arrayOf("hand", "hand-half", "hand-fist")
    private val handRenderer = ElasticFoodCardsHandRenderer(Array(3) { requireNotNull(art[handNames[it]]) })
    private val handShadow = ElasticFoodCardsHandShadow(context, handRenderer)
    fun dispose() { handShadow.dispose(); handRenderer.dispose() }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val path = Path()
    private val add = PorterDuffXfermode(PorterDuff.Mode.ADD)
    private val fonts = arrayOf(400, 500).associateWith { weight ->
        if (Build.VERSION.SDK_INT >= 28) Typeface.create(Typeface.create("sans-serif", Typeface.NORMAL), weight, false)
        else Typeface.create(if (weight == 500) "sans-serif-medium" else "sans-serif", Typeface.NORMAL)
    }
    private val shadows = HashMap<Int, Bitmap>()
    private val products = arrayOf("ramen", "rolls", "nigiri", "miso")
    private val productRects = arrayOf(RectF(19f, 52.35f, 159f, 126.65f), RectF(21.5f, 54.75f, 158.5f, 123.25f),
        RectF(20f, 45.25f, 159f, 137.75f), RectF(27.5f, 45f, 151.5f, 138f))
    init { cardShadow(0.0); cardShadow(44.0) }

    private fun rgba(hex: Int, alpha: Double = 1.0) = NativeColor.argb((alpha.coerceIn(0.0, 1.0) * 255).toInt(), hex shr 16 and 255, hex shr 8 and 255, hex and 255)
    private fun basic(hex: Int = 0xFFFFFF, alpha: Double = 1.0, stroke: Double? = null) {
        paint.reset(); paint.isAntiAlias = true; paint.isFilterBitmap = true
        paint.color = rgba(hex, alpha); paint.alpha = (alpha.coerceIn(0.0, 1.0) * 255).toInt()
        paint.textAlign = Paint.Align.LEFT; paint.style = if (stroke == null) Paint.Style.FILL else Paint.Style.STROKE
        paint.strokeWidth = (stroke ?: 0.0).toFloat(); paint.strokeCap = Paint.Cap.ROUND; paint.strokeJoin = Paint.Join.ROUND
        paint.shader = null; paint.xfermode = null; paint.colorFilter = null
        if (Build.VERSION.SDK_INT >= 29) paint.blendMode = BlendMode.SRC_OVER
    }
    private fun plus() { if (Build.VERSION.SDK_INT >= 29) paint.blendMode = BlendMode.PLUS else paint.xfermode = add }
    private fun rect(c: NativeCanvas, bounds: RectF, radius: Float, color: Int, alpha: Double = 1.0, stroke: Double? = null) {
        basic(color, alpha, stroke); c.drawRoundRect(bounds, radius, radius, paint)
    }
    private fun text(c: NativeCanvas, value: String, size: Double, color: Int, x: Double, centerY: Double, alpha: Double = 1.0, weight: Int = 400, align: Paint.Align = Paint.Align.LEFT, tracking: Double = 0.0) {
        basic(color, alpha); paint.textAlign = align; paint.typeface = fonts[weight]; paint.textSize = size.toFloat()
        val baseline = (centerY - (paint.fontMetrics.ascent + paint.fontMetrics.descent) * 0.5).toFloat()
        if (tracking == 0.0) c.drawText(value, x.toFloat(), baseline, paint)
        else {
            val widths = FloatArray(value.length); paint.getTextWidths(value, widths)
            var cursor = x
            for (i in value.indices) { c.drawText(value, i, i + 1, cursor.toFloat(), baseline, paint); cursor += widths[i] + tracking }
        }
    }
    private fun image(c: NativeCanvas, name: String, bounds: RectF, alpha: Double = 1.0, fill: Boolean = false, stretch: Boolean = false, additive: Boolean = false) {
        val bitmap = art[name] ?: return
        basic(alpha = alpha); if (additive) plus()
        if (stretch) c.drawBitmap(bitmap, null, bounds, paint)
        else {
            val scale = if (fill) max(bounds.width() / bitmap.width, bounds.height() / bitmap.height) else min(bounds.width() / bitmap.width, bounds.height() / bitmap.height)
            val w = bitmap.width * scale; val h = bitmap.height * scale
            c.drawBitmap(bitmap, null, RectF(bounds.centerX() - w / 2, bounds.centerY() - h / 2, bounds.centerX() + w / 2, bounds.centerY() + h / 2), paint)
        }
    }
    fun draw(c: NativeCanvas, f: ElasticFoodCardsFrame, state: ElasticFoodCardsDriver, now: Double, reduced: Boolean, extra: Double, inletX: Double, inletY: Double) {
        c.drawColor(rgba(0xDFDFDD))
        surface.draw(c, f); hand(c, f, inletX, inletY)
        c.save(); c.translate(0f, (148 * f.q[0]).toFloat()); header(c); c.restore()
        c.save(); c.translate(0f, (148 * f.q[1]).toFloat())
        text(c, "Popular Food", 28.0, 0x101110, 16.0, 153.0, tracking = -0.4)
        text(c, "view all", 17.0, 0x8F9290, 377.0, 154.5, align = Paint.Align.RIGHT); c.restore()
        c.save(); c.translate(0f, (148 * f.q[2]).toFloat()); categories(c, state.selectedCategory); c.restore()
        c.save(); c.translate(0f, (148 * f.q[3]).toFloat()); promo(c); c.restore()
        val scroll = state.scrollOffset(now)
        c.save(); c.clipRect(0f, elasticFoodCardsClipTop(f).toFloat(), 393f, 852f)
        for (index in 0..3) {
            val row = index / 2
            val x = if (index % 2 == 0) 16.0 else 203.0
            val y = 389 + row * (238 + extra) + 148 * f.q[row + 4] - scroll
            val matches = state.searchQuery.isEmpty() || Names[index].contains(state.searchQuery, ignoreCase = true)
            val categoryMatches = state.selectedCategory == 0 || state.selectedCategory == 1 && index == 2 || state.selectedCategory == 2 && index == 1 || state.selectedCategory == 3 && index == 3
            val alpha = if (matches && categoryMatches) 1.0 else 0.32
            c.save(); c.translate(x.toFloat(), y.toFloat()); card(c, index, alpha, state.savedMix(index, now, reduced), extra); c.restore()
        }
        c.restore()
    }
    private fun header(c: NativeCanvas) {
        basic(0x7B817D, stroke = 2.0)
        c.drawOval(RectF(30.5f, 74.5f, 53.5f, 97.5f), paint)
        c.drawLine(50f, 95f, 59f, 104f, paint)
        text(c, "location:", 13.0, 0x8F9290, 196.0, 78.0, align = Paint.Align.CENTER)
        text(c, "Fable Bay", 17.0, 0x101110, 196.0, 99.0, weight = 500, align = Paint.Align.CENTER)
        c.save(); path.rewind(); path.addCircle(349f, 89f, 16f, Path.Direction.CW); c.clipPath(path)
        image(c, "avatar", RectF(333f, 73f, 365f, 105f), fill = true); c.restore()
        basic(0xCA5E67); c.drawCircle(363f, 74f, 3.2f, paint)
    }
    private fun categories(c: NativeCanvas, selected: Int) {
        val labels = arrayOf("Ramen", "Sushi", "Rolls", "Soup")
        val names = arrayOf("miso", "nigiri", "rolls", "miso")
        val starts = floatArrayOf(16f, 139f, 253f, 363f)
        val widths = floatArrayOf(117f, 108f, 104f, 104f)
        for (i in 0..3) {
            val left = starts[i]; val w = widths[i]
            rect(c, RectF(left, 194f, left + w, 239f), 24f, 0xE5E5E3)
            if (selected == i && i != 0) rect(c, RectF(left, 194f, left + w, 239f), 24f, 0x101110, 0.12, 0.8)
            val iw = if (i == 0) 22f else 24f
            image(c, names[i], RectF(left + 25 - iw / 2, 206f, left + 25 + iw / 2, 226f))
            text(c, labels[i], 18.0, 0x101110, left + 47.0, 217.5)
        }
    }
    private fun promo(c: NativeCanvas) {
        c.save(); path.rewind(); path.addRoundRect(RectF(16f, 254f, 379f, 375f), 34f, 34f, Path.Direction.CW); c.clipPath(path)
        basic(0x060707); c.drawRect(16f, 254f, 379f, 375f, paint)
        image(c, "promo", RectF(16f, 254f, 379f, 375f), fill = true)
        basic(); paint.shader = LinearGradient(16f, 0f, 379f, 0f,
            intArrayOf(rgba(0x000000, 0.0), rgba(0x000000, 0.65), rgba(0x000000)), floatArrayOf(145f / 363, 182f / 363, 210f / 363), Shader.TileMode.CLAMP)
        c.drawRect(16f, 254f, 379f, 375f, paint); c.restore()
        text(c, "Discount 50%", 23.0, 0xE0E0DE, 207.0, 305.5, tracking = -0.3)
        text(c, "learn more...", 13.0, 0x8F9290, 207.0, 328.0)
    }
    private fun cardShadow(extra: Double): Bitmap = shadows.getOrPut(extra.toInt()) {
        val height = 225 + extra.toInt()
        Bitmap.createBitmap(249, height + 72, Bitmap.Config.ARGB_8888).also { bitmap ->
            val canvas = NativeCanvas(bitmap); val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.color = rgba(0x000000, 0.025); p.maskFilter = BlurMaskFilter(18f, BlurMaskFilter.Blur.NORMAL)
            canvas.drawRoundRect(RectF(36f, 45f, 213f, 45f + height), 31f, 31f, p)
            p.color = rgba(0x000000, 0.04); p.maskFilter = BlurMaskFilter(3f, BlurMaskFilter.Blur.NORMAL)
            canvas.drawRoundRect(RectF(36f, 37.5f, 213f, 37.5f + height), 31f, 31f, p)
        }
    }
    private fun card(c: NativeCanvas, index: Int, alpha: Double, saved: Double, extra: Double) {
        if (alpha < 0.999) {
            basic(alpha = alpha)
            val group = c.saveLayer(RectF(-40f, -40f, 217f, (265 + extra).toFloat()), paint)
            card(c, index, 1.0, saved, extra)
            c.restoreToCount(group)
            return
        }
        basic(alpha = alpha); c.drawBitmap(cardShadow(extra), -36f, -36f, paint)
        rect(c, RectF(0f, 0f, 177f, (225 + extra).toFloat()), 31f, 0xE8E8E6, alpha)
        image(c, products[index], productRects[index], alpha)
        text(c, Names[index], if (extra > 0) 20.0 else 17.0, 0x101110, 14.0, 159 + extra / 2, alpha)
        text(c, Prices[index], if (extra > 0) 19.0 else 16.0, 0x101110, 14.0, 191.5 + extra, alpha)
        star(c, 123.0, 191 + extra, alpha)
        text(c, Ratings[index], if (extra > 0) 18.0 else 15.0, 0x8F9290, 136.0, 191.5 + extra, alpha)
        heart(c, 23.5, 29.5, alpha * (1 - saved), false)
        if (saved > 0) heart(c, 23.5, 29.5, alpha * saved, true)
    }
    private fun star(c: NativeCanvas, x: Double, y: Double, alpha: Double) {
        path.rewind()
        for (i in 0..9) {
            val a = -PI / 2 + i * PI / 5; val r = if (i % 2 == 0) 8.5 else 4.0
            val px = (x + cos(a) * r).toFloat(); val py = (y + sin(a) * r).toFloat()
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close(); basic(0xE8B14B, alpha); c.drawPath(path, paint)
    }
    private fun heart(c: NativeCanvas, x: Double, y: Double, alpha: Double, filled: Boolean) {
        if (alpha <= 0) return
        c.save(); c.translate(x.toFloat(), y.toFloat()); path.rewind()
        path.moveTo(0f, 8f); path.cubicTo(-2.5f, 5.5f, -8.5f, 1.5f, -8.5f, -3f)
        path.cubicTo(-8.5f, -8f, -2.5f, -9f, 0f, -4.5f); path.cubicTo(2.5f, -9f, 8.5f, -8f, 8.5f, -3f)
        path.cubicTo(8.5f, 1.5f, 2.5f, 5.5f, 0f, 8f); path.close()
        basic(0x101110, alpha, if (filled) null else 0.65); c.drawPath(path, paint); c.restore()
    }
    private fun hand(c: NativeCanvas, f: ElasticFoodCardsFrame, inletX: Double, inletY: Double) {
        val alpha = ElasticFoodCardsMath.clamp(f.handAlpha)
        if (alpha <= 0) return
        val material = elasticFoodCardsHandMaterial(f)
        basic(alpha = alpha)
        val outer = c.saveLayer(RectF(0f, 0f, 393f, 852f), paint)
        handShadow.draw(c, f, material, inletX, inletY)
        handRenderer.draw(c, f, material.weights, inletX, inletY)
        c.restoreToCount(outer)
    }

}

@Composable
private fun rememberElasticFoodCardsReduced(): Boolean {
    val resolver = LocalContext.current.contentResolver
    fun read() = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    var reduced by remember(resolver) { mutableStateOf(read()) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) { override fun onChange(selfChange: Boolean) { reduced = read() } }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        reduced = read(); onDispose { resolver.unregisterContentObserver(observer) }
    }
    return reduced
}

@Composable
private fun ElasticFoodCardsSystemBars() {
    val window = LocalActivity.current?.window ?: return
    val view = LocalView.current
    DisposableEffect(window, view) {
        val bars = WindowCompat.getInsetsController(window, view)
        val status = bars.isAppearanceLightStatusBars; val navigation = bars.isAppearanceLightNavigationBars
        bars.isAppearanceLightStatusBars = true; bars.isAppearanceLightNavigationBars = true
        bars.show(WindowInsetsCompat.Type.statusBars())
        onDispose { bars.isAppearanceLightStatusBars = status; bars.isAppearanceLightNavigationBars = navigation }
    }
}
