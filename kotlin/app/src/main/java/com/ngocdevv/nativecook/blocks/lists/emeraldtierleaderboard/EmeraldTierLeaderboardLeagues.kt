package com.ngocdevv.nativecook.blocks.lists.emeraldtierleaderboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// ---------------------------------------------------------------------------------------------
// A scalar animation driven by the frame clock (Reanimated withSpring / withTiming / withDelay)
// ---------------------------------------------------------------------------------------------

internal class EmeraldAnimator {
    private var spring: EmeraldSpring? = null
    private var timingFrom = 0f
    private var timingTo = 0f
    private var timingMs = 0f
    private var delayMs = 0f
    private var instant = false
    private var startMs = -1.0
    private var onFinish: ((Boolean) -> Unit)? = null
    private var active = false

    val running: Boolean get() = active

    /** Cancelling reports finished = false in Reanimated; every callback of this block ignores that, so it is silent here. */
    fun cancel() {
        active = false
        spring = null
        onFinish = null
    }

    fun startSpring(
        from: Float, to: Float, velocity: Float, durationMs: Float, dampingRatio: Float,
        overshootClamping: Boolean, delayMs: Float = 0f, instant: Boolean = false, onFinish: ((Boolean) -> Unit)? = null,
    ) {
        cancel()
        spring = EmeraldSpring(from, to, velocity, durationMs, dampingRatio, overshootClamping)
        this.delayMs = delayMs
        this.instant = instant
        this.onFinish = onFinish
        startMs = -1.0
        active = true
    }

    fun startTiming(from: Float, to: Float, durationMs: Float, onFinish: ((Boolean) -> Unit)? = null) {
        cancel()
        timingFrom = from
        timingTo = to
        timingMs = durationMs
        delayMs = 0f
        instant = false
        this.onFinish = onFinish
        startMs = -1.0
        active = true
    }

