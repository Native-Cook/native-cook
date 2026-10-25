package com.ngocdevv.nativecook.blocks.navigation.railwayswitchnav

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.cssBoxShadow
import kotlin.math.max

// The four section pages of Northline (static content; the parent slides them in and out), as in
// RailwayPages.tsx. Each is a full section sized to the page area: a main card, and a second block
// when there is room (`room` = the page area's height). Copy and names are invented.

internal val RailInk = Color(0xFFEEF1EA)
internal val RailMuted = Color(0xFFEEF1EA).copy(alpha = 0.58f)
internal val RailGold = Color(0xFFF2C14E)
private val Good = Color(0xFF7BE39A)
private val Warn = Color(0xFFF2A65A)
private val Dark = Color(0xFF1A2420)
private val Line = Color(236, 244, 236)
/** Below this page height the second block is left out. */
private const val ROOM_FOR_MORE = 290f

private val tnum = "tnum"

@Composable
private fun RailCard(compact: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .cssBoxShadow(Color.Black.copy(alpha = 0.3f), offsetY = 1.dp, blur = 2.dp, radius = 20.dp)
            .cssBoxShadow(Color.Black.copy(alpha = 0.25f), offsetY = 12.dp, blur = 30.dp, radius = 20.dp)
            .background(Line.copy(alpha = 0.06f), RoundedCornerShape(20.dp))
            .border(Dp.Hairline, Line.copy(alpha = 0.16f), RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = if (compact) 10.dp else 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

@Composable
private fun RailHeading(kicker: String, title: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BasicText(kicker, style = TextStyle(color = RailGold, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp))
        BasicText(title, style = TextStyle(color = RailInk, fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private val LineStyle = TextStyle(color = RailInk, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
private val SubStyle = TextStyle(color = RailMuted, fontSize = 13.sp, fontFeatureSettings = tnum)

@Composable
private fun Dot(color: Color) {
    Box(Modifier.size(8.dp).background(color, CircleShape))
}

@Composable
private fun HomePage(room: Float) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        RailHeading("GOOD MORNING, MAREN", "Your 08:14 is on time")
        RailCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                BasicText("08:14", style = TextStyle(color = RailInk, fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp, fontFeatureSettings = tnum))
                Box(Modifier.height(26.dp).background(RailGold.copy(alpha = 0.16f), RoundedCornerShape(13.dp)).padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                    BasicText("Platform 3", style = TextStyle(color = RailGold, fontSize = 12.sp, fontWeight = FontWeight.Bold))
                }
            }
            BasicText("Elm Cross to Harbour Row", style = LineStyle)
            BasicText("22 min · 4 stops · leave home in 6 min", style = SubStyle)
            Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Line.copy(alpha = 0.12f))) {
                Box(Modifier.fillMaxWidth(0.62f).height(4.dp).background(RailGold, RoundedCornerShape(2.dp)))
            }
        }
        if (room >= ROOM_FOR_MORE) {
            RailCard(compact = true) {
                for ((name, text, color) in listOf(Triple("Harbour line", "Good service", Good), Triple("Millbank loop", "Minor delays, 4 min", Warn), Triple("Fenwick branch", "Good service", Good))) {
                    Row(Modifier.height(22.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Dot(color)
                        BasicText(name, Modifier.weight(1f), style = TextStyle(color = RailInk, fontSize = 14.sp, fontWeight = FontWeight.Medium))
                        BasicText(text, style = TextStyle(color = RailMuted, fontSize = 13.sp))
                    }
                }
            }
        }
    }
}

@Composable
private fun Rule() {
    Box(Modifier.fillMaxWidth().height(Dp.Hairline).background(Line.copy(alpha = 0.14f)))
}

@Composable
private fun TripsPage(room: Float) {
    val rows = listOf(
        listOf("Today", "08:14", "Elm Cross → Harbour Row", "22 min"),
        listOf("Fri", "17:42", "Harbour Row → Millbank", "35 min"),
        listOf("Sun", "10:05", "Elm Cross → Fenwick Bay", "1 h 12"),
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        RailHeading("TRIPS", "Three trips this week")
        Column(
            Modifier
                .fillMaxWidth()
                .cssBoxShadow(Color.Black.copy(alpha = 0.3f), offsetY = 1.dp, blur = 2.dp, radius = 20.dp)
                .cssBoxShadow(Color.Black.copy(alpha = 0.25f), offsetY = 12.dp, blur = 30.dp, radius = 20.dp)
                .background(Line.copy(alpha = 0.06f), RoundedCornerShape(20.dp))
                .border(Dp.Hairline, Line.copy(alpha = 0.16f), RoundedCornerShape(20.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            rows.forEachIndexed { i, r ->
                Column {
                    if (i > 0) Rule()
                    Row(Modifier.padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.width(48.dp)) {
                            BasicText(r[0], style = TextStyle(color = RailMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold))
                            BasicText(r[1], style = TextStyle(color = RailInk, fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = tnum))
                        }
                        BasicText(r[2], Modifier.weight(1f), style = TextStyle(color = RailInk, fontSize = 14.sp, fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        BasicText(r[3], style = TextStyle(color = RailMuted, fontSize = 13.sp, fontFeatureSettings = tnum))
                    }
                }
            }
        }
        if (room >= ROOM_FOR_MORE) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF1D2B27))
                    .border(Dp.Hairline, Line.copy(alpha = 0.16f), RoundedCornerShape(18.dp)),
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    // viewBox 0 0 320 64, xMidYMid slice.
                    val s = max(size.width / 320f, size.height / 64f)
                    val dx = (size.width - 320f * s) / 2f
                    val dy = (size.height - 64f * s) / 2f
                    translate(dx, dy) {
                        val back = Path().apply {
                            moveTo(0f * s, 50f * s)
                            cubicTo(60f * s, 50f * s, 70f * s, 22f * s, 120f * s, 22f * s)
                            cubicTo(170f * s, 22f * s, 190f * s, 44f * s, 240f * s, 30f * s)
                            cubicTo(290f * s, 16f * s, 300f * s, 12f * s, 320f * s, 14f * s)
                        }
                        drawPath(back, Color(232, 224, 200).copy(alpha = 0.18f), style = Stroke(6f * s))
                        val route = Path().apply {
                            moveTo(24f * s, 48f * s)
                            cubicTo(60f * s, 48f * s, 76f * s, 22f * s, 120f * s, 22f * s)
                            cubicTo(164f * s, 22f * s, 190f * s, 44f * s, 236f * s, 31f * s)
                        }
                        drawPath(route, RailGold, style = Stroke(3f * s, cap = StrokeCap.Round))
                        drawCircle(Dark, 5f * s, Offset(24f * s, 48f * s))
                        drawCircle(RailGold, 5f * s, Offset(24f * s, 48f * s), style = Stroke(2f * s))
                        drawCircle(RailGold, 3.5f * s, Offset(120f * s, 22f * s))
                        drawCircle(RailGold, 3.5f * s, Offset(180f * s, 36f * s))
                        drawCircle(RailGold, 5f * s, Offset(236f * s, 31f * s))
                    }
                }
                Row(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    BasicText("Elm Cross", style = TextStyle(color = RailMuted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold))
                    BasicText("Harbour Row · next trip", style = TextStyle(color = RailMuted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold))
                }
            }
        }
    }
}

