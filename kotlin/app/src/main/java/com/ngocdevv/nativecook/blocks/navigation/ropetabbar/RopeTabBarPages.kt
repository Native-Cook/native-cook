package com.ngocdevv.nativecook.blocks.navigation.ropetabbar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableDoubleState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 The four pages of "Basecamp" (static content, composed once). Each page's artwork sits in a layer
 that lags behind the page by 30 % of the slide (`p` is the knot's place, read only in
 graphicsLayer), so the pages cross-slide with a parallax.
 */

internal val RopeInk = Color(0xFFF2EBDD)
private val InkSoft = Color(0xFF9FB0A5)
private val Card = Color(0xFF1D2C25)
private val Line = Color(0x17E2EEE5)
private val Accent = Color(0xFFE8743B)
internal val ropeTabular = TextStyle(fontFeatureSettings = "tnum")

/** A seeded wobble, so the contour map and the ridges look hand-surveyed but never change. */
private fun hash(i: Double): Double {
    val s = sin(i * 12.9898) * 43758.5453
    return s - floor(s)
}

private fun ridge(width: Float, height: Float, base: Float, rough: Float, seed: Double): Path {
    val path = Path()
    path.moveTo(0f, height)
    var x = 0.0
    val step = width / 14.0
    while (x <= width + 0.001) {
        val y = base - rough * (0.5 + 0.5 * sin(x * 0.018 + seed) + 0.35 * hash(x.roundToInt() + seed * 31))
        path.lineTo(x.toFloat(), y.toFloat())
        x += step
    }
    path.lineTo(width, height)
    path.close()
    return path
}

private fun contour(cx: Float, cy: Float, r: Float, seed: Double): Path {
    val path = Path()
    for (k in 0..36) {
        val a = k / 36.0 * PI * 2
        val rr = r * (1 + 0.16 * sin(3 * a + seed) + 0.08 * sin(5 * a + seed * 2))
        val x = (cx + cos(a) * rr * 1.35).toFloat()
        val y = (cy + sin(a) * rr).toFloat()
        if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

/** A layer 1.7 widths wide that lags behind its page by 30 % of the slide. */
@Composable
private fun BoxScope.Parallax(p: MutableDoubleState, index: Int, width: Float, content: @Composable () -> Unit) {
    Box(
        Modifier
            .align(Alignment.TopStart)
            .offset(x = (-width * 0.35f).dp)
            .requiredSize((width * 1.7f).dp, 220.dp)
            .graphicsLayer { translationX = (p.doubleValue - index).toFloat() * width * 0.3f * density },
    ) { content() }
}

@Composable
private fun RowScope.RowText(title: String, detail: String, trailing: String) {
    Row(Modifier.weight(1f).heightIn(min = 60.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = RopeInk, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, color = InkSoft, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, style = ropeTabular)
        }
        if (trailing.isNotEmpty()) Text(trailing, color = InkSoft, fontSize = 14.sp, fontWeight = FontWeight.Medium, style = ropeTabular)
    }
}

@Composable
private fun ListRow(title: String, detail: String, trailing: String, divider: Boolean, extra: (@Composable () -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .drawBehind { if (divider) drawLine(Line, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1f) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowText(title, detail, trailing)
        extra?.invoke()
    }
}

@Composable
private fun ListCard(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Card)
            .drawBehind { drawLine(Color(0x0DFFFFFF), Offset(0f, 0.5f), Offset(size.width, 0.5f), strokeWidth = 1f) }
            .padding(horizontal = 16.dp),
    ) { content() }
}

/** The hero card: a soft two-layer shadow drawn behind it (no Modifier.shadow on a sliding page). */
@Composable
private fun Hero(color: Color, content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(196.dp)
            .drawBehind {
                val r = 24.dp.toPx()
                // 0 16 32 rgba(0,0,0,0.25) and 0 1 2 rgba(0,0,0,0.3), approximated with stacked rounded rects.
                for (k in 1..8) {
                    val spread = k * 2.dp.toPx()
                    drawRoundRect(
                        Color.Black.copy(alpha = 0.035f),
                        topLeft = Offset(-spread / 2, 16.dp.toPx() - spread / 2 + 6.dp.toPx()),
                        size = Size(size.width + spread, size.height + spread - 12.dp.toPx()),
                        cornerRadius = CornerRadius(r + spread / 2),
                    )
                }
                drawRoundRect(Color.Black.copy(alpha = 0.3f), topLeft = Offset(0f, 1.dp.toPx()), size = size, cornerRadius = CornerRadius(r))
            }
            .clip(RoundedCornerShape(24.dp))
            .background(color),
        content = content,
    )
}

