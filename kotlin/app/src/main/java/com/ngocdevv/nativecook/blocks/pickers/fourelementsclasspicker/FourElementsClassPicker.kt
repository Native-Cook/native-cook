package com.ngocdevv.nativecook.blocks.pickers.fourelementsclasspicker

import android.app.Activity
import android.content.ContextWrapper
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

private const val LOOP = 8.0
private fun nowSeconds() = System.nanoTime() / 1e9
private fun clamp(value: Double) = value.coerceIn(0.0, 1.0)
private fun ease(value: Double): Double { val u = clamp(value); return u * u * (3 - 2 * u) }
private fun rounded(value: Double) = if (value >= 0) floor(value + 0.5) else -floor(-value + 0.5)
private fun liftSpring(age: Double) = if (age >= 0.65) 1.0 else (1 - exp(-10 * age) * (cos(14 * age) + 10 / 14.0 * sin(14 * age))).coerceIn(0.0, 1.045)

private data class FourElementsClassPickerCard(
    var guide: Double = 0.44,
    var oldProgress: Double = 1.0,
    var oldAlpha: Double = 0.0,
    var progress: Double = 0.0,
    var alpha: Double = 0.0,
    var selection: Double = 0.0,
    var lift: Double = 0.0,
    var tip: Double = 0.0,
) {
    companion object { fun complete() = FourElementsClassPickerCard(guide = 0.13, progress = 1.0, alpha = 1.0) }
}

private data class FourElementsClassPickerPose(
    val cards: List<FourElementsClassPickerCard>,
    var selected: Int,
    var detailFrom: Int,
    var detailTo: Int,
    var detailMix: Double,
) {
    fun returning(amount: Double): FourElementsClassPickerPose {
        if (amount <= 0) return this
        val target = rest()
        val result = copy(cards = cards.map { it.copy() })
        for (i in 0 until 4) {
            val c = result.cards[i]
            val r = target.cards[i]
            c.guide += (r.guide - cards[i].guide) * amount
            c.oldAlpha *= 1 - amount
            c.progress += (r.progress - cards[i].progress) * amount
            c.alpha += (r.alpha - cards[i].alpha) * amount
            c.selection += (r.selection - cards[i].selection) * amount
            c.lift *= 1 - amount; c.tip *= 1 - amount
        }
        result.detailFrom = detailTo; result.detailTo = 0; result.detailMix = amount
        result.selected = if (amount < 0.5) selected else 0
        return result
    }

    companion object {
        fun rest(): FourElementsClassPickerPose {
            val cards = List(4) { FourElementsClassPickerCard() }
            cards[0].apply { guide = 0.13; progress = 1.0; alpha = 1.0; selection = 1.0 }
            return FourElementsClassPickerPose(cards, 0, 0, 0, 1.0)
        }
        fun reduced(index: Int): FourElementsClassPickerPose {
            val cards = List(4) { FourElementsClassPickerCard.complete() }
            cards[index].selection = 1.0
            return FourElementsClassPickerPose(cards, index, index, index, 1.0)
        }
        fun transition(index: Int, previous: Int, progress: Double, tip: Double, lift: Double, previousLift: Double): FourElementsClassPickerPose {
            val cards = List(4) { FourElementsClassPickerCard() }
            val chosen = ease(progress / 0.6)
            cards[previous].oldAlpha = 1 - ease(progress / 0.65)
            cards[previous].selection = 1 - chosen
            cards[previous].lift = previousLift * (1 - chosen)
            cards[index].guide = 0.44 - 0.31 * ease(progress / 0.3)
            cards[index].progress = progress; cards[index].alpha = 1.0
            cards[index].selection = chosen; cards[index].lift = lift; cards[index].tip = tip
            return FourElementsClassPickerPose(cards, index, previous, index, progress)
        }
        fun manual(base: FourElementsClassPickerPose, index: Int, progress: Double, tip: Double, lift: Double): FourElementsClassPickerPose {
            val cards = base.cards.map { it.copy() }
            val fade = ease(progress / 0.65)
            for (i in 0 until 4) {
                val old = base.cards[i]
                cards[i].apply {
                    oldProgress = if (old.alpha >= old.oldAlpha) old.progress else old.oldProgress
                    oldAlpha = max(old.alpha, old.oldAlpha) * (1 - fade)
                    alpha = 0.0; guide = old.guide + (0.44 - old.guide) * fade
                    selection = old.selection * (1 - fade); this.lift = old.lift * (1 - fade); this.tip = 0.0
                }
            }
            cards[index].guide = base.cards[index].guide + (0.13 - base.cards[index].guide) * fade
            cards[index].progress = progress; cards[index].alpha = 1.0
            cards[index].selection = base.cards[index].selection + (1 - base.cards[index].selection) * fade
            cards[index].lift = base.cards[index].lift + (lift - base.cards[index].lift) * fade
            cards[index].tip = tip
            val previous = if (base.detailMix < 0.5) base.detailFrom else base.detailTo
            return FourElementsClassPickerPose(cards, index, previous, index, ease((progress - 0.75) / 0.25))
        }
    }
}

