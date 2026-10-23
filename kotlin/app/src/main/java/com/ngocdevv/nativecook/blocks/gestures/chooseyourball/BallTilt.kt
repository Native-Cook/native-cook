package com.ngocdevv.nativecook.blocks.gestures.chooseyourball

import androidx.compose.animation.core.animate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
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

/** Finger travel for the full tilt, in dp, and how hard the drag resists past it. */
private const val TILT_REACH = 190f
private const val TILT_RESIST = 2.6f

/** The spring home: damping 17, mass 0.9, stiffness 150. Looser than the snap; nothing is chosen by it. */
private val TILT_HOME_SPRING = reanimatedSpring(damping = 17f, mass = 0.9f, stiffness = 150f, visibilityThreshold = 0.0005f)

/**
 * Free look (useBallTilt.ts): a drag up or down flies the camera over or under the ball, and
 * letting go brings it back. The camera moves, not the ball, so the lights stay where they are
 * and the highlights sweep across the shell.
 */
@Stable
internal class BallTiltState(private val scope: CoroutineScope) {
    /**
     * -1…1 (a little more under resistance). Positive is a downward drag, which lifts the camera
     * to look down on the crown. Read it per frame, not in composition.
     */
    var tilt by mutableFloatStateOf(0f)
        private set

    private var homeJob: Job? = null

    /** `translationY` in dp since the drag was recognised. */
    fun update(translationY: Float) {
        homeJob?.cancel()
        homeJob = null
        val reach = translationY / TILT_REACH
        val over = abs(reach) - 1f
        tilt = if (over <= 0f) reach else sign(reach) * (1f + over / (1f + over * TILT_RESIST))
    }

    /**
     * A touch ended, however it ended: the camera goes home. The reference starts this spring on
     * every finalisation of the gesture; one already on its way is left to finish.
     */
    fun release() {
        if (homeJob != null || tilt == 0f) return
        homeJob = scope.launch {
            animate(initialValue = tilt, targetValue = 0f, animationSpec = TILT_HOME_SPRING) { value, _ -> tilt = value }
            homeJob = null
        }
    }
}

@Composable
internal fun rememberBallTiltState(): BallTiltState {
    val scope = rememberCoroutineScope()
    return remember(scope) { BallTiltState(scope) }
}
