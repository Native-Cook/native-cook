package com.ngocdevv.nativecook.blocks.cards.escapepostercards

import android.content.Context
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.graphics.Canvas as NativeCanvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText as Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/** Four 2.2 s trips: hold 1.4 s, then turn/wipe for 0.8 s with cubic ease-in-out. */
private const val LOOP = 8.8
private const val STOP = 2.2
private const val SWITCH = 1.4
private const val SNAP = 0.45
private const val HOLD = 2.0
private const val ASSETS = "escapepostercards/escapepostercards_"
private val GREEN = Color(0xFF1E4D2B)
private val CREAM = Color(0xFFF6F2E7)
private val NO_PADDING = PlatformTextStyle(includeFontPadding = false)
private val LINE_HEIGHT = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private data class EscapePosterCardsTrip(
    val title: String, val date: String, val from: String, val nights: String, val board: String, val photo: String,
)

private val TRIPS = listOf(
    EscapePosterCardsTrip("Villa\nEscapes", "03 May 2026", "Harbour City", "4 nights", "Self-catering", "villa"),
    EscapePosterCardsTrip("Winter\nBreaks", "11 Jan 2026", "Harbour City", "6 nights", "Half board", "snow"),
    EscapePosterCardsTrip("Lake\nWeekends", "19 Jun 2026", "North Gate", "2 nights", "Breakfast", "forest"),
    EscapePosterCardsTrip("Night Food\nTrails", "07 Oct 2026", "Harbour City", "5 nights", "Room only", "street"),
)

private fun wrap(i: Double): Int = ((i % 4 + 4) % 4).toInt()
private fun rounded(i: Double): Double = floor(i + 0.5)
private fun ease(x: Double): Double {
    val u = x.coerceIn(0.0, 1.0)
    return if (u < 0.5) 4 * u * u * u else 1 - (-2 * u + 2).let { it * it * it } / 2
}
private fun indexAt(t: Double): Double {
    val phase = (t % LOOP + LOOP) % LOOP
    val k = floor(phase / STOP)
    return k + ease((phase - k * STOP - SWITCH) / (STOP - SWITCH))
}

/** Only index is read by draw/layer callbacks; the screen does not recompose every frame. */
private class EscapePosterCardsMotion {
    var index by mutableDoubleStateOf(0.0)
    var manual by mutableStateOf(false)
    var touching = false
    var elapsed = 0.0
    private var origin = 0.0
    private var released = -1.0
    private var grabbed = 0.0
    private var from = 0.0
    private var snapTo = 0.0
    private var lastStep = 0.0

    fun frame(dt: Double, autoPlay: Boolean) {
        elapsed += dt
        if (touching) return
        if (manual) {
            val since = elapsed - released
            index = if (since < SNAP) from + (snapTo - from) * ease(since / SNAP) else snapTo
            if (since > SNAP + HOLD) {
                origin = elapsed - wrap(snapTo) * STOP
                manual = false
            }
            return
        }
        if (autoPlay) index = indexAt(elapsed - origin)
    }

    fun begin() {
        touching = true
        manual = true
        grabbed = index
        lastStep = rounded(index)
    }