private fun loopPose(raw: Double): FourElementsClassPickerPose {
    val t = ((raw % LOOP) + LOOP) % LOOP
    if (t < 1.4 || t >= 7.6) return FourElementsClassPickerPose.rest()
    val index: Int; val previous: Int; val start: Double; val duration: Double; val detailStart: Double; val detailEnd: Double
    when {
        t < 2.15 -> { index = 1; previous = 0; start = 1.4; duration = 0.65; detailStart = 1.9; detailEnd = 2.15 }
        t < 2.9 -> { index = 2; previous = 1; start = 2.15; duration = 0.65; detailStart = 2.65; detailEnd = 2.9 }
        t < 5 -> { index = 3; previous = 2; start = 2.9; duration = 0.9; detailStart = 3.65; detailEnd = 3.95 }
        else -> { index = 0; previous = 3; start = 5.0; duration = 1.1; detailStart = 5.95; detailEnd = 6.25 }
    }
    val age = t - start
    val u = clamp(age / duration)
    val progress = if (index == 1 || index == 2) 1 - (1 - u).pow(3) else ease(u)
    val tip = clamp(age / 0.04) * (1 - clamp((age - duration) / 0.12))
    val pose = FourElementsClassPickerPose.transition(index, previous, progress, tip, 4 * liftSpring(age), if (previous == 0) 0.0 else 4.0)
    pose.detailFrom = previous; pose.detailTo = index; pose.detailMix = ease((t - detailStart) / (detailEnd - detailStart))
    if (t >= 6.25) pose.cards[0].lift = 4 * (1 - ease((t - 6.25) / 1.35))
    return pose
}

private class FourElementsClassPickerMotion {
    var frame by mutableDoubleStateOf(nowSeconds())
    var manualLive by mutableStateOf(false)
    var epoch = nowSeconds()
    var lastTouch = Double.NEGATIVE_INFINITY
    var traceStarted = Double.NEGATIVE_INFINITY
    var traceDuration = 0.65
    var manualBase = FourElementsClassPickerPose.rest()
    var manualIndex = 0
    var dragging = false
    var detailDragging = false
    var spaceHeld = false
    var lastHaptic = Double.NEGATIVE_INFINITY
    var resume: Job? = null

    fun pose(reduced: Boolean, autoPlay: Boolean, previewTime: Double?): FourElementsClassPickerPose {
        val now = frame
        if (previewTime != null) return if (reduced) FourElementsClassPickerPose.reduced(manualIndex) else loopPose(previewTime)
        if (reduced) return FourElementsClassPickerPose.reduced(manualIndex)
        val touchAge = now - lastTouch
        if (touchAge < 2.6 || dragging || detailDragging) {
            val age = max(0.0, now - traceStarted)
            val progress = ease(age / traceDuration)
            val tip = clamp(age / 0.04) * (1 - clamp((age - traceDuration) / 0.12))
            val pose = FourElementsClassPickerPose.manual(manualBase, manualIndex, progress, tip, 4 * liftSpring(age))
            return if (dragging || detailDragging) pose else pose.returning(ease((touchAge - 2) / 0.6))
        }
        if (!autoPlay) return FourElementsClassPickerPose.rest()
        return loopPose(if (lastTouch == Double.NEGATIVE_INFINITY) now - epoch else touchAge - 2.6)
    }
}

