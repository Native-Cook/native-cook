package com.ngocdevv.nativecook.blocks.media.silverscreencarousel

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** The photograph drifts this many dp slower than its frame, like a print sliding under glass (line 33). */
private const val PARALLAX = 22f

private val CardColor = Color(0xFF0B0B0B)
private val CardBorderColor = Color(0xFF1F1F1F)
private val PhotoColor = Color(0xFF161616)
private val ShutterColor = Color(0xFFFFFDF4)

/** `#FFFFFF14`: white at 0x14 of 255, the 1 dp line around the photograph (`photoEdge`, line 127). */
private val PhotoEdgeColor = Color(1f, 1f, 1f, 20f / 255f)

private val CodeStyle = TextStyle(
    color = Color(0xFFF2F2F0),
    fontSize = 26.sp,
    fontWeight = FontWeight.SemiBold,
    letterSpacing = (-0.6).sp,
)
private val NameStyle = TextStyle(
    color = Color(0xFFE6E6E4),
    fontSize = 11.5.sp,
    fontWeight = FontWeight.Medium,
)
private val DescriptionStyle = TextStyle(
    color = Color(0xFF8A8A88),
    fontSize = 10.sp,
    lineHeight = 14.sp,
    lineHeightStyle = SilverScreenCarouselLineHeightStyle,
)

/**
 * One film-profile card (`ProfileCard`, SilverScreenCarousel.tsx:34): a 1 dp bordered, 15 dp rounded
 * panel with 7 dp of padding, a photograph, the profile's code, name and description, and its colour
 * bar. It is [cardWidth] dp wide and at least `cardWidth * 364 / 342` tall; it has the layers of the
 * React Native view in the same order: the photograph with its shutter flash and edge line, then the
 * dark veil that frames waiting at the edges wear (over the panel and the photograph, under the
 * text), then the details and the colour bar.
 *
 * [active] is the card in the gate: only it flashes and is read by accessibility. Everything that
 * moves reads the row's position while drawing, so a drifting row never recomposes the cards.
 */
@Composable
internal fun SilverScreenCarouselCard(
    index: Int,
    cardWidth: Float,
    photo: ImageBitmap?,
    motion: SilverScreenCarouselMotion,
    active: Boolean,
    reduced: Boolean,
    modifier: Modifier = Modifier,
) {
    val profile = SilverScreenCarouselProfiles[index]
    // `imageWidth = width - 15` (line 45): one dp wider than the card's content box, as in React Native.
    val imageWidth = cardWidth - 15f
    val imageHeight = imageWidth * 257f / 327f
    val shape = RoundedCornerShape(15.dp)
    Box(
        modifier = modifier
            .heightIn(min = (cardWidth * 364f / 342f).dp)
            .background(CardColor, shape)
            .border(1.dp, CardBorderColor, shape)
            // `accessible={active}` with one label; the cards waiting at the edges are hidden from accessibility.
            .clearAndSetSemantics {
                if (active) contentDescription = "${profile.name}. ${profile.description}"
            },
    ) {
        // The photograph: 1 dp border plus 7 dp padding from the card's corner.
        Canvas(
            Modifier
                .offset(8.dp, 8.dp)
                .size(imageWidth.dp, imageHeight.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(PhotoColor),
        ) {
            drawPhoto(photo, index, motion, active, reduced, imageWidth)
        }
        // The veil over the card's inner box (inside the border).
        Box(
            Modifier
                .matchParentSize()
                .drawBehind {
                    val alpha = (min(1.0, abs(index - motion.position())) * 0.22).toFloat()
                    if (alpha > 0f) {
                        val border = 1.dp.toPx()
                        drawRoundRect(
                            color = Color.Black,
                            topLeft = Offset(border, border),
                            size = Size(size.width - 2f * border, size.height - 2f * border),
                            cornerRadius = CornerRadius(15.dp.toPx()),
                            alpha = alpha,
                        )
                    }
                },
        )
        Column(
            Modifier.padding(start = 8.dp, top = (8f + imageHeight).dp, end = 8.dp, bottom = 8.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 5.dp)
                    .padding(top = 22.dp, bottom = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText(text = profile.code, style = CodeStyle)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    BasicText(text = profile.name, style = NameStyle)
                    BasicText(text = profile.description, style = DescriptionStyle)
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 5.dp, end = 5.dp, bottom = 7.dp)
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Brush.horizontalGradient(profile.colors)),
            )
        }
    }
}

/**
 * The photograph with its parallax, the shutter flash and the 1 dp edge line, in the order of the
 * React Native layers (lines 47-50). The photograph is centre-cropped (`contentFit="cover"`) into a box
 * [PARALLAX] dp wider than the frame on each side, and shifted by the card's distance from the gate.
 */
private fun DrawScope.drawPhoto(
    photo: ImageBitmap?,
    index: Int,
    motion: SilverScreenCarouselMotion,
    active: Boolean,
    reduced: Boolean,
    imageWidth: Float,
) {
    if (photo != null) {
        val distance = max(-1.0, min(1.0, index - motion.position()))
        val shift = if (reduced) 0f else (distance * -PARALLAX).toFloat() * density
        val destinationWidth = ((imageWidth + PARALLAX * 2f) * density).roundToInt()
        val destinationHeight = size.height.roundToInt()
        val scale = max(destinationWidth.toFloat() / photo.width, destinationHeight.toFloat() / photo.height)
        val sourceWidth = min(photo.width, (destinationWidth / scale).roundToInt())
        val sourceHeight = min(photo.height, (destinationHeight / scale).roundToInt())
        translate(left = shift - PARALLAX * density, top = 0f) {
            drawImage(
                image = photo,
                srcOffset = IntOffset((photo.width - sourceWidth) / 2, (photo.height - sourceHeight) / 2),
                srcSize = IntSize(sourceWidth, sourceHeight),
                dstSize = IntSize(destinationWidth, destinationHeight),
                filterQuality = FilterQuality.Medium,
            )
        }
    }
    if (active) {
        val flash = motion.flash.value
        if (flash > 0f) drawRect(color = ShutterColor, alpha = flash)
    }
    val line = 1.dp.toPx()
    drawRoundRect(
        color = PhotoEdgeColor,
        topLeft = Offset(line / 2f, line / 2f),
        size = Size(size.width - line, size.height - line),
        cornerRadius = CornerRadius(10.dp.toPx() - line / 2f),
        style = Stroke(width = line),
    )
}
