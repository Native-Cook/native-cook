package com.ngocdevv.nativecook.blocks.gestures.hoopshotrefresh

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SportsBasketball
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.cssBoxShadow
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/** One drill card of the Courtside feed. */
data class HoopShotRefreshDrill(
    val id: Int,
    val title: String,
    val coach: String,
    val focus: String,
    val minutes: Int,
    val tintFrom: Long,
    val tintTo: Long,
) {
    companion object {
        /** Four cards: the whole feed fits above the tab bar at 411×891 dp, so no card is ever cut. */
        val samples = listOf(
            HoopShotRefreshDrill(0, "Crossover Ladder", "Coach Dana Reyes", "Ball handling", 12, 0xFFFF7A9E, 0xFFE8457A),
            HoopShotRefreshDrill(1, "Corner Three Reps", "Coach Malik Osei", "Shooting", 18, 0xFF6C6CFF, 0xFF3A3AD6),
            HoopShotRefreshDrill(2, "Mikan Drill", "Coach Priya Nair", "Finishing", 8, 0xFFFFB347, 0xFFFF8A3D),
            HoopShotRefreshDrill(3, "Defensive Slides", "Coach Tom Varga", "Footwork", 10, 0xFF3CC8A8, 0xFF1FA88A),
        )
    }
}

private object HoopShotRefreshPalette {
    val bar = Color(0xFFE8457A)
    val ball = Color(0xFFF0558A)
    val ballLight = Color(0xFFFF8DB1)
    val ballDark = Color(0xFFD63A6F)
    val seam = Color(0xFFC83468)
    val court = Color(0xFF2B2B30)
    val courtEdge = Color(0xFF1F1F24)
    val card = Color(0xFFFFFFFF)
    val cardTint = Color(0xFFFCE4EC)
    val ink = Color(0xFF1E1E22)
    val mute = Color(0xFF7A7A82)
    val label = Color(0xFF9A9AA2)
    val tab = Color(0xFFF2F2F4)
    val tabLine = Color(0xFFE2E2E6)
    val tabIcon = Color(0xFFB0B0B6)
    val board = Color(0xFFF4F4F6)
    val boardSquare = Color(0xFFD6D6DC)
    val bracket = Color(0xFFC9C9D0)
    val net = Color.White.copy(alpha = 0.85f)
}

/** Sizes in dp, the same numbers as the SwiftUI reference in pt. */
internal object HoopShotRefreshMetrics {
    const val BAR_CONTENT = 56.0
    const val TAB_CONTENT = 56.0
    const val MARGIN = 20.0
    const val CARD_HEIGHT = 118.0
    const val CARD_GAP = 14.0
    const val CARD_RADIUS = 18.0
    const val LABEL_TOP = 18.0
    const val FIRST_CARD = 44.0
    const val THRESHOLD = 150.0
    const val RUBBER_LIMIT = 420.0
    const val HOOP_TOP = 14.0
    const val BOARD_WIDTH = 72.0
    const val BOARD_HEIGHT = 44.0
    const val RIM_GAP = 8.0
    const val RIM_RX = 28.0
    const val RIM_RY = 7.0
    const val NET_LENGTH = 40.0
    const val NET_STRETCH = 28.0
    const val CHARGE_MIN = 24.0
    const val CHARGE_MAX = 140.0
    const val FLIGHT_RADIUS = 15.0
    const val DROP_RADIUS = 9.0
    const val DROP_DEPTH = 100.0
}

internal object HoopShotRefreshMotion {
    const val LOOP = 8.0
    const val PULL_FROM = 1.0
    const val PULL_TO = 2.5
    const val RELEASE_AT = 2.8
    // The shot sequence, in seconds after the release.
    const val FLIGHT_AT = 0.2
    const val FLIGHT_FOR = 0.8
    const val DROP_AT = 1.0
    const val DROP_FOR = 0.6
    const val NET_AT = 1.1
    const val NET_FOR = 0.45
    const val CLOSE_AT = 2.0
    const val CLOSE_FOR = 0.6
    const val FLASH_AT = 2.15
    const val FLASH_FOR = 0.35
    const val STAGGER = 0.06
    const val POP_FOR = 0.6
    const val DONE = 3.1
    /** The "Updated just now" note stays this long after the feed has closed. */
    const val NOTE_FOR = 1.5
    const val NOTE_FADE = 0.3
    const val REDUCED_CLOSE_AT = 0.1
    const val REDUCED_DONE = 0.9
    /** Seconds after the last touch at which the screen eases back to rest and the loop restarts. */
    const val RESUME = 2.0
    /** The ease back to rest: the feed closes as after a shot, the cards still flash, the note shows briefly. */
    const val SETTLE_FOR = 1.0
    const val SETTLE_FADE = 0.3
    /** A drag starts once the finger has travelled this far; before that a touch only holds the screen. */
    const val DRAG_SLOP = 8.0

