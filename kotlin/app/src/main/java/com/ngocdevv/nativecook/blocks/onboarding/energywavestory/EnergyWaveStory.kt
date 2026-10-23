package com.ngocdevv.nativecook.blocks.onboarding.energywavestory

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.ngocdevv.nativecook.shared.SoftRoundRect
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.rgba
import com.ngocdevv.nativecook.shared.surface
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * @param headline Its lines are broken by hand with "\n", so an emoji never moves a word to the next line.
 * @param tags One emoji a tag: over the first crest, over the second, under the trough. The first step has none.
 */
data class EnergyWaveStep(val headline: String, val body: String? = null, val tags: List<String> = emptyList())

val SampleEnergyWaveSteps = listOf(
    EnergyWaveStep(
        headline = "Energy in your body\nrises and falls.",
        body = "Your body runs on a daily rhythm, known as the circadian rhythm.",
    ),
    EnergyWaveStep(
        headline = "This cycle controls when\nyou feel ⚡ energetic or\n🥱 tired.",
        tags = listOf("⚡", "⚡", "🥱"),
    ),
    EnergyWaveStep(
        headline = "Sync with your rhythm to\nsleep better, feel more\nenergised, and be more\nproductive.",
        body = "Crest can help you find the ideal time for a 👟 run, 😴 nap or when you should 📵 wind down for sleep.",
        tags = listOf("👟", "📵", "😴"),
    ),
)

private val WaveColors = listOf(Color(0xFFF5C12E), Color(0xFF71AAAB), Color(0xFF309BD0))
private val WaveStops = floatArrayOf(0.08f, 0.5f, 0.88f)
private val Empty = Color(0xFFC9C9CE)
private val Panel = Color(0xFFF0EFF6)
private val Background = Color(0xFFFFFCFF)
private val Ink = Color(0xFF111113)
private val Mute = Color(0xFF9A9AA0)
private val Accent = Color(0xFF3B2F8F)
private val Flash = Color(0xFFECEBF3)
private val Sun = Color(0xFFF5B301)
private val Night = Color(0xFF4B3FB5)
private val Shadow = rgba(53, 45, 110, 0.14f)

// The header is drawn for a screen 393 dp wide and scaled to the screen's width.
private const val DESIGN_WIDTH = 393f

// The panel's lower edge at the sides, and how much deeper it hangs in the middle.
private const val PANEL_EDGE = 353f
private const val PANEL_SAG = 10f
private const val SCENE_HEIGHT = PANEL_EDGE + PANEL_SAG * 2f

// The wave rests on this line, and its turning points are joined by half cosines.
private const val BASE = 200f
private val Knots = listOf(67f to 200f, 125f to 145f, 188f to 187f, 251f to 158f, 324f to 200f)
private val WaveFrom = Knots.first().first
private val WaveTo = Knots.last().first

// The height of the taller crest: a rise of FLEX dp is this share of it.
private val Crest = BASE - Knots[1].second
private const val STROKE = 14f
private const val SAMPLES = 96
private const val DISC = 34f
private val Discs = floatArrayOf(34f, 357f)
private const val TAG_WIDTH = 64f
private const val TAG_HEIGHT = 38f
private const val TAG_RADIUS = 9f
private const val TAIL = 8f

// A square turned by 45° whose corner sticks out of the tag by TAIL.
private val TailSide = TAIL * sqrt(2f)

/** The middle of a tag, and whether its tail points down at the wave. */
private class TagSpot(val x: Float, val y: Float, val down: Boolean)

private val TagSpots = listOf(TagSpot(126f, 95f, true), TagSpot(269f, 113f, true), TagSpot(197f, 235f, false))

// The leading stretch of the colour is a little lighter, in this many layers of white.
private const val HEAD = 30f
private const val HEAD_LAYERS = 5
private const val HEAD_LIGHT = 0.05

// A finger within this band, in the header's own dp, takes hold of the colour.
private val ScrubBand = 110f..240f

// A touch that moves less than this is a tap.
private const val SLOP = 6f

private const val MARGIN = 30f
private const val HEADLINE = 27f
private const val BODY_GAP = 7f
private const val FOOTER_WIDTH = 232f
private const val FOOTER_HEIGHT = 36f
private const val FOOTER_GAP = 12f

private fun waveY(x: Float): Float {
    var i = 0
    while (i < Knots.size - 2 && x > Knots[i + 1].first) i++
    val (x0, y0) = Knots[i]
    val (x1, y1) = Knots[i + 1]
    return y0 + (y1 - y0) * (1f - cos(PI.toFloat() * (x - x0) / (x1 - x0))) / 2f
}

