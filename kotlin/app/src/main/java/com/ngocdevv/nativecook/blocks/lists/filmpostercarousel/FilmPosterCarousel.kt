package com.ngocdevv.nativecook.blocks.lists.filmpostercarousel

import android.view.HapticFeedbackConstants
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.ngocdevv.nativecook.R
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.awaitCancellation
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

private object FilmPosterCarouselLayout {
    const val PosterHeight = 550f
    const val FadeFrom = 0.74f
    const val PanelBottom = 775.5f
    const val PanelRadius = 34f
    const val PanelShadowRadius = 2.2f
    const val PanelShadowY = 2f
    const val FooterTop = PanelBottom - PanelRadius - 10f

    const val CloseRight = 32.3f
    const val CloseTop = 33.7f
    const val CloseSize = 42f
    const val CloseRadius = 8f
    const val CloseIcon = 15f

    const val TitleX = 44.1f
    const val TitleBaseline = 525.3f
    const val TitleSize = 50f
    const val TitleTracking = 0.2f
    const val MetaX = 43.6f
    const val MetaBaseline = 556.4f
    const val MetaSize = 14f
    const val MetaTracking = 0.5f
    const val DurationX = 43.4f
    const val DurationBaseline = 588.7f
    const val DurationSize = 18f
    const val DurationTracking = 1.2f

    const val StarsCenterX = 50.9f
    const val StarsCenterY = 616.9f
    const val StarSize = 16f
    const val StarPitch = 22f
    const val StarInnerRadius = 0.5f
    const val StarHalo = 0.55f

    const val TagsX = 42.7f
    const val TagsTop = 662.3f
    const val TagHeight = 31.5f
    const val TagPaddingX = 16.5f
    const val TagGap = 13f
    const val TagSize = 14.6f
    const val TagTracking = -0.3f

    const val ChevronCenterX = 201.4f
    const val ChevronTop = 747f
    const val ChevronWidth = 26f
    const val ChevronHeight = 12f
    const val ChevronStroke = 2.2f

    const val FooterBaseline = 831.7f
    const val FooterSize = 19.5f
    const val FooterTracking = 1.75f
    const val BaselineRatio = 0.97f
}

private object FilmPosterCarouselMotionSpec {
    const val LoopMs = LOOP_MS
    val Turns = listOf(
        FilmPosterCarouselTurn(0.8, 1.1, 2.05, 2.5, 0f, 1f),
        FilmPosterCarouselTurn(3.8, 4.1, 5.05, 5.5, 1f, 0f),
    )
    const val LiftScale = 0.1f
    const val LiftRadius = 34f
    const val LiftShadow = 0.55f
    const val LiftShadowY = 18f
    const val LiftShadowBlur = 36f
    const val PickMs = 450L
    const val ThrowX = 1.15f
    const val ThrowTilt = 7f
    const val ThrowDrop = 36f
    const val ThrowShrink = 0.04f
    const val ArtLag = 0.3f
    const val WaitingScale = 0.14f
    const val WaitingDim = 0.55f
    const val RoomBlur = 40f
    const val RoomDim = 0.55f
    const val SheenAlpha = 0.22f
    const val SheenWidth = 0.55f
    const val SheenSlant = 20f
    const val SheenTravel = 1.6f
    const val StarPeak = 0.14f
    const val StarStagger = 0.055
    const val StarEach = 0.22
    const val StarsTotal = StarEach + StarStagger * 4
    const val PageResponse = 0.62
    const val PageDamping = 0.86
    const val DownResponse = 0.5
    const val DownDamping = 0.78
    const val PickResponse = 0.35
    const val PickDamping = 1.0
    const val IdleResumeMs = 3_500L
    const val ReducedDurationMs = 200L
    const val BobMs = 1_750L
    const val ResumeDurationMs = 300L
    const val ResumeRestartDelayMs = 320L
    const val FlickThreshold = 0.11f
    const val VelocityProjection = 0.2f
    const val RubberBand = 0.3f
}

private data class FilmPosterCarouselTurn(
    val lift: Double,
    val page: Double,
    val down: Double,
    val stars: Double,
    val from: Float,
    val to: Float,
)

private data class FilmPosterCarouselPose(
    val page: Float,
    val lift: Float,
    val swell0: Float,
    val swell1: Float,
)

private fun filmPosterCarouselSegment(time: Double, start: Double, end: Double): Double =
    ((time - start) / (end - start)).coerceIn(0.0, 1.0)

private fun filmPosterCarouselSpringStep(time: Double, response: Double, damping: Double): Double {
    if (time <= 0) return 0.0
    if (time > response * 4) return 1.0
    val frequency = 2 * PI / response
    if (damping >= 1) return 1 - exp(-frequency * time) * (1 + frequency * time)
    val damped = frequency * sqrt(1 - damping * damping)
    return 1 - exp(-damping * frequency * time) * (
        cos(damped * time) + damping * frequency / damped * sin(damped * time)
        )
}

private fun filmPosterCarouselCubicBezier(
    progress: Double,
    x1: Double,
    y1: Double,
    x2: Double,
    y2: Double,
): Double {
    if (progress <= 0) return 0.0
    if (progress >= 1) return 1.0
    fun sample(t: Double, first: Double, second: Double): Double {
        val inverse = 1 - t
        return 3 * inverse * inverse * t * first +
            3 * inverse * t * t * second +
            t * t * t
    }
    var low = 0.0
    var high = 1.0
    repeat(12) {
        val middle = (low + high) / 2
        if (sample(middle, x1, x2) < progress) low = middle else high = middle
    }
    return sample((low + high) / 2, y1, y2)
}

