package com.ngocdevv.nativecook.blocks.lists.believecardcarousel

import android.graphics.BlurMaskFilter
import android.graphics.Paint as NativePaint
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.WindowCompat
import com.ngocdevv.nativecook.R
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

private object BelieveCardCarouselColors {
    val Background = Color(0xFFFCFCFC)
    val Ink = Color(0xFF111111)
    val Muted = Color(0xFF707070)
    val Meta = Color(0xFF555555)
    val Primary = Color(0xFF00CC46)
    val Secondary = Color(0xFFF7F7F7)
    val Legal = Color(0xFF7A7A7A)
    val SplashMark = Color(0xFFE2E2E2)
    val Card = Color.White
}

private object BelieveCardCarouselLayout {
    object Title {
        const val Top = 140f
        const val Height = 112f
        const val Size = 37.7f
        const val Line1 = 51.7f
        const val Line2 = 94f
    }

    object Card {
        const val Width = 214f
        const val Height = 385f
        const val Radius = 22f
        const val Inset = 5f
        const val ImageWidth = Width - 2 * Inset
        const val ImageHeight = 230f
        const val ImageRadius = 18f
        const val Icon = 125f
        const val GlowRadius = 96f
        const val GlowBlur = 26f
        const val IconRadius = 30f
        const val Padding = 17f
        const val BelieveTop = 252.6f
        const val NameTop = 276.3f
        const val DateTop = 323.7f
        const val ByTop = 350.7f
    }

    object Row {
        const val TopCta = 139.6f
        const val ShiftTitle = 185f
        const val TitleTravel = 0.5f
        val X = floatArrayOf(-34f, 180.4f, 389.3f)
        val Y = floatArrayOf(15.6f, 0f, 29.4f)
        val Scale = floatArrayOf(0.985f, 1f, 1f)
        val Rotation = floatArrayOf(2.5f, -3f, 1.7f)
        val IconShift = floatArrayOf(35f, 6f, 15f)
        val TextShift = floatArrayOf(45f, 0f, -10f)
        val TextY = floatArrayOf(-4f, 0f, 4f)

        object Picture {
            const val X = 2f
            const val Y = 6.2f
            const val Perspective = 800f
            const val Rotation = -2.6f
            const val RotationX = -19f
            const val RotationY = -11f
            const val SkewX = -5.5f
            const val Scale = 1.08f
        }
    }

    object Cta {
        const val Top = 652.6f
        const val Height = 56f
        const val Side = 25f
        const val Gap = 13.4f
        const val LegalTop = 803f
        const val LegalSize = 11.3f
        const val LegalLineHeight = 13.3f
    }

    object Splash {
        const val Width = 90f
        const val Height = 105f
        const val CenterY = 435f
    }
}

private object BelieveCardCarouselMotionSpec {
    const val LoopMs = 4_630L
    const val ResumeMs = 3_500L
    const val HoldMs = 3_700f

    const val Intro = 1_600f
    const val MarkOut = 1_480f
    val Cards = floatArrayOf(1_870f, 2_035f, 2_146f)
    const val Morph = 2_420f
    const val Cta = 3_140f
    const val TitleOut = 3_050f
    const val Buttons = 3_110f

    const val MarkOutDuration = 150f
    const val TitleInDuration = 140f
    const val DropAt = 1_660f
    const val DropDuration = 100f
    const val Drop1 = 8.7f
    const val Drop2 = 18f

    const val RiseGrow = 0.12f
    const val RiseFrom = 260f
    const val RiseOmega = 17.3f
    const val RiseZeta = 0.81f
    const val RiseFadeDuration = 220f

    const val MorphDuration = 220f
    const val MorphBlur = 6f

    val LiftStagger = floatArrayOf(-37f, 0f, 39f)
    const val LiftOmega = 20f
    const val LiftZeta = 0.7f
    const val TitleFadeDuration = 140f
    const val ButtonsFrom = 34f
    const val ButtonsFadeDuration = 60f

    const val Pitch = 225f
    const val SnapDurationMs = 400L
    const val SnapDampingRatio = 0.8f
    const val VelocityProjection = 0.15f
    const val RubberBand = 0.3f
    const val PressScale = 0.97f
    const val PressDurationMs = 120
    const val ReducedFadeDurationMs = 200
}

private enum class BelieveCardCarouselArtwork {
    Mark,
    Figo,
    Loop,
}

private data class BelieveCardCarouselCardData(
    val id: String,
    val name: String,
    val date: String,
    val maker: String,
    val avatar: Int?,
    val artwork: BelieveCardCarouselArtwork,
    val image: List<Color>,
)

private val BelieveCardCarouselCards = listOf(
    BelieveCardCarouselCardData(
        id = "loop",
        name = "Halo",
        date = "12 Apr 25",
        maker = "Ira Koshal",
        avatar = null,
        artwork = BelieveCardCarouselArtwork.Loop,
        image = listOf(Color(0xFFB0CAD0), Color(0xFFB4CFD4), Color(0xFFADC7CB), Color(0xFFC6E2E8)),
    ),
    BelieveCardCarouselCardData(
        id = "beehive",
        name = "Beehive",
        date = "16 Oct 25",
        maker = "Beehive",
        avatar = null,
        artwork = BelieveCardCarouselArtwork.Mark,
        image = listOf(Color(0xFF83F2B2), Color(0xFFA6EEBF), Color(0xFF74E69B), Color(0xFFE4FBEB)),
    ),
    BelieveCardCarouselCardData(
        id = "figo",
        name = "aro",
        date = "29 May 25",
        maker = "Anita Dhaliya",
        avatar = R.drawable.believecardcarousel_avatar_anika,
        artwork = BelieveCardCarouselArtwork.Figo,
        image = listOf(Color(0xFF79B3C8), Color(0xFF78BBD1), Color(0xFF7EB3C9), Color(0xFFCFCBAF)),
    ),
)

