package com.ngocdevv.nativecook.blocks.inputs.mentionautocomplete

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.cssBoxShadow
import kotlin.math.roundToInt

/**
 A drawn avatar (a port of Avatar.tsx): initials on a three-stop gradient disc, with a top
 highlight and an optional presence dot ringed in [ring].
 */
@Composable
fun MentionAutocompleteAvatar(
    initials: String,
    colors: List<Long>,
    size: Float,
    modifier: Modifier = Modifier,
    online: Boolean = false,
    ring: Color = Color(0xFFFBFAF7),
) {
    val dot = (size * 0.3f).roundToInt().toFloat()
    val brush = remember(colors) {
        // expo-linear-gradient from (0.15, 0) to (0.85, 1) of the disc.
        object : androidx.compose.ui.graphics.ShaderBrush() {
            override fun createShader(size: Size) = androidx.compose.ui.graphics.LinearGradientShader(
                from = Offset(size.width * 0.15f, 0f),
                to = Offset(size.width * 0.85f, size.height),
                colors = colors.map { Color(it) },
                colorStops = listOf(0f, 0.55f, 1f),
            )
        }
    }
    // Initials keep their size whatever the font scale (allowFontScaling={false} in React Native).
    val initialsSize = with(LocalDensity.current) { (size * 0.38f).dp.toSp() }
    Box(modifier.size(size.dp)) {
        Box(
            Modifier.size(size.dp)
                .cssBoxShadow(Color(0x2E1E1A14), 1.dp, 2.dp, (size / 2).dp)
                .clip(CircleShape)
                .background(brush)
                .drawBehind {
                    // RN's 1 pt top border on a round view: a thin highlight arc across the top.
                    val w = 1.dp.toPx()
                    drawArc(
                        Color(0x59FFFFFF), startAngle = 200f, sweepAngle = 140f, useCenter = false,
                        topLeft = Offset(w / 2, w / 2), size = Size(this.size.width - w, this.size.height - w), style = Stroke(w),
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                initials,
                style = TextStyle(
                    fontFamily = FontFamily.SansSerif, fontSize = initialsSize, fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFFFFDF8), letterSpacing = 0.2.sp, platformStyle = PlatformTextStyle(includeFontPadding = false),
                ),
            )
        }
        if (online) {
            Box(
                Modifier.offset((size - dot + 1).dp, (size - dot + 1).dp).size(dot.dp)
                    .background(Color(0xFF3DAA63), CircleShape)
                    .border(2.dp, ring, CircleShape),
            )
        }
    }
}
