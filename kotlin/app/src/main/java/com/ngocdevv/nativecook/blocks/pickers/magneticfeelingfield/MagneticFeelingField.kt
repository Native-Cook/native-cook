package com.ngocdevv.nativecook.blocks.pickers.magneticfeelingfield

import android.app.Activity
import android.content.ContextWrapper
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val LOOP = 8.0
private val Ink = Color(0.15f, 0.18f, 0.17f)
private val Background = Color(0.933f, 0.941f, 0.918f)
private val Feelings = listOf("Serene", "Wonder", "Tender")

private fun nowSeconds() = System.nanoTime() / 1e9
private fun smooth(value: Double): Double {
    val t = value.coerceIn(0.0, 1.0)
    return t * t * (3 - 2 * t)
}

private val Route = listOf(Offset(.5f, .5f), Offset(.82f, .42f), Offset(.38f, .18f), Offset(.20f, .66f), Offset(.60f, .82f), Offset(.85f, .36f), Offset(.80f, .68f), Offset(.72f, .48f), Offset(.5f, .5f))
private val RouteTimes = doubleArrayOf(1.1, 1.85, 2.6, 3.4, 4.2, 5.0, 5.8, 6.5, 7.45)
private fun script(t: Double): Offset {
    if (t <= RouteTimes[0]) return Route[0]
    for (i in 1 until RouteTimes.size) if (t < RouteTimes[i]) {
        val u = smooth((t - RouteTimes[i - 1]) / (RouteTimes[i] - RouteTimes[i - 1])).toFloat()
        return Route[i - 1] + (Route[i] - Route[i - 1]) * u
    }
    return Route.last()
}

private val ToneKnots = listOf(Color(.45f,.72f,.80f), Color(.55f,.63f,.83f), Color(.68f,.59f,.79f), Color(.83f,.58f,.73f), Color(.88f,.61f,.57f), Color(.88f,.73f,.43f), Color(.73f,.78f,.35f), Color(.47f,.76f,.66f), Color(.45f,.72f,.80f))
private fun mix(a: Color, b: Color, t: Float) = Color(a.red + (b.red - a.red) * t, a.green + (b.green - a.green) * t, a.blue + (b.blue - a.blue) * t)
private fun tone(point: Offset): Color {
    val angle = (atan2((point.y - .5), (point.x - .5)) / (2 * PI) + 1) % 1
    val position = angle * 8; val i = min(7, position.toInt())
    return mix(ToneKnots[i], ToneKnots[i + 1], (position - i).toFloat())
}
private fun dotTone(point: Offset, activity: Float) = mix(Color(.64f,.66f,.67f), tone(point), smooth(activity.toDouble() * 2).toFloat())

private fun mood(x: Float) = if (x > 0.65f) 1 else if (x < 0.35f) 2 else 0

/** A glyph keeps its exact pose when a new touch interrupts the previous word. */
private data class MagneticFeelingFieldLabelGlyph(
    val character: String,
    val center: Double,
    val width: Double,
    val scaleX: Double = 1.0,
    val blur: Double = 0.0,
    val opacity: Double = 1.0,
)

