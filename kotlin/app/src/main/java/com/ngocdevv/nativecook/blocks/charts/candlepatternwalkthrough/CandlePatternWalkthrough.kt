package com.ngocdevv.nativecook.blocks.charts.candlepatternwalkthrough

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.surface
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private val M = CandlePatternWalkthroughMotion
private val S = CandlePatternWalkthroughStyle
private enum class Mode { Auto, Hand, Returning }
private data class Run(val start: Long, val from: Float, val to: Float)
private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)
private fun lessonText(size: Float, height: Float, color: Color, weight: FontWeight = FontWeight.Normal, spacing: Float = 0f) = TextStyle(
    color = color, fontSize = size.sp, lineHeight = height.sp, fontWeight = weight, letterSpacing = spacing.sp,
    lineHeightStyle = Centered, platformStyle = PlatformTextStyle(includeFontPadding = false),
)

/** Tap to play to the next chapter boundary, or drag horizontally through the whole lesson. */
@Composable
fun CandlePatternWalkthrough(
    title: String = "Trend line breakout",
    hint: String = "Tap for the next step · swipe to scrub",
    chapters: List<String> = listOf("Swing highs", "Trend line", "Breakout", "Re-test", "Confirmation", "Risk to reward", "Target"),
    swingHighLabel: String = "swing\nhigh",
    trendLineLabel: String = "trend line",
    breakoutLabel: String = "breakout",
    retestLabel: String = "re-test",
    confirmationLabel: String = "confirmation",
    ratioPrefix: String = "RR=1:",
    upColor: Color = Color(0xFF3BFF3B),
    downColor: Color = Color(0xFFFF2D2D),
    autoPlay: Boolean = false,
    onChapterChange: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    val view = LocalView.current
    val automatic by rememberUpdatedState(autoPlay)
    val callback by rememberUpdatedState(onChapterChange)
    var mode by remember { mutableStateOf(if (autoPlay) Mode.Auto else Mode.Hand) }
    var origin by remember { mutableLongStateOf(System.nanoTime()) }
    var held by remember { mutableFloatStateOf(0f) }
    var manual by remember { mutableStateOf<Run?>(null) }
    var scrubbing by remember { mutableStateOf(false) }
    var scrubFrom by remember { mutableFloatStateOf(0f) }
    var lastTouch by remember { mutableLongStateOf(System.nanoTime()) }
    var revision by remember { mutableIntStateOf(0) }
    var chapter by remember { mutableIntStateOf(0) }
    var previous by remember { mutableStateOf<Int?>(null) }
    var captionStart by remember { mutableLongStateOf(System.nanoTime() - 1_000_000_000L) }
    val captionTime = remember { mutableLongStateOf(System.nanoTime()) }
    val time = remember { mutableFloatStateOf(0f) }
    val veil = remember { mutableFloatStateOf(1f) }

    fun report(index: Int, byHand: Boolean) {
        if (index == chapter) return
        previous = chapter; chapter = index; captionStart = System.nanoTime()
        if (byHand) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        callback?.invoke(index)
    }
    fun sample(): Float {
        val now = System.nanoTime()
        if (mode == Mode.Auto) return ((now - origin) / 1e9f) % M.LOOP
        val run = manual
        return if (run == null) held else min(run.to, run.from + (now - run.start) / 1e9f)
    }
    fun takeOver() {
        held = sample(); time.floatValue = held; mode = Mode.Hand; manual = null; veil.floatValue = 1f; revision++
    }
    fun next() {
        takeOver()
        val stop = M.STOPS.firstOrNull { it > held + S.STOP_EPSILON } ?: M.LOOP
        manual = Run(System.nanoTime(), held, stop); revision++
    }

    LaunchedEffect(captionStart) {
        if (reducedMotion) return@LaunchedEffect
        val start = captionStart
        do { captionTime.longValue = withFrameNanos { it } } while ((captionTime.longValue - start) / 1e9f < S.CHAPTER_IN)
        previous = null
    }

    LaunchedEffect(revision) {
        val run = manual
        if (run != null) {
            if (run.from < M.SWING_AT) until(run.start + ((M.SWING_AT - run.from) * 1e9).toLong())
            do {
                val now = withFrameNanos { it }
                time.floatValue = min(run.to, run.from + max(0f, (now - run.start) / 1e9f))
                report(M.chapterAt(time.floatValue), byHand = true)
            } while (time.floatValue < run.to)
            held = if (run.to >= M.LOOP) 0f else run.to
            time.floatValue = held; manual = null; lastTouch = System.nanoTime()
            report(M.chapterAt(held), byHand = true)
            revision++
            return@LaunchedEffect
        }
        if (scrubbing) return@LaunchedEffect
        if (mode == Mode.Hand) {
            if (!automatic) return@LaunchedEffect
            until(lastTouch + (S.USER_HOLD * 1e9).toLong())
            if (held >= M.SWING_AT && held < M.FADE[0]) {
                mode = Mode.Returning
                val start = System.nanoTime()
                do {
                    val e = max(0f, (withFrameNanos { it } - start) / 1e9f)
                    veil.floatValue = max(0f, 1f - e / S.VEIL)
                } while (veil.floatValue > 0f)
                held = M.FADE[1]; veil.floatValue = 1f; time.floatValue = held
            }
            mode = Mode.Auto; origin = System.nanoTime() - (held * 1e9).toLong()
            report(M.chapterAt(held), byHand = false)
        }
        val rest = M.REPRINT + (M.BASE - 1) * M.REPRINT_STEP + M.PRINT_GROW
        val period = (M.LOOP * 1e9).toLong()
        while (true) {
            val now = System.nanoTime()
            val start = origin + ((now - origin) / period) * period
            var t = ((now - start) / 1e9f).coerceAtLeast(0f)
            if (t < M.SWING_AT) until(start + (M.SWING_AT * 1e9).toLong())
            while (t < rest) {
                t = max(0f, (withFrameNanos { it } - start) / 1e9f)
                time.floatValue = t
                report(M.chapterAt(t), byHand = false)
            }
            until(start + period)
            time.floatValue = 0f
            report(0, byHand = false)
        }
    }

    val drawing = CandlePatternWalkthroughDrawing(swingHighLabel, trendLineLabel, breakoutLabel, retestLabel, confirmationLabel, ratioPrefix, upColor, downColor)
    Column(modifier.fillMaxSize().background(S.BACKGROUND)) {
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        Column(Modifier.fillMaxWidth().padding(start = S.MARGIN.dp, end = S.MARGIN.dp, top = insets.calculateTopPadding() + S.HEADER_PAD.dp, bottom = S.HEADER_PAD.dp)) {
            BasicText(title, style = lessonText(S.TITLE_SIZE, S.TITLE_HEIGHT, S.INK, FontWeight.ExtraBold, S.TITLE_TRACKING), maxLines = 1, modifier = Modifier.height(S.TITLE_HEIGHT.dp))
            Spacer(Modifier.height(S.HINT_MARGIN.dp))
            BasicText(hint, style = lessonText(S.HINT_SIZE, S.HINT_HEIGHT, S.MUTE), maxLines = 1, modifier = Modifier.height(S.HINT_HEIGHT.dp))
        }
        Box(
            Modifier.fillMaxWidth().weight(1f).clipToBounds()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var delta = Offset.Zero
                        var dragging = false
                        do {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            delta = change.position - down.position
                            if (!dragging && abs(delta.x) > S.PAN_SLOP.dp.toPx()) {
                                takeOver(); dragging = true; scrubbing = true; scrubFrom = held
                            }
                            if (dragging) {
                                held = (scrubFrom + delta.x / size.width * M.LOOP).coerceIn(0f, M.LOOP - S.SCRUB_END_EPSILON)
                                time.floatValue = held
                                report(M.chapterAt(held), byHand = true)
                                change.consume()
                            }
                        } while (change.pressed)
                        if (dragging) { scrubbing = false; lastTouch = System.nanoTime(); revision++ }
                        else if (abs(delta.x) <= S.PAN_SLOP.dp.toPx() && abs(delta.y) <= S.TAP_SLOP.dp.toPx()) next()
                    }
                }
                .semantics {
                    contentDescription = title; stateDescription = chapters[chapter]
                    progressBarRangeInfo = ProgressBarRangeInfo(chapter.toFloat(), 0f..6f, 5)
                    setProgress { requested -> if (requested > chapter) next(); true }
                    customActions = listOf(CustomAccessibilityAction("Next step") { next(); true })
                },
        ) {
            for (pass in 0..7) {
                val glow = pass == 1 || pass == 4 || pass == 6
                val blur = if (pass == 6) S.CONFETTI_BLUR else S.GLOW_BLUR
                Canvas(
                    Modifier.fillMaxSize().then(
                        if (!glow) Modifier else if (Build.VERSION.SDK_INT >= 31) Modifier.blur(sigmaToRadius(blur).dp, BlurredEdgeTreatment.Unbounded)
                        else Modifier.graphicsLayer { alpha = S.UNBLURRED_GLOW },
                    ),
                ) {
                    surface(0.dp) { _, _, w, h -> drawing.draw(this, w, h, time.floatValue, veil.floatValue, reducedMotion, pass) }
                }
            }
            Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(S.EDGE_FADE.dp).background(Brush.verticalGradient(listOf(S.BACKGROUND, Color.Transparent))))
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(S.EDGE_FADE.dp).background(Brush.verticalGradient(listOf(Color.Transparent, S.BACKGROUND))))
        }
        Column(Modifier.fillMaxWidth().padding(start = S.MARGIN.dp, end = S.MARGIN.dp, top = S.FOOTER_PAD.dp, bottom = max(insets.calculateBottomPadding().value, S.FOOTER_PAD).dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(S.SEGMENT_GAP.dp)) {
                chapters.forEachIndexed { k, _ ->
                    Box(Modifier.weight(1f).height(S.SEGMENT_HEIGHT.dp).background(S.TRACK, RoundedCornerShape(S.SEGMENT_RADIUS.dp)).clip(RoundedCornerShape(S.SEGMENT_RADIUS.dp))) {
                        Box(Modifier.fillMaxSize().graphicsLayer {
                            val t = time.floatValue
                            scaleX = if (t >= M.FADE[1]) 0f else M.clamp01((t - M.CHAPTER_STARTS[k]) / (M.STOPS[k] - M.CHAPTER_STARTS[k])) * (1f - M.smooth(t, M.FADE[0], M.FADE[1]))
                            transformOrigin = TransformOrigin(0f, 0.5f)
                        }.background(S.INK))
                    }
                }
            }
            Spacer(Modifier.height(S.CAPTION_MARGIN.dp))
            Row(Modifier.fillMaxWidth().height(S.CAPTION_HEIGHT.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicText((chapter + 1).toString().padStart(2, '0'), style = lessonText(S.STEP_SIZE, S.STEP_HEIGHT, S.MUTE, FontWeight.SemiBold).copy(fontFeatureSettings = "tnum"), modifier = Modifier.width(S.STEP_WIDTH.dp).alignByBaseline())
                Box(Modifier.alignByBaseline()) {
                    BasicText(chapters[chapter], style = lessonText(S.CHAPTER_SIZE, S.CHAPTER_HEIGHT, S.INK, FontWeight.Bold), maxLines = 1, modifier = Modifier.graphicsLayer {
                        alpha = if (reducedMotion) 1f else fade((captionTime.longValue - captionStart) / 1e9f / S.CHAPTER_IN)
                    })
                    previous?.let { old ->
                        BasicText(chapters[old], style = lessonText(S.CHAPTER_SIZE, S.CHAPTER_HEIGHT, S.INK, FontWeight.Bold), maxLines = 1, modifier = Modifier.graphicsLayer {
                            alpha = if (reducedMotion) 0f else 1f - fade((captionTime.longValue - captionStart) / 1e9f / S.CHAPTER_OUT)
                        })
                    }
                }
            }
        }
    }
}

private fun fade(value: Float): Float {
    val u = value.coerceIn(0f, 1f)
    return if (u < 0.5f) 2f * u * u else 1f - 2f * (1f - u) * (1f - u)
}

private suspend fun until(target: Long) {
    while (true) {
        val left = target - System.nanoTime()
        if (left <= 0L) return
        delay((left + 999_999L) / 1_000_000L)
    }
}
