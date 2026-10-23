package com.ngocdevv.nativecook.blocks.gestures.pullleverrefresh

import androidx.compose.animation.core.Easing
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sqrt

private const val DurationModeMass = 4.0
private const val DurationModeDampingRatio = 1.0
private const val DurationModeEnergyThreshold = 0.000000006
private const val DurationModePerceptualCoefficient = 1.5
private const val DurationModeMaximumStiffness = 8_000.0
private const val DurationModeIterations = 100

private val CaptionEnterSpring = durationModeSpring(
    durationMillis = PullLeverRefreshMotion.Timing.charIn,
)

private val CaptionDefaultEasing = Easing { progress ->
    if (progress < 0.5f) {
        2f * progress * progress
    } else {
        1f - (-2f * progress + 2f) * (-2f * progress + 2f) / 2f
    }
}

/** Old and new rows overlap at the same trailing edge, matching the absolute React Native rows. */
@Composable
internal fun PullLeverRefreshCaption(
    text: String,
    reducedMotion: Boolean,
    color: Color,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
) {
    var shownText by remember {
        mutableStateOf(text)
    }
    var outgoingText by remember {
        mutableStateOf<String?>(null)
    }
    var generation by remember {
        mutableIntStateOf(0)
    }

    LaunchedEffect(text, reducedMotion) {
        if (text == shownText) {
            return@LaunchedEffect
        }
        outgoingText = shownText.takeIf { it.isNotEmpty() }
        shownText = text
        generation += 1
        val enterMillis = captionEnterTotalMillis(
            text = shownText,
            reducedMotion = reducedMotion,
        )
        val exitMillis = captionExitTotalMillis(
            text = outgoingText.orEmpty(),
            reducedMotion = reducedMotion,
        )
        delay(max(enterMillis, exitMillis))
        outgoingText = null
    }

    Box(
        modifier = modifier.wrapContentWidth(),
        contentAlignment = Alignment.CenterEnd,
    ) {
        outgoingText?.let { outgoing ->
            key("out-$generation-$outgoing") {
                PullLeverRefreshCaptionRow(
                    text = outgoing,
                    entering = false,
                    reducedMotion = reducedMotion,
                    color = color,
                    textStyle = textStyle,
                )
            }
        }
        key("in-$generation-$shownText") {
            PullLeverRefreshCaptionRow(
                text = shownText,
                entering = true,
                reducedMotion = reducedMotion,
                color = color,
                textStyle = textStyle,
            )
        }
    }
}

@Composable
private fun PullLeverRefreshCaptionRow(
    text: String,
    entering: Boolean,
    reducedMotion: Boolean,
    color: Color,
    textStyle: TextStyle,
) {
    Row {
        text.forEachIndexed { index, character ->
            key(index) {
                PullLeverRefreshCaptionGlyph(
                    character = character,
                    index = index,
                    entering = entering,
                    reducedMotion = reducedMotion,
                    color = color,
                    textStyle = textStyle,
                )
            }
        }
    }
}

@Composable
private fun PullLeverRefreshCaptionGlyph(
    character: Char,
    index: Int,
    entering: Boolean,
    reducedMotion: Boolean,
    color: Color,
    textStyle: TextStyle,
) {
    var progress by remember {
        mutableFloatStateOf(0f)
    }
    val travelPixels = with(LocalDensity.current) {
        PullLeverRefreshTokens.Type.captionTravel.dp.toPx()
    }
    val delayMillis = if (reducedMotion) {
        0L
    } else if (entering) {
        index * PullLeverRefreshMotion.Timing.charStagger
    } else {
        index * (PullLeverRefreshMotion.Timing.charStagger / 2L)
    }
    val durationMillis = if (reducedMotion) {
        PullLeverRefreshMotion.Timing.reduced.toInt()
    } else if (entering) {
        CaptionEnterSpring.first
    } else {
        PullLeverRefreshMotion.Timing.charOut.toInt()
    }
    val easing = if (!reducedMotion && entering) {
        CaptionEnterSpring.second
    } else {
        CaptionDefaultEasing
    }

    LaunchedEffect(entering, reducedMotion, index) {
        progress = 0f
        delay(delayMillis)
        animateCaptionProgress(
            durationMillis = durationMillis,
            easing = easing,
            set = { progress = it },
        )
    }

    BasicText(
        text = if (character == ' ') {
            "\u00a0"
        } else {
            character.toString()
        },
        style = textStyle.merge(TextStyle(color = color)),
        modifier = Modifier.graphicsLayer {
            val amount = progress
            alpha = if (entering) {
                amount
            } else {
                1f - amount
            }
            translationY = if (reducedMotion) {
                0f
            } else if (entering) {
                travelPixels * (1f - amount)
            } else {
                -travelPixels * amount
            }
        },
    )
}