    fun clamp(t: Double) = min(1.0, max(0.0, t))
    fun seg(t: Double, a: Double, b: Double) = clamp((t - a) / (b - a))
    fun easeInOutCubic(x: Double) = if (x < 0.5) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2
    fun easeOutCubic(x: Double) = 1 - (1 - x).pow(3)
    fun easeOutQuad(x: Double) = 1 - (1 - x) * (1 - x)
    fun easeInQuad(x: Double) = x * x
    fun easeOutBack(x: Double) = 1 + 2.2 * (x - 1).pow(3) + 1.2 * (x - 1).pow(2)
    /** Finger travel to feed offset: direct at first, giving less the further it goes. */
    fun rubber(dy: Double): Double {
        val d = max(0.0, dy) * 0.6
        return d / (1 + d / HoopShotRefreshMetrics.RUBBER_LIMIT)
    }
}

internal data class HoopShotRefreshBall(
    val x: Double,
    val y: Double,
    val r: Double,
    val opacity: Double,
    val seam: Double,
    /** In front of the feed (charging, flying) or behind it (falling away). */
    val front: Boolean,
)

/** Everything one frame draws, derived from a time (the loop's or a touch's). */
internal data class HoopShotRefreshFrame(
    val pull: Double = 0.0,
    val ball: HoopShotRefreshBall? = null,
    val net: Double = 0.0,
    val flash: Double = 0.0,
    /** Seconds since the cards started their refresh flash; negative when they have not. */
    val refresh: Double = -1.0,
    val hint: Double = 0.0,
    /** Opacity of the "Updated just now" note that replaces "Today" after a refresh. */
    val note: Double = 0.0,
) {
    companion object { val REST = HoopShotRefreshFrame() }
}

/** The pure functions behind the frames: the same arithmetic serves the loop and the finger. */
internal object HoopShotRefreshScene {
    private val S = HoopShotRefreshMetrics
    private val M = HoopShotRefreshMotion

    /** The ball as it charges with the pull, rising from behind the tab bar. */
    fun charge(pull: Double, cx: Double, tabTop: Double): HoopShotRefreshBall {
        val k = M.clamp(pull / S.THRESHOLD)
        val r = S.CHARGE_MIN + (S.CHARGE_MAX - S.CHARGE_MIN) * k
        return HoopShotRefreshBall(cx, tabTop + 30 - r * 0.15, r, 1.0, 0.3 + k * 0.4, true)
    }

    /** The loop's pull: out to the threshold with a small overshoot, then held until the release. */
    fun loopPull(t: Double): Double {
        val u = M.seg(t, M.PULL_FROM, M.PULL_TO)
        if (u < 0.77) return 162 * M.easeInOutCubic(u / 0.77)
        return 162 - 12 * M.easeOutQuad((u - 0.77) / 0.23)
    }

