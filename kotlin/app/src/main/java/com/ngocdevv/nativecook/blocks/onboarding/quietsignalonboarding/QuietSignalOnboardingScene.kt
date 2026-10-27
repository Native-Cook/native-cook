package com.ngocdevv.nativecook.blocks.onboarding.quietsignalonboarding

import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** One page of the tour: its copy and the description of its art for screen readers (`PAGES`). */
internal class QuietSignalOnboardingPage(val title: String, val body: String, val art: String)

internal val QuietSignalOnboardingPages = listOf(
    QuietSignalOnboardingPage(
        title = "We live in an over-\nstimulating world",
        body = "It makes us tired, stressed, and\nunable to rest",
        art = "A profile, document and winding lines show a busy mind.",
    ),
    QuietSignalOnboardingPage(
        title = "A new world needs\nnew solutions",
        body = "Technology that can help us\nto adapt",
        art = "A luminous profile with gentle sound waves and audio bars.",
    ),
    QuietSignalOnboardingPage(
        title = "That’s why we made Quiet Signal",
        body = "An antidote to a distracting\nworld. Built on the science\nof sound",
        art = "An outlined phone carries waves between two sets of sound bars.",
    ),
    QuietSignalOnboardingPage(
        title = "Improve your state of mind\nwith the power of sound",
        body = "Focus, relax, and unwind.\nBe more present. See the\nmagic in everyday life",
        art = "Concentric rings and diagonal waves surround a profile.",
    ),
    QuietSignalOnboardingPage(
        title = "Welcome to the sound\nwellness revolution",
        body = "Welcome to Quiet Signal",
        art = "A complete circle surrounds an original continuous flowing emblem.",
    ),
)

/** `heading` colour. */
internal val QuietSignalOnboardingHeadingColor = Color(0xFFFFFFFF)

/** `body` colour, shared by the pages and the session preview. */
internal val QuietSignalOnboardingBodyColor = Color(0xFF7C7C80)

/**
 * `Scene`: page [index] of the tour. It is laid over the other four (the caller stacks them in
 * one box), with a spacer, the diagram, and the copy under it. The page's opacity, and the soft
 * focus the copy surfaces out of, are read from [controller] inside `graphicsLayer` blocks, so a
 * frame touches layers and recomposes nothing.
 *
 * Layout (`scene`, `copy`): centred, 28 dp of side padding, 18 dp above the diagram (none with
 * large text), 34 dp between diagram and copy (24 with large text), 10 dp between heading and body.
 */
@Composable
internal fun QuietSignalOnboardingScene(
    index: Int,
    controller: QuietSignalOnboardingController,
    size: Dp,
    largeText: Boolean,
    reduced: Boolean,
) {
    val page = QuietSignalOnboardingPages[index]
    val heading = quietSignalOnboardingStyle(
        color = QuietSignalOnboardingHeadingColor,
        fontSize = 20f,
        lineHeight = 27f,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = -0.2f,
        maxFontScale = 1.6f,
    )
    val body = quietSignalOnboardingStyle(
        color = QuietSignalOnboardingBodyColor,
        fontSize = 20.5f,
        lineHeight = 27f,
        maxFontScale = 1.9f,
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = controller.sceneOpacity(index).toFloat() }
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(if (largeText) 0.dp else 18.dp))
        QuietSignalOnboardingDiagram(
            stage = index,
            size = size,
            time = { controller.playback.displayTime },
            live = { controller.isLive(index) },
            reduced = reduced,
        )
        // Words surface out of the dark: a soft focus pull and a 6 dp rise under the fade.
        Column(
            modifier = Modifier
                .padding(top = if (largeText) 24.dp else 34.dp)
                .graphicsLayer {
                    val shown = controller.copyShown(index)
                    alpha = shown.toFloat()
                    translationY = ((1.0 - shown) * COPY_RISE * density).toFloat()
                    // `blur(${((1 - shown) * 6).toFixed(2)}px)`: N px is a standard deviation of N dp.
                    val sigma = (((1.0 - shown) * COPY_BLUR * 100.0).roundToInt() / 100.0).toFloat()
                    renderEffect = copyBlur(sigma * density)
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BasicText(text = page.title, style = heading)
            BasicText(text = page.body, style = body, modifier = Modifier.padding(top = 10.dp))
        }
    }
}

/** A Gaussian blur of [sigmaPx] device pixels, or null when there is nothing to blur or the device cannot (API 31). */
private fun copyBlur(sigmaPx: Float): RenderEffect? {
    if (sigmaPx < 0.005f || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    return BlurEffect(sigmaPx, sigmaPx, TileMode.Decal)
}

private const val COPY_RISE = 6.0
private const val COPY_BLUR = 6.0
