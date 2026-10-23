package com.ngocdevv.nativecook.blocks.lists.weekendmoodcarousel

import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.Typeface
import android.view.HapticFeedbackConstants
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.core.view.WindowCompat
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sqrt
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

private const val WEEKEND_SCREEN_W = 402f
private const val WEEKEND_PIN_H = 874f
private const val WEEKEND_PANEL_H = 461f
private const val LOOP_MS = 13_800L
private const val WEEKEND_USER_SCROLL_MS = 1_270L
private const val WEEKEND_FILL_IN_MS = 200L
private const val WEEKEND_FILL_OUT_MS = 170L
private const val WEEKEND_RESUME_AFTER_MS = 3_500L
private const val RETURN_TO_REST_MS = 650L
private const val WEEKEND_REDUCED_FADE_MS = 180L
private const val WEEKEND_RUBBER = .35f

private data class WeekendMoodCharacter(
    val panel: Int,
    val chip: IntArray,
    val tick: IntArray,
)

private data class WeekendMoodChip(val label: String, val character: Int)

data class WeekendMoodSelection(val index: Int, val label: String, val characterIndex: Int)

private val weekendCharacters = listOf(
    WeekendMoodCharacter(Color.parseColor("#F0E1B0"), intArrayOf(Color.parseColor("#F6AE31"), Color.parseColor("#EBA36E"), Color.parseColor("#E09AA3")), intArrayOf(Color.parseColor("#C2B4D8"), Color.parseColor("#F4D27E"))),
    WeekendMoodCharacter(Color.parseColor("#BEDB98"), intArrayOf(Color.parseColor("#6BB341"), Color.parseColor("#ADB443"), Color.parseColor("#DBAD6E")), intArrayOf(Color.parseColor("#6FAFDB"), Color.parseColor("#82CC86"))),
    WeekendMoodCharacter(Color.parseColor("#FDBFB6"), intArrayOf(Color.parseColor("#EB7F5F"), Color.parseColor("#EB8551"), Color.parseColor("#F1814F")), intArrayOf(Color.parseColor("#CFB2E6"), Color.parseColor("#F0D978"))),
    WeekendMoodCharacter(Color.parseColor("#F6D0FB"), intArrayOf(Color.parseColor("#C87CF2"), Color.parseColor("#C487D0"), Color.parseColor("#D39B9D")), intArrayOf(Color.parseColor("#DE6573"), Color.parseColor("#D6AFEA"))),
    WeekendMoodCharacter(Color.parseColor("#FDD7FC"), intArrayOf(Color.parseColor("#C976EA"), Color.parseColor("#D376BE"), Color.parseColor("#E57A75")), intArrayOf(Color.parseColor("#DAAEEF"), Color.parseColor("#E4A3CB"))),
    WeekendMoodCharacter(Color.parseColor("#FDA4AB"), intArrayOf(Color.parseColor("#DA735E"), Color.parseColor("#DB7D4E"), Color.parseColor("#EC8E18")), intArrayOf(Color.parseColor("#DAAEEF"), Color.parseColor("#D66476"))),
    WeekendMoodCharacter(Color.parseColor("#FDD2BA"), intArrayOf(Color.parseColor("#EC7C66"), Color.parseColor("#E8854D"), Color.parseColor("#F69318")), intArrayOf(Color.parseColor("#CEB0E8"), Color.parseColor("#6FB1E6"))),
)

private val weekendChips = listOf(
    WeekendMoodChip("Grateful", 2), WeekendMoodChip("Joyful", 3), WeekendMoodChip("Sleepy", 0), WeekendMoodChip("Recovery Mode", 2),
    WeekendMoodChip("Satisfied", 1), WeekendMoodChip("Foodie", 1), WeekendMoodChip("Reflective", 2), WeekendMoodChip("Thoughtful", 6),
    WeekendMoodChip("Nostalgic", 6), WeekendMoodChip("Social Butterfly", 6), WeekendMoodChip("Uncertain", 5), WeekendMoodChip("Melancholy", 0),
    WeekendMoodChip("Binge-Watching", 3), WeekendMoodChip("Disappointed", 5), WeekendMoodChip("Lonely", 4), WeekendMoodChip("Shopping", 4),
    WeekendMoodChip("Hopeless", 5), WeekendMoodChip("Heartbroken", 4), WeekendMoodChip("Scroll Mode", 5), WeekendMoodChip("Devastated", 0),
)