    /**
     * Advances to [nowMs] and hands the value to [apply] BEFORE any finish callback runs, so a
     * callback that starts the next animation reads the final value. A finishing animation applies its target.
     */
    fun advance(nowMs: Double, apply: (Float) -> Unit) {
        if (!active) return
        if (startMs < 0) startMs = nowMs
        val elapsed = (nowMs - startMs).toFloat()
        val s = spring
        var finished = false
        val value: Float
        if (s != null) {
            if (elapsed < delayMs) {
                apply(s.from)
                return
            }
            if (instant) {
                value = s.to
                finished = true
            } else {
                val t = (elapsed - delayMs) / 1000f
                val x = s.valueAt(t)
                val crossed = s.overshootClamping && ((s.from < s.to && x > s.to) || (s.from > s.to && x < s.to))
                val v = (s.valueAt(t + .004f) - x) / .004f
                if (crossed || t >= s.maxSeconds || (abs(x - s.to) <= .01f && abs(v) <= .01f && t > 0f)) {
                    value = s.to
                    finished = true
                } else {
                    value = x
                }
            }
        } else {
            val p = if (timingMs <= 0f) 1f else min(1f, elapsed / timingMs)
            value = timingFrom + (timingTo - timingFrom) * p
            finished = p >= 1f
        }
        apply(value)
        if (finished) {
            val callback = onFinish
            active = false
            spring = null
            onFinish = null
            callback?.invoke(true)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// LeagueCrests.tsx
// ---------------------------------------------------------------------------------------------

internal enum class LeagueState { done, current, locked }

internal class League(
    val key: String, val name: String, val detail: String, val state: LeagueState, val chip: String,
    val tint: List<String>,
)

/** Leagues in climbing order; Emerald is the one this person is playing. */
internal val LEAGUES: List<League> = listOf(
    League("amber", "Amber League", "Completed · you finished 2nd of 8", LeagueState.done, "Promoted Sep 28", listOf("#E39A4E", "#9A5422", "#3A1E0C")),
    League("copper", "Copper League", "Completed · you finished 1st of 8", LeagueState.done, "Promoted Oct 12", listOf("#EA7A3C", "#A4401A", "#3A170B")),
    League("emerald", "Emerald League", "Top 40% advance to the next league", LeagueState.current, "3d 23h 15m left", listOf("#00C88E", "#008C67", "#064B37")),
    League("sapphire", "Sapphire League", "Finish in Emerald’s top 3 to unlock", LeagueState.locked, "Locked", listOf("#4E86E8", "#22508F", "#0B1F3B")),
    League("diamond", "Diamond League", "Unlocks after Sapphire", LeagueState.locked, "Locked", listOf("#A9C4D6", "#5C7787", "#18242C")),
)
internal const val CURRENT_LEAGUE = 2

private class SlotPoses(val x: FloatArray, val y: FloatArray, val scale: FloatArray, val rotate: FloatArray, val ghost: FloatArray)

/** Slot poses of the original composition by distance from focus (left and right differ in the design). */
private val LEFT = SlotPoses(
    floatArrayOf(0f, 104.5f, 180.5f), floatArrayOf(0f, 4f, 18f), floatArrayOf(1f, 0.643f, 0.571f),
    floatArrayOf(0f, 12f, 17f), floatArrayOf(1f, 0.29f, 0.24f),
)
private val RIGHT = SlotPoses(
    floatArrayOf(0f, 95f, 168f), floatArrayOf(0f, 7f, 18.5f), floatArrayOf(1f, 0.625f, 0.545f),
    floatArrayOf(0f, 12f, 17f), floatArrayOf(1f, 0.29f, 0.24f),
)
private const val SPACING = 95f
private const val CENTER = 198f
private const val SPRING_DURATION = 520f
private const val SPRING_DAMPING = 0.86f

internal class SlotPose(val x: Float, val y: Float, val scale: Float, val rotate: Float, val ghost: Float, val opacity: Float)

private fun slotPose(distance: Float): SlotPose {
    val d = abs(distance)
    val sign = if (distance < 0) -1f else 1f
    val side = if (distance < 0) LEFT else RIGHT
    fun at(values: FloatArray): Float {
        if (d >= 2) return values[2]
        val i = floor(d).toInt()
        return values[i] + (values[i + 1] - values[i]) * (d - i)
    }
    val beyond = max(0f, d - 2)
    return SlotPose(
        x = sign * (at(side.x) + beyond * 70),
        y = at(side.y) + beyond * 10,
        scale = at(side.scale) - beyond * 0.08f,
        rotate = sign * at(side.rotate),
        ghost = at(side.ghost),
        opacity = 1 - min(1f, max(0f, d - 2.2f) / 0.6f),
    )
}

/** Demo: after the recast, peek at the next (locked) league and come back. */
private fun demoFocus(seconds: Float): Float {
    fun ease(x: Float): Float {
        val t = max(0f, min(1f, x))
        return t * t * (3 - 2 * t)
    }
    return CURRENT_LEAGUE + ease((seconds - 4.4f) / 0.6f) - ease((seconds - 6.2f) / 0.6f)
}

/** useLeagueFocus: the crest row's focus, owned by the demo clock until a finger takes it. */
internal class EmeraldLeagueFocus(private val motion: EmeraldRankMotion, private val onTick: () -> Unit) {
    var enabled = true
    var reduced = false
    private var manualFocus = CURRENT_LEAGUE.toFloat()
    private var manual = false
    private var dragStart = CURRENT_LEAGUE.toFloat()
    private val animator = EmeraldAnimator()
    private var previousRound: Int? = null
    private val applyManualFocus: (Float) -> Unit = { manualFocus = it }

    /** The published focus; draw lambdas read this. */
    var focus by mutableFloatStateOf(CURRENT_LEAGUE.toFloat())
        private set

    /** manual || !demoRunning ? manualFocus : demoFocus(demoTime) */
    fun currentFocus(): Float = if (manual || motion.manual) manualFocus else demoFocus(motion.demoTime)

    private fun publish() {
        val value = currentFocus()
        focus = value
        val next = jsRound(value)
        val previous = previousRound
        previousRound = next
        if (previous != null && next != previous && manual) onTick()
    }

    /** Called once per frame after the rank motion has ticked. */
    fun frame(nowMs: Double) {
        animator.advance(nowMs, applyManualFocus)
        publish()
    }

    private fun settle(target: Float, velocity: Float) {
        val index = max(0, min(LEAGUES.size - 1, jsRound(target)))
        animator.startSpring(manualFocus, index.toFloat(), velocity, SPRING_DURATION, SPRING_DAMPING, false, instant = reduced) { finished ->
            if (!finished || index == CURRENT_LEAGUE) return@startSpring
            // A browsed league always hands the screen back to the one being played.
            animator.startSpring(
                manualFocus, CURRENT_LEAGUE.toFloat(), 0f, SPRING_DURATION, SPRING_DAMPING, false,
                delayMs = 3200f, instant = reduced,
            ) { done -> if (done) manual = false }
        }
    }

    fun select(index: Int) {
        animator.cancel()
        manualFocus = currentFocus()
        manual = true
        settle(index.toFloat(), 0f)
        publish()
    }

    fun onPanStart() {
        val current = currentFocus()
        animator.cancel()
        manualFocus = current
        dragStart = current
        manual = true
    }

    fun onPanUpdate(translationX: Float) {
        val raw = dragStart - translationX / SPACING
        val edge = max(0f, min((LEAGUES.size - 1).toFloat(), raw))
        val over = raw - edge
        manualFocus = edge + over / (1 + abs(over) * 2.5f)
        publish()
    }

    fun onPanEnd(velocityX: Float) {
        val velocity = -velocityX / SPACING
        settle(manualFocus + max(-1f, min(1f, velocity * 0.18f)), velocity)
    }
}

@Composable
private fun CrestSlot(index: Int, league: EmeraldLeagueFocus, content: @Composable () -> Unit) {
    val state = LEAGUES[index].state
    val z by remember { derivedStateOf { jsRound(10 - abs(index - league.focus) * 2).toFloat() } }
    Box(
        Modifier
            .size(100.dp, 112.dp)
            .zIndex(z)
            .graphicsLayer {
                val d = index - league.focus
                val pose = slotPose(d)
                val near = max(0f, 1 - abs(d))
                // A locked crest is a ghost at the side and a frosted, readable object when focused.
                val material = if (state == LeagueState.locked) pose.ghost * (1 - near) + 0.78f * near else 1f
                // Focusing a locked league nudges its lock once, like trying a closed door.
                val shake = if (state == LeagueState.locked) sin(max(0f, near - 0.75f) * 4 * PI.toFloat() * 3) * near * 3 else 0f
                alpha = pose.opacity * material
                translationX = (CENTER - 50 + pose.x) * density
                translationY = pose.y * density
                rotationZ = pose.rotate + shake
                scaleX = pose.scale
                scaleY = pose.scale
            },
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
internal fun LeagueCrestRow(
    league: EmeraldLeagueFocus,
    modifier: Modifier,
    renderCrest: @Composable (League, Int) -> Unit,
) {
    Box(
        modifier
            .size(393.dp, 118.dp)
            .semantics {
                contentDescription = "Leagues"
                stateDescription = LEAGUES[CURRENT_LEAGUE].name
                customActions = listOf(
                    CustomAccessibilityAction("increment") { league.select(jsRound(league.currentFocus()) + 1); true },
                    CustomAccessibilityAction("decrement") { league.select(jsRound(league.currentFocus()) - 1); true },
                )
            },
    ) {
        LEAGUES.forEachIndexed { index, item -> CrestSlot(index, league) { renderCrest(item, index) } }
    }
}

private val TITLE = Color.White
private val SUBTITLE = hex("#a6c7b9")
private val CHIP_DEFAULT = hex("#72f598")

@Composable
private fun LeagueChip(icon: ImageVector, iconSize: Float, tone: Color, text: String, solid: Boolean) {
    Box(Modifier.padding(top = 10.dp).widthIn(min = 161.dp).height(32.dp)) {
        EmeraldGlass(GlassKind.green, 20f, solid, Modifier.matchParentSize())
        Row(
            Modifier.align(Alignment.Center).padding(horizontal = 16.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = tone, modifier = Modifier.size(iconSize.dp))
            EmeraldText(text, 12f, tone, weight = FontWeight.Bold, tabular = true)
        }
    }
}

@Composable
private fun HeadlineLayer(index: Int, league: EmeraldLeagueFocus, solid: Boolean) {
    val item = LEAGUES[index]
    val icon = when (item.state) {
        LeagueState.current -> Icons.Outlined.Schedule
        LeagueState.done -> Icons.Filled.CheckCircle
        LeagueState.locked -> Icons.Filled.Lock
    }
    val tone = when (item.state) {
        LeagueState.locked -> hex("#B9C9DA")
        LeagueState.done -> hex("#FFD9A8")
        LeagueState.current -> hex("#72F598")
    }
    Column(
        Modifier
            .offset(0.dp, 168.dp)
            .size(393.dp, 92.dp)
            .graphicsLayer {
                val d = index - league.focus
                val near = max(0f, 1 - abs(d) * 1.8f)
                alpha = near
                translationX = d * -22 * density
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        EmeraldText(item.name, 23f, TITLE, Modifier.width(393.dp), FontWeight.Bold, 28f, -0.65f, TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        EmeraldText(item.detail, 13f, SUBTITLE, Modifier.width(393.dp), lineHeight = 18f, align = TextAlign.Center)
        LeagueChip(icon, if (item.state == LeagueState.current) 18f else 15f, tone, item.chip, solid)
    }
}

/** Title, subtitle and countdown of the focused league, cross-sliding with the crests. */
@Composable
internal fun LeagueHeadline(league: EmeraldLeagueFocus, solid: Boolean, challenges: Boolean) {
    if (challenges) {
        Column(
            Modifier.offset(0.dp, 168.dp).size(393.dp, 92.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            EmeraldText("Emerald Challenges", 23f, TITLE, Modifier.width(393.dp), FontWeight.Bold, 28f, -0.65f, TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            EmeraldText("Earn XP to climb the league", 13f, SUBTITLE, Modifier.width(393.dp), lineHeight = 18f, align = TextAlign.Center)
            LeagueChip(Icons.Outlined.Schedule, 18f, CHIP_DEFAULT, "3d 23h 15m left", solid)
        }
        return
    }
    LEAGUES.forEachIndexed { index, _ -> HeadlineLayer(index, league, solid) }
}

@Composable
private fun TintLayer(index: Int, league: EmeraldLeagueFocus) {
    val tint = LEAGUES[index].tint
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = max(0f, 1 - abs(index - league.focus)) }
            .background(
                Brush.verticalGradient(
                    0f to hex(tint[0]), 0.23f to hex(tint[1]), 0.40f to hex(tint[2]),
                    0.58f to hex("#071e14"), 0.82f to hex("#080a09"),
                ),
            ),
    )
}

/** The page's light takes the colour of the league in focus. Emerald is the base gradient itself. */
@Composable
internal fun LeagueTint(league: EmeraldLeagueFocus) {
    LEAGUES.forEachIndexed { index, _ -> if (index != CURRENT_LEAGUE) TintLayer(index, league) }
}

/** The standings belong to Emerald; they recede while another league is in focus. */
internal fun Modifier.leagueRecede(league: EmeraldLeagueFocus): Modifier = graphicsLayer {
    val away = min(1f, abs(league.focus - CURRENT_LEAGUE))
    alpha = 1 - away * 0.62f
    scaleX = 1 - away * 0.02f
    scaleY = 1 - away * 0.02f
}