private val BelieveCardCarouselRegular = FontFamily(
    Font(R.font.believecardcarousel_inter_regular, FontWeight.Normal),
)
private val BelieveCardCarouselMedium = FontFamily(
    Font(R.font.believecardcarousel_inter_medium, FontWeight.Medium),
)
private val BelieveCardCarouselSemibold = FontFamily(
    Font(R.font.believecardcarousel_inter_semibold, FontWeight.SemiBold),
)
private val BelieveCardCarouselExtraBold = FontFamily(
    Font(R.font.believecardcarousel_inter_extrabold, FontWeight.ExtraBold),
)

private data class BelieveCardCarouselBeat(
    val mark: Float,
    val titleIn: Float,
    val line1Drop: Float,
    val line2Drop: Float,
    val morph: Float,
    val lift: Float,
    val titleOut: Float,
    val buttonsIn: Float,
    val buttonsY: Float,
)

private data class BelieveCardCarouselCardBeat(
    val rise: Float,
    val scale: Float,
    val shown: Float,
    val lift: Float,
)

private object BelieveCardCarouselTimeline {
    fun beat(time: Float): BelieveCardCarouselBeat = BelieveCardCarouselBeat(
        mark = 1 - ramp(time, BelieveCardCarouselMotionSpec.MarkOut, BelieveCardCarouselMotionSpec.MarkOutDuration),
        titleIn = ramp(time, BelieveCardCarouselMotionSpec.Intro, BelieveCardCarouselMotionSpec.TitleInDuration),
        line1Drop = -BelieveCardCarouselMotionSpec.Drop1 * (
            1 - easeOut((time - BelieveCardCarouselMotionSpec.DropAt) / BelieveCardCarouselMotionSpec.DropDuration)
        ),
        line2Drop = -BelieveCardCarouselMotionSpec.Drop2 * (
            1 - easeOut((time - BelieveCardCarouselMotionSpec.DropAt) / BelieveCardCarouselMotionSpec.DropDuration)
        ),
        morph = ramp(time, BelieveCardCarouselMotionSpec.Morph, BelieveCardCarouselMotionSpec.MorphDuration),
        lift = 1 - spring(
            time,
            BelieveCardCarouselMotionSpec.Cta,
            BelieveCardCarouselMotionSpec.LiftOmega,
            BelieveCardCarouselMotionSpec.LiftZeta,
        ),
        titleOut = 1 - ramp(
            time,
            BelieveCardCarouselMotionSpec.TitleOut,
            BelieveCardCarouselMotionSpec.TitleFadeDuration,
        ),
        buttonsIn = ramp(
            time,
            BelieveCardCarouselMotionSpec.Buttons,
            BelieveCardCarouselMotionSpec.ButtonsFadeDuration,
        ),
        buttonsY = BelieveCardCarouselMotionSpec.ButtonsFrom * spring(
            time,
            BelieveCardCarouselMotionSpec.Buttons,
            BelieveCardCarouselMotionSpec.LiftOmega,
            BelieveCardCarouselMotionSpec.LiftZeta,
        ),
    )

    fun card(time: Float, index: Int): BelieveCardCarouselCardBeat {
        val cue = BelieveCardCarouselMotionSpec.Cards[index]
        val rest = spring(time, cue, BelieveCardCarouselMotionSpec.RiseOmega, BelieveCardCarouselMotionSpec.RiseZeta)
        return BelieveCardCarouselCardBeat(
            rise = BelieveCardCarouselMotionSpec.RiseFrom * rest,
            scale = 1 - BelieveCardCarouselMotionSpec.RiseGrow * rest,
            shown = clamp01((time - cue) / BelieveCardCarouselMotionSpec.RiseFadeDuration).pow(1.5f),
            lift = 1 - spring(
                time,
                BelieveCardCarouselMotionSpec.Cta + BelieveCardCarouselMotionSpec.LiftStagger[index],
                BelieveCardCarouselMotionSpec.LiftOmega,
                BelieveCardCarouselMotionSpec.LiftZeta,
            ),
        )
    }

    fun interpolate(value: Float, input: FloatArray, output: FloatArray, clamp: Boolean): Float {
        val sample = if (clamp) value.coerceIn(input[0], input[2]) else value
        val lower = if (sample <= input[1]) 0 else 1
        val progress = (sample - input[lower]) / (input[lower + 1] - input[lower])
        return output[lower] + (output[lower + 1] - output[lower]) * progress
    }

    fun snapProgress(
        elapsedSeconds: Double,
        durationSeconds: Double,
        dampingRatio: Double,
        velocity: Double,
        distance: Double,
    ): Double {
        if (elapsedSeconds <= 0) return 0.0
        if (elapsedSeconds >= durationSeconds) return 1.0
        val zeta = min(max(dampingRatio, 0.01), 0.999)
        val omega = 4 / (zeta * durationSeconds)
        val damped = omega * sqrt(1 - zeta * zeta)
        val normalizedVelocity = if (abs(distance) > 0.0001) velocity / distance else 0.0
        fun raw(time: Double): Double {
            val decay = exp(-zeta * omega * time)
            val coefficient = (zeta * omega - normalizedVelocity) / damped
            return 1 - decay * (cos(damped * time) + coefficient * sin(damped * time))
        }
        val end = raw(durationSeconds)
        return if (abs(end) > 0.0001) raw(elapsedSeconds) / end else elapsedSeconds / durationSeconds
    }

    private fun clamp01(value: Float) = value.coerceIn(0f, 1f)

    private fun ease(value: Float): Float {
        val x = clamp01(value)
        return if (x < 0.5f) 2 * x * x else 1 - (-2 * x + 2).pow(2) / 2
    }