private val weekendChipOfCharacter = intArrayOf(2, 5, 3, 12, 15, 18, 9)
private val weekendDx = floatArrayOf(2.8f, -3.2f, -3.8f, 0f, 1.3f, 2.2f, -3f, 2.8f)

private data class WeekendStep(
    val chip: Int,
    val dx: Float,
    val character: Int,
    val outAt: Long,
    val outMs: Long,
    val moveAt: Long,
    val moveMs: Long,
    val fillAt: Long,
    val fillMs: Long,
)

private val weekendSteps = listOf(
    WeekendStep(5, -3.2f, 1, 400, 170, 500, 1333, 1700, 200),
    WeekendStep(12, -3.8f, 3, 2400, 170, 2530, 1267, 3730, 240),
    WeekendStep(15, 0f, 4, 4470, 170, 4470, 1267, 5600, 300),
    WeekendStep(18, 1.3f, 5, 6530, 200, 6570, 1200, 7600, 230),
    WeekendStep(9, 2.2f, 6, 7830, 740, 8430, 1633, 9970, 130),
    WeekendStep(3, -3f, 2, 10570, 100, 10570, 1167, 11730, 170),
    WeekendStep(2, 2.8f, 0, 12450, 170, 12500, 1200, 13600, 200),
)

private data class WeekendFrame(
    val chipX: Float,
    val character: Float,
    val fill: Float,
    val chip: Int,
    val selectedCharacter: Int,
    val age: Float,
    val veil: Float,
    val ink: Float,
)

private enum class WeekendCurve { Bezier, EaseInOut, Linear, Spring }

private class WeekendTween(
    private val from: Float,
    private val to: Float,
    private val startNs: Long,
    private val durationMs: Long,
    private val curve: WeekendCurve,
    private val velocity: Float = 0f,
) {
    fun value(nowNs: Long): Float {
        if (durationMs <= 0L || nowNs <= startNs) return from
        val p = ((nowNs - startNs) / 1_000_000.0 / durationMs).coerceIn(0.0, 1.0)
        val e = when (curve) {
            WeekendCurve.Linear -> p
            WeekendCurve.EaseInOut -> if (p < .5) 2.0 * p * p else 1.0 - ((-2.0 * p + 2.0) * (-2.0 * p + 2.0) / 2.0)
            WeekendCurve.Bezier -> weekendBezier(p, .3, .05, .4, 1.0)
            WeekendCurve.Spring -> {
                val durationSeconds = durationMs / 1_000.0
                val dampingRatio = .8
                val naturalFrequency = 4.0 / (dampingRatio * durationSeconds)
                val dampedFrequency = naturalFrequency * sqrt(1.0 - dampingRatio * dampingRatio)
                val distance = (to - from).toDouble()
                val normalizedVelocity = if (abs(distance) > .0001) velocity / distance else 0.0
                fun raw(time: Double): Double {
                    val decay = exp(-dampingRatio * naturalFrequency * time)
                    val coefficient = (dampingRatio * naturalFrequency - normalizedVelocity) / dampedFrequency
                    return 1.0 - decay * (cos(dampedFrequency * time) + coefficient * kotlin.math.sin(dampedFrequency * time))
                }
                val end = raw(durationSeconds)
                if (abs(end) > .0001) raw(p * durationSeconds) / end else p
            }
        }
        return from + (to - from) * e.toFloat()
    }

    fun done(nowNs: Long) = nowNs >= startNs + durationMs * 1_000_000L
}