    /** The shot, `s` seconds after the release from a feed offset `p0`. */
    fun sequence(s: Double, p0: Double, cx: Double, cavityTop: Double, tabTop: Double, reduced: Boolean): HoopShotRefreshFrame {
        if (reduced) {
            val close = M.easeInOutCubic(M.seg(s, M.REDUCED_CLOSE_AT, M.REDUCED_CLOSE_AT + 0.3))
            return HoopShotRefreshFrame(
                pull = p0 * (1 - close),
                refresh = if (s >= M.REDUCED_CLOSE_AT + 0.05) s - (M.REDUCED_CLOSE_AT + 0.05) else -1.0,
                note = M.seg(s, M.REDUCED_CLOSE_AT, M.REDUCED_CLOSE_AT + M.NOTE_FADE) *
                    (1 - M.seg(s, M.REDUCED_DONE + M.NOTE_FOR, M.REDUCED_DONE + M.NOTE_FOR + M.NOTE_FADE)),
            )
        }
        // The feed settles on the threshold and holds there while the ball is in the air.
        val held = S.THRESHOLD + (p0 - S.THRESHOLD) * exp(-s * 10)
        val close = M.easeInOutCubic(M.seg(s, M.CLOSE_AT, M.CLOSE_AT + M.CLOSE_FOR))
        val pull = held * (1 - close)
        val hint = 1 - M.seg(s, 0.0, M.FLIGHT_AT)

        val rimY = cavityTop + S.HOOP_TOP + S.BOARD_HEIGHT + S.RIM_GAP + S.RIM_RY
        val apexY = rimY - 14
        var ball: HoopShotRefreshBall? = null
        if (s < M.FLIGHT_AT) {
            // Anticipation: a short dip before the launch.
            val b = charge(S.THRESHOLD, cx, tabTop)
            ball = b.copy(y = b.y + 6 * sin(PI * s / M.FLIGHT_AT))
        } else if (s < M.DROP_AT) {
            val u = M.seg(s, M.FLIGHT_AT, M.FLIGHT_AT + M.FLIGHT_FOR)
            val start = charge(S.THRESHOLD, cx, tabTop)
            val y = start.y + (apexY - start.y) * M.easeOutQuad(u)
            val r = start.r + (S.FLIGHT_RADIUS - start.r) * M.easeOutQuad(u)
            ball = HoopShotRefreshBall(cx + 26 * sin(PI * u), y, r, 1.0, 0.7 + u * 2.2, true)
        } else if (s < M.DROP_AT + M.DROP_FOR) {
            val d = M.seg(s, M.DROP_AT, M.DROP_AT + M.DROP_FOR)
            val y = apexY + S.DROP_DEPTH * M.easeInQuad(d)
            val r = S.FLIGHT_RADIUS + (S.DROP_RADIUS - S.FLIGHT_RADIUS) * d
            ball = HoopShotRefreshBall(cx, y, r, 1 - M.seg(d, 0.7, 1.0), 2.9 + d, false)
        }
        // The net stretches as the ball passes, then wobbles back.
        var net = 0.0
        if (s >= M.NET_AT && s < M.NET_AT + M.NET_FOR) {
            net = sin(PI * M.seg(s, M.NET_AT, M.NET_AT + M.NET_FOR))
        } else if (s >= M.NET_AT + M.NET_FOR) {
            val e = s - (M.NET_AT + M.NET_FOR)
            net = 0.12 * exp(-8 * e) * sin(2 * PI * e * 5)
        }
        return HoopShotRefreshFrame(
            pull = pull, ball = ball, net = net,
            flash = M.seg(s, M.NET_AT, M.NET_AT + 0.3),
            refresh = if (s >= M.FLASH_AT) s - M.FLASH_AT else -1.0,
            hint = hint,
            note = M.seg(s, M.FLASH_AT, M.FLASH_AT + M.NOTE_FADE) * (1 - M.seg(s, M.DONE + M.NOTE_FOR, M.DONE + M.NOTE_FOR + M.NOTE_FADE)),
        )
    }

    fun loopFrame(t: Double, cx: Double, cavityTop: Double, tabTop: Double): HoopShotRefreshFrame {
        if (t < M.PULL_FROM) return HoopShotRefreshFrame.REST
        if (t < M.RELEASE_AT) {
            val pull = loopPull(t)
            return HoopShotRefreshFrame(pull = pull, ball = charge(pull, cx, tabTop), hint = M.seg(pull, 110.0, 140.0))
        }
        val s = t - M.RELEASE_AT
        // The same sequence as a hand shot, note included; rest again at 7.7 s.
        if (s >= M.DONE + M.NOTE_FOR + M.NOTE_FADE) return HoopShotRefreshFrame.REST
        return sequence(s, S.THRESHOLD, cx, cavityTop, tabTop, false)
    }

    /** The feed springing back from `p0` after a release short of the threshold: critically damped, at rest after about 0.6 s. */
    fun cancelFrame(e: Double, p0: Double, cx: Double, tabTop: Double, reduced: Boolean): HoopShotRefreshFrame {
        val k = 9.0
        val pull = p0 * (1 + k * e) * exp(-k * e)
        if (pull < 0.5) return HoopShotRefreshFrame.REST
        return HoopShotRefreshFrame(pull = pull, ball = if (reduced) null else charge(pull, cx, tabTop), hint = M.seg(pull, 110.0, 140.0))
    }