    private fun ramp(time: Float, cue: Float, duration: Float) = ease((time - cue) / duration)

    private fun easeOut(value: Float): Float {
        val x = clamp01(value)
        return 1 - (1 - x).pow(3)
    }

    private fun spring(time: Float, cue: Float, omega: Float, zeta: Float): Float {
        val elapsed = (time - cue) / 1_000
        if (elapsed <= 0) return 1f
        val damped = omega * sqrt(1 - zeta * zeta)
        return exp(-zeta * omega * elapsed) * (
            cos(damped * elapsed) + (zeta * omega / damped) * sin(damped * elapsed)
        )
    }
}

@Stable
private class BelieveCardCarouselMotion(autoPlay: Boolean) {
    var nowNanos by mutableLongStateOf(0L)
    var epochNanos = 0L
    var running by mutableStateOf(autoPlay)
    var holding = false
    var heldClock = if (autoPlay) 0f else BelieveCardCarouselMotionSpec.HoldMs
    var touchDownGeneration by mutableIntStateOf(0)
    var touchEndGeneration by mutableIntStateOf(0)
    var resumePending = false

    var scroll by mutableFloatStateOf(0f)
    var dragStart = 0f
    var scrollFrom = 0f
    var scrollTarget = 0f
    var scrollVelocity = 0f
    var scrollStartedAt = 0L
    var scrollAnimating by mutableStateOf(false)
    var scrollGeneration by mutableIntStateOf(0)

    fun applyPlaybackFlags(autoPlay: Boolean, reducedMotion: Boolean) {
        touchDownGeneration += 1
        touchEndGeneration += 1
        resumePending = false
        holding = false
        if (scrollAnimating) {
            scroll = scrollTarget
            scrollAnimating = false
            scrollGeneration += 1
        }
        if (reducedMotion || !autoPlay) {
            heldClock = BelieveCardCarouselMotionSpec.HoldMs
            running = false
            return
        }
        scroll = 0f
        scrollFrom = 0f
        scrollTarget = 0f
        heldClock = 0f
        epochNanos = 0L
        running = true
    }

    fun clock(reducedMotion: Boolean): Float {
        if (!running) return heldClock
        if (epochNanos == 0L) return 0f
        var time = ((nowNanos - epochNanos) / 1_000_000f) % BelieveCardCarouselMotionSpec.LoopMs.toFloat()
        if (time < 0) time += BelieveCardCarouselMotionSpec.LoopMs.toFloat()
        if (holding && time >= BelieveCardCarouselMotionSpec.HoldMs) return BelieveCardCarouselMotionSpec.HoldMs
        if (
            reducedMotion &&
            time > BelieveCardCarouselMotionSpec.HoldMs - 600 &&
            time < BelieveCardCarouselMotionSpec.HoldMs
        ) {
            return BelieveCardCarouselMotionSpec.HoldMs
        }
        return time
    }

    fun beginTouch(reducedMotion: Boolean) {
        resumePending = false
        touchEndGeneration += 1
        if (reducedMotion) {
            holding = false
            return
        }
        if (holding) return
        holding = true
        touchDownGeneration += 1
        if (clock(reducedMotion) >= BelieveCardCarouselMotionSpec.HoldMs) freezeAtHold()
    }

    fun endTouch(autoPlay: Boolean, reducedMotion: Boolean) {
        if (autoPlay && !reducedMotion) {
            resumePending = true
            touchEndGeneration += 1
        }
    }

    fun freezeAtHold() {
        heldClock = BelieveCardCarouselMotionSpec.HoldMs
        running = false
    }

    fun restart() {
        resumePending = false
        holding = false
        scroll = 0f
        scrollFrom = 0f
        scrollTarget = 0f
        scrollAnimating = false
        heldClock = 0f
        epochNanos = 0L
        running = true
    }

    fun scrollValue(): Float {
        if (!scrollAnimating) return scroll
        val elapsed = (nowNanos - scrollStartedAt) / 1e9
        val distance = (scrollTarget - scrollFrom).toDouble()
        val progress = BelieveCardCarouselTimeline.snapProgress(
            elapsedSeconds = elapsed,
            durationSeconds = BelieveCardCarouselMotionSpec.SnapDurationMs / 1_000.0,
            dampingRatio = BelieveCardCarouselMotionSpec.SnapDampingRatio.toDouble(),
            velocity = scrollVelocity.toDouble(),
            distance = distance,
        )
        return (scrollFrom + distance * progress).toFloat()
    }

    fun beginPan() {
        scroll = scrollValue()
        scrollAnimating = false
        dragStart = scroll
    }

    fun updatePan(translationDp: Float) {
        var value = dragStart - translationDp / BelieveCardCarouselMotionSpec.Pitch
        if (value < -1) value = -1 + (value + 1) * BelieveCardCarouselMotionSpec.RubberBand
        if (value > 1) value = 1 + (value - 1) * BelieveCardCarouselMotionSpec.RubberBand
        scroll = value
    }

    fun endPan(velocityDp: Float, reducedMotion: Boolean) {
        val normalizedVelocity = -velocityDp / BelieveCardCarouselMotionSpec.Pitch
        val projected = scroll + normalizedVelocity * BelieveCardCarouselMotionSpec.VelocityProjection
        scrollFrom = scroll
        scrollTarget = round(projected).coerceIn(-1f, 1f)
        scrollVelocity = normalizedVelocity
        if (reducedMotion) {
            scroll = scrollTarget
            scrollFrom = scrollTarget
            scrollAnimating = false
            scrollGeneration += 1
            return
        }
        scrollStartedAt = nowNanos
        scrollAnimating = true
        scrollGeneration += 1
    }
}

const val LOOP_MS = 4_630L

