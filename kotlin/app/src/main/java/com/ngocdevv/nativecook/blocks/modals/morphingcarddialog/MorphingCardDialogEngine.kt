package com.ngocdevv.nativecook.blocks.modals.morphingcarddialog

import androidx.compose.runtime.mutableLongStateOf
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign

/** A frame in dp. */
internal class MorphRect(val x: Float, val y: Float, val width: Float, val height: Float) {
    fun contains(px: Float, py: Float, slop: Float = 0f) =
        px >= x - slop && px <= x + width + slop && py >= y - slop && py <= y + height + slop
}

internal const val MODE_AUTO = 0
internal const val MODE_MANUAL = 1
internal const val MODE_RETURN = 2

private const val CARD_TEXT = 60f

/**
 * The morph's state. While autoPlay runs, every value is a pure function of the loop time ([sampleAuto]:
 * closed-form springs started at the journey's event times). A touch hands the same values, with their
 * velocities, to the live springs ([stepLive]), which are the React Native block's frame callback.
 *
 * Nothing here is Compose state except [tick]; draw and layer lambdas read it to follow each frame.
 */
internal class MorphingCardDialogEngine {
    val tick = mutableLongStateOf(0L)

    // Geometry, set by the screen.
    var cards: List<MorphRect> = emptyList()
    var target = MorphRect(0f, 0f, 1f, 1f)
    var photoHeight = 1f
    var closeHit = MorphRect(0f, 0f, 1f, 1f)
    var actionHit = MorphRect(0f, 0f, 1f, 1f)
    var grabX = 0f
    var grabY = 0f
    var playing = false
    var reduced = false

    // The morph. progress: 0 = the card's rect, 1 = the dialog's.
    var progress = 0f
    var velocity = 0f
    var goal = 0f
    var active = -1
    var sourceIndex = 0
    var titleFollow = 0f
    var subtitleFollow = 0f
    var content = 0f
    var dragX = 0f
    var dragY = 0f
    var dragVX = 0f
    var dragVY = 0f
    var dragging = false
    private var grabProgress = 1f
    private var grabDragX = 0f
    private var grabDragY = 0f
    private var pulled = 0f

    // Press feedback, 0…1 (eased), and where each is heading.
    var pressed = -1
    var pressAmount = 0f
    var closePress = 0f
    var actionPress = 0f
    private var pressRaw = 0f
    private var pressTo = 0f
    private var closeRaw = 0f
    var closeTo = 0f
    private var actionRaw = 0f
    var actionTo = 0f

    // autoPlay.
    var mode = MODE_AUTO
    var fingers = 0
    private var idle = 0f
    private var clock = 0f
    private var autoStart = Long.MIN_VALUE
    private var lastFrame = Long.MIN_VALUE
    private var wasRest = false

    // The ghost finger.
    var ghostX = 0f
    var ghostY = 0f
    var ghostPress = 0f
    var ghostAlpha = 0f

    // What the frame's parts read, derived once a frame.
    var q = 0f
    var frameX = 0f
    var frameY = 0f
    var frameWidth = 1f
    var frameHeight = 1f
    var edge = 1f
    var radius = RADIUS_CARD
    var lifted = 0f
    var lateTitle = 0f
    var lateSubtitle = 0f
    var sceneAmount = 0f
    var frameAlpha = 0f