private class WeekendMoodCarouselMotion(private val startsAutoplay: Boolean) {
    var chipX = 0f
    var character = 0f
    var fill = 1f
    var ink = 1f
    var selectedChip = 2
    var selectedCharacter = 0
    var previousCharacter = -1
    var age = 1_000f
    var phase = 0f
    var veil = 0f
    var dragging = false
    var dragStart = 0f
    var manual = !startsAutoplay
    var loopStartNs = -1L
    var resumeAtNs = -1L
    var returning = false
    var currentNs = 0L
    var onHaptic: (() -> Unit)? = null

    private var chipTween: WeekendTween? = null
    private var characterTween: WeekendTween? = null
    private var fillOutTween: WeekendTween? = null
    private var fillTween: WeekendTween? = null
    private var veilTween: WeekendTween? = null
    private var reducedSwapNs = -1L
    private var reducedTargetChip = 2
    private var reducedTargetCharacter = 0
    private var previousNs = -1L
    private var initialized = false
    private var touching = false

    fun advance(nowNs: Long, centers: FloatArray, reduced: Boolean, autoplay: Boolean) {
        val dtMs = if (previousNs < 0L) 16.667f else ((nowNs - previousNs) / 1_000_000f).coerceIn(0f, 100f)
        previousNs = nowNs
        currentNs = nowNs
        if (centers.isEmpty()) return
        if (!initialized) {
            chipX = centers[2] - weekendDx[0]
            initialized = true
        }
        if (autoplay && !manual) {
            if (loopStartNs < 0L) loopStartNs = nowNs
            val t = ((nowNs - loopStartNs) / 1_000_000L) % LOOP_MS
            val frame = weekendSampleScript(t, centers, reduced)
            chipX = frame.chipX; character = frame.character; fill = frame.fill; ink = frame.ink
            if (frame.selectedCharacter != selectedCharacter) previousCharacter = selectedCharacter
            selectedChip = frame.chip; selectedCharacter = frame.selectedCharacter; age = frame.age; veil = frame.veil; phase = t.toFloat()
            return
        }
        chipTween?.let { tween -> chipX = tween.value(nowNs); if (tween.done(nowNs)) chipTween = null }
        characterTween?.let { tween -> character = tween.value(nowNs); if (tween.done(nowNs)) characterTween = null }
        fillOutTween?.let { tween -> fill = tween.value(nowNs); if (tween.done(nowNs)) fillOutTween = null }
        fillTween?.let { tween -> fill = tween.value(nowNs); ink = fill; if (tween.done(nowNs)) fillTween = null }
        veilTween?.let { tween -> veil = tween.value(nowNs); if (tween.done(nowNs)) veilTween = null }
        age += dtMs
        phase += dtMs
        if (reducedSwapNs >= 0L && nowNs >= reducedSwapNs) {
            chipX = centers[reducedTargetChip]; character = reducedTargetCharacter.toFloat(); fill = 1f; ink = 1f; age = 9_999f
            veilTween = WeekendTween(1f, 0f, nowNs, WEEKEND_REDUCED_FADE_MS / 2, WeekendCurve.EaseInOut)
            reducedSwapNs = -1L
        }
        if (manual && autoplay && resumeAtNs >= 0L && nowNs >= resumeAtNs && !returning) {
            returning = true
            // Reanimated keeps the chip's present fill and fades it out before the return scroll.
            fillTween = null
            fillOutTween = WeekendTween(fill, 0f, nowNs, WEEKEND_FILL_OUT_MS, WeekendCurve.EaseInOut)
            ink = 0f
            chipTween = WeekendTween(chipX, centers[2], nowNs, WEEKEND_USER_SCROLL_MS, WeekendCurve.Bezier)
            characterTween = WeekendTween(character, 0f, nowNs, WEEKEND_USER_SCROLL_MS, WeekendCurve.Bezier)
            resumeAtNs = -1L
        }
        if (returning && characterTween == null) {
            returning = false; manual = false; loopStartNs = -1L; previousCharacter = selectedCharacter; selectedChip = 2; selectedCharacter = 0; age = 1_000f
        }
    }

    fun beginTouch() {
        // Start a new manual transition from this input, not the last idle frame.
        currentNs = System.nanoTime()
        previousNs = currentNs
        touching = true
        manual = true; dragging = false; returning = false; resumeAtNs = -1L
        chipTween = null; characterTween = null; fillOutTween = null; fillTween = null; veilTween = null
        reducedSwapNs = -1L
        veil = 0f
        dragStart = chipX
    }