@Composable
fun BelieveCardCarousel(
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false,
    onGetStarted: (() -> Unit)? = null,
    onSignIn: (() -> Unit)? = null,
) {
    val reducedMotion = rememberReducedMotion()
    val motion = remember { BelieveCardCarouselMotion(autoPlay = false) }
    val density = LocalDensity.current.density
    val activity = LocalActivity.current
    val view = LocalView.current

    DisposableEffect(activity, view) {
        val controller = activity?.window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = true
        onDispose {
            if (previous != null) controller?.isAppearanceLightStatusBars = previous
        }
    }

    LaunchedEffect(autoPlay, reducedMotion) {
        motion.applyPlaybackFlags(autoPlay, reducedMotion)
    }

    LaunchedEffect(motion.running, motion.scrollAnimating, reducedMotion) {
        while (!reducedMotion && (motion.running || motion.scrollAnimating)) {
            withFrameNanos {
                if (motion.epochNanos == 0L && motion.running) motion.epochNanos = it
                motion.nowNanos = it
            }
        }
    }

    LaunchedEffect(motion.touchDownGeneration) {
        if (reducedMotion || !motion.holding || !motion.running) return@LaunchedEffect
        val remaining = (BelieveCardCarouselMotionSpec.HoldMs - motion.clock(reducedMotion)).coerceAtLeast(0f)
        delay(remaining.toLong())
        if (motion.holding) motion.freezeAtHold()
    }

    LaunchedEffect(motion.touchEndGeneration) {
        if (!autoPlay || reducedMotion || !motion.resumePending) return@LaunchedEffect
        delay(BelieveCardCarouselMotionSpec.ResumeMs)
        if (!autoPlay || reducedMotion || !motion.resumePending) return@LaunchedEffect
        motion.restart()
    }

    LaunchedEffect(motion.scrollGeneration) {
        if (!motion.scrollAnimating) return@LaunchedEffect
        delay(BelieveCardCarouselMotionSpec.SnapDurationMs)
        motion.scroll = motion.scrollTarget
        motion.scrollAnimating = false
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(BelieveCardCarouselColors.Background)
            .pointerInput(motion, density, reducedMotion, autoPlay) {
                val rowTop = BelieveCardCarouselLayout.Row.TopCta * density
                val rowBottom = rowTop + (BelieveCardCarouselLayout.Card.Height + 40) * density
                val horizontalThreshold = 8 * density
                val verticalFailure = 16 * density
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    motion.beginTouch(reducedMotion)
                    val canPan = down.position.y in rowTop..rowBottom
                    var panning = false
                    var failed = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        tracker.addPosition(change.uptimeMillis, change.position)
                        val dx = change.position.x - down.position.x
                        val dy = change.position.y - down.position.y
                        if (!panning && abs(dy) > verticalFailure && abs(dx) < horizontalThreshold) failed = true
                        if (!panning && canPan && !failed && abs(dx) > horizontalThreshold) {
                            panning = true
                            motion.beginPan()
                        }
                        if (panning) {
                            change.consume()
                            motion.updatePan(dx / density)
                        }
                        if (!change.pressed) break
                    }
                    if (panning) motion.endPan(tracker.calculateVelocity().x / density, reducedMotion)
                    motion.endTouch(autoPlay, reducedMotion)
                }
            },
    ) {
        val width = maxWidth.value
        val height = maxHeight.value
        BelieveCardCarouselSplash(motion = motion, reducedMotion = reducedMotion, width = width)
        BelieveCardCarouselHeadline(motion = motion, reducedMotion = reducedMotion, width = width)

        Box(
            Modifier
                .fillMaxWidth()
                .height((BelieveCardCarouselLayout.Card.Height + 40).dp)
                .offset(y = BelieveCardCarouselLayout.Row.TopCta.dp)
                .clearAndSetSemantics {
                    contentDescription = "Projects you can believe in: Halo, Beehive, aro"
                },
        ) {
            // The centre card is drawn last, matching the reference row's resting z-order.
            for (index in intArrayOf(0, 2, 1)) {
                BelieveCardCarouselCardSlot(
                    data = BelieveCardCarouselCards[index],
                    index = index,
                    motion = motion,
                    reducedMotion = reducedMotion,
                )
            }
        }

        BelieveCardCarouselButtons(
            motion = motion,
            reducedMotion = reducedMotion,
            width = width,
            onGetStarted = onGetStarted,
            onSignIn = onSignIn,
        )

        if (autoPlay && !reducedMotion && motion.running) {
            Box(
                Modifier
                    .size(1.dp)
                    .offset(x = (width - 1).dp, y = (height - 1).dp)
                    .graphicsLayer {
                        val time = motion.clock(reducedMotion)
                        alpha = if ((time / 50).toInt() % 2 == 0) 0f else 1f
                    }
                    .background(Color(0xFFFBFBFB)),
            )
        }
    }
}

@Composable
private fun BelieveCardCarouselSplash(
    motion: BelieveCardCarouselMotion,
    reducedMotion: Boolean,
    width: Float,
) {
    BelieveCardCarouselBrandMark(
        color = BelieveCardCarouselColors.SplashMark,
        modifier = Modifier
            .size(
                BelieveCardCarouselLayout.Splash.Width.dp,
                (BelieveCardCarouselLayout.Splash.Width * 1.17f).dp,
            )
            .offset(
                x = (width / 2 - BelieveCardCarouselLayout.Splash.Width / 2).dp,
                y = (BelieveCardCarouselLayout.Splash.CenterY - BelieveCardCarouselLayout.Splash.Height / 2).dp,
            )
            .graphicsLayer { alpha = BelieveCardCarouselTimeline.beat(motion.clock(reducedMotion)).mark },
    )
}