private fun filmPosterCarouselPoseAt(time: Double): FilmPosterCarouselPose {
    var page = 0f
    var lift = 0f
    val swell = floatArrayOf(0f, 0f)
    FilmPosterCarouselMotionSpec.Turns.forEach { turn ->
        val pageStep = filmPosterCarouselSpringStep(
            time - turn.page,
            FilmPosterCarouselMotionSpec.PageResponse,
            FilmPosterCarouselMotionSpec.PageDamping,
        )
        page += (turn.to - turn.from) * pageStep.toFloat()
        val pickProgress = filmPosterCarouselSegment(
            time,
            turn.lift,
            turn.lift + FilmPosterCarouselMotionSpec.PickMs / 1_000.0,
        )
        val pick = filmPosterCarouselCubicBezier(pickProgress, 0.23, 1.0, 0.32, 1.0)
        val down = filmPosterCarouselSpringStep(
            time - turn.down,
            FilmPosterCarouselMotionSpec.DownResponse,
            FilmPosterCarouselMotionSpec.DownDamping,
        )
        lift += (pick * (1 - down)).toFloat()
        val star = filmPosterCarouselSegment(
            time,
            turn.stars,
            turn.stars + FilmPosterCarouselMotionSpec.StarsTotal,
        )
        if (star > 0 && star < 1) swell[turn.to.toInt()] = star.toFloat()
    }
    return FilmPosterCarouselPose(page, lift, swell[0], swell[1])
}

private fun filmPosterCarouselStarScale(swell: Float, index: Int): Float {
    if (swell <= 0f || swell >= 1f) return 1f
    val local = filmPosterCarouselSegment(
        swell.toDouble() * FilmPosterCarouselMotionSpec.StarsTotal,
        FilmPosterCarouselMotionSpec.StarStagger * index,
        FilmPosterCarouselMotionSpec.StarStagger * index + FilmPosterCarouselMotionSpec.StarEach,
    )
    val eased = filmPosterCarouselCubicBezier(local, 0.45, 0.0, 0.55, 1.0)
    return 1 + FilmPosterCarouselMotionSpec.StarPeak * sin(PI * eased).toFloat()
}

private data class FilmPosterCarouselFilm(
    val id: String,
    val title: String,
    val year: String,
    val director: String,
    val duration: String,
    val rating: Float,
    val tags: List<String>,
    val poster: Int,
    val panel: Color,
    val chip: Color,
    val chevron: Color,
    val meta: Color,
    val footer: Color,
    val footerInk: Color,
)

private val FilmPosterCarouselFilms = listOf(
    FilmPosterCarouselFilm(
        id = "skyride",
        title = "Skyride",
        year = "2019",
        director = "Rafi Hassan",
        duration = "2h 10m",
        rating = 4.5f,
        tags = listOf("#action", "#romantic", "#family"),
        poster = R.drawable.filmpostercarousel_poster_skyride,
        panel = Color(0xFF151A2D),
        chip = Color(0xFF393A4E),
        chevron = Color(0xFFADAEC0),
        meta = Color(0xFFC6C8D7),
        footer = Color(0xFFF0365D),
        footerInk = Color(0xFFFFEFF6),
    ),
    FilmPosterCarouselFilm(
        id = "pride",
        title = "The Lion Path",
        year = "2019",
        director = "Joel Mercer",
        duration = "2h 22m",
        rating = 5f,
        tags = listOf("#drama", "#adventure", "#family"),
        poster = R.drawable.filmpostercarousel_poster_cub,
        panel = Color(0xFF1F1516),
        chip = Color(0xFF413937),
        chevron = Color(0xFFB4A9A8),
        meta = Color(0xFFD2C9CC),
        footer = Color(0xFFF3D72E),
        footerInk = Color(0xFF140A06),
    ),
)

private enum class FilmPosterCarouselCurve {
    Linear,
    EaseInOut,
    Resume,
    Spring,
}

private class FilmPosterCarouselAnimatedValue(initial: Float) {
    private var from = initial
    private var target = initial
    private var startedAtNanos = 0L
    private var durationNanos = 0L
    private var curve = FilmPosterCarouselCurve.EaseInOut
    private var dampingRatio = FilmPosterCarouselMotionSpec.PageDamping.toFloat()
    private var velocity = 0f

    fun value(nowNanos: Long): Float {
        if (durationNanos <= 0L || nowNanos >= startedAtNanos + durationNanos) return target
        val elapsed = max(0L, nowNanos - startedAtNanos)
        val progress = elapsed.toDouble() / durationNanos
        val eased = when (curve) {
            FilmPosterCarouselCurve.Linear -> progress
            FilmPosterCarouselCurve.EaseInOut -> {
                if (progress < 0.5) {
                    2 * progress * progress
                } else {
                    1 - (2 - 2 * progress).pow(2) / 2
                }
            }
            FilmPosterCarouselCurve.Resume -> filmPosterCarouselCubicBezier(progress, 0.23, 1.0, 0.32, 1.0)
            FilmPosterCarouselCurve.Spring -> springProgress(progress)
        }
        return from + (target - from) * eased.toFloat()
    }

    fun animate(
        to: Float,
        nowNanos: Long,
        durationMs: Long,
        curve: FilmPosterCarouselCurve,
        delayMs: Long = 0,
        dampingRatio: Float = FilmPosterCarouselMotionSpec.PageDamping.toFloat(),
        velocity: Float = 0f,
    ) {
        from = value(nowNanos)
        target = to
        startedAtNanos = nowNanos + delayMs * 1_000_000L
        durationNanos = durationMs * 1_000_000L
        this.curve = curve
        this.dampingRatio = dampingRatio
        this.velocity = velocity
    }

    fun jump(to: Float) {
        from = to
        target = to
        durationNanos = 0
    }

    fun isRunning(nowNanos: Long): Boolean = nowNanos < startedAtNanos + durationNanos

