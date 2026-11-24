package com.ngocdevv.nativecook.blocks.modals.morphingfeedbackpopover

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

internal const val MODE_AUTO = 0
internal const val MODE_MANUAL = 1
internal const val MODE_RETURN = 2

/** What a touch inside the open popover did. */
internal const val INSIDE_NONE = 0
internal const val INSIDE_FACE = 1
internal const val INSIDE_SENT = 2

/**
 * The popover's state. While autoPlay runs, every value is a pure function of the loop time ([sampleAuto]):
 * the springs are linear, so an opening and a closing are two closed-form step responses added together.
 * A touch hands the same values, with their velocities, to the live springs ([stepLive]), which are the
 * React Native block's frame callback.
 *
 * Only [tick], [typed], [isOpen] and [field] are Compose state; draw and layer lambdas read [tick].
 */
internal class MorphingPopoverEngine {
    val tick = mutableLongStateOf(0L)
    /** Characters of the sample autoPlay has typed. */
    val typed = mutableIntStateOf(0)
    /** The real field takes touches only while the popover is open. */
    val isOpen = mutableStateOf(false)
    val field = mutableStateOf(TextFieldValue(""))

    // Geometry, set by the screen (dp).
    var anchorWidth = 132f
    var anchorHeight = 44f
    var sizeWidth = 361f
    var sizeHeight = 232f
    var centre = 0f
    var bottom = 0f
    var belowPill = 0f
    var labelWidth = 76f
    var fieldLeft = 12f
    var fieldTop = 12f
    var fieldHeight = 150f
    var sendLeft = 0f
    var rowTop = 176f
    var rowHeight = 40f
    var faceLeft = 14f
    var faceStep = 44f
    var faceSize = 40f
    var sendWidth = 76f
    var ghostPillY = 0f
    var playing = false
    var reduced = false
    var keyboard = 0f

    // The two axes. 0 = the pill's size, 1 = the popover's.
    var wide = 0f
    var tall = 0f
    private var wideV = 0f
    private var tallV = 0f
    var goal = 0f
    private var wideGoal = 0f
    private var tallGoal = 0f
    private var lag = 0f
    var labelFollow = 0f
    var content = 0f
    var dragY = 0f
    private var dragV = 0f
    var dragging = false
    private var grabWide = 1f
    private var grabTall = 1f
    private var grabY = 0f
    private var pulled = 0f
    /** Seconds since Send; −1 when no thanks is showing. */
    var thanks = -1f

    // Press feedback (raw 0…1 and eased) and the form.
    private var pressRaw = 0f
    var pressTo = 0f
    var pressAmount = 0f
    private var sendRaw = 0f
    var sendTo = 0f
    var sendPress = 0f
    var mood = -1
    private val faceRaw = FloatArray(4)
    private val popAge = FloatArray(4) { -1f }
    private var readyRaw = 0f
    var focused = false
    private var wasClosed = true

    // autoPlay.
    var mode = MODE_AUTO
    var fingers = 0
    private var idle = 0f
    private var autoStart = Long.MIN_VALUE
    private var lastFrame = Long.MIN_VALUE
    private var wasRest = false

    // The ghost finger.
    var ghostX = 0f
    var ghostY = 0f
    var ghostPress = 0f
    var ghostAlpha = 0f

    // What the parts read, derived once a frame.
    var frameX = 0f
    var frameY = 0f
    var frameWidth = 132f
    var frameHeight = 44f
    var frameScale = 1f
    var radius = 22f
    var contentX = 0f
    var contentY = 0f
    var labelX = 0f
    var labelY = 0f
    var labelAlpha = 1f
    var scrim = 0f

    private val wideSpring = ClosedSpring(WIDTH_STIFFNESS, WIDTH_DAMPING)
    private val tallSpring = ClosedSpring(HEIGHT_STIFFNESS, HEIGHT_DAMPING)
    private val popSpring = ClosedSpring(FACE_STIFFNESS, FACE_DAMPING)
    private val tWideOpen = T_TAP_PILL
    private val tTallOpen = T_TAP_PILL + AXIS_STAGGER
    private val tTallClose = T_TAP_SEND
    private val tWideClose = T_TAP_SEND + AXIS_STAGGER
    /** autoPlay's first character. */
    private val tFirstChar = T_TYPE_START + (T_TYPE_END - T_TYPE_START) * TYPE_FIRST / SAMPLE_PAUSE_AT
    /** When the collapsing height first reaches 0.02: the form is cleared there, unseen. */
    private val tCleared: Float = run {
        var at = tTallClose
        while (at < LOOP && autoTall(at) > 0.02f) at += SUBSTEP
        at
    }

