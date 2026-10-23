package com.ngocdevv.nativecook.blocks.onboarding.orbitcointour

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.ngocdevv.nativecook.shared.EaseOutStrong
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.surface
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// A swipe down by this much leaves the tour; a flick carries a page on by this many seconds of its speed.
private const val LEAVE = 80f
private const val FLICK = 0.2f

// The first and the last scene give this much of a page when pulled further.
private const val OVER = 0.2f
private const val COIN_BOX = (COIN + 8f) * 2f

private val CaptionStyle = TextStyle(
    color = Ink,
    fontSize = 34.sp,
    lineHeight = 43.sp,
    fontWeight = FontWeight.Light,
    textAlign = TextAlign.Center,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

/**
 * A feature tour. Three coins ride the rings round a coral disc; a tap on one pushes the camera
 * through the rings while the coin flies to the top of the screen and the disc opens into that
 * product's card. Swipes go from scene to scene, each on its own ground, and a swipe down or a
 * tap on the coin pulls back out to the orbit. In the orbit a drag spins the rings.
 *
 * @param caption Under the orbit.
 * @param scenes The title and the line under it of the three scenes: savings, card, insights.
 * @param balance The balance on the savings and the insights card.
 * @param symbol Written before an amount.
 * @param autoPlay Plays the orbit and the three scenes in turn, every [LOOP] seconds.
 * @param onScene Called with 0 for the orbit and 1 to 3 for a scene when it comes to the front.
 * @param onAction Called when the savings card's button is pressed.
 */
@Composable
fun OrbitCoinTour(
    caption: String = "Your financial\nwellness account",
    scenes: List<SceneText> = SampleScenes,
    balance: Float = 23520f,
    symbol: String = "$",
    autoPlay: Boolean = false,
    onScene: ((scene: Int) -> Unit)? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    // The loop and the gesture outlive a recomposition: they read what the caller passes through these.
    val still by rememberUpdatedState(rememberReducedMotion())
    val changed by rememberUpdatedState(onScene)
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    // 0 is the orbit, 1 to 3 the scene in front.
    var scene by remember { mutableIntStateOf(0) }
    // How far the push from the orbit into the tour has gone, at a steady pace. Read while drawing only.
    val tour = remember { Animatable(0f) }
    // The scene in front, 1 to 3, between two of them while they slide.
    val page = remember { Animatable(1f) }
    // The scene the disc opens into, and shrinks back from.
    var hero by remember { mutableIntStateOf(1) }
    // Seconds the orbit has been on screen, and where its clock last started from.
    val clock = remember { mutableFloatStateOf(0f) }
    var phaseSince by remember { mutableFloatStateOf(-PHASE_AT_START) }
    // What the hand added to the rings, in radians.
    val spin = remember { Animatable(0f) }
    val reveals = remember { List(3) { Animatable(0f) } }
    // A finger is dragging: the loop waits.
    var holding by remember { mutableStateOf(false) }
    // Changes on every touch, which restarts the autoPlay loop behind a pause.
    var touches by remember { mutableIntStateOf(0) }

    // The sand and the taupe scene want dark status bar icons, the green one light ones.
    val darkIcons = scene != 2
    DisposableEffect(view, darkIcons) {
        val controller = view.context.activity()?.window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = darkIcons
        onDispose { if (previous != null) controller.isAppearanceLightStatusBars = previous }
    }

    fun tick() = view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)

    fun phase() = if (still) PHASE_AT_START else clock.floatValue - phaseSince

    /** Starts scene [to]'s contents coming in, [after] seconds from now. */
    fun reveal(to: Int, after: Float) {
        scope.launch {
            reveals[to - 1].snapTo(0f)
            reveals[to - 1].animateTo(1f, tween((REVEAL * 1000).toInt(), (after * 1000).toInt(), LinearEasing))
        }
    }

    /** From the orbit into scene [to]. */
    fun open(to: Int) {
        scene = to
        changed?.invoke(to)
        hero = to
        reveal(to, PUSH * 0.8f)
        scope.launch {
            page.snapTo(to.toFloat())
            tour.animateTo(1f, tween(((if (still) FADE else PUSH) * 1000).toInt(), easing = LinearEasing))
        }
    }

    /** To scene [to] from the one in front, in [seconds]. */
    fun go(to: Int, seconds: Float = SLIDE) {
        if (to != scene) {
            scene = to
            changed?.invoke(to)
            reveal(to, if (seconds == SLIDE) REVEAL_AFTER else seconds * 0.6f)
        }
        hero = to
        scope.launch {
            page.animateTo(to.toFloat(), tween(((if (still) FADE else seconds) * 1000).toInt(), easing = if (seconds == SLIDE) InOut else EaseOutStrong))
        }
    }

    /** Back out to the orbit, whose clock starts again. */
    fun close() {
        scene = 0
        changed?.invoke(0)
        phaseSince = clock.floatValue
        scope.launch { tour.animateTo(0f, tween(((if (still) FADE else PUSH) * 1000).toInt(), easing = LinearEasing)) }
    }

    fun pick(to: Int) {
        touches++
        tick()
        open(to)
    }

    fun leave() {
        touches++
        tick()
        go(max(scene, 1), SETTLE)
        close()
    }

    fun settle(to: Int) {
        if (to != scene) tick()
        go(to, SETTLE)
    }

    // The orbit's clock runs while any of it shows.
    val orbiting by remember { derivedStateOf { tour.value < 1f } }
    LaunchedEffect(still, orbiting) {
        if (still || !orbiting) return@LaunchedEffect
        val base = clock.floatValue
        val origin = withFrameNanos { it }
        while (true) clock.floatValue = base + (withFrameNanos { it } - origin) / 1e9f
    }

    LaunchedEffect(autoPlay, touches, holding) {
        if (!autoPlay || holding) return@LaunchedEffect
        if (touches > 0) {
            // A touch takes over; a while later the tour goes back to the orbit with the rings as
            // they were, and a loop begins as long after a pull back as it always does.
            delay((USER_HOLD * 1000).toLong())
            val inside = scene != 0
            if (inside) close()
            scope.launch { spin.animateTo(0f, tween((UNSPIN * 1000).toInt(), easing = EaseOutStrong)) }
            delay(((if (inside) PHASE_AT_START else UNSPIN) * 1000).toLong())
        }
        var origin = System.nanoTime()
        suspend fun until(seconds: Float) = delay(max(0L, (origin + (seconds * 1e9f).toLong() - System.nanoTime()) / 1_000_000))
        while (true) {
            until(OPEN_AT)
            open(1)
            until(SECOND_AT)
            go(2)
            until(THIRD_AT)
            go(3)
            until(CLOSE_AT)
            close()
            until(LOOP)
            origin += (LOOP * 1e9f).toLong()
        }
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Sand)
            .pointerInput(Unit) {
                val tracker = VelocityTracker()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // Stage dp to pixels, and where the stage's corner is.
                    val unit = min(size.width / STAGE_WIDTH, size.height / STAGE_HEIGHT)
                    val left = (size.width - STAGE_WIDTH * unit) / 2f
                    val top = (size.height - STAGE_HEIGHT * unit) / 2f
                    fun onStage(at: Offset) = Offset((at.x - left) / unit, (at.y - top) / unit)
                    tracker.resetTracking()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    // The drag: 1 spins the rings, 2 turns the pages, 3 pulls down to leave, 4 does nothing.
                    var mode = 0
                    var grab = 0f
                    var turned = 0f
                    var last = down.position
                    try {
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            tracker.addPosition(change.uptimeMillis, change.position)
                            last = change.position
                            val moved = last - down.position
                            if (mode == 0 && moved.getDistance() > viewConfiguration.touchSlop) {
                                holding = true
                                when (tour.value) {
                                    0f -> {
                                        mode = 1
                                        turned = spin.value
                                        grab = onStage(last).let { atan2(it.y - CY, it.x - CX) }
                                    }
                                    1f -> {
                                        mode = if (abs(moved.x) > abs(moved.y)) 2 else 3
                                        grab = page.value + moved.x / size.width
                                    }
                                    else -> mode = 4
                                }
                            }
                            if (mode == 1) {
                                // The rings turn by the angle the finger sweeps round the centre.
                                val angle = onStage(last).let { atan2(it.y - CY, it.x - CX) }
                                var swept = angle - grab
                                swept -= 2f * PI_F * (swept / (2f * PI_F)).roundToInt()
                                turned += swept
                                grab = angle
                                val to = turned
                                scope.launch { spin.snapTo(to) }
                            } else if (mode == 2) {
                                val to = (grab - moved.x / size.width).coerceIn(1f - OVER, 3f + OVER)
                                scope.launch { page.snapTo(to) }
                            }
                            if (mode != 0) change.consume()
                        }
                        val velocity = tracker.calculateVelocity()
                        val moved = last - down.position
                        when (mode) {
                            0 -> {
                                val at = onStage(last)
                                if (tour.value == 0f) {
                                    val hit = Coins.indices.firstOrNull { (at - coinPlace(it, phase(), spin.value)).getDistance() < COIN + 10f }
                                    if (hit != null) pick(hit + 1)
                                } else if (tour.value == 1f) {
                                    // The coin at the top of a scene is the way back.
                                    val front = page.value.roundToInt()
                                    if ((at - Offset(CX, CoinTop[front - 1])).getDistance() < COIN + 10f) leave()
                                }
                            }
                            1 -> {
                                // They coast on at the speed the finger had round the centre.
                                val r = onStage(last) - Offset(CX, CY)
                                val speed = (r.x * velocity.y - r.y * velocity.x) / unit / max(1f, r.x * r.x + r.y * r.y)
                                val to = turned + speed * COAST_REACH
                                scope.launch { spin.animateTo(to, tween((COAST * 1000).toInt(), easing = CoastCurve)) }
                            }
                            2 -> {
                                val at = (grab - moved.x / size.width).coerceIn(1f - OVER, 3f + OVER)
                                settle((at - velocity.x / size.width * FLICK).roundToInt().coerceIn(1, 3))
                            }
                            3 -> if (moved.y > LEAVE.dp.toPx()) leave()
                        }
                    } finally {
                        if (mode != 0) {
                            holding = false
                            touches++
                        }
                    }
                }
            }
            .semantics {
                contentDescription = if (scene == 0) caption.replace('\n', ' ') else scenes[scene - 1].title
                customActions = listOf(
                    CustomAccessibilityAction("Next") {
                        if (scene == 0) pick(1) else if (scene < 3) settle(scene + 1)
                        true
                    },
                    CustomAccessibilityAction("Previous") {
                        if (scene == 1) leave() else if (scene > 1) settle(scene - 1)
                        true
                    },
                )
            },
    ) {
        val stageScale = min(maxWidth.value / STAGE_WIDTH, maxHeight.value / STAGE_HEIGHT)

        // The orbit. The scenes take over at the end of the push, when the disc has become the card's top.
        Box(Modifier.matchParentSize().graphicsLayer { alpha = if (still) 1f - tour.value else 1f - smooth(tour.value, 0.85f, 1f) }) {
            Canvas(Modifier.matchParentSize()) {
                surface(0.dp) { _, _, w, h -> drawOrbit(w, h, stageScale, if (still) 0f else tour.value, hero, phase(), spin.value) }
            }
            Stage(stageScale) {
                BasicText(
                    caption,
                    style = CaptionStyle,
                    modifier = Modifier.offset(y = 683.dp).size(STAGE_WIDTH.dp, 86.dp).graphicsLayer { alpha = 1f - smooth(tour.value, 0f, 0.3f) },
                )
            }
        }

        Box(Modifier.matchParentSize().clipToBounds().graphicsLayer { alpha = if (still) tour.value else smooth(tour.value, 0.8f, 0.95f) }) {
            for (index in 1..3) {
                Box(
                    Modifier
                        .matchParentSize()
                        .graphicsLayer {
                            // Reduced motion: scenes cross-fade in place.
                            val away = index - page.value
                            if (still) alpha = 1f - min(1f, abs(away)) else translationX = away * size.width
                        }
                        .background(Grounds[index - 1]),
                ) {
                    Stage(stageScale) {
                        val reveal = { if (still) 1f else reveals[index - 1].value }
                        when (index) {
                            1 -> Savings(reveal, scenes[0], balance, symbol, enabled = scene == 1) { onAction?.invoke() }
                            2 -> PayCard(reveal, scenes[1])
                            else -> Insights(reveal, scenes[2], balance, symbol)
                        }
                        // The coin arrives turning: edge-on half-way, face-on when the scene is in front.
                        Canvas(Modifier.offset((CX - COIN_BOX / 2f).dp, (CoinTop[index - 1] - COIN_BOX / 2f).dp).size(COIN_BOX.dp)) {
                            surface(0.dp) { _, _, w, h ->
                                val flip = if (still) 1f else cos(PI_F * (index - page.value).coerceIn(-1f, 1f))
                                drawCoin(w / 2f, h / 2f, COIN, flip, Coins[index - 1].icon, 1f)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The stage: 393 × 852 dp, scaled to fit and centred, whatever the screen's size. */
@Composable
private fun BoxScope.Stage(scale: Float, content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier
            .align(Alignment.Center)
            .requiredSize(STAGE_WIDTH.dp, STAGE_HEIGHT.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        content = content,
    )
}