private val Points = List(SAMPLES + 1) { i ->
    val x = WaveFrom + (WaveTo - WaveFrom) * i / SAMPLES
    Offset(x, waveY(x))
}

// The length of the wave up to each sample: the colour is trimmed by length, a finger moves by x.
private val Lengths = FloatArray(SAMPLES + 1).also { sums ->
    for (i in 1..SAMPLES) sums[i] = sums[i - 1] + hypot(Points[i].x - Points[i - 1].x, Points[i].y - Points[i - 1].y)
}

private val WavePath = Path().apply {
    Points.forEachIndexed { i, point -> if (i == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y) }
}

private val PanelPath = Path().apply {
    moveTo(0f, 0f)
    lineTo(DESIGN_WIDTH, 0f)
    lineTo(DESIGN_WIDTH, PANEL_EDGE)
    quadraticTo(DESIGN_WIDTH / 2f, PANEL_EDGE + PANEL_SAG * 2f, 0f, PANEL_EDGE)
    close()
}

private val WaveStroke = Stroke(STROKE, cap = StrokeCap.Round, join = StrokeJoin.Round)

/** The share of the wave's length that lies left of x. */
private fun lengthAt(x: Float): Double {
    val place = ((x - WaveFrom) / (WaveTo - WaveFrom)).coerceIn(0f, 1f) * SAMPLES
    val i = min(SAMPLES - 1, place.toInt())
    return ((Lengths[i] + (Lengths[i + 1] - Lengths[i]) * (place - i)) / Lengths[SAMPLES]).toDouble()
}