    // The scripted journey's constants (independent of the screen).
    private val spring = ClosedSpring(STIFFNESS, DAMPING)
    private val dragSpan = T_DRAG_END - T_DRAG_START
    private val endBanded = rubber(GHOST_PULL, DRAG_RUBBER)
    private val endSlope = rubberSlope(GHOST_PULL, DRAG_RUBBER)
    private val endSpeed = GHOST_PULL * ghostPullSpeed(1f) / dragSpan // the finger's speed as it lets go, dp/s
    private val releaseP = 1f - endBanded / DRAG_SPAN
    private val releaseV = -endSpeed * endSlope / DRAG_SPAN
    private val releaseY = endBanded * DRAG_FOLLOW
    private val releaseVY = endSpeed * endSlope * DRAG_FOLLOW
    private val releaseX = rubber(GHOST_DRIFT, DRAG_SIDE_RUBBER) * DRAG_FOLLOW
    private val releaseVX = GHOST_DRIFT / dragSpan * rubberSlope(releaseX, DRAG_SIDE_RUBBER) * DRAG_FOLLOW
    /** The follow-through during the scripted drag, where the frame follows the finger, not a spring. */
    private val dragTitle = lagTable(FOLLOW_TITLE)
    private val dragSubtitle = lagTable(FOLLOW_SUBTITLE)
    /** When each close has come to rest in its card and the card takes over again. */
    private val homeA = T_DRAG_END + 0.1f + max(
        spring.settle(releaseP, releaseV, 0.00025f),
        max(spring.settle(releaseY, releaseVY, 0.03f), spring.settle(releaseX, releaseVX, 0.03f)),
    )
    private val homeB = T_TAP_CLOSE + 0.1f + spring.settle(1f, 0f, 0.00025f)

    private fun scriptedProgress(u: Float) = max(1f - rubber(GHOST_PULL * ghostPull(u), DRAG_RUBBER) / DRAG_SPAN, 0f)

    private fun lagTable(lag: Float): FloatArray {
        val steps = ceil(dragSpan / SUBSTEP).toInt()
        val h = dragSpan / steps
        val k = 1f - exp(-h / lag)
        val table = FloatArray(steps + 1)
        // The dialog has been open for over two seconds: the lagged value has caught up.
        var f = 1f + spring.lagged(-1f, 0f, -1f, lag, T_DRAG_START - T_TAP_A)
        table[0] = f
        for (i in 1..steps) {
            f += (scriptedProgress((i - 0.5f) / steps) - f) * k
            table[i] = f
        }
        return table
    }

    private fun lookup(table: FloatArray, u: Float): Float {
        val at = clamp(u, 0f, 1f) * (table.size - 1)
        val i = min(at.toInt(), table.size - 2)
        return mix(table[i], table[i + 1], at - i)
    }

    private fun cardA() = min(CARD_A, cards.size - 1)
    private fun cardB() = min(CARD_B, cards.size - 1)

    // ---------------------------------------------------------------- frames

    fun frame(now: Long) {
        val dt = if (lastFrame == Long.MIN_VALUE) 0f else min((now - lastFrame) / 1e9f, 0.048f)
        lastFrame = now
        if (cards.isEmpty()) return
        if (!playing && mode == MODE_AUTO) mode = MODE_MANUAL
        when (mode) {
            MODE_AUTO -> {
                if (autoStart == Long.MIN_VALUE) autoStart = now
                clock = (((now - autoStart) / 1e9) % LOOP).toFloat()
                sampleAuto(clock)
            }
            MODE_MANUAL -> {
                stepLive(dt)
                if (playing) {
                    if (fingers == 0 && !dragging) idle += dt
                    if (idle >= RESUME_AFTER) {
                        mode = MODE_RETURN
                        close(0f)
                    }
                }
            }
            else -> {
                stepLive(dt)
                // Back at rest: the loop starts again from 0.
                if (active < 0) {
                    if (playing) {
                        autoStart = now
                        mode = MODE_AUTO
                        sampleAuto(0f)
                    } else {
                        mode = MODE_MANUAL
                    }
                }
            }
        }
        derive()
        // Nothing moves at rest once autoPlay is off: stop invalidating the layers.
        val rest = mode != MODE_AUTO && active < 0 && pressRaw == 0f && pressTo == 0f &&
            closeRaw == 0f && closeTo == 0f && actionRaw == 0f && actionTo == 0f
        if (!rest || !wasRest) tick.longValue = now
        wasRest = rest
    }

    private fun derive() {
        val index = if (active >= 0) active else sourceIndex
        val from = cards[clamp(index.toFloat(), 0f, (cards.size - 1).toFloat()).toInt()]
        val open = clamp(progress, 0f, 1f)
        q = if (reduced) 1f else progress
        frameWidth = max(1f, mix(from.width, target.width, q))
        frameHeight = max(1f, mix(from.height, target.height, q))
        frameX = mix(from.x, target.x, q) + dragX
        frameY = mix(from.y, target.y, q) + dragY
        edge = max(1f, mix(from.height - CARD_TEXT, photoHeight, q))
        radius = mix(RADIUS_CARD, RADIUS_DIALOG, if (reduced) 1f else open)
        lifted = if (reduced) 1f else open
        lateTitle = if (reduced) 1f else titleFollow
        lateSubtitle = if (reduced) 1f else subtitleFollow
        sceneAmount = open
        frameAlpha = if (active < 0) 0f else if (reduced) open else 1f
    }