private data class FourElementsClassPickerItem(val name: String, val discipline: String, val color: Int, val className: String, val time: String, val line: String, val price: String)
private val Items = listOf(
    FourElementsClassPickerItem("GROUND", "PILATES", 0xC7A186, "GROUNDING PILATES", "Today · 10:30 · 45 min", "A little strength. A softer landing.", "18"),
    FourElementsClassPickerItem("BREATHE", "BREATHWORK", 0x985B40, "OPEN AIR BREATHWORK", "Today · 12:00 · 20 min", "Find a little more room inside.", "12"),
    FourElementsClassPickerItem("KINDLE", "MOVEMENT", 0xC5A15B, "EMBER MOVEMENT", "Today · 17:30 · 50 min", "Move with warmth, leave with light.", "22"),
    FourElementsClassPickerItem("FLOW", "SLOW YOGA", 0x8E9F96, "SLOW CURRENT YOGA", "Today · 18:15 · 40 min", "Let the day loosen its grip.", "20"),
)

/** Four elemental classes, selected by a finger and traced by a moving ink tip. */
@Composable
fun FourElementsClassPicker(modifier: Modifier = Modifier, autoPlay: Boolean = false, previewTime: Double? = null) {
    val reduced = fourElementsClassPickerReducedMotion()
    val view = LocalView.current
    val density = LocalDensity.current.density
    val c = remember { FourElementsClassPickerMotion() }
    val painter = remember(density) { FourElementsClassPickerPainter(density) }
    val background = remember(painter) { painter.background.asImageBitmap() }
    val scope = rememberCoroutineScope()
    val reservationInteraction = remember { MutableInteractionSource() }

    LaunchedEffect(c, reduced, autoPlay, previewTime, c.manualLive) {
        if (reduced || previewTime != null || (!autoPlay && !c.manualLive)) return@LaunchedEffect
        while (true) withFrameNanos { c.frame = it / 1e9 }
    }
    DisposableEffect(c) { onDispose { c.resume?.cancel() } }
    DisposableEffect(view) {
        val activity = generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull()
        val controller = activity?.let { WindowCompat.getInsetsController(it.window, view) }
        val status = controller?.isAppearanceLightStatusBars
        val navigation = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = true; controller?.isAppearanceLightNavigationBars = true
        onDispose {
            if (status != null) controller.isAppearanceLightStatusBars = status
            if (navigation != null) controller.isAppearanceLightNavigationBars = navigation
        }
    }

    fun resumeLater() {
        c.resume?.cancel()
        c.resume = scope.launch {
            delay(2650)
            if (!c.dragging && !c.detailDragging) { c.manualLive = false; c.frame = nowSeconds() }
        }
    }
    fun touch() { c.lastTouch = nowSeconds(); c.frame = c.lastTouch; c.manualLive = true }
    fun select(index: Int, duration: Double = 0.65) {
        c.frame = nowSeconds()
        c.manualBase = c.pose(reduced, autoPlay, previewTime)
        c.manualIndex = index; c.traceStarted = c.frame; c.traceDuration = duration
        c.lastTouch = c.frame; c.manualLive = true; c.spaceHeld = false
        if (c.frame - c.lastHaptic > 0.12) { c.lastHaptic = c.frame; view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
        resumeLater()
    }
    fun reserve() {
        c.frame = nowSeconds()
        select(c.pose(reduced, autoPlay, previewTime).selected)
        c.traceStarted = c.frame - c.traceDuration; c.spaceHeld = true
        c.frame = nowSeconds()
    }

    BoxWithConstraints(modifier.fillMaxSize().background(Color(0xFFF6F2E9)), contentAlignment = Alignment.Center) {
        val fit = min(maxWidth.value / 393, maxHeight.value / 852)
        Box(Modifier.requiredSize(393.dp, 852.dp).graphicsLayer { scaleX = fit; scaleY = fit }) {
            Image(background, null, Modifier.fillMaxSize())
            Canvas(Modifier.fillMaxSize()) {
                val canvas = drawContext.canvas.nativeCanvas
                canvas.save(); canvas.scale(density, density)
                painter.draw(canvas, c.pose(reduced, autoPlay, previewTime), c.spaceHeld && c.frame - c.lastTouch < 2.4)
                canvas.restore()
            }
            Box(Modifier.offset(31.dp, 250.dp).size(331.dp, 332.dp).pointerInput(c, reduced, previewTime) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val tracker = VelocityTracker(); tracker.addPointerInputChange(down)
                    var dragged = false
                    var dragIndex = -1
                    fun indexAt(x: Float, y: Float) = (if (y / density >= 175) 2 else 0) + (if (x / density >= 172) 1 else 0)
                    c.resume?.cancel()
                    try {
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            tracker.addPointerInputChange(change)
                            if (!change.pressed) {
                                if (!dragged) select(indexAt(change.position.x, change.position.y))
                                break
                            }
                            val delta = (change.position - down.position) / density
                            if (!dragged && hypot(delta.x, delta.y) >= 3) dragged = true
                            if (dragged) {
                                val index = indexAt(change.position.x, change.position.y)
                                if (index != dragIndex) {
                                    dragIndex = index
                                    val velocity = tracker.calculateVelocity()
                                    select(index, if (hypot(velocity.x, velocity.y) / density > 500) 0.42 else 0.65)
                                }
                                c.dragging = true; touch(); change.consume()
                            }
                        }
                    } finally {
                        c.dragging = false
                        if (dragged) touch()
                        resumeLater()
                    }
                }
            }) {
                for (index in 0 until 4) Box(Modifier.offset(((index % 2) * 172).dp, ((index / 2) * 175).dp).size(159.dp, 157.dp).semantics {
                    role = Role.Button
                    contentDescription = "${Items[index].name}, ${Items[index].discipline}"
                    stateDescription = if (c.pose(reduced, autoPlay, previewTime).selected == index) "Selected" else ""
                    onClick { select(index); true }
                })
            }
            Box(Modifier.offset(31.dp, 610.dp).size(331.dp, 101.dp)
                .semantics {
                    val pose = c.pose(reduced, autoPlay, previewTime)
                    contentDescription = Items[if (pose.detailMix < 0.5) pose.detailFrom else pose.detailTo].className
                    stateDescription = "Swipe left or right to choose a class"
                    progressBarRangeInfo = ProgressBarRangeInfo(pose.selected.toFloat(), 0f..3f, 2)
                    setProgress { select(rounded(it.toDouble()).toInt().coerceIn(0, 3)); true }
                }
                .pointerInput(c, reduced, previewTime) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val tracker = VelocityTracker(); tracker.addPointerInputChange(down)
                        var started = false
                        var startIndex = 0
                        var dx = 0f
                        c.resume?.cancel()
                        try {
                            while (true) {
                                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                tracker.addPointerInputChange(change)
                                dx = (change.position.x - down.position.x) / density
                                if (!change.pressed) {
                                    if (started) {
                                        val projected = startIndex - dx / 86.0 - tracker.calculateVelocity().x / density * 0.0009
                                        val index = rounded(projected).toInt().coerceIn(0, 3)
                                        if (index != c.manualIndex) select(index)
                                    }
                                    break
                                }
                                if (!started && abs(dx) >= 4) {
                                    c.frame = nowSeconds(); startIndex = c.pose(reduced, autoPlay, previewTime).selected
                                    select(startIndex); c.detailDragging = true; started = true
                                }
                                if (started) {
                                    val index = rounded(startIndex - dx / 86.0).toInt().coerceIn(0, 3)
                                    if (index != c.manualIndex || !c.manualLive) select(index)
                                    touch(); change.consume()
                                }
                            }
                        } finally {
                            c.detailDragging = false
                            if (started) { touch(); resumeLater() }
                        }
                    }
                })
            Box(Modifier.offset(31.dp, 730.dp).size(331.dp, 48.dp).semantics {
                contentDescription = if (c.spaceHeld && c.frame - c.lastTouch < 2.4) "Space held for you" else "Reserve your space"
            }.clickable(interactionSource = reservationInteraction, indication = null, role = Role.Button) { reserve() })
        }
    }
}