@Composable
private fun Chip(text: String, dark: Boolean) {
    Box(
        Modifier
            .padding(start = 14.dp, top = 14.dp)
            .height(28.dp)
            .background(if (dark) Color(0xD91D2C25) else Color(0x8C101A15), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = RopeInk, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
}

@Composable
private fun Landscape(w: Float) {
    val big = w * 1.7f
    Box(
        Modifier.fillMaxSize().drawWithCache {
            val sky = Brush.verticalGradient(0f to Color(0xFFF6D6B0), 0.45f to Color(0xFFE9A986), 1f to Color(0xFF9DB0BD), startY = 0f, endY = 220f)
            val lake = Brush.verticalGradient(listOf(Color(0xFF7D97A6), Color(0xFF2B4246)), startY = 150f, endY = 220f)
            val far = ridge(big, 220f, 112f, 46f, 1.3)
            val mid = ridge(big, 220f, 136f, 40f, 4.1)
            val near = ridge(big, 220f, 176f, 24f, 7.7)
            val front = ridge(big, 220f, 204f, 18f, 2.2)
            onDrawBehind {
                scale(density, density, pivot = Offset.Zero) {
                    drawRect(sky, size = Size(big, 220f))
                    drawCircle(Color(0xFFFFF1D6).copy(alpha = 0.9f), 22f, Offset(big * 0.55f, 96f))
                    drawPath(far, Color(0xFF8D9BB0).copy(alpha = 0.85f))
                    drawPath(mid, Color(0xFF5A6F84))
                    drawRect(lake, topLeft = Offset(0f, 150f), size = Size(big, 70f))
                    drawPath(near, Color(0xFF253B35))
                    drawPath(front, Color(0xFF16261F))
                }
            }
        },
    )
}

@Composable
private fun Topo(w: Float) {
    val big = w * 1.7f
    val cx = big * 0.5f
    Box(
        Modifier.fillMaxSize().drawWithCache {
            val rings = (0..6).map { contour(cx - 40f, 104f, 14f + it * 15f, 0.7 + it * 0.4) }
            val small = (0..3).map { contour(cx + 150f, 60f, 10f + it * 13f, 2.1 + it) }
            val trail = Path().apply {
                moveTo(cx - 150f, 190f)
                cubicTo(cx - 90f, 150f, cx - 120f, 110f, cx - 50f, 100f)
                // SVG's "S" reflects the previous control point (cx − 120, 110) about (cx − 50, 100).
                cubicTo(cx + 20f, 90f, cx + 40f, 60f, cx + 110f, 70f)
            }
            val dash = Stroke(3f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f, 5f)))
            onDrawBehind {
                scale(density, density, pivot = Offset.Zero) {
                    drawRect(Color(0xFFE9E1CF), size = Size(big, 220f))
                    rings.forEachIndexed { k, r -> drawPath(r, Color(0xFFA2906C).copy(alpha = 0.8f), style = Stroke(if (k % 3 == 0) 1.4f else 0.8f)) }
                    small.forEach { drawPath(it, Color(0xFFA2906C).copy(alpha = 0.7f), style = Stroke(0.8f)) }
                    drawPath(trail, Accent, style = dash)
                    drawCircle(Color(0xFF1D2C25), 6f, Offset(cx - 150f, 190f))
                    drawCircle(RopeInk, 6f, Offset(cx - 150f, 190f), style = Stroke(2f))
                    drawCircle(Accent, 7f, Offset(cx + 110f, 70f))
                    drawCircle(RopeInk, 7f, Offset(cx + 110f, 70f), style = Stroke(2f))
                }
            }
        },
    )
}

@Composable
private fun Sparkline(seed: Double) {
    Box(
        Modifier.size(60.dp, 26.dp).drawWithCache {
            val path = Path().apply {
                moveTo(0f, 22f)
                for (k in 0..12) lineTo(k * 5f, (22 - 16 * (0.3 + 0.7 * abs(sin(k * 0.55 + seed)) * hash(k + seed * 7))).toFloat())
            }
            onDrawBehind { scale(density, density, pivot = Offset.Zero) { drawPath(path, Accent, style = Stroke(1.6f, join = StrokeJoin.Round)) } }
        },
    )
}

@Composable
private fun PageColumn(lead: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(lead, color = InkSoft, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        content()
    }
}

@Composable
private fun Section(text: String) {
    Text(text, color = RopeInk, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
}

private data class Pack(val name: String, val kg: Float, val color: Color)

private val PackParts = listOf(
    Pack("Shelter", 2.6f, Color(0xFFE8743B)),
    Pack("Sleep", 2.1f, Color(0xFFD9B36C)),
    Pack("Kitchen", 1.4f, Color(0xFF7FA58E)),
    Pack("Clothing", 2.3f, Color(0xFF6F8FA8)),
)

/** The inner layer of the Gear and Profile cards, lagging by 12 % of the slide. */
@Composable
private fun Inner(p: MutableDoubleState, index: Int, width: Float, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .graphicsLayer { translationX = (p.doubleValue - index).toFloat() * width * 0.12f * density }
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
    ) { content() }
}