@Composable
private fun BelieveCardCarouselHeadline(
    motion: BelieveCardCarouselMotion,
    reducedMotion: Boolean,
    width: Float,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val displayTypeface = remember(context) {
        ResourcesCompat.getFont(context, R.font.believecardcarousel_interdisplay_bold)
    }
    Canvas(
        Modifier
            .width(width.dp)
            .height(BelieveCardCarouselLayout.Title.Height.dp)
            .offset(y = BelieveCardCarouselLayout.Title.Top.dp)
            .graphicsLayer {
                val beat = BelieveCardCarouselTimeline.beat(motion.clock(reducedMotion))
                translationY = -BelieveCardCarouselLayout.Row.ShiftTitle *
                    BelieveCardCarouselLayout.Row.TitleTravel * beat.lift * density
            }
            .semantics {
                heading()
                contentDescription = "Believe in Something"
            },
    ) {
        val beat = BelieveCardCarouselTimeline.beat(motion.clock(reducedMotion))
        val opacity = (beat.titleIn * beat.titleOut).coerceIn(0f, 1f)
        val blur = BelieveCardCarouselMotionSpec.MorphBlur * sin(PI.toFloat() * beat.morph)
        drawIntoCanvas { canvas ->
            val paint = NativePaint(NativePaint.ANTI_ALIAS_FLAG).apply {
                typeface = displayTypeface
                textSize = BelieveCardCarouselLayout.Title.Size * density
                color = android.graphics.Color.rgb(0x11, 0x11, 0x11)
            }
            fun drawWord(text: String, baselineDp: Float, alpha: Float, useBlur: Boolean) {
                paint.alpha = (255 * opacity * alpha).toInt().coerceIn(0, 255)
                paint.maskFilter = if (useBlur && blur > 0.01f) {
                    BlurMaskFilter(
                        max(0.01f, (blur - 0.5f) / 0.57735f) * density,
                        BlurMaskFilter.Blur.NORMAL,
                    )
                } else {
                    null
                }
                val x = (size.width - paint.measureText(text)) / 2
                canvas.nativeCanvas.drawText(text, x, baselineDp * density, paint)
            }
            drawWord("Believe in", BelieveCardCarouselLayout.Title.Line1 + beat.line1Drop, 1f, false)
            drawWord("Someone", BelieveCardCarouselLayout.Title.Line2 + beat.line2Drop, 1 - beat.morph, true)
            drawWord("Something", BelieveCardCarouselLayout.Title.Line2 + beat.line2Drop, beat.morph, true)
        }
    }
}

@Composable
private fun BelieveCardCarouselCardSlot(
    data: BelieveCardCarouselCardData,
    index: Int,
    motion: BelieveCardCarouselMotion,
    reducedMotion: Boolean,
) {
    BelieveCardCarouselCard(
        data = data,
        textShift = BelieveCardCarouselLayout.Row.TextShift[index],
        iconShift = BelieveCardCarouselLayout.Row.IconShift[index],
        textY = BelieveCardCarouselLayout.Row.TextY[index],
        inPerspective = index == 1,
        modifier = Modifier
            .offset(
                x = (-BelieveCardCarouselLayout.Card.Width / 2).dp,
                y = 0.dp,
            )
            .zIndex(
                10f - 4f * abs(
                    index - 1 - if (motion.scrollAnimating) motion.scrollTarget else motion.scroll
                )
            )
            .graphicsLayer {
                val time = motion.clock(reducedMotion)
                val scroll = motion.scrollValue()
                val position = index - 1 - scroll
                val beat = BelieveCardCarouselTimeline.card(time, index)
                val x = BelieveCardCarouselTimeline.interpolate(
                    position,
                    floatArrayOf(-1f, 0f, 1f),
                    BelieveCardCarouselLayout.Row.X,
                    false,
                )
                val y = BelieveCardCarouselTimeline.interpolate(
                    position,
                    floatArrayOf(-1f, 0f, 1f),
                    BelieveCardCarouselLayout.Row.Y,
                    true,
                )
                val slotScale = BelieveCardCarouselTimeline.interpolate(
                    position,
                    floatArrayOf(-1f, 0f, 1f),
                    BelieveCardCarouselLayout.Row.Scale,
                    true,
                )
                translationX = x * density
                translationY = (
                    y + beat.rise + BelieveCardCarouselLayout.Row.ShiftTitle * (1 - beat.lift)
                ) * density
                rotationZ = BelieveCardCarouselTimeline.interpolate(
                    position,
                    floatArrayOf(-1f, 0f, 1f),
                    BelieveCardCarouselLayout.Row.Rotation,
                    true,
                )
                scaleX = slotScale * beat.scale
                scaleY = scaleX
                alpha = beat.shown
            },
    )
}