    /**
     * Whatever the screen showed at the resume deadline (`f0`), `e` seconds later, on its way back to rest.
     * The feed closes as after a shot; the ball, net, hint and note fade. A shot that had not refreshed
     * the feed yet (`refreshing`) still does: the cards flash as the feed closes and the note shows briefly.
     */
    fun settleFrame(e: Double, f0: HoopShotRefreshFrame, refreshing: Boolean): HoopShotRefreshFrame {
        val fade = 1 - M.seg(e, 0.0, M.SETTLE_FADE)
        var pull = f0.pull * (1 - M.easeInOutCubic(M.seg(e, 0.0, M.CLOSE_FOR)))
        if (pull < 0.5) pull = 0.0
        val ball = f0.ball?.takeIf { fade > 0 }?.let { it.copy(opacity = it.opacity * fade) }
        val refresh: Double
        val note: Double
        if (f0.refresh >= 0) {
            refresh = f0.refresh + e
            note = f0.note * fade
        } else if (refreshing) {
            val flashAt = M.FLASH_AT - M.CLOSE_AT
            refresh = if (e >= flashAt) e - flashAt else -1.0
            note = M.seg(e, flashAt, flashAt + M.NOTE_FADE) * (1 - M.seg(e, M.SETTLE_FOR - M.NOTE_FADE, M.SETTLE_FOR))
        } else {
            refresh = -1.0
            note = f0.note * fade
        }
        return HoopShotRefreshFrame(pull = pull, ball = ball, net = f0.net * fade, refresh = refresh, hint = f0.hint * fade, note = note)
    }
}

private sealed interface HoopShotRefreshMode {
    data object Loop : HoopShotRefreshMode
    data class Pulling(val p: Double) : HoopShotRefreshMode
    data class Cancel(val p0: Double, val at: Long) : HoopShotRefreshMode
    data class Shot(val p0: Double, val at: Long) : HoopShotRefreshMode
    /** Easing what the screen showed at the resume deadline back to rest, before the loop restarts. */
    data class Settle(val from: HoopShotRefreshFrame, val refreshing: Boolean, val at: Long) : HoopShotRefreshMode
}