    private fun springProgress(progress: Double): Double {
        val durationSeconds = durationNanos / 1e9
        val zeta = dampingRatio.toDouble().coerceIn(0.01, 0.999)
        val naturalFrequency = 4 / (zeta * durationSeconds)
        val dampedFrequency = naturalFrequency * sqrt(1 - zeta * zeta)
        val distance = (target - from).toDouble()
        val normalizedVelocity = if (abs(distance) > 0.0001) velocity / distance else 0.0

        fun raw(time: Double): Double {
            val decay = exp(-zeta * naturalFrequency * time)
            val sineCoefficient = (zeta * naturalFrequency - normalizedVelocity) / dampedFrequency
            return 1 - decay * (
                cos(dampedFrequency * time) + sineCoefficient * sin(dampedFrequency * time)
            )
        }

        val end = raw(durationSeconds)
        return if (abs(end) > 0.0001) raw(progress * durationSeconds) / end else progress
    }

}

@Stable
private class FilmPosterCarouselMotion {
    private val pageValue = FilmPosterCarouselAnimatedValue(0f)
    private val liftValue = FilmPosterCarouselAnimatedValue(0f)
    private val swell0Value = FilmPosterCarouselAnimatedValue(0f)
    private val swell1Value = FilmPosterCarouselAnimatedValue(0f)
    private val footerPressValue = FilmPosterCarouselAnimatedValue(0f)
    private val closePressValue = FilmPosterCarouselAnimatedValue(0f)

    var page by mutableFloatStateOf(0f)
        private set
    var lift by mutableFloatStateOf(0f)
        private set
    var swell0 by mutableFloatStateOf(0f)
        private set
    var swell1 by mutableFloatStateOf(0f)
        private set
    var footerPress by mutableFloatStateOf(0f)
        private set
    var closePress by mutableFloatStateOf(0f)
        private set
    var bob by mutableFloatStateOf(0f)
        private set
    var frameActive by mutableStateOf(false)
        private set
    var interactionGeneration by mutableIntStateOf(0)
    var manual by mutableStateOf(false)
    var contactActive by mutableStateOf(false)
        private set
    var dragStart = 0f
    var nowNanos = 0L
    private var scripted = false
    private var loopStartedAtNanos = 0L
    private var clockMs = 0L

    fun advance(frameNanos: Long, reducedMotion: Boolean) {
        nowNanos = frameNanos
        if (scripted) {
            if (loopStartedAtNanos == 0L) loopStartedAtNanos = frameNanos
            val elapsed = max(0L, frameNanos - loopStartedAtNanos)
            clockMs = (elapsed / 1_000_000L) % FilmPosterCarouselMotionSpec.LoopMs
            val pose = filmPosterCarouselPoseAt(clockMs / 1_000.0)
            page = pose.page
            lift = if (reducedMotion) 0f else pose.lift
            swell0 = if (reducedMotion) 0f else pose.swell0
            swell1 = if (reducedMotion) 0f else pose.swell1
        } else {
            page = pageValue.value(frameNanos)
            lift = if (reducedMotion) 0f else liftValue.value(frameNanos)
            swell0 = if (reducedMotion) 0f else swell0Value.value(frameNanos)
            swell1 = if (reducedMotion) 0f else swell1Value.value(frameNanos)
        }
        val footerSample = footerPressValue.value(frameNanos)
        val closeSample = closePressValue.value(frameNanos)
        if (footerPress != footerSample) footerPress = footerSample
        if (closePress != closeSample) closePress = closeSample
        if (!reducedMotion) {
            val phase = (clockMs % FilmPosterCarouselMotionSpec.BobMs).toFloat() /
                FilmPosterCarouselMotionSpec.BobMs
            bob = (-3 * sin(phase * PI).pow(2)).toFloat()
        } else if (bob != 0f) {
            bob = 0f
        }
        if (!scripted && !isAnimating(frameNanos)) frameActive = false
    }

    fun beginInteraction() {
        if (contactActive) return
        contactActive = true
        stopScript()
        manual = true
        interactionGeneration += 1
    }

    fun endInteraction(autoPlay: Boolean) {
        contactActive = false
        if (autoPlay) interactionGeneration += 1
    }

    fun freeze(timeMs: Double, reducedMotion: Boolean) {
        stopScript()
        clockMs = timeMs.toLong() % FilmPosterCarouselMotionSpec.LoopMs
        val pose = filmPosterCarouselPoseAt(clockMs / 1_000.0)
        page = pose.page
        lift = if (reducedMotion) 0f else pose.lift
        swell0 = if (reducedMotion) 0f else pose.swell0
        swell1 = if (reducedMotion) 0f else pose.swell1
        val phase = (clockMs % FilmPosterCarouselMotionSpec.BobMs).toDouble() / FilmPosterCarouselMotionSpec.BobMs
        bob = if (reducedMotion) 0f else (-3 * sin(phase * PI).pow(2)).toFloat()
        frameActive = false
    }

    fun beginDrag(reducedMotion: Boolean) {
        dragStart = page
        pageValue.jump(page)
        liftValue.jump(lift)
        if (!reducedMotion) {
            liftValue.animate(
                to = 1f,
                nowNanos = nowNanos,
                durationMs = (FilmPosterCarouselMotionSpec.PickResponse * 1_000).toLong(),
                curve = FilmPosterCarouselCurve.Spring,
                dampingRatio = FilmPosterCarouselMotionSpec.PickDamping.toFloat(),
            )
            frameActive = true
        }
    }

    fun updateDrag(translationX: Float, width: Float) {
        if (width <= 0f) return
        val raw = dragStart - translationX / width
        val last = (FilmPosterCarouselFilms.size - 1).toFloat()
        val over = when {
            raw < 0 -> raw
            raw > last -> raw - last
            else -> 0f
        }
        page = raw - over + over * FilmPosterCarouselMotionSpec.RubberBand
        pageValue.jump(page)
    }