/** Curves, arc-length lookup and native contours are built once per component. */
private class FourElementsClassPickerMark(val path: Path, val lookup: List<PointF>) {
    private val contours = mutableListOf<PathMeasure>()
    private var length = 0f
    private val scratch = Path()
    init {
        val probe = PathMeasure(path, false)
        var index = 0
        do {
            if (probe.length > 0) {
                val measure = PathMeasure(path, false)
                repeat(index) { measure.nextContour() }
                contours.add(measure); length += measure.length
            }
            index++
        } while (probe.nextContour())
    }
    fun trim(from: Double, to: Double): Path {
        scratch.reset()
        val start = from.toFloat() * length
        val end = to.toFloat() * length
        var before = 0f
        for (measure in contours) {
            val a = max(0f, start - before)
            val b = min(measure.length, end - before)
            if (b > a) measure.getSegment(a, b, scratch, true)
            before += measure.length
        }
        return scratch
    }
    fun point(fraction: Double): PointF {
        val position = (fraction * 127).coerceIn(0.0, 127.0)
        val lower = position.toInt(); val upper = min(127, lower + 1); val u = (position - lower).toFloat()
        return PointF(lookup[lower].x + (lookup[upper].x - lookup[lower].x) * u, lookup[lower].y + (lookup[upper].y - lookup[lower].y) * u)
    }
}

