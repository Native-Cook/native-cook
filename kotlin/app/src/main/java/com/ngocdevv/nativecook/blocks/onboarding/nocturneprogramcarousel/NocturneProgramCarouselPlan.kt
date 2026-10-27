package com.ngocdevv.nativecook.blocks.onboarding.nocturneprogramcarousel

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** The three moments of the evening plan: time, title, body. */
private val PlanRows = listOf(
    Triple("21:15", "Put the day down", "Write one thing you want to leave for tomorrow."),
    Triple("21:25", "Find a quiet minute", "Dim the room and take a comfortable breath."),
    Triple("21:30", "Choose something gentle", "A few pages, a warm drink, a little less noise."),
)

/**
 * The local evening plan the Build my evening plan button opens: the mark, an eyebrow and a title,
 * a line of copy, three timed moments and a Back to the tour button at the bottom of the screen
 * (or at the end of the list when the screen is shorter). The content scrolls under the same
 * progressive blur as the tour, 30 dp at both edges. Its lines cascade in, 70 ms apart (see [Rise]).
 *
 * [onPressIn] and [onPressOut] are the tour's `controls` the button also carries; [onBack] returns to the tour.
 */
@Composable
internal fun NocturneProgramCarouselPlan(
    metrics: NocturneProgramCarouselMetrics,
    reduced: Boolean,
    onPressIn: () -> Unit,
    onPressOut: () -> Unit,
    onBack: () -> Unit,
) {
    val density = LocalDensity.current
    val scroll = rememberScrollState()
    NocturneProgramCarouselBlurViewport(
        enabled = true,
        overflowing = { scroll.maxValue > 4f * density.density },
        fadeTop = Color(0xFF0A1A3A),
        fadeBottom = Color(0xFF010A16),
        modifier = Modifier.fillMaxSize(),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val viewport = maxHeight
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = viewport)
                        .padding(
                            start = 28.dp,
                            top = (metrics.insetTop + 32f).dp,
                            end = 28.dp,
                            bottom = (metrics.insetBottom + 14f).dp,
                        ),
                ) {
                    Rise(0, reduced) {
                        NocturneProgramCarouselMark(50f)
                    }
                    Rise(1, reduced) {
                        Column {
                            NocturneProgramCarouselText(
                                text = "YOUR EVENING PLAN",
                                color = Color(0xFF78CFC8),
                                size = 11f,
                                letterSpacing = 2f,
                                modifier = Modifier.padding(top = 24.dp),
                            )
                            NocturneProgramCarouselText(
                                text = "A softer landing.",
                                color = Color(0xFFF1F5FC),
                                size = 36f,
                                weight = FontWeight.Bold,
                                letterSpacing = -1f,
                                modifier = Modifier.padding(top = 12.dp),
                            )
                        }
                    }
                    Rise(2, reduced) {
                        NocturneProgramCarouselText(
                            text = "Three small moments to make tonight your own.",
                            color = Color(0xFF9AAEC9),
                            size = 16f,
                            lineHeight = 24f,
                            modifier = Modifier.padding(top = 14.dp),
                        )
                    }
                    Column(
                        Modifier.padding(top = 42.dp),
                        verticalArrangement = Arrangement.spacedBy(28.dp),
                    ) {
                        PlanRows.forEachIndexed { index, (clock, title, body) ->
                            Rise(3 + index, reduced) {
                                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                                    NocturneProgramCarouselText(
                                        text = clock,
                                        color = Color(0xFF79DAD0),
                                        size = 13f,
                                        tabular = true,
                                        modifier = Modifier.padding(top = 3.dp),
                                    )
                                    Column(Modifier.weight(1f)) {
                                        NocturneProgramCarouselText(
                                            text = title,
                                            color = Color(0xFFDCE8F7),
                                            size = 18f,
                                            weight = FontWeight.SemiBold,
                                        )
                                        NocturneProgramCarouselText(
                                            text = body,
                                            color = Color(0xFF8CA0BD),
                                            size = 14f,
                                            lineHeight = 22f,
                                            modifier = Modifier.padding(top = 7.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    NocturneProgramCarouselButton(
                        label = "Back to the tour",
                        reduced = reduced,
                        onPress = onBack,
                        modifier = Modifier.fillMaxWidth(),
                        onPressIn = onPressIn,
                        onPressOut = onPressOut,
                    )
                }
            }
        }
    }
}

/**
 * `rise`: a plan line arrives as a short cascade. After `80 + 70 * order` ms it fades in and settles
 * 12 dp up over 380 ms on the strong ease-out, starting invisible and 12 dp low. Under Reduce Motion
 * it is simply there.
 */
@Composable
private fun Rise(order: Int, reduced: Boolean, content: @Composable () -> Unit) {
    val progress = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(reduced) {
        if (reduced) {
            progress.snapTo(1f)
        } else if (progress.value < 1f) {
            delay((80 + order * 70).toLong())
            progress.animateTo(1f, tween(380, easing = NocturneProgramCarouselMotion.Enter))
        }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = progress.value
                translationY = 12f * (1f - progress.value) * density
            },
    ) {
        content()
    }
}
