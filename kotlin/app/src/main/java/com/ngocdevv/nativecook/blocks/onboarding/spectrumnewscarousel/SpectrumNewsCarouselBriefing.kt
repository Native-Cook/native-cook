package com.ngocdevv.nativecook.blocks.onboarding.spectrumnewscarousel

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val SpectrumNewsCarouselBriefingLines = listOf(
    "Public space: who gets to use the waterfront?",
    "Design: how do paths make room for water?",
    "Everyday life: what makes a walk feel welcoming?",
)

/**
 * The daily briefing that Continue with Apple opens (`briefing` branch of `SpectrumNewsCarousel`): a
 * scrolling page with the mark and wordmark, a title, the waterfront photograph, one story, three ways
 * to look at it, a Save button and a way back to the tour. The edges of the scrolling page blur
 * progressively as it scrolls (`ProgressiveBlurViewport`, 30 dp, light).
 *
 * [safeTop] and [safeBottom] are the safe-area insets in dp.
 */
@Composable
internal fun SpectrumNewsCarouselBriefing(
    photo: ImageBitmap,
    saved: Boolean,
    reduced: Boolean,
    safeTop: Float,
    safeBottom: Float,
    onPressIn: () -> Unit,
    onPressOut: () -> Unit,
    onToggleSaved: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    val pixelsPerDp = LocalDensity.current.density
    SpectrumNewsCarouselBlurViewport(
        overflowing = { scroll.maxValue > 4f * pixelsPerDp },
        backgroundColor = Color(0xFFFCFBF8),
        modifier = modifier.fillMaxSize(),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(start = 27.dp, end = 27.dp, top = (safeTop + 24f).dp, bottom = (safeBottom + 20f).dp),
        ) {
            // briefingBrand
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                SpectrumNewsCarouselMark(34.dp)
                SpectrumNewsCarouselArtText(
                    "Spectrum",
                    size = 22f,
                    color = Color(0xFF25242A),
                    weight = FontWeight.ExtraBold,
                    letterSpacing = -0.7f,
                )
            }
            SpectrumNewsCarouselBodyText(
                "YOUR DAILY BRIEFING",
                size = 10f,
                color = Color(0xFFA19B91),
                letterSpacing = 1.7f,
                modifier = Modifier.padding(top = 33.dp),
            )
            SpectrumNewsCarouselBodyText(
                "Room for a\ndifferent view.",
                size = 39f,
                lineHeight = 44f,
                color = Color(0xFF2B292D),
                weight = FontWeight.Bold,
                letterSpacing = -1.3f,
                modifier = Modifier.padding(top = 12.dp),
            )
            SpectrumNewsCarouselBodyText(
                "A small collection to start your day.",
                size = 15f,
                lineHeight = 23f,
                color = Color(0xFF8D877D),
                modifier = Modifier.padding(top = 14.dp),
            )
            Image(
                bitmap = photo,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.High,
                modifier = Modifier.padding(top = 27.dp).fillMaxWidth().height(202.dp).clip(RoundedCornerShape(16.dp)),
            )
            SpectrumNewsCarouselBodyText(
                "PLACES · 4 MIN READ",
                size = 9f,
                color = Color(0xFF8B9478),
                weight = FontWeight.Bold,
                letterSpacing = 1.2f,
                modifier = Modifier.padding(top = 22.dp),
            )
            SpectrumNewsCarouselBodyText(
                "The city is meeting its river again.",
                size = 25f,
                lineHeight = 31f,
                color = Color(0xFF35322F),
                weight = FontWeight.SemiBold,
                letterSpacing = -0.6f,
                modifier = Modifier.padding(top = 10.dp),
            )
            SpectrumNewsCarouselBodyText(
                "A new waterfront garden turns a once-forgotten edge into a place to walk, gather and slow down.",
                size = 15f,
                lineHeight = 24f,
                color = Color(0xFF898278),
                modifier = Modifier.padding(top = 12.dp),
            )
            // perspectives: 25 dp above and below, a 1 dp line, then 19 dp before the label; 13 dp between lines.
            Column(Modifier.fillMaxWidth().padding(vertical = 25.dp)) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFE7E1D7)))
                Column(Modifier.padding(top = 19.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                    SpectrumNewsCarouselBodyText(
                        "THREE WAYS TO LOOK AT IT",
                        size = 9f,
                        color = Color(0xFFA29B90),
                        letterSpacing = 1.3f,
                    )
                    SpectrumNewsCarouselBriefingLines.forEach { line ->
                        SpectrumNewsCarouselBodyText(line, size = 13f, lineHeight = 21f, color = Color(0xFF716A61))
                    }
                }
            }
            SpectrumNewsCarouselTourButton(
                label = if (saved) "Saved to your reading list" else "Save this story",
                onPress = onToggleSaved,
                onPressIn = onPressIn,
                onPressOut = onPressOut,
                reduced = reduced,
                background = if (saved) Color(0xFFE4EDDF) else Color(0xFF222126),
                color = if (saved) Color(0xFF37532D) else Color(0xFFFBF9F5),
            )
            SpectrumNewsCarouselTourButton(
                label = "Back to the tour",
                onPress = onBack,
                onPressIn = onPressIn,
                onPressOut = onPressOut,
                reduced = reduced,
                subtle = true,
                color = Color(0xFF77746D),
            )
        }
    }
}