    /** Opacity (and 1 − distance) of the detail block `order` = 0, 1, 2. */
    fun rise(order: Int): Float {
        val start = CONTENT_START[order]
        val gate = if (reduced) clamp(progress, 0f, 1f) else seg(progress, CONTENT_GATE_FROM, CONTENT_GATE_TO)
        return easeOutCubic(seg(content, start, start + CONTENT_SPAN)) * gate
    }

    // ---------------------------------------------------------------- autoPlay: a pure function of t

    private fun sampleAuto(t: Float) {
        val a = cardA()
        val b = cardB()
        pressAmount = max(tapDip(t, T_TAP_A), tapDip(t, T_TAP_B))
        pressRaw = pressAmount
        pressTo = 0f
        pressed = if (t < T_DRAG_END) a else b
        closePress = if (t >= T_TAP_CLOSE + TAP_DOWN && t < T_TAP_CLOSE) 1f else 0f
        closeRaw = closePress
        closeTo = 0f
        actionPress = 0f
        actionRaw = 0f
        actionTo = 0f
        dragging = false
        dragX = 0f
        dragY = 0f
        dragVX = 0f
        dragVY = 0f

        when {
            t < T_TAP_A -> rest()
            t < T_DRAG_START -> opening(a, t - T_TAP_A)
            t < T_DRAG_END -> {
                // The finger holds the dialog and pulls it down.
                val u = (t - T_DRAG_START) / dragSpan
                val down = GHOST_PULL * ghostPull(u)
                val banded = rubber(down, DRAG_RUBBER)
                active = a
                sourceIndex = a
                goal = 1f
                dragging = true
                grabProgress = 1f
                grabDragX = 0f
                grabDragY = 0f
                pulled = down
                progress = max(1f - banded / DRAG_SPAN, 0f)
                velocity = 0f
                dragY = banded * DRAG_FOLLOW
                dragX = rubber(GHOST_DRIFT * u, DRAG_SIDE_RUBBER) * DRAG_FOLLOW
                titleFollow = lookup(dragTitle, u)
                subtitleFollow = lookup(dragSubtitle, u)
                content = clamp(1f - (t - T_DRAG_START) / CONTENT_OUT, 0f, 1f)
            }
            t < homeA -> {
                // Let go: back into the card with the finger's speed.
                val tau = t - T_DRAG_END
                active = a
                sourceIndex = a
                goal = 0f
                progress = spring.value(releaseP, releaseV, tau)
                velocity = spring.velocity(releaseP, releaseV, tau)
                dragY = spring.value(releaseY, releaseVY, tau)
                dragVY = spring.velocity(releaseY, releaseVY, tau)
                dragX = spring.value(releaseX, releaseVX, tau)
                dragVX = spring.velocity(releaseX, releaseVX, tau)
                titleFollow = spring.lagged(releaseP, releaseV, dragTitle.last(), FOLLOW_TITLE, tau)
                subtitleFollow = spring.lagged(releaseP, releaseV, dragSubtitle.last(), FOLLOW_SUBTITLE, tau)
                content = 0f
            }
            t < T_TAP_B -> rest()
            t < T_TAP_CLOSE -> opening(b, t - T_TAP_B)
            t < homeB -> {
                val tau = t - T_TAP_CLOSE
                active = b
                sourceIndex = b
                goal = 0f
                progress = spring.value(1f, 0f, tau)
                velocity = spring.velocity(1f, 0f, tau)
                titleFollow = spring.lagged(1f, 0f, 1f, FOLLOW_TITLE, tau)
                subtitleFollow = spring.lagged(1f, 0f, 1f, FOLLOW_SUBTITLE, tau)
                content = clamp(1f - tau / CONTENT_OUT, 0f, 1f)
            }
            else -> rest()
        }
        sampleGhost(t, a, b)
    }