    fun synchronizeAutoplay(enabled: Boolean, reduced: Boolean, centers: FloatArray) {
        if (!enabled) {
            resumeAtNs = -1L
            returning = false
        }
        if (reduced && (chipTween != null || characterTween != null)) {
            chipTween = null
            characterTween = null
            chipX = centers.getOrElse(selectedChip) { chipX }
            character = selectedCharacter.toFloat()
        }
    }

    fun beginChipDrag() { dragging = true; dragStart = chipX; fillOutTween = WeekendTween(fill, 0f, currentNs, WEEKEND_FILL_OUT_MS, WeekendCurve.EaseInOut); ink = 0f }
    fun updateChipDrag(translationX: Float, centers: FloatArray) {
        val minX = centers.firstOrNull() ?: 0f; val maxX = centers.lastOrNull() ?: 0f
        var x = dragStart - translationX
        if (x < minX) x = minX - (minX - x) * WEEKEND_RUBBER
        if (x > maxX) x = maxX + (x - maxX) * WEEKEND_RUBBER
        chipX = x
    }

    fun beginPanelDrag() { dragging = true; dragStart = character; fillOutTween = WeekendTween(fill, 0f, currentNs, WEEKEND_FILL_OUT_MS, WeekendCurve.EaseInOut); ink = 0f }
    fun updatePanelDrag(translationX: Float) {
        val maxCharacter = 6f; var value = dragStart - translationX / 292f
        if (value < 0f) value *= WEEKEND_RUBBER
        if (value > maxCharacter) value = maxCharacter + (value - maxCharacter) * WEEKEND_RUBBER
        character = value
    }

    fun selectChip(index: Int, centers: FloatArray, reduced: Boolean, fromDrag: Boolean, velocity: Float = 0f, panelDrag: Boolean = false) {
        if (index !in weekendChips.indices) return
        if (selectedCharacter != weekendChips[index].character) previousCharacter = selectedCharacter
        selectedChip = index; selectedCharacter = weekendChips[index].character
        val now = currentNs
        if (reduced) {
            reducedTargetChip = index; reducedTargetCharacter = selectedCharacter; veilTween = WeekendTween(0f, 1f, now, WEEKEND_REDUCED_FADE_MS / 2, WeekendCurve.EaseInOut); reducedSwapNs = now + WEEKEND_REDUCED_FADE_MS / 2 * 1_000_000L; onHaptic?.invoke(); return
        }
        val targetX = centers.getOrElse(index) { chipX }
        chipTween = WeekendTween(chipX, targetX, now, if (fromDrag) 400L else WEEKEND_USER_SCROLL_MS, if (fromDrag) WeekendCurve.Spring else WeekendCurve.Bezier, if (fromDrag) -velocity else 0f)
        characterTween = WeekendTween(character, selectedCharacter.toFloat(), now, if (panelDrag) 400L else WEEKEND_USER_SCROLL_MS, if (panelDrag) WeekendCurve.Spring else WeekendCurve.Bezier, if (panelDrag) -velocity / 292f else 0f)
        val lightAt = when {
            fromDrag -> 380L
            panelDrag -> 420L
            else -> (WEEKEND_USER_SCROLL_MS * .9f).toLong()
        }
        fillOutTween = null; fill = 0f; ink = 0f; age = -lightAt.toFloat(); fillTween = WeekendTween(0f, 1f, now + lightAt * 1_000_000L, WEEKEND_FILL_IN_MS, WeekendCurve.EaseInOut)
        onHaptic?.invoke()
    }

    fun endTouch(nowNs: Long, resumeEnabled: Boolean) {
        touching = false
        dragging = false
        if (startsAutoplay && resumeEnabled) resumeAtNs = nowNs + WEEKEND_RESUME_AFTER_MS * 1_000_000L
    }