private data class MagneticFeelingFieldLabelTransition(
    val from: List<MagneticFeelingFieldLabelGlyph>,
    val target: Int,
    val started: Double,
) {
    fun pose(time: Double): List<MagneticFeelingFieldLabelGlyph> {
        val destination = word(target)
        return from.mapIndexed { index, old ->
            val next = destination[index]
            val u = ((time - started - index * STAGGER) / DURATION).coerceIn(0.0, 1.0)
            when {
                u <= 0 -> old
                u >= 1 -> next
                else -> {
                    val travel = smooth(u)
                    val center = old.center + (next.center - old.center) * travel
                    when {
                        old.character == next.character -> next.copy(
                            center = center,
                            scaleX = old.scaleX + (1 - old.scaleX) * travel,
                            blur = old.blur * (1 - travel),
                            opacity = old.opacity + (1 - old.opacity) * travel,
                        )
                        u < 0.5 -> {
                            val p = smooth(u * 2)
                            old.copy(center = center, scaleX = old.scaleX + (0.25 - old.scaleX) * p,
                                blur = old.blur + (3 - old.blur) * p, opacity = old.opacity + (0.38 - old.opacity) * p)
                        }
                        else -> {
                            val p = smooth((u - 0.5) * 2)
                            next.copy(center = center, scaleX = 0.25 + 0.75 * p,
                                blur = 3 * (1 - p), opacity = 0.38 + 0.62 * p)
                        }
                    }
                }
            }
        }
    }

    companion object {
        const val STAGGER = 0.019
        const val DURATION = 0.12
        fun fontPaint() = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            textSize = 20.5f; letterSpacing = -.4f / 20.5f
            typeface = if (android.os.Build.VERSION.SDK_INT >= 28) Typeface.create(Typeface.DEFAULT, 600, false)
                else Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        private val layouts = fontPaint().let { font ->
            Feelings.map { name ->
                name.mapIndexed { index, character ->
                    val left = font.getRunAdvance(name, 0, name.length, 0, name.length, false, index).toDouble()
                    val width = font.measureText(character.toString()).toDouble()
                    MagneticFeelingFieldLabelGlyph(character.toString(), left + width / 2, width)
                }
            }
        }
        fun word(index: Int) = layouts[index]
        fun rest() = MagneticFeelingFieldLabelTransition(word(0), 0, 0.0)

        // Derive crossing events once; drawing never writes a mood or transition state.
        private val events = buildList {
            add(rest())
            var previous = 0
            for (step in 1..1920) {
                val time = step / 240.0
                val next = mood(script(time).x)
                if (next == previous) continue
                var lower = (step - 1) / 240.0; var upper = time
                repeat(24) {
                    val mid = (lower + upper) / 2
                    if (mood(script(mid).x) == previous) lower = mid else upper = mid
                }
                val start = (lower + upper) / 2
                add(MagneticFeelingFieldLabelTransition(last().pose(start), next, start))
                previous = next
            }
        }
        fun loopPose(raw: Double): List<MagneticFeelingFieldLabelGlyph> {
            val t = ((raw % LOOP) + LOOP) % LOOP
            return (events.lastOrNull { it.started <= t } ?: rest()).pose(t)
        }
        fun returnCrossing(x: Float): Double? {
            if (x <= .65f && x >= .35f) return null
            val p = if (x > .65f) (x - .65) / (x - .5) else (.35 - x) / (.5 - x)
            var lower = 0.0; var upper = 1.0
            repeat(24) {
                val mid = (lower + upper) / 2
                if (smooth(mid) < p) lower = mid else upper = mid
            }
            return (lower + upper) / 2
        }
    }
}

/** Only drawing and layer lambdas observe the continuously updated frame. */
private class MagneticFeelingFieldMotion {
    var frame by mutableDoubleStateOf(nowSeconds())
    var active by mutableStateOf(false)
    var epoch = nowSeconds()
    var touched: Double? = null
    var finger = Offset(0.5f, 0.5f)
    var held = false
    var velocity = Offset.Zero
    var lastMood = 0
    var lastHaptic = Double.NEGATIVE_INFINITY
    var resume: Job? = null
    var labelTransition = MagneticFeelingFieldLabelTransition.rest()

    fun labelPose(reduced: Boolean, autoPlay: Boolean, previewTime: Double?): List<MagneticFeelingFieldLabelGlyph> {
        val now = frame
        if (reduced) return MagneticFeelingFieldLabelTransition.word(mood(focus(true, autoPlay, previewTime).x))
        if (previewTime != null) return MagneticFeelingFieldLabelTransition.loopPose(previewTime)
        touched?.let { touch ->
            if (!held) MagneticFeelingFieldLabelTransition.returnCrossing(finger.x)?.let { crossing ->
                val start = touch + 2 + .6 * crossing
                if (now >= start) return MagneticFeelingFieldLabelTransition(labelTransition.pose(start), 0, start).pose(now)
            }
            return labelTransition.pose(now)
        }
        return if (autoPlay) MagneticFeelingFieldLabelTransition.loopPose(max(0.0, now - epoch))
            else MagneticFeelingFieldLabelTransition.word(0)
    }