    fun goTo(target: Float, velocity: Float, reducedMotion: Boolean) {
        val clamped = target.coerceIn(0f, (FilmPosterCarouselFilms.size - 1).toFloat())
        if (reducedMotion) {
            pageValue.animate(
                to = clamped,
                nowNanos = nowNanos,
                durationMs = FilmPosterCarouselMotionSpec.ReducedDurationMs,
                curve = FilmPosterCarouselCurve.EaseInOut,
            )
        } else {
            pageValue.animate(
                to = clamped,
                nowNanos = nowNanos,
                durationMs = (FilmPosterCarouselMotionSpec.PageResponse * 1_000).toLong(),
                curve = FilmPosterCarouselCurve.Spring,
                dampingRatio = FilmPosterCarouselMotionSpec.PageDamping.toFloat(),
                velocity = velocity,
            )
            liftValue.animate(
                to = 0f,
                nowNanos = nowNanos,
                durationMs = (FilmPosterCarouselMotionSpec.DownResponse * 1_000).toLong(),
                curve = FilmPosterCarouselCurve.Spring,
                delayMs = 120,
                dampingRatio = FilmPosterCarouselMotionSpec.DownDamping.toFloat(),
            )
            val changed = clamped != round(dragStart)
            if (changed) {
                val swell = if (clamped < 0.5f) swell0Value else swell1Value
                swell.jump(0f)
                swell.animate(
                    to = 1f,
                    nowNanos = nowNanos,
                    durationMs = (FilmPosterCarouselMotionSpec.StarsTotal * 1_000).toLong(),
                    curve = FilmPosterCarouselCurve.Linear,
                    delayMs = 450,
                )
            }
        }
        frameActive = true
    }

    fun resumeToRest() {
        scripted = false
        pageValue.animate(
            to = 0f,
            nowNanos = nowNanos,
            durationMs = FilmPosterCarouselMotionSpec.ResumeDurationMs,
            curve = FilmPosterCarouselCurve.Resume,
        )
        liftValue.animate(
            to = 0f,
            nowNanos = nowNanos,
            durationMs = FilmPosterCarouselMotionSpec.ResumeDurationMs,
            curve = FilmPosterCarouselCurve.EaseInOut,
        )
        frameActive = true
    }

    fun pressFooter(pressed: Boolean) {
        footerPressValue.animate(
            to = if (pressed) 1f else 0f,
            nowNanos = nowNanos,
            durationMs = if (pressed) 120 else 180,
            curve = FilmPosterCarouselCurve.EaseInOut,
        )
        frameActive = true
    }

    fun pressClose(pressed: Boolean) {
        closePressValue.animate(
            to = if (pressed) 1f else 0f,
            nowNanos = nowNanos,
            durationMs = 120,
            curve = FilmPosterCarouselCurve.EaseInOut,
        )
        frameActive = true
    }

    fun startScript() {
        loopStartedAtNanos = 0L
        clockMs = 0L
        scripted = true
        frameActive = true
    }

    fun holdReducedMotion() {
        stopScript()
        val selected = round(page).coerceIn(0f, (FilmPosterCarouselFilms.size - 1).toFloat())
        pageValue.jump(selected)
        liftValue.jump(0f)
        swell0Value.jump(0f)
        swell1Value.jump(0f)
        page = selected
        lift = 0f
        swell0 = 0f
        swell1 = 0f
        bob = 0f
        frameActive = false
    }

    fun stopScript() {
        if (!scripted) return
        scripted = false
        pageValue.jump(page)
        liftValue.jump(lift)
        swell0Value.jump(swell0)
        swell1Value.jump(swell1)
        frameActive = false
    }

    fun cancelDrag(reducedMotion: Boolean) {
        goTo(dragStart, velocity = 0f, reducedMotion = reducedMotion)
    }

    private fun isAnimating(frameNanos: Long): Boolean =
        pageValue.isRunning(frameNanos) ||
            liftValue.isRunning(frameNanos) ||
            swell0Value.isRunning(frameNanos) ||
            swell1Value.isRunning(frameNanos) ||
            footerPressValue.isRunning(frameNanos) ||
            closePressValue.isRunning(frameNanos)
}

private enum class FilmPosterCarouselTouchMode {
    Page,
    Footer,
    Close,
}

const val LOOP_MS = 7_000L