    fun hasMotion() = touching || dragging || returning || chipTween != null || characterTween != null || fillOutTween != null || fillTween != null || veilTween != null || reducedSwapNs >= 0L

    /** Manual expressions keep the Canvas clock alive after their layout tween settles. */
    fun needsExpressionFrames(reduced: Boolean) =
        manual && !reduced && (age < 500f || selectedCharacter == 3 || selectedCharacter == 4 || selectedCharacter == 6)
}

private fun weekendSampleScript(tIn: Long, centers: FloatArray, reduced: Boolean): WeekendFrame {
    val t = ((tIn % LOOP_MS) + LOOP_MS) % LOOP_MS
    var chipFrom = 2; var characterFrom = 0; var chipX = centers.getOrElse(2) { 0f } - weekendDx[0]; var dxFrom = weekendDx[0]; var character = 0f
    var fill = 1f; var selectedChip = 2; var selectedCharacter = 0; var age = t + (LOOP_MS - 13_600L); var veil = 0f; var ink = 1f
    for (step in weekendSteps) {
        if (t < step.outAt) break
        fill = 1f - ((t - step.outAt).toFloat() / step.outMs).coerceIn(0f, 1f); ink = 0f
        if (t >= step.moveAt) {
            val linear = ((t - step.moveAt).toFloat() / step.moveMs).coerceIn(0f, 1f)
            val p = if (reduced) { val mid = step.moveAt + step.moveMs / 2; veil = max(veil, 1f - (abs(t - mid).toFloat() / WEEKEND_REDUCED_FADE_MS).coerceIn(0f, 1f)); if (t < mid) 0f else 1f } else weekendBezier(linear.toDouble(), .3, .05, .4, 1.0).toFloat()
            val a = centers.getOrElse(chipFrom) { 0f } - dxFrom; val b = centers.getOrElse(step.chip) { 0f } - step.dx
            chipX = a + (b - a) * p; character = characterFrom + (step.character - characterFrom) * p
        }
        if (t >= step.fillAt) { fill = ((t - step.fillAt).toFloat() / step.fillMs).coerceIn(0f, 1f); ink = fill; selectedChip = step.chip; selectedCharacter = step.character; age = t - step.fillAt }
        chipFrom = step.chip; dxFrom = step.dx; characterFrom = step.character
    }
    return WeekendFrame(chipX, character, fill, selectedChip, selectedCharacter, age.toFloat(), veil, ink)
}

private fun weekendBezier(progress: Double, x1: Double, y1: Double, x2: Double, y2: Double): Double {
    fun sample(t: Double, a: Double, b: Double) = 3 * (1 - t) * (1 - t) * t * a + 3 * (1 - t) * t * t * b + t * t * t
    var lo = 0.0; var hi = 1.0
    repeat(14) { val mid = (lo + hi) / 2; if (sample(mid, x1, x2) < progress) lo = mid else hi = mid }
    return sample((lo + hi) / 2, y1, y2)
}

