package com.ngocdevv.nativecook.blocks.onboarding.spectrumnewscarousel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * `TourButton` with a text label (the briefing's two buttons): as wide as its row and at least 48 dp
 * tall, radius 18, a 15 by 14 dp padding, its label 16 on 22 semibold, or 13 on 18 when [subtle]
 * (no fill). It presses to 0.97 in 120 ms and does not move under Reduce Motion.
 */
@Composable
internal fun SpectrumNewsCarouselTourButton(
    label: String,
    onPress: () -> Unit,
    onPressIn: () -> Unit,
    onPressOut: () -> Unit,
    reduced: Boolean,
    modifier: Modifier = Modifier,
    subtle: Boolean = false,
    background: Color = Color(0xFF256BFF),
    color: Color = Color(0xFFEAF1FF),
) {
    SpectrumNewsCarouselPressable(
        onPress = onPress,
        onPressIn = onPressIn,
        onPressOut = onPressOut,
        reduced = reduced,
        pressedScale = 0.97f,
        scaleMillis = if (reduced) 0 else 120,
        dimsWhenReduced = false,
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp),
    ) {
        if (!subtle) {
            Box(Modifier.matchParentSize().background(background, RoundedCornerShape(18.dp)))
        }
        SpectrumNewsCarouselBodyText(
            text = label,
            size = if (subtle) 13f else 16f,
            lineHeight = if (subtle) 18f else 22f,
            color = color,
            weight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 15.dp, vertical = 14.dp),
        )
    }
}
