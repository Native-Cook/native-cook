package com.ngocdevv.nativecook.blocks.lists.moviecardlist

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.view.ViewCompat
import com.ngocdevv.nativecook.R
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import java.util.Locale

private object MovieCardListColors {
    val Paper = Color(0xFFFFFFFF)
    val Ink = Color(0xFF151515)
    val InkSoft = Color(0xFF4A4A4A)
    val InkMute = Color(0xFF9A9A9A)
    val Chip = Color(0xFFC9C9C9)
    val Star = Color(0xFFE8423F)
    val StarOff = Color(0xFFC4C4C4)
    val Button = Color(0xFF262626)
    val Night = Color.Black
    val SeatFree = Color(0xFFE9E9EE)
    val SeatTaken = Color(0xFF3A3A3E)
    val Red = Color(0xFFE8262B)
    val RedHot = Color(0xFFF2552F)
    val Legend = Color(0xFFB9B9BE)
}

/** All measurements are the approved 402 × 874 React Native reference coordinates. */
private object MovieCardListLayout {
    const val Width = 402f
    const val Height = 874f
    const val IconX = 21.8f
    const val IconY = 60.8f
    const val IconSize = 24.4f
    const val TargetX = 377.7f
    const val TargetY = 61f
    const val TargetSize = 26.2f
    const val CardWidth = 291f
    const val CardTop = 245f
    const val CardHeight = 640f
    const val CardRadius = 43f
    const val PosterInset = 35f
    const val PosterTop = 27f
    const val PosterHeight = 326f
    const val PosterRadius = 26f
    const val Pitch = 287f
    const val SideScale = .85f
    const val SideWash = .85f
    const val BackdropHeight = 600f
    const val TitleY = 639f
    const val ChipsY = 673.3f
    const val RatingY = 703.4f
    const val DotsY = 733.8f
    const val ButtonY = 800.1f
    const val ButtonWidth = 254.7f
    const val ButtonHeight = 58.8f
    const val SheetTop = 257f
    const val SheetInset = 10f
    const val SheetRadius = 28f
    const val SheetPosterTop = 79f
    const val SheetPosterScale = .94f
    const val SheetSideScale = .65f
    const val SheetPitch = .565f
    const val SheetContentX = 33f
    const val DetailTitleY = 87f
    const val DetailChipsY = 122f
    const val DetailRatingY = 152f
    const val DirectorY = 174f
    const val ActorsLabelY = 240f
    const val ActorsTop = 258f
    const val ActorWidth = 100f
    const val ActorHeight = 110f
    const val ActorGap = 18f
    const val ActorRadius = 10f
    const val IntroLabelY = 445f
    const val IntroTop = 466f
    const val DetailButtonY = 521f
    const val DetailButtonWidth = 300f
    const val DetailButtonHeight = 52f
    const val ScreenTop = 112f
    const val ScreenHeight = 128f
    const val ScreenInset = 26f
    const val GridTop = 313.5f
    const val Seat = 20f
    const val SeatPitchX = 26.3f
    const val SeatPitchY = 28f
    const val Aisle = 32.4f
    const val LegendY = 622f
    const val DateY = 690f
    const val PayY = 774f
    const val PayWidth = 300f
    const val PayHeight = 52f
}

private object MovieCardListMotionSpec {
    const val LOOP_MS = 15_800L
    const val IDLE_RESUME_MS = 3_500L
    const val OPEN_MS = 680L
    const val COVER_DELAY_MS = 40L
    const val COVER_MS = 560L
    const val BUILD_DELAY_MS = 620L
    const val BUILD_ROW_STAGGER_MS = 70L
    const val BUILD_ROW_MS = 300L
    const val BUILD_CHROME_AT_MS = 600L
    const val BUILD_CHROME_MS = 360L
    const val FOOTAGE_IN_AT_MS = 600L
    const val FOOTAGE_IN_MS = 400L
    const val FOOTAGE_OUT_AT_MS = 1_820L
    const val FOOTAGE_OUT_MS = 420L
    const val FOOTAGE_DRIFT_MS = 2_200L
    const val PAY_SHRINK_MS = 340L
    const val PAY_BLACK_AT_MS = 260L
    const val PAY_BLACK_MS = 240L
    const val PAY_FACE_AT_MS = 520L
    const val PAY_FACE_MS = 560L
    const val PAY_SPIN_AT_MS = 1_100L
    const val PAY_SPIN_MS = 420L
    const val PAY_CHECK_AT_MS = 1_520L
    const val PAY_CHECK_MS = 380L
    const val RESET_MS = 420L
    const val REDUCED_FADE_MS = 200L
    const val SEAT_CLOCK_MS = 6_000L
    const val PAY_CLOCK_MS = PAY_CHECK_AT_MS + PAY_CHECK_MS + 600L
    const val PRESS_SCALE = .97f
    val openEase = CubicBezierEasing(.32f, .72f, 0f, 1f)
    val coverEase = CubicBezierEasing(.55f, 0f, .35f, 1f)
    val pageSpring = MovieCardListSpring(damping = 22f, stiffness = 210f, mass = .9f)
    val castSpring = MovieCardListSpring(damping = 24f, stiffness = 140f, mass = 1f)
    val seatPop = MovieCardListSpring(damping = 11f, stiffness = 320f, mass = .6f)
}

private data class MovieCardListSpring(val damping: Float, val stiffness: Float, val mass: Float)

private data class MovieCardListCast(val name: String, val image: Int)
private data class MovieCardListMovie(
    val id: String,
    val title: String,
    val genres: List<String>,
    val rating: Float,
    val director: String,
    val intro: String,
    val poster: Int,
    val backdrop: Int,
    val backdropColor: Color,
    val cast: List<MovieCardListCast>,
    val tone: MovieCardListTone,
)

private enum class MovieCardListTone { Buds, Jester, Grift }
private enum class MovieCardListStage { Cards, Detail, Seats, Pay }

private val MovieCardListPoppins = FontFamily(
    Font(R.font.moviecardlist_poppins_regular, FontWeight.Normal),
    Font(R.font.moviecardlist_poppins_medium, FontWeight.Medium),
    Font(R.font.moviecardlist_poppins_semibold, FontWeight.SemiBold),
)
private val MovieCardListAnton = FontFamily(Font(R.font.moviecardlist_anton_regular))
private val MovieCardListSerif = FontFamily(Font(R.font.moviecardlist_dmserifdisplay_regular))

private val MovieCardListMovies = listOf(
    MovieCardListMovie(
        id = "buds", title = "Best Buds", genres = listOf("Comedy", "Family", "Teen"), rating = 8.1f,
        director = "Director / Sam Whitlow",
        intro = "Three sixth-graders skip school to replace a drone they broke, and their one-day trip across town turns into the wildest weekend of their lives.",
        poster = R.drawable.moviecardlist_poster_buds, backdrop = R.drawable.moviecardlist_backdrop_buds,
        backdropColor = Color(0xFFE3221C), tone = MovieCardListTone.Buds,
        cast = listOf(
            MovieCardListCast("Milo Hart", R.drawable.moviecardlist_cast_buds_1),
            MovieCardListCast("Ezra Quinn", R.drawable.moviecardlist_cast_buds_2),
            MovieCardListCast("Dean Porter", R.drawable.moviecardlist_cast_buds_3),
        ),
    ),
    MovieCardListMovie(
        id = "jester", title = "Drift", genres = listOf("Action", "Drama", "History"), rating = 9f,
        director = "Director / Nora Kessler",
        intro = "The film “Drift” follows a night-shift ferry pilot who finds a stranger’s phone on board and is pulled into the city’s underworld. Released by Northlight Pictures this October.",
        poster = R.drawable.moviecardlist_poster_jester, backdrop = R.drawable.moviecardlist_backdrop_jester,
        backdropColor = Color(0xFF050807), tone = MovieCardListTone.Jester,
        cast = listOf(
            MovieCardListCast("Mason Vale", R.drawable.moviecardlist_cast_jester_1),
            MovieCardListCast("Robert Delane", R.drawable.moviecardlist_cast_jester_2),
            MovieCardListCast("Zara Bennett", R.drawable.moviecardlist_cast_jester_3),
        ),
    ),
    MovieCardListMovie(
        id = "grift", title = "The Grift", genres = listOf("Comedy", "Crime", "Romance"), rating = 7.6f,
        director = "Director / Lena Ortiz",
        intro = "Two rival con artists on the Riviera team up to fleece a tech millionaire, and soon discover that the mark is playing them both.",
        poster = R.drawable.moviecardlist_poster_grift, backdrop = R.drawable.moviecardlist_backdrop_grift,
        backdropColor = Color(0xFFC9A23A), tone = MovieCardListTone.Grift,
        cast = listOf(
            MovieCardListCast("Ava Sinclair", R.drawable.moviecardlist_cast_grift_1),
            MovieCardListCast("Rose Hadley", R.drawable.moviecardlist_cast_grift_2),
            MovieCardListCast("Victor Lang", R.drawable.moviecardlist_cast_grift_3),
        ),
    ),
)