@Composable
fun WeekendMoodCarousel(
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false,
    onBack: (() -> Unit)? = null,
    onSave: ((WeekendMoodSelection) -> Unit)? = null,
) {
    val reduced = rememberReducedMotion()
    val context = LocalContext.current
    val density = LocalDensity.current
    val view = LocalView.current
    val activity = LocalActivity.current
    val titleTypeface = remember { Typeface.createFromAsset(context.assets, "weekendmoodcarousel/weekendmoodcarousel_LiberationSerif-Bold.ttf") }
    val chipTypeface = remember { Typeface.createFromAsset(context.assets, "weekendmoodcarousel/weekendmoodcarousel_SourceSans3-Medium.ttf") }
    val uiTypeface = remember { Typeface.create("sans-serif", Typeface.NORMAL) }
    val shapes = remember { WeekendMoodCarouselArt.parseShapes() }
    val motion = remember { WeekendMoodCarouselMotion(autoPlay) }
    var frameTick by remember { mutableLongStateOf(0L) }
    var selectedSemantics by remember { mutableIntStateOf(2) }
    var driverGeneration by remember { mutableIntStateOf(0) }
    val scriptedAutoPlay = autoPlay && !reduced

    DisposableEffect(activity, view) {
        val controller = activity?.window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = true
        onDispose { if (previous != null) controller?.isAppearanceLightStatusBars = previous }
    }

    val chipWidths = remember(chipTypeface) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = chipTypeface; textSize = 16.4f; letterSpacing = -.4f / 16.4f }
        FloatArray(weekendChips.size) { i -> max(111f, p.measureText(weekendChips[i].label) + 32f) }
    }
    val chipCenters = remember(chipWidths) {
        FloatArray(chipWidths.size) { i -> var x = 0f; for (j in 0 until i) x += chipWidths[j] + 10f; x + chipWidths[i] / 2f }
    }
    motion.onHaptic = { view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK) }

    LaunchedEffect(motion, reduced, autoPlay, chipCenters, driverGeneration) {
        motion.synchronizeAutoplay(scriptedAutoPlay, reduced, chipCenters)
        var firstFrame = true
        while (isActive && (scriptedAutoPlay || motion.hasMotion() || motion.needsExpressionFrames(reduced) || firstFrame)) {
            withFrameNanos { now ->
                motion.advance(now, chipCenters, reduced, scriptedAutoPlay)
                if (motion.selectedChip != selectedSemantics) selectedSemantics = motion.selectedChip
                frameTick += 1L
                firstFrame = false
            }
        }
    }

    BoxWithConstraints(modifier.fillMaxSize().background(ComposeColor(0xFF101718))) {
        val width = maxWidth.value
        val height = maxHeight.value
        val extra = height - WEEKEND_PIN_H
        val panelHeight = WEEKEND_PANEL_H + extra
        val chipTop = 752f + extra
        val panelTop = 0f
        Box(
            Modifier.fillMaxSize().semantics { contentDescription = "Weekend mood picker. Swipe horizontally to select a mood." }.pointerInput(motion, reduced, autoPlay, width, height) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val downX = down.position.x / density.density
                    val downY = down.position.y / density.density
                    val inChipArea = downY in chipTop..(chipTop + 65f)
                    val inPanel = downY <= panelHeight
                    // RN installs recognizers only over these two regions; a touch elsewhere leaves autoplay alone.
                    if (!inChipArea && !inPanel) return@awaitEachGesture
                    motion.beginTouch()
                    driverGeneration += 1
                    var mode = 0 // 0 tap, 1 chip drag, 2 panel drag
                    var released = false
                    var cancelled = false
                    var translationX = 0f
                    var releaseX = downX
                    var tapWithinMaxDistance = true
                    val tracker = androidx.compose.ui.input.pointer.util.VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    while (!released) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.changes.count { it.pressed } > 1) {
                            cancelled = true
                            break
                        }
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null || change.isConsumed) {
                            cancelled = true
                            break
                        }
                        tracker.addPosition(change.uptimeMillis, change.position)
                        releaseX = change.position.x / density.density
                        translationX = (change.position.x / density.density) - downX
                        val translationY = (change.position.y / density.density) - downY
                        // Gesture.Tap().maxDistance(10) uses total pointer travel, not horizontal travel alone.
                        tapWithinMaxDistance = tapWithinMaxDistance && hypot(translationX, translationY) <= 10f
                        if (mode == 0 && abs(translationX) >= 8f) {
                            mode = when { inChipArea -> 1; inPanel -> 2; else -> 0 }
                            if (mode == 1) motion.beginChipDrag() else if (mode == 2) motion.beginPanelDrag()
                        }
                        if (mode == 1) { change.consume(); motion.updateChipDrag(translationX, chipCenters) }
                        if (mode == 2) { change.consume(); motion.updatePanelDrag(translationX) }
                        if (!change.pressed) {
                            released = true
                            val velocity = tracker.calculateVelocity().x / density.density
                            if (!cancelled && mode == 1) {
                                val projected = motion.chipX - velocity * .18f
                                motion.selectChip(weekendNearest(chipCenters, projected), chipCenters, reduced, fromDrag = true, velocity = velocity)
                            } else if (!cancelled && mode == 2) {
                                val projected = motion.character - velocity * .2f / 292f
                                val character = projected.roundToInt().coerceIn(0, 6)
                                motion.selectChip(weekendChipOfCharacter[character], chipCenters, reduced, fromDrag = false, velocity = velocity, panelDrag = true)
                            } else if (!cancelled && inChipArea && tapWithinMaxDistance) {
                                val x = motion.chipX + (releaseX - 201f - (width - WEEKEND_SCREEN_W) / 2f)
                                val index = weekendNearest(chipCenters, x)
                                if (abs(chipCenters[index] - x) < 80f && index != motion.selectedChip) motion.selectChip(index, chipCenters, reduced, fromDrag = false)
                            }
                            motion.endTouch(System.nanoTime(), scriptedAutoPlay)
                        }
                    }
                    if (!released) motion.endTouch(System.nanoTime(), scriptedAutoPlay)
                }
            },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                frameTick
                drawIntoCanvas { draw ->
                    val c = draw.nativeCanvas
                    c.save(); c.scale(density.density, density.density)
                    val w = size.width / density.density
                    val opacity = (1f - motion.veil).coerceIn(0f, 1f)
                    c.drawColor(Color.parseColor("#101718"))
                    WeekendMoodCarouselArt.drawPanel(c, w, panelHeight, motion.character, motion.selectedCharacter, motion.previousCharacter, motion.fill, motion.age, motion.phase, reduced, shapes, opacity)
                    drawButtons(c, w, uiTypeface)
                    drawTitle(c, w, 511.3f + extra, titleTypeface)
                    c.save(); c.translate(0f, 626f + extra); WeekendMoodCarouselArt.drawRuler(c, w, motion.chipX, chipCenters[2], motion.character); c.restore()
                    drawChips(c, w, 760f + extra, chipCenters, chipWidths, motion, chipTypeface, opacity)
                    c.restore()
                }
            }
            Box(
                Modifier
                    .offset { IntOffset((22f * density.density).roundToInt(), (77f * density.density).roundToInt()) }
                    .size(54.dp)
                    .semantics { contentDescription = "Back"; role = Role.Button }
                    .clickable(enabled = onBack != null, role = Role.Button) { onBack?.invoke() },
            )
            Box(
                Modifier
                    .offset { IntOffset(((width - 104f) * density.density).roundToInt(), (77f * density.density).roundToInt()) }
                    .size(84.dp, 54.dp)
                    .semantics { contentDescription = "Save mood"; role = Role.Button }
                    .clickable(enabled = onSave != null, role = Role.Button) {
                        val chip = weekendChips[selectedSemantics]
                        onSave?.invoke(WeekendMoodSelection(selectedSemantics, chip.label, chip.character))
                    },
            )
            val semanticTranslation = 201f + (width - WEEKEND_SCREEN_W) / 2f - motion.chipX
            weekendChips.forEachIndexed { index, chip ->
                val left = semanticTranslation + chipCenters[index] - chipWidths[index] / 2f
                Box(
                    Modifier
                        .offset { IntOffset((left * density.density).roundToInt(), ((760f + extra) * density.density).roundToInt()) }
                        .size(chipWidths[index].dp, 49.dp)
                        .semantics {
                            contentDescription = chip.label
                            role = Role.Button
                            selected = selectedSemantics == index
                            onClick {
                                motion.beginTouch()
                                if (index != motion.selectedChip) motion.selectChip(index, chipCenters, reduced, fromDrag = false)
                                motion.endTouch(System.nanoTime(), scriptedAutoPlay)
                                driverGeneration += 1
                                true
                            }
                        },
                )
            }
        }
    }
}