@Composable
internal fun RopeTabBarPage(index: Int, p: MutableDoubleState, width: Float) {
    when (index) {
        0 -> PageColumn("Near Alder Ridge · 14 °C and clear") {
            Hero(Card) {
                Parallax(p, 0, width) { Landscape(width) }
                Box(
                    Modifier
                        .fillMaxSize()
                        .drawBehind {
                            val top = size.height * 0.35f
                            drawRect(
                                Brush.verticalGradient(0f to Color(0x000C1611), 0.4f to Color(0x590C1611), 1f to Color(0xC70C1611), startY = top, endY = size.height),
                                topLeft = Offset(0f, top),
                                size = Size(size.width, size.height - top),
                            )
                        },
                )
                Chip("Trail of the week", dark = false)
                Column(Modifier.align(Alignment.BottomStart).padding(start = 18.dp, end = 18.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Kestrel Lake sunrise", color = RopeInk, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("4.2 km · 2 h 10 min · Moderate", color = RopeInk.copy(alpha = 0.75f), fontSize = 13.sp, fontWeight = FontWeight.Medium, style = ropeTabular)
                }
            }
            Section("Nearby")
            ListCard {
                ListRow("Pinecrest Loop", "6.8 km · ↑ 410 m · 3 h", "", divider = false) { Sparkline(1.2) }
                ListRow("Granite Steps", "3.1 km · ↑ 280 m · 1 h 40", "", divider = true) { Sparkline(3.4) }
            }
        }
        1 -> PageColumn("3 saved · 21.3 km planned this month") {
            Hero(Card) {
                Parallax(p, 1, width) { Topo(width) }
                Chip("Fernwater Trail · offline", dark = true)
            }
            Section("Saved routes")
            ListCard {
                ListRow("Fernwater Trail", "11.4 km · ↑ 620 m", "Sat", divider = false)
                ListRow("Pinecrest Loop", "6.8 km · ↑ 410 m", "Sun", divider = true)
            }
        }
        2 -> PageColumn("Weekend pack · 2 nights") {
            Hero(Color(0xFF22342B)) {
                Inner(p, 2, width) {
                    Text("BASE WEIGHT", color = InkSoft, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                    Text("8.4 kg", color = RopeInk, fontSize = 40.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp, style = ropeTabular)
                    Row(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        PackParts.forEach { Box(Modifier.weight(it.kg).fillMaxHeight().background(it.color)) }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        PackParts.forEach {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(Modifier.size(8.dp).background(it.color, CircleShape))
                                Text("${it.name} ${it.kg} kg", color = InkSoft, fontSize = 12.sp, fontWeight = FontWeight.Medium, style = ropeTabular)
                            }
                        }
                    }
                }
            }
            Section("Still to pack")
            ListCard {
                ListRow("Shell jacket", "Clothing", "420 g", divider = false)
                ListRow("Water filter", "Kitchen", "210 g", divider = true)
            }
        }
        else -> PageColumn("Alder Ridge hiking club · since 2023") {
            Hero(Color(0xFF22342B)) {
                Inner(p, 3, width) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Box(
                            Modifier
                                .size(52.dp)
                                .background(Accent, CircleShape)
                                .drawBehind { drawArc(Color(0x4DFFFFFF), 200f, 140f, false, style = Stroke(1.dp.toPx())) },
                            contentAlignment = Alignment.Center,
                        ) { Text("MR", color = Color(0xFF2A140A), fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                        Column {
                            Text("Mara Reyes", color = RopeInk, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text("Next out: Kestrel Lake, Sat 25 Oct", color = RopeInk.copy(alpha = 0.75f), fontSize = 13.sp, fontWeight = FontWeight.Medium, style = ropeTabular)
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("23" to "Summits", "412" to "km this year", "38" to "Nights out").forEach { (v, l) ->
                            Column(
                                Modifier.weight(1f).background(Color(0x0FF2EBDD), RoundedCornerShape(14.dp)).padding(vertical = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(v, color = RopeInk, fontSize = 20.sp, fontWeight = FontWeight.Bold, style = ropeTabular)
                                Text(l, color = InkSoft, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
            Section("Safety")
            ListCard {
                ListRow("Trip plan shared", "With Theo Lund", "On", divider = false)
                ListRow("Offline maps", "3 regions · 412 MB", "Ready", divider = true)
            }
        }
    }
}
