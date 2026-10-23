package com.ngocdevv.nativecook.blocks.animations.flarenotifications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// The small amount of chrome Flare shares with the other showcase specimens
// (ui.tsx), plus the pill copy styles. Copy uses the
// platform's system face ("sans-serif" in RN on Android).

private val graphite = Color(0xFF2C2C2C)
private val onGraphite = Color(0xFFEEEEEE)

// RN Text with an explicit lineHeight places the glyphs in the middle of the
// line box (CustomLineHeightSpan), with no font padding.
private val fixedLine = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)
private val noPadding = PlatformTextStyle(includeFontPadding = false)
// RN Text without a lineHeight keeps Android's default font padding.
private val fontPadding = PlatformTextStyle(includeFontPadding = true)

// The reference sets the name at a 0.185-height cap (22 here). Its tag line
// is ~7 at this size; 10 bold keeps the look and stays readable.
private fun titleStyle(color: Color) = TextStyle(
    color = color,
    fontFamily = FontFamily.SansSerif,
    fontSize = 22.sp,
    lineHeight = 26.sp,
    fontWeight = FontWeight.Bold,
    letterSpacing = (-0.3).sp,
    lineHeightStyle = fixedLine,
    platformStyle = noPadding,
)

private fun lineStyle(color: Color) = TextStyle(
    color = color,
    fontFamily = FontFamily.SansSerif,
    fontSize = 10.sp,
    lineHeight = 13.sp,
    fontWeight = FontWeight.Bold,
    letterSpacing = 0.4.sp,
    lineHeightStyle = fixedLine,
    platformStyle = noPadding,
)

@Composable
fun PillTitle(text: String, color: Color) {
    BasicText(text, style = titleStyle(color), maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
}

@Composable
fun PillLine(text: String, color: Color, modifier: Modifier = Modifier) {
    BasicText(text, modifier, style = lineStyle(color), maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
}

/** A quiet instruction line: 20 in from each side, centered, polite live region. */
@Composable
fun Hint(text: String, color: Color) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp), contentAlignment = Alignment.TopCenter) {
        BasicText(
            text,
            Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            style = TextStyle(
                color = color,
                fontFamily = FontFamily.SansSerif,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                textAlign = TextAlign.Center,
                lineHeightStyle = fixedLine,
                platformStyle = noPadding,
            ),
        )
    }
}

/** The graphite pill button with a counter-clockwise arrow (RN: Pressable, scale 0.96 while pressed). */
@Composable
fun ReplayButton(label: String, onPress: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        Modifier
            .graphicsLayer {
                val s = if (pressed) 0.96f else 1f
                scaleX = s
                scaleY = s
            }
            .background(graphite, RoundedCornerShape(999.dp))
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onPress)
            .heightIn(min = 36.dp)
            .padding(horizontal = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        // RN on Android draws the glyph; iOS uses SF Symbol arrow.counterclockwise.
        BasicText(
            "↺",
            Modifier.clearAndSetSemantics {},
            style = TextStyle(
                color = onGraphite,
                fontSize = 15.sp,
                lineHeight = 18.sp,
                lineHeightStyle = fixedLine,
                platformStyle = noPadding,
            ),
        )
        BasicText(
            label,
            style = TextStyle(
                color = onGraphite,
                fontFamily = FontFamily.SansSerif,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                platformStyle = fontPadding,
            ),
        )
    }
}
