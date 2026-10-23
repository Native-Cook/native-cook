package com.ngocdevv.nativecook.blocks.lists.walletcardstack

import android.graphics.BitmapFactory
import android.graphics.Paint
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.TransformOrigin
import com.ngocdevv.nativecook.R
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Exact colour tokens from the approved React Native reference. */
private object WalletCardStackColors {
    val screen = Color(0xFFF0F0F0)
    val ink = Color(0xFF1D1D1F)
    val inkSoft = Color(0xFF6B6B6F)
    val muted = Color(0xFF8E8E93)
    val white = Color(0xFFFFFFFF)
    val button = Color(0xFFFBFBFB)
    val tile = Color(0xFFE4E4E4)
    val divider = Color(0xFFC4C4C4)
    val accent = Color(0xFF5A46E0)
    val red = Color(0xFFCF2E2E)
    val green = Color(0xFF2E8B3A)
    val fabTop = Color(0xFF7B80FA)
    val fabBottom = Color(0xFF5C4BE3)
}

private fun walletColor(hex: String): Color {
    val value = hex.removePrefix("#").toLong(16).toInt()
    return if (hex.length == 7) Color(value or 0xFF000000.toInt()) else Color(value)
}

private const val WalletCardStackAvatarZoom = 1f

/** Layout and artwork tokens mirror theme.ts, including legacy colour tokens kept for auditability. */
private object WalletCardStackLayout {
    const val screenWidth = 402f
    const val safeTop = 62f

    const val headerY = 102f
    const val headerButton = 48f
    const val headerInset = 20f
    const val headerTitleY = 93f
    const val headerSubtitleY = 113f

    const val walletLeft = 37.5f
    const val walletTop = 201f
    const val walletWidth = 332f
    const val walletHeight = 195f
    const val canvasPad = 24f
    const val cardsTouchTop = -50f
    const val flapRadius = 26f
    val rimTop = Color(0xFFB994E6)
    val flapTop = Color(0xFF7C5AB4)
    val flapMid = Color(0xFF5A3A90)
    val flapBottom = Color(0xFF2A1462)
    const val pocketSide = 30f
    const val pocketDip = 45f
    const val pocketRadius = 34f
    const val cornerRadius = 12f
    const val flapShort = 24f
    val flapStops = floatArrayOf(0f, 0.1f, 0.5f, 1f)
    val flapColors = arrayOf("#6E4FA9", "#61429C", "#4E3289", "#3B257A").map(::walletColor)
    val pocketStops = floatArrayOf(0f, 0.03f, 0.1f, 0.17f, 0.25f, 0.33f, 0.4f, 0.48f, 0.55f, 0.63f, 0.7f, 0.78f, 0.86f, 1f)
    val pocketColors = arrayOf(
        "#563788", "#563788", "#B48DDC", "#AD85D6", "#9F79CB", "#8F6ABE", "#7E59AC",
        "#714EA0", "#684596", "#61408F", "#583A88", "#4C3180", "#3F2878", "#3C2782",
    ).map(::walletColor)
    val pocketGlow = Color(0x21FFFFFF)
    val pocketLeft = Color(0xFFA37DCF)
    val pocketMid = Color(0xFF8361B4)
    val pocketRight = Color(0xFF33207A)
    val pocketFoot = Color(0xFF24104F)
    const val pocketInset = 2f
    const val pocketBottom = 1f
    const val pocketGlowCx = 0.35f
    const val pocketGlowCy = 0.66f
    const val pocketGlowRadius = 0.42f
    const val stitchInset = 6.5f
    const val stitchRise = 6f
    val stitch = Color(0x8CDEC8FC)
    const val shadowY = 202f

    const val backCardX = 38f
    const val backCardY = -43f
    const val backCardWidth = 255f
    const val backCardHeight = 150f
    const val backCardRadius = 14f
    const val frontCardX = 21f
    const val frontCardY = -32f
    const val frontCardWidth = 290f
    const val frontCardHeight = 180f
    const val frontCardRadius = 20f
    const val cardPadX = 18f
    const val cardNameY = -2f
    const val cardNumberY = 18f
    const val cardBrandY = 2f
    const val cardValidY = 22f

    const val labelY = 80f
    const val balanceY = 105f
    const val pillY = 130.5f
    const val pillX = 24f
    const val pillW = 142f
    const val pillH = 31f
    const val iconY = 145.5f
    const val iconSize = 32f
    val iconXs = floatArrayOf(248f, 292f)

    const val topUpTitleY = 431f
    const val topUpRowY = 487f
    const val topUpAvatar = 53f
    const val topUpAddX = 47f
    const val topUpDividerX = 92f
    const val topUpLabelY = 527f
    val topUpXs = floatArrayOf(139f, 211f, 283f, 355f)

    const val latestTitleY = 580f
    const val latestTop = 615f
    const val latestWidth = 103f
    const val latestHeight = 142f
    const val latestRadius = 18f
    val latestXs = floatArrayOf(25f, 149f, 273f)
    const val latestAvatar = 48f

    const val tabTop = 779f
    const val tabIconY = 803f
    const val tabLabelY = 827f
    val tabXs = floatArrayOf(38f, 111f, 279f, 358f)
    const val fab = 72f
    const val fabY = 795f
}

/** Motion values mirror motion.ts; Android uses the same durations and cubic bezier control points. */
private object WalletCardStackMotionSpec {
    const val lift = 44f
    const val backLift = 7f
    const val tilt = -1.8f
    const val limit = 62f
    const val tapLift = 38f
    const val springDuration = 400L
    const val pullDamping = 1f
    const val dropDamping = 0.8f
    const val pressScale = 0.97f
    const val pressIn = 120
    const val swapTurn = 250L
    const val squishScaleY = 0.982f
    const val squishScaleX = 1.008f
    const val charStagger = 30L
    const val charDuration = 200L
    const val hideRise = 7f
    const val fadeMs = 180L
    const val loopMs = 6_000L
    const val resumeAfterMs = 3_500L
    const val antic = 400f
    const val draw = 600f
    const val swayFrom = 900f
    const val swayTo = 3_700f
    const val fall = 3_800f
    const val impact = 4_160f
    const val dip = 4f
    const val loopLean = -2.2f
    const val sway = 7f
    const val backShare = 0.12f
    const val backLag = 60f
    const val drawResponse = 0.55f
    const val drawDamping = 0.8f
    const val impactVelocity = -160f
    const val impactResponse = 0.42f
    const val impactDamping = 0.55f
    const val squishVelocity = 30f
    const val loopSquishResponse = 0.3f
    const val loopSquishDamping = 0.5f
    const val reducedHideAt = 1_500f
    const val reducedShowAt = 4_000f
    const val turnPerDx = 1f / 6f
    const val turnMax = 10f
    const val perspective = 800f
    const val sheen = 0.32f
    const val sheenTravel = 0.45f
    const val cardShadow = 0.32f
    const val easeOutX1 = 0.23f
    const val easeOutY1 = 1f
    const val easeOutX2 = 0.32f
    const val easeOutY2 = 1f
    const val easeInOutX1 = 0.77f
    const val easeInOutY1 = 0f
    const val easeInOutX2 = 0.175f
    const val easeInOutY2 = 1f
    const val squishDelay = 110L
    const val squishIn = 90L
    const val swapFrontDip = -9f
    const val swapFrontIn = 130L
    const val swapSquish = 0.6f
    const val swapSquishIn = 110L
    const val touchSlop = 6f
}

const val LOOP_MS: Long = WalletCardStackMotionSpec.loopMs

private data class WalletCardStackPerson(val name: String, val image: Int, val tint: Color)

private data class WalletCardStackPayment(
    val person: WalletCardStackPerson,
    val date: String,
    val amount: String,
    val incoming: Boolean,
)

private val WalletCardStackHolder = Triple("Rohan Kesari", "5678", "ZIVA")
private const val WalletCardStackValid = "05/29"
private const val WalletCardStackBalanceLabel = "Total Balance"
private const val WalletCardStackBalance = "25,867.40"
private const val WalletCardStackCurrency = "SAR"

private val WalletCardStackPeople = listOf(
    WalletCardStackPerson("Warren", R.drawable.walletcardstack_warren, Color(0xFFA8E6C6)),
    WalletCardStackPerson("Edwards", R.drawable.walletcardstack_edwards, Color(0xFFA7CCF8)),
    WalletCardStackPerson("Ingrid", R.drawable.walletcardstack_ingrid, Color(0xFFF7DC74)),
    WalletCardStackPerson("Sofia", R.drawable.walletcardstack_sofia, Color(0xFFD8C4F4)),
)