@Composable
fun FilmPosterCarousel(
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false,
    freezeAt: Double? = null,
) {
    val reducedMotion = rememberReducedMotion()
    val motion = remember { FilmPosterCarouselMotion() }
    val activity = LocalActivity.current
    val view = LocalView.current
    val density = LocalDensity.current

    DisposableEffect(activity, view) {
        val controller = activity?.window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = false
        onDispose {
            if (previous != null) controller?.isAppearanceLightStatusBars = previous
        }
    }

    LaunchedEffect(motion, freezeAt, reducedMotion) {
        if (freezeAt != null) motion.freeze(freezeAt, reducedMotion)
        else withFrameNanos { motion.advance(it, reducedMotion) }
    }

    LaunchedEffect(motion.frameActive, reducedMotion, freezeAt) {
        if (freezeAt != null) return@LaunchedEffect
        while (motion.frameActive) {
            withFrameNanos { motion.advance(it, reducedMotion) }
        }
    }

    LaunchedEffect(autoPlay, reducedMotion, motion.interactionGeneration, freezeAt) {
        if (!autoPlay || motion.contactActive || freezeAt != null) return@LaunchedEffect
        if (reducedMotion) {
            motion.holdReducedMotion()
            return@LaunchedEffect
        }
        if (motion.manual) {
            delay(FilmPosterCarouselMotionSpec.IdleResumeMs)
            motion.resumeToRest()
            delay(FilmPosterCarouselMotionSpec.ResumeRestartDelayMs)
            motion.manual = false
        }
        motion.startScript()
        awaitCancellation()
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        val widthDp = maxWidth
        val widthPx = with(density) { widthDp.toPx() }
        val closeHitSlopPx = with(density) { 2.dp.toPx() }
        val closeLeftPx = widthPx - with(density) {
            (FilmPosterCarouselLayout.CloseRight + FilmPosterCarouselLayout.CloseSize).dp.toPx()
        }
            - closeHitSlopPx
        val closeRightPx = widthPx - with(density) {
            FilmPosterCarouselLayout.CloseRight.dp.toPx()
        } + closeHitSlopPx
        val closeTopPx = with(density) { FilmPosterCarouselLayout.CloseTop.dp.toPx() } - closeHitSlopPx
        val closeBottomPx = with(density) {
            (FilmPosterCarouselLayout.CloseTop + FilmPosterCarouselLayout.CloseSize).dp.toPx()
        } + closeHitSlopPx
        val panThresholdPx = with(density) { 10.dp.toPx() }
        val footerTopPx = with(density) { FilmPosterCarouselLayout.PanelBottom.dp.toPx() }

        FilmPosterCarouselRoom(motion = motion, reducedMotion = reducedMotion)
        FilmPosterCarouselFilms.asReversed().forEachIndexed { reversedIndex, film ->
            val index = FilmPosterCarouselFilms.lastIndex - reversedIndex
            FilmPosterCarouselCard(
                film = film,
                index = index,
                motion = motion,
                reducedMotion = reducedMotion,
            )
        }

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(motion, reducedMotion, autoPlay, widthPx, freezeAt) {
                    if (freezeAt != null) return@pointerInput
                    val tracker = VelocityTracker()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        tracker.resetTracking()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        motion.beginInteraction()
                        val mode = when {
                            down.position.x in closeLeftPx..closeRightPx &&
                                down.position.y in closeTopPx..closeBottomPx -> FilmPosterCarouselTouchMode.Close
                            down.position.y >= footerTopPx -> FilmPosterCarouselTouchMode.Footer
                            else -> FilmPosterCarouselTouchMode.Page
                        }
                        when (mode) {
                            FilmPosterCarouselTouchMode.Page -> motion.beginDrag(reducedMotion)
                            FilmPosterCarouselTouchMode.Footer -> motion.pressFooter(true)
                            FilmPosterCarouselTouchMode.Close -> motion.pressClose(true)
                        }
                        val start = down.position
                        var latest: PointerInputChange = down
                        var panActivated = false
                        var cancelled = false

                        while (latest.pressed) {
                            val event = awaitPointerEvent()
                            if (event.changes.size > 1) {
                                cancelled = true
                                break
                            }
                            latest = event.changes.firstOrNull { it.id == down.id } ?: break
                            tracker.addPosition(latest.uptimeMillis, latest.position)
                            if (mode == FilmPosterCarouselTouchMode.Page) {
                                val translation = latest.position.x - start.x
                                if (abs(translation) >= panThresholdPx) {
                                    panActivated = true
                                    motion.updateDrag(translation, widthPx)
                                }
                            }
                            latest.consume()
                        }

                        val velocityX = tracker.calculateVelocity().x
                        when (mode) {
                            FilmPosterCarouselTouchMode.Page -> {
                                if (panActivated && !cancelled) {
                                    val pageVelocity = -velocityX / widthPx
                                    val flick = if (
                                        abs(velocityX) / 1_000 > FilmPosterCarouselMotionSpec.FlickThreshold
                                    ) {
                                        sign(pageVelocity)
                                    } else {
                                        0f
                                    }
                                    val target = if (flick != 0f) {
                                        round(motion.dragStart) + flick
                                    } else {
                                        round(
                                            motion.page +
                                                pageVelocity * FilmPosterCarouselMotionSpec.VelocityProjection,
                                        )
                                    }
                                    val clampedTarget = target.coerceIn(0f, FilmPosterCarouselFilms.lastIndex.toFloat())
                                    if (clampedTarget != round(motion.dragStart)) {
                                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    }
                                    motion.goTo(target, pageVelocity, reducedMotion)
                                } else {
                                    motion.cancelDrag(reducedMotion)
                                }
                            }
                            FilmPosterCarouselTouchMode.Footer -> motion.pressFooter(false)
                            FilmPosterCarouselTouchMode.Close -> motion.pressClose(false)
                        }
                        motion.endInteraction(autoPlay && !reducedMotion)
                    }
                },
        )

        FilmPosterCarouselClose(motion)

        Box(
            Modifier
                .offset(
                    x = (maxWidth.value - FilmPosterCarouselLayout.CloseRight - FilmPosterCarouselLayout.CloseSize - 2).dp,
                    y = (FilmPosterCarouselLayout.CloseTop - 2).dp,
                )
                .size((FilmPosterCarouselLayout.CloseSize + 4).dp)
                .semantics {
                    role = Role.Button
                    contentDescription = "Close"
                    onClick { true }
                },
        )
    }
}

@Composable
private fun FilmPosterCarouselRoom(
    motion: FilmPosterCarouselMotion,
    reducedMotion: Boolean,
) {
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = if (reducedMotion) 0f else (motion.lift * 4).coerceIn(0f, 1f)
            },
    ) {
        FilmPosterCarouselFilms.forEachIndexed { index, film ->
            Image(
                painter = painterResource(film.poster),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = 1.2f
                        scaleY = 1.2f
                    }
                    .blur(FilmPosterCarouselMotionSpec.RoomBlur.dp)
                    .graphicsLayer {
                        alpha = 1 - abs(index - motion.page).coerceIn(0f, 1f)
                    },
            )
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = FilmPosterCarouselMotionSpec.RoomDim)))
    }
}