    private fun rest() {
        active = -1
        goal = 0f
        progress = 0f
        velocity = 0f
        titleFollow = 0f
        subtitleFollow = 0f
        content = 0f
    }

    private fun opening(index: Int, tau: Float) {
        active = index
        sourceIndex = index
        goal = 1f
        progress = 1f + spring.value(-1f, 0f, tau)
        velocity = spring.velocity(-1f, 0f, tau)
        titleFollow = 1f + spring.lagged(-1f, 0f, -1f, FOLLOW_TITLE, tau)
        subtitleFollow = 1f + spring.lagged(-1f, 0f, -1f, FOLLOW_SUBTITLE, tau)
        content = clamp(tau / CONTENT_IN, 0f, 1f)
    }

    private fun sampleGhost(t: Float, a: Int, b: Int) {
        val onB = tapGhost(t, T_TAP_B)
        val onClose = tapGhost(t, T_TAP_CLOSE)
        val landed = T_DRAG_START - GHOST_APPEAR
        val onDrag = seg(t, landed, landed + 0.18f) * (1f - seg(t, T_DRAG_END, T_DRAG_END + 0.2f))
        val cardA = cards[a]
        val cardB = cards[b]
        ghostX = cardA.x + cardA.width / 2f
        ghostY = cardA.y + cardA.height * 0.42f
        ghostPress = tapPress(t, T_TAP_A)
        ghostAlpha = tapGhost(t, T_TAP_A)
        if (onDrag > 0f) {
            val u = seg(t, T_DRAG_START, T_DRAG_END)
            ghostX = grabX + GHOST_DRIFT * u
            ghostY = grabY + GHOST_PULL * ghostPull(u)
            ghostPress = seg(t, T_DRAG_START - 0.1f, T_DRAG_START) * (1f - seg(t, T_DRAG_END, T_DRAG_END + 0.1f))
            ghostAlpha = onDrag
        } else if (onB > 0f) {
            ghostX = cardB.x + cardB.width / 2f
            ghostY = cardB.y + cardB.height * 0.42f
            ghostPress = tapPress(t, T_TAP_B)
            ghostAlpha = onB
        } else if (onClose > 0f) {
            ghostX = target.x + closeHit.x + closeHit.width / 2f
            ghostY = target.y + closeHit.y + closeHit.height / 2f
            ghostPress = tapPress(t, T_TAP_CLOSE)
            ghostAlpha = onClose
        }
    }

    // ---------------------------------------------------------------- live: fingers and their springs

    /** Any touch: the scripted finger lets go and the clock stops. */
    fun touch() {
        idle = 0f
        if (mode == MODE_MANUAL) return
        if (mode == MODE_AUTO && dragging) dragEnd(0f, 0f)
        closeRaw = 0f
        closePress = 0f
        ghostAlpha = 0f
        mode = MODE_MANUAL
    }

    fun pressIn(index: Int) {
        pressed = index
        pressTo = 1f
    }

    fun pressOut() {
        pressTo = 0f
    }

    /** Grows the frame out of card `index`. False while another card's dialog is still on screen. */
    fun open(index: Int): Boolean {
        if (active >= 0 && active != index) return false
        if (active < 0) {
            sourceIndex = index
            progress = 0f
            velocity = 0f
            titleFollow = 0f
            subtitleFollow = 0f
            content = 0f
            dragX = 0f
            dragY = 0f
            dragVX = 0f
            dragVY = 0f
        }
        active = index
        goal = 1f
        return true
    }

    /** Sends the frame back to its card, keeping whatever speed it has plus `kick` (progress per second). */
    fun close(kick: Float) {
        if (active < 0) return
        dragging = false
        velocity += kick
        goal = 0f
    }

    /** Catches the frame wherever it is, mid-spring included. */
    fun dragBegin() {
        dragging = true
        grabProgress = progress
        grabDragX = dragX
        grabDragY = dragY
        pulled = 0f
        velocity = 0f
        dragVX = 0f
        dragVY = 0f
    }