private val WalletCardStackQuick = WalletCardStackPeople
private val WalletCardStackLatest = listOf(
    WalletCardStackPayment(WalletCardStackPeople[3], "April 10,2025", "-36.95 SAR", false),
    WalletCardStackPayment(WalletCardStackPeople[1], "April 05,2025", "-5.00 SAR", false),
    WalletCardStackPayment(WalletCardStackPeople[0], "March 25,2025", "+175.75 SAR", true),
)
private val WalletCardStackEarlier = listOf(
    WalletCardStackPayment(WalletCardStackPeople[2], "March 18,2025", "-120.00 SAR", false),
    WalletCardStackPayment(WalletCardStackPeople[0], "March 12,2025", "+48.50 SAR", true),
    WalletCardStackPayment(WalletCardStackPeople[3], "March 02,2025", "-12.75 SAR", false),
)

private val WalletCardStackEaseOut = CubicBezierEasing(
    WalletCardStackMotionSpec.easeOutX1,
    WalletCardStackMotionSpec.easeOutY1,
    WalletCardStackMotionSpec.easeOutX2,
    WalletCardStackMotionSpec.easeOutY2,
)
private val WalletCardStackEaseInOut = CubicBezierEasing(
    WalletCardStackMotionSpec.easeInOutX1,
    WalletCardStackMotionSpec.easeInOutY1,
    WalletCardStackMotionSpec.easeInOutX2,
    WalletCardStackMotionSpec.easeInOutY2,
)

private fun walletClamp(value: Float, minValue: Float = 0f, maxValue: Float = 1f) =
    value.coerceIn(minValue, maxValue)

private fun walletRubber(distance: Float, limit: Float): Float {
    val sign = if (distance < 0f) -1f else 1f
    val absolute = abs(distance)
    return sign * limit * (1f - 1f / (absolute / limit + 1f))
}

private data class WalletCardStackPose(
    val lift: Float,
    val back: Float,
    val lean: Float,
    val turn: Float,
    val squish: Float,
    val hidden: Float,
)

private fun walletSpringAt(
    elapsed: Float,
    displacement: Float,
    velocity: Float,
    response: Float,
    damping: Float,
): Float {
    if (elapsed <= 0f) return displacement
    val omega = (2f * kotlin.math.PI.toFloat()) / response
    val envelope = exp(-damping * omega * elapsed)
    if (damping >= 1f) {
        return envelope * (displacement + (velocity + omega * displacement) * elapsed)
    }
    val damped = omega * sqrt(1f - damping * damping)
    return envelope * (
        displacement * cos(damped * elapsed) +
            ((velocity + damping * omega * displacement) / damped) * sin(damped * elapsed)
        )
}

private fun walletLoopLift(input: Float): Float {
    val motion = WalletCardStackMotionSpec
    val period = motion.loopMs.toFloat()
    val time = ((input % period) + period) % period
    if (time < motion.antic) return 0f
    if (time < motion.draw) {
        val progress = (time - motion.antic) / (motion.draw - motion.antic)
        return -motion.dip * WalletCardStackEaseInOut.transform(progress)
    }
    fun drawn(value: Float): Float {
        return motion.lift + walletSpringAt(
            elapsed = (value - motion.draw) / 1_000f,
            displacement = -motion.dip - motion.lift,
            velocity = 0f,
            response = motion.drawResponse,
            damping = motion.drawDamping,
        )
    }
    if (time < motion.fall) return drawn(time)
    if (time < motion.impact) {
        val progress = (time - motion.fall) / (motion.impact - motion.fall)
        return drawn(motion.fall) * (1f - progress * progress)
    }
    return walletSpringAt(
        elapsed = (time - motion.impact) / 1_000f,
        displacement = 0f,
        velocity = motion.impactVelocity,
        response = motion.impactResponse,
        damping = motion.impactDamping,
    )
}

private fun walletPose(input: Float, reduced: Boolean): WalletCardStackPose {
    val motion = WalletCardStackMotionSpec
    val period = motion.loopMs.toFloat()
    val time = ((input % period) + period) % period
    if (reduced) {
        fun fade(start: Float) = ((time - start) / motion.fadeMs).coerceIn(0f, 1f)
        return WalletCardStackPose(0f, 0f, 0f, 0f, 0f, fade(motion.reducedHideAt) - fade(motion.reducedShowAt))
    }
    val lift = walletLoopLift(time)
    val turn = if (time > motion.swayFrom && time < motion.swayTo) {
        motion.sway * sin(
            (2f * kotlin.math.PI.toFloat() * (time - motion.swayFrom)) /
                (motion.swayTo - motion.swayFrom),
        )
    } else {
        0f
    }
    val squish = when {
        time < motion.draw -> 0.3f * max(0f, -lift / motion.dip)
        time > motion.impact -> max(
            -0.4f,
            walletSpringAt(
                elapsed = (time - motion.impact) / 1_000f,
                displacement = 0f,
                velocity = motion.squishVelocity,
                response = motion.loopSquishResponse,
                damping = motion.loopSquishDamping,
            ),
        )
        else -> 0f
    }
    return WalletCardStackPose(
        lift = lift,
        back = motion.backShare * walletLoopLift(max(0f, time - motion.backLag)),
        lean = motion.loopLean * min(1.1f, max(0f, lift) / motion.lift),
        turn = turn,
        squish = squish,
        hidden = 0f,
    )
}

private fun walletMediumHaptic(view: android.view.View) {
    view.performHapticFeedback(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS,
    )
}

private enum class WalletCardStackSegmentKind { Hold, Timing, Spring }

private data class WalletCardStackSegment(
    val kind: WalletCardStackSegmentKind,
    val to: Float,
    val durationMs: Long,
    val easing: CubicBezierEasing = WalletCardStackEaseOut,
    val dampingRatio: Float = WalletCardStackMotionSpec.dropDamping,
    val velocity: Float = 0f,
)

/** A deterministic, interruptible track equivalent to Reanimated timing/spring sequences. */
private class WalletCardStackTrack(initial: Float) {
    private var base = initial
    private var startedAtNanos = 0L
    private var segments: List<WalletCardStackSegment> = emptyList()

    fun value(nowNanos: Long): Float {
        if (segments.isEmpty()) return base
        var elapsed = ((nowNanos - startedAtNanos).coerceAtLeast(0L) / 1_000_000L)
        var from = base
        for (segment in segments) {
            if (segment.durationMs <= 0L) {
                from = segment.to
                continue
            }
            if (elapsed < segment.durationMs) {
                val progress = elapsed.toDouble() / segment.durationMs.toDouble()
                return from + (segment.to - from) * walletSegmentProgress(segment, progress, from).toFloat()
            }
            elapsed -= segment.durationMs
            from = segment.to
        }
        return from
    }

    fun start(nowNanos: Long, sequence: List<WalletCardStackSegment>) {
        base = value(nowNanos)
        startedAtNanos = nowNanos
        segments = sequence
    }

    fun jump(nowNanos: Long, value: Float) {
        base = value
        startedAtNanos = nowNanos
        segments = emptyList()
    }

    fun isRunning(nowNanos: Long): Boolean {
        val total = segments.sumOf { it.durationMs }
        return segments.isNotEmpty() && nowNanos < startedAtNanos + total * 1_000_000L
    }

    private fun walletSegmentProgress(segment: WalletCardStackSegment, progress: Double, from: Float): Double {
        return when (segment.kind) {
            WalletCardStackSegmentKind.Hold -> 0.0
            WalletCardStackSegmentKind.Timing -> segment.easing.transform(progress.toFloat()).toDouble()
            WalletCardStackSegmentKind.Spring -> {
                val durationSeconds = segment.durationMs / 1_000.0
                val damping = segment.dampingRatio.toDouble()
                val omega = 12.0 / durationSeconds
                val displacement = (from - segment.to).toDouble()
                if (abs(displacement) <= 0.0001) return 1.0
                val normalizedVelocity = segment.velocity * durationSeconds / displacement
                val elapsed = progress * durationSeconds
                if (damping < 1.0) {
                    val damped = omega * sqrt(max(0.0, 1.0 - damping * damping))
                    val coefficient = (damping * omega + normalizedVelocity) / max(damped, 0.0001)
                    val response = exp(-damping * omega * elapsed) *
                        (cos(damped * elapsed) + coefficient * sin(damped * elapsed))
                    1.0 - response
                } else {
                    val response = exp(-omega * elapsed) * (1.0 + (omega + normalizedVelocity) * elapsed)
                    1.0 - response
                }
            }
        }
    }
}

/** All animated values are stateful only at the leaf draw/graphicsLayer readers. */
private class WalletCardStackMotion {
    private val frontTrack = WalletCardStackTrack(0f)
    private val backTrack = WalletCardStackTrack(0f)
    private val tiltTrack = WalletCardStackTrack(0f)
    private val turnTrack = WalletCardStackTrack(0f)
    private val squishTrack = WalletCardStackTrack(0f)
    private val hiddenTrack = WalletCardStackTrack(0f)
    private val swapTrack = WalletCardStackTrack(0f)
    var frontLift by mutableFloatStateOf(0f)
        private set
    var backLift by mutableFloatStateOf(0f)
        private set
    var tilt by mutableFloatStateOf(0f)
        private set
    var turn by mutableFloatStateOf(0f)
        private set
    var squish by mutableFloatStateOf(0f)
        private set
    var hidden by mutableFloatStateOf(0f)
        private set
    var swapTurn by mutableFloatStateOf(0f)
        private set
    var animationGeneration by mutableIntStateOf(0)
        private set
    var lastFrameNanos: Long = 0L
        private set

