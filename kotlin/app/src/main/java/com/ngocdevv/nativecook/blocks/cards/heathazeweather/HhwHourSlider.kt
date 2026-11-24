// Heat Haze Weather — the hour slider (HhwHourSlider.tsx).
//
// Drawn rather than a Material slider: its thumb follows the hour every frame (the film and the
// finger's momentum both move it). A track that warms and cools along the afternoon, hour ticks, a
// thumb with a sun in it and a bubble with the time, rounded to the half hour. The ghost finger of the
// autoplay sits on the thumb. Every moving part is a graphicsLayer / draw lambda of the engine's tick.
package com.ngocdevv.nativecook.blocks.cards.heathazeweather

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.cssBoxShadow
import com.ngocdevv.nativecook.shared.rgba
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

private val SLIDER_MUTE = rgba(251, 246, 238, 0.66f)
private const val BUBBLE = 76f
private const val SLIDER_GHOST = 44f
/** half hours from H_MIN to H_MAX */
private val STEPS = List(((H_MAX - H_MIN) * 2).toInt() + 1) { H_MIN + it / 2.0 }
private val TICKS = List((H_MAX - H_MIN).toInt() + 1) { it }
private val MARKS = listOf(12, 15, 18, 21)
private val SUN_NOON = Color(0xFFFFC85A)
private val SUN_LATE = Color(0xFFFF9A57)
private val SUN_DUSK = Color(0xFFE8708A)

private fun clock(h: Double): String {
    val whole = floor(h).toInt()
    val hour12 = if (whole % 12 == 0) 12 else whole % 12
    return "$hour12:${if (h - whole >= 0.5) "30" else "00"} ${if (whole < 12) "AM" else "PM"}"
}

private fun mark(h: Int) = "${if (h % 12 == 0) 12 else h % 12} ${if (h < 12) "AM" else "PM"}"

@Composable
internal fun HhwHourSlider(engine: HhwEngine, density: Float, enabled: Boolean) {
    // the half hour the slider rests on, for accessibility (changes every half hour, not every frame)
    val spoken = clock(engine.half)
    Column {
        Box(Modifier.fillMaxWidth().height(30.dp)) {
            Box(
                Modifier
                    .graphicsLayer {
                        engine.tick
                        val w = engine.trackW
                        val u = ((engine.hour - H_MIN) / (H_MAX - H_MIN)).toFloat()
                        // the bubble stays inside the track while the thumb reaches the ends
                        translationX = min(max(u * w - BUBBLE / 2f, -10f), w - BUBBLE + 10f) * density
                    }
                    .size(BUBBLE.dp, 26.dp)
                    .cssBoxShadow(rgba(20, 12, 8, 0.25f), 1.dp, 2.dp, 13.dp)
                    .cssBoxShadow(rgba(20, 12, 8, 0.18f), 6.dp, 16.dp, 13.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(rgba(251, 246, 238, 0.94f)),
                contentAlignment = Alignment.Center,
            ) {
                STEPS.forEachIndexed { i, h ->
                    HhwLabel(
                        clock(h), 13f, FontWeight.Bold, Color(0xFF2A1F1A),
                        Modifier.graphicsLayer {
                            engine.tick
                            // the nearest half hour, rolling to the next one over the last part of each step
                            // (held to the track's ends, so the bubble keeps 12 PM or 9 PM while rubber-banded past them)
                            val at = rolled((engine.hour.coerceIn(H_MIN, H_MAX) - H_MIN) * 2 + 0.5, 0.65)
                            val d = (i - at).toFloat()
                            alpha = (1f - abs(d)).coerceIn(0f, 1f)
                            translationY = 9f * d * density
                        },
                    )
                }
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(36.dp)
                .onSizeChanged { engine.trackW = it.width / density }
                .semantics {
                    contentDescription = "Hour"
                    stateDescription = spoken
                }
                .pointerInput(engine, enabled) {
                    if (!enabled) return@pointerInput
                    awaitEachGesture {
                        val first = awaitFirstDown(requireUnconsumed = false)
                        first.consume()
                        val tracker = VelocityTracker()
                        tracker.addPosition(first.uptimeMillis, first.position)
                        engine.slideBegin(first.position.x / density)
                        var ended = false
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == first.id } ?: break
                                tracker.addPosition(change.uptimeMillis, change.position)
                                if (!change.pressed) {
                                    change.consume()
                                    engine.slideEnd(tracker.calculateVelocity().x / density)
                                    ended = true
                                    break
                                }
                                change.consume()
                                engine.slideUpdate(change.position.x / density)
                            }
                        } finally {
                            if (!ended) engine.slideCancel()
                            engine.slideFinalize()
                        }
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        Brush.horizontalGradient(
                            0f to Color(0xFFFFD36B),
                            0.38f to Color(0xFFFF9F5A),
                            0.72f to Color(0xFFE8708A),
                            1f to Color(0xFF5D5A9E),
                        ),
                    ),
            )
            Row(
                Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .offset(y = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                for (i in TICKS) {
                    val major = i % 3 == 0
                    Box(
                        Modifier
                            .width(1.dp)
                            .height(if (major) 6.dp else 4.dp)
                            .background(rgba(251, 246, 238, if (major) 0.6f else 0.35f)),
                    )
                }
            }
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .offset(y = ((36f - THUMB) / 2f).dp)
                    .graphicsLayer {
                        engine.tick
                        val u = ((engine.hour - H_MIN) / (H_MAX - H_MIN)).toFloat()
                        translationX = (u * engine.trackW - THUMB / 2f) * density
                    }
                    .size(THUMB.dp)
                    .cssBoxShadow(rgba(20, 12, 8, 0.35f), 1.dp, 2.dp, (THUMB / 2f).dp)
                    .cssBoxShadow(rgba(20, 12, 8, 0.28f), 8.dp, 18.dp, (THUMB / 2f).dp)
                    .background(Color(0xFFFBF6EE), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(12.dp)
                        .drawBehind {
                            engine.tick
                            val u = clamp01((engine.hour - H_MIN) / (H_MAX - H_MIN))
                            drawCircle(if (u < 0.55) SUN_NOON else if (u < 0.8) SUN_LATE else SUN_DUSK)
                        },
                )
            }
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .offset(y = ((36f - SLIDER_GHOST) / 2f).dp)
                    .graphicsLayer {
                        engine.tick
                        val u = ((engine.hour - H_MIN) / (H_MAX - H_MIN)).toFloat()
                        translationX = (u * engine.trackW - SLIDER_GHOST / 2f) * density
                        val s = 1f - 0.14f * engine.sliderGhostP
                        scaleX = s
                        scaleY = s
                        alpha = (engine.sliderGhostA * 0.9f).coerceIn(0f, 1f)
                    }
                    .size(SLIDER_GHOST.dp)
                    .background(rgba(251, 246, 238, 0.16f), CircleShape)
                    .border(1.5.dp, rgba(251, 246, 238, 0.62f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(14.dp).background(rgba(251, 246, 238, 0.5f), CircleShape))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            for (h in MARKS) HhwLabel(mark(h), 12f, FontWeight.Medium, SLIDER_MUTE)
        }
    }
}