    fun retargetLabel(next: Offset, reduced: Boolean, autoPlay: Boolean, previewTime: Double?, capturing: Boolean) {
        val target = mood(next.x)
        if (!capturing && target == labelTransition.target) return
        labelTransition = MagneticFeelingFieldLabelTransition(labelPose(reduced, autoPlay, previewTime), target, frame)
    }

    fun focus(reduced: Boolean, autoPlay: Boolean, previewTime: Double?): Offset {
        val now = frame
        if (previewTime != null) return script(previewTime % LOOP)
        if (reduced) return if (touched == null) Offset(0.5f, 0.5f) else finger
        touched?.let { touch ->
            val elapsed = now - touch
            if (held || elapsed < 2) return finger
            if (elapsed < 2.6) return finger + (Offset(0.5f, 0.5f) - finger) * smooth((elapsed - 2) / 0.6).toFloat()
        }
        return if (autoPlay) script(max(0.0, now - epoch) % LOOP) else Offset(0.5f, 0.5f)
    }

    fun drift(reduced: Boolean, autoPlay: Boolean, previewTime: Double?): Offset {
        if (reduced) return Offset.Zero
        touched?.let { touch -> return velocity * exp(-max(0.0, frame - touch) * 8).toFloat() }
        if (!autoPlay) return Offset.Zero
        val t = (previewTime ?: max(0.0, frame - epoch)) % LOOP
        val a = script(t)
        val b = script(max(0.0, t - 0.08))
        return Offset((a.x - b.x) * 296 * 0.35f, (a.y - b.y) * 208 * 0.35f)
    }
    fun activity(focus: Offset): Float {
        val now = frame
        var amount = smooth(hypot((focus.x - .5), (focus.y - .5)) / .22)
        touched?.let { amount = max(amount, if (held) 1.0 else 1 - smooth((now - it - 2) / .6)) }
        return (.18 + .82 * amount).toFloat()
    }

}

