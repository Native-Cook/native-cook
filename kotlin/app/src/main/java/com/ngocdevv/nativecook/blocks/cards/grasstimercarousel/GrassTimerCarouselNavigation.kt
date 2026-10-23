package com.ngocdevv.nativecook.blocks.cards.grasstimercarousel

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp

@Composable
internal fun GrassTimerBottomNavigation(
    textScale: Float,
    point: (Float) -> Dp,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .width(point(280f))
            .height(point(62f))
            .clip(RoundedCornerShape(point(31f)))
            .background(Color(0xEA202A32))
            .background(Color(0x2E5485BD))
            .border(point(1f), Color.White.copy(alpha = 0.18f), RoundedCornerShape(point(31f))),
    ) {
        NavigationTab("Home", selected = false, TabIcon.Home, textScale, point)
        NavigationTab("My Apps", selected = false, TabIcon.Grid, textScale, point)
        NavigationTab("Timer", selected = true, TabIcon.Play, textScale, point)
    }
}

@Composable
private fun NavigationTab(
    label: String,
    selected: Boolean,
    icon: TabIcon,
    textScale: Float,
    point: (Float) -> Dp,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .width(point(280f / 3f))
            .fillMaxHeight()
            .then(
                if (selected) {
                    Modifier
                        .clip(RoundedCornerShape(point(31f)))
                        .background(Color.Black.copy(alpha = 0.20f))
                } else {
                    Modifier
                },
            ),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            TabIcon(icon, selected, Modifier.size(point(21f)))
            Spacer(Modifier.height(point(4f)))
            Text(
                text = label,
                color = Color.White.copy(alpha = if (selected) 1f else 0.72f),
                fontSize = (12f * textScale).sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

private enum class TabIcon { Home, Grid, Play }

@Composable
private fun TabIcon(icon: TabIcon, selected: Boolean, modifier: Modifier) {
    val color = Color.White.copy(alpha = if (selected) 1f else 0.72f)
    Canvas(modifier) {
        when (icon) {
            TabIcon.Home -> {
                val path = Path().apply {
                    moveTo(size.width * 0.15f, size.height * 0.46f)
                    lineTo(size.width * 0.50f, size.height * 0.16f)
                    lineTo(size.width * 0.85f, size.height * 0.46f)
                    lineTo(size.width * 0.78f, size.height * 0.46f)
                    lineTo(size.width * 0.78f, size.height * 0.84f)
                    lineTo(size.width * 0.60f, size.height * 0.84f)
                    lineTo(size.width * 0.60f, size.height * 0.60f)
                    lineTo(size.width * 0.40f, size.height * 0.60f)
                    lineTo(size.width * 0.40f, size.height * 0.84f)
                    lineTo(size.width * 0.22f, size.height * 0.84f)
                    lineTo(size.width * 0.22f, size.height * 0.46f)
                    close()
                }
                drawPath(path, color, style = Stroke(size.width * 0.09f))
            }
            TabIcon.Grid -> {
                val tile = size.width * 0.32f
                val gap = size.width * 0.12f
                val start = (size.width - tile * 2 - gap) / 2
                listOf(
                    Offset(start, start),
                    Offset(start + tile + gap, start),
                    Offset(start, start + tile + gap),
                    Offset(start + tile + gap, start + tile + gap),
                ).forEach { topLeft ->
                    drawRoundRect(
                        color,
                        topLeft,
                        Size(tile, tile),
                        androidx.compose.ui.geometry.CornerRadius(tile * 0.18f),
                    )
                }
            }
            TabIcon.Play -> {
                val path = Path().apply {
                    moveTo(size.width * 0.28f, size.height * 0.14f)
                    lineTo(size.width * 0.82f, size.height * 0.50f)
                    lineTo(size.width * 0.28f, size.height * 0.86f)
                    close()
                }
                drawPath(path, color)
            }
        }
    }
}
