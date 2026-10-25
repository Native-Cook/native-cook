package com.ngocdevv.nativecook.blocks.navigation.magnetsnapdock

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 The tool panel above the dock. The card stays; its six contents (one per tool) are stacked and
 the selection cross-fades them: the old one lifts 8 dp and fades, the new one rises 8 dp into
 place, the title shrinking from 1.04× as it lands. Read only in graphicsLayer, from the state's
 selection and clock.
 */

internal val MagnetToolNames = listOf("Select", "Pen", "Brush", "Shapes", "Type", "Layers")
private val Subtitles = listOf("Move, scale and rotate", "Bézier paths · round caps", "Gouache round · wet edges", "Rectangle · 12 pt corners", "Grotesk Bold · 48 pt", "6 layers · 2 hidden")
private val Rows: List<List<Triple<String, String, Float>>> = listOf(
    listOf(Triple("Snap to grid", "8 px", 0.4f), Triple("Nudge", "1 px", 0.15f), Triple("Rotate step", "15°", 0.25f)),
    listOf(Triple("Stroke", "2 pt", 0.2f), Triple("Smoothing", "40 %", 0.4f), Triple("Pressure", "Off", 0.02f)),
    listOf(Triple("Size", "12 px", 0.48f), Triple("Flow", "68 %", 0.68f), Triple("Wetness", "30 %", 0.3f)),
    listOf(Triple("Corner", "12 pt", 0.5f), Triple("Fill", "80 %", 0.8f), Triple("Stroke", "0 pt", 0.02f)),
    listOf(Triple("Size", "48 pt", 0.6f), Triple("Tracking", "−1 %", 0.45f), Triple("Leading", "1.1", 0.55f)),
    listOf(Triple("Opacity", "100 %", 1f), Triple("Visible", "4 of 6", 0.66f), Triple("Locked", "2", 0.33f)),
)
private val Colors = MagnetToolColors.map { Color(0xFF000000.toInt() or it) }
private val Ink = Color(0xFFF1EDE6)
private val InkSoft = Color(0xFF9A9CA3)
private val Tabular = TextStyle(fontFeatureSettings = "tnum")

@Composable
private fun Content(index: Int, s: MagnetState) {
    Column(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                s.frame.intValue
                val q = magnetSwapAt(s.selFrom, s.selTo, s.selAt, s.clock).toFloat()
                var a = 0f
                var dy = 0f
                if (s.selFrom == s.selTo) {
                    a = if (index == s.selTo) 1f else 0f
                } else if (index == s.selTo) {
                    a = q
                    dy = 8f * (1 - q)
                } else if (index == s.selFrom) {
                    a = 1 - q
                    dy = -8f * q
                }
                alpha = a
                translationY = dy * density
            }
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(bottom = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier
                    .size(36.dp)
                    .drawBehind {
                        val r = CornerRadius(10.dp.toPx())
                        drawRoundRect(Color.Black.copy(alpha = 0.15f), topLeft = Offset(0f, 2.dp.toPx()), size = Size(size.width, size.height), cornerRadius = r)
                    }
                    .clip(RoundedCornerShape(10.dp))
                    .background(Colors[index])
                    .drawBehind { drawLine(Color(0x40FFFFFF), Offset(10.dp.toPx(), 0.5f), Offset(size.width - 10.dp.toPx(), 0.5f), strokeWidth = 1.dp.toPx()) },
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    MagnetToolNames[index],
                    color = Ink,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.3).sp,
                    modifier = Modifier.graphicsLayer {
                        s.frame.intValue
                        val q = if (s.selFrom == s.selTo) 1f else magnetSwapAt(s.selFrom, s.selTo, s.selAt, s.clock).toFloat()
                        val k = if (index == s.selTo) 1.04f - 0.04f * q else 1f
                        scaleX = k
                        scaleY = k
                        transformOrigin = TransformOrigin(0f, 0.5f)
                    },
                )
                Text(Subtitles[index], color = InkSoft, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(
                Modifier
                    .height(26.dp)
                    .background(Color(0x0FFFFFFF), RoundedCornerShape(13.dp))
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(Modifier.size(6.dp).background(Colors[index], CircleShape))
                Text("Active", color = Ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        for ((label, value, frac) in Rows[index]) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(label, color = InkSoft, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text(value, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, style = Tabular)
                }
                Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0x14FFFFFF))) {
                    Box(Modifier.fillMaxWidth(frac).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(Colors[index]))
                }
            }
        }
    }
}

/** The card. It does not move, so its shadow is a plain elevation shadow. */
@Composable
internal fun MagnetSnapDockPanel(s: MagnetState, top: Float, height: Float) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        Modifier
            .offset(y = top.dp)
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .height(height.dp)
            .shadow(18.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(shape)
            .background(Color(0xFF1F2126))
            .border(0.5.dp, Color(0x1AFFFFFF), shape)
            .drawBehind { drawLine(Color(0x0FFFFFFF), Offset(24.dp.toPx(), 1.dp.toPx()), Offset(size.width - 24.dp.toPx(), 1.dp.toPx()), strokeWidth = 1.dp.toPx()) }
            .semantics { contentDescription = "Tool settings" },
    ) {
        for (i in 0 until MAGNET_TOOLS) Content(i, s)
    }
}