/** A dream journal whose dots gather around a finger's emotional trace. */
@Composable
fun MagneticFeelingField(modifier: Modifier = Modifier, autoPlay: Boolean = false, previewTime: Double? = null) {
    val reduced = magneticFeelingFieldReducedMotion()
    val view = LocalView.current
    val density = LocalDensity.current.density
    val c = remember { MagneticFeelingFieldMotion() }
    val artwork = remember { MagneticFeelingFieldArtwork() }
    val scope = rememberCoroutineScope()
    var saved by remember { mutableStateOf(false) }

    LaunchedEffect(c, autoPlay, reduced, previewTime, c.active) {
        if (reduced || previewTime != null || (!autoPlay && !c.active)) return@LaunchedEffect
        while (true) withFrameNanos { c.frame = it / 1e9 }
    }
    DisposableEffect(c) { onDispose { c.resume?.cancel() } }
    DisposableEffect(view) {
        val activity = generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }
            .filterIsInstance<Activity>().firstOrNull()
        val controller = activity?.let { WindowCompat.getInsetsController(it.window, view) }
        val previousStatus = controller?.isAppearanceLightStatusBars
        val previousNavigation = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = true
        controller?.isAppearanceLightNavigationBars = true
        onDispose {
            if (previousStatus != null) controller.isAppearanceLightStatusBars = previousStatus
            if (previousNavigation != null) controller.isAppearanceLightNavigationBars = previousNavigation
        }
    }

    fun release() {
        c.held = false
        c.touched = nowSeconds()
        c.epoch = c.touched!! + 2.6
        c.frame = nowSeconds()
        c.resume?.cancel()
        c.resume = scope.launch {
            delay(2600)
            c.touched = null
            c.active = false
            c.frame = nowSeconds()
        }
    }

    fun selectFeeling(value: Float) {
        c.resume?.cancel()
        c.frame = nowSeconds()
        val next = Offset(value.coerceIn(0f, 1f), 0.5f)
        c.retargetLabel(next, reduced, autoPlay, previewTime, capturing = true)
        c.active = true
        c.finger = next
        c.velocity = Offset.Zero
        release()
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    BoxWithConstraints(modifier.fillMaxSize().background(Background), contentAlignment = Alignment.Center) {
        val fit = min(maxWidth.value / 393f, maxHeight.value / 852f)
        Box(Modifier.requiredSize(393.dp, 852.dp).graphicsLayer { scaleX = fit; scaleY = fit }) {
            Canvas(Modifier.fillMaxSize()) {
                val focus = c.focus(reduced, autoPlay, previewTime)
                val tint = tone(focus); val activity = c.activity(focus)
                scale(density, density, pivot = Offset.Zero) {
                    artwork.bloom(drawContext.canvas.nativeCanvas, tint, activity)
                    artwork.shadow(drawContext.canvas.nativeCanvas)
                    drawRoundRect(Color.White.copy(alpha = .64f), Offset(7f, 370f), Size(379f, 371f), CornerRadius(38f))
                    val card = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(7f, 370f, 386f, 741f, 38f, 38f)) }
                    clipPath(card) {
                        drawRect(Brush.radialGradient(
                            0f to tint.copy(alpha = .43f * activity), .0381f to tint.copy(alpha = .43f * activity), .519f to tint.copy(alpha = .19f * activity), 1f to tint.copy(alpha = 0f),
                            center = Offset(7 + focus.x * 379, 370 + focus.y * 371), radius = 315f,
                        ), Offset(7f, 370f), Size(379f, 371f))
                    }
                    drawRoundRect(Color.White.copy(alpha = .62f), Offset(7f, 370f), Size(379f, 371f), CornerRadius(38f), style = Stroke(.8f))
                    drawRoundRect(Color.White.copy(alpha = .29f), Offset(24.5f, 467f), Size(344f, 255f), CornerRadius(30f))
                    drawRoundRect(Brush.linearGradient(listOf(Color.White.copy(alpha = .72f), Color.White.copy(alpha = .11f), Color.White.copy(alpha = .58f)), start = Offset(24.5f, 467f), end = Offset(368.5f, 722f)), Offset(24.5f, 467f), Size(344f, 255f), CornerRadius(30f), style = Stroke(.8f))
                }
            }
            Row(Modifier.offset(24.dp, 80.dp).width(345.dp), verticalAlignment = Alignment.CenterVertically) {
                Label("REVERIE / JOURNAL", 10f, weight = FontWeight.SemiBold, mono = true, tracking = 2f)
                Spacer(Modifier.weight(1f))
                Moon(Modifier.size(22.dp))
            }
            Label("A trace of\nlast night.", 38f, Modifier.offset(24.dp, 113.dp), serif = true, tracking = -1.4f, lineHeight = 42f)
            Label("FRIDAY, OCTOBER 16", 10f, Modifier.offset(24.dp, 218.dp), color = Ink.copy(alpha = 0.48f), weight = FontWeight.Medium, mono = true, tracking = 1.5f)
            Box(Modifier.offset(24.dp, 245.dp).size(345.dp, 112.dp).background(Color.White.copy(alpha = 0.42f), androidx.compose.foundation.shape.RoundedCornerShape(22.dp))) {
                Row(Modifier.offset(20.dp, 20.dp).width(305.dp)) {
                    Label("THE FLOATING GARDEN", 10f, weight = FontWeight.SemiBold, color = Ink.copy(alpha = 0.65f), tracking = 1.5f)
                    Spacer(Modifier.weight(1f))
                    Label("06:42", 10f, mono = true, color = Ink.copy(alpha = 0.65f))
                }
                Label("I followed a paper boat through a garden\nwhere the flowers floated in the air.", 15f, Modifier.offset(20.dp, 44.dp), color = Ink.copy(alpha = 0.65f), lineHeight = 23f)
            }
            Box(Modifier.offset(35.dp, 396.dp).size(323.dp, 54.dp).clearAndSetSemantics {
                contentDescription = "This dream left me feeling ${Feelings[mood(c.focus(reduced, autoPlay, previewTime).x)]}"
            }) {
                Label("This dream left me feeling", 20.5f, weight = FontWeight.SemiBold, tracking = -.4f)
                Canvas(Modifier.offset(y = 25.5.dp).size(323.dp, 29.dp)) {
                    val focus = c.focus(reduced, autoPlay, previewTime)
                    val glyphs = c.labelPose(reduced, autoPlay, previewTime)
                    scale(density, density, pivot = Offset.Zero) {
                        artwork.mood(drawContext.canvas.nativeCanvas, tone(focus), glyphs)
                    }
                }
            }
            Canvas(Modifier.offset(24.5.dp, 467.dp).size(344.dp, 255.dp)) {
                val focus = c.focus(reduced, autoPlay, previewTime)
                val wake = c.drift(reduced, autoPlay, previewTime)
                val activity = c.activity(focus)
                val cx = 24 + focus.x * 296
                val cy = 23.5f + focus.y * 208
                scale(density, density, pivot = Offset.Zero) {
                    for (row in 0 until 11) for (col in 0 until 11) {
                        val x = col * 29.6f + 24
                        val y = row * 20.8f + 23.5f
                        val dx = cx - x; val dy = cy - y
                        val influence = exp(-(dx * dx + dy * dy) / 2550f)
                        val radius = 1.35f + 7.65f * influence * activity
                        val px = x + dx * influence * (if (reduced) 0f else .04f) - wake.y * influence * .18f
                        val py = y + dy * influence * (if (reduced) 0f else .04f) + wake.x * influence * .18f
                        val tint = dotTone(Offset(col / 10f, row / 10f), influence * activity)
                        drawCircle(tint.copy(alpha = .20f + (if (reduced) .23f else .63f) * influence * activity), radius, Offset(px, py))
                    }
                    if (!reduced) {
                        val radius = (24 + 12 * activity) / 2
                        artwork.halo(drawContext.canvas.nativeCanvas, cx, cy, radius, activity)
                        drawCircle(Brush.linearGradient(listOf(Color.White.copy(alpha = .46f), tone(focus).copy(alpha = .23f), Color.White.copy(alpha = .30f)), start = Offset(cx - 10, cy - 14), end = Offset(cx + 12, cy + 16)), radius, Offset(cx, cy))
                        drawCircle(Color.White.copy(alpha = .79f * activity), radius, Offset(cx, cy), style = Stroke(1.3f))
                    }
                }
            }
            Box(
                Modifier.offset(24.5.dp, 467.dp).size(344.dp, 255.dp)
                    .semantics {
                        contentDescription = "Feeling field"
                        val x = c.focus(reduced, autoPlay, previewTime).x
                        stateDescription = Feelings[mood(x)]
                        progressBarRangeInfo = ProgressBarRangeInfo(x, 0f..1f)
                        setProgress { selectFeeling(it); true }
                    }
                    .pointerInput(c, reduced, previewTime, fit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val tracker = VelocityTracker()
                            tracker.addPointerInputChange(down)
                            c.resume?.cancel()
                            c.active = true
                            fun move(point: Offset, speed: Offset) {
                                c.frame = nowSeconds()
                                val next = Offset(((point.x / density - 24) / 296).coerceIn(0f, 1f), ((point.y / density - 23.5f) / 208).coerceIn(0f, 1f))
                                c.retargetLabel(next, reduced, autoPlay, previewTime, capturing = !c.held)
                                c.held = true
                                c.touched = c.frame
                                c.finger = next
                                // Project the release 250 ms ahead, then apply Swift's 0.06 transverse drift.
                                c.velocity = Offset((speed.x / density * 0.25f * 0.06f).coerceIn(-12f, 12f), (speed.y / density * 0.25f * 0.06f).coerceIn(-12f, 12f))
                                c.frame = nowSeconds()
                                val nextMood = mood(c.finger.x)
                                if (nextMood != c.lastMood && c.frame - c.lastHaptic > 0.18) {
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    c.lastMood = nextMood
                                    c.lastHaptic = c.frame
                                }
                            }
                            move(down.position, Offset.Zero)
                            try {
                                while (true) {
                                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                    tracker.addPointerInputChange(change)
                                    if (!change.pressed) break
                                    val speed = tracker.calculateVelocity()
                                    move(change.position, Offset(speed.x, speed.y))
                                    change.consume()
                                }
                            } finally { release() }
                        }
                    },
            )
            Box(Modifier.offset(y = 748.dp).width(393.dp), contentAlignment = Alignment.TopCenter) {
                Label("SLIDE TO FIND THE FEELING", 9f, mono = true, weight = FontWeight.Medium, tracking = 1.6f, color = Ink.copy(alpha = 0.4f))
            }
            Row(
                Modifier.offset(24.dp, 774.dp).size(345.dp, 44.dp)
                    .clickable(interactionSource = null, indication = null, role = Role.Button) {
                        c.frame = nowSeconds()
                        val shown = c.focus(reduced, autoPlay, previewTime)
                        c.retargetLabel(shown, reduced, autoPlay, previewTime, capturing = true)
                        c.finger = shown
                        c.velocity = Offset.Zero
                        c.active = true
                        release()
                        saved = !saved
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    }.padding(vertical = 1.dp).background(Ink, CircleShape),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
            ) {
                Label(if (saved) "Feeling saved" else "Keep this feeling", 14f, weight = FontWeight.Medium, color = Color(0.95f, 0.96f, 0.93f))
                Spacer(Modifier.width(8.dp))
                Canvas(Modifier.size(14.dp)) {
                    val col = Color(0.95f, 0.96f, 0.93f)
                    scale(density, density, pivot = Offset.Zero) {
                        if (saved) {
                            val check = Path().apply { moveTo(1f, 7f); lineTo(5f, 11f); lineTo(13f, 2f) }
                            drawPath(check, col, style = Stroke(1.5f, cap = StrokeCap.Round))
                        } else {
                            drawLine(col, Offset(2f, 12f), Offset(12f, 2f), 1.3f, StrokeCap.Round)
                            val arrow = Path().apply { moveTo(4f, 2f); lineTo(12f, 2f); lineTo(12f, 10f) }
                            drawPath(arrow, col, style = Stroke(1.3f, cap = StrokeCap.Round))
                        }
                    }
                }
            }
        }
    }
}

