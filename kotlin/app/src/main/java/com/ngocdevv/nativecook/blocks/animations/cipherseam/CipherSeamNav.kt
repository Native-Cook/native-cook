package com.ngocdevv.nativecook.blocks.animations.cipherseam

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

private val MarkColor = Color(0xFFF4F1FF)

/**
 * `nav`: the frosted capsule at the top, 44 dp tall with a 1 dp line at 12% white: a layers mark, a
 * Log in link, a white Talk to an Expert pill and a menu button. It sits above the violet panel,
 * which reaches 60 dp up under it. The display words stop growing at 1.3 times the text size
 * (`maxFontSizeMultiplier`).
 *
 * @param onTalk the pill was pressed (opens the info sheet)
 */
@Composable
internal fun CipherSeamNav(reduced: Boolean, onTalk: () -> Unit, modifier: Modifier = Modifier) {
    val capsule = RoundedCornerShape(22.dp)
    Row(
        modifier
            .zIndex(2f)
            .fillMaxWidth()
            .padding(start = 10.dp, end = 10.dp, top = 6.dp)
            .height(44.dp)
            .clip(capsule)
            .background(Color(0xFF0B0718))
            .border(1.dp, Color(0x1FFFFFFF), capsule)
            // 14 dp on the left and 6 dp on the right inside the 1 dp line.
            .padding(start = 15.dp, end = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LayersMark()
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText(
                text = "Log in",
                style = TextStyle(
                    color = Color(0xFFE8E4F2),
                    fontSize = cipherSeamSp(13f, NAV_TEXT_SCALE),
                    fontWeight = FontWeight.Medium,
                    lineHeightStyle = CipherSeamLineHeightStyle,
                ),
            )
            CipherSeamPressScale(
                label = "Talk to an expert about the tokenization demo",
                reduced = reduced,
                onPress = onTalk,
                surface = Modifier.height(28.dp).clip(RoundedCornerShape(14.dp)).background(Color.White),
                contentPadding = PaddingValues(horizontal = 11.dp),
            ) {
                BasicText(
                    text = "Talk to an Expert",
                    style = TextStyle(
                        color = Color(0xFF110A20),
                        fontSize = cipherSeamSp(13f, NAV_TEXT_SCALE),
                        fontWeight = FontWeight.SemiBold,
                        lineHeightStyle = CipherSeamLineHeightStyle,
                    ),
                )
            }
            Box(
                Modifier.size(40.dp, 30.dp).clip(RoundedCornerShape(15.dp)).background(Color(0xFF2A2735)),
                contentAlignment = Alignment.Center,
            ) {
                CipherSeamGlyph(CipherSeamIcon.Menu, 18f, Color(0xFFC9C3D6))
            }
        }
    }
}

/** `maxFontSizeMultiplier={1.3}` of the nav's words. */
private const val NAV_TEXT_SCALE = 1.3f

/** The layers mark, a 26 by 22 `Svg`: a filled diamond over two chevron lines of 2.2 dp. */
@Composable
private fun LayersMark() {
    val diamond = remember {
        Path().apply {
            moveTo(13f, 1f)
            lineTo(25f, 7f)
            lineTo(13f, 13f)
            lineTo(1f, 7f)
            close()
        }
    }
    val upper = remember {
        Path().apply {
            moveTo(1f, 11.5f)
            lineTo(13f, 17.5f)
            lineTo(25f, 11.5f)
        }
    }
    val lower = remember {
        Path().apply {
            moveTo(1f, 15.5f)
            lineTo(13f, 21.5f)
            lineTo(25f, 15.5f)
        }
    }
    Canvas(Modifier.size(26.dp, 22.dp)) {
        scale(density, density, pivot = Offset.Zero) {
            drawPath(diamond, MarkColor)
            drawPath(upper, MarkColor, style = Stroke(width = 2.2f, join = StrokeJoin.Round))
            drawPath(lower, MarkColor, style = Stroke(width = 2.2f, join = StrokeJoin.Round))
        }
    }
}