@Composable
private fun FilmPosterCarouselCard(
    film: FilmPosterCarouselFilm,
    index: Int,
    motion: FilmPosterCarouselMotion,
    reducedMotion: Boolean,
) {
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                val distance = index - motion.page
                val lift = motion.lift.coerceIn(0f, 1f)
                val baseScale = 1 - FilmPosterCarouselMotionSpec.LiftScale * motion.lift
                alpha = 1f
                translationX = 0f
                translationY = 0f
                rotationZ = 0f
                scaleX = 1f
                scaleY = 1f
                shadowElevation = 0f
                ambientShadowColor = Color.Transparent
                spotShadowColor = Color.Transparent
                shape = RoundedCornerShape(
                    (if (reducedMotion) 0f else FilmPosterCarouselMotionSpec.LiftRadius * lift).dp,
                )
                clip = true
                if (reducedMotion) {
                    alpha = if (distance <= 0) 1 - (-distance).coerceIn(0f, 1f) else 1f
                } else if (distance <= 0) {
                    val progress = (-distance).coerceIn(0f, 1f)
                    translationX = -progress * size.width * FilmPosterCarouselMotionSpec.ThrowX
                    translationY = FilmPosterCarouselMotionSpec.ThrowDrop.dp.toPx() * progress * progress
                    rotationZ = -FilmPosterCarouselMotionSpec.ThrowTilt * progress
                    scaleX = baseScale * (1 - FilmPosterCarouselMotionSpec.ThrowShrink * progress)
                    scaleY = scaleX
                    shadowElevation = FilmPosterCarouselMotionSpec.LiftShadowBlur.dp.toPx() / 2 * lift
                    ambientShadowColor = Color.Black.copy(alpha = FilmPosterCarouselMotionSpec.LiftShadow * lift)
                    spotShadowColor = ambientShadowColor
                } else {
                    val waiting = distance.coerceIn(0f, 1f)
                    scaleX = baseScale * (1 - FilmPosterCarouselMotionSpec.WaitingScale * waiting)
                    scaleY = scaleX
                    shadowElevation = FilmPosterCarouselMotionSpec.LiftShadowBlur.dp.toPx() / 2 * lift * (1 - waiting)
                    ambientShadowColor = Color.Black.copy(
                        alpha = FilmPosterCarouselMotionSpec.LiftShadow * lift * (1 - waiting),
                    )
                    spotShadowColor = ambientShadowColor
                }
            }
            .background(film.panel),
    ) {
        FilmPosterCarouselFooter(film, motion)
        FilmPosterCarouselPanel(film, index, motion, reducedMotion)
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = if (reducedMotion) {
                        0f
                    } else {
                        FilmPosterCarouselMotionSpec.WaitingDim * (index - motion.page).coerceIn(0f, 1f)
                    }
                }
                .background(Color.Black),
        )
    }
}

@Composable
private fun FilmPosterCarouselFooter(
    film: FilmPosterCarouselFilm,
    motion: FilmPosterCarouselMotion,
) {
    Box(
        Modifier
            .fillMaxSize()
            .offset(y = FilmPosterCarouselLayout.FooterTop.dp)
            .background(film.footer)
            .semantics {
                role = Role.Button
                contentDescription = "Watch now"
                onClick { true }
            },
    ) {
        BasicText(
            text = "WATCH NOW",
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(
                    y = (
                        textTop(
                            FilmPosterCarouselLayout.FooterBaseline,
                            FilmPosterCarouselLayout.FooterSize,
                        ) - FilmPosterCarouselLayout.FooterTop
                        ).dp,
                )
                .graphicsLayer { alpha = 1 - 0.35f * motion.footerPress },
            style = TextStyle(
                color = film.footerInk,
                fontSize = FilmPosterCarouselLayout.FooterSize.sp,
                lineHeight = (FilmPosterCarouselLayout.FooterSize * 1.2f).sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.SansSerif,
                letterSpacing = FilmPosterCarouselLayout.FooterTracking.sp,
            ),
        )
    }
}

@Composable
private fun FilmPosterCarouselPanel(
    film: FilmPosterCarouselFilm,
    index: Int,
    motion: FilmPosterCarouselMotion,
    reducedMotion: Boolean,
) {
    val panelShape = RoundedCornerShape(
        topStart = 0.dp,
        topEnd = 0.dp,
        bottomEnd = FilmPosterCarouselLayout.PanelRadius.dp,
        bottomStart = FilmPosterCarouselLayout.PanelRadius.dp,
    )
    Box(
        Modifier
            .fillMaxWidth()
            .height(FilmPosterCarouselLayout.PanelBottom.dp)
            .offset(y = FilmPosterCarouselLayout.PanelShadowY.dp)
            .shadow(
                elevation = (FilmPosterCarouselLayout.PanelShadowRadius * 2).dp,
                shape = panelShape,
                clip = false,
            )
            .background(Color.Black, panelShape),
    )
    Box(
        Modifier
            .fillMaxWidth()
            .height(FilmPosterCarouselLayout.PanelBottom.dp)
            .clip(panelShape)
            .background(film.panel),
    ) {
        FilmPosterCarouselPoster(film, index, motion, reducedMotion)
        FilmPosterCarouselFade(film.panel)
        FilmPosterCarouselSheen(index, motion, reducedMotion)
        FilmPosterCarouselInfo(
            film = film,
            swell = if (index == 0) motion.swell0 else motion.swell1,
            reducedMotion = reducedMotion,
        )
        FilmPosterCarouselChevron(film.chevron, motion)
    }
}

