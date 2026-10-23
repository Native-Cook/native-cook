package com.ngocdevv.nativecook.blocks.progress.stepplannerprogress

import com.ngocdevv.nativecook.shared.EaseOutStrong
import com.ngocdevv.nativecook.shared.PRESS_SCALE
import com.ngocdevv.nativecook.shared.PRESS_SCALE_MS
import com.ngocdevv.nativecook.shared.SoftRoundRect
import com.ngocdevv.nativecook.shared.bleed
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.rgba
import com.ngocdevv.nativecook.shared.sigmaToRadius
import com.ngocdevv.nativecook.shared.surface
import android.graphics.BlurMaskFilter
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

private const val WIDTH = 330f
private const val HEIGHT = 374f
private const val RADIUS = 26f

// The canvas overflows the card by this much on every side to fit its shadow.
private val BLEED = 36.dp

private const val INSET = 20.5f
private const val INNER_W = WIDTH - INSET * 2
private const val TABS_Y = 61f
private const val TABS_H = 39f
private const val PILL_Y = 65f
private const val PILL_H = 31f
private const val STEP_Y = 131f
private const val SEG_Y = 152f
private const val SEG_H = 7.3f
private const val SEG_GAP = 3.3f
private const val LIST_Y = 181.5f
private const val LIST_H = 137f
private const val LIST_R = 20f
private const val ROW_FIRST = 23f
private const val ROW_PITCH = 45f
private const val ROW_X = 43.7f
private const val ROW_R = 12f
private const val ROW_TEXT_X = 70f
private const val ROWS_SHOWN = 3
private const val FOOTER_Y = 350f

// The reference rests with two of five steps done.
private const val REST_DONE = 2

/** Left edge and width of a tab's pill, and where its icon and label sit. */
private class TabFrame(val x: Float, val w: Float, val icon: Float, val text: Float)

private val TabFrames = listOf(
    TabFrame(25f, 86f, 46.5f, 59f),
    TabFrame(112f, 98f, 131f, 144f),
    TabFrame(213f, 91.5f, 233f, 246f),
)

// The autoPlay run is one clock in seconds: the parts arrive, each step completes, a hold, a fade.
private const val ENTER_FIRST = 0.1f
private const val ENTER_STAGGER = 0.09f
private const val ENTER_LENGTH = 0.5f
private const val WAVE_FIRST = 0.45f
private const val WAVE_STAGGER = 0.07f
private const val WAVE_LENGTH = 0.4f
private const val STEP_FIRST = 1.5f
private const val STEP_EVERY = 1.5f
private const val STEP_LENGTH = 0.7f
private const val HOLD = 2.6f
private const val EXIT = 0.4f
private const val GAP = 0.3f

private val Green = Color(0xFF7AFA9B)
private val Well = Color(0xFF17181D)
private val LabelDim = Color(0xFF4D5060)
private val LabelNow = Color(0xFFE9EAF0)
private val LabelDone = Color(0xFF8A8FA2)

private val CenterLine = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)
private val NoFontPadding = PlatformTextStyle(includeFontPadding = false)

private fun label(
    color: Color,
    size: Float,
    lineHeight: Float,
    weight: FontWeight = FontWeight.Normal,
    tracking: Float = 0f,
) = TextStyle(
    color = color,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    letterSpacing = tracking.sp,
    fontFeatureSettings = "tnum",
    lineHeightStyle = CenterLine,
    platformStyle = NoFontPadding,
)