private val MovieCardListSeatRows = listOf(
    "TTAAAAAAA TT", "TTAAAAAAA TT", "TTTTTTTTT TT", "TTTTTTTTT TT", "TTTTTTTTT TT",
    "TTTTTTTTT TT", "TTTTTTTTT TT", "TTTTAAAAA TT", "TAAAAAAAA TT", "TAAAAAAAA TT",
)
private val MovieCardListTimes = listOf("17:30", "18:00", "18:30", "19:00", "20:15")

private class MovieCardListTrack(initial: Float) {
    private var from = initial
    private var to = initial
    private var startNanos = 0L
    private var durationNanos = 0L
    private var easing: (Float) -> Float = { it }
    fun sample(now: Long): Float {
        if (durationNanos == 0L || now >= startNanos + durationNanos) return to
        val p = ((now - startNanos).toDouble() / durationNanos).toFloat().coerceIn(0f, 1f)
        return from + (to - from) * easing(p)
    }
    fun animate(target: Float, now: Long, durationMs: Long, curve: (Float) -> Float) {
        from = sample(now)
        to = target
        startNanos = now
        durationNanos = durationMs * 1_000_000L
        easing = curve
    }
    fun jump(target: Float) { from = target; to = target; durationNanos = 0L }
    fun isRunning(now: Long): Boolean = durationNanos > 0L && now < startNanos + durationNanos
}

@Stable
private class MovieCardListMotion {
    private val pageTrack = MovieCardListTrack(1f)
    private val openTrack = MovieCardListTrack(0f)
    private val coverTrack = MovieCardListTrack(0f)
    private val seatTrack = MovieCardListTrack(0f)
    private val payTrack = MovieCardListTrack(0f)
    private val castTrack = MovieCardListTrack(0f)
    private val nightTrack = MovieCardListTrack(0f)
    private val buttonTrack = MovieCardListTrack(0f)
    private val veilTrack = MovieCardListTrack(0f)
    var page by mutableFloatStateOf(1f); private set
    var open by mutableFloatStateOf(0f); private set
    var cover by mutableFloatStateOf(0f); private set
    var seatClock by mutableFloatStateOf(0f); private set
    var payClock by mutableFloatStateOf(0f); private set
    var castX by mutableFloatStateOf(0f); private set
    var night by mutableFloatStateOf(0f); private set
    var buttonHidden by mutableFloatStateOf(0f); private set
    var veil by mutableFloatStateOf(0f); private set
    var nowNanos by mutableLongStateOf(0L); private set
    var frameActive by mutableStateOf(false)
        private set
    var dragStart = 1f
    fun advance(now: Long) {
        nowNanos = now
        page = pageTrack.sample(now); open = openTrack.sample(now); cover = coverTrack.sample(now)
        seatClock = seatTrack.sample(now); payClock = payTrack.sample(now); castX = castTrack.sample(now)
        night = nightTrack.sample(now); buttonHidden = buttonTrack.sample(now); veil = veilTrack.sample(now)
        frameActive = tracks.any { it.isRunning(now) }
    }
    fun page(target: Int) {
        pageTrack.animate(target.coerceIn(0, 2).toFloat(), animationNow(), 380) {
            movieCardListSpring(it, MovieCardListMotionSpec.pageSpring)
        }
        frameActive = true
    }
    fun pageJump(target: Int) { pageTrack.jump(target.coerceIn(0, 2).toFloat()); page = pageTrack.sample(nowNanos) }
    fun veil(value: Float, duration: Long) {
        veilTrack.animate(value, animationNow(), duration) { it }
        frameActive = true
    }
    fun open(target: Float, reduced: Boolean) {
        openTrack.animate(target, animationNow(), if (reduced) 200 else if (target > open) 680 else 544) {
            MovieCardListMotionSpec.openEase.transform(it)
        }
        frameActive = true
    }
    fun cover(target: Float, reduced: Boolean) {
        coverTrack.animate(target, animationNow(), if (reduced) 200 else if (target > cover) 560 else 448) {
            MovieCardListMotionSpec.coverEase.transform(it)
        }
        frameActive = true
    }
    fun cast(target: Float) { castTrack.animate(target, animationNow(), 480) { movieCardListSpring(it, MovieCardListMotionSpec.castSpring) }; frameActive = true }
    fun seatClock() { seatTrack.animate(MovieCardListMotionSpec.SEAT_CLOCK_MS.toFloat(), animationNow(), MovieCardListMotionSpec.SEAT_CLOCK_MS) { it }; frameActive = true }
    fun payClock() { payTrack.animate(MovieCardListMotionSpec.PAY_CLOCK_MS.toFloat(), animationNow(), MovieCardListMotionSpec.PAY_CLOCK_MS) { it }; frameActive = true }
    fun button(hidden: Boolean) { buttonTrack.animate(if (hidden) 1f else 0f, animationNow(), if (hidden) 160 else 200) { it }; frameActive = true }
    fun nightIn(duration: Long) { nightTrack.animate(1f, animationNow(), duration) { it }; frameActive = true }
    fun nightOut(duration: Long) { nightTrack.animate(0f, animationNow(), duration) { 1f - (1f - it) * (1f - it) }; frameActive = true }
    fun nightJump(value: Float) { nightTrack.jump(value); night = value }
    fun reset() { pageTrack.jump(1f); openTrack.jump(0f); coverTrack.jump(0f); seatTrack.jump(0f); payTrack.jump(0f); castTrack.jump(0f); buttonTrack.jump(0f); veilTrack.jump(0f) }
    fun startSeatClock() { seatTrack.jump(0f); seatClock = 0f; payTrack.jump(0f); payClock = 0f; seatClock() }
    fun stopSeatAndPayClocks() { seatTrack.jump(0f); payTrack.jump(0f); seatClock = 0f; payClock = 0f }
    fun drag(dx: Float) {
        val raw = dragStart - dx / MovieCardListLayout.Pitch
        val overflow = when { raw < 0f -> raw; raw > 2f -> raw - 2f; else -> 0f }
        pageTrack.jump(raw - overflow + overflow * .3f)
        page = pageTrack.sample(nowNanos)
    }
    private val tracks: List<MovieCardListTrack>
        get() = listOf(pageTrack, openTrack, coverTrack, seatTrack, payTrack, castTrack, nightTrack, buttonTrack, veilTrack)
    private fun animationNow(): Long = System.nanoTime().also { nowNanos = it }
}

private fun movieCardListSpring(t: Float, spring: MovieCardListSpring): Float {
    val zeta = (spring.damping / (2f * sqrt(spring.stiffness * spring.mass))).coerceIn(.01f, .99f)
    val omega = sqrt(spring.stiffness / spring.mass)
    val d = sqrt(1f - zeta * zeta)
    val seconds = t * .38f
    val v = 1f - exp((-zeta * omega * seconds).toDouble()).toFloat() *
        (cos((omega * d * seconds).toDouble()).toFloat() + zeta / d * sin((omega * d * seconds).toDouble()).toFloat())
    return if (t >= 1f) 1f else v
}

