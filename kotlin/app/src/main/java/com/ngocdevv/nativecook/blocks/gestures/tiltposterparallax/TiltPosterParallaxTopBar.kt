package com.ngocdevv.nativecook.blocks.gestures.tiltposterparallax

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun TiltPosterParallaxTopBar(stageWidth: Dp, safeTop: Dp) {
    val horizontal = (Tokens.LAYOUT.gutter - Tokens.LAYOUT.topBarInsetAdjustment).dp
    Row(
        modifier = Modifier
            .offset(x = horizontal, y = safeTop + Tokens.LAYOUT.topBar.dp)
            .width(stageWidth - horizontal * 2f)
            .height(Tokens.LAYOUT.close.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TopBarCircle(label = "Close") {
            Canvas(modifier = Modifier.size(Tokens.LAYOUT.closeIcon.dp)) {
                val inset = size.width * 0.28f
                drawLine(
                    color = Tokens.COLOR.ink,
                    start = Offset(inset, inset),
                    end = Offset(size.width - inset, size.height - inset),
                    strokeWidth = 1.7.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = Tokens.COLOR.ink,
                    start = Offset(size.width - inset, inset),
                    end = Offset(inset, size.height - inset),
                    strokeWidth = 1.7.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }
        Row(
            modifier = Modifier
                .height(Tokens.LAYOUT.awardHeight.dp)
                .background(
                    color = androidx.compose.ui.graphics.Color.Transparent,
                    shape = RoundedCornerShape((Tokens.LAYOUT.awardHeight / 2.0).dp),
                )
                .padding(horizontal = Tokens.LAYOUT.awardPadding.dp),
            horizontalArrangement = Arrangement.spacedBy(Tokens.LAYOUT.awardGap.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TrophyIcon()
            val award = buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold)) {
                    append(Tokens.COPY.award[0])
                }
                append(Tokens.COPY.award[1])
                withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold)) {
                    append(Tokens.COPY.award[2])
                }
                append(Tokens.COPY.award[3])
            }
            Text(
                text = award,
                color = Tokens.COLOR.inkFaint,
                fontSize = Tokens.TYPE.awardSize.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.SansSerif,
            )
        }
        TopBarCircle(label = "More options") {
            Canvas(modifier = Modifier.size(Tokens.LAYOUT.moreIcon.dp)) {
                val radius = 1.35.dp.toPx()
                drawCircle(color = Tokens.COLOR.ink, radius = radius, center = Offset(size.width * 0.25f, size.height / 2f))
                drawCircle(color = Tokens.COLOR.ink, radius = radius, center = Offset(size.width * 0.5f, size.height / 2f))
                drawCircle(color = Tokens.COLOR.ink, radius = radius, center = Offset(size.width * 0.75f, size.height / 2f))
            }
        }
    }
}

@Composable
private fun TopBarCircle(label: String, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(Tokens.LAYOUT.close.dp)
            .background(color = Tokens.COLOR.glass, shape = CircleShape)
            .border(width = 0.5.dp, color = Tokens.COLOR.glassBorder, shape = CircleShape)
            .semantics {
                role = Role.Button
                contentDescription = label
            },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun TrophyIcon() {
    Canvas(modifier = Modifier.size(Tokens.LAYOUT.awardIcon.dp)) {
        val stroke = 1.2.dp.toPx()
        val cup = Path().apply {
            moveTo(size.width * 0.27f, size.height * 0.18f)
            lineTo(size.width * 0.73f, size.height * 0.18f)
            lineTo(size.width * 0.66f, size.height * 0.58f)
            quadraticBezierTo(size.width * 0.5f, size.height * 0.74f, size.width * 0.34f, size.height * 0.58f)
            close()
        }
        drawPath(path = cup, color = Tokens.COLOR.trophy)
        drawArc(
            color = Tokens.COLOR.trophy,
            startAngle = 90f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(size.width * 0.04f, size.height * 0.24f),
            size = Size(size.width * 0.32f, size.height * 0.34f),
            style = Stroke(width = stroke),
        )
        drawArc(
            color = Tokens.COLOR.trophy,
            startAngle = -90f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(size.width * 0.64f, size.height * 0.24f),
            size = Size(size.width * 0.32f, size.height * 0.34f),
            style = Stroke(width = stroke),
        )
        drawLine(
            color = Tokens.COLOR.trophy,
            start = Offset(size.width / 2f, size.height * 0.62f),
            end = Offset(size.width / 2f, size.height * 0.84f),
            strokeWidth = stroke,
        )
        drawLine(
            color = Tokens.COLOR.trophy,
            start = Offset(size.width * 0.34f, size.height * 0.86f),
            end = Offset(size.width * 0.66f, size.height * 0.86f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}