/** A basketball feed whose pull-to-refresh charges a ball and, on release, shoots it through the hoop. */
@Composable
fun HoopShotRefresh(
    drills: List<HoopShotRefreshDrill> = HoopShotRefreshDrill.samples,
    autoPlay: Boolean = false,
    title: String = "Courtside",
    sectionLabel: String = "Latest drills",
    onRefresh: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val S = HoopShotRefreshMetrics
    val M = HoopShotRefreshMotion
    val P = HoopShotRefreshPalette
    // Observed while mounted, so turning Remove animations on in Settings stops the loop at once.
    val reduceMotion = rememberLiveReducedMotion()
    val reduced by rememberUpdatedState(reduceMotion)
    val playing by rememberUpdatedState(autoPlay)
    val refreshed by rememberUpdatedState(onRefresh)
    val view = LocalView.current
    val density = LocalDensity.current.density
    val textMeasurer = rememberTextMeasurer()
    BlockSystemBars(darkIcons = false)

    var mode by remember { mutableStateOf<HoopShotRefreshMode>(HoopShotRefreshMode.Loop) }
    var origin by remember { mutableLongStateOf(System.nanoTime()) }
    // A finger is on the screen: the loop never restarts while it is.
    var touchDown by remember { mutableStateOf(false) }
    // The finger has travelled past the slop and owns the pull.
    var dragging by remember { mutableStateOf(false) }
    var base by remember { mutableStateOf(0.0) }
    var hapticFired by remember { mutableStateOf(false) }
    // Counts touch ends (lifts and cancellations); each one restarts the resume deadline.
    var touchEnds by remember { mutableIntStateOf(0) }
    var frame by remember { mutableStateOf(HoopShotRefreshFrame.REST) }

    BoxWithConstraints(modifier.fillMaxSize().background(P.court)) {
        val width = maxWidth.value.toDouble()
        val height = maxHeight.value.toDouble()
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        val topInset = insets.calculateTopPadding().value.toDouble()
        val bottomInset = insets.calculateBottomPadding().value.toDouble()
        val barHeight = topInset + S.BAR_CONTENT
        val tabTop = height - bottomInset - S.TAB_CONTENT
        val cx = width / 2

        fun frameAt(now: Long): HoopShotRefreshFrame = when (val m = mode) {
            HoopShotRefreshMode.Loop -> {
                if (!autoPlay || reduced) HoopShotRefreshFrame.REST
                else HoopShotRefreshScene.loopFrame(((now - origin) / 1e9).coerceAtLeast(0.0) % M.LOOP, cx, barHeight, tabTop)
            }
            is HoopShotRefreshMode.Pulling -> HoopShotRefreshFrame(
                pull = m.p,
                ball = if (reduced) null else HoopShotRefreshScene.charge(m.p, cx, tabTop),
                hint = M.seg(m.p, 110.0, 140.0),
            )
            is HoopShotRefreshMode.Cancel -> HoopShotRefreshScene.cancelFrame((now - m.at) / 1e9, m.p0, cx, tabTop, reduced)
            is HoopShotRefreshMode.Shot -> {
                val s = (now - m.at) / 1e9
                if (s >= (if (reduced) M.REDUCED_DONE else M.DONE) + M.NOTE_FOR + M.NOTE_FADE) HoopShotRefreshFrame.REST
                else HoopShotRefreshScene.sequence(s, m.p0, cx, barHeight, tabTop, reduced)
            }
            is HoopShotRefreshMode.Settle -> {
                val e = (now - m.at) / 1e9
                if (e >= M.SETTLE_FOR) HoopShotRefreshFrame.REST
                else HoopShotRefreshScene.settleFrame(e, m.from, m.refreshing)
            }
        }
        val latestFrameAt by rememberUpdatedState(::frameAt)

        LaunchedEffect(Unit) {
            while (true) {
                val now = withFrameNanos { it }
                frame = latestFrameAt(now)
            }
        }

        // The finger lands: it takes the screen over where it is, so nothing jumps. A pull in progress
        // is held, a shot in the air keeps flying, and a settle hands its offset to the finger.
        fun touchBegan(now: Long) {
            touchDown = true
            dragging = false
            hapticFired = false
            when (mode) {
                HoopShotRefreshMode.Loop -> {
                    val t = ((now - origin) / 1e9).coerceAtLeast(0.0) % M.LOOP
                    mode = if (!playing || reduced || t < M.RELEASE_AT) {
                        HoopShotRefreshMode.Pulling(if (playing && !reduced) HoopShotRefreshScene.loopPull(t) else 0.0)
                    } else {
                        HoopShotRefreshMode.Shot(S.THRESHOLD, now - ((t - M.RELEASE_AT) * 1e9).toLong())
                    }
                }
                is HoopShotRefreshMode.Settle -> mode = HoopShotRefreshMode.Pulling(latestFrameAt(now).pull)
                else -> {}
            }
        }

        fun touchMoved(now: Long, dy: Double) {
            if (!dragging) {
                if (abs(dy) < M.DRAG_SLOP) return
                dragging = true
                base = latestFrameAt(now).pull
            }
            val p = base + M.rubber(dy)
            mode = HoopShotRefreshMode.Pulling(p)
            if (p >= S.THRESHOLD && !hapticFired) {
                hapticFired = true
                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            }
        }

        // The finger lifts or the touch is cancelled. Only a lift past the threshold refreshes; a
        // cancelled touch springs back without a shot.
        fun touchEnded(now: Long, cancelled: Boolean) {
            touchDown = false
            val m = mode
            if (m is HoopShotRefreshMode.Pulling) {
                if (!cancelled && dragging && m.p >= S.THRESHOLD) {
                    mode = HoopShotRefreshMode.Shot(m.p, now)
                    refreshed?.invoke()
                } else {
                    mode = HoopShotRefreshMode.Cancel(m.p, now)
                }
            }
            dragging = false
            touchEnds++
        }

        fun refreshByHand() {
            dragging = false
            mode = HoopShotRefreshMode.Shot(S.THRESHOLD, System.nanoTime())
            refreshed?.invoke()
            touchEnds++
        }

        // Two seconds after the last touch the screen eases back to rest (at once if it is there
        // already), and the loop starts again from rest. A finger landing in the meantime holds everything.
        LaunchedEffect(touchEnds) {
            if (touchEnds == 0) { origin = System.nanoTime(); return@LaunchedEffect }
            delay((M.RESUME * 1000).toLong())
            if (touchDown) return@LaunchedEffect
            val now = System.nanoTime()
            val f0 = latestFrameAt(now)
            if (f0 == HoopShotRefreshFrame.REST) {
                origin = now
                mode = HoopShotRefreshMode.Loop
                return@LaunchedEffect
            }
            mode = HoopShotRefreshMode.Settle(f0, mode is HoopShotRefreshMode.Shot, now)
            delay((M.SETTLE_FOR * 1000).toLong())
            if (touchDown) return@LaunchedEffect
            origin = System.nanoTime()
            mode = HoopShotRefreshMode.Loop
        }

        Box(
            Modifier.fillMaxSize()
                // Raw pointer events, not detectDragGestures: a tap or a hold must pause the loop too,
                // and a cancelled touch (consumed elsewhere, or reset by the system) must be told apart
                // from a lift.
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val startY = down.position.y
                        var settled = false
                        touchBegan(System.nanoTime())
                        try {
                            var lifted = false
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) { lifted = true; break }
                                if (change.isConsumed) break
                                val dy = ((change.position.y - startY) / density).toDouble()
                                touchMoved(System.nanoTime(), dy)
                                if (dragging) change.consume()
                            }
                            settled = true
                            touchEnded(System.nanoTime(), cancelled = !lifted)
                        } finally {
                            // The pointer input was reset (an ACTION_CANCEL, or the block left the screen).
                            if (!settled) touchEnded(System.nanoTime(), cancelled = true)
                        }
                    }
                },
        ) {
            // The court, the hoop and the ball falling away: everything behind the feed.
            Canvas(Modifier.fillMaxSize()) {
                drawRect(P.court)
                drawRect(
                    Brush.radialGradient(
                        0.45f to P.court.copy(alpha = 0f), 1f to P.courtEdge,
                        center = Offset((cx * density).toFloat(), ((barHeight + 90) * density).toFloat()),
                        radius = (width * 0.9 * density).toFloat(),
                    ),
                )
                val f = frame
                if (f.pull <= 0.5) return@Canvas
                scale(density, density, Offset.Zero) {
                    clipRect(0f, barHeight.toFloat(), width.toFloat(), (barHeight + f.pull).toFloat()) {
                        drawCourt(f, cx, barHeight)
                    }
                }
                if (f.hint > 0) {
                    val ready = f.pull >= S.THRESHOLD
                    val text = if (ready) "Release to refresh" else "Pull to shoot"
                    val layout = textMeasurer.measure(text, TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = (0.55 * f.hint).toFloat())))
                    val rimY = barHeight + S.HOOP_TOP + S.BOARD_HEIGHT + S.RIM_GAP + S.RIM_RY
                    val cy = (rimY + S.NET_LENGTH + 22) * density
                    clipRect(0f, (barHeight * density).toFloat(), (width * density).toFloat(), ((barHeight + f.pull) * density).toFloat()) {
                        drawText(layout, topLeft = Offset((cx * density - layout.size.width / 2).toFloat(), (cy - layout.size.height / 2).toFloat()))
                    }
                }
            }

            // The feed slides down with the pull.
            Box(Modifier.fillMaxSize().clipToBounds()) {
                Column(
                    Modifier.fillMaxWidth().graphicsLayer { translationY = ((barHeight + frame.pull) * density).toFloat() },
                ) {
                    Row(
                        Modifier.padding(top = S.LABEL_TOP.dp, start = (S.MARGIN + 2).dp, end = (S.MARGIN + 2).dp).fillMaxWidth().height(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BasicText(sectionLabel.uppercase(), style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.1.em, color = P.label), maxLines = 1)
                        Spacer(Modifier.weight(1f))
                        // The one focusable control: a screen reader refreshes from here without a pull.
                        Box(
                            Modifier.semantics(mergeDescendants = true) {
                                role = Role.Button
                                contentDescription = "Refresh"
                                onClick { refreshByHand(); true }
                            },
                            contentAlignment = Alignment.CenterEnd,
                        ) {
                            BasicText("Today", style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = P.label), modifier = Modifier.graphicsLayer { alpha = (1 - frame.note).toFloat() })
                            BasicText("Updated just now", style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = P.bar), modifier = Modifier.graphicsLayer { alpha = frame.note.toFloat() })
                        }
                    }
                    Spacer(Modifier.height((S.FIRST_CARD - S.LABEL_TOP - 14).dp))
                    drills.forEachIndexed { index, drill ->
                        DrillCard(drill, index, { frame.refresh }, reduceMotion, Modifier.padding(start = S.MARGIN.dp, end = S.MARGIN.dp, bottom = S.CARD_GAP.dp))
                    }
                }
            }

            // The feed steps back while the court is open, so the shot owns the screen.
            Box(
                Modifier.offset(y = barHeight.dp).fillMaxWidth().height(max(0.0, tabTop - barHeight).dp)
                    .graphicsLayer { alpha = (0.35 * M.seg(frame.pull, 0.0, S.THRESHOLD)).toFloat() }
                    .background(P.courtEdge),
            )

            // The ball charging and flying: in front of the feed.
            Canvas(Modifier.fillMaxSize()) {
                val ball = frame.ball ?: return@Canvas
                if (!ball.front) return@Canvas
                scale(density, density, Offset.Zero) { drawBall(ball) }
            }

            TabBar(tabTop, bottomInset)
            TopBar(title, barHeight, topInset)
        }
    }
}