private fun movieCardListRamp(time: Float, at: Long, duration: Long) =
    ((time - at.toFloat()) / duration.toFloat()).coerceIn(0f, 1f)
private fun movieCardListLerp(a: Float, b: Float, p: Float) = a + (b - a) * p

/** Full-screen Compose port of the approved React Native movie-card-list block. */
@Composable
fun MovieCardList(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val reduced = rememberReducedMotion()
    val motion = remember { MovieCardListMotion() }
    val view = LocalView.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    DisposableEffect(view) {
        val controller = ViewCompat.getWindowInsetsController(view)
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = false
        onDispose {
            if (previous != null) controller.isAppearanceLightStatusBars = previous
        }
    }
    var stage by remember { mutableStateOf(MovieCardListStage.Cards) }
    var active by remember { mutableIntStateOf(1) }
    var picked by remember { mutableStateOf(setOf<String>()) }
    var selectedTime by remember { mutableIntStateOf(0) }
    var manual by remember { mutableStateOf(false) }
    var touching by remember { mutableStateOf(false) }
    var releaseGeneration by remember { mutableIntStateOf(0) }
    var paymentGeneration by remember { mutableIntStateOf(0) }
    var paymentJob by remember { mutableStateOf<Job?>(null) }

    fun cancelPaymentCompletion() {
        paymentGeneration += 1
        paymentJob?.cancel()
        paymentJob = null
    }

    fun takeOver() { manual = true }
    fun goPage(target: Int, scripted: Boolean = false) {
        active = target.coerceIn(0, MovieCardListMovies.lastIndex)
        if (reduced) {
            motion.veil(1f, MovieCardListMotionSpec.REDUCED_FADE_MS / 2)
            scope.launch {
                delay(MovieCardListMotionSpec.REDUCED_FADE_MS / 2)
                motion.pageJump(active)
                motion.veil(0f, MovieCardListMotionSpec.REDUCED_FADE_MS / 2)
            }
        } else {
            motion.page(active)
        }
        if (!scripted) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }
    fun openFilm() { stage = MovieCardListStage.Detail; motion.open(1f, reduced) }
    fun closeFilm() { motion.open(0f, reduced); stage = MovieCardListStage.Cards }
    fun buy() {
        if (motion.open < .5f) motion.open(1f, reduced)
        stage = MovieCardListStage.Seats
        motion.button(true); motion.cover(1f, reduced); motion.startSeatClock()
    }
    fun backFromSeats() { motion.cover(0f, reduced); motion.button(false); motion.stopSeatAndPayClocks(); stage = MovieCardListStage.Detail; picked = emptySet() }
    fun toggleSeat(id: String, scripted: Boolean = false) {
        picked = if (id in picked) picked - id else picked + id
        if (!scripted) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }
    fun reset(fade: Boolean) {
        cancelPaymentCompletion()
        if (fade) motion.nightJump(1f)
        stage = MovieCardListStage.Cards; active = 1; picked = emptySet(); selectedTime = 0; motion.reset()
        if (fade) motion.nightOut(MovieCardListMotionSpec.RESET_MS) else motion.nightOut(0)
    }
    fun pay(scripted: Boolean = false) {
        stage = MovieCardListStage.Pay; motion.payClock()
        if (!scripted) {
            cancelPaymentCompletion()
            val generation = paymentGeneration
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            paymentJob = scope.launch {
                delay(MovieCardListMotionSpec.PAY_CHECK_AT_MS + MovieCardListMotionSpec.PAY_CHECK_MS)
                if (generation != paymentGeneration || stage != MovieCardListStage.Pay) return@launch
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                delay(700)
                if (generation != paymentGeneration || stage != MovieCardListStage.Pay) return@launch
                reset(true)
            }
        }
    }

    LaunchedEffect(motion) { withFrameNanos { motion.advance(it) } }
    LaunchedEffect(motion.frameActive) {
        while (motion.frameActive) withFrameNanos { motion.advance(it) }
    }
    LaunchedEffect(autoPlay, reduced) {
        if ((reduced || !autoPlay) && !manual) {
            manual = false
            reset(false)
        }
    }
    LaunchedEffect(autoPlay, reduced, releaseGeneration, manual, touching) {
        if (!autoPlay || reduced || touching) return@LaunchedEffect
        if (manual) {
            delay(MovieCardListMotionSpec.IDLE_RESUME_MS)
            var resumeOwnsNight = true
            try {
                motion.nightIn(220)
                delay(220)
                resumeOwnsNight = false
                reset(true)
                manual = false
            } finally {
                if (resumeOwnsNight) motion.nightJump(0f)
            }
            return@LaunchedEffect
        }
        while (isActive && !manual) {
            delay(780); goPage(0, true)
            delay(700); goPage(1, true)
            delay(460); goPage(0, true)
            delay(420); goPage(1, true)
            delay(500); goPage(2, true)
            delay(1_240); goPage(1, true)
            delay(660); openFilm()
            delay(1_290); motion.cast(-72f)
            delay(900); motion.cast(0f)
            delay(700); buy()
            delay(3_650); toggleSeat("7-6", true)
            delay(600); toggleSeat("7-7", true)
            delay(1_200); pay(true)
            delay(2_000); reset(true)
            delay(700)
        }
    }

    DisposableEffect(Unit) {
        onDispose { paymentJob?.cancel() }
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize().background(MovieCardListColors.Paper)
            .pointerInput(Unit) {
                // Observe contact before children without consuming their clicks or drags.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    touching = true
                    takeOver()
                    try {
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                        } while (event.changes.any { it.pressed })
                    } finally {
                        touching = false
                        releaseGeneration++
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val density = LocalDensity.current
        val scale = min(maxWidth.value / MovieCardListLayout.Width, maxHeight.value / MovieCardListLayout.Height)
        Box(
            Modifier
                .width(MovieCardListLayout.Width.dp)
                .height(MovieCardListLayout.Height.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .pointerInput(stage) {
                    if (stage != MovieCardListStage.Cards) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        takeOver()
                        if (motion.open > .35f) {
                            var released = down
                            while (released.pressed) {
                                val event = awaitPointerEvent()
                                released = event.changes.firstOrNull { it.id == down.id } ?: break
                            }
                            releaseGeneration++
                            return@awaitEachGesture
                        }

                        val tracker = VelocityTracker()
                        tracker.resetTracking()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        val start = down.position
                        motion.dragStart = motion.page
                        var latest = down
                        var active = false

                        while (latest.pressed) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            tracker.addPosition(change.uptimeMillis, change.position)
                            val translation = change.position.x - start.x
                            // PointerInput is inside graphicsLayer, so its coordinates are already inverse-transformed.
                            if (!active && abs(translation) >= 10f * density.density) active = true
                            if (active) {
                                change.consume()
                                motion.drag(translation / density.density)
                            }
                            latest = change
                        }

                        if (active) {
                            val velocityX = tracker.calculateVelocity().x
                            val projected = motion.page -
                                (velocityX / density.density / MovieCardListLayout.Pitch) * .12f
                            goPage(projected.roundToInt())
                        }
                        releaseGeneration++
                    }
                },
        ) {
            MovieCardListScene(
                motion = motion,
                stage = stage,
                active = active,
                picked = picked,
                selectedTime = selectedTime,
                reduced = reduced,
                onCard = { index -> takeOver(); if (index == active) openFilm() else goPage(index); releaseGeneration++ },
                onBuy = { takeOver(); buy(); releaseGeneration++ },
                onSeat = { takeOver(); toggleSeat(it); releaseGeneration++ },
                onTime = { takeOver(); selectedTime = it; releaseGeneration++ },
                onPay = { takeOver(); pay(); releaseGeneration++ },
                onClose = { takeOver(); if (stage == MovieCardListStage.Seats) backFromSeats() else if (stage == MovieCardListStage.Detail) closeFilm(); releaseGeneration++ },
            )
        }
    }
}