    private fun now() = if (lastFrameNanos != 0L) lastFrameNanos else System.nanoTime()

    fun advance(frameNanos: Long) {
        lastFrameNanos = frameNanos
        frontLift = frontTrack.value(frameNanos)
        backLift = backTrack.value(frameNanos)
        tilt = tiltTrack.value(frameNanos)
        turn = turnTrack.value(frameNanos)
        squish = squishTrack.value(frameNanos)
        hidden = hiddenTrack.value(frameNanos)
        swapTurn = swapTrack.value(frameNanos)
    }

    fun isRunning(nowNanos: Long = System.nanoTime()) = listOf(
        frontTrack,
        backTrack,
        tiltTrack,
        turnTrack,
        squishTrack,
        hiddenTrack,
        swapTrack,
    ).any { it.isRunning(nowNanos) }

    private fun changed() {
        animationGeneration += 1
    }

    fun pull(lift: Float, reduced: Boolean) {
        if (reduced) return
        val at = now()
        frontTrack.start(at, listOf(WalletCardStackSegment(WalletCardStackSegmentKind.Spring, lift, WalletCardStackMotionSpec.springDuration, dampingRatio = WalletCardStackMotionSpec.pullDamping)))
        backTrack.start(at, listOf(WalletCardStackSegment(WalletCardStackSegmentKind.Spring, WalletCardStackMotionSpec.backLift * lift / WalletCardStackMotionSpec.lift, WalletCardStackMotionSpec.springDuration, dampingRatio = WalletCardStackMotionSpec.pullDamping)))
        tiltTrack.start(at, listOf(WalletCardStackSegment(WalletCardStackSegmentKind.Spring, WalletCardStackMotionSpec.tilt * lift / WalletCardStackMotionSpec.lift, WalletCardStackMotionSpec.springDuration, dampingRatio = WalletCardStackMotionSpec.pullDamping)))
        changed()
    }

    fun drop(velocity: Float = 0f, reduced: Boolean) {
        if (reduced) return
        val at = now()
        frontTrack.start(at, listOf(WalletCardStackSegment(WalletCardStackSegmentKind.Spring, 0f, WalletCardStackMotionSpec.springDuration, dampingRatio = WalletCardStackMotionSpec.dropDamping, velocity = velocity)))
        backTrack.start(at, listOf(WalletCardStackSegment(WalletCardStackSegmentKind.Spring, 0f, WalletCardStackMotionSpec.springDuration, dampingRatio = WalletCardStackMotionSpec.dropDamping)))
        tiltTrack.start(at, listOf(WalletCardStackSegment(WalletCardStackSegmentKind.Spring, 0f, WalletCardStackMotionSpec.springDuration, dampingRatio = WalletCardStackMotionSpec.dropDamping)))
        turnTrack.start(at, listOf(WalletCardStackSegment(WalletCardStackSegmentKind.Spring, 0f, WalletCardStackMotionSpec.springDuration, dampingRatio = WalletCardStackMotionSpec.dropDamping)))
        squishTrack.start(at, listOf(
            WalletCardStackSegment(WalletCardStackSegmentKind.Hold, squish, WalletCardStackMotionSpec.squishDelay),
            WalletCardStackSegment(WalletCardStackSegmentKind.Timing, 1f, WalletCardStackMotionSpec.squishIn, WalletCardStackEaseOut),
            WalletCardStackSegment(WalletCardStackSegmentKind.Spring, 0f, WalletCardStackMotionSpec.springDuration, dampingRatio = WalletCardStackMotionSpec.dropDamping),
        ))
        changed()
    }

    fun drag(lift: Float, turn: Float) {
        val at = now()
        val back = WalletCardStackMotionSpec.backLift * min(1f, lift / WalletCardStackMotionSpec.lift)
        val lean = WalletCardStackMotionSpec.tilt * min(1.2f, lift / WalletCardStackMotionSpec.lift)
        frontTrack.jump(at, lift)
        backTrack.jump(at, back)
        tiltTrack.jump(at, lean)
        turnTrack.jump(at, turn)
        frontLift = lift
        backLift = back
        tilt = lean
        this.turn = turn
    }

    fun setPose(
        milliseconds: Float,
        reduced: Boolean,
        frameNanos: Long = System.nanoTime(),
    ) {
        lastFrameNanos = frameNanos
        val at = frameNanos
        val pose = walletPose(milliseconds, reduced)
        frontTrack.jump(at, pose.lift)
        backTrack.jump(at, pose.back)
        tiltTrack.jump(at, pose.lean)
        turnTrack.jump(at, pose.turn)
        squishTrack.jump(at, pose.squish)
        hiddenTrack.jump(at, pose.hidden)
        frontLift = pose.lift
        backLift = pose.back
        tilt = pose.lean
        turn = pose.turn
        squish = pose.squish
        hidden = pose.hidden
    }

    fun hide(next: Boolean, reduced: Boolean) {
        val at = now()
        hiddenTrack.start(at, listOf(
            WalletCardStackSegment(
                WalletCardStackSegmentKind.Timing,
                if (next) 1f else 0f,
                if (reduced) WalletCardStackMotionSpec.fadeMs else WalletCardStackMotionSpec.charStagger * (WalletCardStackBalance.length - 1) + WalletCardStackMotionSpec.charDuration,
                WalletCardStackEaseInOut,
            ),
        ))
        changed()
    }

    fun swap(reduced: Boolean) {
        if (reduced) return
        val at = now()
        val nextTurn = kotlin.math.round(swapTurn).toInt() + 1
        swapTrack.start(at, listOf(WalletCardStackSegment(WalletCardStackSegmentKind.Timing, nextTurn.toFloat(), WalletCardStackMotionSpec.swapTurn, WalletCardStackEaseInOut)))
        frontTrack.start(at, listOf(
            WalletCardStackSegment(WalletCardStackSegmentKind.Timing, WalletCardStackMotionSpec.swapFrontDip, WalletCardStackMotionSpec.swapFrontIn, WalletCardStackEaseOut),
            WalletCardStackSegment(WalletCardStackSegmentKind.Spring, 0f, WalletCardStackMotionSpec.springDuration, dampingRatio = WalletCardStackMotionSpec.dropDamping),
        ))
        squishTrack.start(at, listOf(
            WalletCardStackSegment(WalletCardStackSegmentKind.Timing, WalletCardStackMotionSpec.swapSquish, WalletCardStackMotionSpec.swapSquishIn, WalletCardStackEaseOut),
            WalletCardStackSegment(WalletCardStackSegmentKind.Spring, 0f, WalletCardStackMotionSpec.springDuration, dampingRatio = WalletCardStackMotionSpec.dropDamping),
        ))
        changed()
    }
}

private enum class WalletCardStackIcon {
    Menu,
    Plus,
    Swap,
    Eye,
    Home,
    Chart,
    Pulse,
    Card,
    Scan,
}