private fun DrawScope.drawCourt(f: HoopShotRefreshFrame, cx: Double, cavityTop: Double) {
    val S = HoopShotRefreshMetrics
    val M = HoopShotRefreshMotion
    val P = HoopShotRefreshPalette
    val boardTop = cavityTop + S.HOOP_TOP
    val rimY = boardTop + S.BOARD_HEIGHT + S.RIM_GAP + S.RIM_RY
    // Backboard, square and bracket.
    drawRoundRect(P.board, Offset((cx - S.BOARD_WIDTH / 2).toFloat(), boardTop.toFloat()), Size(S.BOARD_WIDTH.toFloat(), S.BOARD_HEIGHT.toFloat()), CornerRadius(6f))
    drawRoundRect(P.boardSquare, Offset((cx - 15).toFloat(), (boardTop + 14).toFloat()), Size(30f, 22f), CornerRadius(2f), style = Stroke(2f))
    drawRect(P.bracket, Offset((cx - 5).toFloat(), (boardTop + S.BOARD_HEIGHT - 2).toFloat()), Size(10f, (S.RIM_GAP + 2).toFloat()))
    // Back half of the rim.
    drawPath(rimArc(cx, rimY, 180.0, 360.0), P.ballDark, style = Stroke(4f))
    if (f.flash > 0 && f.flash < 1) {
        val k = (1 + 0.6 * M.easeOutQuad(f.flash)).toFloat()
        scale(k, k, Offset(cx.toFloat(), rimY.toFloat())) {
            drawPath(rimArc(cx, rimY, 0.0, 360.0), Color.White.copy(alpha = (0.6 * (1 - f.flash)).toFloat()), style = Stroke(1.5f / k))
        }
    }
    f.ball?.let { if (!it.front) drawBall(it) }
    // Front half of the rim and the net.
    drawPath(rimArc(cx, rimY, 0.0, 180.0), P.bar, style = Stroke(4f))
    drawNet(cx, rimY, f.net)
}

