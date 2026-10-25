package com.ngocdevv.nativecook.blocks.progress.rocketlaunchprogress

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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.cssBoxShadow
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/*
 Rocket Launch Progress — the text around the launch: the rolling status line, the odometer of the
 deploy's progress, the 3-2-1, the "Live" badge and the pipeline's steps. Each piece reads the
 loop's values (T = loop time, F = progress %, spin = spinner angle) in graphicsLayer and draw
 lambdas only, so none of it recomposes while the loop runs.
*/

internal val RocketInk = Color(0xFFF3EEF8)
internal val RocketInkSoft = Color(0xA3F3EEF8)
internal val RocketInkFaint = Color(0x66F3EEF8)
internal val RocketAmber = Color(0xFFFFA94D)
internal val RocketNight = Color(0xFF0B0D26)
private val CardColor = Color(0xFF17142A)

/** The loop's values the text reads. */
internal class RocketClock {
    val t = mutableFloatStateOf(0f)
    val fuel = mutableFloatStateOf(0f)
    val spin = mutableFloatStateOf(0f)
}

/** One line in, the previous one out upward: never two strings faded over each other in place. */
@Composable
internal fun RocketStatusLine(clock: RocketClock) {
    Box(Modifier.fillMaxWidth().height(22.dp).clipToBounds()) {
        RocketStatus.forEachIndexed { i, s ->
            BasicText(
                s,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().graphicsLayer {
                    val t = clock.t.floatValue.toDouble()
                    alpha = rocketStatusO(i, t).toFloat()
                    translationY = rocketStatusY(i, t).toFloat() * density
                },
                style = TextStyle(color = RocketInkSoft, fontSize = 15.sp, fontWeight = FontWeight.Medium),
            )
        }
    }
}

private const val DIGIT_H = 56

/** Column `place` (1, 10, 100): the ones roll with the value, higher columns turn over as the one below passes 9. */
@Composable
private fun OdoColumn(place: Int, clock: RocketClock) {
    Box(Modifier.width(32.dp).height(DIGIT_H.dp).clipToBounds()) {
        // The strip keeps its full eleven-digit height inside the one-digit window that clips it;
        // measured to the window, every digit after the first would get no height and not draw.
        Column(
            Modifier.wrapContentHeight(align = Alignment.Top, unbounded = true).graphicsLayer {
                val v = max(0f, clock.fuel.floatValue)
                val m = v / place
                val base = floor(m)
                val pos = if (place == 1) m else base + max(0f, (m - base) * 10 - 9)
                alpha = when (place) {
                    1 -> 1f
                    10 -> min(1f, max(0f, v - 9))
                    else -> min(1f, max(0f, v - 99))
                }
                translationY = -(pos % 10) * DIGIT_H * density
            },
        ) {
            for (d in 0..10) {
                Box(Modifier.width(32.dp).height(DIGIT_H.dp), contentAlignment = Alignment.Center) {
                    BasicText(
                        (d % 10).toString(),
                        style = TextStyle(color = RocketInk, fontSize = 52.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum", textAlign = TextAlign.Center),
                    )
                }
            }
        }
    }
}

@Composable
internal fun RocketOdometer(clock: RocketClock, modifier: Modifier) {
    // Right-aligned columns; the readout steps aside while the countdown and the badge have the sky.
    Column(
        modifier.graphicsLayer {
            val t = clock.t.floatValue
            val away = min(1f, max(0f, (t - 4.3f) / 0.2f)) * (1 - min(1f, max(0f, (t - 9.3f) / 0.3f)))
            alpha = 1 - away
            translationY = -10 * away * density
        },
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        BasicText("DEPLOY PROGRESS", style = TextStyle(color = RocketInkFaint, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp))
        Row(verticalAlignment = Alignment.Bottom) {
            OdoColumn(100, clock)
            OdoColumn(10, clock)
            OdoColumn(1, clock)
            BasicText(
                "%",
                modifier = Modifier.padding(start = 2.dp, bottom = 9.dp),
                style = TextStyle(color = RocketAmber, fontSize = 24.sp, fontWeight = FontWeight.Bold),
            )
        }
    }
}

@Composable
internal fun RocketCountdown(clock: RocketClock, modifier: Modifier) {
    val glow = with(LocalDensity.current) { 24.dp.toPx() }
    Box(modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
        for (k in 0..2) {
            BasicText(
                (3 - k).toString(),
                modifier = Modifier.graphicsLayer {
                    val t = clock.t.floatValue.toDouble()
                    alpha = rocketCountO(k, t).toFloat()
                    translationY = rocketCountY(k, t).toFloat() * density
                    val s = rocketCountS(k, t).toFloat()
                    scaleX = s; scaleY = s
                },
                style = TextStyle(
                    color = RocketInk,
                    fontSize = 96.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFeatureSettings = "tnum",
                    shadow = Shadow(Color(0x73FF9650), Offset.Zero, glow),
                ),
            )
        }
    }
}

@Composable
internal fun RocketBadge(clock: RocketClock, modifier: Modifier) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Row(
            Modifier
                .graphicsLayer {
                    val t = clock.t.floatValue.toDouble()
                    alpha = rocketBadgeO(t).toFloat()
                    val s = rocketBadgeS(t).toFloat()
                    scaleX = s; scaleY = s
                }
                .height(44.dp)
                .cssBoxShadow(Color(0x59000000), 10.dp, 30.dp, 22.dp)
                .background(Color(0xDB1E1834), RoundedCornerShape(22.dp))
                .border(1.dp, Color(0x66FFA94D), RoundedCornerShape(22.dp))
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = RocketAmber, modifier = Modifier.size(20.dp))
            BasicText("v4.2.0 is live", style = TextStyle(color = RocketInk, fontSize = 16.sp, fontWeight = FontWeight.Bold))
        }
    }
}

