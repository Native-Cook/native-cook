package com.ngocdevv.nativecook.blocks.gestures.tiltposterparallax

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun TiltPosterParallaxTopBar(
    stageWidth: Dp,
    safeTop: Dp,
    typography: TiltPosterParallaxTypography,
) {
    Row(
        modifier = Modifier
            .offset(x = Tokens.LAYOUT.gutter.dp, y = safeTop + Tokens.LAYOUT.topBar.dp)
            .width(stageWidth - (Tokens.LAYOUT.gutter * 2.0).dp)
            .height(Tokens.LAYOUT.close.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TiltPosterParallaxTopBarCircle(label = "Close") {
            Canvas(modifier = Modifier.size(Tokens.LAYOUT.closeIcon.dp)) {
                val inset = size.width * 0.28f
                drawLine(Tokens.COLOR.ink, Offset(inset, inset), Offset(size.width - inset, size.height - inset), 1.7.dp.toPx(), StrokeCap.Round)
                drawLine(Tokens.COLOR.ink, Offset(size.width - inset, inset), Offset(inset, size.height - inset), 1.7.dp.toPx(), StrokeCap.Round)
            }
        }
        Row(
            modifier = Modifier
                .height(Tokens.LAYOUT.awardHeight.dp)
                .background(Tokens.COLOR.glass, RoundedCornerShape((Tokens.LAYOUT.awardHeight / 2.0).dp))
                .border(0.5.dp, Tokens.COLOR.glassBorder, RoundedCornerShape((Tokens.LAYOUT.awardHeight / 2.0).dp))
                .padding(horizontal = Tokens.LAYOUT.awardPadding.dp)
                .semantics { contentDescription = "3 festival awards" },
            horizontalArrangement = Arrangement.spacedBy(Tokens.LAYOUT.awardGap.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TiltPosterParallaxTrophyIcon()
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(fontFamily = typography.semibold, fontWeight = FontWeight.SemiBold)) {
                        append(Tokens.COPY.award[0])
                    }
                    append(Tokens.COPY.award[1])
                },
                color = Tokens.COLOR.inkSoft,
                fontSize = Tokens.TYPE.awardSize.sp,
                fontFamily = typography.medium,
                maxLines = 1,
            )
        }
        TiltPosterParallaxTopBarCircle(label = "Share") {
            Canvas(modifier = Modifier.size(Tokens.LAYOUT.shareIcon.dp)) {
                val stroke = 1.55.dp.toPx()
                drawLine(Tokens.COLOR.ink, Offset(size.width * 0.5f, size.height * 0.15f), Offset(size.width * 0.5f, size.height * 0.63f), stroke, StrokeCap.Round)
                drawLine(Tokens.COLOR.ink, Offset(size.width * 0.5f, size.height * 0.15f), Offset(size.width * 0.31f, size.height * 0.34f), stroke, StrokeCap.Round)
                drawLine(Tokens.COLOR.ink, Offset(size.width * 0.5f, size.height * 0.15f), Offset(size.width * 0.69f, size.height * 0.34f), stroke, StrokeCap.Round)
                val tray = Path().apply {
                    moveTo(size.width * 0.25f, size.height * 0.48f)
                    lineTo(size.width * 0.25f, size.height * 0.82f)
                    lineTo(size.width * 0.75f, size.height * 0.82f)
                    lineTo(size.width * 0.75f, size.height * 0.48f)
                }
                drawPath(tray, Tokens.COLOR.ink, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
    }
}

@Composable
private fun TiltPosterParallaxTopBarCircle(label: String, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(Tokens.LAYOUT.close.dp)
            .background(Tokens.COLOR.glass, CircleShape)
            .border(0.5.dp, Tokens.COLOR.glassBorder, CircleShape)
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
private fun TiltPosterParallaxTrophyIcon() {
    Canvas(modifier = Modifier.size(Tokens.LAYOUT.awardIcon.dp)) {
        val stroke = 1.2.dp.toPx()
        val cup = Path().apply {
            moveTo(size.width * 0.27f, size.height * 0.18f)
            lineTo(size.width * 0.73f, size.height * 0.18f)
            lineTo(size.width * 0.66f, size.height * 0.58f)
            quadraticBezierTo(size.width * 0.5f, size.height * 0.74f, size.width * 0.34f, size.height * 0.58f)
            close()
        }
        drawPath(cup, Tokens.COLOR.trophy)
        drawArc(Tokens.COLOR.trophy, 90f, 180f, false, Offset(size.width * 0.04f, size.height * 0.24f), Size(size.width * 0.32f, size.height * 0.34f), style = Stroke(stroke))
        drawArc(Tokens.COLOR.trophy, -90f, 180f, false, Offset(size.width * 0.64f, size.height * 0.24f), Size(size.width * 0.32f, size.height * 0.34f), style = Stroke(stroke))
        drawLine(Tokens.COLOR.trophy, Offset(size.width / 2f, size.height * 0.62f), Offset(size.width / 2f, size.height * 0.84f), stroke)
        drawLine(Tokens.COLOR.trophy, Offset(size.width * 0.34f, size.height * 0.86f), Offset(size.width * 0.66f, size.height * 0.86f), stroke, StrokeCap.Round)
    }
}
