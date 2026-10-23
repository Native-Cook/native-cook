package com.ngocdevv.nativecook.blocks.modals.emojiratingpopup

import androidx.compose.animation.core.EaseInOutQuad
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlin.math.max

internal const val STAR_SIZE = 36f
internal const val STAR_GAP = 14f

private val Outline = Color(0xFF8D939C)
private val Filled = Color(0xFFFFBE2E)
private const val POP_SCALE = 1.3f
private const val POP_UP_MS = 90
private const val POP_SETTLE_DAMPING = 0.55f
private const val POP_SETTLE_STIFFNESS = 233f
private const val SPARK_MS = 380
private const val SPARK_REACH = 22f
private const val SPARK_SIZE = 5f

// Four sparkles leave the star along these directions (unit vectors).
private val Sparks = listOf(0.71f to -0.71f, 0.71f to 0.71f, -0.71f to 0.71f, -0.71f to -0.71f)

internal fun starsWidth(count: Int) = count * STAR_SIZE + (count - 1) * STAR_GAP

/**
 * One outline star. It fills yellow with a pop and a few sparkles when the rating reaches it.
 *
 * @param lit Whether the rating has reached this star.
 * @param appear 0 until the star has appeared with the dialog, 1 when it is in place; may overshoot.
 */
@Composable
internal fun RatingStar(lit: Boolean, appear: () -> Float, reduced: Boolean) {
    val scope = rememberCoroutineScope()
    val pop = remember { Channel(1f, scope) }
    val spark = remember { Channel(0f, scope) }
    var seen by remember { mutableStateOf(false) }

    LaunchedEffect(lit) {
        // The first composition is the starting state, not a change.
        if (!seen) {
            seen = true
            return@LaunchedEffect
        }
        if (lit && !reduced) {
            pop.go {
                animateTo(POP_SCALE, tween(POP_UP_MS, easing = EaseInOutQuad))
                animateTo(1f, spring(POP_SETTLE_DAMPING, POP_SETTLE_STIFFNESS))
            }
            spark.set(0f)
            spark.go { animateTo(1f, tween(SPARK_MS, easing = EaseInOutQuad)) }
        } else {
            pop.set(1f)
        }
    }

    Box(
        Modifier
            .size(STAR_SIZE.dp)
            .graphicsLayer {
                alpha = (appear() * 2f).coerceIn(0f, 1f)
                val s = max(0f, appear()) * pop.value
                scaleX = s
                scaleY = s
            },
        contentAlignment = Alignment.Center,
    ) {
        // Material's star fills 20 of its 24 units; the glyph in the React Native version fills almost its box.
        Icon(Icons.Outlined.StarOutline, null, Modifier.requiredSize((STAR_SIZE * 1.12f).dp), tint = Outline)
        if (lit) Icon(Icons.Filled.Star, null, Modifier.requiredSize((STAR_SIZE * 1.12f).dp), tint = Filled)
        for ((x, y) in Sparks) {
            Box(
                Modifier
                    .size(SPARK_SIZE.dp)
                    .graphicsLayer {
                        val p = spark.value
                        alpha = if (p <= 0f || p >= 1f) 0f else 1f - p
                        translationX = x * SPARK_REACH * p * density
                        translationY = y * SPARK_REACH * p * density
                        rotationZ = 45f
                        scaleX = 1f - p * 0.4f
                        scaleY = 1f - p * 0.4f
                    }
                    .clip(RoundedCornerShape(1.dp))
                    .background(Filled),
            )
        }
    }
}