private val TitleStyle = label(Color(0xFFDCD8D3), 17.5f, 22f, FontWeight.SemiBold)
private val TabStyle = label(Color(0xFFC6C9D2), 14f, 18f)
private val TabSelectedStyle = label(Color(0xFFF0F2F6), 14f, 18f, FontWeight.Bold)
private val StepStyle = label(Color(0xFFE6E6EA), 11.5f, 18f, FontWeight.Medium, tracking = 1f)
private val PercentStyle = label(Green, 13.5f, 18f, FontWeight.Bold)
private val CompleteStyle = label(Color(0xFFECEEF2), 13.5f, 18f)
private val RowStyle = label(LabelNow, 14f, 20f)
private val FooterStyle = label(Color(0xFFE0DCD6), 15.5f, 20f, FontWeight.SemiBold)
private val BadgeStyle = label(Color(0xFF747884), 9.5f, 12f, FontWeight.SemiBold, tracking = 0.6f)

private fun seg(t: Float, from: Float, to: Float) = ((t - from) / (to - from)).coerceIn(0f, 1f)

private fun easeOut(x: Float) = 1f - (1f - x).pow(3)

private fun easeInOut(x: Float) = if (x < 0.5f) 4f * x * x * x else 1f - (-2f * x + 2f).pow(3) / 2f

private fun easeOutBack(x: Float): Float {
    val c = 1.7f
    return 1f + (c + 1f) * (x - 1f).pow(3) + c * (x - 1f).pow(2)
}

/** How far the checklist has scrolled: the current step stays on the middle row. */
private fun scrollOf(done: Float, count: Int) =
    (done - 1f).coerceIn(0f, max(count - ROWS_SHOWN, 0).toFloat()) * ROW_PITCH

/** Label colour for a row: grey once done, white while current, dim while still to come. */
private fun labelColor(ahead: Float) =
    if (ahead < 0f) lerp(LabelDim, LabelNow, seg(ahead, -0.4f, 0f)) else lerp(LabelNow, LabelDone, seg(ahead, 0.6f, 1f))

/** A solid-colour paint with a soft edge. Hardware canvases only honour mask filters from API 28. */
private fun softPaint(color: Color, sigma: Float, stroke: Float = 0f): Paint? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        Paint().apply {
            this.color = color
            if (stroke > 0f) {
                style = PaintingStyle.Stroke
                strokeWidth = stroke
            }
            asFrameworkPaint().maskFilter = BlurMaskFilter(sigmaToRadius(sigma), BlurMaskFilter.Blur.NORMAL)
        }
    } else {
        null
    }

private class PlannerMotion {
    /** Seconds into the autoPlay run. */
    var t by mutableFloatStateOf(0f)

    /** Free-running seconds for the light that circles the current segment. */
    var time by mutableFloatStateOf(0f)

    /** Steps done while controlled. */
    val target = Animatable(0f)

    val shadow = SoftRoundRect(rgba(0, 0, 0, 0.55f), sigma = 14f)
    val outlineGlow = softPaint(rgba(122, 250, 155, 0.4f), 3f, stroke = 3f)
    val fillGlow = softPaint(rgba(122, 250, 155, 0.55f), 5f)
    val glint = softPaint(Color.White, 4f)
    val tickGlow = softPaint(rgba(122, 250, 155, 0.5f), 4f)

    val shape = Path()
    val list = Path()
    val calendarTop = Path().apply {
        addRoundRect(RoundRect(Rect(-5.4f, -4.6f, 5.4f, -1.4f), topLeft = CornerRadius(2f), topRight = CornerRadius(2f)))
    }
    val arrow = Path().apply {
        moveTo(0f, 0f)
        lineTo(5f, -5f)
        moveTo(3f, -5.6f)
        lineTo(5.6f, -5.6f)
        lineTo(5.6f, -3f)
    }
    val check = Path().apply {
        moveTo(-3.1f, 0.2f)
        lineTo(-1f, 2.3f)
        lineTo(3.2f, -2.1f)
    }
    val checkMeasure = PathMeasure().apply { setPath(check, false) }
    val drawnCheck = Path()
    val scallops = PathEffect.dashPathEffect(floatArrayOf(2.6f, 2.27f))

    fun exitAt(rest: Int) = STEP_FIRST + (rest - 1) * STEP_EVERY + STEP_LENGTH + HOLD