@Composable
private fun WalletCardStackIcon(
    icon: WalletCardStackIcon,
    color: Color,
    size: Float,
    stroke: Float? = null,
    crossed: Boolean = false,
) {
    Canvas(Modifier.size(size.dp)) {
        val viewBox = when (icon) {
            WalletCardStackIcon.Menu, WalletCardStackIcon.Plus -> 22f
            WalletCardStackIcon.Swap -> 16f
            WalletCardStackIcon.Eye -> 18f
            WalletCardStackIcon.Home, WalletCardStackIcon.Chart, WalletCardStackIcon.Pulse, WalletCardStackIcon.Card -> 26f
            WalletCardStackIcon.Scan -> 30f
        }
        val scale = this.size.minDimension / viewBox
        withTransform({ scale(scale, scale, pivot = Offset.Zero) }) {
            val line = stroke ?: when (icon) {
                WalletCardStackIcon.Menu -> 2.2f
                WalletCardStackIcon.Plus -> 2f
                WalletCardStackIcon.Swap -> 1.7f
                WalletCardStackIcon.Eye -> 1.5f
                WalletCardStackIcon.Home -> 2.2f
                WalletCardStackIcon.Chart -> 1.8f
                WalletCardStackIcon.Pulse -> 1.9f
                WalletCardStackIcon.Card -> 1.8f
                WalletCardStackIcon.Scan -> 2.2f
            }
            val rounded = Stroke(width = line, cap = StrokeCap.Round, join = StrokeJoin.Round)
            val plain = Stroke(width = line, cap = StrokeCap.Butt, join = StrokeJoin.Miter)
            when (icon) {
                WalletCardStackIcon.Menu -> {
                    val path = Path().apply {
                        moveTo(2f, 5f)
                        lineTo(12f, 5f)
                        moveTo(2f, 11f)
                        lineTo(21f, 11f)
                        moveTo(2f, 17f)
                        lineTo(15f, 17f)
                    }
                    drawPath(path, color, style = rounded)
                }
                WalletCardStackIcon.Plus -> {
                    val path = Path().apply {
                        moveTo(11f, 2f)
                        lineTo(11f, 20f)
                        moveTo(2f, 11f)
                        lineTo(20f, 11f)
                    }
                    drawPath(path, color, style = rounded)
                }
                WalletCardStackIcon.Swap -> {
                    val path = Path().apply {
                        moveTo(3f, 6f)
                        lineTo(13f, 6f)
                        lineTo(10f, 3f)
                        moveTo(13f, 10f)
                        lineTo(3f, 10f)
                        lineTo(6f, 13f)
                    }
                    drawPath(path, color, style = rounded)
                }
                WalletCardStackIcon.Eye -> {
                    val eye = Path().apply {
                        moveTo(1.5f, 9f)
                        cubicTo(3.6f, 5.2f, 6.1f, 3.6f, 9f, 3.6f)
                        cubicTo(11.9f, 3.6f, 14.4f, 5.2f, 16.5f, 9f)
                        cubicTo(14.4f, 12.8f, 11.9f, 14.4f, 9f, 14.4f)
                        cubicTo(6.1f, 14.4f, 3.6f, 12.8f, 1.5f, 9f)
                        close()
                    }
                    drawPath(eye, color, style = rounded)
                    val pupil = Path().apply {
                        moveTo(6.6f, 9f)
                        cubicTo(6.6f, 7.67f, 7.67f, 6.6f, 9f, 6.6f)
                        cubicTo(10.33f, 6.6f, 11.4f, 7.67f, 11.4f, 9f)
                        cubicTo(11.4f, 10.33f, 10.33f, 11.4f, 9f, 11.4f)
                        cubicTo(7.67f, 11.4f, 6.6f, 10.33f, 6.6f, 9f)
                    }
                    drawPath(pupil, color, style = rounded)
                    if (crossed) {
                        val cross = Path().apply {
                            moveTo(2.5f, 2.5f)
                            lineTo(15.5f, 15.5f)
                        }
                        drawPath(cross, color, style = rounded)
                    }
                }
                WalletCardStackIcon.Home -> {
                    val path = Path().apply {
                        moveTo(4f, 11.5f)
                        lineTo(13f, 4f)
                        lineTo(22f, 11.5f)
                        lineTo(22f, 20.5f)
                        cubicTo(22f, 21.6f, 21.1f, 22.5f, 20f, 22.5f)
                        lineTo(6f, 22.5f)
                        cubicTo(4.9f, 22.5f, 4f, 21.6f, 4f, 20.5f)
                        close()
                    }
                    drawPath(path, color, style = Stroke(width = line, join = StrokeJoin.Round))
                }
                WalletCardStackIcon.Chart -> {
                    val axes = Path().apply {
                        moveTo(3f, 3f)
                        lineTo(3f, 22.5f)
                        lineTo(23.5f, 22.5f)
                    }
                    drawPath(axes, color, style = rounded)
                    val chart = Path().apply {
                        moveTo(7f, 17f)
                        lineTo(11.5f, 11.5f)
                        lineTo(15f, 15f)
                        lineTo(22.5f, 8f)
                    }
                    drawPath(chart, color, style = rounded)
                }
                WalletCardStackIcon.Pulse -> {
                    val path = Path().apply {
                        moveTo(2.5f, 14f)
                        lineTo(7f, 14f)
                        lineTo(9.5f, 9f)
                        lineTo(12.5f, 19f)
                        lineTo(15.5f, 7f)
                        lineTo(18f, 14f)
                        lineTo(23.5f, 14f)
                    }
                    drawPath(path, color, style = rounded)
                }
                WalletCardStackIcon.Card -> {
                    drawRoundRect(color, topLeft = Offset(2.5f, 5f), size = Size(21f, 16f), cornerRadius = CornerRadius(3f), style = Stroke(width = line))
                    val path = Path().apply {
                        moveTo(2.5f, 10f)
                        lineTo(23.5f, 10f)
                        moveTo(6f, 16.5f)
                        lineTo(9f, 16.5f)
                    }
                    drawPath(path, color, style = rounded)
                }
                WalletCardStackIcon.Scan -> {
                    val path = Path().apply {
                        moveTo(4f, 10f)
                        lineTo(4f, 4f)
                        lineTo(10f, 4f)
                        moveTo(20f, 4f)
                        lineTo(26f, 4f)
                        lineTo(26f, 10f)
                        moveTo(26f, 20f)
                        lineTo(26f, 26f)
                        lineTo(20f, 26f)
                        moveTo(10f, 26f)
                        lineTo(4f, 26f)
                        lineTo(4f, 20f)
                        moveTo(4f, 15f)
                        lineTo(26f, 15f)
                    }
                    drawPath(path, color, style = plain)
                }
            }
        }
    }
}

@Composable
private fun WalletCardStackTapTarget(
    modifier: Modifier,
    label: String,
    onTap: () -> Unit,
    stateDescriptionValue: String? = null,
    checkedValue: Boolean? = null,
    roleValue: Role = Role.Button,
    selectedValue: Boolean? = null,
    animatedPress: Boolean = true,
    pressScale: Float = WalletCardStackMotionSpec.pressScale,
    pressedOpacity: Float = 1f,
    content: @Composable BoxScope.() -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }
    val latestOnTap by rememberUpdatedState(onTap)
    val pressProgress by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = tween(if (animatedPress) WalletCardStackMotionSpec.pressIn else 0, easing = WalletCardStackEaseOut),
        label = "wallet press",
    )
    Box(
        modifier = modifier
            .graphicsLayer {
                val scale = 1f - (1f - pressScale) * pressProgress
                scaleX = scale
                scaleY = scale
                alpha = 1f - (1f - pressedOpacity) * pressProgress
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        val released = tryAwaitRelease()
                        pressed = false
                        if (released) latestOnTap()
                    },
                )
            }
            .semantics {
                contentDescription = label
                role = roleValue
                if (stateDescriptionValue != null) stateDescription = stateDescriptionValue
                if (checkedValue != null) toggleableState = if (checkedValue) ToggleableState.On else ToggleableState.Off
                if (selectedValue != null) selected = selectedValue
                onClick {
                    latestOnTap()
                    true
                }
            },
        content = content,
    )
}

private fun Modifier.walletCardStackSemantics(label: String, tab: Boolean = false, selected: Boolean = false) = semantics {
    contentDescription = label
    role = if (tab) Role.Tab else Role.Button
    if (tab) this.selected = selected
}

private fun walletCardStackPocketPath(inset: Float, scale: Float): Path {
    val layout = WalletCardStackLayout
    val side = (layout.pocketSide + inset) * scale
    val dip = (layout.pocketDip + inset) * scale
    val corner = max(2f, layout.cornerRadius - inset * 0.6f) * scale
    val radius = (layout.pocketRadius - inset) * scale
    val left = (inset + layout.pocketInset) * scale
    val right = (layout.walletWidth - inset - layout.pocketInset) * scale
    val bottom = (layout.walletHeight - inset - layout.pocketBottom) * scale
    val origin = WalletCardStackLayout.canvasPad * scale
    return Path().apply {
        moveTo(origin + left, origin + side + corner)
        quadraticTo(origin + left, origin + side, origin + left + corner, origin + side)
        cubicTo(origin + layout.walletWidth * 0.3f * scale, origin + side, origin + layout.walletWidth * 0.36f * scale, origin + dip, origin + layout.walletWidth / 2f * scale, origin + dip)
        cubicTo(origin + layout.walletWidth * 0.64f * scale, origin + dip, origin + layout.walletWidth * 0.7f * scale, origin + side, origin + right - corner, origin + side)
        quadraticTo(origin + right, origin + side, origin + right, origin + side + corner)
        lineTo(origin + right, origin + bottom - radius)
        cubicTo(origin + right, origin + bottom - radius * 0.45f, origin + right - radius * 0.45f, origin + bottom, origin + right - radius, origin + bottom)
        lineTo(origin + left + radius, origin + bottom)
        cubicTo(origin + left + radius * 0.45f, origin + bottom, origin + left, origin + bottom - radius * 0.45f, origin + left, origin + bottom - radius)
        close()
    }
}