@Composable
private fun BelieveCardCarouselCard(
    data: BelieveCardCarouselCardData,
    textShift: Float,
    iconShift: Float,
    textY: Float,
    inPerspective: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .size(BelieveCardCarouselLayout.Card.Width.dp, BelieveCardCarouselLayout.Card.Height.dp)
            .believeCardShadow()
            .background(
                BelieveCardCarouselColors.Card,
                RoundedCornerShape(BelieveCardCarouselLayout.Card.Radius.dp),
            )
            .semantics {
                contentDescription = "${data.name}, ${data.date}, by ${data.maker}"
            },
    ) {
        BelieveCardCarouselPicture(
            data = data,
            iconShift = iconShift,
            inPerspective = inPerspective,
            modifier = Modifier.offset(
                BelieveCardCarouselLayout.Card.Inset.dp,
                BelieveCardCarouselLayout.Card.Inset.dp,
            ),
        )

        BasicText(
            text = "I believe in",
            modifier = Modifier.offset(
                (BelieveCardCarouselLayout.Card.Padding + textShift).dp,
                (BelieveCardCarouselLayout.Card.BelieveTop + textY).dp,
            ),
            style = TextStyle(
                color = BelieveCardCarouselColors.Muted,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = BelieveCardCarouselSemibold,
            ),
        )

        BasicText(
            text = data.name,
            modifier = Modifier
                .width((BelieveCardCarouselLayout.Card.Width - BelieveCardCarouselLayout.Card.Padding - 12).dp)
                .offset(
                    (BelieveCardCarouselLayout.Card.Padding + textShift).dp,
                    (BelieveCardCarouselLayout.Card.NameTop + textY).dp,
                ),
            maxLines = 1,
            overflow = TextOverflow.Clip,
            style = TextStyle(
                color = BelieveCardCarouselColors.Ink,
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = BelieveCardCarouselExtraBold,
                letterSpacing = (-0.5).sp,
            ),
        )

        Row(
            Modifier.offset(
                (BelieveCardCarouselLayout.Card.Padding + textShift).dp,
                (BelieveCardCarouselLayout.Card.DateTop + textY).dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                text = "→",
                modifier = Modifier.width(16.dp),
                style = TextStyle(
                    color = BelieveCardCarouselColors.Ink,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = BelieveCardCarouselSemibold,
                    textAlign = TextAlign.Center,
                ),
            )
            Box(Modifier.width(8.dp))
            BasicText(
                text = data.date,
                style = TextStyle(
                    color = BelieveCardCarouselColors.Meta,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = BelieveCardCarouselMedium,
                    fontFeatureSettings = "tnum",
                ),
            )
        }

        Row(
            Modifier.offset(
                (BelieveCardCarouselLayout.Card.Padding + textShift).dp,
                (BelieveCardCarouselLayout.Card.ByTop + textY).dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BelieveCardCarouselMakerIcon(data)
            Box(Modifier.width(8.dp))
            BasicText(
                text = data.maker,
                style = TextStyle(
                    color = BelieveCardCarouselColors.Meta,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = BelieveCardCarouselMedium,
                ),
            )
        }
    }
}

@Composable
private fun BelieveCardCarouselPicture(
    data: BelieveCardCarouselCardData,
    iconShift: Float,
    inPerspective: Boolean,
    modifier: Modifier = Modifier,
) {
    val perspective = if (inPerspective) {
        Modifier
            .believeCardSkewX(BelieveCardCarouselLayout.Row.Picture.SkewX)
            .graphicsLayer {
                translationX = BelieveCardCarouselLayout.Row.Picture.X * density
                translationY = BelieveCardCarouselLayout.Row.Picture.Y * density
                scaleX = BelieveCardCarouselLayout.Row.Picture.Scale
                scaleY = BelieveCardCarouselLayout.Row.Picture.Scale
                rotationZ = BelieveCardCarouselLayout.Row.Picture.Rotation
                rotationX = BelieveCardCarouselLayout.Row.Picture.RotationX
                rotationY = BelieveCardCarouselLayout.Row.Picture.RotationY
                cameraDistance = BelieveCardCarouselLayout.Row.Picture.Perspective * density / 72f
            }
    } else {
        Modifier
    }

    Box(
        modifier
            .size(BelieveCardCarouselLayout.Card.ImageWidth.dp, BelieveCardCarouselLayout.Card.ImageHeight.dp)
            .then(perspective),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawRoundRect(
                brush = Brush.verticalGradient(
                    0f to data.image[0],
                    0.55f to data.image[1],
                    1f to data.image[2],
                ),
                cornerRadius = CornerRadius(
                    BelieveCardCarouselLayout.Card.ImageRadius * density,
                    BelieveCardCarouselLayout.Card.ImageRadius * density,
                ),
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                drawIntoCanvas { canvas ->
                    val paint = NativePaint(NativePaint.ANTI_ALIAS_FLAG).apply {
                        color = data.image[3].toArgb()
                        maskFilter = BlurMaskFilter(
                            ((BelieveCardCarouselLayout.Card.GlowBlur - 0.5f) / 0.57735f) * density,
                            BlurMaskFilter.Blur.NORMAL,
                        )
                    }
                    canvas.nativeCanvas.drawCircle(
                        size.width / 2 + iconShift * density,
                        size.height / 2,
                        BelieveCardCarouselLayout.Card.GlowRadius * density,
                        paint,
                    )
                }
            } else {
                repeat(4) { layer ->
                    drawCircle(
                        color = data.image[3].copy(alpha = 0.12f),
                        radius = (BelieveCardCarouselLayout.Card.GlowRadius + layer * 12) * density,
                        center = Offset(size.width / 2 + iconShift * density, size.height / 2),
                    )
                }
            }
        }
        BelieveCardCarouselIcon(
            data = data,
            modifier = Modifier.offset(x = iconShift.dp),
        )
    }
}

@Composable
private fun BelieveCardCarouselIcon(
    data: BelieveCardCarouselCardData,
    modifier: Modifier = Modifier,
) {
    when (data.artwork) {
        BelieveCardCarouselArtwork.Mark -> {
            Box(
                modifier
                    .size(BelieveCardCarouselLayout.Card.Icon.dp)
                    .believeIconShadow()
                    .clip(RoundedCornerShape(BelieveCardCarouselLayout.Card.IconRadius.dp))
                    .background(
                        Brush.verticalGradient(listOf(Color(0xFF00EA76), Color(0xFF01D454))),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    BelieveCardCarouselBrandMark(
                        color = Color(0xFF0A7A3C).copy(alpha = 0.35f),
                        modifier = Modifier
                            .size(62.dp, (62 * 1.17f).dp)
                            .offset(y = 2.dp)
                            .then(
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    Modifier.blur(4.33.dp, BlurredEdgeTreatment.Unbounded)
                                } else {
                                    Modifier
                                }
                            ),
                    )
                    BelieveCardCarouselBrandMark(
                        color = Color.White.copy(alpha = 0.86f),
                        modifier = Modifier.size(62.dp, (62 * 1.17f).dp),
                    )
                }
            }
        }

        BelieveCardCarouselArtwork.Figo -> {
            Box(
                modifier
                    .size(BelieveCardCarouselLayout.Card.Icon.dp)
                    .believeIconShadow()
                    .clip(RoundedCornerShape(BelieveCardCarouselLayout.Card.IconRadius.dp)),
            ) {
                BelieveCardCarouselPixelBowl(Modifier.fillMaxSize())
                BasicText(
                    text = "a r o",
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .offset(y = (-12).dp),
                    style = TextStyle(
                        color = Color.White,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = BelieveCardCarouselExtraBold,
                        letterSpacing = 1.sp,
                    ),
                )
            }
        }

        BelieveCardCarouselArtwork.Loop -> {
            BelieveCardCarouselLoopIcon(
                modifier
                    .size(BelieveCardCarouselLayout.Card.Icon.dp)
                    .clip(RoundedCornerShape(BelieveCardCarouselLayout.Card.IconRadius.dp))
                    .background(Color(0xFFB2F6FD)),
            )
        }
    }
}