    /** Steps done, a fraction while one is completing. */
    fun done(controlled: Boolean, playing: Boolean, rest: Int): Float {
        if (controlled) return target.value
        if (!playing) return rest.toFloat()
        var d = 0f
        for (k in 0 until rest) {
            val begin = STEP_FIRST + k * STEP_EVERY
            d += easeInOut(seg(t, begin, begin + STEP_LENGTH))
        }
        return d
    }

    /** How far the card's [part]-th block has arrived: they come in one after another when a run starts. */
    fun arrived(part: Int, playing: Boolean): Float {
        if (!playing) return 1f
        val begin = ENTER_FIRST + ENTER_STAGGER * part
        return easeOut(seg(t, begin, begin + ENTER_LENGTH))
    }

    fun alpha(part: Int, playing: Boolean, rest: Int): Float {
        if (!playing) return 1f
        val exit = exitAt(rest)
        return arrived(part, playing) * (1f - seg(t, exit, exit + EXIT))
    }
}

/**
 * "Progress & Planning" panel: tabs, a "STEP 3 OF 5 · 40% Complete" row over a segmented bar, and
 * a checklist that keeps the current step on its middle row. Completing a step sweeps its segment
 * full, moves the outlined segment (with its circling light) on, ticks the row and scrolls the list.
 *
 * @param progress Share of the steps completed, 0…1. Leave null to show the reference's 2 of 5 (40%).
 * @param autoPlay Plays by itself: the card's parts arrive one after another, two steps complete, hold, start over.
 */