private fun walletCardStackStitchPath(scale: Float): Path {
    val layout = WalletCardStackLayout
    val inset = layout.stitchInset
    val side = (layout.pocketSide + inset) * scale
    val dip = (layout.pocketDip + inset) * scale
    val left = (layout.pocketInset + inset + 6f) * scale
    val right = (layout.walletWidth - layout.pocketInset - inset - 6f) * scale
    val origin = WalletCardStackLayout.canvasPad * scale
    val bottomLeft = (layout.pocketInset + inset) * scale
    val bottomRight = (layout.walletWidth - layout.pocketInset - inset) * scale
    val bottom = (layout.walletHeight - layout.pocketBottom - inset) * scale
    val radius = (layout.pocketRadius - inset) * scale
    return Path().apply {
        moveTo(origin + left, origin + side)
        cubicTo(origin + layout.walletWidth * 0.3f * scale, origin + side, origin + layout.walletWidth * 0.36f * scale, origin + dip, origin + layout.walletWidth / 2f * scale, origin + dip)
        cubicTo(origin + layout.walletWidth * 0.64f * scale, origin + dip, origin + layout.walletWidth * 0.7f * scale, origin + side, origin + right, origin + side)
        moveTo(origin + bottomLeft, origin + bottom - radius - layout.stitchRise * scale)
        lineTo(origin + bottomLeft, origin + bottom - radius)
        cubicTo(origin + bottomLeft, origin + bottom - radius * 0.45f, origin + bottomLeft + radius * 0.45f, origin + bottom, origin + bottomLeft + radius, origin + bottom)
        lineTo(origin + bottomRight - radius, origin + bottom)
        cubicTo(origin + bottomRight - radius * 0.45f, origin + bottom, origin + bottomRight, origin + bottom - radius * 0.45f, origin + bottomRight, origin + bottom - radius)
        lineTo(origin + bottomRight, origin + bottom - radius - layout.stitchRise * scale)
    }
}

private fun walletCardStackRimPath(scale: Float): Path {
    val layout = WalletCardStackLayout
    val side = layout.pocketSide * scale
    val dip = layout.pocketDip * scale
    val corner = layout.cornerRadius * scale
    val left = layout.pocketInset * scale
    val right = (layout.walletWidth - layout.pocketInset) * scale
    val origin = WalletCardStackLayout.canvasPad * scale
    return Path().apply {
        moveTo(origin + left, origin + side + corner)
        quadraticTo(origin + left, origin + side, origin + left + corner, origin + side)
        cubicTo(origin + layout.walletWidth * 0.3f * scale, origin + side, origin + layout.walletWidth * 0.36f * scale, origin + dip, origin + layout.walletWidth / 2f * scale, origin + dip)
        cubicTo(origin + layout.walletWidth * 0.64f * scale, origin + dip, origin + layout.walletWidth * 0.7f * scale, origin + side, origin + right - corner, origin + side)
        quadraticTo(origin + right, origin + side, origin + right, origin + side + corner)
    }
}

private fun walletCardStackHorizontalBrush(
    colors: List<Color>,
    stops: FloatArray,
    start: Float,
    end: Float,
) = Brush.horizontalGradient(
    *Array(colors.size) { index -> stops[index] to colors[index] },
    startX = start,
    endX = end,
)

@Composable
private fun WalletCardStackBackSurface(modifier: Modifier) {
    val density = LocalDensity.current.density
    Canvas(modifier = modifier) {
        val scale = density
        val origin = WalletCardStackLayout.canvasPad * scale
        val width = WalletCardStackLayout.walletWidth * scale
        val height = WalletCardStackLayout.walletHeight * scale
        val layout = WalletCardStackLayout
        val shadowLeft = origin + width * 0.1f
        val shadowTop = origin + (height - 14f * scale)
        val shadowRight = shadowLeft + width * 0.8f
        val shadowBottom = shadowTop + 18f * scale
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            drawIntoCanvas { canvas ->
                val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.TRANSPARENT
                    setShadowLayer(10f * scale, 0f, 6f * scale, Color(0x593C1E6E).toArgb())
                }
                canvas.nativeCanvas.drawRoundRect(shadowLeft, shadowTop, shadowRight, shadowBottom, 9f * scale, 9f * scale, shadow)
            }
        } else {
            drawRoundRect(
                color = Color(0x523C1E6E),
                topLeft = Offset(shadowLeft, shadowTop + 6f * scale),
                size = Size(width * 0.8f, 18f * scale),
                cornerRadius = CornerRadius(9f * scale),
            )
        }
        drawRoundRect(
            brush = walletCardStackHorizontalBrush(layout.flapColors, layout.flapStops, origin, origin + width),
            topLeft = Offset(origin, origin),
            size = Size(width, height - layout.flapShort * scale),
            cornerRadius = CornerRadius(layout.flapRadius * scale),
        )
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(layout.rimTop.copy(alpha = 0.8f), Color.Transparent),
                startY = origin,
                endY = origin + 50f * scale,
            ),
            topLeft = Offset(origin + scale, origin + scale),
            size = Size(width - 2f * scale, height - 2f * scale - layout.flapShort * scale),
            cornerRadius = CornerRadius((layout.flapRadius - 1f) * scale),
            style = Stroke(width = 1.5f * scale),
        )
    }
}

@Composable
private fun WalletCardStackFrontSurface(modifier: Modifier) {
    val density = LocalDensity.current.density
    val context = LocalContext.current
    val leather = remember(context) {
        BitmapFactory.decodeResource(context.resources, R.drawable.walletcardstack_leather).asImageBitmap()
    }
    Canvas(modifier = modifier) {
        val scale = density
        val layout = WalletCardStackLayout
        val width = layout.walletWidth * scale
        val height = layout.walletHeight * scale
        val pocket = walletCardStackPocketPath(0f, scale)
        val stitch = walletCardStackStitchPath(scale)
        val rim = walletCardStackRimPath(scale)
        val origin = WalletCardStackLayout.canvasPad * scale

        drawPath(
            path = pocket,
            brush = walletCardStackHorizontalBrush(layout.pocketColors, layout.pocketStops, origin, origin + width),
        )
        drawPath(
            path = pocket,
            brush = Brush.verticalGradient(
                colors = listOf(Color(0x0024104F), Color(0x1F24104F), Color(0xCC24104F)),
                startY = origin + layout.pocketSide * scale,
                endY = origin + height,
            ),
        )
        drawPath(
            path = pocket,
            brush = Brush.radialGradient(
                colors = listOf(layout.pocketGlow, Color.Transparent),
                center = Offset(origin + width * layout.pocketGlowCx, origin + height * layout.pocketGlowCy),
                radius = width * layout.pocketGlowRadius,
            ),
        )
        clipPath(pocket) {
            val tile = (200f * scale).roundToInt()
            val source = IntSize(leather.width, leather.height)
            val countX = ceil(width / tile).toInt() + 1
            val countY = ceil(height / tile).toInt() + 1
            for (x in 0 until countX) {
                for (y in 0 until countY) {
                    drawImage(
                        leather,
                        srcOffset = IntOffset.Zero,
                        srcSize = source,
                        dstOffset = IntOffset(origin.roundToInt() + x * tile, origin.roundToInt() + y * tile),
                        dstSize = IntSize(tile, tile),
                        alpha = 0.16f,
                        blendMode = BlendMode.Softlight,
                    )
                }
            }
        }
        drawPath(
            path = stitch,
            color = layout.stitch,
            style = Stroke(width = 1.1f * scale, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(4.5f * scale, 3.2f * scale))),
        )
        drawPath(path = rim, color = Color(0xB3E2CBFB), style = Stroke(width = scale))
        withTransform({ translate(0f, 1.4f * scale) }) {
            drawPath(
                path = rim,
                color = Color(0xB3301866),
                style = Stroke(width = 1.4f * scale),
            )
        }
    }
}