@Composable
private fun MovieCardListScene(
    motion: MovieCardListMotion,
    stage: MovieCardListStage,
    active: Int,
    picked: Set<String>,
    selectedTime: Int,
    reduced: Boolean,
    onCard: (Int) -> Unit,
    onBuy: () -> Unit,
    onSeat: (String) -> Unit,
    onTime: (Int) -> Unit,
    onPay: () -> Unit,
    onClose: () -> Unit,
) {
    val cardsAccessible = stage == MovieCardListStage.Cards && motion.open <= .35f
    val detailAccessible = stage == MovieCardListStage.Detail && motion.open >= .35f && motion.cover <= .35f
    val seatsAccessible = stage == MovieCardListStage.Seats && motion.seatClock >= MovieCardListMotionSpec.BUILD_DELAY_MS.toFloat() + MovieCardListMotionSpec.BUILD_ROW_MS.toFloat() * .35f
    val buyAccessible = cardsAccessible || detailAccessible
    val closeAccessible = detailAccessible || seatsAccessible
    MovieCardListBackdrops(page = motion.page, open = motion.open)
    Box(
        Modifier.fillMaxSize().then(
            if (cardsAccessible) Modifier else Modifier.semantics { hideFromAccessibility() },
        ),
    ) {
        MovieCardListCards(page = motion.page, open = motion.open, enabled = cardsAccessible, onCard = onCard)
    }
    MovieCardListDetail(movie = MovieCardListMovies[active], open = motion.open, castX = motion.castX, active = detailAccessible)
    MovieCardListBuyButton(open = motion.open, hidden = motion.buttonHidden, enabled = buyAccessible, onClick = onBuy)
    MovieCardListCover(motion.cover, reduced)
    if (stage == MovieCardListStage.Seats || stage == MovieCardListStage.Pay) {
        MovieCardListSeats(clock = motion.seatClock, payClock = motion.payClock, picked = picked, selectedTime = selectedTime, active = seatsAccessible, reduced = reduced, onSeat = onSeat, onTime = onTime, onPay = onPay)
    }
    if (stage == MovieCardListStage.Pay) MovieCardListPayOverlay(motion.payClock, reduced = reduced, active = motion.payClock >= MovieCardListMotionSpec.PAY_BLACK_AT_MS.toFloat() + MovieCardListMotionSpec.PAY_BLACK_MS.toFloat() * .35f)
    MovieCardListTopControls(stage = stage, closeActive = closeAccessible, onClose = onClose)
    Box(Modifier.fillMaxSize().zIndex(60f).background(MovieCardListColors.Paper.copy(alpha = motion.veil)))
    Box(Modifier.fillMaxSize().zIndex(60f).background(MovieCardListColors.Night.copy(alpha = motion.night)))
}

@Composable
private fun MovieCardListBackdrops(page: Float, open: Float) {
    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(0.dp))) {
        Box(Modifier.fillMaxSize().background(MovieCardListColors.Paper))
        MovieCardListMovies.forEachIndexed { index, movie ->
            // The window travels with the card while its image counter-translates with it.
            val windowX = (index - page) * MovieCardListLayout.Width
            Box(Modifier.offset(windowX.dp, 0.dp).width(402.dp).height(MovieCardListLayout.BackdropHeight.dp).clip(RoundedCornerShape(0.dp))) {
                Box(Modifier.offset((-windowX).dp, 0.dp).width(402.dp).height(MovieCardListLayout.BackdropHeight.dp)) {
                    Image(painterResource(movie.backdrop), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alignment = Alignment.TopCenter)
                    MovieCardListLockup(movie)
                }
            }
        }
        val density = LocalDensity.current
        Box(Modifier.width(402.dp).height(MovieCardListLayout.BackdropHeight.dp).background(Brush.verticalGradient(listOf(Color.Transparent, MovieCardListColors.Paper), startY = with(density) { 300.dp.toPx() }, endY = with(density) { 558.dp.toPx() })))
        Box(Modifier.fillMaxSize().background(MovieCardListColors.Night.copy(alpha = open)))
    }
}

@Composable
private fun MovieCardListCover(cover: Float, reduced: Boolean) {
    val density = LocalDensity.current
    Canvas(Modifier.fillMaxSize().zIndex(20f)) {
        if (reduced) {
            drawRect(MovieCardListColors.Night.copy(alpha = cover))
            return@Canvas
        }
        val radius = hypot(size.width, size.height) * max(.0001f, cover)
        val center = Offset(
            x = with(density) { (MovieCardListLayout.Width / 2f + 60f).dp.toPx() },
            y = with(density) { (MovieCardListLayout.SheetTop + MovieCardListLayout.DetailButtonY).dp.toPx() },
        )
        drawCircle(MovieCardListColors.Night, radius = radius, center = center)
    }
}

@Composable
private fun MovieCardListLockup(movie: MovieCardListMovie) {
    when (movie.tone) {
        MovieCardListTone.Jester -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Text("MASON VALE", Modifier.offset(y = 70.7.dp), fontFamily = MovieCardListPoppins, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 2.6.sp, color = Color(0xFFC9CDCB))
            Text("DRIFT", Modifier.offset(y = 82.dp), fontFamily = MovieCardListAnton, fontSize = 78.sp, letterSpacing = (-.5).sp, color = Color(0xFFF2F2EE))
            Text("PUT ON A BRAVE FACE", Modifier.offset(y = 174.7.dp), fontFamily = MovieCardListPoppins, fontWeight = FontWeight.SemiBold, fontSize = 11.3.sp, letterSpacing = 1.6.sp, color = Color(0xFFF2F2EE))
            Text("OCTOBER 4", Modifier.offset(y = 195.dp), fontFamily = MovieCardListPoppins, fontWeight = FontWeight.SemiBold, fontSize = 11.3.sp, letterSpacing = 1.7.sp, color = Color(0xFFB9BFBC))
        }
        MovieCardListTone.Buds -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) { Text("BEST\nBUDS", Modifier.offset(y = 70.dp), fontFamily = MovieCardListAnton, fontSize = 64.sp, lineHeight = 64.sp, textAlign = TextAlign.Center, color = Color.White) }
        MovieCardListTone.Grift -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) { Text("THE GRIFT", Modifier.offset(y = 92.dp), fontFamily = MovieCardListSerif, fontSize = 42.sp, letterSpacing = 6.sp, color = Color(0xFFFFF7DC)) }
    }
}

