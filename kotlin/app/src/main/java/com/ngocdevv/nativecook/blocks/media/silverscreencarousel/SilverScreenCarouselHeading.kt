package com.ngocdevv.nativecook.blocks.media.silverscreencarousel

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.min

/** `heading` (SilverScreenCarousel.tsx:104, 124): the title in Source Serif 4 over the grey subtitle. */
@Composable
internal fun SilverScreenCarouselHeading(
    serif: FontFamily,
    marginTop: Float,
    marginBottom: Float,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    // `maxFontSizeMultiplier={1.5}` on the title: its sp sizes grow with the system font scale only up to 1.5.
    val capped = remember(density) { Density(density.density, min(density.fontScale, 1.5f)) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = marginTop.dp, bottom = marginBottom.dp)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CompositionLocalProvider(LocalDensity provides capped) {
            BasicText(
                text = "Aesthetic shots\nwithout editing",
                style = TextStyle(
                    color = Color(0xFFD9D9D7),
                    fontFamily = serif,
                    fontSize = 28.sp,
                    lineHeight = 32.sp,
                    letterSpacing = (-0.4).sp,
                    textAlign = TextAlign.Center,
                    lineHeightStyle = SilverScreenCarouselLineHeightStyle,
                ),
                modifier = Modifier.semantics { heading() },
            )
        }
        BasicText(
            text = "Not another filter, but soft\nfilm-inspired profiles",
            style = TextStyle(
                color = Color(0xFF6E6E6E),
                fontSize = 13.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center,
                lineHeightStyle = SilverScreenCarouselLineHeightStyle,
            ),
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}