@Composable
fun StepPlannerProgress(
    progress: Float? = null,
    autoPlay: Boolean = false,
    title: String = "Progress & Planning",
    steps: List<String> = listOf("Create Account", "Business Details", "Payment Method", "Team Setup", "Final Review"),
    tabs: List<String> = listOf("Setup", "Timeline", "Goals"),
    label: String = "Setup progress",
    onTabChange: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val still = rememberReducedMotion()
    val view = LocalView.current
    val motion = remember { PlannerMotion() }
    val count = steps.size
    val controlled = progress != null
    val playing = autoPlay && !controlled && !still
    val rest = min(REST_DONE, count)
    var tab by remember { mutableIntStateOf(0) }
    var run by remember { mutableIntStateOf(0) }

    var primed by remember { mutableIntStateOf(0) }
    LaunchedEffect(progress, count) {
        if (progress == null) return@LaunchedEffect
        val next = progress.coerceIn(0f, 1f) * count
        // The first value is shown without a run-up.
        if (primed++ == 0) motion.target.snapTo(next) else motion.target.animateTo(next, tween(if (still) 200 else 700, easing = EaseOutStrong))
    }

    LaunchedEffect(still, playing, run, rest) {
        if (still) return@LaunchedEffect
        val start = withFrameNanos { it }
        val loop = motion.exitAt(rest) + EXIT + GAP
        var last = start
        while (true) {
            val now = withFrameNanos { it }
            motion.time += min((now - last) / 1e9f, 0.064f)
            last = now
            if (playing) motion.t = ((now - start) / 1e9f) % loop
        }
    }

    // The selected tab's pill slides and resizes to the tab that was chosen.
    val frame = TabFrames[tab.coerceIn(0, TabFrames.lastIndex)]
    val glide = tween<Float>(if (still) 150 else 320, easing = EaseOutStrong)
    val pillX by animateFloatAsState(frame.x, glide, label = "pillX")
    val pillW by animateFloatAsState(frame.w, glide, label = "pillW")

    // Index of the step being worked on: what "STEP n OF m" and the footer name.
    val current by remember(controlled, playing, rest, count) {
        derivedStateOf { min((motion.done(controlled, playing, rest) + 0.001f).toInt(), count - 1).coerceAtLeast(0) }
    }
    val name = steps.getOrElse(current) { "" }

    fun Modifier.entrance(part: Int) = graphicsLayer {
        alpha = motion.alpha(part, playing, rest)
        translationY = 10f * (1f - motion.arrived(part, playing)) * density
    }

    Box(
        modifier = modifier
            .size(WIDTH.dp, HEIGHT.dp)
            .semantics {
                contentDescription = label
                stateDescription = "Step ${current + 1} of $count, $name"
                progressBarRangeInfo = ProgressBarRangeInfo((progress ?: (rest.toFloat() / max(count, 1))).coerceIn(0f, 1f), 0f..1f)
            },
    ) {
        Box(Modifier.matchParentSize().bleed(BLEED)) {
            Canvas(Modifier.fillMaxSize()) {
                surface(BLEED) { x, y, _, _ ->
                    val done = motion.done(controlled, playing, rest)

                    drawIntoCanvas { motion.shadow.draw(it.nativeCanvas, x + 8, y + 16, WIDTH - 16, HEIGHT - 16, RADIUS) }
                    drawRoundRect(
                        brush = Brush.verticalGradient(listOf(Color(0xFF141416), Color(0xFF1C1D22)), startY = y, endY = y + HEIGHT),
                        topLeft = Offset(x, y),
                        size = Size(WIDTH, HEIGHT),
                        cornerRadius = CornerRadius(RADIUS),
                    )
                    drawRoundRect(
                        rgba(255, 255, 255, 0.05f), Offset(x + 0.5f, y + 0.5f), Size(WIDTH - 1, HEIGHT - 1),
                        CornerRadius(RADIUS - 0.5f), style = Stroke(1f),
                    )

                    withTransform({ translate(0f, 10f * (1f - motion.arrived(1, playing))) }) {
                        drawTabs(motion, x, y, tab, pillX, pillW, motion.alpha(1, playing, rest))
                    }
                    withTransform({ translate(0f, 10f * (1f - motion.arrived(2, playing))) }) {
                        val alpha = motion.alpha(2, playing, rest)
                        for (index in 0 until count) {
                            drawSegment(motion, index, count, x, y, done, playing, still, alpha)
                        }
                    }
                    withTransform({ translate(0f, 10f * (1f - motion.arrived(3, playing))) }) {
                        val alpha = motion.alpha(3, playing, rest)
                        motion.list.reset()
                        motion.list.addRoundRect(
                            RoundRect(x + INSET, y + LIST_Y, x + INSET + INNER_W, y + LIST_Y + LIST_H, CornerRadius(LIST_R)),
                        )
                        drawPath(
                            motion.list,
                            brush = Brush.verticalGradient(
                                listOf(Color(0xFF282A36), Color(0xFF22242E)),
                                startY = y + LIST_Y,
                                endY = y + LIST_Y + LIST_H,
                            ),
                            alpha = alpha,
                        )
                        clipPath(motion.list) {
                            for (index in 0 until count) drawRow(motion, index, count, x, y, done, alpha)
                        }
                    }
                }
            }
        }

        BasicText(title, Modifier.offset(20.dp, 21.5.dp).entrance(0).clearAndSetSemantics {}, style = TitleStyle)

        Box(Modifier.matchParentSize().entrance(1)) {
            TabFrames.forEachIndexed { index, item ->
                val interaction = remember { MutableInteractionSource() }
                val pressed by interaction.collectIsPressedAsState()
                val scale by animateFloatAsState(
                    targetValue = if (pressed) PRESS_SCALE else 1f,
                    animationSpec = tween(PRESS_SCALE_MS, easing = EaseOutStrong),
                    label = "tab",
                )
                LaunchedEffect(pressed) {
                    if (pressed) view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                }
                Box(
                    modifier = Modifier
                        .offset(item.x.dp, PILL_Y.dp)
                        .size(item.w.dp, PILL_H.dp)
                        .clip(CircleShape)
                        .clickable(interactionSource = interaction, indication = null, role = Role.Tab) {
                            tab = index
                            onTabChange(index)
                            // Choosing a tab also restarts the demo run.
                            run++
                        }
                        .semantics { selected = index == tab }
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        },
                    contentAlignment = Alignment.CenterStart,
                ) {
                    BasicText(
                        tabs.getOrElse(index) { "" },
                        Modifier.offset(x = (item.text - item.x).dp),
                        style = if (index == tab) TabSelectedStyle else TabStyle,
                        maxLines = 1,
                    )
                }
            }
        }

        Box(Modifier.matchParentSize().entrance(2).clearAndSetSemantics {}) {
            BasicText("STEP ${current + 1} OF $count", Modifier.offset(21.dp, (STEP_Y - 9f).dp), style = StepStyle)
            Row(
                Modifier.offset(INSET.dp, (STEP_Y - 9f).dp).width(INNER_W.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End,
            ) {
                // Reads the animated value here, so only this number recomposes while it counts.
                Percent(motion, controlled, playing, rest, count, Modifier.alignByBaseline())
                BasicText("Complete", Modifier.alignByBaseline().padding(start = 4.dp), style = CompleteStyle)
            }
        }

        Box(
            Modifier
                .offset(INSET.dp, LIST_Y.dp)
                .size(INNER_W.dp, LIST_H.dp)
                .entrance(3)
                .clip(RoundedCornerShape(LIST_R.dp))
                .clearAndSetSemantics {},
        ) {
            steps.forEachIndexed { index, step ->
                BasicText(
                    step,
                    Modifier
                        .offset((ROW_TEXT_X - INSET).dp, (ROW_FIRST + ROW_PITCH * index - 10f).dp)
                        .graphicsLayer { translationY = -scrollOf(motion.done(controlled, playing, rest), count) * density },
                    style = RowStyle,
                    maxLines = 1,
                    color = { labelColor(motion.done(controlled, playing, rest) - index) },
                )
            }
        }

        Row(
            Modifier.offset(20.dp, (FOOTER_Y - 11f).dp).height(22.dp).entrance(4).clearAndSetSemantics {},
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(name.take(1) + name.drop(1).lowercase(), style = FooterStyle, maxLines = 1)
            Box(
                Modifier
                    .padding(start = 9.dp)
                    .height(18.dp)
                    .background(Color(0xFF1F2127), CircleShape)
                    .border(1.dp, Color(0xFF383A44), CircleShape)
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                BasicText("CURRENT", style = BadgeStyle)
            }
        }
    }
}

