package com.ngocdevv.nativecook.blocks.onboarding.quietsignalonboarding

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min

private val Footer = Color(0xFF24242B)

/**
 * The five pages and the Continue button (the `content` branch of `QuietSignalOnboarding`).
 *
 * The page area is a scroll view whose content is the five pages laid over each other; it is
 * centred in the area (top-aligned with large text), and its top and bottom edges blur
 * progressively when it overflows. A horizontal pan over the pages turns them; a finger anywhere
 * on the area pauses the loop. All sizes are in dp: [widthDp] and [heightDp] are the window's,
 * [topDp] and [bottomDp] the system bars' and cutout's insets.
 */
@Composable
internal fun QuietSignalOnboardingTour(
    controller: QuietSignalOnboardingController,
    widthDp: Float,
    heightDp: Float,
    topDp: Float,
    bottomDp: Float,
    reduced: Boolean,
) {
    val playback = controller.playback
    val density = LocalDensity.current
    val fontScale = density.fontScale
    val largeText = fontScale > LARGE_TEXT_SCALE
    // The diagram is the shorter of the width less 80, the height left after the copy and button
    // (at least 220), and 300 (230 with large text).
    val diagram = min(min(widthDp - 80f, max(220f, heightDp - topDp - bottomDp - 430f)), if (largeText) 230f else 300f)
    val scroll = remember(widthDp, fontScale) { ScrollState(0) }
    val page = controller.page

    Column(
        Modifier
            .fillMaxSize()
            .padding(top = (topDp + 16f).dp, bottom = (bottomDp + 10f).dp),
    ) {
        QuietSignalOnboardingBlurViewport(
            overflowing = { scroll.maxValue > 4f * density.density },
            backgroundColor = QuietSignalOnboardingBackground,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            QuietSignalOnboardingReadingScroll(scroll, playback)
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val viewport = maxHeight
                Column(
                    Modifier
                        .fillMaxSize()
                        .quietSignalOnboardingReading(playback)
                        .verticalScroll(scroll),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = viewport)
                            .padding(vertical = if (largeText) 16.dp else 0.dp),
                        verticalArrangement = if (largeText) Arrangement.Top else Arrangement.Center,
                    ) {
                        // The five pages laid over each other: the box is as tall as the tallest.
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .quietSignalOnboardingPan(controller)
                                .graphicsLayer { alpha = if (playback.previewing) 1f else controller.presence }
                                .clearAndSetSemantics {
                                    val current = QuietSignalOnboardingPages[page]
                                    contentDescription = "Page ${page + 1} of 5. ${current.title} ${current.body} ${current.art}"
                                    customActions = listOf(
                                        CustomAccessibilityAction("Next stage") {
                                            controller.go(controller.page + 1)
                                            true
                                        },
                                        CustomAccessibilityAction("Previous stage") {
                                            controller.go(controller.page - 1)
                                            true
                                        },
                                    )
                                },
                        ) {
                            for (index in 0 until QuietSignalOnboardingPages.size) {
                                QuietSignalOnboardingScene(index, controller, diagram.dp, largeText, reduced)
                            }
                        }
                    }
                }
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 50.dp),
        ) {
            QuietSignalOnboardingButton(
                label = if (page == QuietSignalOnboardingTimeline.LAST_PAGE) "Continue to a quiet moment" else "Continue",
                background = Footer,
                reduced = reduced,
                onPressIn = playback::pause,
                onPressOut = { playback.settle(QuietSignalOnboardingTimeline.ANCHORS[controller.page]) },
                onPress = controller::continueTour,
                modifier = Modifier.fillMaxWidth(),
            ) {
                QuietSignalOnboardingSwapText(
                    text = "Continue",
                    style = quietSignalOnboardingStyle(
                        color = Color(0xFFF2F2F7),
                        fontSize = 15f,
                        lineHeight = 20f,
                        fontWeight = FontWeight.Medium,
                        maxFontScale = 1.6f,
                    ),
                    reduced = reduced,
                )
            }
        }
    }
}

/** `largeText`: above this system font scale the page switches to its compact layout. */
private const val LARGE_TEXT_SCALE = 1.35f