    fun drag(translationDp: Float): Boolean {
        index = grabbed - translationDp / 300.0
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

    fun reduceMotion() {
        index = rounded(index)
        touching = false
        manual = false
        origin = elapsed - wrap(index) * STOP
    }
}

private class EscapePosterCardsArtwork(context: Context, density: Float) {
    val photos = TRIPS.map { trip ->
        context.assets.open("${ASSETS}photo-${trip.photo}.jpg").use {
            requireNotNull(BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inScaled = false }))
                .apply { this.density = Bitmap.DENSITY_NONE; prepareToDraw() }
        }
    }
    val images = photos.map { it.asImageBitmap() }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val destination = RectF()

    // Blur once on a software canvas, rather than rebuilding a shadow during every turn.
    val shadow = Bitmap.createBitmap((408 * density).roundToInt(), (504 * density).roundToInt(), Bitmap.Config.ARGB_8888).also { bitmap ->
        NativeCanvas(bitmap).apply {
            scale(density, density)
            paint.color = 0x4D000000
            paint.maskFilter = BlurMaskFilter((24f - 0.5f) / 0.57735f, BlurMaskFilter.Blur.NORMAL)
            drawRoundRect(72f, 86f, 336f, 446f, 10f, 10f, paint)
            paint.maskFilter = null
            paint.color = android.graphics.Color.WHITE
        }
        bitmap.prepareToDraw()
    }.asImageBitmap()

    fun photo(canvas: NativeCanvas, k: Int, x: Float, width: Float, height: Float) {
        val image = photos[k]
        val scale = max(width / image.width, height / image.height)
        val w = image.width * scale
        val h = image.height * scale
        destination.set(x + (width - w) / 2, (height - h) / 2, x + (width + w) / 2, (height + h) / 2)
        canvas.drawBitmap(image, null, destination, paint)
    }
}

@Composable
private fun rememberEscapePosterCardsReducedMotion(): Boolean {
    val context = LocalContext.current
    fun read() = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    var reduced by remember(context) { mutableStateOf(read()) }
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { reduced = read() }
        }
        context.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    return reduced
}