@Composable
private fun WalletCardStackCard(kind: WalletCardStackCardKind, modifier: Modifier = Modifier) {
    when (kind) {
        WalletCardStackCardKind.Back -> {
            Box(
                modifier = modifier
                    .size(WalletCardStackLayout.backCardWidth.dp, WalletCardStackLayout.backCardHeight.dp)
                    .clip(RoundedCornerShape(WalletCardStackLayout.backCardRadius.dp)),
            ) {
                Image(
                    painter = painterResource(R.drawable.walletcardstack_back_field),
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        WalletCardStackCardKind.Front -> {
            val card = WalletCardStackLayout
            val holderName = WalletCardStackHolder.first
            val last4 = WalletCardStackHolder.second
            val network = WalletCardStackHolder.third
            Box(
                modifier = modifier
                    .size(card.frontCardWidth.dp, card.frontCardHeight.dp)
                    .clip(RoundedCornerShape(card.frontCardRadius.dp)),
            ) {
                Image(
                    painter = painterResource(R.drawable.walletcardstack_card_field),
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize(),
                )
                Text(
                    text = holderName,
                    color = Color.White.copy(alpha = 0.88f),
                    fontSize = 17.5.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.4.sp,
                    style = WalletCardStackTextStyle,
                    modifier = Modifier.offset(card.cardPadX.dp, (card.cardNameY - card.frontCardY - 11f).dp),
                )
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(fontSize = 8.sp, letterSpacing = 0.8.sp)) { append("**** **** ****  ") }
                        withStyle(SpanStyle(fontSize = 10.sp, letterSpacing = 0.4.sp)) { append(last4) }
                    },
                    color = Color.White.copy(alpha = 0.85f),
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    style = WalletCardStackTextStyle,
                    modifier = Modifier.offset(card.cardPadX.dp, (card.cardNumberY - card.frontCardY - 8f).dp),
                )
                Text(
                    text = network,
                    color = WalletCardStackColors.white,
                    fontSize = 19.5.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.Black,
                    fontStyle = FontStyle.Italic,
                    style = WalletCardStackTextStyle,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset((-card.cardPadX).dp, (card.cardBrandY - card.frontCardY - 12f).dp),
                )
                Text(
                    text = "Valid: $WalletCardStackValid",
                    color = Color.White.copy(alpha = 0.92f),
                    fontSize = 10.5.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    style = WalletCardStackTextStyle,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset((-card.cardPadX).dp, (card.cardValidY - card.frontCardY - 8f).dp),
                )
            }
        }
    }
}

private enum class WalletCardStackCardKind { Back, Front }

private val WalletCardStackTextStyle = TextStyle(
    fontFamily = FontFamily.SansSerif,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

@Composable
private fun WalletCardStackBalanceChar(
    character: Char,
    index: Int,
    motion: WalletCardStackMotion,
    reducedMotion: Boolean,
) {
    val isDigit = character.isDigit()
    val density = LocalDensity.current.density
    val hideSpan = (WalletCardStackMotionSpec.charStagger * (WalletCardStackBalance.length - 1) + WalletCardStackMotionSpec.charDuration).toFloat()
    Box {
        Text(
            text = character.toString(),
            color = WalletCardStackColors.white,
            fontSize = 24.4.sp,
            lineHeight = 36.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.5).sp,
            style = WalletCardStackTextStyle.copy(fontFeatureSettings = "tnum"),
            modifier = Modifier.graphicsLayer {
                val progress = if (reducedMotion) {
                    motion.hidden
                } else {
                    walletClamp((motion.hidden * hideSpan - index * WalletCardStackMotionSpec.charStagger.toFloat()) / WalletCardStackMotionSpec.charDuration.toFloat())
                }
                alpha = 1f - progress
                translationY = if (reducedMotion) 0f else -WalletCardStackMotionSpec.hideRise * progress * density
            },
        )
        if (isDigit) {
            Text(
                text = "•",
                color = WalletCardStackColors.white,
                fontSize = 24.4.sp,
                lineHeight = 36.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp,
                style = WalletCardStackTextStyle,
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        val progress = if (reducedMotion) {
                            motion.hidden
                        } else {
                            walletClamp((motion.hidden * hideSpan - index * WalletCardStackMotionSpec.charStagger.toFloat()) / WalletCardStackMotionSpec.charDuration.toFloat())
                        }
                        alpha = progress
                        translationY = if (reducedMotion) 0f else WalletCardStackMotionSpec.hideRise * (1f - progress) * density
                        scaleX = if (reducedMotion) 1f else 0.6f + 0.4f * progress
                        scaleY = if (reducedMotion) 1f else 0.6f + 0.4f * progress
                    },
            )
        }
    }
}

@Composable
private fun WalletCardStackWallet(
    oy: Float,
    motion: WalletCardStackMotion,
    reducedMotion: Boolean,
    hiddenNow: Boolean,
    frozen: Boolean,
    onEye: () -> Unit,
    onSwap: () -> Unit,
    onAdd: () -> Unit,
    onCardTap: () -> Unit,
) {
    val cardLayout = WalletCardStackLayout
    val density = LocalDensity.current
    val view = LocalView.current
    val latestReducedMotion by rememberUpdatedState(reducedMotion)
    val latestOnCardTap by rememberUpdatedState(onCardTap)
    val chars = WalletCardStackBalance.toList()
    Box(
        modifier = Modifier
            .offset(cardLayout.walletLeft.dp, (oy + cardLayout.walletTop).dp)
            .size(cardLayout.walletWidth.dp, cardLayout.walletHeight.dp)
            .graphicsLayer {
                transformOrigin = TransformOrigin(0.5f, 1f)
                scaleX = 1f + (WalletCardStackMotionSpec.squishScaleX - 1f) * motion.squish
                scaleY = 1f + (WalletCardStackMotionSpec.squishScaleY - 1f) * motion.squish
            },
    ) {
        WalletCardStackBackSurface(
            Modifier
                .offset((-cardLayout.canvasPad).dp, (-cardLayout.canvasPad).dp)
                .size((cardLayout.walletWidth + cardLayout.canvasPad * 2f).dp, (cardLayout.walletHeight + cardLayout.canvasPad * 2f).dp),
        )
        Box(
            modifier = Modifier
                .offset(0.dp, cardLayout.cardsTouchTop.dp)
                .fillMaxWidth()
                .height((-cardLayout.cardsTouchTop + cardLayout.pocketDip + 6f).dp)
                .then(
                    if (frozen) {
                        Modifier
                    } else {
                        Modifier.pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val tracker = VelocityTracker()
                                tracker.addPosition(down.uptimeMillis, down.position)
                                val grab = motion.frontLift
                                var dragging = false
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    tracker.addPosition(change.uptimeMillis, change.position)
                                    if (change.pressed) {
                                        val dx = (change.position.x - down.position.x) / density.density
                                        val dy = change.position.y - down.position.y
                                        if (!dragging && abs(dy) > WalletCardStackMotionSpec.touchSlop * density.density) dragging = true
                                        if (dragging) {
                                            change.consume()
                                            val lift = max(0f, grab + walletRubber(-dy / density.density, WalletCardStackMotionSpec.limit))
                                            val turn = (dx * WalletCardStackMotionSpec.turnPerDx)
                                                .coerceIn(-WalletCardStackMotionSpec.turnMax, WalletCardStackMotionSpec.turnMax)
                                            motion.drag(lift, turn)
                                        }
                                    } else {
                                        if (dragging) {
                                            val velocity = -tracker.calculateVelocity().y / density.density
                                            motion.drop(velocity, latestReducedMotion)
                                            walletMediumHaptic(view)
                                        } else {
                                            latestOnCardTap()
                                        }
                                        break
                                    }
                                }
                            }
                        }
                    },
                )
                .semantics {
                    contentDescription = "${WalletCardStackHolder.third} card ending ${WalletCardStackHolder.second}"
                    role = Role.Button
                    stateDescription = "Pulls the card out of the wallet"
                },
        ) {
            Box(
                modifier = Modifier
                    .offset(cardLayout.backCardX.dp, (cardLayout.backCardY - cardLayout.cardsTouchTop).dp)
                    .graphicsLayer { translationY = -motion.backLift * density.density },
            ) {
                WalletCardStackCard(WalletCardStackCardKind.Back)
            }
            Box(
                modifier = Modifier
                    .offset(cardLayout.frontCardX.dp, (cardLayout.frontCardY - cardLayout.cardsTouchTop).dp)
                    .graphicsLayer {
                        translationY = -motion.frontLift * density.density
                        rotationZ = motion.tilt
                    },
            ) {
                val frontShape = remember { RoundedCornerShape(cardLayout.frontCardRadius.dp) }
                Box(
                    modifier = Modifier
                        .size(cardLayout.frontCardWidth.dp, cardLayout.frontCardHeight.dp)
                        .graphicsLayer {
                            val lifted = (motion.frontLift / WalletCardStackMotionSpec.lift).coerceIn(0f, 1f)
                            rotationY = motion.turn
                            cameraDistance = WalletCardStackMotionSpec.perspective * density.density
                            shadowElevation = (4f + 10f * lifted) * density.density
                            shape = frontShape
                            clip = true
                            ambientShadowColor = Color(0xFF2A1462).copy(
                                alpha = WalletCardStackMotionSpec.cardShadow * lifted,
                            )
                            spotShadowColor = ambientShadowColor
                        },
                ) {
                    WalletCardStackCard(WalletCardStackCardKind.Front)
                    Box(
                        modifier = Modifier
                            .offset(y = (-60).dp)
                            .size(110.dp, 300.dp)
                            .graphicsLayer {
                                val lifted = (motion.frontLift / WalletCardStackMotionSpec.lift).coerceIn(0f, 1f)
                                val ratio = motion.turn / WalletCardStackMotionSpec.sway
                                alpha = WalletCardStackMotionSpec.sheen * lifted *
                                    (0.35f + 0.65f * min(1f, abs(ratio)))
                                translationX = (
                                    cardLayout.frontCardWidth *
                                        (0.5f + ratio * WalletCardStackMotionSpec.sheenTravel) - 55f
                                    ) * density.density
                                rotationZ = 20f
                            }
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        Color.White.copy(alpha = 0f),
                                        Color.White.copy(alpha = 0.9f),
                                        Color.White.copy(alpha = 0f),
                                    ),
                                ),
                            ),
                    )
                }
            }
        }
        WalletCardStackFrontSurface(
            Modifier
                .offset((-cardLayout.canvasPad).dp, (-cardLayout.canvasPad).dp)
                .size((cardLayout.walletWidth + cardLayout.canvasPad * 2f).dp, (cardLayout.walletHeight + cardLayout.canvasPad * 2f).dp),
        )
        Text(
            text = WalletCardStackBalanceLabel,
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 12.2.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.Medium,
            style = WalletCardStackTextStyle,
            modifier = Modifier.offset(cardLayout.pillX.dp, (cardLayout.labelY - 9f).dp),
        )
        Row(
            modifier = Modifier
                .offset(cardLayout.pillX.dp, (cardLayout.balanceY - 20.5f).dp)
                .height(41.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            chars.forEachIndexed { index, character ->
                WalletCardStackBalanceChar(character, index, motion, reducedMotion)
            }
            Text(
                text = WalletCardStackCurrency,
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 12.3.sp,
                fontWeight = FontWeight.SemiBold,
                style = WalletCardStackTextStyle,
                modifier = Modifier.padding(start = 2.dp, bottom = 4.dp),
            )
        }
        WalletCardStackTapTarget(
            modifier = Modifier
                .offset((cardLayout.pillX - 8f).dp, (cardLayout.pillY - 8f).dp)
                .size((cardLayout.pillW + 16f).dp, (cardLayout.pillH + 16f).dp),
            label = "Add balance",
            onTap = onAdd,
        ) {
            Box(
                modifier = Modifier
                    .offset(8.dp, 8.dp)
                    .size(cardLayout.pillW.dp, cardLayout.pillH.dp)
                    .clip(RoundedCornerShape((cardLayout.pillH / 2f).dp))
                    .background(Color.White.copy(alpha = 0.16f))
                    .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape((cardLayout.pillH / 2f).dp)),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    WalletCardStackIcon(WalletCardStackIcon.Plus, WalletCardStackColors.white, 17f, stroke = 1.8f)
                    Spacer(Modifier.width(9.dp))
                    Text("Add Balance", color = WalletCardStackColors.white, fontSize = 13.8.sp, fontWeight = FontWeight.Medium, style = WalletCardStackTextStyle)
                }
            }
        }
        WalletCardStackTapTarget(
            modifier = Modifier
                .offset((cardLayout.iconXs[0] - 24f).dp, (cardLayout.iconY - 24f).dp)
                .size(48.dp),
            label = "Switch card",
            onTap = onSwap,
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(cardLayout.iconSize.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.1f))
                        .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.graphicsLayer { rotationZ = motion.swapTurn * 180f }) {
                        WalletCardStackIcon(WalletCardStackIcon.Swap, WalletCardStackColors.white, 16f)
                    }
                }
            }
        }
        WalletCardStackTapTarget(
            modifier = Modifier
                .offset((cardLayout.iconXs[1] - 24f).dp, (cardLayout.iconY - 24f).dp)
                .size(48.dp),
            label = if (hiddenNow) "Show balance" else "Hide balance",
            stateDescriptionValue = if (hiddenNow) "Balance hidden" else "Balance shown",
            checkedValue = hiddenNow,
            onTap = onEye,
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(cardLayout.iconSize.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.1f))
                        .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    WalletCardStackIcon(WalletCardStackIcon.Eye, WalletCardStackColors.white, 17f, crossed = !hiddenNow)
                }
            }
        }
    }
}

