package com.ngocdevv.nativecook.blocks.lists.emberdayreview

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/** `ZoomIn.springify().damping(13).stiffness(260)` with mass 1: damping ratio 13 / (2 * sqrt(260)). */
private const val TICK_DAMPING_RATIO = 0.4031f
private const val TICK_STIFFNESS = 260f

/**
 * `Check`: a 28 dp round box with a grey ring; ticking fills it orange with a white tick that zooms in on a springy
 * overshoot (a 120 ms fade under Reduce Motion), and unticking lets the fill fade out in 120 ms.
 */
@Composable
internal fun EmberDayReviewCheck(checked: Boolean, reduced: Boolean) {
    val fillScale = remember { Animatable(0f) }
    val fillAlpha = remember { Animatable(0f) }
    var fillShown by remember { mutableStateOf(false) }
    LaunchedEffect(checked) {
        if (checked) {
            fillShown = true
            if (reduced) {
                fillScale.snapTo(1f)
                fillAlpha.snapTo(0f)
                fillAlpha.animateTo(1f, tween(120, easing = EmberQuadInOut))
            } else {
                fillAlpha.snapTo(1f)
                fillScale.snapTo(0f)
                fillScale.animateTo(1f, spring(dampingRatio = TICK_DAMPING_RATIO, stiffness = TICK_STIFFNESS))
            }
        } else if (fillShown) {
            fillAlpha.animateTo(0f, tween(120, easing = EmberQuadInOut))
            fillShown = false
        }
    }
    Box(Modifier.size(28.dp)) {
        Box(
            Modifier
                .size(28.dp)
                .background(EmberDayReviewPalette.CheckBody, CircleShape)
                .border(1.5.dp, if (checked) EmberDayReviewPalette.AccentCheck else EmberDayReviewPalette.CheckRing, CircleShape),
        )
        if (fillShown) {
            Box(
                Modifier
                    .size(28.dp)
                    .graphicsLayer {
                        scaleX = fillScale.value
                        scaleY = fillScale.value
                        alpha = fillAlpha.value
                    }
                    .background(EmberDayReviewPalette.AccentCheck, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                EmberDayReviewIcon(EmberDayReviewGlyph.Checkmark, 15.dp, Color.White)
            }
        }
    }
}
