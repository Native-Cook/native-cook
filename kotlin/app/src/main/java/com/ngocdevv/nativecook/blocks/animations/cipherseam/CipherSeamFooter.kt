package com.ngocdevv.nativecook.blocks.animations.cipherseam

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val LogoColor = Color(0xFFE9E6F2)

/**
 * `logos`: three invented partner logos on one row, an icon and a word each (hidden from
 * accessibility, like the React Native row): LEDGERLY in spaced capitals, Tripwell in bold, orbita light.
 */
@Composable
internal fun CipherSeamLogos() {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 22.dp, end = 22.dp, top = 67.dp)
            .clearAndSetSemantics { },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Logo(CipherSeamIcon.ReorderThree, 18f, "LEDGERLY", fontSize = 13f, weight = FontWeight.ExtraBold, letterSpacing = 1.6f)
        Logo(CipherSeamIcon.Sparkles, 14f, "Tripwell", fontSize = 16f, weight = FontWeight.Bold, letterSpacing = -0.3f)
        Logo(CipherSeamIcon.RadioButtonOn, 15f, "orbita", fontSize = 15f, weight = FontWeight.Normal, letterSpacing = 0f)
    }
}

@Composable
private fun Logo(icon: CipherSeamIcon, iconSize: Float, word: String, fontSize: Float, weight: FontWeight, letterSpacing: Float) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        CipherSeamGlyph(icon, iconSize, LogoColor)
        BasicText(
            text = word,
            style = TextStyle(
                color = LogoColor,
                fontSize = cipherSeamSp(fontSize),
                fontWeight = weight,
                letterSpacing = cipherSeamFixedSp(letterSpacing),
                lineHeightStyle = CipherSeamLineHeightStyle,
            ),
        )
    }
}

/** `domain`: the cipher.dev pill, centred, 4 dp under the logos. */
@Composable
internal fun ColumnScope.CipherSeamDomain() {
    Box(
        Modifier
            .align(Alignment.CenterHorizontally)
            .padding(top = 4.dp)
            .height(28.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF1B1724))
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = "cipher.dev",
            style = TextStyle(
                color = Color(0xFFBDB6CC),
                fontSize = cipherSeamSp(12f),
                lineHeightStyle = CipherSeamLineHeightStyle,
            ),
        )
    }
}