@Composable
private fun Percent(motion: PlannerMotion, controlled: Boolean, playing: Boolean, rest: Int, count: Int, modifier: Modifier) {
    val percent = (motion.done(controlled, playing, rest) / max(count, 1) * 100f).roundToInt()
    BasicText("$percent%", modifier, style = PercentStyle)
}

/** The tab strip: its sliding pill, then a dashboard grid, a calendar and a target. */
private fun DrawScope.drawTabs(motion: PlannerMotion, x: Float, y: Float, tab: Int, pillX: Float, pillW: Float, alpha: Float) {
    val cy = y + TABS_Y + TABS_H / 2
    drawRoundRect(Color(0xFF20222B), Offset(x + INSET, y + TABS_Y), Size(INNER_W, TABS_H), CornerRadius(TABS_H / 2), alpha = alpha)
    drawRoundRect(Color(0xFF3A3E4B), Offset(x + pillX, y + PILL_Y), Size(pillW, PILL_H), CornerRadius(PILL_H / 2), alpha = alpha)

    fun tint(index: Int) = if (index == tab) Color(0xFFF0F2F6) else Color(0xFFB3B7C2)
    val r = CornerRadius(1.3f)
    withTransform({ translate(x + TabFrames[0].icon, cy) }) {
        drawRoundRect(tint(0), Offset(-5.8f, -5.8f), Size(5f, 6.6f), r, alpha = alpha)
        drawRoundRect(tint(0), Offset(0.8f, -5.8f), Size(5f, 4f), r, alpha = alpha)
        drawRoundRect(tint(0), Offset(-5.8f, 2.4f), Size(5f, 3.4f), r, alpha = alpha)
        drawRoundRect(tint(0), Offset(0.8f, -0.2f), Size(5f, 6f), r, alpha = alpha)
    }
    withTransform({ translate(x + TabFrames[1].icon, cy) }) {
        drawRoundRect(tint(1), Offset(-5.4f, -4.6f), Size(10.8f, 10f), CornerRadius(2f), alpha = alpha, style = Stroke(1.3f))
        drawPath(motion.calendarTop, tint(1), alpha = alpha)
        for (px in listOf(-2.6f, 2.6f)) {
            drawLine(tint(1), Offset(px, -6.4f), Offset(px, -4.4f), 1.3f, StrokeCap.Round, alpha = alpha)
        }
    }
    withTransform({ translate(x + TabFrames[2].icon, cy) }) {
        val stroke = Stroke(1.3f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawArc(tint(2), 6f, 258f, false, Offset(-5.6f, -5.6f), Size(11.2f, 11.2f), alpha = alpha, style = stroke)
        drawArc(tint(2), 9f, 252f, false, Offset(-2.5f, -2.5f), Size(5f, 5f), alpha = alpha, style = stroke)
        drawPath(motion.arrow, tint(2), alpha = alpha, style = stroke)
    }
}

/** One segment of the step bar: grey, outlined with a circling light while current, or filled. */
private fun DrawScope.drawSegment(
    motion: PlannerMotion,
    index: Int,
    count: Int,
    x: Float,
    y: Float,
    done: Float,
    playing: Boolean,
    still: Boolean,
    alpha: Float,
) {
    val w = (INNER_W - SEG_GAP * (count - 1)) / count
    val left = x + INSET + (w + SEG_GAP) * index
    val top = y + SEG_Y
    val r = CornerRadius(SEG_H / 2)
    // The segments grow in from the left one after another when a run starts.
    val begin = WAVE_FIRST + WAVE_STAGGER * index
    val grown = if (playing) max(0f, easeOutBack(seg(motion.t, begin, begin + WAVE_LENGTH))) else 1f

    withTransform({ scale(grown, 1f, pivot = Offset(left, top)) }) {
        drawRoundRect(Color(0xFF2C3039), Offset(left, top), Size(w, SEG_H), r, alpha = alpha)

        val current = seg(done - index, -0.25f, 0f) * (1f - seg(done - index, 0.8f, 1f)) * alpha
        if (current > 0f) {
            val inset = Offset(left + 0.6f, top + 0.6f)
            val inner = Size(w - 1.2f, SEG_H - 1.2f)
            drawRoundRect(rgba(122, 250, 155, 0.14f), Offset(left, top), Size(w, SEG_H), r, alpha = current)
            motion.outlineGlow?.let { glow ->
                glow.alpha = 0.4f * current
                drawIntoCanvas {
                    it.drawRoundRect(inset.x, inset.y, inset.x + inner.width, inset.y + inner.height, SEG_H / 2, SEG_H / 2, glow)
                }
            }
            drawRoundRect(rgba(122, 250, 155, 0.8f), inset, inner, r, alpha = current, style = Stroke(1.2f))
            if (!still) {
                val perimeter = 2f * (w - SEG_H) + Math.PI.toFloat() * SEG_H
                drawRoundRect(
                    Color(0xFFEAFFF0), inset, inner, r,
                    alpha = current,
                    style = Stroke(
                        1.4f,
                        cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(perimeter * 0.16f, perimeter * 0.84f), -motion.time * 42f),
                    ),
                )
            }
        }

        // 0…1 while this step completes: the fill sweeps across from the left.
        val filled = (done - index).coerceIn(0f, 1f)
        if (filled <= 0f) return@withTransform
        val lit = min(1f, filled * 8f) * alpha
        clipRect(left, top - 20f, left + w * filled, top + SEG_H + 20f) {
            motion.fillGlow?.let { glow ->
                glow.alpha = 0.55f * lit
                drawIntoCanvas { it.drawRoundRect(left, top, left + w, top + SEG_H, SEG_H / 2, SEG_H / 2, glow) }
            }
        }
        motion.shape.reset()
        motion.shape.addRoundRect(RoundRect(left, top, left + w, top + SEG_H, r))
        clipPath(motion.shape) {
            drawRect(Green, Offset(left, top), Size(w * filled, SEG_H), alpha = lit)
            // A glint rides the leading edge of the sweep.
            if (filled < 1f) {
                motion.glint?.let { glint ->
                    glint.alpha = sin(Math.PI.toFloat() * filled) * lit
                    drawIntoCanvas { it.drawCircle(Offset(left + w * filled, top + SEG_H / 2), 7f, glint) }
                }
            }
        }
    }
}