private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun storyText(size: Float, lineHeight: Float, weight: FontWeight, spacing: Float = 0f) = TextStyle(
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    letterSpacing = spacing.sp,
    lineHeightStyle = Centered,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private val HeadlineStyle = storyText(HEADLINE, 37f, FontWeight.Bold, -0.2f)

// The lines are the headline's own, so it shrinks before it would wrap.
private val HeadlineFit = TextAutoSize.StepBased(minFontSize = (HEADLINE * 0.7f).sp, maxFontSize = HEADLINE.sp, stepSize = 0.5.sp)
private val BodyStyle = storyText(14f, 19f, FontWeight.Normal)
private val FooterStyle = storyText(14f, 18f, FontWeight.SemiBold)
private val EmojiStyle = storyText(16f, 22f, FontWeight.Normal).copy(color = Color.Black)

private fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

/** What changed the step: a tap, the loop that plays by itself, or the return to rest. */
private enum class How { Tap, Auto, Rest }

/**
 * A three-step explainer under a wave. The wave is grey, then colour runs along it; a tap brings
 * three emoji tags out of its crests and trough; another changes them. On the first step a finger
 * dragged along the wave moves the colour, and a tapped tag hops.
 *
 * @param steps The three steps: the wave fills with colour, the tags arrive, the tags change.
 * @param waveColors The wave's colours from its crests down to its troughs.
 * @param autoPlay Goes through the steps by itself every [LOOP] seconds.
 * @param onDone Called when the last step is left.
 */
@Composable
fun EnergyWaveStory(
    steps: List<EnergyWaveStep> = SampleEnergyWaveSteps,
    footerLabel: String = "Tap to continue",
    waveColors: List<Color> = WaveColors,
    emptyColor: Color = Empty,
    panelColor: Color = Panel,
    backgroundColor: Color = Background,
    inkColor: Color = Ink,
    muteColor: Color = Mute,
    accentColor: Color = Accent,
    autoPlay: Boolean = false,
    onStepChange: ((step: Int) -> Unit)? = null,
    onDone: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val still = rememberReducedMotion()
    val view = LocalView.current
    // The functions below outlive a recomposition: they read what the caller passes through these.
    val stepChanged by rememberUpdatedState(onStepChange)
    val finished by rememberUpdatedState(onDone)

    // The screen is light: the status bar's icons are dark while it shows.
    DisposableEffect(view) {
        val controller = view.context.activity()?.window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = true
        onDispose { if (previous != null) controller.isAppearanceLightStatusBars = previous }
    }

    // The clock's zero: every time below is in seconds since then.
    val origin = remember { System.nanoTime() }
    // The story opens as it is a moment after the last step was left: grey, with the colour about to set off.
    var scene by remember { mutableStateOf(Scene(step = 0, from = 2, at = -RETURN, fill = 1.0)) }
    // Where a finger left the colour on the first step; negative while the colour runs by itself.
    var scrub by remember { mutableDoubleStateOf(-1.0) }
    var pressed by remember { mutableDoubleStateOf(-10.0) }
    var pressedUntil by remember { mutableDoubleStateOf(-10.0) }
    // When each tag was last tapped.
    var jumps by remember { mutableStateOf(listOf(-10.0, -10.0, -10.0)) }
    // When nothing moves any more: the clock stops there.
    var settled by remember { mutableDoubleStateOf(FILL_AFTER + FILL) }
    // Changes on every touch, which restarts the autoPlay loop behind a pause.
    var touches by remember { mutableIntStateOf(0) }
    // Seconds since `origin`, while something moves. Read while drawing only.
    val clock = remember { mutableDoubleStateOf(0.0) }

    fun now() = (System.nanoTime() - origin) / 1e9

    /** Runs the clock until nothing moves any more. */
    fun settle() {
        settled = maxOf(settledAt(scene, still), pressedUntil + FLASH_OUT, jumps.max() + SPIN)
    }

    /** Shows a step. Taps, the loop that plays by itself and the return to rest all come through here. */
    fun goTo(step: Int, how: How) {
        val t = now()
        val fill = if (scene.step == 0 && scrub >= 0) scrub else fillAt(scene, t)
        scene = Scene(step, from = scene.step, at = t, fill = fill)
        scrub = -1.0
        settle()
        if (how == How.Tap) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        stepChanged?.invoke(step)
        if (step == 0 && how != How.Rest) finished?.invoke()
    }

    fun advance(how: How) = goTo((scene.step + 1) % 3, how)

    /** Lights the pill behind the footer for a while. */
    fun flash(seconds: Double) {
        pressed = now()
        pressedUntil = pressed + seconds
        settle()
    }

    /** A tap at a point of the header's own dp: on a tag it makes the tag hop, anywhere else it goes on. */
    fun tapped(x: Float, y: Float) {
        touches++
        val t = now()
        if (scene.step != 0) {
            for ((index, spot) in TagSpots.withIndex()) {
                val pose = tagPose(index, scene, t, jumps[index], still)
                if (abs(x - spot.x - pose.x) <= TAG_WIDTH / 2f + 6f && abs(y - spot.y - pose.y) <= TAG_HEIGHT / 2f + 8f) {
                    jumps = jumps.mapIndexed { i, at -> if (i == index) t else at }
                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    settle()
                    return
                }
            }
        }
        flash(FLASH_IN)
        advance(How.Tap)
    }

    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { }
            val t = now()
            clock.doubleValue = min(t, settled)
            // At rest nothing is drawn again until a touch or the loop sets something moving.
            if (t >= settled) snapshotFlow { settled }.first { it > now() }
        }
    }

    LaunchedEffect(autoPlay, touches) {
        if (!autoPlay) return@LaunchedEffect
        var start = System.nanoTime()
        suspend fun until(seconds: Double) = delay(max(0L, (start + (seconds * 1e9).toLong() - System.nanoTime()) / 1_000_000))
        if (touches > 0) {
            delay((USER_HOLD * 1000).toLong())
            // The way back to rest takes RETURN, and the loop starts from rest.
            goTo(0, How.Rest)
            start = System.nanoTime() + (RETURN * 1e9).toLong()
        }
        while (true) {
            for (at in AUTO_TAPS) {
                until(at - PRESS_FOR)
                flash(PRESS_FOR)
                until(at)
                advance(How.Auto)
            }
            start += (LOOP * 1e9).toLong()
        }
    }

    val brush = remember(waveColors) {
        val top = BASE - Crest - STROKE / 2f
        val bottom = BASE + STROKE / 2f
        if (waveColors.size == WaveStops.size) {
            Brush.verticalGradient(*Array(WaveStops.size) { WaveStops[it] to waveColors[it] }, startY = top, endY = bottom)
        } else {
            Brush.verticalGradient(waveColors, startY = top, endY = bottom)
        }
    }
    val measure = remember { PathMeasure().apply { setPath(WavePath, false) } }
    val trimmed = remember { Path() }
    val discShadow = remember { SoftRoundRect(Shadow, sigma = 5f) }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(backgroundColor)
            // One touch is a tap anywhere, a tap on a tag, or a finger moving the colour along the wave.
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    // Pixels to one of the header's own dp.
                    val unit = size.width / DESIGN_WIDTH
                    var moved = false
                    var dragging = false
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            if (!moved) tapped(change.position.x / unit, change.position.y / unit)
                            break
                        }
                        if (!moved && (change.position - down.position).getDistance() > SLOP.dp.toPx()) {
                            moved = true
                            if (scene.step == 0 && down.position.y / unit in ScrubBand) {
                                dragging = true
                                touches++
                            }
                        }
                        if (dragging) {
                            scrub = lengthAt(change.position.x / unit)
                            change.consume()
                        }
                    }
                }
            },
    ) {
        val fit = maxWidth.value / DESIGN_WIDTH
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        val bottom = max(insets.calculateBottomPadding().value - 2f, 14f)

        Canvas(Modifier.fillMaxWidth().height((SCENE_HEIGHT * fit).dp).clearAndSetSemantics {}) {
            val t = clock.doubleValue
            val here = scene
            val fill = (if (here.step == 0 && scrub >= 0) scrub else fillAt(here, t)).toFloat()
            val flex = flexAt(here, t, still).toFloat()
            val unit = size.width / DESIGN_WIDTH
            scale(unit, unit, pivot = Offset.Zero) {
                drawPath(PanelPath, panelColor)
                // The wave swells from the line it rests on.
                scale(1f, 1f + flex / Crest, pivot = Offset(0f, BASE)) {
                    drawPath(WavePath, emptyColor, style = WaveStroke)
                    if (fill > 0f) {
                        val end = fill * measure.length
                        // Without motion the colour fades in over the whole wave.
                        trimmed.rewind()
                        measure.getSegment(0f, if (still) measure.length else end, trimmed, true)
                        drawPath(trimmed, brush, alpha = if (still) fill else 1f, style = WaveStroke)
                        // The head is there while the colour is on its way, and gone once it has arrived.
                        val light = if (still) 0.0 else HEAD_LIGHT * smooth(fill.toDouble(), 0.0, 0.05) * (1 - smooth(fill.toDouble(), 0.93, 1.0))
                        if (light > 0.0) {
                            for (layer in 0 until HEAD_LAYERS) {
                                val back = HEAD * (HEAD_LAYERS - layer) / HEAD_LAYERS
                                trimmed.rewind()
                                measure.getSegment(max(0f, end - back), end, trimmed, true)
                                drawPath(trimmed, Color.White, alpha = light.toFloat(), style = WaveStroke)
                            }
                        }
                    }
                }
                for (x in Discs) {
                    drawIntoCanvas { discShadow.draw(it.nativeCanvas, x - DISC / 2f, BASE + 2f - DISC / 2f, DISC, DISC, DISC / 2f) }
                    drawCircle(Color.White, DISC / 2f, Offset(x, BASE))
                }
                drawAlarm(Discs[0], BASE)
                drawBed(Discs[1], BASE)
            }
        }

        // The tags sit in the header's own dp, scaled with it from the top left corner.
        Box(
            Modifier
                .wrapContentSize(Alignment.TopStart, unbounded = true)
                .size(DESIGN_WIDTH.dp, SCENE_HEIGHT.dp)
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0f, 0f)
                    scaleX = fit
                    scaleY = fit
                }
                .clearAndSetSemantics {},
        ) {
            for (index in TagSpots.indices) {
                Tag(index, early = steps[1].tags.getOrElse(index) { "" }, late = steps[2].tags.getOrElse(index) { "" }) {
                    tagPose(index, scene, clock.doubleValue, jumps[index], still)
                }
            }
        }

        Box(Modifier.fillMaxSize().padding(top = (SCENE_HEIGHT * fit).dp, bottom = (bottom + FOOTER_HEIGHT + FOOTER_GAP).dp)) {
            steps.take(3).forEachIndexed { index, step ->
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = MARGIN.dp)
                        .graphicsLayer {
                            val pose = textPose(index, scene, clock.doubleValue, still)
                            alpha = pose.opacity
                            translationY = pose.y.dp.toPx()
                        }
                        .then(if (index == scene.step) Modifier else Modifier.clearAndSetSemantics {}),
                    verticalArrangement = Arrangement.Center,
                ) {
                    BasicText(
                        step.headline,
                        style = HeadlineStyle.copy(color = inkColor),
                        maxLines = step.headline.count { it == '\n' } + 1,
                        autoSize = HeadlineFit,
                    )
                    if (step.body != null) {
                        Spacer(Modifier.height(BODY_GAP.dp))
                        BasicText(step.body, style = BodyStyle.copy(color = muteColor))
                    }
                }
            }
        }

        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = bottom.dp)
                .size(FOOTER_WIDTH.dp, FOOTER_HEIGHT.dp)
                .semantics(mergeDescendants = true) {
                    role = Role.Button
                    onClick {
                        touches++
                        advance(How.Tap)
                        true
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = flashAt(pressed, pressedUntil, clock.doubleValue) }
                    .background(Flash, CircleShape),
            )
            BasicText(footerLabel, style = FooterStyle.copy(color = accentColor), maxLines = 1)
        }
    }
}

