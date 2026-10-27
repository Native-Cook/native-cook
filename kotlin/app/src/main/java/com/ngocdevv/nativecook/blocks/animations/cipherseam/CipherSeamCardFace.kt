package com.ngocdevv.nativecook.blocks.animations.cipherseam

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val FaceStart = Color(0xFF212027)
private val FaceMiddle = Color(0xFF1C1B21)
private val FaceEnd = Color(0xFF18171C)
private val ChipLine = Color(0xFF8F978C)
private val CircleFill = Color(0xFFF4F2F8)
private val CircleRing = Color(0xFF1F1E25)

/**
 * `Card`: the sample payment card, 210 by 135 dp (scaled with the hero by its plane). A 1 dp line at
 * 5% white, a gradient fill with a faint highlight from the top-left, the chip and contactless mark
 * above, the number in the middle, the holder and expiry below. None of its text follows the system
 * text size (`allowFontScaling={false}`).
 */
@Composable
internal fun CipherSeamCardFace() {
    val outer = RoundedCornerShape(11.dp)
    Box(
        Modifier
            .size(CipherSeamTimeline.CARD_WIDTH.dp, CipherSeamTimeline.CARD_HEIGHT.dp)
            .clip(outer)
            .border(1.dp, Color(0x0DFFFFFF), outer),
    ) {
        // The gradient fills the padding box inside the line, from its top-left corner to its bottom-right.
        Box(
            Modifier
                .padding(1.dp)
                .fillMaxSize()
                .clip(RoundedCornerShape(10.dp))
                .background(
                    Brush.linearGradient(
                        0f to FaceStart,
                        0.55f to FaceMiddle,
                        1f to FaceEnd,
                        start = Offset.Zero,
                        end = Offset.Infinite,
                    ),
                )
                .drawBehind {
                    drawRect(
                        Brush.linearGradient(
                            listOf(Color(0x0CFFFFFF), Color(0x00FFFFFF)),
                            start = Offset.Zero,
                            end = Offset(size.width * 0.6f, size.height * 0.7f),
                        ),
                    )
                },
        ) {
            // 13 dp of padding inside the 1 dp line; the three rows are spread over what is left.
            Column(
                Modifier.fillMaxSize().padding(13.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                CardTop()
                BasicText(
                    text = "4821 0000 5577 0816",
                    style = TextStyle(
                        color = Color(0xFFE2DFE9),
                        fontSize = cipherSeamFixedSp(10.5f),
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = cipherSeamFixedSp(0f),
                        lineHeightStyle = CipherSeamLineHeightStyle,
                    ),
                )
                CardBottom()
            }
        }
    }
}

/** `cardTop`: the chip and the contactless mark on the left, the two circles on the right. */
@Composable
private fun CardTop() {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip()
            CipherSeamGlyph(CipherSeamIcon.Wifi, 14f, Color(0xFFA9A6B4), Modifier.rotate(90f))
        }
        Circles()
    }
}

/** `chip`: 30 by 23 dp, a pale gradient with a centre outline and a cross of hairlines. */
@Composable
private fun Chip() {
    Box(
        Modifier
            .size(30.dp, 23.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(
                Brush.linearGradient(
                    0f to Color(0xFFF4F6EE),
                    0.5f to Color(0xFFC9D4C6),
                    1f to Color(0xFFEEE8D6),
                    start = Offset.Zero,
                    end = Offset.Infinite,
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(12.dp, 15.dp).border(0.6.dp, ChipLine, RoundedCornerShape(3.dp)))
        Box(Modifier.size(30.dp, 0.6.dp).background(ChipLine))
        Box(Modifier.size(0.6.dp, 23.dp).background(ChipLine))
    }
}

/**
 * `cardCircles`: a 15 dp circle and a 16.5 dp circle with a 1.5 dp ring in the card's colour, which
 * starts 6 dp inside the first. Their centres are 7.5 dp from the top of a 25.5 by 15.75 dp box.
 */
@Composable
private fun Circles() {
    Canvas(Modifier.size(25.5.dp, 15.75.dp)) {
        drawCircle(CircleFill, radius = 7.5.dp.toPx(), center = Offset(7.5.dp.toPx(), 7.5.dp.toPx()))
        val second = Offset(17.25.dp.toPx(), 7.5.dp.toPx())
        drawCircle(CircleFill, radius = 8.25.dp.toPx(), center = second)
        drawCircle(CircleRing, radius = 7.5.dp.toPx(), center = second, style = Stroke(1.5.dp.toPx()))
    }
}

/** `cardBottom`: the holder and the expiry, with room for 40 dp at the right. */
@Composable
private fun CardBottom() {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        CardField("Card Holder", "Alex River")
        CardField("Expiry", "08/29")
        Spacer(Modifier.width(40.dp))
    }
}

@Composable
private fun CardField(label: String, value: String) {
    Column {
        BasicText(
            text = label,
            modifier = Modifier.padding(bottom = 2.dp),
            style = TextStyle(
                color = Color(0xFF8D889B),
                fontSize = cipherSeamFixedSp(6.5f),
                lineHeightStyle = CipherSeamLineHeightStyle,
            ),
        )
        BasicText(
            text = value,
            style = TextStyle(
                color = Color(0xFFE4E0EC),
                fontSize = cipherSeamFixedSp(9f),
                fontWeight = FontWeight.Medium,
                lineHeightStyle = CipherSeamLineHeightStyle,
            ),
        )
    }
}
