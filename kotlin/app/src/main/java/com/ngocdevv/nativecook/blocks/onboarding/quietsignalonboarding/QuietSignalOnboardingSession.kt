package com.ngocdevv.nativecook.blocks.onboarding.quietsignalonboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val Modes = listOf("A little focus", "A quiet break", "An easy evening")

/**
 * The silent session preview that the last Continue opens (the `session` branch of
 * `QuietSignalOnboarding`): the emblem, "Your moment is ready.", three directions, a 20:00 time with
 * a caption that follows the chosen direction, and a way back. It arrives as a short cascade, 70 ms
 * a group, each settling 12 dp. The emblem is the still, finished one (the diagram is always
 * `reduced` here). No audio plays.
 */
@Composable
internal fun QuietSignalOnboardingSession(
    controller: QuietSignalOnboardingController,
    topDp: Float,
    bottomDp: Float,
    reduced: Boolean,
) {
    val playback = controller.playback
    val density = LocalDensity.current
    val scroll = rememberScrollState()
    val mode = controller.sessionMode

    val eyebrow = quietSignalOnboardingStyle(
        color = Color(0xFF8A8A96),
        fontSize = 11f,
        letterSpacing = 3f,
        scalesWithSystem = false,
    )
    val title = quietSignalOnboardingStyle(
        color = Color(0xFFE8E8F0),
        fontSize = 28f,
        lineHeight = 35f,
        letterSpacing = -0.7f,
    )
    val body = quietSignalOnboardingStyle(
        color = QuietSignalOnboardingBodyColor,
        fontSize = 20.5f,
        lineHeight = 27f,
        maxFontScale = 1.9f,
    )
    val clockStyle = quietSignalOnboardingStyle(
        color = Color(0xFFD8D8E2),
        fontSize = 43f,
        fontWeight = FontWeight.Light,
        tabularNumbers = true,
    )
    val caption = quietSignalOnboardingStyle(color = Color(0xFF8E8E9C), fontSize = 12f)
    val note = quietSignalOnboardingStyle(color = Color(0xFF777783), fontSize = 12f, lineHeight = 19f)

    QuietSignalOnboardingBlurViewport(
        overflowing = { scroll.maxValue > 4f * density.density },
        backgroundColor = QuietSignalOnboardingBackground,
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(start = 28.dp, end = 28.dp, top = (topDp + 30f).dp, bottom = (bottomDp + 16f).dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier.quietSignalOnboardingRise(0, reduced),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BasicText(text = "QUIET SIGNAL", style = eyebrow)
                QuietSignalOnboardingDiagram(
                    stage = 4,
                    size = 210.dp,
                    time = { playback.displayTime },
                    live = { true },
                    reduced = true,
                )
            }
            Column(
                modifier = Modifier.quietSignalOnboardingRise(1, reduced),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BasicText(text = "Your moment is ready.", style = title)
                BasicText(
                    text = "Choose a direction for this 20-minute session.",
                    style = body,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
            Column(
                modifier = Modifier
                    .quietSignalOnboardingRise(2, reduced)
                    .fillMaxWidth()
                    .padding(top = 24.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                for (choice in Modes) {
                    val selected = mode == choice
                    QuietSignalOnboardingButton(
                        label = if (selected) "$choice, selected" else choice,
                        background = if (selected) Color(0xFFD8D8E0) else Color(0xFF212126),
                        reduced = reduced,
                        onPressIn = playback::pause,
                        onPressOut = { playback.settle(QuietSignalOnboardingTimeline.ANCHORS[controller.page]) },
                        onPress = { controller.selectMode(choice) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        BasicText(
                            text = choice,
                            style = buttonLabel(if (selected) Color(0xFF141419) else Color(0xFFB7B7C2)),
                        )
                    }
                }
            }
            Column(
                modifier = Modifier.quietSignalOnboardingRise(3, reduced),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BasicText(text = "20:00", style = clockStyle, modifier = Modifier.padding(top = 28.dp))
                QuietSignalOnboardingSwapText(
                    text = "$mode · silent session preview",
                    style = caption,
                    reduced = reduced,
                    modifier = Modifier.padding(top = 9.dp),
                )
                BasicText(
                    text = "This preview is yours to explore.\nNo audio is playing.",
                    style = note,
                    modifier = Modifier.padding(top = 18.dp),
                )
            }
            QuietSignalOnboardingButton(
                background = Color(0xFF25252D),
                reduced = reduced,
                onPressIn = playback::pause,
                onPressOut = { playback.settle(QuietSignalOnboardingTimeline.ANCHORS[controller.page]) },
                onPress = controller::backToIntroduction,
                modifier = Modifier
                    .padding(top = 26.dp)
                    .fillMaxWidth(),
            ) {
                BasicText(text = "Back to the introduction", style = buttonLabel(Color(0xFFEAF1FF)))
            }
        }
    }
}

/** The label of a `TourButton` whose child is a string: 16/22, weight 600, centred, grown at most 1.6 times. */
@Composable
private fun buttonLabel(color: Color) = quietSignalOnboardingStyle(
    color = color,
    fontSize = 16f,
    lineHeight = 22f,
    fontWeight = FontWeight.SemiBold,
    maxFontScale = 1.6f,
)

/**
 * `rise(order)`: `FadeInDown.duration(420).delay(100 + order * 70)` on the strong ease-out, from
 * transparent and 12 dp low. Under Reduce Motion there is no entrance.
 */
@Composable
private fun Modifier.quietSignalOnboardingRise(order: Int, reduced: Boolean): Modifier {
    val progress = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(reduced) {
        if (reduced) {
            progress.snapTo(1f)
        } else {
            progress.animateTo(
                1f,
                tween(RISE_MS, delayMillis = RISE_DELAY_MS + order * RISE_STEP_MS, easing = QuietSignalOnboardingTimeline.EnterEasing),
            )
        }
    }
    return graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * RISE_DISTANCE * density
    }
}

private const val RISE_MS = 420
private const val RISE_DELAY_MS = 100
private const val RISE_STEP_MS = 70
private const val RISE_DISTANCE = 12f
