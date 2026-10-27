package com.ngocdevv.nativecook.blocks.splash.daylighticonreveal

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The four blocks of copy that appear one after the other (`Reveal` in the React Native file), with
 * the time their fade starts and the lift curve each one follows.
 */
internal enum class DaylightIconRevealStage(val fadeStart: Double) {
    /** The title, `start={1.75}`. */
    Title(1.75),

    /** The line of copy, `start={2}`: its fade starts at 1.8 s and its lift trails the title's by 0.1 s. */
    Subtitle(1.8),

    /** The Continue button, `start={2.25}`: fades from 2.15 s and follows `BUTTON_LIFT`. */
    Button(2.15),

    /** The small print, `start={3.25}`: no lift curve, it rises by the remaining fade. */
    Note(3.25),
}

/**
 * `Reveal`: the copy materialises out of a soft focus while it rises from [distance] below, and
 * leaves upward into a blur from 7.2 s. Under Reduce Motion it is simply there. The values are read
 * from [time] inside the layer block, so a frame redraws this layer and nothing else.
 */
internal fun Modifier.daylightIconRevealStage(
    stage: DaylightIconRevealStage,
    reduced: Boolean,
    distance: Dp,
    time: () -> Double,
): Modifier = graphicsLayer {
    val timeline = DaylightIconRevealTimeline
    val t = time()
    val progress = if (reduced) 1.0 else timeline.Ease.at(timeline.segment(t, stage.fadeStart, stage.fadeStart + 0.75))
    val returnProgress = if (reduced) 0.0 else timeline.Ease.at(timeline.segment(t, 7.2, 7.55))
    val remaining = when (stage) {
        DaylightIconRevealStage.Title -> timeline.headingLift(t) / 0.32
        DaylightIconRevealStage.Subtitle -> timeline.headingLift(t - 0.1) / 0.32
        DaylightIconRevealStage.Button -> timeline.buttonLift(t) / 0.25
        DaylightIconRevealStage.Note -> 1 - progress
    }
    // Copy materialises out of a soft focus and leaves upward into one, never as a hard fade.
    val blur = if (reduced) 0.0 else max(0.0, 1 - progress) * 6 + returnProgress * 4
    val roundedBlur = (blur * 100).roundToInt() / 100.0
    alpha = (progress * (1 - returnProgress)).toFloat()
    translationY = if (reduced) 0f else ((distance.value * remaining - returnProgress * 10) * density).toFloat()
    renderEffect = if (alpha > 0f) daylightIconRevealBlur((roundedBlur * density).toFloat()) else null
}