private fun rimArc(cx: Double, y: Double, from: Double, to: Double): Path {
    val S = HoopShotRefreshMetrics
    return Path().apply {
        addArc(Rect((cx - S.RIM_RX).toFloat(), (y - S.RIM_RY).toFloat(), (cx + S.RIM_RX).toFloat(), (y + S.RIM_RY).toFloat()), from.toFloat(), (to - from).toFloat())
    }
}

private fun DrawScope.drawNet(cx: Double, rimY: Double, stretch: Double) {
    val S = HoopShotRefreshMetrics
    val P = HoopShotRefreshPalette
    val s = stretch
    val bottomY = rimY + S.NET_LENGTH + S.NET_STRETCH * s
    val bottomRx = 15 + 5 * s
    val bottomRy = 4.0
    val strands = Path()
    for (i in 0 until 7) {
        val a = PI * i / 6
        val topX = cx + S.RIM_RX * cos(a); val topY = rimY + S.RIM_RY * sin(a)
        val botX = cx + bottomRx * cos(a); val botY = bottomY + bottomRy * sin(a)
        val midX = (topX + botX) / 2; val midY = (topY + botY) / 2
        val inward = (4 * (1 - s) - 6 * s) * cos(a)
        strands.moveTo(topX.toFloat(), topY.toFloat())
        strands.quadraticTo((midX - inward).toFloat(), midY.toFloat(), botX.toFloat(), botY.toFloat())
    }
    drawPath(strands, P.net, style = Stroke(1.5f))
    for (f in doubleArrayOf(0.38, 0.72, 1.0)) {
        val rx = S.RIM_RX + (bottomRx - S.RIM_RX) * f
        val ry = S.RIM_RY + (bottomRy - S.RIM_RY) * f
        val y = rimY + (bottomY - rimY) * f
        drawOval(P.net, Offset((cx - rx).toFloat(), (y - ry).toFloat()), Size((rx * 2).toFloat(), (ry * 2).toFloat()), style = Stroke(if (f == 1.0) 1.5f else 1.2f))
    }
}

private fun DrawScope.drawBall(ball: HoopShotRefreshBall) {
    if (ball.opacity <= 0) return
    val P = HoopShotRefreshPalette
    val r = ball.r.toFloat()
    val x = ball.x.toFloat(); val y = ball.y.toFloat()
    val a = ball.opacity.toFloat()
    drawCircle(
        Brush.radialGradient(
            0f to P.ballLight, 0.5f to P.ball, 1f to P.ballDark,
            center = Offset(x - 0.35f * r, y - 0.35f * r), radius = 1.5f * r,
        ),
        radius = r, center = Offset(x, y), alpha = a,
    )
    val w = min(3f, max(1.2f, r / 45f))
    val clip = Path().apply { addOval(Rect(x - r + 0.5f, y - r + 0.5f, x + r - 0.5f, y + r - 0.5f)) }
    clipPath(clip) {
        translate(x, y) {
            rotate(Math.toDegrees(ball.seam).toFloat(), Offset.Zero) {
                drawOval(P.seam, Offset(-0.42f * r, -r), Size(0.84f * r, 2 * r), alpha = 0.8f * a, style = Stroke(w))
                drawOval(P.seam, Offset(-r, -0.42f * r), Size(2 * r, 0.84f * r), alpha = 0.8f * a, style = Stroke(w))
            }
        }
    }
    drawCircle(P.ballDark, radius = r - w / 2, center = Offset(x, y), alpha = 0.5f * a, style = Stroke(w))
}