private class FourElementsClassPickerMarkBuilder {
    private val path = Path()
    private val points = mutableListOf<PointF>()
    private val distances = mutableListOf<Double>()
    private var length = 0.0
    private var current = PointF()
    fun move(x: Double, y: Double) {
        current = PointF(x.toFloat(), y.toFloat()); path.moveTo(current.x, current.y)
        points.add(current); distances.add(length)
    }
    fun curve(x1: Double, y1: Double, x2: Double, y2: Double, x: Double, y: Double) {
        val start = current
        path.cubicTo(x1.toFloat(), y1.toFloat(), x2.toFloat(), y2.toFloat(), x.toFloat(), y.toFloat())
        for (step in 1..24) {
            val u = step / 24.0; val v = 1 - u
            val p = PointF((v * v * v * start.x + 3 * v * v * u * x1 + 3 * v * u * u * x2 + u * u * u * x).toFloat(), (v * v * v * start.y + 3 * v * v * u * y1 + 3 * v * u * u * y2 + u * u * u * y).toFloat())
            length += hypot((p.x - current.x).toDouble(), (p.y - current.y).toDouble())
            points.add(p); distances.add(length); current = p
        }
    }
    fun circle(x: Double, y: Double, r: Double) {
        val k = r * 0.5522847498
        move(x, y - r); curve(x + k, y - r, x + r, y - k, x + r, y)
        curve(x + r, y + k, x + k, y + r, x, y + r); curve(x - k, y + r, x - r, y + k, x - r, y)
        curve(x - r, y - k, x - k, y - r, x, y - r)
    }
    fun finish(): FourElementsClassPickerMark {
        var cursor = 1
        val lookup = List(128) { step ->
            val wanted = step / 127.0 * length
            while (cursor < distances.size - 1 && distances[cursor] < wanted) cursor++
            val previous = max(0, cursor - 1)
            val span = distances[cursor] - distances[previous]
            val u = if (span > 0) (wanted - distances[previous]) / span else 1.0
            PointF((points[previous].x + (points[cursor].x - points[previous].x) * u).toFloat(), (points[previous].y + (points[cursor].y - points[previous].y) * u).toFloat())
        }
        return FourElementsClassPickerMark(path, lookup)
    }
}