@Composable
private fun MovieCardListCards(page: Float, open: Float, enabled: Boolean, onCard: (Int) -> Unit) {
    MovieCardListMovies.forEachIndexed { index, movie ->
        val distance = index - page
        val near = min(1f, abs(distance))
        val scale = (1f - (1f - movieCardListLerp(MovieCardListLayout.SideScale, MovieCardListLayout.SheetSideScale, open)) * near) * movieCardListLerp(1f, MovieCardListLayout.SheetPosterScale, open)
        val lift = movieCardListLerp(0f, MovieCardListLayout.SheetPosterTop - MovieCardListLayout.CardTop - MovieCardListLayout.CardHeight / 2f + (MovieCardListLayout.CardHeight / 2f - MovieCardListLayout.PosterTop) * MovieCardListLayout.SheetPosterScale, open)
        Box(
            Modifier
                .offset(x = ((MovieCardListLayout.Width - MovieCardListLayout.CardWidth) / 2f + distance * MovieCardListLayout.Pitch * movieCardListLerp(1f, MovieCardListLayout.SheetPitch, open)).dp, y = (MovieCardListLayout.CardTop + lift).dp)
                .width(MovieCardListLayout.CardWidth.dp).height(MovieCardListLayout.CardHeight.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .zIndex(10f - abs(distance) * 2f)
                .clip(RoundedCornerShape(MovieCardListLayout.CardRadius.dp))
                .shadow(18.dp, RoundedCornerShape(MovieCardListLayout.CardRadius.dp), clip = false)
                .background(MovieCardListColors.Paper.copy(alpha = 1f - open))
                .clickable(enabled = enabled) { onCard(index) },
        ) {
            Image(painterResource(movie.poster), null, Modifier.offset(MovieCardListLayout.PosterInset.dp, MovieCardListLayout.PosterTop.dp).width((MovieCardListLayout.CardWidth - 2 * MovieCardListLayout.PosterInset).dp).height(MovieCardListLayout.PosterHeight.dp).clip(RoundedCornerShape(MovieCardListLayout.PosterRadius.dp)), contentScale = ContentScale.Crop)
            MovieCardListPosterLockup(movie)
            Box(Modifier.fillMaxSize().background(MovieCardListColors.Paper.copy(alpha = min(1f, abs(distance)) * MovieCardListLayout.SideWash * (1f - open))))
        }
    }
    MovieCardListMovies.forEachIndexed { index, movie ->
        val distance = index - page
        val alpha = max(0f, 1f - abs(distance) * 1.6f) * (1f - min(1f, open * 2.5f))
        val density = LocalDensity.current
        Box(Modifier.fillMaxSize().zIndex(20f).graphicsLayer { this.alpha = alpha; translationX = with(density) { (distance * 60f).dp.toPx() } }) {
            Text(movie.title, Modifier.offset(8.dp, (MovieCardListLayout.TitleY - 18f).dp).width(394.dp), textAlign = TextAlign.Center, fontFamily = MovieCardListPoppins, fontWeight = FontWeight.Medium, fontSize = 30.sp, lineHeight = 36.sp, color = MovieCardListColors.Ink)
            MovieCardListChips(movie.genres, Modifier.offset(0.dp, (MovieCardListLayout.ChipsY - 11f).dp).width(402.dp))
            MovieCardListRating(movie, Modifier.offset(0.dp, (MovieCardListLayout.RatingY - 8f).dp).width(402.dp))
            Text("•••", Modifier.offset(0.dp, (MovieCardListLayout.DotsY - 10f).dp).width(402.dp), textAlign = TextAlign.Center, fontSize = 17.sp, letterSpacing = 2.sp, color = MovieCardListColors.Ink)
        }
    }
}

@Composable
private fun MovieCardListPosterLockup(movie: MovieCardListMovie) {
    val posterWidth = MovieCardListLayout.CardWidth - MovieCardListLayout.PosterInset * 2f
    when (movie.tone) {
        MovieCardListTone.Jester -> Box(Modifier.offset(MovieCardListLayout.PosterInset.dp, MovieCardListLayout.PosterTop.dp).width(posterWidth.dp).height(MovieCardListLayout.PosterHeight.dp).padding(end = 12.dp, bottom = 18.dp), contentAlignment = Alignment.BottomEnd) {
            Column(horizontalAlignment = Alignment.End) {
                Text("MASON VALE", fontFamily = MovieCardListPoppins, fontWeight = FontWeight.SemiBold, fontSize = 6.5.sp, letterSpacing = 1.4.sp, color = Color(0xFFE6E6E2))
                Text("DRIFT", fontFamily = MovieCardListAnton, fontSize = 34.sp, lineHeight = 38.sp, color = Color(0xFFF4F4F0))
                Text("OCTOBER 4", fontFamily = MovieCardListPoppins, fontWeight = FontWeight.SemiBold, fontSize = 6.5.sp, letterSpacing = 1.4.sp, color = Color(0xFFE6E6E2))
            }
        }
        MovieCardListTone.Buds -> Text("BEST\nBUDS", Modifier.offset(MovieCardListLayout.PosterInset.dp, (MovieCardListLayout.PosterTop + 22f).dp).width(posterWidth.dp), textAlign = TextAlign.Center, fontFamily = MovieCardListAnton, fontSize = 40.sp, lineHeight = 40.sp, color = Color.White)
        MovieCardListTone.Grift -> Text("THE GRIFT", Modifier.offset(MovieCardListLayout.PosterInset.dp, (MovieCardListLayout.PosterTop + 22f).dp).width(posterWidth.dp), textAlign = TextAlign.Center, fontFamily = MovieCardListSerif, fontSize = 22.sp, letterSpacing = 3.sp, color = Color(0xFFFFF7DC))
    }
}

@Composable
private fun MovieCardListChips(genres: List<String>, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center) { genres.forEachIndexed { index, genre ->
        Text(genre, Modifier.padding(start = if (index == 0) 0.dp else 6.5.dp).height(22.dp).clip(RoundedCornerShape(11.dp)).border(.5.dp, MovieCardListColors.Chip, RoundedCornerShape(11.dp)).background(Color.Transparent).padding(horizontal = 12.dp, vertical = 3.dp), fontFamily = MovieCardListPoppins, fontSize = 9.5.sp, color = MovieCardListColors.InkSoft)
    } }
}

@Composable
private fun MovieCardListRating(movie: MovieCardListMovie, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Text(String.format(Locale.US, "%.1f", movie.rating), fontFamily = MovieCardListPoppins, fontSize = 15.sp, color = MovieCardListColors.InkSoft, modifier = Modifier.padding(end = 7.dp))
        repeat(5) { i -> MovieCardListStar(13f, if (i < 4) MovieCardListColors.Star else MovieCardListColors.StarOff, i < 4) }
    }
}

@Composable
private fun MovieCardListStar(size: Float, color: Color, filled: Boolean) {
    Canvas(Modifier.size(size.dp)) {
        val unit = size.dp.toPx() / 24f
        val path = Path().apply {
            moveTo(12f * unit, 2f * unit); lineTo(14.9f * unit, 8.3f * unit); lineTo(21.8f * unit, 9.1f * unit)
            lineTo(16.7f * unit, 13.8f * unit); lineTo(18.1f * unit, 20.6f * unit); lineTo(12f * unit, 17.2f * unit)
            lineTo(5.9f * unit, 20.6f * unit); lineTo(7.3f * unit, 13.8f * unit); lineTo(2.2f * unit, 9.1f * unit); lineTo(9.1f * unit, 8.3f * unit); close()
        }
        drawPath(path, color, style = if (filled) androidx.compose.ui.graphics.drawscope.Fill else Stroke(1.6.dp.toPx(), join = StrokeJoin.Round))
    }
}