@Composable
private fun FilmPosterCarouselPoster(
    film: FilmPosterCarouselFilm,
    index: Int,
    motion: FilmPosterCarouselMotion,
    reducedMotion: Boolean,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(FilmPosterCarouselLayout.PosterHeight.dp)
            .graphicsLayer {
                clip = true
            },
    ) {
        Image(
            painter = painterResource(film.poster),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = if (reducedMotion) {
                        0f
                    } else {
                        (motion.page - index).coerceIn(0f, 1f) * size.width * FilmPosterCarouselMotionSpec.ArtLag
                    }
                },
        )
    }
}

@Composable
private fun FilmPosterCarouselFade(color: Color) {
    val top = FilmPosterCarouselLayout.PosterHeight * FilmPosterCarouselLayout.FadeFrom
    val height = FilmPosterCarouselLayout.PosterHeight * (1 - FilmPosterCarouselLayout.FadeFrom) + 1
    Box(
        Modifier
            .fillMaxWidth()
            .height(height.dp)
            .offset(y = top.dp)
            .background(fadeBrush(color)),
    )
}

@Composable
private fun FilmPosterCarouselSheen(
    index: Int,
    motion: FilmPosterCarouselMotion,
    reducedMotion: Boolean,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height((FilmPosterCarouselLayout.PosterHeight * 1.3f).dp)
            .offset(y = (-FilmPosterCarouselLayout.PosterHeight * 0.15f).dp)
            .drawBehind {
                if (reducedMotion) return@drawBehind
                val distance = index - motion.page
                val progress = if (distance <= 0) {
                    (-distance).coerceIn(0f, 1f)
                } else {
                    1 - distance.coerceIn(0f, 1f)
                }
                val opacity = motion.lift.coerceIn(0f, 1f) * sin(PI * progress).toFloat()
                val bandWidth = size.width * FilmPosterCarouselMotionSpec.SheenWidth
                val left = size.width * (1 - FilmPosterCarouselMotionSpec.SheenTravel * progress) - bandWidth / 2
                withTransform({ rotate(FilmPosterCarouselMotionSpec.SheenSlant) }) {
                    drawRect(
                        brush = Brush.horizontalGradient(
                            0f to Color.Transparent,
                            0.5f to Color.White.copy(alpha = FilmPosterCarouselMotionSpec.SheenAlpha * opacity),
                            1f to Color.Transparent,
                            startX = left,
                            endX = left + bandWidth,
                        ),
                        topLeft = Offset(left, 0f),
                        size = androidx.compose.ui.geometry.Size(bandWidth, size.height),
                    )
                }
            },
    )
}