@Composable
private fun BelieveCardCarouselBrandMark(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val scaleX = size.width / 90
        val scaleY = size.height / 105
        val outer = Path().apply {
            moveTo(45 * scaleX, 14 * scaleY)
            lineTo(74 * scaleX, 31 * scaleY)
            lineTo(74 * scaleX, 69 * scaleY)
            lineTo(45 * scaleX, 86 * scaleY)
            lineTo(16 * scaleX, 69 * scaleY)
            lineTo(16 * scaleX, 31 * scaleY)
            close()
        }
        drawPath(
            path = outer,
            color = color,
            style = Stroke(
                width = 13 * scaleX,
                join = StrokeJoin.Round,
            ),
        )
        val inner = Path().apply {
            moveTo(45 * scaleX, 40 * scaleY)
            lineTo(58 * scaleX, 47.5f * scaleY)
            lineTo(58 * scaleX, 62.5f * scaleY)
            lineTo(45 * scaleX, 70 * scaleY)
            lineTo(32 * scaleX, 62.5f * scaleY)
            lineTo(32 * scaleX, 47.5f * scaleY)
            close()
        }
        drawPath(inner, color)
    }
}

@Composable
private fun BelieveCardCarouselLoopIcon(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val svgSize = size.width * 100 / BelieveCardCarouselLayout.Card.Icon
        val scale = svgSize / 86
        val origin = Offset((size.width - svgSize) / 2, (size.height - svgSize) / 2)
        drawCircle(
            color = Color.Black,
            radius = 15 * scale,
            center = origin + Offset(28 * scale, 30 * scale),
            style = Stroke(width = 13 * scale),
        )
        drawCircle(
            color = Color.Black,
            radius = 15 * scale,
            center = origin + Offset(56 * scale, 58 * scale),
            style = Stroke(width = 13 * scale),
        )
        drawLine(
            color = Color.Black,
            start = origin + Offset(66 * scale, 12 * scale),
            end = origin + Offset(18 * scale, 76 * scale),
            strokeWidth = 13 * scale,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun BelieveCardCarouselPixelBowl(modifier: Modifier = Modifier) {
    val rows = remember {
        listOf(
            "....yyyll.....",
            "..yyllgglly...",
            ".ygglwlggyyl..",
            "bllggyygglgyyb",
            "bddbbbbbbbbddb",
            ".bddddddddddb.",
            "..bddddddddb..",
            "....bbbbbb....",
        )
    }
    val fills = remember {
        mapOf(
            'b' to Color(0xFF4A2C17),
            'd' to Color(0xFF7A4A26),
            'g' to Color(0xFF6DBE35),
            'l' to Color(0xFF9BD44A),
            'y' to Color(0xFFF2D23A),
            'w' to Color(0xFFF7F3C8),
        )
    }
    Canvas(modifier) {
        drawRect(Brush.verticalGradient(listOf(Color(0xFF2A8FC4), Color(0xFF145E8E))))
        val pixel = 6 * density
        val originX = (size.width - rows.first().length * pixel) / 2
        val originY = size.height * 0.24f
        for ((row, line) in rows.withIndex()) {
            for ((column, character) in line.withIndex()) {
                val fill = fills[character] ?: continue
                drawRect(
                    color = fill,
                    topLeft = Offset(originX + column * pixel, originY + row * pixel),
                    size = Size(pixel, pixel),
                )
            }
        }
    }
}

@Composable
private fun BelieveCardCarouselMakerIcon(data: BelieveCardCarouselCardData) {
    when {
        data.artwork == BelieveCardCarouselArtwork.Mark -> {
            Box(
                Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF00D45F)),
                contentAlignment = Alignment.Center,
            ) {
                BelieveCardCarouselBrandMark(
                    color = Color.White,
                    modifier = Modifier.size(9.dp, (9 * 1.17f).dp),
                )
            }
        }

        data.avatar != null -> {
            Image(
                painter = painterResource(data.avatar),
                contentDescription = null,
                modifier = Modifier.size(16.dp).clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        }

        else -> Box(Modifier.size(16.dp).clip(CircleShape).background(Color(0xFFE6C9B5)))
    }
}

@Composable
private fun BelieveCardCarouselButtons(
    motion: BelieveCardCarouselMotion,
    reducedMotion: Boolean,
    width: Float,
    onGetStarted: (() -> Unit)?,
    onSignIn: (() -> Unit)?,
) {
    Box(
        Modifier
            .width((width - 2 * BelieveCardCarouselLayout.Cta.Side).dp)
            .height(190.dp)
            .offset(
                x = BelieveCardCarouselLayout.Cta.Side.dp,
                y = BelieveCardCarouselLayout.Cta.Top.dp,
            )
            .graphicsLayer {
                val beat = BelieveCardCarouselTimeline.beat(motion.clock(reducedMotion))
                alpha = beat.buttonsIn
                translationY = beat.buttonsY * density
            },
    ) {
        BelieveCardCarouselButton(
            label = "Get Started",
            primary = true,
            onClick = onGetStarted,
            modifier = Modifier.fillMaxWidth(),
        )
        BelieveCardCarouselButton(
            label = "Already have an account",
            primary = false,
            onClick = onSignIn,
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = (BelieveCardCarouselLayout.Cta.Height + BelieveCardCarouselLayout.Cta.Gap).dp),
        )
        BasicText(
            text = "By continuing, you agree to accept our\nTerms of Use and Privacy Policy.",
            modifier = Modifier
                .fillMaxWidth()
                .offset(
                    y = (
                        2 * BelieveCardCarouselLayout.Cta.Height +
                            2 * BelieveCardCarouselLayout.Cta.Gap +
                            12
                    ).dp,
                ),
            style = TextStyle(
                color = BelieveCardCarouselColors.Legal,
                fontSize = BelieveCardCarouselLayout.Cta.LegalSize.sp,
                lineHeight = BelieveCardCarouselLayout.Cta.LegalLineHeight.sp,
                fontFamily = BelieveCardCarouselRegular,
                fontWeight = FontWeight.Normal,
                textAlign = TextAlign.Center,
            ),
        )
    }
}