    /** The finger has moved (x, y) dp since dragBegin: down shrinks toward the card, the rest stretches. */
    fun dragMove(x: Float, y: Float) {
        val down = max(y, 0f)
        val up = max(-y, 0f)
        val banded = rubber(down, DRAG_RUBBER)
        pulled = down
        progress = max(grabProgress - banded / DRAG_SPAN, 0f)
        dragY = grabDragY + banded * DRAG_FOLLOW - rubber(up, DRAG_UP_RUBBER) * DRAG_FOLLOW
        dragX = grabDragX + sign(x) * rubber(abs(x), DRAG_SIDE_RUBBER) * DRAG_FOLLOW
    }

    /** Lets go with the finger's velocity (dp/s). True when the dialog closes. */
    fun dragEnd(vx: Float, vy: Float): Boolean {
        if (!dragging) return false
        dragging = false
        val down = pulled
        val slope = rubberSlope(down, DRAG_RUBBER)
        // The finger's speed becomes the springs' starting speed.
        velocity = if (vy > 0f || down > 0f) -vy * slope / DRAG_SPAN else 0f
        dragVY = vy * slope * DRAG_FOLLOW
        dragVX = vx * rubberSlope(abs(dragX - grabDragX), DRAG_SIDE_RUBBER) * DRAG_FOLLOW
        val closes = vy > DRAG_CLOSE_VELOCITY || down > DRAG_CLOSE_DISTANCE
        goal = if (closes) 0f else 1f
        return closes
    }

    private fun ramp(raw: Float, to: Float, dt: Float): Float {
        val step = dt / PRESS_SECONDS
        return if (raw < to) min(raw + step, to) else max(raw - step, to)
    }

    private fun stepLive(dt: Float) {
        pressRaw = ramp(pressRaw, pressTo, dt)
        closeRaw = ramp(closeRaw, closeTo, dt)
        actionRaw = ramp(actionRaw, actionTo, dt)
        pressAmount = easeInOutQuad(pressRaw)
        closePress = easeInOutQuad(closeRaw)
        actionPress = easeInOutQuad(actionRaw)
        ghostAlpha = 0f
        if (active < 0 || dt <= 0f) return

        val to = goal
        var x = progress
        val held = dragging
        if (!held) {
            var v = velocity
            var ox = dragX
            var oy = dragY
            var vx = dragVX
            var vy = dragVY
            if (reduced) {
                val k = 1f - exp(-dt * 14f)
                x += (to - x) * k
                ox = 0f
                oy = 0f
                v = 0f
                vx = 0f
                vy = 0f
                if (abs(to - x) < 0.004f) x = to
            } else {
                val steps = max(1, ceil(dt / SUBSTEP).toInt())
                val h = dt / steps
                repeat(steps) {
                    v += (STIFFNESS * (to - x) - DAMPING * v) * h
                    x += v * h
                    vx += (-STIFFNESS * ox - DAMPING * vx) * h
                    ox += vx * h
                    vy += (-STIFFNESS * oy - DAMPING * vy) * h
                    oy += vy * h
                }
                if (abs(to - x) < 0.0004f && abs(v) < 0.004f) {
                    x = to
                    v = 0f
                }
                if (abs(ox) < 0.05f && abs(vx) < 0.5f) {
                    ox = 0f
                    vx = 0f
                }
                if (abs(oy) < 0.05f && abs(vy) < 0.5f) {
                    oy = 0f
                    vy = 0f
                }
            }
            progress = x
            velocity = v
            dragX = ox
            dragY = oy
            dragVX = vx
            dragVY = vy
        }

        // Follow-through and the content's clock run whether a finger holds the frame or not.
        titleFollow += (x - titleFollow) * (1f - exp(-dt / FOLLOW_TITLE))
        subtitleFollow += (x - subtitleFollow) * (1f - exp(-dt / FOLLOW_SUBTITLE))
        val showing = to == 1f && !held
        content = clamp(content + if (showing) dt / CONTENT_IN else -dt / CONTENT_OUT, 0f, 1f)

        // Home: the card takes over again.
        if (to == 0f && !held && x == 0f && abs(titleFollow) < 0.003f && abs(subtitleFollow) < 0.003f && dragX == 0f && dragY == 0f) {
            titleFollow = 0f
            subtitleFollow = 0f
            content = 0f
            active = -1
        }
    }
}