@Composable
private fun FilmPosterCarouselInfo(
    film: FilmPosterCarouselFilm,
    swell: Float,
    reducedMotion: Boolean,
) {
    Box(Modifier.fillMaxSize()) {
        FilmPosterCarouselLine(FilmPosterCarouselLayout.TitleBaseline, FilmPosterCarouselLayout.TitleSize) {
            FilmPosterCarouselText(
                text = film.title,
                x = FilmPosterCarouselLayout.TitleX,
                size = FilmPosterCarouselLayout.TitleSize,
                weight = FontWeight.Bold,
                tracking = FilmPosterCarouselLayout.TitleTracking,
                color = Color.White,
                maxLines = 1,
            )
        }
        FilmPosterCarouselLine(FilmPosterCarouselLayout.MetaBaseline, FilmPosterCarouselLayout.MetaSize) {
            FilmPosterCarouselText(
                text = "${film.year}  ·  ${film.director}",
                x = FilmPosterCarouselLayout.MetaX,
                size = FilmPosterCarouselLayout.MetaSize,
                weight = FontWeight.SemiBold,
                tracking = FilmPosterCarouselLayout.MetaTracking,
                color = film.meta,
            )
        }
        FilmPosterCarouselLine(FilmPosterCarouselLayout.DurationBaseline, FilmPosterCarouselLayout.DurationSize) {
            FilmPosterCarouselText(
                text = film.duration,
                x = FilmPosterCarouselLayout.DurationX,
                size = FilmPosterCarouselLayout.DurationSize,
                weight = FontWeight.Bold,
                tracking = FilmPosterCarouselLayout.DurationTracking,
                color = Color.White,
            )
        }
        FilmPosterCarouselLine(
            top = FilmPosterCarouselLayout.StarsCenterY - FilmPosterCarouselLayout.StarSize / 2,
        ) {
            Row(
                Modifier
                    .offset(x = (FilmPosterCarouselLayout.StarsCenterX - FilmPosterCarouselLayout.StarSize / 2).dp)
                    .semantics {
                        contentDescription = "${film.rating} out of 5 stars"
                    },
            ) {
                repeat(5) { star ->
                    FilmPosterCarouselStar(
                        fill = (film.rating - star).coerceIn(0f, 1f),
                        modifier = Modifier
                            .size(FilmPosterCarouselLayout.StarSize.dp)
                            .graphicsLayer {
                                val scale = if (reducedMotion) 1f else filmPosterCarouselStarScale(swell, star)
                                scaleX = scale
                                scaleY = scale
                            }
                            .offset(
                                x = if (star == 0) 0.dp else (
                                    (FilmPosterCarouselLayout.StarPitch - FilmPosterCarouselLayout.StarSize) * star
                                    ).dp,
                            ),
                    )
                }
            }
        }
        FilmPosterCarouselLine(
            top = FilmPosterCarouselLayout.TagsTop,
        ) {
            Row(
                Modifier.offset(x = FilmPosterCarouselLayout.TagsX.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                film.tags.forEachIndexed { tagIndex, tag ->
                    Box(
                        modifier = Modifier
                            .offset(x = if (tagIndex == 0) 0.dp else (FilmPosterCarouselLayout.TagGap * tagIndex).dp)
                            .height(FilmPosterCarouselLayout.TagHeight.dp)
                            .clip(RoundedCornerShape(50))
                            .background(film.chip)
                            .padding(horizontal = FilmPosterCarouselLayout.TagPaddingX.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        BasicText(
                            text = tag,
                            style = TextStyle(
                                color = Color.White,
                                fontSize = FilmPosterCarouselLayout.TagSize.sp,
                                lineHeight = (FilmPosterCarouselLayout.TagSize * 1.2f).sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.SansSerif,
                                letterSpacing = FilmPosterCarouselLayout.TagTracking.sp,
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilmPosterCarouselLine(
    baseline: Float? = null,
    size: Float? = null,
    top: Float? = null,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .offset(y = (top ?: textTop(checkNotNull(baseline), checkNotNull(size))).dp),
    ) {
        content()
    }
}

@Composable
private fun FilmPosterCarouselText(
    text: String,
    x: Float,
    size: Float,
    weight: FontWeight,
    tracking: Float,
    color: Color,
    maxLines: Int = Int.MAX_VALUE,
) {
    BasicText(
        text = text,
        modifier = Modifier.offset(x = x.dp),
        style = TextStyle(
            color = color,
            fontSize = size.sp,
            lineHeight = (size * 1.2f).sp,
            fontWeight = weight,
            fontFamily = FontFamily.SansSerif,
            letterSpacing = tracking.sp,
        ),
        maxLines = maxLines,
        overflow = TextOverflow.Clip,
    )
}

@Composable
private fun FilmPosterCarouselStar(
    fill: Float,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val path = Path()
        repeat(10) { index ->
            val radius = 11.5f * if (index % 2 == 0) 1f else FilmPosterCarouselLayout.StarInnerRadius
            val angle = -PI / 2 + index * PI / 5
            val point = Offset(
                x = (12 + radius * cos(angle)).toFloat(),
                y = (13 + radius * sin(angle)).toFloat(),
            )
            if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
        }
        path.close()
        withTransform({ scale(size.width / 24, size.height / 24, pivot = Offset.Zero) }) {
            drawPath(
                path,
                Color.Black.copy(alpha = FilmPosterCarouselLayout.StarHalo * 0.35f),
                style = Stroke(width = 5f, join = StrokeJoin.Round),
            )
            drawPath(
                path,
                Color.Black.copy(alpha = FilmPosterCarouselLayout.StarHalo * 0.65f),
                style = Stroke(width = 3f, join = StrokeJoin.Round),
            )
            drawPath(path, Color(0xFF8A8778))
            drawPath(
                path,
                Color(0xFF8A8778),
                style = Stroke(width = 1.4f, join = StrokeJoin.Round),
            )
            clipRect(right = 24 * fill) {
                drawPath(path, Color(0xFFEBD46E))
                drawPath(
                    path,
                    Color(0xFFEBD46E),
                    style = Stroke(width = 1.8f, join = StrokeJoin.Round),
                )
            }
        }
    }
}

@Composable
private fun FilmPosterCarouselChevron(
    color: Color,
    motion: FilmPosterCarouselMotion,
) {
    Canvas(
        Modifier
            .offset(
                x = (FilmPosterCarouselLayout.ChevronCenterX - FilmPosterCarouselLayout.ChevronWidth / 2).dp,
                y = FilmPosterCarouselLayout.ChevronTop.dp,
            )
            .size(
                width = FilmPosterCarouselLayout.ChevronWidth.dp,
                height = FilmPosterCarouselLayout.ChevronHeight.dp,
            )
            .graphicsLayer { translationY = motion.bob.dp.toPx() },
    ) {
        val stroke = FilmPosterCarouselLayout.ChevronStroke.dp.toPx()
        val inset = stroke / 2
        val path = Path().apply {
            moveTo(inset, size.height - inset)
            lineTo(size.width / 2, inset)
            lineTo(size.width - inset, size.height - inset)
        }
        drawPath(
            path,
            color,
            style = Stroke(
                width = stroke,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
}

@Composable
private fun FilmPosterCarouselClose(motion: FilmPosterCarouselMotion) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(
                top = FilmPosterCarouselLayout.CloseTop.dp,
                end = FilmPosterCarouselLayout.CloseRight.dp,
            ),
        contentAlignment = Alignment.TopEnd,
    ) {
        Box(
            Modifier
                .size(FilmPosterCarouselLayout.CloseSize.dp)
                .graphicsLayer {
                    val scale = 1 - 0.03f * motion.closePress
                    scaleX = scale
                    scaleY = scale
                }
                .clip(RoundedCornerShape(FilmPosterCarouselLayout.CloseRadius.dp))
                .background(Color(0xFFF5F5E9)),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(FilmPosterCarouselLayout.CloseIcon.dp)) {
                val scaleX = size.width / 24
                val scaleY = size.height / 24
                drawLine(
                    color = Color(0xFF0A090D),
                    start = Offset(3 * scaleX, 3 * scaleY),
                    end = Offset(21 * scaleX, 21 * scaleY),
                    strokeWidth = 3f * scaleX,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = Color(0xFF0A090D),
                    start = Offset(21 * scaleX, 3 * scaleY),
                    end = Offset(3 * scaleX, 21 * scaleY),
                    strokeWidth = 3f * scaleX,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

private fun fadeBrush(color: Color): Brush = Brush.verticalGradient(
    colorStops = arrayOf(
        0f to color.copy(alpha = 0f),
        0.2f to color.copy(alpha = 0x1F / 255f),
        0.42f to color.copy(alpha = 0x66 / 255f),
        0.62f to color.copy(alpha = 0xB3 / 255f),
        0.82f to color.copy(alpha = 0xE6 / 255f),
        1f to color,
    ),
)

private fun textTop(baseline: Float, size: Float): Float =
    baseline - size * FilmPosterCarouselLayout.BaselineRatio