private fun weekendNearest(centers: FloatArray, x: Float): Int {
    var best = 0
    for (i in 1 until centers.size) if (abs(centers[i] - x) < abs(centers[best] - x)) best = i
    return best
}

private fun drawButtons(canvas: AndroidCanvas, width: Float, uiTypeface: Typeface) {
    val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = .67f; color = Color.argb((.4f * 255).toInt(), 0, 0, 0) }
    canvas.drawCircle(22f + 27f, 77f + 27f, 27f, ring)
    canvas.drawRoundRect(width - 20f - 84f, 77f, width - 20f, 131f, 27f, 27f, ring)
    val arrowLeft = 22f + (54f - 18f) / 2f
    val arrowTop = 77f + (54f - 18f) / 2f
    val arrow = Path().apply { moveTo(arrowLeft + 16f, arrowTop + 9f); lineTo(arrowLeft + 2.5f, arrowTop + 9f); moveTo(arrowLeft + 8.5f, arrowTop + 2.8f); lineTo(arrowLeft + 2.3f, arrowTop + 9f); lineTo(arrowLeft + 8.5f, arrowTop + 15.2f) }
    val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.7f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; color = Color.parseColor("#16191A") }
    canvas.drawPath(arrow, arrowPaint)
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#16191A"); typeface = uiTypeface; textSize = 15f }
    val fm = text.fontMetrics; val baseline = 104f - (fm.ascent + fm.descent) / 2f
    canvas.drawText("Save", width - 20f - 42f - text.measureText("Save") / 2f, baseline, text)
}