/** One tag: a white box with a tail towards the wave and an emoji, its emoji of the second step and of the third. */
@Composable
private fun Tag(index: Int, early: String, late: String, pose: () -> TagPose) {
    val spot = TagSpots[index]
    val bodyTop = if (spot.down) 0f else TAIL
    val shadow = remember { SoftRoundRect(Shadow, sigma = 5f) }
    Box(
        Modifier
            .offset((spot.x - TAG_WIDTH / 2f).dp, (spot.y - TAG_HEIGHT / 2f - bodyTop).dp)
            .size(TAG_WIDTH.dp, (TAG_HEIGHT + TAIL).dp)
            .graphicsLayer {
                val now = pose()
                // Everything a tag does starts from the tip of its tail.
                transformOrigin = TransformOrigin(0.5f, if (spot.down) 1f else 0f)
                translationX = now.x.dp.toPx()
                translationY = now.y.dp.toPx()
                scaleX = now.scaleX
                scaleY = now.scaleY
                alpha = now.opacity
                // The shadow lies outside the tag's box, which a layer of its own would cut off.
                compositingStrategy = CompositingStrategy.ModulateAlpha
            },
    ) {
        Box(
            Modifier
                .offset(y = bodyTop.dp)
                .size(TAG_WIDTH.dp, TAG_HEIGHT.dp)
                .drawBehind { surface(0.dp) { _, _, w, h -> drawIntoCanvas { shadow.draw(it.nativeCanvas, 0f, 3f, w, h, TAG_RADIUS) } } }
                .background(Color.White, RoundedCornerShape(TAG_RADIUS.dp)),
        )
        Box(
            Modifier
                .offset(((TAG_WIDTH - TailSide) / 2f).dp, ((if (spot.down) TAG_HEIGHT else TAIL) - TailSide / 2f).dp)
                .size(TailSide.dp)
                .graphicsLayer { rotationZ = 45f }
                .background(Color.White, RoundedCornerShape(2.dp)),
        )
        Box(Modifier.offset(y = bodyTop.dp).size(TAG_WIDTH.dp, TAG_HEIGHT.dp), contentAlignment = Alignment.Center) {
            BasicText(
                early,
                style = EmojiStyle,
                modifier = Modifier.graphicsLayer {
                    val now = pose()
                    alpha = if (now.late) 0f else 1f
                    rotationZ = now.spin
                },
            )
            BasicText(
                late,
                style = EmojiStyle,
                modifier = Modifier.graphicsLayer {
                    val now = pose()
                    alpha = if (now.late) 1f else 0f
                    rotationZ = now.spin
                },
            )
        }
    }
}