/** Offline travel posters. A 300 dp swipe turns a trip; release snaps 0.45 s and holds 2 s. */
@Composable
fun EscapePosterCards(
    autoPlay: Boolean = false,
    brand: String = "Wander Post",
    action: String = "See this trip",
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val haptic = LocalHapticFeedback.current
    val reduced = rememberEscapePosterCardsReducedMotion()
    val auto by rememberUpdatedState(autoPlay)
    val motion = remember { EscapePosterCardsMotion() }
    val art = remember(context, density) { EscapePosterCardsArtwork(context, density) }
    val archivo = remember(context) {
        FontFamily(androidx.compose.ui.text.font.Typeface(Typeface.createFromAsset(context.assets, "${ASSETS}ArchivoExpanded-Black.ttf")))
    }
    LaunchedEffect(reduced) {
        if (reduced) motion.reduceMotion()
    }
    LaunchedEffect(autoPlay || motion.manual, reduced, lifecycle) {
        if (reduced || (!auto && !motion.manual)) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var previous = 0L
            while (true) withFrameNanos { now ->
                motion.frame(if (previous == 0L) 0.0 else (now - previous) / 1e9, auto)
                previous = now
            }
        }
    }
    val insets = WindowInsets.safeDrawing.asPaddingValues()

    BoxWithConstraints(modifier.fillMaxSize().background(Color(0xFF1B2A22))) {
        val width = maxWidth.value
        val height = maxHeight.value
        val safeTop = insets.calculateTopPadding().value
        val safeBottom = insets.calculateBottomPadding().value
        Box(Modifier.fillMaxSize().pointerInput(reduced, density) {
            if (reduced) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                motion.begin()
                var activated = false
                try {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        val translation = (change.position - down.position) / density
                        if (!activated && hypot(translation.x, translation.y) >= 4f) activated = true
                        if (activated) {
                            change.consume()
                            if (motion.drag(translation.x)) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        }
                    }
                } finally {
                    motion.release()
                }
            }
        }) {
            Canvas(Modifier.fillMaxSize()) {
                val i = motion.index
                val base = floor(i)
                val f = (i - base).toFloat()
                val canvas = drawContext.canvas.nativeCanvas
                canvas.save()
                canvas.scale(density, density)
                art.photo(canvas, wrap(base), -60 * f, width + 60, height)
                canvas.save()
                canvas.clipRect(width * (1 - f), 0f, width, height)
                art.photo(canvas, wrap(base + 1), 60 * (1 - f), width + 60, height)
                canvas.restore()
                canvas.restore()
            }
            Box(Modifier.fillMaxWidth().height((safeTop + 90).dp).background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.Transparent))))
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(220.dp).background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)))))
            Row(Modifier.offset(24.dp, (safeTop + 8).dp).size((width - 48).dp, 34.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(brand, style = TextStyle(fontFamily = archivo, fontSize = 17.sp, color = Color.White, platformStyle = NO_PADDING))
                Spacer(Modifier.weight(1f))
                Text("Deals · Spring", style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.8f), platformStyle = NO_PADDING))
            }
            Box(
                Modifier.offset(((width - 264) / 2).dp, (height * 0.47f - 180).dp).size(264.dp, 360.dp)
                    .graphicsLayer {
                        val i = motion.index
                        val f = i - floor(i)
                        rotationY = (if (f < 0.5) f * 180 else (f - 1) * 180).toFloat()
                        scaleX = (1 - 0.06 * sin(PI * f)).toFloat()
                        scaleY = scaleX
                        cameraDistance = 900 * density
                    }
                    .drawBehind { drawImage(art.shadow, Offset(-72 * density, -72 * density)) }
                    .background(CREAM, RoundedCornerShape(10.dp))
                    .semantics { contentDescription = "Trip poster. Swipe for the next trip." },
            ) {
                TRIPS.forEachIndexed { k, trip ->
                    Box(Modifier.fillMaxSize().graphicsLayer {
                        val i = motion.index
                        val base = floor(i)
                        alpha = if (wrap(if (i - base < 0.5) base else base + 1) == k) 1f else 0f
                    }) {
                        Image(art.images[k], null, Modifier.offset(10.dp, 10.dp).size(244.dp, 156.dp).clip(RoundedCornerShape(3.dp)), contentScale = ContentScale.Crop)
                        Text(trip.title, Modifier.offset(14.dp, 180.dp).size(236.dp, 64.dp), style = TextStyle(fontFamily = archivo, fontSize = 30.sp, lineHeight = 32.sp, letterSpacing = (-0.8).sp, color = GREEN, platformStyle = NO_PADDING, lineHeightStyle = LINE_HEIGHT))
                        Column(Modifier.align(Alignment.BottomStart).padding(start = 14.dp, bottom = 14.dp)) {
                            listOf("DATE" to trip.date, "FROM" to trip.from, "FOR" to trip.nights, "BOARD" to trip.board).forEach { (label, value) ->
                                Text(buildAnnotatedString { withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold)) { append("$label ") }; append(value) }, Modifier.height(15.dp), style = TextStyle(fontSize = 11.sp, lineHeight = 15.sp, letterSpacing = 0.sp, color = GREEN, platformStyle = NO_PADDING, lineHeightStyle = LINE_HEIGHT))
                            }
                        }
                        Text("from $640", Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 14.dp), style = TextStyle(fontFamily = archivo, fontSize = 13.sp, color = GREEN, platformStyle = NO_PADDING))
                    }
                }
            }
        }
        Column(Modifier.align(Alignment.BottomCenter).padding(start = 24.dp, end = 24.dp, bottom = (safeBottom + 14).dp)) {
            Canvas(Modifier.fillMaxWidth().height(8.dp)) {
                val i = motion.index
                val widths = FloatArray(4) { k ->
                    val d = abs(((i - k + 2) % 4 + 4) % 4 - 2).coerceAtMost(1.0)
                    (8 + 18 * (1 - d)).toFloat()
                }
                var x = (size.width / density - widths.sum() - 24) / 2
                widths.forEach { w ->
                    drawRoundRect(Color.White.copy(alpha = 0.45f + 0.55f * ((w - 8) / 18)), Offset(x * density, 0f), Size(w * density, 8 * density), CornerRadius(4 * density))
                    x += w + 8
                }
            }
            Spacer(Modifier.height(18.dp))
            Box(Modifier.fillMaxWidth().height(58.dp).background(CREAM, RoundedCornerShape(29.dp)).semantics { role = Role.Button }, contentAlignment = Alignment.Center) {
                Text(action, style = TextStyle(fontFamily = archivo, fontSize = 15.sp, color = GREEN, platformStyle = NO_PADDING))
            }
        }
    }
}