private fun drawTitle(canvas: AndroidCanvas, width: Float, top: Float, typeface: Typeface) {
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; this.typeface = typeface; textSize = 47f; textAlign = Paint.Align.CENTER }
    val fm = p.fontMetrics; val x = width / 2f - 2.7f
    canvas.drawText("Select your", x, top - fm.top, p); canvas.drawText("weekend mood", x, top + 45f - fm.top, p)
}

private fun drawChips(canvas: AndroidCanvas, width: Float, top: Float, centers: FloatArray, widths: FloatArray, motion: WeekendMoodCarouselMotion, typeface: Typeface, opacity: Float) {
    val translation = 201f + (width - WEEKEND_SCREEN_W) / 2f - motion.chipX
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.typeface = typeface
        textSize = 16.4f
        setLetterSpacing(-.4f / 16.4f)
        textAlign = Paint.Align.CENTER
    }
    for (i in weekendChips.indices) {
        val chipCenter = translation + centers[i]; val left = chipCenter - widths[i] / 2f; val right = chipCenter + widths[i] / 2f
        val selected = motion.selectedChip == i; val ringOpacity = if (selected) 1f - motion.fill else 1f
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1f; color = Color.argb((.3f * ringOpacity * opacity * 255).toInt(), 255, 255, 255) }
        canvas.drawRoundRect(left, top, right, top + 49f, 24.5f, 24.5f, ring)
        if (selected && motion.fill > 0f) {
            val colors = weekendCharacters[weekendChips[i].character].chip
            val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(left, 0f, right, 0f, colors, floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP); alpha = (motion.fill * opacity * 255).toInt() }
            canvas.drawRoundRect(left, top, right, top + 49f, 24.5f, 24.5f, fill)
        }
        text.color = if (selected) alphaColor(mixColor(Color.parseColor("#F2F4F4"), Color.parseColor("#24201F"), motion.ink), opacity) else Color.argb((opacity * 255).toInt(), 242, 244, 244)
        val fm = text.fontMetrics; canvas.drawText(weekendChips[i].label, chipCenter, top + 24.5f - (fm.ascent + fm.descent) / 2f, text)
    }
}

private fun mixColor(from: Int, to: Int, amount: Float): Int {
    val t = amount.coerceIn(0f, 1f)
    fun channel(start: Int, end: Int): Int {
        val a = (start / 255f).pow(2.2f)
        val b = (end / 255f).pow(2.2f)
        return ((a + (b - a) * t).pow(1f / 2.2f) * 255f).roundToInt()
    }
    return Color.rgb(
        channel(Color.red(from), Color.red(to)),
        channel(Color.green(from), Color.green(to)),
        channel(Color.blue(from), Color.blue(to)),
    )
}

private fun alphaColor(color: Int, alpha: Float): Int = Color.argb(
    (Color.alpha(color) * alpha).toInt().coerceIn(0, 255),
    Color.red(color),
    Color.green(color),
    Color.blue(color),
)