/** Runs on the frame clock without Compose's system duration scale, matching ReduceMotion.Never. */
private suspend fun animateCaptionProgress(
    durationMillis: Int,
    easing: Easing,
    set: (Float) -> Unit,
) {
    if (durationMillis <= 0) {
        set(1f)
        return
    }
    val startNanos = System.nanoTime()
    val durationNanos = durationMillis * 1_000_000.0
    while (true) {
        val frameNanos = withFrameNanos { it }
        val linearProgress = ((frameNanos - startNanos) / durationNanos).toFloat().coerceIn(0f, 1f)
        set(easing.transform(linearProgress))
        if (linearProgress >= 1f) {
            return
        }
    }
}

private fun captionEnterTotalMillis(
    text: String,
    reducedMotion: Boolean,
): Long {
    if (text.isEmpty()) {
        return 0L
    }
    if (reducedMotion) {
        return PullLeverRefreshMotion.Timing.reduced
    }
    return CaptionEnterSpring.first +
        (text.length - 1) * PullLeverRefreshMotion.Timing.charStagger
}

private fun captionExitTotalMillis(
    text: String,
    reducedMotion: Boolean,
): Long {
    if (text.isEmpty()) {
        return 0L
    }
    if (reducedMotion) {
        return PullLeverRefreshMotion.Timing.reduced
    }
    return PullLeverRefreshMotion.Timing.charOut +
        (text.length - 1) * (PullLeverRefreshMotion.Timing.charStagger / 2L)
}

/** Ports Reanimated's duration-mode critical spring stiffness solve from springUtils.ts. */
private fun durationModeSpring(durationMillis: Long): Pair<Int, Easing> {
    val settlingSeconds = durationMillis * DurationModePerceptualCoefficient / 1_000.0
    var low = Double.MIN_VALUE
    var high = DurationModeMaximumStiffness
    repeat(DurationModeIterations) {
        val stiffness = (low + high) / 2.0
        val frequency = sqrt(stiffness / DurationModeMass) * DurationModeDampingRatio
        val displacement = (1.0 + frequency * settlingSeconds) * exp(-frequency * settlingSeconds)
        val velocity = -frequency * frequency * settlingSeconds * exp(-frequency * settlingSeconds)
        val initialEnergy = 0.5 * stiffness
        val energy = 0.5 * DurationModeMass * velocity * velocity +
            0.5 * stiffness * displacement * displacement
        if (energy / initialEnergy > DurationModeEnergyThreshold) {
            low = stiffness
        } else {
            high = stiffness
        }
    }
    val frequency = sqrt(((low + high) / 2.0) / DurationModeMass)
    val actualDurationMillis = (settlingSeconds * 1_000.0).toInt()
    val easing = Easing { progress ->
        if (progress >= 1f) {
            1f
        } else {
            val seconds = progress * settlingSeconds
            val remaining = (1.0 + frequency * seconds) * exp(-frequency * seconds)
            (1.0 - remaining).toFloat()
        }
    }
    return actualDurationMillis to easing
}
