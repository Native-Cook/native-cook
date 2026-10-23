package com.ngocdevv.nativecook.blocks.gestures.chooseyourball

import androidx.compose.animation.core.animate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlin.math.abs
import kotlin.math.sign
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The carousel (useChooseBallCarousel.ts): a loop of balls, a drag that turns it and a spring
 * that lands it. One swipe moves one ball; `selectSlot` travels any distance.
 */
@Stable
internal class BallCarouselState(initialBall: BallId, private val scope: CoroutineScope) {
    val order: List<BallId> = chooseBallOrder()
    val total: Int = order.size

    private val startSlot = order.indexOf(initialBall).coerceAtLeast(0)

    /**
     * The position in slots, fractional while it moves. It changes every frame of a swipe: read
     * it in a draw block, a layer block or a frame callback, never in composition.
     */
    var offset by mutableFloatStateOf(startSlot.toFloat())
        private set

    /** The slot nearest the position: it changes half way through a swipe, once per ball. */
    val preview: Int by derivedStateOf { wrapIndex(roundSlot(offset), total) }
    val previewBall: BallId get() = order[preview]

    private var panStart = offset
    private var panHome = startSlot
    private var settleJob: Job? = null

    /**
     * A finger is down: the loop stops where it is. If that touch never becomes a swipe the loop
     * stays there, between two balls, until the next swipe or tap on the strip, as in the reference.
     */
    fun panBegin() {
        settleJob?.cancel()
        settleJob = null
        panStart = offset
        panHome = roundSlot(offset)
    }

    /** `translationX` in dp since the swipe was recognised. Past one ball the drag resists. */
    fun panUpdate(translationX: Float) {
        val drift = panStart - translationX / ITEM_STRIDE - panHome
        val over = abs(drift) - PAN_REACH
        offset = if (over <= 0f) {
            panHome + drift
        } else {
            panHome + sign(drift) * (PAN_REACH + over / (1f + over * PAN_RESIST))
        }
    }

    /**
     * The finger lifted with `velocityX` in dp per second. The velocity only chooses the slot: the
     * fling is projected 0.18 s ahead, held to one ball, and the spring starts from rest.
     */
    fun panEnd(velocityX: Float) {
        val slotsPerSecond = -velocityX / ITEM_STRIDE
        val projected = offset + slotsPerSecond * FLING_PROJECTION_S
        val target = projected.coerceIn(panHome - PAN_REACH, panHome + PAN_REACH)
        settleOn(roundSlot(target))
    }

    /** The swipe was cancelled (a second finger, the system): land on the nearest slot. */
    fun panCancel() = settleOn(roundSlot(offset))

    /** Goes to `slot` the short way round the loop, however far it is. */
    fun selectSlot(slot: Int) {
        val here = roundSlot(offset)
        settleOn(here + cyclicDelta(slot.toFloat(), here.toFloat(), total).toInt())
    }

    private fun settleOn(target: Int) {
        settleJob?.cancel()
        settleJob = scope.launch {
            animate(initialValue = offset, targetValue = target.toFloat(), animationSpec = SNAP_SPRING) { value, _ ->
                offset = value
            }
            // At rest, bring the position back into the first lap. Every reader wraps it, so
            // nothing moves, and a float that only ever grew would lose precision.
            offset = wrapIndex(target, total).toFloat()
            settleJob = null
        }
    }
}

@Composable
internal fun rememberBallCarouselState(initialBall: BallId): BallCarouselState {
    val scope = rememberCoroutineScope()
    return remember(initialBall, scope) { BallCarouselState(initialBall, scope) }
}