@Composable
private fun DrillCard(drill: HoopShotRefreshDrill, index: Int, refresh: () -> Double, reduceMotion: Boolean, modifier: Modifier) {
    val S = HoopShotRefreshMetrics
    val M = HoopShotRefreshMotion
    val P = HoopShotRefreshPalette
    fun at() = refresh() - index * M.STAGGER
    val shape = RoundedCornerShape(S.CARD_RADIUS.dp)
    Box(
        modifier.fillMaxWidth().height(S.CARD_HEIGHT.dp)
            .graphicsLayer {
                val a = at()
                val pop = if (reduceMotion || a < 0) 1.0 else 0.94 + 0.06 * M.easeOutBack(M.clamp(a / M.POP_FOR))
                scaleX = pop.toFloat(); scaleY = pop.toFloat()
                alpha = (if (reduceMotion && a >= 0) 0.55 + 0.45 * M.clamp(a / 0.3) else 1.0).toFloat()
            }
            .cssBoxShadow(Color.Black.copy(alpha = 0.18f), offsetY = 6.dp, blur = 14.dp, radius = S.CARD_RADIUS.dp)
            .background(P.card, shape)
            .drawBehind {
                val a = at()
                val flash = if (reduceMotion || a < 0) 0.0 else sin(PI * M.clamp(a / M.FLASH_FOR))
                if (flash > 0) drawRoundRect(P.cardTint, cornerRadius = CornerRadius((S.CARD_RADIUS * density).toFloat()), alpha = (flash * 0.8).toFloat())
            }
            .padding(18.dp),
    ) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(
                Modifier.size(52.dp).background(Brush.linearGradient(listOf(Color(drill.tintFrom), Color(drill.tintTo))), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.SportsBasketball, contentDescription = null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(24.dp))
            }
            Column(Modifier.weight(1f).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                BasicText(drill.title, style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = P.ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
                BasicText(drill.coach, style = TextStyle(fontSize = 13.sp, color = P.mute), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.height(22.dp).background(P.cardTint, CircleShape).padding(horizontal = 9.dp), contentAlignment = Alignment.Center) {
                        BasicText("${drill.minutes} min", style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = P.bar), maxLines = 1)
                    }
                    BasicText(drill.focus, style = TextStyle(fontSize = 12.sp, color = P.mute), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun TopBar(title: String, height: Double, top: Double) {
    val S = HoopShotRefreshMetrics
    val P = HoopShotRefreshPalette
    Box(Modifier.fillMaxWidth().height(height.dp).background(P.bar)) {
        Row(
            Modifier.offset(y = top.dp).fillMaxWidth().height(S.BAR_CONTENT.dp).padding(horizontal = S.MARGIN.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.size(28.dp).background(Color(0xFF2A2A2E), CircleShape), contentAlignment = Alignment.Center) {
                Box(Modifier.size(12.dp).background(Color(0xFFF7D7E2), CircleShape))
            }
            Spacer(Modifier.weight(1f))
            for (icon in listOf(Icons.Filled.Notifications, Icons.Filled.Search)) {
                Box(Modifier.size(28.dp).background(Color.White.copy(alpha = 0.18f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
        }
        Box(Modifier.offset(y = top.dp).fillMaxWidth().height(S.BAR_CONTENT.dp), contentAlignment = Alignment.Center) {
            BasicText(title, style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp, color = Color.White), maxLines = 1)
        }
    }
}

@Composable
private fun TabBar(top: Double, bottom: Double) {
    val S = HoopShotRefreshMetrics
    val P = HoopShotRefreshPalette
    val items = listOf(
        Triple(Icons.Filled.Home, "Home", true),
        Triple(Icons.Filled.DirectionsRun, "Drills", false),
        Triple(Icons.Filled.BarChart, "Stats", false),
        Triple(Icons.Filled.Person, "Me", false),
    )
    Column(Modifier.offset(y = top.dp).fillMaxWidth().background(P.tab)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(P.tabLine))
        Row(Modifier.fillMaxWidth().height((S.TAB_CONTENT - 1).dp), verticalAlignment = Alignment.CenterVertically) {
            for ((icon, label, active) in items) {
                val color = if (active) P.bar else P.tabIcon
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(24.dp))
                    BasicText(label, style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = color))
                }
            }
        }
        Spacer(Modifier.height(bottom.dp))
    }
}