private val Bars = intArrayOf(3, 1, 2, 1, 1, 3, 2, 1, 3, 1, 2, 2, 1, 3, 1, 1, 2, 3, 1, 2, 1, 3, 2, 1, 1, 2, 3, 1, 2, 1, 3, 1, 2, 1, 1, 3)

@Composable
private fun TicketsPage(room: Float) {
    val sub = TextStyle(color = Dark.copy(alpha = 0.62f), fontSize = 12.sp, fontFeatureSettings = tnum)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        RailHeading("TICKETS", "Monthly pass")
        Column(
            Modifier
                .fillMaxWidth()
                .cssBoxShadow(Color.Black.copy(alpha = 0.35f), offsetY = 1.dp, blur = 2.dp, radius = 20.dp)
                .cssBoxShadow(Color.Black.copy(alpha = 0.3f), offsetY = 14.dp, blur = 30.dp, radius = 20.dp)
                .background(Color(0xFFEFE6CC), RoundedCornerShape(20.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                BasicText("Zones 1 – 3", style = TextStyle(color = Dark, fontSize = 20.sp, fontWeight = FontWeight.Bold))
                BasicText("£142.50", style = TextStyle(color = Dark, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = tnum))
            }
            BasicText("Peak and off-peak · Maren Holt", style = sub)
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.Bottom) {
                for (b in Bars) {
                    Box(Modifier.width(b.dp).height(34.dp).background(Dark, RoundedCornerShape(0.5.dp)))
                    Spacer(Modifier.width(2.dp))
                }
            }
            BasicText("NL · 4471 2093 8810", style = TextStyle(color = Dark.copy(alpha = 0.7f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp, fontFeatureSettings = tnum))
            Box(Modifier.padding(top = 4.dp).fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Dark.copy(alpha = 0.14f))) {
                Box(Modifier.fillMaxWidth(0.4f).height(6.dp).background(Color(0xFF2E6B4E), RoundedCornerShape(3.dp)))
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                BasicText("Valid 1 – 30 Nov", style = sub)
                BasicText("18 days left", style = TextStyle(color = Dark, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = tnum))
            }
        }
        if (room >= ROOM_FOR_MORE) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RailChip(dot = true, text = "Auto-renews 1 Dec")
                RailChip(dot = false, text = "Saved £38 vs daily fares")
            }
        }
    }
}