@Composable
private fun MovieCardListDetail(movie: MovieCardListMovie, open: Float, castX: Float, active: Boolean) {
    val castScroll = rememberScrollState()
    val density = LocalDensity.current
    LaunchedEffect(castX, density) { castScroll.scrollTo(with(density) { (-castX).dp.roundToPx() }) }
    if (open <= .001f) return
    val pHead = ((open - .35f) / .5f).coerceIn(0f, 1f)
    val pBody = ((open - .55f) / .45f).coerceIn(0f, 1f)
    val left = movieCardListLerp((402f - MovieCardListLayout.CardWidth) / 2f, MovieCardListLayout.SheetInset, open)
    val width = movieCardListLerp(MovieCardListLayout.CardWidth, 402f - MovieCardListLayout.SheetInset * 2f, open)
    val top = movieCardListLerp(MovieCardListLayout.CardTop, MovieCardListLayout.SheetTop, open)
    val sceneDensity = LocalDensity.current
    Box(Modifier.offset(left.dp, top.dp).width(width.dp).height((874f - MovieCardListLayout.SheetTop + 40f).dp).graphicsLayer { alpha = if (open > .001f) 1f else 0f; clip = true; shape = RoundedCornerShape(movieCardListLerp(MovieCardListLayout.CardRadius, MovieCardListLayout.SheetRadius, open).dp) }.background(MovieCardListColors.Paper).then(if (active) Modifier else Modifier.semantics { hideFromAccessibility() })) {
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = pHead; translationY = with(sceneDensity) { ((1f - pHead) * 18f).dp.toPx() } }) {
            Text(movie.title, Modifier.offset(0.dp, (MovieCardListLayout.DetailTitleY - 18f).dp).width(width.dp), textAlign = TextAlign.Center, fontFamily = MovieCardListPoppins, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 36.sp, color = MovieCardListColors.Ink)
            MovieCardListChips(movie.genres, Modifier.offset(0.dp, (MovieCardListLayout.DetailChipsY - 10f).dp).width(width.dp))
            MovieCardListRating(movie, Modifier.offset(0.dp, (MovieCardListLayout.DetailRatingY - 8f).dp).width(width.dp))
            Text(movie.director, Modifier.offset(0.dp, (MovieCardListLayout.DirectorY - 8f).dp).width(width.dp), textAlign = TextAlign.Center, fontFamily = MovieCardListPoppins, fontSize = 10.sp, color = MovieCardListColors.InkSoft)
        }
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = pBody; translationY = with(sceneDensity) { ((1f - pBody) * 26f).dp.toPx() } }) {
            Text("Actors", Modifier.offset(MovieCardListLayout.SheetContentX.dp, (MovieCardListLayout.ActorsLabelY - 10f).dp), fontFamily = MovieCardListPoppins, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, color = MovieCardListColors.Ink)
            Row(Modifier.offset(MovieCardListLayout.SheetContentX.dp, MovieCardListLayout.ActorsTop.dp).width((width - MovieCardListLayout.SheetContentX).dp).horizontalScroll(castScroll, enabled = active).padding(end = MovieCardListLayout.SheetContentX.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(MovieCardListLayout.ActorGap.dp)) { movie.cast.forEach { person -> Box(Modifier.width(MovieCardListLayout.ActorWidth.dp)) { Image(painterResource(person.image), person.name, Modifier.width(MovieCardListLayout.ActorWidth.dp).height(MovieCardListLayout.ActorHeight.dp).clip(RoundedCornerShape(MovieCardListLayout.ActorRadius.dp)), contentScale = ContentScale.Crop); Text(person.name, Modifier.offset(y = 118.dp).width(MovieCardListLayout.ActorWidth.dp), textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis, fontFamily = MovieCardListPoppins, fontSize = 9.5.sp, color = MovieCardListColors.InkSoft) } }
            }
            Text("Introduction", Modifier.offset(MovieCardListLayout.SheetContentX.dp, (MovieCardListLayout.IntroLabelY - 10f).dp), fontFamily = MovieCardListPoppins, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, color = MovieCardListColors.Ink)
            Text(movie.intro, Modifier.offset(MovieCardListLayout.SheetContentX.dp, MovieCardListLayout.IntroTop.dp).width((width - MovieCardListLayout.SheetContentX * 2).dp), maxLines = 3, overflow = TextOverflow.Ellipsis, fontFamily = MovieCardListPoppins, fontSize = 10.5.sp, lineHeight = 16.sp, color = MovieCardListColors.InkMute)
        }
    }
}

@Composable
private fun MovieCardListBuyButton(open: Float, hidden: Float, enabled: Boolean, onClick: () -> Unit) {
    val press = remember { Animatable(0f) }
    val pressScope = androidx.compose.runtime.rememberCoroutineScope()
    val width = movieCardListLerp(MovieCardListLayout.ButtonWidth, MovieCardListLayout.DetailButtonWidth, open)
    val centerY = movieCardListLerp(MovieCardListLayout.ButtonY, MovieCardListLayout.SheetTop + MovieCardListLayout.DetailButtonY, open)
    val height = movieCardListLerp(MovieCardListLayout.ButtonHeight, MovieCardListLayout.DetailButtonHeight, open)
    Text("BUY TICKET", Modifier.offset(((402f - width) / 2f).dp, (centerY - height / 2f).dp).width(width.dp).height(height.dp).alpha(1f - hidden).graphicsLayer { val scale = 1f - (1f - MovieCardListMotionSpec.PRESS_SCALE) * press.value; scaleX = scale; scaleY = scale }.clip(RoundedCornerShape(4.dp)).background(MovieCardListColors.Button).pointerInput(enabled) {
        if (!enabled) return@pointerInput
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            pressScope.launch { press.animateTo(1f, spring(dampingRatio = 18f / (2f * sqrt(320f)), stiffness = 320f)) }
            var latest = down
            while (latest.pressed) {
                val event = awaitPointerEvent()
                latest = event.changes.firstOrNull { it.id == down.id } ?: break
            }
            pressScope.launch { press.animateTo(0f, spring(dampingRatio = 18f / (2f * sqrt(320f)), stiffness = 320f)) }
        }
    }.clickable(enabled = enabled, role = Role.Button, onClick = onClick).then(if (enabled) Modifier else Modifier.semantics { hideFromAccessibility() }).padding(top = 16.dp), textAlign = TextAlign.Center, fontFamily = MovieCardListPoppins, fontWeight = FontWeight.SemiBold, fontSize = 16.3.sp, letterSpacing = .4.sp, color = Color.White)
}

@Composable
private fun MovieCardListSeats(clock: Float, payClock: Float, picked: Set<String>, selectedTime: Int, active: Boolean, reduced: Boolean, onSeat: (String) -> Unit, onTime: (Int) -> Unit, onPay: () -> Unit) {
    val entrance = if (reduced) movieCardListRamp(clock, 0, MovieCardListMotionSpec.REDUCED_FADE_MS) else movieCardListRamp(clock, MovieCardListMotionSpec.BUILD_DELAY_MS + MovieCardListMotionSpec.BUILD_CHROME_AT_MS, MovieCardListMotionSpec.BUILD_CHROME_MS)
    val gridAlpha = 1f - movieCardListRamp(payClock, 0, MovieCardListMotionSpec.PAY_BLACK_AT_MS)
    val blackFade = 1f - movieCardListRamp(payClock, MovieCardListMotionSpec.PAY_BLACK_AT_MS, MovieCardListMotionSpec.PAY_BLACK_MS)
    val chrome = entrance * gridAlpha
    val density = LocalDensity.current
    Box(Modifier.fillMaxSize().zIndex(30f).then(if (active) Modifier else Modifier.semantics { hideFromAccessibility() })) {
        MovieCardListCinemaScreen(clock, gridAlpha, reduced)
        MovieCardListSeatMap(clock, gridAlpha, picked, active, reduced, onSeat)
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = chrome; translationY = with(density) { (if (reduced) 0f else (1f - entrance) * 16f).dp.toPx() } }) {
            MovieCardListLegend(); MovieCardListSchedule(selectedTime, active, onTime)
        }
        val shrink = if (reduced) 0f else movieCardListRamp(payClock, 0, MovieCardListMotionSpec.PAY_SHRINK_MS)
        val payWidth = movieCardListLerp(MovieCardListLayout.PayWidth, MovieCardListLayout.PayHeight, shrink)
        val total = picked.size * 12
        val labelAlpha = 1f - movieCardListRamp(payClock, 0, (MovieCardListMotionSpec.PAY_SHRINK_MS * .5f).toLong())
        Text(if (total == 0) "PAY" else "PAY $$total", Modifier.offset(((402f - payWidth) / 2f).dp, (MovieCardListLayout.PayY - MovieCardListLayout.PayHeight / 2f).dp).width(payWidth.dp).height(MovieCardListLayout.PayHeight.dp).graphicsLayer { alpha = entrance * blackFade; translationY = with(density) { (if (reduced) 0f else (1f - entrance) * 20f).dp.toPx() } }.clip(RoundedCornerShape(movieCardListLerp(12f, MovieCardListLayout.PayHeight / 2f, shrink).dp)).background(Brush.horizontalGradient(listOf(MovieCardListColors.Red, MovieCardListColors.RedHot))).clickable(enabled = active, role = Role.Button, onClick = onPay).padding(top = 16.dp), textAlign = TextAlign.Center, fontFamily = MovieCardListPoppins, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = .8.sp, color = Color.White.copy(alpha = labelAlpha))
    }
}