private class MagneticFeelingFieldArtwork {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val text = MagneticFeelingFieldLabelTransition.fontPaint().apply { color = android.graphics.Color.WHITE }
    private val glyphPadding = 12f
    private val glyphs = Feelings.flatMap { it.map(Char::toString) }.distinct().associateWith { character ->
        val width = ceil(text.measureText(character) + glyphPadding * 2).toInt()
        val height = ceil(text.fontMetrics.descent - text.fontMetrics.ascent + glyphPadding * 2).toInt()
        List(31) { step ->
            text.maskFilter = if (step == 0) null else BlurMaskFilter(step / 10f, BlurMaskFilter.Blur.NORMAL)
            bitmap(width, height, 3) { canvas -> canvas.drawText(character, glyphPadding, glyphPadding - text.fontMetrics.ascent, text) }
        }
    }
    private fun bitmap(w: Int, h: Int, resolution: Int = 2, draw: (AndroidCanvas) -> Unit) = Bitmap.createBitmap(w * resolution, h * resolution, Bitmap.Config.ARGB_8888).also {
        AndroidCanvas(it).apply { scale(resolution.toFloat(), resolution.toFloat()); draw(this) }
    }
    private val bloom = bitmap(700, 700) { canvas ->
        paint.color = android.graphics.Color.WHITE; paint.maskFilter = BlurMaskFilter(75f, BlurMaskFilter.Blur.NORMAL)
        canvas.drawCircle(350f, 350f, 210f, paint)
    }
    private val shadows = bitmap(539, 531) { canvas ->
        paint.color = Ink.copy(alpha = .045f).toArgb(); paint.maskFilter = BlurMaskFilter(21f, BlurMaskFilter.Blur.NORMAL)
        canvas.drawRoundRect(80f, 92f, 459f, 463f, 38f, 38f, paint)
        paint.color = Ink.copy(alpha = .025f).toArgb(); paint.maskFilter = BlurMaskFilter(2f, BlurMaskFilter.Blur.NORMAL)
        canvas.drawRoundRect(80f, 82f, 459f, 453f, 38f, 38f, paint)
    }
    private val halos = List(13) { index -> bitmap(100, 100) { canvas ->
        paint.color = android.graphics.Color.WHITE; paint.maskFilter = BlurMaskFilter(9f, BlurMaskFilter.Blur.NORMAL)
        canvas.drawCircle(50f, 50f, 12f + index * .5f, paint)
    } }
    private fun reset() { paint.maskFilter = null; paint.colorFilter = null; paint.color = android.graphics.Color.WHITE; paint.alpha = 255 }
    fun bloom(canvas: AndroidCanvas, tint: Color, activity: Float) {
        reset(); paint.colorFilter = PorterDuffColorFilter(tint.toArgb(), PorterDuff.Mode.SRC_IN)
        paint.alpha = (255 * .10f * activity).toInt()
        canvas.drawBitmap(bloom, null, RectF(-135f, 240f, 565f, 940f), paint)
    }
    fun shadow(canvas: AndroidCanvas) { reset(); canvas.drawBitmap(shadows, null, RectF(-73f, 290f, 466f, 821f), paint) }
    fun halo(canvas: AndroidCanvas, x: Float, y: Float, radius: Float, activity: Float) {
        reset(); paint.alpha = (255 * .72f * activity).toInt()
        val image = halos[((radius - 12) * 2).toInt().coerceIn(0, 12)]
        canvas.drawBitmap(image, null, RectF(x - 50, y - 50, x + 50, y + 50), paint)
    }
    fun mood(canvas: AndroidCanvas, tint: Color, pose: List<MagneticFeelingFieldLabelGlyph>) {
        reset()
        paint.colorFilter = PorterDuffColorFilter(tint.copy(alpha = 1f).toArgb(), PorterDuff.Mode.SRC_IN)
        for (glyph in pose) {
            val image = glyphs.getValue(glyph.character)[(glyph.blur * 10).roundToInt().coerceIn(0, 30)]
            paint.alpha = (255 * .83 * glyph.opacity).roundToInt().coerceIn(0, 255)
            canvas.save()
            canvas.translate(glyph.center.toFloat(), 0f)
            // Blur is baked at natural width before this horizontal compression, matching Swift.
            canvas.scale(glyph.scaleX.toFloat(), 1f)
            val left = -glyph.width.toFloat() / 2 - glyphPadding
            canvas.drawBitmap(image, null, RectF(left, -glyphPadding, left + image.width / 3f, -glyphPadding + image.height / 3f), paint)
            canvas.restore()
        }
    }
}

