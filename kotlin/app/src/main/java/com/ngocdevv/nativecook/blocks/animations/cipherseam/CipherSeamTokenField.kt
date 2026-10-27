package com.ngocdevv.nativecook.blocks.animations.cipherseam

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * `TokenField`: what the card becomes on the other side of the seam, a dense matrix of 35 by 16
 * small glyphs on a 5.7 dp pitch (7 dp bold monospace, 1.5 dp of letter spacing, 8 dp lines) at 62%,
 * on a dark glass card the size of the card. The glyphs change twelve times a second; only this
 * text recomposes, when the phase changes. Under Reduce Motion it shows the first phase.
 */
@Composable
internal fun CipherSeamTokenField(clock: CipherSeamClock, reduced: Boolean) {
    val phase by remember(clock, reduced) {
        derivedStateOf { CipherSeamTimeline.tokenPhase(clock.time, reduced) }
    }
    Box(
        Modifier
            .size(CipherSeamTimeline.CARD_WIDTH.dp, CipherSeamTimeline.CARD_HEIGHT.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(Color(0x5905030E))
            // A 1 dp transparent line and 2 dp of padding inside it.
            .padding(3.dp),
    ) {
        BasicText(
            text = CipherSeamTimeline.glyphs[phase],
            modifier = Modifier.fillMaxSize().alpha(0.62f),
            style = TextStyle(
                color = Color(0xFFE9E6F1),
                fontSize = cipherSeamFixedSp(7f),
                lineHeight = cipherSeamFixedSp(8f),
                letterSpacing = cipherSeamFixedSp(1.5f),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                lineHeightStyle = CipherSeamLineHeightStyle,
            ),
            softWrap = false,
            maxLines = 16,
        )
    }
}
