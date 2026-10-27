package com.ngocdevv.nativecook.blocks.onboarding.nocturneprogramcarousel

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.cssBoxShadow

private val TabNames = listOf("Home", "Program", "Sleep", "Statistic", "Profile")

private val HomePath = PathParser()
    .parsePathString("M4 11.5 12 5l8 6.5V20a1 1 0 0 1-1 1h-4.5v-5.5h-5V21H5a1 1 0 0 1-1-1Z")
    .toPath()
private val StatisticPath = PathParser().parsePathString("M3 13h4l3-7 4 13 3-6h4").toPath()
private val MouthPath = PathParser().parsePathString("M8.5 14.2c1.8 2 5.2 2 7 0").toPath()
private val MoonIconPath = PathParser()
    .parsePathString("M14.5 3.5a8.5 8.5 0 1 0 6 13.9A7 7 0 0 1 14.5 3.5Z")
    .toPath()

private val TabIdle = Color(0xFF3A4866)
private val TabInk = Color(0xFF010A16)

/**
 * `TabBar`: the app's tab bar, as the reference shows it. Program is the current tab and Sleep the
 * raised action. The row is 14 dp wider than its parent on each side (`marginHorizontal: -14`), the
 * tabs share it equally and sit on its bottom edge, and the Sleep pill rises 16 dp above its label's
 * column (`marginTop: -16`), which also pulls the label up. Hidden from accessibility.
 */
@Composable
internal fun NocturneProgramCarouselTabBar(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .bleedWidth(14.dp)
            .padding(top = 2.dp)
            .clearAndSetSemantics { },
        verticalAlignment = Alignment.Bottom,
    ) {
        for (name in TabNames) {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                if (name == "Sleep") {
                    SleepPill()
                    TabLabel("Sleep", Color(0xFF334263))
                } else {
                    val current = name == "Program"
                    TabIcon(name, if (current) Color.White else TabIdle)
                    TabLabel(name, if (current) Color.White else TabIdle)
                }
            }
        }
    }
}

@Composable
private fun TabLabel(text: String, color: Color) {
    NocturneProgramCarouselText(
        text = text,
        color = color,
        size = 10f,
        weight = FontWeight.SemiBold,
        scales = false,
    )
}

/** The raised Sleep button: a 66 by 48 pill, `#5B8DFF` to `#2D57E8` corner to corner, with a blue glow under it. */
@Composable
private fun SleepPill() {
    Box(
        modifier = Modifier
            .negativeTop(16.dp)
            .size(66.dp, 48.dp)
            .cssBoxShadow(Color(0x802D57E8), 8.dp, 22.dp, 24.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF5B8DFF), Color(0xFF2D57E8)))),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(26.dp)) {
            val factor = size.width / 24f
            scale(factor, factor, pivot = Offset.Zero) {
                drawPath(MoonIconPath, Color.White)
                drawCircle(Color.White, radius = 1.4f, center = Offset(17.6f, 7f))
            }
        }
    }
}

/** `TabIcon` for Home, Statistic and Profile (24 by 24 view box shown at 22 dp), and the mark for Program. */
@Composable
private fun TabIcon(name: String, color: Color) {
    if (name == "Program") {
        NocturneProgramCarouselMark(22f)
        return
    }
    Canvas(Modifier.size(22.dp)) {
        val factor = size.width / 24f
        scale(factor, factor, pivot = Offset.Zero) {
            when (name) {
                "Home" -> drawPath(HomePath, color, style = Fill)
                "Statistic" -> drawPath(
                    StatisticPath,
                    color,
                    style = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
                else -> {
                    drawCircle(color, radius = 9f, center = Offset(12f, 12f))
                    drawCircle(TabInk, radius = 1.2f, center = Offset(9f, 10f))
                    drawCircle(TabInk, radius = 1.2f, center = Offset(15f, 10f))
                    drawPath(
                        MouthPath,
                        TabInk,
                        style = Stroke(width = 1.6f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                    )
                }
            }
        }
    }
}

/** Lays the content out [bleed] wider than its parent on each side without changing the parent's size. */
private fun Modifier.bleedWidth(bleed: Dp): Modifier = layout { measurable, constraints ->
    val extra = bleed.roundToPx()
    val wide = constraints.maxWidth + extra * 2
    val placeable = measurable.measure(constraints.copy(minWidth = wide, maxWidth = wide))
    layout(constraints.maxWidth, placeable.height) { placeable.place(-extra, 0) }
}

/** A negative top margin: the content moves up by [margin] and takes that much less room in the column. */
private fun Modifier.negativeTop(margin: Dp): Modifier = layout { measurable, constraints ->
    val pull = margin.roundToPx()
    val placeable = measurable.measure(constraints)
    layout(placeable.width, placeable.height - pull) { placeable.place(0, -pull) }
}