@Composable
private fun WalletCardStackAvatar(person: WalletCardStackPerson, size: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(person.tint),
    ) {
        Image(
            painter = painterResource(person.image),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size((size * WalletCardStackAvatarZoom).dp)
                .offset(
                    x = (-size * (WalletCardStackAvatarZoom - 1f) / 2f).dp,
                    y = (-size * (WalletCardStackAvatarZoom - 1f) / 2f).dp,
                ),
        )
    }
}

@Composable
private fun WalletCardStackHeader(width: Float, oy: Float, onAddCard: () -> Unit) {
    val layout = WalletCardStackLayout
    WalletCardStackTapTarget(
        modifier = Modifier
            .offset(layout.headerInset.dp, (oy + layout.headerY - layout.headerButton / 2f).dp)
            .size(layout.headerButton.dp)
            .shadow(9.dp, CircleShape, clip = false)
            .clip(CircleShape)
            .background(WalletCardStackColors.button),
        label = "Menu",
        onTap = {},
        animatedPress = false,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            WalletCardStackIcon(WalletCardStackIcon.Menu, WalletCardStackColors.ink, 22f)
        }
    }
    WalletCardStackTapTarget(
        modifier = Modifier
            .offset((width - layout.headerInset - layout.headerButton).dp, (oy + layout.headerY - layout.headerButton / 2f).dp)
            .size(layout.headerButton.dp)
            .shadow(9.dp, CircleShape, clip = false)
            .clip(CircleShape)
            .background(WalletCardStackColors.button),
        label = "Add card",
        onTap = onAddCard,
        animatedPress = false,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            WalletCardStackIcon(WalletCardStackIcon.Plus, WalletCardStackColors.ink, 22f)
        }
    }
    Text(
        text = "Wallet",
        color = WalletCardStackColors.ink,
        fontSize = 19.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.Bold,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        style = WalletCardStackTextStyle,
        modifier = Modifier
            .offset(0.dp, (oy + layout.headerTitleY - 13f).dp)
            .fillMaxWidth()
            .semantics { heading() },
    )
    Text(
        text = "My Cards & Transaction",
        color = WalletCardStackColors.inkSoft,
        fontSize = 12.1.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        style = WalletCardStackTextStyle,
        modifier = Modifier.offset(0.dp, (oy + layout.headerSubtitleY - 9f).dp).fillMaxWidth(),
    )
}

@Composable
private fun WalletCardStackSectionHeader(title: String, y: Float, width: Float) {
    Row(
        modifier = Modifier
            .offset(20.dp, (y - 12f).dp)
            .width((width - 40f).dp)
            .height(24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = WalletCardStackColors.ink,
            fontSize = 14.5.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.Bold,
            style = WalletCardStackTextStyle,
            modifier = Modifier.semantics { heading() },
        )
        WalletCardStackTapTarget(
            modifier = Modifier,
            label = "See more $title",
            onTap = {},
            animatedPress = false,
            pressScale = 1f,
        ) {
            Text(
                text = "See more",
                color = WalletCardStackColors.inkSoft,
                fontSize = 12.4.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Medium,
                style = WalletCardStackTextStyle,
            )
        }
    }
}

@Composable
private fun WalletCardStackQuickTopUp(width: Float, oy: Float) {
    val layout = WalletCardStackLayout
    val avatar = layout.topUpAvatar
    WalletCardStackSectionHeader("Quick Top-Up", oy + layout.topUpTitleY, width)
    WalletCardStackTapTarget(
        modifier = Modifier
            .offset((layout.topUpAddX - 40f).dp, (oy + layout.topUpRowY - avatar / 2f).dp)
            .width(80.dp),
        label = "Add contact",
        onTap = {},
        animatedPress = false,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(avatar.dp)) {
                    drawCircle(
                        color = WalletCardStackColors.accent,
                        radius = size.minDimension / 2f - size.minDimension / avatar,
                        style = Stroke(width = 1.4f * (size.minDimension / avatar), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(3.2f * (size.minDimension / avatar), 3.2f * (size.minDimension / avatar)))),
                    )
                }
                WalletCardStackIcon(WalletCardStackIcon.Plus, WalletCardStackColors.accent, 22f, stroke = 2.2f)
            }
            Text(
                text = "Add",
                color = WalletCardStackColors.ink,
                fontSize = 13.5.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.SemiBold,
                style = WalletCardStackTextStyle,
                modifier = Modifier.padding(top = 5.5.dp),
            )
        }
    }
    Box(
        modifier = Modifier
            .offset(layout.topUpDividerX.dp, (oy + layout.topUpRowY - 26f).dp)
            .size(1.5.dp, 52.dp)
            .clip(RoundedCornerShape(1.dp))
            .background(WalletCardStackColors.divider),
    )
    WalletCardStackQuick.forEachIndexed { index, person ->
        WalletCardStackTapTarget(
            modifier = Modifier
                .offset((layout.topUpXs[index] - 40f).dp, (oy + layout.topUpRowY - avatar / 2f).dp)
                .width(80.dp),
            label = "Top up ${person.name}",
            onTap = {},
            animatedPress = false,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                WalletCardStackAvatar(person, avatar)
                Text(
                    text = person.name,
                    color = WalletCardStackColors.ink,
                    fontSize = 13.5.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    style = WalletCardStackTextStyle,
                    modifier = Modifier.padding(top = 5.5.dp),
                )
            }
        }
    }
}