@Composable
private fun RailChip(dot: Boolean, text: String) {
    Row(
        Modifier
            .height(30.dp)
            .background(Line.copy(alpha = 0.06f), RoundedCornerShape(15.dp))
            .border(Dp.Hairline, Line.copy(alpha = 0.16f), RoundedCornerShape(15.dp))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (dot) Dot(Good)
        BasicText(text, style = TextStyle(color = RailInk, fontSize = 12.sp, fontWeight = FontWeight.SemiBold), maxLines = 1)
    }
}

@Composable
private fun AccountPage(room: Float) {
    val rows = listOf("Card" to "ending 4421", "Commute alerts" to "On, 07:45", "Home station" to "Elm Cross")
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        RailHeading("ACCOUNT", "Maren Holt")
        if (room >= ROOM_FOR_MORE) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(48.dp).background(RailGold, CircleShape), contentAlignment = Alignment.Center) {
                    BasicText("MH", style = TextStyle(color = Dark, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold))
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    BasicText("Member since 2021", style = LineStyle)
                    BasicText("312 trips · 4,860 km this year", style = SubStyle)
                }
            }
        }
        RailCard(compact = true) {
            rows.forEachIndexed { i, (label, value) ->
                Column {
                    if (i > 0) Rule()
                    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        BasicText(label, style = TextStyle(color = RailMuted, fontSize = 14.sp))
                        BasicText(value, style = TextStyle(color = RailInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold))
                    }
                }
            }
        }
        if (room >= ROOM_FOR_MORE) {
            BasicText("Signed in on this phone · Sign out", Modifier.fillMaxWidth(), style = TextStyle(color = RailMuted, fontSize = 13.sp, textAlign = TextAlign.Center))
        }
    }
}

/** One section's page, by index. */
@Composable
internal fun RailPage(k: Int, room: Float) {
    when (k) {
        0 -> HomePage(room)
        1 -> TripsPage(room)
        2 -> TicketsPage(room)
        else -> AccountPage(room)
    }
}