@Composable
private fun Label(
    text: String,
    size: Float,
    modifier: Modifier = Modifier,
    color: Color = Ink,
    weight: FontWeight = FontWeight.Normal,
    serif: Boolean = false,
    mono: Boolean = false,
    tracking: Float = 0f,
    lineHeight: Float = size * 1.2f,
) {
    BasicText(text, modifier, style = TextStyle(
        color = color, fontSize = size.sp, fontWeight = weight,
        fontFamily = if (serif) FontFamily.Serif else if (mono) FontFamily.Monospace else FontFamily.SansSerif,
        letterSpacing = tracking.sp, lineHeight = lineHeight.sp,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
    ))
}

@Composable
private fun Moon(modifier: Modifier) {
    Canvas(modifier) {
        scale(density, density, pivot = Offset.Zero) {
            val moon = Path().apply {
                moveTo(12f, 2f)
                cubicTo(1f, 1f, 0f, 17f, 10f, 19f)
                cubicTo(15f, 20f, 19f, 16f, 19f, 12f)
                cubicTo(10f, 15f, 6f, 7f, 12f, 2f)
                close()
            }
            drawPath(moon, Ink, style = Stroke(1.1f))
            drawLine(Ink, Offset(17f, 1f), Offset(17f, 7f), 1f, StrokeCap.Round)
            drawLine(Ink, Offset(14f, 4f), Offset(20f, 4f), 1f, StrokeCap.Round)
            drawCircle(Ink, 0.8f, Offset(22f, 9f))
        }
    }
}

/** Observe the system preference while the block is on screen. */
@Composable
private fun magneticFeelingFieldReducedMotion(): Boolean {
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