@Composable
private fun WalletCardStackLatestTransactions(width: Float, oy: Float) {
    val layout = WalletCardStackLayout
    WalletCardStackSectionHeader("Latest Transactions", oy + layout.latestTitleY, width)
    listOf(WalletCardStackLatest, WalletCardStackEarlier).forEachIndexed { row, payments ->
        payments.forEachIndexed { index, payment ->
            val top = oy + layout.latestTop + row * (layout.latestHeight + 15f)
            WalletCardStackTapTarget(
                modifier = Modifier
                    .offset(layout.latestXs[index].dp, top.dp)
                    .size(layout.latestWidth.dp, layout.latestHeight.dp)
                    .clip(RoundedCornerShape(layout.latestRadius.dp))
                    .background(WalletCardStackColors.tile)
                    .padding(start = 10.dp, top = 14.dp),
                label = "${payment.person.name}, ${payment.date.replace(",", ", ")}, ${payment.amount}",
                onTap = {},
                animatedPress = false,
            ) {
                Column(horizontalAlignment = Alignment.Start) {
                    WalletCardStackAvatar(payment.person, layout.latestAvatar)
                    Text(
                        text = payment.person.name,
                        color = WalletCardStackColors.ink,
                        fontSize = 15.5.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.Bold,
                        style = WalletCardStackTextStyle,
                        modifier = Modifier.padding(top = 9.dp),
                    )
                    Text(
                        text = payment.date,
                        color = WalletCardStackColors.inkSoft,
                        fontSize = 10.4.sp,
                        lineHeight = 15.sp,
                        fontWeight = FontWeight.Medium,
                        style = WalletCardStackTextStyle,
                        modifier = Modifier.padding(top = 1.dp),
                    )
                    Text(
                        text = payment.amount,
                        color = if (payment.incoming) WalletCardStackColors.green else WalletCardStackColors.red,
                        fontSize = 11.5.sp,
                        lineHeight = 17.sp,
                        fontWeight = FontWeight.Bold,
                        style = WalletCardStackTextStyle.copy(fontFeatureSettings = "tnum"),
                        modifier = Modifier.padding(top = 7.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun WalletCardStackTabBar(width: Float, height: Float, oy: Float) {
    val layout = WalletCardStackLayout
    val top = oy + layout.tabTop
    val tabs = listOf(
        Triple("Home", WalletCardStackIcon.Home, true),
        Triple("Statistic", WalletCardStackIcon.Chart, false),
        Triple("Activity", WalletCardStackIcon.Pulse, false),
        Triple("Activity", WalletCardStackIcon.Card, false),
    )
    Box(
        modifier = Modifier
            .offset(0.dp, top.dp)
            .width(width.dp)
            .height((height - top).coerceAtLeast(0f).dp)
            .background(WalletCardStackColors.white),
    ) {
        tabs.forEachIndexed { index, (label, icon, active) ->
            WalletCardStackTapTarget(
                modifier = Modifier
                    .offset((layout.tabXs[index] - 40f).dp, (layout.tabIconY - layout.tabTop - 13f).dp)
                    .width(80.dp),
                label = label,
                onTap = {},
                roleValue = Role.Tab,
                selectedValue = active,
                animatedPress = false,
                pressScale = 1f,
                pressedOpacity = 0.6f,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    WalletCardStackIcon(icon, if (active) WalletCardStackColors.ink else WalletCardStackColors.muted, 26f)
                    Text(
                        text = label,
                        color = if (active) WalletCardStackColors.ink else WalletCardStackColors.muted,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
                        style = WalletCardStackTextStyle,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
        WalletCardStackTapTarget(
            modifier = Modifier
                .offset((width / 2f - layout.fab / 2f).dp, (layout.fabY - layout.tabTop - layout.fab / 2f).dp)
                .size(layout.fab.dp)
                .shadow(10.dp, CircleShape, clip = false)
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(WalletCardStackColors.fabTop, WalletCardStackColors.fabBottom))),
            label = "Scan",
            onTap = {},
            animatedPress = false,
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                WalletCardStackIcon(WalletCardStackIcon.Scan, WalletCardStackColors.white, 30f)
            }
        }
    }
}

/** The full-screen Compose port; all coordinates are in the RN reference's 402-point coordinate space. */
@Composable
fun WalletCardStack(
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false,
    freezeAt: Float? = null,
) {
    val reducedMotion = rememberReducedMotion()
    val motion = remember { WalletCardStackMotion() }
    val view = LocalView.current
    var hiddenNow by remember { mutableStateOf(false) }
    var manual by remember { mutableStateOf(false) }
    var releaseToken by remember { mutableIntStateOf(0) }
    var cardTapToken by remember { mutableIntStateOf(0) }

    androidx.compose.runtime.DisposableEffect(view) {
        val controller = view.context.walletCardStackActivity()?.window?.let { androidx.core.view.WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = true
        onDispose {
            if (previous != null) controller.isAppearanceLightStatusBars = previous
        }
    }

    fun setHidden(next: Boolean) {
        hiddenNow = next
        motion.hide(next, reducedMotion)
    }

    fun lightHaptic() {
        if (!autoPlay || manual) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    LaunchedEffect(motion.animationGeneration) {
        if (!motion.isRunning()) return@LaunchedEffect
        while (isActive) {
            val frame = withFrameNanos { it }
            motion.advance(frame)
            if (!motion.isRunning(frame)) {
                motion.advance(frame)
                break
            }
        }
    }

    LaunchedEffect(autoPlay, freezeAt, reducedMotion, manual) {
        if (freezeAt != null) {
            motion.setPose(freezeAt, reducedMotion)
            hiddenNow = motion.hidden > 0.5f
            return@LaunchedEffect
        }
        if (!autoPlay || reducedMotion || manual) {
            if ((!autoPlay || reducedMotion) && !manual) {
                motion.setPose(0f, false)
                hiddenNow = false
            }
            return@LaunchedEffect
        }
        var loopStartNanos = 0L
        while (isActive) {
            val frameNanos = withFrameNanos { it }
            if (loopStartNanos == 0L) loopStartNanos = frameNanos
            val elapsedMillis = ((frameNanos - loopStartNanos) / 1_000_000L) % LOOP_MS
            motion.setPose(elapsedMillis.toFloat(), false, frameNanos)
        }
    }

    LaunchedEffect(releaseToken, reducedMotion, autoPlay, freezeAt) {
        if (releaseToken == 0 || !autoPlay || freezeAt != null) return@LaunchedEffect
        delay(WalletCardStackMotionSpec.resumeAfterMs)
        if (motion.hidden > 0.001f) setHidden(false)
        motion.drop(reduced = reducedMotion)
        delay(max(WalletCardStackMotionSpec.charStagger * (WalletCardStackBalance.length - 1) + WalletCardStackMotionSpec.charDuration, 900L))
        manual = false
    }

    LaunchedEffect(cardTapToken, reducedMotion, freezeAt) {
        if (cardTapToken == 0 || freezeAt != null) return@LaunchedEffect
        delay(520L)
        motion.drop(reduced = reducedMotion)
        walletMediumHaptic(view)
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(WalletCardStackColors.screen)
            .pointerInput(autoPlay, freezeAt, reducedMotion) {
                if (freezeAt != null) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            event.changes.forEach { it.consume() }
                        }
                    }
                } else {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        if (!manual) manual = true
                        releaseToken = 0
                        cardTapToken = 0
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.changes.none { it.pressed }) break
                        }
                        if (autoPlay) releaseToken += 1
                    }
                }
            },
    ) {
        val width = maxWidth.value
        val height = maxHeight.value
        val safeTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding().value
        val oy = if (safeTop > 0f) safeTop - WalletCardStackLayout.safeTop else -40f

        WalletCardStackHeader(width, oy) { }
        WalletCardStackQuickTopUp(width, oy)
        WalletCardStackLatestTransactions(width, oy)
        WalletCardStackWallet(
            oy = oy,
            motion = motion,
            reducedMotion = reducedMotion,
            hiddenNow = hiddenNow,
            frozen = freezeAt != null,
            onEye = {
                lightHaptic()
                setHidden(!hiddenNow)
            },
            onSwap = {
                lightHaptic()
                motion.swap(reducedMotion)
            },
            onAdd = { lightHaptic() },
            onCardTap = {
                motion.pull(WalletCardStackMotionSpec.tapLift, reducedMotion)
                cardTapToken += 1
            },
        )
        WalletCardStackTabBar(width, height, oy)
    }
}

private tailrec fun android.content.Context.walletCardStackActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.walletCardStackActivity()
    else -> null
}