/** An alarm clock about the point (x, y): two bells, a face with its hands, two feet. */
private fun DrawScope.drawAlarm(x: Float, y: Float) {
    drawCircle(Sun, 2.3f, Offset(x - 4.6f, y - 5.2f))
    drawCircle(Sun, 2.3f, Offset(x + 4.6f, y - 5.2f))
    drawLine(Sun, Offset(x - 3.6f, y + 5.6f), Offset(x - 4.9f, y + 7.2f), strokeWidth = 1.6f, cap = StrokeCap.Round)
    drawLine(Sun, Offset(x + 3.6f, y + 5.6f), Offset(x + 4.9f, y + 7.2f), strokeWidth = 1.6f, cap = StrokeCap.Round)
    drawCircle(Color.White, 7f, Offset(x, y + 0.6f))
    drawCircle(Sun, 5.9f, Offset(x, y + 0.6f))
    drawLine(Color.White, Offset(x, y + 0.6f), Offset(x, y - 2.8f), strokeWidth = 1.4f, cap = StrokeCap.Round)
    drawLine(Color.White, Offset(x, y + 0.6f), Offset(x + 2.4f, y + 0.6f), strokeWidth = 1.4f, cap = StrokeCap.Round)
}

/** A bed about the point (x, y): two pillows on a mattress with two legs. */
private fun DrawScope.drawBed(x: Float, y: Float) {
    fun block(dx: Float, dy: Float, width: Float, height: Float, radius: Float) =
        drawRoundRect(Night, Offset(x + dx, y + dy), Size(width, height), CornerRadius(radius))
    block(-6.6f, -5.6f, 5.8f, 3.6f, 1.3f)
    block(0.8f, -5.6f, 5.8f, 3.6f, 1.3f)
    block(-8f, -1.2f, 16f, 4.8f, 1.8f)
    block(-8f, 2f, 2.2f, 3.6f, 0.6f)
    block(5.8f, 2f, 2.2f, 3.6f, 0.6f)
}