    private fun step(spring: ClosedSpring, tau: Float) = if (tau < 0f) 0f else 1f + spring.value(-1f, 0f, tau)
    private fun stepV(spring: ClosedSpring, tau: Float) = if (tau < 0f) 0f else spring.velocity(-1f, 0f, tau)
    private fun stepLag(tau: Float) = if (tau < 0f) 0f else 1f + tallSpring.lagged(-1f, 0f, -1f, FOLLOW_LABEL, tau)
    private fun autoTall(t: Float) = step(tallSpring, t - tTallOpen) - step(tallSpring, t - tTallClose)

    private fun widthAt(amount: Float) = max(anchorWidth * 0.9f, mix(anchorWidth, sizeWidth, amount))
    private fun heightAt(amount: Float) = max(anchorHeight * 0.92f, mix(anchorHeight, sizeHeight, amount))

    private fun hasText() = typed.intValue > 0 || field.value.text.isNotEmpty()
    private fun ready() = hasText() || mood >= 0

    fun frameContains(x: Float, y: Float) =
        x >= frameX && x <= frameX + frameWidth && y >= frameY && y <= frameY + frameHeight

    fun fieldContains(x: Float, y: Float): Boolean {
        val px = x - contentX
        val py = y - contentY
        return px >= fieldLeft && px <= sizeWidth - fieldLeft && py >= fieldTop && py <= fieldTop + fieldHeight
    }

    /** Opacity and (1 − distance) of the content part `order` = 0, 1, 2. */
    fun rise(order: Int): Float {
        val start = CONTENT_START[order]
        return easeOutCubic(seg(content, start, start + CONTENT_SPAN)) * seg(tall, CONTENT_GATE_FROM, CONTENT_GATE_TO)
    }

    fun faceAmount(index: Int) = easeInOutQuad(faceRaw[index])

    fun facePop(index: Int): Float {
        val age = popAge[index]
        return when {
            age < 0f -> 1f
            age < FACE_POP_SECONDS -> mix(1f, FACE_POP, easeInOutQuad(age / FACE_POP_SECONDS))
            else -> 1f + popSpring.value(FACE_POP - 1f, 0f, age - FACE_POP_SECONDS)
        }
    }

    fun sendAlpha() = mix(0.4f, 1f, easeInOutQuad(readyRaw))

    fun thanksAlpha() =
        if (thanks < 0f) 0f else seg(thanks, THANKS_IN_FROM, THANKS_IN_TO) * (1f - seg(thanks, THANKS_OUT_FROM, THANKS_OUT_TO))

    fun checkShort() = when {
        thanks < 0f -> 0f
        reduced -> 1f
        else -> easeOutCubic(seg(thanks, THANKS_SHORT_FROM, THANKS_SHORT_TO))
    }

    fun checkLong() = when {
        thanks < 0f -> 0f
        reduced -> 1f
        else -> easeOutCubic(seg(thanks, THANKS_LONG_FROM, THANKS_LONG_TO))
    }

    // ---------------------------------------------------------------- frames