private fun marks(): List<FourElementsClassPickerMark> = listOf(
    FourElementsClassPickerMarkBuilder().apply {
        move(50.0, 9.0); curve(17.0, 32.0, 16.0, 65.0, 50.0, 91.0); curve(84.0, 65.0, 83.0, 32.0, 50.0, 9.0)
        move(50.0, 21.0); curve(39.0, 42.0, 40.0, 63.0, 50.0, 81.0); circle(79.0, 14.0, 3.5)
    }.finish(),
    FourElementsClassPickerMarkBuilder().apply {
        move(50.0, 40.0); curve(34.0, 43.0, 31.0, 56.0, 48.0, 64.0); curve(62.0, 71.0, 74.0, 75.0, 81.0, 82.0)
        curve(89.0, 91.0, 66.0, 96.0, 50.0, 78.0); curve(34.0, 96.0, 11.0, 91.0, 19.0, 82.0)
        curve(28.0, 72.0, 42.0, 70.0, 55.0, 63.0); curve(69.0, 54.0, 66.0, 43.0, 50.0, 40.0); circle(50.0, 25.0, 8.0)
    }.finish(),
    FourElementsClassPickerMarkBuilder().apply {
        move(43.0, 92.0); curve(66.0, 72.0, 62.0, 53.0, 48.0, 35.0); curve(40.0, 23.0, 33.0, 15.0, 23.0, 12.0)
        move(20.0, 39.0); curve(36.0, 45.0, 47.0, 48.0, 73.0, 36.0); move(49.0, 60.0); curve(36.0, 74.0, 35.0, 83.0, 33.0, 92.0)
        circle(58.0, 16.0, 4.5); circle(80.0, 28.0, 3.0)
    }.finish(),
    FourElementsClassPickerMarkBuilder().apply {
        move(9.0, 66.0); curve(22.0, 25.0, 37.0, 29.0, 49.0, 55.0); curve(62.0, 83.0, 77.0, 82.0, 91.0, 43.0)
        move(14.0, 47.0); curve(29.0, 17.0, 44.0, 32.0, 55.0, 46.0); circle(84.0, 19.0, 3.5)
    }.finish(),
)