@Composable
private fun MovieCardListCinemaScreen(clock: Float, alpha: Float, reduced: Boolean) {
    val footageTime = clock - MovieCardListMotionSpec.BUILD_DELAY_MS
    val film = if (reduced) 0f else movieCardListRamp(footageTime, MovieCardListMotionSpec.FOOTAGE_IN_AT_MS, MovieCardListMotionSpec.FOOTAGE_IN_MS) * (1f - movieCardListRamp(footageTime, MovieCardListMotionSpec.FOOTAGE_OUT_AT_MS, MovieCardListMotionSpec.FOOTAGE_OUT_MS))
    val pushIn = if (reduced) 1f else 1f + .08f * movieCardListRamp(footageTime - MovieCardListMotionSpec.FOOTAGE_IN_AT_MS, 0, MovieCardListMotionSpec.FOOTAGE_DRIFT_MS)
    val glint = if (reduced) movieCardListRamp(clock, 0, MovieCardListMotionSpec.REDUCED_FADE_MS) else 1f - film
    val screenBand = remember { GenericShape { size, _ -> val unit = size.width / 350f; moveTo(0f, 0f); quadraticTo(size.width / 2f, 26f * unit, size.width, 0f); lineTo(size.width - 18f * unit, size.height); quadraticTo(size.width / 2f, size.height - 14.3f * unit, 18f * unit, size.height); close() } }
    Box(Modifier.offset(26.dp, MovieCardListLayout.ScreenTop.dp).width(350.dp).height(MovieCardListLayout.ScreenHeight.dp).alpha(alpha).clip(screenBand)) {
        Image(painterResource(R.drawable.moviecardlist_screen_jester), null, Modifier.offset(y = (-6).dp).width(350.dp).height(140.dp).graphicsLayer { scaleX = pushIn; scaleY = pushIn }.alpha(film), contentScale = ContentScale.Crop)
        Canvas(Modifier.fillMaxSize().alpha(glint)) {
            val glint = Path().apply {
                moveTo(6.dp.toPx(), 44.dp.toPx())
                quadraticTo(size.width / 2f, 20.dp.toPx(), size.width - 6.dp.toPx(), 44.dp.toPx())
            }
            drawPath(glint, Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0f), Color.White.copy(alpha = .9f), Color.White.copy(alpha = .1f))), style = Stroke(2.4.dp.toPx(), cap = StrokeCap.Round))
        }
    }
}

@Composable
private fun MovieCardListSeatMap(clock: Float, alpha: Float, picked: Set<String>, enabled: Boolean, reduced: Boolean, onSeat: (String) -> Unit) {
    val cells = remember { movieCardListSeatCells() }
    Box(Modifier.fillMaxSize().alpha(alpha)) { cells.forEach { cell ->
        val rowP = if (reduced) movieCardListRamp(clock, 0, MovieCardListMotionSpec.REDUCED_FADE_MS) else movieCardListRamp(clock, MovieCardListMotionSpec.BUILD_DELAY_MS + cell.row * MovieCardListMotionSpec.BUILD_ROW_STAGGER_MS, MovieCardListMotionSpec.BUILD_ROW_MS)
        MovieCardListSeatButton(cell, rowP, cell.id in picked, enabled, reduced, onSeat)
    } }
}

@Composable
private fun MovieCardListSeatButton(cell: MovieCardListSeatCell, rowProgress: Float, selected: Boolean, enabled: Boolean, reduced: Boolean, onSeat: (String) -> Unit) {
    val pop = remember { Animatable(1f) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val color = if (cell.taken) MovieCardListColors.SeatTaken else if (selected) MovieCardListColors.Red else MovieCardListColors.SeatFree
    MovieCardListSeatGlyph(
        Modifier
            .offset(cell.x.dp, (MovieCardListLayout.GridTop + cell.row * MovieCardListLayout.SeatPitchY + (if (reduced) 0f else (1f - rowProgress) * 14f)).dp)
            .size(MovieCardListLayout.Seat.dp)
            .alpha(rowProgress)
            .graphicsLayer { scaleX = pop.value; scaleY = pop.value }
            .clickable(enabled = enabled && !cell.taken, role = Role.Button, onClick = {
                if (!reduced) scope.launch {
                    pop.snapTo(.7f)
                    pop.animateTo(1f, spring(dampingRatio = MovieCardListMotionSpec.seatPop.damping / 20f, stiffness = MovieCardListMotionSpec.seatPop.stiffness))
                }
                onSeat(cell.id)
            })
            .semantics { contentDescription = "Seat ${cell.id}"; this.selected = selected },
        color,
    )
}

private data class MovieCardListSeatCell(val id: String, val row: Int, val x: Float, val taken: Boolean)
private fun movieCardListSeatCells(): List<MovieCardListSeatCell> {
    val all = mutableListOf<MovieCardListSeatCell>(); val columns = MovieCardListSeatRows[0].replace(" ", "").length
    val gridWidth = columns * MovieCardListLayout.SeatPitchX - (MovieCardListLayout.SeatPitchX - MovieCardListLayout.Seat) + MovieCardListLayout.Aisle
    MovieCardListSeatRows.forEachIndexed { r, row -> var x = (402f - gridWidth) / 2f; row.forEachIndexed { c, char -> if (char == ' ') x += MovieCardListLayout.Aisle else { all += MovieCardListSeatCell("$r-$c", r, x, char == 'T'); x += MovieCardListLayout.SeatPitchX } } }; return all
}

@Composable
private fun MovieCardListSeatGlyph(modifier: Modifier, color: Color) {
    Canvas(modifier) {
        val u = size.width / 24f
        val back = Path().apply { moveTo(5.5f * u, 10f * u); lineTo(5.5f * u, 7f * u); cubicTo(5.5f * u, 4.8f * u, 7.3f * u, 3f * u, 9.5f * u, 3f * u); lineTo(14.5f * u, 3f * u); cubicTo(16.7f * u, 3f * u, 18.5f * u, 4.8f * u, 18.5f * u, 7f * u); lineTo(18.5f * u, 10f * u); close() }
        val cushion = Path().apply { moveTo(3f * u, 11.5f * u); cubicTo(3f * u, 10.4f * u, 4f * u, 9.5f * u, 5f * u, 9.5f * u); cubicTo(6.1f * u, 9.5f * u, 7f * u, 10.4f * u, 7f * u, 11.5f * u); lineTo(7f * u, 15.5f * u); lineTo(17f * u, 15.5f * u); lineTo(17f * u, 11.5f * u); cubicTo(17f * u, 10.4f * u, 17.9f * u, 9.5f * u, 19f * u, 9.5f * u); cubicTo(20f * u, 9.5f * u, 21f * u, 10.4f * u, 21f * u, 11.5f * u); lineTo(21f * u, 19f * u); cubicTo(21f * u, 20.1f * u, 20.1f * u, 21f * u, 19f * u, 21f * u); lineTo(5f * u, 21f * u); cubicTo(3.9f * u, 21f * u, 3f * u, 20.1f * u, 3f * u, 19f * u); close() }
        drawPath(back, color); drawPath(cushion, color)
    }
}

@Composable
private fun MovieCardListLegend() {
    Row(Modifier.offset(64.dp, (MovieCardListLayout.LegendY - 8f).dp).width(274.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween) { listOf("Available" to MovieCardListColors.SeatFree, "Taken" to MovieCardListColors.SeatTaken, "Selected" to MovieCardListColors.Red).forEach { (name, color) -> Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(6.dp).clip(CircleShape).background(color)); Text(name, Modifier.padding(start = 6.dp), fontFamily = MovieCardListPoppins, fontSize = 9.5.sp, color = MovieCardListColors.Legend) } } }
}