    fun frame(now: Long) {
        val dt = if (lastFrame == Long.MIN_VALUE) 0f else min((now - lastFrame) / 1e9f, 0.048f)
        lastFrame = now
        if (!playing && mode == MODE_AUTO) {
            mode = MODE_MANUAL
            ghostAlpha = 0f
        }
        when (mode) {
            MODE_AUTO -> {
                if (autoStart == Long.MIN_VALUE) autoStart = now
                sampleAuto((((now - autoStart) / 1e9) % LOOP).toFloat())
            }
            MODE_MANUAL -> {
                stepLive(dt)
                if (playing) {
                    if (fingers == 0 && !dragging && !focused) idle += dt
                    if (idle >= RESUME_AFTER) {
                        mode = MODE_RETURN
                        dismiss()
                    }
                }
            }
            else -> {
                stepLive(dt)
                // Back at rest: the loop starts again from 0.
                if (goal == 0f && tall == 0f && wide == 0f && thanks < 0f) {
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
        val rest = mode != MODE_AUTO && goal == 0f && wide == 0f && tall == 0f && dragY == 0f && thanks < 0f &&
            pressRaw == 0f && pressTo == 0f && sendRaw == 0f && content == 0f && labelFollow == 0f && readyRaw == 0f
        if (!rest || !wasRest) tick.longValue = now
        wasRest = rest
    }

    private fun derive() {
        val open = clamp(tall, 0f, 1f)
        val lift = max(0f, keyboard - belowPill + 12f) * open
        frameWidth = widthAt(wide)
        frameHeight = heightAt(tall)
        frameX = centre - frameWidth / 2f
        frameY = bottom - frameHeight + dragY - lift
        contentX = centre - sizeWidth / 2f
        contentY = bottom - sizeHeight + dragY - lift
        radius = min(frameHeight / 2f, mix(anchorHeight / 2f, RADIUS_OPEN, open))
        val swell = if (thanks < 0f || reduced) 0f else {
            sin(PI * easeInOutCubic(seg(thanks, THANKS_PULSE_FROM, THANKS_PULSE_TO))).toFloat()
        }
        frameScale = (1f - (1f - PRESS_SCALE) * pressAmount) * (1f + THANKS_PULSE * swell)
        val ride = if (reduced) open else labelFollow
        val afterSend = if (thanks < 0f) 1f else {
            clamp(1f - seg(thanks, 0f, THANKS_LABEL_OUT) + seg(thanks, THANKS_LABEL_IN_FROM, THANKS_LABEL_IN_TO), 0f, 1f)
        }
        val covered = (if (hasText()) 1f else 0f) * seg(open, CONTENT_GATE_FROM, CONTENT_GATE_TO)
        labelAlpha = mix(1f, PLACEHOLDER_ALPHA, clamp(ride, 0f, 1f)) * (1f - covered) * afterSend
        labelX = mix(sizeWidth / 2f - labelWidth / 2f, fieldLeft + 14f, ride)
        labelY = mix(sizeHeight - anchorHeight / 2f - 10f, fieldTop + 14f, ride)
        scrim = SCRIM_ALPHA * open
        isOpen.value = goal == 1f
    }

    // ---------------------------------------------------------------- autoPlay: a pure function of t

    private fun sampleAuto(t: Float) {
        wide = step(wideSpring, t - tWideOpen) - step(wideSpring, t - tWideClose)
        wideV = stepV(wideSpring, t - tWideOpen) - stepV(wideSpring, t - tWideClose)
        tall = autoTall(t)
        tallV = stepV(tallSpring, t - tTallOpen) - stepV(tallSpring, t - tTallClose)
        labelFollow = stepLag(t - tTallOpen) - stepLag(t - tTallClose)
        goal = if (t >= T_TAP_PILL && t < T_TAP_SEND) 1f else 0f
        wideGoal = if (t >= tWideOpen && t < tWideClose) 1f else 0f
        tallGoal = if (t >= tTallOpen && t < tTallClose) 1f else 0f
        lag = when {
            t >= tWideOpen && t < tTallOpen -> tTallOpen - t
            t >= tTallClose && t < tWideClose -> tWideClose - t
            else -> 0f
        }
        content = if (t < T_TAP_SEND) seg(t, T_TAP_PILL, T_TAP_PILL + CONTENT_IN) else clamp(1f - (t - T_TAP_SEND) / CONTENT_OUT, 0f, 1f)
        thanks = if (t >= T_TAP_SEND && t < T_TAP_SEND + THANKS_END) t - T_TAP_SEND else -1f
        dragging = false
        dragY = 0f
        dragV = 0f

        pressAmount = tapDip(t, T_TAP_PILL)
        pressRaw = pressAmount
        pressTo = 0f
        sendPress = tapDip(t, T_TAP_SEND)
        sendRaw = sendPress
        sendTo = 0f

        val filled = t < tCleared
        typed.intValue = if (t >= T_TYPE_START && filled) typedCount(seg(t, T_TYPE_START, T_TYPE_END), SAMPLE.length) else 0
        mood = if (t >= T_TAP_FACE && filled) GHOST_FACE else -1
        faceRaw.fill(0f)
        popAge.fill(-1f)
        if (mood == GHOST_FACE) {
            faceRaw[GHOST_FACE] = seg(t, T_TAP_FACE, T_TAP_FACE + FACE_FILL_SECONDS)
            popAge[GHOST_FACE] = t - T_TAP_FACE
        }
        readyRaw = if (filled) seg(t, tFirstChar, tFirstChar + SEND_FADE_SECONDS) else 0f
        wasClosed = goal == 0f && tall <= 0.02f

        sampleGhost(t)
    }

    private fun sampleGhost(t: Float) {
        val onPill = tapGhost(t, T_TAP_PILL)
        val onFace = tapGhost(t, T_TAP_FACE)
        val onSend = tapGhost(t, T_TAP_SEND)
        val originX = centre - sizeWidth / 2f
        val originY = bottom - sizeHeight
        val rowY = originY + rowTop + rowHeight / 2f
        ghostX = centre
        ghostY = ghostPillY
        ghostPress = tapPress(t, T_TAP_PILL)
        ghostAlpha = onPill
        if (onFace > 0f) {
            ghostX = originX + faceLeft + faceStep * GHOST_FACE + faceSize / 2f
            ghostY = rowY
            ghostPress = tapPress(t, T_TAP_FACE)
            ghostAlpha = onFace
        } else if (onSend > 0f) {
            ghostX = originX + sendLeft + sendWidth / 2f
            ghostY = rowY
            ghostPress = tapPress(t, T_TAP_SEND)
            ghostAlpha = onSend
        }
    }

    // ---------------------------------------------------------------- live: fingers and their springs

    /** Any touch: the clock stops, and what autoPlay had typed moves into the real field. */
    fun touch() {
        idle = 0f
        if (mode == MODE_MANUAL) return
        val count = typed.intValue
        if (mode == MODE_AUTO && count > 0 && goal == 1f) {
            val text = SAMPLE.take(count)
            field.value = TextFieldValue(text, TextRange(text.length))
        }
        typed.intValue = 0
        pressTo = 0f
        sendTo = 0f
        ghostAlpha = 0f
        mode = MODE_MANUAL
    }

    /** Stretches the pill open. False when it already is. */
    fun open(): Boolean {
        if (goal == 1f) return false
        thanks = -1f
        goal = 1f
        wideGoal = 1f
        lag = AXIS_STAGGER
        isOpen.value = true
        return true
    }

    /** Closes back into the pill. */
    fun dismiss(): Boolean {
        if (goal == 0f) return false
        dragging = false
        goal = 0f
        tallGoal = 0f
        lag = AXIS_STAGGER
        isOpen.value = false
        return true
    }

    private fun confirm(): Boolean {
        if (!dismiss()) return false
        thanks = 0f
        return true
    }

    fun dragBegin() {
        dragging = true
        grabWide = wide
        grabTall = tall
        grabY = dragY
        pulled = 0f
        wideV = 0f
        tallV = 0f
        dragV = 0f
    }

    /** The finger has moved y since dragBegin: down squashes the popover toward the pill, up stretches a little. */
    fun dragMove(y: Float) {
        val down = max(y, 0f)
        val up = max(-y, 0f)
        val banded = rubber(down, DRAG_RUBBER)
        pulled = down
        tall = max(grabTall - banded / DRAG_SPAN, 0f)
        wide = max(grabWide - banded / DRAG_SPAN * DRAG_WIDTH_SHARE, 0f)
        dragY = grabY + banded * DRAG_FOLLOW - rubber(up, DRAG_UP_RUBBER) * 0.5f
    }

    /** Lets go with the finger's velocity (dp/s). True when the popover closes. */
    fun dragEnd(vy: Float): Boolean {
        if (!dragging) return false
        dragging = false
        val down = pulled
        val slope = rubberSlope(down, DRAG_RUBBER)
        val speed = if (down > 0f) -vy * slope / DRAG_SPAN else 0f
        tallV = speed
        wideV = speed * DRAG_WIDTH_SHARE
        dragV = vy * slope * DRAG_FOLLOW
        val closes = vy > DRAG_CLOSE_VELOCITY || down > DRAG_CLOSE_DISTANCE
        if (closes) {
            goal = 0f
            tallGoal = 0f
            lag = AXIS_STAGGER
            isOpen.value = false
        }
        return closes
    }

    /** A touch on the open popover, in screen dp. Phase 0 = finger down, 1 = a tap ended there. */
    fun inside(x: Float, y: Float, phase: Int): Int {
        val px = x - contentX
        val py = y - contentY
        if (py < rowTop - 4f || py > rowTop + rowHeight + 4f) return INSIDE_NONE
        if (px >= sendLeft - 4f) {
            if (!ready()) return INSIDE_NONE
            if (phase == 0) {
                sendTo = 1f
                return INSIDE_NONE
            }
            return if (confirm()) INSIDE_SENT else INSIDE_NONE
        }
        val index = floor((px - faceLeft + (faceStep - faceSize) / 2f) / faceStep).toInt()
        if (phase == 1 && index in 0..3) {
            if (mood == index) {
                mood = -1
            } else {
                mood = index
                if (!reduced) popAge[index] = 0f
            }
            return INSIDE_FACE
        }
        return INSIDE_NONE
    }

    private fun ramp(raw: Float, to: Float, step: Float) = if (raw < to) min(raw + step, to) else max(raw - step, to)

    private fun stepLive(dt: Float) {
        pressRaw = ramp(pressRaw, pressTo, dt / PRESS_SECONDS)
        sendRaw = ramp(sendRaw, sendTo, dt / PRESS_SECONDS)
        pressAmount = easeInOutQuad(pressRaw)
        sendPress = easeInOutQuad(sendRaw)
        ghostAlpha = 0f
        for (i in 0..3) {
            faceRaw[i] = ramp(faceRaw[i], if (mood == i) 1f else 0f, dt / FACE_FILL_SECONDS)
            if (popAge[i] >= 0f) popAge[i] = if (popAge[i] > 1.6f) -1f else popAge[i] + dt
        }
        readyRaw = ramp(readyRaw, if (ready()) 1f else 0f, dt / SEND_FADE_SECONDS)
        if (dt <= 0f) return

        val to = goal
        val held = dragging

        // The second axis hears about a new goal 50 ms after the first.
        if (lag > 0f) {
            lag -= dt
            if (lag <= 0f) {
                lag = 0f
                wideGoal = to
                tallGoal = to
            }
        }

        var w = wide
        var h = tall
        if (!held) {
            val toWide = wideGoal
            val toTall = tallGoal
            var vw = wideV
            var vh = tallV
            var y = dragY
            var vy = dragV
            if (reduced) {
                val k = 1f - exp(-dt * 14f)
                w += (toWide - w) * k
                h += (toTall - h) * k
                if (abs(toWide - w) < 0.004f) w = toWide
                if (abs(toTall - h) < 0.004f) h = toTall
                vw = 0f
                vh = 0f
                y = 0f
                vy = 0f
            } else {
                val steps = max(1, ceil(dt / SUBSTEP).toInt())
                val slice = dt / steps
                repeat(steps) {
                    vw += (WIDTH_STIFFNESS * (toWide - w) - WIDTH_DAMPING * vw) * slice
                    w += vw * slice
                    vh += (HEIGHT_STIFFNESS * (toTall - h) - HEIGHT_DAMPING * vh) * slice
                    h += vh * slice
                    vy += (-HEIGHT_STIFFNESS * y - HEIGHT_DAMPING * vy) * slice
                    y += vy * slice
                }
                if (abs(toWide - w) < 0.0004f && abs(vw) < 0.004f) {
                    w = toWide
                    vw = 0f
                }
                if (abs(toTall - h) < 0.0004f && abs(vh) < 0.004f) {
                    h = toTall
                    vh = 0f
                }
                if (abs(y) < 0.05f && abs(vy) < 0.5f) {
                    y = 0f
                    vy = 0f
                }
            }
            wide = w
            tall = h
            wideV = vw
            tallV = vh
            dragY = y
            dragV = vy
        }

        val follow = labelFollow + (h - labelFollow) * (1f - exp(-dt / FOLLOW_LABEL))
        labelFollow = if (abs(follow) < 0.0005f && h == 0f) 0f else follow
        val showing = to == 1f && !held
        content = clamp(content + if (showing) dt / CONTENT_IN else -dt / CONTENT_OUT, 0f, 1f)
        if (thanks >= 0f) thanks = if (thanks + dt >= THANKS_END) -1f else thanks + dt

        // Closed again: the next opening starts clean.
        val closed = goal == 0f && tall <= 0.02f
        if (closed && !wasClosed) {
            typed.intValue = 0
            mood = -1
            if (field.value.text.isNotEmpty()) field.value = TextFieldValue("")
        }
        wasClosed = closed
    }
}
