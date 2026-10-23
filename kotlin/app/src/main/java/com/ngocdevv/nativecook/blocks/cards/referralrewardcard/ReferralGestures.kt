package com.ngocdevv.nativecook.blocks.cards.referralrewardcard

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import com.ngocdevv.nativecook.blocks.cards.referralrewardcard.ReferralPalette.Motion
import com.ngocdevv.nativecook.blocks.cards.referralrewardcard.ReferralPalette.Size
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sign

internal fun Modifier.referralRailGestures(
    state: ReferralState,
    density: Float,
) = pointerInput(state, density) {
    val velocity = VelocityTracker()
    awaitEachGesture {
        val first = awaitFirstDown(requireUnconsumed = false)
        state.touch()
        val at = state.scroll.value
        val canPeek =
            abs(at - ReferralMath.nearest(at)) < 0.005 &&
                (state.reduced || abs(at - state.follower.value) < 0.005)
        val origin = ReferralMath.nearest(at)
        state.scroll.cancel()
        state.hand = true
        state.down = true
        val start = first.position
        var from = at
        var moved = false
        var vertical = false
        var last = first
        velocity.resetTracking()
        velocity.addPosition(first.uptimeMillis, first.position)

        fun hits(
            x: Float,
            y: Float,
        ): Boolean {
            if (!state.open || state.peek.value <= 0.5 || state.joined(state.focus)) {
                return false
            }
            val presence = ((state.peek.value - 0.5) / 0.4).coerceIn(0.0, 1.0)
            val scale = if (state.reduced) 1.0 else 0.92 + 0.08 * presence
            val shift =
                if (state.reduced) 0.0 else (state.width / 2 - 66) * (1 - state.peek.value)
            return abs(x - (state.width - 44 - state.actionWidth / 2 - shift)) <=
                max(44.0, state.actionWidth * scale) / 2 &&
                abs(y - Size.cy) <= max(44.0, 44 * scale) / 2
        }
        state.actionPress.set(if (hits(start.x / density, start.y / density)) 1.0 else 0.0)
        while (last.pressed) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == first.id } ?: break
            last = change
            velocity.addPosition(change.uptimeMillis, change.position)
            val dx = (change.position.x - start.x) / density
            val dy = (change.position.y - start.y) / density
            if (abs(dy) > 4) vertical = true
            if (
                !hits(change.position.x / density, change.position.y / density) ||
                abs(dx) > 4 ||
                abs(dy) > 4
            ) {
                state.actionPress.set(0.0)
            }
            if (!moved && abs(dx) > 4) {
                moved = true
                state.changePeek(false)
                from = state.scroll.value + dx / Size.pitch
            }
            if (moved) state.scroll.set(from - dx / Size.pitch)
            change.consume()
        }
        state.actionPress.set(0.0)
        state.down = false
        val x = last.position.x / density
        val y = last.position.y / density
        if (moved) {
            val speed = -velocity.calculateVelocity().x / density / 56.0
            var to =
                ReferralMath.nearest(state.scroll.value + (speed * 0.16).coerceIn(-3.0, 3.0))
            if (abs(speed) > 1.5 && to == origin) to += sign(speed).toInt()
            state.settle(to, speed, true)
        } else if (vertical) {
            state.changePeek(false)
            state.settle(ReferralMath.nearest(state.scroll.value))
        } else if (state.open) {
            if (hits(x, y)) state.inviteCapsule() else state.changePeek(false)
        } else {
            val dx = x - state.width / 2
            val dy = y - Size.cy
            val half = Size.ring + (state.width / 2 - 66) * state.peek.value
            val end = max(abs(dx) - (half - Size.ring), 0.0)
            if (end * end + dy * dy <= Size.ring * Size.ring && (canPeek || state.peek.value > 0)) {
                state.settle(ReferralMath.nearest(state.scroll.value))
                state.changePeek(true)
            } else {
                state.changePeek(false)
                val steps =
                    if (abs(dx) < Size.ring) {
                        0
                    } else {
                        sign(dx).toInt() *
                            ReferralMath.nearest((abs(dx) - 6) / Size.pitch).coerceIn(1, 3)
                    }
                state.settle(ReferralMath.nearest(state.scroll.value) + steps)
            }
        }
    }
}

internal fun Modifier.referralButtonGesture(
    state: ReferralState,
    density: Float,
) = pointerInput(state, density) {
    awaitEachGesture {
        val first = awaitFirstDown(requireUnconsumed = false)
        state.touch()
        state.press.timing(1.0, Motion.press, state.now)
        var last = first
        var inside = true
        while (last.pressed) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == first.id } ?: break
            last = change
            inside =
                change.position.x >= -16 * density &&
                change.position.x <= size.width + 16 * density &&
                change.position.y >= -16 * density &&
                change.position.y <= size.height + 16 * density
            change.consume()
        }
        if (state.reduced) {
            state.press.timing(0.0, Motion.press, state.now)
        } else {
            state.press.spring(0.0, 300.0, 0.8, state.now)
        }
        if (inside) state.activate()
    }
}