/** One step of the checklist: ticked, current, or still to come (scalloped). */
private fun DrawScope.drawRow(motion: PlannerMotion, index: Int, count: Int, x: Float, y: Float, done: Float, alpha: Float) {
    val cy = y + LIST_Y + ROW_FIRST + ROW_PITCH * index - scrollOf(done, count)
    // Rows scrolled out of the list need no drawing.
    if (cy < y + LIST_Y - ROW_PITCH || cy > y + LIST_Y + LIST_H + ROW_PITCH) return
    val c = Offset(x + ROW_X, cy)

    if (index < count - 1) {
        drawLine(Color(0xFF30323D), Offset(c.x, cy + ROW_R + 4f), Offset(c.x, cy + ROW_PITCH - ROW_R - 4f), 1f, alpha = alpha)
    }
    val upcoming = seg(index - done, 0.3f, 0.9f)
    if (upcoming > 0f) {
        drawCircle(Well, ROW_R + 0.4f, c, alpha = upcoming * alpha, style = Stroke(2.4f, pathEffect = motion.scallops))
    }
    drawCircle(Well, ROW_R, c, alpha = alpha)
    drawCircle(rgba(255, 255, 255, 0.06f), ROW_R - 0.5f, c, alpha = alpha, style = Stroke(1f))

    // 0…1 while this step completes.
    val ticked = (done - index).coerceIn(0f, 1f)
    // A ring spreads from the circle as its tick lands.
    if (ticked > 0.25f && ticked < 1f) {
        val spread = seg(ticked, 0.25f, 1f)
        drawCircle(Green, ROW_R + 10f * easeOut(spread), c, alpha = 0.7f * (1f - spread) * alpha, style = Stroke(1.4f))
    }
    val shown = seg(ticked, 0.25f, 0.5f) * alpha
    if (shown <= 0f) return
    drawCircle(rgba(122, 250, 155, 0.28f), ROW_R - 0.5f, c, alpha = shown, style = Stroke(1f))
    val pop = 0.4f + 0.6f * easeOutBack(seg(ticked, 0.25f, 0.85f))
    withTransform({
        translate(c.x, c.y)
        scale(pop, pop, pivot = Offset.Zero)
    }) {
        motion.tickGlow?.let { glow ->
            glow.alpha = 0.5f * shown
            drawIntoCanvas { it.drawCircle(Offset.Zero, 8f, glow) }
        }
        drawCircle(Green, 6.7f, Offset.Zero, alpha = shown)
        val drawn = seg(ticked, 0.55f, 1f)
        if (drawn > 0f) {
            motion.drawnCheck.reset()
            motion.checkMeasure.getSegment(0f, motion.checkMeasure.length * drawn, motion.drawnCheck, true)
            drawPath(
                motion.drawnCheck, Color(0xFF10311A), alpha = shown,
                style = Stroke(1.6f, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}