private class FourElementsClassPickerPainter(density: Float) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val normal = Typeface.create("sans-serif", Typeface.NORMAL)
    private val medium = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private val semibold = if (Build.VERSION.SDK_INT >= 28) Typeface.create(normal, 600, false) else Typeface.create(normal, Typeface.BOLD)
    private val serif = Typeface.create("serif", Typeface.NORMAL)
    private val marks = marks()
    val background = Bitmap.createBitmap((393 * density).roundToInt(), (852 * density).roundToInt(), Bitmap.Config.ARGB_8888).also {
        val canvas = AndroidCanvas(it); canvas.scale(density, density); static(canvas)
    }
    private fun rgba(hex: Int, alpha: Double = 1.0) = AndroidColor.argb((clamp(alpha) * 255).roundToInt(), hex shr 16 and 255, hex shr 8 and 255, hex and 255)
    private fun fill(hex: Int, alpha: Double = 1.0) { paint.shader = null; paint.maskFilter = null; paint.color = rgba(hex, alpha); paint.style = Paint.Style.FILL }
    private fun stroke(hex: Int, width: Float, alpha: Double = 1.0) { fill(hex, alpha); paint.style = Paint.Style.STROKE; paint.strokeWidth = width; paint.strokeCap = Paint.Cap.ROUND; paint.strokeJoin = Paint.Join.ROUND }
    private fun label(c: AndroidCanvas, word: String, x: Float, y: Float, size: Float, hex: Int, font: Typeface = normal, tracking: Float = 0f, left: Boolean = false, alpha: Double = 1.0) {
        text.typeface = font; text.textSize = size; text.color = rgba(hex, alpha)
        val widths = FloatArray(word.length); text.getTextWidths(word, widths)
        var cursor = if (left) x else x - (widths.sum() + max(0, word.length - 1) * tracking) / 2
        val baseline = y - (text.fontMetrics.ascent + text.fontMetrics.descent) / 2
        word.forEachIndexed { i, char -> c.drawText(char.toString(), cursor, baseline, text); cursor += widths[i] + tracking }
    }
    private fun static(c: AndroidCanvas) {
        c.drawColor(rgba(0xF6F2E9))
        fun hash(value: Double): Float { val n = sin(value * 12.9898) * 43758.5453; return (n - floor(n)).toFloat() }
        stroke(0x985B40, 0.55f, 0.025)
        for (i in 0 until 160) {
            val x = hash(i + 0.3) * 393; val y = hash(i + 24.7) * 852
            c.drawLine(x, y, x + 2 + hash(i + 92.1) * 5, y - 0.8f, paint)
        }
        stroke(0x985B40, 2.5f)
        for (i in 0 until 3) {
            val path = Path().apply { moveTo(28f, 92f + i * 7); quadTo(35f, 90f + i * 7, 46f - i * 5, 92f + i * 7) }
            c.drawPath(path, paint)
        }
        label(c, "Veyra", 196.5f, 91f, 42f, 0x985B40, serif, -2f)
        label(c, "WHOLE DAYS", 198f, 131f, 8f, 0xAE8B71, medium, 3f)
        val bell = Path().apply { moveTo(349f, 103f); lineTo(361f, 103f); cubicTo(358f, 100f, 360f, 93f, 355f, 92f); cubicTo(350f, 93f, 352f, 100f, 349f, 103f) }
        stroke(0x985B40, 1.1f); c.drawPath(bell, paint); c.drawArc(RectF(353f, 102f, 357f, 106f), 0f, 180f, false, paint)
        label(c, "What feels right", 196.5f, 180f, 21f, 0x9D7B64, medium)
        label(c, "for you today?", 196.5f, 207f, 21f, 0x9D7B64, medium)
        label(c, "Swipe across the elements", 196.5f, 801f, 10f, 0xA89787)
    }
    private fun mark(c: AndroidCanvas, index: Int, card: FourElementsClassPickerCard) {
        val mark = marks[index]
        val ink = Items[index].color
        stroke(ink, 3.1f, card.guide); c.drawPath(mark.path, paint)
        if (card.oldAlpha > 0.001) { stroke(ink, 3.1f, card.oldAlpha); c.drawPath(mark.trim(0.0, card.oldProgress), paint) }
        if (card.alpha > 0.001 && card.progress > 0.0001) {
            stroke(ink, (3.1 + 0.5 * card.tip).toFloat(), card.alpha); c.drawPath(mark.trim(0.0, card.progress), paint)
        }
        if (card.tip > 0.001 && card.progress > 0 && card.progress < 1.0001) {
            stroke(0xFFFFFF, 1.5f, 0.35 * card.tip); c.drawPath(mark.trim(max(0.0, card.progress - 0.12), card.progress), paint)
            val p = mark.point(card.progress)
            fill(ink, 0.22 * card.tip); paint.maskFilter = BlurMaskFilter(4f, BlurMaskFilter.Blur.NORMAL); c.drawCircle(p.x, p.y, 6f, paint)
            fill(ink, card.tip); c.drawCircle(p.x, p.y, 3.2f, paint)
            fill(0xFFFFFF, 0.65 * card.tip); c.drawCircle(p.x, p.y, 1f, paint)
        }
    }
    private fun tile(c: AndroidCanvas, index: Int, card: FourElementsClassPickerCard) {
        val item = Items[index]
        c.save(); c.translate(31f + (index % 2) * 172, 250f + (index / 2) * 175 - card.lift.toFloat())
        fill(0x89725E, 0.10 + card.selection * 0.04)
        paint.maskFilter = BlurMaskFilter((8 + card.selection * 6).toFloat(), BlurMaskFilter.Blur.NORMAL)
        val drop = (5 + card.selection * 4).toFloat(); c.drawRoundRect(0f, drop, 159f, 157f + drop, 12f, 12f, paint)
        fill(0x000000, 0.07); paint.maskFilter = BlurMaskFilter(2f, BlurMaskFilter.Blur.NORMAL); c.drawRoundRect(0f, 1f, 159f, 158f, 12f, 12f, paint)
        fill(0xFFFDFA); c.drawRoundRect(0f, 0f, 159f, 157f, 12f, 12f, paint)
        fill(item.color, 0.035 * card.selection); c.drawRoundRect(0f, 0f, 159f, 157f, 12f, 12f, paint)
        stroke(0xF4EFE8, 1f); c.drawRoundRect(0f, 0f, 159f, 157f, 12f, 12f, paint)
        stroke(item.color, 1f, 0.4 * card.selection); c.drawRoundRect(0f, 0f, 159f, 157f, 12f, 12f, paint)
        c.save(); c.translate(37.5f, 19f); c.scale(0.84f, 0.84f); mark(c, index, card); c.restore()
        label(c, item.name, 79.5f, 115f, 9f, item.color, semibold, 2f)
        label(c, item.discipline, 79.5f, 134f, 7f, 0xA99786, medium, 1.3f)
        val width = (46 * card.progress).toFloat()
        fill(item.color, 0.38 * card.selection); c.drawRoundRect(79.5f - width / 2, 123.6f, 79.5f + width / 2, 124.4f, 0.4f, 0.4f, paint)
        c.restore()
    }
    private fun detail(c: AndroidCanvas, index: Int) {
        val item = Items[index]
        c.save(); c.translate(18f, 21f); c.scale(0.26f, 0.26f); mark(c, index, FourElementsClassPickerCard.complete()); c.restore()
        label(c, item.className, 57f, 23.5f, 10f, 0x8A684F, semibold, 1.1f, left = true)
        label(c, item.time, 57f, 44f, 11f, 0x9A8571, left = true)
        label(c, item.line, 18f, 71.5f, 11f, 0x9E8772, left = true)
    }
    fun draw(c: AndroidCanvas, pose: FourElementsClassPickerPose, held: Boolean) {
        for (index in 0 until 4) tile(c, index, pose.cards[index])
        fill(0xEBE3D4); c.drawRoundRect(31f, 610f, 362f, 711f, 15f, 15f, paint)
        stroke(0xFFFFFF, 1f, 0.4); c.drawRoundRect(31f, 610f, 362f, 711f, 15f, 15f, paint)
        val mix = pose.detailMix
        val oldAlpha = max(0.0, 1 - mix * 2); val newAlpha = max(0.0, mix * 2 - 1)
        if (oldAlpha > 0) {
            val layer = c.saveLayerAlpha(RectF(31f, 606f, 362f, 715f), (oldAlpha * 255).roundToInt())
            c.translate(31f, 610f - (4 * mix).toFloat()); detail(c, pose.detailFrom); c.restoreToCount(layer)
        }
        if (newAlpha > 0) {
            val layer = c.saveLayerAlpha(RectF(31f, 606f, 362f, 715f), (newAlpha * 255).roundToInt())
            c.translate(31f, 610f + (4 * (1 - mix)).toFloat()); detail(c, pose.detailTo); c.restoreToCount(layer)
        }
        fill(0x9D6045); c.drawRoundRect(31f, 730f, 362f, 778f, 24f, 24f, paint)
        stroke(0xFFFFFF, 1f, 0.12); c.drawRoundRect(31f, 730f, 362f, 778f, 24f, 24f, paint)
        label(c, if (held) "Space held for you" else "Reserve your space", 55f, 754f, 12f, 0xFCF6ED, medium, left = true)
        if (oldAlpha > 0) label(c, Items[pose.detailFrom].price, 306.5f, 754f, 13f, 0xFCF6ED, medium, alpha = oldAlpha)
        if (newAlpha > 0) label(c, Items[pose.detailTo].price, 306.5f, 754f, 13f, 0xFCF6ED, medium, alpha = newAlpha)
        stroke(0xFCF6ED, 1.2f)
        val arrow = Path().apply {
            if (held) { moveTo(327f, 754f); lineTo(331f, 758f); lineTo(338f, 750f) }
            else { moveTo(326f, 754f); lineTo(338f, 754f); moveTo(333f, 749f); lineTo(338f, 754f); lineTo(333f, 759f) }
        }
        c.drawPath(arrow, paint)
    }
}

@Composable
private fun fourElementsClassPickerReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    fun read() = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    var reduced by remember(resolver) { mutableStateOf(read()) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) { override fun onChange(selfChange: Boolean) { reduced = read() } }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return reduced
}
