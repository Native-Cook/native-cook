package com.ngocdevv.nativecook.blocks.sheets.eventpassshare

import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val PosterGreen = Color(0xFF164F3C)
private val PosterGold = Color(0xFFB89965)
private val PosterPaper = Color(0xFFFAF9F7)

@Composable
internal fun EventPassSharePoster(
    assets: EventPassShareAssets,
    scale: Float,
    accessible: Boolean = false,
    modifier: Modifier = Modifier,
) {
    fun dp(value: Float) = (value * scale).dp
    val density = LocalDensity.current
    fun sp(value: Float) = (value * scale / density.fontScale).sp
    val textMeasurer = rememberTextMeasurer()
    val dateText = "Saturday July 18"
    val dateBaseStyle = TextStyle(
        fontSize = sp(14f),
        fontWeight = FontWeight.Normal,
        fontFamily = FontFamily.Serif,
        fontStyle = FontStyle.Italic,
        letterSpacing = 0.sp,
    )
    val dateMeasuredWidth = textMeasurer.measure(dateText, dateBaseStyle, maxLines = 1).size.width
    val dateAvailableWidth = with(density) { dp(108f).toPx() }
    val dateScale = if (dateMeasuredWidth > 0) {
        (dateAvailableWidth / dateMeasuredWidth).coerceIn(0.72f, 1f)
    } else {
        1f
    }
    val semantics = if (accessible) {
        Modifier.clearAndSetSemantics {
            contentDescription = "Paws, People and Purpose event poster, Saturday July 18, 2 to 4 PM at Goodfolk Coffee in West Los Angeles"
        }
    } else {
        Modifier.clearAndSetSemantics { }
    }

    Box(
        modifier
            .size(dp(251f))
            .clip(RoundedCornerShape(dp(13f)))
            .background(PosterPaper)
            .border(dp(0.5f), Color.Black.copy(alpha = 0.035f), RoundedCornerShape(dp(13f)))
            .then(semantics),
    ) {
        Text(
            text = "FIELD & FELLOW",
            color = PosterGreen,
            fontSize = sp(7f),
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Serif,
            letterSpacing = sp(-0.2f),
            modifier = Modifier.offset(dp(38f), dp(16f)),
        )
        Text(
            text = "COMMON\nGROUND",
            color = Color(0xFF252A27),
            fontSize = sp(5.3f),
            lineHeight = sp(5.3f),
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = sp(0.8f),
            textAlign = TextAlign.End,
            modifier = Modifier.offset(dp(160f), dp(15f)).width(dp(55f)),
        )
        Text(
            text = "PAWS, PEOPLE &\nPURPOSE",
            color = PosterGreen,
            fontSize = sp(22f),
            lineHeight = sp(21f),
            fontWeight = FontWeight.Normal,
            letterSpacing = sp(-0.7f),
            maxLines = 2,
            overflow = TextOverflow.Clip,
            modifier = Modifier.offset(dp(38f), dp(51f)).size(dp(190f), dp(42f)),
        )
        Text(
            text = "A COMMUNITY CONVERSATION ON HOW WE SHOW UP FOR SHELTER DOGS",
            color = Color(0xD126322D),
            fontSize = sp(4.5f),
            lineHeight = sp(6f),
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace,
            letterSpacing = sp(-0.16f),
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = Modifier.offset(dp(38f), dp(96f)).width(dp(194f)),
        )
        Image(
            bitmap = assets.portraitStrip,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.offset(dp(38f), dp(105f)).size(dp(145f), dp(29f)),
        )
        Row(Modifier.offset(dp(38f), dp(136f))) {
            listOf("ARI", "MAYA", "JULES", "NOAH", "LEA").forEach { name ->
                Text(
                    text = name,
                    color = Color(0xFF313632),
                    fontSize = sp(4.2f),
                    lineHeight = sp(6f),
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.width(dp(29f)),
                )
            }
        }
        Text(
            text = dateText,
            color = PosterGold,
            fontSize = sp(14f * dateScale),
            lineHeight = sp(17f * dateScale),
            letterSpacing = 0.sp,
            fontWeight = FontWeight.Normal,
            fontFamily = FontFamily.Serif,
            fontStyle = FontStyle.Italic,
            maxLines = 1,
            modifier = Modifier
                .offset(dp(39f), dp(157f))
                .width(dp(108f))
                .graphicsLayer {
                    rotationZ = -4f
                    transformOrigin = TransformOrigin(53f / 108f, 14f / 17f)
                },
        )
        Text(
            text = "PANEL  ·  FIRESIDE CHAT\nTRAINING Q&A\nSILENT AUCTION FUNDRAISER\nLOCAL VENDORS",
            color = Color(0xFF303631),
            fontSize = sp(4.5f),
            lineHeight = sp(5f),
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.offset(dp(150f), dp(159f)),
        )
        Text(
            text = "2–4 PM\nGOODFOLK COFFEE, WEST LA",
            color = Color(0xFF303631),
            fontSize = sp(5.5f),
            lineHeight = sp(8f),
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.offset(dp(38f), dp(198f)),
        )
        Text(
            text = "SUPPORTED BY",
            color = Color(0xFF585B57),
            fontSize = sp(3.6f),
            lineHeight = sp(5f),
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.offset(dp(38f), dp(229f)),
        )
        Text(
            "GOOD DOGS",
            color = Color(0xFF333A35),
            fontSize = sp(5.2f),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.offset(dp(47f), dp(237f)),
        )
        Text(
            "PET KIND",
            color = Color(0xFF333A35),
            fontSize = sp(5.5f),
            fontFamily = FontFamily.Serif,
            fontStyle = FontStyle.Italic,
            modifier = Modifier.offset(dp(96f), dp(237f)),
        )
        Canvas(Modifier.offset(dp(152f), dp(238f)).size(dp(16f), dp(10f))) {
            val path = Path().apply {
                moveTo(0f, size.height * 0.8f)
                cubicTo(size.width * 0.3125f, -size.height * 0.1f, size.width * 0.6875f, -size.height * 0.1f, size.width, size.height * 0.8f)
                cubicTo(size.width * 0.625f, size.height * 0.5f, size.width * 0.375f, size.height * 0.5f, 0f, size.height * 0.8f)
                close()
                moveTo(size.width * 0.5f, 0f)
                lineTo(size.width * 0.5f, size.height)
            }
            drawPath(path, Color(0xFF333A35), style = Stroke(dp(1f).toPx()))
        }
    }
}