private val STEPS = listOf(
    "Build" to "2,184 modules · 41 s",
    "Test" to "612 checks passed",
    "Upload" to "38.4 MB to 3 regions",
    "Launch" to "Traffic 0 → 100 %",
)

@Composable
private fun StepRow(i: Int, clock: RocketClock, rowH: Float) {
    fun state() = rocketStepState(i, clock.t.floatValue.toDouble(), clock.fuel.floatValue.toDouble())
    Row(
        Modifier.fillMaxWidth().height(rowH.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            // Waiting: a hollow dot.
            Box(
                Modifier
                    .size(10.dp)
                    .graphicsLayer { alpha = if (state() == 0) 1f else 0f }
                    .border(1.5.dp, Color(0x59F3EEF8), CircleShape),
            )
            // Running: a ring with its top quarter lit, turning.
            Box(
                Modifier
                    .size(22.dp)
                    .graphicsLayer {
                        alpha = if (state() == 1) 1f else 0f
                        rotationZ = Math.toDegrees(clock.spin.floatValue.toDouble()).toFloat()
                    }
                    .drawBehind {
                        val sw = 2.5.dp.toPx()
                        val inset = sw / 2
                        val s = Size(size.width - sw, size.height - sw)
                        drawArc(Color(0x2EFFA94D), 0f, 360f, false, Offset(inset, inset), s, style = Stroke(sw))
                        drawArc(RocketAmber, 225f, 90f, false, Offset(inset, inset), s, style = Stroke(sw))
                    },
            )
            // Done: an amber disc with a check.
            Box(
                Modifier
                    .size(22.dp)
                    .graphicsLayer {
                        val on = state() == 2
                        alpha = if (on) 1f else 0f
                        val k = if (on) 1f else 0.85f
                        scaleX = k; scaleY = k
                    }
                    .background(RocketAmber, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = Color(0xFF2A1606), modifier = Modifier.size(12.dp))
            }
        }
        Row(
            Modifier
                .weight(1f)
                .graphicsLayer { alpha = if (state() == 0) 0.5f else 1f },
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BasicText(STEPS[i].first, modifier = Modifier.alignByBaseline(), style = TextStyle(color = RocketInk, fontSize = 16.sp, fontWeight = FontWeight.SemiBold))
            BasicText(
                STEPS[i].second,
                modifier = Modifier.weight(1f).alignByBaseline(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(color = RocketInkSoft, fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum", textAlign = TextAlign.End),
            )
        }
    }
}

@Composable
internal fun RocketStepsCard(clock: RocketClock, top: Float, height: Float, rowH: Float) {
    val shape = RoundedCornerShape(26.dp)
    Column(
        Modifier
            .offset(y = top.dp)
            .padding(horizontal = 14.dp)
            .fillMaxWidth()
            .height(height.dp)
            .cssBoxShadow(Color(0x66000000), 18.dp, 40.dp, 26.dp)
            .cssBoxShadow(Color(0x66000000), 1.dp, 2.dp, 26.dp)
            .background(CardColor, shape)
            .border(1.dp, Color(0x0FFFFFFF), shape)
            // The card is not part of the pad: a touch on it does not fuel.
            .pointerInput(Unit) { awaitEachGesture { awaitFirstDown(requireUnconsumed = false) } }
            .padding(start = 18.dp, end = 18.dp, top = 16.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
            BasicText("Pipeline", modifier = Modifier.weight(1f).alignByBaseline(), style = TextStyle(color = RocketInk, fontSize = 17.sp, fontWeight = FontWeight.Bold))
            BasicText("Hold the pad to fuel", modifier = Modifier.alignByBaseline(), style = TextStyle(color = RocketInkFaint, fontSize = 12.sp, fontWeight = FontWeight.SemiBold))
        }
        for (i in STEPS.indices) StepRow(i, clock, rowH)
    }
}