@Composable
private fun BelieveCardCarouselButton(
    label: String,
    primary: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) BelieveCardCarouselMotionSpec.PressScale else 1f,
        animationSpec = tween(
            durationMillis = BelieveCardCarouselMotionSpec.PressDurationMs,
            easing = BelieveCardCarouselEaseInOut,
        ),
        label = "Believe card button press",
    )
    val view = LocalView.current
    LaunchedEffect(pressed, primary, onClick) {
        if (pressed && primary && onClick != null) view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    }
    Box(
        modifier
            .height(BelieveCardCarouselLayout.Cta.Height.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape((BelieveCardCarouselLayout.Cta.Height / 2).dp))
            .background(if (primary) BelieveCardCarouselColors.Primary else BelieveCardCarouselColors.Secondary)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = onClick != null,
                role = Role.Button,
                onClick = { onClick?.invoke() },
            )
            .semantics {
                role = Role.Button
                contentDescription = label
            },
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = label,
            style = TextStyle(
                color = if (primary) Color.White else BelieveCardCarouselColors.Ink,
                fontSize = 17.7.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = BelieveCardCarouselSemibold,
                letterSpacing = (-0.2).sp,
            ),
        )
    }
}

private val BelieveCardCarouselEaseInOut = Easing { value ->
    if (value < 0.5f) 2 * value * value else 1 - (-2 * value + 2).pow(2) / 2
}

private fun Modifier.believeCardSkewX(degrees: Float): Modifier = drawWithContent {
    val skew = tan(degrees * PI.toFloat() / 180)
    val matrix = Matrix().apply { values[Matrix.SkewX] = skew }
    withTransform({
        translate(size.width / 2, size.height / 2)
        transform(matrix)
        translate(-size.width / 2, -size.height / 2)
    }) {
        this@drawWithContent.drawContent()
    }
}

private fun Modifier.believeCardShadow(): Modifier = drawBehind {
    val left = 0f
    val top = 8 * density
    val right = size.width
    val bottom = size.height + 8 * density
    val radius = BelieveCardCarouselLayout.Card.Radius * density
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        drawIntoCanvas { canvas ->
            val paint = NativePaint(NativePaint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.argb((255 * 0.07f).toInt(), 0, 0, 0)
                maskFilter = BlurMaskFilter(18 * density, BlurMaskFilter.Blur.NORMAL)
            }
            canvas.nativeCanvas.drawRoundRect(left, top, right, bottom, radius, radius, paint)
        }
    } else {
        // API 26–27: three translucent layers approximate the same soft ambient shadow.
        drawRoundRect(
            Color.Black.copy(alpha = 0.025f),
            topLeft = Offset(-8 * density, 0f),
            size = Size(size.width + 16 * density, size.height + 24 * density),
            cornerRadius = CornerRadius((BelieveCardCarouselLayout.Card.Radius + 8) * density),
        )
        drawRoundRect(
            Color.Black.copy(alpha = 0.025f),
            topLeft = Offset(-4 * density, 4 * density),
            size = Size(size.width + 8 * density, size.height + 16 * density),
            cornerRadius = CornerRadius((BelieveCardCarouselLayout.Card.Radius + 4) * density),
        )
        drawRoundRect(
            Color.Black.copy(alpha = 0.02f),
            topLeft = Offset(0f, 8 * density),
            size = size,
            cornerRadius = CornerRadius(radius),
        )
    }
}

private fun Modifier.believeIconShadow(): Modifier = drawBehind {
    val radius = BelieveCardCarouselLayout.Card.IconRadius * density
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        drawIntoCanvas { canvas ->
            val paint = NativePaint(NativePaint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.argb((255 * 0.18f).toInt(), 0x0B, 0x5E, 0x33)
                maskFilter = BlurMaskFilter(10 * density, BlurMaskFilter.Blur.NORMAL)
            }
            canvas.nativeCanvas.drawRoundRect(
                0f,
                6 * density,
                size.width,
                size.height + 6 * density,
                radius,
                radius,
                paint,
            )
        }
    } else {
        repeat(3) { layer ->
            val spread = (3 - layer) * 3 * density
            drawRoundRect(
                color = Color(0xFF0B5E33).copy(alpha = 0.06f),
                topLeft = Offset(-spread, 6 * density - spread),
                size = Size(size.width + 2 * spread, size.height + 2 * spread),
                cornerRadius = CornerRadius(radius + spread),
            )
        }
    }
}