@Composable
private fun MovieCardListSchedule(selected: Int, enabled: Boolean, onTime: (Int) -> Unit) {
    val scroll = rememberScrollState()
    Row(Modifier.offset(64.dp, (MovieCardListLayout.DateY - 14f).dp).width(338.dp).horizontalScroll(scroll, enabled = enabled), verticalAlignment = Alignment.CenterVertically) {
        Text("January 18 ▼", fontFamily = MovieCardListPoppins, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = Color.White, modifier = Modifier.padding(end = 22.dp))
        MovieCardListTimes.forEachIndexed { index, time -> val timeSelected = index == selected; Text(time, Modifier.padding(end = 14.dp).height(22.dp).clip(RoundedCornerShape(2.dp)).background(if (timeSelected) MovieCardListColors.Red else Color.Transparent).clickable(enabled = enabled) { onTime(index) }.padding(horizontal = 8.dp, vertical = 3.dp).semantics { this.selected = timeSelected }, fontFamily = MovieCardListPoppins, fontWeight = FontWeight.Medium, fontSize = 11.5.sp, color = Color.White) }
    }
}

@Composable
private fun MovieCardListPayOverlay(clock: Float, active: Boolean, reduced: Boolean) {
    val black = movieCardListRamp(clock, MovieCardListMotionSpec.PAY_BLACK_AT_MS, MovieCardListMotionSpec.PAY_BLACK_MS)
    val face = if (reduced) 0f else movieCardListRamp(clock, MovieCardListMotionSpec.PAY_FACE_AT_MS, 120) * (1f - movieCardListRamp(clock, MovieCardListMotionSpec.PAY_SPIN_AT_MS - 80, 120))
    val spinner = if (reduced) 0f else movieCardListRamp(clock, MovieCardListMotionSpec.PAY_SPIN_AT_MS - 60, 120) * (1f - movieCardListRamp(clock, MovieCardListMotionSpec.PAY_CHECK_AT_MS - 40, 100))
    val check = movieCardListRamp(clock, MovieCardListMotionSpec.PAY_CHECK_AT_MS - 40, 80)
    Canvas(Modifier.fillMaxSize().zIndex(40f).alpha(black).then(if (active) Modifier.semantics { contentDescription = "Payment processing" } else Modifier.semantics { hideFromAccessibility() })) {
        drawRect(MovieCardListColors.Night)
        val center = Offset(size.width / 2f, size.height / 2f)
        val unit = 54.dp.toPx() / 54f
        // The same eight SVG strokes as RN's Face ID mark, revealed one per 30 ms.
        val facePaths = listOf(
            Path().apply { moveTo(6f * unit, 16f * unit); lineTo(6f * unit, 10f * unit); quadraticTo(6f * unit, 6f * unit, 10f * unit, 6f * unit); lineTo(16f * unit, 6f * unit) },
            Path().apply { moveTo(38f * unit, 6f * unit); lineTo(44f * unit, 6f * unit); quadraticTo(48f * unit, 6f * unit, 48f * unit, 10f * unit); lineTo(48f * unit, 16f * unit) },
            Path().apply { moveTo(48f * unit, 38f * unit); lineTo(48f * unit, 44f * unit); quadraticTo(48f * unit, 48f * unit, 44f * unit, 48f * unit); lineTo(38f * unit, 48f * unit) },
            Path().apply { moveTo(16f * unit, 48f * unit); lineTo(10f * unit, 48f * unit); quadraticTo(6f * unit, 48f * unit, 6f * unit, 44f * unit); lineTo(6f * unit, 38f * unit) },
            Path().apply { moveTo(19f * unit, 20f * unit); lineTo(19f * unit, 24f * unit) },
            Path().apply { moveTo(35f * unit, 20f * unit); lineTo(35f * unit, 24f * unit) },
            Path().apply { moveTo(27f * unit, 20f * unit); lineTo(27f * unit, 30f * unit); lineTo(24f * unit, 30f * unit) },
            Path().apply { moveTo(20f * unit, 36f * unit); cubicTo(24f * unit, 39.4f * unit, 30f * unit, 39.4f * unit, 34f * unit, 36f * unit) },
        )
        if (face > 0f) {
            facePaths.forEachIndexed { index, path ->
                val progress = movieCardListRamp(clock, MovieCardListMotionSpec.PAY_FACE_AT_MS + index * 30L, (MovieCardListMotionSpec.PAY_FACE_MS * .6f).toLong())
                if (progress > 0f) {
                    val measure = PathMeasure().also { it.setPath(path, false) }
                    val shown = Path()
                    measure.getSegment(0f, measure.length * progress, shown, true)
                    withTransform({ translate(center.x - 27f * unit, center.y - 27f * unit) }) {
                        drawPath(shown, Color.White.copy(alpha = face), style = Stroke(2.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                }
            }
        }
        val r = 18.dp.toPx()
        if (spinner > 0f) withTransform({ rotate((clock - MovieCardListMotionSpec.PAY_SPIN_AT_MS) * .9f, center) }) {
            drawArc(Color.White.copy(alpha = spinner), -90f, 280f, false, center - Offset(r, r), Size(r * 2, r * 2), style = Stroke(2.6.dp.toPx(), cap = StrokeCap.Round))
        }
        if (check > 0f) {
            drawArc(Color.White.copy(alpha = check), -90f, if (reduced) 360f else 360f * movieCardListRamp(clock, MovieCardListMotionSpec.PAY_CHECK_AT_MS, (MovieCardListMotionSpec.PAY_CHECK_MS * .6f).toLong()), false, center - Offset(r, r), Size(r * 2, r * 2), style = Stroke(2.6.dp.toPx(), cap = StrokeCap.Round))
            val tick = Path().apply { moveTo(center.x - 7.dp.toPx(), center.y); lineTo(center.x - 2.dp.toPx(), center.y + 5.dp.toPx()); lineTo(center.x + 7.dp.toPx(), center.y - 5.dp.toPx()) }
            val measure = PathMeasure().also { it.setPath(tick, false) }
            val shown = Path()
            measure.getSegment(0f, if (reduced) measure.length else measure.length * movieCardListRamp(clock, MovieCardListMotionSpec.PAY_CHECK_AT_MS + (MovieCardListMotionSpec.PAY_CHECK_MS * .35f).toLong(), (MovieCardListMotionSpec.PAY_CHECK_MS * .65f).toLong()), shown, true)
            drawPath(shown, Color.White.copy(alpha = check), style = Stroke(2.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

@Composable
private fun MovieCardListTopControls(stage: MovieCardListStage, closeActive: Boolean, onClose: () -> Unit) {
    Box(Modifier.offset((MovieCardListLayout.IconX - 22f).dp, (MovieCardListLayout.IconY - 22f).dp).size(44.dp).zIndex(50f).clickable(enabled = closeActive, role = Role.Button, onClick = onClose).semantics { contentDescription = if (stage == MovieCardListStage.Seats) "Back to film" else "Close" }.then(if (closeActive) Modifier else Modifier.semantics { hideFromAccessibility() }), contentAlignment = Alignment.Center) { Canvas(Modifier.size(22.4.dp)) { drawLine(Color.White, Offset(size.width * .2f, size.height * .2f), Offset(size.width * .8f, size.height * .8f), 2.2.dp.toPx(), StrokeCap.Round); drawLine(Color.White, Offset(size.width * .8f, size.height * .2f), Offset(size.width * .2f, size.height * .8f), 2.2.dp.toPx(), StrokeCap.Round) } }
    Box(Modifier.offset((MovieCardListLayout.TargetX - 22f).dp, (MovieCardListLayout.TargetY - 22f).dp).size(44.dp).zIndex(50f).semantics { contentDescription = "Nearby cinemas" }, contentAlignment = Alignment.Center) { Canvas(Modifier.size(26.2.dp)) { val c = Offset(size.width / 2f, size.height / 2f); drawCircle(Color.White, size.width * .4f, c, style = Stroke(2.dp.toPx())); drawCircle(Color.White, size.width * .15f, c) } }
}
